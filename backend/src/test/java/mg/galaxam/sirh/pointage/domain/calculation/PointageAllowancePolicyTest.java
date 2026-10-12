package mg.galaxam.sirh.pointage.domain.calculation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointageAllowancePolicyTest {
    @Test
    void defaultsRequireEligibleDayAndNoIndividualExclusion() {
        assertTrue(PointageAllowancePolicy.defaultGranted(true, true, false));
        assertFalse(PointageAllowancePolicy.defaultGranted(false, true, false));
        assertFalse(PointageAllowancePolicy.defaultGranted(true, false, false));
        assertFalse(PointageAllowancePolicy.defaultGranted(true, true, true));
    }

    @Test
    void explicitDecisionAlwaysOverridesDefault() {
        assertTrue(PointageAllowancePolicy.resolve(null, true));
        assertFalse(PointageAllowancePolicy.resolve(null, false));
        assertFalse(PointageAllowancePolicy.resolve(false, true));
        assertTrue(PointageAllowancePolicy.resolve(true, false));
    }
}
