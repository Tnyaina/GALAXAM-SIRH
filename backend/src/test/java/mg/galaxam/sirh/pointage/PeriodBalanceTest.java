package mg.galaxam.sirh.pointage;

import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine.PeriodBalance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeriodBalanceTest {
    @Test
    void seizeHeuresMoinsDeuxAbsencesMoinsCinqDiversDonnentHuitEtUne() {
        var r = PeriodBalance.calculate(480, 480, 120, -300, 480);
        assertEquals(540, r.balanceMinutes());
        assertEquals(480, r.overtime30Minutes());
        assertEquals(60, r.overtime50Minutes());
        assertEquals(0, r.absenceMinutes());
    }

    @Test
    void respecteUnPalierConfigureDeSixHeures() {
        var r = PeriodBalance.calculate(480, 480, 120, -300, 360);
        assertEquals(360, r.overtime30Minutes());
        assertEquals(180, r.overtime50Minutes());
    }

    @Test
    void negatifDevientAbsenceEtZeroNaAucuneHs() {
        var r = PeriodBalance.calculate(60, 60, 180, -60, 480);
        assertEquals(120, r.absenceMinutes());
        assertEquals(0, r.overtime30Minutes());
        assertEquals(0, r.overtime50Minutes());
        assertEquals(0, PeriodBalance.calculate(60, 60, 180, 60, 480).balanceMinutes());
    }

    @Test
    void positifDafSansPointageHsEstUneDecisionDePaiement() {
        var r = PeriodBalance.calculate(0, 0, 0, 120, 480);
        assertEquals(120, r.overtime30Minutes());
        assertEquals(0, r.overtime50Minutes());
    }

    @Test
    void conserveLaMinuteEtNeReinitialisePasLePalierParSemaine() {
        assertEquals(1, PeriodBalance.calculate(481, 0, 0, 0, 480).overtime50Minutes());
        assertEquals(480, PeriodBalance.calculate(960, 0, 0, 0, 480).overtime50Minutes());
    }
}
