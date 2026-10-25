package mg.galaxam.sirh.conge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class RemunerationCongePolicyTest {

    @Test
    void nonAdminSansSurchargeConserveLeDefautDuType() {
        assertNull(RemunerationCongePolicy.resolve(null, null, false, false));
    }

    @Test
    void nonAdminNePeutImposerNiCongéPayeNiSansSolde() {
        assertThrows(AccessDeniedException.class,
                () -> RemunerationCongePolicy.resolve(null, true, false, false));
        assertThrows(AccessDeniedException.class,
                () -> RemunerationCongePolicy.resolve(null, false, false, false));
    }

    @Test
    void agentPointageSansSurchargeConserveLeDefautDuType() {
        assertNull(RemunerationCongePolicy.resolve(null, null, false, false));
    }

    @Test
    void administrateurPeutDefinirChaqueValeurExplicite() {
        assertEquals(Boolean.TRUE, RemunerationCongePolicy.resolve(null, true, false, true));
        assertEquals(Boolean.FALSE, RemunerationCongePolicy.resolve(null, false, false, true));
    }

    @Test
    void omissionPreserveLaValeurExistanteEtResetRetablitLeDefautDuType() {
        assertEquals(Boolean.FALSE, RemunerationCongePolicy.resolve(false, null, false, false));
        assertEquals(Boolean.TRUE, RemunerationCongePolicy.resolve(true, null, false, true));
        assertNull(RemunerationCongePolicy.resolve(false, null, true, true));
    }

    @Test
    void uneRequeteNePeutPasDefinirUneValeurEtDemanderLeDefautSimultanement() {
        assertThrows(IllegalArgumentException.class,
                () -> RemunerationCongePolicy.resolve(null, true, true, true));
    }
}
