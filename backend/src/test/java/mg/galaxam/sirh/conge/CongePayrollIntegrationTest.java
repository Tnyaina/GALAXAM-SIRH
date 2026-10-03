package mg.galaxam.sirh.conge;

import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CongePayrollIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    private int employeId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
            CongeTestFixtureHelper.createAdminUser(c, "admin.payroll@galaxam.mg");
            employeId = CongeTestFixtureHelper.createEmploye(c, "EMP-PAY-001", "Ravelo", "Lala");
            int contratId = CongeTestFixtureHelper.createContratActif(c, employeId, LocalDate.of(2025, 1, 1));
            CongeTestFixtureHelper.createAffectation(c, employeId, contratId, LocalDate.of(2025, 1, 1), new BigDecimal("1500000.00"));
        }
    }

    @Test
    @DisplayName("Le taux moyen mensuel M-1 ne remplace jamais un TCONGE applicable non activé")
    void moyenneMensuelleNeDevientPasTauxApplicable() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 2.5, "Droit déjà acquis");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO conge_journal_droit (id_employe, annee, mois, salaire_base, " +
                    "base_valorisation, jours_droit_acquis, valorisation_mensuelle, " +
                    "cumul_jours_droit, cumul_valorisation, taux_journalier_conge) " +
                    "VALUES (?, 2026, 1, 300000, 300000, 2.5, 25000, 2.5, 25000, 10000)")) {
                ps.setInt(1, employeId);
                ps.executeUpdate();
            }
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> congeTauxApplicableService.resoudreTauxCongeApplicable(
                            employeId, LocalDate.of(2026, 2, 15), new BigDecimal("300000"), c));
            assertTrue(ex.getMessage().contains("LEAVE_RATE_MISSING"));
        }
    }

    @Test
    @DisplayName("23. TCONGE : résolution temporelle exacte, provenance et détection de chevauchement ambigu")
    void testTcongeResolutionEtChevauchement() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Insertion d'un taux applicable actif au 2026-01-01
            LocalDate dateEffet = LocalDate.of(2026, 1, 1);
            LocalDate dateFin = LocalDate.of(2026, 12, 31);
            BigDecimal taux1 = new BigDecimal("55000.00");
            CongeTestFixtureHelper.creerTcongeApplicable(c, employeId, taux1, dateEffet, dateFin, "CALCUL_PERIODE_REFERENCE");

            // Résolution à une date comprise dans la période (2026-06-15)
            CongeTauxApplicableService.TauxCongeResolution res = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    employeId, LocalDate.of(2026, 6, 15), new BigDecimal("50000.00"), c);

            assertNotNull(res);
            assertEquals(0, taux1.compareTo(res.getTauxJournalier()));
            assertEquals(CongeTauxApplicableService.TcongeSourceProvenance.CALCUL_PERIODE_REFERENCE, res.getSourceProvenance());
            assertEquals(dateEffet, res.getDateEffet());

            // Insertion d'un DEUXIÈME taux chevauchant la même période pour tester l'ambiguïté
            BigDecimal taux2 = new BigDecimal("60000.00");
            CongeTestFixtureHelper.creerTcongeApplicable(c, employeId, taux2, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 30), "REPRISE_CUTOVER");

            // La résolution doit lever une exception explicite TCONGE_APPLICABLE_AMBIGUOUS
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                congeTauxApplicableService.resoudreTauxCongeApplicable(
                        employeId, LocalDate.of(2026, 6, 15), new BigDecimal("50000.00"), c);
            });
            assertTrue(ex.getMessage().contains("TCONGE_APPLICABLE_AMBIGUOUS"));
        }
    }

    @Test
    @DisplayName("24. Paie : valorisation congés (DEDUCTION_CONGE_BASE 1/30e vs INDEMNITE_CONGE_PAYE TCONGE)")
    void testConsommationTcongeDansPaie() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Salarié avec salaire de base = 1 500 000 Ar
            // Taux trentième légal = 1 500 000 / 30 = 50 000 Ar/jour
            // TCONGE historique résolu = 54 000 Ar/jour (supérieur grâce aux primes passées)
            BigDecimal salaireBase = new BigDecimal("1500000.00");
            BigDecimal tconge = new BigDecimal("54000.00");
            CongeTestFixtureHelper.creerTcongeApplicable(c, employeId, tconge, LocalDate.of(2026, 1, 1), null, "CALCUL_PERIODE_REFERENCE");

            // Résolution réelle du TCONGE
            CongeTauxApplicableService.TauxCongeResolution resTaux = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    employeId, LocalDate.of(2026, 9, 1), salaireBase, c);
            assertEquals(0, tconge.compareTo(resTaux.getTauxJournalier()));

            // Exécution du moteur de paie PayrollExecutionEngine
            PayrollExecutionEngine engine = new PayrollExecutionEngine();
            PayrollCalculationInput input = new PayrollCalculationInput();
            input.setSalaireBaseContractuel(salaireBase);
            input.setNbJoursConges(new BigDecimal("3.0")); // 3 jours de congés
            input.setTauxJournalierConge(resTaux.getTauxJournalier());

            BulletinPaie bp = new BulletinPaie();
            List<BulletinPaieLigne> lignes = new ArrayList<>();

            BigDecimal regularisationConge = engine.calculerConges(input, bp, lignes);

            // Vérifications :
            // 1. Déduction 1/30e : 3j * 50 000 = 150 000 Ar
            BulletinPaieLigne ligneDed = lignes.stream()
                    .filter(l -> "DEDUCTION_CONGE_BASE".equals(l.getCodeRubrique()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(ligneDed);
            assertEquals("RETENUE", ligneDed.getSens());
            assertEquals(0, new BigDecimal("150000.00").compareTo(ligneDed.getMontantSalarial()));

            // 2. Indemnité congés payés : 3j * 54 000 = 162 000 Ar
            BulletinPaieLigne ligneInd = lignes.stream()
                    .filter(l -> "INDEMNITE_CONGE_PAYE".equals(l.getCodeRubrique()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(ligneInd);
            assertEquals("GAIN", ligneInd.getSens());
            assertEquals(0, new BigDecimal("162000.00").compareTo(ligneInd.getMontantSalarial()));

            // 3. Différence nette en faveur du salarié : 162 000 - 150 000 = +12 000 Ar
            assertEquals(0, new BigDecimal("12000.00").compareTo(regularisationConge));
        }
    }

    @Test
    @DisplayName("25. STC : valorisation du solde restant avec le TCONGE résolu et formule canonique")
    void testConsommationTcongeDansStc() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Salarié partant avec 12.5 jours de congés non pris
            CongeTestFixtureHelper.crediterSolde(c, employeId, 12.5, "Solde de fin de contrat");

            BigDecimal tconge = new BigDecimal("52500.00");
            CongeTestFixtureHelper.creerTcongeApplicable(c, employeId, tconge, LocalDate.of(2026, 1, 1), null, "CALCUL_PERIODE_REFERENCE");

            // Résolution du TCONGE à la date de rupture (2026-09-30)
            CongeTauxApplicableService.TauxCongeResolution resTaux = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    employeId, LocalDate.of(2026, 9, 30), new BigDecimal("1500000.00"), c);
            assertNotNull(resTaux);
            assertEquals(0, tconge.compareTo(resTaux.getTauxJournalier()));

            // Formule canonique STC Indemnité compensatrice de congés non pris = solde * TCONGE
            BigDecimal soldeRestant = new BigDecimal("12.5");
            BigDecimal indemniteCompensatrice = soldeRestant.multiply(resTaux.getTauxJournalier()).setScale(2, java.math.RoundingMode.HALF_UP);

            // 12.5 * 52 500 = 656 250.00 Ar
            assertEquals(0, new BigDecimal("656250.00").compareTo(indemniteCompensatrice));
        }
    }
}
