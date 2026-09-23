package mg.galaxam.sirh.unit.contrat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import mg.galaxam.sirh.contrat.domain.AncienneteEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Tests - AncienneteEngine")
class AncienneteEngineTest {

    private AncienneteEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AncienneteEngine();
    }

    @Test
    @DisplayName("evaluerAnciennete : Contrat unique sans historique antérieur")
    void testContratUnique() {
        LocalDate debut = LocalDate.of(2024, 1, 1);
        LocalDate refDate = LocalDate.of(2026, 1, 1); // 2 ans plus tard

        AncienneteEngine.IntervalleContrat c = new AncienneteEngine.IntervalleContrat(
                1, debut, null, null);

        AncienneteEngine.AncienneteBilan bilan = engine.evaluerAnciennete(List.of(c), 1, refDate);

        assertThat(bilan.nombreContratsChaine()).isEqualTo(1);
        assertThat(bilan.ansEffectifs()).isEqualTo(2);
        assertThat(bilan.moisRestantsEffectifs()).isZero();
        assertThat(bilan.ancienneteEffectiveTexte()).isEqualTo("2 ans");
        assertThat(bilan.ancienneteContratTexte()).isEqualTo("2 ans");
    }

    @Test
    @DisplayName("evaluerAnciennete : Chaîne de contrats successifs (CDD 1 -> CDD 2 -> CDI)")
    void testChaineContratsSuccessifs() {
        // Contrat 1 : CDD 1 an (2023)
        AncienneteEngine.IntervalleContrat c1 = new AncienneteEngine.IntervalleContrat(
                10, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), null);

        // Contrat 2 : Renouvellement CDD 1 an (2024) rattaché à C1
        AncienneteEngine.IntervalleContrat c2 = new AncienneteEngine.IntervalleContrat(
                11, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 10);

        // Contrat 3 : Transformation en CDI (2025 à aujourd'hui) rattaché à C2
        AncienneteEngine.IntervalleContrat c3 = new AncienneteEngine.IntervalleContrat(
                12, LocalDate.of(2025, 1, 1), null, 11);

        LocalDate refDate = LocalDate.of(2026, 7, 1); // 1 an et 6 mois après début CDI

        AncienneteEngine.AncienneteBilan bilan = engine.evaluerAnciennete(List.of(c1, c2, c3), 12, refDate);

        // Ancienneté totale cumulée : 2023 (1 an) + 2024 (1 an) + 1.5 an (CDI) = 3 ans et 6 mois
        assertThat(bilan.nombreContratsChaine()).isEqualTo(3);
        assertThat(bilan.ansEffectifs()).isEqualTo(3);
        assertThat(bilan.moisRestantsEffectifs()).isEqualTo(6);
        assertThat(bilan.dateOrigineChaine()).isEqualTo(LocalDate.of(2023, 1, 1));

        // Ancienneté spécifique au contrat actuel (CDI) : 18 mois (1 an et 6 mois)
        assertThat(bilan.moisContratActuel()).isEqualTo(18);
        assertThat(bilan.ancienneteContratTexte()).isEqualTo("1 an 6 mois");
    }

    @Test
    @DisplayName("evaluerAnciennete : Réembauche ultérieure après rupture/STC (interruption non chaînée)")
    void testReembaucheApresRupture() {
        // Période 1 passée (2020) clôturée et soldée
        AncienneteEngine.IntervalleContrat ancienContrat = new AncienneteEngine.IntervalleContrat(
                50, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), null);

        // Période 2 : Réembauche en 2025 avec un nouveau contrat non lié (idContratPrecedent = null)
        AncienneteEngine.IntervalleContrat nouveauContrat = new AncienneteEngine.IntervalleContrat(
                51, LocalDate.of(2025, 1, 1), null, null);

        LocalDate refDate = LocalDate.of(2026, 1, 1); // 1 an après réembauche

        AncienneteEngine.AncienneteBilan bilan = engine.evaluerAnciennete(
                List.of(ancienContrat, nouveauContrat), 51, refDate);

        // La nouvelle chaîne ne remonte PAS à 2020
        assertThat(bilan.nombreContratsChaine()).isEqualTo(1);
        assertThat(bilan.dateOrigineChaine()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(bilan.ansEffectifs()).isEqualTo(1);
        assertThat(bilan.ancienneteEffectiveTexte()).isEqualTo("1 an");
    }

    @Test
    @DisplayName("formaterAncienneteTexte : Règles de pluriel et mentions officielles")
    void testFormatageTexte() {
        assertThat(engine.formaterAncienneteTexte(1, 0, 365)).isEqualTo("1 an");
        assertThat(engine.formaterAncienneteTexte(3, 2, 1150)).isEqualTo("3 ans 2 mois");
        assertThat(engine.formaterAncienneteTexte(0, 5, 150)).isEqualTo("5 mois");
        assertThat(engine.formaterAncienneteTexte(0, 0, 20)).isEqualTo("Moins d'un mois");
        assertThat(engine.formaterAncienneteTexte(0, 0, 0)).isEqualTo("—");
    }
}
