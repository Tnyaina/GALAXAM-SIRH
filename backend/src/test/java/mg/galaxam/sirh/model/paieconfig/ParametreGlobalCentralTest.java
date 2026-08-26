package mg.galaxam.sirh.model.paieconfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParametreGlobalCentralTest {

    @Test
    @DisplayName("Vérification des accesseurs typés de ParametreGlobal")
    void testTypedAccessors() {
        Map<String, String> map = new HashMap<>();
        map.put("SEUIL_HEURES_JOUR", "8.0");
        map.put("SEUIL_NUIT_DEBUT", "22:00");
        map.put("PLAFOND_HEURES_SUPP_SEMAINE", "20");
        map.put("MONTANT_REPAS_MIDI", "2000.00");
        map.put("REDUCTION_DEPLACT_REPAS_MIDI", "1000");

        // Double
        assertEquals(8.0, ParametreGlobal.getDouble(map, "SEUIL_HEURES_JOUR", 0.0), 0.001);
        assertEquals(2000.0, ParametreGlobal.getDouble(map, "MONTANT_REPAS_MIDI", 0.0), 0.001);
        assertEquals(1000.0, ParametreGlobal.getDouble(map, "REDUCTION_DEPLACT_REPAS_MIDI", 0.0), 0.001);
        assertEquals(50.0, ParametreGlobal.getDouble(map, "INEXISTANT", 50.0), 0.001);

        // Int
        assertEquals(20, ParametreGlobal.getInt(map, "PLAFOND_HEURES_SUPP_SEMAINE", 0));
        assertEquals(10, ParametreGlobal.getInt(map, "INEXISTANT", 10));

        // String
        assertEquals("22:00", ParametreGlobal.getString(map, "SEUIL_NUIT_DEBUT", ""));
        assertEquals("DEFAULT_VAL", ParametreGlobal.getString(map, "INEXISTANT", "DEFAULT_VAL"));

        // LocalTime
        assertEquals(LocalTime.of(22, 0), ParametreGlobal.getLocalTime(map, "SEUIL_NUIT_DEBUT", LocalTime.of(0, 0)));
        assertEquals(LocalTime.of(5, 0), ParametreGlobal.getLocalTime(map, "SEUIL_NUIT_FIN", LocalTime.of(5, 0)));
    }

    @Test
    @DisplayName("Tolérance aux entrées mal formées ou nulles")
    void testInvalidInputs() {
        Map<String, String> map = new HashMap<>();
        map.put("BAD_DOUBLE", "abc");
        map.put("BAD_TIME", "99:99");

        assertEquals(8.0, ParametreGlobal.getDouble(map, "BAD_DOUBLE", 8.0), 0.001);
        assertEquals(LocalTime.of(8, 0), ParametreGlobal.getLocalTime(map, "BAD_TIME", LocalTime.of(8, 0)));
        assertEquals(10.0, ParametreGlobal.getDouble(null, "ANY", 10.0), 0.001);
    }
}
