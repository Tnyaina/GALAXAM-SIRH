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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PointageAnomalieService {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PointageSaisieService pointageSaisieService;

    /**
     * Recherche les anomalies pour un filtre donné (anomalies de saisie, retards non justifiés et jours ouvrés non saisis)
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
            Integer effectivePeriodeId = idPeriode;
            if (effectivePeriodeId == null || effectivePeriodeId <= 0) {
                String sqlFindPer = (datePointage != null && !datePointage.trim().isEmpty()) ?
                    "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin ORDER BY date_debut DESC LIMIT 1" :
                    "SELECT p.id_periode_paie FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE s.code = 'OUVERTE' ORDER BY p.date_debut DESC LIMIT 1";
                try (PreparedStatement psF = c.prepareStatement(sqlFindPer)) {
                    if (datePointage != null && !datePointage.trim().isEmpty()) {
                        psF.setDate(1, Date.valueOf(datePointage));
                    }
                    try (ResultSet rsF = psF.executeQuery()) {
                        if (rsF.next()) {
                            effectivePeriodeId = rsF.getInt(1);
                        }
                    }
                } catch (Exception ignored) {}

                if (effectivePeriodeId == null || effectivePeriodeId <= 0) {
                    try (PreparedStatement psF2 = c.prepareStatement("SELECT id_periode_paie FROM periode_paie ORDER BY date_debut DESC LIMIT 1")) {
                        try (ResultSet rsF2 = psF2.executeQuery()) {
                            if (rsF2.next()) {
                                effectivePeriodeId = rsF2.getInt(1);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

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
            if (effectivePeriodeId != null && effectivePeriodeId > 0) {
                sql.append("AND p.id_periode_paie = ? ");
                params.add(effectivePeriodeId);
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

        try (Connection c = dataSource.getConnection()) {
            LocalTime siteDebM = LocalTime.of(8, 0);
            LocalTime siteFinM = LocalTime.of(12, 0);
            LocalTime siteDebA = LocalTime.of(13, 0);
            LocalTime siteFinA = LocalTime.of(17, 0);

            Integer idSite = null;
            if (idPointage > 0) {
                String sqlSite = "SELECT s.id_site, s.heure_debut_matin, s.heure_fin_matin, s.heure_debut_apres_midi, s.heure_fin_apres_midi, COALESCE(s.horaire_continu, TRUE) AS horaire_continu " +
                                 "FROM pointage_jour p " +
                                 "JOIN ref_site s ON p.id_site = s.id_site " +
                                 "WHERE p.id_pointage = ?";
                try (PreparedStatement psS = c.prepareStatement(sqlSite)) {
                    psS.setInt(1, idPointage);
                    try (ResultSet rsS = psS.executeQuery()) {
                        if (rsS.next()) {
                            idSite = rsS.getInt("id_site");
                            Time hdm = rsS.getTime("heure_debut_matin");
                            Time hfm = rsS.getTime("heure_fin_matin");
                            Time hda = rsS.getTime("heure_debut_apres_midi");
                            Time hfa = rsS.getTime("heure_fin_apres_midi");
                            boolean cont = rsS.getBoolean("horaire_continu");

                            if (hdm != null) siteDebM = hdm.toLocalTime();
                            if (hfm != null) siteFinM = hfm.toLocalTime();
                            if (hda != null) siteDebA = hda.toLocalTime();
                            else if (!cont) siteDebA = LocalTime.of(14, 0);
                            if (hfa != null) siteFinA = hfa.toLocalTime();
                            else if (!cont) siteFinA = LocalTime.of(18, 0);
                        }
                    }
                }
            } else if (payload.get("idSite") != null) {
                idSite = ((Number) payload.get("idSite")).intValue();
            }

            boolean isPauseAuto = (em != null && sa != null && sm == null && ea == null);
            double hMatin;
            double hAm;

            if (isPauseAuto) {
                hMatin = PointageSaisieService.calcHours(em, Time.valueOf(siteFinM));
                hAm = PointageSaisieService.calcHours(Time.valueOf(siteDebA), sa);
            } else {
                hMatin = PointageSaisieService.calcHours(em, sm);
                hAm = PointageSaisieService.calcHours(ea, sa);
            }

            double totalH = hMatin + hAm;
            double hNuit = PointageSaisieService.calcHeuresNuit(sa);

            int retMatin = PointageSaisieService.calcMinutesRetard(em, siteDebM);
            int retAm = 0;
            if (!isPauseAuto && ea != null) {
                if (sm != null && siteDebA.isAfter(siteFinM)) {
                    long pauseTheorique = java.time.Duration.between(siteFinM, siteDebA).toMinutes();
                    LocalTime ltSm = sm.toLocalTime();
                    LocalTime ltEa = ea.toLocalTime();
                    if (ltSm.isAfter(siteFinM) && ltEa.isAfter(ltSm)) {
                        long pauseReelle = java.time.Duration.between(ltSm, ltEa).toMinutes();
                        retAm = (int) Math.max(0, pauseReelle - pauseTheorique);
                    } else {
                        retAm = PointageSaisieService.calcMinutesRetard(ea, siteDebA);
                    }
                } else {
                    retAm = PointageSaisieService.calcMinutesRetard(ea, siteDebA);
                }
            }
            int retMins = retMatin + retAm;

            String codeStatut = (String) payload.get("statutCode");
            if (codeStatut == null || codeStatut.trim().isEmpty() || "NON_SAISI".equals(codeStatut)) {
                codeStatut = totalH > 0 ? "PRESENT" : "ABSENCE_NON_JUSTIFIEE";
            }

            // 1. Identification et chargement des informations clés (employé, site, période, version)
            Integer idEmp = null;
            Integer idPer = null;
            Date datePtg = null;
            Integer currentVersion = null;

            if (idPointage > 0) {
                String sqlFindPtg = "SELECT id_employe, id_site, id_periode_paie, date_pointage, COALESCE(version, 1) AS version FROM pointage_jour WHERE id_pointage = ?";
                try (PreparedStatement psF = c.prepareStatement(sqlFindPtg)) {
                    psF.setInt(1, idPointage);
                    try (ResultSet rsF = psF.executeQuery()) {
                        if (rsF.next()) {
                            idEmp = rsF.getInt("id_employe");
                            if (idSite == null || idSite <= 0) {
                                idSite = rsF.getInt("id_site");
                            }
                            idPer = rsF.getInt("id_periode_paie");
                            datePtg = rsF.getDate("date_pointage");
                            currentVersion = rsF.getInt("version");
                        } else {
                            throw new IllegalArgumentException("Pointage introuvable (ID: " + idPointage + ").");
                        }
                    }
                }
            } else {
                if (payload.get("idEmploye") != null) {
                    idEmp = ((Number) payload.get("idEmploye")).intValue();
                }
                if (payload.get("idPeriodePaie") != null) {
                    idPer = ((Number) payload.get("idPeriodePaie")).intValue();
                }
                if (payload.get("datePointage") != null) {
                    datePtg = Date.valueOf((String) payload.get("datePointage"));
                }
            }

            if (idEmp == null || datePtg == null) {
                throw new IllegalArgumentException("L'employé et la date de pointage sont requis.");
            }
            if (idSite == null || idSite <= 0) {
                idSite = 1;
            }

            // 2. Contrôle P0-1 : Éligibilité stricte au pointage à l'écriture
            pointageSaisieService.verifierEligibilitePointage(c, idEmp, idSite, datePtg);

            // 3. Contrôle P0-3 : Verrouillage de la Période de Paie (avec verrou partagé FOR SHARE)
            if ((idPer == null || idPer <= 0) && datePtg != null) {
                String sqlFindPer2 = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
                try (PreparedStatement psF2 = c.prepareStatement(sqlFindPer2)) {
                    psF2.setDate(1, datePtg);
                    try (ResultSet rsF2 = psF2.executeQuery()) {
                        if (rsF2.next()) idPer = rsF2.getInt(1);
                    }
                }
            }

            if (idPer != null && idPer > 0) {
                String sqlCheckPer = "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ? FOR SHARE";
                try (PreparedStatement psCP = c.prepareStatement(sqlCheckPer)) {
                    psCP.setInt(1, idPer);
                    try (ResultSet rsCP = psCP.executeQuery()) {
                        if (rsCP.next()) {
                            String stCode = rsCP.getString("code");
                            if ("VALIDEE".equalsIgnoreCase(stCode) || "CLOTUREE".equalsIgnoreCase(stCode) || "TRANSMIS_DAF".equalsIgnoreCase(stCode)) {
                                throw new IllegalStateException("Impossible de modifier les pointages : la période de paie est fermée.");
                            }
                        }
                    }
                }
            }

            if (idPointage <= 0) {
                String sqlInsert = "INSERT INTO pointage_jour (" +
                    "id_employe, id_periode_paie, id_site, date_pointage, " +
                    "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                    "heures_matin, heures_am, total_heures, minutes_retard, heures_nuit, " +
                    "motif_ajustement, id_statut_jour, deplacement_matin, deplacement_am, " +
                    "indemnite_deplacement_recue, indemnite_repas_recue, version, created_at, updated_at " +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                    "(SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), " +
                    "?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
                try (PreparedStatement psIns = c.prepareStatement(sqlInsert)) {
                    psIns.setInt(1, idEmp);
                    psIns.setObject(2, idPer);
                    psIns.setInt(3, idSite);
                    psIns.setDate(4, datePtg);
                    psIns.setTime(5, em);
                    psIns.setTime(6, sm);
                    psIns.setTime(7, ea);
                    psIns.setTime(8, sa);
                    psIns.setDouble(9, hMatin);
                    psIns.setDouble(10, hAm);
                    psIns.setDouble(11, totalH);
                    psIns.setInt(12, retMins);
                    psIns.setDouble(13, hNuit);
                    psIns.setString(14, motif);
                    psIns.setString(15, codeStatut);
                    psIns.setBoolean(16, depM);
                    psIns.setBoolean(17, depA);
                    psIns.setBoolean(18, depM || depA);
                    psIns.setBoolean(19, repas);
                    return psIns.executeUpdate() > 0;
                } catch (SQLException eIns) {
                    if (eIns.getMessage() != null && (eIns.getMessage().toLowerCase().contains("duplicate") || eIns.getMessage().toLowerCase().contains("unique"))) {
                        throw new IllegalStateException("Conflit de modification concurrente : le pointage existe déjà.");
                    }
                    throw eIns;
                }
            } else {
                // 4. Contrôle P0-5 : Concurrence & Optimistic Locking obligatoire
                if (payload.get("version") == null) {
                    throw new IllegalStateException("Le champ 'version' est obligatoire pour régulariser le pointage existant ID " + idPointage + ".");
                }
                int providedVer = ((Number) payload.get("version")).intValue();
                if (currentVersion != null && providedVer != currentVersion) {
                    throw new IllegalStateException("Conflit de modification concurrente sur le pointage ID " + idPointage
                            + " (version attendue: " + currentVersion + ", version fournie: " + providedVer + "). Veuillez recharger.");
                }

                String sql = "UPDATE pointage_jour SET " +
                             "heure_entree_matin = ?, heure_sortie_matin = ?, heure_entree_am = ?, heure_sortie_am = ?, " +
                             "heures_matin = ?, heures_am = ?, total_heures = ?, minutes_retard = ?, heures_nuit = ?, " +
                             "motif_ajustement = ?, id_statut_jour = (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), " +
                             "deplacement_matin = ?, deplacement_am = ?, indemnite_deplacement_recue = ?, indemnite_repas_recue = ?, " +
                             "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id_pointage = ? AND version = ?";

                try (PreparedStatement ps = c.prepareStatement(sql)) {
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
                    ps.setInt(17, providedVer);
                    int rows = ps.executeUpdate();
                    if (rows == 0) {
                        throw new IllegalStateException("Conflit de modification concurrente sur le pointage ID " + idPointage + ". La ligne a été modifiée par un tiers.");
                    }
                    return rows > 0;
                }
            }
        }
    }
}
