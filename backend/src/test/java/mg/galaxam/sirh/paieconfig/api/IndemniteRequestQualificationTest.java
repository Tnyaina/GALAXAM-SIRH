package mg.galaxam.sirh.paieconfig.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import mg.galaxam.sirh.paieconfig.api.dto.IndemniteRequest;
import org.junit.jupiter.api.Test;

class IndemniteRequestQualificationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void absenceDeQualificationIrsaResteDistincteDeNonImposable() throws Exception {
        var incomplet = mapper.readValue("{\"code\":\"TEST\"}", IndemniteRequest.class);
        var exonere = mapper.readValue("{\"code\":\"TEST\",\"imposableIrsa\":false}", IndemniteRequest.class);
        assertNull(incomplet.getImposableIrsa());
        assertEquals(Boolean.FALSE, exonere.getImposableIrsa());
    }
}
