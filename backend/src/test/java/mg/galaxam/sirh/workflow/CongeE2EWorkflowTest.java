package mg.galaxam.sirh.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.contrat.EmbaucheInitialeRequest;
import mg.galaxam.sirh.api.dto.contrat.ValiderWorkflowRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CongeE2EWorkflowTest extends BaseIntegrationTest {

    private String tokenAdmin;
    private String tokenAgent;
    private int idEmploye;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();
        tokenAdmin = userFixtures.getAdminToken();
        tokenAgent = userFixtures.getPointageToken();

        // 1. Créer un employé
        Map<String, Object> empReq = new HashMap<>();
        empReq.put("nom", "ANDRIANARIVO");
        empReq.put("prenom", "Faly");
        empReq.put("sexe", "M");
        empReq.put("dateNaissance", "1994-06-15");
        empReq.put("lieuNaissance", "Antananarivo");
        empReq.put("cinNumero", "101991000888");
        empReq.put("cinDateDelivrance", "2013-05-10");
        empReq.put("cinLieuDelivrance", "Antananarivo");
        empReq.put("adresse", "Lot II M 40 Ankadifotsy");
        empReq.put("telephone", "0341122334");
        empReq.put("email", "faly@galaxam.com");

        String empResStr = mockMvc.perform(post("/employes")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(empReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        idEmploye = objectMapper.readTree(empResStr).get("idEmploye").asInt();

        // 2. Activer l'employé via embauche initiale CDI avec > 12 mois d'ancienneté pour congé annuel
        LocalDate debutCdi = LocalDate.now().minusMonths(15);
        EmbaucheInitialeRequest embReq = new EmbaucheInitialeRequest();
        embReq.setIdFiliale(1);
        embReq.setIdTypeContrat(1); // CDI
        embReq.setIdTypeIntervenant(1); // PERMANENT
        embReq.setDateDebut(Date.valueOf(debutCdi));
        embReq.setNumeroContrat("CTR-CDI-FALY");
        embReq.setComptabiliseEffectif(true);
        embReq.setIdSite(1);
        embReq.setIdDepartement(1);
        embReq.setIdService(1);
        embReq.setIdFonction(1);
        embReq.setIdCategorieProfessionnelle(1);
        embReq.setIdModeCalculPaie(1);
        embReq.setSalaireBase(BigDecimal.valueOf(900000.00));
        embReq.setMotif("Embauche CDI Développeur");

        String embRes = mockMvc.perform(post("/employes/" + idEmploye + "/workflows/embauche-initiale")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(embReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idWorkflow = objectMapper.readTree(embRes).get("idWorkflow").asInt();

        ValiderWorkflowRequest valReq = new ValiderWorkflowRequest();
        valReq.setCommentaireValidation("Validation DAF Embauche CDI");

        mockMvc.perform(put("/workflows/" + idWorkflow + "/valider")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(valReq)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cycle Congé E2E : Crédit solde -> Demande congé -> Validation DAF -> Décompte FIFO -> Réflexion Pointage")
    public void testCycleCongeEtPointageComplet() throws Exception {
        // =========================================================================
        // ÉTAPE 1 : Créditer le solde de congé annuel (15 jours acquis)
        // =========================================================================
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                             "VALUES (?, 'ACQUISITION', 15.0, CURRENT_DATE, 'Acquisition cumulée initiale')")) {
            ps.setInt(1, idEmploye);
            ps.executeUpdate();
        }

        // Vérifier solde initial = 15.0
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(nb_jours), 0) as solde FROM mouvement_solde_conge WHERE id_employe = ?")) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(15.0, rs.getDouble("solde"), 0.001);
            }
        }

        // =========================================================================
        // ÉTAPE 2 : Dépôt de demande de congé annuel (du lundi au vendredi dans 3 semaines)
        // Respecte le délai de préavis >= 15 jours
        // =========================================================================
        LocalDate lundiConge = LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate vendrediConge = lundiConge.plusDays(4);

        int idTypeCongeAnnuel = 5;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) idTypeCongeAnnuel = rs.getInt(1);
            }
        }

        Map<String, Object> reqConge = new HashMap<>();
        reqConge.put("idEmploye", idEmploye);
        reqConge.put("idTypeConge", idTypeCongeAnnuel); // Congé annuel payé (décompté du solde)
        reqConge.put("dateDebut", lundiConge.toString());
        reqConge.put("dateFin", vendrediConge.toString());
        reqConge.put("commentaire", "Vacances annuelles prévues en famille");

        String resCongeStr = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqConge)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode congeNode = objectMapper.readTree(resCongeStr);
        int idDemande = congeNode.get("idDemandeConge").asInt();
        assertTrue(idDemande > 0);
        assertEquals(5.0, congeNode.get("nbJoursDecomptes").asDouble(), 0.001);
        assertEquals("EN_ATTENTE", congeNode.get("codeStatutDemande").asText());

        // =========================================================================
        // ÉTAPE 3 : Sécurité - Un Agent Pointage tente de valider la demande (403 interdit)
        // =========================================================================
        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isForbidden());

        // =========================================================================
        // ÉTAPE 4 : Validation officielle par le DAF (200 OK)
        // =========================================================================
        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        // =========================================================================
        // ÉTAPE 5 : Vérifications strictes en base de données
        // =========================================================================
        try (Connection c = dataSource.getConnection()) {
            // A. Statut de la demande = VALIDE
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT s.code FROM demande_conge d JOIN ref_statut_demande_conge s ON d.id_statut_demande_conge = s.id_statut_demande_conge WHERE d.id_demande_conge = ?")) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("VALIDE", rs.getString("code"));
                }
            }

            // B. Mouvement solde PRISE de -5.0 jours enregistré
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT type_mouvement, nb_jours FROM mouvement_solde_conge WHERE id_demande_conge = ?")) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Un mouvement de débit doit avoir été créé");
                    assertEquals("PRISE", rs.getString("type_mouvement"));
                    assertEquals(-5.0, rs.getDouble("nb_jours"), 0.001);
                }
            }

            // C. Solde total restant = 15.0 - 5.0 = 10.0
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(nb_jours), 0) as solde FROM mouvement_solde_conge WHERE id_employe = ?")) {
                ps.setInt(1, idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(10.0, rs.getDouble("solde"), 0.001);
                }
            }
        }

        // =========================================================================
        // ÉTAPE 6 : Réflexion automatique dans le Cockpit Pointage
        // =========================================================================
        String gridRes = mockMvc.perform(get("/pointages/saisie")
                .header("Authorization", "Bearer " + tokenAgent)
                .param("idSite", "1")
                .param("date", lundiConge.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode rootNode = objectMapper.readTree(gridRes);
        assertTrue(rootNode.has("grilleData"));
        JsonNode gridNode = rootNode.get("grilleData");
        assertTrue(gridNode.isArray());

        boolean trouveEmployeConge = false;
        for (JsonNode empRow : gridNode) {
            if (empRow.has("idEmploye") && empRow.get("idEmploye").asInt() == idEmploye) {
                trouveEmployeConge = true;
                assertEquals("CONGE", empRow.get("codeStatut").asText());
                assertEquals(0.0, empRow.get("totalHeures").asDouble(), 0.001);
                assertEquals("—", empRow.get("heureEntreeMatin").asText());
                assertEquals("—", empRow.get("heureSortieMatin").asText());
                break;
            }
        }
        assertTrue(trouveEmployeConge, "L'employé doit être listé dans la grille avec son statut CONGE actif");
    }
}
