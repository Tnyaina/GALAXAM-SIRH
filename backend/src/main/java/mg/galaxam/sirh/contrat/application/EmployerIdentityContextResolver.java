package mg.galaxam.sirh.contrat.application;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

/** Filiale employeur du dernier contrat couvert par une plage métier, bornes inclusives. */
public final class EmployerIdentityContextResolver {
    public record Employer(int id, String name, String nif, String stat, String address,
                           String phone, String email) {}

    public Employer resolve(int employeeId, LocalDate start, LocalDate end, Connection connection) throws Exception {
        if (start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("Bornes métier invalides pour l'employeur.");
        String sql = "SELECT ct.id_contrat, ct.date_debut, ct.date_fin, "
                + "f.id_filiale, f.raison_sociale, f.nif, f.stat, f.adresse_siege, f.telephone, f.email "
                + "FROM contrat_travail ct JOIN ref_filiale f ON f.id_filiale=ct.id_filiale "
                + "WHERE ct.id_employe=? AND ct.date_debut<=? "
                + "AND (ct.date_fin IS NULL OR ct.date_fin>=?) "
                + "ORDER BY LEAST(COALESCE(ct.date_fin, ?), ?) DESC, ct.date_debut DESC, ct.id_contrat DESC LIMIT 2";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, employeeId);
            statement.setDate(2, Date.valueOf(end));
            statement.setDate(3, Date.valueOf(start));
            statement.setDate(4, Date.valueOf(end));
            statement.setDate(5, Date.valueOf(end));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("MISSING_CONFIGURATION: FILIALE employé="
                        + employeeId + " campagne=[" + start + ", " + end + "]");
                LocalDate selectedDay = result.getDate("date_fin") == null ? end
                        : result.getDate("date_fin").toLocalDate().isBefore(end)
                                ? result.getDate("date_fin").toLocalDate() : end;
                Employer employer = new Employer(result.getInt("id_filiale"),
                        result.getString("raison_sociale"), result.getString("nif"), result.getString("stat"),
                        result.getString("adresse_siege"), result.getString("telephone"), result.getString("email"));
                if (result.next()) {
                    LocalDate secondStart = result.getDate("date_debut").toLocalDate();
                    Date secondEnd = result.getDate("date_fin");
                    if (!secondStart.isAfter(selectedDay)
                            && (secondEnd == null || !secondEnd.toLocalDate().isBefore(selectedDay)))
                        throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: FILIALE employé="
                                + employeeId + " au " + selectedDay);
                }
                return employer;
            }
        }
    }
}
