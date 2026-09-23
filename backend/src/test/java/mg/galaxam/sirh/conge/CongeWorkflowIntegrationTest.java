package mg.galaxam.sirh.conge;

import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import mg.galaxam.sirh.conge.persistence.DemandeConge;
import mg.galaxam.sirh.conge.persistence.RefStatutDemandeConge;
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
import java.sql.*;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CongeWorkflowIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeService congeService;

    private int adminUserId;
    private int employeId;
    private int typeAnnuelId;
    private int typeExceptionnelId;
    private int motifMariageId;
    private int motifNaissanceId;
    private int motifDecesId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
            adminUserId = CongeTestFixtureHelper.createAdminUser(c, "admin.rh@galaxam.mg");
            employeId = CongeTestFixtureHelper.createEmploye(c, "EMP-WF-001", "Rakoto", "Jean");
            int contratId = CongeTestFixtureHelper.createContratActif(c, employeId, LocalDate.of(2025, 1, 1));
            CongeTestFixtureHelper.createAffectation(c, employeId, contratId, LocalDate.of(2025, 1, 1), new BigDecimal("1500000.00"));

            typeAnnuelId = CongeTestFixtureHelper.getTypeCongeId(c, "ANNUEL");
            typeExceptionnelId = CongeTestFixtureHelper.getTypeCongeId(c, "EXCEPTIONNEL");
            motifMariageId = CongeTestFixtureHelper.getMotifEvenementFamilialId(c, "MARIAGE");
            motifNaissanceId = CongeTestFixtureHelper.getMotifEvenementFamilialId(c, "NAISSANCE");
            motifDecesId = CongeTestFixtureHelper.getMotifEvenementFamilialId(c, "DECES_CONJOINT_ENFANT");
        }
    }

    @Test
    @DisplayName("A-F. E2E Demande -> Simulation -> Soumission -> Validation -> Ledger PRISE -> Solde")
    void testEndToEndDemandeValidationLedger() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // 1. Créditer un solde initial de 20.0 jours
            CongeTestFixtureHelper.crediterSolde(c, employeId, 20.0, "Solde initial acquis");

            VSoldeCongeEmploye soldeInit = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertNotNull(soldeInit);
            assertEquals(0, new BigDecimal("20.0").compareTo(soldeInit.getSoldeActuel()));

            // 2. Simulation d'une demande de 5 jours ouvrables/calendaires
            LocalDate debut = LocalDate.of(2026, 11, 2);
            LocalDate fin = LocalDate.of(2026, 11, 6);
            CongeCalculateurEngine.SimulationResult sim = congeService.simulerDemande(
                    employeId, typeAnnuelId, null, Date.valueOf(debut), Date.valueOf(fin),
                    false, false, false, false, c);

            assertNotNull(sim);
            assertEquals(0, new BigDecimal("5.0").compareTo(sim.nbJours()));
            assertEquals(0, new BigDecimal("20.0").compareTo(sim.soldeAvant()));
            assertEquals(0, new BigDecimal("15.0").compareTo(sim.soldeApres()));
            assertTrue(sim.canSubmit());
            assertTrue(sim.violations().isEmpty());

            // 3. Création de la demande
            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeAnnuelId);
            d.setDateDebut(Date.valueOf(debut));
            d.setDateFin(Date.valueOf(fin));
            d.setCommentaire("Congés annuels de test");

            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);
            assertNotNull(cree.getIdDemandeConge());
            assertEquals(0, new BigDecimal("5.0").compareTo(cree.getNbJoursDecomptes()));
            assertEquals("EN_ATTENTE", cree.getStatutCode(c));
            assertEquals(adminUserId, cree.getSaisiPar());

            // 4. Validation
            DemandeConge validee = congeService.validerDemande(cree.getIdDemandeConge(), adminUserId, "Approuvé par DAF", c);
            assertEquals("VALIDE", validee.getStatutCode(c));
            assertEquals(adminUserId, validee.getValidePar());
            assertNotNull(validee.getDateValidation());

            // 5. Vérification du grand-livre (mouvement_solde_conge)
            String sqlMvt = "SELECT type_mouvement, nb_jours, id_demande_conge FROM mouvement_solde_conge WHERE id_demande_conge = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlMvt)) {
                ps.setInt(1, cree.getIdDemandeConge());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Un mouvement lié à la demande doit exister");
                    assertEquals("PRISE", rs.getString("type_mouvement"));
                    assertEquals(0, new BigDecimal("-5.0").compareTo(rs.getBigDecimal("nb_jours")));
                    assertEquals(cree.getIdDemandeConge().intValue(), rs.getInt("id_demande_conge"));
                    assertFalse(rs.next(), "Exactement un mouvement PRISE doit avoir été généré");
                }
            }

            // 6. Vérification du solde final
            VSoldeCongeEmploye soldeFinal = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertNotNull(soldeFinal);
            assertEquals(0, new BigDecimal("15.0").compareTo(soldeFinal.getSoldeActuel()));
        }
    }

    @Test
    @DisplayName("G. Annulation d'une demande validée : restitution solde + interdiction double annulation")
    void testAnnulationDemandeValideeEtIdempotence() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 15.0, "Solde initial");

            LocalDate debut = LocalDate.of(2026, 12, 1);
            LocalDate fin = LocalDate.of(2026, 12, 4); // 4 jours

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeAnnuelId);
            d.setDateDebut(Date.valueOf(debut));
            d.setDateFin(Date.valueOf(fin));
            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);
            congeService.validerDemande(cree.getIdDemandeConge(), adminUserId, "OK", c);

            // Solde après validation = 15 - 4 = 11
            VSoldeCongeEmploye soldeApresVal = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("11.0").compareTo(soldeApresVal.getSoldeActuel()));

            // Première annulation
            DemandeConge annulee = congeService.annulerDemande(cree.getIdDemandeConge(), adminUserId, "Annulation par salarié", c);
            assertEquals("ANNULE", annulee.getStatutCode(c));

            // Vérification du mouvement ANNULATION
            String sqlAnnul = "SELECT nb_jours FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'ANNULATION'";
            try (PreparedStatement ps = c.prepareStatement(sqlAnnul)) {
                ps.setInt(1, cree.getIdDemandeConge());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Mouvement ANNULATION attendu");
                    assertEquals(0, new BigDecimal("4.0").compareTo(rs.getBigDecimal("nb_jours")));
                    assertFalse(rs.next(), "Un seul mouvement d'annulation attendu");
                }
            }

            // Solde restauré à 15.0
            VSoldeCongeEmploye soldeApresAnnul = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("15.0").compareTo(soldeApresAnnul.getSoldeActuel()));

            // Deuxième annulation -> doit échouer (pas de double crédit)
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                congeService.annulerDemande(cree.getIdDemandeConge(), adminUserId, "Re-annulation", c);
            });
            assertTrue(ex.getMessage().contains("ne peut plus être annulée"));

            // Vérifier que le solde est toujours 15.0
            VSoldeCongeEmploye soldeInchange = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("15.0").compareTo(soldeInchange.getSoldeActuel()));
        }
    }

    @Test
    @DisplayName("H. Refus d'une demande EN_ATTENTE : statut REFUSE, motif présent, aucun mouvement solde")
    void testRefusDemande() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 10.0, "Solde 10j");

            LocalDate debut = LocalDate.of(2026, 11, 10);
            LocalDate fin = LocalDate.of(2026, 11, 12);

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeAnnuelId);
            d.setDateDebut(Date.valueOf(debut));
            d.setDateFin(Date.valueOf(fin));
            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);

            DemandeConge refusee = congeService.refuserDemande(cree.getIdDemandeConge(), adminUserId, "Effectif insuffisant sur site", c);
            assertEquals("REFUSE", refusee.getStatutCode(c));
            assertEquals("Effectif insuffisant sur site", refusee.getMotifRefus());
            assertEquals(adminUserId, refusee.getValidePar());

            // Aucun mouvement PRISE
            String sqlCheck = "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_demande_conge = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
                ps.setInt(1, cree.getIdDemandeConge());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Aucun mouvement de solde ne doit exister pour une demande refusée");
                }
            }

            // Solde toujours 10.0
            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("10.0").compareTo(solde.getSoldeActuel()));
        }
    }

    @Test
    @DisplayName("J. Demi-journée : Matin, Après-midi, décompte 0.5j et impact solde")
    void testDemiJournee() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 10.0, "Solde 10j");

            LocalDate dateDj = LocalDate.of(2026, 11, 20);

            // Simulation demi-journée
            CongeCalculateurEngine.SimulationResult sim = congeService.simulerDemande(
                    employeId, typeAnnuelId, null, Date.valueOf(dateDj), Date.valueOf(dateDj),
                    true, false, false, false, c);
            assertEquals(0, new BigDecimal("0.5").compareTo(sim.nbJours()));
            assertEquals(0, new BigDecimal("9.5").compareTo(sim.soldeApres()));

            // Demande demi-journée MATIN
            DemandeConge dMatin = new DemandeConge();
            dMatin.setIdEmploye(employeId);
            dMatin.setIdTypeConge(typeAnnuelId);
            dMatin.setDateDebut(Date.valueOf(dateDj));
            dMatin.setDateFin(Date.valueOf(dateDj));
            dMatin.setEstDemiJournee(true);
            dMatin.setMomentDemiJournee("MATIN");

            DemandeConge cree = congeService.creerDemande(dMatin, adminUserId, c);
            assertEquals(0, new BigDecimal("0.5").compareTo(cree.getNbJoursDecomptes()));

            congeService.validerDemande(cree.getIdDemandeConge(), adminUserId, "Validation 0.5j", c);

            // Vérification ledger : -0.5 jour
            String sqlMvt = "SELECT nb_jours FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'PRISE'";
            try (PreparedStatement ps = c.prepareStatement(sqlMvt)) {
                ps.setInt(1, cree.getIdDemandeConge());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, new BigDecimal("-0.5").compareTo(rs.getBigDecimal("nb_jours")));
                }
            }

            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("9.5").compareTo(solde.getSoldeActuel()));
        }
    }

    @Test
    @DisplayName("K. Pool Annuel Partagé : consommation progressive jusqu'au plafond (10j) et alerte/blocage au dépassement")
    void testPoolAnnuelPartage() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Types de congés exceptionnels rattachés au pool annuel (10 jours max par an)
            // Demande 1 : Mariage (3j)
            DemandeConge d1 = new DemandeConge();
            d1.setIdEmploye(employeId);
            d1.setIdTypeConge(typeExceptionnelId);
            d1.setIdMotifEvenementFamilial(motifMariageId);
            d1.setDateDebut(Date.valueOf(LocalDate.of(2026, 2, 2)));
            d1.setDateFin(Date.valueOf(LocalDate.of(2026, 2, 4)));
            DemandeConge c1 = congeService.creerDemande(d1, adminUserId, c);
            congeService.validerDemande(c1.getIdDemandeConge(), adminUserId, "Mariage validé", c);

            // Demande 2 : Naissance (2j)
            DemandeConge d2 = new DemandeConge();
            d2.setIdEmploye(employeId);
            d2.setIdTypeConge(typeExceptionnelId);
            d2.setIdMotifEvenementFamilial(motifNaissanceId);
            d2.setDateDebut(Date.valueOf(LocalDate.of(2026, 4, 6)));
            d2.setDateFin(Date.valueOf(LocalDate.of(2026, 4, 7)));
            DemandeConge c2 = congeService.creerDemande(d2, adminUserId, c);
            congeService.validerDemande(c2.getIdDemandeConge(), adminUserId, "Naissance validée", c);

            // Demande 3 : Décès conjoint/enfant (3j)
            DemandeConge d3 = new DemandeConge();
            d3.setIdEmploye(employeId);
            d3.setIdTypeConge(typeExceptionnelId);
            d3.setIdMotifEvenementFamilial(motifDecesId);
            d3.setDateDebut(Date.valueOf(LocalDate.of(2026, 6, 1)));
            d3.setDateFin(Date.valueOf(LocalDate.of(2026, 6, 3)));
            DemandeConge c3 = congeService.creerDemande(d3, adminUserId, c);
            congeService.validerDemande(c3.getIdDemandeConge(), adminUserId, "Décès validé", c);

            // Demande 4 : Naissance 2 (2j) -> Total cumulé = 3 + 2 + 3 + 2 = 10j = EXACTEMENT le plafond
            DemandeConge d4 = new DemandeConge();
            d4.setIdEmploye(employeId);
            d4.setIdTypeConge(typeExceptionnelId);
            d4.setIdMotifEvenementFamilial(motifNaissanceId);
            d4.setDateDebut(Date.valueOf(LocalDate.of(2026, 8, 3)));
            d4.setDateFin(Date.valueOf(LocalDate.of(2026, 8, 4)));
            DemandeConge c4 = congeService.creerDemande(d4, adminUserId, c);
            congeService.validerDemande(c4.getIdDemandeConge(), adminUserId, "Plafond pile atteint (10j)", c);

            // Simulation d'une 5ème demande (Mariage 3j) sur la même année 2026 -> Doit détecter LEAVE_POOL_EXCEEDED
            LocalDate deb5 = LocalDate.of(2026, 10, 5);
            LocalDate fin5 = LocalDate.of(2026, 10, 7);
            CongeCalculateurEngine.SimulationResult simOver = congeService.simulerDemande(
                    employeId, typeExceptionnelId, motifMariageId,
                    Date.valueOf(deb5), Date.valueOf(fin5), false, false, false, false, c);

            assertEquals(0, new BigDecimal("10.0").compareTo(simOver.quotaAvant()));
            assertEquals(0, new BigDecimal("13.0").compareTo(simOver.quotaApres()));
            assertFalse(simOver.canSubmit(), "La demande ne doit pas pouvoir être soumise sans dérogation");
            assertTrue(simOver.violations().contains("LEAVE_POOL_EXCEEDED"));
        }
    }

    @Test
    @DisplayName("L. Solde négatif : sans dérogation vs sous dérogation (-10j max)")
    void testSoldeNegatifEtDerogation() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 2.0, "Solde 2j");

            LocalDate debut = LocalDate.of(2026, 11, 2);
            LocalDate fin = LocalDate.of(2026, 11, 6); // 5 jours -> solde projeté = 2 - 5 = -3j

            // Simulation sans dérogation
            CongeCalculateurEngine.SimulationResult simSans = congeService.simulerDemande(
                    employeId, typeAnnuelId, null, Date.valueOf(debut), Date.valueOf(fin),
                    false, false, false, false, c);
            assertFalse(simSans.canSubmit());
            assertTrue(simSans.violations().contains("LEAVE_BALANCE_INSUFFICIENT"));

            // Simulation avec dérogation
            CongeCalculateurEngine.SimulationResult simAvec = congeService.simulerDemande(
                    employeId, typeAnnuelId, null, Date.valueOf(debut), Date.valueOf(fin),
                    false, true, false, false, c);
            assertTrue(simAvec.canSubmit());
            assertTrue(simAvec.warnings().contains("LEAVE_BALANCE_INSUFFICIENT"));

            // Tentative de création sans dérogation -> bloquée immédiatement par controler()
            DemandeConge dSans = new DemandeConge();
            dSans.setIdEmploye(employeId);
            dSans.setIdTypeConge(typeAnnuelId);
            dSans.setDateDebut(Date.valueOf(debut));
            dSans.setDateFin(Date.valueOf(fin));
            dSans.setDerogationDelaiPreavis(true); // neutraliser préavis pour tester solde

            IllegalArgumentException exSans = assertThrows(IllegalArgumentException.class, () -> {
                congeService.creerDemande(dSans, adminUserId, c);
            });
            assertTrue(exSans.getMessage().contains("Solde de congé insuffisant"));

            // Création avec dérogation solde négatif activée
            DemandeConge dAvec = new DemandeConge();
            dAvec.setIdEmploye(employeId);
            dAvec.setIdTypeConge(typeAnnuelId);
            dAvec.setDateDebut(Date.valueOf(debut));
            dAvec.setDateFin(Date.valueOf(fin));
            dAvec.setDerogationSoldeNegatif(true);
            dAvec.setDerogationDelaiPreavis(true);

            DemandeConge c1 = congeService.creerDemande(dAvec, adminUserId, c);
            DemandeConge v1 = congeService.validerDemande(c1.getIdDemandeConge(), adminUserId, "Validation avec dérogation", c);
            assertEquals("VALIDE", v1.getStatutCode(c));

            VSoldeCongeEmploye soldeNeg = VSoldeCongeEmploye.rechercherParEmploye(employeId, c);
            assertEquals(0, new BigDecimal("-3.0").compareTo(soldeNeg.getSoldeActuel()));

            // Demande supplémentaire dépassant le plafond légal de -10j (ex: 15j supplémentaires -> solde projeté -18j)
            DemandeConge dTrop = new DemandeConge();
            dTrop.setIdEmploye(employeId);
            dTrop.setIdTypeConge(typeAnnuelId);
            dTrop.setDateDebut(Date.valueOf(LocalDate.of(2026, 12, 1)));
            dTrop.setDateFin(Date.valueOf(LocalDate.of(2026, 12, 15)));
            dTrop.setDerogationSoldeNegatif(true);
            dTrop.setDerogationDelaiPreavis(true);
            DemandeConge cTrop = congeService.creerDemande(dTrop, adminUserId, c);

            IllegalArgumentException exPlafond = assertThrows(IllegalArgumentException.class, () -> {
                congeService.validerDemande(cTrop.getIdDemandeConge(), adminUserId, "Tentative dépassement plafond négatif", c);
            });
            assertTrue(exPlafond.getMessage().contains("plafond de solde négatif autorisé est dépassé"));
        }
    }

    @Test
    @DisplayName("Période de paie clôturée : blocage de l'annulation et de la validation")
    void testPeriodePaieClotureeBloqueOperations() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 20.0, "Solde 20j");

            LocalDate debut = LocalDate.of(2026, 10, 19); // > 15 jours de préavis
            LocalDate fin = LocalDate.of(2026, 10, 23);

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeAnnuelId);
            d.setDateDebut(Date.valueOf(debut));
            d.setDateFin(Date.valueOf(fin));
            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);
            congeService.validerDemande(cree.getIdDemandeConge(), adminUserId, "Validation normale", c);

            // Clôturer la période de paie d'octobre 2026
            CongeTestFixtureHelper.createPeriodePaie(c, "2026-10", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "CLOTUREE");

            // Tentative d'annulation sur période clôturée -> bloquée
            IllegalStateException exAnnul = assertThrows(IllegalStateException.class, () -> {
                congeService.annulerDemande(cree.getIdDemandeConge(), adminUserId, "Annulation tardive", c);
            });
            assertTrue(exAnnul.getMessage().contains("Action interdite sur la période de paie clôturée"));
        }
    }
}
