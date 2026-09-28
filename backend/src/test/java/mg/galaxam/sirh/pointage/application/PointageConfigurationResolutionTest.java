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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointageConfigurationResolutionTest {

    @Test
    void secondMealIsIndependentOfFirstMealAndUsesOnlyOneTier() {
        Map<String, ParametreGlobal.ParametreEtat> params = Map.of(
                "SEUIL_2ND_REPAS_1", new ParametreGlobal.ParametreEtat("SEUIL_2ND_REPAS_1", "19:30", true, true),
                "MONTANT_2ND_REPAS_1", new ParametreGlobal.ParametreEtat("MONTANT_2ND_REPAS_1", "2000", true, true),
                "SEUIL_2ND_REPAS_2", new ParametreGlobal.ParametreEtat("SEUIL_2ND_REPAS_2", "21:00", true, true),
                "MONTANT_2ND_REPAS_2", new ParametreGlobal.ParametreEtat("MONTANT_2ND_REPAS_2", "4000", true, true));
        var first = PointageService.resolveMealTier(params, "SEUIL_2ND_REPAS_1", "MONTANT_2ND_REPAS_1");
        var second = PointageService.resolveMealTier(params, "SEUIL_2ND_REPAS_2", "MONTANT_2ND_REPAS_2");
        assertNull(PointageService.resolveSecondMealAward(false, LocalTime.of(19, 29), first, second));
        var normal = PointageService.resolveSecondMealAward(false, LocalTime.of(19, 30), first, second);
        assertEquals("REPAS_2", normal.code());
        assertEquals(new BigDecimal("2000"), normal.amount());
        var major = PointageService.resolveSecondMealAward(false, LocalTime.of(21, 0), first, second);
        assertEquals("REPAS_2_MAJORE", major.code());
        assertEquals(new BigDecimal("4000"), major.amount());
        assertNull(PointageService.resolveSecondMealAward(true, LocalTime.of(21, 0), first, second));
    }

    @Test
    void disabledMealTierHasNoFallbackButMissingActiveConfigurationFails() {
        Map<String, ParametreGlobal.ParametreEtat> disabled = Map.of(
                "SEUIL_2ND_REPAS_1", new ParametreGlobal.ParametreEtat("SEUIL_2ND_REPAS_1", "19:30", true, true),
                "MONTANT_2ND_REPAS_1", new ParametreGlobal.ParametreEtat("MONTANT_2ND_REPAS_1", "2000", false, true));
        var first = PointageService.resolveMealTier(disabled, "SEUIL_2ND_REPAS_1", "MONTANT_2ND_REPAS_1");
        assertNull(first.threshold());
        assertEquals(BigDecimal.ZERO, first.amount());
        assertNull(PointageService.resolveSecondMealAward(false, LocalTime.of(20, 0), first,
                new PointageService.MealTier(null, BigDecimal.ZERO)));
        assertThrows(SirhBusinessException.class, () -> PointageService.resolveMealTier(
                Map.of(), "SEUIL_2ND_REPAS_1", "MONTANT_2ND_REPAS_1"));
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
    void cas1_siteActif_minimum0730_badge0700_donne0730() {
        LocalTime minimum = LocalTime.of(7, 30);
        LocalTime badge = LocalTime.of(7, 0);
        assertEquals(LocalTime.of(7, 30), PointageService.effectiveMorningEntry(badge, minimum));
    }

    @Test
    void cas2_siteActif_minimum0730_badge0720_donne0730() {
        LocalTime minimum = LocalTime.of(7, 30);
        LocalTime badge = LocalTime.of(7, 20);
        assertEquals(LocalTime.of(7, 30), PointageService.effectiveMorningEntry(badge, minimum));
    }

    @Test
    void cas3_siteActif_minimum0730_badge0745_donne0745() {
        LocalTime minimum = LocalTime.of(7, 30);
        LocalTime badge = LocalTime.of(7, 45);
        assertEquals(LocalTime.of(7, 45), PointageService.effectiveMorningEntry(badge, minimum));
    }

    @Test
    void cas4_siteActif_minimum0730_badge0805_donne0805() {
        LocalTime minimum = LocalTime.of(7, 30);
        LocalTime badge = LocalTime.of(8, 5);
        assertEquals(LocalTime.of(8, 5), PointageService.effectiveMorningEntry(badge, minimum));
    }

    @Test
    void cas5_regleInactive_badge0700_donne0700() {
        LocalTime minimumInactif = null;
        LocalTime badge = LocalTime.of(7, 0);
        assertEquals(LocalTime.of(7, 0), PointageService.effectiveMorningEntry(badge, minimumInactif));
    }

    @Test
    void cas6_regleActive_minimumNull_rejeteParControle() {
        mg.galaxam.sirh.organisation.persistence.RefSite site = new mg.galaxam.sirh.organisation.persistence.RefSite();
        site.setCodeSite("TEST");
        site.setLibelle("Site Test");
        site.setPlancherArriveeActif(true);
        site.setHeureEntreeMinimaleValorisee(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> site.controler(null));
        assertTrue(ex.getMessage().contains("obligatoire lorsque le plancher"));
    }

    @Test
    void cas7_regleInactive_heureStockeeConserveePourReactivation() {
        mg.galaxam.sirh.organisation.persistence.RefSite site = new mg.galaxam.sirh.organisation.persistence.RefSite();
        site.setCodeSite("TEST");
        site.setLibelle("Site Test");
        site.setPlancherArriveeActif(false);
        site.setHeureEntreeMinimaleValorisee(java.sql.Time.valueOf("07:30:00"));
        // La règle inactive avec heure conservée est bien portée par le modèle
        org.junit.jupiter.api.Assertions.assertFalse(site.isPlancherArriveeActif());
        assertEquals(java.sql.Time.valueOf("07:30:00"), site.getHeureEntreeMinimaleValorisee());
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
