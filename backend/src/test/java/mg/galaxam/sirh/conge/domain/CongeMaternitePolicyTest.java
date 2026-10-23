package mg.galaxam.sirh.conge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CongeMaternitePolicyTest {
    private final CongeCalculateurEngine engine = new CongeCalculateurEngine();

    @Test
    void lePlafondStandardResteQuatorzeSemaines() {
        assertEquals(98, engine.plafondMaternite(false, 98, 126));
    }

    @Test
    void lePlafondAvecExtensionInclutLesQuatreSemainesSupplementaires() {
        assertEquals(126, engine.plafondMaternite(true, 98, 126));
    }

    @Test
    void refuseUnParametrageOuLePlafondEtenduEstInferieurAuStandard() {
        assertThrows(IllegalArgumentException.class, () -> engine.plafondMaternite(true, 98, 97));
    }
}
