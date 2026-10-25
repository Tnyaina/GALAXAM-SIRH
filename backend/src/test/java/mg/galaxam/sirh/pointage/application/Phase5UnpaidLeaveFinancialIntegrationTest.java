package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.conge.application.CongePayrollPolicy;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paie.domain.engine.PayrollTaxCalculations;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 5 synthetic integration between the real time and payroll calculation engines. */
class Phase5UnpaidLeaveFinancialIntegrationTest {
    private static final BigDecimal BASE = new BigDecimal("1000000.00");
    private static final BigDecimal DIVISOR = new BigDecimal("173.33");

    private record CaseResult(String code, long absenceMinutes, BigDecimal absenceDays,
                              BigDecimal expectedDeduction, BigDecimal observedDeduction,
                              BigDecimal expectedGross, BigDecimal observedGross,
                              BigDecimal expectedEmployeeContributions, BigDecimal observedEmployeeContributions,
                              BigDecimal expectedEmployerContributions, BigDecimal observedEmployerContributions,
                              BigDecimal expectedNetPay, BigDecimal observedNetPay) {}

    @Test
    void unpaidMultiDayAndHalfDayAbsencesProduceOracleMatchedPayrollResultsOnDisposablePostgres() throws Exception {
        String url = System.getenv("GALAXAM_PHASE5_FINANCE_DB_URL");
        String user = System.getenv("GALAXAM_PHASE5_FINANCE_DB_USER");
        String password = System.getenv("GALAXAM_PHASE5_FINANCE_DB_PASSWORD");
        assertNotNull(url, "Dedicated Phase 5 PostgreSQL URL required");
        assertTrue(url.contains("127.0.0.1:15452/galaxam_ecc_phase5_finance"), "Only the Phase 5 disposable database is allowed");
        assertEquals("ecc_phase5_test", user);

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertEquals("galaxam_ecc_phase5_finance", connection.getCatalog());
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM employe"), "Canonical or other employees must not be seeded");
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='employe'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='demande_conge'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='pointage_jour'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='bulletin_paie'"));

            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE phase5_finance_inputs (case_code text primary key, absence_minutes bigint not null, absence_days numeric(4,2) not null, unpaid boolean not null)");
            }

            AttendanceEngine attendance = new AttendanceEngine();
            long noAbsence = day(attendance, LocalDate.of(2026, 3, 2), LocalTime.of(8, 0), LocalTime.of(12, 0),
                    LocalTime.of(13, 0), LocalTime.of(17, 0)).absenceMinutes();
            long multiDay = day(attendance, LocalDate.of(2026, 3, 2), null, null, null, null).absenceMinutes()
                    + day(attendance, LocalDate.of(2026, 3, 3), null, null, null, null).absenceMinutes();
            long halfDay = day(attendance, LocalDate.of(2026, 3, 4), null, null,
                    LocalTime.of(13, 0), LocalTime.of(17, 0)).absenceMinutes();
            assertEquals(0, noAbsence);
            assertEquals(960, multiDay);
            assertEquals(240, halfDay);
            assertFalse(CongePayrollPolicy.estRemunere(false, true), "Request override must classify the leave as unpaid");
            assertEquals("ABSENCE_JUSTIFIEE", PointageSyntheseService.appliquerStatutCongeValide(
                    "ABSENCE_JUSTIFIEE", 0, false, false));
            assertEquals("ABSENCE_JUSTIFIEE", PointageSyntheseService.appliquerStatutCongeValide(
                    "DEMI_JOURNEE", 4.0, false, true));

            try (var insert = connection.prepareStatement("INSERT INTO phase5_finance_inputs VALUES (?, ?, ?, ?)")) {
                addFixture(insert, "NO_ABSENCE", noAbsence, "0.00", false);
                addFixture(insert, "UNPAID_TWO_DAYS", multiDay, "2.00", true);
                addFixture(insert, "UNPAID_HALF_DAY", halfDay, "0.50", true);
            }

