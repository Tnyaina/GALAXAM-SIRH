package mg.galaxam.sirh.paie.domain.engine;


import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Résolution unique et datée des tranches IRSA. */
public final class IrsaBracketResolver {
    private IrsaBracketResolver() {}

    public static List<IrsaCalculator.Tranche> resolve(Integer configId, LocalDate date, Connection c) throws Exception {
        String sql;
        boolean byId = configId != null;
        if (byId) {
            sql = "SELECT tranche_min, tranche_max, taux, numero_tranche FROM bareme_irsa "
                    + "WHERE id_config_legale = ? AND date_debut <= ? AND (date_fin IS NULL OR date_fin >= ?) "
                    + "ORDER BY numero_tranche ASC";
        } else {
            sql = "SELECT b.tranche_min, b.tranche_max, b.taux, b.numero_tranche FROM bareme_irsa b "
                    + "JOIN paie_config_legale pc ON pc.id_config_legale=b.id_config_legale "
                    + "WHERE pc.date_debut <= ? AND (pc.date_fin IS NULL OR pc.date_fin >= ?) "
                    + "AND b.date_debut <= ? AND (b.date_fin IS NULL OR b.date_fin >= ?) "
                    + "ORDER BY pc.date_debut DESC, b.numero_tranche ASC";
        }
        List<IrsaCalculator.Tranche> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Date d = Date.valueOf(date);
            if (byId) { ps.setInt(1, configId); ps.setDate(2, d); ps.setDate(3, d); }
            else { ps.setDate(1, d); ps.setDate(2, d); ps.setDate(3, d); ps.setDate(4, d); }
            try (ResultSet rs = ps.executeQuery()) {
                int previousNumber = 0;
                while (rs.next()) {
                    int number = rs.getInt(4);
                    if (number <= previousNumber) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: IRSA tranche " + number + " au " + date);
                    if (number != previousNumber + 1) {
                        throw new IllegalStateException("IRSA_CONFIGURATION_INVALID: tranches absentes ou dupliquées à la date " + date);
                    }
                    result.add(new IrsaCalculator.Tranche(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3)));
                    previousNumber = number;
                }
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("IRSA_CONFIGURATION_MISSING au " + date);
        return result;
    }
}
