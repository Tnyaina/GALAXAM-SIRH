package mg.galaxam.sirh.paie.application.export;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Identité légale de l'employeur portée par les bulletins de la période. */
final class PayrollExportCompanyResolver {
    private PayrollExportCompanyResolver() {}

    record Company(String name, String nif, String address) {
        String nifCompact() { return nif.replaceAll("\\s+", ""); }
    }

    static Company forPeriod(int periodId, Connection connection) throws Exception {
        String sql = "SELECT DISTINCT f.id_filiale, f.raison_sociale, f.nif, f.adresse_siege " +
                "FROM bulletin_paie b JOIN periode_paie p ON p.id_periode_paie=b.id_periode_paie " +
                "LEFT JOIN LATERAL (SELECT ct.id_filiale FROM contrat_travail ct " +
                "WHERE ct.id_employe=b.id_employe AND ct.date_debut<=p.date_fin " +
                "ORDER BY ct.date_debut DESC, ct.id_contrat DESC LIMIT 1) ct ON TRUE " +
                "LEFT JOIN ref_filiale f ON f.id_filiale=ct.id_filiale WHERE b.id_periode_paie=?";
        List<Company> companies = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) companies.add(new Company(rs.getString("raison_sociale"), rs.getString("nif"), rs.getString("adresse_siege")));
            }
        }
        if (companies.isEmpty()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT raison_sociale,nif,adresse_siege FROM ref_filiale WHERE est_actif=TRUE ORDER BY id_filiale" );
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) companies.add(new Company(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
        }
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
                "SELECT id_periode_paie FROM periode_paie WHERE EXTRACT(YEAR FROM date_debut)=? " +
                "AND EXTRACT(MONTH FROM date_debut) BETWEEN ? AND ? ORDER BY date_debut")) {
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
        if (companies.isEmpty()) return forPeriod(-1, connection);
        if (companies.size() != 1) throw new IllegalStateException(
                "Export impossible : plusieurs filiales employeurs figurent dans le trimestre.");
        return companies.get(0);
    }
}
