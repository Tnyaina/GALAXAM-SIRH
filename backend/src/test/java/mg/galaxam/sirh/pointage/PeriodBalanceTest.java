package mg.galaxam.sirh.pointage;

import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine.PeriodBalance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeriodBalanceTest {
    @Test
    void lesRetraitsConsommentLaTranche30PuisLaTranche50() {
        var r = PeriodBalance.calculate(480, 480, 120, -300);
        assertEquals(540, r.balanceMinutes());
        assertEquals(60, r.overtime30Minutes());
        assertEquals(480, r.overtime50Minutes());
        assertEquals(0, r.absenceMinutes());
    }

    @Test
    void conserveLesTranchesHebdomadairesSansNouveauPalierDePeriode() {
        var r = PeriodBalance.calculate(537, 0, 0, 0);
        assertEquals(537, r.balanceMinutes());
        assertEquals(537, r.overtime30Minutes());
        assertEquals(0, r.overtime50Minutes());

        var apresRetrait = PeriodBalance.calculate(537, 0, 0, -150);
        assertEquals(387, apresRetrait.balanceMinutes());
        assertEquals(387, apresRetrait.overtime30Minutes());
        assertEquals(0, apresRetrait.overtime50Minutes());
    }

    @Test
    void negatifDevientAbsenceEtZeroNaAucuneHs() {
        var r = PeriodBalance.calculate(60, 60, 180, -60);
        assertEquals(120, r.absenceMinutes());
        assertEquals(0, r.overtime30Minutes());
        assertEquals(0, r.overtime50Minutes());
        assertEquals(0, PeriodBalance.calculate(60, 60, 180, 60).balanceMinutes());
    }

    @Test
    void positifDafSansPointageHsEstUneDecisionDePaiement() {
        var r = PeriodBalance.calculate(0, 0, 0, 120);
        assertEquals(120, r.overtime30Minutes());
        assertEquals(0, r.overtime50Minutes());
    }

    @Test
    void conserveLePalierHebdomadaireQuandSeuleLaTranche50Reste() {
        var r = PeriodBalance.calculate(120, 240, 0, -180);
        assertEquals(180, r.balanceMinutes());
        assertEquals(0, r.overtime30Minutes());
        assertEquals(180, r.overtime50Minutes());
    }

    @Test
    void unRetraitAuDelaDesHsDevientUneAbsenceFinale() {
        var r = PeriodBalance.calculate(120, 240, 0, -420);
        assertEquals(-60, r.balanceMinutes());
        assertEquals(60, r.absenceMinutes());
        assertEquals(0, r.overtime30Minutes());
        assertEquals(0, r.overtime50Minutes());
    }
}
