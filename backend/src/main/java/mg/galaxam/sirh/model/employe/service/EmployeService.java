package mg.galaxam.sirh.model.employe.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.common.PageDTO;
import mg.galaxam.sirh.api.dto.employe.DesactivationRequest;
import mg.galaxam.sirh.api.dto.employe.EmployeFicheDetailDTO;
import mg.galaxam.sirh.api.dto.employe.EmployeRequest;
import mg.galaxam.sirh.model.dashboard.service.NotificationRhService;
import mg.galaxam.sirh.model.employe.Employe;
import mg.galaxam.sirh.model.employe.EmployeCPL;
import mg.galaxam.sirh.model.employe.EmployeCompteBanque;
import mg.galaxam.sirh.model.employe.EmployeConjoint;
import mg.galaxam.sirh.model.employe.EmployeContactUrgence;
import mg.galaxam.sirh.model.employe.EmployeEnfant;
import mg.galaxam.sirh.model.employe.engine.EmployeValidationEngine;
import mg.galaxam.sirh.model.employe.engine.MatriculeGeneratorEngine;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.stereotype.Service;

@Service
public class EmployeService {

    private final DataSource dataSource;
    private final EmployeValidationEngine validationEngine;
    private final MatriculeGeneratorEngine matriculeEngine;
    private final NotificationRhService notificationService;

    public EmployeService(DataSource dataSource, EmployeValidationEngine validationEngine,
                          MatriculeGeneratorEngine matriculeEngine,
                          NotificationRhService notificationService) {
        this.dataSource = dataSource;
        this.validationEngine = validationEngine;
        this.matriculeEngine = matriculeEngine;
        this.notificationService = notificationService;
    }

