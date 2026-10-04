package mg.galaxam.sirh.pointage.application;

import java.sql.*;
import java.time.LocalDate;

/** Préflight des versions journalières avant les agrégations de pointage. */
final class PointageTemporalConfigurationGuard {
    private PointageTemporalConfigurationGuard() {}
    static void verify(int employee, int period, Connection c) throws SQLException {
        try (var ps=c.prepareStatement("SELECT date_debut,date_fin FROM periode_paie WHERE id_periode_paie=?")) {
            ps.setInt(1,period);
            try(var rs=ps.executeQuery()) {
                if(!rs.next()) throw new IllegalStateException("MISSING_CONFIGURATION: PERIODE_PAIE id="+period);
                verify(employee,rs.getDate(1).toLocalDate(),rs.getDate(2).toLocalDate(),c);
            }
        }
    }
    static void verify(int employee, LocalDate start, LocalDate end, Connection c) throws SQLException {
        String sql="WITH jours AS (SELECT generate_series(?::date, ?::date, interval '1 day')::date AS jour) "
                + "SELECT j.jour, 'AFFECTATION' AS type FROM jours j WHERE "
                + "(SELECT COUNT(*) FROM employe_affectation_carriere a WHERE a.id_employe=? "
                + "AND a.date_debut<=j.jour AND (a.date_fin IS NULL OR a.date_fin>=j.jour))>1 "
                + "UNION ALL SELECT j.jour, 'SITE_INDEMNITE' FROM jours j "
                + "JOIN employe_affectation_carriere a ON a.id_employe=? "
                + "AND a.date_debut<=j.jour AND (a.date_fin IS NULL OR a.date_fin>=j.jour) "
                + "JOIN site_indemnite_config s ON s.id_site=a.id_site "
                + "AND s.date_debut<=j.jour AND (s.date_fin IS NULL OR s.date_fin>=j.jour) "
                + "GROUP BY j.jour,s.id_site,s.id_indemnite HAVING COUNT(*)>1 ORDER BY jour LIMIT 1";
        try(var ps=c.prepareStatement(sql)) {
            ps.setDate(1,Date.valueOf(start));ps.setDate(2,Date.valueOf(end));ps.setInt(3,employee);ps.setInt(4,employee);
            try(var rs=ps.executeQuery()) {
                if(rs.next()) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: "+rs.getString(2)
                        +" employé="+employee+" au "+rs.getDate(1));
            }
        }
    }
}
