package mg.galaxam.sirh.model.pointage;

import mg.galaxam.sirh.api.dto.pointage.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import mg.galaxam.sirh.model.pointage.service.PointageSyntheseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PointageWorkflowDbIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    @Test
    @DisplayName("Test BD Intégration Réelle : Saisie, Synthèse, Gel et Rollback")
    public void testWorkflowSyntheseEtGelAvecRollback() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 0. Vérifier la présence et l'utilisation des 9 paramètres globaux de POINTAGE
                Map<String, String> pointageParams = ParametreGlobal.loadActiveMapByCategorie("POINTAGE", c);
                assertNotNull(pointageParams, "La map des paramètres globaux POINTAGE doit être chargée");
                assertTrue(pointageParams.containsKey("SEUIL_NUIT_DEBUT"), "Paramètre global manquant : SEUIL_NUIT_DEBUT");
                assertTrue(pointageParams.containsKey("SEUIL_NUIT_FIN"), "Paramètre global manquant : SEUIL_NUIT_FIN");
                assertTrue(pointageParams.containsKey("MONTANT_REPAS_MIDI"), "Paramètre global manquant : MONTANT_REPAS_MIDI");
                assertTrue(pointageParams.containsKey("SEUIL_2ND_REPAS_1"), "Paramètre global manquant : SEUIL_2ND_REPAS_1");
                assertTrue(pointageParams.containsKey("MONTANT_2ND_REPAS_1"), "Paramètre global manquant : MONTANT_2ND_REPAS_1");
                assertTrue(pointageParams.containsKey("SEUIL_2ND_REPAS_2"), "Paramètre global manquant : SEUIL_2ND_REPAS_2");
                assertTrue(pointageParams.containsKey("MONTANT_2ND_REPAS_2"), "Paramètre global manquant : MONTANT_2ND_REPAS_2");
                assertTrue(pointageParams.containsKey("SEUIL_PAUSE_MIDI_MIN"), "Paramètre global manquant : SEUIL_PAUSE_MIDI_MIN");
                assertTrue(pointageParams.containsKey("REDUCTION_DEPLACT_REPAS_MIDI"), "Paramètre global manquant : REDUCTION_DEPLACT_REPAS_MIDI");
                // 1. Trouver un employé avec contrat actif et un site
                Integer idEmploye = null;
                Integer idContrat = null;
                Integer idSite = null;

                try (PreparedStatement psSite = c.prepareStatement("SELECT id_site FROM ref_site LIMIT 1")) {
                    try (ResultSet rsSite = psSite.executeQuery()) {
                        if (rsSite.next()) idSite = rsSite.getInt(1);
                    }
                }

                try (PreparedStatement psE = c.prepareStatement(
                        "SELECT e.id_employe, ct.id_contrat FROM employe e " +
                        "JOIN contrat_travail ct ON ct.id_employe = e.id_employe " +
                        "JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh " +
                        "WHERE s.code = 'ACTIF' LIMIT 1")) {
                    try (ResultSet rsE = psE.executeQuery()) {
                        if (rsE.next()) {
                            idEmploye = rsE.getInt(1);
                            idContrat = rsE.getInt(2);
                        }
                    }
                }

                if (idEmploye == null) {
                    // Base de test sans contrat actif pré-existant, sortie gracieuse
                    return;
                }

                // S'assurer qu'il a une affectation active sur ce site
                boolean hasAff = false;
                try (PreparedStatement psCheck = c.prepareStatement(
                        "SELECT id_site FROM employe_affectation_carriere WHERE id_employe = ? AND date_fin IS NULL")) {
                    psCheck.setInt(1, idEmploye);
                    try (ResultSet rsCheck = psCheck.executeQuery()) {
                        if (rsCheck.next()) {
                            hasAff = true;
                            idSite = rsCheck.getInt(1);
                        }
                    }
                }

                if (!hasAff) {
                    String sqlAff = "INSERT INTO employe_affectation_carriere (" +
                                    "id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, " +
                                    "id_departement, id_service, id_fonction, id_categorie_professionnelle, " +
                                    "id_mode_calcul_paie, salaire_base, date_debut" +
                                    ") VALUES (?, ?, " +
                                    "(SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF' LIMIT 1), " +
                                    "(SELECT id_motif_mouvement FROM ref_motif_mouvement LIMIT 1), " +
                                    "?, " +
                                    "(SELECT id_departement FROM ref_departement LIMIT 1), " +
                                    "(SELECT id_service FROM ref_service LIMIT 1), " +
                                    "(SELECT id_fonction FROM ref_fonction LIMIT 1), " +
                                    "(SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle LIMIT 1), " +
                                    "(SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie LIMIT 1), " +
                                    "1500000.00, '2020-01-01')";
                    try (PreparedStatement psAff = c.prepareStatement(sqlAff)) {
                        psAff.setInt(1, idEmploye);
                        psAff.setInt(2, idContrat);
                        psAff.setInt(3, idSite);
                        psAff.executeUpdate();
                    }
                }

                // 2. Créer une période de paie de test
                Integer idPeriode = null;
                String sqlPeriode = "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                                    "VALUES ('9999-TEST', 'Période Test Unitaire BD', '2099-01-01', '2099-01-31', " +
                                    "(SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'EN_COURS')) " +
                                    "RETURNING id_periode_paie";
                try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
                    try (ResultSet rsP = psP.executeQuery()) {
                        if (rsP.next()) {
                            idPeriode = rsP.getInt(1);
                        }
                    }
                }
                assertNotNull(idPeriode, "La période de test doit être créée");

                // 3. Insérer 5 jours de pointage (Semaine complète Lundi à Vendredi)
                // Semaine du Lundi 2099-01-05 au Vendredi 2099-01-09
                // Lundi: 9h (08h-12h et 13h-18h), 0 retard
                // Mardi: 9h, 0 retard
                // Mercredi: 9h, 0 retard
                // Jeudi: 9h, 0 retard
                // Vendredi: 8.5h (08h30-12h et 13h-17h30), 30 min de retard (arrivée 08h30)
                // Total semaine = 9 * 4 + 8.5 = 44.5h. Seuil = 40.0h. HS brutes = 4.5h.
                // Retard = 30 min (0.5h). Retard compensé = 0.5h. HS nettes = 4.0h (0 si cadre). Déficit = 0.0h.
                String sqlPtg = "INSERT INTO pointage_jour (" +
                                "id_employe, date_pointage, id_periode_paie, id_site, " +
                                "total_heures, minutes_retard, heures_nuit, " +
                                "id_statut_jour, deplacement_matin, deplacement_am, indemnite_repas_recue" +
                                ") VALUES (?, ?, ?, ?, ?, ?, 0.0, " +
                                "(SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT'), " +
                                "TRUE, TRUE, TRUE)";

                LocalDate lundi = LocalDate.of(2099, 1, 5);
                try (PreparedStatement psIns = c.prepareStatement(sqlPtg)) {
                    for (int i = 0; i < 5; i++) {
                        LocalDate d = lundi.plusDays(i);
                        double th = (i == 4) ? 8.5 : 9.0;
                        int ret = (i == 4) ? 30 : 0;

                        psIns.setInt(1, idEmploye);
                        psIns.setDate(2, Date.valueOf(d));
                        psIns.setInt(3, idPeriode);
                        psIns.setInt(4, idSite);
                        psIns.setDouble(5, th);
                        psIns.setInt(6, ret);
                        psIns.addBatch();
                    }
                    psIns.executeBatch();
                }

                // 4. Calculer la synthèse de la période via la logique métier Java
                List<PointageSyntheseMensuelleDTO> synthese = pointageSyntheseService.getSynthesePeriode(idPeriode, idSite, c);
                assertNotNull(synthese);
                assertFalse(synthese.isEmpty(), "La synthèse doit contenir au moins un résultat");

                final int targetEmpId = idEmploye;
                PointageSyntheseMensuelleDTO empSynthese = synthese.stream()
                        .filter(s -> s.getIdEmploye().equals(targetEmpId))
                        .findFirst()
                        .orElse(null);
                assertNotNull(empSynthese, "La synthèse de l'employé testé doit être présente");

                // Vérifier les résultats calculés en Java
                assertEquals(5, empSynthese.getNbJoursPresents(), "5 jours présents");
                assertEquals(44.5, empSynthese.getTotalHeuresTravaillees(), "44.5 heures travaillées au total");
                assertEquals(30, empSynthese.getTotalMinutesRetard(), "30 min de retard constatées");
                assertEquals(0.0, empSynthese.getHeuresAbsenceRetenues(), "Le retard de 30 min doit être intégralement compensé par les 4.5h de HS");

                if (empSynthese.isEstCadre()) {
                    assertEquals(0.0, empSynthese.getHeuresHs30(), "Un cadre a 0h de HS payées (forfait contractuel)");
                } else {
                    assertEquals(4.0, empSynthese.getHeuresHs30(), "4.0h de HS nettes payées à 30% (4.5h - 0.5h retard)");
                }

                assertEquals(0.0, empSynthese.getHeuresHs50(), "0.0h de HS à 50%");
                assertEquals(5, empSynthese.getNbJoursRepas(), "5 repas accordés");
                assertEquals(5, empSynthese.getNbJoursDeplacement(), "5 jours de déplacement (10 trajets)");

                // 5. Tester le gel physique de la période dans pointage_cumul_periode
                pointageSyntheseService.gelerPeriode(idPeriode, 1, c);

                String sqlCheckCumul = "SELECT total_heures_travaillees, heures_hs_30, heures_absence_retenues " +
                                       "FROM pointage_cumul_periode WHERE id_periode_paie = ? AND id_employe = ?";
                try (PreparedStatement psCheck = c.prepareStatement(sqlCheckCumul)) {
                    psCheck.setInt(1, idPeriode);
                    psCheck.setInt(2, idEmploye);
                    try (ResultSet rsCheck = psCheck.executeQuery()) {
                        assertTrue(rsCheck.next(), "Une ligne doit être insérée dans pointage_cumul_periode");
                        assertEquals(44.5, rsCheck.getDouble("total_heures_travaillees"));
                        if (empSynthese.isEstCadre()) {
                            assertEquals(0.0, rsCheck.getDouble("heures_hs_30"));
                        } else {
                            assertEquals(4.0, rsCheck.getDouble("heures_hs_30"));
                        }
                        assertEquals(0.0, rsCheck.getDouble("heures_absence_retenues"));
                    }
                }

                // 6. Tester le dégel
                pointageSyntheseService.degelerPeriode(idPeriode, c);
                try (PreparedStatement psCheck = c.prepareStatement(sqlCheckCumul)) {
                    psCheck.setInt(1, idPeriode);
                    psCheck.setInt(2, idEmploye);
                    try (ResultSet rsCheck = psCheck.executeQuery()) {
                        assertFalse(rsCheck.next(), "Les données gelées doivent être supprimées lors du dégel");
                    }
                }

            } finally {
                // Rollback systématique : AUCUNE modification ne persiste en BDD
                c.rollback();
            }
        }
    }
}
