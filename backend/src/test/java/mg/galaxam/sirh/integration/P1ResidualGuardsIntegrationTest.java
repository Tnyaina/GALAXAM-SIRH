package mg.galaxam.sirh.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.common.ApiErrorResponse;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculRequest;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.config.GlobalExceptionHandler;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import mg.galaxam.sirh.model.paie.service.StcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("Tests de clôture P1 — P1-03, P1-05, P1-06")
class P1ResidualGuardsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private StcService stcService;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    private String adminToken;
    private CreatedEmploye testEmp;

    @BeforeEach
    void setup() {
        databaseCleaner.cleanTransactionalData();
        adminToken = getAdminToken();
        testEmp = employeFixtures.createActiveEmploye("EMP-P1-001", "TEST", "P1", 1, LocalDate.now().minusYears(2));
    }

    // =========================================================================
    // P1-03 : Temporalité & Rejet des Fallbacks
    // =========================================================================

    @Test
    @DisplayName("P1-03: La saisie de pointage avec idSite <= 0 est rejetée (pas de fallback LIMIT 1 / MIN)")
    void testPointageRejectsInvalidSiteId() throws Exception {
        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(testEmp.idEmploye);
        dto.setIdSite(0); // Site invalide
        dto.setDatePointage(LocalDate.now().toString());
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:00");

        mockMvc.perform(post("/pointages/saisie/batch")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dto))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    @Test
    @DisplayName("P1-03: STC bloque avec LEGAL_CONFIG_MISSING si aucune config légale (SME/SMIG) ne couvre la date")
    void testStcBlocksWhenLegalConfigMissing() {
        // Employé embauché en 2020 avec carrière valide en 2020
        CreatedEmploye emp2020 = employeFixtures.createActiveEmploye("EMP-STC-20", "STC", "Ancien", 1, LocalDate.of(2020, 1, 1));
        // Période de paie couvrant la date de départ
        employeFixtures.createPeriodePaie("P-2020-06", LocalDate.of(2020, 6, 1), LocalDate.of(2020, 6, 30), "EN_COURS");

        StcCalculRequest req = new StcCalculRequest();
        req.setIdEmploye(emp2020.idEmploye);
        // Date de départ en 2020 où aucune config légale SME n'est configurée en base
        req.setDateDepart(Date.valueOf(LocalDate.of(2020, 6, 15)));
        req.setMotifRupture("DEMISSION");

        try (Connection c = dataSource.getConnection()) {
            var ex = assertThrows(mg.galaxam.sirh.common.exception.SirhBusinessException.class, () -> {
                stcService.simulerStc(req, c);
            });
            assertEquals(SirhErrorCode.LEGAL_CONFIG_MISSING, ex.getErrorCode());
            assertTrue(ex.getMessage().contains("LEGAL_REVIEW_REQUIRED"));
        } catch (Exception e) {
            fail("Exception inattendue: " + e.getMessage());
        }
    }

    // =========================================================================
    // P1-05 : IDOR Photos RH & Dépréciation
    // =========================================================================

    @Test
    @DisplayName("P1-05: L'ancien endpoint de consultation /employes/photos/{filename} renvoie 410 Gone")
    void testDeprecatedPhotoEndpointReturns410() throws Exception {
        mockMvc.perform(get("/employes/photos/secret_avatar.png")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ENDPOINT_DEPRECATED"));
    }

    @Test
    @DisplayName("P1-05: L'ancien endpoint d'upload /employes/upload-photo renvoie 410 Gone")
    void testDeprecatedPhotoUploadReturns410() throws Exception {
        mockMvc.perform(post("/employes/upload-photo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ENDPOINT_DEPRECATED"));
    }

    @Test
    @DisplayName("P1-05: La consultation de photo pour un employé inexistant renvoie 404")
    void testPhotoForNonExistentEmployeReturns404() throws Exception {
        mockMvc.perform(get("/employes/999999/photo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(SirhErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    // =========================================================================
    // P1-06 : Contrat d'erreur SQLSTATE
    // =========================================================================

    @Test
    @DisplayName("P1-06: GlobalExceptionHandler convertit SQLSTATE 23505 en DATA_DUPLICATE (409)")
    void testSqlState23505TranslatesToDataDuplicate() {
        SQLException sqlEx = new SQLException("duplicate key value violates unique constraint", "23505");
        DataIntegrityViolationException dive = new DataIntegrityViolationException("Conflict", sqlEx);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/employes");

        ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleDataIntegrity(dive, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(SirhErrorCode.DATA_DUPLICATE.name(), response.getBody().getCode());
    }

    @Test
    @DisplayName("P1-06: GlobalExceptionHandler convertit SQLSTATE 23503 en DATA_REFERENCED (409)")
    void testSqlState23503TranslatesToDataReferenced() {
        SQLException sqlEx = new SQLException("foreign key constraint violation", "23503");
        DataIntegrityViolationException dive = new DataIntegrityViolationException("Conflict", sqlEx);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/sites/1");

        ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleDataIntegrity(dive, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(SirhErrorCode.DATA_REFERENCED.name(), response.getBody().getCode());
    }
}
