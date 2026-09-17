package mg.galaxam.sirh.integration.paie;

import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.OvertimePayCalculator;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.pointage.service.PointageSyntheseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.engine.BucketValuationResult;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy;
import mg.galaxam.sirh.model.pointage.calculation.OvertimeTier;

/**
 * Test d'intégration PostgreSQL réel pour la valorisation monétaire Phase 1C-B (Section 27).
 * Vérifie l'enchaînement :
 * Configuration taux historique -> Pointage -> Calcul -> Gel -> QualifiedTimeBucket -> Préparation Paie -> Valorisation
 * Et démontre la reproductibilité stricte face à une évolution future des taux.
 */
public class Phase1cBPostgreSqlValuationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private DataSource dataSource;

    private TestEmployeFixtures.CreatedEmploye emp;
    private int idPeriode;
    private final OvertimePayCalculator overtimeCalculator = new OvertimePayCalculator();

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();
        LocalDate dEmb = LocalDate.of(2026, 1, 1);
        emp = employeFixtures.createActiveEmploye("EMP_1CB", "VALUATION", "Paul", 1, dEmb);

        LocalDate deb = LocalDate.of(2026, 9, 1);
        LocalDate fin = LocalDate.of(2026, 9, 30);
        idPeriode = employeFixtures.createPeriodePaie("P-2026-09", deb, fin, "EN_COURS");
    }

    @Test
    @DisplayName("Section 27: Pipeline PostgreSQL complet & reproductibilité historique face à un changement futur de taux")
    void testHistoricalRatesAndValuationReproducibility() throws Exception {
        // Dimanche 20 Septembre 2026 : 8h travaillées (08:00 -> 16:00, horaire continu)
        LocalDate dateDimanche = LocalDate.of(2026, 9, 20);

        // 1. Insérer le pointage
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, " +
                     "heure_entree_matin, heure_sortie_am, total_heures, horaire_continu_jour, id_statut_jour) " +
                     "VALUES (?, ?, ?, ?, '08:00', '16:00', 8.0, TRUE, 1)")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idPeriode);
            ps.setInt(3, emp.idSite);
            ps.setDate(4, java.sql.Date.valueOf(dateDimanche));
            ps.executeUpdate();
        }

        // 2. Geler la période pour sceller le snapshot Pointage et ses QualifiedTimeBuckets
        try (Connection c = dataSource.getConnection()) {
            pointageSyntheseService.gelerPeriode(idPeriode, 1, c);
        }

        // 3. Préparer l'input de Paie
        PayrollCalculationInput input;
        try (Connection c = dataSource.getConnection()) {
            input = payrollService.preparerInput(emp.idEmploye, idPeriode, false, c);
        }

        assertThat(input.getAttendanceInput()).isNotNull();
        assertThat(input.getAttendanceInput().qualifiedTimeBuckets()).isNotEmpty();
        assertThat(input.getMajorationDimanche()).isEqualByComparingTo("0.40");

        BigDecimal hourlyRate = input.getTauxHoraireEffectif();
        assertThat(hourlyRate).isGreaterThan(BigDecimal.ZERO);

        // 4. Valorisation monétaire du snapshot de Septembre 2026
        OvertimePayCalculator.Result initialResult = overtimeCalculator.calculate(input, hourlyRate);
        assertThat(initialResult.dimanche()).isGreaterThan(BigDecimal.ZERO);

        // 5. Simuler une modification FUTURE dans ref_taux_majoration pour 2027 (DIMANCHE passe à 50% au 01/01/2027)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ref_taux_majoration (id_config_legale, type_majoration, taux, description, date_debut) " +
                     "VALUES (?, 'DIMANCHE', 0.50, 'Majoration Dimanche Réforme 2027', '2027-01-01')")) {
            ps.setInt(1, input.getIdConfigLegale());
            ps.executeUpdate();
        }

        // 6. Re-préparer et re-valoriser le snapshot historique de Septembre 2026
        PayrollCalculationInput reloadedInput;
        try (Connection c = dataSource.getConnection()) {
            reloadedInput = payrollService.preparerInput(emp.idEmploye, idPeriode, false, c);
        }

        // Le taux résolu à la date de Septembre 2026 DOIT RESTER STRICTEMENT 0.40 (et non 0.50)
        assertThat(reloadedInput.getMajorationDimanche()).isEqualByComparingTo("0.40");

        OvertimePayCalculator.Result reloadedResult = overtimeCalculator.calculate(reloadedInput, hourlyRate);

        // Le montant valorisé reste STRICTEMENT IDENTIQUE : reproductibilité historique absolue
        assertThat(reloadedResult.dimanche()).isEqualByComparingTo(initialResult.dimanche());
        assertThat(reloadedResult.total()).isEqualByComparingTo(initialResult.total());
    }

    @Test
    @DisplayName("Mini-Gate Phase 1C-B.1 (Section 14): Changement de taux en milieu de période (26/09 -> 25/10)")
    void testMidPeriodRateChangePostgreSql() throws Exception {
        LocalDate deb = LocalDate.of(2026, 9, 26);
        LocalDate fin = LocalDate.of(2026, 10, 25);
        int idPeriodeMid = employeFixtures.createPeriodePaie("P-2026-10-MID", deb, fin, "EN_COURS");

        // Configuration légale avec frontière au 30/09/2026 :
        // Dimanche jusqu'au 30/09/2026 = 40% (0.40)
        // Dimanche à partir du 01/10/2026 = 50% (0.50)
        int cfgId;
        try (Connection c = dataSource.getConnection()) {
            mg.galaxam.sirh.model.paieconfig.PaieConfigLegale config = mg.galaxam.sirh.model.paieconfig.PaieConfigLegale.findActiveAt(c, LocalDate.of(2026, 10, 1));
            assertThat(config).isNotNull();
            cfgId = config.getIdConfigLegale();

            try (PreparedStatement psUpdate = c.prepareStatement(
                    "UPDATE ref_taux_majoration SET date_fin = '2026-09-30' WHERE id_config_legale = ? AND type_majoration = 'DIMANCHE'");
                 PreparedStatement psInsert = c.prepareStatement(
                    "INSERT INTO ref_taux_majoration (id_config_legale, type_majoration, taux, description, date_debut, date_fin) " +
                    "VALUES (?, 'DIMANCHE', 0.50, 'Majoration Dimanche Réforme Octobre 2026', '2026-10-01', NULL)")) {
                psUpdate.setInt(1, cfgId);
                psUpdate.executeUpdate();

                psInsert.setInt(1, cfgId);
                psInsert.executeUpdate();
            }
        }

        // Pointages réels des deux côtés de la frontière civile :
        // Dimanche 27 Septembre 2026 (08:00 - 16:00, 8h)
        // Dimanche 04 Octobre 2026 (08:00 - 16:00, 8h)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, " +
                     "heure_entree_matin, heure_sortie_am, total_heures, horaire_continu_jour, id_statut_jour) " +
                     "VALUES (?, ?, ?, '2026-09-27', '08:00', '16:00', 8.0, TRUE, 1), " +
                     "       (?, ?, ?, '2026-10-04', '08:00', '16:00', 8.0, TRUE, 1)")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idPeriodeMid);
            ps.setInt(3, emp.idSite);
            ps.setInt(4, emp.idEmploye);
            ps.setInt(5, idPeriodeMid);
            ps.setInt(6, emp.idSite);
            ps.executeUpdate();
        }

        // Gel et scellement des QualifiedTimeBuckets
        try (Connection c = dataSource.getConnection()) {
            pointageSyntheseService.gelerPeriode(idPeriodeMid, 1, c);
        }

        // Préparation de la paie
        PayrollCalculationInput inputMid;
        try (Connection c = dataSource.getConnection()) {
            inputMid = payrollService.preparerInput(emp.idEmploye, idPeriodeMid, false, c);
        }

        assertThat(inputMid.getTemporalRateResolver()).isNotNull();

        BigDecimal hourlyRate = inputMid.getTauxHoraireEffectif();
        OvertimePayCalculator.Result midResult = overtimeCalculator.calculate(inputMid, hourlyRate);

        assertThat(midResult.bucketDetails()).isNotEmpty();

        // Trouver le bucket du 27/09 (septembre) et celui du 04/10 (octobre)
        BucketValuationResult bucketSept = midResult.bucketDetails().stream()
                .filter(b -> b.sourceBucket().start().toLocalDate().equals(LocalDate.of(2026, 9, 27)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Bucket du 27/09 introuvable"));

        BucketValuationResult bucketOct = midResult.bucketDetails().stream()
                .filter(b -> b.sourceBucket().start().toLocalDate().equals(LocalDate.of(2026, 10, 4)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Bucket du 04/10 introuvable"));

        // Preuve : bucket de septembre applique strictement le taux A (40%)
        assertThat(bucketSept.sundayRate()).isEqualByComparingTo("0.40");
        // Preuve : bucket d'octobre applique strictement le taux B (50%)
        assertThat(bucketOct.sundayRate()).isEqualByComparingTo("0.50");

        // Montants unitaires : 8h * tauxHoraire * 40% vs 8h * tauxHoraire * 50%
        BigDecimal hSept = BigDecimal.valueOf(bucketSept.sourceBucket().minutes()).divide(new BigDecimal("60"), 6, java.math.RoundingMode.HALF_UP);
        BigDecimal hOct = BigDecimal.valueOf(bucketOct.sourceBucket().minutes()).divide(new BigDecimal("60"), 6, java.math.RoundingMode.HALF_UP);
        BigDecimal expectedSept = hSept.multiply(hourlyRate).multiply(new BigDecimal("0.40"));
        BigDecimal expectedOct = hOct.multiply(hourlyRate).multiply(new BigDecimal("0.50"));

        assertThat(bucketSept.sundayPremiumAmount()).isEqualByComparingTo(mg.galaxam.sirh.model.paie.engine.MoneyRoundingPolicy.amount(expectedSept));
        assertThat(bucketOct.sundayPremiumAmount()).isEqualByComparingTo(mg.galaxam.sirh.model.paie.engine.MoneyRoundingPolicy.amount(expectedOct));

        // Simulation d'une modification future en 2028 (taux C = 60%)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ref_taux_majoration (id_config_legale, type_majoration, taux, description, date_debut) " +
                     "VALUES (?, 'DIMANCHE', 0.60, 'Réforme Future 2028', '2028-01-01')")) {
            ps.setInt(1, cfgId);
            ps.executeUpdate();
        }

        // Rechargement et vérification de la reproductibilité historique
        PayrollCalculationInput reloadedInput;
        try (Connection c = dataSource.getConnection()) {
            reloadedInput = payrollService.preparerInput(emp.idEmploye, idPeriodeMid, false, c);
        }
        OvertimePayCalculator.Result reloadedResult = overtimeCalculator.calculate(reloadedInput, hourlyRate);
        assertThat(reloadedResult.dimanche()).isEqualByComparingTo(midResult.dimanche());
        assertThat(reloadedResult.total()).isEqualByComparingTo(midResult.total());
    }

    @Test
    @DisplayName("Mini-Gate Phase 1C-B.1 (Section 28-29): Intégration PayrollExecutionEngine & Anti-double-base de bout en bout")
    void testPayrollExecutionEngineEndToEndIntegration() throws Exception {
        // Pointage et gel sur idPeriode (Dimanche 20 Septembre 8h travaillées)
        LocalDate dateDimanche = LocalDate.of(2026, 9, 20);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, " +
                     "heure_entree_matin, heure_sortie_am, total_heures, horaire_continu_jour, id_statut_jour) " +
                     "VALUES (?, ?, ?, ?, '08:00', '16:00', 8.0, TRUE, 1)")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idPeriode);
            ps.setInt(3, emp.idSite);
            ps.setDate(4, java.sql.Date.valueOf(dateDimanche));
            ps.executeUpdate();
        }

        try (Connection c = dataSource.getConnection()) {
            pointageSyntheseService.gelerPeriode(idPeriode, 1, c);
        }

        // Préparer input
        PayrollCalculationInput input;
        try (Connection c = dataSource.getConnection()) {
            input = payrollService.preparerInput(emp.idEmploye, idPeriode, false, c);
        }

        PayrollExecutionEngine engine = new PayrollExecutionEngine();
        BulletinPaieComplet bpc = engine.calculerBulletin(input);

        assertThat(bpc).isNotNull();
        assertThat(bpc.getLignes()).isNotEmpty();

        // 1. Vérifier que la ligne SALAIRE_BASE existe et correspond exactement au salaire contractuel
        BulletinPaieLigne ligneBase = bpc.getLignes().stream()
                .filter(l -> "SALAIRE_BASE".equals(l.getCodeRubrique()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Ligne SALAIRE_BASE introuvable"));

        assertThat(ligneBase.getMontantSalarial()).isEqualByComparingTo(input.getSalaireBaseContractuel());

        // 2. Vérifier que la ligne HEURES_DIMANCHE existe et contient uniquement la valorisation canonique
        BulletinPaieLigne ligneDimanche = bpc.getLignes().stream()
                .filter(l -> "HEURES_DIMANCHE".equals(l.getCodeRubrique()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Ligne HEURES_DIMANCHE introuvable"));

        assertThat(ligneDimanche.getMontantSalarial()).isGreaterThan(BigDecimal.ZERO);

        // 3. Vérifier que la base normale n'a pas été dupliquée
        // Le brut total = salaire_base + majoration dimanche (+ autres primes/indemnités éventuelles)
        BigDecimal gainsBruts = bpc.getBulletin().getTotalGainsBruts();
        assertThat(gainsBruts).isEqualByComparingTo(
                ligneBase.getMontantSalarial().add(ligneDimanche.getMontantSalarial())
        );
    }
}
