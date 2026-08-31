package mg.galaxam.sirh.model.conge;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import mg.galaxam.sirh.model.conge.service.CongeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CongeServiceTest {

    private final CongeService congeService = new CongeService(new mg.galaxam.sirh.model.conge.engine.CongeCalculateurEngine());

    @Test
    @DisplayName("Calcul standard de jours ouvrés / calendaires pour une demande de congé")
    void testCalculNombreJoursStandard() {
        Date debut = Date.valueOf(LocalDate.of(2026, 8, 1));
        Date fin = Date.valueOf(LocalDate.of(2026, 8, 10));

        BigDecimal jours = congeService.simulerNombreJours(debut, fin, false);
        System.out.println("[TEST CONGE] Simulation 01/08/2026 au 10/08/2026 -> " + jours + " jours décomptés.");

        assertEquals(0, new BigDecimal("10.0").compareTo(jours), "10 jours calendaires attendus.");
    }

    @Test
    @DisplayName("Calcul d'une demi-journée de congé")
    void testCalculDemiJournee() {
        Date date = Date.valueOf(LocalDate.of(2026, 8, 15));

        BigDecimal jours = congeService.simulerNombreJours(date, date, true);
        System.out.println("[TEST CONGE] Simulation Demi-Journée -> " + jours + " jour décompté.");

        assertEquals(new BigDecimal("0.5"), jours, "0.5 jour attendu.");
    }

    @Test
    @DisplayName("Dates invalides : dateFin avant dateDebut")
    void testDatesInvalides() {
        Date debut = Date.valueOf(LocalDate.of(2026, 8, 20));
        Date fin = Date.valueOf(LocalDate.of(2026, 8, 10));

        BigDecimal jours = congeService.simulerNombreJours(debut, fin, false);
        System.out.println("[TEST CONGE] Dates inversées -> " + jours + " jours retournés (ZERO attendu).");

        assertEquals(BigDecimal.ZERO, jours);
    }
}
