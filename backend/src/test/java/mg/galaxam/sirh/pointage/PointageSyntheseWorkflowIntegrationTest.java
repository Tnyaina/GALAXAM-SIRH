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

    @Autowired
    private mg.galaxam.sirh.model.pointage.service.PointageAnomalieService pointageAnomalieService;

    @org.junit.jupiter.api.BeforeAll
    static void initSchema(@Autowired DataSource ds) throws Exception {
        try (Connection c = ds.getConnection();
             java.sql.Statement stmt = c.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS pointage_cumul_indemnite (" +
                    "id_cumul_indemnite   SERIAL PRIMARY KEY, " +
                    "id_periode_paie      INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie) ON DELETE CASCADE, " +
                    "id_employe           INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE, " +
                    "id_indemnite         INTEGER NOT NULL REFERENCES ref_indemnite(id_indemnite), " +
                    "quantite_totale      DECIMAL(6,2) NOT NULL DEFAULT 0, " +
                    "montant_total        DECIMAL(12,2) NOT NULL DEFAULT 0, " +
                    "gele_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "CONSTRAINT uk_cumul_indemnite UNIQUE (id_periode_paie, id_employe, id_indemnite))");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cumul_indemnite_periode ON pointage_cumul_indemnite(id_periode_paie, id_employe)");
        }
    }

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
            try (PreparedStatement psU = c.prepareStatement("UPDATE pointage_jour SET minutes_retard = 10 WHERE id_employe = 2 AND date_pointage = '2026-08-17'")) {
                psU.executeUpdate();
            }

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

    @Test
    @Order(9)
    @DisplayName("PTG-09 : Source unique de vérité dynamique (pointage_indemnite_jour prioritaire)")
    void testPointageIndemniteJourPrioritaire() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Trouver l'id_pointage du 24 août pour l'Employé 4
            int idPtg = -1;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_pointage FROM pointage_jour WHERE id_employe = 4 AND date_pointage = '2026-08-24'")) {
                var rs = ps.executeQuery();
                if (rs.next()) idPtg = rs.getInt(1);
            }
            assertTrue(idPtg > 0, "Le pointage du 24 août doit exister");

            // Trouver l'id_indemnite pour REPAS
            int idIndRepas = -1;
            try (PreparedStatement ps = c.prepareStatement("SELECT id_indemnite FROM ref_indemnite WHERE code = 'REPAS'")) {
                var rs = ps.executeQuery();
                if (rs.next()) idIndRepas = rs.getInt(1);
            }
            assertTrue(idIndRepas > 0);

            // 1. Calculer la synthèse avant modification
            List<PointageSyntheseMensuelleDTO> synAvant = pointageSyntheseService.getSynthesePeriode(1, null);
            PointageSyntheseMensuelleDTO s4Avant = synAvant.stream().filter(d -> d.getIdEmploye() == 4).findFirst().orElseThrow();
            double montantRepasAvant = s4Avant.getMontantRepas();
            int nbJoursRepasAvant = s4Avant.getNbJoursRepas();

            try {
                // 2. Désactiver le repas pour cette journée EXCLUSIVEMENT dans pointage_indemnite_jour (accorde = false)
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_indemnite_jour (id_pointage, id_indemnite, fraction, quantite, montant_calcule, accorde) " +
                        "VALUES (?, ?, 'JOUR_ENTIER', 1.0, 0, false) " +
                        "ON CONFLICT (id_pointage, id_indemnite, fraction) DO UPDATE SET accorde = false")) {
                    ps.setInt(1, idPtg);
                    ps.setInt(2, idIndRepas);
                    ps.executeUpdate();
                }

                // 3. Recalculer la synthèse : elle DOIT refléter la modification de pointage_indemnite_jour
                List<PointageSyntheseMensuelleDTO> synApres = pointageSyntheseService.getSynthesePeriode(1, null);
                PointageSyntheseMensuelleDTO s4Apres = synApres.stream().filter(d -> d.getIdEmploye() == 4).findFirst().orElseThrow();

                assertEquals(nbJoursRepasAvant - 1, s4Apres.getNbJoursRepas(), "Le nombre de jours repas doit avoir diminué de 1");
                assertTrue(s4Apres.getMontantRepas() < montantRepasAvant, "Le montant repas doit avoir diminué grâce à pointage_indemnite_jour");
            } finally {
                // Restauration de l'état initial
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pointage_indemnite_jour SET accorde = true WHERE id_pointage = ? AND id_indemnite = ?")) {
                    ps.setInt(1, idPtg);
                    ps.setInt(2, idIndRepas);
                    ps.executeUpdate();
                }
            }
        }
    }

    @Test
    @Order(10)
    @DisplayName("PTG-10 : Régularisation d'anomalie synchronise fidèlement pointage_indemnite_jour")
    void testResoudreAnomalieSynchroniseIndemnites() throws Exception {
        int idPtg;
        int currentVer;
        try (Connection c = dataSource.getConnection()) {
            // Remettre 2026-08-17 au propre au cas où
            try (PreparedStatement ps = c.prepareStatement("UPDATE pointage_jour SET minutes_retard = 10 WHERE id_employe = 2 AND date_pointage = '2026-08-17'")) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_pointage, version FROM pointage_jour WHERE id_employe = 2 AND date_pointage = '2026-08-18'")) {
                var rs = ps.executeQuery();
                assertTrue(rs.next());
                idPtg = rs.getInt("id_pointage");
                currentVer = rs.getInt("version");
            }

            try {
                // Régulariser l'anomalie avec repas = false et deplacementMatin = false
                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("idPointage", idPtg);
                payload.put("version", currentVer);
                payload.put("heureEntreeMatin", "08:00");
                payload.put("heureSortieMatin", "12:00");
                payload.put("heureEntreeAm", "14:00");
                payload.put("heureSortieAm", "18:00");
                payload.put("totalHeures", 8.0);
                payload.put("minutesRetard", 0);
                payload.put("heuresNuit", 0.0);
                payload.put("codeStatut", "PRESENT");
                payload.put("indemniteRepasRecue", false);
                payload.put("deplacementMatin", false);
                payload.put("deplacementAm", true);
                payload.put("motifAjustement", "Test synchro anomalie -> pointage_indemnite_jour");

                boolean ok = pointageAnomalieService.regulariserPointage(idPtg, payload);
                assertTrue(ok, "La régularisation d'anomalie doit réussir");

                // Vérifier que pointage_indemnite_jour a bien été synchronisé pour ce pointage
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT ri.code, pij.accorde, pij.quantite FROM pointage_indemnite_jour pij " +
                        "JOIN ref_indemnite ri ON pij.id_indemnite = ri.id_indemnite " +
                        "WHERE pij.id_pointage = ?")) {
                    ps.setInt(1, idPtg);
                    var rs = ps.executeQuery();
                    boolean foundRepas = false;
                    boolean foundDep = false;
                    while (rs.next()) {
                        String code = rs.getString("code");
                        if ("REPAS".equals(code)) {
                            foundRepas = true;
                            assertFalse(rs.getBoolean("accorde"), "Le repas doit être accorde=false dans pointage_indemnite_jour");
                        } else if ("DEPLACEMENT".equals(code) || "TRANSPORT".equals(code)) {
                            foundDep = true;
                            assertTrue(rs.getBoolean("accorde"), "Le déplacement doit être accorde=true");
                            assertEquals(0.5, rs.getDouble("quantite"), 0.01, "Seul l'après-midi est coché -> quantite=0.5");
                        }
                    }
                    assertTrue(foundRepas, "L'indemnité REPAS doit être présente dans pointage_indemnite_jour");
                    assertTrue(foundDep, "L'indemnité DEPLACEMENT doit être présente dans pointage_indemnite_jour");
                }
            } finally {
                // Restauration de l'état initial
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pointage_jour SET deplacement_matin = true, deplacement_am = true, " +
                        "indemnite_deplacement_recue = true, indemnite_repas_recue = true WHERE id_pointage = ?")) {
                    ps.setInt(1, idPtg);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pointage_indemnite_jour SET accorde = true, quantite = 1.0 WHERE id_pointage = ?")) {
                    ps.setInt(1, idPtg);
                    ps.executeUpdate();
                }
            }
        }
    }
}
