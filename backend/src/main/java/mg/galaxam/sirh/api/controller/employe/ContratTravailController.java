package mg.galaxam.sirh.api.controller.employe;

import mg.galaxam.sirh.employe.ContratTravail;
import mg.galaxam.sirh.employe.ContratTravailCPL;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

@RestController
public class ContratTravailController {

    @Autowired
    private DataSource dataSource;

    /**
     * Historique des contrats de travail d'un employé.
     */
    @GetMapping("/employes/{idEmploye}/contrats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getContratsByEmploye(@PathVariable int idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            List<ContratTravailCPL> list = ContratTravailCPL.listByEmployeId(idEmploye, c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des contrats de travail.");
        }
    }

    /**
     * Contrat de travail actif d'un employé.
     */
    @GetMapping("/employes/{idEmploye}/contrat-actif")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getContratActif(@PathVariable int idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            ContratTravailCPL ct = ContratTravailCPL.getContratActif(idEmploye, c);
            return ResponseEntity.ok(ct);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération du contrat actif.");
        }
    }

    /**
     * Création / Établissement d'un contrat de travail.
     */
    @PostMapping("/employes/{idEmploye}/contrats")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createContrat(@PathVariable int idEmploye, @RequestBody ContratTravail req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                req.setIdEmploye(idEmploye);

                // Si un contrat est marqué actif, désactiver les anciens contrats actifs et lier le contrat précédent
                if (req.isEstActif()) {
                    List<ContratTravail> anciens = ContratTravail.listByEmployeId(idEmploye, c);
                    for (ContratTravail ct : anciens) {
                        if (ct.isEstActif()) {
                            if (req.getIdContratPrecedent() == null) {
                                req.setIdContratPrecedent(ct.getIdContrat());
                            }
                            ct.setEstActif(false);
                            ct.update(c);
                        }
                    }
                }

                req.save(c);
                AuditLog.audit(c, "CREATION", "contrat_travail", String.valueOf(req.getIdContrat()), null, req);
                c.commit();

                ContratTravailCPL createdView = new ContratTravailCPL().rechercherParId(req.getIdContrat(), c);
                return ResponseEntity.status(HttpStatus.CREATED).body(createdView);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création du contrat de travail.");
        }
    }

    /**
     * Modification d'un contrat de travail.
     */
    @PutMapping("/contrats-travail/{idContrat}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateContrat(@PathVariable int idContrat, @RequestBody ContratTravail req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ContratTravail existing = new ContratTravail().rechercherParId(idContrat, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Contrat de travail non trouvé.");
                }

                existing.setIdFiliale(req.getIdFiliale());
                existing.setIdTypeContrat(req.getIdTypeContrat());
                existing.setIdTypeIntervenant(req.getIdTypeIntervenant());
                existing.setNumeroContrat(req.getNumeroContrat());
                existing.setDateSignature(req.getDateSignature());
                existing.setDateDebut(req.getDateDebut());
                existing.setDateFin(req.getDateFin());
                existing.setDateFinEssai(req.getDateFinEssai());
                existing.setEssaiRenouvele(req.isEssaiRenouvele());
                existing.setComptabiliseEffectif(req.isComptabiliseEffectif());
                existing.setEstActif(req.isEstActif());
                existing.setIdMotifSortie(req.getIdMotifSortie());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "contrat_travail", String.valueOf(idContrat), null, existing);
                c.commit();

                ContratTravailCPL updatedView = new ContratTravailCPL().rechercherParId(idContrat, c);
                return ResponseEntity.ok(updatedView);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la modification du contrat.");
        }
    }

    /**
     * Clôture / Désactivation d'un contrat de travail.
     */
    @DeleteMapping("/contrats-travail/{idContrat}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteContrat(@PathVariable int idContrat) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ContratTravail existing = new ContratTravail().rechercherParId(idContrat, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Contrat de travail non trouvé.");
                }

                existing.delete(c);
                AuditLog.audit(c, "DESACTIVATION", "contrat_travail", String.valueOf(idContrat), null, existing);
                c.commit();

                return ResponseEntity.ok(Map.of("message", "Contrat de travail désactivé avec succès."));
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la désactivation du contrat.");
        }
    }
}
