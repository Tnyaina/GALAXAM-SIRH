package mg.galaxam.sirh.paie.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Source des dates proposées pour les campagnes de paie. */
@Service
public class PayrollPeriodCalendarService {
    private static final ZoneId ZONE = mg.galaxam.sirh.paie.domain.PayrollBusinessTime.ZONE;
    private final Clock clock;

    public PayrollPeriodCalendarService() { this(mg.galaxam.sirh.paie.domain.PayrollBusinessTime.clock()); }
    PayrollPeriodCalendarService(Clock clock) { this.clock = clock; }

    public record Suggestion(String code, String libelle, LocalDate dateDebut, LocalDate dateFin,
                             String baseLibelle) {}

    public Suggestion next(LocalDate latestEnd, String baseLibelle) {
        YearMonth target = latestEnd == null ? targetFor(LocalDate.now(clock))
                : YearMonth.from(latestEnd).plusMonths(1);
        if (latestEnd != null && !target.minusMonths(1).atDay(26).isAfter(latestEnd))
            target = target.plusMonths(1);
        return cycle(target, baseLibelle);
    }

    public Suggestion forDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Date cible obligatoire.");
        return cycle(targetFor(date), null);
    }

    public Suggestion forCode(String code, boolean civil) {
        if (code == null || !code.matches("\\d{4}-(0[1-9]|1[0-2])"))
            throw new IllegalArgumentException("Code de période attendu au format AAAA-MM.");
        YearMonth target = YearMonth.parse(code);
        LocalDate start = civil ? target.atDay(1) : target.minusMonths(1).atDay(26);
        LocalDate end = civil ? target.atEndOfMonth() : target.atDay(25);
        return new Suggestion(code, label(target), start, end, null);
    }

    private static YearMonth targetFor(LocalDate date) {
        YearMonth month = YearMonth.from(date);
        return date.getDayOfMonth() >= 26 ? month.plusMonths(1) : month;
    }

    private static Suggestion cycle(YearMonth target, String baseLibelle) {
        return new Suggestion(target.toString(), label(target), target.minusMonths(1).atDay(26),
                target.atDay(25), baseLibelle);
    }

    private static String label(YearMonth target) {
        String month = target.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH);
        return Character.toUpperCase(month.charAt(0)) + month.substring(1) + " " + target.getYear();
    }
}
