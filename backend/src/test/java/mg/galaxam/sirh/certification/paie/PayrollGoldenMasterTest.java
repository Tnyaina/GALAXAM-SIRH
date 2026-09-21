package mg.galaxam.sirh.certification.paie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

@DisplayName("CERTIFICATION GOLDEN MASTER — DÉCEMBRE 2025 (HISTORIQUE) & SEPTEMBRE 2026 (MODERNE)")
public class PayrollGoldenMasterTest extends BaseIntegrationTest {

    @Autowired
    private PayrollService payrollService;

    private int idPeriode2025;
    private int idPeriode2026;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanTransactionalData();

        // 1. Période historique : Décembre 2025 (26/11/2025 au 25/12/2025)
        idPeriode2025 = employeFixtures.createPeriodePaie(
                "PER-2025-12",
                LocalDate.of(2025, 11, 26),
                LocalDate.of(2025, 12, 25),
                "EN_COURS"
        );

        // 2. Période moderne : Septembre 2026 (26/08/2026 au 25/09/2026)
        idPeriode2026 = employeFixtures.createPeriodePaie(
                "PER-2026-09",
                LocalDate.of(2026, 8, 26),
                LocalDate.of(2026, 9, 25),
                "EN_COURS"
        );
    }

    private void insererPointageScelle(int idPeriode, int idEmp, double heuresAbsence, double hs30, double hs50) {
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
    @DisplayName("1. Golden Master Décembre 2025 : RAKETAKA (TNR) avec règles historiques LF 2024/2025")
    void testGoldenMasterDecembre2025() throws Exception {
        // RAKETAKA : Base 625 000 Ar, site TNR (OSTIE 1%), 0 enfant
        BigDecimal base = new BigDecimal("625000.00");
        CreatedEmploye emp = employeFixtures.createEmployeWithSalary(
                "MATR-002", "RAKETAKA", "Soafara", 1,
                base, "PERMANENT", 0
        );

        insererPointageScelle(idPeriode2025, emp.idEmploye, 0.0, 0.0, 0.0);

        // Oracle Indépendant configuré sur Décembre 2025 (SME = 262 680 Ar, Min IRSA = 2000 Ar)
        PayrollIndependentOracle.LegalConfigExpected config2025 = PayrollIndependentOracle.configDecembre2025();
        PayrollIndependentOracle.PayrollExpectedResult oracle = PayrollIndependentOracle.calculate(
                base,
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
                0,
                false,
                config2025
        );

        // Calcul Runtime Spring Boot & PostgreSQL sur Décembre 2025
        BulletinPaieComplet bpc;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            bpc = payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode2025, false, 1, c);
            c.commit();
        }

        assertNotNull(bpc);
        BulletinPaie bp = bpc.getBulletin();
        assertNotNull(bp);

        // Vérifications strictes (Oracle == Runtime)
        assertEquals(oracle.salaireBaseContractuel(), bp.getSalaireBase());
        assertEquals(oracle.totalGainsBruts(), bp.getTotalGainsBruts());
        assertEquals(oracle.cnapsSalarial().add(oracle.santeSalarial()), bp.getTotalCotisationsSalariales());
        assertEquals(oracle.salaireNetImposable(), bp.getSalaireNetImposable());
        assertEquals(oracle.irsaNet(), bp.getIrsaNet());
        assertEquals(oracle.netAPayer(), bp.getNetAPayer());
    }

    @Test
    @DisplayName("2. Golden Master Septembre 2026 : RAKOTO (TNR) avec cadre réglementaire moderne LF 2026")
    void testGoldenMasterSeptembre2026() throws Exception {
        // RAKOTO : Base 800 000 Ar, site TNR (OSTIE 1%), 2 enfants, 10h HS30
        BigDecimal base = new BigDecimal("800000.00");
        CreatedEmploye emp = employeFixtures.createEmployeWithSalary(
                "MATR-001", "RAKOTO", "Jean", 1,
                base, "PERMANENT", 2
        );

        insererPointageScelle(idPeriode2026, emp.idEmploye, 0.0, 10.0, 0.0);

        PayrollIndependentOracle.LegalConfigExpected config2026 = PayrollIndependentOracle.configSeptembre2026();
        PayrollIndependentOracle.PayrollExpectedResult oracle = PayrollIndependentOracle.calculate(
                base,
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
                2,
                false,
                config2026
        );

        BulletinPaieComplet bpc;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            bpc = payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode2026, false, 1, c);
            c.commit();
        }

        assertNotNull(bpc);
        BulletinPaie bp = bpc.getBulletin();
        assertNotNull(bp);

        assertEquals(oracle.salaireBaseContractuel(), bp.getSalaireBase());
        assertEquals(oracle.totalGainsBruts(), bp.getTotalGainsBruts());
        assertEquals(oracle.cnapsSalarial().add(oracle.santeSalarial()), bp.getTotalCotisationsSalariales());
        assertEquals(oracle.irsaNet(), bp.getIrsaNet());
        assertEquals(oracle.netAPayer(), bp.getNetAPayer());
    }
}
