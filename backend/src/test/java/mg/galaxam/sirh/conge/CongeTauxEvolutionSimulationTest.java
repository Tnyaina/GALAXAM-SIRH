package mg.galaxam.sirh.conge;

import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CongeTauxEvolutionSimulationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    @Autowired
    private CongeService congeService;

    private int adminUserId;
    private int employeId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
            adminUserId = CongeTestFixtureHelper.createAdminUser(c, "admin.simulation@galaxam.mg");
            employeId = CongeTestFixtureHelper.createEmploye(c, "EMP-SIM-200", "AGENT_X", "TMM");
            int contratId = CongeTestFixtureHelper.createContratActif(c, employeId, LocalDate.of(2025, 2, 26));
            CongeTestFixtureHelper.createAffectation(c, employeId, contratId, LocalDate.of(2025, 2, 26), new BigDecimal("300000.00"));
        }
    }

    @Test
    @DisplayName("Simulation complète : Cutover initial -> Évolution mensuelle (Journal des droits) -> Clôture et Nouveau TCONGE")
    void simulerEvolutionTauxCongesHistorique() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            System.out.println("======================================================================");
            System.out.println("SIMULATION DU CYCLE DE VIE DU TAUX DE CONGÉ (TCONGE) DANS LE SIRH");
            System.out.println("======================================================================");

            // ----------------------------------------------------------------------------------------
            // ÉTAPE 1 : CUTOVER INITIAL (Reprise des données historiques de l'ancien système Excel)
            // ----------------------------------------------------------------------------------------
            // Situation au 01/01/2026 : l'employé a 10 jours de solde, et son TCONGE historique est de 9 663.09 Ar
            LocalDate dateCutover = LocalDate.of(2026, 1, 1);
            BigDecimal soldeInitial = new BigDecimal("10.00");
            BigDecimal tcongeInitial = new BigDecimal("9663.09");

            CongeTauxApplicableService.CutoverOpeningRequest req = new CongeTauxApplicableService.CutoverOpeningRequest();
            req.setIdEmploye(employeId);
            req.setDateCutover(dateCutover);
            req.setDateDebutReference(dateCutover);
            req.setSoldeJours(soldeInitial);
            req.setTauxJournalierApplicable(tcongeInitial);
            req.setModeReprise(CongeTauxApplicableService.CongeModeReprise.AGGREGATED_OPENING);
            req.setCumulDroitsOuverture(new BigDecimal("10.00"));
            // 10 jours x 9663.09 = 96 630.90 Ar de réserve valorisée à l'ouverture
            req.setCumulValorisationOuverture(new BigDecimal("96630.90"));
            req.setCommentaire("Cutover initial KAJY sept26");

            int idPeriodeRef = congeTauxApplicableService.enregistrerCutoverSnapshot(req, adminUserId, c);
            assertTrue(idPeriodeRef > 0, "La période de référence doit être créée");
            assertEquals(0, BigDecimal.valueOf(congeService.getImputationSolde(employeId, c)
                    .getStockHistoriqueInitial()).compareTo(soldeInitial));

            System.out.println("\n[ÉTAPE 1 - CUTOVER INITIAL au " + dateCutover + "]");
            System.out.println("  -> Solde initial : " + soldeInitial + " jours");
            System.out.println("  -> Taux TCONGE initial actif : " + tcongeInitial + " Ar/jour");
            System.out.println("  -> Réserve valorisée d'ouverture : 96 630.90 Ar");

            // Vérification de la résolution immédiate par la Paie au 15/01/2026
            CongeTauxApplicableService.TauxCongeResolution res1 = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    employeId, LocalDate.of(2026, 1, 15), new BigDecimal("300000.00"), c);
            assertEquals(0, tcongeInitial.compareTo(res1.getTauxJournalier()));
            assertEquals(CongeTauxApplicableService.TcongeSourceProvenance.REPRISE_CUTOVER, res1.getSourceProvenance());

            // ----------------------------------------------------------------------------------------
            // ÉTAPE 2 : MOIS 1 (Janvier 2026) - Clôture de Paie normale (Salaire 300 000 Ar)
            // ----------------------------------------------------------------------------------------
            // En Janvier : acquisition de 2.5 jours.
            // Valorisation mensuelle = (300 000 / 30) * 2.5 = 25 000 Ar.
            insererLigneJournalDroit(c, employeId, idPeriodeRef, 2026, 1,
                    new BigDecimal("300000.00"), new BigDecimal("2.50"), new BigDecimal("25000.00"),
                    new BigDecimal("12.50"), // 10 (ouverture) + 2.5
                    new BigDecimal("121630.90"), // 96630.90 + 25000
                    new BigDecimal("9730.47") // 121630.90 / 12.5
            );
            System.out.println("\n[ÉTAPE 2 - CLÔTURE PAIE JANVIER 2026]");
            System.out.println("  -> Salaire du mois : 300 000 Ar | Droit acquis : 2.5 jours | Valorisation : 25 000 Ar");
            System.out.println("  -> Cumul droits : 12.5 j | Cumul valorisation : 121 630.90 Ar");
            System.out.println("  -> Taux moyen cumulé glissant = 121 630.90 / 12.5 = 9 730.47 Ar/j");

            // ----------------------------------------------------------------------------------------
            // ÉTAPE 3 : MOIS 2 (Février 2026) - Clôture de Paie normale (Salaire 300 000 Ar)
            // ----------------------------------------------------------------------------------------
            insererLigneJournalDroit(c, employeId, idPeriodeRef, 2026, 2,
                    new BigDecimal("300000.00"), new BigDecimal("2.50"), new BigDecimal("25000.00"),
                    new BigDecimal("15.00"),
                    new BigDecimal("146630.90"),
                    new BigDecimal("9775.39")
            );
            System.out.println("\n[ÉTAPE 3 - CLÔTURE PAIE FÉVRIER 2026]");
            System.out.println("  -> Cumul droits : 15.0 j | Cumul valorisation : 146 630.90 Ar");
            System.out.println("  -> Taux moyen cumulé glissant = 9 775.39 Ar/j");

            // ----------------------------------------------------------------------------------------
            // ÉTAPE 4 : MOIS 3 (Mars 2026) - AUGMENTATION DE SALAIRE À 370 000 Ar
            // ----------------------------------------------------------------------------------------
            // Le salaire passe à 370 000 Ar !
            // Valorisation mensuelle = (370 000 / 30) * 2.5 = 30 833.33 Ar.
            insererLigneJournalDroit(c, employeId, idPeriodeRef, 2026, 3,
                    new BigDecimal("370000.00"), new BigDecimal("2.50"), new BigDecimal("30833.33"),
                    new BigDecimal("17.50"),
                    new BigDecimal("177464.23"),
                    new BigDecimal("10140.81")
            );
            System.out.println("\n[ÉTAPE 4 - CLÔTURE PAIE MARS 2026 (Augmentation à 370 000 Ar)]");
            System.out.println("  -> Salaire : 370 000 Ar | Droit : 2.5 j | Valorisation : 30 833.33 Ar (hausse de la réserve !)");
            System.out.println("  -> Cumul droits : 17.5 j | Cumul valorisation : 177 464.23 Ar");
            System.out.println("  -> Le taux moyen cumulé progresse automatiquement à : 10 140.81 Ar/j");

            // ----------------------------------------------------------------------------------------
            // ÉTAPE 5 : CLÔTURE DE PÉRIODE DE RÉFÉRENCE & CALCUL DU NOUVEAU TCONGE
            // ----------------------------------------------------------------------------------------
            BigDecimal nouveauTconge = congeTauxApplicableService.cloturerPeriodeReference(idPeriodeRef, adminUserId, c);
            assertNotNull(nouveauTconge);
            assertTrue(nouveauTconge.compareTo(tcongeInitial) > 0, "Le nouveau TCONGE doit être supérieur à l'ancien grâce à l'augmentation");

            System.out.println("\n[ÉTAPE 5 - CLÔTURE ANNUELLE DE LA PÉRIODE DE RÉFÉRENCE]");
            System.out.println("  -> Ancien TCONGE initial : " + tcongeInitial + " Ar/j");
            System.out.println("  -> NOUVEAU TCONGE officiel calculé et scellé : " + nouveauTconge + " Ar/j");
            System.out.println("  -> Ce nouveau taux s'appliquera automatiquement dès l'activation pour l'année suivante !");
            System.out.println("======================================================================\n");
        }
    }

    private void insererLigneJournalDroit(Connection c, int idEmp, int idRef, int annee, int mois,
                                          BigDecimal base, BigDecimal droit, BigDecimal valMois,
                                          BigDecimal cumulJours, BigDecimal cumulVal, BigDecimal tauxMoyen) throws Exception {
        String sql = "INSERT INTO conge_journal_droit (" +
                "id_employe, annee, mois, salaire_base, base_valorisation, jours_droit_acquis, " +
                "valorisation_mensuelle, cumul_jours_droit, cumul_valorisation, taux_journalier_conge, " +
                "id_periode_reference" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmp);
            ps.setInt(2, annee);
            ps.setInt(3, mois);
            ps.setBigDecimal(4, base);
            ps.setBigDecimal(5, base); // base_valorisation
            ps.setBigDecimal(6, droit);
            ps.setBigDecimal(7, valMois);
            ps.setBigDecimal(8, cumulJours);
            ps.setBigDecimal(9, cumulVal);
            ps.setBigDecimal(10, tauxMoyen);
            ps.setInt(11, idRef);
            ps.executeUpdate();
        }
    }
}