    public Map<String, Long> getStats(String q) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return getStats(q, c);
        }
    }

    public Map<String, Long> getStats(String q, Connection c) throws Exception {
        long total = EmployeCPL.compterEmployes(c, q, null);
        long actifs = EmployeCPL.compterEmployes(c, q, true);
        long inactifs = EmployeCPL.compterEmployes(c, q, false);

        return Map.of(
            "total", total,
            "actifs", actifs,
            "inactifs", inactifs
        );
    }

    public PageDTO<EmployeCPL> getEmployes(String q, Boolean actif, int page, int size) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return getEmployes(q, actif, page, size, c);
        }
    }

    public PageDTO<EmployeCPL> getEmployes(String q, Boolean actif, int page, int size, Connection c) throws Exception {
        int offset = (page - 1) * size;
        long totalElements = EmployeCPL.compterEmployes(c, q, actif);
        List<EmployeCPL> content = EmployeCPL.rechercherEmployes(c, q, actif, size, offset);
        return new PageDTO<>(content, totalElements, page, size);
    }

    public EmployeFicheDetailDTO getFicheDetail(int id) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return getFicheDetail(id, c);
        }
    }

    public EmployeFicheDetailDTO getFicheDetail(int id, Connection c) throws Exception {
        EmployeCPL empView = new EmployeCPL().rechercherParId(id, c);
        if (empView == null) {
            return null;
        }

        EmployeConjoint conjoint = EmployeConjoint.findByEmployeId(id, c);
        List<EmployeEnfant> enfants = EmployeEnfant.listByEmployeId(id, c);
        List<EmployeContactUrgence> contacts = EmployeContactUrgence.listByEmployeId(id, c);
        List<EmployeCompteBanque> comptes = EmployeCompteBanque.listByEmployeId(id, c);

        return new EmployeFicheDetailDTO(empView, conjoint, enfants, contacts, comptes);
    }

    public EmployeCPL creerEmploye(EmployeRequest req) throws Exception {
        // 1. Validation métier par le Domain Engine
        validationEngine.validerEmploye(req);

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 2. Gestion et génération automatique du matricule si non renseigné
                if (req.getMatricule() == null || req.getMatricule().trim().isEmpty() || "AUTO".equalsIgnoreCase(req.getMatricule().trim())) {
                    req.setMatricule(generateNextMatricule(c));
                }

                // 3. Employé principal
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

                // 4. Conjoint
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

                // 5. Enfants
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

                // 6. Contacts d'urgence
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

                // 7. Compte bancaire principal
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

                // Notification RH via source unique de vérité
                notificationService.creer(c, "EMPLOYE_CREE", "INFO",
                    "Nouvel employé - " + emp.getNom() + " " + emp.getPrenom(),
                    "La fiche de l'employé " + emp.getNom() + " " + emp.getPrenom()
                        + " a été créée. Pensez à ajouter un contrat et les documents obligatoires.",
                    "/employes/" + empId + "?tab=documents",
                    empId);

                c.commit();
                return new EmployeCPL().rechercherParId(empId, c);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public EmployeCPL modifierEmploye(int id, EmployeRequest req) throws Exception {
        // Validation métier
        validationEngine.validerEmploye(req);

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = new Employe().rechercherParId(id, c);
                if (emp == null) {
                    throw new IllegalArgumentException("Employé introuvable.");
                }

                Employe ancienState = new Employe().rechercherParId(id, c);

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

                emp.update(c);

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

                AuditLog.audit(c, "MODIFICATION", "employe", String.valueOf(id), ancienState, emp);

                EmployeCPL updatedView = new EmployeCPL().rechercherParId(id, c);
                c.commit();
                return updatedView;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public EmployeCPL desactiverEmploye(int id, DesactivationRequest req) throws Exception {
        validationEngine.validerDesactivation(req);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = new Employe().rechercherParId(id, c);
                if (emp == null) {
                    throw new IllegalArgumentException("Employé introuvable.");
                }

                String motif = (req != null && req.getMotif() != null && !req.getMotif().isBlank())
                        ? req.getMotif()
                        : "Autre";

                emp.desactiver(motif, c);
                AuditLog.audit(c, "DESACTIVATION", "employe", String.valueOf(id), null, emp);

                EmployeCPL updatedView = new EmployeCPL().rechercherParId(id, c);
                c.commit();
                return updatedView;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public EmployeCPL reactiverEmploye(int id) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = new Employe().rechercherParId(id, c);
                if (emp == null) {
                    throw new IllegalArgumentException("Employé introuvable.");
                }

                emp.reactiver(c);
                AuditLog.audit(c, "ACTIVATION", "employe", String.valueOf(id), null, emp);

                EmployeCPL updatedView = new EmployeCPL().rechercherParId(id, c);
                c.commit();
                return updatedView;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public boolean checkMatriculeExists(String matricule) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM employe WHERE matricule = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, matricule);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    public String generateNextMatricule() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return generateNextMatricule(c);
        }
    }

    public String generateNextMatricule(Connection c) throws Exception {
        String sql = "SELECT matricule FROM employe";
        long maxSeq = 0;
        try (PreparedStatement ps = c.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String mat = rs.getString(1);
                if (mat != null && !mat.trim().isEmpty()) {
                    Long seq = matriculeEngine.extraireSequence(mat.trim());
                    if (seq != null && seq > maxSeq) {
                        maxSeq = seq;
                    }
                }
            }
        }
        return matriculeEngine.genererMatriculeParDefaut(maxSeq + 1);
    }

    public List<Map<String, Object>> getOptions(Boolean actif) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            List<EmployeCPL> list = EmployeCPL.rechercherEmployes(c, null, actif, 1000, 0);
            return list.stream().map(e -> Map.<String, Object>of(
                "idEmploye", e.getTuppleID(),
                "matricule", e.getMatricule() != null ? e.getMatricule() : "",
                "nom", e.getNom() != null ? e.getNom() : "",
                "prenom", e.getPrenom() != null ? e.getPrenom() : "",
                "nomComplet", e.getNomComplet() != null ? e.getNomComplet() : "",
                "photoUrl", e.getPhotoUrl() != null ? e.getPhotoUrl() : ""
            )).toList();
        }
    }
}
