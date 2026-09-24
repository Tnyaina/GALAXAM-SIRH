package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PointageConfigurationResolutionTest {

    @Test
    void disabledMealTierDoesNotUseThresholdOrAmountFallback() {
        Map<String, ParametreGlobal.ParametreEtat> params = Map.of(
                "SEUIL_2ND_REPAS_1", new ParametreGlobal.ParametreEtat("SEUIL_2ND_REPAS_1", null, false, true),
                "MONTANT_2ND_REPAS_1", new ParametreGlobal.ParametreEtat("MONTANT_2ND_REPAS_1", "2000", true, true));

        PointageService.MealTier tier = PointageService.resolveMealTier(
                params, "SEUIL_2ND_REPAS_1", "MONTANT_2ND_REPAS_1");

        assertNull(tier.threshold());
        assertEquals(BigDecimal.ZERO, tier.amount());
    }

    @Test
    void missingMealTierConfigurationIsNotTreatedAsDisabledOrDefaulted() {
        Map<String, ParametreGlobal.ParametreEtat> params = Map.of(
                "MONTANT_2ND_REPAS_1", new ParametreGlobal.ParametreEtat("MONTANT_2ND_REPAS_1", "2000", true, true));

        assertThrows(SirhBusinessException.class, () -> PointageService.resolveMealTier(
                params, "SEUIL_2ND_REPAS_1", "MONTANT_2ND_REPAS_1"));
    }

    @Test
    void activeMealTierUsesConfiguredThreshold() {
        Map<String, ParametreGlobal.ParametreEtat> params = Map.of(
                "SEUIL_2ND_REPAS_1", new ParametreGlobal.ParametreEtat("SEUIL_2ND_REPAS_1", "19:45", true, true),
                "MONTANT_2ND_REPAS_1", new ParametreGlobal.ParametreEtat("MONTANT_2ND_REPAS_1", "2000", true, true));

        PointageService.MealTier tier = PointageService.resolveMealTier(
                params, "SEUIL_2ND_REPAS_1", "MONTANT_2ND_REPAS_1");

        assertEquals(LocalTime.of(19, 45), tier.threshold());
        assertEquals(new BigDecimal("2000"), tier.amount());
    }

    @Test
    void disabledNightRuleIsNotApplied() {
        Map<String, ParametreGlobal.ParametreEtat> params = Map.of(
                "SEUIL_NUIT_DEBUT", new ParametreGlobal.ParametreEtat("SEUIL_NUIT_DEBUT", null, false, true));

        assertNull(PointageService.resolveOptionalTimeRule(params, "SEUIL_NUIT_DEBUT"));
    }

    @Test
    void missingNightRuleIsNotSilentlyDisabled() {
        assertThrows(SirhBusinessException.class,
                () -> PointageService.resolveOptionalTimeRule(Map.of(), "SEUIL_NUIT_DEBUT"));
    }

    @Test
    void arrivalFloorCountsEarlyPunchFromConfiguredTimeOnlyWhenEnabled() {
        LocalTime punched = LocalTime.of(6, 0);
        assertEquals(LocalTime.of(7, 30), PointageService.effectiveMorningEntry(
                punched, LocalTime.of(7, 30)));
        assertEquals(punched, PointageService.effectiveMorningEntry(punched, null));
        assertEquals(LocalTime.of(8, 5), PointageService.effectiveMorningEntry(
                LocalTime.of(8, 5), LocalTime.of(7, 30)));
    }

    @Test
    void monthlyAllowanceCannotFallBackToFullAmountWithoutWorkingCalendar() {
        CalendrierOuvrableEngine calendar = new CalendrierOuvrableEngine();
        assertThrows(IllegalArgumentException.class,
                () -> calendar.calculerProrataJournalier(new BigDecimal("30000"), 0));
        assertEquals(BigDecimal.ZERO, calendar.calculerProrataJournalier(BigDecimal.ZERO, 0));
    }

    @Test
    void dailyTargetUsesOneResolutionForEntryAndSummary() {
        Map<String, ParametreGlobal.ParametreEtat> configured = Map.of(
                "SEUIL_HEURES_JOUR", new ParametreGlobal.ParametreEtat("SEUIL_HEURES_JOUR", "7.5", true, true));
        assertEquals(7.5, PointageService.resolveDailyTargetHours(configured));

        Map<String, ParametreGlobal.ParametreEtat> disabled = Map.of(
                "SEUIL_HEURES_JOUR", new ParametreGlobal.ParametreEtat("SEUIL_HEURES_JOUR", "8", false, true));
        assertEquals(0.0, PointageService.resolveDailyTargetHours(disabled));
        assertThrows(SirhBusinessException.class, () -> PointageService.resolveDailyTargetHours(Map.of()));
    }

    @Test
    void batchRejectsLegacyAllowanceFlagsWithoutGenericSelection() {
        SaisiePointageJourDTO day = new SaisiePointageJourDTO();
        day.setIndemniteRepasRecue(true);
        assertThrows(IllegalArgumentException.class,
                () -> new PointageService().enregistrerPointagesBatch(List.of(day), null));
    }
}
