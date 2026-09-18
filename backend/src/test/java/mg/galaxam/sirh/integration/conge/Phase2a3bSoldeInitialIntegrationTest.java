package mg.galaxam.sirh.integration.conge;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.model.conge.DemandeConge;
import mg.galaxam.sirh.model.conge.MouvementSoldeConge;
import mg.galaxam.sirh.model.conge.service.CongeService;
import mg.galaxam.sirh.model.paie.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.service.StcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Phase 2A-3B : Initialisation des Soldes Historiques, Cutover & Decoupling Pointage")
public class Phase2a3bSoldeInitialIntegrationTest extends BaseIntegrationTest {

    private TestEmployeFixtures.CreatedEmploye emp;

    @Autowired
    private CongeService congeService;

    @Autowired
    private PayrollService payrollService;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        LocalDate dateEmbauche = LocalDate.now().minusYears(2);
        emp = employeFixtures.createActiveEmploye("EMP030", "RABARIJAONA", "Hery", 1, dateEmbauche);
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

    @Test
    @DisplayName("Cas A : Solde initial = 2j sans historique -> Solde = 2, aucune acquisition fictive ni journal de droit fictif")
    public void testCasA_SoldeInitial2JoursSansHistorique() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            MouvementSoldeConge mvt = congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("2.0"), LocalDate.of(2026, 8, 31),
                    false, "Reprise historique initiale", 1, c);
            assertNotNull(mvt);
            assertEquals("AJUSTEMENT", mvt.getTypeMouvement());
        }

        // 1. Solde actuel = 2.0
        assertEquals(2.0, getSoldeActuel(emp.idEmploye), 0.001);

        // 2. Vérifier qu'AUCUN mouvement de type ACQUISITION n'a été créé
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'ACQUISITION'")) {
            ps.setInt(1, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Aucune ligne d'acquisition fictive");
            }
        }

        // 3. Vérifier qu'AUCUNE ligne n'a été inventée dans conge_journal_droit
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM conge_journal_droit WHERE id_employe = ?")) {
            ps.setInt(1, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Aucune ligne dans conge_journal_droit");
            }
        }
    }

    @Test
    @DisplayName("Cas B : Solde initial = 17.5j -> 1 seule écriture de reprise, pas de 7 acquisitions artificielles")
    public void testCasB_SoldeInitial17Point5JoursSansMultiAcquisitions() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("17.5"), LocalDate.of(2026, 8, 31),
                    false, "Reprise historique Galaxam avant SIRH", 1, c);
        }

        assertEquals(17.5, getSoldeActuel(emp.idEmploye), 0.001);

        // Vérifier qu'il y a exactement 1 seule ligne dans mouvement_solde_conge
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*), type_mouvement, nb_jours FROM mouvement_solde_conge WHERE id_employe = ? GROUP BY type_mouvement, nb_jours")) {
            ps.setInt(1, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Exactement 1 ligne au total");
                assertEquals("AJUSTEMENT", rs.getString("type_mouvement"));
                assertEquals(17.5, rs.getDouble("nb_jours"), 0.001);
                assertFalse(rs.next(), "Aucune autre ligne");
            }
        }
    }

    @Test
    @DisplayName("Cas C : Solde initial = 17.5j + nouvelle acquisition réelle = 2.5j -> Solde = 20j avec provenances distinctes")
    public void testCasC_SoldeInitialPlusNouvelleAcquisition() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("17.5"), LocalDate.of(2026, 8, 31),
                    false, "Reprise historique au 31/08", 1, c);

            // Nouvelle acquisition réelle en Septembre 2026
            BigDecimal droitSept = congeService.enregistrerAcquisitionMensuelle(
                    emp.idEmploye, 2026, 9, BigDecimal.ZERO, 1, c);
            assertEquals(0, droitSept.compareTo(new BigDecimal("2.50")));
        }

        // Solde = 17.5 + 2.5 = 20.0
        assertEquals(20.0, getSoldeActuel(emp.idEmploye), 0.001);

        // Vérifier 2 écritures bien distinctes
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT type_mouvement, nb_jours FROM mouvement_solde_conge WHERE id_employe = ? ORDER BY date_mouvement ASC")) {
            ps.setInt(1, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("AJUSTEMENT", rs.getString("type_mouvement"));
                assertEquals(17.5, rs.getDouble("nb_jours"), 0.001);

                assertTrue(rs.next());
                assertEquals("ACQUISITION", rs.getString("type_mouvement"));
                assertEquals(2.5, rs.getDouble("nb_jours"), 0.001);

                assertFalse(rs.next());
            }
        }
    }

    @Test
    @DisplayName("Cas D : Solde initial = 17.5j, congé validé = 3j -> Solde = 14.5j, NBCONGE = 3j")
    public void testCasD_SoldeInitialEtPriseConge() throws Exception {
        int idDemande;
        try (Connection c = dataSource.getConnection()) {
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("17.5"), LocalDate.of(2026, 8, 31),
                    false, "Reprise historique", 1, c);

            // Congé du Vendredi 18/09/2026 au Dimanche 20/09/2026 (3 jours calendaires)
            DemandeConge d = new DemandeConge();
            d.setIdEmploye(emp.idEmploye);
            d.setIdTypeConge(resolveTypeCongeId("ANNUEL"));
            d.setDateDebut(Date.valueOf(LocalDate.of(2026, 9, 18)));
            d.setDateFin(Date.valueOf(LocalDate.of(2026, 9, 20)));
            d.setDerogationDelaiPreavis(true);
            d.setCommentaire("Congé après migration");
            congeService.creerDemande(d, 1, c);
            idDemande = d.getIdDemandeConge();
            congeService.validerDemande(idDemande, 1, "Validation", c);
        }

        // Solde = 17.5 - 3 = 14.5
        assertEquals(14.5, getSoldeActuel(emp.idEmploye), 0.001);

        // NBCONGE valorisé en Paie pour Septembre = 3.0 jours
        try (Connection c = dataSource.getConnection()) {
            BigDecimal nbConge = congeService.calculerNbJoursCongeValorises(
                    emp.idEmploye, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), c);
            assertEquals(0, nbConge.compareTo(new BigDecimal("3.0")));
        }
    }

    @Test
    @DisplayName("Cas E : Solde historique connu, aucun historique TCONGE -> Fallback CURRENT_INITIALIZATION_RULE sans historique fictif")
    public void testCasE_SoldeHistoriqueSansHistoriqueTconge() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("17.5"), LocalDate.of(2026, 8, 31),
                    false, "Reprise", 1, c);

            BigDecimal salaireBase = new BigDecimal("600000.00");
            LocalDate dateRef = LocalDate.of(2026, 9, 1);

            // Résolution TCONGE : aucun historique dans conge_journal_droit -> Fallback Base / 30.0 = 20 000.00 Ar
            BigDecimal taux = StcService.getTauxJournalierApplicable(emp.idEmploye, dateRef, salaireBase, c);
            assertEquals(0, taux.compareTo(new BigDecimal("20000.00")));

            // Vérifier qu'aucun enregistrement n'a été créé dans conge_journal_droit
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM conge_journal_droit WHERE id_employe = ?")) {
                ps.setInt(1, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Strictement aucun faux historique généré");
                }
            }
        }
    }

    @Test
    @DisplayName("Test Cutover : 31/08 autorise Septembre, 30/09 (avec mention inclut) bloque double acquisition")
    public void testCutoverEtNonDuplication() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Salarié 1 : Solde arrêté au 31/08 -> acquisition de Septembre autorisée
            congeService.initialiserSoldeHistorique(
                    emp.idEmploye, new BigDecimal("10.0"), LocalDate.of(2026, 8, 31),
                    false, "Arrêté au 31/08", 1, c);

            BigDecimal acqSept = congeService.enregistrerAcquisitionMensuelle(
                    emp.idEmploye, 2026, 9, BigDecimal.ZERO, 1, c);
            assertEquals(0, acqSept.compareTo(new BigDecimal("2.50")), "Septembre doit être acquis normalement");
            assertEquals(12.5, getSoldeActuel(emp.idEmploye), 0.001);
        }

        // Salarié 2 : Solde arrêté au 30/09 incluant l'acquisition de Septembre
        TestEmployeFixtures.CreatedEmploye emp2 = employeFixtures.createActiveEmploye(
                "EMP031", "RAZAFY", "Bao", 1, LocalDate.now().minusYears(2));
        try (Connection c = dataSource.getConnection()) {
            congeService.initialiserSoldeHistorique(
                    emp2.idEmploye, new BigDecimal("17.5"), LocalDate.of(2026, 9, 30),
                    true, "Arrêté au 30/09 avec acquisition Septembre incluse", 1, c);

            // Tentative d'acquisition de Septembre 2026 -> Doit être neutralisée (0j, pas de double crédit)
            BigDecimal acqDoublon = congeService.enregistrerAcquisitionMensuelle(
                    emp2.idEmploye, 2026, 9, BigDecimal.ZERO, 1, c);
            assertEquals(0, acqDoublon.compareTo(BigDecimal.ZERO), "Acquisition septembre neutralisée car déjà incluse");
            assertEquals(17.5, getSoldeActuel(emp2.idEmploye), 0.001, "Le solde ne doit pas avoir été augmenté une 2e fois");

            // Mois d'Octobre (après cutover) -> Acquisition autorisée normalement
            BigDecimal acqOct = congeService.enregistrerAcquisitionMensuelle(
                    emp2.idEmploye, 2026, 10, BigDecimal.ZERO, 1, c);
            assertEquals(0, acqOct.compareTo(new BigDecimal("2.50")), "Octobre acquis normalement");
            assertEquals(20.0, getSoldeActuel(emp2.idEmploye), 0.001);
        }
    }

    @Test
    @DisplayName("Decoupling Pointage : setAttendanceInput n'alimente plus nbJoursConges")
    public void testDecouplingPointage_SetAttendanceInputNeValorisePlusNbJoursConges() {
        PayrollCalculationInput input = new PayrollCalculationInput();
        assertEquals(0, input.getNbJoursConges().compareTo(BigDecimal.ZERO));

        // Snapshot pointage avec 5 jours de congé détectés
        PayrollAttendanceInput att = new PayrollAttendanceInput(
                100, 1, emp.idEmploye, 200, java.time.Instant.now(),
                8000L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0,
                new BigDecimal("22"), new BigDecimal("17"), BigDecimal.ZERO,
                new BigDecimal("5.0"), // leaveDays in snapshot
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                java.util.List.of()
        );

        input.setAttendanceInput(att);

        // Invariant Phase 2A-3B : input.getNbJoursConges() doit rester à 0 (non écrasé par pointage)
        assertEquals(0, input.getNbJoursConges().compareTo(BigDecimal.ZERO),
                "Pointage ne doit plus alimenter nbJoursConges financier");
        assertEquals(0, input.getAttendanceInput().leaveDays().compareTo(new BigDecimal("5.0")),
                "leaveDays reste conservé dans le snapshot pour affichage");
    }
}
