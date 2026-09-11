package mg.galaxam.sirh.unit.conge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import mg.galaxam.sirh.model.conge.engine.CongeCalculateurEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Tests - CongeCalculateurEngine")
class CongeCalculateurEngineTest {

    private CongeCalculateurEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CongeCalculateurEngine();
    }

    @Test
    @DisplayName("calculerNombreJours : Décompte en jours calendaires stricts")
    void testCalculerNombreJours() {
        LocalDate d1 = LocalDate.of(2026, 9, 1);
        LocalDate d5 = LocalDate.of(2026, 9, 5);

        // 1er au 5 septembre inclus -> 5 jours
        assertThat(engine.calculerNombreJours(d1, d5, false))
                .isEqualByComparingTo(new BigDecimal("5.0"));

        // Demi-journée le 1er septembre -> 0.5 jour
        assertThat(engine.calculerNombreJours(d1, d1, true))
                .isEqualByComparingTo(new BigDecimal("0.5"));

        // Demi-journée sur 2 dates distinctes -> Erreur
        assertThatThrownBy(() -> engine.calculerNombreJours(d1, d5, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Une demi-journée doit avoir une date de début et de fin identiques");

        // Dates obligatoires
        assertThatThrownBy(() -> engine.calculerNombreJours(null, d5, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("calculerAcquisitionMensuelle : +2.5 jours calendaires par mois de service effectif")
    void testCalculerAcquisitionMensuelle() {
        // 1 mois -> 2.5 jours
        assertThat(engine.calculerAcquisitionMensuelle(1))
                .isEqualByComparingTo(new BigDecimal("2.5"));

        // 12 mois (1 an) -> 30.0 jours
        assertThat(engine.calculerAcquisitionMensuelle(12))
                .isEqualByComparingTo(new BigDecimal("30.0"));

        // 0 mois -> 0 jour
        assertThat(engine.calculerAcquisitionMensuelle(0))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("calculerAcquisitionMensuelleProratisee : Proratisation au 30ème")
    void testCalculerAcquisitionProratisee() {
        // 15 jours sur 30 -> 1.25 jour acquis
        assertThat(engine.calculerAcquisitionMensuelleProratisee(new BigDecimal("15.0")))
                .isEqualByComparingTo(new BigDecimal("1.25"));

        // 30 jours sur 30 -> 2.5 jours acquis
        assertThat(engine.calculerAcquisitionMensuelleProratisee(new BigDecimal("30.0")))
                .isEqualByComparingTo(new BigDecimal("2.5"));
    }

    @Test
    @DisplayName("doitScinderArretMaladie : Règle Art. 13 (> 30 jours consécutifs)")
    void testDoitScinderArretMaladie() {
        // 30 jours -> Pas de scission
        assertThat(engine.doitScinderArretMaladie(new BigDecimal("30.0"))).isFalse();

        // 31 jours -> Doit être scindé (30j plein salaire + 1j sans solde CNaPS)
        assertThat(engine.doitScinderArretMaladie(new BigDecimal("31.0"))).isTrue();

        // 45 jours -> Doit être scindé
        assertThat(engine.doitScinderArretMaladie(new BigDecimal("45.0"))).isTrue();
    }

    @Test
    @DisplayName("verifierEligibilitePoolFamilial : Plafond partagé de 10 jours / an (Art. 88)")
    void testPoolFamilialPlafond() {
        // Déjà 6j pris + 3j demandés = 9j <= 10j -> Admissible
        assertThat(engine.verifierEligibilitePoolFamilial(new BigDecimal("6.0"), new BigDecimal("3.0"))).isTrue();

        // Déjà 8j pris + 3j demandés = 11j > 10j -> Dépassement
        assertThat(engine.verifierEligibilitePoolFamilial(new BigDecimal("8.0"), new BigDecimal("3.0"))).isFalse();
    }

    @Test
    @DisplayName("calculerBonificationAnciennete : +1 jour par tranche de 5 ans révolus")
    void testBonificationAnciennete() {
        assertThat(engine.calculerBonificationAnciennete(4)).isZero();
        assertThat(engine.calculerBonificationAnciennete(5)).isEqualTo(1);
        assertThat(engine.calculerBonificationAnciennete(9)).isEqualTo(1);
        assertThat(engine.calculerBonificationAnciennete(10)).isEqualTo(2);
        assertThat(engine.calculerBonificationAnciennete(15)).isEqualTo(3);
    }
}
