package mg.galaxam.sirh.pointage;

import mg.galaxam.sirh.api.dto.pointage.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.model.pointage.service.PointageSyntheseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PointageSyntheseWorkflowIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    @Test
    @Order(1)
    @DisplayName("PTG-01 : Dispense de pointage de l'Employé 1 (DAF Cadre autonome) sans anomalie")
    void testDispensePointageDaf() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) as total, SUM(CASE WHEN id_statut_jour = 10 THEN 1 ELSE 0 END) as nb_dispense " +
                    "FROM pointage_jour WHERE id_employe = 1 AND id_periode_paie = 1")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(31, rs.getInt("total"), "31 jours d'Août enregistrés pour le DAF");
                assertEquals(31, rs.getInt("nb_dispense"), "Tous les jours sont en statut DISPENSE_POINTAGE (10)");
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("PTG-02 : Retard compensé & Déduction Tamatave pour l'Employé 2 (Jean Jacques)")
    void testPointageTamataveEmp2() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Retard de 10 min le 17 Août
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT minutes_retard, total_heures FROM pointage_jour WHERE id_employe = 2 AND date_pointage = '2026-08-17'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(10, rs.getInt("minutes_retard"), "Retard de 10 minutes détecté");
            }

            // Heures sup le 12 Août (total 11h = 8h normales + 3h sup)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT total_heures FROM pointage_jour WHERE id_employe = 2 AND date_pointage = '2026-08-12'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(11.0, rs.getDouble("total_heures"), 0.01);
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("PTG-03 : Pointage en statut CONGE (5) pour l'Employé 3 du 03 au 07 Août")
    void testPointageCongesEmp3() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) as nb_jours_conge " +
                    "FROM pointage_jour WHERE id_employe = 3 AND id_statut_jour = 5 AND date_pointage BETWEEN '2026-08-03' AND '2026-08-07'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(5, rs.getInt("nb_jours_conge"), "5 jours matérialisés en statut CONGE (5) sur le pointage");
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("PTG-04 : Heures de nuit (>22h00) & double panier pour l'Employé 4 en Semaine 35")
    void testPointageNuitEtPanierEmp4() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Lundi 24 Août : 14.5h dont 1.5h de nuit, panier = 4000 Ar
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT total_heures, heures_nuit, montant_repas FROM pointage_jour WHERE id_employe = 4 AND date_pointage = '2026-08-24'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(14.5, rs.getDouble("total_heures"), 0.01);
                assertEquals(1.5, rs.getDouble("heures_nuit"), 0.01, "1.5h de nuit après 22h00");
                assertEquals(4000.00, rs.getDouble("montant_repas"), 0.01, "Double panier repas accordé (>21h00)");
            }

            // Mardi 25 Août : 14h dont 1h de nuit, panier = 4000 Ar
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT total_heures, heures_nuit, montant_repas FROM pointage_jour WHERE id_employe = 4 AND date_pointage = '2026-08-25'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(14.0, rs.getDouble("total_heures"), 0.01);
                assertEquals(1.0, rs.getDouble("heures_nuit"), 0.01, "1h de nuit après 22h00");
                assertEquals(4000.00, rs.getDouble("montant_repas"), 0.01);
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("PTG-05 : Synthèse consolidée de la période d'Août 2026 via PointageSyntheseService")
    void testSyntheseMensuelleService() throws Exception {
        List<PointageSyntheseMensuelleDTO> synthese = pointageSyntheseService.getSynthesePeriode(1, null);
        assertNotNull(synthese);
        assertFalse(synthese.isEmpty(), "La synthèse mensuelle doit retourner des données");

        Map<Integer, PointageSyntheseMensuelleDTO> map = synthese.stream()
                .collect(Collectors.toMap(PointageSyntheseMensuelleDTO::getIdEmploye, s -> s));

        // Emp 1 : DAF (Cadre, dispense pointage)
        PointageSyntheseMensuelleDTO s1 = map.get(1);
        assertNotNull(s1);
        assertTrue(s1.isDispensePointage());
        assertTrue(s1.isEstCadre());

        // Emp 2 : OPE Manager Tamatave
        PointageSyntheseMensuelleDTO s2 = map.get(2);
        assertNotNull(s2);
        assertFalse(s2.isDispensePointage());
        assertTrue(s2.getTotalHeuresTravaillees() >= 160.0);

        // Emp 3 : Admin RH (avec 5 jours de congés)
        PointageSyntheseMensuelleDTO s3 = map.get(3);
        assertNotNull(s3);
        assertEquals(5, s3.getNbJoursConge(), "5 jours de congés dans le mois d'Août");

        // Emp 4 : Chauffeur Transit (avec heures de nuit)
        PointageSyntheseMensuelleDTO s4 = map.get(4);
        assertNotNull(s4);
        assertTrue(s4.getHeuresNuitTotal() >= 2.5, "Au moins 2.5h de travail de nuit");

        // Emp 5 : Nouvel embauché au 17 Août (mois incomplet)
        PointageSyntheseMensuelleDTO s5 = map.get(5);
        assertNotNull(s5);
        assertEquals(11, s5.getNbJoursPresents(), "11 jours travaillés à partir du 17 Août");
    }

    @Test
    @Order(6)
    @DisplayName("PTG-06 : Test des semaines et détail de semaine 1 via PointageSyntheseService")
    void testSemainesEtDetailService() throws Exception {
        List<Map<String, Object>> semaines = pointageSyntheseService.getSemainesPeriode(1, null);
        assertNotNull(semaines);
        assertFalse(semaines.isEmpty());

        List<PointageSyntheseMensuelleDTO> detailSemaine1 = pointageSyntheseService.getSyntheseSemaineDetail(
                1, java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 8, 2), null);
        assertNotNull(detailSemaine1);
        assertFalse(detailSemaine1.isEmpty());

        // Vérifier que le cadre autonome a 0h sur le week-end du 1-2 août
        PointageSyntheseMensuelleDTO s1 = detailSemaine1.stream()
                .filter(d -> d.getIdEmploye() == 1)
                .findFirst().orElse(null);
        assertNotNull(s1);
        assertEquals(0.0, s1.getTotalHeuresTravaillees(), 0.01, "0h sur week-end pur pour autonome");
        assertEquals(0.0, s1.getMontantRepas(), 0.01, "0 Ar repas sur week-end pur");
    }

    @Test
    @Order(7)
    @DisplayName("PTG-07 : Contrôle strict de workflow & Validation de semaine")
    void testValidationSemaineWorkflow() throws Exception {
        java.time.LocalDate debutSemaine = java.time.LocalDate.of(2026, 8, 3);
        java.time.LocalDate finSemaine = java.time.LocalDate.of(2026, 8, 9);

        // 1. Sur période A_VALIDER (id=1), la validation DOIT être bloquée
        assertThrows(IllegalStateException.class, () -> {
            pointageSyntheseService.validerSemaine(1, debutSemaine, finSemaine, null, 1);
        }, "Une période en statut A_VALIDER doit interdire la modification de ses semaines");

        // 2. Sur période OUVERTE (temporairement basculée), la validation doit réussir
        try (Connection c = dataSource.getConnection()) {
            int statutAValider = 2;
            int statutOuverte = 1;
            try (PreparedStatement ps = c.prepareStatement("SELECT id_statut_periode, code FROM ref_statut_periode_paie WHERE code IN ('OUVERTE', 'A_VALIDER')")) {
                var rs = ps.executeQuery();
                while (rs.next()) {
                    if ("OUVERTE".equals(rs.getString("code"))) statutOuverte = rs.getInt("id_statut_periode");
                    if ("A_VALIDER".equals(rs.getString("code"))) statutAValider = rs.getInt("id_statut_periode");
                }
            }

            try {
                // Bascule en OUVERTE
                try (PreparedStatement ps = c.prepareStatement("UPDATE periode_paie SET id_statut_periode = ? WHERE id_periode_paie = 1")) {
                    ps.setInt(1, statutOuverte);
                    ps.executeUpdate();
                }

                // La validation réussit
                pointageSyntheseService.validerSemaine(1, debutSemaine, finSemaine, null, 1);

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM pointage_semaine_calcule WHERE id_periode_paie = 1 AND debut_semaine = ?")) {
                    ps.setDate(1, java.sql.Date.valueOf(debutSemaine));
                    ResultSet rs = ps.executeQuery();
                    assertTrue(rs.next());
                    assertTrue(rs.getInt(1) > 0, "La semaine doit être enregistrée dans pointage_semaine_calcule");
                }

                // Dévalider la semaine
                pointageSyntheseService.devaliderSemaine(1, debutSemaine);

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM pointage_semaine_calcule WHERE id_periode_paie = 1 AND debut_semaine = ?")) {
                    ps.setDate(1, java.sql.Date.valueOf(debutSemaine));
                    ResultSet rs = ps.executeQuery();
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "La semaine doit être supprimée après dévalidation");
                }
            } finally {
                // Restauration du statut initial A_VALIDER
                try (PreparedStatement ps = c.prepareStatement("UPDATE periode_paie SET id_statut_periode = ? WHERE id_periode_paie = 1")) {
                    ps.setInt(1, statutAValider);
                    ps.executeUpdate();
                }
            }
        }
    }

    @Test
    @Order(8)
    @DisplayName("PTG-08 : Gel et dégel mensuel (pointage_cumul_periode)")
    void testGelerEtDegelerPeriode() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Geler la période
            pointageSyntheseService.gelerPeriode(1, 1, c);

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) as nb, SUM(total_heures_travaillees) as total_h, SUM(montant_repas) as total_repas " +
                    "FROM pointage_cumul_periode WHERE id_periode_paie = 1")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertTrue(rs.getInt("nb") >= 4, "Au moins 4 salariés gelés pour la période");
                assertTrue(rs.getDouble("total_h") > 0, "Total d'heures gelées > 0");
                assertTrue(rs.getDouble("total_repas") > 0, "Total repas gelé > 0");
            }

            // Dégeler la période
            pointageSyntheseService.degelerPeriode(1, c);

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM pointage_cumul_periode WHERE id_periode_paie = 1")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Toutes les lignes cumulées doivent être supprimées après dégel");
            }
        }
    }
}
