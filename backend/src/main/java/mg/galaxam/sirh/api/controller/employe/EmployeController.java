package mg.galaxam.sirh.api.controller.employe;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.common.PageDTO;
import mg.galaxam.sirh.api.dto.employe.DesactivationRequest;
import mg.galaxam.sirh.api.dto.employe.EmployeFicheDetailDTO;
import mg.galaxam.sirh.api.dto.employe.EmployeRequest;
import mg.galaxam.sirh.model.employe.Employe;
import mg.galaxam.sirh.model.employe.EmployeCPL;
import mg.galaxam.sirh.model.employe.EmployeCompteBanque;
import mg.galaxam.sirh.model.employe.EmployeConjoint;
import mg.galaxam.sirh.model.employe.EmployeContactUrgence;
import mg.galaxam.sirh.model.employe.EmployeEnfant;
import mg.galaxam.sirh.model.employe.service.EmployeService;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/employes")
public class EmployeController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EmployeService employeService;

    // =========================================================================
    // LECTURE — déléguées à EmployeService (source unique de vérité)
    // =========================================================================

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployeStats(@RequestParam(required = false) String q) {
        try {
            return ResponseEntity.ok(employeService.getStats(q));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du calcul des statistiques.");
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployes(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Boolean actif,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        try {
            PageDTO<EmployeCPL> pageDto = employeService.getEmployes(q, actif, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des employés.");
        }
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployeOptions(@RequestParam(required = false, defaultValue = "true") Boolean actif) {
        try (Connection c = dataSource.getConnection()) {
            List<EmployeCPL> list = EmployeCPL.rechercherEmployes(c, null, actif, 1000, 0);
            List<Map<String, Object>> options = list.stream().map(e -> Map.<String, Object>of(
                "idEmploye", e.getTuppleID(),
                "matricule", e.getMatricule() != null ? e.getMatricule() : "",
                "nom", e.getNom() != null ? e.getNom() : "",
                "prenom", e.getPrenom() != null ? e.getPrenom() : "",
                "nomComplet", e.getNomComplet() != null ? e.getNomComplet() : "",
                "photoUrl", e.getPhotoUrl() != null ? e.getPhotoUrl() : ""
            )).toList();
            return ResponseEntity.ok(options);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des options d'employés.");
        }
    }

    @GetMapping("/matricule/check")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> checkMatriculeExists(@RequestParam String matricule) {
        try {
            boolean exists = employeService.checkMatriculeExists(matricule);
            return ResponseEntity.ok(Map.of("exists", exists));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la vérification du matricule.");
        }
    }

    @GetMapping("/matricule/next")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getNextMatricule() {
        try {
            String matricule = employeService.generateNextMatricule();
            return ResponseEntity.ok(Map.of("matricule", matricule));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la génération du matricule.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployeFicheDetail(@PathVariable int id) {
        try {
            EmployeFicheDetailDTO detail = employeService.getFicheDetail(id);
            if (detail == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employé non trouvé.");
            }
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du chargement de la fiche employé.");
        }
    }

    // =========================================================================
    // CRÉATION — déléguée à EmployeService (évite la double notification)
    // =========================================================================

    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createEmploye(@RequestBody EmployeRequest req) {
        try {
            EmployeCPL created = employeService.creerEmploye(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors de la création d'employé.");
        }
    }

    // =========================================================================
    // MODIFICATION — logique conservée dans le contrôleur (pas encore dans EmployeService)
    // =========================================================================

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateEmploye(@PathVariable int id, @RequestBody EmployeRequest req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe existing = new Employe().rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employé non trouvé.");
                }

                existing.setMatricule(req.getMatricule());
                existing.setNom(req.getNom());
                existing.setPrenom(req.getPrenom());
                existing.setSexe(req.getSexe());
                existing.setDateNaissance(req.getDateNaissance());
                existing.setLieuNaissance(req.getLieuNaissance());
                existing.setNationalite(req.getNationalite());
                existing.setPereNomPrenom(req.getPereNomPrenom());
                existing.setMereNomPrenom(req.getMereNomPrenom());
                existing.setSituationFamiliale(req.getSituationFamiliale());

                existing.setCinNumero(req.getCinNumero());
                existing.setCinDateDelivrance(req.getCinDateDelivrance());
                existing.setCinLieuDelivrance(req.getCinLieuDelivrance());
                existing.setCinDuplicataDate(req.getCinDuplicataDate());
                existing.setCinDuplicataLieu(req.getCinDuplicataLieu());

                existing.setPermisConduireNumero(req.getPermisConduireNumero());
                existing.setPermisConduireDateDelivrance(req.getPermisConduireDateDelivrance());
                existing.setPermisConduireLieuDelivrance(req.getPermisConduireLieuDelivrance());

                existing.setAdresse(req.getAdresse());
                existing.setVille(req.getVille());
                existing.setTelephone(req.getTelephone());
                existing.setEmail(req.getEmail());

                existing.setNumAffiliationCnaps(req.getNumAffiliationCnaps());
                existing.setPhotoUrl(req.getPhotoUrl());
                existing.setDateDeclaration(req.getDateDeclaration());

                existing.update(c);

                // Conjoint
                EmployeConjoint.deleteByEmployeId(id, c);
                if (req.getConjoint() != null && req.getConjoint().getNom() != null && !req.getConjoint().getNom().isBlank()) {
                    EmployeConjoint cj = new EmployeConjoint();
                    cj.setIdEmploye(id);
                    cj.setNom(req.getConjoint().getNom());
                    cj.setPrenom(req.getConjoint().getPrenom());
                    cj.setDateNaissance(req.getConjoint().getDateNaissance());
                    cj.setLieuNaissance(req.getConjoint().getLieuNaissance());
                    cj.setSociete(req.getConjoint().getSociete());
                    cj.setFonction(req.getConjoint().getFonction());
                    cj.save(c);
                }

                // Enfants
                EmployeEnfant.deleteByEmployeId(id, c);
                if (req.getEnfants() != null) {
                    for (EmployeRequest.EnfantRequest enfReq : req.getEnfants()) {
                        if (enfReq.getNom() != null && !enfReq.getNom().isBlank()) {
                            EmployeEnfant enf = new EmployeEnfant();
                            enf.setIdEmploye(id);
                            enf.setNom(enfReq.getNom());
                            enf.setPrenom(enfReq.getPrenom());
                            enf.setSexe(enfReq.getSexe());
                            enf.setDateNaissance(enfReq.getDateNaissance());
                            enf.setLieuNaissance(enfReq.getLieuNaissance());
                            enf.setACharge(enfReq.isACharge());
                            enf.save(c);
                        }
                    }
                }

                // Contacts d'urgence
                EmployeContactUrgence.deleteByEmployeId(id, c);
                if (req.getContactsUrgence() != null) {
                    for (EmployeRequest.ContactUrgenceRequest ctReq : req.getContactsUrgence()) {
                        if (ctReq.getNom() != null && !ctReq.getNom().isBlank()) {
                            EmployeContactUrgence ct = new EmployeContactUrgence();
                            ct.setIdEmploye(id);
                            ct.setNom(ctReq.getNom());
                            ct.setPrenom(ctReq.getPrenom());
                            ct.setLienParente(ctReq.getLienParente());
                            ct.setAdresse(ctReq.getAdresse());
                            ct.setTelephone(ctReq.getTelephone());
                            ct.setSociete(ctReq.getSociete());
                            ct.save(c);
                        }
                    }
                }

                // Compte bancaire principal
                if (req.getCompteBanquePrincipal() != null && req.getCompteBanquePrincipal().getIdBanque() != null) {
                    EmployeCompteBanque.deleteByEmployeId(id, c);
                    EmployeCompteBanque cb = new EmployeCompteBanque();
                    cb.setIdEmploye(id);
                    cb.setIdBanque(req.getCompteBanquePrincipal().getIdBanque());
                    cb.setAgence(req.getCompteBanquePrincipal().getAgence());
                    cb.setNumCompteRib(req.getCompteBanquePrincipal().getNumCompteRib());
                    cb.setTitulaireCompte(req.getCompteBanquePrincipal().getTitulaireCompte());
                    cb.setEstPrincipal(true);
                    cb.save(c);
                }

                AuditLog.audit(c, "MODIFICATION", "employe", String.valueOf(id), null, existing);
                c.commit();

                EmployeCPL updatedView = new EmployeCPL().rechercherParId(id, c);
                return ResponseEntity.ok(updatedView);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors de la modification d'employé.");
        }
    }

    @PutMapping("/{id}/desactiver")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> desactiverEmploye(@PathVariable int id, @RequestBody(required = false) DesactivationRequest req) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = new Employe().rechercherParId(id, c);
                if (emp == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employé non trouvé.");
                }

                String motif = (req != null && req.getMotif() != null && !req.getMotif().isBlank())
                        ? req.getMotif()
                        : "Autre";

                emp.desactiver(motif, c);
                AuditLog.audit(c, "DESACTIVATION", "employe", String.valueOf(id), null, emp);
                c.commit();

                EmployeCPL updatedView = new EmployeCPL().rechercherParId(id, c);
                return ResponseEntity.ok(updatedView);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    @PutMapping("/{id}/activer")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> reactiverEmploye(@PathVariable int id) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = new Employe().rechercherParId(id, c);
                if (emp == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employé non trouvé.");
                }

                emp.reactiver(c);
                AuditLog.audit(c, "ACTIVATION", "employe", String.valueOf(id), null, emp);
                c.commit();

                EmployeCPL updatedView = new EmployeCPL().rechercherParId(id, c);
                return ResponseEntity.ok(updatedView);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }
}
