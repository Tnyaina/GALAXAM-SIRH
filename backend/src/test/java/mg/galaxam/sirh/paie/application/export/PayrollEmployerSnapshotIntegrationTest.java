package mg.galaxam.sirh.paie.application.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PayrollEmployerSnapshotIntegrationTest {
    @Autowired private DataSource dataSource;

    @Test
    void identiteFigeeResisteAuChangementDeFiliale() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    try (ResultSet database = statement.executeQuery("SELECT current_database()")) {
                        database.next();
                        assertEquals("sirh_galaxam_test", database.getString(1));
                    }
                    statement.execute("CREATE TABLE IF NOT EXISTS bulletin_paie_employeur_snapshot ("
                            + "id_bulletin INT PRIMARY KEY REFERENCES bulletin_paie(id_bulletin) ON DELETE CASCADE,"
                            + "id_filiale INT NOT NULL, raison_sociale VARCHAR(200) NOT NULL, nif VARCHAR(100),"
                            + "stat VARCHAR(100), adresse_siege TEXT, telephone VARCHAR(100), email VARCHAR(255),"
                            + "captured_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");
                }
                int employeeId = CongeTestFixtureHelper.createEmploye(connection,
                        "SNAP-" + System.nanoTime(), "Test", "Snapshot");
                CongeTestFixtureHelper.createContratActif(connection, employeeId, java.time.LocalDate.of(2030, 1, 1));
                int periodId;
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO periode_paie (code,libelle,date_debut,date_fin,id_statut_periode) "
                                + "VALUES (?, 'Snapshot test', '2030-01-01', '2030-01-31', 1) RETURNING id_periode_paie")) {
                    ps.setString(1, "2030-01-SNAP-" + employeeId);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); periodId = rs.getInt(1); }
                }
                int bulletinId;
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO bulletin_paie (id_periode_paie,id_employe,type_bulletin,reference_bulletin,"
                                + "matricule,nom_prenom,intitule_poste,nom_site,regime_intervenant,salaire_base,taux_horaire) "
                                + "VALUES (?,?,'MENSUEL',?,'SNAP','Test Snapshot','Test','Test','PERMANENT',100000,1000) "
                                + "RETURNING id_bulletin")) {
                    ps.setInt(1, periodId);
                    ps.setInt(2, employeeId);
                    ps.setString(3, "SNAP-" + employeeId);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); bulletinId = rs.getInt(1); }
                }
                var snapshots = new PayrollEmployerSnapshotService();
                snapshots.capture(bulletinId, connection);
                var captured = snapshots.find(bulletinId, connection);
                assertNotNull(captured);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE ref_filiale SET raison_sociale=? WHERE id_filiale=?")) {
                    ps.setString(1, "Nom modifié après validation");
                    ps.setInt(2, captured.id());
                    ps.executeUpdate();
                }
                assertEquals(captured.name(), snapshots.find(bulletinId, connection).name());
                assertEquals(captured.name(), PayrollExportContextResolver.namesByBulletin(periodId, connection).get(bulletinId));
            } finally {
                connection.rollback();
            }
        }
    }
}
