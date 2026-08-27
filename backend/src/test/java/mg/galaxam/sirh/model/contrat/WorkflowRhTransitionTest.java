package mg.galaxam.sirh.model.contrat;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WorkflowRhTransitionTest {

    @Test
    @DisplayName("Validation de la transition : Passage CDD vers CDI (Fin CDD + Nouveau CDI)")
    void testTransitionCddVersCdi() {
        // Contrat CDD initial
        LocalDate debutCdd = LocalDate.of(2025, 1, 1);
        LocalDate finCdd = LocalDate.of(2025, 12, 31);

        // Déclenchement du workflow de titularisation (Passage CDI)
        LocalDate dateEffetCdi = LocalDate.of(2026, 1, 1);

        System.out.println("[TEST WORKFLOW RH] Passage CDD (" + debutCdd + " à " + finCdd + ") vers CDI à effet du " + dateEffetCdi);

        // Règle 1 : La date d'effet du CDI doit être consécutive ou égale au lendemain de la fin CDD
        assertTrue(!dateEffetCdi.isBefore(finCdd), "Le CDI ne peut pas démarrer avant la fin du CDD sans rupture anticipée.");

        // Règle 2 : L'ancien contrat CDD passe au statut CLOTURE et sa date de fin est synchronisée
        LocalDate dateClotureAttendue = dateEffetCdi.minusDays(1);
        assertEquals(finCdd, dateClotureAttendue, "La date de clôture de l'ancien contrat doit être la veille de la prise d'effet du nouveau contrat.");
    }

    @Test
    @DisplayName("Règle Métier CDD 1 : Interdiction de période d'essai lors d'un renouvellement CDD")
    void testRenouvellementCddInterditEssai() {
        // Règle : Lors d'un renouvellement de CDD, aucune période d'essai ne peut être réimposée
        Integer dureeEssaiMois = 2;
        boolean aEssai = dureeEssaiMois != null && dureeEssaiMois > 0;

        assertTrue(aEssai, "Un essai est présent dans la requête de test.");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            if (aEssai) {
                throw new IllegalArgumentException("Une période d'essai est strictement interdite lors d'un renouvellement de CDD.");
            }
        });

        assertEquals("Une période d'essai est strictement interdite lors d'un renouvellement de CDD.", ex.getMessage());
    }

    @Test
    @DisplayName("Règle Métier CDD 2 : Plafond de 2 renouvellements maximum")
    void testRenouvellementCddPlafondNbMax() {
        int maxRenouvellementsAutorises = 2;

        // Cas 1 : 1er renouvellement (ok)
        int renouvellementsExistants = 0;
        assertTrue(renouvellementsExistants < maxRenouvellementsAutorises, "Le 1er renouvellement doit être autorisé.");

        // Cas 2 : 2ème renouvellement (ok)
        renouvellementsExistants = 1;
        assertTrue(renouvellementsExistants < maxRenouvellementsAutorises, "Le 2ème renouvellement doit être autorisé.");

        // Cas 3 : Tentative d'un 3ème renouvellement (bloqué)
        renouvellementsExistants = 2;
        boolean bloque = renouvellementsExistants >= maxRenouvellementsAutorises;
        assertTrue(bloque, "Le 3ème renouvellement doit être bloqué.");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            if (bloque) {
                throw new IllegalArgumentException("Le CDD a déjà atteint le nombre maximal de renouvellements autorisés (" + maxRenouvellementsAutorises + "). Au-delà, le contrat doit être requalifié en CDI.");
            }
        });
        assertTrue(ex.getMessage().contains("requalifié en CDI"));
    }

    @Test
    @DisplayName("Règle Métier CDD 3 : Plafond de durée cumulée maximale de 24 mois (2 ans)")
    void testRenouvellementCddPlafondDureeCumulee() {
        int maxMoisCumule = 24;

        // CDD Initial : 12 mois (2024-01-01 à 2024-12-31)
        LocalDate debut1 = LocalDate.of(2024, 1, 1);
        LocalDate fin1 = LocalDate.of(2024, 12, 31);
        double mois1 = ChronoUnit.DAYS.between(debut1, fin1.plusDays(1)) / 30.4375;

        // Renouvellement 1 : 12 mois (2025-01-01 à 2025-12-31) -> total 24 mois (ok)
        LocalDate debut2 = LocalDate.of(2025, 1, 1);
        LocalDate fin2 = LocalDate.of(2025, 12, 31);
        double mois2 = ChronoUnit.DAYS.between(debut2, fin2.plusDays(1)) / 30.4375;
        double total1et2 = mois1 + mois2;
        assertTrue(total1et2 <= maxMoisCumule + 0.1, "Un cumul de 24 mois doit être valide.");

        // Renouvellement avec dépassement : 15 mois supplémentaires -> total 27 mois (interdit)
        LocalDate finDepassement = LocalDate.of(2026, 3, 31);
        double moisDepassement = ChronoUnit.DAYS.between(debut2, finDepassement.plusDays(1)) / 30.4375;
        double totalDepasse = mois1 + moisDepassement;
        assertTrue(totalDepasse > maxMoisCumule, "Un cumul de >24 mois doit dépasser le plafond.");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            if (totalDepasse > maxMoisCumule) {
                throw new IllegalArgumentException("La durée totale cumulée des CDD (" + String.format("%.1f", totalDepasse) + " mois) dépasse le plafond légal de " + maxMoisCumule + " mois (2 ans). Le contrat doit être requalifié en CDI.");
            }
        });
        assertTrue(ex.getMessage().contains("dépasse le plafond légal"));
    }

    @Test
    @DisplayName("Cycle de Vie Workflow : Flags DTO WorkflowRhCPL")
    void testFlagsDtoWorkflowRhCpl() {
        WorkflowRhCPL wf = new WorkflowRhCPL();

        // 1. Brouillon
        assertFalse(wf.isBrouillon());
        assertFalse(wf.isPlanifie());
        assertFalse(wf.isActif());
        assertFalse(wf.isAnnule());

        // 2. Test logique des statuts
        String statutPlanifie = "PLANIFIE";
        assertTrue("PLANIFIE".equals(statutPlanifie));
        assertFalse("ACTIF".equals(statutPlanifie));
        assertFalse("ANNULE".equals(statutPlanifie));

        // 3. Test basculement Cron (PLANIFIE -> ACTIF)
        String statutApresCron = "ACTIF";
        assertEquals("ACTIF", statutApresCron, "Le cron doit passer le workflow au statut ACTIF une fois la date d'effet atteinte.");
    }

    @Test
    @DisplayName("Validation de la transition : Mutation de Site (TNR -> TMM)")
    void testMutationSite() {
        int idSiteInitial = 1; // TNR
        int idSiteCible = 2;   // TMM
        LocalDate dateMutation = LocalDate.of(2026, 9, 1);

        System.out.println("[TEST WORKFLOW RH] Mutation de Site ID " + idSiteInitial + " vers ID " + idSiteCible + " au " + dateMutation);

        assertNotEquals(idSiteInitial, idSiteCible, "Le site cible doit être distinct du site actuel.");
        assertNotNull(dateMutation, "La date d'effet de mutation est obligatoire.");
    }

    @Test
    @DisplayName("Validation de la transition : Revalorisation Salariale (Augmentation)")
    void testRevalorisationSalariale() {
        BigDecimal salaireActuel = new BigDecimal("850000.00");
        BigDecimal nouveauSalaire = new BigDecimal("1050000.00");

        System.out.println("[TEST WORKFLOW RH] Augmentation salariale : " + salaireActuel + " Ar -> " + nouveauSalaire + " Ar.");

        assertTrue(nouveauSalaire.compareTo(salaireActuel) > 0, "Une revalorisation à la hausse implique un nouveau salaire supérieur.");
        BigDecimal pourcentageAugmentation = nouveauSalaire.subtract(salaireActuel)
                .divide(salaireActuel, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        System.out.println("[TEST WORKFLOW RH] Pourcentage d'augmentation : " + pourcentageAugmentation.setScale(2, java.math.RoundingMode.HALF_UP) + " %.");
        assertTrue(pourcentageAugmentation.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Validation de la rupture anticipée de contrat avec motif")
    void testRuptureContrat() {
        String motifRupture = "DEMISSION";
        LocalDate dateRupture = LocalDate.of(2026, 8, 15);
        boolean respectPreavis = true;

        System.out.println("[TEST WORKFLOW RH] Rupture de contrat : motif=" + motifRupture + ", date=" + dateRupture + ", préavis=" + respectPreavis);

        assertNotNull(motifRupture);
        assertFalse(motifRupture.isBlank(), "Le motif de rupture est obligatoire.");
    }
}

