package mg.galaxam.sirh.integration.conge;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.cron.PurgeCongeScheduler;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.model.conge.MouvementSoldeConge;
import mg.galaxam.sirh.model.conge.VSoldeCongeEmploye;
import mg.galaxam.sirh.model.conge.service.CongeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Phase 2A-3C : Sécurisation Purge / Expiration des Droits, FIFO & Protection SOLDE_INITIAL")
public class Phase2a3cPurgeExpirationIntegrationTest extends BaseIntegrationTest {

    private TestEmployeFixtures.CreatedEmploye emp;

    @Autowired
    private CongeService congeService;

    @Autowired
    private PurgeCongeScheduler purgeScheduler;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        LocalDate dateEmbauche = LocalDate.now().minusYears(4);
        emp = employeFixtures.createActiveEmploye("EMP031", "RAZAFY", "Tojo", 1, dateEmbauche);
    }

    private double getSoldeActuel(int idEmploye) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT solde_actuel FROM v_solde_conge_employe WHERE id_employe = ?")) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("solde_actuel");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 0.0;
    }

    private double getTotalExpire(int idEmploye) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT total_expire FROM v_solde_conge_employe WHERE id_employe = ?")) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("total_expire");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 0.0;
    }

    @Test
    @DisplayName("Test A — Solde initial seul : 17.5j arrêté au 31/08/2026 ne subit AUCUNE purge même 3 ans plus tard")
    public void testA_soldeInitialSeul_neSubitAucuneExpirationApresTroisAns() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("17.5"), LocalDate.of(2026, 8, 31),
                    false, "Reprise historique initiale", 1, c);
        }

        assertEquals(17.5, getSoldeActuel(emp.idEmploye), 0.001);

        // Simulation temporelle : Exécuter la purge au 01/09/2029 (> 36 mois après la date d'arrêté 31/08/2026)
        try (Connection c = dataSource.getConnection()) {
            int nbPurges = purgeScheduler.purgerCongesExpires(LocalDate.of(2029, 9, 1), c);
            assertEquals(0, nbPurges, "Aucun droit ne doit être purgé pour un SOLDE_INITIAL à ancienneté inconnue");
        }

        // Vérification de l'intégrité du solde
        assertEquals(17.5, getSoldeActuel(emp.idEmploye), 0.001, "Le solde initial de 17.5 reste intégralement disponible");
        assertEquals(0.0, getTotalExpire(emp.idEmploye), 0.001, "Total expiré doit être 0.0");

        // Vérification dans le ledger
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'EXPIRATION'")) {
            ps.setInt(1, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Aucun mouvement d'EXPIRATION ne doit exister");
            }
        }
    }

    @Test
    @DisplayName("Test B — Solde initial + acquisition datée : Seule l'acquisition datée ancienne expire")
    public void testB_soldeInitialEtAcquisitionDatee_seuleAcquisitionExpire() throws Exception {
        // 1. Solde initial à ancienneté inconnue = 10.0 (arrêté au 31/08/2026)
        try (Connection c = dataSource.getConnection()) {
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("10.0"), LocalDate.of(2026, 8, 31),
                    false, "Reprise 10j", 1, c);
        }

        // 2. Acquisition datée authentique = +2.5 (août 2026)
        try (Connection c = dataSource.getConnection()) {
            MouvementSoldeConge acq = new MouvementSoldeConge();
            acq.setIdEmploye(emp.idEmploye);
            acq.setTypeMouvement("ACQUISITION");
            acq.setNbJours(new BigDecimal("2.5"));
            acq.setDateMouvement(Date.valueOf(LocalDate.of(2026, 8, 1)));
            acq.setCommentaire("Acquisition mensuelle 08/2026");
            acq.save(c);
        }

        assertEquals(12.5, getSoldeActuel(emp.idEmploye), 0.001);

        // 3. Purge simulée au 01/09/2029 (> 36 mois après l'acquisition du 01/08/2026)
        try (Connection c = dataSource.getConnection()) {
            int nbPurges = purgeScheduler.purgerCongesExpires(LocalDate.of(2029, 9, 1), c);
            assertEquals(1, nbPurges, "Un employé purgé");
        }

        // Attendu : Le solde passe de 12.5 à 10.0 (les 2.5j datés sont purgés, les 10j historiques restent intacts)
        assertEquals(10.0, getSoldeActuel(emp.idEmploye), 0.001);
        assertEquals(2.5, getTotalExpire(emp.idEmploye), 0.001);
    }

    @Test
    @DisplayName("Test C — Pas de date d'expiration fictive pour le solde initial")
    public void testC_pasDeDateExpirationFictivePourSoldeInitial() throws Exception {
        LocalDate dateArrete = LocalDate.of(2026, 8, 31);
        try (Connection c = dataSource.getConnection()) {
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("17.5"), dateArrete,
                    false, "Données historiques", 1, c);
        }

        try (Connection c = dataSource.getConnection()) {
            // 1. Vérifier la vue VSoldeCongeEmploye : derniere_acquisition doit être NULL
            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(emp.idEmploye, c);
            assertNotNull(solde);
            assertNull(solde.getDerniereAcquisition(), "La date d'arrêté n'est JAMAIS transformée en date d'acquisition");

            // 2. Vérifier qu'aucune date d'expiration n'est attachée au mouvement
            MouvementSoldeConge mvt = MouvementSoldeConge.listParEmploye(emp.idEmploye, c).get(0);
            assertEquals("AJUSTEMENT", mvt.getTypeMouvement());
            assertTrue(mvt.getCommentaire().startsWith("[SOLDE_INITIAL]"));
            assertEquals(Date.valueOf(dateArrete), mvt.getDateMouvement(), "La date du mouvement est la date de constat");
        }
    }

    @Test
    @DisplayName("Test D — Acquisition normale datée participe normalement au moteur de purge")
    public void testD_acquisitionNormaleParticipeToujoursAuMoteurDePurge() throws Exception {
        // Enregistrement de deux acquisitions successives
        try (Connection c = dataSource.getConnection()) {
            MouvementSoldeConge acq1 = new MouvementSoldeConge();
            acq1.setIdEmploye(emp.idEmploye);
            acq1.setTypeMouvement("ACQUISITION");
            acq1.setNbJours(new BigDecimal("2.5"));
            acq1.setDateMouvement(Date.valueOf(LocalDate.of(2026, 1, 15)));
            acq1.setCommentaire("Acquisition 01/2026");
            acq1.save(c);

            MouvementSoldeConge acq2 = new MouvementSoldeConge();
            acq2.setIdEmploye(emp.idEmploye);
            acq2.setTypeMouvement("ACQUISITION");
            acq2.setNbJours(new BigDecimal("2.5"));
            acq2.setDateMouvement(Date.valueOf(LocalDate.of(2026, 2, 15)));
            acq2.setCommentaire("Acquisition 02/2026");
            acq2.save(c);
        }

        assertEquals(5.0, getSoldeActuel(emp.idEmploye), 0.001);

        // Date de référence : 2029-01-20 (plus de 36 mois après 2026-01-15, mais moins de 36 mois après 2026-02-15)
        try (Connection c = dataSource.getConnection()) {
            purgeScheduler.purgerCongesExpires(LocalDate.of(2029, 1, 20), c);
        }

        // Seule la première acquisition (2.5) doit être expirée
        assertEquals(2.5, getSoldeActuel(emp.idEmploye), 0.001);
        assertEquals(2.5, getTotalExpire(emp.idEmploye), 0.001);

        // Date de référence suivante : 2029-02-20 (la seconde acquisition dépasse 36 mois à son tour)
        try (Connection c = dataSource.getConnection()) {
            purgeScheduler.purgerCongesExpires(LocalDate.of(2029, 2, 20), c);
        }

        // La seconde acquisition est expirée
        assertEquals(0.0, getSoldeActuel(emp.idEmploye), 0.001);
        assertEquals(5.0, getTotalExpire(emp.idEmploye), 0.001);
    }

    @Test
    @DisplayName("Test E — Ledger append-only : L'expiration produit une nouvelle ligne sans muter l'acquisition originale")
    public void testE_expirationProduitEcritureAppendOnlySansMutaterAcquisition() throws Exception {
        int idAcquisitionOriginale;
        try (Connection c = dataSource.getConnection()) {
            MouvementSoldeConge acq = new MouvementSoldeConge();
            acq.setIdEmploye(emp.idEmploye);
            acq.setTypeMouvement("ACQUISITION");
            acq.setNbJours(new BigDecimal("2.5"));
            acq.setDateMouvement(Date.valueOf(LocalDate.of(2026, 1, 1)));
            acq.setCommentaire("Acquisition 01/2026");
            acq.save(c);
            idAcquisitionOriginale = acq.getIdMouvement();
        }

        // Purge à T + 37 mois
        try (Connection c = dataSource.getConnection()) {
            purgeScheduler.purgerCongesExpires(LocalDate.of(2029, 2, 1), c);
        }

        try (Connection c = dataSource.getConnection()) {
            // L'acquisition d'origine est strictement intacte
            MouvementSoldeConge acqOriginale = MouvementSoldeConge.rechercherParId(idAcquisitionOriginale, c);
            assertNotNull(acqOriginale);
            assertEquals("ACQUISITION", acqOriginale.getTypeMouvement());
            assertEquals(2.5, acqOriginale.getNbJours().doubleValue(), 0.001);

            // Une nouvelle écriture EXPIRATION de -2.5 existe dans le ledger
            PreparedStatement ps = c.prepareStatement(
                    "SELECT nb_jours, commentaire FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'EXPIRATION'");
            ps.setInt(1, emp.idEmploye);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Une ligne EXPIRATION doit exister");
            assertEquals(-2.5, rs.getDouble("nb_jours"), 0.001);
            assertTrue(rs.getString("commentaire").contains("Purge légale FIFO"));
        }
    }

    @Test
    @DisplayName("Test F — Distinction nette : AJUSTEMENT ordinaire vs SOLDE_INITIAL")
    public void testF_ajustementOrdinaireDistingueDeSoldeInitial() throws Exception {
        TestEmployeFixtures.CreatedEmploye emp2 = employeFixtures.createActiveEmploye(
                "EMP032", "ANDRY", "Koloina", 1, LocalDate.now().minusYears(2));

        try (Connection c = dataSource.getConnection()) {
            // 1. Salarié 1 : Reprise historique SOLDE_INITIAL
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("17.5"), LocalDate.of(2026, 8, 31),
                    false, "Reprise Excel", 1, c);

            // 2. Salarié 2 : Ajustement administratif ordinaire
            MouvementSoldeConge mvtOrdinaire = new MouvementSoldeConge();
            mvtOrdinaire.setIdEmploye(emp2.idEmploye);
            mvtOrdinaire.setTypeMouvement("AJUSTEMENT");
            mvtOrdinaire.setNbJours(new BigDecimal("2.0"));
            mvtOrdinaire.setDateMouvement(Date.valueOf(LocalDate.of(2026, 9, 10)));
            mvtOrdinaire.setCommentaire("Régularisation RH suite erreur de pointage");
            mvtOrdinaire.setCreatedBy(1);
            mvtOrdinaire.save(c);
        }

        try (Connection c = dataSource.getConnection()) {
            MouvementSoldeConge m1 = MouvementSoldeConge.listParEmploye(emp.idEmploye, c).get(0);
            MouvementSoldeConge m2 = MouvementSoldeConge.listParEmploye(emp2.idEmploye, c).get(0);

            assertTrue(m1.getCommentaire().startsWith("[SOLDE_INITIAL]"), "Employé 1 porte le tag structurant [SOLDE_INITIAL]");
            assertFalse(m2.getCommentaire().startsWith("[SOLDE_INITIAL]"), "Employé 2 est un ajustement ordinaire sans tag initial");
        }
    }

    @Test
    @DisplayName("Test G — Restitution via ANNULATION : Recrédite la consommation et protège contre une fausse purge")
    public void testG_annulationDemandeRecrediteConsommation() throws Exception {
        // Droit ancien = 5.0
        try (Connection c = dataSource.getConnection()) {
            MouvementSoldeConge acq = new MouvementSoldeConge();
            acq.setIdEmploye(emp.idEmploye);
            acq.setTypeMouvement("ACQUISITION");
            acq.setNbJours(new BigDecimal("5.0"));
            acq.setDateMouvement(Date.valueOf(LocalDate.of(2026, 1, 1)));
            acq.setCommentaire("Acquisition 5j");
            acq.save(c);

            // Prise de 5j
            MouvementSoldeConge prise = new MouvementSoldeConge();
            prise.setIdEmploye(emp.idEmploye);
            prise.setTypeMouvement("PRISE");
            prise.setNbJours(new BigDecimal("-5.0"));
            prise.setDateMouvement(Date.valueOf(LocalDate.of(2026, 6, 1)));
            prise.setCommentaire("Congé posé");
            prise.save(c);

            // Annulation consécutive (restitution +5j)
            MouvementSoldeConge annulation = new MouvementSoldeConge();
            annulation.setIdEmploye(emp.idEmploye);
            annulation.setTypeMouvement("ANNULATION");
            annulation.setNbJours(new BigDecimal("5.0"));
            annulation.setDateMouvement(Date.valueOf(LocalDate.of(2026, 6, 2)));
            annulation.setCommentaire("Annulation demande #99");
            annulation.save(c);
        }

        assertEquals(5.0, getSoldeActuel(emp.idEmploye), 0.001);

        // Purge à T + 37 mois : Comme le congé a été annulé, la consommation réelle est de 0j.
        // Les 5.0j anciens n'ont pas été consommés et doivent donc être purgés.
        try (Connection c = dataSource.getConnection()) {
            int nb = purgeScheduler.purgerCongesExpires(LocalDate.of(2029, 2, 1), c);
            assertEquals(1, nb);
        }

        assertEquals(0.0, getSoldeActuel(emp.idEmploye), 0.001);
        assertEquals(5.0, getTotalExpire(emp.idEmploye), 0.001);
    }
}
