package mg.galaxam.sirh.contrat.engine;

import mg.galaxam.sirh.model.contrat.engine.CarriereTransitionValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CarriereTransitionValidatorTest — Validation Métier des Transitions de Carrière (Miroir Backend)")
class CarriereTransitionValidatorTest {

    @Test
    @DisplayName("STAGE : Seul Passage vers CDD/CDI est éligible")
    void testStageEligibilite() {
        // Valide
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("STAGE", "EMBAUCHE_APRES_STAGE", "CDI", "PERMANENT", 3));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("STAGE", "EMBAUCHE_APRES_STAGE", "CDD", "PERMANENT", 3));

        // Invalide
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("STAGE", "MUTATION", null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("STAGE", "TRANSFORMATION_CDI", "CDI", "PERMANENT", 0));
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("STAGE", "RENOUVELLEMENT_CDD", "CDD", "PERMANENT", 0));
    }

    @Test
    @DisplayName("CDD : Transformation CDI et Renouvellement autorisés, Stage interdit")
    void testCddEligibilite() {
        // Valide
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDD", "TRANSFORMATION_CDI", "CDI", "PERMANENT", 0));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDD", "RENOUVELLEMENT_CDD", "CDD", "PERMANENT", 0));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDD", "PROMOTION", null, null, null));

        // Invalide : stage interdit
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("CDD", "EMBAUCHE_APRES_STAGE", "CDI", "PERMANENT", 0));

        // Invalide : reprise consultant réservée aux fins de carrière CDI
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("CDD", "REPRISE_CONSULTANT", "PRESTATION", "CONSULTANT", 0));
    }

    @Test
    @DisplayName("CDD Renouvellement : Période d'essai strictement interdite")
    void testCddRenouvellementInterdictionEssai() {
        // Essai > 0 interdit lors d'un renouvellement
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("CDD", "RENOUVELLEMENT_CDD", "CDD", "PERMANENT", 1));
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("CDD", "RENOUVELLEMENT_CDD", "CDD", "PERMANENT", 3));
    }

    @Test
    @DisplayName("CDI : Transformation CDI et Renouvellement CDD interdits")
    void testCdiEligibilite() {
        // Valide : Avenant interne, reprise ancienneté ou consultant
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDI", "MUTATION", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDI", "REPRISE_CONSULTANT", "PRESTATION", "CONSULTANT", 0));

        // Invalide : Déjà en CDI
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("CDI", "TRANSFORMATION_CDI", "CDI", "PERMANENT", 0));

        // Invalide : Pas de renouvellement CDD sur un CDI
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("CDI", "RENOUVELLEMENT_CDD", "CDD", "PERMANENT", 0));
    }

    @Test
    @DisplayName("Transformation CDI : Contrat cible doit obligatoirement être CDI et pas d'essai")
    void testContraintesTransformationCdi() {
        // Valide
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDD", "TRANSFORMATION_CDI", "CDI", "PERMANENT", 0));

        // Type contrat cible incorrect
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("CDD", "TRANSFORMATION_CDI", "CDD", "PERMANENT", 0));

        // Essai interdit en transformation CDD -> CDI
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("CDD", "TRANSFORMATION_CDI", "CDI", "PERMANENT", 3));
    }

    @Test
    @DisplayName("CDI Ruptures : Démission, Licenciement, Rupture conventionnelle, Retraite et Fin de contrat autorisés")
    void testCdiRupturesEligibilite() {
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDI", "DEMISSION", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDI", "LICENCIEMENT", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDI", "RUPTURE_CONVENTIONNELLE", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDI", "DEPART_RETRAITE", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDI", "FIN_CONTRAT", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDI", "RUPTURE_ESSAI", null, null, null));

        // Période d'essai interdite sur une rupture de CDI
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("CDI", "DEMISSION", null, null, 1));
    }

    @Test
    @DisplayName("CDD Ruptures : Fin de contrat, Démission, Licenciement autorisés")
    void testCddRupturesEligibilite() {
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDD", "FIN_CONTRAT", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDD", "DEMISSION", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDD", "LICENCIEMENT", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("CDD", "RUPTURE_ESSAI", null, null, null));

        // Période d'essai interdite sur fin de contrat
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("CDD", "FIN_CONTRAT", null, null, 2));
    }

    @Test
    @DisplayName("STAGE : Fin de contrat et Rupture autorisées en plus de l'embauche")
    void testStageFinEtRuptureEligibilite() {
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("STAGE", "FIN_CONTRAT", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("STAGE", "RUPTURE_ESSAI", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("STAGE", "DEMISSION", null, null, null));
    }

    @Test
    @DisplayName("PRESTATION : Mobilités de salariés interdites, Fin de contrat autorisée")
    void testPrestationEligibilite() {
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("PRESTATION", "FIN_CONTRAT", null, null, null));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerTransition("PRESTATION", "TRANSFORMATION_CDI", "CDI", "PERMANENT", 0));

        // Promotions ou mutations interdites pour prestataires
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition("PRESTATION", "PROMOTION", null, null, null));
        assertTrue(ex.getMessage().contains("Les intervenants sous contrat"));
    }
}
