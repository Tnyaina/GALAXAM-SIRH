package mg.galaxam.sirh.model.conge;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CongeReglesMetierTest {

    @Test
    @DisplayName("Scission automatique d'un arrêt maladie > 30 jours (Article 13 Code du Travail Malagasy)")
    void testScissionArretMaladie30Jours() {
        LocalDate debut = LocalDate.of(2026, 6, 1);
        LocalDate fin = LocalDate.of(2026, 7, 15); // 45 jours au total

        BigDecimal totalJours = DemandeConge.calculerNombreJoursDecomptes(Date.valueOf(debut), Date.valueOf(fin));
        System.out.println("[TEST REGLE CONGE] Arrêt maladie total : " + totalJours + " jours.");

        assertTrue(totalJours.compareTo(BigDecimal.valueOf(30)) > 0, "L'arrêt dépasse 30 jours continus.");

        // Tranche 1 : 30 premiers jours avec maintien de salaire
        LocalDate finTranche1 = debut.plusDays(29);
        BigDecimal joursTranche1 = DemandeConge.calculerNombreJoursDecomptes(Date.valueOf(debut), Date.valueOf(finTranche1));

        // Tranche 2 : Reliquat en Maladie Sans Solde (Prise en charge CNaPS)
        LocalDate debutTranche2 = finTranche1.plusDays(1);
        BigDecimal joursTranche2 = DemandeConge.calculerNombreJoursDecomptes(Date.valueOf(debutTranche2), Date.valueOf(fin));

        System.out.println("[TEST REGLE CONGE] Tranche 1 (Maintien salaire) : " + joursTranche1 + " jours (du " + debut + " au " + finTranche1 + ")");
        System.out.println("[TEST REGLE CONGE] Tranche 2 (Sans solde / CNaPS) : " + joursTranche2 + " jours (du " + debutTranche2 + " au " + fin + ")");

        assertEquals(new BigDecimal("30"), joursTranche1);
        assertEquals(new BigDecimal("15"), joursTranche2);
        assertEquals(totalJours, joursTranche1.add(joursTranche2));
    }

    @Test
    @DisplayName("Congé Maternité légal (14 semaines = 98 jours)")
    void testCongeMaternite14Semaines() {
        LocalDate debut = LocalDate.of(2026, 1, 1);
        LocalDate fin = debut.plusWeeks(14).minusDays(1);

        BigDecimal joursMaternite = DemandeConge.calculerNombreJoursDecomptes(Date.valueOf(debut), Date.valueOf(fin));
        System.out.println("[TEST REGLE CONGE] Congé Maternité 14 semaines -> " + joursMaternite + " jours (98 attendus).");

        assertEquals(new BigDecimal("98"), joursMaternite);
    }

    @Test
    @DisplayName("Événement familial : Mariage ou Naissance (3 jours forfaitaires)")
    void testEvenementFamilialForfait() {
        LocalDate debut = LocalDate.of(2026, 9, 10);
        LocalDate fin = debut.plusDays(2); // 3 jours au total

        BigDecimal jours = DemandeConge.calculerNombreJoursDecomptes(Date.valueOf(debut), Date.valueOf(fin));
        System.out.println("[TEST REGLE CONGE] Événement familial (Mariage/Naissance) -> " + jours + " jours.");

        assertEquals(new BigDecimal("3"), jours);
    }
}
