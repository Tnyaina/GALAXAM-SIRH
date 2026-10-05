package mg.galaxam.sirh.paie.application.export;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

/** Date de débauchage déclarée dans une campagne : STC, puis dernier contrat applicable. */
final class PayrollExportDepartureResolver {
    private PayrollExportDepartureResolver() {}

    static LocalDate forPeriod(int employeeId, LocalDate start, LocalDate end, Connection connection) throws Exception {
        if (start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("Bornes de campagne obligatoires pour la date de débauchage.");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT date_depart FROM paie_stc WHERE id_employe=? "
                        + "AND date_depart BETWEEN ? AND ? ORDER BY date_depart DESC LIMIT 2")) {
            statement.setInt(1, employeeId);
            statement.setDate(2, Date.valueOf(start));
            statement.setDate(3, Date.valueOf(end));
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    LocalDate departure = result.getDate(1).toLocalDate();
                    if (result.next()) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: plusieurs STC "
                            + "pour l'employé " + employeeId + " dans [" + start + ", " + end + "]");
                    return departure;
                }
            }
        }
        String sql = "SELECT date_fin FROM contrat_travail WHERE id_employe=? "
                + "AND date_debut<=? AND (date_fin IS NULL OR date_fin>=?) "
                + "ORDER BY date_debut DESC, id_contrat DESC LIMIT 2";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, employeeId);
            statement.setDate(2, Date.valueOf(end));
            statement.setDate(3, Date.valueOf(start));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                Date finish = result.getDate(1);
                if (result.next() && result.getDate(1) != null && finish != null
                        && result.getDate(1).equals(finish))
                    throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: plusieurs fins de contrat "
                            + "pour l'employé " + employeeId + " au " + finish);
                return finish != null && !finish.toLocalDate().isAfter(end) ? finish.toLocalDate() : null;
            }
        }
    }
}
