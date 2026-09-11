package mg.galaxam.sirh.integration.pointage;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.model.pointage.service.PointageSaisieService;
import mg.galaxam.sirh.model.pointage.service.PointageSyntheseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PointageSyntheseServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PointageSaisieService pointageSaisieService;

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    private TestEmployeFixtures.CreatedEmploye emp;
    private int idPeriode;
    private int idAdminUser;
    private int idAgentUser;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();
        idAdminUser = userFixtures.getAdminUserId();
        idAgentUser = userFixtures.getPointageUserId();
        LocalDate dEmb = LocalDate.now().minusMonths(6);
        emp = employeFixtures.createActiveEmploye("EMP010", "RAKOTOARISOA", "Michel", 1, dEmb);

        // Période couvrant une semaine complète passée (ex: du lundi au dimanche)
        LocalDate lundi = LocalDate.now().minusDays(14);
        while (lundi.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            lundi = lundi.minusDays(1);
        }
        LocalDate dimanche = lundi.plusDays(6);
        idPeriode = employeFixtures.createPeriodePaie("P-SYNTH", lundi, dimanche, "EN_COURS");

        // Enregistrer 5 jours de pointages (40h) du lundi au vendredi
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
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
                pointageSaisieService.enregistrerPointagesBatch(List.of(dto), c);
            }
            c.commit();
        }
    }

    @Test
    @DisplayName("Cycle Synthèse : Calcul de toutes les semaines de la période et persistance en base")
    public void testCalculerToutesSemainesPeriode() throws Exception {
        Map<String, Object> res = pointageSyntheseService.calculerToutesSemainesPeriode(idPeriode, 1, idAgentUser);
        assertNotNull(res);
        assertTrue((Boolean) res.get("success"), "Le calcul des semaines doit réussir");

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
    @DisplayName("Workflow DAF : Transmission DAF -> Rejet avec motif -> Re-transmission -> Validation DAF")
    public void testWorkflowTransmissionRejetEtValidationDAF() throws Exception {
        // 1. Calcul initial
        pointageSyntheseService.calculerToutesSemainesPeriode(idPeriode, 1, idAgentUser);

        // 2. Transmission à la DAF par l'Agent de Pointage
        Map<String, Object> resTrans = pointageSyntheseService.transmettreDaf(idPeriode, 1, idAgentUser);
        assertTrue((Boolean) resTrans.get("success"), "La transmission à la DAF doit réussir");

        // Vérifier le statut de la période en base
        try (Connection c = dataSource.getConnection()) {
            String sqlPer = "SELECT s.code, p.transmis_par FROM periode_paie p " +
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

        // 3. Rejet par la DAF avec motif
        String motifRejet = "Erreur de pointage sur l'employé EMP010 - A corriger";
        Map<String, Object> resRejet = pointageSyntheseService.rejeterParDaf(idPeriode, motifRejet, idAdminUser);
        assertTrue((Boolean) resRejet.get("success"), "Le rejet par la DAF doit réussir");

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
        pointageSyntheseService.transmettreDaf(idPeriode, 1, idAgentUser);

        // 5. Validation finale par la DAF
        Map<String, Object> resVal = pointageSyntheseService.validerParDaf(idPeriode, idAdminUser);
        assertTrue((Boolean) resVal.get("success"), "La validation par la DAF doit réussir");

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
    @DisplayName("Gel de Période : Geler la période et vérifier la persistance dans pointage_cumul_periode / dégel")
    public void testGelerEtDegelerPeriode() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            pointageSyntheseService.gelerPeriode(idPeriode, idAdminUser, c);
            c.commit();

            // Vérifier que pointage_cumul_periode contient les cumuls gelés
            String sqlCumul = "SELECT total_heures_travaillees, gele_par FROM pointage_cumul_periode WHERE id_periode_paie = ? AND id_employe = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCumul)) {
                ps.setInt(1, idPeriode);
                ps.setInt(2, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Les cumuls gelés doivent être insérés dans pointage_cumul_periode");
                    assertEquals(40.0, rs.getDouble("total_heures_travaillees"), 0.01);
                    assertEquals(idAdminUser, rs.getInt("gele_par"));
                }
            }

            // Dégeler la période
            c.setAutoCommit(false);
            pointageSyntheseService.degelerPeriode(idPeriode, c);
            c.commit();

            try (PreparedStatement ps = c.prepareStatement(sqlCumul)) {
                ps.setInt(1, idPeriode);
                ps.setInt(2, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertFalse(rs.next(), "Après dégel, les cumuls gelés doivent être supprimés");
                }
            }
        }
    }
}
