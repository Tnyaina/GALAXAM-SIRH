package mg.galaxam.sirh.paie.domain.engine;

import mg.galaxam.sirh.paie.persistence.*;
import mg.galaxam.sirh.paie.domain.engine.*;
import mg.galaxam.sirh.paie.domain.dto.*;
import mg.galaxam.sirh.paie.domain.dto.export.*;
import mg.galaxam.sirh.paie.application.*;
import mg.galaxam.sirh.paie.application.workflow.*;
import mg.galaxam.sirh.paie.application.export.*;
import mg.galaxam.sirh.paie.api.dto.*;
import mg.galaxam.sirh.paie.api.dto.stc.*;

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
            sql = "SELECT tranche_min, tranche_max, taux FROM bareme_irsa WHERE id_config_legale = ? ORDER BY numero_tranche ASC";
        } else {
            sql = "SELECT b.tranche_min, b.tranche_max, b.taux FROM bareme_irsa b "
                    + "JOIN paie_config_legale pc ON pc.id_config_legale=b.id_config_legale "
                    + "WHERE pc.date_debut <= ? AND (pc.date_fin IS NULL OR pc.date_fin >= ?) "
                    + "ORDER BY pc.date_debut DESC, b.numero_tranche ASC";
        }
        List<IrsaCalculator.Tranche> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (byId) ps.setInt(1, configId);
            else { Date d = Date.valueOf(date); ps.setDate(1, d); ps.setDate(2, d); }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new IrsaCalculator.Tranche(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3)));
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("IRSA_CONFIGURATION_MISSING");
        return result;
    }
}
