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
import mg.galaxam.sirh.conge.persistence.DemandeConge;
import mg.galaxam.sirh.conge.persistence.MouvementSoldeConge;
import mg.galaxam.sirh.conge.domain.PurgeFifoEngine;
import mg.galaxam.sirh.conge.application.CongeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Phase 2A-3D : Finalisation FIFO RG-CONGE-06 & Reprise des Soldes Historiques Négatifs")
public class Phase2a3dFifoFinalizationIntegrationTest extends BaseIntegrationTest {

    private TestEmployeFixtures.CreatedEmploye emp;

    @Autowired
    private CongeService congeService;

    @Autowired
    private PurgeCongeScheduler purgeScheduler;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        LocalDate dateEmbauche = LocalDate.now().minusYears(4);
        emp = employeFixtures.createActiveEmploye("EMP035", "RANDRIA", "Faly", 1, dateEmbauche);
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

    private int resolveTypeCongeId(String code) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_type_conge FROM ref_type_conge WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("Type de congé non trouvé : " + code);
    }

    @Test
    @DisplayName("Test 1 — RG-CONGE-06 Priorité Historique : SOLDE_INITIAL 10j + ACQ 2.5j, Prise 3j -> Hist restant 7j, Acq 2.5j, Solde 9.5j")
    public void test1_prioriteHistorique_priseConsommeHistoriqueEnPremier() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // 1. SOLDE_INITIAL 10.0
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("10.0"), LocalDate.of(2026, 8, 31),
                    false, "Reprise 10j", 1, c);

            // 2. Acquisition datée 2.5
            MouvementSoldeConge acq = new MouvementSoldeConge();
            acq.setIdEmploye(emp.idEmploye);
            acq.setTypeMouvement("ACQUISITION");
            acq.setNbJours(new BigDecimal("2.5"));
            acq.setDateMouvement(Date.valueOf(LocalDate.of(2026, 9, 1)));
            acq.setCommentaire("Acquisition 09/2026");
            acq.save(c);

            // 3. Prise de congé 3j
            MouvementSoldeConge prise = new MouvementSoldeConge();
            prise.setIdEmploye(emp.idEmploye);
            prise.setTypeMouvement("PRISE");
            prise.setNbJours(new BigDecimal("-3.0"));
            prise.setDateMouvement(Date.valueOf(LocalDate.of(2026, 9, 15)));
            prise.setCommentaire("Congé posé");
            prise.save(c);
        }

        assertEquals(9.5, getSoldeActuel(emp.idEmploye), 0.001);

        try (Connection c = dataSource.getConnection()) {
            PurgeFifoEngine.ImputationResult imp = congeService.getImputationSolde(emp.idEmploye, c);
            assertNotNull(imp);
            assertEquals(7.0, imp.getStockHistoriqueRestant(), 0.001, "Le stock historique est débité en priorité : 10 - 3 = 7j");
            assertEquals(2.5, imp.getTotalDroitsDatesRestants(), 0.001, "L'acquisition datée est 100% préservée : 2.5j");
            assertEquals(9.5, imp.getSoldeTotalDisponible(), 0.001);
            assertEquals(3.0, imp.getConsommationImputeeSurHistorique(), 0.001);
            assertEquals(0.0, imp.getConsommationImputeeSurDroitsDates(), 0.001);
        }
    }

    @Test
    @DisplayName("Test 2 — RG-CONGE-06 FIFO Daté : SOLDE_INITIAL 4j + JAN(2.5) + FEB(2.5) + MAR(2.5), Prise 7j -> Hist=0, JAN=0, FEB=2, MAR=2.5")
    public void test2_fifoDateApresEpuisementHistorique() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Stock historique 4j
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("4.0"), LocalDate.of(2025, 12, 31),
                    false, "Reprise 4j", 1, c);

            // 3 acquisitions datées successives (janvier, février, mars 2026)
            for (int mois = 1; mois <= 3; mois++) {
                MouvementSoldeConge acq = new MouvementSoldeConge();
                acq.setIdEmploye(emp.idEmploye);
                acq.setTypeMouvement("ACQUISITION");
                acq.setNbJours(new BigDecimal("2.5"));
                acq.setDateMouvement(Date.valueOf(LocalDate.of(2026, mois, 1)));
                acq.setCommentaire("Acquisition 0" + mois + "/2026");
                acq.save(c);
            }

            // Prise de 7j
            MouvementSoldeConge prise = new MouvementSoldeConge();
            prise.setIdEmploye(emp.idEmploye);
            prise.setTypeMouvement("PRISE");
            prise.setNbJours(new BigDecimal("-7.0"));
            prise.setDateMouvement(Date.valueOf(LocalDate.of(2026, 4, 10)));
            prise.setCommentaire("Prise de 7 jours");
            prise.save(c);
        }

        // Solde global : 4 + 2.5 + 2.5 + 2.5 - 7 = 4.5j
        assertEquals(4.5, getSoldeActuel(emp.idEmploye), 0.001);

        try (Connection c = dataSource.getConnection()) {
            PurgeFifoEngine.ImputationResult imp = congeService.getImputationSolde(emp.idEmploye, c);
            assertNotNull(imp);
            assertEquals(0.0, imp.getStockHistoriqueRestant(), 0.001, "Le stock historique de 4j est épuisé (4j imputés)");
            assertEquals(4.0, imp.getConsommationImputeeSurHistorique(), 0.001);
            assertEquals(3.0, imp.getConsommationImputeeSurDroitsDates(), 0.001);

            // Détails FIFO :
            // JAN (2.5j) : 2.5j consommés -> 0.0j restant
            assertEquals(0.0, imp.getDetailsDroitsDates().get(0).getNbJoursRestants(), 0.001);
            // FEB (2.5j) : 0.5j consommé -> 2.0j restants
            assertEquals(2.0, imp.getDetailsDroitsDates().get(1).getNbJoursRestants(), 0.001);
            // MAR (2.5j) : 0j consommé -> 2.5j restants
            assertEquals(2.5, imp.getDetailsDroitsDates().get(2).getNbJoursRestants(), 0.001);

            assertEquals(4.5, imp.getSoldeTotalDisponible(), 0.001);
        }
    }

    @Test
    @DisplayName("Test 3 — Expiration après FIFO : JAN consommé n'expire pas, seuls les 2.0j restants de FEB expirent")
    public void test3_expirationApresFifoNePurgeQueLeDisponible() throws Exception {
        test2_fifoDateApresEpuisementHistorique();

        // 1. Simulation au 15/01/2029 (seul JAN > 36 mois) :
        // Comme JAN a été intégralement consommé par la prise, 0j n'expire !
        try (Connection c = dataSource.getConnection()) {
            int nb = purgeScheduler.purgerCongesExpires(LocalDate.of(2029, 1, 15), c);
            assertEquals(0, nb, "Aucun droit à purger car JAN est déjà consommé !");
        }
        assertEquals(4.5, getSoldeActuel(emp.idEmploye), 0.001);

        // 2. Simulation au 15/02/2029 (FEB > 36 mois) :
        // FEB avait 2.5j dont 0.5j consommé -> seuls les 2.0j restants expirent !
        try (Connection c = dataSource.getConnection()) {
            int nb = purgeScheduler.purgerCongesExpires(LocalDate.of(2029, 2, 15), c);
            assertEquals(1, nb, "Un lot à purger pour FEB");
        }

        // Solde passe de 4.5j à 2.5j (seul MAR de 2.5j reste disponible)
        assertEquals(2.5, getSoldeActuel(emp.idEmploye), 0.001);

        // 3. Idempotence : Relancer la purge au 15/02/2029 ne doit rien purger de plus
        try (Connection c = dataSource.getConnection()) {
            int nb = purgeScheduler.purgerCongesExpires(LocalDate.of(2029, 2, 15), c);
            assertEquals(0, nb, "Idempotence : 0 nouvelle purge");
        }
        assertEquals(2.5, getSoldeActuel(emp.idEmploye), 0.001);
    }

    @Test
    @DisplayName("Test 4 — Restitution via ANNULATION : Restaure la capacité sans perte ni double crédit")
    public void test4_annulationDemandeRestaureCapacite() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("10.0"), LocalDate.of(2026, 8, 31),
                    false, "Reprise 10j", 1, c);

            MouvementSoldeConge acq = new MouvementSoldeConge();
            acq.setIdEmploye(emp.idEmploye);
            acq.setTypeMouvement("ACQUISITION");
            acq.setNbJours(new BigDecimal("2.5"));
            acq.setDateMouvement(Date.valueOf(LocalDate.of(2026, 9, 1)));
            acq.setCommentaire("Acq 09/2026");
            acq.save(c);

            MouvementSoldeConge prise = new MouvementSoldeConge();
            prise.setIdEmploye(emp.idEmploye);
            prise.setTypeMouvement("PRISE");
            prise.setNbJours(new BigDecimal("-3.0"));
            prise.setDateMouvement(Date.valueOf(LocalDate.of(2026, 9, 10)));
            prise.setCommentaire("Prise");
            prise.save(c);

            // Annulation de la prise
            MouvementSoldeConge annulation = new MouvementSoldeConge();
            annulation.setIdEmploye(emp.idEmploye);
            annulation.setTypeMouvement("ANNULATION");
            annulation.setNbJours(new BigDecimal("3.0"));
            annulation.setDateMouvement(Date.valueOf(LocalDate.of(2026, 9, 12)));
            annulation.setCommentaire("Annulation");
            annulation.save(c);
        }

        // Solde restitué : 10 + 2.5 - 3 + 3 = 12.5j
        assertEquals(12.5, getSoldeActuel(emp.idEmploye), 0.001);

        try (Connection c = dataSource.getConnection()) {
            PurgeFifoEngine.ImputationResult imp = congeService.getImputationSolde(emp.idEmploye, c);
            assertNotNull(imp);
            assertEquals(10.0, imp.getStockHistoriqueRestant(), 0.001, "Le stock historique de 10j est intégralement restauré");
            assertEquals(2.5, imp.getTotalDroitsDatesRestants(), 0.001, "L'acquisition de 2.5j est intacte");
            assertEquals(12.5, imp.getSoldeTotalDisponible(), 0.001);
            assertEquals(0.0, imp.getTotalConsommationNette(), 0.001);
        }
    }

    @Test
    @DisplayName("Test 5 — Reprise fidèle d'un solde initial négatif (-2.0j) et compensation par acquisition (+2.5j) -> +0.5j")
    public void test5_repriseSoldeInitialNegatifEtCompensationAcquisition() throws Exception {
        // 1. Reprise historique d'un solde négatif certifié dans l'Excel (-2.0j)
        try (Connection c = dataSource.getConnection()) {
            MouvementSoldeConge mvt = congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("-2.0"), LocalDate.of(2026, 8, 31),
                    false, "Reprise historique Excel avec solde négatif", 1, c);
            assertNotNull(mvt);
            assertEquals("AJUSTEMENT", mvt.getTypeMouvement());
            assertEquals(-2.0, mvt.getNbJours().doubleValue(), 0.001);
        }

        // Vérification de la fidélité en base
        assertEquals(-2.0, getSoldeActuel(emp.idEmploye), 0.001, "Le solde SIRH reflète fidèlement -2.0j");

        // Vérification : 0 acquisition fictive, 0 ligne dans conge_journal_droit
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM conge_journal_droit WHERE id_employe = ?")) {
            ps.setInt(1, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Aucun TCONGE ou journal de droit fictif");
            }
        }

        // 2. Arrivée de la première acquisition mensuelle (+2.5j)
        try (Connection c = dataSource.getConnection()) {
            MouvementSoldeConge acq = new MouvementSoldeConge();
            acq.setIdEmploye(emp.idEmploye);
            acq.setTypeMouvement("ACQUISITION");
            acq.setNbJours(new BigDecimal("2.5"));
            acq.setDateMouvement(Date.valueOf(LocalDate.of(2026, 9, 1)));
            acq.setCommentaire("Acquisition 09/2026");
            acq.save(c);
        }

        // Compensation naturelle du ledger : -2.0 + 2.5 = +0.5j
        assertEquals(0.5, getSoldeActuel(emp.idEmploye), 0.001);

        try (Connection c = dataSource.getConnection()) {
            PurgeFifoEngine.ImputationResult imp = congeService.getImputationSolde(emp.idEmploye, c);
            assertNotNull(imp);
            assertEquals(0.5, imp.getSoldeTotalDisponible(), 0.001);
            assertEquals(0.5, imp.getTotalDroitsDatesRestants(), 0.001, "0.5j disponibles après absorption de la dette de 2j");
        }
    }

    @Test
    @DisplayName("Test 6 — Contrôle des demandes après reprise négative : Solde 0.5j bloque une demande de 3j")
    public void test6_demandeBloqueeApresRepriseNegative() throws Exception {
        test5_repriseSoldeInitialNegatifEtCompensationAcquisition();

        // Salarié avec solde = 0.5j. Tentative de poser 3 jours sans dérogation
        DemandeConge d = new DemandeConge();
        d.setIdEmploye(emp.idEmploye);
        d.setIdTypeConge(resolveTypeCongeId("ANNUEL"));
        d.setDateDebut(Date.valueOf(LocalDate.of(2026, 10, 5)));
        d.setDateFin(Date.valueOf(LocalDate.of(2026, 10, 7))); // 3 jours
        d.setNbJoursDecomptes(new BigDecimal("3.0"));
        d.setDerogationSoldeNegatif(false);

        try (Connection c = dataSource.getConnection()) {
            Exception ex = assertThrows(IllegalArgumentException.class, () -> {
                d.controler(c);
            });
            assertTrue(ex.getMessage().contains("Solde de congé insuffisant"),
                    "Le solde de 0.5j bloque fermement une demande de 3j : aucun congé anticipé implicite");
        }
    }
}
