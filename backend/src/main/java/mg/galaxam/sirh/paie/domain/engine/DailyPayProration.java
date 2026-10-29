package mg.galaxam.sirh.paie.domain.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Journées payables tirées exclusivement des minutes du pointage gelé. */
public final class DailyPayProration {
    private DailyPayProration() { }

    public record Day(long workedMinutes, long overtimeMinutes, long expectedMinutes) { }

    public static BigDecimal payableDays(List<Day> days) {
        BigDecimal total = BigDecimal.ZERO;
        for (Day day : days) {
            if (day.expectedMinutes() <= 0 || day.workedMinutes() < 0
                    || day.overtimeMinutes() < 0 || day.overtimeMinutes() > day.workedMinutes())
                throw new IllegalStateException("PAYROLL_DAILY_WORK_INVALID: minutes du pointage incohérentes");
            long regularMinutes = day.workedMinutes() - day.overtimeMinutes();
            total = total.add(BigDecimal.valueOf(Math.min(regularMinutes, day.expectedMinutes()))
                    .divide(BigDecimal.valueOf(day.expectedMinutes()), 12, RoundingMode.HALF_UP));
        }
        return total;
    }
}
