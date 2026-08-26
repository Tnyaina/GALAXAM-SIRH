package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.PointageGanttDTO;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
public class PointageSaisieService {

    private final DataSource dataSource;

    public PointageSaisieService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Enregistre un lot de pointages journaliers avec calcul automatique des heures,
     * retards (0 tolérance), heures de nuit dynamiques et indemnités.
     */
    public Map<String, Object> enregistrerPointagesBatch(List<SaisiePointageJourDTO> pointages) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return enregistrerPointagesBatch(pointages, c);
        }
    }

    public Map<String, Object> enregistrerPointagesBatch(List<SaisiePointageJourDTO> pointages, Connection c) throws Exception {
        Map<String, Object> resp = new HashMap<>();

        if (pointages == null || pointages.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "Aucune donnée de pointage fournie.");
            return resp;
        }

        String sampleDate = pointages.get(0).getDatePointage();
        Map<String, Object> periode = getPeriodePaie(c, sampleDate);
        if (periode == null) {
            resp.put("success", false);
            resp.put("message", "Aucune période de paie ouverte ne couvre la date " + sampleDate);
            return resp;
        }

        String statutPeriode = (String) periode.get("statut_code");
        if ("VALIDEE".equals(statutPeriode) || "CLOTUREE".equals(statutPeriode)) {
            resp.put("success", false);
            resp.put("message", "La période de paie est validée et verrouillée. Aucune modification n'est permise.");
            return resp;
        }
        int idPeriodePaie = (Integer) periode.get("id_periode_paie");

        boolean previousAutoCommit = c.getAutoCommit();
        c.setAutoCommit(false);

        String sqlUpsert = "INSERT INTO pointage_jour (" +
                "id_employe, id_periode_paie, id_site, date_pointage, " +
                "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                "id_statut_jour, heures_matin, heures_am, total_heures, minutes_retard, " +
                "indemnite_deplacement_recue, deplacement_matin, deplacement_am, " +
                "indemnite_repas_recue, montant_repas, motif_ajustement, heures_nuit) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (id_employe, date_pointage) DO UPDATE SET " +
                "id_periode_paie = EXCLUDED.id_periode_paie, " +
                "heure_entree_matin = EXCLUDED.heure_entree_matin, " +
                "heure_sortie_matin = EXCLUDED.heure_sortie_matin, " +
                "heure_entree_am = EXCLUDED.heure_entree_am, " +
                "heure_sortie_am = EXCLUDED.heure_sortie_am, " +
                "id_statut_jour = EXCLUDED.id_statut_jour, " +
                "heures_matin = EXCLUDED.heures_matin, " +
                "heures_am = EXCLUDED.heures_am, " +
                "total_heures = EXCLUDED.total_heures, " +
                "minutes_retard = EXCLUDED.minutes_retard, " +
                "heures_nuit = EXCLUDED.heures_nuit, " +
                "indemnite_deplacement_recue = EXCLUDED.indemnite_deplacement_recue, " +
                "deplacement_matin = EXCLUDED.deplacement_matin, " +
                "deplacement_am = EXCLUDED.deplacement_am, " +
                "indemnite_repas_recue = EXCLUDED.indemnite_repas_recue, " +
                "montant_repas = EXCLUDED.montant_repas, " +
                "motif_ajustement = EXCLUDED.motif_ajustement, " +
                "updated_at = CURRENT_TIMESTAMP";

        // Charger les paramètres globaux (Nuit, Repas, etc.)
        Map<String, String> paramsGlobaux = ParametreGlobal.loadActiveMap(c);
        LocalTime seuilNuitDebut = ParametreGlobal.getLocalTime(paramsGlobaux, "SEUIL_NUIT_DEBUT", LocalTime.of(22, 0));
        LocalTime seuilNuitFin = ParametreGlobal.getLocalTime(paramsGlobaux, "SEUIL_NUIT_FIN", LocalTime.of(5, 0));
        LocalTime seuil2ndRepas1 = ParametreGlobal.getLocalTime(paramsGlobaux, "SEUIL_2ND_REPAS_1", LocalTime.of(19, 30));
        double montant2ndRepas1 = ParametreGlobal.getDouble(paramsGlobaux, "MONTANT_2ND_REPAS_1", 2000.0);
        LocalTime seuil2ndRepas2 = ParametreGlobal.getLocalTime(paramsGlobaux, "SEUIL_2ND_REPAS_2", LocalTime.of(21, 0));
        double montant2ndRepas2 = ParametreGlobal.getDouble(paramsGlobaux, "MONTANT_2ND_REPAS_2", 4000.0);

        // Charger les configurations des sites pour reconstitution de la pause (RG-PTG-01)
        Map<Integer, Boolean> siteContinuMap = new HashMap<>();
        String sqlSites = "SELECT id_site, horaire_continu FROM ref_site";
        try (PreparedStatement psSites = c.prepareStatement(sqlSites);
             ResultSet rsSites = psSites.executeQuery()) {
            while (rsSites.next()) {
                siteContinuMap.put(rsSites.getInt("id_site"), rsSites.getBoolean("horaire_continu"));
            }
        }

        // Charger les barèmes repas par site
        Map<Integer, Double> siteRepasBaseMap = new HashMap<>();
        String sqlRepas = "SELECT id_site, montant_reference FROM site_indemnite_config WHERE id_indemnite = 2 AND est_actif = TRUE";
        try (PreparedStatement psRepas = c.prepareStatement(sqlRepas);
             ResultSet rsRepas = psRepas.executeQuery()) {
            while (rsRepas.next()) {
                siteRepasBaseMap.put(rsRepas.getInt("id_site"), rsRepas.getDouble("montant_reference"));
            }
        }

        // Charger les statuts de pointage officiels depuis ref_statut_pointage_jour
        Map<String, Integer> statutMap = new HashMap<>();
        String sqlStatuts = "SELECT id_statut_jour, code FROM ref_statut_pointage_jour";
        try (PreparedStatement psSt = c.prepareStatement(sqlStatuts);
             ResultSet rsSt = psSt.executeQuery()) {
            while (rsSt.next()) {
                statutMap.put(rsSt.getString("code"), rsSt.getInt("id_statut_jour"));
            }
        }

        int count = 0;
        try (PreparedStatement ps = c.prepareStatement(sqlUpsert)) {
            for (SaisiePointageJourDTO dto : pointages) {
                int empSiteId = dto.getIdSite() != null ? dto.getIdSite() : 1;
                boolean isContinu = siteContinuMap.getOrDefault(empSiteId, false);

                ps.setInt(1, dto.getIdEmploye());
                ps.setInt(2, idPeriodePaie);
                ps.setInt(3, empSiteId);
                ps.setDate(4, java.sql.Date.valueOf(dto.getDatePointage()));

                Time tEntreeMatin = parseTime(dto.getHeureEntreeMatin());
                Time tSortieMatin = parseTime(dto.getHeureSortieMatin());
                Time tEntreeAm = parseTime(dto.getHeureEntreeAm());
                Time tSortieAm = parseTime(dto.getHeureSortieAm());

                // Reconstitution automatique de la pause par défaut si Entrée Matin + Sortie Soir saisis seuls (RG-PTG-01)
                if (tEntreeMatin != null && tSortieAm != null && tSortieMatin == null && tEntreeAm == null) {
                    tSortieMatin = Time.valueOf("12:00:00");
                    tEntreeAm = Time.valueOf(isContinu ? "13:00:00" : "14:00:00");
                }

                ps.setTime(5, tEntreeMatin);
                ps.setTime(6, tSortieMatin);
                ps.setTime(7, tEntreeAm);
                ps.setTime(8, tSortieAm);

                double hMatin = calcHours(tEntreeMatin, tSortieMatin);
                double hAm = calcHours(tEntreeAm, tSortieAm);

                if ("MATIN".equalsIgnoreCase(dto.getTypeCongeJour())) hMatin = 4.0;
                if ("APRES_MIDI".equalsIgnoreCase(dto.getTypeCongeJour())) hAm = 4.0;
                if ("FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())) {
                    hMatin = 4.0;
                    hAm = 4.0;
                }

                double totalH = dto.isEstAutonome() ? 8.0 : (hMatin + hAm);

                // Calcul des heures de nuit (après SEUIL_NUIT_DEBUT ou avant SEUIL_NUIT_FIN)
                double hNuit = 0.0;
                if (tSortieAm != null) {
                    LocalTime sAm = tSortieAm.toLocalTime();
                    if (sAm.isAfter(seuilNuitDebut)) {
                        hNuit += (double) Duration.between(seuilNuitDebut, sAm).toMinutes() / 60.0;
                    }
                }
                if (tEntreeMatin != null) {
                    LocalTime eMatin = tEntreeMatin.toLocalTime();
                    if (eMatin.isBefore(seuilNuitFin)) {
                        hNuit += (double) Duration.between(eMatin, seuilNuitFin).toMinutes() / 60.0;
                    }
                }

                int retMins = 0;
                if (!dto.isEstAutonome()) {
                    if (tEntreeMatin != null) {
                        LocalTime startM = tEntreeMatin.toLocalTime();
                        LocalTime normalM = LocalTime.of(8, 0);
                        if (startM.isAfter(normalM)) {
                            retMins += (int) Duration.between(normalM, startM).toMinutes();
                        }
                    }
                    if (tEntreeAm != null) {
                        LocalTime startA = tEntreeAm.toLocalTime();
                        LocalTime normalA = isContinu ? LocalTime.of(13, 0) : LocalTime.of(14, 0);
                        if (startA.isAfter(normalA)) {
                            retMins += (int) Duration.between(normalA, startA).toMinutes();
                        }
                    }
                }

                String targetCode = "PRESENT";
                if ("FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())) {
                    targetCode = "CONGE";
                } else if (dto.isEstAutonome()) {
                    targetCode = "DISPENSE_POINTAGE";
                } else if (totalH == 0.0 && tEntreeMatin == null && tSortieMatin == null && tEntreeAm == null && tSortieAm == null) {
                    targetCode = "ABSENCE_NON_JUSTIFIEE";
                } else if ((tEntreeMatin != null && tSortieMatin == null) ||
                           (tEntreeAm != null && tSortieAm == null) ||
                           (tEntreeMatin == null && tSortieMatin != null) ||
                           (tEntreeAm == null && tSortieAm != null) ||
                           (tEntreeMatin != null && tSortieMatin != null && tEntreeAm == null && tSortieAm == null && !"APRES_MIDI".equalsIgnoreCase(dto.getTypeCongeJour())) ||
                           (tEntreeMatin == null && tSortieMatin == null && tEntreeAm != null && tSortieAm != null && !"MATIN".equalsIgnoreCase(dto.getTypeCongeJour()))) {
                    targetCode = "ANOMALIE_SAISIE";
                } else {
                    targetCode = "PRESENT";
                }

                int idStatut = statutMap.getOrDefault(targetCode, 1);

                ps.setInt(9, idStatut);
                ps.setDouble(10, hMatin);
                ps.setDouble(11, hAm);
                ps.setDouble(12, totalH);
                ps.setInt(13, retMins);

                boolean depRecue = dto.isDeplacementMatin() || dto.isDeplacementAm();
                ps.setBoolean(14, depRecue);
                ps.setBoolean(15, dto.isDeplacementMatin());
                ps.setBoolean(16, dto.isDeplacementAm());

                double baseRepas = siteRepasBaseMap.getOrDefault(empSiteId, ParametreGlobal.getDouble(paramsGlobaux, "MONTANT_REPAS_MIDI", 2000.0));
                double mRepas = 0.0;
                if (dto.isIndemniteRepasRecue()) {
                    mRepas = baseRepas;
                    if (tSortieAm != null) {
                        LocalTime sAm = tSortieAm.toLocalTime();
                        if (sAm.isAfter(seuil2ndRepas2)) {
                            mRepas += montant2ndRepas2;
                        } else if (sAm.isAfter(seuil2ndRepas1)) {
                            mRepas += montant2ndRepas1;
                        }
                    }
                }

                ps.setBoolean(17, dto.isIndemniteRepasRecue());
                ps.setDouble(18, mRepas);
                ps.setString(19, dto.getMotifAjustement());
                ps.setDouble(20, hNuit);

                ps.addBatch();
                count++;
            }
            ps.executeBatch();
            c.commit();
        } finally {
            c.setAutoCommit(previousAutoCommit);
        }

        resp.put("success", true);
        resp.put("count", count);
        resp.put("message", count + " pointage(s) enregistrés avec succès.");
        return resp;
    }

    /**
     * Charge les données de la grille quotidienne de pointage pour une date et un site donnés.
     */
    public List<SaisiePointageJourDTO> getPointagesGrille(String date, Integer idSite, Connection c) throws Exception {
        List<SaisiePointageJourDTO> list = new ArrayList<>();

        LocalDate targetDate;
        try {
            targetDate = (date != null && !date.trim().isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        } catch (Exception e) {
            targetDate = LocalDate.now();
        }

        Date d = Date.valueOf(targetDate);

        // 1. Liste des employés actifs
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, ")
           .append("COALESCE(eac.id_site, 1) AS id_site, rs.libelle AS site_nom, rs.horaire_continu, ")
           .append("rcp.code AS code_categorie, rcp.libelle AS libelle_categorie, ")
           .append("COALESCE(rf.dispense_pointage, FALSE) AS dispense_pointage, ")
           .append("(rcp.code = 'CADRE' OR COALESCE(rf.dispense_pointage, FALSE)) AS est_autonome ")
           .append("FROM employe e ")
           .append("LEFT JOIN ( ")
           .append("    SELECT DISTINCT ON (id_employe) id_employe, id_site, id_categorie_professionnelle, id_fonction ")
           .append("    FROM employe_affectation_carriere ")
           .append("    ORDER BY id_employe, COALESCE(date_fin, '9999-12-31') DESC, date_debut DESC, id_affectation DESC ")
           .append(") eac ON e.id_employe = eac.id_employe ")
           .append("LEFT JOIN ref_site rs ON COALESCE(eac.id_site, 1) = rs.id_site ")
           .append("LEFT JOIN ref_categorie_professionnelle rcp ON eac.id_categorie_professionnelle = rcp.id_categorie_professionnelle ")
           .append("LEFT JOIN ref_fonction rf ON eac.id_fonction = rf.id_fonction ")
           .append("WHERE e.est_actif = TRUE ");

        if (idSite != null && idSite > 0) {
            sql.append("AND COALESCE(eac.id_site, 1) = ? ");
        }
        sql.append("ORDER BY e.matricule ASC");

        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            if (idSite != null && idSite > 0) {
                ps.setInt(1, idSite);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                    dto.setIdEmploye(rs.getInt("id_employe"));
                    dto.setMatricule(rs.getString("matricule"));
                    dto.setNom(rs.getString("nom"));
                    dto.setPrenom(rs.getString("prenom"));
                    dto.setPhotoUrl(rs.getString("photo_url"));
                    dto.setIdSite(rs.getInt("id_site"));
                    dto.setSiteNom(rs.getString("site_nom"));
                    dto.setCodeCategorie(rs.getString("code_categorie"));
                    dto.setLibelleCategorie(rs.getString("libelle_categorie"));
                    dto.setEstAutonome(rs.getBoolean("est_autonome"));
                    dto.setDatePointage(targetDate.toString());
                    dto.setHoraireContinu(rs.getBoolean("horaire_continu"));
                    list.add(dto);
                }
            }
        }

        // 2. Enrichir avec les pointages enregistrés, congés et dérogations
        enrichirPointages(list, d, c);

        return list;
    }

    private void enrichirPointages(List<SaisiePointageJourDTO> list, Date targetDate, Connection c) {
        if (list.isEmpty()) return;

        // Dérogations actives
        Map<Integer, Boolean> derRepas = new HashMap<>();
        Map<Integer, Boolean> derDepl = new HashMap<>();
        try (Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT id_employe, beneficie FROM employe_indemnite_derogation WHERE id_indemnite = 1 AND (date_fin IS NULL OR date_fin >= '" + targetDate + "')")) {
                while (rs.next()) derDepl.put(rs.getInt("id_employe"), rs.getBoolean("beneficie"));
            }
            try (ResultSet rs = st.executeQuery("SELECT id_employe, beneficie FROM employe_indemnite_derogation WHERE id_indemnite = 2 AND (date_fin IS NULL OR date_fin >= '" + targetDate + "')")) {
                while (rs.next()) derRepas.put(rs.getInt("id_employe"), rs.getBoolean("beneficie"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Barèmes actifs
        Map<Integer, Double> siteDeplBase = new HashMap<>();
        Map<Integer, Double> siteRepasBase = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id_site, id_indemnite, montant_reference FROM site_indemnite_config WHERE est_actif = TRUE")) {
            while (rs.next()) {
                int sId = rs.getInt("id_site");
                int indId = rs.getInt("id_indemnite");
                if (indId == 1) siteDeplBase.put(sId, rs.getDouble("montant_reference"));
                if (indId == 2) siteRepasBase.put(sId, rs.getDouble("montant_reference"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Pointages existants pour ce jour
        String sqlPtg = "SELECT p.*, s.code AS code_statut, s.libelle AS libelle_statut " +
                "FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour " +
                "WHERE p.date_pointage = ?";
        Map<Integer, Map<String, Object>> ptgMap = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sqlPtg)) {
            ps.setDate(1, targetDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id_pointage", rs.getInt("id_pointage"));
                    map.put("heure_entree_matin", rs.getTime("heure_entree_matin"));
                    map.put("heure_sortie_matin", rs.getTime("heure_sortie_matin"));
                    map.put("heure_entree_am", rs.getTime("heure_entree_am"));
                    map.put("heure_sortie_am", rs.getTime("heure_sortie_am"));
                    map.put("heures_matin", rs.getDouble("heures_matin"));
                    map.put("heures_am", rs.getDouble("heures_am"));
                    map.put("total_heures", rs.getDouble("total_heures"));
                    map.put("minutes_retard", rs.getInt("minutes_retard"));
                    map.put("heures_nuit", rs.getDouble("heures_nuit"));
                    map.put("indemnite_deplacement_recue", rs.getBoolean("indemnite_deplacement_recue"));
                    map.put("deplacement_matin", rs.getBoolean("deplacement_matin"));
                    map.put("deplacement_am", rs.getBoolean("deplacement_am"));
                    map.put("indemnite_repas_recue", rs.getBoolean("indemnite_repas_recue"));
                    map.put("montant_repas", rs.getDouble("montant_repas"));
                    map.put("code_statut", rs.getString("code_statut"));
                    map.put("libelle_statut", rs.getString("libelle_statut"));
                    map.put("motif_ajustement", rs.getString("motif_ajustement"));
                    ptgMap.put(rs.getInt("id_employe"), map);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Congés approuvés
        String sqlConges = "SELECT id_employe, demi_journee_type " +
                "FROM demande_conge " +
                "WHERE ? >= date_debut AND ? <= date_fin AND statut = 'APPROUVEE'";
        Map<Integer, String> congesMap = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sqlConges)) {
            ps.setDate(1, targetDate);
            ps.setDate(2, targetDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    congesMap.put(rs.getInt("id_employe"), rs.getString("demi_journee_type"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (SaisiePointageJourDTO dto : list) {
            int empId = dto.getIdEmploye();
            int siteId = dto.getIdSite();

            boolean estDerogeDepl = !derDepl.getOrDefault(empId, true);
            boolean estDerogeRepas = !derRepas.getOrDefault(empId, true);
            boolean siteDeplActif = siteDeplBase.containsKey(siteId);
            boolean siteRepasActif = siteRepasBase.containsKey(siteId);

            if (congesMap.containsKey(empId)) {
                String cType = congesMap.get(empId);
                dto.setTypeCongeJour(cType != null ? cType : "FULL_DAY");
            }

            if (ptgMap.containsKey(empId)) {
                Map<String, Object> p = ptgMap.get(empId);
                dto.setIdPointage((Integer) p.get("id_pointage"));
                dto.setHeureEntreeMatin(formatTime(p.get("heure_entree_matin")));
                dto.setHeureSortieMatin(formatTime(p.get("heure_sortie_matin")));
                dto.setHeureEntreeAm(formatTime(p.get("heure_entree_am")));
                dto.setHeureSortieAm(formatTime(p.get("heure_sortie_am")));
                dto.setHeuresMatin((Double) p.get("heures_matin"));
                dto.setHeuresAm((Double) p.get("heures_am"));
                dto.setTotalHeures((Double) p.get("total_heures"));
                dto.setMinutesRetard((Integer) p.get("minutes_retard"));
                dto.setHeuresNuit((Double) p.get("heures_nuit"));
                dto.setIndemniteDeplacementRecue((Boolean) p.get("indemnite_deplacement_recue"));
                dto.setDeplacementMatin((Boolean) p.get("deplacement_matin"));
                dto.setDeplacementAm((Boolean) p.get("deplacement_am"));
                dto.setIndemniteRepasRecue((Boolean) p.get("indemnite_repas_recue"));
                dto.setMontantRepas((Double) p.get("montant_repas"));
                dto.setCodeStatut((String) p.get("code_statut"));
                dto.setLibelleStatut((String) p.get("libelle_statut"));
                dto.setMotifAjustement((String) p.get("motif_ajustement"));
            } else {
                dto.setDeplacementMatin(true);
                dto.setDeplacementAm(true);
                if (dto.isEstAutonome()) {
                    dto.setTotalHeures(8.0);
                    dto.setMinutesRetard(0);
                    dto.setCodeStatut("DISPENSE_POINTAGE");
                } else if ("FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())) {
                    dto.setTotalHeures(8.0);
                    dto.setMinutesRetard(0);
                    dto.setCodeStatut("CONGE");
                } else {
                    dto.setTotalHeures(0.0);
                    dto.setMinutesRetard(0);
                    dto.setCodeStatut("NON_SAISI");
                }
                dto.setIndemniteDeplacementRecue(siteDeplActif && !estDerogeDepl);
                dto.setIndemniteRepasRecue(siteRepasActif && !estDerogeRepas);
            }
        }
    }

    public Time parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty() || "—".equals(timeStr)) return null;
        try {
            String clean = timeStr.trim();
            if (clean.length() == 5) clean += ":00";
            return Time.valueOf(clean);
        } catch (Exception e) {
            return null;
        }
    }

    public double calcHours(Time start, Time end) {
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

    public Map<String, Object> getPeriodePaie(Connection c, String date) throws SQLException {
        String sql = "SELECT p.id_periode_paie, p.code AS periode_code, p.libelle AS periode_libelle, " +
                     "p.date_debut, p.date_fin, s.code AS statut_code, s.libelle AS statut_libelle " +
                     "FROM periode_paie p " +
                     "JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                     "WHERE ? >= p.date_debut AND ? <= p.date_fin " +
                     "LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Date d = Date.valueOf(date);
            ps.setDate(1, d);
            ps.setDate(2, d);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> res = new HashMap<>();
                    res.put("id_periode_paie", rs.getInt("id_periode_paie"));
                    res.put("periode_code", rs.getString("periode_code"));
                    res.put("periode_libelle", rs.getString("periode_libelle"));
                    res.put("date_debut", rs.getDate("date_debut").toString());
                    res.put("date_fin", rs.getDate("date_fin").toString());
                    res.put("statut_code", rs.getString("statut_code"));
                    res.put("statut_libelle", rs.getString("statut_libelle"));
                    return res;
                }
            }
        }
        return null;
    }
}
