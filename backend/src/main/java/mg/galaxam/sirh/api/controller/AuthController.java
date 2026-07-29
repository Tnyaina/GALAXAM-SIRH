package mg.galaxam.sirh.api.controller;

import mg.galaxam.sirh.api.dto.LoginRequest;
import mg.galaxam.sirh.api.dto.RegisterRequest;
import mg.galaxam.sirh.securite.JournalConnexion;
import mg.galaxam.sirh.securite.Utilisateur;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*") // À ajuster plus tard pour ton front-end React
public class AuthController {

    @Autowired
    private DataSource dataSource; // Le pool de connexions HikariCP de Spring

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        
        if (request.getIdentifiant() == null || request.getMotDePasse() == null) {
            return ResponseEntity.badRequest().body("L'identifiant et le mot de passe sont requis.");
        }

        // 1. Récupération de la connexion JDBC
        try (Connection c = dataSource.getConnection()) {
            
            // 2. Gestion manuelle de la transaction (Active Record pattern)
            c.setAutoCommit(false); 
            
            try {
                // Recherche de l'utilisateur
                Utilisateur u = Utilisateur.rechercherParIdentifiant(request.getIdentifiant(), c);
                
                // Initialisation du journal
                JournalConnexion journal = new JournalConnexion();
                
                if (u != null) {
                    journal.setIdUtilisateur(u.getIdUtilisateur());
                    
                    // Vérification du statut du compte
                    if (!u.isEstActif()) {
                        journal.setStatut("COMPTE_DESACTIVE");
                        journal.save(c); // Appelle inserer() via ClassMere
                        c.commit();
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ce compte est désactivé.");
                    }
                    
                    // Vérification du mot de passe
                    if (u.verifierMotDePasse(request.getMotDePasse())) {
                        journal.setStatut("SUCCES");
                        journal.save(c);
                        c.commit();
                        
                        // TODO: Plus tard, retourner un Token (ex: JWT)
                        return ResponseEntity.ok("Connexion réussie pour " + u.getIdentifiant());
                    } else {
                        journal.setStatut("ECHEC");
                        journal.save(c);
                    }
                }
                
                // Si on arrive ici, soit l'utilisateur n'existe pas, soit le mot de passe est faux
                c.commit(); // On commit le journal même si la connexion a échoué
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants invalides.");
                
            } catch (Exception e) {
                c.rollback(); // En cas d'erreur SQL ou métier, on annule tout
                throw e; // Renvoyé au bloc catch externe
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur interne du serveur.");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {

        // 1. Vérification basique de la requête
        if (request.getIdentifiant() == null || request.getMotDePasse() == null || request.getIdRole() == null) {
            return ResponseEntity.badRequest().body("L'identifiant, le mot de passe et l'ID du rôle sont requis.");
        }

        try (Connection c = dataSource.getConnection()) {
            
            c.setAutoCommit(false); // Début de la transaction manuelle

            try {
                // 2. Vérifier si l'utilisateur existe déjà en base
                Utilisateur existant = Utilisateur.rechercherParIdentifiant(request.getIdentifiant(), c);
                if (existant != null) {
                    c.rollback();
                    return ResponseEntity.status(HttpStatus.CONFLICT).body("Cet identifiant est déjà utilisé.");
                }

                // 3. Préparation de la nouvelle entité
                Utilisateur u = new Utilisateur();
                u.setIdentifiant(request.getIdentifiant());
                u.setIdRole(request.getIdRole());
                
                // Utilisation du setter spécifique pour indiquer que c'est un mot de passe en clair
                u.setMotDePasseEnClair(request.getMotDePasse());

                // 4. Déclenchement des règles métier (Hachage BCrypt & Regex)
                u.controler(c); 

                // 5. Sauvegarde en base (qui exécute le INSERT)
                u.save(c); 

                // 6. Validation de la transaction
                c.commit();
                
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body("Utilisateur créé avec succès (ID: " + u.getIdUtilisateur() + ")");

            } catch (IllegalArgumentException e) {
                // Intercepte les erreurs lancées par controler() (ex: mot de passe trop court)
                c.rollback();
                return ResponseEntity.badRequest().body(e.getMessage());
                
            } catch (Exception e) {
                // Erreur SQL ou technique
                c.rollback();
                throw e; // Renvoyé au bloc catch externe
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur interne du serveur lors de l'inscription.");
        }
    }
}