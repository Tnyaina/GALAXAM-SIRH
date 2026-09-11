package mg.galaxam.sirh.integration.contrat;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class WorkflowRhServiceIntegrationTest extends BaseIntegrationTest {

    private int idEmployeNu;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();

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
    @DisplayName("API Workflow RH : Création d'un brouillon d'embauche initiale (HTTP 201)")
    public void testCreerBrouillonEmbauche_Nominal() throws Exception {
        LocalDate dateDebut = LocalDate.now().plusDays(5);
        EmbaucheInitialeRequest req = buildEmbaucheRequest("CDI", dateDebut, null);

        String resStr = mockMvc.perform(post("/employes/" + idEmployeNu + "/workflows/embauche-initiale")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resStr);
        int idWf = json.get("idWorkflow").asInt();
        int idContrat = json.get("idContratCible").asInt();
        int idAff = json.get("idAffectationCible").asInt();
        assertEquals("BROUILLON", json.get("codeStatutWorkflow").asText());

        // Vérification en base
        try (Connection c = dataSource.getConnection()) {
            // Contrat en BROUILLON
            String sqlCt = "SELECT s.code FROM contrat_travail ct " +
                    "JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCt)) {
                ps.setInt(1, idContrat);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("BROUILLON", rs.getString("code"), "Le contrat créé doit être en statut BROUILLON");
                }
            }

            // Affectation en PLANIFIE
            String sqlAff = "SELECT s.code FROM employe_affectation_carriere a " +
                    "JOIN ref_statut_rh s ON a.id_statut_rh = s.id_statut_rh WHERE a.id_affectation = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlAff)) {
                ps.setInt(1, idAff);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("PLANIFIE", rs.getString("code"), "L'affectation créée doit être en statut PLANIFIE");
                }
            }
        }
    }

    @Test
    @DisplayName("API Workflow RH : Validation DAF d'un workflow d'embauche (HTTP 200)")
    public void testValiderWorkflow_EmbaucheInitiale() throws Exception {
        LocalDate dateDebut = LocalDate.now().minusDays(1);
        EmbaucheInitialeRequest req = buildEmbaucheRequest("CDI", dateDebut, null);

        String embRes = mockMvc.perform(post("/employes/" + idEmployeNu + "/workflows/embauche-initiale")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idWf = objectMapper.readTree(embRes).get("idWorkflow").asInt();
        int idContrat = objectMapper.readTree(embRes).get("idContratCible").asInt();
        int idAff = objectMapper.readTree(embRes).get("idAffectationCible").asInt();

        // Validation du workflow via l'API PUT
        ValiderWorkflowRequest valReq = new ValiderWorkflowRequest();
        valReq.setCommentaireValidation("Validation DAF conforme");

        mockMvc.perform(put("/workflows/" + idWf + "/valider")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(valReq)))
                .andExpect(status().isOk());

        // Vérification directe en base
        try (Connection c = dataSource.getConnection()) {
            // Contrat devenu ACTIF
            String sqlCt = "SELECT s.code FROM contrat_travail ct " +
                    "JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCt)) {
                ps.setInt(1, idContrat);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("ACTIF", rs.getString("code"), "Le contrat validé doit être ACTIF");
                }
            }

            // Affectation devenue ACTIF
            String sqlAff = "SELECT s.code FROM employe_affectation_carriere a " +
                    "JOIN ref_statut_rh s ON a.id_statut_rh = s.id_statut_rh WHERE a.id_affectation = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlAff)) {
                ps.setInt(1, idAff);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("ACTIF", rs.getString("code"), "L'affectation validée doit être ACTIF");
                }
            }
        }
    }

    @Test
    @DisplayName("API Workflow RH : Rejet HTTP 400 si l'employé possède déjà un contrat ACTIF")
    public void testRejetEmbaucheSiContratActifExistant() throws Exception {
        // Créer un employé déjà actif avec contrat
        TestEmployeFixtures.CreatedEmploye empActif = employeFixtures.createActiveEmploye(
                "EMP098", "RABETAFIKA", "Zo", 1, LocalDate.now().minusMonths(3));

        EmbaucheInitialeRequest req = buildEmbaucheRequest("CDI", LocalDate.now(), null);

        mockMvc.perform(post("/employes/" + empActif.idEmploye + "/workflows/embauche-initiale")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("possède déjà un contrat ACTIF")));
    }

    @Test
    @DisplayName("API Workflow RH : Rejet HTTP 400 lors de la tentative de double validation d'un workflow")
    public void testInterdictionDoubleValidation() throws Exception {
        LocalDate dateDebut = LocalDate.now().minusDays(1);
        EmbaucheInitialeRequest req = buildEmbaucheRequest("CDI", dateDebut, null);

        String embRes = mockMvc.perform(post("/employes/" + idEmployeNu + "/workflows/embauche-initiale")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idWf = objectMapper.readTree(embRes).get("idWorkflow").asInt();

        ValiderWorkflowRequest valReq = new ValiderWorkflowRequest();
        valReq.setCommentaireValidation("Première validation");

        // 1ère validation -> 200 OK
        mockMvc.perform(put("/workflows/" + idWf + "/valider")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(valReq)))
                .andExpect(status().isOk());

        // 2ème validation -> 400 Bad Request
        mockMvc.perform(put("/workflows/" + idWf + "/valider")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(valReq)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Seul un workflow BROUILLON peut être validé")));
    }
}
