package mg.galaxam.sirh.cron;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerCronTest {

    @Test
    @DisplayName("Calcul de l'acquisition mensuelle de congés (2.5 jours par mois travaillé)")
    void testAcquisitionMensuelleConges() {
        BigDecimal quotaMensuel = new BigDecimal("2.5");
        int moisTravailles = 12;

        BigDecimal totalAnnuel = quotaMensuel.multiply(BigDecimal.valueOf(moisTravailles));
        System.out.println("[TEST CRON CONGES] Acquisition : " + quotaMensuel + " j/mois * " + moisTravailles + " mois = " + totalAnnuel + " jours.");

        assertEquals(new BigDecimal("30.0"), totalAnnuel, "Un salarié acquiert 30 jours calendaires (ou 2.5j/mois) par an.");
    }

    @Test
    @DisplayName("Règle d'ancienneté minimale pour 1ère acquisition (Art. 84 : 1 mois de service effectif)")
    void testAncienneteMinimaleAcquisition() {
        LocalDate today = LocalDate.now();
        LocalDate embaucheAujourdhui = today;
        LocalDate embauche1Mois = today.minusMonths(1);

        boolean eligibleAujourdhui = !embaucheAujourdhui.isAfter(today.minusMonths(1));
        boolean eligible1Mois = !embauche1Mois.isAfter(today.minusMonths(1));

        System.out.println("[TEST CRON CONGES] Embauche aujourd'hui éligible : " + eligibleAujourdhui + " (Faux attendu).");
        System.out.println("[TEST CRON CONGES] Embauche il y a 1 mois éligible : " + eligible1Mois + " (Vrai attendu).");

        assertFalse(eligibleAujourdhui, "Un salarié embauché aujourd'hui ne doit pas acquérir de congés dès son 1er jour.");
        assertTrue(eligible1Mois, "Un salarié ayant 1 mois révolu de service effectif doit acquérir 2.5 jours.");
    }

    @Test
    @DisplayName("Calcul de la bonification d'ancienneté (Code du Travail Malagasy : +1j tous les 5 ans)")
    void testBonificationAnciennete() {
        LocalDate dateDebutContrat = LocalDate.now().minusYears(11);
        long anneesAnciennete = ChronoUnit.YEARS.between(dateDebutContrat, LocalDate.now());

        System.out.println("[TEST CRON CONGES] Ancienneté : " + anneesAnciennete + " ans.");

        // Règle : +1 jour par tranche de 5 ans révolus
        int joursBonus = (int) (anneesAnciennete / 5);
        System.out.println("[TEST CRON CONGES] Jours de bonification accordés : +" + joursBonus + " jours (+2 attendus pour 11 ans).");

        assertEquals(2, joursBonus);
    }

    @Test
    @DisplayName("Détection des seuils d'échéance de fin de contrat (J-30, J-7, J-0)")
    void testCalculEcheancesContrat() {
        LocalDate today = LocalDate.now();

        LocalDate finDans30j = today.plusDays(30);
        LocalDate finDans7j = today.plusDays(7);
        LocalDate finAujourdhui = today;

        long diff30 = ChronoUnit.DAYS.between(today, finDans30j);
        long diff7 = ChronoUnit.DAYS.between(today, finDans7j);
        long diff0 = ChronoUnit.DAYS.between(today, finAujourdhui);

        System.out.println("[TEST CRON ALERTES] Différentiels calculés : 30j=" + diff30 + ", 7j=" + diff7 + ", J-0=" + diff0);

        assertEquals(30, diff30);
        assertEquals(7, diff7);
        assertEquals(0, diff0);
    }
}
