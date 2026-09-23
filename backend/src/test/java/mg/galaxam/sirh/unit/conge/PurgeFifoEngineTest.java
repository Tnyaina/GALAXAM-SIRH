package mg.galaxam.sirh.unit.conge;

import static org.assertj.core.api.Assertions.assertThat;

import mg.galaxam.sirh.conge.domain.PurgeFifoEngine;
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

    @Test
    @DisplayName("RG-CONGE-06 : Priorité 1 Stock historique (10j) puis Priorité 2 Droits datés (2x2.5j), Prise 12j -> Reste 3j")
    void testRgConge06_ExempleCanoniquePrioriteHistoriquePuisFifo() {
        // SOLDE_INITIAL = 10j, ACQ 08/2026 = 2.5j, ACQ 09/2026 = 2.5j, PRISE = 12j
        java.util.List<PurgeFifoEngine.DroitDate> droits = java.util.List.of(
                new PurgeFifoEngine.DroitDate(1, java.time.LocalDate.of(2026, 8, 1), 2.5),
                new PurgeFifoEngine.DroitDate(2, java.time.LocalDate.of(2026, 9, 1), 2.5)
        );

        PurgeFifoEngine.ImputationResult res = engine.calculerImputation(10.0, droits, 12.0);

        // 1. Stock historique intégralement consommé (10j -> 0j)
        assertThat(res.getStockHistoriqueRestant()).isEqualTo(0.0);
        assertThat(res.getConsommationImputeeSurHistorique()).isEqualTo(10.0);

        // 2. Consommation restante (2j) imputée sur la première acquisition datée (2.5j -> 0.5j)
        assertThat(res.getConsommationImputeeSurDroitsDates()).isEqualTo(2.0);
        assertThat(res.getDetailsDroitsDates().get(0).getNbJoursRestants()).isEqualTo(0.5);

        // 3. Deuxième acquisition intacte (2.5j -> 2.5j)
        assertThat(res.getDetailsDroitsDates().get(1).getNbJoursRestants()).isEqualTo(2.5);

        // 4. Solde total restant = 0 + 0.5 + 2.5 = 3.0j
        assertThat(res.getSoldeTotalDisponible()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("RG-CONGE-06 : Stock historique (10j) + Acq (2.5j), Prise 3j -> Historique restant 7j, Acq restant 2.5j")
    void testRgConge06_HistoriqueProtegeAcquisitionDatéeNonConsommee() {
        java.util.List<PurgeFifoEngine.DroitDate> droits = java.util.List.of(
                new PurgeFifoEngine.DroitDate(1, java.time.LocalDate.of(2026, 8, 1), 2.5)
        );

        PurgeFifoEngine.ImputationResult res = engine.calculerImputation(10.0, droits, 3.0);

        assertThat(res.getStockHistoriqueRestant()).isEqualTo(7.0);
        assertThat(res.getConsommationImputeeSurHistorique()).isEqualTo(3.0);
        assertThat(res.getConsommationImputeeSurDroitsDates()).isEqualTo(0.0);
        assertThat(res.getDetailsDroitsDates().get(0).getNbJoursRestants()).isEqualTo(2.5);
        assertThat(res.getSoldeTotalDisponible()).isEqualTo(9.5);

        // À l'expiration (36 mois plus tard) : comme l'acquisition de 2.5j n'a pas été consommée, elle expire !
        java.time.LocalDate seuil = java.time.LocalDate.of(2029, 9, 1);
        double aExpirer = engine.calculerJoursAExpierFifo(res, seuil, 0.0);
        assertThat(aExpirer).isEqualTo(2.5);
    }

    @Test
    @DisplayName("RG-CONGE-06 : Test complexe (4j hist + 3x2.5j datés), Prise 7j -> JAN=0, FEB=2.0, MAR=2.5")
    void testRgConge06_TestComplexeMultiAcquisitions() {
        java.util.List<PurgeFifoEngine.DroitDate> droits = java.util.List.of(
                new PurgeFifoEngine.DroitDate(1, java.time.LocalDate.of(2026, 1, 1), 2.5),
                new PurgeFifoEngine.DroitDate(2, java.time.LocalDate.of(2026, 2, 1), 2.5),
                new PurgeFifoEngine.DroitDate(3, java.time.LocalDate.of(2026, 3, 1), 2.5)
        );

        PurgeFifoEngine.ImputationResult res = engine.calculerImputation(4.0, droits, 7.0);

        // Historique 4j -> 0j (4j consommés)
        assertThat(res.getStockHistoriqueRestant()).isEqualTo(0.0);
        // JAN 2.5j -> 0j (2.5j consommés)
        assertThat(res.getDetailsDroitsDates().get(0).getNbJoursRestants()).isEqualTo(0.0);
        // FEB 2.5j -> 2.0j (0.5j consommé)
        assertThat(res.getDetailsDroitsDates().get(1).getNbJoursRestants()).isEqualTo(2.0);
        // MAR 2.5j -> 2.5j (0j consommé)
        assertThat(res.getDetailsDroitsDates().get(2).getNbJoursRestants()).isEqualTo(2.5);
        // Total = 4.5j
        assertThat(res.getSoldeTotalDisponible()).isEqualTo(4.5);

        // Expiration au seuil 15/01/2026 (seul JAN < seuil) : JAN étant consommé (0j restant), 0j n'expire !
        double expJan = engine.calculerJoursAExpierFifo(res, java.time.LocalDate.of(2026, 1, 15), 0.0);
        assertThat(expJan).isEqualTo(0.0);

        // Expiration au seuil 15/02/2026 (FEB < seuil) : seuls les 2.0j restants de FEB expirent !
        double expFeb = engine.calculerJoursAExpierFifo(res, java.time.LocalDate.of(2026, 2, 15), 0.0);
        assertThat(expFeb).isEqualTo(2.0);
    }

    @Test
    @DisplayName("RG-CONGE-06 : Solde initial négatif de migration (-2j) compensé par acquisition (+2.5j) -> Solde +0.5j")
    void testRgConge06_SoldeInitialNegatifCompenseParAcquisition() {
        java.util.List<PurgeFifoEngine.DroitDate> droits = java.util.List.of(
                new PurgeFifoEngine.DroitDate(1, java.time.LocalDate.of(2026, 9, 1), 2.5)
        );

        PurgeFifoEngine.ImputationResult res = engine.calculerImputation(-2.0, droits, 0.0);

        // L'acquisition a compensé la dette de 2j, il reste 0.5j de disponible
        assertThat(res.getDetailsDroitsDates().get(0).getNbJoursRestants()).isEqualTo(0.5);
        assertThat(res.getSoldeTotalDisponible()).isEqualTo(0.5);
    }
}
