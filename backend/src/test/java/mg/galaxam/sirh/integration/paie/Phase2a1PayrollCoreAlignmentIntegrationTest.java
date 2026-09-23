package mg.galaxam.sirh.integration.paie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.paie.application.StcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Phase 2A-1 : Test d'intégration Spring + PostgreSQL réel.
 * 1. Preuve d'étanchéité temporelle TCONGE : le calcul historique ne lit jamais le futur.
 * 2. Scénario runtime complet : Contrat avec entrée en cours de période + Pointage + Snapshot + Paie.
 */
public class Phase2a1PayrollCoreAlignmentIntegrationTest extends BaseIntegrationTest {

    private TestEmployeFixtures.CreatedEmploye emp;
    private int idPeriode;
    private final LocalDate debutPeriode = LocalDate.of(2026, 8, 1);
    private final LocalDate finPeriode = LocalDate.of(2026, 8, 31);

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();

        idPeriode = employeFixtures.createPeriodePaie("P-2026-08-2A1", debutPeriode, finPeriode, "EN_COURS");

        emp = employeFixtures.createEmployeWithSalary(
                "EMP-2A1-INT",
                "RAZAFY",
                "Phase 2A1",
                1,
                new BigDecimal("600000.00"),
                "PERMANENT",
                0
        );

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE employe_affectation_carriere SET salaire_base = 600000.00, " +
                     "id_categorie_professionnelle = (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A' LIMIT 1) " +
                     "WHERE id_employe = ?")) {
            ps.setInt(1, emp.idEmploye);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // 1. TEST TCONGE HISTORIQUE POSTGRESQL (Section 19, 20, 36)
    // =========================================================================

    @Test
    @DisplayName("Phase 2A-1 : Étanchéité temporelle TCONGE - Une paie historique ne lit JAMAIS une valeur future")
    void testTcongeHistoricalTemporalPartitioning() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Créer une période antérieure (2026-06)
            int pJuin = employeFixtures.createPeriodePaie("P-2026-06", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "VALIDEE");
            int pJuil = employeFixtures.createPeriodePaie("P-2026-07", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "VALIDEE");
            int pAout = employeFixtures.createPeriodePaie("P-2026-08-HIST", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "VALIDEE");
            int pSept = employeFixtures.createPeriodePaie("P-2026-09-HIST", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "VALIDEE");
            int pOct = employeFixtures.createPeriodePaie("P-2026-10-FUTUR", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "VALIDEE");

            // Insérer dans conge_journal_droit les taux historiques et futurs :
            // N-3 (Juin) : 20 000 Ar
            // N-2 (Juillet) : 22 000 Ar
            // N-1 (Août) : 24 000 Ar
            // N   (Septembre) : 26 000 Ar
            // N+1 (Octobre - FUTUR) : 40 000 Ar
            String sqlInsert = "INSERT INTO conge_journal_droit (" +
                    "id_employe, id_periode_paie, annee, mois, salaire_base, jours_travailles_formule, " +
                    "jours_droit_acquis, base_valorisation, valorisation_mensuelle, cumul_jours_droit, " +
                    "cumul_valorisation, taux_journalier_conge" +
                    ") VALUES (?, ?, ?, ?, 600000.00, 30.0, 2.5, 600000.00, 50000.00, 2.5, 50000.00, ?)";

            try (PreparedStatement ps = c.prepareStatement(sqlInsert)) {
                // Juin (2026-06) -> 20 000
                ps.setInt(1, emp.idEmploye); ps.setInt(2, pJuin); ps.setInt(3, 2026); ps.setInt(4, 6);
                ps.setBigDecimal(5, new BigDecimal("20000.00")); ps.executeUpdate();

                // Juillet (2026-07) -> 22 000
                ps.setInt(1, emp.idEmploye); ps.setInt(2, pJuil); ps.setInt(3, 2026); ps.setInt(4, 7);
                ps.setBigDecimal(5, new BigDecimal("22000.00")); ps.executeUpdate();

                // Août (2026-08) -> 24 000
                ps.setInt(1, emp.idEmploye); ps.setInt(2, pAout); ps.setInt(3, 2026); ps.setInt(4, 8);
                ps.setBigDecimal(5, new BigDecimal("24000.00")); ps.executeUpdate();

                // Septembre (2026-09) -> 26 000
                ps.setInt(1, emp.idEmploye); ps.setInt(2, pSept); ps.setInt(3, 2026); ps.setInt(4, 9);
                ps.setBigDecimal(5, new BigDecimal("26000.00")); ps.executeUpdate();

                // Octobre (2026-10 - FUTUR) -> 40 000
                ps.setInt(1, emp.idEmploye); ps.setInt(2, pOct); ps.setInt(3, 2026); ps.setInt(4, 10);
                ps.setBigDecimal(5, new BigDecimal("40000.00")); ps.executeUpdate();
            }

            // 1. Résolution pour la paie de SEPTEMBRE 2026 (dateReference = 2026-09-01) :
            // Doit strictement ignorer Octobre (40 000) et Septembre (26 000).
            // Doit retourner le dernier taux antérieur applicable : AOÛT (24 000 Ar) !
            BigDecimal tauxSept = StcService.getTauxJournalierApplicable(
                    emp.idEmploye, LocalDate.of(2026, 9, 1), new BigDecimal("600000.00"), c);
            assertThat(tauxSept).isEqualByComparingTo("24000.00");

            // 2. Résolution pour la paie d'AOÛT 2026 (dateReference = 2026-08-01) :
            // Doit retourner JUILLET (22 000 Ar) !
            BigDecimal tauxAout = StcService.getTauxJournalierApplicable(
                    emp.idEmploye, LocalDate.of(2026, 8, 1), new BigDecimal("600000.00"), c);
            assertThat(tauxAout).isEqualByComparingTo("22000.00");

            // 3. Résolution pour la paie de JUIN 2026 (dateReference = 2026-06-01) :
            // Aucun historique antérieur à Juin -> Amorçage canonique Base / 30 = 600 000 / 30 = 20 000 Ar
            BigDecimal tauxJuin = StcService.getTauxJournalierApplicable(
                    emp.idEmploye, LocalDate.of(2026, 6, 1), new BigDecimal("600000.00"), c);
            assertThat(tauxJuin).isEqualByComparingTo("20000.00");

            // 4. PREUVE : L'existence de la valeur future Octobre (40 000) ne pollue jamais le calcul de Septembre !
            assertThat(tauxSept).isNotEqualByComparingTo("40000.00");
            assertThat(tauxSept).isNotEqualByComparingTo("26000.00");
        }
    }

    // =========================================================================
    // 2. TEST RUNTIME COMPLET SPRING + POSTGRESQL (Section 37)
    // =========================================================================

    @Test
    @DisplayName("Phase 2A-1 : Pipeline Runtime Complet - Prorata /30 + Pointage + Snapshot + Congé + HS30")
    void testCompleteProductionPayrollPipeline() throws Exception {
        // 1. Configurer un contrat débutant en cours de mois (le 16 août 2026)
        // Période : 01/08/2026 au 31/08/2026 (31 jours)
        // Jours sous contrat inclusifs : 31 - 16 + 1 = 16 jours
        try (Connection c = dataSource.getConnection()) {
            // Mettre à jour le contrat et l'affectation existants pour démarrer le 16 août 2026
            try (PreparedStatement psCt = c.prepareStatement(
                    "UPDATE contrat_travail SET date_debut = '2026-08-16' WHERE id_employe = ?")) {
                psCt.setInt(1, emp.idEmploye);
                psCt.executeUpdate();
            }
            try (PreparedStatement psAff = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET date_debut = '2026-08-16' WHERE id_employe = ?")) {
                psAff.setInt(1, emp.idEmploye);
                psAff.executeUpdate();
            }

            // Insérer un historique TCONGE pour juillet (2026-07) -> TCONGE = 25 000 Ar
            int pJuil = employeFixtures.createPeriodePaie("P-2026-07-RT", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "VALIDEE");
            String sqlCongeHist = "INSERT INTO conge_journal_droit (" +
                    "id_employe, id_periode_paie, annee, mois, salaire_base, jours_travailles_formule, " +
                    "jours_droit_acquis, base_valorisation, valorisation_mensuelle, cumul_jours_droit, " +
                    "cumul_valorisation, taux_journalier_conge" +
                    ") VALUES (?, ?, 2026, 7, 600000.00, 30.0, 2.5, 600000.00, 62500.00, 2.5, 62500.00, 25000.00)";
            try (PreparedStatement psC = c.prepareStatement(sqlCongeHist)) {
                psC.setInt(1, emp.idEmploye);
                psC.setInt(2, pJuil);
                psC.executeUpdate();
            }
        }

        // 2. Saisie des pointages réels du salarié (sous contrat du 16 au 31)
        List<SaisiePointageJourDTO> batchPunches = new ArrayList<>();

        // Mon 17/08 : 8h normales (08:00 - 17:00)
        batchPunches.add(creerJourDTO("2026-08-17", "08:00", "12:00", "13:00", "17:00", false));
        // Tue 18/08 : 8h normales (08:00 - 17:00)
        batchPunches.add(creerJourDTO("2026-08-18", "08:00", "12:00", "13:00", "17:00", false));
        // Wed 19/08 : 8h normales (08:00 - 17:00)
        batchPunches.add(creerJourDTO("2026-08-19", "08:00", "12:00", "13:00", "17:00", false));
        // Thu 20/08 : 8h normales (08:00 - 17:00)
        batchPunches.add(creerJourDTO("2026-08-20", "08:00", "12:00", "13:00", "17:00", false));
        // Fri 21/08 : 8h normales (08:00 - 17:00)
        batchPunches.add(creerJourDTO("2026-08-21", "08:00", "12:00", "13:00", "17:00", false));
        // Sat 22/08 : 4h de surplus (HS30) (08:00 - 12:00)
        batchPunches.add(creerJourDTO("2026-08-22", "08:00", "12:00", null, null, false));

        mockMvc.perform(post("/pointages/saisie/batch")
                        .header("Authorization", "Bearer " + getPointageToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(batchPunches)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Synthèse des pointages & Approbation période (gel du snapshot)
        Map<String, Object> reqSynth = new HashMap<>();
        reqSynth.put("idPeriodePaie", idPeriode);
        reqSynth.put("idSite", emp.idSite);

        mockMvc.perform(post("/pointages/synthese/calculer-tout")
                        .header("Authorization", "Bearer " + getPointageToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqSynth)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/SUBMIT")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/APPROVE")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isOk());

        // 4. Calcul de paie réel via le controller REST de production
        Map<String, Object> reqPaie = new HashMap<>();
        reqPaie.put("idPeriodePaie", idPeriode);
        reqPaie.put("simulation", false);

        mockMvc.perform(post("/paie/calculer-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqPaie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbBulletinsCalcules").value(1));

        // 5. Audit contradictoire du bulletin produit en BDD
        try (Connection c = dataSource.getConnection()) {
            String sqlBull = "SELECT id_bulletin, salaire_base, total_gains_bruts " +
                    "FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ?";
            int idBulletin;
            BigDecimal totalGainsBruts;
            try (PreparedStatement psB = c.prepareStatement(sqlBull)) {
                psB.setInt(1, emp.idEmploye);
                psB.setInt(2, idPeriode);
                try (ResultSet rsB = psB.executeQuery()) {
                    assertThat(rsB.next()).isTrue();
                    idBulletin = rsB.getInt("id_bulletin");
                    totalGainsBruts = rsB.getBigDecimal("total_gains_bruts");
                }
            }

            // Vérifier les lignes de bulletin
            String sqlLignes = "SELECT code_rubrique, base_calcul, taux_salarial, montant_salarial, sens, categorie " +
                    "FROM bulletin_paie_ligne WHERE id_bulletin = ? ORDER BY ordre";
            Map<String, BigDecimal> montants = new HashMap<>();
            try (PreparedStatement psL = c.prepareStatement(sqlLignes)) {
                psL.setInt(1, idBulletin);
                try (ResultSet rsL = psL.executeQuery()) {
                    while (rsL.next()) {
                        montants.put(rsL.getString("code_rubrique"), rsL.getBigDecimal("montant_salarial"));
                    }
                }
            }

            // SALAIRE_BASE : Prorata temporis /30
            // 16 jours sous contrat (16/08 au 31/08 inclus) : 600 000 * 16 / 30 = 320 000.00 Ar
            assertThat(montants.get("SALAIRE_BASE")).isEqualByComparingTo("320000.00");

            // HS30 : 4 heures travaillées le samedi -> 4 * 3461.6050 * 1.30 = 18 000.35 Ar
            assertThat(montants.get("HEURES_SUP_30")).isEqualByComparingTo("18000.35");

            // Total Gains Bruts = 320 000.00 + 18 000.35 = 338 000.35 Ar
            assertThat(totalGainsBruts).isEqualByComparingTo("338000.35");
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
}
