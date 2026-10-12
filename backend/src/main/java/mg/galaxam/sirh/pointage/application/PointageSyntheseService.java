package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageTronconSemaineDTO;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalAttendanceEngine;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalAttendanceEngine.WorkInterval;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalAttendanceEngine.WorkIntervalFactory;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalAttendanceEngine.TemporalBucket;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalAttendanceEngine.AttendanceTemporalPartitioner;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalAttendanceEngine.OvertimeProjectionEngine;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.NightWorkType;
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
import java.time.LocalDateTime;
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

    private record PeriodTotals(long workedMinutes, long overtime30Minutes, long overtime50Minutes,
                                long nightMinutes, long sundayMinutes, long publicHolidayMinutes,
                                long absenceMinutes, long lateMinutes, int mealDays,
                                BigDecimal mealAmount, BigDecimal travelUnits, BigDecimal travelAmount) {}

    private static PeriodTotals aggregateWeeks(List<AttendanceEngine.WeekResult> weeks) {
        List<AttendanceEngine.WeekResult> safeWeeks = weeks == null ? List.of() : weeks;
        List<AttendanceEngine.Result> days = safeWeeks.stream().flatMap(week -> week.days().stream()).toList();
        return new PeriodTotals(
                safeWeeks.stream().mapToLong(AttendanceEngine.WeekResult::workedMinutes).sum(),
                safeWeeks.stream().mapToLong(AttendanceEngine.WeekResult::overtime30Minutes).sum(),
                safeWeeks.stream().mapToLong(AttendanceEngine.WeekResult::overtime50Minutes).sum(),
                days.stream().mapToLong(AttendanceEngine.Result::nightMinutes).sum(),
                days.stream().filter(AttendanceEngine.Result::sunday).mapToLong(AttendanceEngine.Result::workedMinutes).sum(),
                days.stream().filter(AttendanceEngine.Result::publicHoliday).mapToLong(AttendanceEngine.Result::workedMinutes).sum(),
                safeWeeks.stream().mapToLong(AttendanceEngine.WeekResult::absenceMinutes).sum(),
                safeWeeks.stream().mapToLong(AttendanceEngine.WeekResult::lateMinutes).sum(),
                days.stream().mapToInt(AttendanceEngine.Result::mealDays).sum(),
                days.stream().map(AttendanceEngine.Result::mealAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                days.stream().map(AttendanceEngine.Result::travelUnits).reduce(BigDecimal.ZERO, BigDecimal::add),
                days.stream().map(AttendanceEngine.Result::travelAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static long hoursToMinutes(BigDecimal hours) {
        if (hours == null) return 0;
        return hours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static double minutesToHours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP).doubleValue();
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static LocalDate laterDate(Date first, Date second) {
        LocalDate a = first == null ? null : first.toLocalDate();
        LocalDate b = second == null ? null : second.toLocalDate();
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate earlierNonNullDate(Date first, Date second) {
        LocalDate a = first == null ? null : first.toLocalDate();
        LocalDate b = second == null ? null : second.toLocalDate();
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private static BigDecimal requireAllowanceParameter(
            Map<String, ParametreGlobal.ParametreEtat> params, String code,
            int siteId, LocalDate date, int employeeId) {
        ParametreGlobal.ParametreResolution resolution = ParametreGlobal.resolve(params, code);
        if (resolution.isDisabled()) return BigDecimal.ZERO;
        BigDecimal value = null;
        if (resolution.isActive()) {
            try {
                value = new BigDecimal(resolution.valeur().trim());
            } catch (RuntimeException exception) {
                value = null;
            }
        }
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new SirhBusinessException(
                    SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                    "Configuration d'indemnité absente : " + code + ".",
                    Map.of("type", code, "siteId", siteId, "date", date, "employeeId", employeeId),
                    List.of());
        }
        return value;
    }

    private static AttendanceEngine.Result.Status statusFromCode(String code) {
        if (code == null) return AttendanceEngine.Result.Status.ANOMALY;
        return switch (code) {
            case "CONGE" -> AttendanceEngine.Result.Status.LEAVE;
            case "REPOS" -> AttendanceEngine.Result.Status.REST;
            case "FERIE_CHOME" -> AttendanceEngine.Result.Status.PUBLIC_HOLIDAY_OFF;
            case "FERIE_TRAVAILLE" -> AttendanceEngine.Result.Status.PUBLIC_HOLIDAY_WORKED;
            case "DIMANCHE_TRAVAILLE" -> AttendanceEngine.Result.Status.SUNDAY_WORKED;
            case "DISPENSE_POINTAGE" -> AttendanceEngine.Result.Status.AUTONOMOUS;
            case "DEMI_JOURNEE" -> AttendanceEngine.Result.Status.HALF_DAY;
            case "ABSENCE_NON_JUSTIFIEE", "ABSENCE_JUSTIFIEE" -> AttendanceEngine.Result.Status.ABSENT;
            case "ANOMALIE_SAISIE" -> AttendanceEngine.Result.Status.ANOMALY;
            default -> AttendanceEngine.Result.Status.PRESENT;
        };
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PointageAjustementPeriodeService ajustementPeriodeService;

    @Autowired
    private mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine calendrierEngine;

    @Autowired
    private AttendanceEngine weeklyAttendanceCalculator;

    @Autowired
    private AttendanceEngine dailyAttendanceCalculator;

    private final mg.galaxam.sirh.contrat.application.EmploymentContextResolver employmentContextResolver =
            new mg.galaxam.sirh.contrat.application.EmploymentContextResolver();

    public PointageSyntheseService() {
        this.calendrierEngine = new CalendrierOuvrableEngine();
        this.weeklyAttendanceCalculator = new AttendanceEngine();
        this.dailyAttendanceCalculator = new AttendanceEngine();
    }

    public PointageSyntheseService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.calendrierEngine = new CalendrierOuvrableEngine();
        this.weeklyAttendanceCalculator = new AttendanceEngine();
        this.dailyAttendanceCalculator = new AttendanceEngine();
    }

    public PointageSyntheseService(DataSource dataSource, mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine calendrierEngine) {
        this.dataSource = dataSource;
        this.calendrierEngine = calendrierEngine != null ? calendrierEngine : new CalendrierOuvrableEngine();
        this.weeklyAttendanceCalculator = new AttendanceEngine();
        this.dailyAttendanceCalculator = new AttendanceEngine();
    }

    public static boolean isCadreCategorie(String codeCat) {
        if (codeCat == null) return false;
        String c = codeCat.trim().toUpperCase();
        return c.contains("CADRE") || "C".equals(c) || "HC".equals(c) || "DIRIGEANT".equals(c);
    }

    /** Indemnités journalières par défaut d'un autonome, après application des dérogations datées. */
    private Map<String, BigDecimal> loadDefaultAutonomousDailyAllowanceAmounts(
            int employeeId, int siteId, LocalDate day, int workingDays, Integer pointageId, Connection c) throws SQLException {
        String savedAllowanceFilter = pointageId == null ? "" : "AND NOT EXISTS (SELECT 1 FROM pointage_indemnite_jour saved "
                + "WHERE saved.id_pointage = ? AND saved.id_indemnite = c.id_indemnite) ";
        String sql = "SELECT i.code, c.periodicite, c.montant_reference "
                + "FROM site_indemnite_config c JOIN ref_indemnite i ON i.id_indemnite = c.id_indemnite "
                + "WHERE c.id_site = ? AND c.est_actif = TRUE AND i.est_actif = TRUE "
                + "AND c.periodicite <> 'FIXE_MENSUEL' AND c.montant_reference > 0 "
                + "AND c.date_debut <= ? AND (c.date_fin IS NULL OR c.date_fin >= ?) "
                + "AND NOT EXISTS (SELECT 1 FROM employe_indemnite_derogation d "
                + "WHERE d.id_employe = ? AND d.id_indemnite = c.id_indemnite AND d.beneficie = FALSE "
                + "AND d.date_debut <= ? AND (d.date_fin IS NULL OR d.date_fin >= ?)) "
                + savedAllowanceFilter
                + "ORDER BY i.code";
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        Date sqlDay = Date.valueOf(day);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, siteId);
            ps.setDate(2, sqlDay);
            ps.setDate(3, sqlDay);
            ps.setInt(4, employeeId);
            ps.setDate(5, sqlDay);
            ps.setDate(6, sqlDay);
            if (pointageId != null) ps.setInt(7, pointageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal reference = nullToZero(rs.getBigDecimal("montant_reference"));
                    BigDecimal amount = switch (rs.getString("periodicite").toUpperCase(java.util.Locale.ROOT)) {
                        case "JOURNALIER", "FORFAIT" -> reference;
                        case "MENSUEL", "MENSUEL_PRORATISE" -> workingDays > 0
                                ? calendrierEngine.calculerProrataJournalier(reference, workingDays)
                                : BigDecimal.ZERO;
                        default -> BigDecimal.ZERO;
                    };
                    if (amount.signum() > 0) amounts.merge(rs.getString("code"), amount, BigDecimal::add);
                }
            }
        }
        return amounts;
    }

    private static String defaultTravelCode(Map<String, BigDecimal> amounts) {
        return List.of("DPLCT", "DEPLACEMENT", "TRANSPORT").stream()
                .filter(code -> amounts.getOrDefault(code, BigDecimal.ZERO).signum() > 0)
                .findFirst().orElse(null);
    }

    private static BigDecimal defaultTravelAmount(Map<String, BigDecimal> amounts) {
        String code = defaultTravelCode(amounts);
        return code == null ? BigDecimal.ZERO : amounts.get(code);
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
        return calculerSyntheseEmploye(idEmp, matricule, nom, prenom, photoUrl, idSite, siteCode, siteNom,
                null, codeCat, isCadre, estAutonome, dateDebut, dateFin, null, null, idPeriode,
                codePeriode, libellePeriode, statutPeriode, nbJoursOuvrablesPeriode, siteDeplacementRefMap, params, c);
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
        return calculerSyntheseEmploye(idEmp, matricule, nom, prenom, photoUrl, idSite, siteCode, siteNom,
                horaireContinu, codeCat, isCadre, estAutonome, dateDebut, dateFin, null, null, idPeriode,
                codePeriode, libellePeriode, statutPeriode, nbJoursOuvrablesPeriode, siteDeplacementRefMap, params, c);
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
            LocalDate dateDebutEmploi,
            LocalDate dateFinEmploi,
            Integer idPeriode,
            String codePeriode,
            String libellePeriode,
            String statutPeriode,
            int nbJoursOuvrablesPeriode,
            Map<Integer, BigDecimal> siteDeplacementRefMap,
            Map<String, String> params,
            Connection c
    ) throws SQLException {
        return calculerSyntheseEmploye(idEmp, matricule, nom, prenom, photoUrl, idSite, siteCode, siteNom,
                horaireContinu, codeCat, isCadre, estAutonome, dateDebut, dateFin, dateDebutEmploi, dateFinEmploi,
                idPeriode, codePeriode, libellePeriode, statutPeriode, nbJoursOuvrablesPeriode,
                siteDeplacementRefMap, params, null, c);
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
            LocalDate dateDebutEmploi,
            LocalDate dateFinEmploi,
            Integer idPeriode,
            String codePeriode,
            String libellePeriode,
            String statutPeriode,
            int nbJoursOuvrablesPeriode,
            Map<Integer, BigDecimal> siteDeplacementRefMap,
            Map<String, String> params,
            Map<String, ParametreGlobal.ParametreEtat> parameterStates,
            Connection c
    ) throws SQLException {
        if (dateDebut == null || dateFin == null || dateFin.isBefore(dateDebut)) {
            log.warn("[PointageSyntheseService] Dates de période invalides pour l'employé {}: debut={}, fin={}", idEmp, dateDebut, dateFin);
            PointageSyntheseMensuelleDTO emptyDto = new PointageSyntheseMensuelleDTO();
            emptyDto.setIdEmploye(idEmp);
            emptyDto.setIdPeriodePaie(idPeriode);
            return emptyDto;
        }
        PointageTemporalConfigurationGuard.verify(idEmp, dateDebut, dateFin, c);
        Map<LocalDate, mg.galaxam.sirh.contrat.application.EmploymentContextResolver.PointageDay> contextesJournaliers =
                employmentContextResolver.resolvePointageDays(idEmp, dateDebut, dateFin, c);
        Map<Integer, Set<LocalDate>> joursChomesParSite = new HashMap<>();
        Set<LocalDate> joursChomesEmploye = new HashSet<>();
        for (var contexte : contextesJournaliers.entrySet()) {
            Set<LocalDate> joursChomesSite = joursChomesParSite.computeIfAbsent(contexte.getValue().siteId(),
                    site -> calendrierEngine.getDatesFeriesEtChomees(dateDebut, dateFin, site, c));
            if (joursChomesSite.contains(contexte.getKey())) joursChomesEmploye.add(contexte.getKey());
        }
        int joursOuvrablesReels = calendrierEngine.compterJoursOuvrables(dateDebut, dateFin, idSite, c);
        if (joursOuvrablesReels > 0) nbJoursOuvrablesPeriode = joursOuvrablesReels;
        BigDecimal seuilHsTranche1;
        try {
            mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale leg =
                    mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale.findActiveAt(c, dateDebut);
            if (leg == null || leg.getSeuilHsTranche1() == null
                    || leg.getSeuilHsTranche1().signum() < 0) {
                throw new SirhBusinessException(SirhErrorCode.LEGAL_CONFIG_MISSING,
                        "Première tranche HS absente ou invalide pour le " + dateDebut + ".");
            }
            seuilHsTranche1 = leg.getSeuilHsTranche1();
        } catch (SirhBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new SirhBusinessException(SirhErrorCode.LEGAL_CONFIG_MISSING,
                    "Impossible de charger la première tranche HS pour le " + dateDebut + " : " + e.getMessage());
        }
        Map<String, ParametreGlobal.ParametreEtat> effectiveParameterStates = parameterStates != null
                ? parameterStates : ParametreGlobal.loadEtatMapAtDate(Date.valueOf(dateDebut), c);
        BigDecimal seuilHeuresJour = BigDecimal.valueOf(
                PointageService.resolveDailyTargetHours(effectiveParameterStates));

        // Montant de référence mensuel pour le site (ex: 53 200 Ar si horaire discontinu, 31 200 Ar si horaire continu)
        BigDecimal refDeplacementSite = BigDecimal.ZERO;
        if (siteDeplacementRefMap != null && siteDeplacementRefMap.containsKey(idSite)) {
            refDeplacementSite = siteDeplacementRefMap.get(idSite);
        } else {
            String sqlSiteInd = "SELECT c.montant_reference FROM site_indemnite_config c " +
                                "JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite " +
                                "WHERE c.id_site = ? AND i.code IN ('DPLCT', 'DEPLACEMENT', 'TRANSPORT') AND c.est_actif = TRUE AND c.date_debut<=? AND (c.date_fin IS NULL OR c.date_fin>=?)";
            try (PreparedStatement psSI = c.prepareStatement(sqlSiteInd)) {
                psSI.setInt(1, idSite);
                psSI.setDate(2, Date.valueOf(dateDebut));
                psSI.setDate(3, Date.valueOf(dateDebut));
                try (ResultSet rsSI = psSI.executeQuery()) {
                    if (rsSI.next()) {
                        refDeplacementSite = nullToZero(rsSI.getBigDecimal("montant_reference"));
                        if (rsSI.next()) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: DEPLACEMENT au " + dateDebut);
                    }
                }
            }
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
        dto.setDispensePointage(contextesJournaliers.containsKey(dateFin)
                && contextesJournaliers.get(dateFin).dispensePointage());
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
                        "    AND pij_dep.id_indemnite IN (SELECT id_indemnite FROM ref_indemnite WHERE code IN ('DPLCT', 'DEPLACEMENT', 'TRANSPORT') LIMIT 1) " +
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
        Map<String, BigDecimal> indemnitesAutonomesSansPointage = new LinkedHashMap<>();
        int totalMinsRetard = 0;
        int nbAnomalies = 0;

        // Regroupement par semaine civile ISO (Lundi du tronçon)
        Map<LocalDate, List<AttendanceEngine.Result>> parSemaine = new LinkedHashMap<>();
        Set<LocalDate> datesAvecPointage = new HashSet<>();

        // Une semaine sans aucun badge reste une semaine à contrôler : son objectif
        // doit produire une absence, et non disparaître de la synthèse.
        for (LocalDate day = dateDebut; !day.isAfter(dateFin); day = day.plusDays(1)) {
            LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            parSemaine.computeIfAbsent(monday, ignored -> new ArrayList<>());
        }

        record CongeValideInfo(int idDemande, boolean estDemi, String libelle) {}
        Map<LocalDate, CongeValideInfo> congesValidesEmploye = new HashMap<>();
        String sqlCongesPeriode = "SELECT dc.id_demande_conge, dc.date_debut, dc.date_fin, dc.est_demi_journee, tc.libelle " +
                "FROM demande_conge dc " +
                "JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
                "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                "WHERE dc.id_employe = ? AND sc.code IN ('VALIDE', 'APPROUVEE') " +
                "AND dc.date_debut <= ? AND dc.date_fin >= ?";
        try (PreparedStatement psCP = c.prepareStatement(sqlCongesPeriode)) {
            psCP.setInt(1, idEmp);
            psCP.setDate(2, Date.valueOf(dateFin));
            psCP.setDate(3, Date.valueOf(dateDebut));
            try (ResultSet rsCP = psCP.executeQuery()) {
                while (rsCP.next()) {
                    int idDemandeVal = rsCP.getInt("id_demande_conge");
                    LocalDate dDeb = rsCP.getDate("date_debut").toLocalDate();
                    LocalDate dFin = rsCP.getDate("date_fin").toLocalDate();
                    boolean estDemi = rsCP.getBoolean("est_demi_journee");
                    String typeLib = rsCP.getString("libelle");
                    LocalDate from = dDeb.isBefore(dateDebut) ? dateDebut : dDeb;
                    LocalDate to = dFin.isAfter(dateFin) ? dateFin : dFin;
                    for (LocalDate cur = from; !cur.isAfter(to); cur = cur.plusDays(1)) {
                        congesValidesEmploye.put(cur, new CongeValideInfo(idDemandeVal, estDemi, typeLib));
                    }
                }
            }
        }

        try (PreparedStatement psPtg = c.prepareStatement(sqlPtg)) {
            psPtg.setInt(1, idEmp);
            psPtg.setDate(2, Date.valueOf(dateDebut));
            psPtg.setDate(3, Date.valueOf(dateFin));
            try (ResultSet rsPtg = psPtg.executeQuery()) {
                while (rsPtg.next()) {
                    LocalDate dPointage = rsPtg.getDate("date_pointage").toLocalDate();
                    boolean autonomeCeJour = contextesJournaliers.containsKey(dPointage)
                            && contextesJournaliers.get(dPointage).dispensePointage();
                    datesAvecPointage.add(dPointage);
                    boolean isWeekend = CalendrierOuvrableEngine.isWeekend(dPointage);
                    boolean isSunday = (dPointage.getDayOfWeek() == DayOfWeek.SUNDAY);
                    String stCode = rsPtg.getString("statut_code");
                    double th = rsPtg.getDouble("total_heures");

                    CongeValideInfo congeValide = congesValidesEmploye.get(dPointage);
                    if (congeValide != null && (th <= 0.0 || "ABSENCE_NON_JUSTIFIEE".equals(stCode) || "ABSENCE_JUSTIFIEE".equals(stCode) || "ANOMALIE_SAISIE".equals(stCode))) {
                        stCode = congeValide.estDemi() ? "DEMI_JOURNEE" : "CONGE";
                    }

                    boolean isAbsenceOuConge = "CONGE".equals(stCode) || "ABSENCE_NON_JUSTIFIEE".equals(stCode) || "ABSENCE_JUSTIFIEE".equals(stCode) || "FERIE_CHOME".equals(stCode);
                    if ("DISPENSE_POINTAGE".equals(stCode) || (autonomeCeJour && !isAbsenceOuConge)) {
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

                    if ("PRESENT".equals(stCode) || "DISPENSE_POINTAGE".equals(stCode) || "SAMEDI_TRAVAILLE".equals(stCode) || "RETARD".equals(stCode)) {
                        presents++;
                    } else if ("DEMI_JOURNEE".equals(stCode)) {
                        presents++;
                        conges += 0.5;
                    } else if ("DIMANCHE_TRAVAILLE".equals(stCode) || (isSunday && th > 0)) {
                        presents++;
                        dimanchesTravailles++;
                    } else if ("CONGE".equals(stCode)) {
                        conges++;
                    } else if ("ABSENCE_NON_JUSTIFIEE".equals(stCode) || "ABSENCE_JUSTIFIEE".equals(stCode)) {
                        absences++;
                    } else if ("FERIE_CHOME".equals(stCode)) {
                        feriesChomes++;
                    } else if ("FERIE_TRAVAILLE".equals(stCode)) {
                        presents++;
                        feriesTravailles++;
                    } else if ("ANOMALIE_SAISIE".equals(stCode)) {
                        nbAnomalies++;
                    }

                    totalMinsRetard += (autonomeCeJour ? 0 : ret);

                    // Saisie quotidienne est la vérité finale depuis pointage_indemnite_jour
                    Object dynRepasObj = rsPtg.getObject("dyn_repas_accorde");
                    boolean repasEffectif = (dynRepasObj != null) && rsPtg.getBoolean("dyn_repas_accorde");

                    boolean estPresentAutonome = autonomeCeJour && !isWeekend && !isAbsenceOuConge && !"REPOS".equals(stCode);
                    boolean estTravailleOuPresent = ("PRESENT".equals(stCode) || "SAMEDI_TRAVAILLE".equals(stCode) 
                            || "DIMANCHE_TRAVAILLE".equals(stCode) || "FERIE_TRAVAILLE".equals(stCode)
                            || "DEMI_JOURNEE".equals(stCode)
                            || estPresentAutonome || th > 0.0);

                    Map<String, BigDecimal> defaultsAutonomes = estPresentAutonome
                            ? loadDefaultAutonomousDailyAllowanceAmounts(idEmp,
                                    contextesJournaliers.get(dPointage).siteId(), dPointage,
                                    nbJoursOuvrablesPeriode, rsPtg.getInt("id_pointage"), c)
                            : Map.of();
                    defaultsAutonomes.forEach((code, amount) -> indemnitesAutonomesSansPointage.merge(code, amount, BigDecimal::add));
                    boolean aPrisRepasCeJour = estTravailleOuPresent && (repasEffectif
                            || (dynRepasObj == null && defaultsAutonomes.containsKey("REPAS")));
                    BigDecimal mealAmount = BigDecimal.ZERO;
                    if (aPrisRepasCeJour) {
                        repasCount++;
                        mealAmount = dynRepasObj == null
                                ? defaultsAutonomes.getOrDefault("REPAS", BigDecimal.ZERO)
                                : nullToZero(rsPtg.getBigDecimal("dyn_repas_montant"));
                        montantRepasTotal = montantRepasTotal.add(mealAmount);
                    }

                    // Détermination du déplacement : source de vérité pointage_indemnite_jour
                    BigDecimal fractionJour = BigDecimal.ZERO;
                    Object dynDepQteObj = rsPtg.getObject("dyn_dep_quantite");
                    if (dynDepQteObj != null) {
                        if (estTravailleOuPresent && rsPtg.getBoolean("dyn_dep_accorde")) {
                            fractionJour = nullToZero(rsPtg.getBigDecimal("dyn_dep_quantite"));
                        }
                    } else if (estTravailleOuPresent && estPresentAutonome) {
                        BigDecimal defaultTravelAmount = defaultTravelAmount(defaultsAutonomes);
                        if (defaultTravelAmount.signum() > 0) fractionJour = BigDecimal.ONE;
                    }
                    totalTrajets = totalTrajets.add(fractionJour);
                    BigDecimal travelAmount = fractionJour.signum() == 0 ? BigDecimal.ZERO
                            : dynDepQteObj == null ? defaultTravelAmount(defaultsAutonomes)
                                    : nullToZero(rsPtg.getBigDecimal("dyn_dep_montant"));
                    totalMntDeplacement = totalMntDeplacement.add(travelAmount);

                    LocalDate lundiSemaine = dPointage.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    parSemaine.computeIfAbsent(lundiSemaine, k -> new ArrayList<>());

                    parSemaine.get(lundiSemaine).add(new AttendanceEngine.Result(
                            idEmp, dPointage, statusFromCode(stCode), workedMinutes, 0, workedMinutes,
                            workedMinutes, autonomeCeJour ? 0 : ret, 0, nightMinutes, isSunday,
                            "FERIE_CHOME".equals(stCode) || "FERIE_TRAVAILLE".equals(stCode),
                            aPrisRepasCeJour ? 1 : 0, mealAmount, fractionJour, travelAmount, List.of()));
                }
            }
        }

        // Prise en compte des congés validés sur la période n'ayant pas encore de ligne dans pointage_jour
        for (Map.Entry<LocalDate, CongeValideInfo> entry : congesValidesEmploye.entrySet()) {
            LocalDate cur = entry.getKey();
            if (datesAvecPointage.contains(cur)) continue;
            datesAvecPointage.add(cur);

            CongeValideInfo cv = entry.getValue();
            boolean estDemi = cv.estDemi();
            String typeLib = cv.libelle();
            int idDemandeVal = cv.idDemande();

            var ctxJour = contextesJournaliers.get(cur);
            int curSite = ctxJour != null ? ctxJour.siteId() : idSite;

            // Si une période de paie existe et que le pointage n'a pas encore été matérialisé en base,
            // on le matérialise pour que pointage_jour soit synchronisé avec demande_conge
            if (idPeriode != null) {
                try (PreparedStatement psMat = c.prepareStatement(
                        "INSERT INTO pointage_jour (id_periode_paie, id_employe, id_site, date_pointage, id_statut_jour, id_demande_conge, " +
                        "total_heures, minutes_retard, heures_nuit, motif_ajustement, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), ?, 0.0, 0, 0.0, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                        "ON CONFLICT (id_employe, date_pointage) DO UPDATE SET " +
                        "id_statut_jour = (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), " +
                        "id_demande_conge = EXCLUDED.id_demande_conge, motif_ajustement = EXCLUDED.motif_ajustement, version = pointage_jour.version + 1")) {
                    String codeSt = estDemi ? "DEMI_JOURNEE" : "CONGE";
                    psMat.setInt(1, idPeriode);
                    psMat.setInt(2, idEmp);
                    psMat.setInt(3, curSite);
                    psMat.setDate(4, Date.valueOf(cur));
                    psMat.setString(5, codeSt);
                    psMat.setInt(6, idDemandeVal);
                    psMat.setString(7, "Congé validé : " + (typeLib != null ? typeLib : "Congé"));
                    psMat.setString(8, codeSt);
                    psMat.executeUpdate();
                } catch (Exception e) {
                    log.debug("[PointageSyntheseService] Notice matérialisation congé {}: {}", cur, e.getMessage());
                }
            }

            if (!CalendrierOuvrableEngine.isWeekend(cur) && !joursChomesEmploye.contains(cur)) {
                conges += (estDemi ? 0.5 : 1);
            }

            LocalDate lundiSemaine = cur.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            parSemaine.computeIfAbsent(lundiSemaine, k -> new ArrayList<>());
            parSemaine.get(lundiSemaine).add(new AttendanceEngine.Result(
                    idEmp, cur, estDemi ? AttendanceEngine.Result.Status.HALF_DAY : AttendanceEngine.Result.Status.LEAVE,
                    0, 0, 0, 0, 0, 0, 0, cur.getDayOfWeek() == DayOfWeek.SUNDAY,
                    joursChomesEmploye.contains(cur), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
        }

        // Les journées autonomes sans badge sont des résultats journaliers calculés,
        // jamais une seconde formule de synthèse.
        if (contextesJournaliers.values().stream().anyMatch(
                mg.galaxam.sirh.contrat.application.EmploymentContextResolver.PointageDay::dispensePointage)) {
            for (LocalDate day = dateDebut; !day.isAfter(dateFin); day = day.plusDays(1)) {
                if (datesAvecPointage.contains(day) || congesValidesEmploye.containsKey(day)
                        || CalendrierOuvrableEngine.isWeekend(day)) continue;
                var contexteJour = contextesJournaliers.get(day);
                if (contexteJour == null || !contexteJour.dispensePointage()) continue;
                boolean isPublicHoliday = calendrierEngine.getJourChomeInfo(day, contexteJour.siteId(), c).estChome();
                if (isPublicHoliday) continue;
                int dailySite = contexteJour.siteId();
                var allowanceStates = ParametreGlobal.loadEtatMapAtDate(Date.valueOf(day), c);
                BigDecimal mealRate = BigDecimal.ZERO, dailyTravelReference = BigDecimal.ZERO;
                Map<String, BigDecimal> defaultAllowances = loadDefaultAutonomousDailyAllowanceAmounts(
                        idEmp, dailySite, day, nbJoursOuvrablesPeriode, null, c);
                mealRate = defaultAllowances.getOrDefault("REPAS", BigDecimal.ZERO);
                dailyTravelReference = defaultTravelAmount(defaultAllowances);
                BigDecimal travelRate = dailyTravelReference;
                BigDecimal travelReduction = requireAllowanceParameter(allowanceStates,
                        "REDUCTION_DEPLACT_REPAS_MIDI", dailySite, day, idEmp);
                AttendanceEngine.Result autonomousDay = dailyAttendanceCalculator
                        .calculateDay(new AttendanceEngine.Context(
                                idEmp, day, null, null, null, null,
                                LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(17, 0),
                                PointageService.resolveOptionalTimeRule(allowanceStates,"SEUIL_NUIT_DEBUT"),
                                PointageService.resolveOptionalTimeRule(allowanceStates,"SEUIL_NUIT_FIN"),
                                false, true, isPublicHoliday,
                                AttendanceEngine.Context.LeavePart.NONE,
                                mealRate.signum()>0, mealRate, travelRate.signum() > 0 ? BigDecimal.ONE : BigDecimal.ZERO,
                                travelRate, travelReduction,
                                mealRate.signum() == 0));
                LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                parSemaine.computeIfAbsent(monday, ignored -> new ArrayList<>()).add(autonomousDay);
                datesAvecPointage.add(day);
                presents++;
                repasCount += autonomousDay.mealDays();
                montantRepasTotal = montantRepasTotal.add(autonomousDay.mealAmount());
                totalTrajets = totalTrajets.add(autonomousDay.travelUnits());
                totalMntDeplacement = totalMntDeplacement.add(autonomousDay.travelAmount());
                defaultAllowances.forEach((code, amount) -> indemnitesAutonomesSansPointage.merge(code, amount, BigDecimal::add));
            }
        }

        // Calcul de la compensation hebdomadaire par tronçon
        long totalHsBrutesMinutes = 0;
        long totalRetardCompenseMinutes = 0;
        long totalDeficitMinutes = 0;
        List<PointageTronconSemaineDTO> tronconsList = new ArrayList<>();
        List<AttendanceEngine.WeekResult> weeklyResults = new ArrayList<>();
        int semIndex = 1;

        for (Map.Entry<LocalDate, List<AttendanceEngine.Result>> entry : parSemaine.entrySet()) {
            LocalDate lundi = entry.getKey();
            List<AttendanceEngine.Result> jours = entry.getValue();

            // Compter les jours ouvrés et fériés chômés du tronçon via le moteur centralisé
            LocalDate finTroncon = lundi.plusDays(6);
            if (finTroncon.isAfter(dateFin)) {
                finTroncon = dateFin;
            }
            LocalDate debutTroncon = lundi.isBefore(dateDebut) ? dateDebut : lundi;

            int joursOuvresTroncon = 0;
            int joursFerieTroncon = 0;
            for (LocalDate cur = debutTroncon; !cur.isAfter(finTroncon); cur = cur.plusDays(1)) {
                if (!contextesJournaliers.containsKey(cur) || CalendrierOuvrableEngine.isWeekend(cur)) continue;
                if (joursChomesEmploye.contains(cur)) joursFerieTroncon++;
                else joursOuvresTroncon++;
            }

            // Décompter les congés validés sur les jours ouvrés non fériés du tronçon
            // (évite le cumul : un jour férié chômé ne peut pas être décompté deux fois)
            double joursCongeTroncon = 0.0;
            for (AttendanceEngine.Result j : jours) {
                if (!contextesJournaliers.containsKey(j.date())) continue;
                if (!CalendrierOuvrableEngine.isWeekend(j.date()) && !joursChomesEmploye.contains(j.date())) {
                    if (j.status() == AttendanceEngine.Result.Status.LEAVE) {
                        joursCongeTroncon += 1.0;
                    } else if (j.status() == AttendanceEngine.Result.Status.HALF_DAY) {
                        joursCongeTroncon += 0.5;
                    }
                }
            }

            // Seuil d'heures attendu de la semaine abaissé proportionnellement aux congés
            double joursRequis = Math.max(0.0, (double) joursOuvresTroncon - joursCongeTroncon);
            long expectedMinutes = seuilHeuresJour
                    .multiply(BigDecimal.valueOf(joursRequis)).multiply(BigDecimal.valueOf(60))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            long configuredFirstBandMinutes = seuilHsTranche1.multiply(BigDecimal.valueOf(60))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            long nominalWeekMinutes = seuilHeuresJour.multiply(BigDecimal.valueOf(
                    CalendrierOuvrableEngine.calculerJoursOuvrables(lundi, lundi.plusDays(6))))
                    .multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValueExact();
            long firstBandMinutes = AttendanceEngine.prorateFirstOvertimeBand(
                    configuredFirstBandMinutes, expectedMinutes, nominalWeekMinutes);
            LocalDate premierJourEmploi = null;
            for (LocalDate day = debutTroncon; !day.isAfter(finTroncon); day = day.plusDays(1)) {
                if (contextesJournaliers.containsKey(day)) {
                    premierJourEmploi = day;
                    break;
                }
            }
            // Règle métier confirmée GALAXAM (Phase 1B) :
            // Les périodes de paie sont strictement étanches pour le calcul des HS et des absences.
            // Aucune compensation ordinaire (HS <-> déficit) ne traverse une frontière de période.
            // Chaque tronçon est calculé avec son propre seuil et sa propre compensation autonome.
            AttendanceEngine.WeekResult res = weeklyAttendanceCalculator.calculateWeek(
                    new AttendanceEngine.WeekContext(lundi, expectedMinutes, firstBandMinutes,
                            premierJourEmploi != null && contextesJournaliers.get(premierJourEmploi).overtimeEligible(),
                            seuilHeuresJour.multiply(BigDecimal.valueOf(60)).longValueExact()), jours);
            weeklyResults.add(res);

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
            long nightMinutes = 0;
            long sundayMinutes = 0;
            long holidayMinutes = 0;
            for (AttendanceEngine.Result day : res.days()) {
                nightMinutes += day.nightMinutes();
                if (day.sunday()) sundayMinutes += day.workedMinutes();
                if (day.publicHoliday()) holidayMinutes += day.workedMinutes();
            }
            tDto.setHeuresNuit(minutesToHours(nightMinutes));
            tDto.setHeuresDimanche(minutesToHours(sundayMinutes));
            tDto.setHeuresFerie(minutesToHours(holidayMinutes));
            tronconsList.add(tDto);
        }

        PeriodTotals periodResult = aggregateWeeks(weeklyResults);

        // La compensation de chaque semaine est déjà terminée. Le rapprochement
        // de période reprend uniquement ses totaux, comme le récapitulatif Excel.
        // Le solde inclut HS30 et HS50 brutes ; les qualifications nuit/dimanche/férié ne sont pas ajoutées à nouveau.
        int deficitMinutes = Math.toIntExact(periodResult.absenceMinutes());
        int hs30Minutes = Math.toIntExact(periodResult.overtime30Minutes());
        int hs50Minutes = Math.toIntExact(periodResult.overtime50Minutes());
        int ajustementMinutes = 0;
        if (idPeriode != null && ajustementPeriodeService != null) {
            try (PreparedStatement bornes = c.prepareStatement(
                    "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?")) {
                bornes.setInt(1, idPeriode);
                try (ResultSet rsBornes = bornes.executeQuery()) {
                    if (rsBornes.next() && dateDebut.equals(rsBornes.getDate(1).toLocalDate())
                            && dateFin.equals(rsBornes.getDate(2).toLocalDate())) {
                        ajustementMinutes = ajustementPeriodeService.totalMinutesValidees(idPeriode, idEmp, c);
                    }
                }
            }
        }
        var balance = AttendanceEngine.PeriodBalance.calculate(
                hs30Minutes, hs50Minutes, deficitMinutes, ajustementMinutes,
                seuilHsTranche1.multiply(BigDecimal.valueOf(60)).intValueExact());
        dto.setMinutesPremierPalierHs(seuilHsTranche1.multiply(BigDecimal.valueOf(60)).intValueExact());
        int soldeMinutes = balance.balanceMinutes();
        dto.setMinutesDeficitHebdo(deficitMinutes);
        dto.setMinutesHs30Hebdo(hs30Minutes);
        dto.setMinutesHs50Hebdo(hs50Minutes);
        dto.setMinutesAjustementPeriode(ajustementMinutes);
        dto.setMinutesSoldePeriode(soldeMinutes);
        dto.setMinutesAbsenceFinale(balance.absenceMinutes());
        dto.setMinutesHs30Finales(balance.overtime30Minutes());
        dto.setMinutesHs50Finales(balance.overtime50Minutes());

        dto.setTronconsSemaine(tronconsList);

        dto.setNbJoursPresents(presents);
        dto.setNbJoursConge(conges);
        dto.setNbJoursAbsence(absences);
        dto.setNbJoursFerieChome(feriesChomes);
        dto.setNbJoursFerieTravaille(feriesTravailles);
        dto.setNbJoursDimancheTravaille(dimanchesTravailles);
        // Conversion finale pour l'API ; les minutes entières restent la valeur de calcul.
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
            BigDecimal ajustementJournalier = tarifJourDeplacement.multiply(periodResult.travelUnits())
                    .subtract(periodResult.travelAmount());
            if (ajustementJournalier.abs().compareTo(BigDecimal.ONE) < 0) {
                ajustementJournalier = BigDecimal.ZERO;
            }
            montantDeplacementFinal = refDeplacementSite.subtract(ajustementJournalier).max(BigDecimal.ZERO);
        }
        dto.setMontantDeplacement(mg.galaxam.sirh.paie.domain.engine.MoneyRoundingPolicy.amount(montantDeplacementFinal));
        Map<String, BigDecimal> fixedAmounts = loadFixedMonthlyAmounts(idEmp, idPeriode, dateDebut, dateFin, statutPeriode, c);
        Map<String, BigDecimal> totalAllowances = new LinkedHashMap<>(
                loadAllowanceAmounts(idEmp, idPeriode, dateDebut, dateFin, statutPeriode, c, fixedAmounts));
        indemnitesAutonomesSansPointage.forEach((code, amount) -> totalAllowances.merge(code, amount, BigDecimal::add));
        dto.setMontantsIndemnites(totalAllowances);
        dto.setIndemnitesFixesMensuelles(fixedAmounts);
        dto.setNbAnomaliesNonResolues(nbAnomalies);
        dto.setHeuresNormales(seuilHeuresJour.multiply(BigDecimal.valueOf(nbJoursOuvrablesPeriode)).doubleValue());

        if (idPeriode != null && statutPeriode != null
                && java.util.Set.of("VALIDEE", "PAIE_VALIDEE", "CLOTUREE").contains(statutPeriode)) {
            try (PreparedStatement frozen = c.prepareStatement(
                    "SELECT s.* FROM pointage_cumul_periode s JOIN periode_paie p ON p.id_periode_paie=s.id_periode_paie "
                    + "WHERE s.id_employe=? AND s.id_periode_paie=? AND p.date_debut=? AND p.date_fin=? "
                    + "AND s.statut_snapshot IN ('GELE','CONSOMME') ORDER BY s.version_snapshot DESC LIMIT 1")) {
                frozen.setInt(1,idEmp); frozen.setInt(2,idPeriode);
                frozen.setDate(3,Date.valueOf(dateDebut)); frozen.setDate(4,Date.valueOf(dateFin));
                try (ResultSet rs = frozen.executeQuery()) {
                    if (rs.next()) {
                        dto.setMinutesDeficitHebdo((Integer)rs.getObject("minutes_deficit_hebdo"));
                        dto.setMinutesHs30Hebdo((Integer)rs.getObject("minutes_hs30_hebdo"));
                        dto.setMinutesHs50Hebdo((Integer)rs.getObject("minutes_hs50_hebdo"));
                        dto.setMinutesAjustementPeriode((Integer)rs.getObject("minutes_ajustement_periode"));
                        dto.setMinutesSoldePeriode((Integer)rs.getObject("minutes_solde_periode"));
                        Integer frozenAbsence = (Integer)rs.getObject("minutes_absence_finale");
                        Integer frozen30 = (Integer)rs.getObject("minutes_hs30_finales");
                        dto.setMinutesAbsenceFinale(frozenAbsence == null
                                ? Math.toIntExact(hoursToMinutes(rs.getBigDecimal("heures_absence_retenues"))) : frozenAbsence);
                        dto.setMinutesHs30Finales(frozen30 == null
                                ? Math.toIntExact(hoursToMinutes(rs.getBigDecimal("heures_hs_30"))) : frozen30);
                        dto.setMinutesPremierPalierHs((Integer)rs.getObject("minutes_palier_hs30"));
                        Integer hs50Final = (Integer)rs.getObject("minutes_hs50_finales");
                        dto.setMinutesHs50Finales(hs50Final == null
                                ? Math.toIntExact(hoursToMinutes(rs.getBigDecimal("heures_hs_50"))) : hs50Final);
                    }
                }
            }
        }
        return dto;
    }

    /** Lecture générique : journal avant gel, valeurs figées après gel. */
    Map<String, BigDecimal> loadAllowanceAmounts(int employeeId, Integer periodId,
            LocalDate from, LocalDate to, String periodStatus, Connection c) throws SQLException {
        return loadAllowanceAmounts(employeeId, periodId, from, to, periodStatus, c, null);
    }

    private Map<String, BigDecimal> loadAllowanceAmounts(int employeeId, Integer periodId,
            LocalDate from, LocalDate to, String periodStatus, Connection c,
            Map<String, BigDecimal> fixedAmounts) throws SQLException {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        boolean frozen = periodId != null && periodStatus != null
                && Set.of("VALIDEE", "PAIE_VALIDEE", "CLOTUREE").contains(periodStatus);
        if (frozen) {
            try (PreparedStatement bounds = c.prepareStatement(
                    "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?")) {
                bounds.setInt(1, periodId);
                try (ResultSet rs = bounds.executeQuery()) {
                    frozen = rs.next() && from.equals(rs.getDate(1).toLocalDate())
                            && to.equals(rs.getDate(2).toLocalDate());
                }
            }
        }
        String sql = frozen
                ? "SELECT si.code, SUM(si.montant) AS montant FROM pointage_snapshot_indemnite si "
                    + "WHERE si.id_cumul = (SELECT cp.id_cumul FROM pointage_cumul_periode cp "
                    + "WHERE cp.id_employe = ? AND cp.id_periode_paie = ? "
                    + "AND cp.statut_snapshot IN ('GELE','CONSOMME') "
                    + "ORDER BY cp.version_snapshot DESC, cp.id_cumul DESC LIMIT 1) "
                    + "GROUP BY si.code ORDER BY si.code"
                : "SELECT i.code, SUM(pij.montant_calcule) AS montant FROM pointage_jour p "
                    + "JOIN pointage_indemnite_jour pij ON pij.id_pointage = p.id_pointage "
                    + "JOIN ref_indemnite i ON i.id_indemnite = pij.id_indemnite "
                    + "WHERE p.id_employe = ? AND p.date_pointage BETWEEN ? AND ? AND pij.accorde = TRUE "
                    + "GROUP BY i.code ORDER BY i.code";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            if (frozen) {
                ps.setInt(2, periodId);
            } else {
                ps.setDate(2, Date.valueOf(from));
                ps.setDate(3, Date.valueOf(to));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    amounts.put(rs.getString("code"), rs.getBigDecimal("montant"));
                }
            }
        }
        if (!frozen && (fixedAmounts != null || isFullPeriod(periodId, from, to, c))) {
            (fixedAmounts != null ? fixedAmounts : estimateFixedMonthlyAmounts(employeeId, periodId, c))
                    .forEach((code, amount) -> amounts.merge(code, amount, BigDecimal::add));
        }
        return amounts;
    }

    private boolean isFullPeriod(Integer periodId, LocalDate from, LocalDate to, Connection c) throws SQLException {
        if (periodId == null) return false;
        try (PreparedStatement ps = c.prepareStatement("SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?")) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && from.equals(rs.getDate(1).toLocalDate()) && to.equals(rs.getDate(2).toLocalDate());
            }
        }
    }

    Map<String, BigDecimal> loadFixedMonthlyAmounts(int employeeId, Integer periodId,
            LocalDate from, LocalDate to, String periodStatus, Connection c) throws SQLException {
        if (!isFullPeriod(periodId, from, to, c)) return Map.of();
        if (!Set.of("VALIDEE", "PAIE_VALIDEE", "CLOTUREE").contains(periodStatus == null ? "" : periodStatus)) {
            return estimateFixedMonthlyAmounts(employeeId, periodId, c);
        }
        Map<String, BigDecimal> frozen = loadAllowanceAmounts(employeeId, periodId, from, to, periodStatus, c);
        Map<String, BigDecimal> daily = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT i.code, SUM(pij.montant_calcule) FROM pointage_jour p "
                + "JOIN pointage_indemnite_jour pij ON pij.id_pointage = p.id_pointage "
                + "JOIN ref_indemnite i ON i.id_indemnite = pij.id_indemnite "
                + "WHERE p.id_employe = ? AND p.id_periode_paie = ? AND pij.accorde = TRUE GROUP BY i.code")) {
            ps.setInt(1, employeeId);
            ps.setInt(2, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) daily.put(rs.getString(1), rs.getBigDecimal(2));
            }
        }
        Map<String, BigDecimal> fixed = new LinkedHashMap<>();
        frozen.keySet().forEach(code -> {
            BigDecimal amount = frozen.getOrDefault(code, BigDecimal.ZERO)
                    .subtract(daily.getOrDefault(code, BigDecimal.ZERO)).max(BigDecimal.ZERO);
            if (amount.signum() > 0) fixed.put(code, amount);
        });
        return fixed;
    }

    Map<String, BigDecimal> estimateFixedMonthlyAmounts(int employeeId, int periodId, Connection c) throws SQLException {
        PointageTemporalConfigurationGuard.verify(employeeId, periodId, c);
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        String sql = "SELECT i.code, SUM(config.montant_reference / (periode.date_fin - periode.date_debut + 1)) "
                + "FROM periode_paie periode "
                + "CROSS JOIN LATERAL generate_series(periode.date_debut, periode.date_fin, interval '1 day') AS jour(date_jour) "
                + "JOIN LATERAL (SELECT a.id_site FROM employe_affectation_carriere a "
                + "JOIN contrat_travail ct ON ct.id_contrat=a.id_contrat "
                + "JOIN ref_statut_rh sa ON sa.id_statut_rh=a.id_statut_rh "
                + "JOIN ref_statut_rh sc ON sc.id_statut_rh=ct.id_statut_rh "
                + "WHERE a.id_employe = ? AND a.date_debut <= jour.date_jour::date "
                + "AND (a.date_fin IS NULL OR a.date_fin >= jour.date_jour::date) "
                + "AND ct.date_debut <= jour.date_jour::date AND (ct.date_fin IS NULL OR ct.date_fin >= jour.date_jour::date) "
                + "AND sa.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND sc.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + ") affectation ON TRUE "
                + "JOIN ref_indemnite i ON i.est_actif = TRUE "
                + "JOIN LATERAL (SELECT sic.montant_reference, sic.periodicite, sic.est_actif FROM site_indemnite_config sic "
                + "WHERE sic.id_site = affectation.id_site AND sic.id_indemnite = i.id_indemnite "
                + "AND sic.date_debut <= jour.date_jour::date "
                + "AND (sic.date_fin IS NULL OR sic.date_fin >= jour.date_jour::date) "
                + "AND sic.est_actif = TRUE) config ON TRUE "
                + "WHERE periode.id_periode_paie = ? AND config.montant_reference > 0 "
                + "AND config.periodicite = 'FIXE_MENSUEL' AND config.est_actif = TRUE GROUP BY i.code";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setInt(2, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) amounts.put(rs.getString(1), rs.getBigDecimal(2).setScale(2, RoundingMode.HALF_UP));
            }
        }
        return amounts;
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
        Map<String, ParametreGlobal.ParametreEtat> parameterStates =
                ParametreGlobal.loadEtatMapAtDate(Date.valueOf(dateDebut), c);
        Map<String, String> params = ParametreGlobal.activeValues(parameterStates);

        // Calcul des jours ouvrables réels de la période via le moteur centralisé
        int nbJoursOuvrablesPeriode = calendrierEngine.compterJoursOuvrables(dateDebut, dateFin, idSite, c);
        if (nbJoursOuvrablesPeriode == 0) throw new IllegalStateException("MISSING_WORKING_CALENDAR");

        // Charger les indemnités de déplacement/transport configurées par site (site_indemnite_config)
        Map<Integer, BigDecimal> siteDeplacementRefMap = new HashMap<>();
        String sqlSiteIndConfig = "SELECT c.id_site, c.montant_reference FROM site_indemnite_config c " +
                                  "JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite " +
                                  "WHERE i.code IN ('DPLCT', 'DEPLACEMENT', 'TRANSPORT') AND c.est_actif = TRUE";
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
            "a.date_debut AS date_debut_affectation, a.date_fin AS date_fin_affectation, " +
            "ct.date_debut AS date_debut_contrat, ct.date_fin AS date_fin_contrat, " +
            "s.id_site, s.code_site AS site_code, s.libelle AS site_nom, COALESCE(s.horaire_continu, TRUE) AS horaire_continu, " +
            "COALESCE(f.dispense_pointage, FALSE) AS est_autonome, " +
            "COALESCE(cp.code, '') AS code_categorie, COALESCE(cp.est_cadre, FALSE) AS categorie_est_cadre " +
            "FROM employe e " +
            "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
            "    AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
            "JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
            "LEFT JOIN contrat_travail ct ON ct.id_contrat = a.id_contrat " +
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
                    boolean isCadre = rsE.getBoolean("categorie_est_cadre");

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
                            laterDate(rsE.getDate("date_debut_affectation"), rsE.getDate("date_debut_contrat")),
                            earlierNonNullDate(rsE.getDate("date_fin_affectation"), rsE.getDate("date_fin_contrat")),
                            idPeriode,
                            codePeriode,
                            libellePeriode,
                            statutPeriode,
                            nbJoursOuvrablesPeriode,
                            siteDeplacementRefMap,
                            params,
                            parameterStates,
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
            resp.put("kpis", Map.of(
                "totalSalaries", 0, "totalHeures", 0.0, "totalHs30", 0.0, "totalHs50", 0.0,
                "totalAbsenceFinaleMins", 0,
                "totalNuit", 0.0, "totalDimanche", 0.0, "totalFerie", 0.0, "totalRetardMins", 0, "totalIndemnites", 0.0
            ));
            resp.put("alertCounts", Map.of(
                "all", 0, "hs", 0, "nuit", 0, "retard", 0, "compense", 0, "absence", 0
            ));
            return resp;
        }

        // Weekly values are calculated from pointage_jour and never read from a persisted week table.
        List<PointageSyntheseMensuelleDTO> periodValues = getSynthesePeriode(idPeriode, idSite, c);
        Set<Integer> employeesWithHs = new HashSet<>();
        Set<Integer> employeesWithCompensation = new HashSet<>();
        double totalHs30 = 0;
        double totalHs50 = 0;
        long totalAbsenceFinaleMins = 0;
        double totalNuit = 0;
        double totalDimanche = 0;
        double totalFerie = 0;
        double totalHeures = 0;
        BigDecimal totalIndemnites = BigDecimal.ZERO;
        for (PointageSyntheseMensuelleDTO dto : periodValues) {
            // Les KPI de synthèse doivent suivre le même calcul et la même population
            // que les lignes du tableau (notamment les collaborateurs autonomes).
            totalHeures += dto.getTotalHeuresTravaillees();
            totalHs30 += dto.getMinutesHs30Finales() / 60.0;
            totalHs50 += dto.getMinutesHs50Finales() / 60.0;
            totalAbsenceFinaleMins += dto.getMinutesAbsenceFinale() == null ? 0 : dto.getMinutesAbsenceFinale();
            if (dto.getMontantsIndemnites() != null) {
                totalIndemnites = totalIndemnites.add(dto.getMontantsIndemnites().values().stream()
                        .filter(amount -> amount != null).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            if (dto.getTronconsSemaine() != null) {
                totalNuit += dto.getTronconsSemaine().stream().mapToDouble(PointageTronconSemaineDTO::getHeuresNuit).sum();
                totalDimanche += dto.getTronconsSemaine().stream().mapToDouble(PointageTronconSemaineDTO::getHeuresDimanche).sum();
                totalFerie += dto.getTronconsSemaine().stream().mapToDouble(PointageTronconSemaineDTO::getHeuresFerie).sum();
            }
            if (dto.getMinutesHs30Finales() > 0 || dto.getMinutesHs50Finales() > 0) employeesWithHs.add(dto.getIdEmploye());
            if (dto.getTronconsSemaine() != null && dto.getTronconsSemaine().stream()
                    .anyMatch(w -> w.getHeuresHsBrutes() > w.getHeuresHsNettes())) {
                employeesWithCompensation.add(dto.getIdEmploye());
            }
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
            resp.put("kpis", Map.of(
                "totalSalaries", 0, "totalHeures", 0.0, "totalHs30", 0.0, "totalHs50", 0.0,
                "totalAbsenceFinaleMins", 0,
                "totalNuit", 0.0, "totalDimanche", 0.0, "totalFerie", 0.0, "totalRetardMins", 0, "totalIndemnites", 0.0
            ));
            resp.put("alertCounts", Map.of(
                "all", 0, "hs", 0, "nuit", 0, "retard", 0, "compense", 0, "absence", 0
            ));
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
        int countHs = employeesWithHs.size();
        int countCompense = employeesWithCompensation.size();
        StringBuilder sqlAl = new StringBuilder(
            "SELECT " +
            "(SELECT COUNT(DISTINCT pj.id_employe) FROM pointage_jour pj JOIN employe e ON pj.id_employe = e.id_employe WHERE pj.id_periode_paie = ? AND e.est_actif = TRUE AND pj.heures_nuit > 0 " + (idSite != null && idSite > 0 ? "AND pj.id_site = ? " : "") + ") AS count_nuit, " +
            "(SELECT COUNT(DISTINCT pj.id_employe) FROM pointage_jour pj JOIN employe e ON pj.id_employe = e.id_employe WHERE pj.id_periode_paie = ? AND e.est_actif = TRUE AND pj.minutes_retard > 0 " + (idSite != null && idSite > 0 ? "AND pj.id_site = ? " : "") + ") AS count_retard, " +
            "(SELECT COUNT(DISTINCT pj.id_employe) FROM pointage_jour pj JOIN ref_statut_pointage_jour spj ON pj.id_statut_jour = spj.id_statut_jour JOIN employe e ON pj.id_employe = e.id_employe WHERE pj.id_periode_paie = ? AND e.est_actif = TRUE AND spj.code LIKE '%ABS%' " + (idSite != null && idSite > 0 ? "AND pj.id_site = ? " : "") + ") AS count_absence, " +
            "0 AS count_compense, 0 AS count_hs"
        );
        try (PreparedStatement psAl = c.prepareStatement(sqlAl.toString())) {
            int aIdx = 1;
            psAl.setInt(aIdx++, idPeriode);
            if (idSite != null && idSite > 0) psAl.setInt(aIdx++, idSite);
            psAl.setInt(aIdx++, idPeriode);
            if (idSite != null && idSite > 0) psAl.setInt(aIdx++, idSite);
            psAl.setInt(aIdx++, idPeriode);
            if (idSite != null && idSite > 0) psAl.setInt(aIdx++, idSite);
            try (ResultSet rsAl = psAl.executeQuery()) {
                if (rsAl.next()) {
                    countNuit = rsAl.getInt("count_nuit");
                    countRetard = rsAl.getInt("count_retard");
                    countAbsence = rsAl.getInt("count_absence");
                }
            }
        }

        // 3. Global KPIs
        int totalRetardMins = 0;
        StringBuilder sqlKpi = new StringBuilder(
            "SELECT COALESCE(SUM(pj.minutes_retard), 0) AS tot_ret " +
            "FROM pointage_jour pj JOIN employe e ON pj.id_employe = e.id_employe " +
            "WHERE pj.id_periode_paie = ? AND e.est_actif = TRUE "
        );
        if (idSite != null && idSite > 0) sqlKpi.append("AND pj.id_site = ? ");
        try (PreparedStatement psKpi = c.prepareStatement(sqlKpi.toString())) {
            psKpi.setInt(1, idPeriode);
            if (idSite != null && idSite > 0) psKpi.setInt(2, idSite);
            try (ResultSet rsKpi = psKpi.executeQuery()) {
                if (rsKpi.next()) {
                    totalRetardMins = rsKpi.getInt("tot_ret");
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
            appendEmployeeSetFilter(whereFilter, filterParams, employeesWithHs);
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
            appendEmployeeSetFilter(whereFilter, filterParams, employeesWithCompensation);
        }

        String cteSql = 
            "WITH employes_actifs AS ( " +
            "    SELECT DISTINCT ON (e.id_employe) e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
            "           a.date_debut AS date_debut_affectation, a.date_fin AS date_fin_affectation, " +
            "           ct.date_debut AS date_debut_contrat, ct.date_fin AS date_fin_contrat, " +
            "           s.id_site, s.code_site AS site_code, s.libelle AS site_nom, COALESCE(s.horaire_continu, TRUE) AS horaire_continu, " +
            "           COALESCE(f.dispense_pointage, FALSE) AS est_autonome, " +
            "           COALESCE(cp.code, '') AS code_categorie, COALESCE(cp.est_cadre, FALSE) AS categorie_est_cadre " +
            "    FROM employe e " +
            "    JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
            "        AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
            "    JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
            "    LEFT JOIN contrat_travail ct ON ct.id_contrat = a.id_contrat " +
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
            Map<Integer, PointageSyntheseMensuelleDTO> calculatedByEmployee = new HashMap<>();
            periodValues.forEach(dto -> calculatedByEmployee.put(dto.getIdEmploye(), dto));

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
                        PointageSyntheseMensuelleDTO dto = calculatedByEmployee.get(idEmp);
                        if (dto == null) {
                            throw new IllegalStateException("Population de synthèse incohérente pour " + idEmp);
                        }
                        pageList.add(dto);
                    }
                }
            }
        }

        Map<String, Object> kpisMap = new HashMap<>();
        kpisMap.put("totalSalaries", countAll);
        kpisMap.put("totalHeures", Math.round(totalHeures * 100.0) / 100.0);
        kpisMap.put("totalHs30", Math.round(totalHs30 * 100.0) / 100.0);
        kpisMap.put("totalHs50", Math.round(totalHs50 * 100.0) / 100.0);
        kpisMap.put("totalAbsenceFinaleMins", totalAbsenceFinaleMins);
        kpisMap.put("totalRetardMins", totalRetardMins);
        kpisMap.put("totalIndemnites", totalIndemnites);
        kpisMap.put("totalNuit", Math.round(totalNuit * 100.0) / 100.0);
        kpisMap.put("totalDimanche", Math.round(totalDimanche * 100.0) / 100.0);
        kpisMap.put("totalFerie", Math.round(totalFerie * 100.0) / 100.0);

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
     * Gèle le cumul de période, les journées sources et les buckets temporels pour Paie.
     */
    public void gelerPeriode(int idPeriode, Integer userId, Connection c) throws SQLException {
        var vacationsChevauchantes = vacationsSurDeuxCampagnes(idPeriode, c);
        if (!vacationsChevauchantes.isEmpty()) {
            throw new IllegalStateException("POINTAGE_CROSS_PERIOD_SHIFT: scinder la vacation "
                    + vacationsChevauchantes.get(0).idPointage() + " du "
                    + vacationsChevauchantes.get(0).datePointage()
                    + " avant le gel de la période.");
        }
        // Calculer la synthèse via notre logique métier pure
        List<PointageSyntheseMensuelleDTO> synthese = getSynthesePeriode(idPeriode, null, c);
        var changementsEligibilite = changementsEligibiliteHs(idPeriode, synthese, c);
        if (!changementsEligibilite.isEmpty()) {
            var changement = changementsEligibilite.get(0);
            throw new IllegalStateException("POINTAGE_HS_ELIGIBILITY_CHANGE: employé "
                    + changement.matricule() + ", semaine " + changement.debutTroncon()
                    + " à " + changement.finTroncon() + ", changement le "
                    + changement.dateChangement() + ". Décision DAF requise avant le gel.");
        }

        String sqlCumul = "INSERT INTO pointage_cumul_periode (" +
                          "id_employe, id_periode_paie, version_snapshot, contrat_indemnites_version, statut_snapshot, remplace_id_cumul, nb_jours_ouvrables, nb_jours_presents, " +
                          "nb_jours_absence, nb_jours_conge, nb_jours_ferie_chome, nb_jours_ferie_travaille, " +
                          "total_heures_travaillees, heures_hs_30, heures_hs_50, heures_nuit_total, " +
                          "heures_dimanche_total, heures_ferie_total, " +
                          "heures_absence_retenues, total_minutes_retard, nb_jours_deplacement, montant_deplacement, " +
                          "nb_jours_repas, montant_repas, gele_par, gele_at" +
                          ") VALUES (?, ?, " +
                          "(SELECT COALESCE(MAX(version_snapshot), 0) + 1 FROM pointage_cumul_periode WHERE id_employe = ? AND id_periode_paie = ?), 2, 'GELE', " +
                          "(SELECT id_cumul FROM pointage_cumul_periode WHERE id_employe = ? AND id_periode_paie = ? ORDER BY version_snapshot DESC LIMIT 1), " +
                          "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id_cumul";

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
                ps.setBigDecimal(17, BigDecimal.valueOf(dto.getHeuresDimancheTotal()));
                ps.setBigDecimal(18, BigDecimal.valueOf(dto.getHeuresFerieTotal()));
                ps.setBigDecimal(19, BigDecimal.valueOf(dto.getHeuresAbsenceRetenues()));
                ps.setInt(20, dto.getTotalMinutesRetard());
                ps.setBigDecimal(21, BigDecimal.valueOf(dto.getNbJoursDeplacement()));
                ps.setBigDecimal(22, dto.getMontantDeplacement());
                ps.setInt(23, dto.getNbJoursRepas());
                ps.setBigDecimal(24, dto.getMontantRepas());
                if (userId != null) ps.setInt(25, userId); else ps.setNull(25, java.sql.Types.INTEGER);
                int snapshotId;
                try (ResultSet inserted = ps.executeQuery()) {
                    if (!inserted.next()) throw new SQLException("Le snapshot Pointage n'a pas été créé.");
                    snapshotId = inserted.getInt(1);
                }
                enregistrerProvenanceSnapshot(snapshotId, dto, idPeriode, c);
                var snapshotBuckets = getSnapshotBuckets(snapshotId, c);
                long minutesHs30Buckets = snapshotBuckets.stream()
                        .filter(bucket -> bucket.overtimeTier() == OvertimeTier.OVERTIME_30)
                        .mapToLong(QualifiedTimeBucket::minutes)
                        .sum();
                if (minutesHs30Buckets != dto.getMinutesHs30Hebdo()) {
                    throw new IllegalStateException("HS30_SNAPSHOT_INCOHERENT: " + dto.getMatricule()
                            + " possède " + dto.getMinutesHs30Hebdo() + " minutes de HS 30 % hebdomadaires, "
                            + "mais " + minutesHs30Buckets + " minutes dans les journées figées. "
                            + "Vérifier les horaires et les tronçons de semaine avant le gel.");
                }
                long minutesHs50Buckets = snapshotBuckets.stream()
                        .filter(bucket -> bucket.overtimeTier() == OvertimeTier.OVERTIME_50)
                        .mapToLong(QualifiedTimeBucket::minutes).sum();
                if (minutesHs50Buckets != dto.getMinutesHs50Hebdo())
                    throw new IllegalStateException("HS50_SNAPSHOT_INCOHERENT: ventilation des journées divergente.");
                try (PreparedStatement solde = c.prepareStatement(
                        "UPDATE pointage_cumul_periode SET minutes_deficit_hebdo = ?, minutes_hs30_hebdo = ?, " +
                        "minutes_hs50_hebdo = ?, minutes_ajustement_periode = ?, minutes_solde_periode = ?, " +
                        "minutes_absence_finale = ?, minutes_hs30_finales = ?, minutes_hs50_finales = ?, minutes_palier_hs30 = ? WHERE id_cumul = ?")) {
                    solde.setInt(1, dto.getMinutesDeficitHebdo());
                    solde.setInt(2, dto.getMinutesHs30Hebdo());
                    solde.setInt(3, dto.getMinutesHs50Hebdo());
                    solde.setInt(4, dto.getMinutesAjustementPeriode());
                    solde.setInt(5, dto.getMinutesSoldePeriode());
                    solde.setInt(6, dto.getMinutesAbsenceFinale());
                    solde.setInt(7, dto.getMinutesHs30Finales());
                    solde.setInt(8, dto.getMinutesHs50Finales());
                    solde.setInt(9, dto.getMinutesPremierPalierHs());
                    solde.setInt(10, snapshotId);
                    solde.executeUpdate();
                }
                if (ajustementPeriodeService != null) {
                    ajustementPeriodeService.figerProvenance(snapshotId, idPeriode, dto.getIdEmploye(), c);
                }
            }
        }

    }

    /** Même contrôle utilisé par la décision backend et par la revue DAF. */
    public PointagePreflight preflightPeriode(int idPeriode, Connection c) throws SQLException {
        var vacationsChevauchantes = vacationsSurDeuxCampagnes(idPeriode, c);
        List<PointagePreflight.Blocker> blockers = new ArrayList<>();
        List<PointageSyntheseMensuelleDTO> synthese = getSynthesePeriode(idPeriode, null, c);
        var changementsEligibilite = changementsEligibiliteHs(idPeriode, synthese, c);
        return new PointagePreflight(idPeriode, synthese.size(), List.copyOf(blockers),
                List.copyOf(journeesOuvrablesSansStatut(idPeriode, synthese, c)),
                vacationsChevauchantes, changementsEligibilite);
    }

    public record PointagePreflight(int idPeriodePaie, int nombreEmployes, List<Blocker> blocages,
                                   List<JourneeManquante> journeesManquantes,
                                   List<VacationChevauchante> vacationsChevauchantes,
                                   List<ChangementEligibiliteHs> changementsEligibiliteHs) {
        @com.fasterxml.jackson.annotation.JsonProperty("pret")
        public boolean pret() { return blocages.isEmpty() && journeesManquantes.isEmpty()
                && vacationsChevauchantes.isEmpty() && changementsEligibiliteHs.isEmpty()
                && nombreEmployes > 0; }

        @com.fasterxml.jackson.annotation.JsonProperty("pret")
        public boolean isPret() { return pret(); }

        public record Blocker(int idEmploye, String matricule, String nom, String prenom,
                              int minutesHsSansSemaine) {}
        public record VacationChevauchante(int idPointage, int idEmploye, LocalDate datePointage,
                                            LocalDate dateSuivante) {}
        public record ChangementEligibiliteHs(int idEmploye, String matricule, String nom, String prenom,
                                               LocalDate debutTroncon, LocalDate finTroncon,
                                               LocalDate dateChangement, boolean eligibleAvant,
                                               boolean eligibleApres) {}
        public record JourneeManquante(int idEmploye, String matricule, String nom, String prenom,
                                      LocalDate date, Integer idSite) {
            public JourneeManquante(int idEmploye, String matricule, String nom, String prenom, LocalDate date) {
                this(idEmploye, matricule, nom, prenom, date, null);
            }
        }
    }

    private List<PointagePreflight.ChangementEligibiliteHs> changementsEligibiliteHs(int periodId,
            List<PointageSyntheseMensuelleDTO> synthese, Connection c) throws SQLException {
        LocalDate debut;
        LocalDate fin;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT date_debut,date_fin FROM periode_paie WHERE id_periode_paie=?")) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("MISSING_CONFIGURATION: PERIODE_PAIE id=" + periodId);
                debut = rs.getDate(1).toLocalDate();
                fin = rs.getDate(2).toLocalDate();
            }
        }
        Map<Integer, Set<LocalDate>> weekEndsTravailles = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id_employe,date_pointage FROM pointage_jour "
                        + "WHERE id_periode_paie=? AND total_heures>0")) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) weekEndsTravailles
                        .computeIfAbsent(rs.getInt(1), ignored -> new HashSet<>())
                        .add(rs.getDate(2).toLocalDate());
            }
        }
        List<PointagePreflight.ChangementEligibiliteHs> changements = new ArrayList<>();
        var troncons = AttendanceEngine.segmentWeeks(debut, fin);
        for (PointageSyntheseMensuelleDTO employe : synthese) {
            int idEmp = employe.getIdEmploye();
            var jours = employmentContextResolver.resolvePointageDays(idEmp, debut, fin, c);
            var travailles = weekEndsTravailles.getOrDefault(idEmp, Set.of());
            for (var troncon : troncons) {
                Boolean precedent = null;
                for (LocalDate jour = troncon.start(); !jour.isAfter(troncon.end()); jour = jour.plusDays(1)) {
                    var contexte = jours.get(jour);
                    if (contexte == null || (CalendrierOuvrableEngine.isWeekend(jour) && !travailles.contains(jour)))
                        continue;
                    boolean eligible = contexte.overtimeEligible();
                    if (precedent != null && precedent != eligible) {
                        changements.add(new PointagePreflight.ChangementEligibiliteHs(idEmp,
                                employe.getMatricule(), employe.getNom(), employe.getPrenom(),
                                troncon.start(), troncon.end(), jour, precedent, eligible));
                        break;
                    }
                    precedent = eligible;
                }
            }
        }
        return List.copyOf(changements);
    }

    private List<PointagePreflight.JourneeManquante> journeesOuvrablesSansStatut(int periodId,
            List<PointageSyntheseMensuelleDTO> synthese, Connection c) throws SQLException {
        LocalDate debut;
        LocalDate fin;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?")) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return List.of();
                debut = rs.getDate(1).toLocalDate();
                fin = rs.getDate(2).toLocalDate();
            }
        }
        Map<Integer, Set<LocalDate>> datesSaisies = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id_employe, date_pointage FROM pointage_jour WHERE id_periode_paie = ?")) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) datesSaisies.computeIfAbsent(rs.getInt(1), ignored -> new HashSet<>())
                        .add(rs.getDate(2).toLocalDate());
            }
        }
        Map<Integer, Set<LocalDate>> datesCongesValides = new HashMap<>();
        String sqlConges = "SELECT dc.id_employe, dc.date_debut, dc.date_fin " +
                "FROM demande_conge dc " +
                "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                "WHERE sc.code IN ('VALIDE', 'APPROUVEE') " +
                "AND dc.date_debut <= ? AND dc.date_fin >= ?";
        try (PreparedStatement ps = c.prepareStatement(sqlConges)) {
            ps.setDate(1, Date.valueOf(fin));
            ps.setDate(2, Date.valueOf(debut));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int empId = rs.getInt("id_employe");
                    LocalDate dDeb = rs.getDate("date_debut").toLocalDate();
                    LocalDate dFin = rs.getDate("date_fin").toLocalDate();
                    LocalDate from = dDeb.isBefore(debut) ? debut : dDeb;
                    LocalDate to = dFin.isAfter(fin) ? fin : dFin;
                    for (LocalDate cur = from; !cur.isAfter(to); cur = cur.plusDays(1)) {
                        datesCongesValides.computeIfAbsent(empId, ignored -> new HashSet<>()).add(cur);
                    }
                }
            }
        }
        Map<Integer, Set<LocalDate>> feriesParSite = new HashMap<>();
        List<PointagePreflight.JourneeManquante> manquantes = new ArrayList<>();
        for (PointageSyntheseMensuelleDTO employe : synthese) {
            Set<LocalDate> saisies = datesSaisies.getOrDefault(employe.getIdEmploye(), Set.of());
            Set<LocalDate> conges = datesCongesValides.getOrDefault(employe.getIdEmploye(), Set.of());
            var joursEmploi = employmentContextResolver.resolvePointageDays(employe.getIdEmploye(), debut, fin, c);
            for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
                if (CalendrierOuvrableEngine.isWeekend(jour) || saisies.contains(jour) || conges.contains(jour)) continue;
                var emploiDuJour = joursEmploi.get(jour);
                if (emploiDuJour == null || emploiDuJour.dispensePointage()) continue;
                Set<LocalDate> feries = feriesParSite.get(emploiDuJour.siteId());
                if (feries == null) {
                    feries = calendrierEngine.getDatesFeriesEtChomees(debut, fin, emploiDuJour.siteId(), c);
                    feriesParSite.put(emploiDuJour.siteId(), feries);
                }
                if (!feries.contains(jour)) manquantes.add(new PointagePreflight.JourneeManquante(
                        employe.getIdEmploye(), employe.getMatricule(), employe.getNom(), employe.getPrenom(), jour, emploiDuJour.siteId()));
            }
        }
        return manquantes;
    }

    /**
     * Dégèle les données lors de la réouverture exceptionnelle d'une période
     */
    public void degelerPeriode(int idPeriode, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE pointage_cumul_periode SET statut_snapshot = 'REMPLACE' " +
                "WHERE id_periode_paie = ? AND statut_snapshot = 'GELE'")) {
            ps.setInt(1, idPeriode);
            ps.executeUpdate();
        }
    }

    private void enregistrerProvenanceSnapshot(int snapshotId, PointageSyntheseMensuelleDTO dto, int periodId, Connection c)
            throws SQLException {
        int employeeId = dto.getIdEmploye();
        // Capture l'heure effective (plancher d'arrivée valorisé déjà appliqué lors de la saisie)
        // pour permettre l'audit complet du calcul : "quelle entrée a été comptabilisée lors du gel V1 ?"
        String days = "INSERT INTO pointage_snapshot_jour " +
                "(id_cumul, id_pointage, version_pointage, date_pointage, minutes_travaillees, minutes_retard, minutes_nuit, statut_jour, heure_entree_effective) " +
                "SELECT ?, p.id_pointage, p.version, p.date_pointage, ROUND(p.total_heures * 60), " +
                "p.minutes_retard, ROUND(p.heures_nuit * 60), s.code, p.heure_entree_matin_effective FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour s ON s.id_statut_jour = p.id_statut_jour " +
                "WHERE p.id_employe = ? AND p.id_periode_paie = ?";
        try (PreparedStatement ps = c.prepareStatement(days)) {
            ps.setInt(1, snapshotId); ps.setInt(2, employeeId); ps.setInt(3, periodId); ps.executeUpdate();
        }
        String unqualifiedAllowance = "SELECT i.code FROM pointage_indemnite_jour pij "
                + "JOIN pointage_jour p ON p.id_pointage = pij.id_pointage "
                + "JOIN ref_indemnite i ON i.id_indemnite = pij.id_indemnite "
                + "WHERE p.id_employe = ? AND p.id_periode_paie = ? "
                + "AND pij.accorde = TRUE AND pij.montant_calcule > 0 "
                + "AND i.soumis_cotisation IS NULL LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(unqualifiedAllowance)) {
            ps.setInt(1, employeeId);
            ps.setInt(2, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException("Qualification cotisations sociales manquante pour l'indemnité "
                            + rs.getString(1) + ". Renseigner le catalogue avant de valider le pointage.");
                }
            }
        }
        String allowances = "INSERT INTO pointage_snapshot_indemnite "
                + "(id_cumul, id_indemnite, code, libelle, imposable_irsa, soumis_cotisation, code_rubrique_paie, montant) "
                + "SELECT ?, i.id_indemnite, i.code, i.libelle, COALESCE(i.imposable_irsa, FALSE), i.soumis_cotisation, "
                + "COALESCE(i.code_rubrique_paie, 'INDEMNITE_' || i.code), "
                + "SUM(pij.montant_calcule) FROM pointage_indemnite_jour pij "
                + "JOIN pointage_jour p ON p.id_pointage = pij.id_pointage "
                + "JOIN ref_indemnite i ON i.id_indemnite = pij.id_indemnite "
                + "WHERE p.id_employe = ? AND p.id_periode_paie = ? "
                + "AND pij.accorde = TRUE AND pij.montant_calcule > 0 "
                + "GROUP BY i.id_indemnite, i.code, i.libelle, i.imposable_irsa, i.soumis_cotisation, i.code_rubrique_paie";
        try (PreparedStatement ps = c.prepareStatement(allowances)) {
            ps.setInt(1, snapshotId);
            ps.setInt(2, employeeId);
            ps.setInt(3, periodId);
            ps.executeUpdate();
        }
        enregistrerIndemnitesFixesMensuelles(snapshotId, employeeId, periodId, c);
        enregistrerSnapshotBuckets(snapshotId, employeeId, periodId, dto.getTronconsSemaine(), c);
    }

    /**
     * Le fixe mensuel ne dépend pas des journées pointées. Chaque date de la période
     * contribue au tarif du site d'affectation applicable à cette date, divisé par
     * le nombre de jours calendaires de la période. Le résultat est figé pour Paie.
     */
    void enregistrerIndemnitesFixesMensuelles(int snapshotId, int employeeId, int periodId, Connection c)
            throws SQLException {
        PointageTemporalConfigurationGuard.verify(employeeId, periodId, c);
        String sql = "SELECT i.id_indemnite, i.code, i.libelle, i.imposable_irsa, i.soumis_cotisation, "
                + "COALESCE(i.code_rubrique_paie, 'INDEMNITE_' || i.code) AS code_rubrique_paie, "
                + "SUM(config.montant_reference / (periode.date_fin - periode.date_debut + 1)) AS montant "
                + "FROM periode_paie periode "
                + "CROSS JOIN LATERAL generate_series(periode.date_debut, periode.date_fin, interval '1 day') AS jour(date_jour) "
                + "JOIN LATERAL (SELECT a.id_site FROM employe_affectation_carriere a "
                + "JOIN contrat_travail ct ON ct.id_contrat=a.id_contrat "
                + "JOIN ref_statut_rh sa ON sa.id_statut_rh=a.id_statut_rh "
                + "JOIN ref_statut_rh sc ON sc.id_statut_rh=ct.id_statut_rh "
                + "WHERE a.id_employe = ? AND a.date_debut <= jour.date_jour::date "
                + "AND (a.date_fin IS NULL OR a.date_fin >= jour.date_jour::date) "
                + "AND ct.date_debut <= jour.date_jour::date AND (ct.date_fin IS NULL OR ct.date_fin >= jour.date_jour::date) "
                + "AND sa.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND sc.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + ") affectation ON TRUE "
                + "JOIN ref_indemnite i ON i.est_actif = TRUE "
                + "JOIN LATERAL (SELECT sic.montant_reference, sic.periodicite, sic.est_actif FROM site_indemnite_config sic "
                + "WHERE sic.id_site = affectation.id_site AND sic.id_indemnite = i.id_indemnite "
                + "AND sic.date_debut <= jour.date_jour::date "
                + "AND (sic.date_fin IS NULL OR sic.date_fin >= jour.date_jour::date) "
                + "AND sic.est_actif = TRUE) config ON TRUE "
                + "WHERE periode.id_periode_paie = ? AND config.montant_reference > 0 "
                + "AND config.periodicite = 'FIXE_MENSUEL' AND config.est_actif = TRUE "
                + "GROUP BY i.id_indemnite, i.code, i.libelle, i.imposable_irsa, i.soumis_cotisation, i.code_rubrique_paie";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setInt(2, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getObject("soumis_cotisation") == null) {
                        throw new IllegalStateException("Qualification cotisations sociales manquante pour l'indemnité "
                                + rs.getString("code") + ".");
                    }
                    BigDecimal amount = rs.getBigDecimal("montant").setScale(2, RoundingMode.HALF_UP);
                    if (amount.signum() <= 0) continue;
                    String insert = "INSERT INTO pointage_snapshot_indemnite "
                            + "(id_cumul, id_indemnite, code, libelle, imposable_irsa, soumis_cotisation, code_rubrique_paie, montant) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT (id_cumul, id_indemnite) DO UPDATE SET montant = pointage_snapshot_indemnite.montant + EXCLUDED.montant";
                    try (PreparedStatement snapshot = c.prepareStatement(insert)) {
                        snapshot.setInt(1, snapshotId);
                        snapshot.setInt(2, rs.getInt("id_indemnite"));
                        snapshot.setString(3, rs.getString("code"));
                        snapshot.setString(4, rs.getString("libelle"));
                        snapshot.setBoolean(5, rs.getBoolean("imposable_irsa"));
                        snapshot.setBoolean(6, rs.getBoolean("soumis_cotisation"));
                        snapshot.setString(7, rs.getString("code_rubrique_paie"));
                        snapshot.setBigDecimal(8, amount);
                        snapshot.executeUpdate();
                    }
                }
            }
        }
    }

    private void enregistrerSnapshotBuckets(int snapshotId, int employeeId, int periodId,
            List<PointageTronconSemaineDTO> troncons, Connection c)
            throws SQLException {
        LocalDate dateDebutPeriode = null;
        LocalDate dateFinPeriode = null;
        String sqlPeriode = "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?";
        try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
            psP.setInt(1, periodId);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    Date dDeb = rsP.getDate("date_debut");
                    Date dFin = rsP.getDate("date_fin");
                    if (dDeb != null) dateDebutPeriode = dDeb.toLocalDate();
                    if (dFin != null) dateFinPeriode = dFin.toLocalDate();
                }
            }
        }
        if (dateDebutPeriode == null || dateFinPeriode == null) {
            return;
        }

        List<WorkInterval> intervals = new ArrayList<>();
        String sqlP = "SELECT id_pointage, date_pointage, COALESCE(heure_entree_matin_effective, heure_entree_matin) AS heure_entree_matin, heure_sortie_matin, " +
                      "heure_entree_am, heure_sortie_am, horaire_continu_jour " +
                      "FROM pointage_jour WHERE id_employe = ? AND id_periode_paie = ? ORDER BY date_pointage ASC";
        try (PreparedStatement psP = c.prepareStatement(sqlP)) {
            psP.setInt(1, employeeId);
            psP.setInt(2, periodId);
            try (ResultSet rsP = psP.executeQuery()) {
                while (rsP.next()) {
                    int ptId = rsP.getInt("id_pointage");
                    Date dP = rsP.getDate("date_pointage");
                    if (dP == null) continue;
                    LocalDate datePointage = dP.toLocalDate();
                    Time em = rsP.getTime("heure_entree_matin");
                    Time sm = rsP.getTime("heure_sortie_matin");
                    Time ea = rsP.getTime("heure_entree_am");
                    Time sa = rsP.getTime("heure_sortie_am");
                    boolean continu = rsP.getBoolean("horaire_continu_jour");
                    intervals.addAll(WorkIntervalFactory.createIntervals(
                            employeeId, ptId, datePointage,
                            em != null ? em.toLocalTime() : null,
                            sm != null ? sm.toLocalTime() : null,
                            ea != null ? ea.toLocalTime() : null,
                            sa != null ? sa.toLocalTime() : null,
                            continu,
                            "PTG_" + ptId
                    ));
                }
            }
        }

        if (intervals.isEmpty()) {
            return;
        }

        LocalDateTime finExclusive = dateFinPeriode.plusDays(1).atStartOfDay();
        for (WorkInterval interval : intervals) {
            if (interval.end().isAfter(finExclusive)) {
                throw new IllegalStateException("POINTAGE_CROSS_PERIOD_SHIFT: la vacation du "
                        + interval.start() + " au " + interval.end()
                        + " dépasse la campagne se terminant le " + dateFinPeriode
                        + ". Scinder et imputer les heures du 26 à la campagne suivante avant validation.");
            }
        }

        var contextes = employmentContextResolver.resolvePointageDays(
                employeeId, dateDebutPeriode, dateFinPeriode, c);
        final LocalDate debutCalendrier = dateDebutPeriode;
        final LocalDate finCalendrier = dateFinPeriode;
        Map<Integer, Set<LocalDate>> feriesParSite = new HashMap<>();
        Set<LocalDate> feries = new HashSet<>();
        Map<LocalDate, TemporalAttendanceEngine.NightWindow> plagesNuit = new HashMap<>();
        for (LocalDate day = dateDebutPeriode; !day.isAfter(dateFinPeriode); day = day.plusDays(1)) {
            var contexte = contextes.get(day);
            if (contexte != null) {
                Set<LocalDate> datesFeries = feriesParSite.computeIfAbsent(contexte.siteId(),
                        site -> calendrierEngine.getDatesFeriesEtChomees(debutCalendrier, finCalendrier, site, c));
                if (datesFeries.contains(day)) feries.add(day);
            }
            var plageNuit = PointageService.resolveNightWindowAt(c, day);
            if (plageNuit != null) plagesNuit.put(day, plageNuit);
        }

        AttendanceTemporalPartitioner partitioner = new AttendanceTemporalPartitioner(
                feries::contains,
                date -> periodId,
                plagesNuit::get
        );
        List<TemporalBucket> allBuckets = partitioner.partition(intervals);

        List<QualifiedTimeBucket> qualifiedBuckets = new ArrayList<>();
        Set<TemporalBucket> projectedBuckets = new HashSet<>();
        OvertimeProjectionEngine projectionEngine = new OvertimeProjectionEngine();

        for (PointageTronconSemaineDTO troncon : troncons) {
            LocalDate debut = LocalDate.parse(troncon.getDebutSemaine());
            LocalDate fin = LocalDate.parse(troncon.getFinSemaine());
            List<TemporalBucket> tronconBuckets = allBuckets.stream()
                    .filter(b -> !b.start().toLocalDate().isBefore(debut) && !b.start().toLocalDate().isAfter(fin))
                    .toList();
            long h30 = hoursToMinutes(BigDecimal.valueOf(troncon.getHeuresHs30()));
            long h50 = hoursToMinutes(BigDecimal.valueOf(troncon.getHeuresHs50()));
            long totalBuckets = tronconBuckets.stream().mapToLong(TemporalBucket::minutes).sum();
            long regular = Math.max(0, totalBuckets - h30 - h50);
            if (!tronconBuckets.isEmpty()) {
                qualifiedBuckets.addAll(projectionEngine.project(tronconBuckets, regular, h30, h50));
                projectedBuckets.addAll(tronconBuckets);
            }
        }

        for (TemporalBucket b : allBuckets) {
            if (!projectedBuckets.contains(b)) {
                qualifiedBuckets.add(new QualifiedTimeBucket(
                        b.start(),
                        b.end(),
                        b.minutes(),
                        OvertimeTier.REGULAR,
                        b.dayType(),
                        b.nightType(),
                        b.payrollPeriodId(),
                        b.pointageId()
                ));
            }
        }

        String sqlInsertBucket = "INSERT INTO pointage_snapshot_bucket " +
                                 "(id_cumul, id_pointage, date_debut, date_fin, minutes, " +
                                 "overtime_tier, day_special_type, night_work_type, id_periode_paie) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement psB = c.prepareStatement(sqlInsertBucket)) {
            for (QualifiedTimeBucket qb : qualifiedBuckets) {
                psB.setInt(1, snapshotId);
                if (qb.pointageId() != null) {
                    psB.setInt(2, qb.pointageId());
                } else {
                    psB.setNull(2, java.sql.Types.INTEGER);
                }
                psB.setTimestamp(3, java.sql.Timestamp.valueOf(qb.start()));
                psB.setTimestamp(4, java.sql.Timestamp.valueOf(qb.end()));
                psB.setInt(5, (int) qb.minutes());
                psB.setString(6, qb.overtimeTier().name());
                psB.setString(7, qb.dayType().name());
                psB.setString(8, qb.nightType().name());
                psB.setInt(9, qb.payrollPeriodId());
                psB.addBatch();
            }
            psB.executeBatch();
        }
    }

    private static List<PointagePreflight.VacationChevauchante> vacationsSurDeuxCampagnes(
            int periodId, Connection c) throws SQLException {
        List<PointagePreflight.VacationChevauchante> chevauchantes = new ArrayList<>();
        LocalDate fin;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT date_fin FROM periode_paie WHERE id_periode_paie=?")) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("MISSING_CONFIGURATION: PERIODE_PAIE id=" + periodId);
                fin = rs.getDate(1).toLocalDate();
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id_pointage,id_employe,COALESCE(heure_entree_matin_effective,heure_entree_matin) AS em,"
                        + "heure_sortie_matin AS sm,heure_entree_am AS ea,heure_sortie_am AS sa,horaire_continu_jour "
                        + "FROM pointage_jour WHERE id_periode_paie=? AND date_pointage=?")) {
            ps.setInt(1, periodId);
            ps.setDate(2, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id_pointage");
                    var intervals = WorkIntervalFactory.createIntervals(rs.getInt("id_employe"), id, fin,
                            rs.getTime("em") == null ? null : rs.getTime("em").toLocalTime(),
                            rs.getTime("sm") == null ? null : rs.getTime("sm").toLocalTime(),
                            rs.getTime("ea") == null ? null : rs.getTime("ea").toLocalTime(),
                            rs.getTime("sa") == null ? null : rs.getTime("sa").toLocalTime(),
                            rs.getBoolean("horaire_continu_jour"), "PTG_" + id);
                    if (intervals.stream().anyMatch(interval -> interval.end().isAfter(fin.plusDays(1).atStartOfDay()))) {
                        chevauchantes.add(new PointagePreflight.VacationChevauchante(
                                id, rs.getInt("id_employe"), fin, fin.plusDays(1)));
                    }
                }
            }
        }
        return List.copyOf(chevauchantes);
    }

    public List<QualifiedTimeBucket> getSnapshotBuckets(int snapshotId, Connection c) throws SQLException {
        String sql = "SELECT date_debut, date_fin, minutes, overtime_tier, day_special_type, night_work_type, id_periode_paie, id_pointage " +
                     "FROM pointage_snapshot_bucket WHERE id_cumul = ? ORDER BY date_debut ASC, id_snapshot_bucket ASC";
        List<QualifiedTimeBucket> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, snapshotId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new QualifiedTimeBucket(
                            rs.getTimestamp("date_debut").toLocalDateTime(),
                            rs.getTimestamp("date_fin").toLocalDateTime(),
                            rs.getLong("minutes"),
                            OvertimeTier.valueOf(rs.getString("overtime_tier")),
                            DaySpecialType.valueOf(rs.getString("day_special_type")),
                            NightWorkType.valueOf(rs.getString("night_work_type")),
                            rs.getInt("id_periode_paie"),
                            rs.getObject("id_pointage") != null ? rs.getInt("id_pointage") : null
                    ));
                }
            }
        }
        return Collections.unmodifiableList(list);
    }

    public List<QualifiedTimeBucket> getSnapshotBuckets(int snapshotId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return getSnapshotBuckets(snapshotId, c);
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

        BigDecimal seuilHeuresJour = BigDecimal.valueOf(PointageService.resolveDailyTargetHours(
                ParametreGlobal.loadEtatMapAtDate(Date.valueOf(dateDebut), c)));

        // Découpage canonique unifié en tronçons (TRONÇON = SEMAINE ∩ PÉRIODE ∩ CALENDRIER APPLICABLE)
        List<AttendanceEngine.WeekSegment> segments = AttendanceEngine.segmentWeeks(dateDebut, dateFin);
        for (AttendanceEngine.WeekSegment seg : segments) {
            LocalDate dateDebutTroncon = seg.start();
            LocalDate dateFinTroncon = seg.end();
            int semNum = seg.index();

            // Calcul des jours ouvrés du tronçon via le moteur centralisé
            int nbOuvrables = (calendrierEngine != null)
                    ? calendrierEngine.compterJoursOuvrables(dateDebutTroncon, dateFinTroncon, idSite, c)
                    : CalendrierOuvrableEngine.calculerJoursOuvrables(dateDebutTroncon, dateFinTroncon);

            double seuilHeures = seuilHeuresJour.multiply(BigDecimal.valueOf(nbOuvrables)).doubleValue();

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

            boolean estValidee = false; // Un tronçon est calculé à la lecture, jamais validé séparément.

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

            if (totalPointages == 0) {
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

        Map<String, ParametreGlobal.ParametreEtat> etatMap = ParametreGlobal.loadEtatMapAtDate(dateDebutTroncon != null ? Date.valueOf(dateDebutTroncon) : null, c);
        Map<String, String> params = ParametreGlobal.activeValues(etatMap);
        BigDecimal seuilHeuresJour = BigDecimal.valueOf(PointageService.resolveDailyTargetHours(etatMap));
        BigDecimal montantRepasMidiDefaut = ParametreGlobal.getBigDecimal(etatMap, "MONTANT_REPAS_MIDI", null, BigDecimal.ZERO);
        if (montantRepasMidiDefaut == null) {
            Map<String, Object> details = new HashMap<>();
            details.put("type", "MONTANT_REPAS_MIDI");
            if (idSite != null) details.put("siteId", idSite);
            if (dateDebutTroncon != null) details.put("date", dateDebutTroncon);
            throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                    "Configuration d'indemnité absente : MONTANT_REPAS_MIDI.",
                    details, List.of());
        }

        // Compter les jours ouvrés du tronçon via le moteur centralisé
        int nbOuvrables = (calendrierEngine != null)
                ? calendrierEngine.compterJoursOuvrables(dateDebutTroncon, dateFinTroncon, idSite, c)
                : CalendrierOuvrableEngine.calculerJoursOuvrables(dateDebutTroncon, dateFinTroncon);

        // Charger les indemnités de déplacement/transport configurées par site (site_indemnite_config)
        Map<Integer, BigDecimal> siteDeplacementRefMap = new HashMap<>();
        String sqlSiteIndConfig = "SELECT c.id_site, c.montant_reference FROM site_indemnite_config c " +
                                  "JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite " +
                                  "WHERE i.code IN ('DPLCT', 'DEPLACEMENT', 'TRANSPORT') AND c.est_actif = TRUE";
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
            "a.date_debut AS date_debut_affectation, a.date_fin AS date_fin_affectation, " +
            "ct.date_debut AS date_debut_contrat, ct.date_fin AS date_fin_contrat, " +
            "s.id_site, s.code_site AS site_code, s.libelle AS site_nom, COALESCE(s.horaire_continu, TRUE) AS horaire_continu, " +
            "COALESCE(f.dispense_pointage, FALSE) AS est_autonome, " +
            "COALESCE(cp.code, '') AS code_categorie, COALESCE(cp.est_cadre, FALSE) AS categorie_est_cadre " +
            "FROM employe e " +
            "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
            "    AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
            "JOIN ref_statut_rh rsa ON a.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
            "LEFT JOIN contrat_travail ct ON ct.id_contrat = a.id_contrat " +
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
                    boolean isCadre = rsE.getBoolean("categorie_est_cadre");

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
                            laterDate(rsE.getDate("date_debut_affectation"), rsE.getDate("date_debut_contrat")),
                            earlierNonNullDate(rsE.getDate("date_fin_affectation"), rsE.getDate("date_fin_contrat")),
                            idPeriode,
                            null,
                            null,
                            null,
                            nbOuvrables,
                            siteDeplacementRefMap,
                            params,
                            etatMap,
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

    /** Compatibility endpoint: calculates the week list dynamically without persisting results. */
    public Map<String, Object> calculerToutesSemainesPeriode(int idPeriode, Integer idSite, Integer currentUserId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return calculerToutesSemainesPeriode(idPeriode, idSite, currentUserId, c);
        }
    }

    public Map<String, Object> calculerToutesSemainesPeriode(int idPeriode, Integer idSite, Integer currentUserId, Connection c) throws SQLException {
        List<Map<String, Object>> semaines = getSemainesPeriode(idPeriode, idSite, c);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("nbSemaines", semaines.size());
        result.put("message", semaines.size() + " semaine(s) calculée(s) à la demande, sans persistance.");
        return result;
    }

    private static void appendEmployeeSetFilter(StringBuilder where, List<Object> params, Set<Integer> ids) {
        if (ids.isEmpty()) {
            where.append("AND 1 = 0 ");
            return;
        }
        where.append("AND e.id_employe IN (")
                .append(String.join(",", Collections.nCopies(ids.size(), "?")))
                .append(") ");
        params.addAll(ids);
    }
}
