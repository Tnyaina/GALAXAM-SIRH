package mg.galaxam.sirh.certification.pointage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.model.pointage.service.PointageEmployeService;
import mg.galaxam.sirh.model.pointage.service.PointageSyntheseService;
import mg.galaxam.sirh.oracle.AttendanceIndependentOracle;

@DisplayName("CERTIFICATION POINTAGE INTÉGRATION — PIPELINE COMPLET & SNAPSHOT POSTGRESQL")
public class AttendanceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    @Autowired
    private PointageEmployeService pointageEmployeService;

    private TestEmployeFixtures.CreatedEmploye emp;
    private int idPeriode;
    private LocalDate lundi;
    private LocalDate dimanche;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();
        LocalDate dEmb = LocalDate.of(2023, 1, 1);
        emp = employeFixtures.createActiveEmploye("EMP006-PTG", "RAKOTO", "Herizo", 1, dEmb);

        // Semaine complète du lundi au dimanche
        lundi = LocalDate.of(2026, 9, 7);
        dimanche = LocalDate.of(2026, 9, 13);
        idPeriode = employeFixtures.createPeriodePaie("P-SEPT-2026", lundi, dimanche, "EN_COURS");
    }

    @Test
    @DisplayName("Pipeline PostgreSQL : Raw Punches -> Synthese -> Snapshot persistant == Oracle Indépendant")
    void testPipelineCompletVersSnapshotPostgres() throws Exception {
        List<AttendanceIndependentOracle.DailyShift> expectedShifts = new ArrayList<>();

        // Enregistrer les pointages bruts via API REST batch :
        // 5 jours de semaine (du lundi au vendredi) : 8h/jour nominal
        for (int i = 0; i < 5; i++) {
            LocalDate dateJour = lundi.plusDays(i);
            SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
            dto.setIdEmploye(emp.idEmploye);
            dto.setIdSite(emp.idSite);
            dto.setDatePointage(dateJour.toString());
            dto.setIdPeriodePaie(idPeriode);
            dto.setHeureEntreeMatin("08:00");
            dto.setHeureSortieMatin("12:00");
            dto.setHeureEntreeAm("13:00");
            dto.setHeureSortieAm("17:00");
            dto.setHoraireContinu(false);
            dto.setIndemniteRepasRecue(true);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/pointages/saisie/batch")
                    .header("Authorization", "Bearer " + getPointageToken())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(toJson(List.of(dto))))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

            expectedShifts.add(new AttendanceIndependentOracle.DailyShift(
                    dateJour,
                    LocalTime.of(8, 0), LocalTime.of(12, 0),
                    LocalTime.of(13, 0), LocalTime.of(17, 0),
                    LocalTime.of(8, 0), LocalTime.of(12, 0),
                    LocalTime.of(13, 0), LocalTime.of(17, 0),
                    false, false, false, false
            ));
        }

        // Calcul indépendant de l'Oracle (5 x 8h = 40h = 2400 min)
        AttendanceIndependentOracle.AttendanceExpectedResult oracle = AttendanceIndependentOracle.calculatePeriod(
                expectedShifts, 2400, 480
        );

        // Exécution du calcul de synthèse globale via endpoint REST
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("idPeriodePaie", idPeriode);
        req.put("idSite", emp.idSite);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/pointages/synthese/calculer-tout")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        // Vérification directe dans la table PostgreSQL pointage_semaine_calcule
        try (Connection c = dataSource.getConnection()) {
            String sql = "SELECT total_heures_travaillees, heures_hs_brutes, heures_retard FROM pointage_semaine_calcule WHERE id_employe = ? AND id_periode_paie = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, emp.idEmploye);
                ps.setInt(2, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "L'enregistrement de la semaine calculée doit exister en PostgreSQL");

                    double dbHeuresTravaillees = rs.getDouble("total_heures_travaillees");
                    double dbHeuresHs = rs.getDouble("heures_hs_brutes");
                    double dbHeuresRetard = rs.getDouble("heures_retard");

                    // Preuve 1 : Snapshot runtime == Oracle Indépendant (delta = 0)
                    assertEquals((double) oracle.actualWorkedMinutes() / 60.0, dbHeuresTravaillees, 0.01,
                            "Heures travaillées : Runtime PostgreSQL == Oracle Indépendant (40.0 h)");
                    assertEquals((double) oracle.overtimeMinutes() / 60.0, dbHeuresHs, 0.01,
                            "Heures supplémentaires : Runtime PostgreSQL == Oracle Indépendant (0.0 h)");
                    assertEquals((double) oracle.lateMinutes() / 60.0, dbHeuresRetard, 0.01,
                            "Heures retard : Runtime PostgreSQL == Oracle Indépendant (0.0 h)");
                }
            }
        }
    }
}
