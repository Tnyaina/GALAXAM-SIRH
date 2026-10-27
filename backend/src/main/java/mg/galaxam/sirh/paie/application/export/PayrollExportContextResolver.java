package mg.galaxam.sirh.paie.application.export;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.time.LocalDate;

/** Identité légale de l'employeur portée par les bulletins de la période. */
final class PayrollExportContextResolver {
    private PayrollExportContextResolver() {}

    record Company(String name, String nif, String address) {
        String nifCompact() { return nif.replaceAll("\\s+", ""); }
    }

    static Map<Integer, String> namesByBulletin(int periodId, Connection connection) throws Exception {
        return namesByBulletins(List.of(periodId), connection);
    }

    static Map<Integer, String> namesByBulletins(Collection<Integer> periodIds, Connection connection) throws Exception {
        Map<Integer, String> result = new LinkedHashMap<>();
        companiesByBulletin(periodIds, connection).forEach((bulletinId, company) -> result.put(bulletinId, company.name()));
        return result;
    }

    static Map<Integer, Company> companiesByBulletin(int periodId, Connection connection) throws Exception {
        return companiesByBulletin(List.of(periodId), connection);
    }

    private static Map<Integer, Company> companiesByBulletin(Collection<Integer> periodIds, Connection connection) throws Exception {
        Map<Integer, Company> result = new LinkedHashMap<>();
        if (periodIds == null || periodIds.isEmpty()) return result;
        List<Integer> ids = periodIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return result;
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT s.id_bulletin,s.raison_sociale,s.nif,s.adresse_siege "
                        + "FROM bulletin_paie_employeur_snapshot s JOIN bulletin_paie b "
                        + "ON b.id_bulletin=s.id_bulletin WHERE b.id_periode_paie IN (" + placeholders + ")")) {
            for (int index = 0; index < ids.size(); index++) ps.setInt(index + 1, ids.get(index));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getInt(1), new Company(rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        }
        String sql = "SELECT b.id_bulletin,b.id_employe,p.date_debut AS period_start,p.date_fin AS period_end, "
                + "candidate.date_debut AS contract_start,candidate.date_fin AS contract_end, "
                + "candidate.id_filiale,candidate.raison_sociale,candidate.nif,candidate.stat, "
                + "candidate.adresse_siege,candidate.telephone,candidate.email "
                + "FROM bulletin_paie b JOIN periode_paie p ON p.id_periode_paie=b.id_periode_paie "
                + "LEFT JOIN bulletin_paie_employeur_snapshot snap ON snap.id_bulletin=b.id_bulletin "
                + "LEFT JOIN LATERAL (SELECT ct.date_debut,ct.date_fin,f.id_filiale,f.raison_sociale,f.nif,f.stat, "
                + "f.adresse_siege,f.telephone,f.email FROM contrat_travail ct "
                + "JOIN ref_filiale f ON f.id_filiale=ct.id_filiale WHERE ct.id_employe=b.id_employe "
                + "AND ct.date_debut<=p.date_fin AND (ct.date_fin IS NULL OR ct.date_fin>=p.date_debut) "
                + "ORDER BY LEAST(COALESCE(ct.date_fin,p.date_fin),p.date_fin) DESC,ct.date_debut DESC,ct.id_contrat DESC LIMIT 2) candidate ON TRUE "
                + "WHERE b.id_periode_paie IN (" + placeholders + ") AND snap.id_bulletin IS NULL "
                + "ORDER BY b.id_periode_paie,b.id_bulletin";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int index = 0; index < ids.size(); index++) ps.setInt(index + 1, ids.get(index));
            try (ResultSet rs = ps.executeQuery()) {
                int currentBulletin = -1;
                int employeeId = -1;
                LocalDate start = null;
                LocalDate end = null;
                Company selected = null;
                LocalDate selectedDay = null;
                while (rs.next()) {
                    int bulletinId = rs.getInt("id_bulletin");
                    if (currentBulletin != -1 && currentBulletin != bulletinId) {
                        result.put(currentBulletin, finishEmployerContext(employeeId, start, end, selected));
                        selected = null;
                        selectedDay = null;
                    }
                    if (currentBulletin != bulletinId) {
                        currentBulletin = bulletinId;
                        employeeId = rs.getInt("id_employe");
                        start = rs.getDate("period_start").toLocalDate();
                        end = rs.getDate("period_end").toLocalDate();
                    }
                    if (rs.getObject("id_filiale") != null) {
                        if (selected == null) {
                            selected = company(rs);
                            Date contractEnd = rs.getDate("contract_end");
                            selectedDay = contractEnd == null || contractEnd.toLocalDate().isAfter(end)
                                    ? end : contractEnd.toLocalDate();
                        } else {
                            LocalDate secondStart = rs.getDate("contract_start").toLocalDate();
                            Date secondEnd = rs.getDate("contract_end");
                            if (!secondStart.isAfter(selectedDay)
                                    && (secondEnd == null || !secondEnd.toLocalDate().isBefore(selectedDay)))
                                throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: FILIALE employé="
                                        + employeeId + " au " + selectedDay);
                        }
                    }
                }
                if (currentBulletin != -1) result.put(currentBulletin, finishEmployerContext(employeeId,
                        start, end, selected));
            }
        }
        return result;
    }

    private static Company finishEmployerContext(int employeeId, LocalDate start, LocalDate end, Company selected) {
        if (selected == null) throw new IllegalStateException("MISSING_CONFIGURATION: FILIALE employé="
                + employeeId + " campagne=[" + start + ", " + end + "]");
        return selected;
    }

    private static Company company(ResultSet rs) throws Exception {
        return new Company(rs.getString("raison_sociale"), rs.getString("nif"), rs.getString("adresse_siege"));
    }

    static Company forPeriod(int periodId, Connection connection) throws Exception {
        List<Company> companies = new ArrayList<>();
        for (Company company : companiesByBulletin(periodId, connection).values())
            if (!companies.contains(company)) companies.add(company);
        if (companies.isEmpty()) throw new IllegalStateException(
                "Export impossible : aucun bulletin ne permet de déterminer la filiale de la période " + periodId + ".");
        if (companies.size() != 1) throw new IllegalStateException(
                "Export impossible : sélectionnez une seule filiale employeur pour cette période.");
        Company company = companies.get(0);
        if (company.name() == null || company.name().isBlank() || company.nif() == null || company.nif().isBlank())
            throw new IllegalStateException("Export impossible : renseignez la raison sociale et le NIF de la filiale.");
        return company;
    }

    static Map<Integer, LocalDate> departuresForPeriod(Collection<Integer> employeeIds, LocalDate start,
            LocalDate end, Connection connection) throws Exception {
        if (start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("Bornes de campagne obligatoires pour la date de débauchage.");
        Map<Integer, LocalDate> departures = new LinkedHashMap<>();
        if (employeeIds == null || employeeIds.isEmpty()) return departures;
        List<Integer> ids = employeeIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return departures;
        String employeeValues = String.join(",", java.util.Collections.nCopies(ids.size(), "(?)"));
        String sql = "WITH employees(id_employe) AS (VALUES " + employeeValues + "), "
                + "stc AS (SELECT p.id_employe,p.date_depart,ROW_NUMBER() OVER (PARTITION BY p.id_employe ORDER BY p.date_depart DESC) AS rn, "
                + "COUNT(*) OVER (PARTITION BY p.id_employe) AS matches FROM paie_stc p JOIN employees e USING (id_employe) "
                + "WHERE p.date_depart BETWEEN ? AND ?), "
                + "contracts AS (SELECT ct.id_employe,ct.date_fin,ct.id_contrat, "
                + "ROW_NUMBER() OVER (PARTITION BY ct.id_employe ORDER BY ct.date_debut DESC,ct.id_contrat DESC) AS rn, "
                + "COUNT(*) OVER (PARTITION BY ct.id_employe) AS matches FROM contrat_travail ct JOIN employees e USING (id_employe) "
                + "WHERE ct.date_debut<=? AND (ct.date_fin IS NULL OR ct.date_fin>=?)) "
                + "SELECT e.id_employe,s.date_depart,s.matches AS stc_matches,c1.date_fin AS first_finish, "
                + "c1.matches AS contract_matches,c2.id_contrat AS second_contract_id,c2.date_fin AS second_finish "
                + "FROM employees e LEFT JOIN stc s ON s.id_employe=e.id_employe AND s.rn=1 "
                + "LEFT JOIN contracts c1 ON c1.id_employe=e.id_employe AND c1.rn=1 "
                + "LEFT JOIN contracts c2 ON c2.id_employe=e.id_employe AND c2.rn=2 ORDER BY e.id_employe";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (Integer id : ids) ps.setInt(parameter++, id);
            ps.setDate(parameter++, Date.valueOf(start));
            ps.setDate(parameter++, Date.valueOf(end));
            ps.setDate(parameter++, Date.valueOf(end));
            ps.setDate(parameter, Date.valueOf(start));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int employeeId = rs.getInt("id_employe");
                    Date stcDate = rs.getDate("date_depart");
                    if (stcDate != null) {
                        if (rs.getInt("stc_matches") > 1) throw new IllegalStateException(
                                "AMBIGUOUS_CONFIGURATION: plusieurs STC pour l'employé " + employeeId
                                        + " dans [" + start + ", " + end + "]");
                        departures.put(employeeId, stcDate.toLocalDate());
                        continue;
                    }
                    if (rs.getInt("contract_matches") == 0) {
                        departures.put(employeeId, null);
                        continue;
                    }
                    Date finish = rs.getDate("first_finish");
                    Date secondFinish = rs.getDate("second_finish");
                    if (rs.getObject("second_contract_id") != null && finish != null && secondFinish != null
                            && finish.equals(secondFinish)) throw new IllegalStateException(
                                    "AMBIGUOUS_CONFIGURATION: plusieurs fins de contrat pour l'employé "
                                            + employeeId + " au " + finish);
                    departures.put(employeeId, finish != null && !finish.toLocalDate().isAfter(end)
                            ? finish.toLocalDate() : null);
                }
            }
        }
        return departures;
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
