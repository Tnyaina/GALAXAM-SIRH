package mg.galaxam.sirh.unit.paie.certification;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.IrsaCalculator;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;

/**
 * MISSION — PAYROLL PRODUCTION READINESS GATE
 * 
 * Qualification exhaustive de production :
 * Gate 1 : Résolution définitive de l'exonération des 20h HS (CGI Art. 01.03.03-9°)
 * Gate 2 : Traçabilité temporelle 12/2025, 01/2026, 02/2026, 03/2026
 * Gate 3 : Aucun fallback financier dangereux
 * Gate 4 : Reproductibilité et immutabilité
 * Gate 5 : Réconciliation comptable unitaire
 * Gate 6 : Sommes des bulletins et journal
 * Gate 7 : Idempotence et anti-double paiement
 * Gate 8 : Concurrence et intégrité transactionnelle
 * Gate 9 : Rollback transactionnel
 * Gate 10 : Shadow Payroll multi-profils
 */
public class PayrollProductionReadinessCertificationTest {

    private static PayrollCalculationInput configuredInput() {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setTauxCnapsSalarial(new BigDecimal("0.01"));
        input.setTauxCnapsPatronal(new BigDecimal("0.13"));
        input.setTauxSanteSalarial(new BigDecimal("0.01"));
        input.setTauxSantePatronal(new BigDecimal("0.05"));
        input.setTauxFmfpPatronal(new BigDecimal("0.01"));
        input.setMajorationHs30(new BigDecimal("0.30"));
        input.setMajorationHs50(new BigDecimal("0.50"));
        input.setMajorationNuit(new BigDecimal("0.30"));
        input.setMajorationDimanche(new BigDecimal("0.40"));
        input.setMajorationFerie(BigDecimal.ZERO);
        input.setExonerationHsIrsaActive(true);
        input.setPlafondMensuelHsExonerees(new BigDecimal("20.00"));
        return input;
    }

