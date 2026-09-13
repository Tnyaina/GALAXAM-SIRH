package mg.galaxam.sirh.unit.pointage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import mg.galaxam.sirh.model.pointage.mapper.PointageTimeBoundaryMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PointageTimeBoundaryMapperTest {
    @ParameterizedTest
    @CsvSource({"1,00h01,0.02", "59,00h59,0.98", "60,01h00,1.00", "61,01h01,1.02",
            "457,07h37,7.62", "465,07h45,7.75", "531,08h51,8.85"})
    void conversionResteUneFrontiereDePresentation(long minutes, String label, double decimal) {
        assertEquals(label, PointageTimeBoundaryMapper.formatHoursMinutes(minutes));
        assertEquals(decimal, PointageTimeBoundaryMapper.toLegacyDecimalHours(minutes));
    }
}
