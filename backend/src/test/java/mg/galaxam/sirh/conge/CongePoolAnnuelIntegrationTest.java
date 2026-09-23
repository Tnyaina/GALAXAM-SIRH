package mg.galaxam.sirh.conge;

import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import mg.galaxam.sirh.conge.persistence.DemandeConge;
import mg.galaxam.sirh.conge.persistence.RefPoolCongeAnnuel;
import mg.galaxam.sirh.conge.persistence.RefMotifEvenementFamilial;
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
class CongePoolAnnuelIntegrationTest {

    @Autowired DataSource dataSource;
    @Autowired CongeService congeService;

    private int userId;
    private int employeId;
    private int typeExceptionnelId;
    private int typePermissionId;
    private int poolId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
            userId = CongeTestFixtureHelper.createAdminUser(c, "admin.pool@galaxam.mg");
            employeId = CongeTestFixtureHelper.createEmploye(c, "EMP-POOL-001", "Pool", "Test");
            int contrat = CongeTestFixtureHelper.createContratActif(c, employeId, LocalDate.of(2024, 1, 1));
            CongeTestFixtureHelper.createAffectation(c, employeId, contrat, LocalDate.of(2024, 1, 1), new BigDecimal("1500000"));
            typeExceptionnelId = CongeTestFixtureHelper.getTypeCongeId(c, "EXCEPTIONNEL");
            typePermissionId = CongeTestFixtureHelper.getTypeCongeId(c, "PERMISSION");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_pool_conge_annuel FROM ref_type_conge WHERE id_type_conge = ?")) {
                ps.setInt(1, typeExceptionnelId);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); poolId = rs.getInt(1); }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_pool_conge_annuel FROM ref_type_conge WHERE code = 'PATERNITE'")) {
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertNull(rs.getObject(1)); }
            }
        }
    }

    @Test
    @DisplayName("Pool : zéro, VALIDE, EN_ATTENTE et total engagé sont distingués")
    void distingueConsommeReserveEtEngage() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertPool(c, 2026, "0", "0", "0");
            DemandeConge valide = creer(c, LocalDate.of(2026, 2, 1), 3);
            congeService.validerDemande(valide.getIdDemandeConge(), userId, "OK", c);
            assertPool(c, 2026, "3", "0", "3");
            creer(c, LocalDate.of(2026, 3, 1), 2);
            assertPool(c, 2026, "3", "2", "5");
        }
    }

    @Test
    @DisplayName("Pool : plafond BDD exactement atteint puis dépassement refusé en simulation et création")
    void plafondEtDepassement() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            BigDecimal plafond = RefPoolCongeAnnuel.rechercherParId(poolId, c).getPlafondJoursAn();
            creer(c, LocalDate.of(2026, 1, 1), plafond.intValue());
            assertPool(c, 2026, "0", plafond.toPlainString(), plafond.toPlainString());

            CongeCalculateurEngine.SimulationResult simulation = congeService.simulerDemande(
                    employeId, typeExceptionnelId, null, Date.valueOf("2026-03-01"), Date.valueOf("2026-03-01"),
                    false, false, false, false, c);
            assertFalse(simulation.canSubmit());
            assertTrue(simulation.violations().contains(CongeCalculateurEngine.LEAVE_POOL_EXCEEDED));
            assertThrows(IllegalArgumentException.class, () -> creer(c, LocalDate.of(2026, 4, 1), 1));
        }
    }

    @Test
    @DisplayName("Validation transforme la réservation en consommation sans changer l'engagement")
    void validationSansDoubleComptage() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DemandeConge demande = creer(c, LocalDate.of(2026, 5, 1), 3);
            assertPool(c, 2026, "0", "3", "3");
            congeService.validerDemande(demande.getIdDemandeConge(), userId, "OK", c);
            assertPool(c, 2026, "3", "0", "3");
        }
    }

    @Test
    @DisplayName("Refus et annulation libèrent respectivement réservation et consommation")
    void refusEtAnnulationLiberentQuota() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DemandeConge refusee = creer(c, LocalDate.of(2026, 6, 1), 2);
            congeService.refuserDemande(refusee.getIdDemandeConge(), userId, "Refus", c);
            assertPool(c, 2026, "0", "0", "0");

            DemandeConge annulee = creer(c, LocalDate.of(2026, 7, 1), 3);
            congeService.validerDemande(annulee.getIdDemandeConge(), userId, "OK", c);
            assertPool(c, 2026, "3", "0", "3");
            congeService.annulerDemande(annulee.getIdDemandeConge(), userId, "Annulation", c);
            assertPool(c, 2026, "0", "0", "0");
        }
    }

    @Test
    @DisplayName("Modification remplace la réservation et gère entrée/sortie du pool")
    void modificationRemplaceReservationEtChangePool() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DemandeConge demande = creer(c, LocalDate.of(2026, 8, 1), 2);
            demande.setDateFin(Date.valueOf("2026-08-04"));
            demande.controler(c);
            demande.update(c);
            assertPool(c, 2026, "0", "4", "4");

            demande.setIdTypeConge(typePermissionId);
            demande.controler(c);
            demande.update(c);
            assertPool(c, 2026, "0", "0", "0");

            demande.setIdTypeConge(typeExceptionnelId);
            demande.controler(c);
            demande.update(c);
            assertPool(c, 2026, "0", "4", "4");
        }
    }

    @Test
    @DisplayName("Plusieurs demandes et motifs partagent le même pool")
    void plusieursMotifsPartagentPool() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            int mariage = CongeTestFixtureHelper.getMotifEvenementFamilialId(c, "MARIAGE");
            int naissance = CongeTestFixtureHelper.getMotifEvenementFamilialId(c, "NAISSANCE");
            creerAvecMotif(c, LocalDate.of(2026, 9, 1), mariage);
            creerAvecMotif(c, LocalDate.of(2026, 10, 1), naissance);
            assertPool(c, 2026, "0", "5", "5");
        }
    }

    @Test
    @DisplayName("Paternité est un droit séparé et ne consomme pas le pool Article 126")
    void paterniteEstHorsPoolPermissions() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            int typePaternite = CongeTestFixtureHelper.getTypeCongeId(c, "PATERNITE");
            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typePaternite);
            d.setDateDebut(Date.valueOf("2026-09-01"));
            d.setDateFin(Date.valueOf("2026-09-03"));
            congeService.creerDemande(d, userId, c);
            assertPool(c, 2026, "0", "0", "0");
        }
    }

    @Test
    @DisplayName("Nouvelle année isole le quota ; chevauchement reste imputé à l'année de début")
    void anneeCivileEtChevauchementDocumente() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            creer(c, LocalDate.of(2026, 12, 31), 3);
            assertPool(c, 2026, "0", "3", "3");
            assertPool(c, 2027, "0", "0", "0");
            creer(c, LocalDate.of(2027, 2, 1), 2);
            assertPool(c, 2027, "0", "2", "2");
        }
    }

    @Test
    @DisplayName("Deux réservations concurrentes sont sérialisées et ne peuvent dépasser le plafond")
    void reservationsConcurrentes() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            BigDecimal plafond = RefPoolCongeAnnuel.rechercherParId(poolId, c).getPlafondJoursAn();
            creer(c, LocalDate.of(2026, 1, 1), plafond.intValue() - 2);
        }
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        AtomicInteger succes = new AtomicInteger();
        AtomicInteger refus = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            final int jour = 10 + i * 3;
            executor.submit(() -> {
                try (Connection c = dataSource.getConnection()) {
                    c.setAutoCommit(false);
                    depart.await();
                    try {
                        creer(c, LocalDate.of(2026, 11, jour), 2);
                        c.commit();
                        succes.incrementAndGet();
                    } catch (Exception e) {
                        c.rollback();
                        refus.incrementAndGet();
                    }
                } catch (Exception e) {
                    refus.incrementAndGet();
                }
            });
        }
        depart.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        assertEquals(1, succes.get());
        assertEquals(1, refus.get());
        try (Connection c = dataSource.getConnection()) {
            BigDecimal plafond = RefPoolCongeAnnuel.rechercherParId(poolId, c).getPlafondJoursAn();
            assertEquals(0, plafond.compareTo(consommation(c, 2026).getJoursEngages()));
        }
    }

    private DemandeConge creer(Connection c, LocalDate debut, int jours) throws Exception {
        DemandeConge d = new DemandeConge();
        d.setIdEmploye(employeId);
        d.setIdTypeConge(typeExceptionnelId);
        d.setDateDebut(Date.valueOf(debut));
        d.setDateFin(Date.valueOf(debut.plusDays(jours - 1L)));
        return congeService.creerDemande(d, userId, c);
    }

    private DemandeConge creerAvecMotif(Connection c, LocalDate debut, int motifId) throws Exception {
        int jours = RefMotifEvenementFamilial.rechercherParId(motifId, c).getNbJoursAutorises().intValueExact();
        DemandeConge d = new DemandeConge();
        d.setIdEmploye(employeId);
        d.setIdTypeConge(typeExceptionnelId);
        d.setIdMotifEvenementFamilial(motifId);
        d.setDateDebut(Date.valueOf(debut));
        d.setDateFin(Date.valueOf(debut.plusDays(jours - 1L)));
        return congeService.creerDemande(d, userId, c);
    }

    private RefPoolCongeAnnuel.Consommation consommation(Connection c, int annee) throws Exception {
        return RefPoolCongeAnnuel.rechercherConsommation(employeId, poolId, annee, c);
    }

    private void assertPool(Connection c, int annee, String consomme, String attente, String engage) throws Exception {
        RefPoolCongeAnnuel.Consommation p = consommation(c, annee);
        assertNotNull(p);
        assertEquals(0, new BigDecimal(consomme).compareTo(p.getJoursConsommes()));
        assertEquals(0, new BigDecimal(attente).compareTo(p.getJoursEnAttente()));
        assertEquals(0, new BigDecimal(engage).compareTo(p.getJoursEngages()));
    }
}
