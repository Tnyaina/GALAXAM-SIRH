package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import mg.galaxam.sirh.model.paie.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceCalculator;
import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceContext;
import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceResult;
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
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
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
    private DailyAttendanceCalculator dailyAttendanceCalculator;

    public PointageSaisieService() {
        this.calendrierEngine = new CalendrierOuvrableEngine();
        this.dailyAttendanceCalculator = new DailyAttendanceCalculator();
    }

    public PointageSaisieService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.calendrierEngine = new CalendrierOuvrableEngine();
        this.dailyAttendanceCalculator = new DailyAttendanceCalculator();
    }

    public PointageSaisieService(DataSource dataSource,
                                 mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine calendrierEngine,
                                 mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine ignored) {
        this.dataSource = dataSource;
        this.calendrierEngine = ignored != null ? ignored : new CalendrierOuvrableEngine();
        this.dailyAttendanceCalculator = new DailyAttendanceCalculator();
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

    private static LocalTime toLocalTime(Time value) {
        return value == null ? null : value.toLocalTime();
    }

    /** Conversion de frontière uniquement : le domaine conserve les minutes entières. */
    private static double minutesToStorageHours(long minutes) {
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static DailyAttendanceContext.LeavePart parseLeavePart(String value) {
        if (value == null || value.isBlank()) return DailyAttendanceContext.LeavePart.NONE;
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "FULL_DAY" -> DailyAttendanceContext.LeavePart.FULL_DAY;
            case "MATIN" -> DailyAttendanceContext.LeavePart.MORNING;
            case "APRES_MIDI" -> DailyAttendanceContext.LeavePart.AFTERNOON;
            default -> DailyAttendanceContext.LeavePart.NONE;
        };
    }

    private static String toPersistenceStatus(DailyAttendanceResult result, SaisiePointageJourDTO dto) {
        return switch (result.status()) {
            case PRESENT -> "PRESENT";
            case HALF_DAY -> "DEMI_JOURNEE";
            case ABSENT -> "ABSENCE_NON_JUSTIFIEE";
            case LEAVE -> !dto.isCongePaye() ? "ABSENCE_JUSTIFIEE"
                    : (!dto.isCongeDecompteSolde() ? "DISPENSE_POINTAGE" : "CONGE");
            case REST -> "REPOS";
            case PUBLIC_HOLIDAY_OFF -> "FERIE_CHOME";
            case PUBLIC_HOLIDAY_WORKED -> "FERIE_TRAVAILLE";
            case SUNDAY_WORKED -> "DIMANCHE_TRAVAILLE";
            case AUTONOMOUS -> "DISPENSE_POINTAGE";
            case ANOMALY -> "ANOMALIE_SAISIE";
        };
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
                            ouvrables = calendrierEngine.compterJoursOuvrables(dDeb.toLocalDate(), dFin.toLocalDate(), idSite > 0 ? idSite : null, c);
                        }
                        periodeInfo.put("nbJoursOuvrables", ouvrables > 0 ? ouvrables : null);
                    }
                }
            }
            result.put("periodeInfo", periodeInfo);

            // 2. Vérification du Jour Férié Chômé / Période chômée via le moteur canonique
            CalendrierOuvrableEngine.JourChomeInfo chomeInfo = calendrierEngine.getJourChomeInfo(date != null ? date.toLocalDate() : null, idSite > 0 ? idSite : null, c);
            boolean isFerie = chomeInfo.estChome();
            String libelleFerie = chomeInfo.libelle();
            result.put("isFerie", isFerie);
            result.put("libelleFerie", libelleFerie);

            // 3. Paramètres Horaires du Site (directement depuis ref_site)
            // P1-03 : idSite est OBLIGATOIRE. Aucun fallback LIMIT 1 n'est toléré.
            // Le site représente la ressource ; la configuration ne peut pas être devinée.
            if (idSite <= 0) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                        "L'identifiant du site est obligatoire pour la saisie de pointage. "
                        + "Un site non défini (idSite=" + idSite + ") ne peut pas être résolu.");
            }
            Map<String, Object> siteParams = new HashMap<>();
            String sqlSite = "SELECT s.id_site, s.code_site, s.libelle, "
                    + "s.heure_debut_matin, s.heure_fin_matin, s.heure_debut_apres_midi, "
                    + "s.heure_fin_apres_midi, s.horaire_continu "
                    + "FROM ref_site s WHERE s.id_site = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlSite)) {
                ps.setInt(1, idSite);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                                mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                                "Site introuvable (idSite=" + idSite + ").");
                    }
                    siteParams.put("idSite", rs.getInt("id_site"));
                    siteParams.put("codeSite", rs.getString("code_site"));
                    siteParams.put("libelle", rs.getString("libelle"));
                    Time hdm = rs.getTime("heure_debut_matin");
                    Time hfm = rs.getTime("heure_fin_matin");
                    Time hda = rs.getTime("heure_debut_apres_midi");
                    Time hfa = rs.getTime("heure_fin_apres_midi");
                    // P1-03 : pas de COALESCE côté Java non plus. null = non configuré.
                    boolean continu = rs.getBoolean("horaire_continu");
                    boolean continuu = !rs.wasNull() && continu;

                    siteParams.put("horaireContinu", continuu);
                    siteParams.put("heureDebutMatin", hdm != null ? hdm.toString().substring(0, 5) : "08:00");
                    siteParams.put("heureFinMatin", hfm != null ? hfm.toString().substring(0, 5) : "12:00");
                    siteParams.put("heureDebutAm", hda != null ? hda.toString().substring(0, 5) : (continuu ? "13:00" : "14:00"));
                    siteParams.put("heureFinAm", hfa != null ? hfa.toString().substring(0, 5) : "17:00");
                    siteParams.put("siteDeplacementActif", true);
                    siteParams.put("siteRepasActif", true);
                }
            }

            // 4. Indemnités Spécifiques du Site (site_indemnite_config) avec Prorata sur jours ouvrables réels
            int nbOuvrablesPeriode = (periodeInfo != null && periodeInfo.get("nbJoursOuvrables") != null)
                    ? (int) periodeInfo.get("nbJoursOuvrables") : 0;

            // Chargement anticipé des paramètres globaux pour résoudre MONTANT_REPAS_MIDI
            // (nécessaire dès la construction de siteIndemnites pour les sites à tarif repas = 0 comme TMM)
            Map<String, String> globalParams = ParametreGlobal.loadActiveMap(c);
            BigDecimal mntRepasMidiGlobal = ParametreGlobal.getBigDecimal(globalParams, "MONTANT_REPAS_MIDI", new BigDecimal("2000"));

            BigDecimal montantDeplacementMois = BigDecimal.ZERO;
            BigDecimal montantDeplacementJour = BigDecimal.ZERO;
            BigDecimal montantRepasSite = BigDecimal.ZERO;
            boolean siteRepasInclusParDefaut = false;

            List<Map<String, Object>> siteIndemnites = new ArrayList<>();
            String sqlSiteInd = "SELECT c.id_indemnite, i.code, i.libelle, COALESCE(i.fractionnable, FALSE) AS fractionnable, c.periodicite, c.montant_reference "
                    + "FROM site_indemnite_config c JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite "
                    + "WHERE c.id_site = ? AND c.est_actif = TRUE AND c.periodicite != 'FIXE_MENSUEL' ORDER BY i.libelle";
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
                        BigDecimal montantRef = zeroIfNull(rsI.getBigDecimal("montant_reference"));

                        boolean isProratise = "MENSUEL".equalsIgnoreCase(period) || "MENSUEL_PRORATISE".equalsIgnoreCase(period);
                        BigDecimal montantJour = isProratise
                                ? calendrierEngine.calculerProrataJournalier(montantRef, nbOuvrablesPeriode)
                                : montantRef;
                        BigDecimal montantTrajet = fractionnable ? calendrierEngine.calculerMontantTrajet(montantJour) : montantJour;

                        // Pour les indemnités REPAS avec montantRef = 0 (ex: TMM), le montant affiché
                        // quand la case est cochée manuellement doit être le montant global MONTANT_REPAS_MIDI.
                        boolean isRepasZero = "REPAS".equalsIgnoreCase(codeInd) && montantRef.signum() <= 0;
                        BigDecimal montantGlobal = isRepasZero ? mntRepasMidiGlobal : montantJour;

                        String sousTitre;
                        if (isRepasZero) {
                            sousTitre = "Non inclus site (" + String.format(Locale.FRENCH, "%,d", Math.round(montantGlobal.doubleValue())) + " Ar/j si coché)";
                        } else if (isProratise) {
                            sousTitre = "prorata (" + String.format(Locale.FRENCH, "%,d", Math.round(montantJour.doubleValue())) + " Ar/j)";
                        } else {
                            sousTitre = String.format(Locale.FRENCH, "%,d", Math.round(montantJour.doubleValue())) + " Ar/j";
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
                            siteRepasInclusParDefaut = montantRef.signum() > 0;
                        }
                    }
                }
            }
            result.put("siteIndemnites", siteIndemnites);

            siteParams.put("nbJoursOuvrablesPeriode", nbOuvrablesPeriode);
            siteParams.put("montantDeplacementMois", montantDeplacementMois);
            siteParams.put("montantDeplacementJour", montantDeplacementJour);
            siteParams.put("montantDeplacementTrajet", calendrierEngine.calculerMontantTrajet(montantDeplacementJour));
            siteParams.put("montantRepasJour", siteRepasInclusParDefaut ? montantRepasSite : mntRepasMidiGlobal);
            siteParams.put("montantRepasGlobal", mntRepasMidiGlobal);
            siteParams.put("siteRepasInclusParDefaut", siteRepasInclusParDefaut);
            result.put("siteParams", siteParams);

            // 5. Salariés rattachés au site pour la date demandée (Logique métier en code Java)
            LocalDate targetDate = date.toLocalDate();

            // A. Salariés ayant déjà un pointage pour cette date
            Set<Integer> existingPtgEmpIds = new HashSet<>();
            String sqlPtgIds = (idSite > 0)
                ? "SELECT DISTINCT id_employe FROM pointage_jour WHERE id_site = ? AND date_pointage = ?"
                : "SELECT DISTINCT id_employe FROM pointage_jour WHERE date_pointage = ?";
            try (PreparedStatement psPtgIds = c.prepareStatement(sqlPtgIds)) {
                if (idSite > 0) {
                    psPtgIds.setInt(1, idSite);
                    psPtgIds.setDate(2, date);
                } else {
                    psPtgIds.setDate(1, date);
                }
                try (ResultSet rsPtgIds = psPtgIds.executeQuery()) {
                    while (rsPtgIds.next()) {
                        existingPtgEmpIds.add(rsPtgIds.getInt("id_employe"));
                    }
                }
            }

            // B. Dates d'embauche et de fin de contrat de tous les salariés
            Map<Integer, LocalDate> embaucheMap = new HashMap<>();
            Map<Integer, LocalDate> departMap = new HashMap<>();
            Map<Integer, Boolean> contratOuvertMap = new HashMap<>();
            String sqlCtAll = "SELECT ct.id_employe, ct.date_debut, ct.date_fin, rsc.code AS code_statut " +
                              "FROM contrat_travail ct " +
                              "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                              "ORDER BY ct.date_debut ASC";
            try (PreparedStatement psCtAll = c.prepareStatement(sqlCtAll);
                 ResultSet rsCtAll = psCtAll.executeQuery()) {
                while (rsCtAll.next()) {
                    int empId = rsCtAll.getInt("id_employe");
                    Date dDeb = rsCtAll.getDate("date_debut");
                    Date dFin = rsCtAll.getDate("date_fin");
                    String st = rsCtAll.getString("code_statut");

                    if (dDeb != null) {
                        LocalDate ldDeb = dDeb.toLocalDate();
                        LocalDate currentMin = embaucheMap.get(empId);
                        if (currentMin == null || ldDeb.isBefore(currentMin)) {
                            embaucheMap.put(empId, ldDeb);
                        }
                    }
                    if (dFin == null || "ACTIF".equalsIgnoreCase(st)) {
                        contratOuvertMap.put(empId, true);
                    } else {
                        LocalDate ldFin = dFin.toLocalDate();
                        LocalDate currentMax = departMap.get(empId);
                        if (currentMax == null || ldFin.isAfter(currentMax)) {
                            departMap.put(empId, ldFin);
                        }
                    }
                }
            }

            // Chargement de la liste des noms de site pour affichage direct
            Map<Integer, String> allSiteNoms = new HashMap<>();
            try (PreparedStatement psSites = c.prepareStatement("SELECT id_site, libelle FROM ref_site");
                 ResultSet rsSites = psSites.executeQuery()) {
                while (rsSites.next()) {
                    allSiteNoms.put(rsSites.getInt("id_site"), rsSites.getString("libelle"));
                }
            }

            // C. Salariés actifs et leurs affectations de carrière
            Map<Integer, EmployeCandidatSite> empInfoMap = new LinkedHashMap<>();
            Map<Integer, List<AffCandidat>> affMap = new HashMap<>();

            String sqlEmpAff = "SELECT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                               "a.id_affectation, a.id_site, a.date_debut AS aff_debut, a.date_fin AS aff_fin, " +
                               "rsa.code AS code_statut_aff, " +
                               "COALESCE(cp.code, '') AS code_categorie, " +
                               "COALESCE(f.dispense_pointage, FALSE) AS dispense_pointage " +
                               "FROM employe e " +
                               "LEFT JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                               "LEFT JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh " +
                               "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                               "LEFT JOIN ref_categorie_professionnelle cp ON a.id_categorie_professionnelle = cp.id_categorie_professionnelle " +
                               "WHERE e.est_actif = TRUE " +
                               "ORDER BY e.nom, e.prenom, a.date_debut ASC, a.id_affectation ASC";

            try (PreparedStatement psEA = c.prepareStatement(sqlEmpAff);
                 ResultSet rsEA = psEA.executeQuery()) {
                while (rsEA.next()) {
                    int idEmp = rsEA.getInt("id_employe");
                    if (!empInfoMap.containsKey(idEmp)) {
                        EmployeCandidatSite cnd = new EmployeCandidatSite();
                        cnd.idEmploye = idEmp;
                        cnd.matricule = rsEA.getString("matricule");
                        cnd.nom = rsEA.getString("nom");
                        cnd.prenom = rsEA.getString("prenom");
                        cnd.photoUrl = rsEA.getString("photo_url");
                        String codeCat = rsEA.getString("code_categorie");
                        cnd.codeCategorie = codeCat;
                        cnd.isCadre = PointageSyntheseService.isCadreCategorie(codeCat);
                        empInfoMap.put(idEmp, cnd);
                    }

                    int affSite = rsEA.getInt("id_site");
                    if (affSite > 0) {
                        AffCandidat aff = new AffCandidat();
                        aff.idAffectation = rsEA.getInt("id_affectation");
                        aff.idSite = affSite;
                        Date ad = rsEA.getDate("aff_debut");
                        Date af = rsEA.getDate("aff_fin");
                        aff.dateDebut = ad != null ? ad.toLocalDate() : null;
                        aff.dateFin = af != null ? af.toLocalDate() : null;
                        aff.codeStatut = rsEA.getString("code_statut_aff");
                        aff.dispensePointage = rsEA.getBoolean("dispense_pointage");
                        affMap.computeIfAbsent(idEmp, k -> new ArrayList<>()).add(aff);
                    }
                }
            }

            // D. Filtrage métier en Java des salariés retenus sur ce site
            List<EmployeCandidatSite> eligibleEmployees = new ArrayList<>();

            for (EmployeCandidatSite emp : empInfoMap.values()) {
                int idEmp = emp.idEmploye;
                boolean hasPtg = existingPtgEmpIds.contains(idEmp);
                List<AffCandidat> affs = affMap.getOrDefault(idEmp, Collections.emptyList());

                // 1. Filtrage sur date d'embauche et départ en Java
                LocalDate dateEmb = embaucheMap.get(idEmp);
                if (dateEmb == null && !affs.isEmpty()) {
                    for (AffCandidat a : affs) {
                        if (a.dateDebut != null && (dateEmb == null || a.dateDebut.isBefore(dateEmb))) {
                            dateEmb = a.dateDebut;
                        }
                    }
                }

                if (!hasPtg) {
                    if (dateEmb != null && targetDate.isBefore(dateEmb)) {
                        continue; // Salarié pas encore embauché à cette date
                    }
                    boolean isOpen = contratOuvertMap.getOrDefault(idEmp, false);
                    LocalDate dateDep = departMap.get(idEmp);
                    if (!isOpen && dateDep != null && targetDate.isAfter(dateDep)) {
                        continue; // Salarié ayant déjà quitté l'entreprise
                    }
                }

                // 2. Détermination canonique de l'affectation active du moment par rapport à la date cible
                AffCandidat affApplicable = trouverAffectationApplicable(affs, targetDate);
                if (affApplicable == null && !hasPtg) {
                    continue; // Aucune affectation valide pour ce salarié
                }

                int empSiteId = affApplicable != null ? affApplicable.idSite : 0;
                boolean isAutonome = affApplicable != null && affApplicable.dispensePointage;

                // 3. Vérification du rattachement au site demandé
                boolean rattachAuSite = false;
                if (idSite <= 0) {
                    // Vue tous les sites : le salarié est rattaché à son site effectif du jour
                    rattachAuSite = true;
                } else if (hasPtg) {
                    // Un pointage est déjà enregistré pour ce salarié sur ce site précis
                    rattachAuSite = true;
                } else if (empSiteId == idSite) {
                    // L'affectation active à cette date correspond au site demandé
                    rattachAuSite = true;
                }

                if (!rattachAuSite) {
                    continue; // Non éligible sur ce site pour ce jour
                }

                int finalSiteId = (idSite > 0) ? idSite : empSiteId;
                emp.idSite = finalSiteId;
                emp.nomSite = allSiteNoms.getOrDefault(finalSiteId, "Site #" + finalSiteId);
                emp.isAutonome = isAutonome;
                eligibleEmployees.add(emp);
            }

            List<SaisiePointageJourDTO> grille = new ArrayList<>();
            for (EmployeCandidatSite empCand : eligibleEmployees) {
                int idEmp = empCand.idEmploye;
                boolean isAutonome = empCand.isAutonome;

                SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                dto.setIdEmploye(idEmp);
                dto.setMatricule(empCand.matricule);
                dto.setNom(empCand.nom);
                dto.setPrenom(empCand.prenom);
                dto.setPhotoUrl(empCand.photoUrl);
                dto.setDatePointage(dateStr);
                dto.setIdSite(empCand.idSite);
                dto.setSiteNom(empCand.nomSite);
                dto.setIdPeriodePaie(idPeriode);
                dto.setEstAutonome(isAutonome);
                dto.setEstCadre(empCand.isCadre);
                dto.setCodeCategorie(empCand.codeCategorie);

                // Profil Backend-Driven : pour un rendu direct en front sans logique conditionnelle
                if (isAutonome && empCand.isCadre) {
                    dto.setBadgeProfilCode("CADRE_AUTO");
                    dto.setBadgeProfilLabel("CADRE AUTO");
                    dto.setBadgeProfilVariant("purple");
                    dto.setBadgeProfilTooltip("Cadre autonome (forfait sans HS payees)");
                } else if (isAutonome) {
                    dto.setBadgeProfilCode("AUTONOME");
                    dto.setBadgeProfilLabel("AUTONOME");
                    dto.setBadgeProfilVariant("brand");
                    dto.setBadgeProfilTooltip("Autonome dispense de pointage");
                } else if (empCand.isCadre) {
                    dto.setBadgeProfilCode("CADRE");
                    dto.setBadgeProfilLabel("CADRE");
                    dto.setBadgeProfilVariant("purple");
                    dto.setBadgeProfilTooltip("Cadre soumis au badgeage");
                }

                dto.setFerieJour(isFerie);
                dto.setLibelleFerie(libelleFerie);

                        // Congé approuvé ce jour
                        String sqlConge = "SELECT tc.libelle, tc.code AS type_code, COALESCE(dc.est_remunere, tc.est_paye) AS est_paye, " +
                                          "tc.decompte_solde_acquis, " +
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
                                        boolean decompteSolde = rsC.getBoolean("decompte_solde_acquis");
                                        String baseLib = rsC.getString("libelle");

                                        dto.setCongePaye(estPayeConge);
                                        dto.setCongeDecompteSolde(decompteSolde);

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
                                        "p.montant_total_indemnites, p.horaire_continu_jour " +
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
                                    dto.setHoraireContinu(Boolean.TRUE.equals(rsPtg.getObject("horaire_continu_jour")));

                                    boolean depM = false;
                                    boolean depA = false;
                                    boolean depRecue = false;
                                    BigDecimal mntDepDb = BigDecimal.ZERO;
                                    boolean repasRecue = false;
                                    BigDecimal mntRepasDb = BigDecimal.ZERO;

                                    try (PreparedStatement psInd = c.prepareStatement(
                                            "SELECT ri.code, pij.fraction, pij.quantite, pij.montant_calcule, pij.accorde " +
                                            "FROM pointage_indemnite_jour pij " +
                                            "JOIN ref_indemnite ri ON pij.id_indemnite = ri.id_indemnite " +
                                            "WHERE pij.id_pointage = ?")) {
                                        psInd.setInt(1, rsPtg.getInt("id_pointage"));
                                        try (ResultSet rsInd = psInd.executeQuery()) {
                                            while (rsInd.next()) {
                                                String codeInd = rsInd.getString("code");
                                                boolean accorde = rsInd.getBoolean("accorde");
                                                BigDecimal mntCalc = zeroIfNull(rsInd.getBigDecimal("montant_calcule"));
                                                String fraction = rsInd.getString("fraction");
                                                BigDecimal qte = zeroIfNull(rsInd.getBigDecimal("quantite"));

                                                if ("REPAS".equalsIgnoreCase(codeInd)) {
                                                    repasRecue = accorde;
                                                    mntRepasDb = mntCalc;
                                                } else if ("DEPLACEMENT".equalsIgnoreCase(codeInd) || "TRANSPORT".equalsIgnoreCase(codeInd)) {
                                                    depRecue = accorde;
                                                    mntDepDb = mntCalc;
                                                    if (accorde) {
                                                        if ("MATIN".equalsIgnoreCase(fraction)) {
                                                            depM = true;
                                                            depA = false;
                                                        } else if ("APRES_MIDI".equalsIgnoreCase(fraction)) {
                                                            depM = false;
                                                            depA = true;
                                                        } else {
                                                            depM = qte.signum() > 0;
                                                            depA = qte.compareTo(BigDecimal.ONE) >= 0;
                                                        }
                                                    } else {
                                                        depM = false;
                                                        depA = false;
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    dto.setDeplacementMatin(depM);
                                    dto.setDeplacementAm(depA);
                                    dto.setIndemniteDeplacementRecue(depRecue);

                                    BigDecimal fractionDep = (depM ? new BigDecimal("0.5") : BigDecimal.ZERO)
                                            .add(depA ? new BigDecimal("0.5") : BigDecimal.ZERO);
                                    dto.setMontantDeplacement(mntDepDb.signum() > 0 ? mntDepDb
                                            : (depRecue ? MoneyRoundingPolicy.amount(montantDeplacementJour.multiply(fractionDep)) : BigDecimal.ZERO));

                                    dto.setIndemniteRepasRecue(repasRecue);
                                    dto.setMontantRepas(repasRecue
                                            ? (mntRepasDb.signum() > 0 ? mntRepasDb : (siteRepasInclusParDefaut ? montantRepasSite : mntRepasMidiGlobal))
                                            : BigDecimal.ZERO);
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
                                dto.setMinutesRetard(0);
                                if (!dto.isCongePaye()) {
                                    dto.setTotalHeures(0.0);
                                    dto.setCodeStatut("ABSENCE_JUSTIFIEE");
                                    dto.setLibelleStatut(libelleConge != null ? libelleConge : "Absence autorisée (Sans solde)");
                                } else if (dto.isCongeDecompteSolde()) {
                                    dto.setTotalHeures(0.0);
                                    dto.setCodeStatut("CONGE");
                                    dto.setLibelleStatut(libelleConge != null ? libelleConge : "Congé annuel");
                                } else {
                                    dto.setTotalHeures(8.0);
                                    dto.setCodeStatut("DISPENSE_POINTAGE");
                                    dto.setLibelleStatut(libelleConge != null ? libelleConge : "Absence autorisée payée");
                                }
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
                                dto.setLibelleStatut(isAutonome ? "Autonome (4h)" : "Congé Matin");
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
                                dto.setLibelleStatut(isAutonome ? "Autonome (4h)" : "Congé Après-midi");
                                dto.setDeplacementMatin(true);
                                dto.setDeplacementAm(false);
                                dto.setIndemniteRepasRecue(siteRepasInclusParDefaut);
                            } else if (CalendrierOuvrableEngine.isWeekend(targetDate)) {
                                // Week-end sans pointage existant : repos de fin de semaine par défaut pour tous (y compris autonome)
                                dto.setHeureEntreeMatin("");
                                dto.setHeureSortieMatin("");
                                dto.setHeureEntreeAm("");
                                dto.setHeureSortieAm("");
                                dto.setTotalHeures(0.0);
                                dto.setMinutesRetard(0);
                                dto.setCodeStatut("REPOS");
                                dto.setLibelleStatut("Repos de fin de semaine");
                                dto.setDeplacementMatin(false);
                                dto.setDeplacementAm(false);
                                dto.setIndemniteDeplacementRecue(false);
                                dto.setMontantDeplacement(BigDecimal.ZERO);
                                dto.setIndemniteRepasRecue(false);
                                dto.setMontantRepas(BigDecimal.ZERO);
                            } else if (isAutonome) {
                                dto.setHeureEntreeMatin((String) siteParams.get("heureDebutMatin"));
                                dto.setHeureSortieMatin((String) siteParams.get("heureFinMatin"));
                                dto.setHeureEntreeAm((String) siteParams.get("heureDebutAm"));
                                dto.setHeureSortieAm((String) siteParams.get("heureFinAm"));
                                dto.setTotalHeures(8.0);
                                dto.setMinutesRetard(0);
                                dto.setCodeStatut("DISPENSE_POINTAGE");
                                dto.setLibelleStatut("Autonome (8h)");
                                dto.setDeplacementMatin(true);
                                dto.setDeplacementAm(true);
                                dto.setIndemniteDeplacementRecue(true);
                                dto.setMontantDeplacement(montantDeplacementJour);
                                dto.setIndemniteRepasRecue(siteRepasInclusParDefaut);
                                dto.setMontantRepas(siteRepasInclusParDefaut
                                        ? (montantRepasSite.signum() > 0 ? montantRepasSite : mntRepasMidiGlobal)
                                        : BigDecimal.ZERO);
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
                                dto.setIndemniteDeplacementRecue(true);
                                dto.setMontantDeplacement(montantDeplacementJour);
                                dto.setIndemniteRepasRecue(siteRepasInclusParDefaut);
                                dto.setMontantRepas(siteRepasInclusParDefaut
                                        ? (montantRepasSite.signum() > 0 ? montantRepasSite : mntRepasMidiGlobal)
                                        : BigDecimal.ZERO);
                            }
                        }

                        // Indemnités dynamiques & Dérogations individuelles
                        Map<String, Object> dynMap = new HashMap<>();

                        if (hasExisting && dto.getIdPointage() != null) {
                            // 1. Charger depuis pointage_indemnite_jour si déjà enregistré
                            String sqlLoadDyn = "SELECT id_indemnite, fraction, accorde, quantite, montant_calcule FROM pointage_indemnite_jour WHERE id_pointage = ?";
                            Map<Integer, Map<String, Object>> savedDyn = new HashMap<>();
                            try (PreparedStatement psDynL = c.prepareStatement(sqlLoadDyn)) {
                                psDynL.setInt(1, dto.getIdPointage());
                                try (ResultSet rsDynL = psDynL.executeQuery()) {
                                    while (rsDynL.next()) {
                                        Map<String, Object> item = new HashMap<>();
                                        boolean acc = rsDynL.getBoolean("accorde");
                                        String frac = rsDynL.getString("fraction");
                                        BigDecimal qte = zeroIfNull(rsDynL.getBigDecimal("quantite"));
                                        item.put("accordee", acc);
                                        item.put("montantApplique", zeroIfNull(rsDynL.getBigDecimal("montant_calcule")));
                                        item.put("fractionJour", qte);
                                        item.put("fraction", frac);
                                        if ("MATIN".equalsIgnoreCase(frac)) {
                                            item.put("matin", acc);
                                            item.put("am", false);
                                        } else if ("APRES_MIDI".equalsIgnoreCase(frac)) {
                                            item.put("matin", false);
                                            item.put("am", acc);
                                        } else {
                                            item.put("matin", acc && qte.signum() > 0);
                                            item.put("am", acc && qte.compareTo(BigDecimal.ONE) >= 0);
                                        }
                                        savedDyn.put(rsDynL.getInt("id_indemnite"), item);
                                    }
                                }
                            }
                            for (Map<String, Object> sInd : siteIndemnites) {
                                int idInd = (int) sInd.get("idIndemnite");
                                String codeInd = (String) sInd.get("code");
                                boolean isDep = "DEPLACEMENT".equalsIgnoreCase(codeInd) || "TRANSPORT".equalsIgnoreCase(codeInd);
                                boolean isRep = "REPAS".equalsIgnoreCase(codeInd);

                                if (savedDyn.containsKey(idInd)) {
                                    Map<String, Object> item = savedDyn.get(idInd);
                                    if (isDep) {
                                        item.put("matin", dto.isDeplacementMatin());
                                        item.put("am", dto.isDeplacementAm());
                                        item.put("accordee", dto.isDeplacementMatin() || dto.isDeplacementAm());
                                    } else if (isRep) {
                                        item.put("accordee", dto.isIndemniteRepasRecue());
                                    }
                                    dynMap.put(String.valueOf(idInd), item);
                                } else {
                                    BigDecimal mntJour = (BigDecimal) sInd.get("montantJour");
                                    BigDecimal mntGlobal = sInd.get("montantGlobal") != null ? (BigDecimal) sInd.get("montantGlobal") : mntJour;
                                    boolean useGlobal = Boolean.TRUE.equals(sInd.get("useGlobalIfZero"));
                                    boolean cocheParDefaut = !Boolean.FALSE.equals(sInd.get("cocheParDefaut"));
                                    boolean acc = !isFerie && !"FULL_DAY".equalsIgnoreCase(typeCongeJour) && cocheParDefaut;

                                    Map<String, Object> item = new HashMap<>();
                                    item.put("accordee", acc);
                                    item.put("montantApplique", useGlobal ? mntGlobal : mntJour);
                                    item.put("fractionJour", acc ? 1.0 : 0.0);
                                    if (isDep) {
                                        item.put("matin", acc && dto.isDeplacementMatin());
                                        item.put("am", acc && dto.isDeplacementAm());
                                    }
                                    dynMap.put(String.valueOf(idInd), item);
                                }
                            }
                        } else {
                            // 2. Calcul par défaut avec vérification des dérogations (exclusions) individuelles
                            Set<Integer> exclusionsIndemnites = new HashSet<>();
                            boolean excluDeplacement = false;
                            String motifExcluDeplacement = null;
                            boolean excluRepas = false;
                            String motifExcluRepas = null;

                            String sqlDerog = "SELECT d.id_indemnite, i.code, d.beneficie, d.motif " +
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
                                        String motif = rsDerog.getString("motif");
                                        if (!beneficie) {
                                            exclusionsIndemnites.add(idIndDerog);
                                            if ("TRANSPORT".equalsIgnoreCase(codeInd) || "DEPLACEMENT".equalsIgnoreCase(codeInd)) {
                                                excluDeplacement = true;
                                                motifExcluDeplacement = motif;
                                            } else if ("REPAS".equalsIgnoreCase(codeInd)) {
                                                excluRepas = true;
                                                motifExcluRepas = motif;
                                            }
                                        }
                                    }
                                }
                            }

                            if (excluDeplacement) {
                                dto.setDeplacementMatin(false);
                                dto.setDeplacementAm(false);
                                dto.setEstDerogeDeplacement(true);
                                dto.setMotifDerogationDeplacement(motifExcluDeplacement != null ? motifExcluDeplacement : "Dérogation transport");
                                dto.setLabelDerogationDeplacement("Vehicule");
                            }
                            if (excluRepas) {
                                dto.setIndemniteRepasRecue(false);
                                dto.setEstDerogeRepas(true);
                                dto.setMotifDerogationRepas(motifExcluRepas != null ? motifExcluRepas : "Dérogation repas");
                                dto.setLabelDerogationRepas("Exclu repas");
                            }

                            for (Map<String, Object> sInd : siteIndemnites) {
                                int idInd = (int) sInd.get("idIndemnite");
                                BigDecimal mntJour = (BigDecimal) sInd.get("montantJour");
                                BigDecimal mntGlobal = sInd.get("montantGlobal") != null ? (BigDecimal) sInd.get("montantGlobal") : mntJour;
                                boolean useGlobal = Boolean.TRUE.equals(sInd.get("useGlobalIfZero"));
                                boolean cocheParDefaut = !Boolean.FALSE.equals(sInd.get("cocheParDefaut"));
                                boolean estExclu = exclusionsIndemnites.contains(idInd);
                                // Si le site a un tarif = 0 (useGlobal), la case n'est pas cochée par défaut
                                // même si l'employé n'est pas en congé/férié.
                                boolean acc = !estExclu && !isFerie && !"FULL_DAY".equalsIgnoreCase(typeCongeJour) && cocheParDefaut;

                                Map<String, Object> item = new HashMap<>();
                                item.put("accordee", acc);
                                // Si coché, on applique le montant global (MONTANT_REPAS_MIDI) si site = 0
                                item.put("montantApplique", useGlobal ? mntGlobal : mntJour);
                                item.put("fractionJour", acc ? 1.0 : 0.0);
                                String codeInd = (String) sInd.get("code");
                                if ("DEPLACEMENT".equalsIgnoreCase(codeInd) || "TRANSPORT".equalsIgnoreCase(codeInd)) {
                                    item.put("matin", acc && dto.isDeplacementMatin());
                                    item.put("am", acc && dto.isDeplacementAm());
                                }
                                dynMap.put(String.valueOf(idInd), item);
                            }
                        }
                        dto.setDynamicIndemnites(dynMap);

                        grille.add(dto);
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
        if (datePointage == null) {
            throw new IllegalArgumentException("La date de pointage est requise.");
        }
        LocalDate dtPtg = datePointage.toLocalDate();

        // 1. Contrôle temporel strict : interdiction absolue de pointage dans le futur (fuseau Madagascar UTC+3)
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Indian/Antananarivo"));
        if (dtPtg.isAfter(today)) {
            throw new IllegalStateException("Action interdite : Impossible d'enregistrer un pointage pour une date future (" + dtPtg + "). La date maximale autorisée est " + today + ".");
        }

        // 2. Vérifier existence et statut actif
        String sqlEmp = "SELECT est_actif FROM employe WHERE id_employe = ?";
        boolean estActif = false;
        try (PreparedStatement ps = c.prepareStatement(sqlEmp)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    estActif = rs.getBoolean("est_actif");
                } else {
                    throw new IllegalStateException("L'employé ID " + idEmploye + " est introuvable.");
                }
            }
        }
        if (!estActif) {
            throw new IllegalStateException("L'employé ID " + idEmploye + " est inactif.");
        }

        // 3. Vérifier date d'embauche en Java
        String sqlCt = "SELECT MIN(date_debut) AS date_embauche FROM contrat_travail WHERE id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlCt)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getDate("date_embauche") != null) {
                    LocalDate dEmb = rs.getDate("date_embauche").toLocalDate();
                    if (dtPtg.isBefore(dEmb)) {
                        throw new IllegalStateException("L'employé ID " + idEmploye + " n'était pas encore embauché le " + datePointage + " (embauche : " + dEmb + ").");
                    }
                }
            }
        }

        // 3. Vérifier affectation au site en Java par rapport à la date du pointage
        String sqlAff = "SELECT a.id_affectation, a.date_debut, a.date_fin, a.id_site, rsa.code AS statut_code, " +
                        "COALESCE(f.dispense_pointage, FALSE) AS dispense_pointage " +
                        "FROM employe_affectation_carriere a " +
                        "LEFT JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh " +
                        "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                        "WHERE a.id_employe = ?";
        List<AffCandidat> affsEmp = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sqlAff)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int sId = rs.getInt("id_site");
                    if (sId > 0) {
                        AffCandidat aff = new AffCandidat();
                        aff.idAffectation = rs.getInt("id_affectation");
                        aff.idSite = sId;
                        Date dDeb = rs.getDate("date_debut");
                        Date dFin = rs.getDate("date_fin");
                        aff.dateDebut = dDeb != null ? dDeb.toLocalDate() : null;
                        aff.dateFin = dFin != null ? dFin.toLocalDate() : null;
                        aff.codeStatut = rs.getString("statut_code");
                        aff.dispensePointage = rs.getBoolean("dispense_pointage");
                        affsEmp.add(aff);
                    }
                }
            }
        }

        boolean rattacheAuSite = false;
        AffCandidat affApplicable = trouverAffectationApplicable(affsEmp, dtPtg);
        if (affApplicable != null && affApplicable.idSite == idSite) {
            rattacheAuSite = true;
        }

        if (!rattacheAuSite) {
            // Vérifier si un pointage existe déjà pour ce salarié sur ce site à cette date
            String sqlPtgEx = "SELECT 1 FROM pointage_jour WHERE id_employe = ? AND date_pointage = ? AND id_site = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlPtgEx)) {
                ps.setInt(1, idEmploye);
                ps.setDate(2, datePointage);
                ps.setInt(3, idSite);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) rattacheAuSite = true;
                }
            }
        }

        if (!rattacheAuSite) {
            throw new IllegalStateException("L'employé ID " + idEmploye + " n'est pas rattaché au site ID " + idSite + " pour la date du " + datePointage + ".");
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

        Map<String, ParametreGlobal.ParametreEtat> etatMap = ParametreGlobal.loadEtatMap(c);
        boolean actif2eRepas1 = ParametreGlobal.isActif(etatMap, "SEUIL_2ND_REPAS_1", true);
        BigDecimal mntRepas1 = actif2eRepas1 ? ParametreGlobal.getBigDecimal(etatMap, "MONTANT_2ND_REPAS_1", new BigDecimal("2000"), BigDecimal.ZERO) : BigDecimal.ZERO;
        String seuilRepas1 = ParametreGlobal.getString(etatMap, "SEUIL_2ND_REPAS_1", "19:30", "23:59");

        boolean actif2eRepas2 = ParametreGlobal.isActif(etatMap, "SEUIL_2ND_REPAS_2", true);
        BigDecimal mntRepas2 = actif2eRepas2 ? ParametreGlobal.getBigDecimal(etatMap, "MONTANT_2ND_REPAS_2", new BigDecimal("4000"), BigDecimal.ZERO) : BigDecimal.ZERO;
        String seuilRepas2 = ParametreGlobal.getString(etatMap, "SEUIL_2ND_REPAS_2", "21:00", "23:59");

        BigDecimal mntRepasMidi = ParametreGlobal.getBigDecimal(etatMap, "MONTANT_REPAS_MIDI", new BigDecimal("2000"), new BigDecimal("2000"));
        String debutNuitStr = ParametreGlobal.getString(etatMap, "SEUIL_NUIT_DEBUT", "22:00", "22:00");
        String finNuitStr = ParametreGlobal.getString(etatMap, "SEUIL_NUIT_FIN", "05:00", "05:00");
        LocalTime debutNuit = LocalTime.parse(debutNuitStr.length() == 5 ? debutNuitStr : debutNuitStr.substring(0, 5));
        LocalTime finNuit = LocalTime.parse(finNuitStr.length() == 5 ? finNuitStr : finNuitStr.substring(0, 5));

        Map<String, Integer> idIndemniteByCode = new HashMap<>();
        try (PreparedStatement psRI = c.prepareStatement("SELECT id_indemnite, code FROM ref_indemnite");
             ResultSet rsRI = psRI.executeQuery()) {
            while (rsRI.next()) {
                idIndemniteByCode.put(rsRI.getString("code"), rsRI.getInt("id_indemnite"));
            }
        }

        String sqlFindPer = "SELECT id_periode_paie, date_debut, date_fin FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
        String sqlPerById = "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?";
        String sqlCheckPer = "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ? FOR SHARE";
        String sqlCheckRow = "SELECT id_pointage, COALESCE(version, 1) AS version FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?";

        String sqlUpdate = "UPDATE pointage_jour SET " +
                "heure_entree_matin = ?, heure_sortie_matin = ?, heure_entree_am = ?, heure_sortie_am = ?, " +
                "heures_matin = ?, heures_am = ?, total_heures = ?, minutes_retard = ?, heures_nuit = ?, " +
                "motif_ajustement = ?, id_statut_jour = COALESCE((SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT')), " +
                "montant_total_indemnites = ?, horaire_continu_jour = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_pointage = ? AND version = ? RETURNING id_pointage, version";

        String sqlInsert = "INSERT INTO pointage_jour (" +
                "id_employe, date_pointage, id_periode_paie, id_site, " +
                "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                "heures_matin, heures_am, total_heures, minutes_retard, heures_nuit, " +
                "motif_ajustement, id_statut_jour, montant_total_indemnites, horaire_continu_jour, version, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                "COALESCE((SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT')), " +
                "?, ?, 1, CURRENT_TIMESTAMP) RETURNING id_pointage, version";

        String sqlDynInd = "INSERT INTO pointage_indemnite_jour (" +
                "id_pointage, id_indemnite, fraction, quantite, montant_calcule, accorde, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id_pointage, id_indemnite, fraction) DO UPDATE SET " +
                "quantite = EXCLUDED.quantite, " +
                "montant_calcule = EXCLUDED.montant_calcule, " +
                "accorde = EXCLUDED.accorde, " +
                "updated_at = CURRENT_TIMESTAMP";

        // Chargement des horaires et plafonds configurés par site
        Map<Integer, LocalTime[]> siteHoraires = new HashMap<>();
        Map<Integer, Boolean> siteHoraireContinuMap = new HashMap<>();
        String sqlSites = "SELECT s.id_site, s.heure_debut_matin, s.heure_fin_matin, s.heure_debut_apres_midi, s.heure_fin_apres_midi, " +
                "COALESCE(s.horaire_continu, TRUE) AS horaire_continu, t.heure_plafond " +
                "FROM ref_site s " +
                "LEFT JOIN ref_tolerance_arrivee_pointage t ON s.id_site = t.id_site";
        try (PreparedStatement psSites = c.prepareStatement(sqlSites);
             ResultSet rsSites = psSites.executeQuery()) {
            while (rsSites.next()) {
                int sId = rsSites.getInt("id_site");
                Time hdm = rsSites.getTime("heure_debut_matin");
                Time hfm = rsSites.getTime("heure_fin_matin");
                Time hda = rsSites.getTime("heure_debut_apres_midi");
                Time hfa = rsSites.getTime("heure_fin_apres_midi");
                Time hp = rsSites.getTime("heure_plafond");
                boolean cont = rsSites.getBoolean("horaire_continu");
                siteHoraireContinuMap.put(sId, cont);

                LocalTime ltDebM = hdm != null ? hdm.toLocalTime() : LocalTime.of(8, 0);
                LocalTime ltFinM = hfm != null ? hfm.toLocalTime() : LocalTime.of(12, 0);
                LocalTime ltDebA = hda != null ? hda.toLocalTime() : (cont ? LocalTime.of(13, 0) : LocalTime.of(14, 0));
                LocalTime ltFinA = hfa != null ? hfa.toLocalTime() : (cont ? LocalTime.of(17, 0) : LocalTime.of(18, 0));
                LocalTime ltPlafond = hp != null ? hp.toLocalTime() : LocalTime.of(7, 30);

                // Index: 0=debM, 1=finM, 2=debA, 3=finA, 4=plafondArrivee
                siteHoraires.put(sId, new LocalTime[]{ltDebM, ltFinM, ltDebA, ltFinA, ltPlafond});
            }
        } catch (Exception ignored) {}

        Map<String, String> paramsGlobauxBatch = ParametreGlobal.loadActiveMap(c);
        BigDecimal reductionDeplactRepasConfig = ParametreGlobal.getBigDecimal(paramsGlobauxBatch, "REDUCTION_DEPLACT_REPAS_MIDI", new BigDecimal("1000"));

        Map<Integer, BigDecimal> siteDeplacementRefMap = new HashMap<>();
        String sqlSiteDep = "SELECT c.id_site, c.montant_reference FROM site_indemnite_config c " +
                            "JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite " +
                            "WHERE i.code IN ('DEPLACEMENT', 'TRANSPORT') AND c.est_actif = TRUE";
        try (PreparedStatement psSD = c.prepareStatement(sqlSiteDep);
             ResultSet rsSD = psSD.executeQuery()) {
            while (rsSD.next()) {
                siteDeplacementRefMap.put(rsSD.getInt("id_site"), zeroIfNull(rsSD.getBigDecimal("montant_reference")));
            }
        } catch (Exception ignored) {}

        Map<Integer, LocalDate[]> periodeBoundsCache = new HashMap<>();
        Map<String, BigDecimal> sitePeriodeDeplacementJourMap = new HashMap<>();

        try (PreparedStatement psFindPer = c.prepareStatement(sqlFindPer);
             PreparedStatement psPerById = c.prepareStatement(sqlPerById);
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
                LocalDate perDebut = null;
                LocalDate perFin = null;

                if (idPer == null || idPer == 0) {
                    psFindPer.setDate(1, dtPtg);
                    try (ResultSet rsF = psFindPer.executeQuery()) {
                        if (rsF.next()) {
                            idPer = rsF.getInt("id_periode_paie");
                            Date d1 = rsF.getDate("date_debut");
                            Date d2 = rsF.getDate("date_fin");
                            if (d1 != null && d2 != null) {
                                perDebut = d1.toLocalDate();
                                perFin = d2.toLocalDate();
                                periodeBoundsCache.put(idPer, new LocalDate[]{perDebut, perFin});
                            }
                        }
                    }
                }

                if (idPer != null && idPer > 0) {
                    psCheckPer.setInt(1, idPer);
                    try (ResultSet rsCP = psCheckPer.executeQuery()) {
                        if (rsCP.next()) {
                            String stCode = rsCP.getString("code");
                            if ("VALIDEE".equalsIgnoreCase(stCode) || "CLOTUREE".equalsIgnoreCase(stCode) || "A_VALIDER".equalsIgnoreCase(stCode) || "TRANSMIS_DAF".equalsIgnoreCase(stCode)) {
                                throw new IllegalStateException("Impossible de modifier les pointages : la période de paie est fermée ou en attente de validation.");
                            }
                        }
                    }

                    if (perDebut == null || perFin == null) {
                        if (periodeBoundsCache.containsKey(idPer)) {
                            LocalDate[] b = periodeBoundsCache.get(idPer);
                            perDebut = b[0];
                            perFin = b[1];
                        } else {
                            psPerById.setInt(1, idPer);
                            try (ResultSet rsPBI = psPerById.executeQuery()) {
                                if (rsPBI.next()) {
                                    Date d1 = rsPBI.getDate("date_debut");
                                    Date d2 = rsPBI.getDate("date_fin");
                                    if (d1 != null && d2 != null) {
                                        perDebut = d1.toLocalDate();
                                        perFin = d2.toLocalDate();
                                        periodeBoundsCache.put(idPer, new LocalDate[]{perDebut, perFin});
                                    }
                                }
                            }
                        }
                    }
                }

                if (perDebut == null || perFin == null) {
                    LocalDate ld = dtPtg.toLocalDate();
                    perDebut = LocalDate.of(ld.getYear(), ld.getMonth(), 1);
                    perFin = LocalDate.of(ld.getYear(), ld.getMonth(), ld.lengthOfMonth());
                }

                String cacheKey = idSiteRow + "_" + (idPer != null ? idPer : (perDebut + "_" + perFin));
                BigDecimal montantDeplacementJour = sitePeriodeDeplacementJourMap.get(cacheKey);
                if (montantDeplacementJour == null) {
                    int jours = calendrierEngine.compterJoursOuvrables(perDebut, perFin, idSiteRow, c);
                    if (jours <= 0) {
                        jours = CalendrierOuvrableEngine.calculerJoursOuvrables(perDebut, perFin);
                    }
                    if (jours <= 0) throw new IllegalStateException("MISSING_WORKING_CALENDAR");
                    BigDecimal refMnt = siteDeplacementRefMap.getOrDefault(idSiteRow, BigDecimal.ZERO);
                    montantDeplacementJour = calendrierEngine.calculerProrataJournalier(refMnt, jours);
                    sitePeriodeDeplacementJourMap.put(cacheKey, montantDeplacementJour);
                }

                LocalTime[] sH = siteHoraires.getOrDefault(idSiteRow, new LocalTime[]{
                        LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(17, 0), LocalTime.of(7, 30)
                });
                LocalTime siteDebM = sH[0];
                LocalTime siteFinM = sH[1];
                LocalTime siteDebA = sH[2];
                LocalTime siteFinA = sH[3];

                Time em = parseTime(dto.getHeureEntreeMatin());
                Time sm = parseTime(dto.getHeureSortieMatin());
                Time ea = parseTime(dto.getHeureEntreeAm());
                Time sa = parseTime(dto.getHeureSortieAm());

                // =========================================================================
                // RÈGLE MÉTIER GALAXAM - Tolérance d'arrivée / Heure minimale (ex: 07h30)
                // Écrêtage de l'arrivée anticipée pour le calcul des heures travaillées effectives.
                // NOTE IMPORTANTE : La valeur brute badgée/saisie (ex: 07:21) reste TOUJOURS
                // stockée telle quelle en base de données pour la traçabilité d'audit.
                //
                // POUR ACTIVER L'ÉCRÊTAGE : Décommenter le bloc ci-dessous le jour où l'entreprise
                // souhaite appliquer le plafond :
                /*
                LocalTime ltPlafondSite = sH.length > 4 ? sH[4] : null;
                LocalTime ltEmEcrete = (em != null && ltPlafondSite != null)
                        ? em.toLocalTime()
                        : (em != null ? em.toLocalTime() : null);
                Time emEffectif = ltEmEcrete != null ? Time.valueOf(ltEmEcrete) : em;
                */
                Time emEffectif = em; // Actuellement désactivé : calcul sur l'heure brute saisie
                // =========================================================================

                boolean isJourneeContinue = dto.isHoraireContinu();
                LocalDate datePtg = null;
                try {
                    if (dto.getDatePointage() != null) datePtg = LocalDate.parse(dto.getDatePointage());
                } catch (Exception ignored) {}
                boolean isWeekendPtg = (datePtg != null && CalendrierOuvrableEngine.isWeekend(datePtg));
                DailyAttendanceContext.LeavePart leavePart = parseLeavePart(dto.getTypeCongeJour());
                DailyAttendanceResult dailyResult = dailyAttendanceCalculator.calculateDay(new DailyAttendanceContext(
                        dto.getIdEmploye(), datePtg,
                        toLocalTime(emEffectif), toLocalTime(sm), toLocalTime(ea), toLocalTime(sa),
                        siteDebM, siteFinM, siteDebA, siteFinA, debutNuit, finNuit,
                        isJourneeContinue, dto.isEstAutonome(), dto.isFerieJour(), leavePart,
                        false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));

                double hMatin = minutesToStorageHours(dailyResult.morningMinutes());
                double hAm = minutesToStorageHours(dailyResult.afternoonMinutes());
                double totalH = minutesToStorageHours(dailyResult.workedMinutes());
                double hNuit = minutesToStorageHours(dailyResult.nightMinutes());
                int retMins = Math.toIntExact(dailyResult.lateMinutes());

                // Calcul dynamique du montant repas (déjeuner + extra soirée) via moteur
                BigDecimal mntRepasJour = BigDecimal.ZERO;
                if (dto.isIndemniteRepasRecue() && !"FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour()) && !dto.isFerieJour()) {
                    mntRepasJour = dailyAttendanceCalculator.calculateMealAmount(
                            true, mntRepasMidi, sa != null ? sa.toLocalTime() : null,
                            LocalTime.parse(seuilRepas1), mntRepas1,
                            LocalTime.parse(seuilRepas2), mntRepas2);
                }

                Integer idIndDep = idIndemniteByCode.get("DEPLACEMENT");
                if (idIndDep == null) idIndDep = idIndemniteByCode.get("TRANSPORT");
                Integer idIndRepas = idIndemniteByCode.get("REPAS");

                boolean depM = dto.isDeplacementMatin();
                boolean depA = dto.isDeplacementAm();
                boolean repasRecue = dto.isIndemniteRepasRecue();

                if (dto.getDynamicIndemnites() != null) {
                    if (idIndDep != null && dto.getDynamicIndemnites().containsKey(String.valueOf(idIndDep))) {
                        Object val = dto.getDynamicIndemnites().get(String.valueOf(idIndDep));
                        if (val instanceof Map<?, ?> m) {
                            if (m.get("matin") != null) depM = Boolean.TRUE.equals(m.get("matin"));
                            if (m.get("am") != null) depA = Boolean.TRUE.equals(m.get("am"));
                        }
                    }
                    if (idIndRepas != null && dto.getDynamicIndemnites().containsKey(String.valueOf(idIndRepas))) {
                        Object val = dto.getDynamicIndemnites().get(String.valueOf(idIndRepas));
                        if (val instanceof Map<?, ?> m) {
                            if (m.get("accordee") != null) repasRecue = Boolean.TRUE.equals(m.get("accordee"));
                        }
                    }
                }
                boolean depRecue = depM || depA;
                dto.setDeplacementMatin(depM);
                dto.setDeplacementAm(depA);
                dto.setIndemniteDeplacementRecue(depRecue);
                dto.setIndemniteRepasRecue(repasRecue);

                String codeStatut = dto.getCodeStatut();
                if (codeStatut == null || codeStatut.isEmpty() || "NON_SAISI".equals(codeStatut)) {
                    codeStatut = toPersistenceStatus(dailyResult, dto);
                    if (datePtg != null && datePtg.getDayOfWeek() == DayOfWeek.SATURDAY
                            && dailyResult.workedMinutes() > 0) {
                        codeStatut = "SAMEDI_TRAVAILLE";
                    }
                } else if (dto.getTypeCongeJour() != null && "FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())) {
                    // Sécurisation stricte côté serveur : si congé non payé, forcer ABSENCE_JUSTIFIEE
                    if (!dto.isCongePaye()) {
                        codeStatut = "ABSENCE_JUSTIFIEE";
                    } else if (!dto.isCongeDecompteSolde() && "CONGE".equalsIgnoreCase(codeStatut)) {
                        codeStatut = "DISPENSE_POINTAGE";
                    }
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

                boolean estNonTravaille = "FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())
                        || (dto.isFerieJour() && totalH <= 0.0)
                        || "CONGE".equalsIgnoreCase(codeStatut)
                        || "FERIE_CHOME".equalsIgnoreCase(codeStatut)
                        || "ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(codeStatut)
                        || "ABSENCE_JUSTIFIEE".equalsIgnoreCase(codeStatut)
                        || "REPOS".equalsIgnoreCase(codeStatut)
                        || (isWeekendPtg && totalH <= 0.0);

                if (estNonTravaille) {
                    depM = false;
                    depA = false;
                    depRecue = false;
                    mntRepasJour = BigDecimal.ZERO;
                }

                boolean siteEstContinu = siteHoraireContinuMap.getOrDefault(idSiteRow, true);
                BigDecimal fractionDep = (depM ? new BigDecimal("0.5") : BigDecimal.ZERO)
                        .add(depA ? new BigDecimal("0.5") : BigDecimal.ZERO);
                boolean doitReduireTransport = (!siteEstContinu && dto.isIndemniteRepasRecue() && mntRepasJour.signum() > 0);
                BigDecimal mntDepJour = MoneyRoundingPolicy.amount(dailyAttendanceCalculator.calculateTravelAmount(
                        fractionDep, montantDeplacementJour,
                        doitReduireTransport, reductionDeplactRepasConfig));

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

                    BigDecimal mntTotalInd = (dto.isIndemniteRepasRecue() ? mntRepasJour : BigDecimal.ZERO)
                            .add(depRecue ? mntDepJour : BigDecimal.ZERO);
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
                    psUp.setBigDecimal(12, mntTotalInd);
                    psUp.setBoolean(13, dto.isHoraireContinu());
                    psUp.setInt(14, existingIdPointage);
                    psUp.setInt(15, dto.getVersion());

                    try (ResultSet rsUp = psUp.executeQuery()) {
                        if (rsUp.next()) {
                            idPtg = rsUp.getInt("id_pointage");
                        } else {
                            throw new IllegalStateException("Conflit de modification concurrente sur le pointage du " + dto.getDatePointage()
                                    + " pour l'employé ID " + dto.getIdEmploye() + ". Les données ont été modifiées par un autre utilisateur.");
                        }
                    }
                } else {
                    // En week-end ou jour férié chômé, on n'insère QUE les salariés réellement pointés (avec heures) ou ayant un congé approuvé
                    // Évite que les autres salariés soient insérés à 0h et marqués absents
                    boolean sansHeures = (em == null && sm == null && ea == null && sa == null && totalH <= 0.0);
                    boolean sansConge = (dto.getTypeCongeJour() == null || dto.getTypeCongeJour().isEmpty());
                    if ((isWeekendPtg || dto.isFerieJour()) && sansHeures && sansConge) {
                        continue;
                    }

                    BigDecimal mntTotalInd = (dto.isIndemniteRepasRecue() ? mntRepasJour : BigDecimal.ZERO)
                            .add(depRecue ? mntDepJour : BigDecimal.ZERO);
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
                    psIns.setBigDecimal(16, mntTotalInd);
                    psIns.setBoolean(17, dto.isHoraireContinu());

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
                if (idIndRepas != null) {
                    boolean repasAccorde = !estNonTravaille && dto.isIndemniteRepasRecue();
                    psDyn.setInt(1, idPtg);
                    psDyn.setInt(2, idIndRepas);
                    psDyn.setString(3, "JOUR_ENTIER");
                    psDyn.setBigDecimal(4, repasAccorde ? BigDecimal.ONE : BigDecimal.ZERO);
                    psDyn.setBigDecimal(5, repasAccorde ? mntRepasJour : BigDecimal.ZERO);
                    psDyn.setBoolean(6, repasAccorde);
                    psDyn.executeUpdate();
                }

                if (idIndDep != null) {
                    boolean depAccorde = !estNonTravaille && depRecue;
                    BigDecimal qteDep = depAccorde
                            ? (depM ? new BigDecimal("0.5") : BigDecimal.ZERO)
                                    .add(depA ? new BigDecimal("0.5") : BigDecimal.ZERO)
                            : BigDecimal.ZERO;
                    BigDecimal mntDep = depAccorde ? mntDepJour : BigDecimal.ZERO;
                    String fracDep = (depM && depA) ? "JOUR_ENTIER" : (depM ? "MATIN" : (depA ? "APRES_MIDI" : "JOUR_ENTIER"));
                    psDyn.setInt(1, idPtg);
                    psDyn.setInt(2, idIndDep);
                    psDyn.setString(3, fracDep);
                    psDyn.setBigDecimal(4, qteDep);
                    psDyn.setBigDecimal(5, mntDep);
                    psDyn.setBoolean(6, depAccorde);
                    psDyn.executeUpdate();
                }

                if (dto.getDynamicIndemnites() != null) {
                    for (Map.Entry<String, Object> entry : dto.getDynamicIndemnites().entrySet()) {
                        int idInd = Integer.parseInt(entry.getKey());
                        // NE PAS écraser REPAS et DEPLACEMENT qui sont gérés avec précision ci-dessus
                        if ((idIndRepas != null && idInd == idIndRepas) || (idIndDep != null && idInd == idIndDep)) {
                            continue;
                        }
                        if (entry.getValue() instanceof Map<?, ?> vals) {
                            boolean acc = !estNonTravaille && Boolean.TRUE.equals(vals.get("accordee"));
                            BigDecimal mnt = estNonTravaille ? BigDecimal.ZERO
                                    : (vals.get("montantApplique") != null ? new BigDecimal(vals.get("montantApplique").toString()) : BigDecimal.ZERO);
                            BigDecimal fracQte = estNonTravaille ? BigDecimal.ZERO
                                    : (vals.get("fractionJour") != null ? new BigDecimal(vals.get("fractionJour").toString()) : (acc ? BigDecimal.ONE : BigDecimal.ZERO));
                            String fracStr = (vals.get("fraction") != null && !vals.get("fraction").toString().isBlank()) ? vals.get("fraction").toString() : "JOUR_ENTIER";

                            psDyn.setInt(1, idPtg);
                            psDyn.setInt(2, idInd);
                            psDyn.setString(3, fracStr);
                            psDyn.setBigDecimal(4, fracQte);
                            psDyn.setBigDecimal(5, mnt);
                            psDyn.setBoolean(6, acc);
                            psDyn.executeUpdate();
                        }
                    }
                }

                // Recalcul du montant_total_indemnites dénormalisé depuis pointage_indemnite_jour
                try (PreparedStatement psUpdateTotal = c.prepareStatement(
                        "UPDATE pointage_jour SET montant_total_indemnites = COALESCE((SELECT SUM(montant_calcule) FROM pointage_indemnite_jour WHERE id_pointage = ? AND accorde = true), 0) WHERE id_pointage = ?")) {
                    psUpdateTotal.setInt(1, idPtg);
                    psUpdateTotal.setInt(2, idPtg);
                    psUpdateTotal.executeUpdate();
                }
            }
        }
        result.put("success", true);
        result.put("message", "Pointages enregistrés avec succès.");
        return result;
    }

    public static class EmployeCandidatSite {
        public int idEmploye;
        public String matricule;
        public String nom;
        public String prenom;
        public String photoUrl;
        public int idSite;
        public String nomSite;
        public boolean isAutonome;
        public boolean isCadre;
        public String codeCategorie;
    }

    public static class AffCandidat {
        public int idAffectation;
        public int idSite;
        public LocalDate dateDebut;
        public LocalDate dateFin;
        public String codeStatut;
        public boolean dispensePointage;
    }

    /**
     * Détermine de manière canonique l'affectation active d'un collaborateur à une date donnée.
     * Gère les transitions successives : si une ancienne affectation a une date_fin NULL,
     * sa date de fin effective est automatiquement déduite comme étant la veille de l'affectation suivante.
     */
    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static AffCandidat trouverAffectationApplicable(List<AffCandidat> affs, LocalDate targetDate) {
        if (affs == null || affs.isEmpty()) return null;

        // 1. Filtrer les statuts non effectifs (annulés, brouillons, rejetés)
        List<AffCandidat> valides = new ArrayList<>();
        for (AffCandidat a : affs) {
            if (a.codeStatut != null && ("ANNULE".equalsIgnoreCase(a.codeStatut)
                    || "BROUILLON".equalsIgnoreCase(a.codeStatut)
                    || "REJETE".equalsIgnoreCase(a.codeStatut))) {
                continue;
            }
            valides.add(a);
        }
        if (valides.isEmpty()) return null;

        // 2. Trier par dateDebut ASC, en plaçant ACTIF en dernier en cas d'égalité, puis par idAffectation ASC
        valides.sort((a, b) -> {
            LocalDate d1 = a.dateDebut != null ? a.dateDebut : LocalDate.MIN;
            LocalDate d2 = b.dateDebut != null ? b.dateDebut : LocalDate.MIN;
            int c = d1.compareTo(d2);
            if (c != 0) return c;
            boolean aActif = "ACTIF".equalsIgnoreCase(a.codeStatut);
            boolean bActif = "ACTIF".equalsIgnoreCase(b.codeStatut);
            if (aActif != bActif) return aActif ? 1 : -1;
            return Integer.compare(a.idAffectation, b.idAffectation);
        });

        // 3. Évaluer les intervalles [dateDebut, dateFinEffective]
        AffCandidat retenue = null;
        for (int i = 0; i < valides.size(); i++) {
            AffCandidat cur = valides.get(i);
            LocalDate start = cur.dateDebut != null ? cur.dateDebut : LocalDate.MIN;
            LocalDate end = cur.dateFin;

            if (end == null && i + 1 < valides.size()) {
                AffCandidat next = valides.get(i + 1);
                if (next.dateDebut != null && !next.dateDebut.isBefore(start)) {
                    end = next.dateDebut.minusDays(1);
                }
            }

            boolean apresDebut = (targetDate == null) || !targetDate.isBefore(start);
            boolean avantFin = (targetDate == null) || (end == null || !targetDate.isAfter(end));

            if (apresDebut && avantFin) {
                retenue = cur;
            }
        }

        // 4. Si aucune affectation ne couvre précisément (ex : targetDate dans le futur par rapport à une affectation clôturée),
        // retenir la plus récente commencée avant targetDate
        if (retenue == null && targetDate != null) {
            for (int i = valides.size() - 1; i >= 0; i--) {
                AffCandidat a = valides.get(i);
                LocalDate start = a.dateDebut != null ? a.dateDebut : LocalDate.MIN;
                if (!targetDate.isBefore(start)) {
                    retenue = a;
                    break;
                }
            }
        }

        // 5. Repli de secours : l'affectation active (ACTIF), sinon la dernière de la liste
        if (retenue == null) {
            for (AffCandidat a : valides) {
                if ("ACTIF".equalsIgnoreCase(a.codeStatut)) {
                    retenue = a;
                    break;
                }
            }
            if (retenue == null && !valides.isEmpty()) {
                retenue = valides.get(valides.size() - 1);
            }
        }

        return retenue;
    }
}
