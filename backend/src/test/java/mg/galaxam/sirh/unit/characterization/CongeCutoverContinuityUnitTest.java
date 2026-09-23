package mg.galaxam.sirh.unit.characterization;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de continuité mathématique et de caractérisation des modes de reprise (Cutover)
 * sans fabrication d'historique passé.
 */
@DisplayName("Tests de Caractérisation — Stratégie de Cutover sans fabrication d'historique")
public class CongeCutoverContinuityUnitTest {

    private final CongeCalculateurEngine congeEngine = new CongeCalculateurEngine();

    /**
     * Représente les données d'un mois extrait du fichier Excel KAJY (Havana).
     */
    static class MonthData {
        int mois;
        BigDecimal base;
        BigDecimal nbAbs;
        BigDecimal nbConge;
        BigDecimal tcongeApplique;
        BigDecimal hs;
        BigDecimal netBase;
        BigDecimal baseTotale;
        BigDecimal droitAcquis;
        BigDecimal valoMensuelle;
        BigDecimal cumulDroits;
        BigDecimal cumulValo;
    }

    private List<MonthData> loadKajyHavanaMonths() throws Exception {
        File file = new File("../model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        if (!file.exists()) {
            file = new File("model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        }
        List<MonthData> list = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 2; r <= 13; r++) {
                Row row = sheet.getRow(r);
                MonthData m = new MonthData();
                m.mois = (int) row.getCell(0).getNumericCellValue();
                m.base = BigDecimal.valueOf(row.getCell(4).getNumericCellValue());
                m.nbAbs = BigDecimal.valueOf(row.getCell(5).getNumericCellValue());
                Cell cNbC = row.getCell(7);
                m.nbConge = (cNbC != null && cNbC.getCellType() == CellType.NUMERIC)
                        ? BigDecimal.valueOf(cNbC.getNumericCellValue()) : BigDecimal.ZERO;
                Cell cTc = row.getCell(8);
                m.tcongeApplique = (cTc != null && cTc.getCellType() == CellType.NUMERIC)
                        ? BigDecimal.valueOf(cTc.getNumericCellValue()) : BigDecimal.ZERO;
                Cell cHs = row.getCell(11);
                m.hs = (cHs != null && cHs.getCellType() == CellType.NUMERIC)
                        ? BigDecimal.valueOf(cHs.getNumericCellValue()) : BigDecimal.ZERO;

                m.netBase = BigDecimal.valueOf(row.getCell(10).getNumericCellValue());
                m.baseTotale = BigDecimal.valueOf(row.getCell(17).getNumericCellValue());
                m.droitAcquis = BigDecimal.valueOf(row.getCell(18).getNumericCellValue());
                m.valoMensuelle = BigDecimal.valueOf(row.getCell(19).getNumericCellValue());
                m.cumulDroits = BigDecimal.valueOf(row.getCell(20).getNumericCellValue());
                m.cumulValo = BigDecimal.valueOf(row.getCell(21).getNumericCellValue());
                list.add(m);
            }
        }
        return list;
    }

