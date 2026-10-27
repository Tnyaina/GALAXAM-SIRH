package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Contrat de présentation commun aux deux exports ; aucun calcul RH dans les renderers. */
public record PointageDocumentData(FichePointageEmployeDTO fiche, String edition, String provenance,
        Integer versionSnapshot, List<Indemnite> indemnites, List<Ajustement> ajustements,
        List<BigDecimal> bilan) {
    public record Indemnite(String code, String libelle, String periodicite, BigDecimal quantite, BigDecimal montant) {}
    public record Ajustement(Integer minutes, String motif, String date, String auteur, String statut) {}

    public static PointageDocumentData from(FichePointageEmployeDTO fiche) {
        var syn = fiche.getSoldePeriode();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>(fiche.getTotauxIndemnites());
        if (syn != null) amounts.putAll(syn.getMontantsIndemnites());
        List<PointageJourDTO> days = days(fiche);
        var codes = new TreeSet<>(amounts.keySet());
        codes.addAll(fiche.getIndemnitesDisponibles().keySet());
        Map<String, BigDecimal> quantities = new HashMap<>();
        Set<String> unknownQuantities = new HashSet<>();
        Set<String> journalCodes = new HashSet<>();
        for (var day : days) {
            for (var entry : day.getIndemnites().entrySet()) {
                String code = entry.getKey();
                var item = entry.getValue();
                codes.add(code);
                journalCodes.add(code);
                if (item == null) continue;
                if (item.accordee()) {
                    if (item.quantite() == null) unknownQuantities.add(code);
                    else quantities.merge(code, item.quantite(), BigDecimal::add);
                }
            }
        }
        List<Indemnite> allowances = new ArrayList<>();
        for (String code : codes) {
            var config = fiche.getIndemnitesDisponibles().get(code);
            String periodicity = config == null ? null : config.periodicite();
            // Un forfait absent du journal ne possède pas de quantité journalière.
            boolean unknown = unknownQuantities.contains(code) || (config == null && !journalCodes.contains(code));
            allowances.add(new Indemnite(code, config == null || config.libelle() == null ? code : config.libelle(),
                    periodicity, unknown ? null : quantities.getOrDefault(code, BigDecimal.ZERO), amounts.get(code)));
        }
        List<BigDecimal> balance = syn == null ? Arrays.asList(new BigDecimal[11]) : Arrays.asList(
                hours(syn.getMinutesDeficitHebdo()), hours(syn.getMinutesHs30Hebdo()), hours(syn.getMinutesHs50Hebdo()),
                hours(syn.getMinutesAjustementPeriode()), hours(syn.getMinutesSoldePeriode()), hours(syn.getMinutesAbsenceFinale()),
                hours(syn.getMinutesHs30Finales()), hours(syn.getMinutesHs50Finales()), BigDecimal.valueOf(syn.getHeuresNuitTotal()),
                BigDecimal.valueOf(syn.getHeuresDimancheTotal()), BigDecimal.valueOf(syn.getHeuresFerieTotal()));
        return new PointageDocumentData(fiche, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                "PROVISOIRE - données calculées par le SIRH", null, allowances, List.of(), balance);
    }
    public static List<PointageJourDTO> days(FichePointageEmployeDTO fiche) {
        return fiche.getPointagesData() == null ? List.of() : fiche.getPointagesData();
    }
    public List<Ajustement> appliedAdjustments() {
        return ajustements == null ? List.of() : ajustements.stream()
                .filter(a -> "VALIDE".equalsIgnoreCase(a.statut())).toList();
    }
    public static String dualHours(Number hours) {
        if (hours == null) return "-";
        return dualHours(new BigDecimal(hours.toString()));
    }
    public static String dualHours(BigDecimal hours) {
        if (hours == null) return "-";
        long minutes = hours.abs().multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValue();
        String sign = hours.signum() < 0 ? "−" : "";
        return sign + String.format(Locale.FRANCE, "%02dh%02d", minutes / 60, minutes % 60)
                + " (" + sign + hours.abs().setScale(2, RoundingMode.HALF_UP).toPlainString() + "h)";
    }
    public static String signedDualHours(BigDecimal hours) {
        if (hours == null) return "-";
        return (hours.signum() > 0 ? "+" : "") + dualHours(hours);
    }
    public static String signedDualMinutes(Integer minutes) {
        return minutes == null ? "-" : signedDualHours(hours(minutes));
    }
    public static BigDecimal hours(Integer minutes) { return minutes == null ? null : BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP); }
    public static String number(BigDecimal value) { return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ','); }
    public static String date(String value) {
        if (value == null || value.isBlank()) return "-";
        try { return LocalDate.parse(value.substring(0, 10)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }
        catch (RuntimeException ignored) { return value; }
    }
    /** Montant final présenté en ariary entiers ; les calculs et snapshots gardent leurs centimes. */
    public static BigDecimal ariary(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP);
    }
    public static String money(BigDecimal value) {
        if (value == null) return "-";
        return java.text.NumberFormat.getIntegerInstance(Locale.FRANCE).format(ariary(value)).replace('\u202f', ' ').replace('\u00a0', ' ') + " Ar";
    }
    public static String value(String value) { return value == null || value.isBlank() ? "-" : value; }
    public String note() {
        var summary = fiche.getSoldePeriode();
        String threshold = summary == null || summary.getMinutesPremierPalierHs() == null ? "non historisé"
                : number(hours(summary.getMinutesPremierPalierHs())) + " h";
        return provenance + " · Édité le " + edition + " · Palier HS30 : " + threshold
                + (versionSnapshot == null ? "" : ". Synthèse issue du snapshot ; journal vérifié par version. Les semaines sont reconstituées par le moteur backend.");
    }
}
