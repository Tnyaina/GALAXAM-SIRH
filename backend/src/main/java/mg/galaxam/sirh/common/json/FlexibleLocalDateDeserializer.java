package mg.galaxam.sirh.common.json;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Désérialiseur Jackson 3 tolérant pour java.time.LocalDate.
 * Accepte les formats :
 * - YYYY-MM-DD (ex: "2026-08-17")
 * - ISO OffsetDateTime (ex: "2026-08-17T00:00:00.000+03:00", "2026-08-17T00:00:00Z")
 * - ISO LocalDateTime (ex: "2026-08-17T00:00:00")
 */
public class FlexibleLocalDateDeserializer extends ValueDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        String text = p.getString();
        if (text == null || text.isBlank()) {
            return null;
        }
        text = text.trim();

        // 1. Fast path: si commence par format YYYY-MM-DD
        if (text.length() >= 10 && text.charAt(4) == '-' && text.charAt(7) == '-') {
            try {
                return LocalDate.parse(text.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ignored) {
            }
        }

        // 2. Format ISO OffsetDateTime / ZonedDateTime
        try {
            return OffsetDateTime.parse(text).toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            return ZonedDateTime.parse(text).toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(text).toLocalDate();
        } catch (Exception ignored) {
        }

        // 3. Fallback direct
        return LocalDate.parse(text);
    }
}
