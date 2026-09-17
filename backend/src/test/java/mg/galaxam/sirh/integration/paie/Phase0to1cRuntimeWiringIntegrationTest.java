package mg.galaxam.sirh.integration.paie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
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

/**
 * Gate d'intégration réel Phase 0 -> Phase 1C.
 * Démontre par PREUVE D'EXÉCUTION RUNTIME que la chaîne complète :
 *
 * Saisie REST (Batch)
 *    ↓
 * PointageSaisieController -> PointageSaisieService
 *    ↓
 * DailyAttendanceCalculator (Temps journaliers, repas, déplacement)
 *    ↓
 * Synthèse REST -> PointageSyntheseController -> PointageSyntheseService
 *    ↓
 * PeriodWeekSegmenter & WeeklyAttendanceCalculator (40h, surplus, compensation, HS30/HS50)
 *    ↓
 * Workflow Période (SUBMIT -> APPROVE) -> PayrollPeriodWorkflowService
 *    ↓
 * PointageSyntheseService.gelerPeriode
 *    ↓
 * WorkIntervalFactory (Vacations cross-midnight)
 *    ↓
 * AttendanceTemporalPartitioner (Partitionnement 22h/00h/05h, dimanche, férié)
 *    ↓
 * OvertimeProjectionEngine (Projection orthogonale Phase 1B sur segments)
 *    ↓
 * QualifiedTimeBucket -> pointage_snapshot_bucket
 *    ↓
 * Calcul Paie REST -> PaieController -> PayrollService.preparerInput
 *    ↓
 * PayrollLegalContextResolver -> TemporalRateResolver (Barèmes historiques)
 *    ↓
 * PayrollExecutionEngine -> OvertimePayCalculator (Valorisation monétaire anti-double base)
 *    ↓
 * paie_bulletin & paie_bulletin_ligne
 *
 * Est RÉELLEMENT câblée et exécutée sans instancier directement les calculateurs dans le test.
 */
@DisplayName("Gate d'Intégration Réel Phase 0 -> 1C : Workflow Applicatif de Bout en Bout")
public class Phase0to1cRuntimeWiringIntegrationTest extends BaseIntegrationTest {

    private TestEmployeFixtures.CreatedEmploye emp;
    private int idPeriode;
    private final LocalDate debutPeriode = LocalDate.of(2026, 8, 1);
    private final LocalDate finPeriode = LocalDate.of(2026, 8, 31);

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();

        // 1. Période de paie passée (1er août au 31 août 2026)
        idPeriode = employeFixtures.createPeriodePaie("P-2026-08-E2E", debutPeriode, finPeriode, "EN_COURS");

        // 2. Employé permanent avec salaire de base 600 000 Ar (taux horaire base 173.33 = 3461.61 Ar/h)
        emp = employeFixtures.createEmployeWithSalary(
                "EMP-WIRING-01",
                "RAKOTO",
                "Jean",
                1,
                new BigDecimal("600000.00"),
                "PERMANENT",
                0
        );

