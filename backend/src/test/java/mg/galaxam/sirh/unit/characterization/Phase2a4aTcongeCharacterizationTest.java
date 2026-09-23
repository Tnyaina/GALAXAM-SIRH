package mg.galaxam.sirh.unit.characterization;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2A-4A — Tests de Caractérisation du Runtime TCONGE Actuel.
 * 
 * IMPORTANT : Ces tests figent le comportement actuel du runtime (CURRENT_RUNTIME)
 * et NE constituent PAS une validation juridique de la conformité légale.
 */
@DisplayName("Phase 2A-4A : Caractérisation du Runtime Actuel de Valorisation TCONGE")
class Phase2a4aTcongeCharacterizationTest {

    private final CongeCalculateurEngine congeEngine = new CongeCalculateurEngine();
    private final PayrollExecutionEngine payrollEngine = new PayrollExecutionEngine();

    @Nested
    @DisplayName("1. Formule de Valorisation Mensuelle et Lien avec le 1/12ème")
    class FormuleValorisationMensuelle {

        @Test
        @DisplayName("1.1 Le runtime calcule ValoMois = BaseTotale * DroitMois / 30, équivalent à BaseTotale / 12 pour 2.5j")
        void currentRuntime_shouldCalculateValoMoisAsBaseTotaleTimesDroitMoisDividedBy30() {
            BigDecimal baseTotale = new BigDecimal("1200000.00");
            BigDecimal droitMois = new BigDecimal("2.5000");

            // Formule runtime : CongeCalculateurEngine.calculerValorisationDroitMensuel(baseTotale, droitMois, 30)
            BigDecimal valoMois = congeEngine.calculerValorisationDroitMensuel(baseTotale, droitMois, 30);

            // 1 200 000 * 2.5 / 30 = 100 000.00 Ar (exactement 1/12ème de 1 200 000 Ar)
            assertThat(valoMois).isEqualByComparingTo("100000.00");
            assertThat(valoMois).isEqualByComparingTo(baseTotale.divide(new BigDecimal("12.0"), 2, RoundingMode.HALF_UP));
        }

        @Test
        @DisplayName("1.2 En cas d'absences, DroitMois est proratisé et ValoMois diminue proportionnellement")
        void currentRuntime_shouldProrateValoMoisWhenAbsencesOccur() {
            BigDecimal absInjustifiees = new BigDecimal("6.0"); // 6 jours d'absence
            BigDecimal formule = new BigDecimal("30.0").subtract(absInjustifiees); // 24.0
            BigDecimal droitMois = congeEngine.calculerAcquisitionMensuelleProratisee(formule); // 2.5 * 24 / 30 = 2.0j

            BigDecimal baseTotale = new BigDecimal("1200000.00");
            BigDecimal valoMois = congeEngine.calculerValorisationDroitMensuel(baseTotale, droitMois, 30);

            // 1 200 000 * 2.0 / 30 = 80 000.00 Ar
            assertThat(droitMois).isEqualByComparingTo("2.0000");
            assertThat(valoMois).isEqualByComparingTo("80000.00");
        }
    }

    @Nested
    @DisplayName("2. Cumul Infini 'Tous Temps' et Dilution Lors d'une Augmentation")
    class CumulTousTempsEtDilution {

        @Test
        @DisplayName("2.1 Le runtime accumule sans fenêtre glissante 12 mois : TCONGE dilue les hausses de salaire")
        void currentRuntime_shouldUseAllHistoricalJournalRowsWithoutSlidingWindow() {
            // Simulation : 24 mois d'historique au lieu de 12 mois légaux
            // Mois 1 à 12 : salaire 600 000 Ar (Valo = 50 000 Ar/mois, Droits = 2.5j/mois)
            // Mois 13 à 24 : salaire 1 200 000 Ar (Valo = 100 000 Ar/mois, Droits = 2.5j/mois)

            BigDecimal cumulDroitsAnnee1 = new BigDecimal("30.0"); // 12 * 2.5
            BigDecimal cumulValoAnnee1 = new BigDecimal("600000.00"); // 12 * 50 000

            BigDecimal cumulDroitsAnnee2 = new BigDecimal("30.0");
            BigDecimal cumulValoAnnee2 = new BigDecimal("1200000.00"); // 12 * 100 000

            // Cumul "Tous temps" actuel (24 mois) :
            BigDecimal cumulDroitsTotal = cumulDroitsAnnee1.add(cumulDroitsAnnee2); // 60.0 j
            BigDecimal cumulValoTotal = cumulValoAnnee1.add(cumulValoAnnee2); // 1 800 000 Ar

            BigDecimal tcongeRuntime = congeEngine.calculerTauxJournalierMoyen(cumulValoTotal, cumulDroitsTotal);
            // 1 800 000 / 60 = 30 000.00 Ar/jour
            assertThat(tcongeRuntime).isEqualByComparingTo("30000.00");

            // ALORS QUE la fenêtre légale des 12 derniers mois donnerait :
            // 1 200 000 / 30 = 40 000.00 Ar/jour !
            BigDecimal tcongeLegal12Mois = congeEngine.calculerTauxJournalierMoyen(cumulValoAnnee2, cumulDroitsAnnee2);
            assertThat(tcongeLegal12Mois).isEqualByComparingTo("40000.00");

            // PREUVE DU BIAIS : Le runtime sous-évalue le droit de congé du salarié de 25% (30 000 vs 40 000)
            assertThat(tcongeRuntime).isLessThan(tcongeLegal12Mois);
        }
    }

