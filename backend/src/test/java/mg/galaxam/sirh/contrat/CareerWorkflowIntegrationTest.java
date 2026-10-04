package mg.galaxam.sirh.contrat;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Properties;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.contrat.api.dto.TransitionCarriereRequest;
import mg.galaxam.sirh.contrat.api.dto.TransitionCarriereRequest.EmbaucheInitialeRequest;
import mg.galaxam.sirh.contrat.api.dto.TransitionCarriereRequest.ValidationRequest;
import mg.galaxam.sirh.contrat.application.WorkflowRhService;
import mg.galaxam.sirh.contrat.persistence.WorkflowRhCPL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests d'intégration du cycle de vie des contrats, de la période d'essai et des workflows RH.
 */
class CareerWorkflowIntegrationTest {

    private Connection c;
    private int idEmploye;
    private int idUser;
    private final WorkflowRhService workflowRhService = new WorkflowRhService();

    @BeforeEach
    void setup() throws Exception {
        Properties properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application-test.properties")) {
            properties.load(input);
        }
        String url = properties.getProperty("spring.datasource.url");
        c = DriverManager.getConnection(
                url,
                properties.getProperty("spring.datasource.username"),
                properties.getProperty("spring.datasource.password"));
        c.setAutoCommit(false);

        idEmploye = CongeTestFixtureHelper.createEmploye(c, "CW" + System.nanoTime(), "RAZAFY", "Hery");
        idUser = CongeTestFixtureHelper.createAdminUser(c, "admin_career_" + System.nanoTime());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (c != null) {
            c.rollback();
            c.close();
        }
    }

    @Test
    void embauchePuisConfirmationEssaiFermeLaPeriodeProprement() throws Exception {
        // 1. Créer brouillon embauche avec période d'essai de 3 mois
        EmbaucheInitialeRequest req = new EmbaucheInitialeRequest();
        req.setIdTypeContrat(1); // CDI
        req.setIdTypeIntervenant(1); // Permanent
        req.setIdFiliale(1);
        req.setIdSite(1);
        req.setIdDepartement(1);
        req.setIdService(1);
        req.setIdFonction(1);
        req.setIdModeCalculPaie(1);
        req.setIdCategorieProfessionnelle(1);
        req.setSalaireBase(new BigDecimal("1500000.00"));
        req.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));
        req.setDureeEssaiMois(3);
        req.setDateFinEssai(Date.valueOf(LocalDate.of(2026, 4, 1)));

        WorkflowRhCPL brouillon = workflowRhService.creerBrouillonEmbauche(idEmploye, req, idUser, c);
        assertNotNull(brouillon);
        int idWf = brouillon.getIdWorkflow();
        int idContrat = brouillon.getIdContratCible();

        // 2. Valider l'embauche
        workflowRhService.validerWorkflow(idWf, new ValidationRequest(), idUser, c);

        // Vérifier que la période d'essai est EN_COURS et date_fin_essai est présente
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT statut, date_fin_prevue FROM contrat_periode_essai WHERE id_contrat = ?")) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("EN_COURS", rs.getString("statut"));
                assertEquals(Date.valueOf(LocalDate.of(2026, 4, 1)), rs.getDate("date_fin_prevue"));
            }
        }

        // 3. Confirmer l'essai directement à mi-parcours (ex: 2026-03-01)
        TransitionCarriereRequest reqConf = new TransitionCarriereRequest();
        reqConf.setCodeTypeEvenement("CONFIRMATION_ESSAI");
        reqConf.setIdContratSource(idContrat);
        reqConf.setDateEffet(Date.valueOf(LocalDate.of(2026, 3, 1)));

        workflowRhService.confirmerEssaiDirectement(idEmploye, reqConf, idUser, c);

        // Vérifier que la période d'essai est CONFIRMEE et date_fin_essai sur contrat est devenue NULL
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT statut, date_fin_reelle FROM contrat_periode_essai WHERE id_contrat = ?")) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("CONFIRMEE", rs.getString("statut"));
                assertEquals(Date.valueOf(LocalDate.of(2026, 3, 1)), rs.getDate("date_fin_reelle"));
            }
        }

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT date_fin_essai FROM contrat_travail WHERE id_contrat = ?")) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNull(rs.getDate("date_fin_essai"));
            }
        }
    }

    @Test
    void ruptureContratClotureLaPeriodeEssaiEnCoursAvecStatutRompu() throws Exception {
        // 1. Embauche initiale
        EmbaucheInitialeRequest req = new EmbaucheInitialeRequest();
        req.setIdTypeContrat(1);
        req.setIdTypeIntervenant(1);
        req.setIdFiliale(1);
        req.setIdSite(1);
        req.setIdDepartement(1);
        req.setIdService(1);
        req.setIdFonction(1);
        req.setIdModeCalculPaie(1);
        req.setIdCategorieProfessionnelle(1);
        req.setSalaireBase(new BigDecimal("1200000.00"));
        req.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));
        req.setDureeEssaiMois(3);
        req.setDateFinEssai(Date.valueOf(LocalDate.of(2026, 4, 1)));

        WorkflowRhCPL brouillon = workflowRhService.creerBrouillonEmbauche(idEmploye, req, idUser, c);
        workflowRhService.validerWorkflow(brouillon.getIdWorkflow(), new ValidationRequest(), idUser, c);
        int idContrat = brouillon.getIdContratCible();

        // 2. Rupture pendant la période d'essai au 2026-02-15
        TransitionCarriereRequest reqRupture = new TransitionCarriereRequest();
        reqRupture.setCodeTypeEvenement("RUPTURE_ESSAI");
        reqRupture.setIdContratSource(idContrat);
        reqRupture.setDateEffet(Date.valueOf(LocalDate.of(2026, 2, 15)));
        reqRupture.setMotif("Période d'essai non concluante");

        WorkflowRhCPL brouillonRupture = workflowRhService.creerBrouillonTransition(idEmploye, reqRupture, idUser, c);
        workflowRhService.validerWorkflow(brouillonRupture.getIdWorkflow(), new ValidationRequest(), idUser, c);

        // 3. Vérifications strictes :
        // Le contrat doit être ROMPU
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT s.code, ct.date_fin, ct.date_fin_essai FROM contrat_travail ct " +
                "JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat = ?")) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("ROMPU", rs.getString("code"));
                assertEquals(Date.valueOf(LocalDate.of(2026, 2, 15)), rs.getDate("date_fin"));
                assertNull(rs.getDate("date_fin_essai"));
            }
        }

        // La période d'essai doit être ROMPUE avec date_fin_reelle
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT statut, date_fin_reelle FROM contrat_periode_essai WHERE id_contrat = ?")) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("ROMPUE", rs.getString("statut"));
                assertEquals(Date.valueOf(LocalDate.of(2026, 2, 15)), rs.getDate("date_fin_reelle"));
            }
        }
    }

    @Test
    void annulationBrouillonEmbaucheNettoieLaPeriodeEssaiAssociee() throws Exception {
        // 1. Créer brouillon
        EmbaucheInitialeRequest req = new EmbaucheInitialeRequest();
        req.setIdTypeContrat(1);
        req.setIdTypeIntervenant(1);
        req.setIdFiliale(1);
        req.setIdSite(1);
        req.setIdDepartement(1);
        req.setIdService(1);
        req.setIdFonction(1);
        req.setIdModeCalculPaie(1);
        req.setIdCategorieProfessionnelle(1);
        req.setSalaireBase(new BigDecimal("1000000.00"));
        req.setDateDebut(Date.valueOf(LocalDate.of(2026, 2, 1)));
        req.setDureeEssaiMois(2);
        req.setDateFinEssai(Date.valueOf(LocalDate.of(2026, 4, 1)));

        WorkflowRhCPL brouillon = workflowRhService.creerBrouillonEmbauche(idEmploye, req, idUser, c);
        int idWf = brouillon.getIdWorkflow();
        int idContrat = brouillon.getIdContratCible();

        // 2. Annuler le brouillon
        ValidationRequest reqAnnul = new ValidationRequest();
        reqAnnul.setCommentaireValidation("Candidat a décliné l'offre");
        workflowRhService.annulerWorkflow(idWf, reqAnnul, idUser, c);

        // 3. Vérifier que la table contrat_periode_essai n'a conservé aucune ligne orpheline
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM contrat_periode_essai WHERE id_contrat = ?")) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
