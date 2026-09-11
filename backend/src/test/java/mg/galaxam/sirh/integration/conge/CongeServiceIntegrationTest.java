package mg.galaxam.sirh.integration.conge;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class CongeServiceIntegrationTest extends BaseIntegrationTest {

    private TestEmployeFixtures.CreatedEmploye emp;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        // Embauché il y a 2 ans pour valider l'ancienneté légale (Art. 84 : 12 mois)
        LocalDate dateEmbauche = LocalDate.now().minusYears(2);
        emp = employeFixtures.createActiveEmploye("EMP020", "RAVOAVIHASINA", "Fanja", 1, dateEmbauche);
    }

    private int resolveTypeCongeId(String code) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_type_conge FROM ref_type_conge WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("Type de congé non trouvé : " + code);
    }

    @Test
    @DisplayName("API Congé : Demande nominale et validation FIFO via endpoints REST")
    public void testCreerEtValiderDemande_FIFO() throws Exception {
        // 1. Créditer 20 jours de congés
        employeFixtures.crediterSoldeConge(emp.idEmploye, 20.0);

        // 2. Créer une demande de 5 jours de congé annuel via POST /conges/demandes
        LocalDate debut = LocalDate.now().plusDays(20);
        LocalDate fin = debut.plusDays(4); // 5 jours calendaires

        Map<String, Object> req = new HashMap<>();
        req.put("idEmploye", emp.idEmploye);
        req.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        req.put("dateDebut", debut.toString());
        req.put("dateFin", fin.toString());
        req.put("commentaire", "Congé annuel d'été");

        String resStr = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resStr);
        int idDemande = json.get("idDemandeConge").asInt();
        assertTrue(idDemande > 0);

        // Vérifier statut EN_ATTENTE en base
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT s.code FROM demande_conge dc JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge WHERE dc.id_demande_conge = ?")) {
            ps.setInt(1, idDemande);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("EN_ATTENTE", rs.getString("code"));
            }
        }

        // 3. Valider la demande via POST /conges/demandes/{id}/valider
        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk());

        // 4. Vérifier la création d'un mouvement de type PRISE dans mouvement_solde_conge
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT type_mouvement, nb_jours FROM mouvement_solde_conge WHERE id_employe = ? AND id_demande_conge = ?")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idDemande);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Un mouvement de prise doit avoir été enregistré pour cette demande");
                assertEquals("PRISE", rs.getString("type_mouvement"));
                assertEquals(-5.0, rs.getDouble("nb_jours"), 0.01, "La prise de congé doit être un mouvement négatif (-5j)");
            }
        }
    }

    @Test
    @DisplayName("API Congé : Scission automatique arrêt maladie > 30j (maintien + sans solde) via REST")
    public void testScissionAutomatiqueArretMaladie() throws Exception {
        LocalDate debut = LocalDate.now().plusDays(2);
        LocalDate fin = debut.plusDays(44); // 45 jours calendaires (> 30j)

        Map<String, Object> req = new HashMap<>();
        req.put("idEmploye", emp.idEmploye);
        req.put("idTypeConge", resolveTypeCongeId("MALADIE"));
        req.put("dateDebut", debut.toString());
        req.put("dateFin", fin.toString());
        req.put("justificatifFourni", true);
        req.put("commentaire", "Hospitalisation et convalescence");

        mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated());

        // Vérifier qu'il existe 2 demandes en base pour cet employé :
        // 1ère demande : MALADIE de 30 jours (du debut au debut + 29 jours)
        // 2ème demande : MALADIE_SANS_SOLDE pour le reliquat (15 jours)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT dc.id_demande_conge, t.code, dc.date_debut, dc.date_fin, dc.nb_jours_decomptes " +
                     "FROM demande_conge dc " +
                     "JOIN ref_type_conge t ON dc.id_type_conge = t.id_type_conge " +
                     "WHERE dc.id_employe = ? ORDER BY dc.date_debut ASC")) {
            ps.setInt(1, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "La 1ère tranche (30j) doit exister");
                assertEquals("MALADIE", rs.getString("code"));
                assertEquals(30.0, rs.getDouble("nb_jours_decomptes"), 0.01);

                assertTrue(rs.next(), "La 2ème tranche (15j sans solde CNaPS) doit avoir été scindée");
                assertEquals("MALADIE_SANS_SOLDE", rs.getString("code"));
                assertEquals(15.0, rs.getDouble("nb_jours_decomptes"), 0.01);
            }
        }
    }

    @Test
    @DisplayName("API Congé : Refus d'une demande avec motif via POST /conges/demandes/{id}/refuser")
    public void testRefuserDemandeConge() throws Exception {
        employeFixtures.crediterSoldeConge(emp.idEmploye, 10.0);

        LocalDate debut = LocalDate.now().plusDays(20);
        LocalDate fin = debut.plusDays(2);

        Map<String, Object> req = new HashMap<>();
        req.put("idEmploye", emp.idEmploye);
        req.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        req.put("dateDebut", debut.toString());
        req.put("dateFin", fin.toString());

        String resStr = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idDemande = objectMapper.readTree(resStr).get("idDemandeConge").asInt();

        String motifRefus = "Nécessité de service impérieuse";
        Map<String, String> refusBody = new HashMap<>();
        refusBody.put("motifRefus", motifRefus);

        mockMvc.perform(post("/conges/demandes/" + idDemande + "/refuser")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(refusBody)))
                .andExpect(status().isOk());

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT s.code, dc.motif_refus FROM demande_conge dc " +
                     "JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge " +
                     "WHERE dc.id_demande_conge = ?")) {
            ps.setInt(1, idDemande);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("REFUSE", rs.getString("code"));
                assertEquals(motifRefus, rs.getString("motif_refus"));
            }
        }
    }
}
