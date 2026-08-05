package mg.galaxam.sirh.api.controller.securite;

import mg.galaxam.sirh.api.dto.securite.UtilisateurRequest;
import mg.galaxam.sirh.api.dto.securite.UtilisateurResponse;
import mg.galaxam.sirh.referentiel.RefRole;
import mg.galaxam.sirh.securite.AuditLog;
import mg.galaxam.sirh.securite.Utilisateur;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/securite/utilisateurs")
public class UtilisateurController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllUsers() {
        String sql = "SELECT u.*, r.code_role, r.libelle as role_libelle " +
                     "FROM utilisateur u " +
                     "JOIN ref_role r ON u.id_role = r.id_role " +
                     "ORDER BY u.identifiant";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<UtilisateurResponse> responses = new ArrayList<>();
            while (rs.next()) {
                UtilisateurResponse res = new UtilisateurResponse();
                res.setIdUtilisateur(rs.getInt("id_utilisateur"));
                res.setIdentifiant(rs.getString("identifiant"));
                res.setIdRole(rs.getInt("id_role"));
                res.setCodeRole(rs.getString("code_role"));
                res.setRoleLibelle(rs.getString("role_libelle"));
                res.setEstActif(rs.getBoolean("est_actif"));
                res.setDateDesactivation(rs.getTimestamp("date_desactivation"));
                res.setMotifDesactivation(rs.getString("motif_desactivation"));
                responses.add(res);
            }
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des utilisateurs.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getUserById(@PathVariable int id) {
        String sql = "SELECT u.*, r.code_role, r.libelle as role_libelle " +
                     "FROM utilisateur u " +
                     "JOIN ref_role r ON u.id_role = r.id_role " +
                     "WHERE u.id_utilisateur = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UtilisateurResponse res = new UtilisateurResponse();
                    res.setIdUtilisateur(rs.getInt("id_utilisateur"));
                    res.setIdentifiant(rs.getString("identifiant"));
                    res.setIdRole(rs.getInt("id_role"));
                    res.setCodeRole(rs.getString("code_role"));
                    res.setRoleLibelle(rs.getString("role_libelle"));
                    res.setEstActif(rs.getBoolean("est_actif"));
                    res.setDateDesactivation(rs.getTimestamp("date_desactivation"));
                    res.setMotifDesactivation(rs.getString("motif_desactivation"));
                    return ResponseEntity.ok(res);
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération de l'utilisateur.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody UtilisateurRequest request) {
        if (request.getIdentifiant() == null || request.getIdentifiant().isBlank()) {
            return ResponseEntity.badRequest().body("L'identifiant est obligatoire.");
        }
        if (request.getMotDePasse() == null || request.getMotDePasse().isBlank()) {
            return ResponseEntity.badRequest().body("Le mot de passe est obligatoire.");
        }
        if (request.getIdRole() == null) {
            return ResponseEntity.badRequest().body("Le rôle est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Vérifier si le rôle existe et est actif
                RefRole role = new RefRole();
                RefRole r = (RefRole) role.findById(String.valueOf(request.getIdRole()), c);
                if (r == null) {
                    c.rollback();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Rôle assigné non trouvé.");
                }
                if (!r.isEstActif()) {
                    c.rollback();
                    return ResponseEntity.badRequest().body("Le rôle assigné est désactivé.");
                }

                // Créer l'utilisateur
                Utilisateur u = new Utilisateur();
                u.setIdentifiant(request.getIdentifiant());
                u.setIdRole(request.getIdRole());
                u.setMotDePasseEnClair(request.getMotDePasse());
                u.setEstActif(true);

                u.controler(c);
                u.save(c);

                AuditLog.audit(c, "INSERT", "utilisateur", String.valueOf(u.getIdUtilisateur()), null, u);

                c.commit();

                UtilisateurResponse res = new UtilisateurResponse();
                res.setIdUtilisateur(u.getIdUtilisateur());
                res.setIdentifiant(u.getIdentifiant());
                res.setIdRole(u.getIdRole());
                res.setCodeRole(r.getCodeRole());
                res.setRoleLibelle(r.getLibelle());
                res.setEstActif(u.isEstActif());

                return ResponseEntity.status(HttpStatus.CREATED).body(res);

            } catch (IllegalArgumentException e) {
                c.rollback();
                return ResponseEntity.badRequest().body(e.getMessage());
            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de la création de l'utilisateur.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion base de données.");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable int id, @RequestBody UtilisateurRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Utilisateur user = new Utilisateur();
                Utilisateur existing = (Utilisateur) user.findById(String.valueOf(id), c);
                if (existing == null) {
                    c.rollback();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé.");
                }

                // Pour l'audit
                Utilisateur oldState = new Utilisateur();
                oldState.setIdUtilisateur(existing.getIdUtilisateur());
                oldState.setIdentifiant(existing.getIdentifiant());
                oldState.setMotDePasseHache(existing.getMotDePasseHache());
                oldState.setIdRole(existing.getIdRole());
                oldState.setEstActif(existing.isEstActif());
                oldState.setDateDesactivation(existing.getDateDesactivation());
                oldState.setMotifDesactivation(existing.getMotifDesactivation());

                if (request.getIdentifiant() != null && !request.getIdentifiant().isBlank()) {
                    existing.setIdentifiant(request.getIdentifiant());
                }
                if (request.getIdRole() != null) {
                    // Vérifier si le rôle existe et est actif
                    RefRole r = (RefRole) new RefRole().findById(String.valueOf(request.getIdRole()), c);
                    if (r == null) {
                        c.rollback();
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Rôle assigné non trouvé.");
                    }
                    if (!r.isEstActif()) {
                        c.rollback();
                        return ResponseEntity.badRequest().body("Le rôle assigné est désactivé.");
                    }
                    existing.setIdRole(request.getIdRole());
                }
                if (request.getMotDePasse() != null && !request.getMotDePasse().isBlank()) {
                    existing.setMotDePasseEnClair(request.getMotDePasse());
                }

                existing.controler(c);
                existing.update(c);

                AuditLog.audit(c, "UPDATE", "utilisateur", String.valueOf(id), oldState, existing);

                c.commit();

                // Charger les infos du rôle mis à jour
                RefRole finalRole = (RefRole) new RefRole().findById(String.valueOf(existing.getIdRole()), c);
                UtilisateurResponse res = new UtilisateurResponse();
                res.setIdUtilisateur(existing.getIdUtilisateur());
                res.setIdentifiant(existing.getIdentifiant());
                res.setIdRole(existing.getIdRole());
                if (finalRole != null) {
                    res.setCodeRole(finalRole.getCodeRole());
                    res.setRoleLibelle(finalRole.getLibelle());
                }
                res.setEstActif(existing.isEstActif());
                res.setDateDesactivation(existing.getDateDesactivation());
                res.setMotifDesactivation(existing.getMotifDesactivation());

                return ResponseEntity.ok(res);

            } catch (IllegalArgumentException e) {
                c.rollback();
                return ResponseEntity.badRequest().body(e.getMessage());
            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de la modification de l'utilisateur.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion base de données.");
        }
    }

    @PutMapping("/{id}/desactiver")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateUser(@PathVariable int id, @RequestBody(required = false) UtilisateurRequest request) {
        String motif = (request != null) ? request.getMotifDesactivation() : null;
        if (motif == null || motif.isBlank()) {
            return ResponseEntity.badRequest().body("Le motif de désactivation est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Utilisateur user = new Utilisateur();
                Utilisateur existing = (Utilisateur) user.findById(String.valueOf(id), c);
                if (existing == null) {
                    c.rollback();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé.");
                }

                if (!existing.isEstActif()) {
                    c.rollback();
                    return ResponseEntity.badRequest().body("L'utilisateur est déjà désactivé.");
                }

                Utilisateur oldState = new Utilisateur();
                oldState.setIdUtilisateur(existing.getIdUtilisateur());
                oldState.setIdentifiant(existing.getIdentifiant());
                oldState.setMotDePasseHache(existing.getMotDePasseHache());
                oldState.setIdRole(existing.getIdRole());
                oldState.setEstActif(existing.isEstActif());
                oldState.setDateDesactivation(existing.getDateDesactivation());
                oldState.setMotifDesactivation(existing.getMotifDesactivation());

                existing.setEstActif(false);
                existing.setDateDesactivation(new Timestamp(System.currentTimeMillis()));
                existing.setMotifDesactivation(motif);

                existing.update(c);

                AuditLog.audit(c, "UPDATE", "utilisateur", String.valueOf(id), oldState, existing);

                c.commit();

                // Retourner l'utilisateur désactivé
                RefRole finalRole = (RefRole) new RefRole().findById(String.valueOf(existing.getIdRole()), c);
                UtilisateurResponse res = new UtilisateurResponse();
                res.setIdUtilisateur(existing.getIdUtilisateur());
                res.setIdentifiant(existing.getIdentifiant());
                res.setIdRole(existing.getIdRole());
                if (finalRole != null) {
                    res.setCodeRole(finalRole.getCodeRole());
                    res.setRoleLibelle(finalRole.getLibelle());
                }
                res.setEstActif(existing.isEstActif());
                res.setDateDesactivation(existing.getDateDesactivation());
                res.setMotifDesactivation(existing.getMotifDesactivation());

                return ResponseEntity.ok(res);

            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de la désactivation de l'utilisateur.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion base de données.");
        }
    }

    @PutMapping("/{id}/activer")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateUser(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Utilisateur user = new Utilisateur();
                Utilisateur existing = (Utilisateur) user.findById(String.valueOf(id), c);
                if (existing == null) {
                    c.rollback();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé.");
                }

                if (existing.isEstActif()) {
                    c.rollback();
                    return ResponseEntity.badRequest().body("L'utilisateur est déjà actif.");
                }

                Utilisateur oldState = new Utilisateur();
                oldState.setIdUtilisateur(existing.getIdUtilisateur());
                oldState.setIdentifiant(existing.getIdentifiant());
                oldState.setMotDePasseHache(existing.getMotDePasseHache());
                oldState.setIdRole(existing.getIdRole());
                oldState.setEstActif(existing.isEstActif());
                oldState.setDateDesactivation(existing.getDateDesactivation());
                oldState.setMotifDesactivation(existing.getMotifDesactivation());

                existing.setEstActif(true);
                existing.setDateDesactivation(null);
                existing.setMotifDesactivation(null);

                existing.update(c);

                AuditLog.audit(c, "UPDATE", "utilisateur", String.valueOf(id), oldState, existing);

                c.commit();

                // Retourner l'utilisateur réactivé
                RefRole finalRole = (RefRole) new RefRole().findById(String.valueOf(existing.getIdRole()), c);
                UtilisateurResponse res = new UtilisateurResponse();
                res.setIdUtilisateur(existing.getIdUtilisateur());
                res.setIdentifiant(existing.getIdentifiant());
                res.setIdRole(existing.getIdRole());
                if (finalRole != null) {
                    res.setCodeRole(finalRole.getCodeRole());
                    res.setRoleLibelle(finalRole.getLibelle());
                }
                res.setEstActif(existing.isEstActif());
                res.setDateDesactivation(existing.getDateDesactivation());
                res.setMotifDesactivation(existing.getMotifDesactivation());

                return ResponseEntity.ok(res);

            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de l'activation de l'utilisateur.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion base de données.");
        }
    }
}
