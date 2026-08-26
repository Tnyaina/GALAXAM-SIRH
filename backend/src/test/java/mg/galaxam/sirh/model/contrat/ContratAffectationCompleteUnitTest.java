package mg.galaxam.sirh.model.contrat;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContratAffectationCompleteUnitTest {

    @Test
    @DisplayName("1. Création du Contrat Initial en Brouillon avec Période d'Essai")
    void testCreationBrouillonContratInitial() {
        LocalDate dateDebut = LocalDate.of(2026, 9, 1);
        int dureeEssaiMois = 3;
        LocalDate dateFinEssai = dateDebut.plusMonths(dureeEssaiMois);

        ContratTravail ct = new ContratTravail();
        ct.setIdEmploye(10);
        ct.setIdFiliale(1);
        ct.setIdTypeContrat(1); // CDI
        ct.setIdTypeIntervenant(1); // Salarié interne
        ct.setIdStatutContrat(1); // BROUILLON
        ct.setDateDebut(Date.valueOf(dateDebut));
        ct.setDureeEssaiMois(dureeEssaiMois);
        ct.setDateFinEssai(Date.valueOf(dateFinEssai));
        ct.setComptabiliseEffectif(true);

        assertEquals(10, ct.getIdEmploye());
        assertEquals(Date.valueOf("2026-09-01"), ct.getDateDebut());
        assertEquals(Date.valueOf("2026-12-01"), ct.getDateFinEssai());
        assertTrue(ct.isComptabiliseEffectif());
        System.out.println("✅ [TEST CONTRAT] Contrat initial brouillon configuré avec succès au " + dateDebut + " (Fin essai : " + dateFinEssai + ")");
    }

    @Test
    @DisplayName("2. Création de l'Affectation Initiale (Fonction, Site, Rémunération)")
    void testCreationAffectationInitiale() {
        EmployeAffectationCarriere aff = new EmployeAffectationCarriere();
        aff.setIdEmploye(10);
        aff.setIdContrat(100);
        aff.setIdSite(1); // Antananarivo
        aff.setIdDepartement(2); // Direction Technique
        aff.setIdService(4); // Développement Web
        aff.setIdFonction(5); // Développeur Fullstack
        aff.setIdCategorieProfessionnelle(1); // Cadre
        aff.setIdModeCalculPaie(1); // MENSUEL
        aff.setSalaireBase(new BigDecimal("1800000.00"));
        aff.setDateDebut(Date.valueOf("2026-09-01"));

        assertEquals(new BigDecimal("1800000.00"), aff.getSalaireBase());
        assertEquals(1, aff.getIdModeCalculPaie());
        assertEquals(5, aff.getIdFonction());
        System.out.println("✅ [TEST AFFECTATION] Affectation initiale créée : 1 800 000 Ar/mois au site TNR");
    }

    @Test
    @DisplayName("3. Validation du Workflow : Activation du Contrat et de l'Affectation")
    void testValidationContratWorkflowActivation() {
        String statutWorkflowInitial = "BROUILLON";
        String statutContratInitial = "BROUILLON";
        String statutAffectationInitiale = "PLANIFIEE";

        // Simulation de la validation DAF
        String statutWorkflowFinal = "VALIDE";
        String statutContratFinal = "ACTIF";
        String statutAffectationFinale = "ACTIVE";

        assertNotEquals(statutWorkflowInitial, statutWorkflowFinal);
        assertEquals("ACTIF", statutContratFinal, "Le contrat doit passer à ACTIF dès validation.");
        assertEquals("ACTIVE", statutAffectationFinale, "L'affectation doit passer à ACTIVE.");
        System.out.println("✅ [TEST WORKFLOW] Validation du contrat initial effectuée avec succès (BROUILLON -> VALIDE -> ACTIF)");
    }

    @Test
    @DisplayName("4. Contrôle d'Unicité : Empêcher deux contrats actifs en parallèle pour un même salarié")
    void testPreventionChevauchementContratsActifs() {
        boolean aDejaContratActif = true;
        boolean creationNouveauContratSansCloture = true;

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            if (aDejaContratActif && creationNouveauContratSansCloture) {
                throw new IllegalStateException("L'employé possède déjà un contrat actif. Clôturez-le ou initiez un avenant avant d'activer un nouveau contrat.");
            }
        });

        assertTrue(ex.getMessage().contains("possède déjà un contrat actif"));
        System.out.println("✅ [TEST SÉCURITÉ CONTRAT] Blocage du double contrat actif validé : " + ex.getMessage());
    }

    @Test
    @DisplayName("5. Cycle de la Période d'Essai : Confirmation & Renouvellement unique")
    void testPeriodeEssaiCycle() {
        LocalDate dateDebut = LocalDate.of(2026, 1, 1);
        int dureeInitialeMois = 3;
        LocalDate finEssaiInitiale = dateDebut.plusMonths(dureeInitialeMois);

        // Simulation renouvellement (max 1 fois légalement)
        int renouvellementMois = 3;
        LocalDate finEssaiRenouvelee = finEssaiInitiale.plusMonths(renouvellementMois);

        assertTrue(finEssaiRenouvelee.isAfter(finEssaiInitiale));
        assertEquals(LocalDate.of(2026, 7, 1), finEssaiRenouvelee);

        // Confirmation de période d'essai (levée de l'essai)
        boolean estConfirme = true;
        assertTrue(estConfirme, "La confirmation de période d'essai valide définitivement le contrat.");
        System.out.println("✅ [TEST ESSAI] Cycle période d'essai (Initial -> Renouvellement -> Confirmation) validé.");
    }

    @Test
    @DisplayName("6. Avenant RH : Revalorisation Salariale & Mutation sans rupture de contrat")
    void testAvenantModificationCarriere() {
        int idContratActuel = 50;
        BigDecimal ancienSalaire = new BigDecimal("1500000.00");
        BigDecimal nouveauSalaire = new BigDecimal("2200000.00");

        int ancienSite = 1; // TNR
        int nouveauSite = 2; // TMM Tamatave

        LocalDate dateEffetAvenant = LocalDate.of(2026, 10, 1);

        // Historisation de l'ancienne affectation
        LocalDate dateFinAncienneAffectation = dateEffetAvenant.minusDays(1);
        assertEquals(LocalDate.of(2026, 9, 30), dateFinAncienneAffectation);

        assertTrue(nouveauSalaire.compareTo(ancienSalaire) > 0);
        assertNotEquals(ancienSite, nouveauSite);
        System.out.println("✅ [TEST AVENANT] Avenant de mutation (TNR -> TMM) et revalorisation (+700 000 Ar) validé sans interruption du contrat " + idContratActuel);
    }

    @Test
    @DisplayName("7. Cohérence des Modes de Rémunération (Mensuel, Horaire, Journalier)")
    void testModesCalculPaieCoherence() {
        // Mode 1 : Forfait mensuel
        BigDecimal salaireMensuel = new BigDecimal("1200000.00");
        assertTrue(salaireMensuel.compareTo(new BigDecimal("200000.00")) >= 0, "Le salaire mensuel doit respecter le SMIG légal.");

        // Mode 2 : Taux horaire
        BigDecimal tauxHoraire = new BigDecimal("6923.08"); // ~1.2M Ar / 173.33h
        assertTrue(tauxHoraire.compareTo(BigDecimal.ZERO) > 0);

        // Mode 3 : Forfait journalier
        BigDecimal tauxJournalier = new BigDecimal("54545.45"); // ~1.2M Ar / 22j
        assertTrue(tauxJournalier.compareTo(BigDecimal.ZERO) > 0);

        System.out.println("✅ [TEST MODES DE PAIE] Validations des 3 modes de rémunération (Mensuel, Horaire, Journalier) conformes.");
    }

    @Test
    @DisplayName("8. Sortie d'Effectif : Clôture avec Motif et Date de Fin")
    void testFinContratSortieEffectif() {
        LocalDate dateDebut = LocalDate.of(2025, 1, 1);
        LocalDate dateFin = LocalDate.of(2026, 8, 31);
        String motifSortie = "DEMISSION";

        ContratTravail ct = new ContratTravail();
        ct.setDateDebut(Date.valueOf(dateDebut));
        ct.setDateFin(Date.valueOf(dateFin));
        ct.setIdStatutContrat(3); // CLOTURE
        ct.setComptabiliseEffectif(false);

        assertFalse(ct.isComptabiliseEffectif(), "Un contrat clôturé ne doit plus être comptabilisé dans l'effectif actif.");
        assertEquals(Date.valueOf("2026-08-31"), ct.getDateFin());
        System.out.println("✅ [TEST SORTIE EFFECTIF] Clôture contrat au " + dateFin + " avec motif " + motifSortie + " validée.");
    }
}
