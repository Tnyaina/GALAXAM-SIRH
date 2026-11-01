package mg.galaxam.sirh.conge.application;

import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Replays the October workbook against a disposable copy of the 592 database state. */
class Employee592ReplayIntegrationTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 23);
    private static final LocalDate END = LocalDate.of(2026, 10, 25);

    @Test
    void partialLeaveAndPayrollLinesMatchOctoberWorkbook() throws Exception {
        String url = System.getenv("GALAXAM_592_REPLAY_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "Requires a disposable 592 database copy");
        try (Connection c = DriverManager.getConnection(url,
                System.getenv("GALAXAM_592_REPLAY_DB_USER"),
                System.getenv("GALAXAM_592_REPLAY_DB_PASSWORD"))) {
            c.setAutoCommit(false);
            try {
                int employee = scalarInt(c, "SELECT id_employe FROM employe WHERE matricule='0592'");
                int user = scalarInt(c, "SELECT id_utilisateur FROM utilisateur ORDER BY id_utilisateur LIMIT 1");
                // Repartir du solde de reprise ; la copie peut déjà contenir l'essai de congé.
                try (var statement = c.createStatement()) {
                    statement.executeUpdate("UPDATE pointage_jour SET id_demande_conge=NULL "
                            + "WHERE id_demande_conge IN (SELECT id_demande_conge FROM demande_conge "
                            + "WHERE id_employe=" + employee + " AND date_debut='2026-10-23' AND date_fin='2026-10-25')");
                    statement.executeUpdate("DELETE FROM mouvement_solde_conge WHERE id_demande_conge IN "
                            + "(SELECT id_demande_conge FROM demande_conge WHERE id_employe=" + employee
                            + " AND date_debut='2026-10-23' AND date_fin='2026-10-25')");
                    statement.executeUpdate("DELETE FROM demande_conge WHERE id_employe=" + employee
                            + " AND date_debut='2026-10-23' AND date_fin='2026-10-25'");
                }
                CongeService conges = new CongeService(new CongeCalculateurEngine());
                var result = conges.regulariserCongeAnnuelDepuisPointageStructure(employee,
                        Date.valueOf(START), Date.valueOf(END), 3, null,
                        "Rejeu de l'Excel octobre 592", user, c);
                assertEquals(2.44, result.joursCouverts(), 0.000001);
                assertEquals(0.56, result.joursRestants(), 0.000001);
                assertNotNull(result.demandeId());
                try (var statement = c.prepareStatement("SELECT COALESCE(SUM(nb_jours),0) "
                        + "FROM mouvement_solde_conge WHERE id_demande_conge=?")) {
                    statement.setInt(1, result.demandeId());
                    try (ResultSet rs = statement.executeQuery()) {
                        rs.next();
                        assertDecimal("-2.44", rs.getBigDecimal(1));
                    }
                }

                Map<LocalDate, BigDecimal> paid = conges.calculerJoursCongeValorisesParDate(
                        employee, START, END, c);
                Map<LocalDate, BigDecimal> base = conges.calculerJoursCongeBaseParDate(
                        employee, START, END, c);
                assertDecimal("2.44", total(paid));
                assertDecimal("3", total(base));
                assertDecimal("0.44", paid.get(END));

                CongeLotValorisationService lots = new CongeLotValorisationService();
                BigDecimal fifo = lots.valorisationPrisesSurPeriode(employee, START, END, c);
                assertDecimal("63829.85", lots.valorisationPrisesSurPeriode(
                        employee, START, START.plusDays(1), c));
                assertDecimal("14042.57", lots.valorisationPrisesSurPeriode(employee, END, END, c));
                PayrollCalculationInput input = new PayrollCalculationInput();
                input.setIdEmploye(employee);
                input.setSalaireBaseContractuel(new BigDecimal("1000000"));
                input.setDiviseurMensuelHeures(new BigDecimal("173.33"));
                input.setNbHeuresAbsence(new BigDecimal("2.75"));
                input.setNbJoursConges(total(paid));
                input.setJoursCongeValorisesParDate(paid);
                input.setJoursCongeBaseParDate(base);
                input.setSalaireParJour(Map.of(
                        START, new BigDecimal("1000000"),
                        START.plusDays(1), new BigDecimal("1000000"),
                        END, new BigDecimal("1000000")));
                input.setIndemniteCongeLots(fifo);
                PayrollExecutionEngine engine = new PayrollExecutionEngine();
                List<BulletinPaieLigne> leaveLines = new ArrayList<>();
                engine.calculerConges(input, new BulletinPaie(), leaveLines);
                List<BulletinPaieLigne> absenceLines = new ArrayList<>();
                engine.calculerRetenueAbsence(input, new BigDecimal("1000000"),
                        input.getTauxHoraireEffectif(), new BulletinPaie(), absenceLines);
                assertDecimal("100000.00", amount(leaveLines, "DEDUCTION_CONGE_BASE"));
                assertDecimal("77872.42", amount(leaveLines, "INDEMNITE_CONGE_PAYE"));
                assertDecimal("15865.69", amount(absenceLines, "RETENUE_ABSENCE"));
                assertDecimal("3.34375", new BigDecimal("3")
                        .add(new BigDecimal("2.75").divide(new BigDecimal("8"))));
                assertDecimal("115865.69", amount(leaveLines, "DEDUCTION_CONGE_BASE")
                        .add(amount(absenceLines, "RETENUE_ABSENCE")));
            } finally {
                c.rollback();
            }
        }
    }

    private static int scalarInt(Connection c, String sql) throws Exception {
        try (var statement = c.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            assumeTrue(rs.next(), "592 replay fixture is incomplete");
            return rs.getInt(1);
        }
    }

    private static BigDecimal total(Map<LocalDate, BigDecimal> days) {
        return days.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal amount(List<BulletinPaieLigne> lines, String code) {
        return lines.stream().filter(l -> code.equals(l.getCodeRubrique()))
                .map(BulletinPaieLigne::getMontantSalarial).findFirst().orElseThrow();
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
