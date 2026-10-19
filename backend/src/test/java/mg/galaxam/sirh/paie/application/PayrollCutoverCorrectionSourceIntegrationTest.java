package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Vérifie sur PostgreSQL que la source de correction ne finance pas deux mouvements actifs. */
@SpringBootTest
@ActiveProfiles("test")
class PayrollCutoverCorrectionSourceIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void uneCorrectionNePeutServirDeuxFoisMaisUneAnnulationResteHistorique() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (var ps = c.prepareStatement("SELECT current_database()")) {
                    try (var rs = ps.executeQuery()) {
                        rs.next();
                        assertEquals("sirh_galaxam_test", rs.getString(1));
                    }
                }
                // La DDL est transactionnelle ; la base de recette n'est pas modifiée par ce test.
                c.createStatement().execute("ALTER TABLE paie_avance_acompte ADD COLUMN IF NOT EXISTS "
                        + "id_conge_cutover_correction INT REFERENCES conge_cutover_correction(id_correction)");
                c.createStatement().execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_paie_regularisation_conge_cutover_active "
                        + "ON paie_avance_acompte(id_conge_cutover_correction) "
                        + "WHERE id_conge_cutover_correction IS NOT NULL AND statut NOT IN ('REJETE', 'ANNULE')");
                int employee = CongeTestFixtureHelper.createEmploye(c,
                        "REG-" + Long.toString(System.nanoTime(), 36), "Source", "Congé");
                int actor = CongeTestFixtureHelper.createAdminUser(c,
                        "reg-" + Long.toString(System.nanoTime(), 36));
                int reprise = mouvement(c, employee);
                int ajustement = mouvement(c, employee);
                int correction;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO conge_cutover_correction "
                        + "(id_employe,id_mouvement_reprise,id_mouvement_ajustement,ancien_reliquat_jours,"
                        + "nouveau_reliquat_jours,ancien_reliquat_valeur,nouveau_reliquat_valeur,"
                        + "ancien_taux,nouveau_taux,regularisation_a_examiner,motif,corrige_par) "
                        + "VALUES (?,?,?,2,2,20000,24000,10000,12000,4000,'Rectification de recette',?) "
                        + "RETURNING id_correction")) {
                    ps.setInt(1, employee); ps.setInt(2, reprise); ps.setInt(3, ajustement);
                    ps.setInt(4, actor);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); correction = rs.getInt(1); }
                }
                int first = mouvementPaie(c, employee, correction);
                Savepoint beforeDuplicate = c.setSavepoint();
                SQLException duplicate = assertThrows(SQLException.class,
                        () -> mouvementPaie(c, employee, correction));
                assertEquals("23505", duplicate.getSQLState());
                c.rollback(beforeDuplicate);
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE paie_avance_acompte SET statut='ANNULE' WHERE id_avance=?")) {
                    ps.setInt(1, first); ps.executeUpdate();
                }
                mouvementPaie(c, employee, correction);
            } finally {
                c.rollback();
            }
        }
    }

    private static int mouvement(Connection c, int employee) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                + "(id_employe,type_mouvement,nb_jours,date_mouvement,commentaire) "
                + "VALUES (?,'AJUSTEMENT',2,CURRENT_DATE,'Recette') RETURNING id_mouvement")) {
            ps.setInt(1, employee);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static int mouvementPaie(Connection c, int employee, int correction) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO paie_avance_acompte "
                + "(id_employe,type_mouvement,sens,montant,montant_initial,solde_restant,motif,"
                + "id_conge_cutover_correction) VALUES (?,'REGULARISATION_CONGE','GAIN',4000,4000,4000,?,?) "
                + "RETURNING id_avance")) {
            ps.setInt(1, employee); ps.setString(2, "CORRECTION_CUTOVER#" + correction);
            ps.setInt(3, correction);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }
}
