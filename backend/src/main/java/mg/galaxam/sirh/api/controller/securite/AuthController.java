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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        
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
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ce compte est désactivé.");
                    }
                    
                    if (u.verifierMotDePasse(request.getMotDePasse())) {
                        journal.setStatut("SUCCES");
                        journal.save(c);
                        c.commit();
                        
                        // Génération des tokens JWT
                        UserDetails userDetails = customUserDetailsService.loadUserByUsername(u.getIdentifiant());
                        String accessToken = jwtService.generateAccessToken(userDetails);
                        String refreshToken = jwtService.generateRefreshToken(userDetails);

                        // Création du cookie HTTP-Only pour le Refresh Token
                        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                                .httpOnly(true)
                                .secure(true)
                                .path("/api/auth")
                                .maxAge(jwtService.getRefreshTokenExpirationMs() / 1000)
                                .sameSite("None")
                                .build();

                        Map<String, Object> responseBody = new HashMap<>();
                        responseBody.put("accessToken", accessToken);
                        responseBody.put("expiresIn", 900); // 15 minutes en secondes
                        responseBody.put("identifiant", u.getIdentifiant());
                        responseBody.put("role", userDetails.getAuthorities().stream()
                                .map(auth -> auth.getAuthority())
                                .findFirst()
                                .orElse("ROLE_USER"));

                        return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                                .body(responseBody);
                    } else {
                        journal.setStatut("ECHEC");
                        journal.save(c);
                    }
                }
                
                c.commit(); 
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants invalides.");
                
            } catch (Exception e) {
                c.rollback(); 
                throw e; 
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur interne du serveur.");
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token manquant.");
        }

        try {
            if (!jwtService.isRefreshTokenValid(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token invalide ou expiré.");
            }

            String username = jwtService.extractUsername(refreshToken);
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

            String newAccessToken = jwtService.generateAccessToken(userDetails);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("accessToken", newAccessToken);

            return ResponseEntity.ok(responseBody);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Erreur lors du rafraîchissement du token.");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/auth")
                .maxAge(0)
                .sameSite("None")
                .build();

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
