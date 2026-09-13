package mg.galaxam.sirh.integration.pointage;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import mg.galaxam.sirh.model.pointage.service.PointageSyntheseService;

public class PointageSyntheseServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    private TestEmployeFixtures.CreatedEmploye emp;
    private int idPeriode;
    private LocalDate lundi;
    private LocalDate dimanche;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();
        LocalDate dEmb = LocalDate.now().minusMonths(6);
        emp = employeFixtures.createActiveEmploye("EMP010", "RAKOTOARISOA", "Michel", 1, dEmb);

        // Période couvrant une semaine complète passée (du lundi au dimanche)
        lundi = LocalDate.now().minusDays(14);
        while (lundi.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            lundi = lundi.minusDays(1);
        }
        dimanche = lundi.plusDays(6);
        idPeriode = employeFixtures.createPeriodePaie("P-SYNTH", lundi, dimanche, "EN_COURS");

        // Enregistrer 5 jours de pointages (40h) du lundi au vendredi via API REST
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

            mockMvc.perform(post("/pointages/saisie/batch")
                    .header("Authorization", "Bearer " + getPointageToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(List.of(dto))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("API Synthèse Pointage : Calcul global de toutes les semaines via POST /pointages/synthese/calculer-tout")
    public void testCalculerToutesSemainesPeriode() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("idPeriodePaie", idPeriode);
        req.put("idSite", 1);

        mockMvc.perform(post("/pointages/synthese/calculer-tout")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isOk());

        try (Connection c = dataSource.getConnection()) {
            // Vérifier que pointage_semaine_calcule contient la semaine calculée avec 40h
            String sqlSem = "SELECT total_heures_travaillees, heures_retard, heures_hs_brutes, heures_hs_nettes " +
                    "FROM pointage_semaine_calcule WHERE id_periode_paie = ? AND id_employe = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlSem)) {
                ps.setInt(1, idPeriode);
                ps.setInt(2, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Un enregistrement de semaine calculée doit exister");
                    assertEquals(40.0, rs.getDouble("total_heures_travaillees"), 0.01);
                    assertEquals(0.0, rs.getDouble("heures_retard"), 0.01);
                    assertEquals(0.0, rs.getDouble("heures_hs_brutes"), 0.01);
                }
            }

        }
    }

    @Test
    @DisplayName("Snapshot Pointage : une réouverture crée v2 sans écraser v1")
    void snapshotVersionIsAppendOnly() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            pointageSyntheseService.gelerPeriode(idPeriode, null, c);
            pointageSyntheseService.degelerPeriode(idPeriode, c);
            pointageSyntheseService.gelerPeriode(idPeriode, null, c);
            c.commit();

            int v1Id;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_cumul, version_snapshot, statut_snapshot FROM pointage_cumul_periode " +
                    "WHERE id_periode_paie = ? AND id_employe = ? ORDER BY version_snapshot")) {
                ps.setInt(1, idPeriode); ps.setInt(2, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    v1Id = rs.getInt("id_cumul");
                    assertEquals(1, rs.getInt("version_snapshot")); assertEquals("REMPLACE", rs.getString("statut_snapshot"));
                    assertTrue(rs.next()); assertEquals(2, rs.getInt("version_snapshot")); assertEquals("GELE", rs.getString("statut_snapshot"));
                    assertFalse(rs.next());
                }
            }
            try (PreparedStatement source = c.prepareStatement(
                    "SELECT COUNT(*) FROM pointage_snapshot_jour WHERE id_cumul = ?")) {
                source.setInt(1, v1Id);
                try (ResultSet rs = source.executeQuery()) {
                    assertTrue(rs.next()); assertEquals(5, rs.getInt(1));
                }
            }
        }
    }

    @Test
    @DisplayName("API Workflow Pointage : Transmission DAF -> Rejet DAF -> Validation DAF via endpoints REST")
    public void testWorkflowTransmissionRejetEtValidationDAF() throws Exception {
        // 1. Calcul initial via API
        Map<String, Object> calcReq = new HashMap<>();
        calcReq.put("idPeriodePaie", idPeriode);
        calcReq.put("idSite", 1);

        mockMvc.perform(post("/pointages/synthese/calculer-tout")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(calcReq)))
                .andExpect(status().isOk());

        // 2. Transmission à la DAF par l'Agent de Pointage via POST /pointages/synthese/transmettre-daf
        Map<String, Object> transReq = new HashMap<>();
        transReq.put("idPeriodePaie", idPeriode);
        transReq.put("idSite", 1);

        mockMvc.perform(post("/pointages/synthese/transmettre-daf")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(transReq)))
                .andExpect(status().isOk());

        // Vérifier le statut de la période en base (A_VALIDER ou TRANSMIS_DAF)
        try (Connection c = dataSource.getConnection()) {
            String sqlPer = "SELECT s.code FROM periode_paie p " +
                    "JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                    "WHERE p.id_periode_paie = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlPer)) {
                ps.setInt(1, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    String code = rs.getString("code");
                    assertTrue("TRANSMIS_DAF".equals(code) || "A_VALIDER".equals(code),
                            "La période doit être en statut A_VALIDER ou TRANSMIS_DAF (actuel: " + code + ")");
                }
            }
        }

        // 3. Rejet par la DAF via POST /pointages/synthese/rejeter-daf
        String motifRejet = "Erreur de pointage sur l'employé EMP010 - A corriger";
        Map<String, Object> rejetReq = new HashMap<>();
        rejetReq.put("idPeriodePaie", idPeriode);
        rejetReq.put("motif", motifRejet);

        mockMvc.perform(post("/pointages/synthese/rejeter-daf")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(rejetReq)))
                .andExpect(status().isOk());

        // Vérifier le retour en EN_COURS avec motif de rejet
        try (Connection c = dataSource.getConnection()) {
            String sqlPer = "SELECT s.code, p.motif_rejet FROM periode_paie p " +
                    "JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                    "WHERE p.id_periode_paie = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlPer)) {
                ps.setInt(1, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("EN_COURS", rs.getString("code"), "La période doit être revenue en EN_COURS");
                    assertEquals(motifRejet, rs.getString("motif_rejet"));
                }
            }
        }

        // 4. Nouvelle transmission après correction
        mockMvc.perform(post("/pointages/synthese/transmettre-daf")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(transReq)))
                .andExpect(status().isOk());

        // 5. Validation finale par la DAF via POST /pointages/synthese/valider-daf
        Map<String, Object> valReq = new HashMap<>();
        valReq.put("idPeriodePaie", idPeriode);

        mockMvc.perform(post("/pointages/synthese/valider-daf")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(valReq)))
                .andExpect(status().isOk());

        // Vérifier que la période est VALIDEE en base
        try (Connection c = dataSource.getConnection()) {
            String sqlPer = "SELECT s.code, p.valide_par FROM periode_paie p " +
                    "JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                    "WHERE p.id_periode_paie = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlPer)) {
                ps.setInt(1, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("VALIDEE", rs.getString("code"), "La période doit être en statut VALIDEE");
                }
            }
        }
    }

    @Test
    @DisplayName("API Pointage Synthèse : Verrouillage et déverrouillage de semaine via REST")
    public void testValiderEtDevaliderSemaine() throws Exception {
        // 1. Calcul préalable
        Map<String, Object> calcReq = new HashMap<>();
        calcReq.put("idPeriodePaie", idPeriode);
        calcReq.put("idSite", 1);
        mockMvc.perform(post("/pointages/synthese/calculer-tout")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(calcReq)))
                .andExpect(status().isOk());

        // 2. Verrouiller la semaine via POST /pointages/synthese/semaines/valider
        Map<String, Object> semReq = new HashMap<>();
        semReq.put("idPeriodePaie", idPeriode);
        semReq.put("dateDebut", lundi.toString());
        semReq.put("dateFin", dimanche.toString());
        semReq.put("idSite", 1);

        mockMvc.perform(post("/pointages/synthese/semaines/valider")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(semReq)))
                .andExpect(status().isOk());

        // Vérifier le statut de verrouillage en base (gele_par non null)
        try (Connection c = dataSource.getConnection()) {
            String sql = "SELECT gele_par FROM pointage_semaine_calcule WHERE id_periode_paie = ? AND id_employe = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, idPeriode);
                ps.setInt(2, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertNotNull(rs.getObject("gele_par"), "La semaine doit être marquée verrouillée (gele_par non null)");
                }
            }
        }

        // 3. Déverrouiller la semaine via POST /pointages/synthese/semaines/devalider
        Map<String, Object> devalReq = new HashMap<>();
        devalReq.put("idPeriodePaie", idPeriode);
        devalReq.put("dateDebut", lundi.toString());

        mockMvc.perform(post("/pointages/synthese/semaines/devalider")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(devalReq)))
                .andExpect(status().isOk());

        // Vérifier le déverrouillage en base (gele_par redevient null)
        try (Connection c = dataSource.getConnection()) {
            String sql = "SELECT gele_par FROM pointage_semaine_calcule WHERE id_periode_paie = ? AND id_employe = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, idPeriode);
                ps.setInt(2, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertFalse(rs.next(), "Après dévalidation, la semaine calculée est supprimée de pointage_semaine_calcule");
                }
            }
        }
    }
}
