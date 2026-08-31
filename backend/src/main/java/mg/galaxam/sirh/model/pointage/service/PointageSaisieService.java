package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import org.springframework.beans.factory.annotation.Autowired;
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
public class PointageSaisieService {

    @Autowired
    private DataSource dataSource;

    public PointageSaisieService() {}

    public PointageSaisieService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Parse une heure sous forme de chaîne "HH:mm" ou "HH:mm:ss" en java.sql.Time
     */
    public static Time parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty() || "—".equals(timeStr) || "--:--".equals(timeStr)) {
            return null;
        }
        try {
            String clean = timeStr.trim();
            if (clean.length() == 5) {
                clean += ":00";
            }
            return Time.valueOf(clean);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Calcul de la durée en heures entre 2 bornes (gère les shifts de nuit traversant minuit)
     */
    public static double calcHours(Time debut, Time fin) {
        if (debut == null || fin == null) return 0.0;
        LocalTime t1 = debut.toLocalTime();
        LocalTime t2 = fin.toLocalTime();
        long minutes;
        if (t2.isBefore(t1)) {
            minutes = Duration.between(t1, LocalTime.MAX).toMinutes() + 1 + Duration.between(LocalTime.MIN, t2).toMinutes();
        } else {
            minutes = Duration.between(t1, t2).toMinutes();
        }
        return Math.round((minutes / 60.0) * 100.0) / 100.0;
    }

    /**
     * Calcul des heures de nuit travaillées (entre 22h00 et 05h00)
     */
    public static double calcHeuresNuit(Time sortieSoir) {
        return calcHeuresNuit(sortieSoir, LocalTime.of(22, 0), LocalTime.of(5, 0));
    }

    public static double calcHeuresNuit(Time sortieSoir, LocalTime debutNuit, LocalTime finNuit) {
        if (sortieSoir == null || debutNuit == null) return 0.0;
        LocalTime s = sortieSoir.toLocalTime();
        if (finNuit == null) finNuit = LocalTime.of(5, 0);

        if (s.isAfter(debutNuit)) {
            long mins = Duration.between(debutNuit, s).toMinutes();
            return Math.round((mins / 60.0) * 100.0) / 100.0;
        } else if (s.isBefore(finNuit)) {
            long mins = Duration.between(debutNuit, LocalTime.MAX).toMinutes() + 1 + Duration.between(LocalTime.MIN, s).toMinutes();
            return Math.round((mins / 60.0) * 100.0) / 100.0;
        }
        return 0.0;
    }

    /**
     * Calcul des minutes de retard par rapport à une heure prévue
     */
    public static int calcMinutesRetard(Time reel, LocalTime prevu) {
        if (reel == null || prevu == null) return 0;
        LocalTime r = reel.toLocalTime();
        if (r.isAfter(prevu)) {
            return (int) Duration.between(prevu, r).toMinutes();
        }
        return 0;
    }

    /**
     * Matrice de décision logique stricte pour le statut du pointage.
     * Note: Dans la BDD, un salarié en retard est "PRESENT" avec minutes_retard > 0.
     */
    public static String determinerStatutJour(
            boolean isFerie,
            String typeConge,
            boolean isAutonome,
            Time em, Time sm, Time ea, Time sa,
            int retardMinutes,
            boolean hasValidationErr
    ) {
        boolean isFullConge = "FULL_DAY".equalsIgnoreCase(typeConge);
        boolean isCongeMatin = "MATIN".equalsIgnoreCase(typeConge);
        boolean isCongeAm = "APRES_MIDI".equalsIgnoreCase(typeConge);

        boolean aSaisiDesHeures = (em != null || sm != null || ea != null || sa != null);

        // 1. Primauté absolue du FÉRIÉ CHÔMÉ
        if (isFerie) {
            if (aSaisiDesHeures) {
                return "FERIE_TRAVAILLE";
            }
            return "FERIE_CHOME";
        }

        // 2. Erreur de cohérence chronologique
        if (hasValidationErr) {
            return "ANOMALIE_SAISIE";
        }

        // 3. Congé journée entière
        if (isFullConge) {
            return "CONGE";
        }

        // 4. Aucun horaire saisi
        if (!aSaisiDesHeures) {
            if (isAutonome) return "DISPENSE_POINTAGE";
            return "NON_SAISI";
        }

        // 5. Cas pause automatique (Entrée Matin et Sortie Soir uniquement)
        if (em != null && sa != null && sm == null && ea == null) {
            return "PRESENT";
        }

        // 6. Incohérence de badgeage partiel
        if ((em != null && sm == null) || (ea != null && sa == null) || (em == null && sm != null) || (ea == null && sa != null)) {
            return "ANOMALIE_SAISIE";
        }

        // 7. Demi-journées de congé
        if (em != null && sm != null && ea == null && sa == null) {
            return isCongeAm ? "PRESENT" : "ANOMALIE_SAISIE";
        }
        if (em == null && sm == null && ea != null && sa != null) {
            return isCongeMatin ? "PRESENT" : "ANOMALIE_SAISIE";
        }

        // 8. Jour normal complet
        double totalH = calcHours(em, sm) + calcHours(ea, sa);
        if (totalH == 0.0) {
            return "ABSENCE_NON_JUSTIFIEE";
        }
        return "PRESENT";
    }

    /**
     * Génère la grille de saisie quotidienne pour un site et une date donnés
     */
    public Map<String, Object> getGrilleSaisie(int idSite, String dateStr) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        Date date = Date.valueOf(dateStr);

        try (Connection c = dataSource.getConnection()) {
            // 1. Informations de la Période de Paie correspondante
            Integer idPeriode = null;
            Map<String, Object> periodeInfo = null;
            String sqlPeriode = "SELECT p.id_periode_paie, p.code, p.libelle, p.date_debut, p.date_fin, s.code AS statut_code, s.libelle AS statut_libelle " +
                                "FROM periode_paie p " +
                                "JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                                "WHERE ? BETWEEN p.date_debut AND p.date_fin LIMIT 1";
            try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
                psP.setDate(1, date);
                try (ResultSet rsP = psP.executeQuery()) {
                    if (rsP.next()) {
                        idPeriode = rsP.getInt("id_periode_paie");
                        periodeInfo = new HashMap<>();
                        periodeInfo.put("id_periode_paie", idPeriode);
                        periodeInfo.put("code", rsP.getString("code"));
                        periodeInfo.put("libelle", rsP.getString("libelle"));
                        java.sql.Date dDeb = rsP.getDate("date_debut");
                        java.sql.Date dFin = rsP.getDate("date_fin");
                        periodeInfo.put("date_debut", dDeb != null ? dDeb.toString() : null);
                        periodeInfo.put("date_fin", dFin != null ? dFin.toString() : null);
                        periodeInfo.put("statut_code", rsP.getString("statut_code"));
                        periodeInfo.put("statut_libelle", rsP.getString("statut_libelle"));

                        int ouvrables = 0;
                        if (dDeb != null && dFin != null) {
                            for (LocalDate cur = dDeb.toLocalDate(); !cur.isAfter(dFin.toLocalDate()); cur = cur.plusDays(1)) {
                                if (cur.getDayOfWeek() != DayOfWeek.SATURDAY && cur.getDayOfWeek() != DayOfWeek.SUNDAY) {
                                    ouvrables++;
                                }
                            }
                        }
                        if (ouvrables == 0) ouvrables = 22;
                        periodeInfo.put("nbJoursOuvrables", ouvrables);
                    }
                }
            }
            result.put("periodeInfo", periodeInfo);

            // 2. Vérification du Jour Férié Chômé (National ou spécifique au Site)
            boolean isFerie = false;
            String libelleFerie = null;
            String sqlFerie = "SELECT f.libelle, f.est_chome " +
                              "FROM ref_calendrier_ferie f " +
                              "LEFT JOIN site_calendrier_ferie scf ON f.id_ferie = scf.id_ferie AND scf.id_site = ? " +
                              "WHERE f.date_ferie = ? " +
                              "  AND f.est_chome = TRUE " +
                              "  AND (f.est_national = TRUE OR scf.id_site IS NOT NULL) " +
                              "LIMIT 1";
            try (PreparedStatement psF = c.prepareStatement(sqlFerie)) {
                psF.setInt(1, idSite);
                psF.setDate(2, date);
                try (ResultSet rsF = psF.executeQuery()) {
                    if (rsF.next()) {
                        isFerie = true;
                        libelleFerie = rsF.getString("libelle");
                    }
                }
            }

            // 2.bis Vérification des Périodes Exceptionnelles chômées (ref_periode_chomee, ex: intempéries, cyclone, fermeture)
            if (!isFerie) {
                String sqlPeriodeChomee = "SELECT libelle FROM ref_periode_chomee " +
                                          "WHERE ? BETWEEN date_debut AND COALESCE(date_fin, date_debut) " +
                                          "  AND (id_site IS NULL OR id_site = ?) LIMIT 1";
                try (PreparedStatement psPC = c.prepareStatement(sqlPeriodeChomee)) {
                    psPC.setDate(1, date);
                    psPC.setInt(2, idSite);
                    try (ResultSet rsPC = psPC.executeQuery()) {
                        if (rsPC.next()) {
                            isFerie = true;
                            libelleFerie = rsPC.getString("libelle");
                        }
                    }
                }
            }
            result.put("isFerie", isFerie);
            result.put("libelleFerie", libelleFerie);

            // 3. Paramètres Horaires et Indemnités du Site
            Map<String, Object> siteParams = new HashMap<>();
            String sqlSite = "SELECT horaire_continu, heure_debut_matin, heure_fin_matin, heure_debut_apres_midi, heure_fin_apres_midi " +
                             "FROM ref_site WHERE id_site = ?";
            try (PreparedStatement psS = c.prepareStatement(sqlSite)) {
                psS.setInt(1, idSite);
                try (ResultSet rsS = psS.executeQuery()) {
                    if (rsS.next()) {
                        boolean continu = rsS.getBoolean("horaire_continu");
                        siteParams.put("horaireContinu", continu);
                        Time hdm = rsS.getTime("heure_debut_matin");
                        Time hfm = rsS.getTime("heure_fin_matin");
                        Time hda = rsS.getTime("heure_debut_apres_midi");
                        Time hfa = rsS.getTime("heure_fin_apres_midi");
                        siteParams.put("heureDebutMatin", hdm != null ? hdm.toString().substring(0, 5) : "08:00");
                        siteParams.put("heureFinMatin", hfm != null ? hfm.toString().substring(0, 5) : "12:00");
                        siteParams.put("heureDebutAm", hda != null ? hda.toString().substring(0, 5) : (continu ? "13:00" : "14:00"));
                        siteParams.put("heureFinAm", hfa != null ? hfa.toString().substring(0, 5) : "17:00");
                        siteParams.put("siteDeplacementActif", true);
                        siteParams.put("siteRepasActif", true);
                    }
                }
            }

            // 4. Indemnités Spécifiques du Site (site_indemnite_config) avec Prorata sur jours ouvrables réels
            int nbOuvrablesPeriode = (periodeInfo != null && periodeInfo.get("nbJoursOuvrables") != null)
                    ? (int) periodeInfo.get("nbJoursOuvrables")
                    : 22;

            try (Statement stmt = c.createStatement()) {
                stmt.execute("ALTER TABLE ref_indemnite ADD COLUMN IF NOT EXISTS fractionnable BOOLEAN DEFAULT FALSE");
                stmt.execute("UPDATE ref_indemnite SET fractionnable = TRUE WHERE code IN ('DEPLACEMENT', 'TRANSPORT') AND fractionnable IS NOT TRUE");
            } catch (Exception ignored) {}

            double montantDeplacementMois = 31200.0;
            double montantDeplacementJour = Math.round((montantDeplacementMois / nbOuvrablesPeriode) * 100.0) / 100.0;
            double montantRepasJour = 2000.0;

            List<Map<String, Object>> siteIndemnites = new ArrayList<>();
            String sqlSiteInd = "SELECT c.id_indemnite, i.code, i.libelle, COALESCE(i.fractionnable, FALSE) AS fractionnable, " +
                                "c.periodicite, c.montant_reference " +
                                "FROM site_indemnite_config c " +
                                "JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite " +
                                "WHERE c.id_site = ? AND c.est_actif = TRUE AND c.periodicite != 'FIXE_MENSUEL' ORDER BY i.libelle";
            try (PreparedStatement psI = c.prepareStatement(sqlSiteInd)) {
                psI.setInt(1, idSite);
                try (ResultSet rsI = psI.executeQuery()) {
                    while (rsI.next()) {
                        Map<String, Object> ind = new HashMap<>();
                        int idInd = rsI.getInt("id_indemnite");
                        String codeInd = rsI.getString("code");
                        String libInd = rsI.getString("libelle");
                        boolean fractionnable = rsI.getBoolean("fractionnable");
                        String period = rsI.getString("periodicite");
                        double montantRef = rsI.getDouble("montant_reference");

                        boolean isProratise = "MENSUEL".equalsIgnoreCase(period) || "MENSUEL_PRORATISE".equalsIgnoreCase(period);
                        double montantJour = isProratise
                                ? Math.round((montantRef / nbOuvrablesPeriode) * 100.0) / 100.0
                                : montantRef;
                        double montantTrajet = fractionnable ? Math.round((montantJour / 2.0) * 100.0) / 100.0 : montantJour;

                        String sousTitre = isProratise
                                ? "prorata (" + String.format(Locale.FRENCH, "%,d", Math.round(montantJour)) + " Ar/j)"
                                : String.format(Locale.FRENCH, "%,d", Math.round(montantJour)) + " Ar/j";

                        ind.put("idIndemnite", idInd);
                        ind.put("code", codeInd);
                        ind.put("libelle", libInd);
                        ind.put("fractionnable", fractionnable);
                        ind.put("estFractionnable", fractionnable);
                        ind.put("estProratise", isProratise);
                        ind.put("periodicite", period);
                        ind.put("montantReference", montantRef);
                        ind.put("montantJour", montantJour);
                        ind.put("montantTrajet", montantTrajet);
                        ind.put("sousTitre", sousTitre);
                        ind.put("nbJoursOuvrablesPeriode", nbOuvrablesPeriode);
                        siteIndemnites.add(ind);

                        if ("DEPLACEMENT".equalsIgnoreCase(codeInd) || "TRANSPORT".equalsIgnoreCase(codeInd)) {
                            montantDeplacementMois = montantRef;
                            montantDeplacementJour = montantJour;
                        } else if ("REPAS".equalsIgnoreCase(codeInd)) {
                            montantRepasJour = montantRef;
                        }
                    }
                }
            }
            result.put("siteIndemnites", siteIndemnites);

            siteParams.put("nbJoursOuvrablesPeriode", nbOuvrablesPeriode);
            siteParams.put("montantDeplacementMois", montantDeplacementMois);
            siteParams.put("montantDeplacementJour", montantDeplacementJour);
            siteParams.put("montantDeplacementTrajet", Math.round((montantDeplacementJour / 2.0) * 100.0) / 100.0);
            siteParams.put("montantRepasJour", montantRepasJour);
            result.put("siteParams", siteParams);

            // 5. Employés Affectés au Site (dispense_pointage provient de ref_fonction)
            String sqlEmp = "SELECT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                            "COALESCE(f.dispense_pointage, FALSE) AS est_autonome " +
                            "FROM employe e " +
                            "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                            "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                            "WHERE a.id_site = ? AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                            "AND e.est_actif = TRUE " +
                            "ORDER BY e.nom, e.prenom";

            List<SaisiePointageJourDTO> grille = new ArrayList<>();
            try (PreparedStatement psE = c.prepareStatement(sqlEmp)) {
                psE.setInt(1, idSite);
                psE.setDate(2, date);
                psE.setDate(3, date);

                try (ResultSet rsE = psE.executeQuery()) {
                    while (rsE.next()) {
                        int idEmp = rsE.getInt("id_employe");
                        boolean isAutonome = rsE.getBoolean("est_autonome");

                        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                        dto.setIdEmploye(idEmp);
                        dto.setMatricule(rsE.getString("matricule"));
                        dto.setNom(rsE.getString("nom"));
                        dto.setPrenom(rsE.getString("prenom"));
                        dto.setPhotoUrl(rsE.getString("photo_url"));
                        dto.setDatePointage(dateStr);
                        dto.setIdSite(idSite);
                        dto.setIdPeriodePaie(idPeriode);
                        dto.setEstAutonome(isAutonome);
                        dto.setFerieJour(isFerie);
                        dto.setLibelleFerie(libelleFerie);

                        // Congé approuvé ce jour
                        String sqlConge = "SELECT tc.libelle, dc.est_demi_journee, dc.moment_demi_journee " +
                                          "FROM demande_conge dc " +
                                          "JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
                                          "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                                          "WHERE dc.id_employe = ? AND ? BETWEEN dc.date_debut AND dc.date_fin " +
                                          "AND sc.code IN ('VALIDE', 'APPROUVEE') LIMIT 1";
                        String typeCongeJour = null;
                        String libelleConge = null;

                        if (!isFerie) {
                            try (PreparedStatement psC = c.prepareStatement(sqlConge)) {
                                psC.setInt(1, idEmp);
                                psC.setDate(2, date);
                                try (ResultSet rsC = psC.executeQuery()) {
                                    if (rsC.next()) {
                                        libelleConge = rsC.getString("libelle");
                                        boolean estDemi = rsC.getBoolean("est_demi_journee");
                                        String mdj = rsC.getString("moment_demi_journee");
                                        if (estDemi && "MATIN".equalsIgnoreCase(mdj)) typeCongeJour = "MATIN";
                                        else if (estDemi && "APRES_MIDI".equalsIgnoreCase(mdj)) typeCongeJour = "APRES_MIDI";
                                        else typeCongeJour = "FULL_DAY";
                                    }
                                }
                            }
                        }
                        dto.setTypeCongeJour(typeCongeJour);
                        dto.setLibelleConge(libelleConge);

                        // Pointage journalier existant
                        String sqlPtg = "SELECT p.id_pointage, p.heure_entree_matin, p.heure_sortie_matin, " +
                                        "p.heure_entree_am, p.heure_sortie_am, p.total_heures, p.minutes_retard, " +
                                        "p.heures_nuit, p.motif_ajustement, st.code AS statut_code, st.libelle AS statut_libelle, " +
                                        "p.deplacement_matin, p.deplacement_am, p.indemnite_repas_recue " +
                                        "FROM pointage_jour p " +
                                        "LEFT JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                                        "WHERE p.id_employe = ? AND p.date_pointage = ?";

                        boolean hasExisting = false;
                        try (PreparedStatement psPtg = c.prepareStatement(sqlPtg)) {
                            psPtg.setInt(1, idEmp);
                            psPtg.setDate(2, date);
                            try (ResultSet rsPtg = psPtg.executeQuery()) {
                                if (rsPtg.next()) {
                                    hasExisting = true;
                                    dto.setIdPointage(rsPtg.getInt("id_pointage"));
                                    Time em = rsPtg.getTime("heure_entree_matin");
                                    Time sm = rsPtg.getTime("heure_sortie_matin");
                                    Time ea = rsPtg.getTime("heure_entree_am");
                                    Time sa = rsPtg.getTime("heure_sortie_am");

                                    dto.setHeureEntreeMatin(em != null ? em.toString().substring(0, 5) : "");
                                    dto.setHeureSortieMatin(sm != null ? sm.toString().substring(0, 5) : "");
                                    dto.setHeureEntreeAm(ea != null ? ea.toString().substring(0, 5) : "");
                                    dto.setHeureSortieAm(sa != null ? sa.toString().substring(0, 5) : "");
                                    dto.setTotalHeures(rsPtg.getDouble("total_heures"));
                                    dto.setMinutesRetard(rsPtg.getInt("minutes_retard"));
                                    dto.setHeuresNuit(rsPtg.getDouble("heures_nuit"));
                                    dto.setMotifAjustement(rsPtg.getString("motif_ajustement"));
                                    dto.setCodeStatut(rsPtg.getString("statut_code"));
                                    dto.setLibelleStatut(rsPtg.getString("statut_libelle"));
                                    dto.setDeplacementMatin(rsPtg.getBoolean("deplacement_matin"));
                                    dto.setDeplacementAm(rsPtg.getBoolean("deplacement_am"));
                                    dto.setIndemniteRepasRecue(rsPtg.getBoolean("indemnite_repas_recue"));
                                }
                            }
                        }

                        // Valeurs par défaut si non encore enregistré
                        if (!hasExisting) {
                            if (isFerie) {
                                dto.setHeureEntreeMatin("");
                                dto.setHeureSortieMatin("");
                                dto.setHeureEntreeAm("");
                                dto.setHeureSortieAm("");
                                dto.setTotalHeures(0.0);
                                dto.setMinutesRetard(0);
                                dto.setCodeStatut("FERIE_CHOME");
                                dto.setLibelleStatut(libelleFerie != null ? libelleFerie : "Férié chômé");
                                dto.setDeplacementMatin(false);
                                dto.setDeplacementAm(false);
                                dto.setIndemniteRepasRecue(false);
                            } else if ("FULL_DAY".equalsIgnoreCase(typeCongeJour)) {
                                dto.setHeureEntreeMatin("—");
                                dto.setHeureSortieMatin("—");
                                dto.setHeureEntreeAm("—");
                                dto.setHeureSortieAm("—");
                                dto.setTotalHeures(0.0);
                                dto.setMinutesRetard(0);
                                dto.setCodeStatut("CONGE");
                                dto.setLibelleStatut(libelleConge != null ? libelleConge : "Congé");
                                dto.setDeplacementMatin(false);
                                dto.setDeplacementAm(false);
                                dto.setIndemniteRepasRecue(false);
                            } else if ("MATIN".equalsIgnoreCase(typeCongeJour)) {
                                dto.setHeureEntreeMatin("—");
                                dto.setHeureSortieMatin("—");
                                dto.setHeureEntreeAm(isAutonome ? (String) siteParams.get("heureDebutAm") : "");
                                dto.setHeureSortieAm(isAutonome ? (String) siteParams.get("heureFinAm") : "");
                                dto.setTotalHeures(isAutonome ? 4.0 : 0.0);
                                dto.setMinutesRetard(0);
                                dto.setCodeStatut(isAutonome ? "DISPENSE_POINTAGE" : "NON_SAISI");
                                dto.setLibelleStatut(isAutonome ? "Cadre autonome (4h)" : "Congé Matin");
                                dto.setDeplacementMatin(false);
                                dto.setDeplacementAm(true);
                                dto.setIndemniteRepasRecue(false);
                            } else if ("APRES_MIDI".equalsIgnoreCase(typeCongeJour)) {
                                dto.setHeureEntreeMatin(isAutonome ? (String) siteParams.get("heureDebutMatin") : "");
                                dto.setHeureSortieMatin(isAutonome ? (String) siteParams.get("heureFinMatin") : "");
                                dto.setHeureEntreeAm("—");
                                dto.setHeureSortieAm("—");
                                dto.setTotalHeures(isAutonome ? 4.0 : 0.0);
                                dto.setMinutesRetard(0);
                                dto.setCodeStatut(isAutonome ? "DISPENSE_POINTAGE" : "NON_SAISI");
                                dto.setLibelleStatut(isAutonome ? "Cadre autonome (4h)" : "Congé Après-midi");
                                dto.setDeplacementMatin(true);
                                dto.setDeplacementAm(false);
                                dto.setIndemniteRepasRecue(true);
                            } else if (isAutonome) {
                                dto.setHeureEntreeMatin((String) siteParams.get("heureDebutMatin"));
                                dto.setHeureSortieMatin((String) siteParams.get("heureFinMatin"));
                                dto.setHeureEntreeAm((String) siteParams.get("heureDebutAm"));
                                dto.setHeureSortieAm((String) siteParams.get("heureFinAm"));
                                dto.setTotalHeures(8.0);
                                dto.setMinutesRetard(0);
                                dto.setCodeStatut("DISPENSE_POINTAGE");
                                dto.setLibelleStatut("Cadre autonome (8h)");
                                dto.setDeplacementMatin(true);
                                dto.setDeplacementAm(true);
                                dto.setIndemniteRepasRecue(true);
                            } else {
                                dto.setHeureEntreeMatin("");
                                dto.setHeureSortieMatin("");
                                dto.setHeureEntreeAm("");
                                dto.setHeureSortieAm("");
                                dto.setTotalHeures(0.0);
                                dto.setMinutesRetard(0);
                                dto.setCodeStatut("NON_SAISI");
                                dto.setLibelleStatut("Non saisi");
                                dto.setDeplacementMatin(true);
                                dto.setDeplacementAm(true);
                                dto.setIndemniteRepasRecue(true);
                            }
                        }

                        // Indemnités dynamiques & Dérogations individuelles
                        Map<String, Object> dynMap = new HashMap<>();

                        if (hasExisting && dto.getIdPointage() != null) {
                            // 1. Charger depuis pointage_indemnite_jour si déjà enregistré
                            String sqlLoadDyn = "SELECT id_indemnite, accorde, quantite, montant_calcule FROM pointage_indemnite_jour WHERE id_pointage = ?";
                            Map<Integer, Map<String, Object>> savedDyn = new HashMap<>();
                            try (PreparedStatement psDynL = c.prepareStatement(sqlLoadDyn)) {
                                psDynL.setInt(1, dto.getIdPointage());
                                try (ResultSet rsDynL = psDynL.executeQuery()) {
                                    while (rsDynL.next()) {
                                        Map<String, Object> item = new HashMap<>();
                                        item.put("accordee", rsDynL.getBoolean("accorde"));
                                        item.put("montantApplique", rsDynL.getDouble("montant_calcule"));
                                        item.put("fractionJour", rsDynL.getDouble("quantite"));
                                        savedDyn.put(rsDynL.getInt("id_indemnite"), item);
                                    }
                                }
                            }
                            for (Map<String, Object> sInd : siteIndemnites) {
                                int idInd = (int) sInd.get("idIndemnite");
                                if (savedDyn.containsKey(idInd)) {
                                    dynMap.put(String.valueOf(idInd), savedDyn.get(idInd));
                                } else {
                                    double mntJour = (double) sInd.get("montantJour");
                                    Map<String, Object> item = new HashMap<>();
                                    item.put("accordee", !isFerie && !"FULL_DAY".equalsIgnoreCase(typeCongeJour));
                                    item.put("montantApplique", mntJour);
                                    item.put("fractionJour", (!isFerie && !"FULL_DAY".equalsIgnoreCase(typeCongeJour)) ? 1.0 : 0.0);
                                    dynMap.put(String.valueOf(idInd), item);
                                }
                            }
                        } else {
                            // 2. Calcul par défaut avec vérification des dérogations (exclusions) individuelles
                            Set<Integer> exclusionsIndemnites = new HashSet<>();
                            boolean excluDeplacement = false;
                            boolean excluRepas = false;

                            String sqlDerog = "SELECT d.id_indemnite, i.code, d.beneficie " +
                                              "FROM employe_indemnite_derogation d " +
                                              "JOIN ref_indemnite i ON d.id_indemnite = i.id_indemnite " +
                                              "WHERE d.id_employe = ? AND d.date_debut <= ? AND (d.date_fin IS NULL OR d.date_fin >= ?)";
                            try (PreparedStatement psDerog = c.prepareStatement(sqlDerog)) {
                                psDerog.setInt(1, idEmp);
                                psDerog.setDate(2, date);
                                psDerog.setDate(3, date);
                                try (ResultSet rsDerog = psDerog.executeQuery()) {
                                    while (rsDerog.next()) {
                                        boolean beneficie = rsDerog.getBoolean("beneficie");
                                        int idIndDerog = rsDerog.getInt("id_indemnite");
                                        String codeInd = rsDerog.getString("code");
                                        if (!beneficie) {
                                            exclusionsIndemnites.add(idIndDerog);
                                            if ("TRANSPORT".equalsIgnoreCase(codeInd) || "DEPLACEMENT".equalsIgnoreCase(codeInd)) {
                                                excluDeplacement = true;
                                            } else if ("REPAS".equalsIgnoreCase(codeInd)) {
                                                excluRepas = true;
                                            }
                                        }
                                    }
                                }
                            }

                            if (excluDeplacement) {
                                dto.setDeplacementMatin(false);
                                dto.setDeplacementAm(false);
                            }
                            if (excluRepas) {
                                dto.setIndemniteRepasRecue(false);
                            }

                            for (Map<String, Object> sInd : siteIndemnites) {
                                int idInd = (int) sInd.get("idIndemnite");
                                double mntJour = (double) sInd.get("montantJour");
                                boolean estExclu = exclusionsIndemnites.contains(idInd);
                                boolean acc = !estExclu && !isFerie && !"FULL_DAY".equalsIgnoreCase(typeCongeJour);

                                Map<String, Object> item = new HashMap<>();
                                item.put("accordee", acc);
                                item.put("montantApplique", mntJour);
                                item.put("fractionJour", acc ? 1.0 : 0.0);
                                dynMap.put(String.valueOf(idInd), item);
                            }
                        }
                        dto.setDynamicIndemnites(dynMap);

                        grille.add(dto);
                    }
                }
            }
            result.put("grilleData", grille);
        }

        return result;
    }

    /**
     * Enregistrement en lot des pointages journaliers
     */
    public Map<String, Object> enregistrerPointagesBatch(List<SaisiePointageJourDTO> pointages, Connection c) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        if (pointages == null || pointages.isEmpty()) {
            result.put("success", true);
            result.put("message", "Aucun pointage à enregistrer.");
            return result;
        }

        Map<String, String> params = ParametreGlobal.loadActiveMap(c);
        String seuilRepas1 = ParametreGlobal.getString(params, "SEUIL_2ND_REPAS_1", "19:30");
        double mntRepas1 = ParametreGlobal.getDouble(params, "MONTANT_2ND_REPAS_1", 2000.0);
        String seuilRepas2 = ParametreGlobal.getString(params, "SEUIL_2ND_REPAS_2", "21:00");
        double mntRepas2 = ParametreGlobal.getDouble(params, "MONTANT_2ND_REPAS_2", 4000.0);
        double mntRepasMidi = ParametreGlobal.getDouble(params, "MONTANT_REPAS_MIDI", 2000.0);
        String debutNuitStr = ParametreGlobal.getString(params, "SEUIL_NUIT_DEBUT", "22:00");
        String finNuitStr = ParametreGlobal.getString(params, "SEUIL_NUIT_FIN", "05:00");
        LocalTime debutNuit = LocalTime.parse(debutNuitStr.length() == 5 ? debutNuitStr : debutNuitStr.substring(0, 5));
        LocalTime finNuit = LocalTime.parse(finNuitStr.length() == 5 ? finNuitStr : finNuitStr.substring(0, 5));

        String sqlUpsert = "INSERT INTO pointage_jour (" +
                "id_employe, date_pointage, id_periode_paie, id_site, " +
                "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                "heures_matin, heures_am, total_heures, minutes_retard, heures_nuit, " +
                "motif_ajustement, id_statut_jour, deplacement_matin, deplacement_am, " +
                "indemnite_deplacement_recue, indemnite_repas_recue, montant_repas, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                "COALESCE((SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT')), " +
                "?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id_employe, date_pointage) DO UPDATE SET " +
                "heure_entree_matin = EXCLUDED.heure_entree_matin, " +
                "heure_sortie_matin = EXCLUDED.heure_sortie_matin, " +
                "heure_entree_am = EXCLUDED.heure_entree_am, " +
                "heure_sortie_am = EXCLUDED.heure_sortie_am, " +
                "heures_matin = EXCLUDED.heures_matin, " +
                "heures_am = EXCLUDED.heures_am, " +
                "total_heures = EXCLUDED.total_heures, " +
                "minutes_retard = EXCLUDED.minutes_retard, " +
                "heures_nuit = EXCLUDED.heures_nuit, " +
                "motif_ajustement = EXCLUDED.motif_ajustement, " +
                "id_statut_jour = EXCLUDED.id_statut_jour, " +
                "deplacement_matin = EXCLUDED.deplacement_matin, " +
                "deplacement_am = EXCLUDED.deplacement_am, " +
                "indemnite_deplacement_recue = EXCLUDED.indemnite_deplacement_recue, " +
                "indemnite_repas_recue = EXCLUDED.indemnite_repas_recue, " +
                "montant_repas = EXCLUDED.montant_repas, " +
                "updated_at = CURRENT_TIMESTAMP RETURNING id_pointage";

        String sqlDynInd = "INSERT INTO pointage_indemnite_jour (" +
                "id_pointage, id_indemnite, fraction, quantite, montant_calcule, accorde, updated_at" +
                ") VALUES (?, ?, 'JOUR_ENTIER', ?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id_pointage, id_indemnite, fraction) DO UPDATE SET " +
                "quantite = EXCLUDED.quantite, " +
                "montant_calcule = EXCLUDED.montant_calcule, " +
                "accorde = EXCLUDED.accorde, " +
                "updated_at = CURRENT_TIMESTAMP";

        try (PreparedStatement ps = c.prepareStatement(sqlUpsert);
             PreparedStatement psDyn = c.prepareStatement(sqlDynInd)) {

            for (SaisiePointageJourDTO dto : pointages) {
                Time em = parseTime(dto.getHeureEntreeMatin());
                Time sm = parseTime(dto.getHeureSortieMatin());
                Time ea = parseTime(dto.getHeureEntreeAm());
                Time sa = parseTime(dto.getHeureSortieAm());

                double hMatin = calcHours(em, sm);
                double hAm = calcHours(ea, sa);
                double totalH = hMatin + hAm;
                double hNuit = calcHeuresNuit(sa, debutNuit, finNuit);
                int retMins = calcMinutesRetard(em, LocalTime.of(8, 0)) + calcMinutesRetard(ea, LocalTime.of(13, 0));

                if ("FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())) totalH = 0.0;

                // Calcul dynamique du montant repas (déjeuner + extra soirée)
                double mntRepasJour = 0.0;
                if (dto.isIndemniteRepasRecue() && !"FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour()) && !dto.isFerieJour()) {
                    mntRepasJour = mntRepasMidi;
                    if (sa != null) {
                        String saStr = sa.toString().substring(0, 5);
                        if (saStr.compareTo(seuilRepas2) >= 0) {
                            mntRepasJour += mntRepas2;
                        } else if (saStr.compareTo(seuilRepas1) >= 0) {
                            mntRepasJour += mntRepas1;
                        }
                    }
                }

                boolean depM = dto.isDeplacementMatin();
                boolean depA = dto.isDeplacementAm();
                boolean depRecue = depM || depA;

                String codeStatut = dto.getCodeStatut();
                if (codeStatut == null || codeStatut.isEmpty() || "NON_SAISI".equals(codeStatut)) {
                    codeStatut = determinerStatutJour(
                            dto.isFerieJour(), dto.getTypeCongeJour(), dto.isEstAutonome(),
                            em, sm, ea, sa, retMins, false
                    );
                }
                // Si le statut calculé est "RETARD", en base le statut est "PRESENT" (minutes_retard contient les minutes)
                if ("RETARD".equals(codeStatut)) {
                    codeStatut = "PRESENT";
                }

                Integer idPer = dto.getIdPeriodePaie();
                if (idPer == null || idPer == 0) {
                    String sqlFind = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
                    try (PreparedStatement psF = c.prepareStatement(sqlFind)) {
                        psF.setDate(1, Date.valueOf(dto.getDatePointage()));
                        try (ResultSet rsF = psF.executeQuery()) {
                            if (rsF.next()) idPer = rsF.getInt(1);
                        }
                    }
                }

                ps.setInt(1, dto.getIdEmploye());
                ps.setDate(2, Date.valueOf(dto.getDatePointage()));
                ps.setObject(3, idPer);
                ps.setInt(4, dto.getIdSite());
                ps.setTime(5, em);
                ps.setTime(6, sm);
                ps.setTime(7, ea);
                ps.setTime(8, sa);
                ps.setDouble(9, hMatin);
                ps.setDouble(10, hAm);
                ps.setDouble(11, totalH);
                ps.setInt(12, retMins);
                ps.setDouble(13, hNuit);
                ps.setString(14, dto.getMotifAjustement());
                ps.setString(15, codeStatut);
                ps.setBoolean(16, depM);
                ps.setBoolean(17, depA);
                ps.setBoolean(18, depRecue);
                ps.setBoolean(19, dto.isIndemniteRepasRecue());
                ps.setDouble(20, mntRepasJour);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int idPtg = rs.getInt(1);
                        if (dto.getDynamicIndemnites() != null) {
                            for (Map.Entry<String, Object> entry : dto.getDynamicIndemnites().entrySet()) {
                                int idInd = Integer.parseInt(entry.getKey());
                                if (entry.getValue() instanceof Map<?, ?> vals) {
                                    boolean acc = Boolean.TRUE.equals(vals.get("accordee"));
                                    double mnt = vals.get("montantApplique") != null ? Double.parseDouble(vals.get("montantApplique").toString()) : 0.0;
                                    double frac = vals.get("fractionJour") != null ? Double.parseDouble(vals.get("fractionJour").toString()) : (acc ? 1.0 : 0.0);

                                    psDyn.setInt(1, idPtg);
                                    psDyn.setInt(2, idInd);
                                    psDyn.setDouble(3, frac);
                                    psDyn.setDouble(4, mnt);
                                    psDyn.setBoolean(5, acc);
                                    psDyn.executeUpdate();
                                }
                            }
                        }
                    }
                }
            }
        }
        result.put("success", true);
        result.put("message", "Pointages enregistrés avec succès.");
        return result;
    }
}
