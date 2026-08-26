package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.api.dto.pointage.PointageTronconSemaineDTO;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.Date;

@Service
public class PointageSyntheseService {

    private final DataSource dataSource;

    public PointageSyntheseService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Calcule ou récupère la synthèse mensuelle des pointages pour une période de paie donnée.
     * Si la période est VALIDEE ou CLOTUREE et que les données ont été gelées, retourne les snapshots.
     * Sinon, effectue le calcul dynamique temps réel.
     */
    public List<PointageSyntheseMensuelleDTO> getSyntheseMensuelle(int idPeriodePaie, Integer idSite, String search) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return getSyntheseMensuelle(idPeriodePaie, idSite, search, c);
        }
    }

    public List<PointageSyntheseMensuelleDTO> getSyntheseMensuelle(int idPeriodePaie, Integer idSite, String search, Connection c) throws Exception {
        // 1. Récupérer les informations de la période
        String sqlPeriode = "SELECT p.code, p.libelle, p.date_debut, p.date_fin, st.code AS statut_code " +
                "FROM periode_paie p " +
                "JOIN ref_statut_periode_paie st ON p.id_statut_periode = st.id_statut_periode " +
                "WHERE p.id_periode_paie = ?";

        String codePeriode = "";
        String libellePeriode = "";
        Date dateDebutPeriode = null;
        Date dateFinPeriode = null;
        String statutPeriode = "EN_COURS";

        try (PreparedStatement ps = c.prepareStatement(sqlPeriode)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    codePeriode = rs.getString("code");
                    libellePeriode = rs.getString("libelle");
                    dateDebutPeriode = rs.getDate("date_debut");
                    dateFinPeriode = rs.getDate("date_fin");
                    statutPeriode = rs.getString("statut_code");
                } else {
                    throw new IllegalArgumentException("Période de paie introuvable (ID: " + idPeriodePaie + ")");
                }
            }
        }

        boolean isFrozen = "VALIDEE".equalsIgnoreCase(statutPeriode) || "CLOTUREE".equalsIgnoreCase(statutPeriode);

        // 2. Si gelé, tenter de lire les snapshots de cumul
        if (isFrozen) {
            List<PointageSyntheseMensuelleDTO> frozenList = getFrozenSynthese(idPeriodePaie, idSite, search, codePeriode, libellePeriode, statutPeriode, c);
            if (!frozenList.isEmpty()) {
                return frozenList;
            }
        }

        // 3. Calcul dynamique temps réel
        return computeDynamicSynthese(idPeriodePaie, dateDebutPeriode, dateFinPeriode, codePeriode, libellePeriode, statutPeriode, idSite, search, c);
    }

    private List<PointageSyntheseMensuelleDTO> getFrozenSynthese(
            int idPeriodePaie, Integer idSite, String search, String codePeriode, String libellePeriode, String statutPeriode, Connection c) throws Exception {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.*, e.matricule, e.nom, e.prenom, e.photo_url, ")
           .append("COALESCE(eac.id_site, 1) AS id_site_actuel, rs.libelle AS site_nom, ")
           .append("rcp.code AS code_categorie, rcp.libelle AS libelle_categorie, ")
           .append("COALESCE(rf.dispense_pointage, FALSE) AS dispense_pointage, ")
           .append("(rcp.code = 'CADRE' OR COALESCE(rf.dispense_pointage, FALSE)) AS est_cadre ")
           .append("FROM pointage_cumul_periode c ")
           .append("JOIN employe e ON c.id_employe = e.id_employe ")
           .append("LEFT JOIN ( ")
           .append("    SELECT DISTINCT ON (id_employe) id_employe, id_site, id_categorie_professionnelle, id_fonction ")
           .append("    FROM employe_affectation_carriere ")
           .append("    ORDER BY id_employe, COALESCE(date_fin, '9999-12-31') DESC, date_debut DESC, id_affectation DESC ")
           .append(") eac ON e.id_employe = eac.id_employe ")
           .append("LEFT JOIN ref_site rs ON COALESCE(eac.id_site, 1) = rs.id_site ")
           .append("LEFT JOIN ref_categorie_professionnelle rcp ON eac.id_categorie_professionnelle = rcp.id_categorie_professionnelle ")
           .append("LEFT JOIN ref_fonction rf ON eac.id_fonction = rf.id_fonction ")
           .append("WHERE c.id_periode_paie = ? ");

        if (idSite != null && idSite > 0) {
            sql.append("AND COALESCE(eac.id_site, 1) = ? ");
        }
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (e.matricule ILIKE ? OR e.nom ILIKE ? OR e.prenom ILIKE ?) ");
        }
        sql.append("ORDER BY e.matricule ASC");

        List<PointageSyntheseMensuelleDTO> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            ps.setInt(paramIndex++, idPeriodePaie);
            if (idSite != null && idSite > 0) {
                ps.setInt(paramIndex++, idSite);
            }
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim() + "%";
                ps.setString(paramIndex++, pattern);
                ps.setString(paramIndex++, pattern);
                ps.setString(paramIndex++, pattern);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PointageSyntheseMensuelleDTO dto = new PointageSyntheseMensuelleDTO();
                    dto.setIdEmploye(rs.getInt("id_employe"));
                    dto.setMatricule(rs.getString("matricule"));
                    dto.setNom(rs.getString("nom"));
                    dto.setPrenom(rs.getString("prenom"));
                    dto.setPhotoUrl(rs.getString("photo_url"));
                    dto.setIdSite(rs.getInt("id_site_actuel"));
                    dto.setSiteNom(rs.getString("site_nom"));
                    dto.setCodeCategorie(rs.getString("code_categorie"));
                    dto.setLibelleCategorie(rs.getString("libelle_categorie"));
                    dto.setEstCadre(rs.getBoolean("est_cadre"));
                    dto.setDispensePointage(rs.getBoolean("dispense_pointage"));

                    dto.setIdPeriodePaie(idPeriodePaie);
                    dto.setCodePeriode(codePeriode);
                    dto.setLibellePeriode(libellePeriode);
                    dto.setStatutPeriode(statutPeriode);
                    dto.setEstGele(true);

                    dto.setNbJoursOuvrables(rs.getInt("nb_jours_ouvrables_periode"));
                    dto.setNbJoursPresents(rs.getInt("nb_jours_presence_reelle"));
                    dto.setNbJoursAbsence(rs.getInt("nb_jours_absence"));
                    dto.setNbJoursConge(rs.getInt("nb_jours_conge"));
                    dto.setNbJoursFerieChome(rs.getInt("nb_jours_ferie_chome"));
                    dto.setNbJoursFerieTravaille(rs.getInt("nb_jours_ferie_travaille"));
                    dto.setNbJoursDimancheTravaille(rs.getInt("nb_jours_dimanche_travaille"));

                    dto.setTotalHeuresTravaillees(rs.getDouble("heures_normales") + rs.getDouble("heures_hs_30") + rs.getDouble("heures_hs_50"));
                    dto.setHeuresNormales(rs.getDouble("heures_normales"));
                    dto.setHeuresHs30(rs.getDouble("heures_hs_30"));
                    dto.setHeuresHs50(rs.getDouble("heures_hs_50"));
                    dto.setHeuresDimancheTotal(rs.getDouble("heures_dimanche"));
                    dto.setHeuresFerieTotal(rs.getDouble("heures_ferie"));
                    dto.setHeuresNuitTotal(rs.getDouble("heures_nuit"));
                    dto.setHeuresAbsenceRetenues(rs.getDouble("heures_absence_retenues"));

                    dto.setNbJoursDeplacement(rs.getInt("nb_jours_deplacement"));
                    dto.setMontantDeplacement(rs.getDouble("montant_indemnite_deplacement"));
                    dto.setNbJoursRepas(rs.getInt("nb_jours_repas"));
                    dto.setMontantRepas(rs.getDouble("montant_indemnite_repas"));

                    result.add(dto);
                }
            }
        }
        return result;
    }

    private List<PointageSyntheseMensuelleDTO> computeDynamicSynthese(
            int idPeriodePaie, Date dateDebutPeriode, Date dateFinPeriode, String codePeriode,
            String libellePeriode, String statutPeriode, Integer idSiteFilter, String search, Connection c) throws Exception {

        // 0. Chargement dynamique des paramètres globaux RH_GENERAL et POINTAGE
        Map<String, String> paramsGlobaux = ParametreGlobal.loadActiveMap(c);

        // 1. Jours ouvrables par site pour le prorata de déplacement
        Map<Integer, Integer> joursOuvrablesParSite = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id_site, fn_jours_ouvrables('" + dateDebutPeriode + "', '" + dateFinPeriode + "', id_site) AS nb_j FROM ref_site")) {
            while (rs.next()) {
                joursOuvrablesParSite.put(rs.getInt("id_site"), rs.getInt("nb_j"));
            }
        }

        // 2. Barèmes de déplacement (id_indemnite = 1) et repas (id_indemnite = 2) par site
        Map<Integer, Double> baseDeplacementParSite = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id_site, montant_reference FROM site_indemnite_config WHERE id_indemnite = 1 AND est_actif = TRUE")) {
            while (rs.next()) {
                baseDeplacementParSite.put(rs.getInt("id_site"), rs.getDouble("montant_reference"));
            }
        }

        Map<Integer, Double> baseRepasParSite = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id_site, montant_reference FROM site_indemnite_config WHERE id_indemnite = 2 AND est_actif = TRUE")) {
            while (rs.next()) {
                baseRepasParSite.put(rs.getInt("id_site"), rs.getDouble("montant_reference"));
            }
        }

        // 3. Dérogations actives (employés dispensés ou non bénéficiaires)
        Map<Integer, Boolean> derogationDeplacement = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id_employe, beneficie FROM employe_indemnite_derogation WHERE id_indemnite = 1 AND (date_fin IS NULL OR date_fin >= '" + dateFinPeriode + "')")) {
            while (rs.next()) {
                derogationDeplacement.put(rs.getInt("id_employe"), rs.getBoolean("beneficie"));
            }
        }

        Map<Integer, Boolean> derogationRepas = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id_employe, beneficie FROM employe_indemnite_derogation WHERE id_indemnite = 2 AND (date_fin IS NULL OR date_fin >= '" + dateFinPeriode + "')")) {
            while (rs.next()) {
                derogationRepas.put(rs.getInt("id_employe"), rs.getBoolean("beneficie"));
            }
        }

        // 4. Liste des employés éligibles (avec EXACTEMENT une ligne par employé via DISTINCT ON)
        StringBuilder sqlEmp = new StringBuilder();
        sqlEmp.append("SELECT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, ")
              .append("COALESCE(eac.id_site, 1) AS id_site, rs.code_site, rs.libelle AS site_nom, ")
              .append("rcp.code AS code_categorie, rcp.libelle AS libelle_categorie, ")
              .append("COALESCE(rf.dispense_pointage, FALSE) AS dispense_pointage, ")
              .append("(rcp.code = 'CADRE' OR COALESCE(rf.dispense_pointage, FALSE)) AS est_cadre ")
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

        if (idSiteFilter != null && idSiteFilter > 0) {
            sqlEmp.append("AND COALESCE(eac.id_site, 1) = ? ");
        }
        if (search != null && !search.trim().isEmpty()) {
            sqlEmp.append("AND (e.matricule ILIKE ? OR e.nom ILIKE ? OR e.prenom ILIKE ?) ");
        }
        sqlEmp.append("ORDER BY e.matricule ASC");

        List<PointageSyntheseMensuelleDTO> result = new ArrayList<>();

        try (PreparedStatement psEmp = c.prepareStatement(sqlEmp.toString())) {
            int pIdx = 1;
            if (idSiteFilter != null && idSiteFilter > 0) {
                psEmp.setInt(pIdx++, idSiteFilter);
            }
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim() + "%";
                psEmp.setString(pIdx++, pattern);
                psEmp.setString(pIdx++, pattern);
                psEmp.setString(pIdx++, pattern);
            }

            try (ResultSet rsEmp = psEmp.executeQuery()) {
                while (rsEmp.next()) {
                    int idEmploye = rsEmp.getInt("id_employe");
                    int idSiteEmp = rsEmp.getInt("id_site");
                    String codeSiteEmp = rsEmp.getString("code_site");
                    boolean estCadre = rsEmp.getBoolean("est_cadre");

                    PointageSyntheseMensuelleDTO dto = new PointageSyntheseMensuelleDTO();
                    dto.setIdEmploye(idEmploye);
                    dto.setMatricule(rsEmp.getString("matricule"));
                    dto.setNom(rsEmp.getString("nom"));
                    dto.setPrenom(rsEmp.getString("prenom"));
                    dto.setPhotoUrl(rsEmp.getString("photo_url"));
                    dto.setIdSite(idSiteEmp);
                    dto.setSiteNom(rsEmp.getString("site_nom"));
                    dto.setCodeCategorie(rsEmp.getString("code_categorie"));
                    dto.setLibelleCategorie(rsEmp.getString("libelle_categorie"));
                    dto.setEstCadre(estCadre);
                    dto.setDispensePointage(rsEmp.getBoolean("dispense_pointage"));

                    dto.setIdPeriodePaie(idPeriodePaie);
                    dto.setCodePeriode(codePeriode);
                    dto.setLibellePeriode(libellePeriode);
                    dto.setStatutPeriode(statutPeriode);
                    dto.setEstGele(false);

                    int nbJoursOuvrables = joursOuvrablesParSite.getOrDefault(idSiteEmp, 21);
                    dto.setNbJoursOuvrables(nbJoursOuvrables);

                    // Calcul hebdomadaire et mensuel pour cet employé
                    calculerCompteursDynamiques(
                            dto, idEmploye, idPeriodePaie, estCadre, idSiteEmp, codeSiteEmp, nbJoursOuvrables,
                            baseDeplacementParSite, baseRepasParSite,
                            derogationDeplacement, derogationRepas, paramsGlobaux, c
                    );

                    result.add(dto);
                }
            }
        }

        return result;
    }

    private void calculerCompteursDynamiques(
            PointageSyntheseMensuelleDTO dto, int idEmploye, int idPeriodePaie, boolean estCadre,
            int idSite, String codeSite, int nbJoursOuvrables, Map<Integer, Double> baseDeplacementParSite,
            Map<Integer, Double> baseRepasParSite,
            Map<Integer, Boolean> derogationDeplacement, Map<Integer, Boolean> derogationRepas,
            Map<String, String> paramsGlobaux,
            Connection c) throws Exception {

        // Extraits des paramètres dynamiques
        String seuil2ndRepas1 = getParamString(paramsGlobaux, "SEUIL_2ND_REPAS_1", "19:30:00");
        double montant2ndRepas1 = getParamDouble(paramsGlobaux, "MONTANT_2ND_REPAS_1", 2000.0);
        String seuil2ndRepas2 = getParamString(paramsGlobaux, "SEUIL_2ND_REPAS_2", "21:00:00");
        double montant2ndRepas2 = getParamDouble(paramsGlobaux, "MONTANT_2ND_REPAS_2", 4000.0);
        double seuilPauseMidiMin = getParamDouble(paramsGlobaux, "SEUIL_PAUSE_MIDI_MIN", 60.0);
        double reductionDeplactRepasMidi = getParamDouble(paramsGlobaux, "REDUCTION_DEPLACT_REPAS_MIDI", 1000.0);

        // 1. Calculs des jours et des pointages du mois
        String sqlJours = "SELECT " +
                "COUNT(*) FILTER (WHERE st.code IN ('PRESENT', 'DISPENSE_POINTAGE', 'FERIE_TRAVAILLE', 'SAMEDI_TRAVAILLE', 'DIMANCHE_TRAVAILLE')) AS nb_presents, " +
                "COUNT(*) FILTER (WHERE st.code IN ('ABSENCE_NON_JUSTIFIEE', 'ABSENCE_JUSTIFIEE')) AS nb_absences, " +
                "COUNT(*) FILTER (WHERE st.code = 'CONGE') AS nb_conges, " +
                "COUNT(*) FILTER (WHERE st.code = 'FERIE_CHOME') AS nb_feries_chomes, " +
                "COUNT(*) FILTER (WHERE st.code = 'FERIE_TRAVAILLE') AS nb_feries_travailles, " +
                "COUNT(*) FILTER (WHERE st.code = 'DIMANCHE_TRAVAILLE' OR (EXTRACT(ISODOW FROM p.date_pointage) = 7 AND p.total_heures > 0)) AS nb_dimanches_travailles, " +
                "COUNT(*) FILTER (WHERE st.code = 'ANOMALIE_SAISIE' OR (p.heure_entree_matin IS NOT NULL AND p.heure_sortie_matin IS NULL) OR (p.heure_entree_am IS NOT NULL AND p.heure_sortie_am IS NULL)) AS nb_anomalies, " +
                "COALESCE(SUM(p.total_heures) FILTER (WHERE st.decompte_absence = FALSE), 0) AS total_heures, " +
                "COALESCE(SUM(p.heures_nuit), 0) AS total_nuit, " +
                "COALESCE(SUM(p.total_heures) FILTER (WHERE st.code = 'DIMANCHE_TRAVAILLE' OR EXTRACT(ISODOW FROM p.date_pointage) = 7), 0) AS total_dimanche, " +
                "COALESCE(SUM(p.total_heures) FILTER (WHERE st.code = 'FERIE_TRAVAILLE'), 0) AS total_ferie, " +
                "COALESCE(SUM(" +
                "    CASE " +
                "        WHEN p.utilise_vehicule_fonction_jour = TRUE THEN 0.0 " +
                "        WHEN p.deplacement_matin IS NOT NULL AND p.deplacement_am IS NOT NULL " +
                "            THEN ((CASE WHEN p.deplacement_matin = TRUE THEN 0.5 ELSE 0.0 END) + (CASE WHEN p.deplacement_am = TRUE THEN 0.5 ELSE 0.0 END)) " +
                "        WHEN p.indemnite_deplacement_recue = TRUE " +
                "            THEN (CASE WHEN p.heures_matin > 0 AND p.heures_am > 0 THEN 1.0 WHEN p.total_heures >= 4.0 THEN 1.0 ELSE 0.5 END) " +
                "        ELSE 0.0 " +
                "    END" +
                "), 0) AS nb_deplacement, " +
                "COUNT(*) FILTER (WHERE p.indemnite_repas_recue = TRUE OR st.code IN ('PRESENT', 'DISPENSE_POINTAGE', 'FERIE_TRAVAILLE', 'SAMEDI_TRAVAILLE')) AS nb_repas, " +
                "COUNT(*) FILTER (WHERE p.indemnite_repas_recue = TRUE OR (p.heure_sortie_matin IS NOT NULL AND p.heure_entree_am IS NOT NULL AND EXTRACT(EPOCH FROM (p.heure_entree_am - p.heure_sortie_matin))/60.0 <= " + seuilPauseMidiMin + ")) AS nb_repas_sur_site_midi, " +
                "COALESCE(SUM(p.montant_repas), 0) AS total_montant_repas, " +
                "COALESCE(SUM(CASE WHEN p.heure_sortie_am > '" + seuil2ndRepas2 + "' THEN " + montant2ndRepas2 + " WHEN p.heure_sortie_am > '" + seuil2ndRepas1 + "' THEN " + montant2ndRepas1 + " ELSE 0 END), 0) AS extra_repas_soiree " +
                "FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                "WHERE p.id_employe = ? AND p.id_periode_paie = ?";

        double extraRepasSoiree = 0.0;
        double nbDepJours = 0.0;
        int nbRepasSurSiteMidi = 0;
        try (PreparedStatement ps = c.prepareStatement(sqlJours)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dto.setNbJoursPresents(rs.getInt("nb_presents"));
                    dto.setNbJoursAbsence(rs.getInt("nb_absences"));
                    dto.setNbJoursConge(rs.getInt("nb_conges"));
                    dto.setNbJoursFerieChome(rs.getInt("nb_feries_chomes"));
                    dto.setNbJoursFerieTravaille(rs.getInt("nb_feries_travailles"));
                    dto.setNbJoursDimancheTravaille(rs.getInt("nb_dimanches_travailles"));
                    dto.setNbAnomaliesNonResolues(rs.getInt("nb_anomalies"));

                    dto.setTotalHeuresTravaillees(rs.getDouble("total_heures"));
                    dto.setHeuresNuitTotal(rs.getDouble("total_nuit"));
                    dto.setHeuresDimancheTotal(rs.getDouble("total_dimanche"));
                    dto.setHeuresFerieTotal(rs.getDouble("total_ferie"));

                    nbDepJours = rs.getDouble("nb_deplacement");
                    dto.setNbJoursDeplacement((int) Math.round(nbDepJours));
                    dto.setNbJoursRepas(rs.getInt("nb_repas"));
                    nbRepasSurSiteMidi = rs.getInt("nb_repas_sur_site_midi");
                    dto.setMontantRepas(rs.getDouble("total_montant_repas"));
                    extraRepasSoiree = rs.getDouble("extra_repas_soiree");
                }
            }
        }

        // 2. Calcul du montant déplacement proratisé (Forfait mensuel / nb_jours_ouvrables * nb_jours_deplacement)
        boolean aDroitDeplacement = derogationDeplacement.getOrDefault(idEmploye, true);
        if (aDroitDeplacement && nbJoursOuvrables > 0) {
            double baseSite = baseDeplacementParSite.getOrDefault(idSite, 31200.0);
            double tauxJour = baseSite / nbJoursOuvrables;
            double montantDep = BigDecimal.valueOf(tauxJour * nbDepJours)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue();

            // Règle spécifique Tamatave (TMM) : déduction de REDUCTION_DEPLACT_REPAS_MIDI si repas sur site / journée continue
            if ("TMM".equalsIgnoreCase(codeSite) && nbRepasSurSiteMidi > 0) {
                double reductionMidi = nbRepasSurSiteMidi * reductionDeplactRepasMidi;
                montantDep = Math.max(0.0, montantDep - reductionMidi);
            }

            dto.setMontantDeplacement(montantDep);
        } else {
            dto.setMontantDeplacement(0.0);
        }

        // 3. Calcul du montant repas (Journalier du site * nb_jours_repas + extras soirée 19h30/21h)
        boolean aDroitRepas = derogationRepas.getOrDefault(idEmploye, true);
        if (aDroitRepas) {
            double baseRepasJour = baseRepasParSite.getOrDefault(idSite, getParamDouble(paramsGlobaux, "MONTANT_REPAS_MIDI", 2000.0));
            double montantRepasCalcule = (baseRepasJour * dto.getNbJoursRepas()) + extraRepasSoiree;
            if (dto.getMontantRepas() <= 0 && montantRepasCalcule > 0) {
                dto.setMontantRepas(montantRepasCalcule);
            } else if (montantRepasCalcule > dto.getMontantRepas()) {
                dto.setMontantRepas(montantRepasCalcule);
            }
        } else {
            dto.setMontantRepas(0.0);
            dto.setNbJoursRepas(0);
        }

        // 4. Compensation hebdomadaire par tronçon
        List<PointageTronconSemaineDTO> troncons = getTronconsSemainesEmploye(idEmploye, idPeriodePaie, paramsGlobaux, c);
        double cumHs30 = 0.0;
        double cumHs50 = 0.0;
        double cumDeficit = 0.0;

        for (PointageTronconSemaineDTO t : troncons) {
            cumHs30 += t.getHeuresHs30();
            cumHs50 += t.getHeuresHs50();
            cumDeficit += t.getHeuresDeficit();
        }

        dto.setHeuresHs30(cumHs30);
        dto.setHeuresHs50(cumHs50);
        dto.setHeuresAbsenceRetenues(cumDeficit);
        dto.setHeuresNormales(Math.max(0, dto.getTotalHeuresTravaillees() - cumHs30 - cumHs50));
    }

    /**
     * Retourne le détail des tronçons hebdomadaires pour un employé dans une période donnée.
     * Applique la règle légale : proratisation (nb_jours * seuilHeuresJour), absorption des retards, ventilation HS 30/50 pour non-cadres, forçage à 0 pour cadres.
     */
    public List<PointageTronconSemaineDTO> getTronconsSemainesEmploye(int idEmploye, int idPeriodePaie, Connection c) throws Exception {
        Map<String, String> params = chargerParametresGlobaux(c);
        return getTronconsSemainesEmploye(idEmploye, idPeriodePaie, params, c);
    }

    public List<PointageTronconSemaineDTO> getTronconsSemainesEmploye(int idEmploye, int idPeriodePaie, Map<String, String> paramsGlobaux, Connection c) throws Exception {
        if (paramsGlobaux == null) {
            paramsGlobaux = chargerParametresGlobaux(c);
        }
        double seuilHeuresJour = getParamDouble(paramsGlobaux, "SEUIL_HEURES_JOUR", 8.0);
        double seuilHsTranche1 = getParamDouble(paramsGlobaux, "SEUIL_HS_TRANCHE_1", 8.0);

        // Vérifier si cadre
        boolean estCadre = false;
        String sqlCadre = "SELECT (rcp.code = 'CADRE' OR COALESCE(rf.dispense_pointage, FALSE)) AS est_cadre " +
                "FROM employe e " +
                "LEFT JOIN ( " +
                "    SELECT DISTINCT ON (id_employe) id_employe, id_site, id_categorie_professionnelle, id_fonction " +
                "    FROM employe_affectation_carriere " +
                "    ORDER BY id_employe, COALESCE(date_fin, '9999-12-31') DESC, date_debut DESC, id_affectation DESC " +
                ") eac ON e.id_employe = eac.id_employe " +
                "LEFT JOIN ref_categorie_professionnelle rcp ON eac.id_categorie_professionnelle = rcp.id_categorie_professionnelle " +
                "LEFT JOIN ref_fonction rf ON eac.id_fonction = rf.id_fonction " +
                "WHERE e.id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlCadre)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    estCadre = rs.getBoolean("est_cadre");
                }
            }
        }

        String sqlTroncons = "SELECT " +
                "date_trunc('week', p.date_pointage)::date AS debut_semaine, " +
                "MIN(p.date_pointage) AS premier_jour, " +
                "MAX(p.date_pointage) AS dernier_jour, " +
                "COUNT(*) AS nb_jours_troncon, " +
                "COUNT(*) * " + seuilHeuresJour + " AS seuil_heures_troncon, " +
                "COALESCE(SUM(p.total_heures) FILTER (WHERE st.decompte_absence = FALSE), 0) AS total_heures_semaine, " +
                "COALESCE(SUM(p.minutes_retard), 0) / 60.0 AS heures_retard_semaine, " +
                "COALESCE(SUM(p.total_heures) FILTER (WHERE st.code = 'DIMANCHE_TRAVAILLE' OR EXTRACT(ISODOW FROM p.date_pointage) = 7), 0) AS heures_dimanche, " +
                "COALESCE(SUM(p.total_heures) FILTER (WHERE st.code = 'FERIE_TRAVAILLE'), 0) AS heures_ferie, " +
                "COALESCE(SUM(p.heures_nuit), 0) AS heures_nuit " +
                "FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                "WHERE p.id_employe = ? AND p.id_periode_paie = ? " +
                "GROUP BY date_trunc('week', p.date_pointage) " +
                "ORDER BY debut_semaine ASC";

        List<PointageTronconSemaineDTO> list = new ArrayList<>();
        int semIndex = 1;

        try (PreparedStatement ps = c.prepareStatement(sqlTroncons)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PointageTronconSemaineDTO t = new PointageTronconSemaineDTO();
                    t.setNumeroSemaine(semIndex++);
                    t.setDebutSemaine(rs.getString("premier_jour"));
                    t.setFinSemaine(rs.getString("dernier_jour"));
                    t.setNbJoursTroncon(rs.getInt("nb_jours_troncon"));
                    t.setSeuilHeuresTroncon(rs.getDouble("seuil_heures_troncon"));

                    double totalHeures = rs.getDouble("total_heures_semaine");
                    double retards = rs.getDouble("heures_retard_semaine");
                    double seuil = t.getSeuilHeuresTroncon();

                    t.setTotalHeuresTravaillees(totalHeures);
                    t.setHeuresRetard(retards);
                    t.setHeuresDimanche(rs.getDouble("heures_dimanche"));
                    t.setHeuresFerie(rs.getDouble("heures_ferie"));
                    t.setHeuresNuit(rs.getDouble("heures_nuit"));

                    // Règle de compensation hebdomadaire
                    double surplus = Math.max(0, totalHeures - seuil);

                    if (estCadre) {
                        // Règle Cadre : les heures sup effacent les retards, mais pas de paiement d'HS
                        double retardNet = Math.max(0, retards - surplus);
                        t.setHeuresCompensees(Math.min(surplus, retards));
                        t.setHeuresHsNettes(0.0);
                        t.setHeuresHs30(0.0);
                        t.setHeuresHs50(0.0);
                        t.setHeuresDeficit(retardNet);
                    } else {
                        // Règle Non-Cadre : surplus absorbe les retards, solde net ventilé en 30% et 50%
                        double compense = Math.min(surplus, retards);
                        double hsNettes = surplus - compense;
                        double retardResiduel = retards - compense;

                        t.setHeuresCompensees(compense);
                        t.setHeuresHsNettes(hsNettes);

                        if (hsNettes > 0) {
                            double hs30 = Math.min(seuilHsTranche1, hsNettes);
                            double hs50 = Math.max(0.0, hsNettes - seuilHsTranche1);
                            t.setHeuresHs30(hs30);
                            t.setHeuresHs50(hs50);
                        } else {
                            t.setHeuresHs30(0.0);
                            t.setHeuresHs50(0.0);
                        }
                        t.setHeuresDeficit(retardResiduel);
                    }

                    list.add(t);
                }
            }
        }

        return list;
    }

    /**
     * Retourne la liste consolidée des tronçons hebdomadaires pour l'ensemble des salariés
     * avec possibilité de filtrer par semaine (S1..S5), site et recherche.
     */
    public List<PointageTronconSemaineDTO> getSyntheseHebdomadaire(
            int idPeriodePaie, Integer numeroSemaine, Integer idSiteFilter, String search, Connection c) throws Exception {

        StringBuilder sqlEmp = new StringBuilder();
        sqlEmp.append("SELECT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, ")
              .append("COALESCE(eac.id_site, 1) AS id_site, rs.libelle AS site_nom, ")
              .append("rcp.code AS code_categorie, ")
              .append("(rcp.code = 'CADRE' OR COALESCE(rf.dispense_pointage, FALSE)) AS est_cadre ")
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

        if (idSiteFilter != null && idSiteFilter > 0) {
            sqlEmp.append("AND COALESCE(eac.id_site, 1) = ? ");
        }
        if (search != null && !search.trim().isEmpty()) {
            sqlEmp.append("AND (e.matricule ILIKE ? OR e.nom ILIKE ? OR e.prenom ILIKE ?) ");
        }
        sqlEmp.append("ORDER BY e.matricule ASC");

        List<PointageTronconSemaineDTO> result = new ArrayList<>();

        try (PreparedStatement psEmp = c.prepareStatement(sqlEmp.toString())) {
            int pIdx = 1;
            if (idSiteFilter != null && idSiteFilter > 0) {
                psEmp.setInt(pIdx++, idSiteFilter);
            }
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim() + "%";
                psEmp.setString(pIdx++, pattern);
                psEmp.setString(pIdx++, pattern);
                psEmp.setString(pIdx++, pattern);
            }

            try (ResultSet rsEmp = psEmp.executeQuery()) {
                while (rsEmp.next()) {
                    int idEmploye = rsEmp.getInt("id_employe");
                    String matricule = rsEmp.getString("matricule");
                    String nom = rsEmp.getString("nom");
                    String prenom = rsEmp.getString("prenom");
                    String photoUrl = rsEmp.getString("photo_url");
                    int idSite = rsEmp.getInt("id_site");
                    String siteNom = rsEmp.getString("site_nom");
                    String codeCat = rsEmp.getString("code_categorie");
                    boolean estCadre = rsEmp.getBoolean("est_cadre");

                    List<PointageTronconSemaineDTO> troncons = getTronconsSemainesEmploye(idEmploye, idPeriodePaie, c);
                    for (PointageTronconSemaineDTO t : troncons) {
                        if (numeroSemaine == null || t.getNumeroSemaine() == numeroSemaine) {
                            t.setIdEmploye(idEmploye);
                            t.setMatricule(matricule);
                            t.setNom(nom);
                            t.setPrenom(prenom);
                            t.setPhotoUrl(photoUrl);
                            t.setIdSite(idSite);
                            t.setSiteNom(siteNom);
                            t.setCodeCategorie(codeCat);
                            t.setEstCadre(estCadre);
                            result.add(t);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Contrôle pré-clôture : vérifie s'il reste des anomalies non résolues sur la période.
     */
    public Map<String, Object> verifierPreCloture(int idPeriodePaie) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return verifierPreCloture(idPeriodePaie, c);
        }
    }

    public Map<String, Object> verifierPreCloture(int idPeriodePaie, Connection c) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        String sql = "SELECT " +
                "COUNT(*) FILTER (WHERE st.code = 'ANOMALIE_SAISIE' OR (p.heure_entree_matin IS NOT NULL AND p.heure_sortie_matin IS NULL) OR (p.heure_entree_am IS NOT NULL AND p.heure_sortie_am IS NULL)) AS incomplets, " +
                "COUNT(*) FILTER (WHERE st.code = 'ABSENCE_NON_JUSTIFIEE') AS absences, " +
                "COUNT(*) FILTER (WHERE p.minutes_retard > 0 AND (p.motif_ajustement IS NULL OR p.motif_ajustement = '')) AS retards " +
                "FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                "WHERE p.id_periode_paie = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int incomplets = rs.getInt("incomplets");
                    int absences = rs.getInt("absences");
                    int retards = rs.getInt("retards");
                    int total = incomplets + absences + retards;

                    resp.put("totalAnomalies", total);
                    resp.put("totalIncomplets", incomplets);
                    resp.put("totalAbsencesInjustifiees", absences);
                    resp.put("totalRetardsNonJustifies", retards);
                    resp.put("peutValider", true);
                    resp.put("avertissement", total > 0 ? "Attention : Il subsiste " + total + " anomalie(s) non résolue(s) sur cette période." : null);
                }
            }
        }
        return resp;
    }

    /**
     * Gèle définitivement les données de la période dans pointage_semaine_calcule et pointage_cumul_periode.
     */
    public void gelerPeriode(int idPeriodePaie, Integer idUtilisateur, Connection c) throws Exception {
        // 1. Calculer la synthèse pour tous les employés
        List<PointageSyntheseMensuelleDTO> syntheses = getSyntheseMensuelle(idPeriodePaie, null, null, c);

        // 2. Nettoyer les anciens enregistrements de cette période s'il y en avait
        try (PreparedStatement psDelSem = c.prepareStatement("DELETE FROM pointage_semaine_calcule WHERE id_periode_paie = ?");
             PreparedStatement psDelCum = c.prepareStatement("DELETE FROM pointage_cumul_periode WHERE id_periode_paie = ?")) {
            psDelSem.setInt(1, idPeriodePaie);
            psDelSem.executeUpdate();
            psDelCum.setInt(1, idPeriodePaie);
            psDelCum.executeUpdate();
        }

        // 3. Insérer les cumuls de période
        String sqlInsCumul = "INSERT INTO pointage_cumul_periode (" +
                "id_employe, id_periode_paie, nb_jours_ouvrables_periode, nb_jours_presence_reelle, " +
                "nb_jours_absence, nb_jours_conge, nb_jours_ferie_chome, nb_jours_ferie_travaille, " +
                "nb_jours_dimanche_travaille, heures_normales, heures_hs_30, heures_hs_50, " +
                "heures_dimanche, heures_ferie, heures_nuit, heures_absence_retenues, " +
                "nb_jours_deplacement, montant_indemnite_deplacement, nb_jours_repas, montant_indemnite_repas, " +
                "gele_par, gele_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement ps = c.prepareStatement(sqlInsCumul)) {
            for (PointageSyntheseMensuelleDTO s : syntheses) {
                ps.setInt(1, s.getIdEmploye());
                ps.setInt(2, idPeriodePaie);
                ps.setInt(3, s.getNbJoursOuvrables());
                ps.setInt(4, s.getNbJoursPresents());
                ps.setInt(5, s.getNbJoursAbsence());
                ps.setInt(6, s.getNbJoursConge());
                ps.setInt(7, s.getNbJoursFerieChome());
                ps.setInt(8, s.getNbJoursFerieTravaille());
                ps.setInt(9, s.getNbJoursDimancheTravaille());
                ps.setDouble(10, s.getHeuresNormales());
                ps.setDouble(11, s.getHeuresHs30());
                ps.setDouble(12, s.getHeuresHs50());
                ps.setDouble(13, s.getHeuresDimancheTotal());
                ps.setDouble(14, s.getHeuresFerieTotal());
                ps.setDouble(15, s.getHeuresNuitTotal());
                ps.setDouble(16, s.getHeuresAbsenceRetenues());
                ps.setInt(17, s.getNbJoursDeplacement());
                ps.setDouble(18, s.getMontantDeplacement());
                ps.setInt(19, s.getNbJoursRepas());
                ps.setDouble(20, s.getMontantRepas());
                if (idUtilisateur != null) {
                    ps.setInt(21, idUtilisateur);
                } else {
                    ps.setNull(21, Types.INTEGER);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // 4. Insérer les tronçons hebdomadaires
        String sqlInsSem = "INSERT INTO pointage_semaine_calcule (" +
                "id_employe, id_periode_paie, numero_semaine_annee, debut_semaine, fin_semaine, " +
                "nb_jours_troncon, seuil_heures_troncon, total_heures_travaillees, heures_retard, " +
                "heures_compensees, heures_hs_nettes, heures_hs_30, heures_hs_50, heures_dimanche, " +
                "heures_ferie, heures_nuit, heures_deficit) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = c.prepareStatement(sqlInsSem)) {
            for (PointageSyntheseMensuelleDTO s : syntheses) {
                List<PointageTronconSemaineDTO> troncons = getTronconsSemainesEmploye(s.getIdEmploye(), idPeriodePaie, c);
                for (PointageTronconSemaineDTO t : troncons) {
                    ps.setInt(1, s.getIdEmploye());
                    ps.setInt(2, idPeriodePaie);
                    ps.setInt(3, t.getNumeroSemaine());
                    ps.setDate(4, java.sql.Date.valueOf(t.getDebutSemaine()));
                    ps.setDate(5, java.sql.Date.valueOf(t.getFinSemaine()));
                    ps.setInt(6, t.getNbJoursTroncon());
                    ps.setDouble(7, t.getSeuilHeuresTroncon());
                    ps.setDouble(8, t.getTotalHeuresTravaillees());
                    ps.setDouble(9, t.getHeuresRetard());
                    ps.setDouble(10, t.getHeuresCompensees());
                    ps.setDouble(11, t.getHeuresHsNettes());
                    ps.setDouble(12, t.getHeuresHs30());
                    ps.setDouble(13, t.getHeuresHs50());
                    ps.setDouble(14, t.getHeuresDimanche());
                    ps.setDouble(15, t.getHeuresFerie());
                    ps.setDouble(16, t.getHeuresNuit());
                    ps.setDouble(17, t.getHeuresDeficit());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    /**
     * Dégèle les données lors de la réouverture exceptionnelle de la période.
     */
    public void degelerPeriode(int idPeriodePaie, Connection c) throws Exception {
        try (PreparedStatement psDelSem = c.prepareStatement("DELETE FROM pointage_semaine_calcule WHERE id_periode_paie = ?");
             PreparedStatement psDelCum = c.prepareStatement("DELETE FROM pointage_cumul_periode WHERE id_periode_paie = ?")) {
            psDelSem.setInt(1, idPeriodePaie);
            psDelSem.executeUpdate();
            psDelCum.setInt(1, idPeriodePaie);
            psDelCum.executeUpdate();
        }
    }

    /**
     * Charge l'ensemble des paramètres globaux applicables (RH_GENERAL, POINTAGE) depuis la BDD.
     */
    public Map<String, String> chargerParametresGlobaux(Connection c) {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT code_parametre, valeur FROM parametre_global WHERE date_fin IS NULL OR date_fin >= CURRENT_DATE";
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString("code_parametre").toUpperCase(), rs.getString("valeur"));
            }
        } catch (Exception e) {
            System.err.println("[POINTAGE PARAMETRE] Erreur lors du chargement des paramètres globaux: " + e.getMessage());
        }
        return map;
    }

    private double getParamDouble(Map<String, String> params, String key, double defaultValue) {
        if (params == null) return defaultValue;
        try {
            String val = params.get(key.toUpperCase());
            return val != null ? Double.parseDouble(val.trim()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getParamString(Map<String, String> params, String key, String defaultValue) {
        if (params == null) return defaultValue;
        String val = params.get(key.toUpperCase());
        return (val != null && !val.trim().isEmpty()) ? val.trim() : defaultValue;
    }
}
