package mg.galaxam.sirh.certification.paie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.certification.dataset.CertificationDataset;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.oracle.PayrollIndependentOracle;

@DisplayName("CERTIFICATION PAIE INTÉGRATION — PIPELINE COMPLET ET ORACLES INDÉPENDANTS SUR POSTGRESQL")
public class PayrollIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PayrollService payrollService;

    private int idPeriode;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanTransactionalData();

        // Période de paie moderne : Septembre 2026 (26/08/2026 au 25/09/2026)
        idPeriode = employeFixtures.createPeriodePaie(
                "PER-2026-09",
                LocalDate.of(2026, 8, 26),
                LocalDate.of(2026, 9, 25),
                "EN_COURS"
        );
    }

    private void insererPointageScelle(int idEmp, double heuresAbsence, double hs30, double hs50) {
        String sql = "INSERT INTO pointage_cumul_periode (" +
                "id_periode_paie, id_employe, nb_jours_ouvrables, nb_jours_presents, " +
                "total_heures_travaillees, heures_absence_retenues, heures_hs_30, heures_hs_50) " +
                "VALUES (?, ?, 22, 22, 173.33, ?, ?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPeriode);
            ps.setInt(2, idEmp);
            ps.setDouble(3, heuresAbsence);
            ps.setDouble(4, hs30);
            ps.setDouble(5, hs50);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Erreur insertion pointage cumul : " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("1. EMP001_STANDARD : Mois nominal sans aléas (Base 400 000 Ar, 0 absences, 0 HS)")
    void testEmp001Standard() throws Exception {
        CertificationDataset.EmployeeProfile profile = CertificationDataset.EMP001_STANDARD;

        CreatedEmploye emp = employeFixtures.createEmployeWithSalary(
                profile.matricule(), profile.nom(), profile.prenom(), 1,
                profile.salaireBaseContractuel(), "PERMANENT", profile.nbEnfants()
        );

        insererPointageScelle(emp.idEmploye, 0.0, 0.0, 0.0);

        // 1. Calcul via Oracle Indépendant (Source externe autonome)
        PayrollIndependentOracle.LegalConfigExpected config2026 = PayrollIndependentOracle.configSeptembre2026();
        PayrollIndependentOracle.PayrollExpectedResult oracle = PayrollIndependentOracle.calculate(
                profile.salaireBaseContractuel(),
                null,
                new BigDecimal("173.33"),
                0L,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                profile.nbEnfants(),
                profile.estCadre(),
                config2026
        );

        // 2. Calcul via Runtime Spring Boot & PostgreSQL
        BulletinPaieComplet bpc;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            bpc = payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1, c);
            c.commit();
        }

        assertNotNull(bpc);
        BulletinPaie bp = bpc.getBulletin();
        assertNotNull(bp);

        // 3. Comparaison stricte Runtime vs Oracle Indépendant (Delta = 0.00 Ar)
        assertEquals(oracle.salaireBaseContractuel(), bp.getSalaireBase(), "Salaire de base");
        assertEquals(oracle.totalGainsBruts(), bp.getTotalGainsBruts(), "Gains bruts");
        assertEquals(oracle.cnapsSalarial().add(oracle.santeSalarial()), bp.getTotalCotisationsSalariales(), "Cotisations salariales");
        assertEquals(oracle.salaireNetImposable(), bp.getSalaireNetImposable(), "Net imposable social");
        assertEquals(oracle.irsaNet(), bp.getIrsaNet(), "IRSA net");
        assertEquals(oracle.netAPayer(), bp.getNetAPayer(), "Net à payer MROUND(10)");
    }

    @Test
    @DisplayName("2. EMP002_ABSENCE : Déduction horaire légale (8h d'absence sur base 500 000 Ar)")
    void testEmp002Absence() throws Exception {
        CertificationDataset.EmployeeProfile profile = CertificationDataset.EMP002_ABSENCE;

        CreatedEmploye emp = employeFixtures.createEmployeWithSalary(
                profile.matricule(), profile.nom(), profile.prenom(), 1,
                profile.salaireBaseContractuel(), "PERMANENT", profile.nbEnfants()
        );

        insererPointageScelle(emp.idEmploye, 8.0, 0.0, 0.0);

        PayrollIndependentOracle.LegalConfigExpected config2026 = PayrollIndependentOracle.configSeptembre2026();
        PayrollIndependentOracle.PayrollExpectedResult oracle = PayrollIndependentOracle.calculate(
                profile.salaireBaseContractuel(),
                null,
                new BigDecimal("173.33"),
                8L * 60L,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                profile.nbEnfants(),
                profile.estCadre(),
                config2026
        );

        BulletinPaieComplet bpc;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            bpc = payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1, c);
            c.commit();
        }

        assertNotNull(bpc);
        BulletinPaie bp = bpc.getBulletin();
        assertNotNull(bp);

        assertEquals(oracle.totalGainsBruts(), bp.getTotalGainsBruts(), "Gains bruts après déduction absence 8h");
        assertEquals(oracle.cnapsSalarial().add(oracle.santeSalarial()), bp.getTotalCotisationsSalariales(), "Cotisations salariales");
        assertEquals(oracle.irsaNet(), bp.getIrsaNet(), "IRSA net après absence");
        assertEquals(oracle.netAPayer(), bp.getNetAPayer(), "Net à payer final");
    }

    @Test
    @DisplayName("3. EMP004_HS : Heures supplémentaires et plafond d'exonération fiscale 20h (15h HS30 + 5h HS50)")
    void testEmp004Overtime() throws Exception {
        CertificationDataset.EmployeeProfile profile = CertificationDataset.EMP004_HS;

        CreatedEmploye emp = employeFixtures.createEmployeWithSalary(
                profile.matricule(), profile.nom(), profile.prenom(), 1,
                profile.salaireBaseContractuel(), "PERMANENT", profile.nbEnfants()
        );

        insererPointageScelle(emp.idEmploye, 0.0, 15.0, 5.0);

        PayrollIndependentOracle.LegalConfigExpected config2026 = PayrollIndependentOracle.configSeptembre2026();
        PayrollIndependentOracle.PayrollExpectedResult oracle = PayrollIndependentOracle.calculate(
                profile.salaireBaseContractuel(),
                null,
                new BigDecimal("173.33"),
                0L,
                BigDecimal.ZERO,
                null,
                new BigDecimal("15.00"),
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                profile.nbEnfants(),
                profile.estCadre(),
                config2026
        );

        BulletinPaieComplet bpc;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            bpc = payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1, c);
            c.commit();
        }

        assertNotNull(bpc);
        BulletinPaie bp = bpc.getBulletin();
        assertNotNull(bp);

        assertEquals(oracle.totalGainsBruts(), bp.getTotalGainsBruts(), "Gains bruts avec HS30 + HS50");
        // Vérification de la présence de la ligne d'exonération fiscale HS
        boolean hasExoLine = bpc.getLignes().stream()
                .anyMatch(l -> "HS_EXONEREE_IRSA".equals(l.getCodeRubrique()));
        assertTrue(hasExoLine, "La ligne d'exonération fiscale HS_EXONEREE_IRSA doit figurer sur le bulletin");

        assertEquals(oracle.irsaNet(), bp.getIrsaNet(), "IRSA calculé sur assiette diminuée des HS exonérées");
        assertEquals(oracle.netAPayer(), bp.getNetAPayer(), "Net à payer");
    }

    @Test
    @DisplayName("4. EMP008_HIGH_INCOME : Plafonnement CNaPS à 8 × SME 2026 (Base 8 500 000 Ar, 3 enfants)")
    void testEmp008HighIncome() throws Exception {
        CertificationDataset.EmployeeProfile profile = CertificationDataset.EMP008_HIGH_INCOME;

        CreatedEmploye emp = employeFixtures.createEmployeWithSalary(
                profile.matricule(), profile.nom(), profile.prenom(), 1,
                profile.salaireBaseContractuel(), "PERMANENT", profile.nbEnfants()
        );

        insererPointageScelle(emp.idEmploye, 0.0, 0.0, 0.0);

        PayrollIndependentOracle.LegalConfigExpected config2026 = PayrollIndependentOracle.configSeptembre2026();
        PayrollIndependentOracle.PayrollExpectedResult oracle = PayrollIndependentOracle.calculate(
                profile.salaireBaseContractuel(),
                null,
                new BigDecimal("173.33"),
                0L,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                profile.nbEnfants(),
                profile.estCadre(),
                config2026
        );

        BulletinPaieComplet bpc;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            bpc = payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1, c);
            c.commit();
        }

        assertNotNull(bpc);
        BulletinPaie bp = bpc.getBulletin();
        assertNotNull(bp);

        // CNaPS plafonné à 8 * 300 000 = 2 400 000 Ar (1% = 24 000 Ar)
        // Santé 1% (OSTIE) plafonnée à 2 400 000 Ar = 24 000 Ar
        // Total cotisations salariales = 48 000 Ar
        assertEquals(oracle.cnapsSalarial().add(oracle.santeSalarial()), bp.getTotalCotisationsSalariales(), "CNaPS plafonné + Santé plafonnée");
        assertEquals(new BigDecimal("6000.00"), bp.getReductionEnfants(), "3 enfants x 2 000 Ar = 6 000 Ar de réduction");
        assertEquals(oracle.irsaNet(), bp.getIrsaNet(), "IRSA tranche 25% (LF 2026)");
        assertEquals(oracle.netAPayer(), bp.getNetAPayer(), "Net à payer haut revenu");
    }

    @Test
    @DisplayName("5. EMP007_CADRE : Statut Cadre -> Non-éligibilité stricte à l'exonération des Heures Supplémentaires")
    void testEmp007Cadre() throws Exception {
        CertificationDataset.EmployeeProfile profile = CertificationDataset.EMP007_CADRE;

        CreatedEmploye emp = employeFixtures.createEmployeWithSalary(
                profile.matricule(), profile.nom(), profile.prenom(), 1,
                profile.salaireBaseContractuel(), "PERMANENT", profile.nbEnfants(), profile.estCadre()
        );

        // 10h de HS 30%
        insererPointageScelle(emp.idEmploye, 0.0, 10.0, 0.0);

        PayrollIndependentOracle.LegalConfigExpected config2026 = PayrollIndependentOracle.configSeptembre2026();
        PayrollIndependentOracle.PayrollExpectedResult oracle = PayrollIndependentOracle.calculate(
                profile.salaireBaseContractuel(),
                null,
                new BigDecimal("173.33"),
                0L,
                BigDecimal.ZERO,
                null,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                profile.nbEnfants(),
                true, // estCadre = true
                config2026
        );

        // L'oracle doit indiquer 0 HS exonérées pour un cadre
        assertEquals(BigDecimal.ZERO, oracle.hsExonereeIrsa(), "Un cadre n'a droit à aucune exonération HS");

        BulletinPaieComplet bpc;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            bpc = payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1, c);
            c.commit();
        }

        assertNotNull(bpc);
        BulletinPaie bp = bpc.getBulletin();
        assertNotNull(bp);

        // Aucune ligne d'exonération HS pour le cadre
        boolean hasExoLine = bpc.getLignes().stream()
                .anyMatch(l -> "HS_EXONEREE_IRSA".equals(l.getCodeRubrique()));
        assertTrue(!hasExoLine, "Le cadre ne doit pas avoir d'exonération HS d'IRSA");

        assertEquals(oracle.totalGainsBruts(), bp.getTotalGainsBruts(), "Gains bruts incluant HS");
        assertEquals(oracle.irsaNet(), bp.getIrsaNet(), "IRSA net calculé sans exonération HS");
        assertEquals(oracle.netAPayer(), bp.getNetAPayer(), "Net à payer cadre");
    }
}
