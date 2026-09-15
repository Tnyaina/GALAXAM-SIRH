package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.api.dto.pointage.PointageTronconSemaineDTO;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import mg.galaxam.sirh.model.paieconfig.RefTauxMajoration;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.model.pointage.mapper.PointageTimeBoundaryMapper;
import mg.galaxam.sirh.model.pointage.calculation.AttendancePeriodCalculator;
import mg.galaxam.sirh.model.pointage.calculation.AttendancePeriodContext;
import mg.galaxam.sirh.model.pointage.calculation.AttendancePeriodResult;
import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceResult;
import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceCalculator;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceCalculator;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceContext;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PointageSyntheseService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PointageSyntheseService.class);

    private static long hoursToMinutes(BigDecimal hours) {
        if (hours == null) return 0;
        return hours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static double minutesToHours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).doubleValue();
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static DailyAttendanceResult.Status statusFromCode(String code) {
        if (code == null) return DailyAttendanceResult.Status.ANOMALY;
        return switch (code) {
            case "CONGE" -> DailyAttendanceResult.Status.LEAVE;
            case "REPOS" -> DailyAttendanceResult.Status.REST;
            case "FERIE_CHOME" -> DailyAttendanceResult.Status.PUBLIC_HOLIDAY_OFF;
            case "FERIE_TRAVAILLE" -> DailyAttendanceResult.Status.PUBLIC_HOLIDAY_WORKED;
            case "DIMANCHE_TRAVAILLE" -> DailyAttendanceResult.Status.SUNDAY_WORKED;
            case "DISPENSE_POINTAGE" -> DailyAttendanceResult.Status.AUTONOMOUS;
            case "DEMI_JOURNEE" -> DailyAttendanceResult.Status.HALF_DAY;
            case "ABSENCE_NON_JUSTIFIEE", "ABSENCE_JUSTIFIEE" -> DailyAttendanceResult.Status.ABSENT;
            case "ANOMALIE_SAISIE" -> DailyAttendanceResult.Status.ANOMALY;
            default -> DailyAttendanceResult.Status.PRESENT;
        };
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine calendrierEngine;

    @Autowired
    private WeeklyAttendanceCalculator weeklyAttendanceCalculator;

    @Autowired
    private AttendancePeriodCalculator attendancePeriodCalculator;

    @Autowired
    private DailyAttendanceCalculator dailyAttendanceCalculator;

    public PointageSyntheseService() {
        this.calendrierEngine = new CalendrierOuvrableEngine();
        this.weeklyAttendanceCalculator = new WeeklyAttendanceCalculator();
        this.attendancePeriodCalculator = new AttendancePeriodCalculator();
        this.dailyAttendanceCalculator = new DailyAttendanceCalculator();
    }

    public PointageSyntheseService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.calendrierEngine = new CalendrierOuvrableEngine();
        this.weeklyAttendanceCalculator = new WeeklyAttendanceCalculator();
        this.attendancePeriodCalculator = new AttendancePeriodCalculator();
        this.dailyAttendanceCalculator = new DailyAttendanceCalculator();
    }

    public PointageSyntheseService(DataSource dataSource, mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine calendrierEngine) {
        this.dataSource = dataSource;
        this.calendrierEngine = calendrierEngine != null ? calendrierEngine : new CalendrierOuvrableEngine();
        this.weeklyAttendanceCalculator = new WeeklyAttendanceCalculator();
        this.attendancePeriodCalculator = new AttendancePeriodCalculator();
        this.dailyAttendanceCalculator = new DailyAttendanceCalculator();
    }

    public static boolean isCadreCategorie(String codeCat) {
        if (codeCat == null) return false;
        String c = codeCat.trim().toUpperCase();
        return c.contains("CADRE") || "C".equals(c) || "HC".equals(c) || "DIRIGEANT".equals(c);
    }

    /**
     * Calcule la synthèse de pointage d'une période en logique métier pure Java
     */
    public List<PointageSyntheseMensuelleDTO> getSynthesePeriode(int idPeriode, Integer idSite) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return getSynthesePeriode(idPeriode, idSite, c);
        }
    }

    public Map<String, Object> getSynthesePeriodePaginee(
            int idPeriode, Integer idSite, String search, String filtre, int page, int size
    ) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return getSynthesePeriodePaginee(idPeriode, idSite, search, filtre, page, size, c);
        }
    }

    /**
     * MÉTHODE MÉTIER UNIQUE & UNIFIÉE : Calcule la synthèse complète pour un employé sur une plage de dates donnée.
     * Réutilisée pour :
     * 1. Fiche Employé Individuelle (Mois ou période arbitraire)
     * 2. Détail Hebdomadaire (Tronçon 40h de la semaine)
     * 3. Synthèse Mensuelle Globale & Clôture DAF
     */
    public PointageSyntheseMensuelleDTO calculerSyntheseEmploye(
            int idEmp,
            String matricule,
            String nom,
            String prenom,
            String photoUrl,
            int idSite,
            String siteCode,
            String siteNom,
            String codeCat,
            boolean isCadre,
            boolean estAutonome,
            LocalDate dateDebut,
            LocalDate dateFin,
            Integer idPeriode,
            String codePeriode,
            String libellePeriode,
            String statutPeriode,
            int nbJoursOuvrablesPeriode,
            Map<Integer, BigDecimal> siteDeplacementRefMap,
            Map<String, String> params,
            Connection c
    ) throws SQLException {
        return calculerSyntheseEmploye(
                idEmp, matricule, nom, prenom, photoUrl, idSite, siteCode, siteNom, null,
                codeCat, isCadre, estAutonome, dateDebut, dateFin, idPeriode, codePeriode,
                libellePeriode, statutPeriode, nbJoursOuvrablesPeriode, siteDeplacementRefMap, params, c
        );
    }

    public PointageSyntheseMensuelleDTO calculerSyntheseEmploye(
            int idEmp,
            String matricule,
            String nom,
            String prenom,
            String photoUrl,
            int idSite,
            String siteCode,
            String siteNom,
            Boolean horaireContinu,
            String codeCat,
            boolean isCadre,
            boolean estAutonome,
            LocalDate dateDebut,
            LocalDate dateFin,
            Integer idPeriode,
            String codePeriode,
            String libellePeriode,
            String statutPeriode,
            int nbJoursOuvrablesPeriode,
            Map<Integer, BigDecimal> siteDeplacementRefMap,
            Map<String, String> params,
            Connection c
    ) throws SQLException {
        if (dateDebut == null || dateFin == null || dateFin.isBefore(dateDebut)) {
            log.warn("[PointageSyntheseService] Dates de période invalides pour l'employé {}: debut={}, fin={}", idEmp, dateDebut, dateFin);
            PointageSyntheseMensuelleDTO emptyDto = new PointageSyntheseMensuelleDTO();
            emptyDto.setIdEmploye(idEmp);
            emptyDto.setIdPeriodePaie(idPeriode);
            return emptyDto;
        }
        int joursOuvrablesReels = calendrierEngine.compterJoursOuvrables(dateDebut, dateFin, idSite, c);
        if (joursOuvrablesReels > 0) nbJoursOuvrablesPeriode = joursOuvrablesReels;
        BigDecimal dureeHebdoLegale = ParametreGlobal.getBigDecimal(params, "SEUIL_HEURES_SEMAINE_COMPENSATION", new BigDecimal("40.0"));
        BigDecimal seuilHsTranche1 = new BigDecimal("8");
        try {
            mg.galaxam.sirh.model.paieconfig.PaieConfigLegale leg = mg.galaxam.sirh.model.paieconfig.PaieConfigLegale.findActiveAt(c, dateDebut != null ? dateDebut : LocalDate.now());
            if (leg != null) {
                if (leg.getSeuilHsTranche1() != null) {
                    seuilHsTranche1 = leg.getSeuilHsTranche1();
                }
                if (leg.getDureeHebdoLegale() != null) {
                    dureeHebdoLegale = leg.getDureeHebdoLegale();
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de charger PaieConfigLegale pour seuilHsTranche1/dureeHebdoLegale, repli : {}", e.getMessage());
        }
        BigDecimal seuilHeuresJour = ParametreGlobal.getBigDecimal(
                params, "SEUIL_HEURES_JOUR", dureeHebdoLegale.divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP));
        Map<String, ParametreGlobal.ParametreEtat> etatMap = ParametreGlobal.loadEtatMapAtDate(dateDebut != null ? Date.valueOf(dateDebut) : null, c);
        // Résolution de l'horaire continu du site (TRUE = TNR / continu, FALSE = TMM / discontinu)
        boolean siteHoraireContinu;
        if (horaireContinu != null) {
            siteHoraireContinu = horaireContinu;
        } else {
            String sqlHC = "SELECT COALESCE(horaire_continu, TRUE) FROM ref_site WHERE id_site = ?";
            try (PreparedStatement psHC = c.prepareStatement(sqlHC)) {
                psHC.setInt(1, idSite);
                try (ResultSet rsHC = psHC.executeQuery()) {
                    siteHoraireContinu = rsHC.next() ? rsHC.getBoolean(1) : true;
                }
            } catch (Exception ignored) {
                siteHoraireContinu = true;
            }
        }

        // Montant de référence mensuel pour le site (ex: 53 200 Ar si horaire discontinu, 31 200 Ar si horaire continu)
        BigDecimal refDeplacementSite = BigDecimal.ZERO;
        if (siteDeplacementRefMap != null && siteDeplacementRefMap.containsKey(idSite)) {
            refDeplacementSite = siteDeplacementRefMap.get(idSite);
        } else {
            String sqlSiteInd = "SELECT c.montant_reference FROM site_indemnite_config c " +
                                "JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite " +
                                "WHERE c.id_site = ? AND i.code IN ('DEPLACEMENT', 'TRANSPORT') AND c.est_actif = TRUE LIMIT 1";
            try (PreparedStatement psSI = c.prepareStatement(sqlSiteInd)) {
                psSI.setInt(1, idSite);
                try (ResultSet rsSI = psSI.executeQuery()) {
                    if (rsSI.next()) {
                        refDeplacementSite = nullToZero(rsSI.getBigDecimal("montant_reference"));
                    }
                }
            } catch (Exception ignored) {}
            refDeplacementSite = refDeplacementSite.max(BigDecimal.ZERO);
        }

        // Déplacement journalier de référence proratisé sur le nombre de jours ouvrables
        BigDecimal tarifJourDeplacement = calendrierEngine.calculerProrataJournalier(refDeplacementSite, nbJoursOuvrablesPeriode);

        PointageSyntheseMensuelleDTO dto = new PointageSyntheseMensuelleDTO();
        dto.setIdEmploye(idEmp);
        dto.setMatricule(matricule);
        dto.setNom(nom);
        dto.setPrenom(prenom);
        dto.setPhotoUrl(photoUrl);
        dto.setIdSite(idSite);
        dto.setSiteCode(siteCode);
        dto.setSiteNom(siteNom);
        dto.setCodeCategorie(codeCat);
        dto.setLibelleCategorie(isCadre ? "Cadre" : "Employé");
        dto.setIdPeriodePaie(idPeriode);
        dto.setCodePeriode(codePeriode);
        dto.setLibellePeriode(libellePeriode);
        dto.setStatutPeriode(statutPeriode);
        dto.setDispensePointage(estAutonome);
        dto.setEstCadre(isCadre);
        dto.setNbJoursOuvrables(nbJoursOuvrablesPeriode);

        // Charger tous les pointages quotidiens de cet employé sur [dateDebut, dateFin]
        // en priorisant les indemnités dynamiques depuis pointage_indemnite_jour
        String sqlPtg = "SELECT p.id_pointage, p.date_pointage, p.total_heures, p.heures_nuit, p.minutes_retard, " +
                        "p.horaire_continu_jour, st.code AS statut_code, " +
                        "pij_repas.accorde AS dyn_repas_accorde, pij_repas.montant_calcule AS dyn_repas_montant, " +
                        "pij_dep.accorde AS dyn_dep_accorde, pij_dep.quantite AS dyn_dep_quantite, " +
                        "pij_dep.montant_calcule AS dyn_dep_montant " +
                        "FROM pointage_jour p " +
                        "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                        "LEFT JOIN pointage_indemnite_jour pij_repas ON p.id_pointage = pij_repas.id_pointage " +
                        "    AND pij_repas.id_indemnite = (SELECT id_indemnite FROM ref_indemnite WHERE code = 'REPAS' LIMIT 1) " +
                        "LEFT JOIN pointage_indemnite_jour pij_dep ON p.id_pointage = pij_dep.id_pointage " +
                        "    AND pij_dep.id_indemnite IN (SELECT id_indemnite FROM ref_indemnite WHERE code IN ('DEPLACEMENT', 'TRANSPORT') LIMIT 1) " +
                        "WHERE p.id_employe = ? AND p.date_pointage BETWEEN ? AND ? " +
                        "ORDER BY p.date_pointage";

        int presents = 0;
        int conges = 0;
        int absences = 0;
        int feriesChomes = 0;
        int feriesTravailles = 0;
        int dimanchesTravailles = 0;
        int repasCount = 0;
        BigDecimal montantRepasTotal = BigDecimal.ZERO;
        BigDecimal totalTrajets = BigDecimal.ZERO;
        BigDecimal totalMntDeplacement = BigDecimal.ZERO;
        // CORRECTION : accumuler en minutes entières (long) pour éviter les erreurs
        // d'arrondi flottant cumulées (ex : 139h56 vs 139h57).
        // La conversion en décimales ne se fait qu'UNE SEULE FOIS à la fin.
        long cumulMinutesTravaillees = 0;
        long cumulMinutesNuit = 0;
        long cumulMinutesDimanche = 0;
        long cumulMinutesFerie = 0;
        int totalMinsRetard = 0;
        int nbAnomalies = 0;

        // Regroupement par semaine civile ISO (Lundi du tronçon)
        Map<LocalDate, List<DailyAttendanceResult>> parSemaine = new LinkedHashMap<>();
        Set<LocalDate> datesAvecPointage = new HashSet<>();

        // Une semaine sans aucun badge reste une semaine à contrôler : son objectif
        // doit produire une absence, et non disparaître de la synthèse.
        for (LocalDate day = dateDebut; !day.isAfter(dateFin); day = day.plusDays(1)) {
            LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            parSemaine.computeIfAbsent(monday, ignored -> new ArrayList<>());
        }

        try (PreparedStatement psPtg = c.prepareStatement(sqlPtg)) {
            psPtg.setInt(1, idEmp);
            psPtg.setDate(2, Date.valueOf(dateDebut));
            psPtg.setDate(3, Date.valueOf(dateFin));
            try (ResultSet rsPtg = psPtg.executeQuery()) {
                while (rsPtg.next()) {
                    LocalDate dPointage = rsPtg.getDate("date_pointage").toLocalDate();
                    datesAvecPointage.add(dPointage);
                    boolean isWeekend = CalendrierOuvrableEngine.isWeekend(dPointage);
                    boolean isSunday = (dPointage.getDayOfWeek() == DayOfWeek.SUNDAY);
                    String stCode = rsPtg.getString("statut_code");
                    double th = rsPtg.getDouble("total_heures");

                    boolean isAbsenceOuConge = "CONGE".equals(stCode) || "ABSENCE_NON_JUSTIFIEE".equals(stCode) || "ABSENCE_JUSTIFIEE".equals(stCode) || "FERIE_CHOME".equals(stCode);
                    if ("DISPENSE_POINTAGE".equals(stCode) || (estAutonome && !isAbsenceOuConge)) {
                        if (isWeekend && th <= 0.0) {
                            th = 0.0;
                            stCode = "REPOS";
                        } else if (!isWeekend && th <= 0.0) {
                            th = seuilHeuresJour.doubleValue();
                        }
                    }

                    long workedMinutes = hoursToMinutes(rsPtg.getBigDecimal("total_heures"));
                    long nightMinutes = hoursToMinutes(rsPtg.getBigDecimal("heures_nuit"));
                    int ret = rsPtg.getInt("minutes_retard");

                    if ("PRESENT".equals(stCode) || "DISPENSE_POINTAGE".equals(stCode) || "SAMEDI_TRAVAILLE".equals(stCode) || "DEMI_JOURNEE".equals(stCode)) {
                        presents++;
                    } else if ("DIMANCHE_TRAVAILLE".equals(stCode) || (isSunday && th > 0)) {
                        presents++;
                        dimanchesTravailles++;
                        cumulMinutesDimanche += workedMinutes;
                    } else if ("CONGE".equals(stCode)) {
                        conges++;
                    } else if ("ABSENCE_NON_JUSTIFIEE".equals(stCode) || "ABSENCE_JUSTIFIEE".equals(stCode)) {
                        absences++;
                    } else if ("FERIE_CHOME".equals(stCode)) {
                        feriesChomes++;
                    } else if ("FERIE_TRAVAILLE".equals(stCode)) {
                        presents++;
                        feriesTravailles++;
                        cumulMinutesFerie += workedMinutes;
                    } else if ("ANOMALIE_SAISIE".equals(stCode)) {
                        nbAnomalies++;
                    }

                    cumulMinutesTravaillees += workedMinutes;
                    cumulMinutesNuit += nightMinutes;
                    totalMinsRetard += (estAutonome ? 0 : ret);

                    // Saisie quotidienne est la vérité finale depuis pointage_indemnite_jour
                    Object dynRepasObj = rsPtg.getObject("dyn_repas_accorde");
                    boolean repasEffectif = (dynRepasObj != null) && rsPtg.getBoolean("dyn_repas_accorde");

                    boolean estPresentAutonome = estAutonome && !isWeekend && !isAbsenceOuConge && !"REPOS".equals(stCode);
                    boolean estTravailleOuPresent = ("PRESENT".equals(stCode) || "SAMEDI_TRAVAILLE".equals(stCode) 
                            || "DIMANCHE_TRAVAILLE".equals(stCode) || "FERIE_TRAVAILLE".equals(stCode)
                            || "DEMI_JOURNEE".equals(stCode)
                            || estPresentAutonome || th > 0.0);

                    boolean aPrisRepasCeJour = estTravailleOuPresent && (repasEffectif || estPresentAutonome);
                    BigDecimal mealAmount = BigDecimal.ZERO;
                    if (aPrisRepasCeJour) {
                        repasCount++;
                        mealAmount = nullToZero(rsPtg.getBigDecimal("dyn_repas_montant"));
                        montantRepasTotal = montantRepasTotal.add(mealAmount);
                    }

                    // Détermination du déplacement : source de vérité pointage_indemnite_jour
                    BigDecimal fractionJour = BigDecimal.ZERO;
                    Object dynDepQteObj = rsPtg.getObject("dyn_dep_quantite");
                    if (dynDepQteObj != null) {
                        if (estTravailleOuPresent && (rsPtg.getBoolean("dyn_dep_accorde") || estPresentAutonome)) {
                            fractionJour = nullToZero(rsPtg.getBigDecimal("dyn_dep_quantite"));
                        }
                    } else if (estTravailleOuPresent && estPresentAutonome) {
                        fractionJour = BigDecimal.ONE;
                    }
                    totalTrajets = totalTrajets.add(fractionJour);
                    BigDecimal travelAmount = fractionJour.signum() == 0
                            ? BigDecimal.ZERO : nullToZero(rsPtg.getBigDecimal("dyn_dep_montant"));
                    totalMntDeplacement = totalMntDeplacement.add(travelAmount);

                    LocalDate lundiSemaine = dPointage.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    parSemaine.computeIfAbsent(lundiSemaine, k -> new ArrayList<>());

                    parSemaine.get(lundiSemaine).add(new DailyAttendanceResult(
                            idEmp, dPointage, statusFromCode(stCode), workedMinutes, 0, workedMinutes,
                            workedMinutes, estAutonome ? 0 : ret, 0, nightMinutes, isSunday,
                            "FERIE_CHOME".equals(stCode) || "FERIE_TRAVAILLE".equals(stCode),
                            aPrisRepasCeJour ? 1 : 0, mealAmount, fractionJour, travelAmount, List.of()));
                }
            }
        }

        // Les journées autonomes sans badge sont des résultats journaliers calculés,
        // jamais une seconde formule de synthèse.
        if (estAutonome) {
            BigDecimal mealRate = ParametreGlobal.getBigDecimal(params, "MONTANT_REPAS_MIDI", new BigDecimal("2000"));
            BigDecimal travelRate = tarifJourDeplacement;
            BigDecimal travelReduction = ParametreGlobal.getBigDecimal(params, "REDUCTION_DEPLACT_REPAS_MIDI", new BigDecimal("1000"));
            for (LocalDate day = dateDebut; !day.isAfter(dateFin); day = day.plusDays(1)) {
                if (datesAvecPointage.contains(day) || day.getDayOfWeek() == DayOfWeek.SATURDAY
                        || day.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
                DailyAttendanceResult autonomousDay = dailyAttendanceCalculator
                        .calculateDay(new mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceContext(
                                idEmp, day, null, null, null, null,
                                LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(17, 0),
                                LocalTime.of(22, 0), LocalTime.of(5, 0), false, true, false,
                                mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceContext.LeavePart.NONE,
                                true, mealRate, BigDecimal.ONE, travelRate, travelReduction, !siteHoraireContinu));
                LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                parSemaine.computeIfAbsent(monday, ignored -> new ArrayList<>()).add(autonomousDay);
                datesAvecPointage.add(day);
                presents++;
                repasCount++;
                montantRepasTotal = montantRepasTotal.add(autonomousDay.mealAmount());
                totalTrajets = totalTrajets.add(autonomousDay.travelUnits());
                totalMntDeplacement = totalMntDeplacement.add(autonomousDay.travelAmount());
            }
        }

        // Calcul de la compensation hebdomadaire par tronçon
        long totalHs30Minutes = 0;
        long totalHs50Minutes = 0;
        long totalHsBrutesMinutes = 0;
        long totalRetardCompenseMinutes = 0;
        long totalDeficitMinutes = 0;
        List<PointageTronconSemaineDTO> tronconsList = new ArrayList<>();
        List<WeeklyAttendanceResult> weeklyResults = new ArrayList<>();
        int semIndex = 1;

        Set<LocalDate> feriesChomesTroncon = (calendrierEngine != null) 
                ? calendrierEngine.getDatesFeriesEtChomees(dateDebut, dateFin, idSite, c)
                : Collections.emptySet();

        for (Map.Entry<LocalDate, List<DailyAttendanceResult>> entry : parSemaine.entrySet()) {
            LocalDate lundi = entry.getKey();
            List<DailyAttendanceResult> jours = entry.getValue();

            // Compter les jours ouvrés et fériés chômés du tronçon via le moteur centralisé
            LocalDate finTroncon = lundi.plusDays(6);
            if (finTroncon.isAfter(dateFin)) {
                finTroncon = dateFin;
            }
            LocalDate debutTroncon = lundi.isBefore(dateDebut) ? dateDebut : lundi;

            int joursOuvresTroncon = (calendrierEngine != null)
                    ? calendrierEngine.compterJoursOuvrables(debutTroncon, finTroncon, idSite, c)
                    : CalendrierOuvrableEngine.calculerJoursOuvrables(debutTroncon, finTroncon);
            int joursFerieTroncon = 0;
            for (LocalDate cur = debutTroncon; !cur.isAfter(finTroncon); cur = cur.plusDays(1)) {
                if (!CalendrierOuvrableEngine.isWeekend(cur) && feriesChomesTroncon.contains(cur)) {
                    joursFerieTroncon++;
                }
            }

            // Décompter les congés validés sur les jours ouvrés non fériés du tronçon
            // (évite le cumul : un jour férié chômé ne peut pas être décompté deux fois)
            double joursCongeTroncon = 0.0;
            for (DailyAttendanceResult j : jours) {
                if (!CalendrierOuvrableEngine.isWeekend(j.date()) && !feriesChomesTroncon.contains(j.date())) {
                    if (j.status() == DailyAttendanceResult.Status.LEAVE) {
                        joursCongeTroncon += 1.0;
                    } else if (j.status() == DailyAttendanceResult.Status.HALF_DAY) {
                        joursCongeTroncon += 0.5;
                    }
                }
            }

            // Seuil d'heures attendu de la semaine abaissé proportionnellement aux congés
            double joursRequis = Math.max(0.0, (double) joursOuvresTroncon - joursCongeTroncon);
            long expectedMinutes = seuilHeuresJour
                    .multiply(BigDecimal.valueOf(joursRequis)).multiply(BigDecimal.valueOf(60))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            long firstBandMinutes = seuilHsTranche1.multiply(BigDecimal.valueOf(60))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            WeeklyAttendanceResult res = weeklyAttendanceCalculator.calculateWeek(
                    new WeeklyAttendanceContext(lundi, expectedMinutes, firstBandMinutes, !isCadre,
                            seuilHeuresJour.multiply(BigDecimal.valueOf(60)).longValueExact()), jours);
            weeklyResults.add(res);
            totalHs30Minutes += res.overtime30Minutes();
            totalHs50Minutes += res.overtime50Minutes();

            // Résultat unique du moteur hebdomadaire : affichage et paie utilisent
            // exactement la même compensation, y compris pour les jours sans badge.
            long surplusBrutTroncon = res.dailySurplusMinutes();
            long heuresCompenseesTroncon = res.compensatedMinutes();
            long deficitTroncon = res.absenceMinutes();

            totalHsBrutesMinutes += surplusBrutTroncon;
            totalRetardCompenseMinutes += heuresCompenseesTroncon;
            totalDeficitMinutes += deficitTroncon;

            PointageTronconSemaineDTO tDto = new PointageTronconSemaineDTO();
            tDto.setIdEmploye(idEmp);
            tDto.setMatricule(matricule);
            tDto.setNom(nom);
            tDto.setPrenom(prenom);
            tDto.setPhotoUrl(photoUrl);
            tDto.setIdSite(idSite);
            tDto.setSiteNom(siteNom);
            tDto.setCodeCategorie(codeCat);
            tDto.setEstCadre(isCadre);
            tDto.setIdPeriodePaie(idPeriode);
            tDto.setDebutSemaine(debutTroncon.toString());
            tDto.setFinSemaine(finTroncon.toString());
            tDto.setNumeroSemaine(semIndex++);
            tDto.setNbJoursTroncon(joursOuvresTroncon);
            tDto.setNbJoursConge(joursCongeTroncon);
            tDto.setNbJoursFerie(joursFerieTroncon);
            tDto.setSeuilHeuresTroncon(minutesToHours(res.expectedMinutes()));
            tDto.setTotalHeuresTravaillees(minutesToHours(res.workedMinutes()));
            tDto.setHeuresRetard(minutesToHours(res.lateMinutes()));
            tDto.setHeuresHsBrutes(minutesToHours(surplusBrutTroncon));
            tDto.setHeuresCompensees(minutesToHours(heuresCompenseesTroncon));
            tDto.setHeuresHsNettes(minutesToHours(res.overtime30Minutes() + res.overtime50Minutes()));
            tDto.setHeuresDeficit(minutesToHours(deficitTroncon));
            tDto.setHeuresHs30(minutesToHours(res.overtime30Minutes()));
            tDto.setHeuresHs50(minutesToHours(res.overtime50Minutes()));
            tronconsList.add(tDto);
        }

        AttendancePeriodResult periodResult = attendancePeriodCalculator.calculateEmployeePeriod(
                new AttendancePeriodContext(idEmp, idPeriode, dateDebut, dateFin), weeklyResults);

        dto.setTronconsSemaine(tronconsList);

        dto.setNbJoursPresents(presents);
        dto.setNbJoursConge(conges);
        dto.setNbJoursAbsence(absences);
        dto.setNbJoursFerieChome(feriesChomes);
        dto.setNbJoursFerieTravaille(feriesTravailles);
        dto.setNbJoursDimancheTravaille(dimanchesTravailles);
        // Conversion finale unique : minutes → décimales (arrondi au centième)
        dto.setTotalHeuresTravaillees(minutesToHours(periodResult.workedMinutes()));
        dto.setSurplusBrut(minutesToHours(totalHsBrutesMinutes));
        dto.setHeuresHs30(minutesToHours(periodResult.overtime30Minutes()));
        dto.setHeuresHs50(minutesToHours(periodResult.overtime50Minutes()));

        // REGLE GESTION CADRES & HEURES DE NUIT :
        // Les cadres au forfait n'ont pas de majoration HS ni d'heures de nuit payees en sus par defaut.
        // Pour activer la remuneration des heures de nuit pour les cadres, decommenter le bloc ci-dessous :
        /*
        if (isCadre) {
            dto.setHeuresNuitTotal(minutesToHours(periodResult.nightMinutes()));
        }
        */
        if (isCadre) {
            dto.setHeuresNuitTotal(0.0);
        } else {
            dto.setHeuresNuitTotal(minutesToHours(periodResult.nightMinutes()));
        }
        dto.setHeuresDimancheTotal(minutesToHours(periodResult.sundayMinutes()));
        dto.setHeuresFerieTotal(minutesToHours(periodResult.publicHolidayMinutes()));
        dto.setRetardCompense(minutesToHours(totalRetardCompenseMinutes));
        dto.setHeuresAbsenceRetenues(minutesToHours(totalDeficitMinutes));
        dto.setTotalMinutesRetard(totalMinsRetard);
        dto.setNbJoursRepas(repasCount);
        dto.setMontantRepas(periodResult.mealAmount());
        dto.setNbJoursDeplacement(periodResult.travelUnits().setScale(0, RoundingMode.HALF_UP).intValue());
        BigDecimal montantDeplacementFinal = periodResult.travelAmount();
        // Un forfait mensuel est dû intégralement lorsque tous les jours ouvrables
        // de la période ont été pointés; la proratisation journalière ne doit pas
        // introduire un double arrondi (notamment sur les périodes à 21 jours).
        if (refDeplacementSite.signum() > 0 && dto.getNbJoursPresents() >= nbJoursOuvrablesPeriode) {
            montantDeplacementFinal = refDeplacementSite;
        }
        dto.setMontantDeplacement(mg.galaxam.sirh.model.paie.engine.MoneyRoundingPolicy.amount(montantDeplacementFinal));
        dto.setNbAnomaliesNonResolues(nbAnomalies);
        dto.setHeuresNormales(seuilHeuresJour.multiply(BigDecimal.valueOf(nbJoursOuvrablesPeriode)).doubleValue());

        return dto;
    }

    public List<PointageSyntheseMensuelleDTO> getSynthesePeriode(int idPeriode, Integer idSite, Connection c) throws SQLException {
        List<PointageSyntheseMensuelleDTO> list = new ArrayList<>();
        if (idPeriode <= 0) return list;

        // 1. Charger les dates de la période de paie
        LocalDate dateDebut = null;
        LocalDate dateFin = null;
        String codePeriode = null;
        String libellePeriode = null;
        String statutPeriode = null;

        String sqlPeriode = "SELECT p.code, p.libelle, p.date_debut, p.date_fin, s.code AS statut_code " +
                            "FROM periode_paie p " +
                            "JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                            "WHERE p.id_periode_paie = ?";
        try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
            psP.setInt(1, idPeriode);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    dateDebut = rsP.getDate("date_debut").toLocalDate();
                    dateFin = rsP.getDate("date_fin").toLocalDate();
                    codePeriode = rsP.getString("code");
                    libellePeriode = rsP.getString("libelle");
                    statutPeriode = rsP.getString("statut_code");
                }
            }
        }

        if (dateDebut == null || dateFin == null) {
            return list;
        }
        Map<String, String> params = ParametreGlobal.loadActiveMapAtDate(Date.valueOf(dateDebut), c);

        // Calcul des jours ouvrables réels de la période via le moteur centralisé
        int nbJoursOuvrablesPeriode = calendrierEngine.compterJoursOuvrables(dateDebut, dateFin, idSite, c);
        if (nbJoursOuvrablesPeriode == 0) throw new IllegalStateException("MISSING_WORKING_CALENDAR");

        // Charger les indemnités de déplacement/transport configurées par site (site_indemnite_config)
        Map<Integer, BigDecimal> siteDeplacementRefMap = new HashMap<>();
        String sqlSiteIndConfig = "SELECT c.id_site, c.montant_reference FROM site_indemnite_config c " +
                                  "JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite " +
                                  "WHERE i.code IN ('DEPLACEMENT', 'TRANSPORT') AND c.est_actif = TRUE";
        try (PreparedStatement psSIC = c.prepareStatement(sqlSiteIndConfig);
             ResultSet rsSIC = psSIC.executeQuery()) {
            while (rsSIC.next()) {
                siteDeplacementRefMap.put(rsSIC.getInt("id_site"), nullToZero(rsSIC.getBigDecimal("montant_reference")));
            }
        } catch (Exception e) {
            log.warn("[PointageSyntheseService] Erreur chargement montants référence indemnité déplacement: {}", e.getMessage());
        }

        // 2. Charger les employés éligibles avec l'affectation active durant cette période
        StringBuilder sqlEmp = new StringBuilder(
            "SELECT DISTINCT ON (e.id_employe) e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
            "s.id_site, s.code_site AS site_code, s.libelle AS site_nom, COALESCE(s.horaire_continu, TRUE) AS horaire_continu, " +
            "COALESCE(f.dispense_pointage, FALSE) AS est_autonome, " +
            "COALESCE(cp.code, '') AS code_categorie " +
            "FROM employe e " +
            "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
            "    AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
            "JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
            "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
            "LEFT JOIN ref_categorie_professionnelle cp ON a.id_categorie_professionnelle = cp.id_categorie_professionnelle " +
            "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
            "WHERE e.est_actif = TRUE "
        );

        if (idSite != null && idSite > 0) {
            sqlEmp.append("AND (a.id_site = ? OR EXISTS (SELECT 1 FROM pointage_jour pj WHERE pj.id_employe = e.id_employe AND pj.id_periode_paie = ? AND pj.id_site = ?)) ");
        }
        sqlEmp.append("ORDER BY e.id_employe, a.date_debut DESC");

        // 3. Traiter chaque employé via la méthode unifiée
        try (PreparedStatement psE = c.prepareStatement(sqlEmp.toString())) {
            int pIdx = 1;
            psE.setDate(pIdx++, Date.valueOf(dateFin));
            psE.setDate(pIdx++, Date.valueOf(dateDebut));
            if (idSite != null && idSite > 0) {
                psE.setInt(pIdx++, idSite);
                psE.setInt(pIdx++, idPeriode);
                psE.setInt(pIdx++, idSite);
            }
            try (ResultSet rsE = psE.executeQuery()) {
                while (rsE.next()) {
                    int idEmp = rsE.getInt("id_employe");
                    boolean estAutonome = rsE.getBoolean("est_autonome");
                    String codeCat = rsE.getString("code_categorie");
                    boolean isCadre = isCadreCategorie(codeCat);

                    PointageSyntheseMensuelleDTO dto = calculerSyntheseEmploye(
                            idEmp,
                            rsE.getString("matricule"),
                            rsE.getString("nom"),
                            rsE.getString("prenom"),
                            rsE.getString("photo_url"),
                            rsE.getInt("id_site"),
                            rsE.getString("site_code"),
                            rsE.getString("site_nom"),
                            rsE.getBoolean("horaire_continu"),
                            codeCat,
                            isCadre,
                            estAutonome,
                            dateDebut,
                            dateFin,
                            idPeriode,
                            codePeriode,
                            libellePeriode,
                            statutPeriode,
                            nbJoursOuvrablesPeriode,
                            siteDeplacementRefMap,
                            params,
                            c
                    );

                    list.add(dto);
                }
            }
        }

        list.sort(Comparator.comparing(PointageSyntheseMensuelleDTO::getNom, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(PointageSyntheseMensuelleDTO::getPrenom, Comparator.nullsLast(String::compareToIgnoreCase)));

        return list;
    }

    public Map<String, Object> getSynthesePeriodePaginee(
            int idPeriode, Integer idSite, String search, String filtre, int page, int size, Connection c
    ) throws SQLException {
        Map<String, Object> resp = new HashMap<>();
        if (idPeriode <= 0) {
            resp.put("synthese", Collections.emptyList());
            resp.put("totalElements", 0);
            resp.put("totalPages", 0);
            resp.put("currentPage", 1);
            resp.put("pageSize", size);
            resp.put("kpis", Collections.emptyMap());
            resp.put("alertCounts", Collections.emptyMap());
            return resp;
        }

        if (page < 1) page = 1;
        if (size < 1) size = 25;
        int offset = (page - 1) * size;

        LocalDate dateDebut = null;
        LocalDate dateFin = null;
        String codePeriode = null;
        String libellePeriode = null;
        String statutPeriode = null;

        String sqlPeriode = "SELECT p.code, p.libelle, p.date_debut, p.date_fin, s.code AS statut_code " +
                            "FROM periode_paie p " +
                            "JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                            "WHERE p.id_periode_paie = ?";
        try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
            psP.setInt(1, idPeriode);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    dateDebut = rsP.getDate("date_debut").toLocalDate();
                    dateFin = rsP.getDate("date_fin").toLocalDate();
                    codePeriode = rsP.getString("code");
                    libellePeriode = rsP.getString("libelle");
                    statutPeriode = rsP.getString("statut_code");
                }
            }
        }

        if (dateDebut == null || dateFin == null) {
            resp.put("synthese", Collections.emptyList());
            resp.put("totalElements", 0);
            resp.put("totalPages", 0);
            resp.put("currentPage", page);
            resp.put("pageSize", size);
            resp.put("kpis", Collections.emptyMap());
            resp.put("alertCounts", Collections.emptyMap());
            return resp;
        }

        // 1. Total effectif actif pour la période
        int countAll = 0;
        StringBuilder sqlTot = new StringBuilder(
            "SELECT COUNT(DISTINCT e.id_employe) FROM employe e " +
            "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
            "    AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
            "JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
            "WHERE e.est_actif = TRUE "
        );
        if (idSite != null && idSite > 0) sqlTot.append("AND (a.id_site = ? OR EXISTS (SELECT 1 FROM pointage_jour pj WHERE pj.id_employe = e.id_employe AND pj.id_periode_paie = ? AND pj.id_site = ?)) ");
        try (PreparedStatement psTot = c.prepareStatement(sqlTot.toString())) {
            int pIdx = 1;
            psTot.setDate(pIdx++, Date.valueOf(dateFin));
            psTot.setDate(pIdx++, Date.valueOf(dateDebut));
            if (idSite != null && idSite > 0) {
                psTot.setInt(pIdx++, idSite);
                psTot.setInt(pIdx++, idPeriode);
                psTot.setInt(pIdx++, idSite);
            }
            try (ResultSet rsTot = psTot.executeQuery()) {
                if (rsTot.next()) countAll = rsTot.getInt(1);
            }
        }

        // 2. Alert counts (pour pills d'alertes)
        int countNuit = 0;
        int countRetard = 0;
        int countAbsence = 0;
        int countHs = 0;
        int countCompense = 0;
        StringBuilder sqlAl = new StringBuilder(
            "SELECT " +
            "(SELECT COUNT(DISTINCT pj.id_employe) FROM pointage_jour pj JOIN employe e ON pj.id_employe = e.id_employe WHERE pj.id_periode_paie = ? AND e.est_actif = TRUE AND pj.heures_nuit > 0 " + (idSite != null && idSite > 0 ? "AND pj.id_site = ? " : "") + ") AS count_nuit, " +
            "(SELECT COUNT(DISTINCT pj.id_employe) FROM pointage_jour pj JOIN employe e ON pj.id_employe = e.id_employe WHERE pj.id_periode_paie = ? AND e.est_actif = TRUE AND pj.minutes_retard > 0 " + (idSite != null && idSite > 0 ? "AND pj.id_site = ? " : "") + ") AS count_retard, " +
            "(SELECT COUNT(DISTINCT pj.id_employe) FROM pointage_jour pj JOIN ref_statut_pointage_jour spj ON pj.id_statut_jour = spj.id_statut_jour JOIN employe e ON pj.id_employe = e.id_employe WHERE pj.id_periode_paie = ? AND e.est_actif = TRUE AND spj.code LIKE '%ABS%' " + (idSite != null && idSite > 0 ? "AND pj.id_site = ? " : "") + ") AS count_absence, " +
            "(SELECT COUNT(DISTINCT psc.id_employe) FROM pointage_semaine_calcule psc JOIN employe e ON psc.id_employe = e.id_employe WHERE psc.id_periode_paie = ? AND e.est_actif = TRUE AND (psc.heures_hs_brutes > psc.heures_hs_nettes) " + (idSite != null && idSite > 0 ? "AND EXISTS (SELECT 1 FROM pointage_jour pj2 WHERE pj2.id_employe = e.id_employe AND pj2.id_periode_paie = psc.id_periode_paie AND pj2.id_site = ?) " : "") + ") AS count_compense, " +
            "(SELECT COUNT(DISTINCT e.id_employe) FROM employe e WHERE e.est_actif = TRUE AND (" +
            "   EXISTS (SELECT 1 FROM pointage_jour pj WHERE pj.id_employe = e.id_employe AND pj.id_periode_paie = ? " + (idSite != null && idSite > 0 ? "AND pj.id_site = ? " : "") + " AND pj.total_heures > 8.0) " +
            "   OR EXISTS (SELECT 1 FROM pointage_semaine_calcule psc WHERE psc.id_employe = e.id_employe AND psc.id_periode_paie = ? " + (idSite != null && idSite > 0 ? "AND EXISTS (SELECT 1 FROM pointage_jour pj2 WHERE pj2.id_employe = e.id_employe AND pj2.id_periode_paie = ? AND pj2.id_site = ?) " : "") + " AND (psc.heures_hs_30 > 0 OR psc.heures_hs_50 > 0))" +
            ")) AS count_hs"
        );
        try (PreparedStatement psAl = c.prepareStatement(sqlAl.toString())) {
            int aIdx = 1;
            psAl.setInt(aIdx++, idPeriode);
            if (idSite != null && idSite > 0) psAl.setInt(aIdx++, idSite);
            psAl.setInt(aIdx++, idPeriode);
            if (idSite != null && idSite > 0) psAl.setInt(aIdx++, idSite);
            psAl.setInt(aIdx++, idPeriode);
            if (idSite != null && idSite > 0) psAl.setInt(aIdx++, idSite);
            psAl.setInt(aIdx++, idPeriode);
            if (idSite != null && idSite > 0) psAl.setInt(aIdx++, idSite);
            psAl.setInt(aIdx++, idPeriode);
            if (idSite != null && idSite > 0) psAl.setInt(aIdx++, idSite);
            psAl.setInt(aIdx++, idPeriode);
            if (idSite != null && idSite > 0) {
                psAl.setInt(aIdx++, idPeriode);
                psAl.setInt(aIdx++, idSite);
            }
            try (ResultSet rsAl = psAl.executeQuery()) {
                if (rsAl.next()) {
                    countNuit = rsAl.getInt("count_nuit");
                    countRetard = rsAl.getInt("count_retard");
                    countAbsence = rsAl.getInt("count_absence");
                    countCompense = rsAl.getInt("count_compense");
                    countHs = rsAl.getInt("count_hs");
                }
            }
        }

        // 3. Global KPIs
        double totalHeures = 0;
        int totalRetardMins = 0;
        StringBuilder sqlKpi = new StringBuilder(
            "SELECT COALESCE(SUM(pj.total_heures), 0) AS tot_h, COALESCE(SUM(pj.minutes_retard), 0) AS tot_ret " +
            "FROM pointage_jour pj JOIN employe e ON pj.id_employe = e.id_employe " +
            "WHERE pj.id_periode_paie = ? AND e.est_actif = TRUE "
        );
        if (idSite != null && idSite > 0) sqlKpi.append("AND pj.id_site = ? ");
        try (PreparedStatement psKpi = c.prepareStatement(sqlKpi.toString())) {
            psKpi.setInt(1, idPeriode);
            if (idSite != null && idSite > 0) psKpi.setInt(2, idSite);
            try (ResultSet rsKpi = psKpi.executeQuery()) {
                if (rsKpi.next()) {
                    totalHeures = rsKpi.getDouble("tot_h");
                    totalRetardMins = rsKpi.getInt("tot_ret");
                }
            }
        }

        double totalHs30 = 0;
        double totalHs50 = 0;
        StringBuilder sqlHs = new StringBuilder(
            "SELECT COALESCE(SUM(psc.heures_hs_30), 0) AS hs30, COALESCE(SUM(psc.heures_hs_50), 0) AS hs50 " +
            "FROM pointage_semaine_calcule psc JOIN employe e ON psc.id_employe = e.id_employe " +
            "WHERE psc.id_periode_paie = ? AND e.est_actif = TRUE "
        );
        if (idSite != null && idSite > 0) {
            sqlHs.append("AND EXISTS (SELECT 1 FROM pointage_jour pj WHERE pj.id_employe = psc.id_employe AND pj.id_periode_paie = psc.id_periode_paie AND pj.id_site = ?) ");
        }
        try (PreparedStatement psHs = c.prepareStatement(sqlHs.toString())) {
            psHs.setInt(1, idPeriode);
            if (idSite != null && idSite > 0) psHs.setInt(2, idSite);
            try (ResultSet rsHs = psHs.executeQuery()) {
                if (rsHs.next()) {
                    totalHs30 = rsHs.getDouble("hs30");
                    totalHs50 = rsHs.getDouble("hs50");
                }
            }
        }
        if (totalHs30 == 0 && totalHs50 == 0) {
            StringBuilder sqlHsEst = new StringBuilder(
                "SELECT COALESCE(SUM(GREATEST(0, pj.total_heures - 8.0)), 0) AS tot_hs " +
                "FROM pointage_jour pj JOIN employe e ON pj.id_employe = e.id_employe " +
                "WHERE pj.id_periode_paie = ? AND e.est_actif = TRUE "
            );
            if (idSite != null && idSite > 0) sqlHsEst.append("AND pj.id_site = ? ");
            try (PreparedStatement psHsE = c.prepareStatement(sqlHsEst.toString())) {
                psHsE.setInt(1, idPeriode);
                if (idSite != null && idSite > 0) psHsE.setInt(2, idSite);
                try (ResultSet rsHsE = psHsE.executeQuery()) {
                    if (rsHsE.next()) {
                        double totHsEst = rsHsE.getDouble("tot_hs");
                        totalHs30 = Math.min(8.0 * (countAll > 0 ? countAll : 1), totHsEst);
                        totalHs50 = Math.max(0.0, totHsEst - totalHs30);
                    }
                }
            }
        }

        // 4. CTE filtrée pour paginer au niveau SQL
        StringBuilder whereFilter = new StringBuilder();
        List<Object> filterParams = new ArrayList<>();

        if (idSite != null && idSite > 0) {
            whereFilter.append("AND (a.id_site = ? OR EXISTS (SELECT 1 FROM pointage_jour pj WHERE pj.id_employe = e.id_employe AND pj.id_periode_paie = ? AND pj.id_site = ?)) ");
            filterParams.add(idSite);
            filterParams.add(idPeriode);
            filterParams.add(idSite);
        }

        if (search != null && !search.trim().isEmpty()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            whereFilter.append("AND (LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.matricule) LIKE ?) ");
            filterParams.add(pattern);
            filterParams.add(pattern);
            filterParams.add(pattern);
        }

        if ("HS".equalsIgnoreCase(filtre)) {
            whereFilter.append("AND (EXISTS (SELECT 1 FROM pointage_jour pj WHERE pj.id_employe = e.id_employe AND pj.id_periode_paie = ? AND pj.total_heures > 8.0) " +
                               "OR EXISTS (SELECT 1 FROM pointage_semaine_calcule psc WHERE psc.id_employe = e.id_employe AND psc.id_periode_paie = ? AND (psc.heures_hs_30 > 0 OR psc.heures_hs_50 > 0))) ");
            filterParams.add(idPeriode);
            filterParams.add(idPeriode);
        } else if ("NUIT".equalsIgnoreCase(filtre)) {
            whereFilter.append("AND EXISTS (SELECT 1 FROM pointage_jour pj WHERE pj.id_employe = e.id_employe AND pj.id_periode_paie = ? AND pj.heures_nuit > 0) ");
            filterParams.add(idPeriode);
        } else if ("RETARD".equalsIgnoreCase(filtre)) {
            whereFilter.append("AND EXISTS (SELECT 1 FROM pointage_jour pj WHERE pj.id_employe = e.id_employe AND pj.id_periode_paie = ? AND pj.minutes_retard > 0) ");
            filterParams.add(idPeriode);
        } else if ("ABSENCE".equalsIgnoreCase(filtre)) {
            whereFilter.append("AND EXISTS (SELECT 1 FROM pointage_jour pj JOIN ref_statut_pointage_jour spj ON pj.id_statut_jour = spj.id_statut_jour WHERE pj.id_employe = e.id_employe AND pj.id_periode_paie = ? AND spj.code LIKE '%ABS%') ");
            filterParams.add(idPeriode);
        } else if ("COMPENSE".equalsIgnoreCase(filtre)) {
            whereFilter.append("AND EXISTS (SELECT 1 FROM pointage_semaine_calcule psc WHERE psc.id_employe = e.id_employe AND psc.id_periode_paie = ? AND (psc.heures_hs_brutes > psc.heures_hs_nettes)) ");
            filterParams.add(idPeriode);
        }

        String cteSql = 
            "WITH employes_actifs AS ( " +
            "    SELECT DISTINCT ON (e.id_employe) e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
            "           s.id_site, s.code_site AS site_code, s.libelle AS site_nom, COALESCE(s.horaire_continu, TRUE) AS horaire_continu, " +
            "           COALESCE(f.dispense_pointage, FALSE) AS est_autonome, " +
            "           COALESCE(cp.code, '') AS code_categorie " +
            "    FROM employe e " +
            "    JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
            "        AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
            "    JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
            "    LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
            "    LEFT JOIN ref_categorie_professionnelle cp ON a.id_categorie_professionnelle = cp.id_categorie_professionnelle " +
            "    LEFT JOIN ref_site s ON a.id_site = s.id_site " +
            "    WHERE e.est_actif = TRUE " + whereFilter.toString() +
            "    ORDER BY e.id_employe, a.date_debut DESC " +
            ") ";

        // Count query
        String countSql = cteSql + "SELECT COUNT(*) FROM employes_actifs";
        int totalElements = 0;
        try (PreparedStatement psC = c.prepareStatement(countSql)) {
            int idx = 1;
            psC.setDate(idx++, Date.valueOf(dateFin));
            psC.setDate(idx++, Date.valueOf(dateDebut));
            for (Object p : filterParams) {
                if (p instanceof Integer) psC.setInt(idx++, (Integer) p);
                else if (p instanceof String) psC.setString(idx++, (String) p);
            }
            try (ResultSet rsC = psC.executeQuery()) {
                if (rsC.next()) totalElements = rsC.getInt(1);
            }
        }

        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        // Page data query
        String dataSql = cteSql + "SELECT * FROM employes_actifs ORDER BY nom ASC, prenom ASC, matricule ASC LIMIT ? OFFSET ?";
        List<PointageSyntheseMensuelleDTO> pageList = new ArrayList<>();
        if (totalElements > 0) {
            int nbJoursOuvrablesPeriode = calendrierEngine.compterJoursOuvrables(dateDebut, dateFin, idSite, c);
            if (nbJoursOuvrablesPeriode == 0) throw new IllegalStateException("MISSING_WORKING_CALENDAR");

            Map<Integer, BigDecimal> siteDeplacementRefMap = new HashMap<>();
            String sqlSiteIndConfig = "SELECT c.id_site, c.montant_reference FROM site_indemnite_config c " +
                                      "JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite " +
                                      "WHERE i.code IN ('DEPLACEMENT', 'TRANSPORT') AND c.est_actif = TRUE";
            try (PreparedStatement psSIC = c.prepareStatement(sqlSiteIndConfig);
                 ResultSet rsSIC = psSIC.executeQuery()) {
                while (rsSIC.next()) {
                    siteDeplacementRefMap.put(rsSIC.getInt("id_site"), nullToZero(rsSIC.getBigDecimal("montant_reference")));
                }
            } catch (Exception e) {
                log.warn("Erreur chargement siteDeplacementRefMap: {}", e.getMessage());
            }

            Map<String, String> params = ParametreGlobal.loadActiveMapAtDate(Date.valueOf(dateDebut), c);

            try (PreparedStatement psD = c.prepareStatement(dataSql)) {
                int idx = 1;
                psD.setDate(idx++, Date.valueOf(dateFin));
                psD.setDate(idx++, Date.valueOf(dateDebut));
                for (Object p : filterParams) {
                    if (p instanceof Integer) psD.setInt(idx++, (Integer) p);
                    else if (p instanceof String) psD.setString(idx++, (String) p);
                }
                psD.setInt(idx++, size);
                psD.setInt(idx++, offset);

                try (ResultSet rsE = psD.executeQuery()) {
                    while (rsE.next()) {
                        int idEmp = rsE.getInt("id_employe");
                        boolean estAutonome = rsE.getBoolean("est_autonome");
                        String codeCat = rsE.getString("code_categorie");
                        boolean isCadre = isCadreCategorie(codeCat);

                        PointageSyntheseMensuelleDTO dto = calculerSyntheseEmploye(
                                idEmp,
                                rsE.getString("matricule"),
                                rsE.getString("nom"),
                                rsE.getString("prenom"),
                                rsE.getString("photo_url"),
                                rsE.getInt("id_site"),
                                rsE.getString("site_code"),
                                rsE.getString("site_nom"),
                                rsE.getBoolean("horaire_continu"),
                                codeCat,
                                isCadre,
                                estAutonome,
                                dateDebut,
                                dateFin,
                                idPeriode,
                                codePeriode,
                                libellePeriode,
                                statutPeriode,
                                nbJoursOuvrablesPeriode,
                                siteDeplacementRefMap,
                                params,
                                c
                        );
                        pageList.add(dto);
                    }
                }
            }
        }

        Map<String, Object> kpisMap = new HashMap<>();
        kpisMap.put("totalSalaries", countAll);
        kpisMap.put("totalHeures", Math.round(totalHeures * 10.0) / 10.0);
        kpisMap.put("totalHs30", Math.round(totalHs30 * 10.0) / 10.0);
        kpisMap.put("totalHs50", Math.round(totalHs50 * 10.0) / 10.0);
        kpisMap.put("totalRetardMins", totalRetardMins);

        Map<String, Object> alertCountsMap = new HashMap<>();
        alertCountsMap.put("all", countAll);
        alertCountsMap.put("hs", countHs);
        alertCountsMap.put("nuit", countNuit);
        alertCountsMap.put("retard", countRetard);
        alertCountsMap.put("absence", countAbsence);
        alertCountsMap.put("compense", countCompense);

        resp.put("synthese", pageList);
        resp.put("totalElements", totalElements);
        resp.put("totalPages", totalPages);
        resp.put("currentPage", page);
        resp.put("pageSize", size);
        resp.put("kpis", kpisMap);
        resp.put("alertCounts", alertCountsMap);
        return resp;
    }

    /**
     * Gèle les tronçons hebdomadaires et le cumul mensuel dans les tables immuables
     */
    public void gelerPeriode(int idPeriode, Integer userId, Connection c) throws SQLException {
        // Calculer la synthèse via notre logique métier pure
        List<PointageSyntheseMensuelleDTO> synthese = getSynthesePeriode(idPeriode, null, c);

        String sqlCumul = "INSERT INTO pointage_cumul_periode (" +
                          "id_employe, id_periode_paie, version_snapshot, statut_snapshot, remplace_id_cumul, nb_jours_ouvrables, nb_jours_presents, " +
                          "nb_jours_absence, nb_jours_conge, nb_jours_ferie_chome, nb_jours_ferie_travaille, " +
                          "total_heures_travaillees, heures_hs_30, heures_hs_50, heures_nuit_total, " +
                          "heures_absence_retenues, total_minutes_retard, nb_jours_deplacement, montant_deplacement, " +
                          "nb_jours_repas, montant_repas, gele_par, gele_at" +
                          ") VALUES (?, ?, " +
                          "(SELECT COALESCE(MAX(version_snapshot), 0) + 1 FROM pointage_cumul_periode WHERE id_employe = ? AND id_periode_paie = ?), 'GELE', " +
                          "(SELECT id_cumul FROM pointage_cumul_periode WHERE id_employe = ? AND id_periode_paie = ? ORDER BY version_snapshot DESC LIMIT 1), " +
                          "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id_cumul";

        try (PreparedStatement ps = c.prepareStatement(sqlCumul)) {
            for (PointageSyntheseMensuelleDTO dto : synthese) {
                try (PreparedStatement replace = c.prepareStatement(
                        "UPDATE pointage_cumul_periode SET statut_snapshot = 'REMPLACE' " +
                        "WHERE id_employe = ? AND id_periode_paie = ? AND statut_snapshot = 'GELE'")) {
                    replace.setInt(1, dto.getIdEmploye());
                    replace.setInt(2, idPeriode);
                    replace.executeUpdate();
                }
                ps.setInt(1, dto.getIdEmploye());
                ps.setInt(2, idPeriode);
                ps.setInt(3, dto.getIdEmploye());
                ps.setInt(4, idPeriode);
                ps.setInt(5, dto.getIdEmploye());
                ps.setInt(6, idPeriode);
                ps.setInt(7, dto.getNbJoursOuvrables());
                ps.setInt(8, dto.getNbJoursPresents());
                ps.setInt(9, dto.getNbJoursAbsence());
                ps.setInt(10, dto.getNbJoursConge());
                ps.setInt(11, dto.getNbJoursFerieChome());
                ps.setInt(12, dto.getNbJoursFerieTravaille());
                ps.setBigDecimal(13, BigDecimal.valueOf(dto.getTotalHeuresTravaillees()));
                ps.setBigDecimal(14, BigDecimal.valueOf(dto.getHeuresHs30()));
                ps.setBigDecimal(15, BigDecimal.valueOf(dto.getHeuresHs50()));
                ps.setBigDecimal(16, BigDecimal.valueOf(dto.getHeuresNuitTotal()));
                ps.setBigDecimal(17, BigDecimal.valueOf(dto.getHeuresAbsenceRetenues()));
                ps.setInt(18, dto.getTotalMinutesRetard());
                ps.setBigDecimal(19, BigDecimal.valueOf(dto.getNbJoursDeplacement()));
                ps.setBigDecimal(20, dto.getMontantDeplacement());
                ps.setInt(21, dto.getNbJoursRepas());
                ps.setBigDecimal(22, dto.getMontantRepas());
                if (userId != null) ps.setInt(23, userId); else ps.setNull(23, java.sql.Types.INTEGER);
                int snapshotId;
                try (ResultSet inserted = ps.executeQuery()) {
                    if (!inserted.next()) throw new SQLException("Le snapshot Pointage n'a pas été créé.");
                    snapshotId = inserted.getInt(1);
                }
                enregistrerProvenanceSnapshot(snapshotId, dto.getIdEmploye(), idPeriode, c);
            }
        }

        // Archiver également toutes les indemnités dynamiques consolidées
        String sqlCumulInd = "INSERT INTO pointage_cumul_indemnite (id_periode_paie, id_employe, id_indemnite, quantite_totale, montant_total) " +
                "SELECT pj.id_periode_paie, pj.id_employe, pij.id_indemnite, SUM(pij.quantite), SUM(pij.montant_calcule) " +
                "FROM pointage_indemnite_jour pij " +
                "JOIN pointage_jour pj ON pij.id_pointage = pj.id_pointage " +
                "WHERE pj.id_periode_paie = ? AND pij.accorde = true " +
                "GROUP BY pj.id_periode_paie, pj.id_employe, pij.id_indemnite " +
                "ON CONFLICT (id_periode_paie, id_employe, id_indemnite) DO UPDATE SET " +
                "quantite_totale = EXCLUDED.quantite_totale, montant_total = EXCLUDED.montant_total, gele_at = CURRENT_TIMESTAMP";
        try (PreparedStatement psInd = c.prepareStatement(sqlCumulInd)) {
            psInd.setInt(1, idPeriode);
            psInd.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    /**
     * Dégèle les données lors de la réouverture exceptionnelle d'une période
     */
    public void degelerPeriode(int idPeriode, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE pointage_cumul_periode SET statut_snapshot = 'REMPLACE' " +
                "WHERE id_periode_paie = ? AND statut_snapshot = 'GELE'");
             PreparedStatement psSem = c.prepareStatement(
                     "UPDATE pointage_semaine_calcule SET gele_par = NULL WHERE id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            ps.executeUpdate();
            psSem.setInt(1, idPeriode);
            psSem.executeUpdate();
        }
    }

    private void enregistrerProvenanceSnapshot(int snapshotId, int employeeId, int periodId, Connection c)
            throws SQLException {
        String days = "INSERT INTO pointage_snapshot_jour " +
                "(id_cumul, id_pointage, version_pointage, date_pointage, minutes_travaillees, minutes_retard, minutes_nuit, statut_jour) " +
                "SELECT ?, p.id_pointage, p.version, p.date_pointage, ROUND(p.total_heures * 60), " +
                "p.minutes_retard, ROUND(p.heures_nuit * 60), s.code FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour s ON s.id_statut_jour = p.id_statut_jour " +
                "WHERE p.id_employe = ? AND p.id_periode_paie = ?";
        try (PreparedStatement ps = c.prepareStatement(days)) {
            ps.setInt(1, snapshotId); ps.setInt(2, employeeId); ps.setInt(3, periodId); ps.executeUpdate();
        }
        String weeks = "INSERT INTO pointage_snapshot_semaine " +
                "(id_cumul, id_semaine_calcule, debut_semaine, minutes_travaillees, minutes_hs_30, minutes_hs_50) " +
                "SELECT ?, id_semaine_calcule, debut_semaine, ROUND(total_heures_travaillees * 60), " +
                "ROUND(heures_hs_30 * 60), ROUND(heures_hs_50 * 60) FROM pointage_semaine_calcule " +
                "WHERE id_employe = ? AND id_periode_paie = ?";
        try (PreparedStatement ps = c.prepareStatement(weeks)) {
            ps.setInt(1, snapshotId); ps.setInt(2, employeeId); ps.setInt(3, periodId); ps.executeUpdate();
        }
    }

    /**
     * Découpe autoritaire de la période de paie en ses semaines réelles avec statut de validation
     */
    public List<Map<String, Object>> getSemainesPeriode(int idPeriode, Integer idSite) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return getSemainesPeriode(idPeriode, idSite, c);
        }
    }

    public List<Map<String, Object>> getSemainesPeriode(int idPeriode, Integer idSite, Connection c) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        if (idPeriode <= 0) return result;

        LocalDate dateDebut = null;
        LocalDate dateFin = null;
        String sqlPeriode = "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?";
        try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
            psP.setInt(1, idPeriode);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    dateDebut = rsP.getDate("date_debut").toLocalDate();
                    dateFin = rsP.getDate("date_fin").toLocalDate();
                }
            }
        }

        if (dateDebut == null || dateFin == null) return result;

        // Découpage en tronçons du Lundi au Dimanche
        LocalDate cur = dateDebut;
        int semNum = 1;
        while (!cur.isAfter(dateFin)) {
            LocalDate debutSemaine = cur.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate finSemaine = cur.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            // Si le début du tronçon tombe un Samedi ou Dimanche et qu'il y a une semaine suivante dans la période,
            // on rattache ce weekend directement au dimanche de la semaine suivante (évite une semaine vide de 0 jour ouvré)
            if (CalendrierOuvrableEngine.isWeekend(cur) && finSemaine.plusDays(7).isBefore(dateFin.plusDays(7))) {
                cur = cur.plusDays(1);
                continue;
            }

            LocalDate dateDebutTroncon = cur.isBefore(debutSemaine) ? debutSemaine : cur;
            if (dateDebutTroncon.isBefore(dateDebut)) dateDebutTroncon = dateDebut;

            LocalDate dateFinTroncon = finSemaine.isAfter(dateFin) ? dateFin : finSemaine;

            // Calcul des jours ouvrés du tronçon
            int nbOuvrables = 0;
            for (LocalDate d = dateDebutTroncon; !d.isAfter(dateFinTroncon); d = d.plusDays(1)) {
                if (!CalendrierOuvrableEngine.isWeekend(d)) {
                    nbOuvrables++;
                }
            }

            double seuilHeures = nbOuvrables * 8.0;

            // Statistiques des pointages réels saisis pour ce tronçon
            String sqlStats = "SELECT COUNT(*) AS total_points, " +
                              "COALESCE(SUM(p.total_heures), 0) AS somme_heures, " +
                              "COUNT(CASE WHEN st.code IN ('ANOMALIE_SAISIE', 'ABSENCE_NON_JUSTIFIEE') THEN 1 END) AS nb_anomalies " +
                              "FROM pointage_jour p " +
                              "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                              "WHERE p.id_periode_paie = ? AND p.date_pointage BETWEEN ? AND ? " +
                              (idSite != null && idSite > 0 ? "AND p.id_site = ? " : "");

            double heuresSaisies = 0.0;
            int totalPointages = 0;
            int nbAnomalies = 0;

            try (PreparedStatement psStats = c.prepareStatement(sqlStats)) {
                psStats.setInt(1, idPeriode);
                psStats.setDate(2, Date.valueOf(dateDebutTroncon));
                psStats.setDate(3, Date.valueOf(dateFinTroncon));
                if (idSite != null && idSite > 0) {
                    psStats.setInt(4, idSite);
                }
                try (ResultSet rsS = psStats.executeQuery()) {
                    if (rsS.next()) {
                        totalPointages = rsS.getInt("total_points");
                        heuresSaisies = rsS.getDouble("somme_heures");
                        nbAnomalies = rsS.getInt("nb_anomalies");
                    }
                }
            }

            // Compter les salariés actifs non dispensés pour calculer les pointages attendus
            int nbSalariesActifs = 0;
            String sqlNbEmp = "SELECT COUNT(DISTINCT e.id_employe) FROM employe e " +
                              "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                              "    AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                              "JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
                              "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                              "WHERE COALESCE(f.dispense_pointage, FALSE) = FALSE " +
                              (idSite != null && idSite > 0 ? "AND (a.id_site = ? OR EXISTS (SELECT 1 FROM pointage_jour pj WHERE pj.id_employe = e.id_employe AND pj.id_periode_paie = ? AND pj.id_site = ?)) " : "");
            try (PreparedStatement psEmp = c.prepareStatement(sqlNbEmp)) {
                int empIdx = 1;
                psEmp.setDate(empIdx++, Date.valueOf(dateFinTroncon));
                psEmp.setDate(empIdx++, Date.valueOf(dateDebutTroncon));
                if (idSite != null && idSite > 0) {
                    psEmp.setInt(empIdx++, idSite);
                    psEmp.setInt(empIdx++, idPeriode);
                    psEmp.setInt(empIdx++, idSite);
                }
                try (ResultSet rsEmp = psEmp.executeQuery()) {
                    if (rsEmp.next()) nbSalariesActifs = rsEmp.getInt(1);
                }
            }

            int pointagesAttendus = nbSalariesActifs * nbOuvrables;

            // Vérifier si cette semaine est déjà validée / calculée dans pointage_semaine_calcule
            boolean estValidee = false;
            String sqlCheckValidee = "SELECT COUNT(*) FROM pointage_semaine_calcule psc WHERE psc.id_periode_paie = ? AND psc.debut_semaine = ?" +
                                     (idSite != null && idSite > 0 ? " AND EXISTS (SELECT 1 FROM pointage_jour pj WHERE pj.id_employe = psc.id_employe AND pj.id_periode_paie = psc.id_periode_paie AND pj.id_site = ?)" : "");
            try (PreparedStatement psVal = c.prepareStatement(sqlCheckValidee)) {
                psVal.setInt(1, idPeriode);
                psVal.setDate(2, Date.valueOf(dateDebutTroncon));
                if (idSite != null && idSite > 0) {
                    psVal.setInt(3, idSite);
                }
                try (ResultSet rsVal = psVal.executeQuery()) {
                    if (rsVal.next() && rsVal.getInt(1) > 0) {
                        estValidee = true;
                    }
                }
            } catch (Exception ignored) {}

            Map<String, Object> semMap = new HashMap<>();
            semMap.put("id", semNum);
            semMap.put("numeroSemaine", semNum);
            semMap.put("title", "Semaine " + semNum);
            semMap.put("dateDebut", dateDebutTroncon.toString());
            semMap.put("dateFin", dateFinTroncon.toString());

            // Libellé court des dates
            String libelleDates = dateDebutTroncon.getDayOfMonth() + " " +
                    dateDebutTroncon.getMonth().toString().substring(0, 3).toLowerCase() + " – " +
                    dateFinTroncon.getDayOfMonth() + " " +
                    dateFinTroncon.getMonth().toString().substring(0, 3).toLowerCase();
            semMap.put("dates", libelleDates);
            semMap.put("nbJoursOuvrables", nbOuvrables);
            semMap.put("seuilHeures", seuilHeures);
            semMap.put("totalHeures", Math.round(heuresSaisies * 100.0) / 100.0);
            semMap.put("totalPointages", totalPointages);
            semMap.put("nbAnomalies", nbAnomalies);
            semMap.put("pointagesAttendus", pointagesAttendus);
            semMap.put("estValidee", estValidee);

            // Détermination du statut de la semaine
            boolean isReady = false;
            String statut;
            String desc;

            if (estValidee) {
                statut = "Validée";
                desc = "Heures supplémentaires et compensations consolidées.";
                isReady = false;
            } else if (totalPointages == 0) {
                statut = "Saisie en cours";
                desc = "Pointages quotidiens non encore saisis.";
                isReady = false;
            } else if (nbAnomalies > 0) {
                statut = "Prête (avec anomalies)";
                desc = nbAnomalies + " anomalie(s) détectée(s). Validation possible avec signalement.";
                isReady = true; // Non-bloquant mais signalé !
            } else if (totalPointages < pointagesAttendus) {
                statut = "Saisie partielle";
                desc = "Saisie en cours (" + totalPointages + "/" + pointagesAttendus + " pointages réalisés).";
                isReady = true; // Permettre la validation opérationnelle
            } else {
                statut = "Prête à valider";
                desc = "Saisie complète, aucune anomalie.";
                isReady = true;
            }

            semMap.put("statut", statut);
            semMap.put("desc", desc);
            semMap.put("ready", isReady);

            result.add(semMap);

            semNum++;
            cur = dateFinTroncon.plusDays(1);
        }

        return result;
    }

    /**
     * Calcule le détail nominatif des salariés sur un tronçon hebdomadaire précis
     */
    public List<PointageSyntheseMensuelleDTO> getSyntheseSemaineDetail(
            int idPeriode,
            LocalDate dateDebutTroncon,
            LocalDate dateFinTroncon,
            Integer idSite
    ) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return getSyntheseSemaineDetail(idPeriode, dateDebutTroncon, dateFinTroncon, idSite, c);
        }
    }

    public List<PointageSyntheseMensuelleDTO> getSyntheseSemaineDetail(
            int idPeriode,
            LocalDate dateDebutTroncon,
            LocalDate dateFinTroncon,
            Integer idSite,
            Connection c
    ) throws SQLException {
        List<PointageSyntheseMensuelleDTO> list = new ArrayList<>();
        if (idPeriode <= 0 || dateDebutTroncon == null || dateFinTroncon == null) return list;

        Map<String, String> params = ParametreGlobal.loadActiveMapAtDate(Date.valueOf(dateDebutTroncon), c);
        BigDecimal seuilHeuresJour = ParametreGlobal.getBigDecimal(params, "SEUIL_HEURES_JOUR", new BigDecimal("8"));
        BigDecimal seuilHsTranche1 = new BigDecimal("8");
        try {
            mg.galaxam.sirh.model.paieconfig.PaieConfigLegale leg = mg.galaxam.sirh.model.paieconfig.PaieConfigLegale.findActiveAt(c, dateDebutTroncon);
            if (leg != null && leg.getSeuilHsTranche1() != null) {
                seuilHsTranche1 = leg.getSeuilHsTranche1();
            }
        } catch (Exception e) {
            log.warn("Impossible de charger PaieConfigLegale pour seuilHsTranche1, repli sur 8.0 : {}", e.getMessage());
        }
        Map<String, ParametreGlobal.ParametreEtat> etatMap = ParametreGlobal.loadEtatMapAtDate(dateDebutTroncon != null ? Date.valueOf(dateDebutTroncon) : null, c);
        BigDecimal montantRepasMidiDefaut = ParametreGlobal.getBigDecimal(etatMap, "MONTANT_REPAS_MIDI", new BigDecimal("2000"), new BigDecimal("2000"));

        boolean actif2eRepas1 = ParametreGlobal.isActif(etatMap, "SEUIL_2ND_REPAS_1", true);
        BigDecimal montant2eRepas1 = actif2eRepas1 ? ParametreGlobal.getBigDecimal(etatMap, "MONTANT_2ND_REPAS_1", new BigDecimal("2000"), BigDecimal.ZERO) : BigDecimal.ZERO;
        String seuil2eRepas1Str = ParametreGlobal.getString(etatMap, "SEUIL_2ND_REPAS_1", "19:30", "23:59");
        LocalTime seuil2eRepas1 = LocalTime.parse(seuil2eRepas1Str.length() == 5 ? seuil2eRepas1Str : seuil2eRepas1Str.substring(0, 5));

        boolean actif2eRepas2 = ParametreGlobal.isActif(etatMap, "SEUIL_2ND_REPAS_2", true);
        BigDecimal montant2eRepas2 = actif2eRepas2 ? ParametreGlobal.getBigDecimal(etatMap, "MONTANT_2ND_REPAS_2", new BigDecimal("4000"), BigDecimal.ZERO) : BigDecimal.ZERO;
        String seuil2eRepas2Str = ParametreGlobal.getString(etatMap, "SEUIL_2ND_REPAS_2", "21:00", "23:59");
        LocalTime seuil2eRepas2 = LocalTime.parse(seuil2eRepas2Str.length() == 5 ? seuil2eRepas2Str : seuil2eRepas2Str.substring(0, 5));

        // Compter les jours ouvrés du tronçon via le moteur centralisé
        int nbOuvrables = (calendrierEngine != null)
                ? calendrierEngine.compterJoursOuvrables(dateDebutTroncon, dateFinTroncon, idSite, c)
                : CalendrierOuvrableEngine.calculerJoursOuvrables(dateDebutTroncon, dateFinTroncon);

        // Charger les indemnités de déplacement/transport configurées par site (site_indemnite_config)
        Map<Integer, BigDecimal> siteDeplacementRefMap = new HashMap<>();
        String sqlSiteIndConfig = "SELECT c.id_site, c.montant_reference FROM site_indemnite_config c " +
                                  "JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite " +
                                  "WHERE i.code IN ('DEPLACEMENT', 'TRANSPORT') AND c.est_actif = TRUE";
        try (PreparedStatement psSIC = c.prepareStatement(sqlSiteIndConfig);
             ResultSet rsSIC = psSIC.executeQuery()) {
            while (rsSIC.next()) {
                siteDeplacementRefMap.put(rsSIC.getInt("id_site"), nullToZero(rsSIC.getBigDecimal("montant_reference")));
            }
        } catch (Exception e) {
            log.warn("[PointageSyntheseService] Erreur chargement montants référence indemnité déplacement tronçon: {}", e.getMessage());
        }

        StringBuilder sqlEmp = new StringBuilder(
            "SELECT DISTINCT ON (e.id_employe) e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
            "s.id_site, s.code_site AS site_code, s.libelle AS site_nom, COALESCE(s.horaire_continu, TRUE) AS horaire_continu, " +
            "COALESCE(f.dispense_pointage, FALSE) AS est_autonome, " +
            "COALESCE(cp.code, '') AS code_categorie " +
            "FROM employe e " +
            "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
            "    AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
            "JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
            "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
            "LEFT JOIN ref_categorie_professionnelle cp ON a.id_categorie_professionnelle = cp.id_categorie_professionnelle " +
            "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
            "WHERE e.est_actif = TRUE "
        );

        if (idSite != null && idSite > 0) {
            sqlEmp.append("AND (a.id_site = ? OR EXISTS (SELECT 1 FROM pointage_jour pj WHERE pj.id_employe = e.id_employe AND pj.id_periode_paie = ? AND pj.id_site = ?)) ");
        }
        sqlEmp.append("ORDER BY e.id_employe, a.date_debut DESC");

        try (PreparedStatement psE = c.prepareStatement(sqlEmp.toString())) {
            int pIdx = 1;
            psE.setDate(pIdx++, Date.valueOf(dateFinTroncon));
            psE.setDate(pIdx++, Date.valueOf(dateDebutTroncon));
            if (idSite != null && idSite > 0) {
                psE.setInt(pIdx++, idSite);
                psE.setInt(pIdx++, idPeriode);
                psE.setInt(pIdx++, idSite);
            }
            try (ResultSet rsE = psE.executeQuery()) {
                while (rsE.next()) {
                    int idEmp = rsE.getInt("id_employe");
                    boolean estAutonome = rsE.getBoolean("est_autonome");
                    String codeCat = rsE.getString("code_categorie");
                    boolean isCadre = isCadreCategorie(codeCat);

                    PointageSyntheseMensuelleDTO dto = calculerSyntheseEmploye(
                            idEmp,
                            rsE.getString("matricule"),
                            rsE.getString("nom"),
                            rsE.getString("prenom"),
                            rsE.getString("photo_url"),
                            rsE.getInt("id_site"),
                            rsE.getString("site_code"),
                            rsE.getString("site_nom"),
                            rsE.getBoolean("horaire_continu"),
                            codeCat,
                            isCadre,
                            estAutonome,
                            dateDebutTroncon,
                            dateFinTroncon,
                            idPeriode,
                            null,
                            null,
                            null,
                            nbOuvrables,
                            siteDeplacementRefMap,
                            params,
                            c
                    );
                    dto.setHeuresNormales(seuilHeuresJour.multiply(BigDecimal.valueOf(nbOuvrables)).doubleValue());

                    list.add(dto);
                }
            }
        }

        list.sort(Comparator.comparing(PointageSyntheseMensuelleDTO::getNom, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(PointageSyntheseMensuelleDTO::getPrenom, Comparator.nullsLast(String::compareToIgnoreCase)));

        return list;
    }

    /**
     * Valide et fige une semaine de pointage dans pointage_semaine_calcule
     */
    public void validerSemaine(int idPeriode, LocalDate dateDebutTroncon, LocalDate dateFinTroncon, Integer idSite, Integer currentUserId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                validerSemaine(idPeriode, dateDebutTroncon, dateFinTroncon, idSite, currentUserId, c);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void validerSemaine(int idPeriode, LocalDate dateDebutTroncon, LocalDate dateFinTroncon, Integer idSite, Integer currentUserId, Connection c) throws SQLException {
        if (idPeriode <= 0 || dateDebutTroncon == null || dateFinTroncon == null) return;

        // Contrôle strict de workflow : la période ne doit être ni clôturée ni validée par la DAF
        String sqlCheck = "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ?";
        try (PreparedStatement psC = c.prepareStatement(sqlCheck)) {
            psC.setInt(1, idPeriode);
            try (ResultSet rsC = psC.executeQuery()) {
                if (rsC.next()) {
                    String st = rsC.getString("code");
                    org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                    boolean isAdminOrDaf = auth != null && auth.getAuthorities().stream().anyMatch(a ->
                            "ROLE_ADMIN".equals(a.getAuthority()) || "POINTAGE_VALIDATE".equals(a.getAuthority())
                    );

                    if ("CLOTUREE".equalsIgnoreCase(st) || "VALIDEE".equalsIgnoreCase(st)) {
                        throw new IllegalStateException("Action impossible : La période de paie est en statut '" + st + "'. Vous ne pouvez pas modifier ou valider les semaines d'une période clôturée ou validée.");
                    }
                    if (("A_VALIDER".equalsIgnoreCase(st) || "TRANSMIS_DAF".equalsIgnoreCase(st)) && !isAdminOrDaf) {
                        throw new IllegalStateException("Action impossible : La période de paie est transmise pour validation. Seuls les administrateurs autorisés peuvent recalculer les semaines.");
                    }
                }
            }
        }

        upsertSemaineCalculee(idPeriode, dateDebutTroncon, dateFinTroncon, idSite, currentUserId, c);
    }

    public void upsertSemaineCalculee(int idPeriode, LocalDate dateDebutTroncon, LocalDate dateFinTroncon, Integer idSite, Integer currentUserId, Connection c) throws SQLException {
        mg.galaxam.sirh.model.paieconfig.PaieConfigLegale legalConfig;
        try {
            legalConfig = mg.galaxam.sirh.model.paieconfig.PaieConfigLegale.findActiveAt(c, dateDebutTroncon);
        } catch (Exception e) {
            throw new IllegalStateException("MAJORATION_CONFIGURATION_INVALID: " + e.getMessage(), e);
        }
        if (legalConfig == null || legalConfig.getIdConfigLegale() == null) {
            throw new IllegalStateException("MAJORATION_CONFIGURATION_INVALID: aucune configuration légale applicable");
        }
        BigDecimal majorationHs30;
        BigDecimal majorationHs50;
        BigDecimal majorationNuit;
        BigDecimal majorationDimanche;
        BigDecimal majorationFerie;
        try {
            int configId = legalConfig.getIdConfigLegale();
            majorationHs30 = RefTauxMajoration.resolveApplicable(configId, "HS_30", dateDebutTroncon, c).getTaux();
            majorationHs50 = RefTauxMajoration.resolveApplicable(configId, "HS_50", dateDebutTroncon, c).getTaux();
            majorationNuit = RefTauxMajoration.resolveApplicable(configId, "NUIT", dateDebutTroncon, c).getTaux();
            majorationDimanche = RefTauxMajoration.resolveApplicable(configId, "DIMANCHE", dateDebutTroncon, c).getTaux();
            majorationFerie = RefTauxMajoration.resolveApplicable(configId, "FERIE", dateDebutTroncon, c).getTaux();
        } catch (Exception e) {
            throw new IllegalStateException("MAJORATION_CONFIGURATION_INVALID: " + e.getMessage(), e);
        }

        List<PointageSyntheseMensuelleDTO> dtos = getSyntheseSemaineDetail(idPeriode, dateDebutTroncon, dateFinTroncon, idSite, c);

        String sqlUpsert = """
            INSERT INTO pointage_semaine_calcule (
                id_employe, id_periode_paie, debut_semaine, nb_jours_troncon, seuil_heures_troncon,
                total_heures_travaillees, heures_retard, heures_hs_brutes, heures_hs_nettes,
                heures_deficit, taux_hs_30_applique, taux_hs_50_applique, taux_nuit_applique,
                taux_dimanche_applique, taux_ferie_applique, heures_hs_30, heures_hs_50, gele_par, gele_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id_employe, id_periode_paie, debut_semaine)
            DO UPDATE SET
                nb_jours_troncon = EXCLUDED.nb_jours_troncon,
                seuil_heures_troncon = EXCLUDED.seuil_heures_troncon,
                total_heures_travaillees = EXCLUDED.total_heures_travaillees,
                heures_retard = EXCLUDED.heures_retard,
                heures_hs_brutes = EXCLUDED.heures_hs_brutes,
                heures_hs_nettes = EXCLUDED.heures_hs_nettes,
                heures_deficit = EXCLUDED.heures_deficit,
                taux_hs_30_applique = EXCLUDED.taux_hs_30_applique,
                taux_hs_50_applique = EXCLUDED.taux_hs_50_applique,
                taux_nuit_applique = EXCLUDED.taux_nuit_applique,
                taux_dimanche_applique = EXCLUDED.taux_dimanche_applique,
                taux_ferie_applique = EXCLUDED.taux_ferie_applique,
                heures_hs_30 = EXCLUDED.heures_hs_30,
                heures_hs_50 = EXCLUDED.heures_hs_50,
                gele_par = EXCLUDED.gele_par,
                gele_at = CURRENT_TIMESTAMP
        """;

        try (PreparedStatement ps = c.prepareStatement(sqlUpsert)) {
            for (PointageSyntheseMensuelleDTO dto : dtos) {
                double hsNettes = dto.isEstCadre() ? 0.0 : (dto.getHeuresHs30() + dto.getHeuresHs50());

                ps.setInt(1, dto.getIdEmploye());
                ps.setInt(2, idPeriode);
                ps.setDate(3, Date.valueOf(dateDebutTroncon));
                ps.setInt(4, dto.getNbJoursOuvrables());
                ps.setDouble(5, dto.getHeuresNormales());
                ps.setDouble(6, dto.getTotalHeuresTravaillees());
                // Colonne historique en heures décimales : conversion confinée à la persistance legacy.
                ps.setDouble(7, PointageTimeBoundaryMapper.toLegacyDecimalHours(dto.getTotalMinutesRetard()));
                ps.setDouble(8, dto.getSurplusBrut());
                ps.setDouble(9, hsNettes);
                ps.setDouble(10, dto.getHeuresAbsenceRetenues());
                ps.setBigDecimal(11, majorationHs30.movePointRight(2));
                ps.setBigDecimal(12, majorationHs50.movePointRight(2));
                ps.setBigDecimal(13, majorationNuit.movePointRight(2));
                ps.setBigDecimal(14, majorationDimanche.movePointRight(2));
                ps.setBigDecimal(15, majorationFerie.movePointRight(2));
                ps.setDouble(16, dto.getHeuresHs30());
                ps.setDouble(17, dto.getHeuresHs50());
                if (currentUserId != null) ps.setInt(18, currentUserId); else ps.setNull(18, java.sql.Types.INTEGER);

                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Recalcule automatiquement la semaine tronçon dans pointage_semaine_calcule après une rectification journalière,
     * si cette semaine était déjà calculée.
     */
    public void recalculerSemainePourDate(int idPeriode, LocalDate datePtg, Integer idSite, Connection c) throws SQLException {
        if (idPeriode <= 0 || datePtg == null) return;

        LocalDate dateDebutPeriode = null;
        LocalDate dateFinPeriode = null;
        String sqlPer = "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?";
        try (PreparedStatement psP = c.prepareStatement(sqlPer)) {
            psP.setInt(1, idPeriode);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    dateDebutPeriode = rsP.getDate("date_debut").toLocalDate();
                    dateFinPeriode = rsP.getDate("date_fin").toLocalDate();
                }
            }
        }
        if (dateDebutPeriode == null || dateFinPeriode == null) return;
        if (datePtg.isBefore(dateDebutPeriode) || datePtg.isAfter(dateFinPeriode)) return;

        LocalDate debutLundi = datePtg.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate finDimanche = datePtg.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate debutTroncon = debutLundi.isBefore(dateDebutPeriode) ? dateDebutPeriode : debutLundi;
        LocalDate finTroncon = finDimanche.isAfter(dateFinPeriode) ? dateFinPeriode : finDimanche;

        // Vérifier si cette semaine a déjà été calculée dans pointage_semaine_calcule
        String sqlCheck = "SELECT COUNT(*) FROM pointage_semaine_calcule WHERE id_periode_paie = ? AND debut_semaine = ?";
        boolean hasSemaineCalculee = false;
        try (PreparedStatement psCheck = c.prepareStatement(sqlCheck)) {
            psCheck.setInt(1, idPeriode);
            psCheck.setDate(2, Date.valueOf(debutTroncon));
            try (ResultSet rsCheck = psCheck.executeQuery()) {
                if (rsCheck.next() && rsCheck.getInt(1) > 0) {
                    hasSemaineCalculee = true;
                }
            }
        }

        if (hasSemaineCalculee) {
            upsertSemaineCalculee(idPeriode, debutTroncon, finTroncon, idSite, null, c);
        }
    }

    /**
     * Dévalide / déverrouille une semaine (avec contrôle strict du statut de la période)
     */
    public void devaliderSemaine(int idPeriode, LocalDate dateDebutTroncon) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            // Contrôle strict de workflow : la période ne doit être ni clôturée ni transmise/validée par la DAF
            String sqlCheck = "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ?";
            try (PreparedStatement psC = c.prepareStatement(sqlCheck)) {
                psC.setInt(1, idPeriode);
                try (ResultSet rsC = psC.executeQuery()) {
                    if (rsC.next()) {
                        String st = rsC.getString("code");
                        if ("CLOTUREE".equalsIgnoreCase(st) || "VALIDEE".equalsIgnoreCase(st) || "A_VALIDER".equalsIgnoreCase(st) || "TRANSMIS_DAF".equalsIgnoreCase(st)) {
                            throw new IllegalStateException("Action impossible : La période de paie est en statut '" + st + "'. Vous devez d'abord réouvrir la période avant de déverrouiller une semaine.");
                        }
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pointage_semaine_calcule WHERE id_periode_paie = ? AND debut_semaine = ?")) {
                ps.setInt(1, idPeriode);
                ps.setDate(2, Date.valueOf(dateDebutTroncon));
                ps.executeUpdate();
            }
        }
    }

    /**
     * Calcule et actualise en une seule transaction atomique l'ensemble des semaines de la periode,
     * sans modifier son statut (reste EN_COURS pour consultation et corrections libres par l'agent).
     */
    public Map<String, Object> calculerToutesSemainesPeriode(int idPeriode, Integer idSite, Integer currentUserId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Map<String, Object> res = calculerToutesSemainesPeriode(idPeriode, idSite, currentUserId, c);
                c.commit();
                return res;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public Map<String, Object> calculerToutesSemainesPeriode(int idPeriode, Integer idSite, Integer currentUserId, Connection c) throws SQLException {
        List<Map<String, Object>> semaines = getSemainesPeriode(idPeriode, idSite, c);
        int nbSemainesCalculees = 0;

        for (Map<String, Object> sem : semaines) {
            String strDeb = (String) sem.get("dateDebut");
            String strFin = (String) sem.get("dateFin");
            if (strDeb != null && strFin != null) {
                LocalDate dDeb = LocalDate.parse(strDeb);
                LocalDate dFin = LocalDate.parse(strFin);
                validerSemaine(idPeriode, dDeb, dFin, idSite, currentUserId, c);
                nbSemainesCalculees++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("nbSemaines", nbSemainesCalculees);
        result.put("message", nbSemainesCalculees + " semaine(s) calculee(s) et actualisee(s) avec succes.");
        return result;
    }
}
