package mg.galaxam.sirh.conge.engine;

import mg.galaxam.sirh.model.conge.engine.PurgeFifoEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PurgeFifoEngine (Art. 85 — prescription 36 mois)")
class PurgeFifoEngineTest {

    private PurgeFifoEngine engine;

    @BeforeEach
    void setUp() { engine = new PurgeFifoEngine(); }

    @Test @DisplayName("Aucun ancien acquis -> 0 a expirer")
    void aucunAncienAcquis() {
        assertEquals(0.0, engine.calculerJoursAExpier(0.0, 5.0, 0.0), 0.001);
    }

    @Test @DisplayName("Anciens droits entierement consommes -> 0 a expirer")
    void anciensEntierementConsommes() {
        // 10j anciens, 15j pris -> les anciens sont consommes
        assertEquals(0.0, engine.calculerJoursAExpier(10.0, 15.0, 0.0), 0.001);
    }

    @Test @DisplayName("Anciens non consommes, rien deja expire -> tout a expirer")
    void toutAExpirer() {
        // 10j anciens, 0j pris, 0j deja expire -> 10j a expirer
        assertEquals(10.0, engine.calculerJoursAExpier(10.0, 0.0, 0.0), 0.001);
    }

    @Test @DisplayName("Anciens partiellement consommes -> reste a expirer")
    void partiellementConsommes() {
        // 10j anciens, 4j pris (sur les anciens) -> 6j non consommes, 0 deja expire -> 6j a expirer
        assertEquals(6.0, engine.calculerJoursAExpier(10.0, 4.0, 0.0), 0.001);
    }

    @Test @DisplayName("Deja partiellement expire -> deduire")
    void dejaPartiellementExpire() {
        // 10j anciens, 0j pris, 3j deja expires -> 7j a expirer
        assertEquals(7.0, engine.calculerJoursAExpier(10.0, 0.0, 3.0), 0.001);
    }

    @Test @DisplayName("Tout deja expire -> 0 a expirer")
    void toutDejaExpire() {
        assertEquals(0.0, engine.calculerJoursAExpier(10.0, 0.0, 10.0), 0.001);
    }

    @Test @DisplayName("Resultat arrondi a 2 decimales")
    void arrondissage() {
        // 7j anciens, 3.33j pris -> 3.67j non consommes = 3.67
        double result = engine.calculerJoursAExpier(7.0, 3.33, 0.0);
        assertEquals(3.67, result, 0.001);
    }

    @Test @DisplayName("Valeurs negatives protegees -> 0")
    void valeursNegatives() {
        assertEquals(0.0, engine.calculerJoursAExpier(-5.0, 0.0, 0.0), 0.001);
    }
}
