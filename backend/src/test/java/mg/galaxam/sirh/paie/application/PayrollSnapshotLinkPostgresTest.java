package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PayrollSnapshotLinkPostgresTest {
    @Test
    void refusesCrossEmployeePeriodAndVersionInOneTransaction() throws Exception {
        String url = System.getenv("GALAXAM_FIX3_DISPOSABLE_DB_URL");
        Assumptions.assumeTrue(url != null && url.contains("fix3_disposable"),
                "Base PostgreSQL explicitement jetable requise");
        try (Connection connection = DriverManager.getConnection(url,
                System.getenv("GALAXAM_FIX3_DISPOSABLE_DB_USER"),
                System.getenv("GALAXAM_FIX3_DISPOSABLE_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE bulletin_paie (id_bulletin int primary key, id_employe int, "
                        + "id_periode_paie int, id_snapshot_pointage int, version_snapshot_pointage int)");
                statement.execute("CREATE TEMP TABLE pointage_cumul_periode (id_cumul int primary key, "
                        + "id_employe int, id_periode_paie int, version_snapshot int, statut_snapshot text)");
                statement.execute("INSERT INTO bulletin_paie VALUES (1, 10, 20, NULL, NULL)");
                statement.execute("INSERT INTO pointage_cumul_periode VALUES "
                        + "(100, 10, 20, 2, 'GELE'), (101, 11, 20, 2, 'GELE'), "
                        + "(102, 10, 21, 2, 'GELE'), (103, 10, 20, 2, 'REMPLACE')");
            }
            for (int badSnapshot : new int[]{101, 102, 103}) {
                assertThrows(SQLException.class, () -> link(connection, badSnapshot, 2));
                assertNull(linkedSnapshot(connection));
            }
            assertThrows(SQLException.class, () -> link(connection, 100, 3));
            assertNull(linkedSnapshot(connection));
            link(connection, 100, 2);
            assertEquals(100, linkedSnapshot(connection));
            linkWithoutSnapshot(connection);
            assertNull(linkedSnapshot(connection));
            connection.rollback();
        }
    }

    private static void link(Connection connection, int snapshot, int version) throws Exception {
        PayrollCalculationInput input = new PayrollCalculationInput();
        // The normal assembler supplies this record; only its identity is used here.
        input.setAttendanceInput(new PayrollAttendanceInput(snapshot, version, 10, 20, Instant.now(),
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                null, null, null, null, null, null, null, null));
        invokeLink(connection, input);
    }

    private static void linkWithoutSnapshot(Connection connection) throws Exception {
        invokeLink(connection, new PayrollCalculationInput());
    }

    private static void invokeLink(Connection connection, PayrollCalculationInput input) throws Exception {
        var method = PayrollService.class.getDeclaredMethod("lierBulletinAuSnapshot",
                Integer.class, PayrollCalculationInput.class, Connection.class);
        method.setAccessible(true);
        try {
            method.invoke(new PayrollService(), 1, input, connection);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            throw exception;
        }
    }

    private static Integer linkedSnapshot(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_snapshot_pointage FROM bulletin_paie WHERE id_bulletin = 1");
             var rows = statement.executeQuery()) {
            rows.next();
            return (Integer) rows.getObject(1);
        }
    }
}
