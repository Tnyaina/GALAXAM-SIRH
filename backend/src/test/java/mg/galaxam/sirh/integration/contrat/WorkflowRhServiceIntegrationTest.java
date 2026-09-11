package mg.galaxam.sirh.integration.contrat;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.contrat.EmbaucheInitialeRequest;
import mg.galaxam.sirh.api.dto.contrat.ValiderWorkflowRequest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.model.contrat.WorkflowRhCPL;
import mg.galaxam.sirh.model.contrat.WorkflowRhService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class WorkflowRhServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WorkflowRhService workflowRhService;

    private int idEmployeNu;
    private int idAdminUser;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();
        idAdminUser = userFixtures.getAdminUserId();

        // Créer un employé sans contrat (employé nu)
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            String sqlEmp = "INSERT INTO employe (" +
                    "matricule, nom, prenom, sexe, date_naissance, lieu_naissance, " +
                    "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone, email, est_actif) " +
                    "VALUES ('EMP099', 'ANDRIANINA', 'Hery', 'M', '1995-05-15', 'Antananarivo', " +
                    "'101991000999', '2015-05-15', 'Antananarivo', 'Lot III Ankadifotsy', '0341122334', 'hery@galaxam.com', true) " +
                    "RETURNING id_employe";
            try (PreparedStatement ps = c.prepareStatement(sqlEmp);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idEmployeNu = rs.getInt(1);
                }
            }
            c.commit();
        }
    }

    private EmbaucheInitialeRequest buildEmbaucheRequest(String codeContrat, LocalDate dateDebut, LocalDate dateFin) {
        EmbaucheInitialeRequest req = new EmbaucheInitialeRequest();
        req.setIdFiliale(1);
        req.setIdSite(1);
        req.setIdDepartement(1);
        req.setIdService(1);
        req.setIdFonction(1);
        req.setIdCategorieProfessionnelle(1);
        req.setIdModeCalculPaie(1);
        req.setSalaireBase(BigDecimal.valueOf(650000.00));
        req.setDateDebut(Date.valueOf(dateDebut));
        if (dateFin != null) req.setDateFin(Date.valueOf(dateFin));
        req.setDateSignature(Date.valueOf(dateDebut.minusDays(2)));
        req.setNumeroContrat("CTR-TEST-" + System.currentTimeMillis() % 10000);
        req.setComptabiliseEffectif(true);
        req.setMotif("Embauche nouveau collaborateur");

        // Résoudre idTypeContrat et idTypeIntervenant depuis DB
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id_type_contrat FROM ref_type_contrat WHERE code = ?")) {
                ps.setString(1, codeContrat);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) req.setIdTypeContrat(rs.getInt(1));
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) req.setIdTypeIntervenant(rs.getInt(1));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return req;
    }

    @Test
    @DisplayName("Brouillon d'embauche : Création du contrat BROUILLON, affectation PLANIFIE et workflow BROUILLON")
    public void testCreerBrouillonEmbauche_Nominal() throws Exception {
        LocalDate dateDebut = LocalDate.now().plusDays(5);
        EmbaucheInitialeRequest req = buildEmbaucheRequest("CDI", dateDebut, null);

        WorkflowRhCPL wf;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            wf = workflowRhService.creerBrouillonEmbauche(idEmployeNu, req, idAdminUser, c);
            c.commit();
        }

        assertNotNull(wf);
        assertNotNull(wf.getIdWorkflow());
        assertEquals("BROUILLON", wf.getCodeStatutWorkflow());

        // Vérification en base
        try (Connection c = dataSource.getConnection()) {
            // Contrat en BROUILLON
            String sqlCt = "SELECT s.code FROM contrat_travail ct " +
                    "JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCt)) {
                ps.setInt(1, wf.getIdContratCible());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("BROUILLON", rs.getString("code"), "Le contrat créé doit être en statut BROUILLON");
                }
            }

            // Affectation en PLANIFIE
            String sqlAff = "SELECT s.code FROM employe_affectation_carriere a " +
                    "JOIN ref_statut_rh s ON a.id_statut_rh = s.id_statut_rh WHERE a.id_affectation = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlAff)) {
                ps.setInt(1, wf.getIdAffectationCible());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("PLANIFIE", rs.getString("code"), "L'affectation créée doit être en statut PLANIFIE");
                }
            }
        }
    }

    @Test
    @DisplayName("Validation Embauche : Le contrat devient ACTIF, l'affectation devient ACTIF, le workflow est validé")
    public void testValiderWorkflow_EmbaucheInitiale() throws Exception {
        LocalDate dateDebut = LocalDate.now().minusDays(1);
        EmbaucheInitialeRequest req = buildEmbaucheRequest("CDI", dateDebut, null);

        WorkflowRhCPL wf;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            wf = workflowRhService.creerBrouillonEmbauche(idEmployeNu, req, idAdminUser, c);
            c.commit();
        }

        // Validation du workflow
        ValiderWorkflowRequest valReq = new ValiderWorkflowRequest();
        valReq.setCommentaireValidation("Validation DAF conforme");

        WorkflowRhCPL wfValide;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            wfValide = workflowRhService.validerWorkflow(wf.getIdWorkflow(), valReq, idAdminUser, c);
            c.commit();
        }

        assertNotNull(wfValide);

        // Vérification directe en base
        try (Connection c = dataSource.getConnection()) {
            // Contrat devenu ACTIF
            String sqlCt = "SELECT s.code FROM contrat_travail ct " +
                    "JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCt)) {
                ps.setInt(1, wf.getIdContratCible());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("ACTIF", rs.getString("code"), "Le contrat validé doit être ACTIF");
                }
            }

            // Affectation devenue ACTIF
            String sqlAff = "SELECT s.code FROM employe_affectation_carriere a " +
                    "JOIN ref_statut_rh s ON a.id_statut_rh = s.id_statut_rh WHERE a.id_affectation = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlAff)) {
                ps.setInt(1, wf.getIdAffectationCible());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("ACTIF", rs.getString("code"), "L'affectation validée doit être ACTIF");
                }
            }
        }
    }

    @Test
    @DisplayName("Sécurité Métier : Interdiction de créer une embauche initiale si un contrat ACTIF existe déjà")
    public void testRejetEmbaucheSiContratActifExistant() throws Exception {
        // Créer un employé déjà actif avec contrat
        TestEmployeFixtures.CreatedEmploye empActif = employeFixtures.createActiveEmploye(
                "EMP098", "RABETAFIKA", "Zo", 1, LocalDate.now().minusMonths(3));

        EmbaucheInitialeRequest req = buildEmbaucheRequest("CDI", LocalDate.now(), null);

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                workflowRhService.creerBrouillonEmbauche(empActif.idEmploye, req, idAdminUser, c);
            });
            assertTrue(ex.getMessage().contains("possède déjà un contrat ACTIF"),
                    "L'embauche initiale doit être bloquée si un contrat actif existe déjà");
        }
    }

    @Test
    @DisplayName("Verrouillage transactionnel : Interdiction de valider un workflow déjà validé")
    public void testInterdictionDoubleValidation() throws Exception {
        LocalDate dateDebut = LocalDate.now().minusDays(1);
        EmbaucheInitialeRequest req = buildEmbaucheRequest("CDI", dateDebut, null);

        WorkflowRhCPL wf;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            wf = workflowRhService.creerBrouillonEmbauche(idEmployeNu, req, idAdminUser, c);
            c.commit();

            ValiderWorkflowRequest valReq = new ValiderWorkflowRequest();
            workflowRhService.validerWorkflow(wf.getIdWorkflow(), valReq, idAdminUser, c);
            c.commit();

            // Deuxième tentative de validation sur le même workflow
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                workflowRhService.validerWorkflow(wf.getIdWorkflow(), valReq, idAdminUser, c);
            });
            assertTrue(ex.getMessage().contains("Seul un workflow BROUILLON peut être validé"),
                    "La tentative de re-validation doit être rejetée par le contrôle FOR UPDATE de statut");
        }
    }
}
