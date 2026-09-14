package mg.galaxam.sirh.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class PointageSecurityAndApiTest extends BaseIntegrationTest {

    private int idPeriode;
    private TestEmployeFixtures.CreatedEmploye emp;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        LocalDate d = LocalDate.now().minusDays(5);
        idPeriode = employeFixtures.createPeriodePaie("P-SEC", d, d.plusDays(10), "EN_COURS");
        emp = employeFixtures.createActiveEmploye("EMP030", "RAZAFY", "Haja", 1, d.minusMonths(1));

        // Insérer un pointage pour permettre la transmission
        try (java.sql.Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe, date_pointage, id_periode_paie, id_site, total_heures, id_statut_jour) " +
                    "VALUES (?, ?, ?, 1, 8.0, (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT'))")) {
                ps.setInt(1, emp.idEmploye);
                ps.setDate(2, java.sql.Date.valueOf(d));
                ps.setInt(3, idPeriode);
                ps.executeUpdate();
            }
            c.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Sécurité API : Requête non authentifiée rejetée (403 Forbidden)")
    public void testAccesAnonymeRejete() throws Exception {
        mockMvc.perform(get("/pointages/saisie")
                .param("idSite", "1")
                .param("date", LocalDate.now().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Agent de Pointage : Autorisé à consulter la grille et à transmettre à la DAF")
    public void testAgentPointage_AutoriseLectureEtTransmission() throws Exception {
        String tokenAgent = userFixtures.getPointageToken();

        // 1. Consultation de la grille
        mockMvc.perform(get("/pointages/saisie")
                .header("Authorization", "Bearer " + tokenAgent)
                .param("idSite", "1")
                .param("date", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isOk());

        // 2. Transmission DAF
        Map<String, Object> body = Map.of(
                "version", getPayrollPeriodVersion(idPeriode),
                "idSite", 1
        );
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/SUBMIT")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RBAC Agent de Pointage : STRICTEMENT INTERDIT de valider la DAF (403 Forbidden)")
    public void testAgentPointage_InterditDeValiderDaf() throws Exception {
        String tokenAgent = userFixtures.getPointageToken();

        Map<String, Object> body = Map.of("version", getPayrollPeriodVersion(idPeriode));

        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/APPROVE")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Agent de Pointage : STRICTEMENT INTERDIT de rejeter la DAF (403 Forbidden)")
    public void testAgentPointage_InterditDeRejeterDaf() throws Exception {
        String tokenAgent = userFixtures.getPointageToken();

        Map<String, Object> body = Map.of(
                "version", getPayrollPeriodVersion(idPeriode),
                "motif", "Tentative non autorisée"
        );

        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/REJECT")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC DAF / Administrateur : Autorisé à valider et rejeter la période de paie")
    public void testAdmin_AutoriseValiderEtRejeterDaf() throws Exception {
        String tokenAdmin = userFixtures.getAdminToken();

        // 1. Transmission préalable, puis rejet par la DAF
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/SUBMIT")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("version", getPayrollPeriodVersion(idPeriode), "idSite", 1))))
                .andExpect(status().isOk());

        Map<String, Object> bodyRejet = Map.of(
                "version", getPayrollPeriodVersion(idPeriode),
                "motif", "À corriger par l'agent"
        );
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/REJECT")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyRejet)))
                .andExpect(status().isOk());

        // 2. Nouvelle transmission, puis validation par la DAF
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/SUBMIT")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("version", getPayrollPeriodVersion(idPeriode), "idSite", 1))))
                .andExpect(status().isOk());

        Map<String, Object> bodyVal = Map.of("version", getPayrollPeriodVersion(idPeriode));
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/APPROVE")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyVal)))
                .andExpect(status().isOk());
    }
}
