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
                + "(SELECT COUNT(*) FROM employe_affectation_carriere a "
                + "JOIN contrat_travail ct ON ct.id_contrat=a.id_contrat "
                + "JOIN ref_statut_rh sa ON sa.id_statut_rh=a.id_statut_rh "
                + "JOIN ref_statut_rh sc ON sc.id_statut_rh=ct.id_statut_rh "
                + "WHERE a.id_employe=? AND sa.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND sc.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND a.date_debut<=j.jour AND (a.date_fin IS NULL OR a.date_fin>=j.jour) "
                + "AND ct.date_debut<=j.jour AND (ct.date_fin IS NULL OR ct.date_fin>=j.jour))>1 "
                + "UNION ALL SELECT j.jour, 'SITE_INDEMNITE' FROM jours j "
                + "JOIN employe_affectation_carriere a ON a.id_employe=? "
                + "AND a.date_debut<=j.jour AND (a.date_fin IS NULL OR a.date_fin>=j.jour) "
                + "JOIN contrat_travail ct ON ct.id_contrat=a.id_contrat "
                + "AND ct.date_debut<=j.jour AND (ct.date_fin IS NULL OR ct.date_fin>=j.jour) "
                + "JOIN ref_statut_rh sa ON sa.id_statut_rh=a.id_statut_rh "
                + "AND sa.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "JOIN ref_statut_rh sc ON sc.id_statut_rh=ct.id_statut_rh "
                + "AND sc.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "JOIN site_indemnite_config s ON s.id_site=a.id_site "
                + "AND s.date_debut<=j.jour AND (s.date_fin IS NULL OR s.date_fin>=j.jour) "
                + "AND s.est_actif=TRUE "
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
