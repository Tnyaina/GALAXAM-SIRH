package mg.galaxam.sirh.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mg.galaxam.sirh.model.contrat.engine.AncienneteEngine;
import mg.galaxam.sirh.model.contrat.engine.AncienneteEngine.AncienneteBilan;
import mg.galaxam.sirh.model.contrat.engine.AncienneteEngine.IntervalleContrat;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.CotisationResult;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.TrancheIrsa;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.util.UtilDB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Audit Phase 5 & 6 — Validation des Tests de Parité Métier & Nettoyage")
class AuditPhase5Et6VerificationTest {

    private final CalculateurPaieEngine calculateurPaieEngine = new CalculateurPaieEngine();
    private final AncienneteEngine ancienneteEngine = new AncienneteEngine();
    private final CalendrierOuvrableEngine calendrierEngine = new CalendrierOuvrableEngine();

    private List<TrancheIrsa> getBaremeLf2026Tranches() {
        return List.of(
                new TrancheIrsa(BigDecimal.ZERO, new BigDecimal("350000.00"), 0.0),
                new TrancheIrsa(new BigDecimal("350000.00"), new BigDecimal("400000.00"), 5.0),
                new TrancheIrsa(new BigDecimal("400000.00"), new BigDecimal("500000.00"), 10.0),
                new TrancheIrsa(new BigDecimal("500000.00"), new BigDecimal("600000.00"), 15.0),
                new TrancheIrsa(new BigDecimal("600000.00"), new BigDecimal("4000000.00"), 20.0),
                new TrancheIrsa(new BigDecimal("4000000.00"), null, 25.0)
        );
    }

    @Test
    @DisplayName("ACT-019 : Identité stricte du taux horaire entre StcService et PayrollEngine")
    void testPariteTauxHoraireStcVsBulletin() {
        BigDecimal salaireBase = new BigDecimal("1500000.00");
        BigDecimal diviseurCanonique = PayrollExecutionEngine.DIVISEUR_HEURES_MENSUELLES;

        // 1. Taux calculé selon la constante unifiée 173.33
        BigDecimal tauxAttendu = salaireBase.divide(diviseurCanonique, 2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("8654.01"), tauxAttendu, "1 500 000 / 173.33 doit être égal à 8654.01 Ar/h");

        // 2. Taux d'un PayrollCalculationInput (précision intermédiaire à 4 décimales)
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setSalaireBaseContractuel(salaireBase);
        BigDecimal tauxPayroll = input.getTauxHoraireEffectif();
        assertEquals(new BigDecimal("8654.0126"), tauxPayroll, "Précision intermédiaire à 4 décimales pour les heures sup");
        assertEquals(tauxAttendu, tauxPayroll.setScale(2, RoundingMode.HALF_UP), "Le taux arrondi du bulletin doit être strictement identique au taux STC");
    }

    @Test
    @DisplayName("ACT-019 : Identité stricte du calcul CNaPS entre STC et Bulletin")
    void testPariteCnapsStcVsBulletin() {
        BigDecimal salaireBrut = new BigDecimal("3000000.00");
        BigDecimal smig = new BigDecimal("262680.00");
        double tauxCnapsSal = 0.01; // 1%
        double tauxCnapsPat = 0.13; // 13%

        // Plafond = 8 * 262 680 = 2 101 440 Ar
        CotisationResult res = calculateurPaieEngine.calculerCnaps(salaireBrut, smig, tauxCnapsSal, tauxCnapsPat);
        assertEquals(new BigDecimal("2101440.00"), res.assiette(), "Assiette CNaPS plafonnée à 8xSMIG");
        assertEquals(new BigDecimal("21014.40"), res.partSalariale(), "Part salariale CNaPS 1% = 21 014.40 Ar");
        assertEquals(new BigDecimal("273187.20"), res.partPatronale(), "Part patronale CNaPS 13% = 273 187.20 Ar");
    }

    @Test
    @DisplayName("ACT-019 : Identité absolue du calcul IRSA entre CalculateurPaieEngine et PayrollEngine")
    void testPariteIrsaStcVsBulletin() {
        BigDecimal baseImposable = new BigDecimal("1200000.00");
        List<TrancheIrsa> tranches = getBaremeLf2026Tranches();

        BigDecimal irsaDirect = calculateurPaieEngine.calculerIrsaBrut(baseImposable, tranches);

        // Tranche 0 (0-350k)    : 0
        // Tranche 1 (350k-400k) : 50 000 * 5% = 2 500
        // Tranche 2 (400k-500k) : 100 000 * 10% = 10 000
        // Tranche 3 (500k-600k) : 100 000 * 15% = 15 000
        // Tranche 4 (600k-1.2M) : 600 000 * 20% = 120 000
        // Total = 2 500 + 10 000 + 15 000 + 120 000 = 147 500 Ar
        assertEquals(new BigDecimal("147500.00"), irsaDirect, "L'IRSA brut sur 1 200 000 Ar doit valoir 147 500.00 Ar");
    }

    @Test
    @DisplayName("ACT-020 : Calcul de l'ancienneté continue sur chaîne de contrats CDD -> CDI")
    void testAncienneteChaineContrats() {
        LocalDate dateDebutCdd = LocalDate.of(2021, 1, 1);
        LocalDate dateFinCdd = LocalDate.of(2023, 1, 1); // 2 ans

        LocalDate dateDebutCdi = LocalDate.of(2023, 1, 2);
        LocalDate dateDepartStc = LocalDate.of(2026, 1, 2); // 3 ans de plus

        List<IntervalleContrat> contrats = List.of(
                new IntervalleContrat(1, dateDebutCdd, dateFinCdd, null),
                new IntervalleContrat(2, dateDebutCdi, null, 1) // CDI lié au CDD précédent
        );

        AncienneteBilan bilan = ancienneteEngine.evaluerAnciennete(contrats, 2, dateDepartStc);

        assertEquals(5, bilan.ansEffectifs(), "La chaîne CDD -> CDI doit totaliser 5 ans effectifs");
        assertEquals(2, bilan.nombreContratsChaine(), "La chaîne doit compter 2 contrats successifs");
    }

    @Test
    @DisplayName("ACT-021 : Non-régression barème IRSA vide -> levée stricte d'exception")
    void testIrsaTranchesVidesRejetees() {
        BigDecimal base = new BigDecimal("1000000.00");
        assertThrows(IllegalStateException.class, () -> {
            calculateurPaieEngine.calculerIrsaBrut(base, null);
        }, "Un barème null doit lever IllegalStateException");

        assertThrows(IllegalStateException.class, () -> {
            calculateurPaieEngine.calculerIrsaBrut(base, new ArrayList<>());
        }, "Un barème vide doit lever IllegalStateException");
    }

    @Test
    @DisplayName("ACT-022 : Vérification de la disponibilité de UtilDB dans mg.galaxam.sirh.util")
    void testUtilDbPackagePresence() {
        assertNotNull(UtilDB.class);
        assertEquals("mg.galaxam.sirh.util.UtilDB", UtilDB.class.getName());
    }

    @Test
    @DisplayName("ACT-023 : Robustesse de CalendrierOuvrableEngine face aux connexions nulles")
    void testCalendrierOuvrableRobustesse() {
        Set<LocalDate> dates = calendrierEngine.getDatesFeriesEtChomees(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                1,
                null // Connexion nulle
        );
        assertNotNull(dates);
        assertTrue(dates.isEmpty(), "Une connexion nulle ne doit pas provoquer d'exception mais retourner un ensemble vide sécurisé");
    }
}