            List<CaseResult> results = new java.util.ArrayList<>();
            try (var rows = connection.createStatement().executeQuery(
                    "SELECT case_code, absence_minutes, absence_days, unpaid FROM phase5_finance_inputs ORDER BY case_code")) {
                while (rows.next()) {
                    String code = rows.getString(1);
                    long minutes = rows.getLong(2);
                    BigDecimal days = rows.getBigDecimal(3);
                    boolean unpaid = rows.getBoolean(4);
                    assertEquals(minutes > 0, unpaid);
                    PayrollCalculationInput input = payrollInput(minutes, days);
                    BulletinPaieComplet bulletin = new PayrollExecutionEngine().calculer(input);

                    BigDecimal expectedDeduction = amount(BASE.multiply(BigDecimal.valueOf(minutes))
                            .divide(DIVISOR.multiply(new BigDecimal("60")), 12, RoundingMode.HALF_UP));
                    BigDecimal expectedGross = amount(BASE.subtract(expectedDeduction));
                    BigDecimal expectedCnaps = amount(expectedGross.multiply(new BigDecimal("0.01")));
                    BigDecimal expectedHealth = amount(expectedGross.multiply(new BigDecimal("0.01")));
                    BigDecimal expectedContributions = expectedCnaps.add(expectedHealth);
                    BigDecimal expectedEmployerContributions = amount(expectedGross.multiply(new BigDecimal("0.13")))
                            .add(amount(expectedGross.multiply(new BigDecimal("0.05"))))
                            .add(amount(expectedGross.multiply(new BigDecimal("0.01"))));
                    BigDecimal expectedNetTaxable = expectedGross.subtract(expectedContributions);
                    BigDecimal expectedIrsa = independentIrsa(expectedNetTaxable);
                    BigDecimal beforePayoutRounding = expectedNetTaxable.subtract(expectedIrsa);
                    BigDecimal expectedNetPay = beforePayoutRounding.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP);

                    BigDecimal observedDeduction = bulletin.getMontantLigneSalariale("RETENUE_ABSENCE");
                    BigDecimal observedGross = bulletin.getBulletin().getTotalGainsBruts();
                    BigDecimal observedContributions = bulletin.getBulletin().getTotalCotisationsSalariales();
                    BigDecimal observedEmployerContributions = bulletin.getBulletin().getTotalCotisationsPatronales();
                    BigDecimal observedNetPay = bulletin.getBulletin().getNetAPayer();
                    assertEquals(0, expectedDeduction.compareTo(observedDeduction), code + " absence deduction");
                    assertEquals(0, expectedGross.compareTo(observedGross), code + " gross");
                    assertEquals(0, expectedContributions.compareTo(observedContributions), code + " employee contributions");
                    assertEquals(0, expectedEmployerContributions.compareTo(observedEmployerContributions), code + " employer contributions");
                    assertEquals(0, expectedIrsa.compareTo(bulletin.getBulletin().getIrsaNet()), code + " IRSA");
                    assertEquals(0, expectedNetPay.compareTo(observedNetPay), code + " net payable");
                    assertEquals(minutes / 60.0, input.getNbHeuresAbsence().doubleValue(), 0.000001);
                    results.add(new CaseResult(code, minutes, days, expectedDeduction, observedDeduction,
                            expectedGross, observedGross, expectedContributions, observedContributions,
                            expectedEmployerContributions, observedEmployerContributions,
                            expectedNetPay, observedNetPay));
                }
            }
            assertEquals(3, results.size());
            results.forEach(r -> System.out.printf(
                    "PHASE5_FINANCE case=%s unpaidMinutes=%d absenceDays=%s deductionExpected=%s deductionActual=%s grossExpected=%s grossActual=%s employeeContribExpected=%s employeeContribActual=%s employerContribExpected=%s employerContribActual=%s netExpected=%s netActual=%s%n",
                    r.code(), r.absenceMinutes(), r.absenceDays(), r.expectedDeduction(), r.observedDeduction(),
                    r.expectedGross(), r.observedGross(), r.expectedEmployeeContributions(), r.observedEmployeeContributions(),
                    r.expectedEmployerContributions(), r.observedEmployerContributions(),
                    r.expectedNetPay(), r.observedNetPay()));
            String resultsFile = System.getenv("ECC_PHASE5_RESULTS_FILE");
            if (resultsFile != null && !resultsFile.isBlank()) {
                String json = "{\"test\":\"Phase5UnpaidLeaveFinancialIntegrationTest\",\"database\":\"galaxam_ecc_phase5_finance\",\"results\":["
                        + results.stream().map(r -> String.format(
                                "{\"case\":\"%s\",\"unpaidMinutes\":%d,\"absenceDays\":\"%s\",\"deductionExpected\":\"%s\",\"deductionActual\":\"%s\",\"grossExpected\":\"%s\",\"grossActual\":\"%s\",\"employeeContributionsExpected\":\"%s\",\"employeeContributionsActual\":\"%s\",\"employerContributionsExpected\":\"%s\",\"employerContributionsActual\":\"%s\",\"netPayExpected\":\"%s\",\"netPayActual\":\"%s\"}",
                                r.code(), r.absenceMinutes(), r.absenceDays(), r.expectedDeduction(), r.observedDeduction(),
                                r.expectedGross(), r.observedGross(), r.expectedEmployeeContributions(), r.observedEmployeeContributions(),
                                r.expectedEmployerContributions(), r.observedEmployerContributions(), r.expectedNetPay(), r.observedNetPay()))
                        .collect(java.util.stream.Collectors.joining(",")) + "]}\n";
                java.nio.file.Path target = java.nio.file.Path.of(resultsFile);
                java.nio.file.Files.createDirectories(target.getParent());
                java.nio.file.Files.writeString(target, json);
            }
        }
    }

    private static AttendanceEngine.Result day(AttendanceEngine engine, LocalDate date,
                                                 LocalTime morningIn, LocalTime morningOut,
                                                 LocalTime afternoonIn, LocalTime afternoonOut) {
        return engine.calculateDay(new AttendanceEngine.Context(1, date,
                morningIn, morningOut, afternoonIn, afternoonOut,
                LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(17, 0),
                null, null, false, false, false, AttendanceEngine.Context.LeavePart.NONE,
                false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
    }

    private static void addFixture(java.sql.PreparedStatement insert, String code, long minutes, String days,
                                   boolean unpaid) throws Exception {
        insert.setString(1, code);
        insert.setLong(2, minutes);
        insert.setBigDecimal(3, new BigDecimal(days));
        insert.setBoolean(4, unpaid);
        insert.executeUpdate();
    }

    private static int scalar(Connection connection, String sql) throws Exception {
        try (var result = connection.createStatement().executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static PayrollCalculationInput payrollInput(long unpaidMinutes, BigDecimal absenceDays) {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setIdEmploye(9001);
        input.setMatricule("ECC-P5-SYNTH");
        input.setNomPrenom("Synthetic Payroll Fixture");
        input.setIntitulePoste("Test Finance");
        input.setCodePeriode("2026-03-P5");
        input.setDateDebutPeriode(Date.valueOf(LocalDate.of(2026, 3, 1)));
        input.setDateFinPeriode(Date.valueOf(LocalDate.of(2026, 3, 31)));
        input.setDateEmbauche(Date.valueOf(LocalDate.of(2025, 1, 1)));
        input.setSalaireBaseContractuel(BASE);
        input.setTauxHoraire(BASE.divide(DIVISOR, 8, RoundingMode.HALF_UP));
        input.setDiviseurMensuelHeures(DIVISOR);
        input.setNbJoursOuvrables(new BigDecimal("22.00"));
        input.setNbJoursPresents(new BigDecimal("22.00").subtract(absenceDays));
        input.setNbJoursAbsence(absenceDays);
        input.setNbJoursConges(BigDecimal.ZERO);
        input.setTotalHeuresTravaillees(new BigDecimal("173.33").subtract(BigDecimal.valueOf(unpaidMinutes)
                .divide(new BigDecimal("60"), 8, RoundingMode.HALF_UP)));
        input.setHeuresNormales(input.getTotalHeuresTravaillees());
        input.setNbHeuresAbsence(BigDecimal.valueOf(unpaidMinutes).divide(new BigDecimal("60"), 8, RoundingMode.HALF_UP));
        input.setTauxCnapsSalarial(new BigDecimal("0.01"));
        input.setTauxCnapsPatronal(new BigDecimal("0.13"));
        input.setTauxSanteSalarial(new BigDecimal("0.01"));
        input.setTauxSantePatronal(new BigDecimal("0.05"));
        input.setTauxFmfpPatronal(new BigDecimal("0.01"));
        input.setPlafondCotisationMensuel(new BigDecimal("2400000.00"));
        input.setEstPlafonneSante(true);
        input.setTranchesIrsa(List.of(
                new PayrollTaxCalculations.Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), new BigDecimal("0.00")),
                new PayrollTaxCalculations.Tranche(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("0.05")),
                new PayrollTaxCalculations.Tranche(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("0.10")),
                new PayrollTaxCalculations.Tranche(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("0.15")),
                new PayrollTaxCalculations.Tranche(new BigDecimal("600000.00"), new BigDecimal("4000000.00"), new BigDecimal("0.20")),
                new PayrollTaxCalculations.Tranche(new BigDecimal("4000000.00"), null, new BigDecimal("0.25"))));
        input.setMinimumPerceptionIrsa(new BigDecimal("3000.00"));
        input.setDeductionParEnfant(new BigDecimal("2000.00"));
        input.setNbEnfantsEligibles(0);
        return input;
    }

    /** Independent spreadsheet-style oracle from the configured LF 2026 bracket schedule. */
    private static BigDecimal independentIrsa(BigDecimal netTaxable) {
        BigDecimal taxBase = netTaxable.divide(new BigDecimal("100"), 0, RoundingMode.DOWN).multiply(new BigDecimal("100"));
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal[][] bands = {
                {new BigDecimal("0"), new BigDecimal("350000"), BigDecimal.ZERO},
                {new BigDecimal("350000"), new BigDecimal("400000"), new BigDecimal("0.05")},
                {new BigDecimal("400000"), new BigDecimal("500000"), new BigDecimal("0.10")},
                {new BigDecimal("500000"), new BigDecimal("600000"), new BigDecimal("0.15")},
                {new BigDecimal("600000"), new BigDecimal("4000000"), new BigDecimal("0.20")},
                {new BigDecimal("4000000"), taxBase, new BigDecimal("0.25")}};
        for (BigDecimal[] band : bands) {
            BigDecimal taxable = taxBase.min(band[1]).subtract(band[0]).max(BigDecimal.ZERO);
            tax = tax.add(taxable.multiply(band[2]).setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal taxRoundedTen = tax.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN);
        return taxRoundedTen.max(new BigDecimal("3000.00")).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
