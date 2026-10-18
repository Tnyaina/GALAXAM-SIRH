package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
import mg.galaxam.sirh.paie.api.BulletinReglementController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Reproductions transactionnelles des parcours audités, sans correction du code métier. */
@SpringBootTest
@ActiveProfiles("test")
class PayrollAuditReproductionTest {
    @Autowired DataSource dataSource;
    @Autowired PayrollPopulationResolver populationResolver;
    @Autowired EmploymentContextResolver employmentContextResolver;
    @Autowired PayrollService payrollService;

    @Test
    void inactiveEnCoursDePeriodeResteDansLaPopulationAvecCouverturePartielle() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                CongeTestFixtureHelper.cleanDatabase(c);
                int employee = CongeTestFixtureHelper.createEmploye(c, "AUDIT-INACTIF", "Audit", "Sortie");
                int contract = CongeTestFixtureHelper.createContratActif(c, employee, LocalDate.of(2026, 10, 1));
                int assignment = CongeTestFixtureHelper.createAffectation(c, employee, contract,
                        LocalDate.of(2026, 10, 1), new BigDecimal("300000"));
                int period = CongeTestFixtureHelper.createPeriodePaie(c, "AUDIT-OCT-2026",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), "VALIDEE");
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf("2026-10-15")); ps.setInt(2, contract); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe_affectation_carriere SET date_fin = ? WHERE id_affectation = ?")) {
                    ps.setDate(1, Date.valueOf("2026-10-15")); ps.setInt(2, assignment); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe SET est_actif = FALSE WHERE id_employe = ?")) {
                    ps.setInt(1, employee); ps.executeUpdate();
                }
                var member = populationResolver.resolve(period, c).members().stream()
                        .filter(m -> m.employeeId() == employee).findFirst().orElseThrow();
                assertEquals("INCLUDED", member.decision());
                var coverage = employmentContextResolver.resolveCoverage(employee,
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), c);
                assertEquals(LocalDate.of(2026, 10, 15), coverage.lastDay());
                assertEquals(15, coverage.days());
            } finally { c.rollback(); }
        }
    }

    @Test
    void bulletinStcDansLaMemePeriodeNeBloquePasValidationMensuelle() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                CongeTestFixtureHelper.cleanDatabase(c);
                int employee = CongeTestFixtureHelper.createEmploye(c, "AUDIT-STC", "Audit", "Stc");
                int contract = CongeTestFixtureHelper.createContratActif(c, employee, LocalDate.of(2026, 10, 1));
                CongeTestFixtureHelper.createAffectation(c, employee, contract,
                        LocalDate.of(2026, 10, 1), new BigDecimal("300000"));
                int period = CongeTestFixtureHelper.createPeriodePaie(c, "AUDIT-STC-2026",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), "VALIDEE");
                for (String type : new String[] {"MENSUEL", "STC"}) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO bulletin_paie (id_periode_paie,id_employe,type_bulletin,reference_bulletin,"
                            + "matricule,nom_prenom,intitule_poste,nom_site,regime_intervenant,salaire_base,"
                            + "taux_horaire,net_a_payer,statut) VALUES (?,?,?,?,?,?,?,?,?,300000,1730,300000,?)")) {
                        ps.setInt(1, period); ps.setInt(2, employee); ps.setString(3, type);
                        ps.setString(4, "AUDIT-" + type); ps.setString(5, "AUDIT-STC");
                        ps.setString(6, "Audit Stc"); ps.setString(7, "Salarié");
                        ps.setString(8, "Antananarivo"); ps.setString(9, "PERMANENT");
                        ps.setString(10, "STC".equals(type) ? "VALIDE" : "CALCULE");
                        ps.executeUpdate();
                    }
                }
                assertDoesNotThrow(() -> payrollService.validerPaiePeriode(period, null, c));
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT type_bulletin,statut FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ?")) {
                    ps.setInt(1, employee); ps.setInt(2, period);
                    try (var rs = ps.executeQuery()) {
                        int count = 0;
                        while (rs.next()) {
                            assertEquals("VALIDE", rs.getString("statut"));
                            count++;
                        }
                        assertEquals(2, count);
                    }
                }
            } finally { c.rollback(); }
        }
    }

    @Test
    void modificationSalaireApresCalculBloqueValidation() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                CongeTestFixtureHelper.cleanDatabase(c);
                int employee = CongeTestFixtureHelper.createEmploye(c, "AUDIT-SALAIRE", "Audit", "Salaire");
                int contract = CongeTestFixtureHelper.createContratActif(c, employee, LocalDate.of(2026, 10, 1));
                int assignment = CongeTestFixtureHelper.createAffectation(c, employee, contract,
                        LocalDate.of(2026, 10, 1), new BigDecimal("300000"));
                int period = CongeTestFixtureHelper.createPeriodePaie(c, "AUDIT-SALAIRE-2026",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), "VALIDEE");
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO bulletin_paie (id_periode_paie,id_employe,type_bulletin,reference_bulletin,"
                        + "matricule,nom_prenom,intitule_poste,nom_site,regime_intervenant,salaire_base,"
                        + "taux_horaire,net_a_payer,statut) VALUES (?,?,'MENSUEL','AUDIT-SALAIRE',"
                        + "'AUDIT-SALAIRE','Audit Salaire','Salarié','Antananarivo','PERMANENT',300000,1730,300000,'CALCULE')")) {
                    ps.setInt(1, period); ps.setInt(2, employee); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe_affectation_carriere SET salaire_base = 450000 WHERE id_affectation = ?")) {
                    ps.setInt(1, assignment); ps.executeUpdate();
                }
                var failure = assertThrows(IllegalStateException.class,
                        () -> payrollService.validerPaiePeriode(period, null, c));
                assertTrue(failure.getMessage().startsWith("PAYROLL_RESULT_OBSOLETE"), failure.getMessage());
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT statut,salaire_base FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ?")) {
                    ps.setInt(1, employee); ps.setInt(2, period);
                    try (var rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("CALCULE", rs.getString("statut"));
                        assertEquals(0, new BigDecimal("300000").compareTo(rs.getBigDecimal("salaire_base")));
                    }
                }
            } finally { c.rollback(); }
        }
    }

    @Test
    void calculIndividuelSalarieSansContratSurPeriodeEstRejete() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                CongeTestFixtureHelper.cleanDatabase(c);
                int employee = CongeTestFixtureHelper.createEmploye(c, "AUDIT-EXCLU", "Audit", "Exclu");
                int contract = CongeTestFixtureHelper.createContratActif(c, employee, LocalDate.of(2020, 1, 1));
                int assignment = CongeTestFixtureHelper.createAffectation(c, employee, contract,
                        LocalDate.of(2020, 1, 1), new BigDecimal("300000"));
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE contrat_travail SET date_fin = '2020-03-01' WHERE id_contrat = ?")) {
                    ps.setInt(1, contract); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe_affectation_carriere SET date_fin = '2020-03-01' WHERE id_affectation = ?")) {
                    ps.setInt(1, assignment); ps.executeUpdate();
                }
                int period = CongeTestFixtureHelper.createPeriodePaie(c, "AUDIT-EXCLU-2026",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), "VALIDEE");
                assertThrows(Exception.class, () -> payrollService.calculerBulletinEmploye(employee, period, false, null, c));
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ?")) {
                    ps.setInt(1, employee); ps.setInt(2, period);
                    try (var rs = ps.executeQuery()) { rs.next(); assertEquals(0, rs.getInt(1)); }
                }
            } finally { c.rollback(); }
        }
    }

    @Test
    void changementDateSortieApresCalculBloqueValidation() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                CongeTestFixtureHelper.cleanDatabase(c);
                int employee = CongeTestFixtureHelper.createEmploye(c, "AUDIT-DEPART", "Audit", "Depart");
                int contract = CongeTestFixtureHelper.createContratActif(c, employee, LocalDate.of(2026, 10, 1));
                int assignment = CongeTestFixtureHelper.createAffectation(c, employee, contract,
                        LocalDate.of(2026, 10, 1), new BigDecimal("300000"));
                int period = CongeTestFixtureHelper.createPeriodePaie(c, "AUDIT-DEPART-2026",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), "VALIDEE");
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO bulletin_paie (id_periode_paie,id_employe,type_bulletin,reference_bulletin,"
                        + "matricule,nom_prenom,intitule_poste,nom_site,regime_intervenant,salaire_base,"
                        + "taux_horaire,net_a_payer,statut,date_calcul) VALUES (?,?,'MENSUEL','AUDIT-DEPART',"
                        + "'AUDIT-DEPART','Audit Depart','Salarié','Antananarivo','PERMANENT',"
                        + "300000,1730,300000,'CALCULE',CURRENT_TIMESTAMP - INTERVAL '1 hour')")) {
                    ps.setInt(1, period); ps.setInt(2, employee); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE contrat_travail SET date_fin='2026-10-15',updated_at=CURRENT_TIMESTAMP WHERE id_contrat=?")) {
                    ps.setInt(1, contract); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe_affectation_carriere SET date_fin='2026-10-15',updated_at=CURRENT_TIMESTAMP WHERE id_affectation=?")) {
                    ps.setInt(1, assignment); ps.executeUpdate();
                }
                var failure = assertThrows(IllegalStateException.class,
                        () -> payrollService.validerPaiePeriode(period, null, c));
                assertTrue(failure.getMessage().startsWith("PAYROLL_RESULT_OBSOLETE"), failure.getMessage());
            } finally { c.rollback(); }
        }
    }

    @Test
    void csvLotExclutLesLignesRejetees() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ScriptUtils.executeSqlScript(c, new FileSystemResource(
                        "conception/bdd/migrations/26-10-18-sp84-bulletin-reglements.sql"));
                CongeTestFixtureHelper.cleanDatabase(c);
                int period = CongeTestFixtureHelper.createPeriodePaie(c, "AUDIT-CSV-2026",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), "VALIDEE");
                long lot;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO paie_bulletin_virement_lot(id_periode_paie) VALUES (?) RETURNING id_lot")) {
                    ps.setInt(1, period);
                    try (var rs = ps.executeQuery()) { rs.next(); lot = rs.getLong(1); }
                }
                for (String state : new String[] {"EN_ATTENTE", "REJETE"}) {
                    int employee = CongeTestFixtureHelper.createEmploye(c, "AUDIT-CSV-" + state,
                            "Audit", state);
                    int bulletin;
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO bulletin_paie (id_periode_paie,id_employe,type_bulletin,reference_bulletin,"
                            + "matricule,nom_prenom,intitule_poste,nom_site,regime_intervenant,salaire_base,"
                            + "taux_horaire,net_a_payer,statut) VALUES (?,?,'MENSUEL',?,?,?,'Salarié',"
                            + "'Antananarivo','PERMANENT',300000,1730,300000,'VALIDE') RETURNING id_bulletin")) {
                        ps.setInt(1, period); ps.setInt(2, employee); ps.setString(3, "AUDIT-" + state);
                        ps.setString(4, "AUDIT-CSV-" + state); ps.setString(5, "Audit " + state);
                        try (var rs = ps.executeQuery()) { rs.next(); bulletin = rs.getInt(1); }
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO paie_bulletin_virement_ligne(id_lot,id_bulletin,montant,beneficiaire,"
                            + "banque,rib,statut) VALUES (?,?,300000,?,'Banque audit','RIB-AUDIT',?)")) {
                        ps.setLong(1, lot); ps.setInt(2, bulletin); ps.setString(3, "Audit " + state);
                        ps.setString(4, state); ps.executeUpdate();
                    }
                }
                var controller = new BulletinReglementController(new SingleConnectionDataSource(c, true));
                String csv = new String(controller.csv(lot).getBody(), StandardCharsets.UTF_8);
                assertTrue(csv.contains("AUDIT-EN_ATTENTE"));
                assertFalse(csv.contains("AUDIT-REJETE"));
            } finally { c.rollback(); }
        }
    }
}
