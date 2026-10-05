package mg.galaxam.sirh.paie.domain.engine;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ManualRuleApplicabilityTest {
    @ParameterizedTest
    @CsvSource({"TOUS,false,false,false,true", "ASSUJETTI_IRSA,true,false,false,true",
        "ASSUJETTI_IRSA,false,true,true,false", "ASSUJETTI_CNAPS,false,true,false,true",
        "ASSUJETTI_CNAPS,true,false,true,false", "ASSUJETTI_SANTE,false,false,true,true",
        "ASSUJETTI_SANTE,true,true,false,false"})
    void conditionDependDuProfilEtNonDesDrapeauxRubrique(String condition, boolean irsa,
            boolean cnaps, boolean health, boolean expected) {
        assertEquals(expected,ManualPayrollRules.applies(condition,irsa,cnaps,health));
    }
    @Test void validiteCouvreTouteLaPeriodeEtInclutLesBornes() {
        var start=LocalDate.of(2026,7,26); var end=LocalDate.of(2026,8,25);
        assertTrue(ManualPayrollRules.covers(start,end,start,end));
        assertTrue(ManualPayrollRules.covers(null,null,start,end));
        assertFalse(ManualPayrollRules.covers(start.plusDays(1),end,start,end));
        assertFalse(ManualPayrollRules.covers(start,end.minusDays(1),start,end));
        assertThrows(IllegalArgumentException.class,()->ManualPayrollRules.applies("UNKNOWN",true,true,true));
    }
}
