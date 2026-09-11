package mg.galaxam.sirh.unit.conge;

import static org.assertj.core.api.Assertions.assertThat;

import mg.galaxam.sirh.model.conge.engine.PurgeFifoEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Tests - PurgeFifoEngine")
class PurgeFifoEngineTest {

    private PurgeFifoEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PurgeFifoEngine();
    }

    @Test
    @DisplayName("calculerJoursAExpier : Règle FIFO de consommation prioritaire")
    void testCalculerJoursAExpier() {
        // Cas 1 : 30j de droits anciens (> 36 mois), 10j consommés au total, 0 déjà expiré
        // Consommation sur anciens = 10j -> 20j non consommés à expirer
        assertThat(engine.calculerJoursAExpier(30.0, 10.0, 0.0)).isEqualTo(20.0);

        // Cas 2 : 30j de droits anciens, 35j consommés au total
        // Les 35j consommés absorbent intégralement les 30j anciens -> 0j à expirer
        assertThat(engine.calculerJoursAExpier(30.0, 35.0, 0.0)).isEqualTo(0.0);

        // Cas 3 : 30j anciens, 10j consommés, 5j déjà expirés l'année dernière
        // 20j non consommés - 5j déjà expirés = 15j à expirer
        assertThat(engine.calculerJoursAExpier(30.0, 10.0, 5.0)).isEqualTo(15.0);

        // Cas 4 : Aucun droit ancien (> 36 mois)
        assertThat(engine.calculerJoursAExpier(0.0, 10.0, 0.0)).isEqualTo(0.0);
    }
}
