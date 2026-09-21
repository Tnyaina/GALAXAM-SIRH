package mg.galaxam.sirh.certification.conges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.oracle.LeaveIndependentOracle;

@DisplayName("CERTIFICATION CONGÉS INTÉGRATION — CYCLE COMPLET LEDGER & POSTGRESQL")
public class LeaveIntegrationTest extends BaseIntegrationTest {

    private TestEmployeFixtures.CreatedEmploye emp;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        LocalDate dateEmbauche = LocalDate.now().minusYears(2);
        emp = employeFixtures.createActiveEmploye("EMP003-CNG", "RASOA", "Marie", 1, dateEmbauche);
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
        throw new IllegalStateException("Type de congé introuvable : " + code);
    }

    @Test
    @DisplayName("Workflow Congé PostgreSQL : Demande -> Validation -> Mouvement Ledger == Oracle Indépendant")
    void testWorkflowCongePostgreSql() throws Exception {
        BigDecimal soldeInit = new BigDecimal("15.00");
        BigDecimal joursPris = new BigDecimal("2.00");
        BigDecimal baseContractuelle = new BigDecimal("450000.00");

        // Oracle indépendant
        LeaveIndependentOracle.LeaveLedgerResult oracle = LeaveIndependentOracle.evaluateLeavePeriod(
                soldeInit, BigDecimal.ZERO, joursPris, baseContractuelle, null
        );

        // 1. Initialiser le solde à 15 jours en base
        employeFixtures.crediterSoldeConge(emp.idEmploye, 15.0);

        // 2. Créer une demande de 2 jours ouvrables via l'API REST
        LocalDate debut = LocalDate.of(2026, 9, 14);
        LocalDate fin = LocalDate.of(2026, 9, 15);

        Map<String, Object> req = new HashMap<>();
        req.put("idEmploye", emp.idEmploye);
        req.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        req.put("dateDebut", debut.toString());
        req.put("dateFin", fin.toString());
        req.put("derogationDelaiPreavis", true);
        req.put("commentaire", "Congé de mi-septembre");

        String resStr = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resStr);
        int idDemande = json.get("idDemandeConge").asInt();
        assertTrue(idDemande > 0, "L'identifiant de demande doit être généré");

        // 3. Valider la demande par l'administrateur
        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk());

        // 4. Vérifier les écritures du ledger dans la table mouvement_solde_conge
        try (Connection c = dataSource.getConnection()) {
            String sqlMvt = "SELECT type_mouvement, nb_jours FROM mouvement_solde_conge WHERE id_employe = ? AND id_demande_conge = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlMvt)) {
                ps.setInt(1, emp.idEmploye);
                ps.setInt(2, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Un mouvement de ledger doit être créé suite à la validation");
                    assertEquals("PRISE", rs.getString("type_mouvement"), "Le mouvement est de type PRISE");
                    assertEquals(-oracle.joursPrisPeriode().doubleValue(), rs.getDouble("nb_jours"), 0.01,
                            "Le nombre de jours débités en base correspond à l'Oracle Indépendant (-2.0 jours)");
                }
            }

            // 5. Vérifier le solde résultant dans la vue officielle v_solde_conge_employe
            String sqlSolde = "SELECT solde_actuel FROM v_solde_conge_employe WHERE id_employe = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlSolde)) {
                ps.setInt(1, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Le solde doit être calculable via la vue de ledger");
                    double soldeBase = rs.getDouble("solde_actuel");
                    assertEquals(oracle.soldeFinal().doubleValue(), soldeBase, 0.01,
                            "Solde final PostgreSQL (13.0 j) == Oracle Indépendant (15.0 - 2.0 = 13.0 j)");
                }
            }
        }
    }
}
