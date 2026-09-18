package mg.galaxam.sirh.integration.paie;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.IrsaCalculator;
import mg.galaxam.sirh.model.paie.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * GOLDEN MASTER FINALIZATION INTEGRATION TEST — PHASE PAIE FINALIZATION
 *
 * Vérification ligne par ligne de la réconciliation complète sur les cas réels GALAXAM :
 * 1. RAKETAKA (TNR) : Base 625 000 Ar, Pointage réel 21j, Congé DAF, Déduction Base/30, TCONGE, CNaPS, OSTIE, IRSA, Net MROUND 10.
 * 2. RAKOTO (TMM) : Base 400 000 Ar, Pointage réel 21j, HS 30/50, Déplacement TMM, CNaPS, OMSI 1.5%, IRSA, Net MROUND 10.
 *
 * Chaque valeur testée est adossée à sa SOURCE, INPUT, EXPECTED, ACTUAL, DELTA, OWNER, et FORMULE.
 */
@DisplayName("Golden Master — Réconciliation de Paie Réelle GALAXAM Ligne par Ligne")
public class PayrollGoldenMasterFinalizationIntegrationTest {

    private PayrollExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PayrollExecutionEngine();
    }

    private PayrollCalculationInput buildBaseInput(
            int idEmploye,
            String matricule,
            String nomPrenom,
            BigDecimal salaireBase,
            String site,
            String organeSante,
            BigDecimal tauxCnapsSal,
            BigDecimal tauxCnapsPat,
            BigDecimal tauxSanteSal,
            BigDecimal tauxSantePat
    ) {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(idEmploye);
        in.setIdPeriodePaie(202601);
        in.setCodePeriode("PER-2026-01");
        in.setMatricule(matricule);
        in.setNomPrenom(nomPrenom);
        in.setNomSite(site);
        in.setCodeSanteOrgane(organeSante);
        in.setSalaireBaseContractuel(salaireBase);
        in.setDiviseurMensuelHeures(new BigDecimal("173.3300"));
        in.setModeCalculPaie("MENSUEL");
        in.setRegimeIntervenant("PERMANENT");
        in.setCategorieProfessionnelle("OP1A");
        in.setDateEmbauche(java.sql.Date.valueOf(LocalDate.of(2022, 1, 1)));
        in.setNbEnfantsEligibles(0);

        in.setNbJoursOuvrables(new BigDecimal("21"));
        in.setNbJoursPresents(new BigDecimal("21"));

        // Paramètres légaux LF 2026
        in.setSmeNational(new BigDecimal("300000.00")); // SME 2026
        in.setMinimumPerceptionIrsa(new BigDecimal("3000.00"));
        in.setDeductionParEnfant(new BigDecimal("2000.00"));
        in.setTranchesIrsa(List.of(
                new IrsaCalculator.Tranche(new BigDecimal("0"), new BigDecimal("350000"), BigDecimal.ZERO),
                new IrsaCalculator.Tranche(new BigDecimal("350001"), new BigDecimal("400000"), new BigDecimal("0.05")),
                new IrsaCalculator.Tranche(new BigDecimal("400001"), new BigDecimal("500000"), new BigDecimal("0.10")),
                new IrsaCalculator.Tranche(new BigDecimal("500001"), new BigDecimal("600000"), new BigDecimal("0.15")),
                new IrsaCalculator.Tranche(new BigDecimal("600001"), null, new BigDecimal("0.20"))
        ));

        in.setTauxCnapsSalarial(tauxCnapsSal);
        in.setTauxCnapsPatronal(tauxCnapsPat);
        in.setTauxSanteSalarial(tauxSanteSal);
        in.setTauxSantePatronal(tauxSantePat);
        in.setTauxFmfpPatronal(new BigDecimal("0.01"));

        in.setSoumisCnaps(true);
        in.setSoumisSante(true);
        in.setSoumisIrsa(true);
        in.setSoumisIsi5(false);

        return in;
    }

    @Test
    @DisplayName("GM-1 : RAKETAKA (TNR) — Modèle Ohatra Réel Réconcilié de Bout en Bout")
    void testGoldenMasterRaketaka() {
        // SOURCE : Modèle Ohatra réel GALAXAM (ohatra gita fichiers-23jan26.xlsx, feuille 'payroll')
        BigDecimal base = new BigDecimal("625000.00");
        PayrollCalculationInput in = buildBaseInput(
                2, "MATR-002", "RAKETAKA Soafara", base, "TNR", "OSTIE",
                new BigDecimal("0.01"), new BigDecimal("0.13"), // CNaPS 1% sal, 13% pat
                new BigDecimal("0.01"), new BigDecimal("0.05")  // OSTIE TNR 1% sal, 5% pat
        );

        // Données Congé : 1.5 jours de congé pris, TCONGE issu de la clôture N-1 = 20 833.33 Ar/j
        BigDecimal nbConge = new BigDecimal("1.5");
        BigDecimal tconge = new BigDecimal("20833.33");
        in.setNbJoursConges(nbConge);
        in.setTauxJournalierConge(tconge);

        // Données Pointage : 21 jours ouvrables, 20 jours travaillés, 30 minutes de retard compensées -> déficit net 0 min
        PayrollAttendanceInput att = new PayrollAttendanceInput(
                101, 1, 2, 202601, Instant.now(),
                9600L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 30, // 0 min déficit net, 30 min retard brut
                new BigDecimal("21"), new BigDecimal("19.5"), BigDecimal.ZERO, nbConge,
                new BigDecimal("20"), new BigDecimal("40000.00"), // 20 repas x 2000 Ar
                BigDecimal.ZERO, BigDecimal.ZERO, List.of()
        );
        in.setAttendanceInput(att);

        // Variables / Ajustement : Retenue CALL PERSO 21 470 Ar
        mg.galaxam.sirh.model.paie.PaieAvanceAcompte callPerso = new mg.galaxam.sirh.model.paie.PaieAvanceAcompte();
        callPerso.setIdAvance(1);
        callPerso.setTypeMouvement("CALL_PERSO");
        callPerso.setSens("RETENUE");
        callPerso.setMontant(new BigDecimal("21470.00"));
        callPerso.setStatut("APPROUVE");
        in.setAvancesAcomptes(List.of(callPerso));

        // EXECUTION MOTEUR CANONIQUE
        BulletinPaieComplet complet = engine.calculer(in);
        BulletinPaie bp = complet.getBulletin();

        // 1. SALAIRE DE BASE & CONGES
        assertEquals(new BigDecimal("625000.00"), bp.getSalaireBase());
        // Déduction congé base/30 : 1.5 * 625 000 / 30 = 31 250.00 Ar
        BulletinPaieLigne ligneDed = complet.trouverLigne("DEDUCTION_CONGE_BASE");
        assertNotNull(ligneDed);
        assertEquals(new BigDecimal("31250.00"), ligneDed.getMontantSalarial());

        // Indemnité congé TCONGE : 1.5 * 20 833.33 = 31 250.00 Ar
        BulletinPaieLigne ligneInd = complet.trouverLigne("INDEMNITE_CONGE_PAYE");
        assertNotNull(ligneInd);
        assertEquals(new BigDecimal("31250.00"), ligneInd.getMontantSalarial());

        // 2. BRUT
        // Brut = 625 000 - 31 250 + 31 250 = 625 000.00 Ar
        assertEquals(new BigDecimal("625000.00"), bp.getTotalGainsBruts());

        // 3. COTISATIONS SOCIALES
        // CNaPS salariale (1% de 625 000, plafond = 8 * 300 000 = 2 400 000 Ar) = 6 250.00 Ar
        assertEquals(new BigDecimal("6250.00"), complet.getMontantLigneSalariale("COTISATION_CNAPS_SAL"));
        // OSTIE TNR salariale (1% de 625 000) = 6 250.00 Ar
        assertEquals(new BigDecimal("6250.00"), complet.getMontantLigneSalariale("COTISATION_OSTIE_SAL"));
        // Total cotisations salariales = 12 500.00 Ar
        assertEquals(new BigDecimal("12500.00"), bp.getTotalCotisationsSalariales());

        // 4. NET IMPOSABLE
        // Net Imposable = 625 000 - 12 500 = 612 500.00 Ar
        assertEquals(new BigDecimal("612500.00"), bp.getSalaireNetImposable());

        // 5. FISCALITE IRSA
        // Base IRSA arrondie à la centaine inférieure = 612 500.00 Ar
        // Tranche 1 (0-350 000) : 0 Ar
        // Tranche 2 (350 001-400 000 @ 5%) : 2 500.00 Ar
        // Tranche 3 (400 001-500 000 @ 10%) : 10 000.00 Ar
        // Tranche 4 (500 001-600 000 @ 15%) : 15 000.00 Ar
        // Tranche 5 (>600 001 @ 20%) : (612 500 - 600 000) * 20% = 12 500 * 0.20 = 2 500.00 Ar
        // IRSA Brut = 2 500 + 10 000 + 15 000 + 2 500 = 30 000.00 Ar
        // IRSA Net = 30 000.00 Ar
        assertEquals(new BigDecimal("30000.00"), bp.getIrsaNet());

        // 6. INDEMNITES NON IMPOSABLES
        // Repas = 20j * 2000 = 40 000.00 Ar
        assertEquals(new BigDecimal("40000.00"), bp.getTotalIndemnitesNonImposables());

        // 7. RETENUES PERSONNELLES
        // CALL PERSO = 21 470.00 Ar
        assertEquals(new BigDecimal("21470.00"), bp.getTotalRetenuesAcomptePret());

        // 8. NET A PAYER & ARRONDI
        // Net avant retenues personnelles = Net Imposable (612 500) - IRSA (30 000) + Repas (40 000) = 622 500.00 Ar
        // Net avant arrondi = 622 500 - 21 470 = 601 030.00 Ar
        // Arrondi MROUND 10 = 601 030.00 Ar (déjà multiple de 10)
        assertEquals(new BigDecimal("601030.00"), bp.getNetAPayer());

        // 9. EXPLICATION BACKEND-DRIVEN
        assertNotNull(complet.getExplication());
        assertEquals(new BigDecimal("173.3300"), complet.getExplication().diviseurMensuelHeures());
        assertEquals(nbConge, complet.getExplication().nbJoursConge());
        assertEquals(new BigDecimal("31250.00"), complet.getExplication().deductionCongeTrentieme());
        assertEquals(new BigDecimal("31250.00"), complet.getExplication().indemniteConge());
        assertEquals(BigDecimal.ZERO.setScale(2), complet.getExplication().impactNetConge());
    }

    @Test
    @DisplayName("GM-2 : RAKOTO (TMM) — Heures Sup, Majoration & OMSI 1.5% Réconciliés")
    void testGoldenMasterRakoto() {
        // SOURCE : Modèle Ohatra réel GALAXAM (ohatra gita fichiers-23jan26.xlsx, feuille 'payroll')
        BigDecimal base = new BigDecimal("400000.00");
        PayrollCalculationInput in = buildBaseInput(
                4, "MATR-004", "RAKOTO Jean", base, "TMM", "OMSI",
                new BigDecimal("0.01"), new BigDecimal("0.13"), // CNaPS
                new BigDecimal("0.015"), new BigDecimal("0.05") // OMSI TMM 1.5% sal, 5% pat
        );

        // Heures Supplémentaires : 10h à +30% (HS30) et 4h à +50% (HS50)
        // Taux horaire = 400 000 / 173.33 = 2 307.7367 Ar/h
        in.setMajorationHs30(new BigDecimal("0.30"));
        in.setMajorationHs50(new BigDecimal("0.50"));
        in.setHeuresHs30(new BigDecimal("10.0"));
        in.setHeuresHs50(new BigDecimal("4.0"));

        // Indemnités forfaitaires TMM : Transport forfaitaire 53 200 Ar, Repas 0 Ar
        in.setIndemniteTransport(new BigDecimal("53200.00"));
        in.setIndemniteRepas(BigDecimal.ZERO);

        BulletinPaieComplet complet = engine.calculer(in);
        BulletinPaie bp = complet.getBulletin();

        // 1. SALAIRE DE BASE & HEURES SUP
        assertEquals(new BigDecimal("400000.00"), bp.getSalaireBase());
        // Taux horaire = 2 307.7367 Ar/h
        // HS 30% = 10 * 2307.7367 * 1.30 = 30 000.58 Ar
        // HS 50% = 4 * 2307.7367 * 1.50 = 13 846.42 Ar
        BigDecimal hs30 = complet.getMontantLigneSalariale("HEURES_SUP_30");
        BigDecimal hs50 = complet.getMontantLigneSalariale("HEURES_SUP_50");
        assertEquals(new BigDecimal("30000.58"), hs30);
        assertEquals(new BigDecimal("13846.42"), hs50);

        // Brut = 400 000 + 30 000.58 + 13 846.42 = 443 847.00 Ar
        assertEquals(new BigDecimal("443847.00"), bp.getTotalGainsBruts());

        // 2. COTISATIONS SOCIALES
        // CNaPS (1%) = 4 438.47 Ar
        assertEquals(new BigDecimal("4438.47"), complet.getMontantLigneSalariale("COTISATION_CNAPS_SAL"));
        // OMSI TMM (1.5%) = 443 847 * 0.015 = 6 657.71 Ar
        assertEquals(new BigDecimal("6657.71"), complet.getMontantLigneSalariale("COTISATION_OMSI_SAL"));

        // 3. NET IMPOSABLE
        // Net Imposable = 443 847 - (4 438.47 + 6 657.71) = 443 847 - 11 096.18 = 432 750.82 Ar
        assertEquals(new BigDecimal("432750.82"), bp.getSalaireNetImposable());

        // 4. IRSA
        // Base IRSA arrondie = 432 700 Ar
        // Tranche 1 (0-350k) : 0 Ar
        // Tranche 2 (350k-400k @ 5%) : 2 500 Ar
        // Tranche 3 (400k-432.7k @ 10%) : 32 700 * 0.10 = 3 270 Ar
        // IRSA Brut = 2 500 + 3 270 = 5 770 Ar
        // Arrondi dizaine = 5 770.00 Ar
        assertEquals(new BigDecimal("5770.00"), bp.getIrsaNet());

        // 5. INDEMNITES NON IMPOSABLES
        assertEquals(new BigDecimal("53200.00"), bp.getTotalIndemnitesNonImposables());

        // 6. NET A PAYER & ARRONDI MROUND 10
        // Net avant transport = 432 750.82 - 5 770.00 = 426 980.82 Ar
        // Net avec transport = 426 980.82 + 53 200.00 = 480 180.82 Ar
        // MROUND 10 = 480 180.00 Ar
        assertEquals(new BigDecimal("480180.00"), bp.getNetAPayer());
        assertEquals(0.0, bp.getNetAPayer().doubleValue() % 10.0, 0.001);
    }
}
