package mg.galaxam.sirh.pointage.application;

import org.junit.jupiter.api.Test;

import java.sql.Time;

import static org.junit.jupiter.api.Assertions.*;

class PointageTimeValidationTest {
    @Test
    void rejectsMalformedAndOutOfRangeTimes() {
        for (String value : new String[]{"99:99", "24:00", "12:60", "04:39:99", "4:39", "abc"}) {
            assertThrows(IllegalArgumentException.class, () -> PointageService.parseTime(value), value);
        }
    }

    @Test
    void keepsSupportedValidAndEmptyValues() {
        assertEquals(Time.valueOf("23:59:00"), PointageService.parseTime("23:59"));
        assertEquals(Time.valueOf("08:00:30"), PointageService.parseTime("08:00:30"));
        assertNull(PointageService.parseTime("--:--"));
    }
}
