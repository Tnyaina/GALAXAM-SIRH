package mg.galaxam.sirh.pointage.engine;

import mg.galaxam.sirh.model.pointage.engine.PointageCalculateurEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PointageCalculateurEngine")
class PointageCalculateurEngineTest {

    private PointageCalculateurEngine engine;

    @BeforeEach
    void setUp() { engine = new PointageCalculateurEngine(); }

    @Nested @DisplayName("calculerMinutesRetard")
    class CalculerMinutesRetard {

        @Test @DisplayName("Arrivee exacte = 0 retard")
        void arriveeExacte() {
            assertEquals(0, engine.calculerMinutesRetard(LocalTime.of(8,0), LocalTime.of(8,0), null));
        }

        @Test @DisplayName("Arrivee en avance = 0 retard")
        void arriveeEnAvance() {
            assertEquals(0, engine.calculerMinutesRetard(LocalTime.of(7,50), LocalTime.of(8,0), null));
        }

        @Test @DisplayName("Arrivee 15 min en retard = 15")
        void retard15min() {
            assertEquals(15, engine.calculerMinutesRetard(LocalTime.of(8,15), LocalTime.of(8,0), null));
        }

        @Test @DisplayName("Arrivee 1h en retard = 60")
        void retard60min() {
            assertEquals(60, engine.calculerMinutesRetard(LocalTime.of(9,0), LocalTime.of(8,0), null));
        }

        @Test @DisplayName("arrivee null -> 0")
        void arriveeNull() {
            assertEquals(0, engine.calculerMinutesRetard(null, LocalTime.of(8,0), null));
        }

        @Test @DisplayName("debutTheorique null -> 0")
        void debutNull() {
            assertEquals(0, engine.calculerMinutesRetard(LocalTime.of(8,30), null, null));
        }
    }

    @Nested @DisplayName("calculerHeuresNuit")
    class CalculerHeuresNuit {

        @Test @DisplayName("Sortie avant 22h -> 0")
        void avant22h() {
            assertEquals(0.0, engine.calculerHeuresNuit(LocalTime.of(21,0), LocalTime.of(22,0), LocalTime.of(5,0)));
        }

        @Test @DisplayName("Sortie a 22h -> 0 (seuil exact)")
        void a22h() {
            assertEquals(0.0, engine.calculerHeuresNuit(LocalTime.of(22,0), LocalTime.of(22,0), LocalTime.of(5,0)));
        }

        @Test @DisplayName("Sortie a 23h -> 1.0 heure de nuit")
        void a23h() {
            assertEquals(1.0, engine.calculerHeuresNuit(LocalTime.of(23,0), LocalTime.of(22,0), LocalTime.of(5,0)));
        }

        @Test @DisplayName("Sortie a 22h30 -> 0.5 heure")
        void a22h30() {
            assertEquals(0.5, engine.calculerHeuresNuit(LocalTime.of(22,30), LocalTime.of(22,0), LocalTime.of(5,0)));
        }

        @Test @DisplayName("Sortie apres minuit (01h30) traversant minuit -> 3.5h")
        void apresMinuit() {
            // 22h -> 24h = 2h, 00h -> 01h30 = 1.5h => 3.5h
            assertEquals(3.5, engine.calculerHeuresNuit(LocalTime.of(1, 30), LocalTime.of(22, 0), LocalTime.of(5, 0)));
        }

        @Test @DisplayName("Sortie a 04h00 du matin -> 6.0h de nuit")
        void a4hMatin() {
            // 22h -> 24h = 2h, 00h -> 04h00 = 4h => 6.0h
            assertEquals(6.0, engine.calculerHeuresNuit(LocalTime.of(4, 0), LocalTime.of(22, 0), LocalTime.of(5, 0)));
        }

        @Test @DisplayName("Sortie a 06h00 (apres 05h00) -> 0h")
        void apresFinNuit() {
            assertEquals(0.0, engine.calculerHeuresNuit(LocalTime.of(6, 0), LocalTime.of(22, 0), LocalTime.of(5, 0)));
        }

        @Test @DisplayName("sortie null -> 0")
        void sortieNull() {
            assertEquals(0.0, engine.calculerHeuresNuit(null, LocalTime.of(22,0), LocalTime.of(5,0)));
        }
    }

