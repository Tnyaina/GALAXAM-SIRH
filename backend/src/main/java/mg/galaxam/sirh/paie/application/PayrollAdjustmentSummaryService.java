package mg.galaxam.sirh.paie.application;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

/** Shared payroll adjustment totals for campaign and variable entry screens. */
@Service
public class PayrollAdjustmentSummaryService {
    public record Summary(int count, int activeCount, int cancelledCount, int pendingCount,
                          BigDecimal gains, BigDecimal retenues) {}

    private final DataSource dataSource;

    public PayrollAdjustmentSummaryService(DataSource dataSource) { this.dataSource = dataSource; }

    public Summary summarize(Integer periodId) throws Exception {
        String cancelled = mg.galaxam.sirh.paie.domain.PayrollStatuses.cancelledAdjustmentSql("paa.statut");
        String sql = "SELECT COUNT(*) AS total_count, "
                + "COUNT(*) FILTER (WHERE NOT (" + cancelled + ")) AS active_count, "
                + "COUNT(*) FILTER (WHERE " + cancelled + ") AS cancelled_count, "
                + "COUNT(*) FILTER (WHERE paa.statut='EN_ATTENTE') AS pending_count, "
                + "COALESCE(SUM(paa.montant) FILTER (WHERE paa.sens='GAIN' AND NOT (" + cancelled + ")),0) AS gains, "
                + "COALESCE(SUM(paa.montant) FILTER (WHERE paa.sens='RETENUE' AND NOT (" + cancelled + ")),0) AS retenues "
                + "FROM paie_avance_acompte paa WHERE (? IS NULL OR paa.id_periode_paie=?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (periodId == null) { ps.setNull(1, java.sql.Types.INTEGER); ps.setNull(2, java.sql.Types.INTEGER); }
            else { ps.setInt(1, periodId); ps.setInt(2, periodId); }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Summary(rs.getInt("total_count"), rs.getInt("active_count"),
                        rs.getInt("cancelled_count"), rs.getInt("pending_count"),
                        rs.getBigDecimal("gains"), rs.getBigDecimal("retenues"));
            }
        }
    }
}