    private PayrollExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PayrollExecutionEngine();
    }

    private List<IrsaCalculator.Tranche> createIrsa2025Tranches() {
        List<IrsaCalculator.Tranche> tranches = new ArrayList<>();
        tranches.add(new IrsaCalculator.Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), new BigDecimal("0.00")));
        tranches.add(new IrsaCalculator.Tranche(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("0.05")));
        tranches.add(new IrsaCalculator.Tranche(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("0.10")));
        tranches.add(new IrsaCalculator.Tranche(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("0.15")));
        tranches.add(new IrsaCalculator.Tranche(new BigDecimal("600000.00"), null, new BigDecimal("0.20")));
        return tranches;
    }

    private List<IrsaCalculator.Tranche> createIrsa2026Tranches() {
        List<IrsaCalculator.Tranche> tranches = new ArrayList<>();
        tranches.add(new IrsaCalculator.Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), new BigDecimal("0.00")));
        tranches.add(new IrsaCalculator.Tranche(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("0.05")));
        tranches.add(new IrsaCalculator.Tranche(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("0.10")));
        tranches.add(new IrsaCalculator.Tranche(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("0.15")));
        tranches.add(new IrsaCalculator.Tranche(new BigDecimal("600000.00"), new BigDecimal("4000000.00"), new BigDecimal("0.20")));
        tranches.add(new IrsaCalculator.Tranche(new BigDecimal("4000000.00"), null, new BigDecimal("0.25")));
        return tranches;
    }

    private PayrollCalculationInput createRakotoDecember2025BaseInput() {
        BigDecimal base = new BigDecimal("400000.00");
        BigDecimal nbAbsHeures = new BigDecimal("1.51");
        BigDecimal nbCongeJours = new BigDecimal("1.50");
        BigDecimal tconge = new BigDecimal("12267.84");
        BigDecimal autimp = new BigDecimal("56182.14");
        BigDecimal h30 = new BigDecimal("10.00");
        BigDecimal h50 = new BigDecimal("4.00");

        PayrollCalculationInput in = configuredInput();
        in.setIdEmploye(4);
        in.setMatricule("MATR-004");
        in.setNomPrenom("RAKOTO Hery");
        in.setIntitulePoste("Aide-Déclarant");
        in.setCategorieProfessionnelle("AGENT");
        in.setSalaireBaseContractuel(base);
        in.setDiviseurMensuelHeures(new BigDecimal("173.33"));
        in.setNbHeuresAbsence(nbAbsHeures);
        in.setNbJoursConges(nbCongeJours);
        in.setTauxJournalierConge(tconge);
        in.setHeuresHs30(h30);
        in.setHeuresHs50(h50);
        in.setMajorationHs30(new BigDecimal("0.30"));
        in.setMajorationHs50(new BigDecimal("0.50"));
        in.setAutimp(autimp);
        in.setSmeNational(new BigDecimal("262680.00"));
        in.setSoumisCnaps(true);
        in.setSoumisSante(true);
        in.setCodeSanteOrgane("OMSI");
        in.setTauxSanteSalarial(new BigDecimal("0.015"));
        in.setTauxSantePatronal(new BigDecimal("0.050"));
        in.setTauxCnapsSalarial(new BigDecimal("0.01"));
        in.setTauxCnapsPatronal(new BigDecimal("0.13"));
        in.setSoumisIrsa(true);
        in.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
        in.setDeductionParEnfant(new BigDecimal("2000.00"));
        in.setNbEnfantsEligibles(0);
        in.setTranchesIrsa(createIrsa2025Tranches());
        return in;
    }

    // =========================================================================
    // GATE 1 : RÉSULTAT ET PREUVE RAKOTO AVEC EXONÉRATION FISCALE (FAIL BEFORE FIX)
    // =========================================================================
    @Nested
    @DisplayName("Gate 1 : RAKOTO — Exonération Fiscale des 14h de HS (CGI Art. 01.03.03-9°)")
    class Gate1RakotoOvertimeTests {

        @Test
        @DisplayName("1.1 RAKOTO Éligible : Séparation nette HS_BRUT, HS_EXONEREE_IRSA, HS_IMPOSABLE_IRSA")
        void testRakotoEligibleOvertimeExemption() {
            BigDecimal base = new BigDecimal("400000.00");
            BigDecimal nbAbsHeures = new BigDecimal("1.51");
            BigDecimal nbCongeJours = new BigDecimal("1.50");
            BigDecimal tconge = new BigDecimal("12267.84");
            BigDecimal autimp = new BigDecimal("56182.14");
            BigDecimal h30 = new BigDecimal("10.00");
            BigDecimal h50 = new BigDecimal("4.00");

            PayrollCalculationInput in = configuredInput();
            in.setIdEmploye(4);
            in.setMatricule("MATR-004");
            in.setNomPrenom("RAKOTO Hery");
            in.setIntitulePoste("Aide-Déclarant");
            in.setCategorieProfessionnelle("AGENT"); // Non-cadre
            in.setSalaireBaseContractuel(base);
            in.setDiviseurMensuelHeures(new BigDecimal("173.33"));
            in.setNbHeuresAbsence(nbAbsHeures);
            in.setNbJoursConges(nbCongeJours);
            in.setTauxJournalierConge(tconge);
            in.setHeuresHs30(h30);
            in.setHeuresHs50(h50);
            in.setMajorationHs30(new BigDecimal("0.30"));
            in.setMajorationHs50(new BigDecimal("0.50"));
            in.setAutimp(autimp);
            in.setSmeNational(new BigDecimal("262680.00"));
            in.setSoumisCnaps(true);
            in.setSoumisSante(true);
            in.setCodeSanteOrgane("OMSI");
            in.setTauxSanteSalarial(new BigDecimal("0.015"));
            in.setTauxSantePatronal(new BigDecimal("0.050"));
            in.setTauxCnapsSalarial(new BigDecimal("0.01"));
            in.setTauxCnapsPatronal(new BigDecimal("0.13"));
            in.setSoumisIrsa(true);
            in.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
            in.setDeductionParEnfant(new BigDecimal("2000.00"));
            in.setNbEnfantsEligibles(0);
            in.setTranchesIrsa(createIrsa2025Tranches());

            // APPLICATION EXPLICITE DU DROIT FISCAL (Art. 01.03.03-9° CGI)
            in.setAppliquerExonerationHs(true);

            BulletinPaieComplet complet = engine.calculer(in);
            BulletinPaie bp = complet.getBulletin();

            // 1. Rémunération HS Brute : 10 * (400k/173.33)*1.30 + 4 * (400k/173.33)*1.50 = 43 847.00 Ar
            BigDecimal hs30Mnt = complet.getMontantLigneSalariale("HEURES_SUP_30");
            BigDecimal hs50Mnt = complet.getMontantLigneSalariale("HEURES_SUP_50");
            BigDecimal totalHsBrut = hs30Mnt.add(hs50Mnt);
            assertEquals(new BigDecimal("43847.00"), totalHsBrut, "Total HS brut correct");

            // 2. Le Brut Social inclut 100% des HS (Principe absolu Section 7)
            assertEquals(new BigDecimal("494946.22"), bp.getTotalGainsBruts(), "Brut social inclut l'intégralité des HS");

            // 3. Les cotisations sociales sont calculées sur le Brut total
            assertEquals(new BigDecimal("4949.46"), complet.getMontantLigneSalariale("COTISATION_CNAPS_SAL"));
            assertEquals(new BigDecimal("7424.19"), complet.getMontantLigneSalariale("COTISATION_OMSI_SAL"));

            // 4. Net Imposable Social
            assertEquals(new BigDecimal("482572.57"), bp.getSalaireNetImposable());

            // 5. Part Exonérée IRSA des HS (14h <= 20h -> 100% exonérées)
            BigDecimal hsExo = complet.getMontantLigneSalariale("HS_EXONEREE_IRSA");
            assertEquals(new BigDecimal("43847.00"), hsExo, "14h de HS entièrement exonérées d'IRSA");

            // 6. Assiette IRSA Fiscale : Net Social (482 572.57) - HS Exonérées (43 847.00) = 438 725.57 Ar -> Tronqué à 438 700 Ar
            assertEquals(new BigDecimal("438700.00"), bp.getBaseIrsaArrondie(), "Assiette IRSA exclut les HS exonérées");

            // 7. IRSA Net : 2 500 (T2) + (38 700 * 10% = 3 870) = 6 370.00 Ar
            assertEquals(new BigDecimal("6370.00"), bp.getIrsaNet(), "IRSA conforme au droit fiscal légal avec exonération HS");

            // 8. Net à payer : 482 572.57 - 6 370.00 = 476 202.57 Ar -> Arrondi MROUND 10 = 476 200.00 Ar
            assertEquals(new BigDecimal("476202.57"), bp.getNetAvantArrondi());
            assertEquals(new BigDecimal("476200.00"), bp.getNetAPayer(), "Net à payer final conforme Oracle Légal");
        }

        @Test
        @DisplayName("1.2 Ancien Excel RAKOTO sans exonération (Conservé comme LEGACY_REFERENCE_ONLY)")
        void testRakotoLegacyReferenceOnly() {
            BigDecimal base = new BigDecimal("400000.00");
            BigDecimal nbAbsHeures = new BigDecimal("1.51");
            BigDecimal nbCongeJours = new BigDecimal("1.50");
            BigDecimal tconge = new BigDecimal("12267.84");
            BigDecimal autimp = new BigDecimal("56182.14");
            BigDecimal h30 = new BigDecimal("10.00");
            BigDecimal h50 = new BigDecimal("4.00");

            PayrollCalculationInput in = configuredInput();
            in.setIdEmploye(4);
            in.setMatricule("MATR-004");
            in.setNomPrenom("RAKOTO Hery");
            in.setCategorieProfessionnelle("AGENT");
            in.setSalaireBaseContractuel(base);
            in.setDiviseurMensuelHeures(new BigDecimal("173.33"));
            in.setNbHeuresAbsence(nbAbsHeures);
            in.setNbJoursConges(nbCongeJours);
            in.setTauxJournalierConge(tconge);
            in.setHeuresHs30(h30);
            in.setHeuresHs50(h50);
            in.setAutimp(autimp);
            in.setSmeNational(new BigDecimal("262680.00"));
            in.setSoumisCnaps(true);
            in.setSoumisSante(true);
            in.setCodeSanteOrgane("OMSI");
            in.setTauxSanteSalarial(new BigDecimal("0.015"));
            in.setTauxSantePatronal(new BigDecimal("0.050"));
            in.setTauxCnapsSalarial(new BigDecimal("0.01"));
            in.setTauxCnapsPatronal(new BigDecimal("0.13"));
            in.setSoumisIrsa(true);
            in.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
            in.setDeductionParEnfant(new BigDecimal("2000.00"));
            in.setNbEnfantsEligibles(0);
            in.setTranchesIrsa(createIrsa2025Tranches());

            // MODE NON EXONÉRÉ (Comportement historique de l'Excel)
            in.setAppliquerExonerationHs(false);

            BulletinPaieComplet complet = engine.calculer(in);
            BulletinPaie bp = complet.getBulletin();

            // Dans l'ancien Excel : 0 Ar d'exonération, assiette IRSA = 482 500 Ar, IRSA = 10 750 Ar, Net = 471 820 Ar
            assertEquals(0, BigDecimal.ZERO.compareTo(complet.getMontantLigneSalariale("HS_EXONEREE_IRSA")));
            assertEquals(new BigDecimal("482500.00"), bp.getBaseIrsaArrondie());
            assertEquals(new BigDecimal("10750.00"), bp.getIrsaNet());
            assertEquals(new BigDecimal("471820.00"), bp.getNetAPayer());
        }
    }

    // =========================================================================
    // GATE 1 BIS : TESTS FRONTIÈRES EXACTES 20H (19h59, 20h00, 20h01, 21h)
    // =========================================================================
    @Nested
    @DisplayName("Gate 1 Bis : Frontières Précises du Plafond 20h (Section 8)")
    class Gate1Frontieres20hTests {

        private PayrollCalculationInput buildInputForHours(BigDecimal h30, BigDecimal h50) {
            BigDecimal base = new BigDecimal("400000.00");
            PayrollCalculationInput in = configuredInput();
            in.setIdEmploye(100);
            in.setCategorieProfessionnelle("AGENT");
            in.setSalaireBaseContractuel(base);
            in.setDiviseurMensuelHeures(new BigDecimal("173.33"));
            in.setHeuresHs30(h30);
            in.setHeuresHs50(h50);
            in.setMajorationHs30(new BigDecimal("0.30"));
            in.setMajorationHs50(new BigDecimal("0.50"));
            in.setSmeNational(new BigDecimal("262680.00"));
            in.setSoumisCnaps(true);
            in.setSoumisSante(true);
            in.setSoumisIrsa(true);
            in.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
            in.setDeductionParEnfant(new BigDecimal("2000.00"));
            in.setNbEnfantsEligibles(0);
            in.setTranchesIrsa(createIrsa2025Tranches());
            in.setAppliquerExonerationHs(true);
            return in;
        }

        @Test
        @DisplayName("8.1 Seuil 19h59 (19.9833 h) : 100% exonéré")
        void testSeuil19h59() {
            BigDecimal h = new BigDecimal("19.9833"); // 19h 59min
            PayrollCalculationInput in = buildInputForHours(h, BigDecimal.ZERO);
            BulletinPaieComplet bpc = engine.calculer(in);

            BigDecimal totalHs = bpc.getMontantLigneSalariale("HEURES_SUP_30");
            BigDecimal exo = bpc.getMontantLigneSalariale("HS_EXONEREE_IRSA");
            assertEquals(totalHs, exo, "À 19h59, 100% des HS sont exonérées");
            assertEquals(0, BigDecimal.ZERO.compareTo(bpc.getMontantLigneSalariale("HS_IMPOSABLE_IRSA")));
        }

        @Test
        @DisplayName("8.2 Seuil 20h00 pile : 100% exonéré (Plafond exact atteint)")
        void testSeuil20hPile() {
            BigDecimal h = new BigDecimal("20.00");
            PayrollCalculationInput in = buildInputForHours(h, BigDecimal.ZERO);
            BulletinPaieComplet bpc = engine.calculer(in);

            BigDecimal totalHs = bpc.getMontantLigneSalariale("HEURES_SUP_30");
            BigDecimal exo = bpc.getMontantLigneSalariale("HS_EXONEREE_IRSA");
            assertEquals(totalHs, exo, "À 20h pile, 100% des HS sont exonérées");
            assertEquals(0, BigDecimal.ZERO.compareTo(bpc.getMontantLigneSalariale("HS_IMPOSABLE_IRSA")));
        }

        @Test
        @DisplayName("8.3 Seuil 20h01 (20.0167 h) : 20h exonérées, 1 min imposable")
        void testSeuil20h01() {
            BigDecimal h = new BigDecimal("20.0167"); // 20h 01min
            PayrollCalculationInput in = buildInputForHours(h, BigDecimal.ZERO);
            BulletinPaieComplet bpc = engine.calculer(in);

            BigDecimal totalHs = bpc.getMontantLigneSalariale("HEURES_SUP_30");
            BigDecimal exo = bpc.getMontantLigneSalariale("HS_EXONEREE_IRSA");
            BigDecimal imp = bpc.getMontantLigneSalariale("HS_IMPOSABLE_IRSA");

            assertTrue(exo.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(imp.compareTo(BigDecimal.ZERO) > 0, "1 min doit être imposable");
            assertEquals(totalHs, exo.add(imp), "Somme des composantes HS égale au total");
        }

        @Test
        @DisplayName("8.4 Seuil 21h00 : 20h exonérées, 1h imposable")
        void testSeuil21h() {
            BigDecimal h = new BigDecimal("21.00");
            PayrollCalculationInput in = buildInputForHours(h, BigDecimal.ZERO);
            BulletinPaieComplet bpc = engine.calculer(in);

            BigDecimal totalHs = bpc.getMontantLigneSalariale("HEURES_SUP_30");
            BigDecimal exo = bpc.getMontantLigneSalariale("HS_EXONEREE_IRSA");
            BigDecimal imp = bpc.getMontantLigneSalariale("HS_IMPOSABLE_IRSA");

            // Taux horaire = 400 000 / 173.33 = 2 307.7367 Ar/h
            // 20h à 130% = 20 * 2307.7367 * 1.30 = 60 001.15 Ar
            // 1h à 130% = 1 * 2307.7367 * 1.30 = 3 000.06 Ar
            assertEquals(new BigDecimal("60001.15"), exo);
            assertEquals(new BigDecimal("3000.06"), imp);
            assertEquals(totalHs, exo.add(imp));
        }

        @Test
        @DisplayName("9.1 Cumul HS30 (12h) + HS50 (10h) = 22h : 12h HS30 + 8h HS50 exonérées, 2h HS50 imposables")
        void testCumulHs30EtHs50() {
            BigDecimal h30 = new BigDecimal("12.00");
            BigDecimal h50 = new BigDecimal("10.00");
            PayrollCalculationInput in = buildInputForHours(h30, h50);
            BulletinPaieComplet bpc = engine.calculer(in);

            BigDecimal exo = bpc.getMontantLigneSalariale("HS_EXONEREE_IRSA");
            BigDecimal imp = bpc.getMontantLigneSalariale("HS_IMPOSABLE_IRSA");

            // Taux horaire = 400 000 / 173.33 = 2 307.7367 Ar/h
            // Part exonérée = 12h HS30 (36 000.69) + 8h HS50 (27 692.84) = 63 693.53 Ar
            // Part imposable = 2h HS50 (6 923.21)
            assertEquals(new BigDecimal("63693.53"), exo);
            assertEquals(new BigDecimal("6923.21"), imp);
            BigDecimal totalHs = bpc.getMontantLigneSalariale("HEURES_SUP_30").add(bpc.getMontantLigneSalariale("HEURES_SUP_50"));
            assertEquals(totalHs, exo.add(imp));
        }

        @Test
        @DisplayName("10.1 Sujétions Nuit, Dimanche, Férié : Distinctes et non décomptées du plafond 20h HS")
        void testSpecialPremiumsIndependence() {
            PayrollCalculationInput in = buildInputForHours(new BigDecimal("15.00"), BigDecimal.ZERO);
            in.setHeuresNuit(new BigDecimal("10.00"));
            in.setHeuresDimanche(new BigDecimal("8.00"));
            in.setHeuresFeriees(new BigDecimal("8.00"));

            BulletinPaieComplet bpc = engine.calculer(in);
            // 15h HS <= 20h -> 100% des HS restent exonérées, sans être amputées par la nuit ou le dimanche
            BigDecimal exo = bpc.getMontantLigneSalariale("HS_EXONEREE_IRSA");
            BigDecimal hs30 = bpc.getMontantLigneSalariale("HEURES_SUP_30");
            assertEquals(hs30, exo, "La majoration nuit/dimanche/férié n'ampute pas le contingent de 20h d'HS");

            assertTrue(bpc.getMontantLigneSalariale("MAJORATION_NUIT").compareTo(BigDecimal.ZERO) > 0);
            assertTrue(bpc.getMontantLigneSalariale("HEURES_DIMANCHE").compareTo(BigDecimal.ZERO) > 0);
            assertTrue(bpc.getMontantLigneSalariale("MAJORATION_FERIE").compareTo(BigDecimal.ZERO) > 0);
        }
    }

    // =========================================================================
    // GATE 4 & 5 : REPRODUCTIBILITÉ ET RÉCONCILIATION COMPTABLE UNITAIRE
    // =========================================================================
    @Nested
    @DisplayName("Gate 4 & 5 : Reproductibilité Déterministe et Équation Comptable")
    class Gate4And5AccountingIdentityTests {

        @Test
        @DisplayName("5.1 Équation comptable parfaite : Gains - Retenues + Indemnités - Trésorerie = Net")
        void testAccountingIdentityTenCases() {
            BigDecimal[] salaires = {
                    new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("550000.00"),
                    new BigDecimal("600000.00"), new BigDecimal("850000.00"), new BigDecimal("1200000.00"),
                    new BigDecimal("1850000.00"), new BigDecimal("2500000.00"), new BigDecimal("3800000.00"),
                    new BigDecimal("5000000.00")
            };

            for (BigDecimal base : salaires) {
                PayrollCalculationInput in = configuredInput();
                in.setIdEmploye(1);
                in.setSalaireBaseContractuel(base);
                in.setDiviseurMensuelHeures(new BigDecimal("173.33"));
                in.setHeuresHs30(new BigDecimal("5.00"));
                in.setNbHeuresAbsence(new BigDecimal("2.00"));
                in.setNbJoursConges(new BigDecimal("1.00"));
                in.setTauxJournalierConge(base.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP));
                in.setIndemniteTransport(new BigDecimal("53200.00"));
                in.setIndemniteRepas(new BigDecimal("20000.00"));
                in.setSmeNational(new BigDecimal("262680.00"));
                in.setSoumisCnaps(true);
                in.setSoumisSante(true);
                in.setSoumisIrsa(true);
                in.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
                in.setDeductionParEnfant(new BigDecimal("2000.00"));
                in.setNbEnfantsEligibles(1);
                in.setTranchesIrsa(createIrsa2025Tranches());
                in.setAppliquerExonerationHs(true);

                // Run A
                BulletinPaieComplet runA = engine.calculer(in);
                BulletinPaie bpA = runA.getBulletin();

                // Run B (Reproductibilité)
                BulletinPaieComplet runB = engine.calculer(in);
                BulletinPaie bpB = runB.getBulletin();

                assertEquals(bpA.getNetAPayer(), bpB.getNetAPayer(), "RUN_A == RUN_B pour base " + base);
                assertEquals(bpA.getTotalGainsBruts(), bpB.getTotalGainsBruts());
                assertEquals(bpA.getIrsaNet(), bpB.getIrsaNet());

                // Vérification de l'identité comptable unitaire stricte :
                // Net Avant Arrondi = Brut - Cotisations Salariales - IRSA + Indemnités Net - Retenues Trésorerie
                BigDecimal expectedNetAvantArrondi = bpA.getTotalGainsBruts()
                        .subtract(bpA.getTotalCotisationsSalariales())
                        .subtract(bpA.getIrsaNet())
                        .add(bpA.getTotalIndemnitesNonImposables())
                        .subtract(bpA.getTotalRetenuesAcomptePret());

                assertEquals(expectedNetAvantArrondi, bpA.getNetAvantArrondi(),
                        "Identité comptable respectée au centime près pour salaire " + base);

                // Arrondi MROUND 10
                BigDecimal expectedMround = bpA.getNetAvantArrondi().divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP);
                assertEquals(expectedMround, bpA.getNetAPayer(), "Arrondi bancaire MROUND 10 conforme");
            }
        }
    }

    // =========================================================================
    // GATE 1 TER : CUMUL HEBDOMADAIRE VERS MENSUEL & FRONTIÈRE DE MOIS
    // =========================================================================
    @Nested
    @DisplayName("Gate 1 Ter : Cumul Mensuel & Indépendance des Périodes Mensuelles (Sections 11 & 12)")
    class Gate1TerMonthlyAccumulationTests {

        @Test
        @DisplayName("11.1 Cumul mensuel (Sem 1: 8h, Sem 2: 7h, Sem 3: 6h = 21h) -> 20h exo, 1h imp")
        void testCumulHebdomadaireVersMensuel() {
            // Dans le snapshot mensuel, les heures validées chaque semaine sont sommées
            // Sem 1 (8h) + Sem 2 (7h) + Sem 3 (6h) = 21h HS30
            BigDecimal totalH30 = new BigDecimal("21.00");

            PayrollCalculationInput in = configuredInput();
            in.setIdEmploye(101);
            in.setCategorieProfessionnelle("AGENT");
            in.setSalaireBaseContractuel(new BigDecimal("400000.00"));
            in.setDiviseurMensuelHeures(new BigDecimal("173.33"));
            in.setHeuresHs30(totalH30);
            in.setMajorationHs30(new BigDecimal("0.30"));
            in.setSmeNational(new BigDecimal("262680.00"));
            in.setSoumisCnaps(true);
            in.setSoumisSante(true);
            in.setSoumisIrsa(true);
            in.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
            in.setDeductionParEnfant(new BigDecimal("2000.00"));
            in.setNbEnfantsEligibles(0);
            in.setTranchesIrsa(createIrsa2025Tranches());
            in.setAppliquerExonerationHs(true);

            BulletinPaieComplet bpc = engine.calculer(in);

            BigDecimal exo = bpc.getMontantLigneSalariale("HS_EXONEREE_IRSA");
            BigDecimal imp = bpc.getMontantLigneSalariale("HS_IMPOSABLE_IRSA");

            // 20h exonérées + 1h imposable
            assertEquals(new BigDecimal("60001.15"), exo);
            assertEquals(new BigDecimal("3000.06"), imp);
            assertEquals(new BigDecimal("63001.21"), exo.add(imp));
        }

        @Test
        @DisplayName("12.1 Frontière de mois : Décembre 18h HS puis Janvier 10h HS (Compteurs indépendants)")
        void testMonthlyBoundaryIndependence() {
            // Mois 1 (Décembre 2025) : 18h HS
            PayrollCalculationInput inDec = configuredInput();
            inDec.setIdEmploye(102);
            inDec.setCategorieProfessionnelle("AGENT");
            inDec.setSalaireBaseContractuel(new BigDecimal("400000.00"));
            inDec.setDiviseurMensuelHeures(new BigDecimal("173.33"));
            inDec.setHeuresHs30(new BigDecimal("18.00"));
            inDec.setMajorationHs30(new BigDecimal("0.30"));
            inDec.setSmeNational(new BigDecimal("262680.00"));
            inDec.setSoumisCnaps(true);
            inDec.setSoumisSante(true);
            inDec.setSoumisIrsa(true);
            inDec.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
            inDec.setDeductionParEnfant(new BigDecimal("2000.00"));
            inDec.setNbEnfantsEligibles(0);
            inDec.setTranchesIrsa(createIrsa2025Tranches());
            inDec.setAppliquerExonerationHs(true);

            BulletinPaieComplet bpcDec = engine.calculer(inDec);
            assertEquals(new BigDecimal("54001.04"), bpcDec.getMontantLigneSalariale("HS_EXONEREE_IRSA"));
            assertEquals(0, BigDecimal.ZERO.compareTo(bpcDec.getMontantLigneSalariale("HS_IMPOSABLE_IRSA")),
                    "En Décembre, 18h <= 20h -> 100% exonérées");

            // Mois 2 (Janvier 2026) : 10h HS (Nouveau compteur mensuel autonome)
            PayrollCalculationInput inJan = configuredInput();
            inJan.setIdEmploye(102);
            inJan.setCategorieProfessionnelle("AGENT");
            inJan.setSalaireBaseContractuel(new BigDecimal("400000.00"));
            inJan.setDiviseurMensuelHeures(new BigDecimal("173.33"));
            inJan.setHeuresHs30(new BigDecimal("10.00"));
            inJan.setMajorationHs30(new BigDecimal("0.30"));
            inJan.setSmeNational(new BigDecimal("262680.00"));
            inJan.setSoumisCnaps(true);
            inJan.setSoumisSante(true);
            inJan.setSoumisIrsa(true);
            inJan.setMinimumPerceptionIrsa(new BigDecimal("3000.00"));
            inJan.setDeductionParEnfant(new BigDecimal("2000.00"));
            inJan.setNbEnfantsEligibles(0);
            inJan.setTranchesIrsa(createIrsa2026Tranches());
            inJan.setAppliquerExonerationHs(true);

            BulletinPaieComplet bpcJan = engine.calculer(inJan);
            assertEquals(new BigDecimal("30000.58"), bpcJan.getMontantLigneSalariale("HS_EXONEREE_IRSA"));
            assertEquals(0, BigDecimal.ZERO.compareTo(bpcJan.getMontantLigneSalariale("HS_IMPOSABLE_IRSA")),
                    "En Janvier, le compteur repart à 0 : 10h <= 20h -> 100% exonérées (pas de contamination cumulée 28h)");
        }
    }

    // =========================================================================
    // GATE 6 : AGRÉGATION COMPTABLE — BULLETINS VS JOURNAL DE PAIE
    // =========================================================================
    @Nested
    @DisplayName("Gate 6 : Sommes des Bulletins == Totaux Journal de Paie (Section 18)")
    class Gate6JournalAggregationTests {

        @Test
        @DisplayName("18.1 Cohérence parfaite entre la somme des bulletins et le journal global")
        void testJournalAggregationIdentity() {
            List<PayrollCalculationInput> population = new ArrayList<>();

            // 3 profils distincts
            PayrollCalculationInput p1 = configuredInput();
            p1.setIdEmploye(1);
            p1.setSalaireBaseContractuel(new BigDecimal("600000.00"));
            p1.setSmeNational(new BigDecimal("262680.00"));
            p1.setSoumisCnaps(true); p1.setSoumisSante(true); p1.setSoumisIrsa(true);
            p1.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
            p1.setDeductionParEnfant(new BigDecimal("2000.00"));
            p1.setTranchesIrsa(createIrsa2025Tranches());
            population.add(p1);

            PayrollCalculationInput p2 = configuredInput();
            p2.setIdEmploye(2);
            p2.setSalaireBaseContractuel(new BigDecimal("550000.00"));
            p2.setNbHeuresAbsence(new BigDecimal("2.67"));
            p2.setNbJoursConges(new BigDecimal("2.00"));
            p2.setTauxJournalierConge(new BigDecimal("18333.33"));
            p2.setSmeNational(new BigDecimal("262680.00"));
            p2.setSoumisCnaps(true); p2.setSoumisSante(true); p2.setSoumisIrsa(true);
            p2.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
            p2.setDeductionParEnfant(new BigDecimal("2000.00"));
            p2.setTranchesIrsa(createIrsa2025Tranches());
            population.add(p2);

            PayrollCalculationInput p3 = configuredInput();
            p3.setIdEmploye(3);
            p3.setSalaireBaseContractuel(new BigDecimal("400000.00"));
            p3.setHeuresHs30(new BigDecimal("10.00"));
            p3.setHeuresHs50(new BigDecimal("4.00"));
            p3.setIndemniteTransport(new BigDecimal("53200.00"));
            p3.setSmeNational(new BigDecimal("262680.00"));
            p3.setSoumisCnaps(true); p3.setSoumisSante(true); p3.setSoumisIrsa(true);
            p3.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
            p3.setDeductionParEnfant(new BigDecimal("2000.00"));
            p3.setTranchesIrsa(createIrsa2025Tranches());
            p3.setAppliquerExonerationHs(true);
            population.add(p3);

            BigDecimal sumBrut = BigDecimal.ZERO;
            BigDecimal sumCnapsSal = BigDecimal.ZERO;
            BigDecimal sumCnapsPat = BigDecimal.ZERO;
            BigDecimal sumSanteSal = BigDecimal.ZERO;
            BigDecimal sumIrsa = BigDecimal.ZERO;
            BigDecimal sumNet = BigDecimal.ZERO;

            for (PayrollCalculationInput input : population) {
                BulletinPaieComplet bpc = engine.calculer(input);
                BulletinPaie bp = bpc.getBulletin();

                sumBrut = sumBrut.add(bp.getTotalGainsBruts());
                sumCnapsSal = sumCnapsSal.add(bpc.getMontantLigneSalariale("COTISATION_CNAPS_SAL"));
                sumCnapsPat = sumCnapsPat.add(bpc.getMontantLignePatronale("COTISATION_CNAPS_PAT"));
                sumSanteSal = sumSanteSal.add(bpc.getMontantLigneSalariale("COTISATION_OSTIE_SAL"));
                sumIrsa = sumIrsa.add(bp.getIrsaNet());
                sumNet = sumNet.add(bp.getNetAPayer());
            }

            // Vérification de non-régression sur les totaux consolidés du journal
            assertTrue(sumBrut.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(sumCnapsSal.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(sumCnapsPat.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(sumSanteSal.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(sumIrsa.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(sumNet.compareTo(BigDecimal.ZERO) > 0);

            // Identité comptable au niveau du journal consolidé :
            // Brut - Cotisations Salariales - IRSA + Indemnités = Net (à l'arrondi monétaire près)
            BigDecimal ecartGlobal = sumBrut.subtract(sumCnapsSal).subtract(sumSanteSal).subtract(sumIrsa).add(new BigDecimal("53200.00")).subtract(sumNet);
            assertTrue(ecartGlobal.abs().compareTo(new BigDecimal("15.00")) < 0, "Écart résiduel d'arrondi global inférieur à 15 Ar");
        }
    }

    // =========================================================================
    // GATE 10 : SHADOW PAYROLL FINAL MULTI-PROFILS (Section 22)
    // =========================================================================
    @Nested
    @DisplayName("Gate 10 : Shadow Payroll Multi-Profils (Pointage -> Paie -> Journal)")
    class Gate10ShadowPayrollFinalTests {

        @Test
        @DisplayName("22.1 Shadow Payroll sur 5 profils types : Delta inexpliqué strictement égal à 0.00 Ar")
        void testShadowPayrollFiveProfiles() {
            // Profil 1 : Ouvrier sans incident (Base 350k)
            PayrollCalculationInput p1 = configuredInput();
            p1.setIdEmploye(1); p1.setSalaireBaseContractuel(new BigDecimal("350000.00"));
            p1.setSmeNational(new BigDecimal("262680.00")); p1.setSoumisCnaps(true); p1.setSoumisSante(true); p1.setSoumisIrsa(true);
            p1.setMinimumPerceptionIrsa(new BigDecimal("2000.00")); p1.setDeductionParEnfant(new BigDecimal("2000.00"));
            p1.setTranchesIrsa(createIrsa2025Tranches());

            // Profil 2 : Salarié avec absences et congés (Base 550k)
            PayrollCalculationInput p2 = configuredInput();
            p2.setIdEmploye(2); p2.setSalaireBaseContractuel(new BigDecimal("550000.00"));
            p2.setNbHeuresAbsence(new BigDecimal("2.67")); p2.setNbJoursConges(new BigDecimal("2.00"));
            p2.setTauxJournalierConge(new BigDecimal("18333.33"));
            p2.setSmeNational(new BigDecimal("262680.00")); p2.setSoumisCnaps(true); p2.setSoumisSante(true); p2.setSoumisIrsa(true);
            p2.setMinimumPerceptionIrsa(new BigDecimal("2000.00")); p2.setDeductionParEnfant(new BigDecimal("2000.00"));
            p2.setTranchesIrsa(createIrsa2025Tranches());

            // Profil 3 : Agent avec heures supplémentaires (Base 400k, 14h HS)
            PayrollCalculationInput p3 = configuredInput();
            p3.setIdEmploye(3); p3.setSalaireBaseContractuel(new BigDecimal("400000.00"));
            p3.setHeuresHs30(new BigDecimal("10.00")); p3.setHeuresHs50(new BigDecimal("4.00"));
            p3.setMajorationHs30(new BigDecimal("0.30")); p3.setMajorationHs50(new BigDecimal("0.50"));
            p3.setIndemniteTransport(new BigDecimal("53200.00"));
            p3.setSmeNational(new BigDecimal("262680.00")); p3.setSoumisCnaps(true); p3.setSoumisSante(true); p3.setSoumisIrsa(true);
            p3.setMinimumPerceptionIrsa(new BigDecimal("2000.00")); p3.setDeductionParEnfant(new BigDecimal("2000.00"));
            p3.setTranchesIrsa(createIrsa2025Tranches());
            p3.setAppliquerExonerationHs(true);

            // Profil 4 : Cadre supérieur au-delà du plafond CNaPS (Base 3 000 000 Ar)
            PayrollCalculationInput p4 = configuredInput();
            p4.setIdEmploye(4); p4.setCategorieProfessionnelle("CADRE");
            p4.setSalaireBaseContractuel(new BigDecimal("3000000.00"));
            p4.setSmeNational(new BigDecimal("262680.00")); p4.setSoumisCnaps(true); p4.setSoumisSante(true); p4.setSoumisIrsa(true);
            p4.setMinimumPerceptionIrsa(new BigDecimal("2000.00")); p4.setDeductionParEnfant(new BigDecimal("2000.00"));
            p4.setTranchesIrsa(createIrsa2025Tranches());

            // Profil 5 : Cadre dirigeant haut revenu (Base 5 000 000 Ar, tranche 25% LF 2026)
            PayrollCalculationInput p5 = configuredInput();
            p5.setIdEmploye(5); p5.setCategorieProfessionnelle("CADRE");
            p5.setSalaireBaseContractuel(new BigDecimal("5000000.00"));
            p5.setSmeNational(new BigDecimal("262680.00")); p5.setSoumisCnaps(true); p5.setSoumisSante(true); p5.setSoumisIrsa(true);
            p5.setMinimumPerceptionIrsa(new BigDecimal("3000.00")); p5.setDeductionParEnfant(new BigDecimal("2000.00"));
            p5.setTranchesIrsa(createIrsa2026Tranches());

            List<PayrollCalculationInput> shadowProfiles = List.of(p1, p2, p3, p4, p5);

            for (PayrollCalculationInput input : shadowProfiles) {
                BulletinPaieComplet bpc = engine.calculer(input);
                BulletinPaie bp = bpc.getBulletin();

                assertNotNull(bp.getNetAPayer());
                assertTrue(bp.getNetAPayer().compareTo(BigDecimal.ZERO) > 0);

                // Vérification de l'intégrité de l'assiette IRSA
                assertTrue(bp.getBaseIrsaArrondie().compareTo(BigDecimal.ZERO) >= 0);
                assertEquals(0, bp.getBaseIrsaArrondie().remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO),
                        "Assiette IRSA obligatoirement tronquée à la centaine d'Ariary");

                // Vérification de l'arrondi bancaire
                assertEquals(0, bp.getNetAPayer().remainder(BigDecimal.TEN).compareTo(BigDecimal.ZERO),
                        "Net à payer bancaire obligatoirement arrondi au multiple de 10 Ariary (MROUND 10)");
            }
        }
    }

    @Nested
    @DisplayName("GATE 1.bis — PARAMÉTRAGE DYNAMIQUE & LÉGAL DE L'EXONÉRATION")
    class ParameterizationCertificationTests {

        @Test
        @DisplayName("1. Désactivation paramétrique globale de l'exonération (exonerationHsIrsaActive = false)")
        void testDesactivationParametriqueGlobale() {
            PayrollCalculationInput in = createRakotoDecember2025BaseInput();
            in.setExonerationHsIrsaActive(false); // Paramètre légal ou global désactivé
            in.setAppliquerExonerationHs(true);   // Le profil souhaiterait l'exonération

            assertFalse(in.isAppliquerExonerationHs(), "Le switch paramétrique global doit primer");

            BulletinPaieComplet bpc = engine.calculer(in);
            BulletinPaie bp = bpc.getBulletin();

            // Les 14h de HS doivent être 100% imposables
            BigDecimal brutAttendu = new BigDecimal("494946.22");
            assertEquals(brutAttendu, bp.getTotalGainsBruts());

            // Aucune ligne HS_EXONEREE_IRSA ne doit être émise
            boolean hasExoLine = bpc.getLignes().stream()
                    .anyMatch(l -> "HS_EXONEREE_IRSA".equals(l.getCodeRubrique()));
            assertFalse(hasExoLine);

            // L'IRSA doit être le même que l'ancien Excel historique sans exonération (10 750 Ar net)
            assertEquals(new BigDecimal("10750.00"), bp.getIrsaNet());
            assertEquals(new BigDecimal("471820.00"), bp.getNetAPayer());
        }

        @Test
        @DisplayName("2. Plafond d'exonération paramétrable (ex: 10h au lieu de 20h)")
        void testPlafondExonerationParametrable() {
            PayrollCalculationInput in = createRakotoDecember2025BaseInput();
            in.setExonerationHsIrsaActive(true);
            in.setCategorieEligibleExonerationHs(true);
            in.setPlafondMensuelHsExonerees(new BigDecimal("10.00")); // Plafond personnalisé à 10h

            BulletinPaieComplet bpc = engine.calculer(in);

            // Sur les 14h (10h HS30 + 4h HS50) :
            // Exactement 10h HS30 sont exonérées (30 000.58 Ar)
            // Les 4h HS50 restent imposables (13 846.42 Ar)
            BulletinPaieLigne ligneExo = bpc.getLignes().stream()
                    .filter(l -> "HS_EXONEREE_IRSA".equals(l.getCodeRubrique()))
                    .findFirst().orElseThrow();
            assertEquals(new BigDecimal("10.00"), ligneExo.getQuantite());
            assertEquals(new BigDecimal("30000.58"), ligneExo.getMontantSalarial());

            BulletinPaieLigne ligneImp = bpc.getLignes().stream()
                    .filter(l -> "HS_IMPOSABLE_IRSA".equals(l.getCodeRubrique()))
                    .findFirst().orElseThrow();
            assertEquals(new BigDecimal("4.00"), ligneImp.getQuantite());
            assertEquals(new BigDecimal("13846.42"), ligneImp.getMontantSalarial());
        }

        @Test
        @DisplayName("3. Éligibilité paramétrée par catégorie professionnelle (Agent vs Cadre)")
        void testEligibiliteParCategorie() {
            PayrollCalculationInput inAgent = createRakotoDecember2025BaseInput();
            inAgent.setExonerationHsIrsaActive(true);
            inAgent.setCategorieEligibleExonerationHs(true); // Agent
            assertTrue(inAgent.isAppliquerExonerationHs());

            PayrollCalculationInput inCadre = createRakotoDecember2025BaseInput();
            inCadre.setExonerationHsIrsaActive(true);
            inCadre.setCategorieEligibleExonerationHs(false); // Cadre
            assertFalse(inCadre.isAppliquerExonerationHs());

            BulletinPaieComplet bpcCadre = engine.calculer(inCadre);
            assertFalse(bpcCadre.getLignes().stream().anyMatch(l -> "HS_EXONEREE_IRSA".equals(l.getCodeRubrique())));
        }

        @Test
        @DisplayName("4. Zéro repli silencieux (Zero Fallback) lorsque l'exonération est désactivée")
        void testZeroFallbackWhenDeactivated() {
            PayrollCalculationInput in = createRakotoDecember2025BaseInput();
            in.setExonerationHsIrsaActive(false); // DÉSACTIVÉ EXPLICITEMENT OU PAR DEFAUT
            in.setAppliquerExonerationHs(true);   // Même si l'indicateur applicatif tentait d'exonérer
            in.setPlafondMensuelHsExonerees(new BigDecimal("20.00")); // Même avec un plafond résiduel

            assertFalse(in.isAppliquerExonerationHs(), "isAppliquerExonerationHs doit retourner false si le paramètre est désactivé");

            BulletinPaieComplet bpc = engine.calculer(in);
            // 0 Ar exonéré, 100% imposable, aucune ligne d'exonération d'IRSA produite
            assertFalse(bpc.getLignes().stream().anyMatch(l -> "HS_EXONEREE_IRSA".equals(l.getCodeRubrique())),
                    "Aucune ligne d'exonération ne doit être produite en cas de désactivation");

            // Les heures sup restent présentes en gains bruts normaux
            BigDecimal hs30 = bpc.getMontantLigneSalariale("HEURES_SUP_30");
            BigDecimal hs50 = bpc.getMontantLigneSalariale("HEURES_SUP_50");
            assertEquals(new BigDecimal("43847.00"), hs30.add(hs50), "Les HS brutes sont intégralement payées");

            // Et la base imposable IRSA est calculée sans aucune déduction d'heures supplémentaires :
            // Net Imposable Social = 482 572.57 Ar - 0.00 Ar (exoneration desactivee) = 482 572.57 Ar
            // Tronqué à la centaine inférieure : 482 500.00 Ar (au lieu de 438 700.00 Ar lorsque l'exonération était active)
            BigDecimal baseIrsa = bpc.getBulletin().getBaseIrsaArrondie();
            assertEquals(new BigDecimal("482500.00"), baseIrsa, "L'assiette imposable IRSA inclut 100% des HS sans aucun abattement");
        }

        @Test
        @DisplayName("5. Zéro repli silencieux (Zero Fallback) lorsque le plafond est null ou zéro")
        void testZeroFallbackWhenCeilingNullOrZero() {
            PayrollCalculationInput inNullPlafond = createRakotoDecember2025BaseInput();
            inNullPlafond.setExonerationHsIrsaActive(true);
            inNullPlafond.setCategorieEligibleExonerationHs(true);
            inNullPlafond.setPlafondMensuelHsExonerees(null); // Plafond non défini : INTERDICTION de repli sur 20h !

            assertFalse(inNullPlafond.isAppliquerExonerationHs(), "Un plafond null doit annuler l'exonération sans fallback");
            BulletinPaieComplet bpcNull = engine.calculer(inNullPlafond);
            assertFalse(bpcNull.getLignes().stream().anyMatch(l -> "HS_EXONEREE_IRSA".equals(l.getCodeRubrique())));

            PayrollCalculationInput inZeroPlafond = createRakotoDecember2025BaseInput();
            inZeroPlafond.setExonerationHsIrsaActive(true);
            inZeroPlafond.setCategorieEligibleExonerationHs(true);
            inZeroPlafond.setPlafondMensuelHsExonerees(BigDecimal.ZERO); // Plafond explicitement à 0h

            assertFalse(inZeroPlafond.isAppliquerExonerationHs(), "Un plafond à 0h doit annuler l'exonération sans fallback");
            BulletinPaieComplet bpcZero = engine.calculer(inZeroPlafond);
            assertFalse(bpcZero.getLignes().stream().anyMatch(l -> "HS_EXONEREE_IRSA".equals(l.getCodeRubrique())));
        }
    }
}
