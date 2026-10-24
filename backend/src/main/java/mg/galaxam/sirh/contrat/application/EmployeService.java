package mg.galaxam.sirh.contrat.application;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
                verrouillerAllocateurMatricule(c);
                // 2. Gestion et génération automatique du matricule si non renseigné
                if (req.getMatricule() == null || req.getMatricule().trim().isEmpty() || "AUTO".equalsIgnoreCase(req.getMatricule().trim())) {
                    req.setMatricule(calculerProchainMatricule(c));
                }

                // 3. Employé principal
                Employe emp = new Employe();
                copierChampsEmploye(emp, req);

                emp.save(c);
                Integer empId = emp.getIdEmploye();

                // 4. Conjoint actuel facultatif
                appliquerConjoint(empId, req, c);

                // 5–6. Enfants et contacts d'urgence
                appliquerEnfants(empId, req, c);
                appliquerContactsUrgence(empId, req, c);

                // 7. Compte bancaire facultatif et unique
                appliquerCompteBanque(empId, req, c);

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

                copierChampsEmploye(emp, req);

                emp.update(c);

                // Conjoint : absence du champ = conservation ; suppression explicite uniquement.
                appliquerConjoint(id, req, c);

                // Les listes absentes ou vides n'effacent aucune ligne ; seules les suppressions explicites le font.
                appliquerEnfants(id, req, c);
                appliquerContactsUrgence(id, req, c);

                appliquerCompteBanque(id, req, c);

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

    private static void copierChampsEmploye(Employe employe, EmployeRequest req) {
        employe.setMatricule(req.getMatricule());
        employe.setNom(req.getNom());
        employe.setPrenom(req.getPrenom());
        employe.setSexe(req.getSexe());
        employe.setDateNaissance(req.getDateNaissance());
        employe.setLieuNaissance(req.getLieuNaissance());
        employe.setNationalite(req.getNationalite());
        employe.setPereNomPrenom(req.getPereNomPrenom());
        employe.setMereNomPrenom(req.getMereNomPrenom());
        employe.setSituationFamiliale(req.getSituationFamiliale());

        employe.setCinNumero(req.getCinNumero());
        employe.setCinDateDelivrance(req.getCinDateDelivrance());
        employe.setCinLieuDelivrance(req.getCinLieuDelivrance());
        employe.setCinDuplicataDate(req.getCinDuplicataDate());
        employe.setCinDuplicataLieu(req.getCinDuplicataLieu());

        employe.setPermisConduireNumero(req.getPermisConduireNumero());
        employe.setPermisConduireDateDelivrance(req.getPermisConduireDateDelivrance());
        employe.setPermisConduireLieuDelivrance(req.getPermisConduireLieuDelivrance());

        employe.setAdresse(req.getAdresse());
        employe.setVille(req.getVille());
        employe.setTelephone(req.getTelephone());
        employe.setEmail(req.getEmail());

        employe.setNumAffiliationCnaps(req.getNumAffiliationCnaps());
        employe.setPhotoUrl(req.getPhotoUrl());
        employe.setDateDeclaration(req.getDateDeclaration());
    }

    void appliquerCompteBanque(int idEmploye, EmployeRequest req, Connection c) throws Exception {
        EmployeRequest.CompteBanqueRequest donnees = req.getCompteBanquePrincipal();
        boolean renseigne = donnees != null && (donnees.getIdBanque() != null
                || !estVide(donnees.getNumCompteRib()) || !estVide(donnees.getAgence())
                || !estVide(donnees.getTitulaireCompte()));
        if (req.isSupprimerCompteBanque()) {
            if (renseigne || donnees != null && donnees.getIdCompteBanque() != null) {
                throw new IllegalArgumentException("Suppression et renseignement du compte bancaire incompatibles.");
            }
            EmployeCompteBanque existant = EmployeCompteBanque.findByEmployeId(idEmploye, c);
            if (existant != null) existant.delete(c);
            return;
        }
        if (!renseigne) {
            if (donnees != null && donnees.getIdCompteBanque() != null) {
                throw new IllegalArgumentException("Identifiant bancaire sans coordonnées bancaires.");
            }
            return;
        }
        if (donnees.getIdBanque() == null || estVide(donnees.getNumCompteRib())) {
            throw new IllegalArgumentException("La banque et le numéro de compte RIB sont obligatoires.");
        }
        EmployeCompteBanque compte = EmployeCompteBanque.findByEmployeId(idEmploye, c);
        if (donnees.getIdCompteBanque() != null && (donnees.getIdCompteBanque() <= 0
                || compte == null || !donnees.getIdCompteBanque().equals(compte.getIdCompteBanque()))) {
            throw new IllegalArgumentException("Compte bancaire introuvable pour cet employé.");
        }
        if (compte == null) {
            compte = new EmployeCompteBanque();
            compte.setIdEmploye(idEmploye);
        } else if (Objects.equals(compte.getIdBanque(), donnees.getIdBanque())
                && Objects.equals(compte.getAgence(), donnees.getAgence())
                && Objects.equals(compte.getNumCompteRib(), donnees.getNumCompteRib())
                && Objects.equals(compte.getTitulaireCompte(), donnees.getTitulaireCompte())) {
            return;
        }
        compte.setIdBanque(donnees.getIdBanque());
        compte.setAgence(donnees.getAgence());
        compte.setNumCompteRib(donnees.getNumCompteRib());
        compte.setTitulaireCompte(donnees.getTitulaireCompte());
        if (compte.getIdCompteBanque() == null) compte.save(c); else compte.update(c);
    }

    void appliquerEnfants(int idEmploye, EmployeRequest req, Connection c) throws Exception {
        Set<Integer> modifications = new HashSet<>();
        Set<Integer> suppressions = validerSuppressions(req.getEnfantsASupprimer(), "enfant");
        if (req.getEnfants() != null) {
            for (EmployeRequest.EnfantRequest donnees : req.getEnfants()) {
                if (donnees == null) throw new IllegalArgumentException("Enfant non renseigné.");
                Integer id = donnees.getIdEnfant();
                if (id != null && (id <= 0 || !modifications.add(id) || suppressions.contains(id))) {
                    throw new IllegalArgumentException("Identifiant d'enfant contradictoire ou répété.");
                }
            }
        }
        for (Integer id : suppressions) {
            EmployeEnfant enfant = new EmployeEnfant().rechercherParId(id, c);
            verifierProprietaire(enfant == null ? null : enfant.getIdEmploye(), idEmploye, "Enfant");
            enfant.delete(c);
        }
        if (req.getEnfants() == null) return;
        for (EmployeRequest.EnfantRequest donnees : req.getEnfants()) {
            Integer id = donnees.getIdEnfant();
            EmployeEnfant enfant = id == null ? new EmployeEnfant() : new EmployeEnfant().rechercherParId(id, c);
            if (id != null) verifierProprietaire(enfant == null ? null : enfant.getIdEmploye(), idEmploye, "Enfant");
            if (id == null) enfant.setIdEmploye(idEmploye);
            if (id != null && memesInformationsEnfant(enfant, donnees)) continue;
            enfant.setNom(donnees.getNom());
            enfant.setPrenom(donnees.getPrenom());
            enfant.setSexe(donnees.getSexe());
            enfant.setDateNaissance(donnees.getDateNaissance());
            enfant.setLieuNaissance(donnees.getLieuNaissance());
            enfant.setACharge(donnees.isACharge());
            enfant.setEstEtudiant(donnees.isEstEtudiant());
            enfant.setDateFinScolarite(donnees.getDateFinScolarite());
            enfant.setEstHandicape(donnees.isEstHandicape());
            enfant.setCertificatFourni(donnees.isCertificatFourni());
            if (id == null) enfant.save(c); else enfant.update(c);
        }
    }

    void appliquerContactsUrgence(int idEmploye, EmployeRequest req, Connection c) throws Exception {
        Set<Integer> modifications = new HashSet<>();
        Set<Integer> suppressions = validerSuppressions(req.getContactsUrgenceASupprimer(), "contact d'urgence");
        if (req.getContactsUrgence() != null) {
            for (EmployeRequest.ContactUrgenceRequest donnees : req.getContactsUrgence()) {
                if (donnees == null) throw new IllegalArgumentException("Contact d'urgence non renseigné.");
                Integer id = donnees.getIdContact();
                if (id != null && (id <= 0 || !modifications.add(id) || suppressions.contains(id))) {
                    throw new IllegalArgumentException("Identifiant de contact contradictoire ou répété.");
                }
            }
        }
        for (Integer id : suppressions) {
            EmployeContactUrgence contact = new EmployeContactUrgence().rechercherParId(id, c);
            verifierProprietaire(contact == null ? null : contact.getIdEmploye(), idEmploye, "Contact d'urgence");
            contact.delete(c);
        }
        if (req.getContactsUrgence() == null) return;
        for (EmployeRequest.ContactUrgenceRequest donnees : req.getContactsUrgence()) {
            Integer id = donnees.getIdContact();
            if (id == null && estVide(donnees.getNom()) && estVide(donnees.getPrenom())
                    && estVide(donnees.getTelephone()) && estVide(donnees.getAdresse())
                    && estVide(donnees.getSociete())) continue;
            EmployeContactUrgence contact = id == null ? new EmployeContactUrgence() : new EmployeContactUrgence().rechercherParId(id, c);
            if (id != null) verifierProprietaire(contact == null ? null : contact.getIdEmploye(), idEmploye, "Contact d'urgence");
            if (id == null) contact.setIdEmploye(idEmploye);
            if (id != null && memesInformationsContact(contact, donnees)) continue;
            contact.setNom(donnees.getNom());
            contact.setPrenom(donnees.getPrenom());
            contact.setLienParente(donnees.getLienParente());
            contact.setAdresse(donnees.getAdresse());
            contact.setTelephone(donnees.getTelephone());
            contact.setSociete(donnees.getSociete());
            if (id == null) contact.save(c); else contact.update(c);
        }
    }

    private static Set<Integer> validerSuppressions(List<Integer> ids, String objet) {
        Set<Integer> uniques = new HashSet<>();
        if (ids == null) return uniques;
        for (Integer id : ids) {
            if (id == null || id <= 0 || !uniques.add(id)) {
                throw new IllegalArgumentException("Identifiant de " + objet + " à supprimer invalide ou répété.");
            }
        }
        return uniques;
    }

    private static void verifierProprietaire(Integer proprietaire, int idEmploye, String objet) {
        if (!Objects.equals(proprietaire, idEmploye)) {
            throw new IllegalArgumentException(objet + " introuvable pour cet employé.");
        }
    }

    private static boolean memesInformationsEnfant(EmployeEnfant actuel, EmployeRequest.EnfantRequest demande) {
        return Objects.equals(actuel.getNom(), demande.getNom())
                && Objects.equals(actuel.getPrenom(), demande.getPrenom())
                && Objects.equals(actuel.getSexe(), demande.getSexe())
                && Objects.equals(actuel.getDateNaissance(), demande.getDateNaissance())
                && Objects.equals(actuel.getLieuNaissance(), demande.getLieuNaissance())
                && actuel.isACharge() == demande.isACharge()
                && actuel.isEstEtudiant() == demande.isEstEtudiant()
                && Objects.equals(actuel.getDateFinScolarite(), demande.getDateFinScolarite())
                && actuel.isEstHandicape() == demande.isEstHandicape()
                && actuel.isCertificatFourni() == demande.isCertificatFourni();
    }

    private static boolean memesInformationsContact(EmployeContactUrgence actuel, EmployeRequest.ContactUrgenceRequest demande) {
        EmployeContactUrgence normalise = new EmployeContactUrgence();
        normalise.setLienParente(demande.getLienParente());
        return Objects.equals(actuel.getNom(), demande.getNom())
                && Objects.equals(actuel.getPrenom(), demande.getPrenom())
                && Objects.equals(actuel.getLienParente(), normalise.getLienParente())
                && Objects.equals(actuel.getAdresse(), demande.getAdresse())
                && Objects.equals(actuel.getTelephone(), demande.getTelephone())
                && Objects.equals(actuel.getSociete(), demande.getSociete());
    }

    void appliquerConjoint(int idEmploye, EmployeRequest req, Connection c) throws Exception {
        EmployeRequest.ConjointRequest donnees = req.getConjoint();
        boolean renseigne = donnees != null && (
                !estVide(donnees.getNom()) || !estVide(donnees.getPrenom())
                || donnees.getDateNaissance() != null || !estVide(donnees.getLieuNaissance())
                || !estVide(donnees.getSociete()) || !estVide(donnees.getFonction()));
        if (req.isSupprimerConjoint()) {
            if (renseigne) {
                throw new IllegalArgumentException("Suppression et renseignement du conjoint incompatibles.");
            }
            EmployeConjoint.deleteByEmployeId(idEmploye, c);
            return;
        }
        if (!renseigne) {
            return;
        }
        if (estVide(donnees.getNom()) || estVide(donnees.getPrenom())) {
            throw new IllegalArgumentException("Le nom et le prénom du conjoint sont obligatoires.");
        }

        EmployeConjoint conjoint = EmployeConjoint.findByEmployeId(idEmploye, c);
        if (conjoint == null) {
            conjoint = new EmployeConjoint();
            conjoint.setIdEmploye(idEmploye);
            renseignerConjoint(conjoint, donnees);
            conjoint.save(c);
        } else if (!memesInformationsConjoint(conjoint, donnees)) {
            renseignerConjoint(conjoint, donnees);
            conjoint.update(c);
        }
    }

    private static boolean estVide(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    private static boolean memesInformationsConjoint(EmployeConjoint actuel, EmployeRequest.ConjointRequest demande) {
        return Objects.equals(actuel.getNom(), demande.getNom())
                && Objects.equals(actuel.getPrenom(), demande.getPrenom())
                && Objects.equals(actuel.getDateNaissance(), demande.getDateNaissance())
                && Objects.equals(actuel.getLieuNaissance(), demande.getLieuNaissance())
                && Objects.equals(actuel.getSociete(), demande.getSociete())
                && Objects.equals(actuel.getFonction(), demande.getFonction());
    }

    private static void renseignerConjoint(EmployeConjoint conjoint, EmployeRequest.ConjointRequest donnees) {
        conjoint.setNom(donnees.getNom());
        conjoint.setPrenom(donnees.getPrenom());
        conjoint.setDateNaissance(donnees.getDateNaissance());
        conjoint.setLieuNaissance(donnees.getLieuNaissance());
        conjoint.setSociete(donnees.getSociete());
        conjoint.setFonction(donnees.getFonction());
    }

    public EmployeCPL desactiverEmploye(int id, DesactivationRequest req) throws Exception {
        validationEngine.validerDesactivation(req);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id_employe FROM employe WHERE id_employe = ? FOR UPDATE")) {
                    ps.setInt(1, id);
                    try (var rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Employé introuvable.");
                    }
                }
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
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id_employe FROM employe WHERE id_employe = ? FOR UPDATE")) {
                    ps.setInt(1, id);
                    try (var rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Employé introuvable.");
                    }
                }
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
            // Cet aperçu ne réserve rien ; l'allocation définitive est sérialisée à la création.
            return calculerProchainMatricule(c);
        }
    }

    public String generateNextMatricule(Connection c) throws Exception {
        verifierTransactionMatricule(c);
        verrouillerAllocateurMatricule(c);
        return calculerProchainMatricule(c);
    }

    private void verrouillerAllocateurMatricule(Connection c) throws Exception {
        verifierTransactionMatricule(c);
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_xact_lock(46002, 1)")) {
            ps.execute();
        }
    }

    private void verifierTransactionMatricule(Connection c) throws Exception {
        if (c.getAutoCommit()) {
            throw new IllegalStateException("L'allocation d'un matricule exige une transaction active.");
        }
    }

    private String calculerProchainMatricule(Connection c) throws Exception {
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
