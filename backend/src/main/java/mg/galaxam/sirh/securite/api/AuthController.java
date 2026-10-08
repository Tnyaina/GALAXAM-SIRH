package mg.galaxam.sirh.securite.api;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.securite.api.dto.LoginRequest;
import mg.galaxam.sirh.securite.api.dto.RegisterRequest;
import mg.galaxam.sirh.admin.persistence.RefMenu;
import mg.galaxam.sirh.securite.Utilisateur;
import mg.galaxam.sirh.securite.AuditLog;
import mg.galaxam.sirh.securite.CustomUserDetails;
import mg.galaxam.sirh.securite.CustomUserDetailsService;
import mg.galaxam.sirh.securite.JournalConnexion;
import mg.galaxam.sirh.securite.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private mg.galaxam.sirh.cron.RattrapageAutomatiqueService rattrapageAutomatiqueService;

    private ResponseCookie createRefreshCookie(String token, boolean isSecure, long maxAgeSeconds) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(isSecure)
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .sameSite(isSecure ? "None" : "Lax")
                .build();
    }

    @GetMapping("/ping")
    public ResponseEntity<java.util.Map<String, String>> ping() {
        return ResponseEntity.ok(java.util.Map.of("status", "UP"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        
        if (request.getEmail() == null || request.getMotDePasse() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "L'adresse e-mail et le mot de passe sont requis.");
        }

        try (Connection c = dataSource.getConnection()) {
            
            c.setAutoCommit(false); 
            
            try {
                Utilisateur u = Utilisateur.rechercherParEmail(request.getEmail(), c);
                JournalConnexion journal = new JournalConnexion();
                
                if (u != null) {
                    journal.setIdUtilisateur(u.getIdUtilisateur());
                    
                    if (!u.isEstActif()) {
                        journal.setStatut("COMPTE_DESACTIVE");
                        journal.save(c);
                        c.commit();
                        throw new org.springframework.security.access.AccessDeniedException(
                                "Votre compte utilisateur est actuellement désactivé.");
                    }
                    
                    if (u.verifierMotDePasse(request.getMotDePasse())) {
                        journal.setStatut("SUCCES");
                        journal.save(c);
                        
                        // Génération des tokens JWT
                        UserDetails userDetails = customUserDetailsService.loadUserByUsername(u.getEmail());
                        String accessToken = jwtService.generateAccessToken(userDetails);
                        String refreshToken = jwtService.generateRefreshToken(userDetails);

                        // Persistance du refresh token en base de données
                        java.sql.Timestamp expirationTs = new java.sql.Timestamp(System.currentTimeMillis() + jwtService.getRefreshTokenExpirationMs());
                        Utilisateur.enregistrerRefreshToken(u.getIdUtilisateur(), refreshToken, expirationTs, c);
                        c.commit();

                        // Création du cookie HTTP-Only pour le Refresh Token
                        boolean isSecure = httpRequest != null && (httpRequest.isSecure() || "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto")));
                        ResponseCookie refreshCookie = createRefreshCookie(refreshToken, isSecure, jwtService.getRefreshTokenExpirationMs() / 1000);

                        Map<String, Object> responseBody = new HashMap<>();
                        responseBody.put("accessToken", accessToken);
                        responseBody.put("expiresIn", jwtService.getAccessTokenExpirationMs() / 1000);
                        responseBody.put("identifiant", u.getIdentifiant());
                        responseBody.put("email", u.getEmail());
                        responseBody.put("role", userDetails.getAuthorities().stream()
                                .map(auth -> auth.getAuthority())
                                .findFirst()
                                .orElse("ROLE_USER"));

                        if (rattrapageAutomatiqueService != null) {
                            rattrapageAutomatiqueService.verifierEtExecuterRattrapageAsync();
                        }

                        return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                                .body(responseBody);
                    } else {
                        journal.setStatut("ECHEC");
                        journal.save(c);
                    }
                }
                
                c.commit(); 
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.AUTHENTICATION_REQUIRED,
                        "Identifiant ou mot de passe incorrect.");
                
            } catch (Exception e) {
                c.rollback(); 
                throw e; 
            }
            
        } catch (Exception e) {
            if (e instanceof mg.galaxam.sirh.common.exception.SirhBusinessException business) throw business;
            if (e instanceof org.springframework.security.access.AccessDeniedException denied) throw denied;
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletRequest httpRequest
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.AUTHENTICATION_REQUIRED,
                    "Refresh token manquant.");
        }

        try {
            if (!jwtService.isRefreshTokenValid(refreshToken)) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.AUTHENTICATION_REQUIRED,
                        "Refresh token invalide ou expiré.");
            }

            try (Connection c = dataSource.getConnection()) {
                Utilisateur u = Utilisateur.rechercherParRefreshToken(refreshToken, c);
                if (u == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.AUTHENTICATION_REQUIRED,
                            "Session révoquée ou introuvable.");
                }

                UserDetails userDetails = customUserDetailsService.loadUserByUsername(u.getEmail());

                String newAccessToken = jwtService.generateAccessToken(userDetails);
                String newRefreshToken = jwtService.generateRefreshToken(userDetails);

                // Rotation du refresh token en base
                java.sql.Timestamp expirationTs = new java.sql.Timestamp(System.currentTimeMillis() + jwtService.getRefreshTokenExpirationMs());
                Utilisateur.enregistrerRefreshToken(u.getIdUtilisateur(), newRefreshToken, expirationTs, c);

                boolean isSecure = httpRequest != null && (httpRequest.isSecure() || "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto")));
                ResponseCookie refreshCookie = createRefreshCookie(newRefreshToken, isSecure, jwtService.getRefreshTokenExpirationMs() / 1000);

                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("accessToken", newAccessToken);

                if (rattrapageAutomatiqueService != null) {
                    rattrapageAutomatiqueService.verifierEtExecuterRattrapageAsync();
                }

                return ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                        .body(responseBody);
            }
        } catch (Exception e) {
            if (e instanceof mg.galaxam.sirh.common.exception.SirhBusinessException business) throw business;
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletRequest httpRequest
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try (Connection c = dataSource.getConnection()) {
                Utilisateur u = Utilisateur.rechercherParRefreshToken(refreshToken, c);
                if (u != null) {
                    Utilisateur.revoquerRefreshToken(u.getIdUtilisateur(), c);
                }
            } catch (Exception ignored) {
            }
        }

        boolean isSecure = httpRequest != null && (httpRequest.isSecure() || "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto")));
        ResponseCookie deleteCookie = createRefreshCookie("", isSecure, 0);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body("Déconnexion réussie.");
    }

    @GetMapping("/menu")
    public ResponseEntity<?> getMenu() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.AUTHENTICATION_REQUIRED);
        }
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();

        try (Connection c = dataSource.getConnection()) {
            Utilisateur u = Utilisateur.rechercherParEmail(userDetails.getUsername(), c);
            if (u == null || u.getIdRole() == null) {
                throw new org.springframework.security.access.AccessDeniedException("Utilisateur sans rôle assigné.");
            }

            List<RefMenu> flatMenus = RefMenu.rechercherParRole(u.getIdRole(), c);
            List<RefMenu> menuTree = buildMenuTree(flatMenus);

            return ResponseEntity.ok(menuTree);
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof org.springframework.security.access.AccessDeniedException denied) throw denied;
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    private List<RefMenu> buildMenuTree(List<RefMenu> flatMenus) {
        List<RefMenu> rootMenus = new ArrayList<>();
        Map<String, RefMenu> menuMap = new HashMap<>();

        for (RefMenu m : flatMenus) {
            menuMap.put(m.getIdMenu(), m);
        }

        for (RefMenu m : flatMenus) {
            if (m.getIdPere() == null) {
                rootMenus.add(m);
            } else {
                RefMenu parent = menuMap.get(m.getIdPere());
                if (parent != null) {
                    parent.getSousMenus().add(m);
                } else {
                    if (m.getNiveau() == 1) {
                        rootMenus.add(m);
                    }
                }
            }
        }
        return rootMenus;
    }

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {

        if (request.getIdentifiant() == null || request.getEmail() == null || request.getMotDePasse() == null || request.getIdRole() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le nom, l'adresse e-mail, le mot de passe et l'ID du rôle sont requis.");
        }

        try (Connection c = dataSource.getConnection()) {
            
            c.setAutoCommit(false); 

            try {
                Utilisateur existant = Utilisateur.rechercherParEmail(request.getEmail(), c);
                if (existant != null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED, "Cette adresse e-mail est déjà utilisée.");
                }

                Utilisateur u = new Utilisateur();
                u.setIdentifiant(request.getIdentifiant());
                u.setEmail(request.getEmail());
                u.setIdRole(request.getIdRole());
                u.setMotDePasseEnClair(request.getMotDePasse());

                u.controler(c); 
                u.save(c); 

                // Test d'audit : Écriture de l'audit d'insertion utilisateur
                AuditLog.audit(c, "INSERT", "utilisateur", String.valueOf(u.getIdUtilisateur()), null, u);

                c.commit();
                
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body("Utilisateur créé avec succès (ID: " + u.getIdUtilisateur() + ")");

            } catch (IllegalArgumentException e) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, e.getMessage());
                
            } catch (Exception e) {
                c.rollback();
                throw e; 
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
