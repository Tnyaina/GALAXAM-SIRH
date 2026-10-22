package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;

class PayrollLegalContextResolverTest {

    @Test
    void selectsConfigurationByThePayrollPeriodDateAcrossAnEffectiveDateChange() {
        PaieConfigLegale disabled = config(10, "2026-01-01", "2026-01-31");
        disabled.setExonerationHsIrsaActive(false);
        PaieConfigLegale enabled = config(11, "2026-02-01", null);
        enabled.setExonerationHsIrsaActive(true);

        assertEquals(10, PayrollLegalContextResolver.resolveConfigIdAtDate(
                List.of(disabled, enabled), LocalDate.of(2026, 1, 31)));
        assertEquals(11, PayrollLegalContextResolver.resolveConfigIdAtDate(
                List.of(disabled, enabled), LocalDate.of(2026, 2, 1)));
        assertThrows(IllegalStateException.class, () -> PayrollLegalContextResolver.resolveConfigIdAtDate(
                List.of(disabled, enabled), LocalDate.of(2025, 12, 31)));
    }

    private static PaieConfigLegale config(int id, String start, String end) {
        PaieConfigLegale config = new PaieConfigLegale();
        config.setIdConfigLegale(id);
        config.setDateDebut(Date.valueOf(LocalDate.parse(start)));
        config.setDateFin(end == null ? null : Date.valueOf(LocalDate.parse(end)));
        return config;
    }
}
