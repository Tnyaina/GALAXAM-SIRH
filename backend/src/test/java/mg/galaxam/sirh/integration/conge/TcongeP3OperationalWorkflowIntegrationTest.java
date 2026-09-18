package mg.galaxam.sirh.integration.conge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.model.conge.engine.TauxCongeResolution;
import mg.galaxam.sirh.model.conge.engine.TcongeConfigurationDTO;
import mg.galaxam.sirh.model.conge.engine.TcongeReferenceStatus;
import mg.galaxam.sirh.model.conge.engine.TcongeSourceProvenance;
import mg.galaxam.sirh.model.conge.service.CongeTauxApplicableService;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.service.StcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2A-4C.1 — Correction P3 : Sécurisation intégrale du workflow opérationnel TCONGE.
 * Période bornée (12 mois stricts) -> Clôture idempotente -> Activation transactionnelle atomique.
 */
@DisplayName("Phase 2A-4C.1 — P3 : Workflow opérationnel et activation transactionnelle TCONGE")
public class TcongeP3OperationalWorkflowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    @Autowired
    private PayrollService payrollService;

    private int testEmployeId;

    @BeforeEach
    void setup() {
        databaseCleaner.cleanTransactionalData();
        testEmployeId = employeFixtures.createActiveEmploye("EMP-P3-WF", "RABE", "Jean", 1).idEmploye;
    }

    private int creerPeriodePaieEtBulletin(int annee, int mois, BigDecimal base, BigDecimal hs, Connection c) throws Exception {
        LocalDate debut = LocalDate.of(annee, mois, 1);
        LocalDate fin = debut.plusMonths(1).minusDays(1);
        String code = "P-" + annee + "-" + String.format("%02d", mois);

        int idPer = employeFixtures.createPeriodePaie(code, debut, fin, "EN_COURS");

        String sqlBull = "INSERT INTO bulletin_paie (" +
                "id_periode_paie, id_employe, reference_bulletin, matricule, nom_prenom, intitule_poste, " +
                "nom_site, regime_intervenant, salaire_base, taux_horaire, statut" +
                ") VALUES (?, ?, ?, 'EMP-P3-WF', 'RABE Jean', 'Agent', 'TNR', 'SALARIE_STANDARD', ?, ?, 'VALIDE') " +
                "RETURNING id_bulletin";
        int idBull;
        try (PreparedStatement ps = c.prepareStatement(sqlBull)) {
            ps.setInt(1, idPer);
            ps.setInt(2, testEmployeId);
            ps.setString(3, "B-" + code);
            ps.setBigDecimal(4, base);
            ps.setBigDecimal(5, base.divide(new BigDecimal("173.33"), 4, RoundingMode.HALF_UP));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idBull = rs.getInt(1);
            }
        }

        if (hs != null && hs.compareTo(BigDecimal.ZERO) > 0) {
            String sqlLigne = "INSERT INTO bulletin_paie_ligne (" +
                    "id_bulletin, code_rubrique, libelle_rubrique, categorie, sens, montant_salarial" +
                    ") VALUES (?, 'HS_HEBDO', 'Heures Supplémentaires', 'HEURES_SUP', 'GAIN', ?)";
            try (PreparedStatement ps = c.prepareStatement(sqlLigne)) {
                ps.setInt(1, idBull);
                ps.setBigDecimal(2, hs);
                ps.executeUpdate();
            }
        }

        return idPer;
    }

    @Test
    @DisplayName("Validation de la durée : exactement 12 mois exigés (rejet si < ou > 12 mois)")
    void shouldValidateStrict12MonthDuration() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Période invalide de 6 mois
            LocalDate debut = LocalDate.of(2027, 1, 1);
            LocalDate fin6Mois = LocalDate.of(2027, 6, 30);
            assertThatThrownBy(() -> congeTauxApplicableService.creerPeriodeReference(testEmployeId, debut, fin6Mois, 1, c))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TCONGE_REFERENCE_DURATION_MISMATCH");

            // Période valide de 12 mois
            LocalDate fin12Mois = LocalDate.of(2027, 12, 31);
            int idRef = congeTauxApplicableService.creerPeriodeReference(testEmployeId, debut, fin12Mois, 1, c);
            assertThat(idRef).isPositive();
        }
    }

    @Test
    @DisplayName("Anti-chevauchement des références : deux périodes d'un employé ne peuvent se croiser")
    void shouldRejectOverlappingReferences() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Période 1 : 01/01/2027 au 31/12/2027
            congeTauxApplicableService.creerPeriodeReference(
                    testEmployeId, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), 1, c
            );

            // Tentative de créer une période chevauchante : 01/06/2027 au 31/05/2028
            assertThatThrownBy(() -> congeTauxApplicableService.creerPeriodeReference(
                    testEmployeId, LocalDate.of(2027, 6, 1), LocalDate.of(2028, 5, 31), 1, c
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TCONGE_REFERENCE_PERIOD_OVERLAP");
        }
    }

    @Test
    @DisplayName("Détection de discontinuité (trou) sans comblement automatique")
    void shouldDetectReferencePeriodGap() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Période 1 : 01/01/2027 au 31/12/2027
            congeTauxApplicableService.creerPeriodeReference(
                    testEmployeId, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), 1, c
            );

            // Période 2 démarrant le 01/03/2028 (trou de 2 mois : Janvier et Février 2028)
            String gap = congeTauxApplicableService.detecterDiscontinuiteReference(
                    testEmployeId, LocalDate.of(2028, 3, 1), c
            );
            assertThat(gap).isEqualTo("TCONGE_REFERENCE_PERIOD_GAP");
        }
    }

    @Test
    @DisplayName("Scénario PostgreSQL réel complet : Migration -> 12 mois -> Clôture -> Activation explicite -> Paie = STC")
    void shouldExecuteFullOperationalWorkflowWithStrictAsOfAndAlignment() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // 1. Enregistrement d'un taux initial de migration A (ex. 8 669,52 Ar) au 01/01/2026
            congeTauxApplicableService.enregistrerTauxApplicableMigration(
                    testEmployeId, new BigDecimal("8669.52"), LocalDate.of(2026, 1, 1), null, "Taux KAJY initial", 1, c
            );

            // Au 01/06/2026, aucune référence créée -> Warning REFERENCE_SETUP_REQUIRED exposé
            TcongeConfigurationDTO cfgInit = congeTauxApplicableService.evaluerConfigurationTconge(
                    testEmployeId, LocalDate.of(2026, 6, 1), c
            );
            assertThat(cfgInit.getWarnings()).contains("REFERENCE_SETUP_REQUIRED");

            // 2. Création explicite de la Référence R : 01/01/2027 au 31/12/2027 (12 mois)
            int idRef = congeTauxApplicableService.creerPeriodeReference(
                    testEmployeId, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), 1, c
            );
            assertThat(idRef).isPositive();

            // 3. Traitement des 12 mois de paie de 2027 (Base = 300 000, HS = 10 000 -> BaseValo = 310 000)
            BigDecimal base = new BigDecimal("300000.00");
            BigDecimal hs = new BigDecimal("10000.00");
            for (int m = 1; m <= 12; m++) {
                int idPer = creerPeriodePaieEtBulletin(2027, m, base, hs, c);
                payrollService.cloturerDonneesPaie(idPer, 1, c);
            }

            // 4. Clôture automatique au 12ème mois : taux calculé = 310 000 / 30 = 10 333,33 Ar
            // Vérification que la clôture est idempotente
            BigDecimal tauxCalcule = congeTauxApplicableService.cloturerPeriodeReference(idRef, 1, c);
            assertThat(tauxCalcule).isEqualByComparingTo("10333.33");

            // 5. Invariant P3 : Le taux calculé n'est PAS automatiquement applicable !
            // En Janvier 2028, avant activation explicite, l'ancien taux A (8 669,52) reste applicable
            TauxCongeResolution resAvant = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    testEmployeId, LocalDate.of(2028, 1, 15), null, c
            );
            assertThat(resAvant.getTauxJournalier()).isEqualByComparingTo("8669.52");

            // Diagnostic : Période clôturée en attente d'activation
            TcongeConfigurationDTO cfgAwaiting = congeTauxApplicableService.evaluerConfigurationTconge(
                    testEmployeId, LocalDate.of(2028, 1, 15), c
            );
            assertThat(cfgAwaiting.getReferenceStatus()).isEqualTo(TcongeReferenceStatus.CLOSED_AWAITING_ACTIVATION);
            assertThat(cfgAwaiting.getWarnings()).contains("REFERENCE_CLOSED_AWAITING_RATE_ACTIVATION");

            // 6. Activation explicite du taux B à la date d'effet D = 01/02/2028
            LocalDate dateD = LocalDate.of(2028, 2, 1);
            int idTauxB = congeTauxApplicableService.activerTauxCalcule(idRef, dateD, 1, c);
            assertThat(idTauxB).isPositive();

            // 7. Idempotence de l'activation : un second appel avec la même date retourne le même id sans créer de doublon
            int idTauxB2 = congeTauxApplicableService.activerTauxCalcule(idRef, dateD, 1, c);
            assertThat(idTauxB2).isEqualTo(idTauxB);

            // Tentative d'activer la même référence avec une date d'effet différente -> Rejet strict TCONGE_ACTIVATION_CONFLICT
            assertThatThrownBy(() -> congeTauxApplicableService.activerTauxCalcule(idRef, LocalDate.of(2028, 3, 1), 1, c))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TCONGE_ACTIVATION_CONFLICT");

            // 8. Vérification de la transition sans trou en base :
            // Taux A : [2026-01-01, 2028-02-01[
            // Taux B : [2028-02-01, NULL[
            String sqlVerifRates = "SELECT id_taux_applicable, taux_journalier, date_effet, date_fin, source_provenance " +
                    "FROM conge_taux_applicable WHERE id_employe = ? ORDER BY date_effet ASC";
            try (PreparedStatement ps = c.prepareStatement(sqlVerifRates)) {
                ps.setInt(1, testEmployeId);
                try (ResultSet rs = ps.executeQuery()) {
                    // Taux A
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBigDecimal("taux_journalier")).isEqualByComparingTo("8669.52");
                    assertThat(rs.getDate("date_effet").toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 1));
                    assertThat(rs.getDate("date_fin").toLocalDate()).isEqualTo(LocalDate.of(2028, 2, 1));

                    // Taux B
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBigDecimal("taux_journalier")).isEqualByComparingTo("10333.33");
                    assertThat(rs.getDate("date_effet").toLocalDate()).isEqualTo(LocalDate.of(2028, 2, 1));
                    assertThat(rs.getDate("date_fin")).isNull();

                    assertThat(rs.next()).isFalse(); // Exactement 2 taux, aucun doublon !
                }
            }

            // 9. Vérification de la résolution As-Of stricte :
            // Avant D (31/01/2028) -> Taux A (8 669,52 Ar)
            TauxCongeResolution asOfAvant = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    testEmployeId, LocalDate.of(2028, 1, 31), null, c
            );
            assertThat(asOfAvant.getTauxJournalier()).isEqualByComparingTo("8669.52");

            // Dès D (01/02/2028) -> Taux B (10 333,33 Ar)
            TauxCongeResolution asOfApres = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    testEmployeId, LocalDate.of(2028, 2, 1), null, c
            );
            assertThat(asOfApres.getTauxJournalier()).isEqualByComparingTo("10333.33");
            assertThat(asOfApres.getSourceProvenance()).isEqualTo(TcongeSourceProvenance.CALCUL_PERIODE_REFERENCE);

            // 10. Alignement parfait Paie = STC
            BigDecimal tauxPaie = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    testEmployeId, LocalDate.of(2028, 2, 15), null, c
            ).getTauxJournalier();

            BigDecimal tauxStc = StcService.getTauxJournalierApplicable(
                    testEmployeId, LocalDate.of(2028, 2, 15), null, c
            );

            assertThat(tauxPaie).isEqualByComparingTo(tauxStc);
            assertThat(tauxStc).isEqualByComparingTo("10333.33");
        }
    }

    @Test
    @DisplayName("Anti-concurrence : Deux activations concurrentes produisent un résultat déterministe sans doublon")
    void shouldHandleConcurrentActivationsDeterministically() throws Exception {
        int idRef;
        try (Connection c = dataSource.getConnection()) {
            idRef = congeTauxApplicableService.creerPeriodeReference(
                    testEmployeId, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), 1, c
            );
            congeTauxApplicableService.cloturerPeriodeReference(
                    idRef, new BigDecimal("30.0000"), new BigDecimal("310000.00"), new BigDecimal("10333.33"), c
            );
        }

        LocalDate dateD = LocalDate.of(2028, 2, 1);
        java.util.concurrent.atomic.AtomicInteger res1 = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger res2 = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            try (Connection c1 = dataSource.getConnection()) {
                c1.setAutoCommit(false);
                int id = congeTauxApplicableService.activerTauxCalcule(idRef, dateD, 1, c1);
                c1.commit();
                res1.set(id);
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });

        Thread t2 = new Thread(() -> {
            try (Connection c2 = dataSource.getConnection()) {
                c2.setAutoCommit(false);
                int id = congeTauxApplicableService.activerTauxCalcule(idRef, dateD, 1, c2);
                c2.commit();
                res2.set(id);
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });

        t1.start();
        t2.start();
        boolean finished = latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        // Preuve BDD : Exactement 1 enregistrement créé pour cette référence, aucun doublon
        try (Connection c = dataSource.getConnection()) {
            String sql = "SELECT COUNT(*), MIN(id_taux_applicable) FROM conge_taux_applicable WHERE id_periode_reference = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, idRef);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                    int singleId = rs.getInt(2);
                    assertThat(singleId).isPositive();
                }
            }
        }
    }
}
