package mg.galaxam.sirh.securite.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.securite.api.dto.UtilisateurRequest;
import mg.galaxam.sirh.securite.api.dto.UtilisateurResponse;
import mg.galaxam.sirh.common.transaction.TransactionRunner;
import mg.galaxam.sirh.admin.persistence.RefRole;
import mg.galaxam.sirh.securite.Utilisateur;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/securite/utilisateurs")
public class UtilisateurController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionRunner transactionRunner;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllUsers() {
        String sql = "SELECT u.id_utilisateur, u.identifiant, u.email, u.id_role, u.est_actif, " +
                     "u.date_desactivation, u.motif_desactivation, r.code_role, r.libelle as role_libelle " +
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
                res.setEmail(rs.getString("email"));
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getUserById(@PathVariable int id) {
        String sql = "SELECT u.id_utilisateur, u.identifiant, u.email, u.id_role, u.est_actif, " +
                     "u.date_desactivation, u.motif_desactivation, r.code_role, r.libelle as role_libelle " +
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
                    res.setEmail(rs.getString("email"));
                    res.setIdRole(rs.getInt("id_role"));
                    res.setCodeRole(rs.getString("code_role"));
                    res.setRoleLibelle(rs.getString("role_libelle"));
                    res.setEstActif(rs.getBoolean("est_actif"));
                    res.setDateDesactivation(rs.getTimestamp("date_desactivation"));
                    res.setMotifDesactivation(rs.getString("motif_desactivation"));
                    return ResponseEntity.ok(res);
                } else {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                            "Utilisateur non trouvé.");
                }
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody UtilisateurRequest request) {
        if (request.getIdentifiant() == null || request.getIdentifiant().isBlank() || request.getEmail() == null || request.getEmail().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Le nom d'affichage et l'adresse e-mail sont obligatoires.");
        }
        if (request.getMotDePasse() == null || request.getMotDePasse().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Le mot de passe est obligatoire.");
        }
        if (request.getIdRole() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Le rôle est obligatoire.");
        }

        return transactionRunner.required(c -> {
            RefRole r = (RefRole) new RefRole().findById(String.valueOf(request.getIdRole()), c);
            if (r == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Rôle assigné non trouvé.");
            }
            if (!r.isEstActif()) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                        "Le rôle assigné est désactivé.");
            }

            Utilisateur u = new Utilisateur();
            u.setIdentifiant(request.getIdentifiant());
            u.setEmail(request.getEmail());
            u.setIdRole(request.getIdRole());
            u.setMotDePasseEnClair(request.getMotDePasse());
            u.setEstActif(true);

            u.controler(c);
            u.save(c);

            AuditLog.audit(c, "INSERT", "utilisateur", String.valueOf(u.getIdUtilisateur()), null, u);

            UtilisateurResponse res = new UtilisateurResponse();
            res.setIdUtilisateur(u.getIdUtilisateur());
            res.setIdentifiant(u.getIdentifiant());
            res.setEmail(u.getEmail());
            res.setIdRole(u.getIdRole());
            res.setCodeRole(r.getCodeRole());
            res.setRoleLibelle(r.getLibelle());
            res.setEstActif(u.isEstActif());

            return ResponseEntity.status(HttpStatus.CREATED).body(res);
        });
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable int id, @RequestBody UtilisateurRequest request) {
        return transactionRunner.required(c -> {
            Utilisateur existing = (Utilisateur) new Utilisateur().findById(String.valueOf(id), c);
            if (existing == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Utilisateur non trouvé.");
            }

            Utilisateur oldState = new Utilisateur();
            oldState.setIdUtilisateur(existing.getIdUtilisateur());
            oldState.setIdentifiant(existing.getIdentifiant());
            oldState.setEmail(existing.getEmail());
            oldState.setMotDePasseHache(existing.getMotDePasseHache());
            oldState.setIdRole(existing.getIdRole());
            oldState.setEstActif(existing.isEstActif());
            oldState.setDateDesactivation(existing.getDateDesactivation());
            oldState.setMotifDesactivation(existing.getMotifDesactivation());

            if (request.getIdentifiant() != null && !request.getIdentifiant().isBlank()) {
                existing.setIdentifiant(request.getIdentifiant());
            }
            if (request.getEmail() != null && !request.getEmail().isBlank()) existing.setEmail(request.getEmail());
            if (request.getIdRole() != null) {
                RefRole r = (RefRole) new RefRole().findById(String.valueOf(request.getIdRole()), c);
                if (r == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                            "Rôle assigné non trouvé.");
                }
                if (!r.isEstActif()) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                            "Le rôle assigné est désactivé.");
                }
                existing.setIdRole(request.getIdRole());
            }
            if (request.getMotDePasse() != null && !request.getMotDePasse().isBlank()) {
                existing.setMotDePasseEnClair(request.getMotDePasse());
            }

            existing.controler(c);
            existing.update(c);

            AuditLog.audit(c, "UPDATE", "utilisateur", String.valueOf(id), oldState, existing);

            RefRole finalRole = (RefRole) new RefRole().findById(String.valueOf(existing.getIdRole()), c);
            UtilisateurResponse res = new UtilisateurResponse();
            res.setIdUtilisateur(existing.getIdUtilisateur());
            res.setIdentifiant(existing.getIdentifiant());
            res.setEmail(existing.getEmail());
            res.setIdRole(existing.getIdRole());
            if (finalRole != null) {
                res.setCodeRole(finalRole.getCodeRole());
                res.setRoleLibelle(finalRole.getLibelle());
            }
            res.setEstActif(existing.isEstActif());
            res.setDateDesactivation(existing.getDateDesactivation());
            res.setMotifDesactivation(existing.getMotifDesactivation());

            return ResponseEntity.ok(res);
        });
    }

    @PutMapping("/{id}/desactiver")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateUser(@PathVariable int id, @RequestBody(required = false) UtilisateurRequest request) {
        String motif = (request != null) ? request.getMotifDesactivation() : null;
        if (motif == null || motif.isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Le motif de désactivation est obligatoire.");
        }

        return transactionRunner.required(c -> {
            Utilisateur existing = (Utilisateur) new Utilisateur().findById(String.valueOf(id), c);
            if (existing == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Utilisateur non trouvé.");
            }
            if (!existing.isEstActif()) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                        "L'utilisateur est déjà désactivé.");
            }

            Utilisateur oldState = new Utilisateur();
            oldState.setIdUtilisateur(existing.getIdUtilisateur());
            oldState.setIdentifiant(existing.getIdentifiant());
            oldState.setEmail(existing.getEmail());
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

            RefRole finalRole = (RefRole) new RefRole().findById(String.valueOf(existing.getIdRole()), c);
            UtilisateurResponse res = new UtilisateurResponse();
            res.setIdUtilisateur(existing.getIdUtilisateur());
            res.setIdentifiant(existing.getIdentifiant());
            res.setEmail(existing.getEmail());
            res.setIdRole(existing.getIdRole());
            if (finalRole != null) {
                res.setCodeRole(finalRole.getCodeRole());
                res.setRoleLibelle(finalRole.getLibelle());
            }
            res.setEstActif(existing.isEstActif());
            res.setDateDesactivation(existing.getDateDesactivation());
            res.setMotifDesactivation(existing.getMotifDesactivation());

            return ResponseEntity.ok(res);
        });
    }

    @PutMapping("/{id}/activer")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateUser(@PathVariable int id) {
        return transactionRunner.required(c -> {
            Utilisateur existing = (Utilisateur) new Utilisateur().findById(String.valueOf(id), c);
            if (existing == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Utilisateur non trouvé.");
            }
            if (existing.isEstActif()) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                        "L'utilisateur est déjà actif.");
            }

            Utilisateur oldState = new Utilisateur();
            oldState.setIdUtilisateur(existing.getIdUtilisateur());
            oldState.setIdentifiant(existing.getIdentifiant());
            oldState.setEmail(existing.getEmail());
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

            RefRole finalRole = (RefRole) new RefRole().findById(String.valueOf(existing.getIdRole()), c);
            UtilisateurResponse res = new UtilisateurResponse();
            res.setIdUtilisateur(existing.getIdUtilisateur());
            res.setIdentifiant(existing.getIdentifiant());
            res.setEmail(existing.getEmail());
            res.setIdRole(existing.getIdRole());
            if (finalRole != null) {
                res.setCodeRole(finalRole.getCodeRole());
                res.setRoleLibelle(finalRole.getLibelle());
            }
            res.setEstActif(existing.isEstActif());
            res.setDateDesactivation(existing.getDateDesactivation());
            res.setMotifDesactivation(existing.getMotifDesactivation());

            return ResponseEntity.ok(res);
        });
    }
}