    @Nested @DisplayName("calculerTotalRepasJour (Indemnites & Majorations)")
    class CalculerTotalRepasJour {

        private final double MNT_MIDI = 2000.0;
        private final double MNT_REPAS_1 = 2000.0;
        private final double MNT_REPAS_2 = 4000.0;
        private final LocalTime SEUIL_1 = LocalTime.of(19, 30);
        private final LocalTime SEUIL_2 = LocalTime.of(21, 0);

        @Test @DisplayName("Aucun repas coche, pas de sortie soir -> 0 Ar")
        void aucunRepas() {
            double total = engine.calculerTotalRepasJour(false, MNT_MIDI, null, SEUIL_1, MNT_REPAS_1, SEUIL_2, MNT_REPAS_2);
            assertEquals(0.0, total, 0.01);
        }

        @Test @DisplayName("Repas midi uniquement (sortie normale a 17h00) -> 2000 Ar")
        void repasMidiSeul() {
            double total = engine.calculerTotalRepasJour(true, MNT_MIDI, LocalTime.of(17, 0), SEUIL_1, MNT_REPAS_1, SEUIL_2, MNT_REPAS_2);
            assertEquals(2000.0, total, 0.01);
        }

        @Test @DisplayName("Repas midi + sortie a 20h00 (depasse 19h30, mais avant 21h00) -> 2000 + 2000 = 4000 Ar")
        void repasMidiPlusSeuil1() {
            double total = engine.calculerTotalRepasJour(true, MNT_MIDI, LocalTime.of(20, 0), SEUIL_1, MNT_REPAS_1, SEUIL_2, MNT_REPAS_2);
            assertEquals(4000.0, total, 0.01);
        }

        @Test @DisplayName("Repas midi + sortie a 21h30 (depasse 21h00) -> 2000 + 4000 = 6000 Ar (Double panier)")
        void repasMidiPlusSeuil2() {
            double total = engine.calculerTotalRepasJour(true, MNT_MIDI, LocalTime.of(21, 30), SEUIL_1, MNT_REPAS_1, SEUIL_2, MNT_REPAS_2);
            assertEquals(6000.0, total, 0.01);
        }

        @Test @DisplayName("Repas midi non coche mais sortie tardive a 22h00 -> 0 + 4000 = 4000 Ar")
        void pasDeMidiMaisSortieTardive() {
            double total = engine.calculerTotalRepasJour(false, MNT_MIDI, LocalTime.of(22, 0), SEUIL_1, MNT_REPAS_1, SEUIL_2, MNT_REPAS_2);
            assertEquals(4000.0, total, 0.01);
        }

        @Test @DisplayName("Seuils nulls -> utilise valeurs par defaut 19h30 et 21h00 sans planter")
        void seuilsNullsFallback() {
            double total = engine.calculerTotalRepasJour(true, MNT_MIDI, LocalTime.of(21, 15), null, MNT_REPAS_1, null, MNT_REPAS_2);
            assertEquals(6000.0, total, 0.01);
        }
    }

    @Nested @DisplayName("calculerDeplacementJour (Indemnite Transport)")
    class CalculerDeplacementJour {

        private final double TARIF_TRAJET = 1500.0;

        @Test @DisplayName("Aucun trajet effectue -> 0 Ar")
        void zeroTrajet() {
            double total = engine.calculerDeplacementJour(false, false, TARIF_TRAJET, false, 0.0);
            assertEquals(0.0, total, 0.01);
        }

        @Test @DisplayName("Trajet matin seul -> 1500 Ar")
        void trajetMatinSeul() {
            double total = engine.calculerDeplacementJour(true, false, TARIF_TRAJET, false, 0.0);
            assertEquals(1500.0, total, 0.01);
        }

        @Test @DisplayName("Trajet soir seul -> 1500 Ar")
        void trajetSoirSeul() {
            double total = engine.calculerDeplacementJour(false, true, TARIF_TRAJET, false, 0.0);
            assertEquals(1500.0, total, 0.01);
        }

