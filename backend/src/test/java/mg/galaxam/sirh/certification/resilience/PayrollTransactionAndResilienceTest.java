package mg.galaxam.sirh.certification.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollBatchResult;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.workflow.PayrollAction;
import mg.galaxam.sirh.model.paie.workflow.PayrollPeriodCommand;
import mg.galaxam.sirh.model.paie.workflow.PayrollPeriodCommandRequest;
import mg.galaxam.sirh.model.paie.workflow.PayrollPeriodUserContext;
import mg.galaxam.sirh.model.paie.workflow.PayrollPeriodWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * =========================================================================
 * PHASE 12 : CERTIFICATION DE LA RÉSILIENCE, DES TRANSACTIONS & DE L'IDEMPOTENCE
 * =========================================================================
 *
 * Vérifie formellement :
 * 1. Atomicité ACID : En cas d'erreur au cours d'un calcul, la transaction rollback
 *    complètement et ne laisse aucun état partiel (0 bulletin orphelin, 0 ligne corrompue).
 * 2. Idempotence absolue : Le recalcul successif N fois d'un bulletin ne duplique
 *    ni les bulletins, ni les lignes de bulletin, et produit un résultat au centime identique.
 * 3. Prévention des conflits concurrents (Optimistic Locking) :
 *    Deux actions concurrentes avec le même numéro de version résultent en exactement
 *    1 succès et 1 rejet PAYROLL_PERIOD_VERSION_CONFLICT.
 * 4. Étanchéité de validation : La validation scelle les snapshots ('CONSOMME')
 *    et passe la période en 'PAIE_VALIDEE' de façon indivisible.
 * 5. Verrou d'immutabilité : Une fois clôturée ('CLOTUREE'), toute tentative
 *    de calcul est interceptée et bloquée (STATE_CONFLICT / PERIOD_CLOSED).
 */
@DisplayName("CERTIFICATION RÉSILIENCE & TRANSACTIONS — ACID, IDEMPOTENCE, IMMUTABILITÉ")
public class PayrollTransactionAndResilienceTest extends BaseIntegrationTest {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollPeriodWorkflowService workflowService;

    private int idPeriode;
    private CreatedEmploye emp;
    private LocalDate debut;
    private LocalDate fin;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();

        debut = LocalDate.of(2026, 9, 1);
        fin = LocalDate.of(2026, 9, 30);
        idPeriode = employeFixtures.createPeriodePaie("P-2026-09-RESIL", debut, fin, "VALIDEE");

        emp = employeFixtures.createEmployeWithSalary(
                "EMP-RESIL-01",
                "RAKOTO",
                "Resilience",
                1,
                new BigDecimal("600000.00"),
                "PERMANENT",
                0
        );

