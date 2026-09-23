package mg.galaxam.sirh.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class PointageE2EWorkflowTest extends BaseIntegrationTest {

    private TestEmployeFixtures.CreatedEmploye emp1;
    private TestEmployeFixtures.CreatedEmploye emp2;
    private int idPeriode;
    private String tokenAgent;
    private String tokenAdmin;
    private LocalDate lundiSemaine;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        tokenAgent = userFixtures.getPointageToken();
        tokenAdmin = userFixtures.getAdminToken();

        LocalDate dEmb = LocalDate.now().minusMonths(6);
        emp1 = employeFixtures.createActiveEmploye("EMP101", "RANDRIA", "Tojo", 1, dEmb);
        emp2 = employeFixtures.createActiveEmploye("EMP102", "RAKOTONIAINA", "Faly", 1, dEmb);

        // Période couvrant une semaine passée
        lundiSemaine = LocalDate.now().minusDays(14);
        while (lundiSemaine.getDayOfWeek() != DayOfWeek.MONDAY) {
            lundiSemaine = lundiSemaine.minusDays(1);
        }
        LocalDate dimancheSemaine = lundiSemaine.plusDays(6);
        idPeriode = employeFixtures.createPeriodePaie("P-E2E-PTG", lundiSemaine, dimancheSemaine, "EN_COURS");
    }

    @Test
    @DisplayName("Cockpit Pointage E2E : Saisie Batch -> Calcul Tout -> Transmettre DAF -> Rejet DAF -> Correction -> Validation DAF -> Gel")
    public void testCycleCompletCockpitPointage() throws Exception {
        // =========================================================================
        // ÉTAPE 1 : L'Agent de pointage saisit la semaine pour les 2 employés (40h chacun)
        // =========================================================================
        for (int dayOffset = 0; dayOffset < 5; dayOffset++) {
            LocalDate dateJour = lundiSemaine.plusDays(dayOffset);

            SaisiePointageJourDTO p1 = new SaisiePointageJourDTO();
            p1.setIdEmploye(emp1.idEmploye);
            p1.setIdSite(emp1.idSite);
            p1.setDatePointage(dateJour.toString());
            p1.setIdPeriodePaie(idPeriode);
            p1.setHeureEntreeMatin("08:00");
            p1.setHeureSortieMatin("12:00");
            p1.setHeureEntreeAm("13:00");
            p1.setHeureSortieAm("17:00");
            p1.setIndemniteRepasRecue(true);
            p1.setDeplacementMatin(true);
            p1.setDeplacementAm(true);

            SaisiePointageJourDTO p2 = new SaisiePointageJourDTO();
            p2.setIdEmploye(emp2.idEmploye);
            p2.setIdSite(emp2.idSite);
            p2.setDatePointage(dateJour.toString());
            p2.setIdPeriodePaie(idPeriode);
            p2.setHeureEntreeMatin("08:00");
            p2.setHeureSortieMatin("12:00");
            p2.setHeureEntreeAm("13:00");
            p2.setHeureSortieAm(dayOffset == 4 ? "18:00" : "17:00"); // 1h HS le vendredi pour emp2
            p2.setIndemniteRepasRecue(true);
            p2.setDeplacementMatin(true);
            p2.setDeplacementAm(true);

            mockMvc.perform(post("/pointages/saisie/batch")
                    .header("Authorization", "Bearer " + tokenAgent)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(List.of(p1, p2))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        // =========================================================================
        // ÉTAPE 2 : L'Agent lance le calcul global de la synthèse
        // =========================================================================
        mockMvc.perform(post("/pointages/synthese/calculer-tout")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("idPeriodePaie", idPeriode, "idSite", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // =========================================================================
        // ÉTAPE 3 : L'Agent transmet la période à la DAF
        // =========================================================================
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/SUBMIT")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "version", getPayrollPeriodVersion(idPeriode), "idSite", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"));

        // Vérification statut en base : A_VALIDER ou TRANSMIS_DAF
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                String code = rs.getString("code");
                assertTrue("TRANSMIS_DAF".equals(code) || "A_VALIDER".equals(code));
            }
        }

        // =========================================================================
        // ÉTAPE 4 : La DAF rejette la période avec motif de correction
        // =========================================================================
        String motifRejet = "Erreur de pointage sur EMP102 : vérifier sortie du vendredi";
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/REJECT")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "version", getPayrollPeriodVersion(idPeriode), "motif", motifRejet))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"));

        // Vérification retour en EN_COURS
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT s.code, p.motif_rejet FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("EN_COURS", rs.getString("code"));
                assertEquals(motifRejet, rs.getString("motif_rejet"));
            }
        }

        // =========================================================================
        // ÉTAPE 5 : L'Agent corrige le vendredi de l'employé 2 (17h00 au lieu de 18h00)
        // =========================================================================
        LocalDate vendredi = lundiSemaine.plusDays(4);
        SaisiePointageJourDTO p2Corrige = new SaisiePointageJourDTO();
        p2Corrige.setIdEmploye(emp2.idEmploye);
        p2Corrige.setIdSite(emp2.idSite);
        p2Corrige.setDatePointage(vendredi.toString());
        p2Corrige.setIdPeriodePaie(idPeriode);
        p2Corrige.setHeureEntreeMatin("08:00");
        p2Corrige.setHeureSortieMatin("12:00");
        p2Corrige.setHeureEntreeAm("13:00");
        p2Corrige.setHeureSortieAm("17:00");
        p2Corrige.setIndemniteRepasRecue(true);
        p2Corrige.setDeplacementMatin(true);
        p2Corrige.setDeplacementAm(true);

        // Récupérer l'idPointage et la version (OCC - Optimistic Concurrency Control)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_pointage, version FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?")) {
            ps.setInt(1, emp2.idEmploye);
            ps.setDate(2, java.sql.Date.valueOf(vendredi));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    p2Corrige.setIdPointage(rs.getInt("id_pointage"));
                    p2Corrige.setVersion(rs.getInt("version"));
                }
            }
        }

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(p2Corrige))))
                .andExpect(status().isOk());

        // Re-calcul et re-transmission
        mockMvc.perform(post("/pointages/synthese/calculer-tout")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("idPeriodePaie", idPeriode, "idSite", 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/SUBMIT")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "version", getPayrollPeriodVersion(idPeriode), "idSite", 1))))
                .andExpect(status().isOk());

        // =========================================================================
        // ÉTAPE 6 : La DAF valide définitivement la période
        // =========================================================================
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/APPROVE")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "version", getPayrollPeriodVersion(idPeriode)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"));

        // =========================================================================
        // ÉTAPE 7 : Vérifications finales d'intégrité en base
        // =========================================================================
        try (Connection c = dataSource.getConnection()) {
            // Statut de la période passé à VALIDEE
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ?")) {
                ps.setInt(1, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("VALIDEE", rs.getString("code"));
                }
            }

            // Cumuls gelés dans pointage_cumul_periode pour les 2 employés (40h chacun)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_employe, total_heures_travaillees FROM pointage_cumul_periode WHERE id_periode_paie = ?")) {
                ps.setInt(1, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        assertEquals(40.0, rs.getDouble("total_heures_travaillees"), 0.01);
                    }
                    assertEquals(2, count, "Les 2 employés doivent avoir leurs cumuls gelés en base");
                }
            }
        }
    }
}
