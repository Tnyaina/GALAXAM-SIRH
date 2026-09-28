package mg.galaxam.sirh.organisation.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.organisation.api.dto.FilialeRequest;
import mg.galaxam.sirh.organisation.persistence.RefFiliale;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/filiales")
public class FilialeController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllFiliales() {
        try (Connection c = dataSource.getConnection()) {
            List<RefFiliale> list = RefFiliale.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getFilialeById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefFiliale f = new RefFiliale();
            RefFiliale found = (RefFiliale) f.rechercherParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Filiale non trouvée.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createFiliale(@RequestBody FilialeRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale f = new RefFiliale();
                f.setCodeFiliale(request.getCodeFiliale());
                f.setRaisonSociale(request.getRaisonSociale());
                f.setNif(request.getNif());
                f.setStat(request.getStat());
                f.setAdresseSiege(request.getAdresseSiege());
                f.setRcs(request.getRcs());
                f.setTelephone(request.getTelephone());
                f.setEmail(request.getEmail());
                if (request.getSeuilEffectifAlerte() != null) {
                    f.setSeuilEffectifAlerte(request.getSeuilEffectifAlerte());
                }

                f.save(c);
                AuditLog.audit(c, "CREATION", "ref_filiale", String.valueOf(f.getIdFiliale()), null, f);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(f);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateFiliale(@PathVariable int id, @RequestBody FilialeRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale f = new RefFiliale();
                RefFiliale existing = (RefFiliale) f.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Filiale non trouvée.");
                }

                RefFiliale oldState = new RefFiliale();
                oldState.setIdFiliale(existing.getIdFiliale());
                oldState.setCodeFiliale(existing.getCodeFiliale());
                oldState.setRaisonSociale(existing.getRaisonSociale());

                existing.setCodeFiliale(request.getCodeFiliale());
                existing.setRaisonSociale(request.getRaisonSociale());
                existing.setNif(request.getNif());
                existing.setStat(request.getStat());
                existing.setAdresseSiege(request.getAdresseSiege());
                existing.setRcs(request.getRcs());
                existing.setTelephone(request.getTelephone());
                existing.setEmail(request.getEmail());
                if (request.getSeuilEffectifAlerte() != null) {
                    existing.setSeuilEffectifAlerte(request.getSeuilEffectifAlerte());
                }

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_filiale", String.valueOf(id), oldState, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}/desactiver")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateFiliale(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale f = new RefFiliale();
                RefFiliale existing = (RefFiliale) f.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Filiale non trouvée.");
                }

                // Garde-fou d'intégrité : vérifier la présence de sites actifs et de contrats en cours
                int nbSitesActifs = 0;
                String sqlCheckSites = "SELECT COUNT(*) FROM ref_site WHERE id_filiale_rattachement = ? AND est_actif = true";
                try (PreparedStatement ps = c.prepareStatement(sqlCheckSites)) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) nbSitesActifs = rs.getInt(1);
                    }
                }

                int nbContratsEnCours = 0;
                String sqlCheckContrats = "SELECT COUNT(*) FROM contrat_travail WHERE id_filiale = ? AND (date_fin IS NULL OR date_fin >= CURRENT_DATE)";
                try (PreparedStatement ps = c.prepareStatement(sqlCheckContrats)) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) nbContratsEnCours = rs.getInt(1);
                    }
                }

                if (nbSitesActifs > 0 || nbContratsEnCours > 0) {
                    StringBuilder errMsg = new StringBuilder("Impossible de désactiver la filiale « ")
                            .append(existing.getRaisonSociale())
                            .append(" » : elle comporte encore ");
                    if (nbSitesActifs > 0) {
                        errMsg.append(nbSitesActifs).append(" site(s) actif(s)");
                    }
                    if (nbSitesActifs > 0 && nbContratsEnCours > 0) {
                        errMsg.append(" et ");
                    }
                    if (nbContratsEnCours > 0) {
                        errMsg.append(nbContratsEnCours).append(" contrat(s) de travail en cours");
                    }
                    errMsg.append(". Veuillez réaffecter ou clôturer ces éléments avant de pouvoir désactiver la filiale.");
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.DATA_REFERENCED,
                            errMsg.toString());
                }

                existing.setEstActif(false);
                existing.setDateDesactivation(new Timestamp(System.currentTimeMillis()));
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_filiale", String.valueOf(id), null, existing);
                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}/activer")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateFiliale(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale f = new RefFiliale();
                RefFiliale existing = (RefFiliale) f.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Filiale non trouvée.");
                }

                existing.setEstActif(true);
                existing.setDateDesactivation(null);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_filiale", String.valueOf(id), null, existing);
                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