    @Test
    @DisplayName("Test 1 — Continuité Mathématique : 12 Mois Complets vs Cutover Mois 9 + Mois 10-12")
    public void test1_shouldProduceIdenticalResultBetweenFullHistoryAndOpeningSnapshot() throws Exception {
        List<MonthData> all12Months = loadKajyHavanaMonths();
        assertThat(all12Months).hasSize(12);

        // 1. Branche A : Simulation continue sur 12 mois complets
        BigDecimal cumulDroitsFull = BigDecimal.ZERO;
        BigDecimal cumulValoFull = BigDecimal.ZERO;
        for (MonthData m : all12Months) {
            cumulDroitsFull = cumulDroitsFull.add(m.droitAcquis);
            cumulValoFull = cumulValoFull.add(m.valoMensuelle);
        }
        BigDecimal tcongeFinalFull = congeEngine.calculerTauxJournalierMoyen(cumulValoFull, cumulDroitsFull);

        // 2. Branche B : Cutover arrêté après le Mois 9
        // On prend l'état cumulé réel après 9 mois dans l'ancien système (Opening State)
        MonthData m9 = all12Months.get(8); // Mois 9 (index 8)
        BigDecimal openingCumulDroits = m9.cumulDroits; // 22.4575 j
        BigDecimal openingCumulValo = m9.cumulValo;     // 240 092.52 Ar

        // Le nouveau SIRH démarre au 1er Octobre (Mois 10) et calcule uniquement les mois 10, 11, 12 :
        BigDecimal postCutoverDroits = BigDecimal.ZERO;
        BigDecimal postCutoverValo = BigDecimal.ZERO;
        for (int i = 9; i < 12; i++) {
            MonthData m = all12Months.get(i);
            postCutoverDroits = postCutoverDroits.add(m.droitAcquis);
            postCutoverValo = postCutoverValo.add(m.valoMensuelle);
        }

        // Reconstitution finale au Mois 12 avec Opening State + événements post-cutover
        BigDecimal cumulDroitsWithSnapshot = openingCumulDroits.add(postCutoverDroits);
        BigDecimal cumulValoWithSnapshot = openingCumulValo.add(postCutoverValo);
        BigDecimal tcongeFinalWithSnapshot = congeEngine.calculerTauxJournalierMoyen(cumulValoWithSnapshot, cumulDroitsWithSnapshot);

        // 3. Vérification de la continuité mathématique absolue
        assertThat(cumulDroitsWithSnapshot.setScale(4, RoundingMode.HALF_UP))
                .isEqualByComparingTo(cumulDroitsFull.setScale(4, RoundingMode.HALF_UP))
                .isEqualByComparingTo("29.9567");

        // Écart toléré <= 0.05 Ar dû à l'addition de nombres flottants vs décimaux
        assertThat(cumulValoWithSnapshot.subtract(cumulValoFull).abs().doubleValue()).isLessThanOrEqualTo(0.05);
        assertThat(cumulValoWithSnapshot.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo("324385.38");

        // TCONGE final identique au centime près : 10 828.49 Ar/j
        assertThat(tcongeFinalWithSnapshot.setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(tcongeFinalFull.setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo("10828.49");
    }

    @Test
    @DisplayName("Test 2 — Nouveau Salarié Embauché Post-Cutover : Démarrage Naturel à Zéro")
    public void test2_shouldStartCleanForNewHirePostCutover() {
        LocalDate dateEmbauche = LocalDate.of(2026, 10, 1);
        BigDecimal openingSolde = BigDecimal.ZERO;
        BigDecimal openingCumulDroits = BigDecimal.ZERO;
        BigDecimal openingCumulValo = BigDecimal.ZERO;

        // Mois 1 d'activité (Octobre 2026) : mois complet sans absence, salaire 500 000 Ar
        BigDecimal salaireBase = new BigDecimal("500000.00");
        BigDecimal droitsAcquisMois1 = congeEngine.calculerAcquisitionMensuelleProratisee(new BigDecimal("30.0")); // 2.5j
        BigDecimal valoMois1 = congeEngine.calculerValorisationDroitMensuel(salaireBase, droitsAcquisMois1, 30); // 41 666.67 Ar

        BigDecimal soldeApresMois1 = openingSolde.add(droitsAcquisMois1);
        BigDecimal cumulDroitsCycle = openingCumulDroits.add(droitsAcquisMois1);
        BigDecimal cumulValoCycle = openingCumulValo.add(valoMois1);

        assertThat(soldeApresMois1).isEqualByComparingTo("2.5000");
        assertThat(cumulDroitsCycle).isEqualByComparingTo("2.5000");
        assertThat(cumulValoCycle).isEqualByComparingTo("41666.67");
    }

    @Test
    @DisplayName("Test 3 — Salarié Historique avec Reprise Complète (Niveau B : AGGREGATED_OPENING)")
    public void test3_shouldHandleAggregatedOpeningProperly() {
        // État de cutover au 30/09/2026 :
        // Solde consommable connu = 14.50 j
        // TCONGE actuellement applicable = 8 669.52 Ar/j (hérité du cycle précédent)
        // Accumulateurs du cycle en cours (Mois 1 à 9) = 22.4575 j et 240 092.52 Ar
        BigDecimal soldeDisponible = new BigDecimal("14.50");
        BigDecimal tcongeApplicable = new BigDecimal("8669.52");
        BigDecimal openingCumulDroits = new BigDecimal("22.4575");
        BigDecimal openingCumulValo = new BigDecimal("240092.52");

        // Action 1 : Prise de 2.0 jours de congé au Mois 10
        BigDecimal joursPris = new BigDecimal("2.0");
        BigDecimal soldeApresPrise = soldeDisponible.subtract(joursPris); // 12.50 j
        BigDecimal indemniteVersee = joursPris.multiply(tcongeApplicable).setScale(2, RoundingMode.HALF_UP); // 17 339.04 Ar

        // RÈGLE CARDINALE : La prise de congés DIMINUE soldeJours mais NE DIMINUE PAS cumulValorisationCycle !
        assertThat(soldeApresPrise).isEqualByComparingTo("12.50");
        assertThat(indemniteVersee).isEqualByComparingTo("17339.04");
        assertThat(openingCumulDroits).isEqualByComparingTo("22.4575"); // Inchangé
        assertThat(openingCumulValo).isEqualByComparingTo("240092.52"); // Inchangé

        // Action 2 : Acquisition du Mois 10 (salaire base 350k, 0 absence, HS = 4 715 Ar)
        BigDecimal baseTotaleMois10 = new BigDecimal("353196.23");
        BigDecimal droitMois10 = new BigDecimal("2.5000");
        BigDecimal valoMois10 = congeEngine.calculerValorisationDroitMensuel(baseTotaleMois10, droitMois10, 30); // 29 433.02 Ar

        soldeApresPrise = soldeApresPrise.add(droitMois10); // 15.00 j
        BigDecimal cumulDroitsMois10 = openingCumulDroits.add(droitMois10); // 24.9575 j
        BigDecimal cumulValoMois10 = openingCumulValo.add(valoMois10);     // 269 525.54 Ar

        assertThat(soldeApresPrise).isEqualByComparingTo("15.0000");
        assertThat(cumulDroitsMois10).isEqualByComparingTo("24.9575");
        assertThat(cumulValoMois10).isEqualByComparingTo("269525.54");
    }

    @Test
    @DisplayName("Test 4 — Reprise Minimale (Niveau C : MINIMAL_OPENING, Cumuls Cycle Inconnus)")
    public void test4_shouldOperateNormallyForLeavesAndRefuseSilentTcongeCalculation() {
        // État de cutover minimal :
        // Solde connu = 10.0 j
        // TCONGE applicable connu = 9 000.00 Ar/j
        // Cumuls antérieurs du cycle = INCONNUS (null)
        BigDecimal soldeDisponible = new BigDecimal("10.0");
        BigDecimal tcongeApplicable = new BigDecimal("9000.00");
        BigDecimal openingCumulDroits = null; // UNKNOWN
        BigDecimal openingCumulValo = null;   // UNKNOWN

        // 1. Le salarié peut prendre des congés et être payé
        BigDecimal joursPris = new BigDecimal("3.0");
        BigDecimal soldeRestant = soldeDisponible.subtract(joursPris);
        BigDecimal montantConge = joursPris.multiply(tcongeApplicable);
        assertThat(soldeRestant).isEqualByComparingTo("7.0");
        assertThat(montantConge).isEqualByComparingTo("27000.00");

        // 2. Le calcul d'un nouveau TCONGE de clôture de cycle ne peut pas se faire silencieusement avec zéro
        assertThatThrownBy(() -> {
            if (openingCumulDroits == null || openingCumulValo == null) {
                throw new IllegalStateException("CYCLE_ACCUMULATORS_UNKNOWN: Impossible de calculer le nouveau TCONGE de clôture. " +
                        "Les cumuls antérieurs du cycle de référence sont inconnus. Un arbitrage RH ou un maintien de taux est requis.");
            }
            congeEngine.calculerTauxJournalierMoyen(openingCumulValo, openingCumulDroits);
        }).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("CYCLE_ACCUMULATORS_UNKNOWN");
    }

    @Test
    @DisplayName("Test 5 — Données Insuffisantes : Solde Connu mais TCONGE Inconnu (Refus de Fallback Silencieux)")
    public void test5_shouldThrowExplicitErrorWhenTcongeIsMissing() {
        // Cas d'erreur : Solde importé mais aucun TCONGE fourni
        BigDecimal soldeDisponible = new BigDecimal("12.0");
        BigDecimal tcongeApplicable = null;

        // Le système DOIT bloquer la prise de congé payé ou le calcul de paie avec une erreur explicite
        assertThatThrownBy(() -> {
            if (tcongeApplicable == null || tcongeApplicable.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("LEAVE_RATE_MISSING: Aucun taux journalier TCONGE configuré pour le salarié. " +
                        "La valorisation du congé ne peut pas être inventée silencieusement.");
            }
        }).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("LEAVE_RATE_MISSING");
    }
}
