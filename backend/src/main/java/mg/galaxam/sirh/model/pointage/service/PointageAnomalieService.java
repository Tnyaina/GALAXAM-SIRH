package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.PointageAnomalieDTO;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
public class PointageAnomalieService {

    private final DataSource dataSource;

    public PointageAnomalieService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Object> getAnomalies(
            Integer idSite, Integer idPeriodePaie, String datePointage,
            String dateDebut, String dateFin, String typeAnomalie, String search) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return getAnomalies(idSite, idPeriodePaie, datePointage, dateDebut, dateFin, typeAnomalie, search, c);
        }
    }

    public Map<String, Object> getAnomalies(
            Integer idSite, Integer idPeriodePaie, String datePointage,
            String dateDebut, String dateFin, String typeAnomalie, String search,
            Connection c) throws Exception {

        Map<String, Object> response = new HashMap<>();
        List<PointageAnomalieDTO> list = new ArrayList<>();

        int countIncomplets = 0;
        int countRetards = 0;
        int countAbsences = 0;

        // 1. Récupération des informations de la période
        LocalDate dateDebutPeriode = null;
        LocalDate dateFinPeriode = null;
        String periodeCode = "";
        String periodeLibelle = "";
        String statutPeriodeCode = "EN_COURS";
        int activeIdPeriode = (idPeriodePaie != null && idPeriodePaie > 0) ? idPeriodePaie : 0;

        if (activeIdPeriode > 0) {
            String sqlPer = "SELECT p.date_debut, p.date_fin, p.code, p.libelle, st.code AS statut_code " +
                            "FROM periode_paie p " +
                            "LEFT JOIN ref_statut_periode_paie st ON p.id_statut_periode = st.id_statut_periode " +
                            "WHERE p.id_periode_paie = ?";
            try (PreparedStatement psPer = c.prepareStatement(sqlPer)) {
                psPer.setInt(1, activeIdPeriode);
                try (ResultSet rsPer = psPer.executeQuery()) {
                    if (rsPer.next()) {
                        dateDebutPeriode = rsPer.getDate("date_debut").toLocalDate();
                        dateFinPeriode = rsPer.getDate("date_fin").toLocalDate();
                        periodeCode = rsPer.getString("code");
                        periodeLibelle = rsPer.getString("libelle");
                        statutPeriodeCode = rsPer.getString("statut_code");
                    }
                }
            }
        }

        // 2. Anomalies enregistrées dans pointage_jour
        StringBuilder sql = new StringBuilder(
                "SELECT p.id_pointage, p.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                "p.id_site, s_site.libelle AS site_nom, p.date_pointage, " +
                "p.heure_entree_matin, p.heure_sortie_matin, p.heure_entree_am, p.heure_sortie_am, " +
                "p.total_heures, p.minutes_retard, p.id_statut_jour, st.code AS statut_code, st.libelle AS statut_libelle, " +
                "p.motif_ajustement, p.indemnite_deplacement_recue, p.indemnite_repas_recue, " +
                "p.id_periode_paie, per.code AS periode_code, per.libelle AS periode_libelle, per_st.code AS statut_periode_code " +
                "FROM pointage_jour p " +
                "JOIN employe e ON p.id_employe = e.id_employe " +
                "LEFT JOIN ref_site s_site ON p.id_site = s_site.id_site " +
                "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                "LEFT JOIN periode_paie per ON p.id_periode_paie = per.id_periode_paie " +
                "LEFT JOIN ref_statut_periode_paie per_st ON per.id_statut_periode = per_st.id_statut_periode " +
                "WHERE (st.code IN ('ANOMALIE_SAISIE', 'ABSENCE_NON_JUSTIFIEE') " +
                "   OR (p.minutes_retard > 0 AND (p.motif_ajustement IS NULL OR TRIM(p.motif_ajustement) = ''))) "
        );

        List<Object> params = new ArrayList<>();
        if (idSite != null && idSite > 0) {
            sql.append("AND p.id_site = ? ");
            params.add(idSite);
        }
        if (activeIdPeriode > 0) {
            sql.append("AND p.id_periode_paie = ? ");
            params.add(activeIdPeriode);
        }
        if (datePointage != null && !datePointage.trim().isEmpty()) {
            sql.append("AND p.date_pointage = ? ");
            params.add(java.sql.Date.valueOf(datePointage.trim()));
        } else {
            if (dateDebut != null && !dateDebut.trim().isEmpty()) {
                sql.append("AND p.date_pointage >= ? ");
                params.add(java.sql.Date.valueOf(dateDebut.trim()));
            }
            if (dateFin != null && !dateFin.trim().isEmpty()) {
                sql.append("AND p.date_pointage <= ? ");
                params.add(java.sql.Date.valueOf(dateFin.trim()));
            }
        }
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.matricule) LIKE ?) ");
            String q = "%" + search.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }
        sql.append("ORDER BY p.date_pointage DESC, e.nom ASC");

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
                    dto.setDatePointage(rs.getDate("date_pointage").toString());

                    Time em = rs.getTime("heure_entree_matin");
                    Time sm = rs.getTime("heure_sortie_matin");
                    Time ea = rs.getTime("heure_entree_am");
                    Time sa = rs.getTime("heure_sortie_am");

                    dto.setHeureEntreeMatin(formatTime(em));
                    dto.setHeureSortieMatin(formatTime(sm));
                    dto.setHeureEntreeAm(formatTime(ea));
                    dto.setHeureSortieAm(formatTime(sa));

                    dto.setTotalHeures(rs.getDouble("total_heures"));
                    dto.setMinutesRetard(rs.getInt("minutes_retard"));
                    dto.setIdStatutJour(rs.getInt("id_statut_jour"));
                    dto.setStatutCode(rs.getString("statut_code"));
                    dto.setStatutLibelle(rs.getString("statut_libelle"));
                    dto.setMotifAjustement(rs.getString("motif_ajustement"));
                    dto.setIndemniteDeplacementRecue(rs.getBoolean("indemnite_deplacement_recue"));
                    dto.setIndemniteRepasRecue(rs.getBoolean("indemnite_repas_recue"));

                    dto.setIdPeriodePaie(rs.getInt("id_periode_paie"));
                    dto.setPeriodeCode(rs.getString("periode_code") != null ? rs.getString("periode_code") : periodeCode);
                    dto.setPeriodeLibelle(rs.getString("periode_libelle") != null ? rs.getString("periode_libelle") : periodeLibelle);
                    dto.setStatutPeriodeCode(rs.getString("statut_periode_code") != null ? rs.getString("statut_periode_code") : statutPeriodeCode);

                    // Qualification du type d'anomalie
                    String codeSt = rs.getString("statut_code");
                    int retard = rs.getInt("minutes_retard");
                    boolean isIncomplet = (em != null && sm == null) || (ea != null && sa == null) ||
                                          (em == null && sm != null) || (ea == null && sa != null);

                    if ("ANOMALIE_SAISIE".equals(codeSt) || isIncomplet) {
                        dto.setTypeAnomalie("POINTAGE_INCOMPLET");
                        dto.setDescriptionAnomalie("Badgeage incomplet ou manquant (matin ou après-midi sans heure de sortie/entrée)");
                        countIncomplets++;
                    } else if ("ABSENCE_NON_JUSTIFIEE".equals(codeSt)) {
                        dto.setTypeAnomalie("ABSENCE_NON_JUSTIFIEE");
                        dto.setDescriptionAnomalie("Absence totale non justifiée constatée sur la journée");
                        countAbsences++;
                    } else if (retard > 0) {
                        dto.setTypeAnomalie("RETARD_NON_JUSTIFIE");
                        dto.setDescriptionAnomalie("Retard constaté de " + retard + " min sans motif de justification");
                        countRetards++;
                    } else {
                        dto.setTypeAnomalie("AUTRE");
                        dto.setDescriptionAnomalie("Anomalie nécessitant un contrôle RH");
                    }

                    if (typeAnomalie == null || typeAnomalie.trim().isEmpty() || typeAnomalie.equalsIgnoreCase(dto.getTypeAnomalie())) {
                        list.add(dto);
                    }
                }
            }
        }

        response.put("anomalies", list);
        response.put("total", list.size());
        response.put("countIncomplets", countIncomplets);
        response.put("countRetards", countRetards);
        response.put("countAbsences", countAbsences);

        return response;
    }

    public Map<String, Object> resoudreAnomalie(int idPointage, String motifAjustement) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return resoudreAnomalie(idPointage, motifAjustement, c);
        }
    }

    public Map<String, Object> resoudreAnomalie(int idPointage, String motifAjustement, Connection c) throws Exception {
        Map<String, Object> resp = new HashMap<>();

        if (motifAjustement == null || motifAjustement.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("message", "Le motif d'ajustement ou de justification est obligatoire pour résoudre une anomalie.");
            return resp;
        }

        // Vérifier le verrouillage de la période
        String sqlVerif = "SELECT per_st.code AS statut_code FROM pointage_jour p " +
                          "JOIN periode_paie per ON p.id_periode_paie = per.id_periode_paie " +
                          "JOIN ref_statut_periode_paie per_st ON per.id_statut_periode = per_st.id_statut_periode " +
                          "WHERE p.id_pointage = ?";
        try (PreparedStatement psVerif = c.prepareStatement(sqlVerif)) {
            psVerif.setInt(1, idPointage);
            try (ResultSet rsVerif = psVerif.executeQuery()) {
                if (rsVerif.next()) {
                    String statutPer = rsVerif.getString("statut_code");
                    if ("VALIDEE".equals(statutPer) || "CLOTUREE".equals(statutPer)) {
                        resp.put("success", false);
                        resp.put("message", "La période de paie est validée ou clôturée. Aucune modification n'est permise.");
                        return resp;
                    }
                }
            }
        }

        String sqlUpdate = "UPDATE pointage_jour SET " +
                           "motif_ajustement = ?, " +
                           "id_statut_jour = CASE " +
                           "    WHEN (SELECT code FROM ref_statut_pointage_jour WHERE id_statut_jour = pointage_jour.id_statut_jour) = 'ANOMALIE_SAISIE' " +
                           "        THEN (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT') " +
                           "    ELSE id_statut_jour " +
                           "END, " +
                           "updated_at = CURRENT_TIMESTAMP " +
                           "WHERE id_pointage = ?";

        try (PreparedStatement ps = c.prepareStatement(sqlUpdate)) {
            ps.setString(1, motifAjustement.trim());
            ps.setInt(2, idPointage);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                resp.put("success", true);
                resp.put("message", "Anomalie résolue avec succès.");
            } else {
                resp.put("success", false);
                resp.put("message", "Pointage introuvable.");
            }
        }

        return resp;
    }

    public Map<String, Object> regulariserJournee(Map<String, Object> payload) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return regulariserJournee(payload, c);
        }
    }

    public Map<String, Object> regulariserJournee(Map<String, Object> payload, Connection c) throws Exception {
        Map<String, Object> resp = new HashMap<>();

        Integer idEmploye = (Integer) payload.get("idEmploye");
        String datePointageStr = (String) payload.get("datePointage");
        Integer idPeriode = (Integer) payload.get("idPeriodePaie");

        if (idEmploye == null || datePointageStr == null) {
            resp.put("success", false);
            resp.put("message", "Identifiant employé ou date de pointage manquant.");
            return resp;
        }

        if (idPeriode == null || idPeriode == 0) {
            String sqlFindPer = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
            try (PreparedStatement psFind = c.prepareStatement(sqlFindPer)) {
                psFind.setDate(1, Date.valueOf(datePointageStr));
                try (ResultSet rsFind = psFind.executeQuery()) {
                    if (rsFind.next()) {
                        idPeriode = rsFind.getInt("id_periode_paie");
                    }
                }
            }
        }

        String emStr = (String) payload.get("heureEntreeMatin");
        String smStr = (String) payload.get("heureSortieMatin");
        String eaStr = (String) payload.get("heureEntreeAm");
        String saStr = (String) payload.get("heureSortieAm");
        String motif = (String) payload.get("motifAjustement");
        String actionType = (String) payload.getOrDefault("actionType", "PRESENCE");

        Time tEm = parseTime(emStr);
        Time tSm = parseTime(smStr);
        Time tEa = parseTime(eaStr);
        Time tSa = parseTime(saStr);

        double hMatin = calcHours(tEm, tSm);
        double hAm = calcHours(tEa, tSa);
        double totalH = hMatin + hAm;

        int retMins = 0;
        if (tEm != null && tEm.toLocalTime().isAfter(LocalTime.of(8, 0))) {
            retMins += (int) Duration.between(LocalTime.of(8, 0), tEm.toLocalTime()).toMinutes();
        }
        if (tEa != null && tEa.toLocalTime().isAfter(LocalTime.of(13, 0))) {
            retMins += (int) Duration.between(LocalTime.of(13, 0), tEa.toLocalTime()).toMinutes();
        }

        String targetStatutCode = "ABSENCE_JUSTIFIEE".equalsIgnoreCase(actionType) ? "ABSENCE_JUSTIFIEE" : "PRESENT";

        String sqlUpsert = "INSERT INTO pointage_jour (" +
                           "id_employe, date_pointage, id_periode_paie, id_site, " +
                           "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                           "heures_matin, heures_am, total_heures, minutes_retard, motif_ajustement, id_statut_jour, updated_at" +
                           ") VALUES (?, ?, ?, (SELECT id_site FROM employe_affectation_carriere WHERE id_employe = ? AND date_fin IS NULL LIMIT 1), ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                           "(SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), CURRENT_TIMESTAMP) " +
                           "ON CONFLICT (id_employe, date_pointage) DO UPDATE SET " +
                           "heure_entree_matin = EXCLUDED.heure_entree_matin, " +
                           "heure_sortie_matin = EXCLUDED.heure_sortie_matin, " +
                           "heure_entree_am = EXCLUDED.heure_entree_am, " +
                           "heure_sortie_am = EXCLUDED.heure_sortie_am, " +
                           "heures_matin = EXCLUDED.heures_matin, " +
                           "heures_am = EXCLUDED.heures_am, " +
                           "total_heures = EXCLUDED.total_heures, " +
                           "minutes_retard = EXCLUDED.minutes_retard, " +
                           "motif_ajustement = EXCLUDED.motif_ajustement, " +
                           "id_statut_jour = EXCLUDED.id_statut_jour, " +
                           "updated_at = CURRENT_TIMESTAMP";

        try (PreparedStatement psUpsert = c.prepareStatement(sqlUpsert)) {
            psUpsert.setInt(1, idEmploye);
            psUpsert.setDate(2, Date.valueOf(datePointageStr));
            psUpsert.setObject(3, idPeriode);
            psUpsert.setInt(4, idEmploye);
            psUpsert.setTime(5, tEm);
            psUpsert.setTime(6, tSm);
            psUpsert.setTime(7, tEa);
            psUpsert.setTime(8, tSa);
            psUpsert.setDouble(9, hMatin);
            psUpsert.setDouble(10, hAm);
            psUpsert.setDouble(11, totalH);
            psUpsert.setInt(12, retMins);
            psUpsert.setString(13, motif);
            psUpsert.setString(14, targetStatutCode);

            psUpsert.executeUpdate();
        }

        resp.put("success", true);
        resp.put("message", "Anomalie régularisée avec succès.");
        return resp;
    }

    private Time parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty() || "—".equals(timeStr)) return null;
        try {
            String clean = timeStr.trim();
            if (clean.length() == 5) clean += ":00";
            return Time.valueOf(clean);
        } catch (Exception e) {
            return null;
        }
    }

    private double calcHours(Time start, Time end) {
        if (start == null || end == null) return 0.0;
        long mins = Duration.between(start.toLocalTime(), end.toLocalTime()).toMinutes();
        return Math.max(0.0, mins / 60.0);
    }

    private String formatTime(Object timeObj) {
        if (timeObj == null) return "";
        String s = timeObj.toString();
        if (s.length() >= 5) return s.substring(0, 5);
        return s;
    }
}
