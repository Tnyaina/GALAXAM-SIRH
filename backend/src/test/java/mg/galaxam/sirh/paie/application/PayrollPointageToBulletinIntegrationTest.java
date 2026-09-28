package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.pointage.application.PointageSyntheseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Chaîne réelle JDBC : contrat et journée saisis, snapshot figé, bulletin calculé. */
@SpringBootTest
@ActiveProfiles("test")
class PayrollPointageToBulletinIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired PointageSyntheseService pointageSyntheseService;
    @Autowired PayrollService payrollService;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void uneJourneeCompleteProduitUnBulletinAvecSaProvenancePointage(boolean complementDaf) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int employeeId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, "
                                + "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone) "
                                + "VALUES (?, 'Recette', 'Paie', 'M', '1990-01-15', 'Antananarivo', ?, "
                                + "'2010-01-15', 'TNR', 'Rue Test', '0340000000') RETURNING id_employe")) {
                    statement.setString(1, "E2E-" + Long.toString(System.nanoTime(), 36).toUpperCase());
                    statement.setString(2, String.format("%012d", Math.abs(System.nanoTime() % 1_000_000_000_000L)));
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        employeeId = result.getInt(1);
                    }
                }
                // Le jeu de tests partagé peut contenir des employés persistés par d'autres suites.
                // Les neutraliser uniquement dans cette transaction, puis tout annuler en sortie.
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE employe SET est_actif = FALSE WHERE id_employe <> ?")) {
                    statement.setInt(1, employeeId);
                    statement.executeUpdate();
                }
                int contractId = CongeTestFixtureHelper.createContratActif(connection, employeeId,
                        LocalDate.of(2026, 9, 24));
                CongeTestFixtureHelper.createAffectation(connection, employeeId, contractId,
                        LocalDate.of(2026, 9, 24), new BigDecimal("350000"));
                int periodId = CongeTestFixtureHelper.createPeriodePaie(connection,
                        "E2E-" + employeeId, LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), "VALIDEE");
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, "
                                + "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, "
                                + "heure_entree_matin_effective, total_heures, version) "
                                + "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT'), "
                                + "'08:00', '12:00', '13:00', '17:00', '08:00', 8, 1)")) {
                    statement.setInt(1, employeeId);
                    statement.setInt(2, periodId);
                    statement.setDate(3, Date.valueOf("2026-09-24"));
                    statement.executeUpdate();
                    statement.setDate(3, Date.valueOf("2026-09-25"));
                    statement.executeUpdate();
                }
                if (complementDaf) {
                    try (var statement=connection.prepareStatement(
                            "INSERT INTO pointage_ajustement_periode (id_employe,id_periode_paie,minutes_signees,motif,saisi_par,valide_par) "
                            + "VALUES (?,?,600,'Complément fin de période validé DAF',"
                            + "(SELECT MIN(id_utilisateur) FROM utilisateur),(SELECT MIN(id_utilisateur) FROM utilisateur))")) {
                        statement.setInt(1,employeeId);statement.setInt(2,periodId);statement.executeUpdate();
                    }
                }
                var preflight = pointageSyntheseService.preflightPeriode(periodId, connection);
                assertTrue(preflight.pret(), () -> "Préflight : " + preflight);
                pointageSyntheseService.gelerPeriode(periodId, null, connection);

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO paie_avance_acompte (id_employe, id_periode_paie, type_mouvement, id_type_mouvement, "
                        + "sens, montant, montant_initial, solde_restant, motif, statut) "
                        + "SELECT ?, ?, code, id_type_mouvement, sens, 10000, 10000, 10000, 'Recette recalcul', 'APPROUVE' "
                        + "FROM ref_type_mouvement_paie WHERE code IN ('PRIME_EXCEPTIONNELLE', 'ACOMPTE_15')")) {
                    statement.setInt(1, employeeId);
                    statement.setInt(2, periodId);
                    assertEquals(2, statement.executeUpdate());
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE paie_avance_acompte SET statut = 'EN_ATTENTE' "
                        + "WHERE id_employe = ? AND type_mouvement = 'PRIME_EXCEPTIONNELLE'")) {
                    statement.setInt(1, employeeId);
                    statement.executeUpdate();
                }
                int loanId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO paie_pret (id_employe, reference_pret, montant_total, montant_mensualite, "
                        + "nb_echeances, date_premiere_echeance, capital_restant_du, statut, motif) "
                        + "VALUES (?, ?, 12000, 12000, 1, '2026-09-25', 12000, 'EN_COURS', 'Recette recalcul') "
                        + "RETURNING id_pret")) {
                    statement.setInt(1, employeeId);
                    statement.setString(2, "LOAN-E2E-" + employeeId);
                    try (ResultSet rows = statement.executeQuery()) { rows.next(); loanId = rows.getInt(1); }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO paie_pret_echeance (id_pret, numero_echeance, date_echeance, "
                        + "montant_echeance, capital_restant_apres, montant_restant_echeance) "
                        + "VALUES (?, 1, '2026-09-25', 12000, 0, 12000)")) {
                    statement.setInt(1, loanId);
                    statement.executeUpdate();
                }

                assertThrows(IllegalStateException.class, () -> payrollService.calculerBulletinEmploye(
                        employeeId, periodId, false, null, connection));
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE paie_avance_acompte SET statut = 'APPROUVE' "
                        + "WHERE id_employe = ? AND type_mouvement = 'PRIME_EXCEPTIONNELLE'")) {
                    statement.setInt(1, employeeId);
                    statement.executeUpdate();
                }
                var result = payrollService.calculerBulletinEmploye(employeeId, periodId,
                        false, null, connection);
                assertNotNull(result.getBulletin().getIdBulletin());
                assertEquals(0, new BigDecimal("350000").compareTo(result.getBulletin().getSalaireBase()));
                assertTrue(result.getLignes().stream()
                        .anyMatch(line -> "SALAIRE_BASE".equals(line.getCodeRubrique())));
                assertNotNull(result.getBulletin().getIdSnapshotPointage());
                if (complementDaf) {
                    var hs30=result.getLignes().stream().filter(l->"HEURES_SUP_30".equals(l.getCodeRubrique())).findFirst().orElseThrow();
                    var hs50=result.getLignes().stream().filter(l->"HEURES_SUP_50".equals(l.getCodeRubrique())).findFirst().orElseThrow();
                    assertEquals(0,new BigDecimal("8").compareTo(hs30.getQuantite()));
                    assertEquals(0,new BigDecimal("2").compareTo(hs50.getQuantite()));
                    try(var ps=connection.prepareStatement(
                            "SELECT minutes_hs30_finales,minutes_hs50_finales,total_heures_travaillees FROM pointage_cumul_periode WHERE id_employe=? AND id_periode_paie=?")) {
                        ps.setInt(1,employeeId);ps.setInt(2,periodId);
                        try(var rs=ps.executeQuery()){assertTrue(rs.next());assertEquals(480,rs.getInt(1));assertEquals(120,rs.getInt(2));assertEquals(0,new BigDecimal("16").compareTo(rs.getBigDecimal(3)));}
                    }
                }
                var simulation = payrollService.calculerBulletinEmploye(employeeId, periodId,
                        true, null, connection);
                assertEquals(result.getBulletin().getTotalGainsBruts(), simulation.getBulletin().getTotalGainsBruts(),
                        "Une simulation après calcul doit conserver la prime déjà imputée");
                var recalcul = payrollService.calculerBulletinEmploye(employeeId, periodId,
                        false, null, connection);
                assertEquals(result.getBulletin().getIdBulletin(), recalcul.getBulletin().getIdBulletin());
                assertEquals(result.getBulletin().getNetAPayer(), recalcul.getBulletin().getNetAPayer(),
                        "Un recalcul ne doit ni perdre la prime ni prélever une seconde fois l'acompte ou le prêt");
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT capital_rembourse FROM paie_pret WHERE id_pret = ?")) {
                    statement.setInt(1, loanId);
                    try (ResultSet rows = statement.executeQuery()) {
                        rows.next();
                        assertEquals(0, new BigDecimal("12000").compareTo(rows.getBigDecimal(1)));
                    }
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
