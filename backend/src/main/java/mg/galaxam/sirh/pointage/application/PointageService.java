package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import mg.galaxam.sirh.paie.domain.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine;
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
public class PointageService {

    record DailyAllowanceTariff(int id, String code, String label, boolean fractionable,
            String periodicity, BigDecimal siteAmount) {}

    DailyAllowanceTariff loadDailyAllowanceTariff(
            Connection connection, int siteId, int allowanceId, Date businessDate) throws SQLException {
        String sql = "SELECT i.id_indemnite, i.code, i.libelle, i.fractionnable, "
                + "c.periodicite, c.montant_reference "
                + "FROM site_indemnite_config c JOIN ref_indemnite i ON i.id_indemnite = c.id_indemnite "
                + "WHERE c.id_site = ? AND c.id_indemnite = ? AND c.est_actif = TRUE AND i.est_actif = TRUE "
                + "AND c.date_debut <= ? AND (c.date_fin IS NULL OR c.date_fin >= ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, siteId);
            statement.setInt(2, allowanceId);
            statement.setDate(3, businessDate);
            statement.setDate(4, businessDate);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                            "Indemnité " + allowanceId + " non configurée pour le site " + siteId
                                    + " au " + businessDate + ".");
                }
                DailyAllowanceTariff tariff = new DailyAllowanceTariff(
                        result.getInt("id_indemnite"), result.getString("code"), result.getString("libelle"),
                        result.getBoolean("fractionnable"), result.getString("periodicite"),
                        zeroIfNull(result.getBigDecimal("montant_reference")));
                if (result.next()) {
                    throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                            "Plusieurs tarifs applicables pour l'indemnité " + tariff.code() + " au " + businessDate + ".");
                }
                return tariff;
            }
        }
    }

    BigDecimal dailyAllowanceUnitAmount(DailyAllowanceTariff tariff, Date businessDate,
            int workingDays, Connection connection) {
        BigDecimal reference = tariff.siteAmount();
        if (reference.signum() < 0) {
            throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                    "Montant négatif pour l'indemnité " + tariff.code() + ".");
        }
        return switch (tariff.periodicity().toUpperCase(Locale.ROOT)) {
            case "JOURNALIER", "FORFAIT" -> reference;
            case "MENSUEL", "MENSUEL_PRORATISE" -> {
                if (workingDays <= 0) {
                    throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                            "Jours ouvrables manquants pour l'indemnité " + tariff.code() + ".");
                }
                yield calendrierEngine.calculerProrataJournalier(reference, workingDays);
            }
            default -> throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                    "Périodicité non applicable au pointage quotidien : " + tariff.periodicity() + ".");
        };
    }

    static BigDecimal requireMonetaryStateParameter(
            Map<String, ParametreGlobal.ParametreEtat> params, String code) {
        ParametreGlobal.ParametreResolution resolution = ParametreGlobal.resolve(params, code);
        if (resolution.isDisabled()) return BigDecimal.ZERO;
        BigDecimal value = parseMonetaryValue(resolution, code);
        if (value == null || value.signum() < 0) {
            throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                    "Configuration d'indemnité absente : " + code + ".");
        }
        return value;
    }

    private static BigDecimal parseMonetaryValue(
            ParametreGlobal.ParametreResolution resolution, String code) {
        if (resolution.isMissing()) return null;
        try {
            return new BigDecimal(resolution.valeur().trim());
        } catch (RuntimeException exception) {
            throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                    "Valeur d'indemnité invalide : " + code + ".");
        }
    }

    static LocalTime resolveOptionalTimeRule(
            Map<String, ParametreGlobal.ParametreEtat> params, String code) {
        ParametreGlobal.ParametreResolution resolution = ParametreGlobal.resolve(params, code);
        if (resolution.isDisabled()) return null;
        if (resolution.isMissing()) {
            throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                    "Configuration horaire absente : " + code + ".");
        }
        String raw = resolution.valeur();
        try {
            return LocalTime.parse(raw.length() == 5 ? raw : raw.substring(0, 8));
        } catch (RuntimeException exception) {
            throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                    "Valeur horaire invalide : " + code + ".");
        }
    }

    record MealTier(LocalTime threshold, BigDecimal amount) {}

    static MealTier resolveMealTier(Map<String, ParametreGlobal.ParametreEtat> params,
            String thresholdCode, String amountCode) {
        var threshold = ParametreGlobal.resolve(params, thresholdCode);
        var amount = ParametreGlobal.resolve(params, amountCode);
        if (threshold.isDisabled() || amount.isDisabled()) return new MealTier(null, BigDecimal.ZERO);
        return new MealTier(resolveOptionalTimeRule(params, thresholdCode),
                requireMonetaryStateParameter(params, amountCode));
    }

    record SecondMealAward(String code, BigDecimal amount) {}

    static SecondMealAward resolveSecondMealAward(boolean nonWorked, LocalTime afternoonExit,
            MealTier first, MealTier second) {
        if (nonWorked || afternoonExit == null) return null;
        if (second.threshold() != null && !afternoonExit.isBefore(second.threshold())) {
            return new SecondMealAward("REPAS_2_MAJORE", second.amount());
        }
        if (first.threshold() != null && !afternoonExit.isBefore(first.threshold())) {
            return new SecondMealAward("REPAS_2", first.amount());
        }
        return null;
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine calendrierEngine;

    @Autowired
    private AttendanceEngine dailyAttendanceCalculator;

    public PointageService() {
        this.calendrierEngine = new CalendrierOuvrableEngine();
        this.dailyAttendanceCalculator = new AttendanceEngine();
    }

    public PointageService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.calendrierEngine = new CalendrierOuvrableEngine();
        this.dailyAttendanceCalculator = new AttendanceEngine();
    }

    public PointageService(DataSource dataSource,
                                 mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine calendrierEngine,
                                 mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine ignored) {
        this.dataSource = dataSource;
        this.calendrierEngine = ignored != null ? ignored : new CalendrierOuvrableEngine();
        this.dailyAttendanceCalculator = new AttendanceEngine();
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

    /**
     * Plancher d'arrivée valorisé (earliest paid start).
     *
     * <p>Ce n'est PAS une tolérance de retard. C'est un plancher en début de journée :
     * si l'employé badge avant l'heure minimale comptabilisée, seules les minutes
     * à partir de cette heure minimale sont valorisées.
     *
     * <p>Exemples :
     * <ul>
     *   <li>badge 07:00, plancher 07:30 → effectif 07:30 (30 min valorisées avant 08:00)</li>
     *   <li>badge 07:45, plancher 07:30 → effectif 07:45 (badge supérieur au plancher)</li>
     *   <li>badge 07:00, plancher null (règle désactivée) → effectif 07:00</li>
     * </ul>
     *
     * <p>L'heure badgée réelle reste stockée dans {@code pointage_jour.heure_entree_matin}.
     * L'heure effective est stockée dans {@code pointage_jour.heure_entree_matin_effective}.
     *
     * @param punched        heure badgée réelle, null si absent
     * @param earliestCounted heure minimale comptabilisée (config site), null si règle désactivée
     * @return max(punched, earliestCounted), ou punched si règle désactivée
     */
    public static LocalTime effectiveMorningEntry(LocalTime punched, LocalTime earliestCounted) {
        if (punched == null || earliestCounted == null) return punched;
        return punched.isBefore(earliestCounted) ? earliestCounted : punched;
    }

    /**
     * Retourne le plancher d'arrivée valorisé configuré pour ce site, ou {@code null}
     * si la règle est désactivée ({@code plancher_arrivee_actif = FALSE}) ou absente.
     *
     * <p>Quand null est retourné, {@link #effectiveMorningEntry} utilise le badge brut directement.
     * Aucun fallback implicite n'est appliqué.
     *
     * <p>La configuration est stockée directement dans {@code ref_site} (colonnes
     * {@code plancher_arrivee_actif} et {@code heure_entree_minimale_valorisee}).
     * Le concept est un plancher d'arrivée valorisé (earliest_paid_start), pas une tolérance retard.
     */
    public static LocalTime activeArrivalFloor(Connection c, int siteId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT heure_entree_minimale_valorisee FROM ref_site WHERE id_site = ? AND plancher_arrivee_actif = TRUE")) {
            ps.setInt(1, siteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? toLocalTime(rs.getTime(1)) : null;
            }
        }
    }

    static double resolveDailyTargetHours(Map<String, ParametreGlobal.ParametreEtat> params) {
        ParametreGlobal.ParametreResolution resolution = ParametreGlobal.resolve(params, "SEUIL_HEURES_JOUR");
        if (resolution.isDisabled()) return 0.0;
        if (resolution.isMissing()) {
            throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                    "Objectif journalier absent : SEUIL_HEURES_JOUR.");
        }
        try {
            BigDecimal hours = new BigDecimal(resolution.valeur().trim());
            if (hours.signum() < 0) throw new NumberFormatException("target cannot be negative");
            return hours.doubleValue();
        } catch (RuntimeException exception) {
            throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                    "Objectif journalier invalide : SEUIL_HEURES_JOUR.");
        }
    }

    /** Conversion de frontière uniquement : le domaine conserve les minutes entières. */
    private static double minutesToStorageHours(long minutes) {
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 8, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static AttendanceEngine.Context.LeavePart parseLeavePart(String value) {
        if (value == null || value.isBlank()) return AttendanceEngine.Context.LeavePart.NONE;
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "FULL_DAY" -> AttendanceEngine.Context.LeavePart.FULL_DAY;
            case "MATIN" -> AttendanceEngine.Context.LeavePart.MORNING;
            case "APRES_MIDI" -> AttendanceEngine.Context.LeavePart.AFTERNOON;
            default -> AttendanceEngine.Context.LeavePart.NONE;
        };
    }

    private static String toPersistenceStatus(AttendanceEngine.Result result, SaisiePointageJourDTO dto) {
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

    static String resolveDayStatus(SaisiePointageJourDTO dto, AttendanceEngine.Result result, LocalDate date) {
        String code = dto.getCodeStatut();
        if (!result.anomalies().isEmpty()) return "ANOMALIE_SAISIE";
        if ("FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())) {
            return toPersistenceStatus(result, dto);
        }
        // Absence is an explicit administrative decision; presence, rest and anomaly
        // are calculated afresh from the punches and the calendar.
        if ("ABSENCE_JUSTIFIEE".equals(code) || "ABSENCE_NON_JUSTIFIEE".equals(code)) return code;
        boolean hasPunch = hasTime(dto.getHeureEntreeMatin()) || hasTime(dto.getHeureSortieMatin())
                || hasTime(dto.getHeureEntreeAm()) || hasTime(dto.getHeureSortieAm());
        if (!hasPunch && !dto.isFerieJour() && !CalendrierOuvrableEngine.isWeekend(date)
                && !dto.isEstAutonome() && dto.getTypeCongeJour() == null) return "NON_SAISI";
        String calculated = toPersistenceStatus(result, dto);
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY && result.workedMinutes() > 0
                && !dto.isFerieJour()) return "SAMEDI_TRAVAILLE";
        return calculated;
    }

    private static boolean hasTime(String value) {
        return value != null && !value.isBlank() && !"—".equals(value) && !"--:--".equals(value);
    }

    private static void normalizeDayIntent(SaisiePointageJourDTO dto, LocalTime startMorning, LocalTime endMorning,
                                          LocalTime startAfternoon, LocalTime endAfternoon) {
        String code = dto.getCodeStatut();
        if ("ABSENCE_JUSTIFIEE".equals(code) || "ABSENCE_NON_JUSTIFIEE".equals(code) || "CONGE".equals(code)) {
            dto.setHeureEntreeMatin(null);
            dto.setHeureSortieMatin(null);
            dto.setHeureEntreeAm(null);
            dto.setHeureSortieAm(null);
            dto.setDeplacementMatin(false);
            dto.setDeplacementAm(false);
            dto.setIndemniteDeplacementRecue(false);
            dto.setIndemniteRepasRecue(false);
            if ("CONGE".equals(code)) dto.setTypeCongeJour("FULL_DAY");
            Map<String, Object> normalized = new HashMap<>();
            if (dto.getDynamicIndemnites() != null) {
                dto.getDynamicIndemnites().forEach((key, value) -> {
                    if (value instanceof Map<?, ?> current) {
                        Map<String, Object> updated = new HashMap<>();
                        current.forEach((k, v) -> updated.put(String.valueOf(k), v));
                        if ("CONGE".equals(code)) {
                            // Pour les conges : conserver les valeurs explicitement cochees (exception manuelle).
                            // Ne pas forcer a false si l'utilisateur a explicitement accorde l'indemnite.
                            updated.putIfAbsent("accordee", false);
                            updated.putIfAbsent("matin", false);
                            updated.putIfAbsent("am", false);
                        } else {
                            // Pour les absences justifiees/non-justifiees : toujours effacer.
                            updated.put("accordee", false);
                            updated.put("matin", false);
                            updated.put("am", false);
                        }
                        normalized.put(key, updated);
                    }
                });
            }
            dto.setDynamicIndemnites(normalized);
        } else if ("PRESENT".equals(code)
                && dto.getHeureEntreeMatin() == null && dto.getHeureSortieMatin() == null
                && dto.getHeureEntreeAm() == null && dto.getHeureSortieAm() == null) {
            dto.setHeureEntreeMatin(startMorning != null ? startMorning.toString() : null);
            dto.setHeureSortieMatin(endMorning != null ? endMorning.toString() : null);
            dto.setHeureEntreeAm(startAfternoon != null ? startAfternoon.toString() : null);
            dto.setHeureSortieAm(endAfternoon != null ? endAfternoon.toString() : null);
            dto.setDeplacementMatin(true);
            dto.setDeplacementAm(true);
            dto.setIndemniteDeplacementRecue(true);
        }
    }

    public SaisiePointageJourDTO previsualiserPointage(SaisiePointageJourDTO dto) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return previsualiserPointage(dto, c);
        }
    }

    SaisiePointageJourDTO previsualiserPointage(SaisiePointageJourDTO dto, Connection c) throws SQLException {
        if (dto == null || dto.getIdEmploye() == null || dto.getIdSite() == null || dto.getDatePointage() == null) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "Employé, site et date sont obligatoires pour prévisualiser une journée.");
        }
        LocalDate date = LocalDate.parse(dto.getDatePointage());
        LocalTime startMorning;
        LocalTime endMorning;
        LocalTime startAfternoon;
        LocalTime endAfternoon;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT heure_debut_matin, heure_fin_matin, heure_debut_apres_midi, heure_fin_apres_midi " +
                        "FROM ref_site WHERE id_site = ?")) {
            ps.setInt(1, dto.getIdSite());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Site introuvable.");
                startMorning = toLocalTime(rs.getTime("heure_debut_matin"));
                endMorning = toLocalTime(rs.getTime("heure_fin_matin"));
                startAfternoon = toLocalTime(rs.getTime("heure_debut_apres_midi"));
                endAfternoon = toLocalTime(rs.getTime("heure_fin_apres_midi"));
            }
        }
        boolean scheduleComplete = startMorning != null && endAfternoon != null
                && (dto.isHoraireContinu() || (endMorning != null && startAfternoon != null));
        if (!scheduleComplete) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "Horaires de travail incomplets pour le site " + dto.getIdSite() + ".");
        }
        normalizeDayIntent(dto, startMorning, endMorning, startAfternoon, endAfternoon);
        LocalTime effectiveEntry = effectiveMorningEntry(
                toLocalTime(parseTime(dto.getHeureEntreeMatin())), activeArrivalFloor(c, dto.getIdSite()));
        LocalTime morningExit = toLocalTime(parseTime(dto.getHeureSortieMatin()));
        LocalTime dayExit = toLocalTime(parseTime(dto.getHeureSortieAm()));
        if (effectiveEntry != null && ((dto.isHoraireContinu() && dayExit != null && effectiveEntry.isAfter(dayExit))
                || (!dto.isHoraireContinu() && morningExit != null && effectiveEntry.isAfter(morningExit)))) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "La sortie précède l'heure minimale comptabilisée du site.");
        }
        Map<String, ParametreGlobal.ParametreEtat> params = ParametreGlobal.loadEtatMap(c);
        AttendanceEngine.Result result = dailyAttendanceCalculator.calculateDay(new AttendanceEngine.Context(
                dto.getIdEmploye(), date, effectiveEntry,
                toLocalTime(parseTime(dto.getHeureSortieMatin())), toLocalTime(parseTime(dto.getHeureEntreeAm())),
                toLocalTime(parseTime(dto.getHeureSortieAm())), startMorning, endMorning, startAfternoon, endAfternoon,
                resolveOptionalTimeRule(params, "SEUIL_NUIT_DEBUT"), resolveOptionalTimeRule(params, "SEUIL_NUIT_FIN"),
                dto.isHoraireContinu(), dto.isEstAutonome(), dto.isFerieJour(), parseLeavePart(dto.getTypeCongeJour()),
                false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
        dto.setHeuresMatin(minutesToStorageHours(result.morningMinutes()));
        dto.setHeuresAm(minutesToStorageHours(result.afternoonMinutes()));
        dto.setTotalHeures(minutesToStorageHours(result.workedMinutes()));
        dto.setHeuresNuit(minutesToStorageHours(result.nightMinutes()));
        dto.setMinutesRetard(Math.toIntExact(result.lateMinutes()));
        dto.setHasValidationError(!result.anomalies().isEmpty());
        dto.setValidationMessages(result.anomalies());
        dto.setCodeStatut(resolveDayStatus(dto, result, date));
        dto.setLibelleStatut(statusLabel(dto.getCodeStatut()));
        dto.setStatusActions(resolveStatusActions(dto));
        return dto;
    }

    private static List<SaisiePointageJourDTO.StatusAction> resolveStatusActions(SaisiePointageJourDTO dto) {
        if (dto.isFerieChome() || dto.getTypeCongeJour() != null || dto.isEstAutonome()) return List.of();
        return List.of(
                new SaisiePointageJourDTO.StatusAction("PRESENT", "Présent", "Journée travaillée", false),
                new SaisiePointageJourDTO.StatusAction("ABSENCE_JUSTIFIEE", "Absence justifiée", "Absence documentée ou autorisée", true),
                new SaisiePointageJourDTO.StatusAction("ABSENCE_NON_JUSTIFIEE", "Absence non justifiée", "Absence sans justificatif", false)
        );
    }

    private static String statusLabel(String code) {
        return switch (code == null ? "" : code) {
            case "PRESENT" -> "Présent";
            case "RETARD" -> "Retard";
            case "DEMI_JOURNEE" -> "Demi-journée travaillée";
            case "ABSENCE_NON_JUSTIFIEE" -> "Absent";
            case "ABSENCE_JUSTIFIEE" -> "Absence justifiée";
            case "ANOMALIE_SAISIE" -> "Incomplet / Anomalie";
            case "CONGE" -> "Congé";
            case "DISPENSE_POINTAGE" -> "Dispense de pointage";
            case "FERIE_TRAVAILLE" -> "Férié travaillé";
            case "FERIE_CHOME" -> "Férié chômé";
            case "SAMEDI_TRAVAILLE" -> "Samedi travaillé";
            case "DIMANCHE_TRAVAILLE" -> "Dimanche travaillé";
            case "REPOS" -> "Repos";
            case "NON_SAISI" -> "Non saisi";
            default -> "Non saisi";
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
                    Boolean continuu = rs.wasNull() ? null : continu;

                    siteParams.put("horaireContinu", continuu);
                    siteParams.put("heureDebutMatin", hdm != null ? hdm.toString().substring(0, 5) : null);
                    siteParams.put("heureFinMatin", hfm != null ? hfm.toString().substring(0, 5) : null);
                    siteParams.put("heureDebutAm", hda != null ? hda.toString().substring(0, 5) : null);
                    siteParams.put("heureFinAm", hfa != null ? hfa.toString().substring(0, 5) : null);
                }
            }

            // 4. Indemnités Spécifiques du Site (site_indemnite_config) avec Prorata sur jours ouvrables réels
            int nbOuvrablesPeriode = (periodeInfo != null && periodeInfo.get("nbJoursOuvrables") != null)
                    ? (int) periodeInfo.get("nbJoursOuvrables") : 0;
            if (nbOuvrablesPeriode <= 0) {
                LocalDate d = date.toLocalDate();
                nbOuvrablesPeriode = calendrierEngine.compterJoursOuvrables(
                        d.withDayOfMonth(1), d.withDayOfMonth(d.lengthOfMonth()), idSite > 0 ? idSite : null, c);
                if (nbOuvrablesPeriode <= 0) {
                    nbOuvrablesPeriode = 22; // Sécurité de repli
                }
            }

            // Les tarifs d'indemnité sont résolus à la date du pointage.
            Map<String, ParametreGlobal.ParametreEtat> globalParams = ParametreGlobal.loadEtatMapAtDate(date, c);
            BigDecimal mntRepasMidiGlobal = BigDecimal.ZERO;
            double dureeJourReference = resolveDailyTargetHours(globalParams);

            BigDecimal montantDeplacementMois = BigDecimal.ZERO;
            BigDecimal montantDeplacementJour = BigDecimal.ZERO;
            BigDecimal montantRepasSite = BigDecimal.ZERO;
            boolean siteRepasInclusParDefaut = false;

            List<Map<String, Object>> siteIndemnites = new ArrayList<>();
            Set<Integer> seenSiteAllowances = new HashSet<>();
            String sqlSiteInd = "SELECT c.id_indemnite, i.code, i.libelle, COALESCE(i.fractionnable, FALSE) AS fractionnable, "
                    + "c.periodicite, c.montant_reference "
                    + "FROM site_indemnite_config c JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite "
                    + "WHERE c.id_site = ? AND c.est_actif = TRUE AND i.est_actif = TRUE "
                    + "AND c.date_debut <= ? AND (c.date_fin IS NULL OR c.date_fin >= ?) "
                    + "AND c.periodicite != 'FIXE_MENSUEL' ORDER BY i.code";
            try (PreparedStatement psI = c.prepareStatement(sqlSiteInd)) {
                psI.setInt(1, idSite);
                psI.setDate(2, date);
                psI.setDate(3, date);
                try (ResultSet rsI = psI.executeQuery()) {
                    while (rsI.next()) {
                        Map<String, Object> ind = new HashMap<>();
                        int idInd = rsI.getInt("id_indemnite");
                        if (!seenSiteAllowances.add(idInd)) {
                            throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                                    "Plusieurs tarifs actifs couvrent la même date pour l'indemnité " + idInd
                                            + " sur le site " + idSite + ".");
                        }
                        String codeInd = rsI.getString("code");
                        String libInd = rsI.getString("libelle");
                        boolean fractionnable = rsI.getBoolean("fractionnable");
                        String period = rsI.getString("periodicite");
                        BigDecimal montantRef = zeroIfNull(rsI.getBigDecimal("montant_reference"));

                        boolean isProratise = "MENSUEL".equalsIgnoreCase(period) || "MENSUEL_PRORATISE".equalsIgnoreCase(period);
                        DailyAllowanceTariff tariff = new DailyAllowanceTariff(
                                idInd, codeInd, libInd, fractionnable, period, montantRef);
                        BigDecimal montantJour = dailyAllowanceUnitAmount(tariff, date, nbOuvrablesPeriode, c);
                        BigDecimal montantTrajet = fractionnable ? calendrierEngine.calculerMontantTrajet(montantJour) : montantJour;

                        // Un tarif REPAS à 0 représente un repas fourni sans versement.
                        boolean isSiteZero = montantRef.signum() == 0;
                        BigDecimal montantGlobal = montantJour;
                        boolean useGlobalIfZero = false;

                        String sousTitre;
                        if (isSiteZero && "REPAS".equalsIgnoreCase(codeInd)) {
                            sousTitre = "0 Ar/j — cocher si repas fourni sur site";
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
                        ind.put("montantGlobal", montantGlobal);
                        // Conservé dans le contrat de grille pour les clients existants.
                        ind.put("useGlobalIfZero", useGlobalIfZero);
                        ind.put("cocheParDefaut", !isSiteZero);
                        ind.put("montantTrajet", montantTrajet);
                        ind.put("sousTitre", sousTitre);
                        ind.put("nbJoursOuvrablesPeriode", nbOuvrablesPeriode);
                        siteIndemnites.add(ind);

                        if ("DPLCT".equalsIgnoreCase(codeInd) || "DEPLACEMENT".equalsIgnoreCase(codeInd) || "TRANSPORT".equalsIgnoreCase(codeInd)) {
                            montantDeplacementMois = montantRef;
                            montantDeplacementJour = montantJour;
                        } else if ("REPAS".equalsIgnoreCase(codeInd)) {
                            montantRepasSite = montantRef;
                            siteRepasInclusParDefaut = montantRef.signum() > 0;
                            mntRepasMidiGlobal = montantGlobal;
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

            // Charge en lot les données variables par salarié. Les anciennes lectures dans
            // la boucle faisaient jusqu'à quatre requêtes SQL par personne (congé, pointage,
            // indemnités et dérogations), ce qui rendait la grille lente dès que le site
            // comptait beaucoup de salariés.
            Map<Integer, Map<String, Object>> congesParEmploye = new HashMap<>();
            String sqlCongesJour = "SELECT dc.id_employe, tc.libelle, tc.code AS type_code, "
                    + "COALESCE(dc.est_remunere, tc.est_paye) AS est_paye, tc.decompte_solde_acquis, "
                    + "dc.est_demi_journee, dc.moment_demi_journee "
                    + "FROM demande_conge dc JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge "
                    + "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge "
                    + "WHERE ? BETWEEN dc.date_debut AND dc.date_fin AND sc.code IN ('VALIDE', 'APPROUVEE')";
            try (PreparedStatement ps = c.prepareStatement(sqlCongesJour)) {
                ps.setDate(1, date);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("libelle", rs.getString("libelle"));
                        row.put("type_code", rs.getString("type_code"));
                        row.put("est_paye", rs.getBoolean("est_paye"));
                        row.put("decompte_solde_acquis", rs.getBoolean("decompte_solde_acquis"));
                        row.put("est_demi_journee", rs.getBoolean("est_demi_journee"));
                        row.put("moment_demi_journee", rs.getString("moment_demi_journee"));
                        congesParEmploye.putIfAbsent(rs.getInt("id_employe"), row);
                    }
                }
            }

            Map<Integer, Map<String, Object>> pointagesParEmploye = new HashMap<>();
            String sqlPointagesJour = "SELECT p.id_employe, p.id_pointage, COALESCE(p.version, 1) AS version, "
                    + "p.heure_entree_matin, p.heure_sortie_matin, p.heure_entree_am, p.heure_sortie_am, "
                    + "p.total_heures, p.minutes_retard, p.heures_nuit, p.motif_ajustement, "
                    + "st.code AS statut_code, st.libelle AS statut_libelle, p.horaire_continu_jour "
                    + "FROM pointage_jour p LEFT JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour "
                    + "WHERE p.date_pointage = ? ORDER BY p.id_pointage";
            try (PreparedStatement ps = c.prepareStatement(sqlPointagesJour)) {
                ps.setDate(1, date);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id_pointage", rs.getInt("id_pointage"));
                        row.put("version", rs.getInt("version"));
                        row.put("heure_entree_matin", rs.getTime("heure_entree_matin"));
                        row.put("heure_sortie_matin", rs.getTime("heure_sortie_matin"));
                        row.put("heure_entree_am", rs.getTime("heure_entree_am"));
                        row.put("heure_sortie_am", rs.getTime("heure_sortie_am"));
                        row.put("total_heures", rs.getDouble("total_heures"));
                        row.put("minutes_retard", rs.getInt("minutes_retard"));
                        row.put("heures_nuit", rs.getDouble("heures_nuit"));
                        row.put("motif_ajustement", rs.getString("motif_ajustement"));
                        row.put("statut_code", rs.getString("statut_code"));
                        row.put("statut_libelle", rs.getString("statut_libelle"));
                        row.put("horaire_continu_jour", rs.getObject("horaire_continu_jour"));
                        pointagesParEmploye.putIfAbsent(rs.getInt("id_employe"), row);
                    }
                }
            }

            Map<Integer, List<Map<String, Object>>> indemnitesParPointage = new HashMap<>();
            String sqlIndemnitesJour = "SELECT p.id_employe, pij.id_pointage, pij.id_indemnite, ri.code, "
                    + "pij.fraction, pij.quantite, pij.montant_calcule, pij.accorde "
                    + "FROM pointage_jour p JOIN pointage_indemnite_jour pij ON pij.id_pointage = p.id_pointage "
                    + "JOIN ref_indemnite ri ON pij.id_indemnite = ri.id_indemnite WHERE p.date_pointage = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlIndemnitesJour)) {
                ps.setDate(1, date);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id_indemnite", rs.getInt("id_indemnite"));
                        row.put("code", rs.getString("code"));
                        row.put("fraction", rs.getString("fraction"));
                        row.put("quantite", rs.getBigDecimal("quantite"));
                        row.put("montant_calcule", rs.getBigDecimal("montant_calcule"));
                        row.put("accorde", rs.getBoolean("accorde"));
                        indemnitesParPointage.computeIfAbsent(rs.getInt("id_pointage"), ignored -> new ArrayList<>()).add(row);
                    }
                }
            }

            Map<Integer, List<Map<String, Object>>> derogationsParEmploye = new HashMap<>();
            String sqlDerogationsJour = "SELECT d.id_employe, d.id_indemnite, i.code, d.beneficie, d.motif "
                    + "FROM employe_indemnite_derogation d JOIN ref_indemnite i ON d.id_indemnite = i.id_indemnite "
                    + "WHERE d.date_debut <= ? AND (d.date_fin IS NULL OR d.date_fin >= ?)";
            try (PreparedStatement ps = c.prepareStatement(sqlDerogationsJour)) {
                ps.setDate(1, date);
                ps.setDate(2, date);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id_indemnite", rs.getInt("id_indemnite"));
                        row.put("code", rs.getString("code"));
                        row.put("beneficie", rs.getBoolean("beneficie"));
                        row.put("motif", rs.getString("motif"));
                        derogationsParEmploye.computeIfAbsent(rs.getInt("id_employe"), ignored -> new ArrayList<>()).add(row);
                    }
                }
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

                        // Congé approuvé ce jour (préchargé pour toute la date)
                        String typeCongeJour = null;
                        String libelleConge = null;

                        Map<String, Object> conge = isFerie ? null : congesParEmploye.get(idEmp);
                        if (conge != null) {
                                        String tCode = (String) conge.get("type_code");
                                        boolean estPayeConge = Boolean.TRUE.equals(conge.get("est_paye"));
                                        boolean decompteSolde = Boolean.TRUE.equals(conge.get("decompte_solde_acquis"));
                                        String baseLib = (String) conge.get("libelle");

                                        dto.setCongePaye(estPayeConge);
                                        dto.setCongeDecompteSolde(decompteSolde);

                                        if ("PERMISSION".equalsIgnoreCase(tCode)) {
                                            libelleConge = estPayeConge ? "Permission autorisée (Payée)" : "Permission autorisée (Non payée)";
                                        } else if (!estPayeConge) {
                                            libelleConge = baseLib + " (Sans solde)";
                                        } else {
                                            libelleConge = baseLib;
                                        }

                                        boolean estDemi = Boolean.TRUE.equals(conge.get("est_demi_journee"));
                                        String mdj = (String) conge.get("moment_demi_journee");
                                        if (estDemi && "MATIN".equalsIgnoreCase(mdj)) typeCongeJour = "MATIN";
                                        else if (estDemi && "APRES_MIDI".equalsIgnoreCase(mdj)) typeCongeJour = "APRES_MIDI";
                                        else typeCongeJour = "FULL_DAY";
                        }
                        dto.setTypeCongeJour(typeCongeJour);
                        dto.setLibelleConge(libelleConge);

                        // Pointage journalier existant
                        boolean hasExisting = false;
                        Map<String, Object> existingPointage = pointagesParEmploye.get(idEmp);
                        if (existingPointage != null) {
                                    hasExisting = true;
                                    dto.setIdPointage((Integer) existingPointage.get("id_pointage"));
                                    dto.setVersion((Integer) existingPointage.get("version"));
                                    Time em = (Time) existingPointage.get("heure_entree_matin");
                                    Time sm = (Time) existingPointage.get("heure_sortie_matin");
                                    Time ea = (Time) existingPointage.get("heure_entree_am");
                                    Time sa = (Time) existingPointage.get("heure_sortie_am");

                                    dto.setHeureEntreeMatin(em != null ? em.toString().substring(0, 5) : "");
                                    dto.setHeureSortieMatin(sm != null ? sm.toString().substring(0, 5) : "");
                                    dto.setHeureEntreeAm(ea != null ? ea.toString().substring(0, 5) : "");
                                    dto.setHeureSortieAm(sa != null ? sa.toString().substring(0, 5) : "");
                                    dto.setTotalHeures((Double) existingPointage.get("total_heures"));
                                    dto.setMinutesRetard((Integer) existingPointage.get("minutes_retard"));
                                    dto.setHeuresNuit((Double) existingPointage.get("heures_nuit"));
                                    dto.setMotifAjustement((String) existingPointage.get("motif_ajustement"));
                                    dto.setCodeStatut((String) existingPointage.get("statut_code"));
                                    dto.setLibelleStatut((String) existingPointage.get("statut_libelle"));
                                    dto.setHoraireContinu(Boolean.TRUE.equals(existingPointage.get("horaire_continu_jour")));

                                    boolean depM = false;
                                    boolean depA = false;
                                    boolean depRecue = false;
                                    BigDecimal mntDepDb = BigDecimal.ZERO;
                                    boolean repasRecue = false;
                                    BigDecimal mntRepasDb = BigDecimal.ZERO;

                                    for (Map<String, Object> savedAllowance : indemnitesParPointage.getOrDefault(dto.getIdPointage(), List.of())) {
                                                String codeInd = (String) savedAllowance.get("code");
                                                boolean accorde = Boolean.TRUE.equals(savedAllowance.get("accorde"));
                                                BigDecimal mntCalc = zeroIfNull((BigDecimal) savedAllowance.get("montant_calcule"));
                                                String fraction = (String) savedAllowance.get("fraction");
                                                BigDecimal qte = zeroIfNull((BigDecimal) savedAllowance.get("quantite"));

                                                if ("REPAS".equalsIgnoreCase(codeInd)) {
                                                    repasRecue = accorde;
                                                    mntRepasDb = mntCalc;
                                                } else if ("DPLCT".equalsIgnoreCase(codeInd) || "DEPLACEMENT".equalsIgnoreCase(codeInd) || "TRANSPORT".equalsIgnoreCase(codeInd)) {
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

                                    dto.setDeplacementMatin(depM);
                                    dto.setDeplacementAm(depA);
                                    dto.setIndemniteDeplacementRecue(depRecue);

                                    BigDecimal fractionDep = (depM ? new BigDecimal("0.5") : BigDecimal.ZERO)
                                            .add(depA ? new BigDecimal("0.5") : BigDecimal.ZERO);
                                    dto.setMontantDeplacement(mntDepDb.signum() > 0 ? mntDepDb
                                            : (depRecue ? MoneyRoundingPolicy.amount(montantDeplacementJour.multiply(fractionDep)) : BigDecimal.ZERO));

                                    dto.setIndemniteRepasRecue(repasRecue);
                                    dto.setMontantRepas(repasRecue ? mntRepasDb : BigDecimal.ZERO);
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
                                    dto.setTotalHeures(dureeJourReference);
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
                                dto.setTotalHeures(isAutonome ? dureeJourReference / 2.0 : 0.0);
                                dto.setMinutesRetard(0);
                                dto.setCodeStatut(isAutonome ? "DISPENSE_POINTAGE" : "NON_SAISI");
                                dto.setLibelleStatut(isAutonome ? "Autonome" : "Congé Matin");
                                dto.setDeplacementMatin(false);
                                dto.setDeplacementAm(true);
                                dto.setIndemniteRepasRecue(false);
                            } else if ("APRES_MIDI".equalsIgnoreCase(typeCongeJour)) {
                                dto.setHeureEntreeMatin(isAutonome ? (String) siteParams.get("heureDebutMatin") : "");
                                dto.setHeureSortieMatin(isAutonome ? (String) siteParams.get("heureFinMatin") : "");
                                dto.setHeureEntreeAm("—");
                                dto.setHeureSortieAm("—");
                                dto.setTotalHeures(isAutonome ? dureeJourReference / 2.0 : 0.0);
                                dto.setMinutesRetard(0);
                                dto.setCodeStatut(isAutonome ? "DISPENSE_POINTAGE" : "NON_SAISI");
                                dto.setLibelleStatut(isAutonome ? "Autonome" : "Congé Après-midi");
                                dto.setDeplacementMatin(true);
                                dto.setDeplacementAm(false);
                                dto.setIndemniteRepasRecue(siteRepasInclusParDefaut);
                            } else if (CalendrierOuvrableEngine.isWeekend(targetDate)) {
                                // Week-end sans pointage existant : repos de fin de semaine par défaut pour tous (y compris autonome)
                                dto.setHeureEntreeMatin("");
                                dto.setHeureSortieMatin("");
                                dto.setHeureEntreeAm("");
                                dto.setHeureSortieAm("");
                                dto.setTotalHeures(dureeJourReference);
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
                                dto.setTotalHeures(dureeJourReference);
                                dto.setMinutesRetard(0);
                                dto.setCodeStatut("DISPENSE_POINTAGE");
                                dto.setLibelleStatut("Autonome");
                                dto.setDeplacementMatin(true);
                                dto.setDeplacementAm(true);
                                dto.setIndemniteDeplacementRecue(true);
                                dto.setMontantDeplacement(montantDeplacementJour);
                                dto.setIndemniteRepasRecue(siteRepasInclusParDefaut);
                                dto.setMontantRepas(siteRepasInclusParDefaut ? montantRepasSite : BigDecimal.ZERO);
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
                                dto.setMontantRepas(siteRepasInclusParDefaut ? montantRepasSite : BigDecimal.ZERO);
                            }
                        }

                        // Indemnités dynamiques & Dérogations individuelles
                        Map<String, Object> dynMap = new HashMap<>();

                        if (hasExisting && dto.getIdPointage() != null) {
                            // 1. Charger depuis pointage_indemnite_jour si déjà enregistré
                            Map<Integer, Map<String, Object>> savedDyn = new HashMap<>();
                            for (Map<String, Object> savedRow : indemnitesParPointage.getOrDefault(dto.getIdPointage(), List.of())) {
                                int allowanceId = (Integer) savedRow.get("id_indemnite");
                                Map<String, Object> item = savedDyn.computeIfAbsent(allowanceId, ignored -> new HashMap<>());
                                boolean acc = Boolean.TRUE.equals(savedRow.get("accorde"));
                                String frac = (String) savedRow.get("fraction");
                                BigDecimal qte = zeroIfNull((BigDecimal) savedRow.get("quantite"));
                                item.put("accordee", Boolean.TRUE.equals(item.get("accordee")) || acc);
                                item.put("montantApplique", zeroIfNull((BigDecimal) item.get("montantApplique"))
                                        .add(zeroIfNull((BigDecimal) savedRow.get("montant_calcule"))));
                                item.put("fractionJour", zeroIfNull((BigDecimal) item.get("fractionJour")).add(qte));
                                item.put("fraction", frac);
                                if ("MATIN".equalsIgnoreCase(frac)) {
                                    item.put("matin", Boolean.TRUE.equals(item.get("matin")) || acc);
                                } else if ("APRES_MIDI".equalsIgnoreCase(frac)) {
                                    item.put("am", Boolean.TRUE.equals(item.get("am")) || acc);
                                } else {
                                    item.put("matin", Boolean.TRUE.equals(item.get("matin")) || acc && qte.signum() > 0);
                                    item.put("am", Boolean.TRUE.equals(item.get("am")) || acc && qte.compareTo(BigDecimal.ONE) >= 0);
                                }
                            }
                            for (Map<String, Object> sInd : siteIndemnites) {
                                int idInd = (int) sInd.get("idIndemnite");
                                boolean fractionnable = Boolean.TRUE.equals(sInd.get("fractionnable"));

                                if (savedDyn.containsKey(idInd)) {
                                    Map<String, Object> item = savedDyn.get(idInd);
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
                                    if (fractionnable) {
                                        item.put("matin", acc);
                                        item.put("am", acc);
                                    }
                                    dynMap.put(String.valueOf(idInd), item);
                                }
                            }
                        } else {
                            // 2. Calcul par défaut avec vérification des dérogations (exclusions) individuelles
                            Set<Integer> exclusionsIndemnites = new HashSet<>();
                            Map<Integer, String> derogationMotifs = new HashMap<>();
                            boolean excluDeplacement = false;
                            String motifExcluDeplacement = null;
                            boolean excluRepas = false;
                            String motifExcluRepas = null;

                            for (Map<String, Object> derogation : derogationsParEmploye.getOrDefault(idEmp, List.of())) {
                                        boolean beneficie = Boolean.TRUE.equals(derogation.get("beneficie"));
                                        int idIndDerog = (Integer) derogation.get("id_indemnite");
                                        String codeInd = (String) derogation.get("code");
                                        String motif = (String) derogation.get("motif");
                                        if (!beneficie) {
                                            exclusionsIndemnites.add(idIndDerog);
                                            derogationMotifs.put(idIndDerog, motif);
                                            if ("DPLCT".equalsIgnoreCase(codeInd) || "TRANSPORT".equalsIgnoreCase(codeInd) || "DEPLACEMENT".equalsIgnoreCase(codeInd)) {
                                                excluDeplacement = true;
                                                motifExcluDeplacement = motif;
                                            } else if ("REPAS".equalsIgnoreCase(codeInd)) {
                                                excluRepas = true;
                                                motifExcluRepas = motif;
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
                                // Le montant affiché reste celui du site, même si la case est cochée.
                                item.put("montantApplique", useGlobal ? mntGlobal : mntJour);
                                item.put("fractionJour", acc ? 1.0 : 0.0);
                                if (estExclu) {
                                    item.put("motifSuggestion", derogationMotifs.get(idInd));
                                }
                                if (Boolean.TRUE.equals(sInd.get("fractionnable"))) {
                                    item.put("matin", acc);
                                    item.put("am", acc);
                                }
                                dynMap.put(String.valueOf(idInd), item);
                            }
                        }
                        dto.setDynamicIndemnites(dynMap);
                        dto.setStatusActions(resolveStatusActions(dto));

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

        if (pointages.stream().anyMatch(day -> day.getDynamicIndemnites() == null)) {
            throw new IllegalArgumentException(
                    "Le champ dynamicIndemnites est obligatoire, même vide : les anciennes cases Repas/Déplacement ne sont plus une source de vérité.");
        }

        Map<String, ParametreGlobal.ParametreEtat> etatMap = ParametreGlobal.loadEtatMap(c);
        LocalTime debutNuit = resolveOptionalTimeRule(etatMap, "SEUIL_NUIT_DEBUT");
        LocalTime finNuit = resolveOptionalTimeRule(etatMap, "SEUIL_NUIT_FIN");

        String sqlFindPer = "SELECT id_periode_paie, date_debut, date_fin FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
        String sqlPerById = "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?";
        String sqlCheckPer = "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ? FOR SHARE";
        String sqlCheckRow = "SELECT id_pointage, COALESCE(version, 1) AS version FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?";

        String sqlUpdate = "UPDATE pointage_jour SET " +
                "heure_entree_matin = ?, heure_sortie_matin = ?, heure_entree_am = ?, heure_sortie_am = ?, " +
                "heures_matin = ?, heures_am = ?, total_heures = ?, minutes_retard = ?, heures_nuit = ?, " +
                "motif_ajustement = ?, id_statut_jour = (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), " +
                "montant_total_indemnites = ?, horaire_continu_jour = ?, heure_entree_matin_effective = ?, " +
                "ajuste_par = NULL, date_ajustement = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_pointage = ? AND version = ? RETURNING id_pointage, version";

        String sqlInsert = "INSERT INTO pointage_jour (" +
                "id_employe, date_pointage, id_periode_paie, id_site, " +
                "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                "heures_matin, heures_am, total_heures, minutes_retard, heures_nuit, " +
                "motif_ajustement, id_statut_jour, montant_total_indemnites, horaire_continu_jour, heure_entree_matin_effective, version, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                "(SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), " +
                "?, ?, ?, 1, CURRENT_TIMESTAMP) RETURNING id_pointage, version";

        String sqlDynInd = "INSERT INTO pointage_indemnite_jour (" +
                "id_pointage, id_indemnite, fraction, quantite, montant_calcule, accorde, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id_pointage, id_indemnite, fraction) DO UPDATE SET " +
                "quantite = EXCLUDED.quantite, " +
                "montant_calcule = EXCLUDED.montant_calcule, " +
                "accorde = EXCLUDED.accorde, " +
                "updated_at = CURRENT_TIMESTAMP";

        // Chargement des horaires et planchers d'arrivée configurés par site
        Map<Integer, LocalTime[]> siteHoraires = new HashMap<>();
        Map<Integer, Boolean> siteHoraireContinuMap = new HashMap<>();
        String sqlSites = "SELECT s.id_site, s.heure_debut_matin, s.heure_fin_matin, s.heure_debut_apres_midi, s.heure_fin_apres_midi, " +
                "s.horaire_continu, s.plancher_arrivee_actif, s.heure_entree_minimale_valorisee " +
                "FROM ref_site s";
        try (PreparedStatement psSites = c.prepareStatement(sqlSites);
             ResultSet rsSites = psSites.executeQuery()) {
            while (rsSites.next()) {
                int sId = rsSites.getInt("id_site");
                Time hdm = rsSites.getTime("heure_debut_matin");
                Time hfm = rsSites.getTime("heure_fin_matin");
                Time hda = rsSites.getTime("heure_debut_apres_midi");
                Time hfa = rsSites.getTime("heure_fin_apres_midi");
                boolean plancherActif = rsSites.getBoolean("plancher_arrivee_actif");
                Time hp = plancherActif ? rsSites.getTime("heure_entree_minimale_valorisee") : null;
                boolean cont = rsSites.getBoolean("horaire_continu");
                siteHoraireContinuMap.put(sId, rsSites.wasNull() ? null : cont);

                LocalTime ltDebM = hdm != null ? hdm.toLocalTime() : null;
                LocalTime ltFinM = hfm != null ? hfm.toLocalTime() : null;
                LocalTime ltDebA = hda != null ? hda.toLocalTime() : null;
                LocalTime ltFinA = hfa != null ? hfa.toLocalTime() : null;
                LocalTime ltPlafond = hp != null ? hp.toLocalTime() : null;

                // Index: 0=debM, 1=finM, 2=debA, 3=finA, 4=plafondArrivee
                siteHoraires.put(sId, new LocalTime[]{ltDebM, ltFinM, ltDebA, ltFinA, ltPlafond});
            }
        }

        Map<Integer, LocalDate[]> periodeBoundsCache = new HashMap<>();

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
                            if ("VALIDEE".equalsIgnoreCase(stCode) || "PAIE_VALIDEE".equalsIgnoreCase(stCode) || "CLOTUREE".equalsIgnoreCase(stCode) || "A_VALIDER".equalsIgnoreCase(stCode) || "TRANSMIS_DAF".equalsIgnoreCase(stCode)) {
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

                LocalTime[] sH = siteHoraires.get(idSiteRow);
                Boolean siteContinuConfig = siteHoraireContinuMap.get(idSiteRow);
                if (siteContinuConfig == null) {
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                            "Type d'horaire non configuré pour le site " + idSiteRow + ".");
                }
                boolean siteEstContinu = siteContinuConfig;
                boolean horairesComplets = sH != null && sH[0] != null && sH[3] != null
                        && (siteEstContinu || (sH[1] != null && sH[2] != null));
                if (!horairesComplets) {
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                            "Horaires de travail incomplets pour le site " + idSiteRow
                                    + " : configurez les horaires avant d'enregistrer le pointage.");
                }
                LocalTime siteDebM = sH[0];
                LocalTime siteFinM = sH[1];
                LocalTime siteDebA = sH[2];
                LocalTime siteFinA = sH[3];

                normalizeDayIntent(dto, siteDebM, siteFinM, siteDebA, siteFinA);
                // Une nouvelle sauvegarde de la journée rouvre l'examen d'un retard
                // confirmé sur une version antérieure du pointage.
                if ("Retard réel confirmé".equalsIgnoreCase(dto.getMotifAjustement())) {
                    dto.setMotifAjustement(null);
                }

                Time em = parseTime(dto.getHeureEntreeMatin());
                Time sm = parseTime(dto.getHeureSortieMatin());
                Time ea = parseTime(dto.getHeureEntreeAm());
                Time sa = parseTime(dto.getHeureSortieAm());

                LocalTime ltPlafondSite = sH.length > 4 ? sH[4] : null;
                LocalTime ltEmEffectif = effectiveMorningEntry(toLocalTime(em), ltPlafondSite);
                if (ltEmEffectif != null && ((dto.isHoraireContinu() && sa != null && ltEmEffectif.isAfter(sa.toLocalTime()))
                        || (!dto.isHoraireContinu() && sm != null && ltEmEffectif.isAfter(sm.toLocalTime())))) {
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                            "La sortie précède l'heure minimale comptabilisée du site.");
                }
                Time emEffectif = ltEmEffectif == null ? null : Time.valueOf(ltEmEffectif);

                boolean isJourneeContinue = dto.isHoraireContinu();
                LocalDate datePtg = null;
                try {
                    if (dto.getDatePointage() != null) datePtg = LocalDate.parse(dto.getDatePointage());
                } catch (Exception ignored) {}
                boolean isWeekendPtg = (datePtg != null && CalendrierOuvrableEngine.isWeekend(datePtg));
                AttendanceEngine.Context.LeavePart leavePart = parseLeavePart(dto.getTypeCongeJour());
                AttendanceEngine.Result dailyResult = dailyAttendanceCalculator.calculateDay(new AttendanceEngine.Context(
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

                String codeStatut = resolveDayStatus(dto, dailyResult, datePtg);
                if ("NON_SAISI".equals(codeStatut)) {
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                            "Aucun horaire ou statut d'absence n'a été saisi pour l'employé ID " + dto.getIdEmploye() + ".");
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
                    psUp.setBigDecimal(12, BigDecimal.ZERO);
                    psUp.setBoolean(13, dto.isHoraireContinu());
                    psUp.setTime(14, emEffectif);
                    psUp.setInt(15, existingIdPointage);
                    psUp.setInt(16, dto.getVersion());

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
                    psIns.setBigDecimal(16, BigDecimal.ZERO);
                    psIns.setBoolean(17, dto.isHoraireContinu());
                    psIns.setTime(18, emEffectif);

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

                // Le choix vient du pointage ; les montants viennent exclusivement de la configuration datée.
                try (PreparedStatement reset = c.prepareStatement(
                        "UPDATE pointage_indemnite_jour SET accorde = FALSE, quantite = 0, montant_calcule = 0, "
                                + "updated_at = CURRENT_TIMESTAMP WHERE id_pointage = ?")) {
                    reset.setInt(1, idPtg);
                    reset.executeUpdate();
                }
                int workingDays = calendrierEngine.compterJoursOuvrables(perDebut, perFin, idSiteRow, c);
                persistDynamicAllowances(dto.getDynamicIndemnites(), idPtg, idSiteRow, dtPtg,
                        workingDays, estNonTravaille, toLocalTime(sa), c, psDyn);

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

    /** Payload SQL du chemin de régularisation, gardé dans le propriétaire des writes journaliers. */
    public record RegularizedDayWrite(
            int pointageId, int employeeId, Integer periodId, int siteId, Date date,
            Time morningEntry, Time morningExit, Time afternoonEntry, Time afternoonExit,
            double morningHours, double afternoonHours, double totalHours, int lateMinutes,
            double nightHours, String adjustmentReason, String statusCode,
            boolean continuousSchedule, int expectedVersion,
            Time effectiveMorningEntry, Map<String, Object> dynamicAllowances, Integer validatedLeaveId
    ) {}

    /**
     * Persiste une journée corrigée et ses indemnités. La transaction et le recalcul hebdomadaire
     * restent possédés par l'appelant pour préserver l'atomicité historique.
     */
    public int saveRegularizedDay(RegularizedDayWrite day, Connection c) throws SQLException {
        if (day.pointageId() <= 0) {
            String sql = "INSERT INTO pointage_jour (" +
                    "id_employe, id_periode_paie, id_site, date_pointage, " +
                    "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                    "heures_matin, heures_am, total_heures, minutes_retard, heures_nuit, " +
                    "motif_ajustement, id_statut_jour, montant_total_indemnites, horaire_continu_jour, heure_entree_matin_effective, id_demande_conge, version, created_at, updated_at " +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                    "(SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), " +
                    "?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id_pointage";
            try (PreparedStatement statement = c.prepareStatement(sql)) {
                statement.setInt(1, day.employeeId());
                statement.setObject(2, day.periodId());
                statement.setInt(3, day.siteId());
                statement.setDate(4, day.date());
                statement.setTime(5, day.morningEntry());
                statement.setTime(6, day.morningExit());
                statement.setTime(7, day.afternoonEntry());
                statement.setTime(8, day.afternoonExit());
                statement.setDouble(9, day.morningHours());
                statement.setDouble(10, day.afternoonHours());
                statement.setDouble(11, day.totalHours());
                statement.setInt(12, day.lateMinutes());
                statement.setDouble(13, day.nightHours());
                statement.setString(14, day.adjustmentReason());
                statement.setString(15, day.statusCode());
                statement.setBigDecimal(16, BigDecimal.ZERO);
                statement.setBoolean(17, day.continuousSchedule());
                statement.setTime(18, day.effectiveMorningEntry());
                statement.setObject(19, day.validatedLeaveId());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("Échec de l'insertion du pointage régularisé.");
                    int id = result.getInt(1);
                    syncRegularizedAllowances(day, id, c);
                    return id;
                }
            } catch (SQLException exception) {
                if (exception.getMessage() != null
                        && (exception.getMessage().toLowerCase(Locale.ROOT).contains("duplicate")
                        || exception.getMessage().toLowerCase(Locale.ROOT).contains("unique"))) {
                    throw new IllegalStateException("Conflit : le pointage pour cet employé à cette date existe déjà.");
                }
                throw exception;
            }
        }

        try (PreparedStatement linked = c.prepareStatement(
                "SELECT id_demande_conge FROM pointage_jour WHERE id_pointage = ? FOR UPDATE")) {
            linked.setInt(1, day.pointageId());
            try (ResultSet current = linked.executeQuery()) {
                if (current.next() && current.getObject(1) != null && day.validatedLeaveId() == null) {
                    throw new IllegalStateException(
                            "Cette journée est liée à un congé validé. Modifier d'abord ce congé dans le domaine Congés.");
                }
            }
        }

        String sql = "UPDATE pointage_jour SET " +
                "heure_entree_matin = ?, heure_sortie_matin = ?, heure_entree_am = ?, heure_sortie_am = ?, " +
                "heures_matin = ?, heures_am = ?, total_heures = ?, minutes_retard = ?, heures_nuit = ?, " +
                "motif_ajustement = ?, id_statut_jour = (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), " +
                "montant_total_indemnites = ?, horaire_continu_jour = ?, heure_entree_matin_effective = ?, id_demande_conge = ?, " +
                "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id_pointage = ? AND version = ?";
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setTime(1, day.morningEntry());
            statement.setTime(2, day.morningExit());
            statement.setTime(3, day.afternoonEntry());
            statement.setTime(4, day.afternoonExit());
            statement.setDouble(5, day.morningHours());
            statement.setDouble(6, day.afternoonHours());
            statement.setDouble(7, day.totalHours());
            statement.setInt(8, day.lateMinutes());
            statement.setDouble(9, day.nightHours());
            statement.setString(10, day.adjustmentReason());
            statement.setString(11, day.statusCode());
            statement.setBigDecimal(12, BigDecimal.ZERO);
            statement.setBoolean(13, day.continuousSchedule());
            statement.setTime(14, day.effectiveMorningEntry());
            statement.setObject(15, day.validatedLeaveId());
            statement.setInt(16, day.pointageId());
            statement.setInt(17, day.expectedVersion());
            if (statement.executeUpdate() == 0) {
                throw new IllegalStateException("Conflit de modification concurrente sur le pointage ID "
                        + day.pointageId() + ". La ligne a été modifiée par un tiers.");
            }
        }
        syncRegularizedAllowances(day, day.pointageId(), c);
        return day.pointageId();
    }

    private void syncRegularizedAllowances(RegularizedDayWrite day, int pointageId, Connection c) throws SQLException {
        if (day.dynamicAllowances() == null) {
            throw new IllegalArgumentException("La sélection dynamique des indemnités est obligatoire.");
        }
        try (PreparedStatement clear = c.prepareStatement(
                "UPDATE pointage_indemnite_jour SET accorde = FALSE, quantite = 0, montant_calcule = 0, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE id_pointage = ?")) {
            clear.setInt(1, pointageId);
            clear.executeUpdate();
        }
        int workingDays = 0;
        if (day.periodId() != null) {
            try (PreparedStatement period = c.prepareStatement(
                    "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?")) {
                period.setInt(1, day.periodId());
                try (ResultSet bounds = period.executeQuery()) {
                    if (bounds.next()) {
                        workingDays = calendrierEngine.compterJoursOuvrables(
                                bounds.getDate(1).toLocalDate(), bounds.getDate(2).toLocalDate(), day.siteId(), c);
                    }
                }
            }
        }
        if (workingDays <= 0) {
            LocalDate date = day.date().toLocalDate();
            workingDays = calendrierEngine.compterJoursOuvrables(
                    date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()), day.siteId(), c);
        }
        String upsert = "INSERT INTO pointage_indemnite_jour "
                + "(id_pointage, id_indemnite, fraction, quantite, montant_calcule, accorde, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id_pointage, id_indemnite, fraction) DO UPDATE SET "
                + "quantite = EXCLUDED.quantite, montant_calcule = EXCLUDED.montant_calcule, "
                + "accorde = EXCLUDED.accorde, updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = c.prepareStatement(upsert)) {
            persistDynamicAllowances(day.dynamicAllowances(), pointageId, day.siteId(), day.date(),
                    workingDays, isNonWorkedStatus(day.statusCode()), toLocalTime(day.afternoonExit()), c, statement);
        }
        updateRegularizedAllowanceTotal(pointageId, c);
    }
    private static boolean isNonWorkedStatus(String status) {
        return "CONGE".equals(status) || "ABSENCE_NON_JUSTIFIEE".equals(status)
                || "ABSENCE_JUSTIFIEE".equals(status) || "FERIE_CHOME".equals(status)
                || "REPOS".equals(status);
    }

    private void updateRegularizedAllowanceTotal(int pointageId, Connection c) throws SQLException {
        try (PreparedStatement update = c.prepareStatement(
                "UPDATE pointage_jour SET montant_total_indemnites = COALESCE((SELECT SUM(montant_calcule) "
                        + "FROM pointage_indemnite_jour WHERE id_pointage = ? AND accorde = true), 0) WHERE id_pointage = ?")) {
            update.setInt(1, pointageId);
            update.setInt(2, pointageId);
            update.executeUpdate();
        }
    }

    private void persistDynamicAllowances(Map<String, Object> selections, int pointageId, int siteId,
            Date date, int workingDays, boolean nonWorked, LocalTime afternoonExit,
            Connection c, PreparedStatement statement) throws SQLException {
        boolean repasFourniSansVersement = false;
        for (Map.Entry<String, Object> entry : selections.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> values)) continue;
            DailyAllowanceTariff tariff = loadDailyAllowanceTariff(c, siteId, Integer.parseInt(entry.getKey()), date);
            if ("REPAS".equalsIgnoreCase(tariff.code()) && tariff.siteAmount().signum() == 0
                    && !nonWorked && Boolean.TRUE.equals(values.get("accordee"))) {
                repasFourniSansVersement = true;
            }
        }
        BigDecimal reductionDeplacement = repasFourniSansVersement
                ? requireMonetaryStateParameter(ParametreGlobal.loadEtatMapAtDate(date, c),
                        "REDUCTION_DEPLACT_REPAS_MIDI")
                : BigDecimal.ZERO;
        for (Map.Entry<String, Object> entry : selections.entrySet()) {
            int allowanceId = Integer.parseInt(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> values)) continue;
            DailyAllowanceTariff tariff = loadDailyAllowanceTariff(c, siteId, allowanceId, date);
            boolean morning = Boolean.TRUE.equals(values.get("matin"));
            boolean afternoon = Boolean.TRUE.equals(values.get("am"));
            boolean explicitlyGranted = tariff.fractionable()
                    ? (morning || afternoon)
                    : Boolean.TRUE.equals(values.get("accordee"));
            // Sur un jour non-travaille (conge, absence...) : autoriser uniquement si explicitement accorde (exception manuelle).
            boolean requested = explicitlyGranted && (!nonWorked || explicitlyGranted);
            BigDecimal units = tariff.fractionable()
                    ? (morning ? new BigDecimal("0.5") : BigDecimal.ZERO)
                            .add(afternoon ? new BigDecimal("0.5") : BigDecimal.ZERO)
                    : (requested ? BigDecimal.ONE : BigDecimal.ZERO);
            BigDecimal unitAmount = requested
                    ? dailyAllowanceUnitAmount(tariff, date, workingDays, c) : BigDecimal.ZERO;
            boolean repasSansVersement = "REPAS".equalsIgnoreCase(tariff.code())
                    && tariff.siteAmount().signum() == 0;
            if (requested && unitAmount.signum() == 0 && !repasSansVersement
                    && tariff.siteAmount().signum() == 0) {
                throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                        "Le tarif de l'indemnité " + tariff.code()
                                + " est nul : aucune indemnité monétaire ne peut être accordée.");
            }
            boolean granted = requested && (unitAmount.signum() > 0 || repasSansVersement);
            BigDecimal quantity = granted ? units : BigDecimal.ZERO;
            BigDecimal amount = granted ? MoneyRoundingPolicy.amount(unitAmount.multiply(units)) : BigDecimal.ZERO;
            if (granted && repasFourniSansVersement
                    && ("DPLCT".equalsIgnoreCase(tariff.code())
                            || "DEPLACEMENT".equalsIgnoreCase(tariff.code())
                            || "TRANSPORT".equalsIgnoreCase(tariff.code()))) {
                amount = amount.subtract(MoneyRoundingPolicy.amount(reductionDeplacement.multiply(units)))
                        .max(BigDecimal.ZERO);
            }
            String fraction = tariff.fractionable() && granted && units.compareTo(BigDecimal.ONE) < 0
                    ? (morning ? "MATIN" : "APRES_MIDI") : "JOUR_ENTIER";
            statement.setInt(1, pointageId);
            statement.setInt(2, allowanceId);
            statement.setString(3, fraction);
            statement.setBigDecimal(4, quantity);
            statement.setBigDecimal(5, amount);
            statement.setBoolean(6, granted);
            statement.executeUpdate();
        }
        persistSecondMeal(pointageId, date, nonWorked, afternoonExit, c, statement);
    }

    private void persistSecondMeal(int pointageId, Date date, boolean nonWorked, LocalTime afternoonExit,
            Connection c, PreparedStatement statement) throws SQLException {
        SecondMealAward award = null;
        if (!nonWorked && afternoonExit != null) {
            Map<String, ParametreGlobal.ParametreEtat> params = ParametreGlobal.loadEtatMapAtDate(date, c);
            MealTier first = resolveMealTier(params, "SEUIL_2ND_REPAS_1", "MONTANT_2ND_REPAS_1");
            MealTier second = resolveMealTier(params, "SEUIL_2ND_REPAS_2", "MONTANT_2ND_REPAS_2");
            award = resolveSecondMealAward(false, afternoonExit, first, second);
        }
        BigDecimal amount = award == null ? BigDecimal.ZERO : MoneyRoundingPolicy.amount(award.amount());
        try (PreparedStatement update = c.prepareStatement(
                "UPDATE pointage_jour SET code_second_repas = ?, montant_second_repas = ? WHERE id_pointage = ?")) {
            update.setString(1, award == null ? null : award.code());
            update.setBigDecimal(2, amount);
            update.setInt(3, pointageId);
            update.executeUpdate();
        }
        if (award == null) return;
        try (PreparedStatement lookup = c.prepareStatement(
                "SELECT id_indemnite FROM ref_indemnite WHERE code = 'REPAS' AND est_actif = TRUE")) {
            try (ResultSet result = lookup.executeQuery()) {
                if (!result.next()) {
                    throw new SirhBusinessException(SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                            "Indemnité REPAS absente du référentiel.");
                }
                int mealId = result.getInt(1);
                try (PreparedStatement add = c.prepareStatement(
                        "UPDATE pointage_indemnite_jour SET montant_calcule = montant_calcule + ?, "
                                + "accorde = TRUE, updated_at = CURRENT_TIMESTAMP "
                                + "WHERE id_pointage = ? AND id_indemnite = ? AND fraction = 'JOUR_ENTIER'")) {
                    add.setBigDecimal(1, amount);
                    add.setInt(2, pointageId);
                    add.setInt(3, mealId);
                    if (add.executeUpdate() > 0) return;
                }
                statement.setInt(1, pointageId);
                statement.setInt(2, mealId);
                statement.setString(3, "JOUR_ENTIER");
                statement.setBigDecimal(4, BigDecimal.ZERO);
                statement.setBigDecimal(5, amount);
                statement.setBoolean(6, true);
                statement.executeUpdate();
            }
        }
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