        // Assurer que l'employé est dans la catégorie non-cadre 'A' (éligible aux HS)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE employe_affectation_carriere SET id_categorie_professionnelle = " +
                     "(SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A') " +
                     "WHERE id_employe = ?")) {
            ps.setInt(1, emp.idEmploye);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Workflow E2E Complet : Saisie -> Synthèse -> Gel -> Snapshot Buckets -> Paie Canonique")
    void testEndToEndRuntimeWorkflowFromPunchesToPayroll() throws Exception {
        // =========================================================================
        // ÉTAPE 1 : Saisie de Pointage Quotidien via REST (WF01)
        // Scénario complexe :
        // - Lundi 03/08 : Jour normal (8h)
        // - Mardi 04/08 : Surplus (9h : 08h-12h, 13h-18h -> +1h surplus)
        // - Mercredi 05/08 : Déficit compensable (7h : 08h-12h, 13h-16h -> -1h déficit)
        // - Jeudi 06/08 : Jour normal (8h)
        // - Vendredi 07/08 : Jour normal (8h)
        // [Semaine de base : 8+9+7+8+8 = 40h standard. Le surplus compense le déficit]
        // - Samedi 08/08 : Travail samedi (4h : 08h-12h -> HS30)
        // - Dimanche 09/08 : Travail dimanche (3h : 09h-12h -> HS30 + Dimanche)
        // - Lundi 10/08 : Vacation de nuit cross-midnight (21h-05h -> 8h au total dont 7h de nuit)
        // =========================================================================
        List<SaisiePointageJourDTO> batchPunches = new ArrayList<>();

        // Mon 03/08 (8h)
        batchPunches.add(creerJourDTO("2026-08-03", "08:00", "12:00", "13:00", "17:00", false));
        // Tue 04/08 (9h)
        batchPunches.add(creerJourDTO("2026-08-04", "08:00", "12:00", "13:00", "18:00", false));
        // Wed 05/08 (7h)
        batchPunches.add(creerJourDTO("2026-08-05", "08:00", "12:00", "13:00", "16:00", false));
        // Thu 06/08 (8h)
        batchPunches.add(creerJourDTO("2026-08-06", "08:00", "12:00", "13:00", "17:00", false));
        // Fri 07/08 (8h)
        batchPunches.add(creerJourDTO("2026-08-07", "08:00", "12:00", "13:00", "17:00", false));
        // Sat 08/08 (4h)
        batchPunches.add(creerJourDTO("2026-08-08", "08:00", "12:00", null, null, false));
        // Sun 09/08 (3h)
        batchPunches.add(creerJourDTO("2026-08-09", "09:00", "12:00", null, null, false));
        // Mon 10/08 (8h cross-midnight: 21:00 -> 05:00)
        batchPunches.add(creerJourContinuDTO("2026-08-10", "21:00", "05:00"));

        mockMvc.perform(post("/pointages/saisie/batch")
                        .header("Authorization", "Bearer " + getPointageToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(batchPunches)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Vérification DB : DailyAttendanceCalculator a bien calculé les journées
        try (Connection c = dataSource.getConnection()) {
            String sqlPtg = "SELECT date_pointage, total_heures, heures_nuit FROM pointage_jour " +
                    "WHERE id_employe = ? AND id_periode_paie = ? ORDER BY date_pointage";
            try (PreparedStatement ps = c.prepareStatement(sqlPtg)) {
                ps.setInt(1, emp.idEmploye);
                ps.setInt(2, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        LocalDate d = rs.getDate("date_pointage").toLocalDate();
                        double tot = rs.getDouble("total_heures");
                        double nuit = rs.getDouble("heures_nuit");
                        if (d.equals(LocalDate.of(2026, 8, 4))) {
                            assertThat(tot).isEqualTo(9.0);
                        } else if (d.equals(LocalDate.of(2026, 8, 5))) {
                            assertThat(tot).isEqualTo(7.0);
                        } else if (d.equals(LocalDate.of(2026, 8, 8))) {
                            assertThat(tot).isEqualTo(4.0);
                        } else if (d.equals(LocalDate.of(2026, 8, 9))) {
                            assertThat(tot).isEqualTo(3.0);
                        } else if (d.equals(LocalDate.of(2026, 8, 10))) {
                            assertThat(tot).isEqualTo(8.0);
                            // De 21h à 05h, la nuit légale est de 22h à 05h (7h de nuit)
                            assertThat(nuit).isEqualTo(7.0);
                        }
                    }
                    assertThat(count).isEqualTo(8);
                }
            }
        }

        // =========================================================================
        // ÉTAPE 2 : Synthèse Hebdomadaire via REST (WF03)
        // =========================================================================
        Map<String, Object> reqSynth = new HashMap<>();
        reqSynth.put("idPeriodePaie", idPeriode);
        reqSynth.put("idSite", emp.idSite);

        mockMvc.perform(post("/pointages/synthese/calculer-tout")
                        .header("Authorization", "Bearer " + getPointageToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqSynth)))
                .andExpect(status().isOk());

        // Vérification DB : WeeklyAttendanceCalculator & PeriodWeekSegmenter
        try (Connection c = dataSource.getConnection()) {
            String sqlSem = "SELECT debut_semaine, seuil_heures_troncon, total_heures_travaillees, heures_hs_brutes, heures_hs_nettes, " +
                    "heures_hs_30, heures_hs_50, heures_deficit FROM pointage_semaine_calcule " +
                    "WHERE id_employe = ? AND id_periode_paie = ? ORDER BY debut_semaine";
            try (PreparedStatement ps = c.prepareStatement(sqlSem)) {
                ps.setInt(1, emp.idEmploye);
                ps.setInt(2, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean foundTargetWeek = false;
                    while (rs.next()) {
                        LocalDate semDebut = rs.getDate("debut_semaine").toLocalDate();
                        if (semDebut.equals(LocalDate.of(2026, 8, 3))) {
                            foundTargetWeek = true;
                            System.out.println("DEBUG_WEEK: seuil=" + rs.getDouble("seuil_heures_troncon") +
                                    ", tot=" + rs.getDouble("total_heures_travaillees") +
                                    ", hs_brutes=" + rs.getDouble("heures_hs_brutes") +
                                    ", hs_nettes=" + rs.getDouble("heures_hs_nettes") +
                                    ", hs30=" + rs.getDouble("heures_hs_30") +
                                    ", hs50=" + rs.getDouble("heures_hs_50") +
                                    ", def=" + rs.getDouble("heures_deficit"));
                            // 8+9+7+8+8+4+3 = 47h
                            assertThat(rs.getDouble("total_heures_travaillees")).isEqualTo(47.0);
                            assertThat(rs.getDouble("heures_hs_brutes")).isEqualTo(8.0);
                            assertThat(rs.getDouble("heures_hs_nettes")).isEqualTo(8.0);
                        }
                    }
                    assertThat(foundTargetWeek).isTrue();
                }
            }
        }

        // =========================================================================
        // ÉTAPE 3 : Workflow Période (SUBMIT -> APPROVE) via REST (WF04 & WF05)
        // Le passage en APPROVE déclenche le gel canonique :
        // PointageSyntheseService.gelerPeriode -> WorkIntervalFactory ->
        // AttendanceTemporalPartitioner -> OvertimeProjectionEngine -> pointage_snapshot_bucket
        // =========================================================================
        // SUBMIT (version 1 -> version 2, état A_VALIDER)
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/SUBMIT")
                        .header("Authorization", "Bearer " + getPointageToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("TRANSMIS_DAF"));

        // APPROVE (version 2 -> version 3, état VALIDEE + déclenchement gelerPeriode)
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/APPROVE")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("VALIDEE"));

        // Vérification DB du Snapshot scellé et des QualifiedTimeBuckets (Phase 1C-A)
        try (Connection c = dataSource.getConnection()) {
            // Cumul période gelé
            String sqlCumul = "SELECT id_cumul, statut_snapshot, version_snapshot FROM pointage_cumul_periode " +
                    "WHERE id_employe = ? AND id_periode_paie = ?";
            int idCumul = 0;
            try (PreparedStatement ps = c.prepareStatement(sqlCumul)) {
                ps.setInt(1, emp.idEmploye);
                ps.setInt(2, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("statut_snapshot")).isEqualTo("GELE");
                    assertThat(rs.getInt("version_snapshot")).isGreaterThanOrEqualTo(1);
                    idCumul = rs.getInt("id_cumul");
                }
            }

            // QualifiedTimeBuckets persistés dans pointage_snapshot_bucket
            String sqlBuckets = "SELECT overtime_tier, day_special_type, night_work_type, minutes " +
                    "FROM pointage_snapshot_bucket WHERE id_cumul = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlBuckets)) {
                ps.setInt(1, idCumul);
                try (ResultSet rs = ps.executeQuery()) {
                    long totalBucketMinutes = 0;
                    long hs30Minutes = 0;
                    long sundayMinutes = 0;
                    long nightMinutes = 0;
                    while (rs.next()) {
                        String tier = rs.getString("overtime_tier");
                        String dayType = rs.getString("day_special_type");
                        String nightType = rs.getString("night_work_type");
                        long mins = rs.getLong("minutes");

                        totalBucketMinutes += mins;
                        if ("OVERTIME_30".equals(tier)) hs30Minutes += mins;
                        if ("SUNDAY".equals(dayType)) sundayMinutes += mins;
                        if ("NIGHT_UNCLASSIFIED".equals(nightType) || "HABITUEL".equals(nightType) || "OCCASIONNEL".equals(nightType)) nightMinutes += mins;
                    }

                    // Total = 47h (semaine 1) + 8h (lundi 10/08) = 55h = 3300 minutes
                    assertThat(totalBucketMinutes).isEqualTo(55 * 60);
                    // 12h d'heures supplémentaires à 30% = 720 minutes
                    assertThat(hs30Minutes).isEqualTo(12 * 60);
                    // 3h de travail le dimanche = 180 minutes
                    assertThat(sundayMinutes).isEqualTo(3 * 60);
                    // 7h de nuit (22:00 à 05:00 le 10-11/08) = 420 minutes
                    assertThat(nightMinutes).isEqualTo(7 * 60);
                }
            }
        }

        // =========================================================================
        // ÉTAPE 4 : Calcul de la Paie via REST (WF10)
        // Appelle PaieController -> PayrollService -> PayrollLegalContextResolver ->
        // PayrollExecutionEngine -> OvertimePayCalculator
        // =========================================================================
        Map<String, Object> reqPaie = new HashMap<>();
        reqPaie.put("idPeriodePaie", idPeriode);
        reqPaie.put("simulation", false);

        // 4.1 Première tentative : Le bucket de nuit est NIGHT_UNCLASSIFIED.
        // Le moteur canonique Phase 1C-B bloque l'employé avec NIGHT_CLASSIFICATION_REQUIRED.
        mockMvc.perform(post("/paie/calculer-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqPaie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbBulletinsCalcules").value(0))
                .andExpect(jsonPath("$.employeeOutcomes[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.employeeOutcomes[0].message").value(org.hamcrest.Matchers.containsString("NIGHT_CLASSIFICATION_REQUIRED")));

        // 4.2 Arbitrage RH : Qualification contractuelle/affectation du travail de nuit
        // La qualification RH passe le bucket gelé en 'OCCASIONNEL' dans le snapshot
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE pointage_snapshot_bucket SET night_work_type = 'OCCASIONNEL' " +
                     "WHERE id_periode_paie = ? AND night_work_type = 'NIGHT_UNCLASSIFIED'")) {
            ps.setInt(1, idPeriode);
            int updated = ps.executeUpdate();
            assertThat(updated).isGreaterThan(0);
        }

        // 4.3 Deuxième tentative : Recalcul après arbitrage RH
        // Le calcul s'exécute avec succès et génère le bulletin complet
        mockMvc.perform(post("/paie/calculer-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqPaie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbBulletinsCalcules").value(1));

        // Vérification DB du Bulletin de Paie et de ses lignes de valorisation monétaire
        try (Connection c = dataSource.getConnection()) {
            String sqlBull = "SELECT id_bulletin, salaire_base, total_gains_bruts, net_a_payer FROM bulletin_paie " +
                    "WHERE id_employe = ? AND id_periode_paie = ?";
            int idBulletin = 0;
            try (PreparedStatement ps = c.prepareStatement(sqlBull)) {
                ps.setInt(1, emp.idEmploye);
                ps.setInt(2, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBigDecimal("salaire_base")).isEqualByComparingTo("600000.00");
                    assertThat(rs.getBigDecimal("total_gains_bruts")).isGreaterThan(new BigDecimal("600000.00"));
                    idBulletin = rs.getInt("id_bulletin");
                }
            }

            // Vérification détaillée des lignes de majoration créées par OvertimePayCalculator
            String sqlLignes = "SELECT code_rubrique, libelle_rubrique, montant_salarial FROM bulletin_paie_ligne " +
                    "WHERE id_bulletin = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlLignes)) {
                ps.setInt(1, idBulletin);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, BigDecimal> montantsLignes = new HashMap<>();
                    while (rs.next()) {
                        montantsLignes.put(rs.getString("code_rubrique"), rs.getBigDecimal("montant_salarial"));
                    }

                    // 1. Salaire de base contractuel présent
                    assertThat(montantsLignes).containsKey("SALAIRE_BASE");
                    assertThat(montantsLignes.get("SALAIRE_BASE")).isEqualByComparingTo("600000.00");

                    // 2. Ligne HEURES_SUP_30 présente (12h valorisées à taux horaire * 1.30)
                    assertThat(montantsLignes).containsKey("HEURES_SUP_30");
                    BigDecimal montantHs30 = montantsLignes.get("HEURES_SUP_30");
                    assertThat(montantHs30).isGreaterThan(BigDecimal.ZERO);

                    // 3. Ligne MAJORATION_NUIT présente (7h de nuit valorisées à taux horaire * 0.30)
                    assertThat(montantsLignes).containsKey("MAJORATION_NUIT");
                    BigDecimal montantNuit = montantsLignes.get("MAJORATION_NUIT");
                    assertThat(montantNuit).isGreaterThan(BigDecimal.ZERO);

                    // 4. Ligne HEURES_DIMANCHE présente (3h dimanche valorisées à taux horaire * 0.40)
                    assertThat(montantsLignes).containsKey("HEURES_DIMANCHE");
                    BigDecimal montantDimanche = montantsLignes.get("HEURES_DIMANCHE");
                    assertThat(montantDimanche).isGreaterThan(BigDecimal.ZERO);

                    // Vérification de la cohérence mathématique :
                    // Taux horaire = 600000 / 173.33 = 3461.60503... Ar/h
                    // HS 30% (12h à 130%) = 12 * 3461.60503 * 1.30 = ~54 001.04 Ar
                    // Nuit 30% (7h à 30%) = 7 * 3461.60503 * 0.30 = ~7 269.37 Ar
                    // Dimanche 40% (3h à 40%) = 3 * 3461.60503 * 0.40 = ~4 153.93 Ar
                    assertThat(montantHs30).isBetween(new BigDecimal("50000"), new BigDecimal("60000"));
                    assertThat(montantNuit).isBetween(new BigDecimal("7000"), new BigDecimal("8000"));
                    assertThat(montantDimanche).isBetween(new BigDecimal("4000"), new BigDecimal("5000"));
                }
            }
        }
    }

    private SaisiePointageJourDTO creerJourDTO(String date, String em, String sm, String ea, String sa, boolean horaireContinu) {
        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(emp.idEmploye);
        dto.setIdSite(emp.idSite);
        dto.setIdPeriodePaie(idPeriode);
        dto.setDatePointage(date);
        dto.setHeureEntreeMatin(em);
        dto.setHeureSortieMatin(sm);
        dto.setHeureEntreeAm(ea);
        dto.setHeureSortieAm(sa);
        dto.setHoraireContinu(horaireContinu);
        dto.setIndemniteRepasRecue(true);
        return dto;
    }

    private SaisiePointageJourDTO creerJourContinuDTO(String date, String em, String sa) {
        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(emp.idEmploye);
        dto.setIdSite(emp.idSite);
        dto.setIdPeriodePaie(idPeriode);
        dto.setDatePointage(date);
        dto.setHeureEntreeMatin(em);
        dto.setHeureSortieAm(sa);
        dto.setHoraireContinu(true);
        dto.setIndemniteRepasRecue(true);
        return dto;
    }
}
