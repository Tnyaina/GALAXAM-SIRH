package mg.galaxam.sirh.paie.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Source unique de la date métier courante pour les traitements de paie. */
public final class PayrollBusinessTime {
    public static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private static final Clock CLOCK = Clock.system(ZONE);

    private PayrollBusinessTime() {}

    public static LocalDate today() { return LocalDate.now(CLOCK); }
    public static Clock clock() { return CLOCK; }
}
