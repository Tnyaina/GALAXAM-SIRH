package mg.galaxam.sirh.paie.application;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PayrollLegalDateResolutionTest {
    PaieConfigLegale config(int id,String start,String end) {
        var config = new PaieConfigLegale(); config.setIdConfigLegale(id);
        config.setDateDebut(Date.valueOf(start)); config.setDateFin(end==null?null:Date.valueOf(end)); return config;
    }
    @Test void chaqueJourResoutSonMillesime() {
        var configs=List.of(config(1,"2025-01-01","2025-12-31"),config(2,"2026-01-01",null));
        assertEquals(1,PayrollLegalContextResolver.resolveConfigIdAtDate(configs,LocalDate.of(2025,12,31)));
        assertEquals(2,PayrollLegalContextResolver.resolveConfigIdAtDate(configs,LocalDate.of(2026,1,1)));
    }
    @Test void trouNeReprendPasLaConfigurationDeCloture() {
        var configs=List.of(config(2,"2026-01-01",null));
        var error=assertThrows(IllegalStateException.class,()->PayrollLegalContextResolver.resolveConfigIdAtDate(configs,LocalDate.of(2025,12,31)));
        assertTrue(error.getMessage().contains("2025-12-31")); assertTrue(error.getMessage().contains("versions="));
    }
    @Test void chevauchementNeChoisitPasLaPremiereVersion() {
        var configs=List.of(config(1,"2025-01-01",null),config(2,"2026-01-01",null));
        assertTrue(assertThrows(IllegalStateException.class,()->PayrollLegalContextResolver.resolveConfigIdAtDate(configs,LocalDate.of(2026,1,1)))
                .getMessage().contains("AMBIGUOUS_CONFIGURATION"));
    }
}
