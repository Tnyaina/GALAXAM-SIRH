package mg.galaxam.sirh.contrat.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import mg.galaxam.sirh.contrat.api.dto.TransitionCarriereRequest.EmbaucheInitialeRequest;
import mg.galaxam.sirh.contrat.api.dto.TransitionCarriereRequest.ValidationRequest;
import mg.galaxam.sirh.contrat.domain.EmployeValidationEngine;
import mg.galaxam.sirh.contrat.domain.MatriculeGeneratorEngine;
import mg.galaxam.sirh.contrat.persistence.EmployeCPL;
import mg.galaxam.sirh.contrat.persistence.WorkflowRhCPL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Fixture persistante manuelle. Jamais exécutée par un mvn test ordinaire.
 *
 * Depuis la racine du projet, avec la base DEV du docker-compose.dev.yml :
 * docker exec galaxam-sirh-local-backend-1 sh -lc 'GALAXAM_AGENT_B_DB_URL=jdbc:postgresql://postgres:5432/sirh_galaxam GALAXAM_AGENT_B_DB_USER="$DATABASE_USERNAME" GALAXAM_AGENT_B_DB_PASSWORD="$DATABASE_PASSWORD" GALAXAM_AGENT_B_DB_NAME=sirh_galaxam GALAXAM_AGENT_B_CONFIRM=CREATE_0595 mvn -q -Dtest=ManualAgentB0595FixtureTest -Dmanual.agentB0595=true test'
 *
 * Le nom attendu doit correspondre à current_database() de la connexion DEV.
 * La fiche est créée par EmployeService ; le CDI et l'affectation sont créés
 * et validés par WorkflowRhService, comme dans le parcours applicatif.
 */
@EnabledIfSystemProperty(named = "manual.agentB0595", matches = "true")
class ManualAgentB0595FixtureTest {
    private static final String MATRICULE = "0595";
    private static final Date DATE_DEBUT = Date.valueOf("2025-01-01");

