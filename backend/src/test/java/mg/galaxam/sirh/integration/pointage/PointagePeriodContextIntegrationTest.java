package mg.galaxam.sirh.integration.pointage;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PointagePeriodContextIntegrationTest extends BaseIntegrationTest {

    @BeforeEach
    void clean() {
        databaseCleaner.cleanTransactionalData();
    }

    @Test
    void refuseUneRechercheSansPeriodeNiDate() throws Exception {
        mockMvc.perform(get("/pointages/anomalies")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("POINTAGE_PERIOD_CONTEXT_REQUIRED")));
    }

    @Test
    void refuseUneDateCouverteParPlusieursPeriodes() throws Exception {
        employeFixtures.createPeriodePaie(
                "PER-TEMP-A", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "EN_COURS");
        employeFixtures.createPeriodePaie(
                "PER-TEMP-B", LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 14), "EN_COURS");

        mockMvc.perform(get("/pointages/anomalies")
                        .param("datePointage", "2026-01-20")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("POINTAGE_PERIOD_CONTEXT_AMBIGUOUS")));
    }
}
