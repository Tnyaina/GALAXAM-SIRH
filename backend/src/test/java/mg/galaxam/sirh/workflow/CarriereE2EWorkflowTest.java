package mg.galaxam.sirh.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.contrat.api.dto.EmbaucheInitialeRequest;
import mg.galaxam.sirh.contrat.api.dto.TransitionCarriereRequest;
import mg.galaxam.sirh.contrat.api.dto.ValiderWorkflowRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class CarriereE2EWorkflowTest extends BaseIntegrationTest {

    private String tokenAdmin;
    private int idEmploye;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();
        tokenAdmin = userFixtures.getAdminToken();

        // 1. Créer une fiche employé
        Map<String, Object> empReq = new java.util.HashMap<>();
        empReq.put("nom", "RAZAFIMANDIMBY");
        empReq.put("prenom", "Arnaud");
        empReq.put("sexe", "M");
        empReq.put("dateNaissance", "1994-04-12");
        empReq.put("lieuNaissance", "Antananarivo");
        empReq.put("cinNumero", "101991000777");
        empReq.put("cinDateDelivrance", "2014-04-12");
        empReq.put("cinLieuDelivrance", "Antananarivo");
        empReq.put("adresse", "Lot IVG 12 Antanimena");
        empReq.put("telephone", "0342233445");
        empReq.put("email", "arnaud@galaxam.com");

        String empResStr = mockMvc.perform(post("/employes")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(empReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode empNode = objectMapper.readTree(empResStr);
        idEmploye = empNode.get("idEmploye").asInt();
        assertTrue(idEmploye > 0);
    }

    @Test
    @DisplayName("Cycle Carrière E2E : Embauche CDD -> Validation -> Renouvellement CDD -> Validation -> Transformation CDI -> Validation")
    public void testCycleDeVieCarriereComplet() throws Exception {
        LocalDate debutCdd1 = LocalDate.now().minusMonths(12);
        LocalDate finCdd1 = debutCdd1.plusMonths(5);

        // =========================================================================
        // ÉTAPE 1 : Embauche Initiale en CDD (6 mois)
        // =========================================================================
        EmbaucheInitialeRequest embReq = new EmbaucheInitialeRequest();
        embReq.setIdFiliale(1);
        embReq.setIdTypeContrat(2); // CDD
        embReq.setIdTypeIntervenant(1); // PERMANENT
        embReq.setDateDebut(Date.valueOf(debutCdd1));
        embReq.setDateFin(Date.valueOf(finCdd1));
        embReq.setNumeroContrat("CTR-CDD-001");
        embReq.setComptabiliseEffectif(true);
        embReq.setIdSite(1);
        embReq.setIdDepartement(1);
        embReq.setIdService(1);
        embReq.setIdFonction(1);
        embReq.setIdCategorieProfessionnelle(1);
        embReq.setIdModeCalculPaie(1);
        embReq.setSalaireBase(BigDecimal.valueOf(800000.00));
        embReq.setMotif("Recrutement initial CDD projet");

        String embRes = mockMvc.perform(post("/employes/" + idEmploye + "/workflows/embauche-initiale")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(embReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idWfEmbauche = objectMapper.readTree(embRes).get("idWorkflow").asInt();
        int idCdd1 = objectMapper.readTree(embRes).get("idContratCible").asInt();
        int idAff1 = objectMapper.readTree(embRes).get("idAffectationCible").asInt();

        // Validation Embauche Initiale
        ValiderWorkflowRequest valReq = new ValiderWorkflowRequest();
        valReq.setCommentaireValidation("Validation DAF Embauche");

        mockMvc.perform(put("/workflows/" + idWfEmbauche + "/valider")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(valReq)))
                .andExpect(status().isOk());

        // Vérification : CDD 1 est ACTIF
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT s.code FROM contrat_travail ct JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat = ?")) {
            ps.setInt(1, idCdd1);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("ACTIF", rs.getString("code"));
            }
        }

        // =========================================================================
        // ÉTAPE 2 : Renouvellement CDD (Avenant / Nouveau CDD pour 6 mois de plus)
        // =========================================================================
        LocalDate debutCdd2 = finCdd1.plusDays(1);
        LocalDate finCdd2 = debutCdd2.plusMonths(6);

        TransitionCarriereRequest renReq = new TransitionCarriereRequest();
        renReq.setCodeTypeEvenement("RENOUVELLEMENT_CDD");
        renReq.setIdContratSource(idCdd1);
        renReq.setIdAffectationSource(idAff1);
        renReq.setIdFiliale(1);
        renReq.setIdTypeContrat(2); // CDD
        renReq.setIdTypeIntervenant(1);
        renReq.setDateDebut(Date.valueOf(debutCdd2));
        renReq.setDateFin(Date.valueOf(finCdd2));
        renReq.setDateEffet(Date.valueOf(debutCdd2));
        renReq.setNumeroContrat("CTR-CDD-002");
        renReq.setComptabiliseEffectif(true);
        renReq.setIdSite(1);
        renReq.setIdDepartement(1);
        renReq.setIdService(1);
        renReq.setIdFonction(1);
        renReq.setIdCategorieProfessionnelle(1);
        renReq.setIdModeCalculPaie(1);
        renReq.setSalaireBase(BigDecimal.valueOf(850000.00));
        renReq.setMotif("Renouvellement 1er terme CDD");

        String renRes = mockMvc.perform(post("/employes/" + idEmploye + "/workflows/transition")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(renReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idWfRenouv = objectMapper.readTree(renRes).get("idWorkflow").asInt();
        int idCdd2 = objectMapper.readTree(renRes).get("idContratCible").asInt();
        int idAff2 = objectMapper.readTree(renRes).get("idAffectationCible").asInt();

        // Validation Renouvellement
        mockMvc.perform(put("/workflows/" + idWfRenouv + "/valider")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(valReq)))
                .andExpect(status().isOk());

        // Vérification : CDD 1 est CLOTURE, CDD 2 est ACTIF
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_contrat, s.code FROM contrat_travail ct JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat IN (?, ?)")) {
            ps.setInt(1, idCdd1);
            ps.setInt(2, idCdd2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cId = rs.getInt("id_contrat");
                    String st = rs.getString("code");
                    if (cId == idCdd1) assertEquals("CLOTURE", st, "L'ancien CDD doit être clôturé");
                    if (cId == idCdd2) assertEquals("ACTIF", st, "Le nouveau CDD doit être actif");
                }
            }
        }

        // =========================================================================
        // ÉTAPE 3 : Transformation en CDI
        // =========================================================================
        LocalDate debutCdi = finCdd2.plusDays(1);

        TransitionCarriereRequest cdiReq = new TransitionCarriereRequest();
        cdiReq.setCodeTypeEvenement("TRANSFORMATION_CDI");
        cdiReq.setIdContratSource(idCdd2);
        cdiReq.setIdAffectationSource(idAff2);
        cdiReq.setIdFiliale(1);
        cdiReq.setIdTypeContrat(1); // CDI (dateFin = null)
        cdiReq.setIdTypeIntervenant(1);
        cdiReq.setDateDebut(Date.valueOf(debutCdi));
        cdiReq.setDateEffet(Date.valueOf(debutCdi));
        cdiReq.setNumeroContrat("CTR-CDI-001");
        cdiReq.setComptabiliseEffectif(true);
        cdiReq.setIdSite(1);
        cdiReq.setIdDepartement(1);
        cdiReq.setIdService(1);
        cdiReq.setIdFonction(1);
        cdiReq.setIdCategorieProfessionnelle(1);
        cdiReq.setIdModeCalculPaie(1);
        cdiReq.setSalaireBase(BigDecimal.valueOf(1000000.00));
        cdiReq.setMotif("Pérennisation du collaborateur en CDI");

        String cdiRes = mockMvc.perform(post("/employes/" + idEmploye + "/workflows/transition")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cdiReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idWfCdi = objectMapper.readTree(cdiRes).get("idWorkflow").asInt();
        int idCdi = objectMapper.readTree(cdiRes).get("idContratCible").asInt();

        // Validation Transformation CDI
        mockMvc.perform(put("/workflows/" + idWfCdi + "/valider")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(valReq)))
                .andExpect(status().isOk());

        // Vérification finale : CDD 2 est CLOTURE, CDI est ACTIF
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_contrat, s.code FROM contrat_travail ct JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat IN (?, ?)")) {
            ps.setInt(1, idCdd2);
            ps.setInt(2, idCdi);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cId = rs.getInt("id_contrat");
                    String st = rs.getString("code");
                    if (cId == idCdd2) assertEquals("CLOTURE", st);
                    if (cId == idCdi) assertEquals("ACTIF", st, "Le nouveau CDI doit être actif");
                }
            }
        }
    }
}