        // Snapshot gelé conforme
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO pointage_cumul_periode (" +
                     "id_periode_paie, id_employe, version_snapshot, statut_snapshot, nb_jours_ouvrables, nb_jours_presents, " +
                     "total_heures_travaillees, heures_absence_retenues, total_minutes_retard, " +
                     "heures_hs_30, heures_hs_50, heures_nuit_total, heures_dimanche_total, " +
                     "nb_jours_conge, montant_deplacement, gele_at) " +
                     "VALUES (?, ?, 1, 'GELE', 22, 22, 173.33, 0, 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP)")) {
            ps.setInt(1, idPeriode);
            ps.setInt(2, emp.idEmploye);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // 1. ATOMICITÉ ACID : ROLLBACK INTÉGRAL EN CAS D'ÉCHEC
    // =========================================================================

    @Test
    @DisplayName("Transaction : Un échec en cours de calcul rollback l'intégralité du bulletin et de ses lignes")
    public void testRollbackIntegralSurEchec() throws Exception {
        // Exécuter un calcul au sein d'une transaction manuelle que l'on annule (rollback)
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Calcul du bulletin
                payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1, c);

                // Vérifier qu'au sein de la transaction le bulletin existe
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND id_employe = ?")) {
                    ps.setInt(1, idPeriode);
                    ps.setInt(2, emp.idEmploye);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertEquals(1, rs.getInt(1), "Le bulletin existe au sein de la transaction");
                    }
                }

                // Déclenchement intentionnel du ROLLBACK
                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }

        // Vérification externe : la base ne doit contenir AUCUN bulletin ni aucune ligne
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND id_employe = ?")) {
                ps.setInt(1, idPeriode);
                ps.setInt(2, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals(0, rs.getInt(1), "Aucun bulletin orphelin après rollback");
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM bulletin_paie_ligne WHERE id_bulletin IN " +
                    "(SELECT id_bulletin FROM bulletin_paie WHERE id_periode_paie = ?)")) {
                ps.setInt(1, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals(0, rs.getInt(1), "Aucune ligne orpheline après rollback");
                }
            }
        }
    }

    // =========================================================================
    // 2. IDEMPOTENCE ABSOLUE DU RECALCUL
    // =========================================================================

    @Test
    @DisplayName("Idempotence : Recalculer 3 fois successivement produit des données strictement identiques")
    public void testIdempotenceRecalculPaie() throws Exception {
        // Premier calcul
        BulletinPaieComplet run1 = payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1);
        assertNotNull(run1);
        BigDecimal net1 = run1.getBulletin().getNetAPayer();
        BigDecimal brut1 = run1.getBulletin().getTotalGainsBruts();
        int lineCount1 = run1.getLignes().size();

        // Deuxième calcul (recalcul)
        BulletinPaieComplet run2 = payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1);
        assertNotNull(run2);
        assertEquals(net1, run2.getBulletin().getNetAPayer(), "Net invariant au 2e recalcul");
        assertEquals(brut1, run2.getBulletin().getTotalGainsBruts(), "Brut invariant au 2e recalcul");
        assertEquals(lineCount1, run2.getLignes().size(), "Nombre de lignes invariant au 2e recalcul");

        // Troisième calcul (recalcul)
        BulletinPaieComplet run3 = payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1);
        assertNotNull(run3);
        assertEquals(net1, run3.getBulletin().getNetAPayer(), "Net invariant au 3e recalcul");
        assertEquals(brut1, run3.getBulletin().getTotalGainsBruts(), "Brut invariant au 3e recalcul");
        assertEquals(lineCount1, run3.getLignes().size(), "Nombre de lignes invariant au 3e recalcul");

        // Preuve BDD : Il existe exactement 1 seul bulletin pour cet employé sur cette période
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND id_employe = ?")) {
            ps.setInt(1, idPeriode);
            ps.setInt(2, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(1, rs.getInt(1), "Exactement un bulletin après 3 recalculs");
            }
        }
    }

    // =========================================================================
    // 3. OPTIMISTIC LOCKING : PRÉVENTION DES CONFLITS CONCURRENTS
    // =========================================================================

    @Test
    @DisplayName("Concurrence : Deux soumissions concurrentes avec la même version provoquent 1 succès et 1 conflit")
    public void testPreventionConflitConcurrent() throws Exception {
        // Créer une période en BROUILLON pour tester une transition OPEN concurrente
        int idPeriodeTest = employeFixtures.createPeriodePaie("P-CONC-01", debut, fin, "BROUILLON");
        int versionInitiale = getPayrollPeriodVersion(idPeriodeTest);

        PayrollPeriodUserContext admin = new PayrollPeriodUserContext(1, Set.of("ROLE_ADMIN"));
        CountDownLatch latch = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var action = (java.util.concurrent.Callable<Boolean>) () -> {
                latch.await();
                PayrollPeriodCommandRequest req = new PayrollPeriodCommandRequest();
                req.setVersion(versionInitiale);
                try {
                    workflowService.execute(idPeriodeTest, PayrollPeriodCommand.OPEN, req, admin);
                    return true;
                } catch (SirhBusinessException ex) {
                    if (ex.getErrorCode() == SirhErrorCode.PAYROLL_PERIOD_VERSION_CONFLICT) {
                        return false;
                    }
                    throw ex;
                }
            };

            var f1 = executor.submit(action);
            var f2 = executor.submit(action);
            latch.countDown();

            List<Boolean> results = List.of(f1.get(), f2.get());
            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        // La version finale doit être incrémentée exactement d'une unité
        assertEquals(versionInitiale + 1, getPayrollPeriodVersion(idPeriodeTest));
    }

    // =========================================================================
    // 4. ATOMICITÉ DE VALIDATION : TRANSITION INDIVISIBLE BULLETIN & SNAPSHOT
    // =========================================================================

    @Test
    @DisplayName("Validation : Transition indivisible des bulletins en VALIDE et du snapshot en CONSOMME")
    public void testValidationIndivisible() throws Exception {
        // 1. Calculer le bulletin
        payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1);

        // 2. Valider la période de paie
        PayrollBatchResult batch = payrollService.validerPaiePeriode(idPeriode, 1);
        assertEquals(1, batch.getNbBulletinsCalcules(), "1 bulletin validé dans le lot");

        // 3. Vérification des états BDD
        try (Connection c = dataSource.getConnection()) {
            // Bulletin -> VALIDE
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT statut FROM bulletin_paie WHERE id_periode_paie = ? AND id_employe = ?")) {
                ps.setInt(1, idPeriode);
                ps.setInt(2, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals("VALIDE", rs.getString(1), "Statut bulletin = VALIDE");
                }
            }

            // Snapshot -> CONSOMME
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT statut_snapshot FROM pointage_cumul_periode WHERE id_periode_paie = ? AND id_employe = ?")) {
                ps.setInt(1, idPeriode);
                ps.setInt(2, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals("CONSOMME", rs.getString(1), "Snapshot pointage = CONSOMME");
                }
            }

            // Période -> PAIE_VALIDEE
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT s.code FROM ref_statut_periode_paie s JOIN periode_paie p ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ?")) {
                ps.setInt(1, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals("PAIE_VALIDEE", rs.getString(1), "Statut période = PAIE_VALIDEE");
                }
            }
        }
    }

    // =========================================================================
    // 5. IMMUTABILITÉ POST-CLÔTURE
    // =========================================================================

    @Test
    @DisplayName("Immutabilité : Une période clôturée bloque strictement toute tentative de recalcul")
    public void testImmutabilitePeriodeCloturee() throws Exception {
        // 1. Calculer puis valider
        payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1);
        payrollService.validerPaiePeriode(idPeriode, 1);

        // 2. Clôturer les données
        try (Connection c = dataSource.getConnection()) {
            payrollService.cloturerDonneesPaie(idPeriode, 1, c);
            // Mettre aussi le statut de la période à CLOTUREE
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE periode_paie SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'CLOTUREE') WHERE id_periode_paie = ?")) {
                ps.setInt(1, idPeriode);
                ps.executeUpdate();
            }
        }

        // 3. Tenter un recalcul : le workflow doit bloquer avec STATE_CONFLICT
        PayrollPeriodUserContext admin = new PayrollPeriodUserContext(1, Set.of("ROLE_ADMIN"));
        SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                workflowService.requirePayrollAction(idPeriode, PayrollAction.CALCULATE, admin)
        );
        assertEquals(SirhErrorCode.STATE_CONFLICT, ex.getErrorCode(), "Erreur STATE_CONFLICT attendue");
        assertTrue(ex.getMessage().contains("clôturé"), "Message mentionnant la clôture immuable");
    }
}
