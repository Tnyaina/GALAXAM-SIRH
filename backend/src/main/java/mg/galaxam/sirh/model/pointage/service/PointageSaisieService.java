package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PointageSaisieService {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine calendrierEngine;

    @Autowired
    private mg.galaxam.sirh.model.pointage.engine.PointageCalculateurEngine calculateurEngine;

    private static final mg.galaxam.sirh.model.pointage.engine.PointageCalculateurEngine CALCULATEUR_ENGINE =
            new mg.galaxam.sirh.model.pointage.engine.PointageCalculateurEngine();

    public PointageSaisieService() {
        this.calendrierEngine = new CalendrierOuvrableEngine();
        this.calculateurEngine = CALCULATEUR_ENGINE;
    }

    public PointageSaisieService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.calendrierEngine = new CalendrierOuvrableEngine();
        this.calculateurEngine = CALCULATEUR_ENGINE;
    }

    public PointageSaisieService(DataSource dataSource,
                                 mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine calendrierEngine,
                                 mg.galaxam.sirh.model.pointage.engine.PointageCalculateurEngine calculateurEngine) {
        this.dataSource = dataSource;
        this.calendrierEngine = calendrierEngine != null ? calendrierEngine : new CalendrierOuvrableEngine();
        this.calculateurEngine = calculateurEngine != null ? calculateurEngine : CALCULATEUR_ENGINE;
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
     * Calcul des heures de nuit travaillées (entre 22h00 et 05h00) délégué au moteur.
     */
    public static double calcHeuresNuit(Time sortieSoir) {
        return calcHeuresNuit(sortieSoir, LocalTime.of(22, 0), LocalTime.of(5, 0));
    }

    public static double calcHeuresNuit(Time sortieSoir, LocalTime debutNuit, LocalTime finNuit) {
        if (sortieSoir == null || debutNuit == null) return 0.0;
        return CALCULATEUR_ENGINE.calculerHeuresNuit(sortieSoir.toLocalTime(), debutNuit, finNuit);
    }

    /**
     * Calcul des minutes de retard par rapport à une heure prévue délégué au moteur.
     */
    public static int calcMinutesRetard(Time reel, LocalTime prevu) {
        if (reel == null || prevu == null) return 0;
        return CALCULATEUR_ENGINE.calculerMinutesRetard(reel.toLocalTime(), prevu, null);
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
        return determinerStatutJour(null, isFerie, typeConge, isAutonome, em, sm, ea, sa, retardMinutes, hasValidationErr);
    }

    public static String determinerStatutJour(
            LocalDate date,
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
            // Un week-end sans heures saisies n'est JAMAIS une absence : c'est un repos légal !
            if (CalendrierOuvrableEngine.isWeekend(date)) {
                return "REPOS";
            }
            return "ABSENCE_NON_JUSTIFIEE";
        }

        // 5. Cas pause automatique (Entrée Matin et Sortie Soir uniquement)
        if (em != null && sa != null && sm == null && ea == null) {
            if (date != null && CalendrierOuvrableEngine.isWeekend(date)) {
                return (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) ? "SAMEDI_TRAVAILLE" : "DIMANCHE_TRAVAILLE";
            }
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

        // 8. Jour normal complet ou Week-end travaillé
        double totalH = calcHours(em, sm) + calcHours(ea, sa);
        if (totalH == 0.0) {
            if (CalendrierOuvrableEngine.isWeekend(date)) {
                return "REPOS";
            }
            return "ABSENCE_NON_JUSTIFIEE";
        }
        if (date != null && CalendrierOuvrableEngine.isWeekend(date)) {
            return (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) ? "SAMEDI_TRAVAILLE" : "DIMANCHE_TRAVAILLE";
        }
        return "PRESENT";
    }

    /**
     * Génère la grille de saisie quotidienne pour un site et une date donnés (avec DataSource)
     */
    public Map<String, Object> getGrilleSaisie(int idSite, String dateStr) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return getGrilleSaisie(idSite, dateStr, c);
        }
    }

    /**
     * Génère la grille de saisie quotidienne pour un site et une date donnés (avec Connection fournie)
     */
    public Map<String, Object> getGrilleSaisie(int idSite, String dateStr, Connection c) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        Date date = Date.valueOf(dateStr);
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
                            ouvrables = calendrierEngine.compterJoursOuvrables(dDeb.toLocalDate(), dFin.toLocalDate(), idSite, c);
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
                                          "WHERE (id_site IS NULL OR id_site = ?) " +
                                          "  AND ? BETWEEN date_debut AND COALESCE(date_fin, date_debut) " +
                                          "LIMIT 1";
                try (PreparedStatement psPC = c.prepareStatement(sqlPeriodeChomee)) {
                    psPC.setInt(1, idSite);
                    psPC.setDate(2, date);
                    try (ResultSet rsPC = psPC.executeQuery()) {
                        if (rsPC.next()) {
                            isFerie = true;
                            libelleFerie = rsPC.getString("libelle");
                        }
                    }
                } catch (Exception ignored) {}
            }
            result.put("isFerie", isFerie);
            result.put("libelleFerie", libelleFerie);

            // 3. Paramètres Horaires du Site (directement depuis ref_site)
            Map<String, Object> siteParams = new HashMap<>();
            String sqlSite = "SELECT s.id_site, s.code_site, s.libelle, " +
                             "s.heure_debut_matin, s.heure_fin_matin, s.heure_debut_apres_midi, s.heure_fin_apres_midi, " +
                             "COALESCE(s.horaire_continu, TRUE) AS horaire_continu " +
                             "FROM ref_site s " +
                             "WHERE s.id_site = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlSite)) {
                ps.setInt(1, idSite);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        siteParams.put("idSite", rs.getInt("id_site"));
                        siteParams.put("codeSite", rs.getString("code_site"));
                        siteParams.put("libelle", rs.getString("libelle"));
                        Time hdm = rs.getTime("heure_debut_matin");
                        Time hfm = rs.getTime("heure_fin_matin");
                        Time hda = rs.getTime("heure_debut_apres_midi");
                        Time hfa = rs.getTime("heure_fin_apres_midi");
                        boolean continu = rs.getBoolean("horaire_continu");

                        siteParams.put("horaireContinu", continu);
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

            // Chargement anticipé des paramètres globaux pour résoudre MONTANT_REPAS_MIDI
            // (nécessaire dès la construction de siteIndemnites pour les sites à tarif repas = 0 comme TMM)
            Map<String, String> globalParams = ParametreGlobal.loadActiveMap(c);
            double mntRepasMidiGlobal = ParametreGlobal.getDouble(globalParams, "MONTANT_REPAS_MIDI", 2000.0);

            double montantDeplacementMois = 31200.0;
            double montantDeplacementJour = calendrierEngine.calculerProrataJournalier(montantDeplacementMois, nbOuvrablesPeriode);
            double montantRepasSite = 0.0;
            boolean siteRepasInclusParDefaut = false;

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
                                ? calendrierEngine.calculerProrataJournalier(montantRef, nbOuvrablesPeriode)
                                : montantRef;
                        double montantTrajet = fractionnable ? calendrierEngine.calculerMontantTrajet(montantJour) : montantJour;

                        // Pour les indemnités REPAS avec montantRef = 0 (ex: TMM), le montant affiché
                        // quand la case est cochée manuellement doit être le montant global MONTANT_REPAS_MIDI.
                        boolean isRepasZero = "REPAS".equalsIgnoreCase(codeInd) && montantRef <= 0.0;
                        double montantGlobal = isRepasZero ? mntRepasMidiGlobal : montantJour;

                        String sousTitre;
                        if (isRepasZero) {
                            sousTitre = "Non inclus site (" + String.format(Locale.FRENCH, "%,d", Math.round(montantGlobal)) + " Ar/j si coché)";
                        } else if (isProratise) {
                            sousTitre = "prorata (" + String.format(Locale.FRENCH, "%,d", Math.round(montantJour)) + " Ar/j)";
                        } else {
                            sousTitre = String.format(Locale.FRENCH, "%,d", Math.round(montantJour)) + " Ar/j";
                        }

                        ind.put("idIndemnite", idInd);
                        ind.put("code", codeInd);
                        ind.put("libelle", libInd);
                        ind.put("fractionnable", fractionnable);
                        ind.put("estFractionnable", fractionnable);
                        ind.put("estProratise", isProratise);
                        ind.put("periodicite", period);
                        ind.put("montantReference", montantRef);
                        ind.put("montantJour", montantJour);
                        // montantGlobal = montant à utiliser si coché manuellement alors que montantJour = 0
                        ind.put("montantGlobal", montantGlobal);
                        // useGlobalIfZero = true indique au frontend que la valeur site est 0 et que le
                        // montant effectif appliqué sera celui des paramètres globaux si l'agent coche la case
                        ind.put("useGlobalIfZero", isRepasZero);
                        ind.put("cocheParDefaut", !isRepasZero);  // false = non coché par défaut si site = 0
                        ind.put("montantTrajet", montantTrajet);
                        ind.put("sousTitre", sousTitre);
                        ind.put("nbJoursOuvrablesPeriode", nbOuvrablesPeriode);
                        siteIndemnites.add(ind);

                        if ("DEPLACEMENT".equalsIgnoreCase(codeInd) || "TRANSPORT".equalsIgnoreCase(codeInd)) {
                            montantDeplacementMois = montantRef;
                            montantDeplacementJour = montantJour;
                        } else if ("REPAS".equalsIgnoreCase(codeInd)) {
                            montantRepasSite = montantRef;
                            siteRepasInclusParDefaut = montantRef > 0.0;
                        }
                    }
                }
            }
            result.put("siteIndemnites", siteIndemnites);

            siteParams.put("nbJoursOuvrablesPeriode", nbOuvrablesPeriode);
            siteParams.put("montantDeplacementMois", montantDeplacementMois);
            siteParams.put("montantDeplacementJour", montantDeplacementJour);
            siteParams.put("montantDeplacementTrajet", Math.round((montantDeplacementJour / 2.0) * 100.0) / 100.0);
            siteParams.put("montantRepasJour", siteRepasInclusParDefaut ? montantRepasSite : mntRepasMidiGlobal);
            siteParams.put("siteRepasInclusParDefaut", siteRepasInclusParDefaut);
            result.put("siteParams", siteParams);
            result.put("siteParams", siteParams);

            // 5. Employés Affectés au Site avec Contrat ACTIF couvrant la date
            String sqlEmp = "SELECT DISTINCT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                            "COALESCE(f.dispense_pointage, FALSE) AS est_autonome " +
                            "FROM employe e " +
                            "JOIN contrat_travail ct ON e.id_employe = ct.id_employe " +
                            "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                            "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe AND a.id_contrat = ct.id_contrat " +
                            "JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh " +
                            "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                            "WHERE e.est_actif = TRUE " +
                            "  AND rsc.code = 'ACTIF' " +
                            "  AND ct.date_debut <= ? AND (ct.date_fin IS NULL OR ct.date_fin >= ?) " +
                            "  AND rsa.code = 'ACTIF' " +
                            "  AND a.id_site = ? AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                            "ORDER BY e.nom, e.prenom";

            List<SaisiePointageJourDTO> grille = new ArrayList<>();
            try (PreparedStatement psE = c.prepareStatement(sqlEmp)) {
                psE.setDate(1, date);
                psE.setDate(2, date);
                psE.setInt(3, idSite);
                psE.setDate(4, date);
                psE.setDate(5, date);

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
                        String sqlConge = "SELECT tc.libelle, tc.code AS type_code, COALESCE(dc.est_remunere, tc.est_paye) AS est_paye, " +
                                          "dc.est_demi_journee, dc.moment_demi_journee " +
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
                                        String tCode = rsC.getString("type_code");
                                        boolean estPayeConge = rsC.getBoolean("est_paye");
                                        String baseLib = rsC.getString("libelle");

                                        if ("PERMISSION".equalsIgnoreCase(tCode)) {
                                            libelleConge = estPayeConge ? "Permission autorisée (Payée)" : "Permission autorisée (Non payée)";
                                        } else if (!estPayeConge) {
                                            libelleConge = baseLib + " (Sans solde)";
                                        } else {
                                            libelleConge = baseLib;
                                        }

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
                        String sqlPtg = "SELECT p.id_pointage, COALESCE(p.version, 1) AS version, p.heure_entree_matin, p.heure_sortie_matin, " +
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
                                    dto.setVersion(rsPtg.getInt("version"));
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
                                dto.setIndemniteRepasRecue(siteRepasInclusParDefaut);
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
                                dto.setIndemniteRepasRecue(siteRepasInclusParDefaut);
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
                                dto.setIndemniteRepasRecue(siteRepasInclusParDefaut);
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
                                    double mntGlobal = sInd.get("montantGlobal") != null ? (double) sInd.get("montantGlobal") : mntJour;
                                    boolean useGlobal = Boolean.TRUE.equals(sInd.get("useGlobalIfZero"));
                                    boolean cocheParDefaut = !Boolean.FALSE.equals(sInd.get("cocheParDefaut"));
                                    boolean acc = !isFerie && !"FULL_DAY".equalsIgnoreCase(typeCongeJour) && cocheParDefaut;

                                    Map<String, Object> item = new HashMap<>();
                                    item.put("accordee", acc);
                                    item.put("montantApplique", useGlobal ? mntGlobal : mntJour);
                                    item.put("fractionJour", acc ? 1.0 : 0.0);
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
                                double mntGlobal = sInd.get("montantGlobal") != null ? (double) sInd.get("montantGlobal") : mntJour;
                                boolean useGlobal = Boolean.TRUE.equals(sInd.get("useGlobalIfZero"));
                                boolean cochePaDefaut = !Boolean.FALSE.equals(sInd.get("cochePaDefaut"));
                                boolean estExclu = exclusionsIndemnites.contains(idInd);
                                // Si le site a un tarif = 0 (useGlobal), la case n'est pas cochée par défaut
                                // même si l'employé n'est pas en congé/férié.
                                boolean acc = !estExclu && !isFerie && !"FULL_DAY".equalsIgnoreCase(typeCongeJour) && cochePaDefaut;

                                Map<String, Object> item = new HashMap<>();
                                item.put("accordee", acc);
                                // Si coché, on applique le montant global (MONTANT_REPAS_MIDI) si site = 0
                                item.put("montantApplique", useGlobal ? mntGlobal : mntJour);
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

            // Calcul autoritaire côté serveur des statistiques globales de la journée
            int totalInscrits = grille.size();
            int totalPresents = 0;
            int totalConges = 0;
            int totalRetards = 0;
            int cumulMinsRetard = 0;
            double totalHeures = 0.0;
            int totalAnomalies = 0;

            for (SaisiePointageJourDTO row : grille) {
                if (row.isPresent() || row.isDispensePointage() || row.isFerieTravaille() || (row.getTotalHeures() != null && row.getTotalHeures() > 0)) {
                    totalPresents++;
                }
                if (row.isConge()) {
                    totalConges++;
                }
                if (row.isRetard()) {
                    totalRetards++;
                }
                if (row.getMinutesRetard() != null) {
                    cumulMinsRetard += row.getMinutesRetard();
                }
                if (row.getTotalHeures() != null) {
                    totalHeures += row.getTotalHeures();
                }
                if (row.isAnomalie() || row.isAbsenceNonJustifiee()) {
                    totalAnomalies++;
                }
            }

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalInscrits", totalInscrits);
            stats.put("totalPresents", totalPresents);
            stats.put("totalConges", totalConges);
            stats.put("totalRetards", totalRetards);
            stats.put("cumulMinutesRetard", cumulMinsRetard);
            stats.put("totalHeuresTravaillees", Math.round(totalHeures * 100.0) / 100.0);
            stats.put("totalAnomalies", totalAnomalies);
            result.put("statsJour", stats);

            return result;
    }

    /**
     * Vérifie l'éligibilité stricte d'un employé au pointage pour un site et une date donnés.
     * Doit être appelé sur TOUS les chemins d'écriture de pointage.
     */
    public void verifierEligibilitePointage(Connection c, int idEmploye, int idSite, Date datePointage) throws SQLException {
        String sql = "SELECT e.id_employe " +
                "FROM employe e " +
                "JOIN contrat_travail ct ON e.id_employe = ct.id_employe " +
                "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe AND ct.id_contrat = a.id_contrat " +
                "JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh " +
                "WHERE e.id_employe = ? " +
                "  AND e.est_actif = TRUE " +
                "  AND rsc.code = 'ACTIF' " +
                "  AND ct.date_debut <= ? AND (ct.date_fin IS NULL OR ct.date_fin >= ?) " +
                "  AND rsa.code = 'ACTIF' " +
                "  AND a.id_site = ? " +
                "  AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                "LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, datePointage);
            ps.setDate(3, datePointage);
            ps.setInt(4, idSite);
            ps.setDate(5, datePointage);
            ps.setDate(6, datePointage);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("L'employé ID " + idEmploye + " n'est pas éligible au pointage pour la date du "
                            + datePointage + " sur le site ID " + idSite + " (absence d'employé actif, de contrat ACTIF couvrant la date, ou d'affectation ACTIVE sur ce site).");
                }
            }
        }
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

        Map<String, Integer> idIndemniteByCode = new HashMap<>();
        try (PreparedStatement psRI = c.prepareStatement("SELECT id_indemnite, code FROM ref_indemnite");
             ResultSet rsRI = psRI.executeQuery()) {
            while (rsRI.next()) {
                idIndemniteByCode.put(rsRI.getString("code"), rsRI.getInt("id_indemnite"));
            }
        }

        String sqlFindPer = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
        String sqlCheckPer = "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ? FOR SHARE";
        String sqlCheckRow = "SELECT id_pointage, COALESCE(version, 1) AS version FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?";

        String sqlUpdate = "UPDATE pointage_jour SET " +
                "heure_entree_matin = ?, heure_sortie_matin = ?, heure_entree_am = ?, heure_sortie_am = ?, " +
                "heures_matin = ?, heures_am = ?, total_heures = ?, minutes_retard = ?, heures_nuit = ?, " +
                "motif_ajustement = ?, id_statut_jour = COALESCE((SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT')), " +
                "deplacement_matin = ?, deplacement_am = ?, indemnite_deplacement_recue = ?, indemnite_repas_recue = ?, " +
                "montant_repas = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_pointage = ? AND version = ? RETURNING id_pointage, version";

        String sqlInsert = "INSERT INTO pointage_jour (" +
                "id_employe, date_pointage, id_periode_paie, id_site, " +
                "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                "heures_matin, heures_am, total_heures, minutes_retard, heures_nuit, " +
                "motif_ajustement, id_statut_jour, deplacement_matin, deplacement_am, " +
                "indemnite_deplacement_recue, indemnite_repas_recue, montant_repas, version, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                "COALESCE((SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT')), " +
                "?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP) RETURNING id_pointage, version";

        String sqlDynInd = "INSERT INTO pointage_indemnite_jour (" +
                "id_pointage, id_indemnite, fraction, quantite, montant_calcule, accorde, updated_at" +
                ") VALUES (?, ?, 'JOUR_ENTIER', ?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id_pointage, id_indemnite, fraction) DO UPDATE SET " +
                "quantite = EXCLUDED.quantite, " +
                "montant_calcule = EXCLUDED.montant_calcule, " +
                "accorde = EXCLUDED.accorde, " +
                "updated_at = CURRENT_TIMESTAMP";

        // Chargement des horaires configurés par site
        Map<Integer, LocalTime[]> siteHoraires = new HashMap<>();
        String sqlSites = "SELECT id_site, heure_debut_matin, heure_fin_matin, heure_debut_apres_midi, heure_fin_apres_midi, COALESCE(horaire_continu, TRUE) AS horaire_continu FROM ref_site";
        try (PreparedStatement psSites = c.prepareStatement(sqlSites);
             ResultSet rsSites = psSites.executeQuery()) {
            while (rsSites.next()) {
                int sId = rsSites.getInt("id_site");
                Time hdm = rsSites.getTime("heure_debut_matin");
                Time hfm = rsSites.getTime("heure_fin_matin");
                Time hda = rsSites.getTime("heure_debut_apres_midi");
                Time hfa = rsSites.getTime("heure_fin_apres_midi");
                boolean cont = rsSites.getBoolean("horaire_continu");

                LocalTime ltDebM = hdm != null ? hdm.toLocalTime() : LocalTime.of(8, 0);
                LocalTime ltFinM = hfm != null ? hfm.toLocalTime() : LocalTime.of(12, 0);
                LocalTime ltDebA = hda != null ? hda.toLocalTime() : (cont ? LocalTime.of(13, 0) : LocalTime.of(14, 0));
                LocalTime ltFinA = hfa != null ? hfa.toLocalTime() : (cont ? LocalTime.of(17, 0) : LocalTime.of(18, 0));

                siteHoraires.put(sId, new LocalTime[]{ltDebM, ltFinM, ltDebA, ltFinA});
            }
        } catch (Exception ignored) {}

        try (PreparedStatement psFindPer = c.prepareStatement(sqlFindPer);
             PreparedStatement psCheckPer = c.prepareStatement(sqlCheckPer);
             PreparedStatement psCheckRow = c.prepareStatement(sqlCheckRow);
             PreparedStatement psUp = c.prepareStatement(sqlUpdate);
             PreparedStatement psIns = c.prepareStatement(sqlInsert);
             PreparedStatement psDyn = c.prepareStatement(sqlDynInd)) {

            for (SaisiePointageJourDTO dto : pointages) {
                if (dto.getIdEmploye() == null || dto.getDatePointage() == null) {
                    continue;
                }

                int idSiteRow = dto.getIdSite() != null ? dto.getIdSite() : 1;
                Date dtPtg = Date.valueOf(dto.getDatePointage());

                // 1. Contrôle P0-1 : Éligibilité stricte au pointage à l'écriture (Employé actif + Contrat ACTIF + Affectation ACTIVE sur le site)
                verifierEligibilitePointage(c, dto.getIdEmploye(), idSiteRow, dtPtg);

                // 2. Contrôle P0-3 : Verrouillage de la Période de Paie (avec verrou partagé FOR SHARE)
                Integer idPer = dto.getIdPeriodePaie();
                if (idPer == null || idPer == 0) {
                    psFindPer.setDate(1, dtPtg);
                    try (ResultSet rsF = psFindPer.executeQuery()) {
                        if (rsF.next()) idPer = rsF.getInt(1);
                    }
                }

                if (idPer != null && idPer > 0) {
                    psCheckPer.setInt(1, idPer);
                    try (ResultSet rsCP = psCheckPer.executeQuery()) {
                        if (rsCP.next()) {
                            String stCode = rsCP.getString("code");
                            if ("VALIDEE".equalsIgnoreCase(stCode) || "CLOTUREE".equalsIgnoreCase(stCode) || "TRANSMIS_DAF".equalsIgnoreCase(stCode)) {
                                throw new IllegalStateException("Impossible de modifier les pointages : la période de paie est fermée.");
                            }
                        }
                    }
                }

                LocalTime[] sH = siteHoraires.getOrDefault(idSiteRow, new LocalTime[]{
                        LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(17, 0)
                });
                LocalTime siteDebM = sH[0];
                LocalTime siteFinM = sH[1];
                LocalTime siteDebA = sH[2];
                LocalTime siteFinA = sH[3];

                Time em = parseTime(dto.getHeureEntreeMatin());
                Time sm = parseTime(dto.getHeureSortieMatin());
                Time ea = parseTime(dto.getHeureEntreeAm());
                Time sa = parseTime(dto.getHeureSortieAm());

                boolean isPauseAuto = (em != null && sa != null && sm == null && ea == null);
                double hMatin;
                double hAm;

                if (isPauseAuto) {
                    Time smEff = Time.valueOf(siteFinM);
                    Time eaEff = Time.valueOf(siteDebA);
                    hMatin = calcHours(em, smEff);
                    hAm = calcHours(eaEff, sa);
                } else {
                    hMatin = calcHours(em, sm);
                    hAm = calcHours(ea, sa);
                }

                double totalH = hMatin + hAm;
                double hNuit = calcHeuresNuit(sa, debutNuit, finNuit);

                // Calcul du retard précis et juste selon le site
                int retMatin = calcMinutesRetard(em, siteDebM);
                int retAm = 0;
                if (!isPauseAuto && ea != null) {
                    if (sm != null && siteDebA.isAfter(siteFinM)) {
                        long pauseTheorique = Duration.between(siteFinM, siteDebA).toMinutes();
                        LocalTime ltSm = sm.toLocalTime();
                        LocalTime ltEa = ea.toLocalTime();
                        if (ltSm.isAfter(siteFinM) && ltEa.isAfter(ltSm)) {
                            long pauseReelle = Duration.between(ltSm, ltEa).toMinutes();
                            retAm = (int) Math.max(0, pauseReelle - pauseTheorique);
                        } else {
                            retAm = calcMinutesRetard(ea, siteDebA);
                        }
                    } else {
                        retAm = calcMinutesRetard(ea, siteDebA);
                    }
                }

                boolean isExemptRetard = dto.isFerieJour() || "FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour()) || dto.isEstAutonome();
                int retMins = isExemptRetard ? 0 : (retMatin + retAm);

                if ("FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())) {
                    totalH = 0.0;
                } else if (dto.isFerieJour() && em == null && sm == null && ea == null && sa == null) {
                    totalH = 0.0;
                } else if (dto.isEstAutonome() && em == null && sm == null && ea == null && sa == null) {
                    totalH = 8.0;
                }

                // Calcul dynamique du montant repas (déjeuner + extra soirée) via moteur
                double mntRepasJour = 0.0;
                if (dto.isIndemniteRepasRecue() && !"FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour()) && !dto.isFerieJour()) {
                    mntRepasJour = CALCULATEUR_ENGINE.calculerTotalRepasJour(
                            true,
                            mntRepasMidi,
                            sa != null ? sa.toLocalTime() : null,
                            LocalTime.parse(seuilRepas1),
                            mntRepas1,
                            LocalTime.parse(seuilRepas2),
                            mntRepas2
                    );
                }

                boolean depM = dto.isDeplacementMatin();
                boolean depA = dto.isDeplacementAm();
                boolean depRecue = depM || depA;

                LocalDate datePtg = null;
                try {
                    if (dto.getDatePointage() != null) datePtg = LocalDate.parse(dto.getDatePointage());
                } catch (Exception ignored) {}

                String codeStatut = dto.getCodeStatut();
                if (codeStatut == null || codeStatut.isEmpty() || "NON_SAISI".equals(codeStatut)) {
                    codeStatut = determinerStatutJour(
                            datePtg,
                            dto.isFerieJour(), dto.getTypeCongeJour(), dto.isEstAutonome(),
                            em, sm, ea, sa, retMins, false
                    );
                } else if ("PRESENT".equals(codeStatut) && datePtg != null && totalH > 0) {
                    if (CalendrierOuvrableEngine.isWeekend(datePtg)) {
                        codeStatut = (datePtg.getDayOfWeek() == java.time.DayOfWeek.SATURDAY)
                                ? "SAMEDI_TRAVAILLE" : "DIMANCHE_TRAVAILLE";
                    }
                }
                if ("RETARD".equals(codeStatut)) {
                    codeStatut = "PRESENT";
                }

                // 2. Contrôle P0-5 : Concurrence & Optimistic Locking
                psCheckRow.setInt(1, dto.getIdEmploye());
                psCheckRow.setDate(2, Date.valueOf(dto.getDatePointage()));
                Integer existingIdPointage = null;
                Integer currentDbVersion = null;
                try (ResultSet rsCheck = psCheckRow.executeQuery()) {
                    if (rsCheck.next()) {
                        existingIdPointage = rsCheck.getInt("id_pointage");
                        currentDbVersion = rsCheck.getInt("version");
                    }
                }

                int idPtg;
                if (existingIdPointage != null) {
                    if (dto.getVersion() == null) {
                        throw new IllegalStateException("Le champ 'version' est obligatoire pour modifier le pointage existant du "
                                + dto.getDatePointage() + " pour l'employé ID " + dto.getIdEmploye() + ".");
                    }
                    if (!dto.getVersion().equals(currentDbVersion)) {
                        throw new IllegalStateException("Conflit de modification concurrente sur le pointage du " + dto.getDatePointage()
                                + " pour l'employé ID " + dto.getIdEmploye()
                                + " (version attendue: " + currentDbVersion + ", version fournie: " + dto.getVersion() + "). Veuillez recharger la grille.");
                    }

                    psUp.setTime(1, em);
                    psUp.setTime(2, sm);
                    psUp.setTime(3, ea);
                    psUp.setTime(4, sa);
                    psUp.setDouble(5, hMatin);
                    psUp.setDouble(6, hAm);
                    psUp.setDouble(7, totalH);
                    psUp.setInt(8, retMins);
                    psUp.setDouble(9, hNuit);
                    psUp.setString(10, dto.getMotifAjustement());
                    psUp.setString(11, codeStatut);
                    psUp.setBoolean(12, depM);
                    psUp.setBoolean(13, depA);
                    psUp.setBoolean(14, depRecue);
                    psUp.setBoolean(15, dto.isIndemniteRepasRecue());
                    psUp.setDouble(16, mntRepasJour);
                    psUp.setInt(17, existingIdPointage);
                    psUp.setInt(18, dto.getVersion());

                    try (ResultSet rsUp = psUp.executeQuery()) {
                        if (rsUp.next()) {
                            idPtg = rsUp.getInt("id_pointage");
                        } else {
                            throw new IllegalStateException("Conflit de modification concurrente sur le pointage du " + dto.getDatePointage()
                                    + " pour l'employé ID " + dto.getIdEmploye() + ". Les données ont été modifiées par un autre utilisateur.");
                        }
                    }
                } else {
                    psIns.setInt(1, dto.getIdEmploye());
                    psIns.setDate(2, Date.valueOf(dto.getDatePointage()));
                    psIns.setObject(3, idPer);
                    psIns.setInt(4, dto.getIdSite());
                    psIns.setTime(5, em);
                    psIns.setTime(6, sm);
                    psIns.setTime(7, ea);
                    psIns.setTime(8, sa);
                    psIns.setDouble(9, hMatin);
                    psIns.setDouble(10, hAm);
                    psIns.setDouble(11, totalH);
                    psIns.setInt(12, retMins);
                    psIns.setDouble(13, hNuit);
                    psIns.setString(14, dto.getMotifAjustement());
                    psIns.setString(15, codeStatut);
                    psIns.setBoolean(16, depM);
                    psIns.setBoolean(17, depA);
                    psIns.setBoolean(18, depRecue);
                    psIns.setBoolean(19, dto.isIndemniteRepasRecue());
                    psIns.setDouble(20, mntRepasJour);

                    try (ResultSet rsIns = psIns.executeQuery()) {
                        if (rsIns.next()) {
                            idPtg = rsIns.getInt("id_pointage");
                        } else {
                            throw new SQLException("Échec de l'insertion du pointage.");
                        }
                    } catch (SQLException eIns) {
                        if (eIns.getMessage() != null && (eIns.getMessage().toLowerCase().contains("duplicate") || eIns.getMessage().toLowerCase().contains("unique"))) {
                            throw new IllegalStateException("Conflit de modification concurrente : le pointage a été créé en parallèle.");
                        }
                        throw eIns;
                    }
                }

                // Synchronisation systématique dans la table dynamique pointage_indemnite_jour
                Integer idIndRepas = idIndemniteByCode.get("REPAS");
                if (idIndRepas != null) {
                    psDyn.setInt(1, idPtg);
                    psDyn.setInt(2, idIndRepas);
                    psDyn.setDouble(3, 1.0);
                    psDyn.setDouble(4, mntRepasJour);
                    psDyn.setBoolean(5, dto.isIndemniteRepasRecue());
                    psDyn.executeUpdate();
                }

                Integer idIndDep = idIndemniteByCode.get("DEPLACEMENT");
                if (idIndDep == null) idIndDep = idIndemniteByCode.get("TRANSPORT");
                if (idIndDep != null) {
                    double qteDep = (depM ? 0.5 : 0.0) + (depA ? 0.5 : 0.0);
                    psDyn.setInt(1, idPtg);
                    psDyn.setInt(2, idIndDep);
                    psDyn.setDouble(3, qteDep);
                    psDyn.setDouble(4, 0.0);
                    psDyn.setBoolean(5, depRecue);
                    psDyn.executeUpdate();
                }

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
        result.put("success", true);
        result.put("message", "Pointages enregistrés avec succès.");
        return result;
    }
}
