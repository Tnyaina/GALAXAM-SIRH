package mg.galaxam.sirh.model.pointage.engine;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PointageCalculateurEngineTest {

    private PointageCalculateurEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PointageCalculateurEngine();
    }

    @Test
    @DisplayName("Calcul des minutes de retard (0 tolérance)")
    void testCalculRetard() {
        LocalTime debut = LocalTime.of(8, 0);
        LocalTime plafond = LocalTime.of(7, 30);

        // Arrivée à 07h45 -> 0 retard
        assertEquals(0, engine.calculerMinutesRetard(LocalTime.of(7, 45), debut, plafond));

        // Arrivée à 08h00 -> 0 retard
        assertEquals(0, engine.calculerMinutesRetard(LocalTime.of(8, 0), debut, plafond));

        // Arrivée à 08h05 -> 5 min retard
        assertEquals(5, engine.calculerMinutesRetard(LocalTime.of(8, 5), debut, plafond));

        // Arrivée à 08h30 -> 30 min retard
        assertEquals(30, engine.calculerMinutesRetard(LocalTime.of(8, 30), debut, plafond));
    }

    @Test
    @DisplayName("Calcul des heures de nuit (> 22h00)")
    void testCalculHeuresNuit() {
        // Sortie à 17h00 -> 0h nuit
        assertEquals(0.0, engine.calculerHeuresNuit(LocalTime.of(17, 0), null));

        // Sortie à 22h00 -> 0h nuit
        assertEquals(0.0, engine.calculerHeuresNuit(LocalTime.of(22, 0), null));

        // Sortie à 22h30 -> 0.5h nuit
        assertEquals(0.5, engine.calculerHeuresNuit(LocalTime.of(22, 30), null));

        // Sortie à 23h00 -> 1.0h nuit
        assertEquals(1.0, engine.calculerHeuresNuit(LocalTime.of(23, 0), null));
    }

    @Test
    @DisplayName("Calcul des indemnités de repas et majorations tardives")
    void testCalculRepas() {
        // Déjeuner seul (2000 Ar)
        double r1 = engine.calculerTotalRepasJour(true, 2000.0, LocalTime.of(17, 0), 2000.0, 4000.0);
        assertEquals(2000.0, r1);

        // Déjeuner + sortie à 20h00 (>19h30 : +2000 Ar)
        double r2 = engine.calculerTotalRepasJour(true, 2000.0, LocalTime.of(20, 0), 2000.0, 4000.0);
        assertEquals(4000.0, r2);

        // Sans déjeuner à midi mais sortie à 21h30 (>21h00 : +4000 Ar)
        double r3 = engine.calculerTotalRepasJour(false, 2000.0, LocalTime.of(21, 30), 2000.0, 4000.0);
        assertEquals(4000.0, r3);
    }

    @Test
    @DisplayName("Bilan de semaine 40h avec compensation 1:1 des retards")
    void testCalculTronconHebdomadaire() {
        LocalDate debut = LocalDate.of(2026, 8, 3);
        // 5 jours ouvrés = 40h seuil, 45h effectuées, 30 min (0.5h) de retard
        var res = engine.calculerTroncon(debut, 5, 45.0, 30, false, 8.0, 8.0);

        assertEquals(40.0, res.seuilTroncon());
        assertEquals(5.0, res.hsBrutes());
        assertEquals(0.5, res.retardCompense(), "Le surplus de 5h compense intégralement les 30 min de retard.");
        assertEquals(0.0, res.deficitNet(), "Retard net = 0.");
        assertEquals(4.5, res.hsNettes());
        assertEquals(4.5, res.hs30(), "Les 4.5h nettes sont dans la tranche 1 (HS 30%).");
        assertEquals(0.0, res.hs50());
    }

    @Test
    @DisplayName("Règle Cadre : compensation des retards mais 0 HS payées")
    void testCalculTronconCadre() {
        LocalDate debut = LocalDate.of(2026, 8, 3);
        var res = engine.calculerTroncon(debut, 5, 45.0, 30, true, 8.0, 8.0);

        assertEquals(0.5, res.retardCompense());
        assertEquals(0.0, res.deficitNet());
        assertEquals(0.0, res.hsNettes(), "Un cadre a 0 HS nette payée.");
        assertEquals(0.0, res.hs30());
        assertEquals(0.0, res.hs50());
    }
}
