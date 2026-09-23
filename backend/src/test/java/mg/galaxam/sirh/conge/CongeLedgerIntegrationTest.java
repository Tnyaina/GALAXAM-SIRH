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
    private TransactionRunner transactionRunner;

    private int adminUserId;
    private int employeId;
    private int typeAnnuelId;

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
    @DisplayName("20. Purge FIFO sur PostgreSQL : lots anciens prescrits (> 36 mois) purgés avec EXPIRATION")
    void testPurgeFifoPostgres() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            LocalDate aujourdhui = LocalDate.of(2026, 9, 23);

            // Lot 1 : 10 jours acquis il y a 40 mois (2023-05-01) -> Prescrit car > 36 mois
            CongeTestFixtureHelper.crediterSoldeAvecDate(c, employeId, 10.0, LocalDate.of(2023, 5, 1), "Acquisition Lot Ancien");

            // Lot 2 : 15 jours acquis il y a 6 mois (2026-03-01) -> Actif / non prescrit
            CongeTestFixtureHelper.crediterSoldeAvecDate(c, employeId, 15.0, LocalDate.of(2026, 3, 1), "Acquisition Lot Récent");

            // Prise de 4 jours (consommation imputée en priorité sur Lot 1 selon règle FIFO)
            String sqlPrise = "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                    "VALUES (?, 'PRISE', -4.0, ?, 'Prise partielle')";
            try (PreparedStatement psP = c.prepareStatement(sqlPrise)) {
                psP.setInt(1, employeId);
                psP.setDate(2, Date.valueOf(LocalDate.of(2024, 1, 15)));
                psP.executeUpdate();
            }

            // Solde avant purge = 10 + 15 - 4 = 21 jours
            VSoldeCongeEmploye soldeAvant = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("21.0").compareTo(soldeAvant.getSoldeActuel()));

            // Exécution de la purge FIFO
            // Le Lot 1 avait 10j, 4j consommés -> reste 6j prescrits qui doivent être purgés
            int nbPurges = congeService.executerPurge(aujourdhui, c);
            assertEquals(1, nbPurges, "1 salarié doit être purgé");

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

            // Solde après purge = 21 - 6 = 15.0 jours (exactement le stock récent non expiré)
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
