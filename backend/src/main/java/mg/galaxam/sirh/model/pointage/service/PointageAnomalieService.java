package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.PointageAnomalieDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PointageAnomalieService {

    @Autowired
    private DataSource dataSource;

    /**
     * Recherche les anomalies pour un filtre donné
     */
    public Map<String, Object> getAnomalies(
            Integer idSite,
            Integer idPeriode,
            String datePointage,
            String search
    ) throws SQLException {
        Map<String, Object> response = new HashMap<>();
        List<PointageAnomalieDTO> anomalies = new ArrayList<>();
        int countIncomplets = 0;
        int countRetards = 0;
        int countAbsences = 0;

        try (Connection c = dataSource.getConnection()) {
            StringBuilder sql = new StringBuilder(
                "SELECT p.id_pointage, p.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                "p.id_site, s.libelle AS site_nom, p.date_pointage, " +
                "p.heure_entree_matin, p.heure_sortie_matin, p.heure_entree_am, p.heure_sortie_am, " +
                "p.total_heures, p.minutes_retard, st.code AS statut_code, st.libelle AS statut_libelle, " +
                "p.motif_ajustement, p.deplacement_matin, p.deplacement_am, p.indemnite_repas_recue " +
                "FROM pointage_jour p " +
                "JOIN employe e ON p.id_employe = e.id_employe " +
                "LEFT JOIN ref_site s ON p.id_site = s.id_site " +
                "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                "WHERE (st.code IN ('ANOMALIE_SAISIE', 'ABSENCE_NON_JUSTIFIEE') " +
                "   OR (p.minutes_retard > 0 AND (p.motif_ajustement IS NULL OR TRIM(p.motif_ajustement) = ''))) "
            );

            List<Object> params = new ArrayList<>();
            if (idSite != null && idSite > 0) {
                sql.append("AND p.id_site = ? ");
                params.add(idSite);
            }
            if (idPeriode != null && idPeriode > 0) {
                sql.append("AND p.id_periode_paie = ? ");
                params.add(idPeriode);
            }
            if (datePointage != null && !datePointage.trim().isEmpty()) {
                sql.append("AND p.date_pointage = ? ");
                params.add(Date.valueOf(datePointage));
            }
            if (search != null && !search.trim().isEmpty()) {
                sql.append("AND (LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.matricule) LIKE ?) ");
                String term = "%" + search.trim().toLowerCase() + "%";
                params.add(term);
                params.add(term);
                params.add(term);
            }

            sql.append("ORDER BY p.date_pointage DESC, e.nom, e.prenom");

            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        PointageAnomalieDTO dto = new PointageAnomalieDTO();
                        dto.setIdPointage(rs.getInt("id_pointage"));
                        dto.setIdEmploye(rs.getInt("id_employe"));
                        dto.setMatricule(rs.getString("matricule"));
                        dto.setNom(rs.getString("nom"));
                        dto.setPrenom(rs.getString("prenom"));
                        dto.setPhotoUrl(rs.getString("photo_url"));
                        dto.setIdSite(rs.getInt("id_site"));
                        dto.setSiteNom(rs.getString("site_nom"));
                        Date d = rs.getDate("date_pointage");
                        dto.setDatePointage(d != null ? d.toString() : null);

                        Time em = rs.getTime("heure_entree_matin");
                        Time sm = rs.getTime("heure_sortie_matin");
                        Time ea = rs.getTime("heure_entree_am");
                        Time sa = rs.getTime("heure_sortie_am");
                        dto.setHeureEntreeMatin(em != null ? em.toString().substring(0, 5) : "");
                        dto.setHeureSortieMatin(sm != null ? sm.toString().substring(0, 5) : "");
                        dto.setHeureEntreeAm(ea != null ? ea.toString().substring(0, 5) : "");
                        dto.setHeureSortieAm(sa != null ? sa.toString().substring(0, 5) : "");
                        dto.setTotalHeures(rs.getDouble("total_heures"));
                        dto.setMinutesRetard(rs.getInt("minutes_retard"));
                        dto.setStatutCode(rs.getString("statut_code"));
                        dto.setStatutLibelle(rs.getString("statut_libelle"));
                        dto.setMotifAjustement(rs.getString("motif_ajustement"));
                        dto.setDeplacementMatin(rs.getBoolean("deplacement_matin"));
                        dto.setDeplacementAm(rs.getBoolean("deplacement_am"));
                        dto.setIndemniteRepasRecue(rs.getBoolean("indemnite_repas_recue"));

                        String stCode = dto.getStatutCode();
                        if ("ANOMALIE_SAISIE".equals(stCode)) countIncomplets++;
                        else if ("ABSENCE_NON_JUSTIFIEE".equals(stCode)) countAbsences++;
                        else if (dto.getMinutesRetard() != null && dto.getMinutesRetard() > 0) countRetards++;

                        anomalies.add(dto);
                    }
                }
            }
        }

        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", anomalies.size());
        stats.put("incomplets", countIncomplets);
        stats.put("retards", countRetards);
        stats.put("absences", countAbsences);

        response.put("anomalies", anomalies);
        response.put("stats", stats);
        return response;
    }

    /**
     * Régularise une anomalie avec motif obligatoire et recalcul complet
     */
    public boolean regulariserPointage(int idPointage, Map<String, Object> payload) throws SQLException {
        Time em = PointageSaisieService.parseTime((String) payload.get("heureEntreeMatin"));
        Time sm = PointageSaisieService.parseTime((String) payload.get("heureSortieMatin"));
        Time ea = PointageSaisieService.parseTime((String) payload.get("heureEntreeAm"));
        Time sa = PointageSaisieService.parseTime((String) payload.get("heureSortieAm"));
        String motif = (String) payload.get("motifAjustement");

        boolean depM = Boolean.TRUE.equals(payload.get("deplacementMatin"));
        boolean depA = Boolean.TRUE.equals(payload.get("deplacementAm"));
        boolean repas = Boolean.TRUE.equals(payload.get("indemniteRepasRecue"));

        double hMatin = PointageSaisieService.calcHours(em, sm);
        double hAm = PointageSaisieService.calcHours(ea, sa);
        double totalH = hMatin + hAm;
        double hNuit = PointageSaisieService.calcHeuresNuit(sa);
        int retMins = PointageSaisieService.calcMinutesRetard(em, LocalTime.of(8, 0)) +
                      PointageSaisieService.calcMinutesRetard(ea, LocalTime.of(13, 0));

        String codeStatut = totalH > 0 ? "PRESENT" : "ABSENCE_NON_JUSTIFIEE";

        String sql = "UPDATE pointage_jour SET " +
                     "heure_entree_matin = ?, heure_sortie_matin = ?, heure_entree_am = ?, heure_sortie_am = ?, " +
                     "heures_matin = ?, heures_am = ?, total_heures = ?, minutes_retard = ?, heures_nuit = ?, " +
                     "motif_ajustement = ?, id_statut_jour = (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), " +
                     "deplacement_matin = ?, deplacement_am = ?, indemnite_deplacement_recue = ?, indemnite_repas_recue = ?, " +
                     "updated_at = CURRENT_TIMESTAMP WHERE id_pointage = ?";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTime(1, em);
            ps.setTime(2, sm);
            ps.setTime(3, ea);
            ps.setTime(4, sa);
            ps.setDouble(5, hMatin);
            ps.setDouble(6, hAm);
            ps.setDouble(7, totalH);
            ps.setInt(8, retMins);
            ps.setDouble(9, hNuit);
            ps.setString(10, motif);
            ps.setString(11, codeStatut);
            ps.setBoolean(12, depM);
            ps.setBoolean(13, depA);
            ps.setBoolean(14, depM || depA);
            ps.setBoolean(15, repas);
            ps.setInt(16, idPointage);
            return ps.executeUpdate() > 0;
        }
    }
}
