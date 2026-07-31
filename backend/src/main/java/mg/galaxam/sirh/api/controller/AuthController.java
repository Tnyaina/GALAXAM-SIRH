package mg.galaxam.sirh.api.controller;

import mg.galaxam.sirh.api.dto.LoginRequest;
import mg.galaxam.sirh.api.dto.RegisterRequest;
import mg.galaxam.sirh.securite.JournalConnexion;
import mg.galaxam.sirh.securite.Utilisateur;
import mg.galaxam.sirh.securite.JwtService;
import mg.galaxam.sirh.securite.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

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
                                .sameSite("Strict")
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
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body("Déconnexion réussie.");
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