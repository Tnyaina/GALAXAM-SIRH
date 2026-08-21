package mg.galaxam.sirh.api.controller.employe;

import java.sql.Connection;
import java.sql.PreparedStatement;
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

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployeStats(@RequestParam(required = false) String q) {
        try (Connection c = dataSource.getConnection()) {
            long total = EmployeCPL.compterEmployes(c, q, null);
            long actifs = EmployeCPL.compterEmployes(c, q, true);
            long inactifs = EmployeCPL.compterEmployes(c, q, false);

            return ResponseEntity.ok(Map.of(
                "total", total,
                "actifs", actifs,
                "inactifs", inactifs
            ));
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
        try (Connection c = dataSource.getConnection()) {
            int offset = (page - 1) * size;
            long totalElements = EmployeCPL.compterEmployes(c, q, actif);
            List<EmployeCPL> list = EmployeCPL.rechercherEmployes(c, q, actif, size, offset);
            
            PageDTO<EmployeCPL> pageDto = new PageDTO<>(list, totalElements, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des employés.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployeFicheDetail(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            EmployeCPL empView = new EmployeCPL().rechercherParId(id, c);
            if (empView == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employé non trouvé.");
            }

            EmployeConjoint conjoint = EmployeConjoint.findByEmployeId(id, c);
            List<EmployeEnfant> enfants = EmployeEnfant.listByEmployeId(id, c);
            List<EmployeContactUrgence> contacts = EmployeContactUrgence.listByEmployeId(id, c);
            List<EmployeCompteBanque> comptes = EmployeCompteBanque.listByEmployeId(id, c);

            EmployeFicheDetailDTO detail = new EmployeFicheDetailDTO(empView, conjoint, enfants, contacts, comptes);
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du chargement de la fiche employé.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createEmploye(@RequestBody EmployeRequest req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Employé principal
                Employe emp = new Employe();
                emp.setMatricule(req.getMatricule());
                emp.setNom(req.getNom());
                emp.setPrenom(req.getPrenom());
                emp.setSexe(req.getSexe());
                emp.setDateNaissance(req.getDateNaissance());
                emp.setLieuNaissance(req.getLieuNaissance());
                emp.setNationalite(req.getNationalite());
                emp.setPereNomPrenom(req.getPereNomPrenom());
                emp.setMereNomPrenom(req.getMereNomPrenom());
                emp.setSituationFamiliale(req.getSituationFamiliale());

                emp.setCinNumero(req.getCinNumero());
                emp.setCinDateDelivrance(req.getCinDateDelivrance());
                emp.setCinLieuDelivrance(req.getCinLieuDelivrance());
                emp.setCinDuplicataDate(req.getCinDuplicataDate());
                emp.setCinDuplicataLieu(req.getCinDuplicataLieu());

                emp.setPermisConduireNumero(req.getPermisConduireNumero());
                emp.setPermisConduireDateDelivrance(req.getPermisConduireDateDelivrance());
                emp.setPermisConduireLieuDelivrance(req.getPermisConduireLieuDelivrance());

                emp.setAdresse(req.getAdresse());
                emp.setVille(req.getVille());
                emp.setTelephone(req.getTelephone());
                emp.setEmail(req.getEmail());

                emp.setNumAffiliationCnaps(req.getNumAffiliationCnaps());
                emp.setPhotoUrl(req.getPhotoUrl());
                emp.setDateDeclaration(req.getDateDeclaration());

                emp.save(c);
                Integer empId = emp.getIdEmploye();

                // 2. Conjoint
                if (req.getConjoint() != null && req.getConjoint().getNom() != null && !req.getConjoint().getNom().isBlank()) {
                    EmployeConjoint cj = new EmployeConjoint();
                    cj.setIdEmploye(empId);
                    cj.setNom(req.getConjoint().getNom());
                    cj.setPrenom(req.getConjoint().getPrenom());
                    cj.setDateNaissance(req.getConjoint().getDateNaissance());
                    cj.setLieuNaissance(req.getConjoint().getLieuNaissance());
                    cj.setSociete(req.getConjoint().getSociete());
                    cj.setFonction(req.getConjoint().getFonction());
                    cj.save(c);
                }

                // 3. Enfants
                if (req.getEnfants() != null) {
                    for (EmployeRequest.EnfantRequest enfReq : req.getEnfants()) {
                        if (enfReq.getNom() != null && !enfReq.getNom().isBlank()) {
                            EmployeEnfant enf = new EmployeEnfant();
                            enf.setIdEmploye(empId);
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

                // 4. Contacts d'urgence
                if (req.getContactsUrgence() != null) {
                    for (EmployeRequest.ContactUrgenceRequest ctReq : req.getContactsUrgence()) {
                        if (ctReq.getNom() != null && !ctReq.getNom().isBlank()) {
                            EmployeContactUrgence ct = new EmployeContactUrgence();
                            ct.setIdEmploye(empId);
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

                // 5. Compte bancaire principal
                if (req.getCompteBanquePrincipal() != null && req.getCompteBanquePrincipal().getIdBanque() != null) {
                    EmployeCompteBanque cb = new EmployeCompteBanque();
                    cb.setIdEmploye(empId);
                    cb.setIdBanque(req.getCompteBanquePrincipal().getIdBanque());
                    cb.setAgence(req.getCompteBanquePrincipal().getAgence());
                    cb.setNumCompteRib(req.getCompteBanquePrincipal().getNumCompteRib());
                    cb.setTitulaireCompte(req.getCompteBanquePrincipal().getTitulaireCompte());
                    cb.setEstPrincipal(true);
                    cb.save(c);
                }

                AuditLog.audit(c, "CREATION", "employe", String.valueOf(empId), null, emp);
                
                // Notification en temps réel de création d'employé (destinée à l'équipe RH)
                try (PreparedStatement psNotif = c.prepareStatement(
                        "INSERT INTO notification_rh (id_utilisateur, type_alerte, priorite, titre, message, lien, id_employe_concerne) " +
                        "VALUES (null, 'EMPLOYE_CREE', 'INFO', ?, ?, ?, ?)")) {
                    psNotif.setString(1, "Nouvel employe - " + emp.getNom() + " " + emp.getPrenom());
                    psNotif.setString(2, "La fiche de l'employé " + emp.getNom() + " " + emp.getPrenom() + " a été cree. Pensez a ajouter un contrat et les documents obligatoires.");
                    psNotif.setString(3, "/employes/" + empId + "?tab=documents");
                    psNotif.setInt(4, empId);
                    psNotif.executeUpdate();
                }

                c.commit();

                EmployeCPL createdView = new EmployeCPL().rechercherParId(empId, c);
                return ResponseEntity.status(HttpStatus.CREATED).body(createdView);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors de la création d'employé.");
        }
    }

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
    public ResponseEntity<?> desactiverEmploye(@PathVariable int id, @RequestBody(required = false) DesactivationRequest req) {
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
                ex.printStackTrace();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors de la désactivation d'employé.");
        }
    }

    @PutMapping("/{id}/activer")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> reactiverEmploye(@PathVariable int id) {
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
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors de la réactivation d'employé.");
        }
    }
}