    @Test
    void creerAgentBEnBaseDevUniquementSurActivationExplicite() throws Exception {
        assertEquals("CREATE_0595", requiredEnv("GALAXAM_AGENT_B_CONFIRM"),
                "Confirmation explicite requise pour une écriture persistante.");
        String expectedDatabase = requiredEnv("GALAXAM_AGENT_B_DB_NAME");
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(requiredEnv("GALAXAM_AGENT_B_DB_URL"));
        source.setUser(requiredEnv("GALAXAM_AGENT_B_DB_USER"));
        source.setPassword(requiredEnv("GALAXAM_AGENT_B_DB_PASSWORD"));

        try (Connection c = source.getConnection()) {
            assertEquals(expectedDatabase, scalarString(c, "SELECT current_database()"),
                    "La connexion ne pointe pas sur la base DEV attendue.");
            int existing = employeeId(c);
            if (existing != 0 && hasContract(c, existing)) {
                verifyResult(c, existing);
                return; // Réexécution : le fixture complet existe déjà.
            }
            if (existing != 0) {
                assertEquals("AGENT", scalarString(c, "SELECT nom FROM employe WHERE id_employe = " + existing));
                assertEquals("B", scalarString(c, "SELECT prenom FROM employe WHERE id_employe = " + existing));
                assertEquals("2000-01-01", scalarString(c,
                        "SELECT date_naissance::text FROM employe WHERE id_employe = " + existing));
                assertEquals("000000000595", scalarString(c,
                        "SELECT cin_numero FROM employe WHERE id_employe = " + existing));
            }
        }

        EmployeService employes = new EmployeService(source, new EmployeValidationEngine(),
                new MatriculeGeneratorEngine());
        int employeeId;
        try (Connection c = source.getConnection()) {
            employeeId = employeeId(c);
        }
        if (employeeId == 0) {
            EmployeCPL created = employes.creerEmploye(employeeRequest());
            assertNotNull(created);
            employeeId = created.getIdEmploye();
        }

        WorkflowRhService workflows = new WorkflowRhService();
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                EmbaucheInitialeRequest request = hiringRequest(c);
                WorkflowRhCPL draft = workflows.creerBrouillonEmbauche(employeeId, request, null, c);
                assertNotNull(draft);
                ValidationRequest validation = new ValidationRequest();
                validation.setCommentaireValidation("Fixture DEV AGENT B 0595");
                workflows.validerWorkflow(draft.getIdWorkflow(), validation, null, c);
                c.commit();
            } catch (Exception | AssertionError error) {
                c.rollback();
                throw error;
            }
        }
        try (Connection c = source.getConnection()) {
            verifyResult(c, employeeId);
        }
    }

    private static EmployeRequest employeeRequest() {
        EmployeRequest request = new EmployeRequest();
        request.setMatricule(MATRICULE);
        request.setNom("AGENT");
        request.setPrenom("B");
        request.setSexe("M"); // Donnée fictive obligatoire.
        request.setDateNaissance(Date.valueOf("2000-01-01"));
        request.setLieuNaissance("Antananarivo (fictif)");
        request.setCinNumero("000000000595"); // CIN fictive de 12 chiffres.
        request.setCinDateDelivrance(Date.valueOf("2020-01-01"));
        request.setCinLieuDelivrance("Antananarivo (fictif)");
        request.setSituationFamiliale("Celibataire");
        request.setAdresse("Adresse fictive - Antananarivo");
        request.setVille("Antananarivo");
        request.setTelephone("0000000595");
        return request;
    }

    private static EmbaucheInitialeRequest hiringRequest(Connection c) throws Exception {
        EmbaucheInitialeRequest request = new EmbaucheInitialeRequest();
        request.setIdFiliale(ref(c, "SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX' AND est_actif"));
        request.setIdTypeContrat(ref(c, "SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI' AND est_actif"));
        request.setIdTypeIntervenant(ref(c, "SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT' AND est_actif"));
        request.setIdSite(ref(c, "SELECT id_site FROM ref_site WHERE code_site = 'TNR' AND est_actif AND id_filiale_rattachement = " + request.getIdFiliale()));
        request.setIdDepartement(ref(c, "SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG' AND est_actif"));
        request.setIdService(ref(c, "SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND est_actif AND id_departement = " + request.getIdDepartement()));
        request.setIdFonction(ref(c, "SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AGENT_B_D' AND est_actif"));
        request.setIdCategorieProfessionnelle(ref(c, "SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A' AND actif"));
        request.setIdModeCalculPaie(ref(c, "SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL' AND actif"));
        request.setNumeroContrat("GLX-CDI-0595-2025");
        request.setDateDebut(DATE_DEBUT);
        request.setDateSignature(DATE_DEBUT);
        request.setDateEffet(DATE_DEBUT);
        request.setDureeEssaiMois(0);
        request.setSalaireBase(new BigDecimal("450000.00"));
        request.setDescriptionAffectation("Fixture DEV AGENT B 0595");
        request.setMotif("Embauche initiale fictive pour la base DEV");
        return request;
    }

    private static void verifyResult(Connection c, int employeeId) throws Exception {
        String sql = "SELECT e.matricule,e.nom,e.prenom,e.date_naissance,"
                + "f.code_filiale,site.code_site,tc.code AS type_contrat,"
                + "ct.date_debut,a.salaire_base,mode.code AS mode_paie "
                + "FROM employe e JOIN contrat_travail ct ON ct.id_employe=e.id_employe "
                + "JOIN ref_statut_rh cs ON cs.id_statut_rh=ct.id_statut_rh AND cs.code='ACTIF' "
                + "JOIN ref_filiale f ON f.id_filiale=ct.id_filiale "
                + "JOIN ref_type_contrat tc ON tc.id_type_contrat=ct.id_type_contrat "
                + "JOIN employe_affectation_carriere a ON a.id_contrat=ct.id_contrat "
                + "JOIN ref_statut_rh asr ON asr.id_statut_rh=a.id_statut_rh AND asr.code='ACTIF' "
                + "JOIN ref_site site ON site.id_site=a.id_site "
                + "JOIN ref_mode_calcul_paie mode ON mode.id_mode_calcul_paie=a.id_mode_calcul_paie "
                + "WHERE e.id_employe=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new AssertionError("Contrat et affectation actifs introuvables pour 0595.");
                assertEquals("0595", rs.getString("matricule"));
                assertEquals("AGENT", rs.getString("nom"));
                assertEquals("B", rs.getString("prenom"));
                assertEquals(Date.valueOf("2000-01-01"), rs.getDate("date_naissance"));
                assertEquals("GLX", rs.getString("code_filiale"));
                assertEquals("TNR", rs.getString("code_site"));
                assertEquals("CDI", rs.getString("type_contrat"));
                assertEquals(DATE_DEBUT, rs.getDate("date_debut"));
                assertEquals(0, new BigDecimal("450000.00").compareTo(rs.getBigDecimal("salaire_base")));
                assertEquals("MENSUEL", rs.getString("mode_paie"));
                if (rs.next()) throw new AssertionError("Plusieurs contrats ou affectations actifs pour 0595.");
            }
        }
        assertEquals("0", scalarString(c, "SELECT count(*)::text FROM demande_conge WHERE id_employe = " + employeeId));
        assertEquals("0", scalarString(c, "SELECT count(*)::text FROM mouvement_solde_conge WHERE id_employe = " + employeeId));
    }

    private static int employeeId(Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT id_employe FROM employe WHERE matricule=?")) {
            ps.setString(1, MATRICULE);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    private static boolean hasContract(Connection c, int employeeId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM contrat_travail WHERE id_employe=? LIMIT 1")) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static int ref(Connection c, String query) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new IllegalStateException("Référentiel DEV manquant : " + query);
            return rs.getInt(1);
        }
    }

    private static String scalarString(Connection c, String query) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new IllegalStateException("Aucun résultat : " + query);
            return rs.getString(1);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " est requis.");
        return value;
    }
}
