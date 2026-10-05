package mg.galaxam.sirh.paie.application.export;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import mg.galaxam.sirh.contrat.application.EmployerIdentityContextResolver;

/** Identité légale de l'employeur portée par les bulletins de la période. */
final class PayrollExportContextResolver {
    private PayrollExportContextResolver() {}

    record Company(String name, String nif, String address) {
        String nifCompact() { return nif.replaceAll("\\s+", ""); }
    }

    static Company forEmployee(int employeeId, LocalDate start, LocalDate end,
            Connection connection) throws Exception {
        var employer = new EmployerIdentityContextResolver().resolve(employeeId, start, end, connection);
        return new Company(employer.name(), employer.nif(), employer.address());
    }

    static Map<Integer, String> namesByBulletin(int periodId, Connection connection) throws Exception {
        Map<Integer, String> result = new LinkedHashMap<>();
        String sql = "SELECT b.id_bulletin,b.id_employe,p.date_debut,p.date_fin FROM bulletin_paie b "
                + "JOIN periode_paie p ON p.id_periode_paie=b.id_periode_paie WHERE b.id_periode_paie=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getInt(1), forEmployee(rs.getInt(2),
                        rs.getDate(3).toLocalDate(), rs.getDate(4).toLocalDate(), connection).name());
            }
        }
        return result;
    }

    static Company forPeriod(int periodId, Connection connection) throws Exception {
        String sql = "SELECT b.id_employe,p.date_debut,p.date_fin FROM bulletin_paie b "
                + "JOIN periode_paie p ON p.id_periode_paie=b.id_periode_paie "
                + "WHERE b.id_periode_paie=? ORDER BY b.id_employe";
        List<Company> companies = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Company company = forEmployee(rs.getInt(1), rs.getDate(2).toLocalDate(),
                            rs.getDate(3).toLocalDate(), connection);
                    if (!companies.contains(company)) companies.add(company);
                }
            }
        }
        if (companies.isEmpty()) throw new IllegalStateException(
                "Export impossible : aucun bulletin ne permet de déterminer la filiale de la période " + periodId + ".");
        if (companies.size() != 1) throw new IllegalStateException(
                "Export impossible : sélectionnez une seule filiale employeur pour cette période.");
        Company company = companies.get(0);
        if (company.name() == null || company.name().isBlank() || company.nif() == null || company.nif().isBlank())
            throw new IllegalStateException("Export impossible : renseignez la raison sociale et le NIF de la filiale.");
        return company;
    }

    static Company forQuarter(int year, int quarter, Connection connection) throws Exception {
        List<Company> companies = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id_periode_paie FROM periode_paie WHERE EXTRACT(YEAR FROM date_fin)=? " +
                "AND EXTRACT(MONTH FROM date_fin) BETWEEN ? AND ? ORDER BY date_debut")) {
            ps.setInt(1, year);
            ps.setInt(2, (quarter - 1) * 3 + 1);
            ps.setInt(3, (quarter - 1) * 3 + 3);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Company company = forPeriod(rs.getInt(1), connection);
                    if (!companies.contains(company)) companies.add(company);
                }
            }
        }
        if (companies.isEmpty()) throw new IllegalStateException(
                "Export impossible : aucune période ne permet de déterminer la filiale du trimestre.");
        if (companies.size() != 1) throw new IllegalStateException(
                "Export impossible : plusieurs filiales employeurs figurent dans le trimestre.");
        return companies.get(0);
    }

    /** Départ déclaré dans une campagne : STC, puis dernier contrat applicable. */
    static LocalDate departureForPeriod(int employeeId, LocalDate start, LocalDate end, Connection connection) throws Exception {
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
