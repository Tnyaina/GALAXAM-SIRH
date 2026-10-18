package mg.galaxam.sirh.conge;

import mg.galaxam.sirh.common.transaction.TransactionRunner;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.persistence.DemandeConge;
import mg.galaxam.sirh.conge.persistence.VSoldeCongeEmploye;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CongeLedgerIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeService congeService;

    @Autowired
    private mg.galaxam.sirh.conge.application.CongeExpirationExamenService congeExpirationExamenService;

    @Autowired
    private TransactionRunner transactionRunner;

    private int adminUserId;
    private int employeId;
    private int typeAnnuelId;

    @Test
    void acquisitionPartielleRespecteLeDiviseurConfigure() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE parametre_global SET valeur='26' WHERE code_parametre='DIVISEUR_JOURS_CALENDAIRE_CONGE'")) {
                    ps.executeUpdate();
                }
                BigDecimal acquis = congeService.enregistrerAcquisitionMensuelle(
                        employeId, 2026, 3, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                        new BigDecimal("15"), null, adminUserId, c);
                assertEquals(0, new BigDecimal("1.4423").compareTo(acquis));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void rattrapageBonificationUtiliseLaSituationALaDateDeReference() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE contrat_travail SET date_debut=?, date_fin=?, "
                                + "id_statut_rh=(SELECT id_statut_rh FROM ref_statut_rh WHERE code='CLOTURE') "
                                + "WHERE id_employe=?")) {
                    ps.setDate(1, Date.valueOf("2010-01-01"));
                    ps.setDate(2, Date.valueOf("2027-01-31"));
                    ps.setInt(3, employeId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO mouvement_solde_conge(id_employe,type_mouvement,nb_jours,date_mouvement,commentaire) "
                                + "VALUES (?, 'AJUSTEMENT', 900, ?, 'Mouvement futur')")) {
                    ps.setInt(1, employeId);
                    ps.setDate(2, Date.valueOf("2027-12-31"));
                    ps.executeUpdate();
                }
                congeService.executerBonificationMensuelle(LocalDate.of(2026, 9, 30), c);
                assertEquals(1, nombreBonifications(c));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void palierBonificationDesactiveNeReintroduitPasUnPalierParDefaut() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE contrat_travail SET date_debut = ? WHERE id_employe = ?")) {
                    ps.setDate(1, Date.valueOf("2010-01-01"));
                    ps.setInt(2, employeId);
                    ps.executeUpdate();
                }
                var beforeActive = c.setSavepoint();
                congeService.executerBonificationMensuelle(LocalDate.of(2026, 9, 30), c);
                assertTrue(nombreBonifications(c) > 0, "La règle active doit créditer la bonification");
                c.rollback(beforeActive);

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE parametre_global SET est_actif = FALSE WHERE code_parametre = 'PALIER_ANNEES_BONIFICATION'")) {
                    ps.executeUpdate();
                }
                congeService.executerBonificationMensuelle(LocalDate.of(2026, 9, 30), c);
                assertEquals(0, nombreBonifications(c));
            } finally {
                c.rollback();
            }
        }
    }

    private int nombreBonifications(Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'BONIFICATION'")) {
            ps.setInt(1, employeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
            adminUserId = CongeTestFixtureHelper.createAdminUser(c, "admin.ledger@galaxam.mg");
            employeId = CongeTestFixtureHelper.createEmploye(c, "EMP-LDG-001", "Andria", "Hery");
            int contratId = CongeTestFixtureHelper.createContratActif(c, employeId, LocalDate.of(2024, 1, 1));
            CongeTestFixtureHelper.createAffectation(c, employeId, contratId, LocalDate.of(2024, 1, 1), new BigDecimal("1800000.00"));
            typeAnnuelId = CongeTestFixtureHelper.getTypeCongeId(c, "ANNUEL");
        }
    }

    @Test
    @DisplayName("11. Acquisition mensuelle réelle et idempotence (aucun double crédit)")
    void testAcquisitionMensuelleEtIdempotence() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Premier cycle d'acquisition pour mars 2026 (sans absence)
            BigDecimal acquis1 = congeService.enregistrerAcquisitionMensuelle(
                    employeId, 2026, 3, BigDecimal.ZERO, adminUserId, c);
            assertEquals(0, new BigDecimal("2.5").compareTo(acquis1));

            // Vérifier présence d'un seul mouvement
            String sqlCount = "SELECT count(*), sum(nb_jours) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'ACQUISITION'";
            try (PreparedStatement ps = c.prepareStatement(sqlCount)) {
                ps.setInt(1, employeId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                    assertEquals(0, new BigDecimal("2.5").compareTo(rs.getBigDecimal(2)));
                }
            }

            // Deuxième exécution pour le même mois (même période) -> IDEMPOTENCE
            BigDecimal acquis2 = congeService.enregistrerAcquisitionMensuelle(
                    employeId, 2026, 3, BigDecimal.ZERO, adminUserId, c);
            assertEquals(0, new BigDecimal("2.5").compareTo(acquis2));

            // Toujours exactement 1 seul mouvement en base et solde inchangé
            try (PreparedStatement ps = c.prepareStatement(sqlCount)) {
                ps.setInt(1, employeId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Il ne doit y avoir aucun doublon d'acquisition");
                    assertEquals(0, new BigDecimal("2.5").compareTo(rs.getBigDecimal(2)));
                }
            }

            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertNotNull(solde);
            assertEquals(0, new BigDecimal("2.5").compareTo(solde.getSoldeActuel()));
        }
    }

    @Test
    void periodeCompleteVingtHuitJoursCrediteeALaFinDeLaPeriode() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE contrat_travail SET date_debut = ? WHERE id_employe = ?")) {
                ps.setDate(1, Date.valueOf("2025-02-26"));
                ps.setInt(2, employeId);
                ps.executeUpdate();
            }
            BigDecimal acquis = congeService.enregistrerAcquisitionMensuelle(employeId, 2025, 3,
                    LocalDate.of(2025, 2, 26), LocalDate.of(2025, 3, 25),
                    BigDecimal.ZERO, adminUserId, c);
            assertEquals(0, new BigDecimal("2.5000").compareTo(acquis));
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT date_mouvement FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'ACQUISITION'")) {
                ps.setInt(1, employeId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(LocalDate.of(2025, 3, 25), rs.getDate(1).toLocalDate());
                }
            }
        }
    }

    @Test
    void embaucheEnCoursDeMoisEstProratiseeSansDroitAvantContrat() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE contrat_travail SET date_debut = ? WHERE id_employe = ?")) {
                ps.setDate(1, Date.valueOf("2026-01-16"));
                ps.setInt(2, employeId);
                ps.executeUpdate();
            }
            BigDecimal acquis = congeService.enregistrerAcquisitionMensuelle(employeId, 2026, 1,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                    BigDecimal.ZERO, adminUserId, c);
            assertEquals(0, new BigDecimal("1.2903").compareTo(acquis));
        }
    }

    @Test
    void vingtQuatreJoursDeTravailDonnentUnMoisEquivalent() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE contrat_travail SET date_debut = ? WHERE id_employe = ?")) {
                ps.setDate(1, Date.valueOf("2026-01-05"));
                ps.setInt(2, employeId);
                ps.executeUpdate();
            }
            BigDecimal acquis = congeService.enregistrerAcquisitionMensuelle(employeId, 2026, 1,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                    BigDecimal.ZERO, new BigDecimal("24"), adminUserId, c);
            assertEquals(0, new BigDecimal("2.5000").compareTo(acquis));
        }
    }

    @Test
    void sortieEnCoursDeMoisArreteLAcquisitionAuDernierJourDuContrat() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE contrat_travail SET date_fin = ? WHERE id_employe = ?")) {
                ps.setDate(1, Date.valueOf("2026-01-20"));
                ps.setInt(2, employeId);
                ps.executeUpdate();
            }
            BigDecimal acquis = congeService.enregistrerAcquisitionMensuelle(employeId, 2026, 1,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                    BigDecimal.ZERO, adminUserId, c);
            assertEquals(0, new BigDecimal("1.6129").compareTo(acquis));
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT date_mouvement FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'ACQUISITION'")) {
                ps.setInt(1, employeId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(LocalDate.of(2026, 1, 20), rs.getDate(1).toLocalDate());
                }
            }
        }
    }

    @Test
    @DisplayName("21. Le plafond de stock ne diminue pas les droits acquis")
    void testPlafondStockNeReduitPasAcquisition() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                CongeTestFixtureHelper.crediterSolde(c, employeId, 89.0, "Solde avant plafond optionnel");

                try (PreparedStatement ps = c.prepareStatement("UPDATE parametre_global SET est_actif = TRUE "
                        + "WHERE code_parametre = 'PLAFOND_CUMUL_SOLDE_CONGE_JOURS' "
                        + "AND date_debut <= CURRENT_DATE AND (date_fin IS NULL OR date_fin >= CURRENT_DATE)")) {
                    assertEquals(1, ps.executeUpdate());
                }
                BigDecimal acquisSousPlafond = congeService.enregistrerAcquisitionMensuelle(
                        employeId, 2026, 3, BigDecimal.ZERO, adminUserId, c);
                assertEquals(0, new BigDecimal("2.50").compareTo(acquisSousPlafond));

                try (PreparedStatement ps = c.prepareStatement("UPDATE parametre_global SET est_actif = FALSE "
                        + "WHERE code_parametre = 'PLAFOND_CUMUL_SOLDE_CONGE_JOURS' "
                        + "AND date_debut <= CURRENT_DATE AND (date_fin IS NULL OR date_fin >= CURRENT_DATE)")) {
                    assertEquals(1, ps.executeUpdate());
                }
                BigDecimal acquisSansPlafond = congeService.enregistrerAcquisitionMensuelle(
                        employeId, 2026, 4, BigDecimal.ZERO, adminUserId, c);
                assertEquals(0, new BigDecimal("2.50").compareTo(acquisSansPlafond));
                assertEquals(0, new BigDecimal("94.00").compareTo(
                        VSoldeCongeEmploye.rechercherParEmploye(employeId, c).getSoldeActuel()));

                try (PreparedStatement ps = c.prepareStatement("DELETE FROM parametre_global "
                        + "WHERE code_parametre = 'PLAFOND_CUMUL_SOLDE_CONGE_JOURS' "
                        + "AND date_debut <= CURRENT_DATE AND (date_fin IS NULL OR date_fin >= CURRENT_DATE)")) {
                    assertEquals(1, ps.executeUpdate());
                }
                assertEquals(0, new BigDecimal("2.50").compareTo(congeService.enregistrerAcquisitionMensuelle(
                        employeId, 2026, 5, BigDecimal.ZERO, adminUserId, c)));
                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("20. Examen DAF : droits candidats restent au solde jusqu'à confirmation motivée")
    void testExamenExpirationDafPostgres() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            LocalDate aujourdhui = LocalDate.now();
            LocalDate ancien = aujourdhui.minusMonths(40);
            LocalDate recent = aujourdhui.minusMonths(6);

            // Lot 1 : 10 jours acquis il y a 40 mois -> à examiner
            CongeTestFixtureHelper.crediterSoldeAvecDate(c, employeId, 10.0, ancien, "Acquisition Lot Ancien");

            // Lot 2 : 15 jours acquis il y a 6 mois -> non échu
            CongeTestFixtureHelper.crediterSoldeAvecDate(c, employeId, 15.0, recent, "Acquisition Lot Récent");

            // Prise de 4 jours postérieure à l'acquisition ancienne et antérieure au lot récent.
            LocalDate datePrise = ancien.plusMonths(8);
            String sqlPrise = "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                    "VALUES (?, 'PRISE', -4.0, ?, 'Prise partielle')";
            try (PreparedStatement psP = c.prepareStatement(sqlPrise)) {
                psP.setInt(1, employeId);
                psP.setDate(2, Date.valueOf(datePrise));
                psP.executeUpdate();
            }

            // Solde avant examen = 10 + 15 - 4 = 21 jours
            VSoldeCongeEmploye soldeAvant = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("21.0").compareTo(soldeAvant.getSoldeActuel()));

            var examen = congeExpirationExamenService.obtenir(employeId, aujourdhui, c);
            assertEquals(0, new BigDecimal("6.00").compareTo(examen.joursAExaminer()));
            assertEquals(1, examen.droitsCandidats().size());
            assertEquals(0, new BigDecimal("6.00").compareTo(examen.droitsCandidats().get(0).joursDisponibles()));

            // L'examen seul ne débite pas le solde.
            VSoldeCongeEmploye soldeApresExamen = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("21.0").compareTo(soldeApresExamen.getSoldeActuel()));

            var reporte = congeExpirationExamenService.reporter(employeId, aujourdhui.plusDays(30),
                    "Congés à planifier avec le responsable de service.", adminUserId, c);
            assertEquals("REPORTE", reporte.derniereDecision());
            assertEquals(0, new BigDecimal("21.0").compareTo(
                    VSoldeCongeEmploye.rechercherParEmploye(employeId, c).getSoldeActuel()));

            // La décision DAF doit être motivée et confirmer la vérification de planification.
            assertThrows(IllegalArgumentException.class, () -> congeExpirationExamenService.confirmerExpiration(
                    employeId, "Planification vérifiée, aucun empêchement de service déclaré.", false, adminUserId, c));
            assertEquals(0, new BigDecimal("21.0").compareTo(
                    VSoldeCongeEmploye.rechercherParEmploye(employeId, c).getSoldeActuel()));
            congeExpirationExamenService.confirmerExpiration(employeId,
                    "Planification vérifiée, aucun empêchement de service déclaré.", true, adminUserId, c);

            // Vérification du mouvement EXPIRATION
            String sqlExp = "SELECT nb_jours, type_mouvement FROM mouvement_solde_conge " +
                    "WHERE id_employe = ? AND type_mouvement = 'EXPIRATION'";
            try (PreparedStatement psE = c.prepareStatement(sqlExp)) {
                psE.setInt(1, employeId);
                try (ResultSet rs = psE.executeQuery()) {
                    assertTrue(rs.next(), "Un mouvement EXPIRATION doit exister");
                    assertEquals(0, new BigDecimal("-6.0").compareTo(rs.getBigDecimal("nb_jours")));
                    assertFalse(rs.next());
                }
            }

            // Solde après confirmation = 21 - 6 = 15.0 jours (exactement le stock récent non expiré)
            VSoldeCongeEmploye soldeApres = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("15.0").compareTo(soldeApres.getSoldeActuel()));
        }
    }

    @Test
    @DisplayName("17. Transactionnalité : rollback complet en cas d'erreur au milieu d'une opération")
    void testTransactionRollbackComplet() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 10.0, "Solde test rollback");

            LocalDate debut = LocalDate.of(2026, 11, 2);
            LocalDate fin = LocalDate.of(2026, 11, 6);

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeAnnuelId);
            d.setDateDebut(Date.valueOf(debut));
            d.setDateFin(Date.valueOf(fin));
            d.setDerogationDelaiPreavis(true);
            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);
            int idDemande = cree.getIdDemandeConge();

            // Exécution sous TransactionRunner d'un bloc qui valide puis lève une exception inattendue
            assertThrows(RuntimeException.class, () -> {
                transactionRunner.required(conn -> {
                    congeService.validerDemande(idDemande, adminUserId, "Validation provisoire", conn);
                    // Erreur simulée au milieu de la transaction
                    throw new RuntimeException("Crash transactionnel provoqué");
                });
            });

            // Vérification que RIEN n'a été commité : la demande doit être restée EN_ATTENTE
            DemandeConge apresCrash = DemandeConge.rechercherParId(idDemande, c);
            assertNotNull(apresCrash);
            assertEquals("EN_ATTENTE", apresCrash.getStatutCode(c));
            assertNull(apresCrash.getDateValidation());

            // Aucun mouvement PRISE ne doit subsister
            String sqlMvt = "SELECT count(*) FROM mouvement_solde_conge WHERE id_demande_conge = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlMvt)) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Aucun mouvement PRISE ne doit persister après rollback");
                }
            }

            // Solde intact
            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("10.0").compareTo(solde.getSoldeActuel()));
        }
    }

    @Test
    @DisplayName("18. Concurrence sur validation : deux validations simultanées -> une seule gagne, pas de double PRISE")
    void testConcurrenceValidationDemande() throws Exception {
        int idDemande;
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 20.0, "Solde 20j");

            LocalDate debut = LocalDate.of(2026, 11, 9);
            LocalDate fin = LocalDate.of(2026, 11, 13); // 5 jours

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeAnnuelId);
            d.setDateDebut(Date.valueOf(debut));
            d.setDateFin(Date.valueOf(fin));
            d.setDerogationDelaiPreavis(true);
            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);
            idDemande = cree.getIdDemandeConge();
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    transactionRunner.required(conn -> {
                        congeService.validerDemande(idDemande, adminUserId, "Validation concurrente", conn);
                        return null;
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            });
        }

        // Déclenchement synchronisé des 2 threads
        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Vérification des résultats concurrents : exactement 1 succès et 1 échec
        assertEquals(1, successCount.get(), "Exactement une validation doit réussir");
        assertEquals(1, failureCount.get(), "L'autre validation concurrente doit échouer");

        // Vérification de la base réelle
        try (Connection c = dataSource.getConnection()) {
            // Un seul mouvement PRISE en base
            String sqlMvt = "SELECT count(*), sum(nb_jours) FROM mouvement_solde_conge WHERE id_demande_conge = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlMvt)) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Il ne doit y avoir qu'un seul mouvement PRISE");
                    assertEquals(0, new BigDecimal("-5.0").compareTo(rs.getBigDecimal(2)));
                }
            }

            // Solde net = 20 - 5 = 15.0 jours (aucune double déduction)
            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("15.0").compareTo(solde.getSoldeActuel()));
        }
    }
}
