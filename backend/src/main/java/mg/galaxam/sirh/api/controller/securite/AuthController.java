package mg.galaxam.sirh.api.controller.securite;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.securite.LoginRequest;
import mg.galaxam.sirh.api.dto.securite.RegisterRequest;
import mg.galaxam.sirh.model.admin.RefMenu;
import mg.galaxam.sirh.model.securite.Utilisateur;
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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        
        if (request.getIdentifiant() == null || request.getMotDePasse() == null) {
            return ResponseEntity.badRequest().body("L'identifiant et le mot de passe sont requis.");
        }

        try (Connection c = dataSource.getConnection()) {
            
            c.setAutoCommit(false); 
            
            try {
                Utilisateur u = Utilisateur.rechercherParIdentifiant(request.getIdentifiant(), c);
                JournalConnexion journal = new JournalConnexion();
                
                if (u != null) {
                    journal.setIdUtilisateur(u.getIdUtilisateur());
                    
                    if (!u.isEstActif()) {
                        journal.setStatut("COMPTE_DESACTIVE");
                        journal.save(c);
                        c.commit();
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body("Votre compte utilisateur est actuellement désactivé. Veuillez contacter le service RH pour le réactiver.");
                    }
                    
                    if (u.verifierMotDePasse(request.getMotDePasse())) {
                        journal.setStatut("SUCCES");
                        journal.save(c);
                        
                        // Génération des tokens JWT
                        UserDetails userDetails = customUserDetailsService.loadUserByUsername(u.getIdentifiant());
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
                        responseBody.put("expiresIn", 900); // 15 minutes en secondes
                        responseBody.put("identifiant", u.getIdentifiant());
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
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Identifiant ou mot de passe incorrect. Veuillez vérifier votre saisie ou vous rapprocher du service RH si vous n'avez pas encore d'accès.");
                
            } catch (Exception e) {
                c.rollback(); 
                throw e; 
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Une erreur technique temporaire est survenue sur le serveur. Veuillez réessayer dans quelques instants.");
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletRequest httpRequest
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token manquant.");
        }

        try {
            if (!jwtService.isRefreshTokenValid(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token invalide ou expiré.");
            }

            try (Connection c = dataSource.getConnection()) {
                Utilisateur u = Utilisateur.rechercherParRefreshToken(refreshToken, c);
                if (u == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Session révoquée ou introuvable.");
                }

                UserDetails userDetails = customUserDetailsService.loadUserByUsername(u.getIdentifiant());

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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Erreur lors du rafraîchissement du token.");
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Non authentifié.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();

        try (Connection c = dataSource.getConnection()) {
            Utilisateur u = Utilisateur.rechercherParIdentifiant(userDetails.getUsername(), c);
            if (u == null || u.getIdRole() == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Utilisateur sans rôle assigné.");
            }

            List<RefMenu> flatMenus = RefMenu.rechercherParRole(u.getIdRole(), c);
            List<RefMenu> menuTree = buildMenuTree(flatMenus);

            return ResponseEntity.ok(menuTree);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de récupération du menu.");
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

        if (request.getIdentifiant() == null || request.getMotDePasse() == null || request.getIdRole() == null) {
            return ResponseEntity.badRequest().body("L'identifiant, le mot de passe et l'ID du rôle sont requis.");
        }

        try (Connection c = dataSource.getConnection()) {
            
            c.setAutoCommit(false); 

            try {
                Utilisateur existant = Utilisateur.rechercherParIdentifiant(request.getIdentifiant(), c);
                if (existant != null) {
                    c.rollback();
                    return ResponseEntity.status(HttpStatus.CONFLICT).body("Cet identifiant est déjà utilisé.");
                }

                Utilisateur u = new Utilisateur();
                u.setIdentifiant(request.getIdentifiant());
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
                return ResponseEntity.badRequest().body(e.getMessage());
                
            } catch (Exception e) {
                c.rollback();
                throw e; 
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur interne du serveur lors de l'inscription.");
        }
    }
}
