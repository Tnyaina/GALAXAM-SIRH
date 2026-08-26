package mg.galaxam.sirh.model.paieconfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires validant les contrôles stricts de min/max, intervalles,
 * non-chevauchement et continuité des barèmes IRSA et types de cotisations.
 */
class BaremeIrsaValidationTest {

    @Test
    @DisplayName("IRSA : Validation réussie pour un intervalle valide [0 Ar - 350 000 Ar à 0%]")
    void testIrsaValide() {
        BaremeIrsa b = new BaremeIrsa();
        b.setNumeroTranche(1);
        b.setTrancheMin(BigDecimal.ZERO);
        b.setTrancheMax(new BigDecimal("350000"));
        b.setTaux(BigDecimal.ZERO);
        b.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));

        assertDoesNotThrow(b::valider);
    }

    @Test
    @DisplayName("IRSA : Rejet si trancheMax <= trancheMin (Intervalle invalide)")
    void testIrsaTrancheMaxInferieureOuEgale() {
        BaremeIrsa b = new BaremeIrsa();
        b.setNumeroTranche(2);
        b.setTrancheMin(new BigDecimal("400000"));
        b.setTrancheMax(new BigDecimal("350000")); // Invalide : Max < Min
        b.setTaux(new BigDecimal("5"));
        b.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, b::valider);
        System.out.println("[TEST IRSA VALIDATION] Erreur capturée : " + ex.getMessage());
        assertTrue(ex.getMessage().contains("strictement supérieur au montant minimum"));
    }

    @Test
    @DisplayName("IRSA : Détection de chevauchement entre deux tranches [300k - 500k] et [400k - 600k]")
    void testIrsaChevauchementIntervalles() {
        BaremeIrsa t1 = new BaremeIrsa();
        t1.setNumeroTranche(1);
        t1.setTrancheMin(new BigDecimal("300000"));
        t1.setTrancheMax(new BigDecimal("500000"));

        BaremeIrsa t2 = new BaremeIrsa();
        t2.setNumeroTranche(2);
        t2.setTrancheMin(new BigDecimal("400000")); // Chevauche t1 !
        t2.setTrancheMax(new BigDecimal("600000"));

        // Algorithme de détection de chevauchement
        BigDecimal startMax = t1.getTrancheMin().max(t2.getTrancheMin()); // 400 000
        BigDecimal endMin = t1.getTrancheMax().min(t2.getTrancheMax());     // 500 000
        boolean chevauchement = startMax.compareTo(endMin) <= 0;

        assertTrue(chevauchement, "Un chevauchement doit être détecté car [400k, 500k] est commun aux deux tranches.");
    }

    @Test
    @DisplayName("IRSA : Tranches strictement disjointes et continues [0 - 350k] et [350 001 - 400k]")
    void testIrsaTranchesDisjointes() {
        BaremeIrsa t1 = new BaremeIrsa();
        t1.setTrancheMin(BigDecimal.ZERO);
        t1.setTrancheMax(new BigDecimal("350000"));

        BaremeIrsa t2 = new BaremeIrsa();
        t2.setTrancheMin(new BigDecimal("350001"));
        t2.setTrancheMax(new BigDecimal("400000"));

        BigDecimal startMax = t1.getTrancheMin().max(t2.getTrancheMin()); // 350 001
        BigDecimal endMin = t1.getTrancheMax().min(t2.getTrancheMax());     // 350 000
        boolean chevauchement = startMax.compareTo(endMin) <= 0;

        assertFalse(chevauchement, "Les tranches sont disjointes et continues (350 001 > 350 000)");
    }

    @Test
    @DisplayName("IRSA : Rejet si montant minimum négatif")
    void testIrsaTrancheMinNegative() {
        BaremeIrsa b = new BaremeIrsa();
        b.setNumeroTranche(1);
        b.setTrancheMin(new BigDecimal("-500"));
        b.setTrancheMax(new BigDecimal("100000"));
        b.setTaux(BigDecimal.ZERO);
        b.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, b::valider);
        assertTrue(ex.getMessage().contains("ne peut pas être négatif"));
    }

    @Test
    @DisplayName("IRSA : Rejet si taux < 0% ou > 100%")
    void testIrsaTauxInvalide() {
        BaremeIrsa b = new BaremeIrsa();
        b.setNumeroTranche(3);
        b.setTrancheMin(new BigDecimal("350000"));
        b.setTrancheMax(new BigDecimal("400000"));
        b.setTaux(new BigDecimal("120")); // > 100%
        b.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, b::valider);
        assertTrue(ex.getMessage().contains("doit être compris entre 0% et 100%"));
    }

    @Test
    @DisplayName("IRSA : Rejet si dateFin < dateDebut")
    void testIrsaDatesInvalides() {
        BaremeIrsa b = new BaremeIrsa();
        b.setNumeroTranche(1);
        b.setTrancheMin(BigDecimal.ZERO);
        b.setTrancheMax(new BigDecimal("350000"));
        b.setTaux(BigDecimal.ZERO);
        b.setDateDebut(Date.valueOf(LocalDate.of(2026, 6, 1)));
        b.setDateFin(Date.valueOf(LocalDate.of(2026, 1, 1))); // Invalide : Fin avant Début

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, b::valider);
        assertTrue(ex.getMessage().contains("ne peut pas être antérieure"));
    }

    @Test
    @DisplayName("Cotisations : Rejet si taux salarial ou patronal > 100% ou < 0%")
    void testCotisationTauxInvalide() {
        RefTypeCotisation cot = new RefTypeCotisation();
        cot.setCode("TEST_COT");
        cot.setLibelle("Cotisation Test");
        cot.setTauxSalarial(new BigDecimal("-1")); // Invalide
        cot.setTauxPatronal(new BigDecimal("13"));
        cot.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, cot::valider);
        assertTrue(ex.getMessage().contains("doit être compris entre 0% et 100%"));
    }
}
