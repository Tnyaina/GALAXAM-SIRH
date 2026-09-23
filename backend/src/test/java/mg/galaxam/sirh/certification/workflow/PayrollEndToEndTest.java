package mg.galaxam.sirh.certification.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.certification.dataset.CertificationDataset;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.domain.dto.PayrollBatchResult;
import mg.galaxam.sirh.paie.application.PayrollService;
import mg.galaxam.sirh.oracle.PayrollIndependentOracle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CERTIFICATION WORKFLOW PAIE END-TO-END — CYCLE DE VIE INTÉGRAL EMP006_COMPLEX")
public class PayrollEndToEndTest extends BaseIntegrationTest {

    @Autowired
    private PayrollService payrollService;

    private int idPeriode;
    private LocalDate debutPeriode;
    private LocalDate finPeriode;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanTransactionalData();

        debutPeriode = LocalDate.of(2026, 9, 1);
        finPeriode = LocalDate.of(2026, 9, 30);

        idPeriode = employeFixtures.createPeriodePaie(
                "PER-2026-09-E2E",
                debutPeriode,
                finPeriode,
                "EN_COURS"
        );
    }

    private int resolveTypeCongeId(String code) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_type_conge FROM ref_type_conge WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("Type de congé introuvable : " + code);
    }

    @Test
    @DisplayName("Cycle E2E Complet EMP006_COMPLEX : RH -> Congés -> Pointage -> Simulation -> Calcul -> Validation -> Clôture == Oracle Indépendant")
    void testCycleCompletEmp006Complex() throws Exception {
        CertificationDataset.EmployeeProfile profile = CertificationDataset.EMP006_COMPLEX;

        // =========================================================================
        // 1. CRÉATION EMPLOYÉ & CARRIÈRE (Site 2 TMM avec OMSI 1.5% salarial / 5.5% patronal)
        // =========================================================================
        CreatedEmploye emp = employeFixtures.createEmployeWithSalary(
                profile.matricule(), profile.nom(), profile.prenom(), 2,
                profile.salaireBaseContractuel(), "PERMANENT", profile.nbEnfants()
        );

        // =========================================================================
        // 2. CONGÉS PAYÉS : Crédit Solde (TCONGE 20 000 Ar = 600k / 30) -> Demande 1j (15/09/2026) -> Validation
        // =========================================================================
        employeFixtures.crediterSoldeConge(emp.idEmploye, 15.0, new BigDecimal("20000.00"));

        LocalDate dateConge = LocalDate.of(2026, 9, 15);
        Map<String, Object> reqConge = new HashMap<>();
        reqConge.put("idEmploye", emp.idEmploye);
        reqConge.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        reqConge.put("dateDebut", dateConge.toString());
        reqConge.put("dateFin", dateConge.toString());
        reqConge.put("derogationDelaiPreavis", true);
        reqConge.put("commentaire", "Journée congé payé EMP006");

        String resCongeStr = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(reqConge)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode jsonConge = objectMapper.readTree(resCongeStr);
        int idDemandeConge = jsonConge.get("idDemandeConge").asInt();

        mockMvc.perform(post("/conges/demandes/" + idDemandeConge + "/valider")
                .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk());

        // =========================================================================
        // 3. VARIABLES DE PAIE : Prime Rendement Imposable (AUTIMP) + Acompte
        // =========================================================================
        try (Connection c = dataSource.getConnection()) {
            // Prime AUTIMP 40 000 Ar (sens GAIN)
            String sqlAutimp = "INSERT INTO paie_avance_acompte (" +
                    "id_employe, id_periode_paie, type_mouvement, sens, montant, date_demande, statut, motif) " +
                    "VALUES (?, ?, 'AUTIMP', 'GAIN', 40000.00, ?, 'APPROUVE', 'Prime rendement septembre')";
            try (PreparedStatement ps = c.prepareStatement(sqlAutimp)) {
                ps.setInt(1, emp.idEmploye);
                ps.setInt(2, idPeriode);
                ps.setDate(3, Date.valueOf(debutPeriode));
                ps.executeUpdate();
            }

            // Acompte 50 000 Ar (sens RETENUE)
            String sqlAcompte = "INSERT INTO paie_avance_acompte (" +
                    "id_employe, id_periode_paie, type_mouvement, sens, montant, date_demande, statut, motif) " +
                    "VALUES (?, ?, 'ACOMPTE_15', 'RETENUE', 50000.00, ?, 'APPROUVE', 'Acompte quinzaine')";
            try (PreparedStatement ps = c.prepareStatement(sqlAcompte)) {
                ps.setInt(1, emp.idEmploye);
                ps.setInt(2, idPeriode);
                ps.setDate(3, Date.valueOf(LocalDate.of(2026, 9, 15)));
                ps.executeUpdate();
            }
        }

        // =========================================================================
        // 4. POINTAGE : Saisie et Snapshot Scellé (statut_snapshot = 'GELE') avec Buckets
        // =========================================================================
        // Valeurs conformes à EMP006_COMPLEX_EXPECTED.md :
        // 186h travaillées, 2h retards/absence (120 min), 10h HS30, 4h HS50, 2h nuit, 4h dimanche, 30 000 Ar transport
        int idCumul;
        String sqlCumul = "INSERT INTO pointage_cumul_periode (" +
                "id_periode_paie, id_employe, version_snapshot, statut_snapshot, nb_jours_ouvrables, nb_jours_presents, " +
                "total_heures_travaillees, heures_absence_retenues, total_minutes_retard, " +
                "heures_hs_30, heures_hs_50, heures_nuit_total, heures_dimanche_total, " +
                "nb_jours_conge, montant_deplacement, gele_at) " +
                "VALUES (?, ?, 1, 'GELE', 22, 22, 186.00, 2.00, 120, 10.00, 4.00, 2.00, 4.00, 1.00, 30000.00, CURRENT_TIMESTAMP) " +
                "RETURNING id_cumul";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sqlCumul)) {
            ps.setInt(1, idPeriode);
            ps.setInt(2, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idCumul = rs.getInt(1);
            }

            // Insertion des buckets qualifiés unitaires pour calcul exact
            String sqlBucket = "INSERT INTO pointage_snapshot_bucket (" +
                    "id_cumul, id_periode_paie, date_debut, date_fin, minutes, overtime_tier, day_special_type, night_work_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement psB = c.prepareStatement(sqlBucket)) {
                // 1. Dimanche (4h = 240 min)
                psB.setInt(1, idCumul);
                psB.setInt(2, idPeriode);
                psB.setTimestamp(3, java.sql.Timestamp.valueOf("2026-09-06 08:00:00"));
                psB.setTimestamp(4, java.sql.Timestamp.valueOf("2026-09-06 12:00:00"));
                psB.setInt(5, 240);
                psB.setString(6, "REGULAR");
                psB.setString(7, "SUNDAY");
                psB.setString(8, "NONE");
                psB.addBatch();

                // 2. Nuit (2h = 120 min)
                psB.setInt(1, idCumul);
                psB.setInt(2, idPeriode);
                psB.setTimestamp(3, java.sql.Timestamp.valueOf("2026-09-08 22:00:00"));
                psB.setTimestamp(4, java.sql.Timestamp.valueOf("2026-09-09 00:00:00"));
                psB.setInt(5, 120);
                psB.setString(6, "REGULAR");
                psB.setString(7, "STANDARD");
                psB.setString(8, "HABITUEL");
                psB.addBatch();

                // 3. HS 30% (10h = 600 min)
                psB.setInt(1, idCumul);
                psB.setInt(2, idPeriode);
                psB.setTimestamp(3, java.sql.Timestamp.valueOf("2026-09-11 08:00:00"));
                psB.setTimestamp(4, java.sql.Timestamp.valueOf("2026-09-11 18:00:00"));
                psB.setInt(5, 600);
                psB.setString(6, "OVERTIME_30");
                psB.setString(7, "STANDARD");
                psB.setString(8, "NONE");
                psB.addBatch();

                // 4. HS 50% (4h = 240 min)
                psB.setInt(1, idCumul);
                psB.setInt(2, idPeriode);
                psB.setTimestamp(3, java.sql.Timestamp.valueOf("2026-09-18 08:00:00"));
                psB.setTimestamp(4, java.sql.Timestamp.valueOf("2026-09-18 12:00:00"));
                psB.setInt(5, 240);
                psB.setString(6, "OVERTIME_50");
                psB.setString(7, "STANDARD");
                psB.setString(8, "NONE");
                psB.addBatch();

                psB.executeBatch();
            }
        }

        // =========================================================================
        // 5. ORACLE INDÉPENDANT : Calcul a priori strict
        // =========================================================================
        // Configuration avec OMSI (1.5% salarial / 5.5% patronal)
        PayrollIndependentOracle.LegalConfigExpected config2026Omsi = new PayrollIndependentOracle.LegalConfigExpected(
                new BigDecimal("300000.00"),
                new BigDecimal("0.01"),
                new BigDecimal("0.13"),
                new BigDecimal("8.00"),
                new BigDecimal("0.015"), // OMSI salarial
                new BigDecimal("0.055"), // OMSI patronal
                true,
                new BigDecimal("2400000.00"),
                new BigDecimal("0.01"),
                true,
                new BigDecimal("20.00"),
                new BigDecimal("350000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("2000.00"),
                PayrollIndependentOracle.configSeptembre2026().tranchesIrsa()
        );

        PayrollIndependentOracle.PayrollExpectedResult oracle = PayrollIndependentOracle.calculate(
                profile.salaireBaseContractuel(),
                null,
                new BigDecimal("173.33"),
                120L, // 2h absence
                BigDecimal.ONE, // 1 jour congé
                new BigDecimal("20000.00"), // TCONGE journalier (600 000 / 30)
                new BigDecimal("10.00"), // HS 30%
                new BigDecimal("4.00"),  // HS 50%
                new BigDecimal("2.00"),  // Nuit
                new BigDecimal("4.00"),  // Dimanche
                BigDecimal.ZERO,
                new BigDecimal("40000.00"), // AUTIMP
                new BigDecimal("30000.00"), // Transport
                new BigDecimal("50000.00"), // Acompte
                profile.nbEnfants(),
                profile.estCadre(),
                config2026Omsi
        );

        // =========================================================================
        // 6. PHASE SIMULATION (simulation = true) : Aucun effet de bord en DB
        // =========================================================================
        BulletinPaieComplet simulationResult = payrollService.calculerBulletinEmploye(
                emp.idEmploye, idPeriode, true, 1
        );
        assertNotNull(simulationResult, "Le résultat de simulation doit être retourné");
        assertEquals(oracle.netAPayer(), simulationResult.getBulletin().getNetAPayer(),
                "Simulation : Net à payer identique à l'oracle");

        // Preuve d'absence d'effets de bord : 0 bulletin persisté en base
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1), "Aucun bulletin ne doit être persisté lors d'une simulation");
            }
        }

        // =========================================================================
        // 7. PHASE CALCUL RÉEL (simulation = false) : Persistance & Statut CALCULE
        // =========================================================================
        BulletinPaieComplet reelResult = payrollService.calculerBulletinEmploye(
                emp.idEmploye, idPeriode, false, 1
        );
        assertNotNull(reelResult, "Le bulletin calculé doit exister");
        int idBulletin = reelResult.getBulletin().getIdBulletin();
        assertTrue(idBulletin > 0, "L'idBulletin doit être généré");

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT statut FROM bulletin_paie WHERE id_bulletin = ?")) {
            ps.setInt(1, idBulletin);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals("CALCULE", rs.getString("statut"), "Le statut initial après calcul doit être CALCULE");
            }
        }

        // =========================================================================
        // 8. PHASE VALIDATION : Statut VALIDE & Snapshot CONSOMME
        // =========================================================================
        PayrollBatchResult batchValidation = payrollService.validerPaiePeriode(idPeriode, 1);
        assertNotNull(batchValidation, "Le résultat de validation batch ne doit pas être nul");

        try (Connection c = dataSource.getConnection()) {
            // Vérification statut bulletin
            try (PreparedStatement ps = c.prepareStatement("SELECT statut FROM bulletin_paie WHERE id_bulletin = ?")) {
                ps.setInt(1, idBulletin);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals("VALIDE", rs.getString("statut"), "Le statut après validation doit être VALIDE");
                }
            }

            // Vérification statut snapshot pointage
            try (PreparedStatement ps = c.prepareStatement("SELECT statut_snapshot FROM pointage_cumul_periode WHERE id_periode_paie = ? AND id_employe = ?")) {
                ps.setInt(1, idPeriode);
                ps.setInt(2, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals("CONSOMME", rs.getString("statut_snapshot"), "Le snapshot de pointage doit passer à CONSOMME");
                }
            }
        }

        // =========================================================================
        // 9. PHASE CLÔTURE & SCELLAGE IMMUABLE : Statut CLOTURE
        // =========================================================================
        try (Connection c = dataSource.getConnection()) {
            payrollService.cloturerDonneesPaie(idPeriode, 1, c);
            try (PreparedStatement ps = c.prepareStatement("SELECT statut FROM bulletin_paie WHERE id_bulletin = ?")) {
                ps.setInt(1, idBulletin);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals("CLOTURE", rs.getString("statut"), "Le statut après clôture doit être CLOTURE");
                }
            }
        }

        // =========================================================================
        // 10. CERTIFICATION NUMÉRIQUE CONTRE ORACLE INDÉPENDANT (DELTA STRICT = 0)
        // =========================================================================
        BulletinPaieComplet complet;
        try (Connection c = dataSource.getConnection()) {
            complet = payrollService.obtenirBulletinComplet(idBulletin, c);
        }
        BulletinPaie bp = complet.getBulletin();

        assertEquals(new BigDecimal("600000.00"), bp.getSalaireBase(), "Salaire de base contractuel");
        assertEquals(oracle.totalGainsBruts(), bp.getTotalGainsBruts(), "Salaire brut social (706 462.81 Ar)");
        assertEquals(oracle.cnapsSalarial().add(oracle.santeSalarial()), bp.getTotalCotisationsSalariales(), "Total cotisations salariales (CNaPS 1% + OMSI 1.5%)");
        assertEquals(oracle.salaireNetImposable(), bp.getSalaireNetImposable(), "Net imposable social (688 801.24 Ar)");
        assertEquals(oracle.baseIrsaArrondie(), bp.getBaseIrsaArrondie(), "Base fiscale IRSA arrondie (623 000.00 Ar)");
        assertEquals(oracle.irsaNet(), bp.getIrsaNet(), "IRSA net après réduction 2 enfants (28 100.00 Ar)");
        assertEquals(oracle.acomptesEtAvances(), bp.getTotalRetenuesAcomptePret(), "Acompte déduit (50 000.00 Ar)");
        assertEquals(oracle.indemnitesNonImposables(), bp.getTotalIndemnitesNonImposables(), "Indemnité transport (30 000.00 Ar)");
        assertEquals(oracle.netAPayer(), bp.getNetAPayer(), "Net à payer final arrondi (640 700.00 Ar)");

        // Assertions détaillées sur les lignes du bulletin
        Map<String, BigDecimal> lignesMap = new HashMap<>();
        for (var l : complet.getLignes()) {
            lignesMap.put(l.getCodeRubrique(), l.getMontantSalarial());
        }

        assertEquals(oracle.retenueAbsence(), lignesMap.get("RETENUE_ABSENCE"), "Ligne RETENUE_ABSENCE (6 923.21 Ar)");
        assertEquals(oracle.deductionCongeBase(), lignesMap.get("DEDUCTION_CONGE_BASE"), "Ligne DEDUCTION_CONGE_BASE (20 000.00 Ar)");
        assertEquals(oracle.indemniteConge(), lignesMap.get("INDEMNITE_CONGE_PAYE"), "Ligne INDEMNITE_CONGE_PAYE (20 000.00 Ar)");
        assertEquals(oracle.montantHs30(), lignesMap.get("HEURES_SUP_30"), "Ligne HEURES_SUP_30 (45 000.86 Ar)");
        assertEquals(oracle.montantHs50(), lignesMap.get("HEURES_SUP_50"), "Ligne HEURES_SUP_50 (20 769.63 Ar)");
        assertEquals(oracle.montantNuit(), lignesMap.get("MAJORATION_NUIT"), "Ligne MAJORATION_NUIT (2 076.96 Ar)");
        assertEquals(oracle.montantDimanche(), lignesMap.get("HEURES_DIMANCHE"), "Ligne HEURES_DIMANCHE (5 538.57 Ar)");
        assertEquals(oracle.primesImposables(), lignesMap.get("AUTIMP"), "Ligne AUTIMP prime de rendement (40 000.00 Ar)");
        assertEquals(oracle.cnapsSalarial(), lignesMap.get("COTISATION_CNAPS_SAL"), "Ligne CNAPS salariale 1% (7 064.63 Ar)");
        assertEquals(oracle.santeSalarial(), lignesMap.get("COTISATION_OMSI_SAL"), "Ligne OMSI salariale 1.5% (10 596.94 Ar)");
        assertEquals(oracle.indemnitesNonImposables(), lignesMap.get("INDEMNITE_TRANSPORT"), "Ligne INDEMNITE_TRANSPORT (30 000.00 Ar)");
        assertEquals(oracle.acomptesEtAvances(), lignesMap.get("RETENUE_ACOMPTE_15"), "Ligne RETENUE_ACOMPTE_15 (50 000.00 Ar)");
    }
}
