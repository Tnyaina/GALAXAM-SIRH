package mg.galaxam.sirh.unit.contrat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.time.LocalDate;
import mg.galaxam.sirh.contrat.domain.ContratValidationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Tests - ContratValidationEngine")
class ContratValidationEngineTest {

    private ContratValidationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ContratValidationEngine();
    }

    @Test
    @DisplayName("validerDatesContrat : Date début obligatoire")
    void testDateDebutObligatoire() {
        assertThatThrownBy(() -> engine.validerDatesContrat("CDI", null, null, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La date de début de contrat est obligatoire");
    }

    @Test
    @DisplayName("validerDatesContrat : Date de fin obligatoire pour CDD et STAGE, facultative pour CDI")
    void testDateFinExigeeSelonType() {
        Date debut = Date.valueOf("2026-01-01");

        // CDI sans date de fin -> OK
        assertThatCode(() -> engine.validerDatesContrat("CDI", debut, null, 3))
                .doesNotThrowAnyException();

        // CDD sans date de fin -> Erreur
        assertThatThrownBy(() -> engine.validerDatesContrat("CDD", debut, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La date de fin est obligatoire pour un contrat de type CDD");

        // STAGE sans date de fin -> Erreur
        assertThatThrownBy(() -> engine.validerDatesContrat("STAGE", debut, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La date de fin est obligatoire pour un contrat de type STAGE");
    }

    @Test
    @DisplayName("validerDatesContrat : Date fin antérieure à date début")
    void testDateFinAnterieure() {
        Date debut = Date.valueOf("2026-06-01");
        Date finInvalide = Date.valueOf("2026-05-01");

        assertThatThrownBy(() -> engine.validerDatesContrat("CDD", debut, finInvalide, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ne peut pas être antérieure à la date de début");
    }

    @Test
    @DisplayName("validerDatesContrat : Période d'essai bornée entre 0 et 6 mois")
    void testDureeEssaiLimites() {
        Date debut = Date.valueOf("2026-01-01");

        // Négative -> Erreur
        assertThatThrownBy(() -> engine.validerDatesContrat("CDI", debut, null, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comprise entre 0 et 6 mois");

        // Supérieure à 6 mois -> Erreur
        assertThatThrownBy(() -> engine.validerDatesContrat("CDI", debut, null, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comprise entre 0 et 6 mois");

        // Valide (ex: 3 mois) -> OK
        assertThatCode(() -> engine.validerDatesContrat("CDI", debut, null, 3))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("calculerFinPeriodeEssai : Calcul exact du dernier jour d'essai")
    void testCalculerFinPeriodeEssai() {
        LocalDate debut = LocalDate.of(2026, 1, 1);

        // 3 mois d'essai -> Fin au 31 Mars 2026
        assertThat(engine.calculerFinPeriodeEssai(debut, 3))
                .isEqualTo(LocalDate.of(2026, 3, 31));

        // 1 mois d'essai -> Fin au 31 Janvier 2026
        assertThat(engine.calculerFinPeriodeEssai(debut, 1))
                .isEqualTo(LocalDate.of(2026, 1, 31));

        // Duree <= 0 -> null
        assertThat(engine.calculerFinPeriodeEssai(debut, 0)).isNull();
    }

    @Test
    @DisplayName("estTransitionStatutAutorisee : Machine à états des statuts de contrat")
    void testMachineAEtatsTransitions() {
        // BROUILLON vers VALIDE ou ANNULE
        assertThat(engine.estTransitionStatutAutorisee("BROUILLON", "VALIDE")).isTrue();
        assertThat(engine.estTransitionStatutAutorisee("BROUILLON", "ANNULE")).isTrue();

        // VALIDE vers ACTIF
        assertThat(engine.estTransitionStatutAutorisee("VALIDE", "ACTIF")).isTrue();

        // ACTIF vers CLOTURE
        assertThat(engine.estTransitionStatutAutorisee("ACTIF", "CLOTURE")).isTrue();

        // Transitions interdites / impossibles
        assertThat(engine.estTransitionStatutAutorisee("CLOTURE", "ACTIF")).isFalse();
        assertThat(engine.estTransitionStatutAutorisee("ANNULE", "VALIDE")).isFalse();
        assertThat(engine.estTransitionStatutAutorisee("ACTIF", "BROUILLON")).isFalse();
    }
}
