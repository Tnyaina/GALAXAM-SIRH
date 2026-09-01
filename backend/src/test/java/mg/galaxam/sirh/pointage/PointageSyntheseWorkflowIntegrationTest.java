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
}
