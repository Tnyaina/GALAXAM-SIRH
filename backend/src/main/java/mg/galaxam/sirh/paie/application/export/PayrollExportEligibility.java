package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.domain.PayrollStatuses;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/** Contrats d'accès communs aux exports de paie; le mapping de chaque format reste distinct. */
public final class PayrollExportEligibility {
    private PayrollExportEligibility() {}

    public static boolean isFinalPeriod(String status) { return PayrollStatuses.isFinalPeriod(status); }
    public static boolean isFinalBulletin(String status) { return PayrollStatuses.isFinalBulletin(status); }

    public static void requireFinalPeriod(String status, String periodCode) {
        if (!isFinalPeriod(status))
            throw new IllegalStateException("La paie " + periodCode + " doit être validée avant la consolidation.");
    }

    public static void requireFinalBulletin(String status, String periodCode) {
        if (!isFinalBulletin(status))
            throw new IllegalStateException("Bulletin non validé dans la période " + periodCode + ".");
    }

    public static void requireFinalBulletins(List<BulletinPaie> bulletins, String periodCode) {
        for (BulletinPaie bulletin : bulletins) requireFinalBulletin(bulletin.getStatut(), periodCode);
    }

    public static void requireFinalPeriod(Integer periodId, Connection connection) throws Exception {
        String sql = "SELECT p.code, s.code AS statut FROM periode_paie p "
                + "JOIN ref_statut_periode_paie s ON s.id_statut_periode = p.id_statut_periode "
                + "WHERE p.id_periode_paie = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Période de paie introuvable.");
                requireFinalPeriod(rs.getString("statut"), rs.getString("code"));
            }
        }
    }
}
