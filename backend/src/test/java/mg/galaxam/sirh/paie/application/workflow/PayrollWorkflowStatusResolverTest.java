package mg.galaxam.sirh.paie.application.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PayrollWorkflowStatusResolverTest {
    @Test
    void periodStatesKeepPointageAndPayrollSeparate() {
        assertStatuses("BROUILLON", 0, 0, 0, false, "A_OUVRIR", "EN_ATTENTE_POINTAGE");
        assertStatuses("EN_COURS", 0, 0, 0, false, "EN_SAISIE", "EN_ATTENTE_POINTAGE");
        assertStatuses("TRANSMIS_DAF", 0, 0, 0, false, "A_VALIDER", "EN_ATTENTE_POINTAGE");
        assertStatuses("A_VALIDER", 0, 0, 0, false, "A_VALIDER", "EN_ATTENTE_POINTAGE");
        assertStatuses("VALIDEE", 0, 0, 0, false, "FIGE", "A_PREPARER");
        assertStatuses("VALIDEE", 4, 4, 0, false, true, "FIGE", "A_CONTROLER");
        assertStatuses("VALIDEE", 1, 1, 0, false, false, "FIGE", "A_COMPLETER");
        assertStatuses("VALIDEE", 4, 4, 0, true, true, "FIGE", "A_RECALCULER");
        assertStatuses("VALIDEE", 4, 0, 4, false, true, "FIGE", "A_FINALISER");
        assertStatuses("PAIE_VALIDEE", 4, 0, 4, false, "FIGE", "VALIDEE");
        assertStatuses("PAIE_VALIDEE", 4, 0, 4, false, false, "FIGE", "A_COMPLETER");
        assertStatuses("PAIE_VALIDEE", 4, 0, 4, true, "FIGE", "A_RECALCULER");
        assertStatuses("CLOTUREE", 4, 0, 4, false, "FIGE", "CLOTUREE");
    }

    private void assertStatuses(String state, int total, int calculated, int validated,
            boolean recalculationRequired, String pointageCode, String paieCode) {
        assertStatuses(state, total, calculated, validated, recalculationRequired,
                true, pointageCode, paieCode);
    }

    private void assertStatuses(String state, int total, int calculated, int validated,
            boolean recalculationRequired, boolean populationComplete,
            String pointageCode, String paieCode) {
        var statuses = PayrollWorkflowStatusResolver.resolve(
                state, total, calculated, validated, recalculationRequired, populationComplete);
        assertEquals(pointageCode, statuses.pointage().code());
        assertEquals(paieCode, statuses.paie().code());
    }
}
