package mg.galaxam.sirh.paie.application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/** Couverture du pointage figé pour la population réellement incluse dans la paie. */
public final class PayrollPointageCoverage {
    private PayrollPointageCoverage() {}

    public static int frozenCount(int periodId, List<Integer> employeeIds, Connection connection) throws SQLException {
        if (employeeIds == null || employeeIds.isEmpty()) return 0;
        String placeholders = String.join(",", Collections.nCopies(employeeIds.size(), "?"));
        String sql = "SELECT COUNT(DISTINCT id_employe) FROM pointage_cumul_periode "
                + "WHERE id_periode_paie=? AND statut_snapshot IN ('GELE','CONSOMME') "
                + "AND id_employe IN (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, periodId);
            for (int index = 0; index < employeeIds.size(); index++) statement.setInt(index + 2, employeeIds.get(index));
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    public static boolean allFrozen(int periodId, List<Integer> employeeIds, Connection connection) throws SQLException {
        return employeeIds != null && !employeeIds.isEmpty()
                && frozenCount(periodId, employeeIds, connection) == employeeIds.stream().distinct().count();
    }
}
