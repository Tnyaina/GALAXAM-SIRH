package mg.galaxam.sirh.pointage.domain.calculation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointageAllowancePolicyTest {
    @Test
    void restDayDoesNotSuggestAllowancesButWorkedWeekendCan() {
        assertFalse(PointageAllowancePolicy.defaultGranted(
                PointageAllowancePolicy.eligibleForDefault("REPOS", false, null), true, false));
        assertTrue(PointageAllowancePolicy.defaultGranted(
                PointageAllowancePolicy.eligibleForDefault("PRESENT", false, null), true, false));
    }

    @Test
    void holidayLeaveAndIndividualDerogationRemainExcluded() {
        assertFalse(PointageAllowancePolicy.eligibleForDefault("FERIE_CHOME", true, null));
        assertFalse(PointageAllowancePolicy.eligibleForDefault("CONGE", false, "FULL_DAY"));
        assertFalse(PointageAllowancePolicy.defaultGranted(
                PointageAllowancePolicy.eligibleForDefault("DISPENSE_POINTAGE", false, null), true, true));
    }

    @Test
    void savedDecisionStillOverridesTheSuggestion() {
        assertTrue(PointageAllowancePolicy.resolve(true, false));
        assertFalse(PointageAllowancePolicy.resolve(false, true));
    }
}
