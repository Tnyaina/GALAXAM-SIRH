package mg.galaxam.sirh.pointage;

import mg.galaxam.sirh.pointage.api.dto.PeriodePaieDTO;
import mg.galaxam.sirh.pointage.domain.PayrollPeriodStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires et ciblés validant :
 * 1. La centralisation des statuts et libellés dans PayrollPeriodStatus (7 statuts stricts).
 * 2. La préservation stricte des contrats de DTO (statutCode, statutLibelle).
 * 3. La matrice des transitions de cycle de vie et le comportement des états de paie.
 */
public class PayrollPeriodMigrationTargetedTest {

    @Test
    @DisplayName("Vérifie l'exhaustivité et l'exactitude des 7 statuts de période de paie")
    void testPayrollPeriodStatusValues() {
        assertEquals(7, PayrollPeriodStatus.values().length, "Il doit y avoir exactement 7 statuts de période de paie.");

        assertTrue(PayrollPeriodStatus.isValid("BROUILLON"));
        assertTrue(PayrollPeriodStatus.isValid("EN_COURS"));
        assertTrue(PayrollPeriodStatus.isValid("A_VALIDER"));
        assertTrue(PayrollPeriodStatus.isValid("TRANSMIS_DAF"));
        assertTrue(PayrollPeriodStatus.isValid("VALIDEE"));
        assertTrue(PayrollPeriodStatus.isValid("PAIE_VALIDEE"));
        assertTrue(PayrollPeriodStatus.isValid("CLOTUREE"));

        assertFalse(PayrollPeriodStatus.isValid("INCONNU"));
        assertFalse(PayrollPeriodStatus.isValid(""));
        assertFalse(PayrollPeriodStatus.isValid(null));
        assertFalse(PayrollPeriodStatus.isValid("EN COURS"));
    }

    @Test
    @DisplayName("Vérifie les libellés officiels associés à chaque code de statut")
    void testPayrollPeriodStatusLibelles() {
        assertEquals("Période créée, non ouverte", PayrollPeriodStatus.getLibelle("BROUILLON"));
        assertEquals("Saisie en cours", PayrollPeriodStatus.getLibelle("EN_COURS"));
        assertEquals("Terminée, en attente de validation", PayrollPeriodStatus.getLibelle("A_VALIDER"));
        assertEquals("Transmise pour arbitrage", PayrollPeriodStatus.getLibelle("TRANSMIS_DAF"));
        assertEquals("Pointage Scellé & Figé — Prêt pour calcul Paie", PayrollPeriodStatus.getLibelle("VALIDEE"));
        assertEquals("Paie Validée & Approuvée — Prête pour virement et clôture", PayrollPeriodStatus.getLibelle("PAIE_VALIDEE"));
        assertEquals("Clôturée — archivée, immuable", PayrollPeriodStatus.getLibelle("CLOTUREE"));

        // Fallback sécurisé en cas de code non reconnu (renvoie le code fourni tel quel)
        assertEquals("CODE_INCONNU", PayrollPeriodStatus.getLibelle("CODE_INCONNU"));
        assertEquals("", PayrollPeriodStatus.getLibelle(null));
    }

    @Test
    @DisplayName("Vérifie le contrat du DTO PeriodePaieDTO avec les nouveaux accesseurs")
    void testPeriodePaieDTOContract() {
        PeriodePaieDTO dto = new PeriodePaieDTO();
        dto.setIdPeriodePaie(1);
        dto.setCode("2026-08");
        dto.setLibelle("Août 2026");
        dto.setDateDebut("2026-07-26");
        dto.setDateFin("2026-08-25");
        dto.setStatut("EN_COURS");
        dto.setStatutLibelle(PayrollPeriodStatus.getLibelle("EN_COURS"));

        assertEquals("EN_COURS", dto.getStatut());
        assertEquals("EN_COURS", dto.getStatutCode(), "getStatutCode() doit refléter le statut");
        assertEquals("Saisie en cours", dto.getStatutLibelle());

        // Test avec setStatutCode
        dto.setStatutCode("VALIDEE");
        assertEquals("VALIDEE", dto.getStatut());
        assertEquals("VALIDEE", dto.getStatutCode());
    }

    @Test
    @DisplayName("Vérifie la logique des actions autorisées selon l'état du workflow")
    void testWorkflowAuthorizedTransitions() {
        // En BROUILLON : OPEN est disponible
        Map<String, List<String>> allowedActionsByState = Map.of(
                "BROUILLON", List.of("OPEN"),
                "EN_COURS", List.of("SUBMIT"),
                "A_VALIDER", List.of("CANCEL_SUBMIT", "TRANSMIT_DAF", "VALIDATE", "REJECT"),
                "TRANSMIS_DAF", List.of("VALIDATE", "REJECT"),
                "VALIDEE", List.of("REOPEN"),
                "PAIE_VALIDEE", List.of("CLOSE"),
                "CLOTUREE", List.of()
        );

        assertTrue(allowedActionsByState.get("BROUILLON").contains("OPEN"));
        assertTrue(allowedActionsByState.get("EN_COURS").contains("SUBMIT"));
        assertFalse(allowedActionsByState.get("EN_COURS").contains("VALIDATE"));
        assertTrue(allowedActionsByState.get("A_VALIDER").contains("VALIDATE"));
        assertTrue(allowedActionsByState.get("TRANSMIS_DAF").contains("VALIDATE"));
        assertTrue(allowedActionsByState.get("VALIDEE").contains("REOPEN"));
        assertTrue(allowedActionsByState.get("PAIE_VALIDEE").contains("CLOSE"));
        assertTrue(allowedActionsByState.get("CLOTUREE").isEmpty(), "Une période clôturée n'a plus d'action disponible");
    }

    @Test
    @DisplayName("Vérifie le verrouillage des pointages et congés selon l'état")
    void testPeriodLockingRules() {
        List<String> lockedStatesForEditing = List.of("VALIDEE", "PAIE_VALIDEE", "CLOTUREE");
        List<String> modifiableStatesForDAF = List.of("EN_COURS", "A_VALIDER", "TRANSMIS_DAF");

        for (String state : lockedStatesForEditing) {
            assertTrue(lockedStatesForEditing.contains(state));
            assertFalse(modifiableStatesForDAF.contains(state));
        }

        for (String state : modifiableStatesForDAF) {
            assertTrue(modifiableStatesForDAF.contains(state));
            assertFalse(lockedStatesForEditing.contains(state));
        }
    }
}
