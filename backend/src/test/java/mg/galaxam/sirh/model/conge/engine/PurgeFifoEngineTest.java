package mg.galaxam.sirh.model.conge.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PurgeFifoEngineTest {

    private PurgeFifoEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PurgeFifoEngine();
    }

    @Test
    @DisplayName("Calcul de purge FIFO : Ancien solde non consommé et non purgé")
    void testCalculPurgeNouveauCas() {
        double acquisAnciens = 15.0;
        double totalPris = 0.0;
        double totalDejaExpire = 0.0;

        double aPurger = engine.calculerJoursAExpier(acquisAnciens, totalPris, totalDejaExpire);
        assertEquals(15.0, aPurger, "Les 15 jours anciens non consommés doivent être purgés.");
    }

    @Test
    @DisplayName("Calcul de purge FIFO : Ancien solde partiellement consommé")
    void testCalculPurgePartiellementConsomme() {
        double acquisAnciens = 15.0;
        double totalPris = 5.0; // 5 jours pris sur les 15 anciens
        double totalDejaExpire = 0.0;

        double aPurger = engine.calculerJoursAExpier(acquisAnciens, totalPris, totalDejaExpire);
        assertEquals(10.0, aPurger, "Seul le reliquat non consommé de 10 jours doit être purgé.");
    }

    @Test
    @DisplayName("Calcul de purge FIFO : Idempotence (2ème exécution consécutive = 0)")
    void testCalculPurgeIdempotence() {
        double acquisAnciens = 15.0;
        double totalPris = 0.0;
        double totalDejaExpire = 15.0; // Déjà purgé au run précédent

        double aPurger = engine.calculerJoursAExpier(acquisAnciens, totalPris, totalDejaExpire);
        assertEquals(0.0, aPurger, "Aucun jour ne doit être purgé lors d'une 2ème exécution.");
    }
}
