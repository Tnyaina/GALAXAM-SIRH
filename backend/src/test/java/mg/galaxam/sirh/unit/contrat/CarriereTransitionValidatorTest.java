package mg.galaxam.sirh.unit.contrat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mg.galaxam.sirh.contrat.domain.CarriereTransitionValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Tests - CarriereTransitionValidator")
class CarriereTransitionValidatorTest {

    @Test
    @DisplayName("Validation événement RH obligatoire")
    void testEventObligatoire() {
        assertThatThrownBy(() -> CarriereTransitionValidator.validerTransition("CDI", null, "CDI", "PERMANENT", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le type d'événement RH est obligatoire");

        assertThatThrownBy(() -> CarriereTransitionValidator.validerTransition("CDI", "  ", "CDI", "PERMANENT", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le type d'événement RH est obligatoire");
    }

    @Test
    @DisplayName("STAGE : Interdiction stricte des avenants internes (Promotion, Mutation, Revalorisation)")
    void testStageInterdictionAvenantInterne() {
        // Un stagiaire ne peut pas faire l'objet d'une mutation ou promotion
        assertThatThrownBy(() -> CarriereTransitionValidator.validerTransition(
                "STAGE", "PROMOTION", "STAGE", "STAGIAIRE", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Un collaborateur sous convention de Stage ne peut pas faire l'objet d'un avenant interne");

        assertThatThrownBy(() -> CarriereTransitionValidator.validerTransition(
                "STAGE", "MUTATION", "STAGE", "STAGIAIRE", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Un collaborateur sous convention de Stage ne peut pas faire l'objet d'un avenant interne");
    }

    @Test
    @DisplayName("STAGE : Événements autorisés (Embauche après stage, Fin de stage, Démission)")
    void testStageEvenementsAutorises() {
        // Embauche après stage vers CDD ou CDI avec période d'essai autorisée
        assertThatCode(() -> CarriereTransitionValidator.validerTransition(
                "STAGE", "EMBAUCHE_APRES_STAGE", "CDI", "PERMANENT", 3))
                .doesNotThrowAnyException();

        assertThatCode(() -> CarriereTransitionValidator.validerTransition(
                "STAGE", "EMBAUCHE_APRES_STAGE", "CDD", "PERMANENT", 1))
                .doesNotThrowAnyException();

        assertThatCode(() -> CarriereTransitionValidator.validerTransition(
                "STAGE", "FIN_CONTRAT", null, null, 0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TRANSFORMATION_CDI : Contrat cible CDI obligatoire et période d'essai interdite")
    void testTransformationCdi() {
        // Nominal : passage en CDI sans période d'essai
        assertThatCode(() -> CarriereTransitionValidator.validerTransition(
                "CDD", "TRANSFORMATION_CDI", "CDI", "PERMANENT", 0))
                .doesNotThrowAnyException();

        // Adversarial 1 : cible n'est pas CDI
        assertThatThrownBy(() -> CarriereTransitionValidator.validerTransition(
                "CDD", "TRANSFORMATION_CDI", "CDD", "PERMANENT", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("le type de contrat cible doit être parmi [CDI]");

        // Adversarial 2 : tentative d'imposer une période d'essai lors du passage en CDI
        assertThatThrownBy(() -> CarriereTransitionValidator.validerTransition(
                "CDD", "TRANSFORMATION_CDI", "CDI", "PERMANENT", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Une nouvelle période d'essai est interdite");
    }

    @Test
    @DisplayName("RENOUVELLEMENT_CDD : Contrat cible CDD obligatoire et période d'essai interdite")
    void testRenouvellementCdd() {
        // Nominal
        assertThatCode(() -> CarriereTransitionValidator.validerTransition(
                "CDD", "RENOUVELLEMENT_CDD", "CDD", "PERMANENT", 0))
                .doesNotThrowAnyException();

        // Adversarial : tentative d'ajouter une période d'essai
        assertThatThrownBy(() -> CarriereTransitionValidator.validerTransition(
                "CDD", "RENOUVELLEMENT_CDD", "CDD", "PERMANENT", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Une nouvelle période d'essai est interdite");
    }

    @Test
    @DisplayName("CDI : Avenants internes (Promotion, Mutation, Revalorisation)")
    void testCdiAvenants() {
        assertThatCode(() -> CarriereTransitionValidator.validerTransition(
                "CDI", "PROMOTION", "CDI", "PERMANENT", 0))
                .doesNotThrowAnyException();

        assertThatCode(() -> CarriereTransitionValidator.validerTransition(
                "CDI", "MUTATION", "CDI", "PERMANENT", 0))
                .doesNotThrowAnyException();

        assertThatCode(() -> CarriereTransitionValidator.validerTransition(
                "CDI", "REVALORISATION", "CDI", "PERMANENT", 0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CDD : Durée maximale et minimale de contrat individuel")
    void testValiderDureeContratCDD() {
        java.sql.Date debut = java.sql.Date.valueOf("2026-01-01");
        
        // CDD de 6 mois : valide
        java.sql.Date finValide = java.sql.Date.valueOf("2026-07-01");
        assertThatCode(() -> CarriereTransitionValidator.validerDureeContratCDD(debut, finValide, null))
                .doesNotThrowAnyException();

        // CDD de 3 ans (36 mois) : rejeté (> 24 mois)
        java.sql.Date fin3Ans = java.sql.Date.valueOf("2029-01-01");
        assertThatThrownBy(() -> CarriereTransitionValidator.validerDureeContratCDD(debut, fin3Ans, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ne peut excéder 24 mois");

        // CDD de moins d'un mois : rejeté
        java.sql.Date finTropCourt = java.sql.Date.valueOf("2026-01-15");
        assertThatThrownBy(() -> CarriereTransitionValidator.validerDureeContratCDD(debut, finTropCourt, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durée minimale d'un mois");

        // Date fin antérieure à date début : rejeté
        java.sql.Date finAnterieure = java.sql.Date.valueOf("2025-12-01");
        assertThatThrownBy(() -> CarriereTransitionValidator.validerDureeContratCDD(debut, finAnterieure, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postérieure à sa date de début");
    }
}
