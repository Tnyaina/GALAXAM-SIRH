package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Date;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PointageFixedMonthlySnapshotTest {
    @Autowired DataSource dataSource;
    @Autowired PointageSyntheseService synthese;
    @Autowired PointageService pointage;

    @Test
    void prorataMensuelUtiliseLeTarifDeChaqueSiteEtNeRepliePasUneRegleDesactivee() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String suffix = Long.toString(System.nanoTime(), 36);
                String parameterCode = "TEST_INDEMNITE_" + suffix;
                int secondSite;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id_site FROM ref_site WHERE id_site <> 1 ORDER BY id_site LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    secondSite = rs.getInt(1);
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO parametre_global (code_parametre, categorie, valeur, date_debut, est_actif) "
                                + "VALUES (?, 'POINTAGE', '30000', '2026-01-01', TRUE)")) {
                    ps.setString(1, parameterCode);
                    ps.executeUpdate();
                }
                int allowance;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ref_indemnite (code, libelle, imposable_irsa, soumis_cotisation, "
                                + "fractionnable, est_actif) VALUES (?, 'Test prorata', FALSE, FALSE, TRUE, TRUE) "
                                + "RETURNING id_indemnite")) {
                    ps.setString(1, "PR" + suffix);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        allowance = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO site_indemnite_config (id_site, id_indemnite, periodicite, "
                                + "montant_reference, date_debut, est_actif) "
                                + "VALUES (?, ?, 'MENSUEL_PRORATISE', ?, '2026-01-01', TRUE)")) {
                    ps.setInt(1, 1);
                    ps.setInt(2, allowance);
                    ps.setBigDecimal(3, new BigDecimal("30000"));
                    ps.executeUpdate();
                    ps.setInt(1, secondSite);
                    ps.setBigDecimal(3, new BigDecimal("60000"));
                    ps.executeUpdate();
                }
                Date date = Date.valueOf("2026-09-15");
                PointageService.DailyAllowanceTariff oldSite =
                        pointage.loadDailyAllowanceTariff(c, 1, allowance, date);
                PointageService.DailyAllowanceTariff newSite =
                        pointage.loadDailyAllowanceTariff(c, secondSite, allowance, date);
                assertEquals(0, new BigDecimal("1500").compareTo(
                        pointage.dailyAllowanceUnitAmount(oldSite, date, 20, c)));
                assertEquals(0, new BigDecimal("3000").compareTo(
                        pointage.dailyAllowanceUnitAmount(newSite, date, 20, c)));

                PointageService.DailyAllowanceTariff zeroTariff = new PointageService.DailyAllowanceTariff(
                        allowance, "TEST", "Test", true, "MENSUEL_PRORATISE", BigDecimal.ZERO);
                assertEquals(0, BigDecimal.ZERO.compareTo(
                        pointage.dailyAllowanceUnitAmount(zeroTariff, date, 20, c)));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void montantFixeMensuelEstRepartiParJoursCalendairesEntreDeuxSites() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int site2;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_site FROM ref_site WHERE id_site <> 1 ORDER BY id_site LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Le jeu de test doit contenir un second site.");
                    site2 = rs.getInt(1);
                }
                String suffix = Long.toString(System.nanoTime(), 36);
                int employee;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, "
                                + "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone) "
                                + "VALUES (?, 'Fixe', 'Mensuel', 'M', '1990-01-15', 'Antananarivo', ?, "
                                + "'2010-01-15', 'TNR', 'Test', '0340000000') RETURNING id_employe")) {
                    ps.setString(1, "FX-" + suffix);
                    ps.setString(2, Long.toString(Math.abs(System.nanoTime() % 1_000_000_000_000L)));
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        employee = rs.getInt(1);
                    }
                }
                int contract = CongeTestFixtureHelper.createContratActif(c, employee, LocalDate.of(2026, 8, 1));
                int firstAssignment = CongeTestFixtureHelper.createAffectation(c, employee, contract,
                        LocalDate.of(2026, 9, 1), new BigDecimal("1500000"));
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe_affectation_carriere SET date_fin = '2026-09-10' WHERE id_affectation = ?")) {
                    ps.setInt(1, firstAssignment);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO employe_affectation_carriere "
                                + "(id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, "
                                + "id_departement, id_service, id_fonction, id_categorie_professionnelle, "
                                + "id_mode_calcul_paie, salaire_base, date_debut) "
                                + "VALUES (?, ?, 4, 1, ?, 1, 1, 3, 1, 1, 1500000, '2026-09-11')")) {
                    ps.setInt(1, employee);
                    ps.setInt(2, contract);
                    ps.setInt(3, site2);
                    ps.executeUpdate();
                }
                int period = CongeTestFixtureHelper.createPeriodePaie(c, "FX-" + suffix,
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "EN_COURS");
                int allowance;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ref_indemnite (code, libelle, imposable_irsa, soumis_cotisation, est_actif) "
                                + "VALUES (?, 'Fixe test', FALSE, FALSE, TRUE) RETURNING id_indemnite")) {
                    ps.setString(1, "FIX" + suffix);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        allowance = rs.getInt(1);
                    }
                }
                String tariff = "INSERT INTO site_indemnite_config "
                        + "(id_site, id_indemnite, periodicite, montant_reference, date_debut, est_actif) "
                        + "VALUES (?, ?, 'FIXE_MENSUEL', ?, '2026-01-01', TRUE)";
                try (PreparedStatement ps = c.prepareStatement(tariff)) {
                    ps.setInt(1, 1); ps.setInt(2, allowance); ps.setBigDecimal(3, new BigDecimal("30000")); ps.executeUpdate();
                    ps.setInt(1, site2); ps.setInt(2, allowance); ps.setBigDecimal(3, new BigDecimal("60000")); ps.executeUpdate();
                }
                int snapshot;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_cumul_periode (id_employe, id_periode_paie, nb_jours_ouvrables) "
                                + "VALUES (?, ?, 22) RETURNING id_cumul")) {
                    ps.setInt(1, employee);
                    ps.setInt(2, period);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        snapshot = rs.getInt(1);
                    }
                }
                assertEquals(0, new BigDecimal("50000.00").compareTo(
                        synthese.estimateFixedMonthlyAmounts(employee, period, c).get("FIX" + suffix)));
                assertEquals(0, new BigDecimal("50000.00").compareTo(
                        synthese.loadAllowanceAmounts(employee, period, LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 30), "EN_COURS", c).get("FIX" + suffix)));
                synthese.enregistrerIndemnitesFixesMensuelles(snapshot, employee, period, c);
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pointage_cumul_periode SET statut_snapshot = 'GELE' WHERE id_cumul = ?")) {
                    ps.setInt(1, snapshot);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT montant FROM pointage_snapshot_indemnite WHERE id_cumul = ? AND id_indemnite = ?")) {
                    ps.setInt(1, snapshot);
                    ps.setInt(2, allowance);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, new BigDecimal("50000.00").compareTo(rs.getBigDecimal(1)));
                    }
                }
                assertEquals(0, new BigDecimal("50000.00").compareTo(
                        synthese.loadAllowanceAmounts(employee, period, LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 30), "VALIDEE", c)
                                .get("FIX" + suffix)));
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE site_indemnite_config SET montant_reference = 99000 WHERE id_indemnite = ?")) {
                    ps.setInt(1, allowance);
                    ps.executeUpdate();
                }
                assertEquals(0, new BigDecimal("50000.00").compareTo(
                        synthese.loadAllowanceAmounts(employee, period, LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 30), "VALIDEE", c)
                                .get("FIX" + suffix)),
                        "La lecture après gel ne doit jamais reprendre les tarifs modifiés après coup.");
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pointage_cumul_periode SET statut_snapshot = 'CONSOMME' WHERE id_cumul = ?")) {
                    ps.setInt(1, snapshot);
                    ps.executeUpdate();
                }
                assertEquals(0, new BigDecimal("50000.00").compareTo(
                        synthese.loadAllowanceAmounts(employee, period, LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 30), "PAIE_VALIDEE", c).get("FIX" + suffix)));
                assertEquals(0, new BigDecimal("50000.00").compareTo(
                        synthese.loadFixedMonthlyAmounts(employee, period, LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 30), "PAIE_VALIDEE", c).get("FIX" + suffix)));
                assertTrue(synthese.loadAllowanceAmounts(employee, period, LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 7), "VALIDEE", c).isEmpty(),
                        "Un tronçon hebdomadaire ne doit pas réutiliser le snapshot mensuel.");
            } finally {
                c.rollback();
            }
        }
    }
}
