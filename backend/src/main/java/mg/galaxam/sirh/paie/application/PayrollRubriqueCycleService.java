package mg.galaxam.sirh.paie.application;

import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;

/** Cycle coverage preview for manual rubrique validity dates. */
@Service
public class PayrollRubriqueCycleService {
    private final PayrollPeriodCalendarService calendar;

    public PayrollRubriqueCycleService(PayrollPeriodCalendarService calendar) { this.calendar = calendar; }

    public record Cycle(String code, LocalDate start, LocalDate end) {}
    public record Preview(Cycle first, Cycle last, boolean startCutsCycle, boolean endCutsCycle,
                         boolean noCompleteCycle) {}

    public Preview preview(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("Dates de début et de fin cohérentes requises.");
        Cycle containingStart = containing(start);
        Cycle containingEnd = containing(end);
        Cycle first = start.equals(containingStart.start()) ? containingStart
                : containing(containingStart.end().plusDays(1));
        Cycle last = end.equals(containingEnd.end()) || end.isAfter(containingEnd.end()) ? containingEnd
                : containing(containingEnd.start().minusDays(1));
        boolean none = first.start().isAfter(last.start());
        return new Preview(none ? null : first, none ? null : last,
                !start.equals(containingStart.start()), !end.equals(containingEnd.end()), none);
    }

    private Cycle containing(LocalDate date) {
        YearMonth target = YearMonth.from(date);
        if (date.getDayOfMonth() >= 26) target = target.plusMonths(1);
        var period = calendar.forCode(target.toString(), false);
        return new Cycle(period.code(), period.dateDebut(), period.dateFin());
    }
}
