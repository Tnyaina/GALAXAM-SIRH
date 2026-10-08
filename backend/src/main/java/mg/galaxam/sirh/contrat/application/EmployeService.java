package mg.galaxam.sirh.contrat.application;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.dto.PageDTO;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest.DesactivationRequest;
import mg.galaxam.sirh.contrat.api.dto.EmployeFicheDetailDTO;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import mg.galaxam.sirh.dashboard.application.NotificationRhService;
import mg.galaxam.sirh.contrat.persistence.Employe;
import mg.galaxam.sirh.contrat.persistence.EmployeCPL;
import mg.galaxam.sirh.contrat.persistence.EmployeCompteBanque;
import mg.galaxam.sirh.contrat.persistence.EmployeConjoint;
import mg.galaxam.sirh.contrat.persistence.EmployeContactUrgence;
import mg.galaxam.sirh.contrat.persistence.EmployeEnfant;
import mg.galaxam.sirh.contrat.domain.EmployeValidationEngine;
import mg.galaxam.sirh.contrat.domain.MatriculeGeneratorEngine;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.stereotype.Service;

@Service
public class EmployeService {

    private final DataSource dataSource;
    private final EmployeValidationEngine validationEngine;
    private final MatriculeGeneratorEngine matriculeEngine;

    public EmployeService(DataSource dataSource, EmployeValidationEngine validationEngine,
                          MatriculeGeneratorEngine matriculeEngine) {
        this.dataSource = dataSource;
        this.validationEngine = validationEngine;
        this.matriculeEngine = matriculeEngine;
    }

    public Map<String, Long> getStats(String q) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return getStats(q, c);
        }
    }

    public Map<String, Long> getStats(String q, Connection c) throws Exception {
        String searchFilter = "";
        if (q != null && !q.isBlank()) {
            searchFilter = " AND (LOWER(matricule) LIKE ? OR LOWER(nom) LIKE ? "
                    + "OR LOWER(prenom) LIKE ? OR LOWER(cin_numero) LIKE ? OR LOWER(telephone) LIKE ?)";
        }

        String sql = "SELECT COUNT(*) AS total, "
                + "COUNT(*) FILTER (WHERE est_actif = TRUE) AS actifs, "
                + "COUNT(*) FILTER (WHERE est_actif = FALSE) AS inactifs "
                + "FROM employe WHERE TRUE" + searchFilter;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (!searchFilter.isEmpty()) {
                String search = "%" + q.trim().toLowerCase() + "%";
                for (int i = 1; i <= 5; i++) ps.setString(i, search);
            }
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Map.of("total", 0L, "actifs", 0L, "inactifs", 0L);
                return Map.of(
                    "total", rs.getLong("total"),
                    "actifs", rs.getLong("actifs"),
                    "inactifs", rs.getLong("inactifs")
                );
            }
        }
    }

    public PageDTO<EmployeCPL> getEmployes(
            String q, Boolean actif, Integer site, Integer departement, Integer service,
            String sort, String direction, int page, int size) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return getEmployes(q, actif, site, departement, service, sort, direction, page, size, c);
        }
    }

    public PageDTO<EmployeCPL> getEmployes(
            String q, Boolean actif, Integer site, Integer departement, Integer service,
            String sort, String direction, int page, int size, Connection c) throws Exception {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        long totalElements = EmployeCPL.compterEmployes(c, q, actif, site, departement, service);
        List<EmployeCPL> content = EmployeCPL.rechercherEmployes(
                c, q, actif, site, departement, service, sort, direction, safeSize, offset);
        return new PageDTO<>(content, totalElements, safePage, safeSize);
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
                            enf.setEstEtudiant(enfReq.isEstEtudiant());
                            enf.setDateFinScolarite(enfReq.getDateFinScolarite());
                            enf.setEstHandicape(enfReq.isEstHandicape());
                            enf.setCertificatFourni(enfReq.isCertificatFourni());
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
                NotificationRhService.creer(c, "EMPLOYE_CREE", "INFO",
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

                Employe ancienState = new Employe(emp);

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
                            enf.setEstEtudiant(enfReq.isEstEtudiant());
                            enf.setDateFinScolarite(enfReq.getDateFinScolarite());
                            enf.setEstHandicape(enfReq.isEstHandicape());
                            enf.setCertificatFourni(enfReq.isCertificatFourni());
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
                    EmployeCompteBanque.deletePrincipalByEmployeId(id, c);
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
        String sql = "WITH normalized AS ("
                + " SELECT btrim(matricule, E' \\t\\n\\r\\f\\013') AS normalized_value"
                + " FROM employe WHERE matricule IS NOT NULL"
                + "), parsed AS ("
                + " SELECT CASE WHEN normalized_value ~ '^EMP-[0-9]{4}-[0-9]{4,6}$'"
                + " THEN substring(normalized_value FROM '^EMP-[0-9]{4}-([0-9]{4,6})$')::numeric"
                + " ELSE (SELECT groups.captures[1]::numeric FROM regexp_matches(normalized_value, '([0-9]+)', 'g')"
                + " WITH ORDINALITY AS groups(captures, ordinal)"
                + " WHERE groups.captures[1]::numeric <= 9223372036854775807"
                + " ORDER BY groups.ordinal DESC LIMIT 1) END AS sequence_value"
                + " FROM normalized)"
                + " SELECT COALESCE(MAX(sequence_value), 0) FROM parsed";
        long maxSeq;
        try (PreparedStatement ps = c.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            rs.next();
            maxSeq = rs.getBigDecimal(1).longValueExact();
        }
        return matriculeEngine.genererMatriculeParDefaut(maxSeq + 1);
    }

    public PageDTO<Map<String, Object>> getOptions(String query, Boolean actif, int page, int size) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            PageDTO<EmployeCPL> employees = getEmployes(
                    query, actif, null, null, null, "MATRICULE", "ASC", page, size, c);
            List<Map<String, Object>> options = employees.getContent().stream().map(e -> Map.<String, Object>of(
                "idEmploye", e.getTuppleID(),
                "matricule", e.getMatricule() != null ? e.getMatricule() : "",
                "nom", e.getNom() != null ? e.getNom() : "",
                "prenom", e.getPrenom() != null ? e.getPrenom() : "",
                "nomComplet", e.getNomComplet() != null ? e.getNomComplet() : "",
                "photoUrl", e.getPhotoUrl() != null ? e.getPhotoUrl() : ""
            )).toList();
            return new PageDTO<>(options, employees.getTotalElements(),
                    employees.getCurrentPage(), employees.getPageSize());
        }
    }
}