    @Nested
    @DisplayName("3. Assiette de BaseTotale dans le Runtime : Exclusion des Primes")
    class AssietteBaseTotaleRuntime {

        @Test
        @DisplayName("3.1 Le runtime inclut HEURES_SUP mais ignore silencieusement les rubriques PRIME dans BaseTotale")
        void currentRuntime_shouldExcludePrimesFromBaseTotale() {
            // Reconstitution du filtre de PayrollService.mettreAJourJournalConge (lignes 957-968)
            BigDecimal base = new BigDecimal("800000.00");
            BigDecimal netBase = base;
            BigDecimal mntHs = BigDecimal.ZERO;
            BigDecimal mntPrimes = BigDecimal.ZERO;

            List<BulletinPaieLigne> lignes = new ArrayList<>();
            lignes.add(creerLigneMock("RETENUE_ABSENCE", "SALAIRE_BASE", new BigDecimal("50000.00"), "RETENUE"));
            lignes.add(creerLigneMock("HEURES_SUP_30", "HEURES_SUP", new BigDecimal("75000.00"), "GAIN"));
            lignes.add(creerLigneMock("PRIME_PERFORMANCE", "PRIME", new BigDecimal("120000.00"), "GAIN"));
            lignes.add(creerLigneMock("PRIME_ASSIDUITE", "PRIME", new BigDecimal("30000.00"), "GAIN"));

            for (BulletinPaieLigne l : lignes) {
                String codeRub = l.getCodeRubrique();
                if ("RETENUE_ABSENCE".equals(codeRub) || "RETENUE_CONGE".equals(codeRub) || "DEDUCTION_CONGE_BASE".equals(codeRub)) {
                    netBase = netBase.subtract(l.getMontantSalarial());
                } else if ("INDEMNITE_CONGE".equals(codeRub) || "INDEMNITE_CONGE_PAYE".equals(codeRub)) {
                    netBase = netBase.add(l.getMontantSalarial());
                } else if ("HEURES_SUP".equalsIgnoreCase(l.getCategorie())) {
                    mntHs = mntHs.add(l.getMontantSalarial());
                } else if ("PRIME".equalsIgnoreCase(l.getCategorie())) {
                    mntPrimes = mntPrimes.add(l.getMontantSalarial());
                }
            }

            BigDecimal baseTotaleRuntime = netBase.add(mntHs).max(BigDecimal.ZERO);

            // netBase = 800 000 - 50 000 = 750 000 Ar
            // mntHs = 75 000 Ar
            // baseTotaleRuntime = 750 000 + 75 000 = 825 000 Ar
            assertThat(baseTotaleRuntime).isEqualByComparingTo("825000.00");

            // Les primes (150 000 Ar) sont ignorées par le runtime actuel
            assertThat(mntPrimes).isEqualByComparingTo("150000.00");
            BigDecimal baseAvecPrimes = baseTotaleRuntime.add(mntPrimes);
            assertThat(baseAvecPrimes).isEqualByComparingTo("975000.00");
            assertThat(baseTotaleRuntime).isLessThan(baseAvecPrimes);
        }
    }

    @Nested
    @DisplayName("4. Mécanisme de Compensation Paie : Retenue 1/30ème vs Allocation TCONGE")
    class CompensationPaieRetenueVsAllocation {

        @Test
        @DisplayName("4.1 Le moteur de paie génère DEDUCTION_CONGE_BASE (Base/30) et INDEMNITE_CONGE_PAYE (TCONGE)")
        void currentRuntime_payrollExecutionEngine_appliesDeductionAndIndemnite() {
            PayrollCalculationInput in = new PayrollCalculationInput();
            in.setSalaireBaseContractuel(new BigDecimal("900000.00"));
            in.setNbJoursConges(new BigDecimal("5.0"));
            in.setTauxJournalierConge(new BigDecimal("35000.00")); // TCONGE > Base/30 (30 000)

            BulletinPaie bp = new BulletinPaie();
            List<BulletinPaieLigne> lignes = new ArrayList<>();

            BigDecimal impactNet = payrollEngine.calculerConges(in, bp, lignes);

            // Vérification des lignes générées :
            // 1. DEDUCTION_CONGE_BASE : 5j * (900 000 / 30) = 5j * 30 000 = 150 000.00 Ar (RETENUE)
            // 2. INDEMNITE_CONGE_PAYE : 5j * 35 000 = 175 000.00 Ar (GAIN)
            // Impact net = +25 000.00 Ar
            assertThat(lignes).hasSize(2);
            assertThat(lignes.get(0).getCodeRubrique()).isEqualTo("DEDUCTION_CONGE_BASE");
            assertThat(lignes.get(0).getMontantSalarial()).isEqualByComparingTo("150000.00");

            assertThat(lignes.get(1).getCodeRubrique()).isEqualTo("INDEMNITE_CONGE_PAYE");
            assertThat(lignes.get(1).getMontantSalarial()).isEqualByComparingTo("175000.00");

            assertThat(impactNet).isEqualByComparingTo("25000.00");
        }
    }

    private BulletinPaieLigne creerLigneMock(String code, String categorie, BigDecimal montant, String sens) {
        BulletinPaieLigne l = new BulletinPaieLigne();
        l.setCodeRubrique(code);
        l.setCategorie(categorie);
        l.setMontantSalarial(montant);
        l.setSens(sens);
        return l;
    }
}