        @Test @DisplayName("Aller-retour complet (matin + soir) -> 3000 Ar")
        void allerRetourComplet() {
            double total = engine.calculerDeplacementJour(true, true, TARIF_TRAJET, false, 0.0);
            assertEquals(3000.0, total, 0.01);
        }

        @Test @DisplayName("Aller-retour avec repas sur site et reduction transport -> 3000 - 500 = 2500 Ar")
        void allerRetourAvecReductionRepasSurSite() {
            double total = engine.calculerDeplacementJour(true, true, TARIF_TRAJET, true, 500.0);
            assertEquals(2500.0, total, 0.01);
        }

        @Test @DisplayName("Reduction superieure a l'indemnite -> plafonnee a 0 Ar (pas de montant negatif)")
        void reductionSuperieureBrut() {
            double total = engine.calculerDeplacementJour(true, false, TARIF_TRAJET, true, 2000.0);
            assertEquals(0.0, total, 0.01);
        }
    }

    @Nested @DisplayName("calculerTroncon (40h semaine)")
    class CalculerTroncon {

        @Test @DisplayName("40h exactes, 0 retard, non cadre -> 0 HS")
        void exactement40h() {
            var bilan = engine.calculerTroncon(LocalDate.of(2024,1,1), 5, 40.0, 0, false, 8.0, 8.0);
            assertEquals(0.0, bilan.hsNettes(), 0.01);
            assertEquals(0.0, bilan.deficitNet(), 0.01);
        }

        @Test @DisplayName("42h, 0 retard -> 2h HS nettes")
        void deuxHeureHS() {
            var bilan = engine.calculerTroncon(LocalDate.of(2024,1,1), 5, 42.0, 0, false, 8.0, 8.0);
            assertEquals(2.0, bilan.hsNettes(), 0.01);
        }

        @Test @DisplayName("42h avec 60 min retard -> HS compensee par retard")
        void compensationRetard() {
            var bilan = engine.calculerTroncon(LocalDate.of(2024,1,1), 5, 42.0, 60, false, 8.0, 8.0);
            // 2h HS brutes - 1h retard compense = 1h HS nettes
            assertEquals(1.0, bilan.hsNettes(), 0.01);
            assertEquals(0.0, bilan.deficitNet(), 0.01);
        }

        @Test @DisplayName("38h, 0 retard -> 0 HS, 0 deficit (pas de retard)")
        void moinsde40h() {
            var bilan = engine.calculerTroncon(LocalDate.of(2024,1,1), 5, 38.0, 0, false, 8.0, 8.0);
            assertEquals(0.0, bilan.hsNettes(), 0.01);
            assertEquals(0.0, bilan.deficitNet(), 0.01);
        }

        @Test @DisplayName("Cadre : HS nettes toujours 0")
        void cadreZeroHS() {
            var bilan = engine.calculerTroncon(LocalDate.of(2024,1,1), 5, 45.0, 0, true, 8.0, 8.0);
            assertEquals(0.0, bilan.hsNettes(), 0.01);
        }

        @Test @DisplayName("Ventilation HS : < seuil = HS30, > seuil = HS50")
        void ventilationHS() {
            // Seuil tranche1 = 8h -> 48h total sur 5j (seuil=40h), 8h HS
            // hs30 = min(8, 8) = 8, hs50 = 0
            var bilan = engine.calculerTroncon(LocalDate.of(2024,1,1), 5, 48.0, 0, false, 8.0, 8.0);
            assertEquals(8.0, bilan.hsNettes(), 0.01);
            assertEquals(8.0, bilan.hs30(), 0.01);
            assertEquals(0.0, bilan.hs50(), 0.01);
        }

        @Test @DisplayName("Plus de HS que seuil -> HS50 > 0")
        void hs50Positif() {
            // seuil troncon = 5*8=40h, hs brutes = 50-40=10h, seuil tranche1=8h
            // hs30=8, hs50=2
            var bilan = engine.calculerTroncon(LocalDate.of(2024,1,1), 5, 50.0, 0, false, 8.0, 8.0);
            assertEquals(8.0, bilan.hs30(), 0.01);
            assertEquals(2.0, bilan.hs50(), 0.01);
        }
    }
}
