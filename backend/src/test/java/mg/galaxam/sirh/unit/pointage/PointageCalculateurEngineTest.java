package mg.galaxam.sirh.unit.pointage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import mg.galaxam.sirh.model.pointage.engine.PointageCalculateurEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Tests - PointageCalculateurEngine")
class PointageCalculateurEngineTest {

    private PointageCalculateurEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PointageCalculateurEngine();
    }

    @Test
    @DisplayName("calculerMinutesRetard : Arrivée à l'heure, en avance, ou en retard")
    void testCalculerMinutesRetard() {
        LocalTime debut = LocalTime.of(8, 0);

        // À l'heure exacte
        assertThat(engine.calculerMinutesRetard(LocalTime.of(8, 0), debut, null)).isZero();

        // En avance
        assertThat(engine.calculerMinutesRetard(LocalTime.of(7, 50), debut, null)).isZero();

        // En retard de 15 minutes (tolérance zéro)
        assertThat(engine.calculerMinutesRetard(LocalTime.of(8, 15), debut, null)).isEqualTo(15);

        // Nulls
        assertThat(engine.calculerMinutesRetard(null, debut, null)).isZero();
        assertThat(engine.calculerMinutesRetard(LocalTime.of(8, 15), null, null)).isZero();
    }

    @Test
    @DisplayName("ecreterArriveeAuPlafond : Arrivée matinale écrêtée au plafond toléré")
    void testEcreterArriveeAuPlafond() {
        LocalTime plafond = LocalTime.of(7, 30);

        // Arrivée avant le plafond (ex: 07:15) -> ramené à 07:30
        assertThat(engine.ecreterArriveeAuPlafond(LocalTime.of(7, 15), plafond))
                .isEqualTo(LocalTime.of(7, 30));

        // Arrivée après le plafond (ex: 07:45) -> reste 07:45
        assertThat(engine.ecreterArriveeAuPlafond(LocalTime.of(7, 45), plafond))
                .isEqualTo(LocalTime.of(7, 45));

        // Arrivée à l'heure du plafond (07:30)
        assertThat(engine.ecreterArriveeAuPlafond(LocalTime.of(7, 30), plafond))
                .isEqualTo(LocalTime.of(7, 30));

        // Valeur nulle
        assertThat(engine.ecreterArriveeAuPlafond(null, plafond)).isNull();
    }

    @Test
    @DisplayName("calculerHeuresNuit : Sortie de jour vs sortie nocturne")
    void testCalculerHeuresNuit() {
        LocalTime debutNuit = LocalTime.of(22, 0);
        LocalTime finNuit = LocalTime.of(5, 0);

        // Sortie à 17:00 (plein jour)
        assertThat(engine.calculerHeuresNuit(LocalTime.of(17, 0), debutNuit, finNuit)).isEqualTo(0.0);

        // Sortie à 23:30 (1h30 dans la tranche nocturne)
        assertThat(engine.calculerHeuresNuit(LocalTime.of(23, 30), debutNuit, finNuit)).isEqualTo(1.5);

        // Sortie après minuit à 02:00 (4h dans la tranche nocturne : 22h -> 02h)
        assertThat(engine.calculerHeuresNuit(LocalTime.of(2, 0), debutNuit, finNuit)).isEqualTo(4.0);

        // Valeur nulle
        assertThat(engine.calculerHeuresNuit(null, debutNuit, finNuit)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("calculerTotalRepasJour : Midi + Seuils sortie tardive (19h30, 21h00)")
    void testCalculerTotalRepasJour() {
        double montantMidi = 2000.0;
        LocalTime s1 = LocalTime.of(19, 30);
        double mnt1 = 2000.0;
        LocalTime s2 = LocalTime.of(21, 0);
        double mnt2 = 4000.0;

        // Pas de repas midi, sortie à 17:00
        assertThat(engine.calculerTotalRepasJour(false, montantMidi, LocalTime.of(17, 0), s1, mnt1, s2, mnt2))
                .isEqualTo(0.0);

        // Repas midi coché, sortie normale à 17:00
        assertThat(engine.calculerTotalRepasJour(true, montantMidi, LocalTime.of(17, 0), s1, mnt1, s2, mnt2))
                .isEqualTo(2000.0);

        // Repas midi coché + sortie tardive à 20:00 (dépasse 19h30 -> +2000 Ar)
        assertThat(engine.calculerTotalRepasJour(true, montantMidi, LocalTime.of(20, 0), s1, mnt1, s2, mnt2))
                .isEqualTo(4000.0);

        // Repas midi coché + sortie très tardive à 21:30 (dépasse 21h00 -> +4000 Ar)
        assertThat(engine.calculerTotalRepasJour(true, montantMidi, LocalTime.of(21, 30), s1, mnt1, s2, mnt2))
                .isEqualTo(6000.0);
    }

    @Test
    @DisplayName("calculerDeplacementJour : Règle Tamatave TMM (Déduction 1000 Ar si repas midi pris sur site)")
    void testCalculerDeplacementJour() {
        double tarifJourTmm = 2418.18; // 53 200 / 22
        double reductionRepas = 1000.0;

        // Jour entier sans repas sur site -> Plein tarif TMM
        assertThat(engine.calculerDeplacementJour(1.0, tarifJourTmm, false, reductionRepas))
                .isCloseTo(2418.18, org.assertj.core.data.Offset.offset(0.01));

        // Jour entier avec repas sur site -> Déduction de 1 000 Ar (ramené à 1 418.18 Ar ~ tarif TNR)
        assertThat(engine.calculerDeplacementJour(1.0, tarifJourTmm, true, reductionRepas))
                .isCloseTo(1418.18, org.assertj.core.data.Offset.offset(0.01));

        // Demi-journée sans repas
        assertThat(engine.calculerDeplacementJour(0.5, tarifJourTmm, false, reductionRepas))
                .isCloseTo(1209.09, org.assertj.core.data.Offset.offset(0.01));

        // Absence (fraction = 0)
        assertThat(engine.calculerDeplacementJour(0.0, tarifJourTmm, false, reductionRepas))
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("Modèle Réel Ohatra - RAKOTO (Site TMM Toamasina) : 20 jours travaillés, dont 7 journées continues avec repas")
    void testCalculDeplacementEtRepasOhatraRakoto() {
        int joursOuvrablesMois = 22;
        double refDeplacementTmm = 53200.0;
        double tarifJourTmm = refDeplacementTmm / joursOuvrablesMois; // 2418.1818...
        double reductionDeplactRepas = 1000.0; // REDUCTION_DEPLACT_REPAS_MIDI

        int totalJoursTravailles = 20;
        int joursContinusAvecRepas = 7;
        int joursNormauxDiscontinus = totalJoursTravailles - joursContinusAvecRepas; // 13

        double cumulDeplacement = 0.0;
        double cumulRepas = 0.0;

        // 13 jours normaux coupés (sans repas sur site, pas de réduction de transport)
        for (int i = 0; i < joursNormauxDiscontinus; i++) {
            cumulDeplacement += engine.calculerDeplacementJour(1.0, tarifJourTmm, false, reductionDeplactRepas);
        }

        // 7 jours continus (avec repas sur site 2000 Ar, déduction 1000 Ar sur le transport)
        for (int i = 0; i < joursContinusAvecRepas; i++) {
            cumulDeplacement += engine.calculerDeplacementJour(1.0, tarifJourTmm, true, reductionDeplactRepas);
            cumulRepas += 2000.0;
        }

        // Vérification formule Excel exacte: (20 * 2418.1818) - (7 * 1000) = 48363.64 - 7000 = 41363.64 Ar
        assertThat(Math.round(cumulDeplacement * 100.0) / 100.0).isEqualTo(41363.64);
        assertThat(cumulRepas).isEqualTo(14000.0);
    }

    @Test
    @DisplayName("Modèle Réel Ohatra - RAKETAKA (Site TNR Antananarivo) : 19 jours travaillés, jamais de réduction de transport")
    void testCalculDeplacementEtRepasOhatraRaketaka() {
        int joursOuvrablesMois = 22;
        double refDeplacementTnr = 31200.0;
        double tarifJourTnr = refDeplacementTnr / joursOuvrablesMois; // 1418.1818...
        double reductionDeplactRepas = 1000.0;

        int totalJoursTravailles = 19;
        int joursAvecRepas = 18;

        double cumulDeplacement = 0.0;
        for (int i = 0; i < totalJoursTravailles; i++) {
            // Sur site continu (TNR), la réduction transport n'est JAMAIS appliquée
            cumulDeplacement += engine.calculerDeplacementJour(1.0, tarifJourTnr, false, reductionDeplactRepas);
        }
        double cumulRepas = joursAvecRepas * 2000.0;

        // Formule Excel exacte: 19 * (31200 / 22) = 26945.45 Ar
        assertThat(Math.round(cumulDeplacement * 100.0) / 100.0).isEqualTo(26945.45);
        assertThat(cumulRepas).isEqualTo(36000.0);
    }

    @Test
    @DisplayName("calculerTroncon : Compensation hebdomadaire stricte  (HS compense le retard)")
    void testCalculerTronconCompensation() {
        LocalDate debut = LocalDate.of(2026, 9, 1);
        int nbJours = 5; // Seuil = 5 * 8h = 40h
        double seuilJour = 8.0;
        double seuilHsT1 = 8.0;

        // Cas 1 : 42h travaillées (2h HS), 60 min de retard (1h de retard)
        // Les 2h HS compensent 1h de retard -> Retard compensé = 1h, Déficit net = 0h, HS nettes = 1h
        PointageCalculateurEngine.BilanTronconHebdo b1 = engine.calculerTroncon(
                debut, nbJours, 42.0, 60.0, false, seuilJour, seuilHsT1);

        assertThat(b1.seuilTroncon()).isEqualTo(40.0);
        assertThat(b1.heuresRetard()).isEqualTo(1.0);
        assertThat(b1.hsBrutes()).isEqualTo(2.0);
        assertThat(b1.retardCompense()).isEqualTo(1.0);
        assertThat(b1.deficitNet()).isEqualTo(0.0);
        assertThat(b1.hsNettes()).isEqualTo(1.0);
        assertThat(b1.hs30()).isEqualTo(1.0);
        assertThat(b1.hs50()).isEqualTo(0.0);

        // Cas 2 : 38h travaillées, 120 min de retard (2h de retard)
        // 0 HS brute -> Pas de compensation -> Déficit net = 2h à déduire
        PointageCalculateurEngine.BilanTronconHebdo b2 = engine.calculerTroncon(
                debut, nbJours, 38.0, 120.0, false, seuilJour, seuilHsT1);

        assertThat(b2.hsBrutes()).isEqualTo(0.0);
        assertThat(b2.retardCompense()).isEqualTo(0.0);
        assertThat(b2.deficitNet()).isEqualTo(2.0);
        assertThat(b2.hsNettes()).isEqualTo(0.0);

        // Cas 3 : Cadre autonome -> 50h travaillées (10h HS brutes), 60 min de retard (1h retard)
        // Pour les cadres : compensation active (retardCompense = 1h), mais 0 HS payée (hsNettes = 0.0)
        PointageCalculateurEngine.BilanTronconHebdo b3 = engine.calculerTroncon(
                debut, nbJours, 50.0, 60.0, true, seuilJour, seuilHsT1);

        assertThat(b3.retardCompense()).isEqualTo(1.0);
        assertThat(b3.deficitNet()).isEqualTo(0.0);
        assertThat(b3.hsNettes()).isEqualTo(0.0);
        assertThat(b3.hs30()).isEqualTo(0.0);
        assertThat(b3.hs50()).isEqualTo(0.0);

        // Cas 4 : Ventilation HS (jusqu'à 8h à +30%, reliquat à +50%)
        // 51h travaillées (11h HS brutes), 0 retard -> 8h à 30%, 3h à 50%
        PointageCalculateurEngine.BilanTronconHebdo b4 = engine.calculerTroncon(
                debut, nbJours, 51.0, 0.0, false, seuilJour, seuilHsT1);

        assertThat(b4.hsNettes()).isEqualTo(11.0);
        assertThat(b4.hs30()).isEqualTo(8.0);
        assertThat(b4.hs50()).isEqualTo(3.0);
    }
}
