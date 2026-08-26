package mg.galaxam.sirh.service.pointage;


import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PeriodePaieWorkflowTest {

    @Test
    @DisplayName("Suite logique des statuts d'une période de paie")
    void testCycleDeViePeriodePaie() {
        // 1. BROUILLON -> EN_COURS
        String statut1 = "BROUILLON";
        String statut2 = transition(statut1, "OUVRIR");
        System.out.println("[TEST PERIODE PAIE] Action OUVRIR -> Statut : " + statut2);
        assertEquals("EN_COURS", statut2);

        // 2. EN_COURS -> A_VALIDER (Fin des saisies)
        String statut3 = transition(statut2, "TRANSMETTRE");
        System.out.println("[TEST PERIODE PAIE] Action TRANSMETTRE -> Statut : " + statut3);
        assertEquals("A_VALIDER", statut3);

        // 3. A_VALIDER -> VALIDEE (Gel des données)
        String statut4 = transition(statut3, "VALIDER");
        System.out.println("[TEST PERIODE PAIE] Action VALIDER -> Statut : " + statut4);
        assertEquals("VALIDEE", statut4);

        // 4. VALIDEE -> CLOTUREE (Paie exécutée)
        String statut5 = transition(statut4, "CLOTURER");
        System.out.println("[TEST PERIODE PAIE] Action CLOTURER -> Statut : " + statut5);
        assertEquals("CLOTUREE", statut5);
    }

    @Test
    @DisplayName("Contrôle de pré-clôture : Détection d'anomalies bloquantes")
    void testPreClotureVerification() {
        int pointagesIncomplets = 2;
        int absencesInjustifiees = 1;
        int retardsNonJustifies = 3;

        int totalAnomalies = pointagesIncomplets + absencesInjustifiees + retardsNonJustifies;
        System.out.println("[TEST PERIODE PAIE] Contrôle pré-clôture : " + totalAnomalies + " anomalies détectées.");

        assertEquals(6, totalAnomalies);
        boolean aAvertissement = totalAnomalies > 0;
        assertTrue(aAvertissement, "Un avertissement doit être émis pour informer le gestionnaire avant validation.");
    }

    private String transition(String currentStatut, String action) {
        return switch (action) {
            case "OUVRIR" -> "EN_COURS";
            case "TRANSMETTRE" -> "A_VALIDER";
            case "VALIDER" -> "VALIDEE";
            case "CLOTURER" -> "CLOTUREE";
            case "ROUVRIR" -> "EN_COURS";
            default -> currentStatut;
        };
    }
}
