package mg.galaxam.sirh.integration.conge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.model.conge.engine.CongeCutoverOpeningRequestDTO;
import mg.galaxam.sirh.model.conge.engine.CongeModeReprise;
import mg.galaxam.sirh.model.conge.engine.CongeSituationEmployeDTO;
import mg.galaxam.sirh.model.conge.service.CongeTauxApplicableService;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests d'intégration du cycle complet de cutover des congés :
 * - AGGREGATED_OPENING : continuité mathématique exacte avec cumul ouverture
 * - Découplage prise de congés vs accumulateurs du cycle
 * - MINIMAL_OPENING : rejet de clôture automatique (CYCLE_ACCUMULATORS_UNKNOWN) et arbitrage RH
 * - Idempotence et protection contre les chevauchements
 */
@DisplayName("P9/P11 — Tests d'Intégration du Workflow de Cutover et Continuité Congés")
public class CongeCutoverWorkflowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    @Autowired
    private PayrollService payrollService;

    private int testEmployeId;

    @BeforeEach
    void setup() {
        databaseCleaner.cleanTransactionalData();
        testEmployeId = employeFixtures.createActiveEmploye("EMP-CUTOVER", "RAKOTO", "Faly", 1).idEmploye;
    }

    private int creerPeriodePaieEtBulletin(int annee, int mois, BigDecimal base, BigDecimal hs, Connection c) throws Exception {
        LocalDate debut = LocalDate.of(annee, mois, 1);
        LocalDate fin = debut.plusMonths(1).minusDays(1);
        String code = "P-" + annee + "-" + String.format("%02d", mois);

        int idPer = employeFixtures.createPeriodePaie(code, debut, fin, "EN_COURS");

        String sqlBull = "INSERT INTO bulletin_paie (" +
                "id_periode_paie, id_employe, reference_bulletin, matricule, nom_prenom, intitule_poste, " +
                "nom_site, regime_intervenant, salaire_base, taux_horaire, statut" +
                ") VALUES (?, ?, ?, 'EMP-CUTOVER', 'RAKOTO Faly', 'Chauffeur', 'TNR', 'SALARIE_STANDARD', ?, ?, 'VALIDE') " +
                "RETURNING id_bulletin";
        int idBull;
        try (PreparedStatement ps = c.prepareStatement(sqlBull)) {
            ps.setInt(1, idPer);
            ps.setInt(2, testEmployeId);
            ps.setString(3, "BULL-" + code);
            ps.setBigDecimal(4, base);
            ps.setBigDecimal(5, base.divide(new BigDecimal("173.33"), 2, RoundingMode.HALF_UP));
            var rs = ps.executeQuery();
            rs.next();
            idBull = rs.getInt(1);
        }

        if (hs != null && hs.compareTo(BigDecimal.ZERO) > 0) {
            String sqlRub = "INSERT INTO bulletin_paie_ligne (id_bulletin, code_rubrique, libelle_rubrique, montant_salarial, sens, categorie, ordre) " +
                    "VALUES (?, 'HS_25', 'Heures Supplémentaires 25%', ?, 'GAIN', 'HEURES_SUPPLEMENTAIRES', 10)";
            try (PreparedStatement psR = c.prepareStatement(sqlRub)) {
                psR.setInt(1, idBull);
                psR.setBigDecimal(2, hs);
                psR.executeUpdate();
            }
        }

        return idPer;
    }

    @Test
    @DisplayName("Workflow AGGREGATED_OPENING : Cutover M9 + Prise congés + Paie M10 -> Continuité exacte")
    void testAggregatedOpeningWorkflowWithLeaveTakeAndPayroll() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);

            // 1. Initialisation Cutover Snapshot au 30/09/2026 (Mois 9 Havana)
            CongeCutoverOpeningRequestDTO req = new CongeCutoverOpeningRequestDTO();
            req.setIdEmploye(testEmployeId);
            req.setModeReprise(CongeModeReprise.AGGREGATED_OPENING);
            req.setDateCutover(LocalDate.of(2026, 9, 30));
            req.setSoldeJours(new BigDecimal("14.50"));
            req.setTauxJournalierApplicable(new BigDecimal("8669.52"));
            req.setDateDebutReference(LocalDate.of(2026, 1, 1));
            req.setCumulDroitsOuverture(new BigDecimal("22.4575"));
            req.setCumulValorisationOuverture(new BigDecimal("240092.52"));
            req.setCommentaire("Reprise Excel Havana fin Septembre 2026");

            int idRef = congeTauxApplicableService.enregistrerCutoverSnapshot(req, 1, c);
            c.commit();

            // 2. Vérification de la situation post-cutover
            CongeSituationEmployeDTO sit1 = congeTauxApplicableService.obtenirSituationComplete(
                    testEmployeId, LocalDate.of(2026, 10, 1), c
            );
            assertThat(sit1.getSoldeJours()).isEqualByComparingTo(new BigDecimal("14.50"));
            assertThat(sit1.getTauxApplicable().getMontant()).isEqualByComparingTo(new BigDecimal("8669.52"));
            assertThat(sit1.getTauxApplicable().getSource()).isEqualTo("REPRISE_CUTOVER");
            assertThat(sit1.getPeriodeReference().getDateDebut()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(sit1.getPeriodeReference().getDateFin()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(sit1.getPeriodeReference().getCumulDroitsTotal()).isEqualByComparingTo(new BigDecimal("22.4575"));
            assertThat(sit1.getPeriodeReference().getCumulValorisationTotal()).isEqualByComparingTo(new BigDecimal("240092.52"));
            assertThat(sit1.getPeriodeReference().isOpeningComplete()).isTrue();
            assertThat(sit1.getPeriodeReference().isClotureAutomatiquePossible()).isTrue();

            // 3. Prise de congés de 5 jours le 10/10/2026
            String sqlPrise = "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire, created_by) " +
                    "VALUES (?, 'PRISE', ?, ?, 'Congé annuel payé', 1)";
            try (PreparedStatement psP = c.prepareStatement(sqlPrise)) {
                psP.setInt(1, testEmployeId);
                psP.setBigDecimal(2, new BigDecimal("5.00"));
                psP.setDate(3, Date.valueOf(LocalDate.of(2026, 10, 10)));
                psP.executeUpdate();
            }
            c.commit();

            // 4. Vérification du découplage P3 : le solde diminue de 5j, mais les accumulateurs de cycle restent INTACTS !
            CongeSituationEmployeDTO sit2 = congeTauxApplicableService.obtenirSituationComplete(
                    testEmployeId, LocalDate.of(2026, 10, 15), c
            );
            assertThat(sit2.getSoldeJours()).isEqualByComparingTo(new BigDecimal("9.50")); // 14.50 - 5.00
            assertThat(sit2.getPeriodeReference().getCumulDroitsTotal()).isEqualByComparingTo(new BigDecimal("22.4575")); // Inchangé !
            assertThat(sit2.getPeriodeReference().getCumulValorisationTotal()).isEqualByComparingTo(new BigDecimal("240092.52")); // Inchangé !

            // 5. Exécution de la paie du Mois 10 (Octobre 2026) :
            // Salaire base 350 000 Ar, HS 3 196.25 Ar -> Droit mois = 2.50, Valo mois = 29 433.02 Ar
            int idPerM10 = creerPeriodePaieEtBulletin(2026, 10, new BigDecimal("350000.00"), new BigDecimal("3196.25"), c);
            payrollService.mettreAJourJournalConge(idPerM10, c);
            c.commit();

            // 6. Vérification des nouveaux cumuls intégrant l'ouverture + Mois 10 :
            CongeSituationEmployeDTO sit3 = congeTauxApplicableService.obtenirSituationComplete(
                    testEmployeId, LocalDate.of(2026, 10, 31), c
            );
            // Droits post-cutover : 2.5j
            assertThat(sit3.getPeriodeReference().getCumulDroitsPostCutover()).isEqualByComparingTo(new BigDecimal("2.5000"));
            assertThat(sit3.getPeriodeReference().getCumulValorisationPostCutover()).isEqualByComparingTo(new BigDecimal("29433.02"));
            // Total = 22.4575 + 2.5 = 24.9575 j
            assertThat(sit3.getPeriodeReference().getCumulDroitsTotal()).isEqualByComparingTo(new BigDecimal("24.9575"));
            // Total valo = 240 092.52 + 29 433.02 = 269 525.54 Ar
            assertThat(sit3.getPeriodeReference().getCumulValorisationTotal()).isEqualByComparingTo(new BigDecimal("269525.54"));
        }
    }

    @Test
    @DisplayName("Workflow MINIMAL_OPENING : Clôture automatique interdite -> Arbitrage RH obligatoire")
    void testMinimalOpeningBlocksAutomaticClosureAndRequiresArbitration() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);

            // 1. Initialisation Cutover MINIMAL_OPENING (historique antérieur inconnu)
            CongeCutoverOpeningRequestDTO req = new CongeCutoverOpeningRequestDTO();
            req.setIdEmploye(testEmployeId);
            req.setModeReprise(CongeModeReprise.MINIMAL_OPENING);
            req.setDateCutover(LocalDate.of(2026, 9, 30));
            req.setSoldeJours(new BigDecimal("10.00"));
            req.setTauxJournalierApplicable(new BigDecimal("9500.00"));
            req.setDateDebutReference(LocalDate.of(2026, 1, 1));
            req.setCumulDroitsOuverture(null); // UNKNOWN
            req.setCumulValorisationOuverture(null); // UNKNOWN
            req.setCommentaire("Cutover minimal sans tableau Excel de cumuls");

            int idRef = congeTauxApplicableService.enregistrerCutoverSnapshot(req, 1, c);
            c.commit();

            // 2. Vérification : openingComplete = false, clôture automatique impossible
            CongeSituationEmployeDTO sit = congeTauxApplicableService.obtenirSituationComplete(
                    testEmployeId, LocalDate.of(2026, 10, 1), c
            );
            assertThat(sit.getPeriodeReference().isOpeningComplete()).isFalse();
            assertThat(sit.getPeriodeReference().isClotureAutomatiquePossible()).isFalse();
            assertThat(sit.getPeriodeReference().getTauxProjection()).isNull(); // Pas de fausse projection !

            // 3. Simulation paie mois 10
            int idPerM10 = creerPeriodePaieEtBulletin(2026, 10, new BigDecimal("350000.00"), BigDecimal.ZERO, c);
            payrollService.mettreAJourJournalConge(idPerM10, c);
            c.commit();

            // 4. Tentative de clôture automatique -> DOIT LEVER CYCLE_ACCUMULATORS_UNKNOWN
            assertThatThrownBy(() -> congeTauxApplicableService.cloturerPeriodeReference(idRef, 1, c))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CYCLE_ACCUMULATORS_UNKNOWN");

            // 5. Arbitrage RH explicite : reconduction du taux validé
            congeTauxApplicableService.cloturerPeriodeArbitree(
                    idRef,
                    new BigDecimal("9500.00"),
                    "Arbitrage RH : reconduction du TCONGE 9 500 Ar pour l'année 2027 suite à cutover partiel",
                    1,
                    c
            );
            c.commit();

            // 6. Vérification : la période est désormais clôturée avec le taux arbitré
            String sqlCheck = "SELECT statut, taux_calcule_cloture FROM conge_periode_reference WHERE id_periode_reference = ?";
            try (PreparedStatement psC = c.prepareStatement(sqlCheck)) {
                psC.setInt(1, idRef);
                var rs = psC.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("statut")).isEqualTo("CLOTUREE");
                assertThat(rs.getBigDecimal("taux_calcule_cloture")).isEqualByComparingTo(new BigDecimal("9500.00"));
            }
        }
    }

    @Test
    @DisplayName("Protection anti-doublon : Deux cutovers sur la même période sont strictement rejetés")
    void testPreventDuplicateCutoverOnSamePeriod() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);

            CongeCutoverOpeningRequestDTO req1 = new CongeCutoverOpeningRequestDTO();
            req1.setIdEmploye(testEmployeId);
            req1.setModeReprise(CongeModeReprise.MINIMAL_OPENING);
            req1.setDateCutover(LocalDate.of(2026, 9, 30));
            req1.setSoldeJours(new BigDecimal("10.00"));
            req1.setTauxJournalierApplicable(new BigDecimal("9500.00"));
            req1.setDateDebutReference(LocalDate.of(2026, 1, 1));
            congeTauxApplicableService.enregistrerCutoverSnapshot(req1, 1, c);
            c.commit();

            // Tentative d'un second cutover pour le même salarié sur la même période
            CongeCutoverOpeningRequestDTO req2 = new CongeCutoverOpeningRequestDTO();
            req2.setIdEmploye(testEmployeId);
            req2.setModeReprise(CongeModeReprise.MINIMAL_OPENING);
            req2.setDateCutover(LocalDate.of(2026, 10, 1));
            req2.setSoldeJours(new BigDecimal("12.00"));
            req2.setTauxJournalierApplicable(new BigDecimal("9500.00"));
            req2.setDateDebutReference(LocalDate.of(2026, 1, 1));

            assertThatThrownBy(() -> congeTauxApplicableService.enregistrerCutoverSnapshot(req2, 1, c))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TCONGE_REFERENCE_PERIOD_OVERLAP");
        }
    }
}
