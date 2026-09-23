package mg.galaxam.sirh.unit.conge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
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
    @DisplayName("RG-CONGE-01/02 : Décompte en jours calendaires stricts - Cas A à E obligatoires")
    void testCalculerNombreJoursCasA_E() {
        // Cas A : Vendredi -> Dimanche = 3 jours calendaires (week-end inclus)
        LocalDate venA = LocalDate.of(2026, 9, 18);
        LocalDate dimA = LocalDate.of(2026, 9, 20);
        assertThat(engine.calculerNombreJours(venA, dimA, false))
                .isEqualByComparingTo(new BigDecimal("3.0"));

        // Cas B : Lundi -> Dimanche = 7 jours calendaires
        LocalDate lunB = LocalDate.of(2026, 9, 14);
        LocalDate dimB = LocalDate.of(2026, 9, 20);
        assertThat(engine.calculerNombreJours(lunB, dimB, false))
                .isEqualByComparingTo(new BigDecimal("7.0"));

        // Cas C : Lundi -> Vendredi = 5 jours calendaires (même si un jour férié est inclus)
        LocalDate lunC = LocalDate.of(2026, 9, 14);
        LocalDate venC = LocalDate.of(2026, 9, 18);
        assertThat(engine.calculerNombreJours(lunC, venC, false))
                .isEqualByComparingTo(new BigDecimal("5.0"));

        // Cas D : Vendredi -> Lundi inclus = 4 jours calendaires
        LocalDate venD = LocalDate.of(2026, 9, 18);
        LocalDate lunD = LocalDate.of(2026, 9, 21);
        assertThat(engine.calculerNombreJours(venD, lunD, false))
                .isEqualByComparingTo(new BigDecimal("4.0"));

        // Cas E : Vendredi -> Dimanche retour lundi = 3 jours débités
        LocalDate venE = LocalDate.of(2026, 9, 18);
        LocalDate dimE = LocalDate.of(2026, 9, 20);
        assertThat(engine.calculerNombreJours(venE, dimE, false))
                .isEqualByComparingTo(new BigDecimal("3.0"));

        // Demi-journée le 18 septembre -> 0.5 jour
        assertThat(engine.calculerNombreJours(venA, venA, true))
                .isEqualByComparingTo(new BigDecimal("0.5"));

        // Demi-journée sur 2 dates distinctes -> Erreur
        assertThatThrownBy(() -> engine.calculerNombreJours(venA, dimA, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Une demi-journée doit avoir une date de début et de fin identiques");

        // Dates obligatoires
        assertThatThrownBy(() -> engine.calculerNombreJours(null, dimA, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RG-CONGE-05 : Tests 1 à 5 obligatoires - Valorisation NBCONGE calendaire")
    void testCalculerNbJoursCongeValorises_Tests1a5() {
        // Test 1 : Vendredi -> Dimanche => NBCONGE = 3
        LocalDate ven1 = LocalDate.of(2026, 9, 18);
        LocalDate dim1 = LocalDate.of(2026, 9, 20);
        assertThat(engine.calculerNbJoursCongeValorises(ven1, dim1, false))
                .isEqualByComparingTo(new BigDecimal("3.0"));

        // Test 2 : Lundi -> Dimanche => NBCONGE = 7
        LocalDate lun2 = LocalDate.of(2026, 9, 14);
        LocalDate dim2 = LocalDate.of(2026, 9, 20);
        assertThat(engine.calculerNbJoursCongeValorises(lun2, dim2, false))
                .isEqualByComparingTo(new BigDecimal("7.0"));

        // Test 3 : Lundi -> Vendredi (mercredi férié compris) => NBCONGE = 5 (aucune réduction)
        LocalDate lun3 = LocalDate.of(2026, 9, 14);
        LocalDate ven3 = LocalDate.of(2026, 9, 18);
        assertThat(engine.calculerNbJoursCongeValorises(lun3, ven3, false))
                .isEqualByComparingTo(new BigDecimal("5.0"));

        // Test 4 : Vendredi -> Lundi inclus => NBCONGE = 4
        LocalDate ven4 = LocalDate.of(2026, 9, 18);
        LocalDate lun4 = LocalDate.of(2026, 9, 21);
        assertThat(engine.calculerNbJoursCongeValorises(ven4, lun4, false))
                .isEqualByComparingTo(new BigDecimal("4.0"));

        // Test 5 : Demi-journée => NBCONGE = 0.5
        assertThat(engine.calculerNbJoursCongeValorises(ven1, ven1, true))
                .isEqualByComparingTo(new BigDecimal("0.5"));
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
    @DisplayName("Acquisition mensuelle selon absences injustifiées : 0 absence -> 2.5j, 3 absences -> 2.25j")
    void testCalculerAcquisitionAvecAbsencesInjustifiees() {
        // 0 absence injustifiée : base = 30 -> 2.5 jours
        BigDecimal baseSansAbsence = new BigDecimal("30.0").subtract(BigDecimal.ZERO);
        assertThat(engine.calculerAcquisitionMensuelleProratisee(baseSansAbsence))
                .isEqualByComparingTo(new BigDecimal("2.50"));

        // 3 jours d'absence injustifiée : base = 27 -> 2.5 * 27 / 30 = 2.25 jours
        BigDecimal base3Absences = new BigDecimal("30.0").subtract(new BigDecimal("3.0"));
        assertThat(engine.calculerAcquisitionMensuelleProratisee(base3Absences))
                .isEqualByComparingTo(new BigDecimal("2.25"));
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
