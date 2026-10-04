package mg.galaxam.sirh.paie.domain.engine;


import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

/** Résolution commune des paramètres fiscaux datés. */
public final class TaxConfigurationResolver {
    private TaxConfigurationResolver() {}
    public static BigDecimal resolveIsiRate(LocalDate date, Connection c) throws Exception {
        if (date == null) throw new IllegalArgumentException("La date métier ISI est obligatoire.");
        String sql = "SELECT valeur FROM parametre_global WHERE code_parametre IN ('TAUX_ISI','TAUX_ISI_POURCENT') "
                + "AND est_actif = true AND date_debut <= ? AND (date_fin IS NULL OR date_fin >= ?) "
                + "ORDER BY date_debut DESC, id_parametre DESC LIMIT 2";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Date d = Date.valueOf(date); ps.setDate(1, d); ps.setDate(2, d);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("TAX_CONFIGURATION_MISSING: ISI au " + date);
                BigDecimal rate = new BigDecimal(rs.getString(1));
                if (rs.next()) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: TAX_CONFIGURATION_AMBIGUOUS: ISI au " + date);
                return rate;
            }
        }
    }
}
