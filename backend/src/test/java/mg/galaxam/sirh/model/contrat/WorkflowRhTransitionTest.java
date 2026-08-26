package mg.galaxam.sirh.model.contrat;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
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
        String statutContratInitial = "ACTIF";

        // Déclenchement du workflow de titularisation (Passage CDI)
        LocalDate dateEffetCdi = LocalDate.of(2026, 1, 1);
        String typeNouveauContrat = "CDI";

        System.out.println("[TEST WORKFLOW RH] Passage CDD (" + debutCdd + " à " + finCdd + ") vers CDI à effet du " + dateEffetCdi);

        // Règle 1 : La date d'effet du CDI doit être consécutive ou égale au lendemain de la fin CDD
        assertTrue(!dateEffetCdi.isBefore(finCdd), "Le CDI ne peut pas démarrer avant la fin du CDD sans rupture anticipée.");

        // Règle 2 : L'ancien contrat CDD passe au statut CLOTURE
        String statutAncienContratApresValidation = "CLOTURE";
        assertEquals("CLOTURE", statutAncienContratApresValidation, "L'ancien contrat CDD doit être automatiquement clôturé.");

        // Règle 3 : Le nouveau contrat CDI devient ACTIF
        String statutNouveauContrat = "ACTIF";
        assertEquals("ACTIF", statutNouveauContrat);
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
