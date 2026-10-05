package mg.galaxam.sirh.common.json;

import java.time.LocalDate;
import mg.galaxam.sirh.conge.domain.DemandeCongeRequests.SimulerDemandeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class FlexibleLocalDateDeserializerTest {

    private final ObjectMapper objectMapper = new JsonMapper();

    @Test
    @DisplayName("Désérialise correctement les dates ISO YYYY-MM-DD standard")
    void testStandardIsoDate() throws Exception {
        String json = """
            {
                "idEmploye": 10,
                "idTypeConge": 1,
                "dateDebut": "2026-08-17",
                "dateFin": "2026-08-25"
            }
            """;

        SimulerDemandeRequest req = objectMapper.readValue(json, SimulerDemandeRequest.class);
        assertThat(req.dateDebut()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(req.dateFin()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    @DisplayName("Désérialise sans erreur le timestamp ISO avec offset (+03:00) rapporté en anomalie")
    void testIsoDateTimeWithOffset() throws Exception {
        String json = """
            {
                "idEmploye": 10,
                "idTypeConge": 1,
                "dateDebut": "2026-08-17T00:00:00.000+03:00",
                "dateFin": "2026-08-25T23:59:59.000+03:00"
            }
            """;

        SimulerDemandeRequest req = objectMapper.readValue(json, SimulerDemandeRequest.class);
        assertThat(req.dateDebut()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(req.dateFin()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    @DisplayName("Désérialise les dates UTC avec suffixe Z et LocalDateTime ISO")
    void testIsoUtcAndLocal() throws Exception {
        String json = """
            {
                "idEmploye": 10,
                "idTypeConge": 1,
                "dateDebut": "2026-08-17T00:00:00Z",
                "dateFin": "2026-08-25T12:30:00"
            }
            """;

        SimulerDemandeRequest req = objectMapper.readValue(json, SimulerDemandeRequest.class);
        assertThat(req.dateDebut()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(req.dateFin()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    @DisplayName("Gère les valeurs nulles et les chaînes vides")
    void testNullAndEmpty() throws Exception {
        String json = """
            {
                "idEmploye": 10,
                "idTypeConge": 1,
                "dateDebut": null,
                "dateFin": ""
            }
            """;

        SimulerDemandeRequest req = objectMapper.readValue(json, SimulerDemandeRequest.class);
        assertThat(req.dateDebut()).isNull();
        assertThat(req.dateFin()).isNull();
    }
}
