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

        @Test @DisplayName("sortie null -> 0")
        void sortieNull() {
            assertEquals(0.0, engine.calculerHeuresNuit(null, LocalTime.of(22,0), LocalTime.of(5,0)));
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
