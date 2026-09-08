package mg.galaxam.sirh.paie;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.model.paie.service.StcService;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.TrancheIrsa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 1 & Phase 2 Audit Verification Test — Validations P0 et P1")
class Phase1Et2AuditVerificationTest {

    private final CalculateurPaieEngine calculateur = new CalculateurPaieEngine();
    private final PayrollExecutionEngine payrollEngine = new PayrollExecutionEngine(calculateur);

    private static List<TrancheIrsa> baremeLf2026() {
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
    @DisplayName("P0-001 : IRSA sans fallback silencieux — Exception stricte si barème manquant")
    void testIrsaExceptionSiTranchesManquantes() {
        assertThrows(IllegalStateException.class, () ->
                calculateur.calculerIrsa(new BigDecimal("600000.00"), null, 0));
        assertThrows(IllegalStateException.class, () ->
                calculateur.calculerIrsa(new BigDecimal("600000.00"), List.of(), 0));
        assertThrows(IllegalStateException.class, () ->
                calculateur.calculerIrsaBrut(new BigDecimal("600000.00"), null));
        assertThrows(IllegalStateException.class, () ->
                calculateur.calculerIrsaBrut(new BigDecimal("600000.00"), List.of()));
    }

    @Test
    @DisplayName("P0-002 : Diviseur mensuel légal 173.33 uniforme")
    void testDiviseurMensuel17333() {
        assertEquals(new BigDecimal("173.33"), PayrollExecutionEngine.DIVISEUR_HEURES_MENSUELLES);

        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setSalaireBaseContractuel(new BigDecimal("1733300.00"));

        // 1 733 300 / 173.33 = 10 000.0000
        BigDecimal tauxHoraire = in.getTauxHoraireEffectif();
        assertEquals(0, new BigDecimal("10000.0000").compareTo(tauxHoraire));
    }

    @Test
    @DisplayName("P0-006 : Détection centralisée des forfaits et organe de santé selon le site")
    void testCentralisationSiteSanteEtForfaits() {
        // Site Toamasina (TMM) : OMSI, panier 0, transport 53 200
        PayrollCalculationInput inTmm = new PayrollCalculationInput();
        inTmm.setIdEmploye(1);
        inTmm.setMatricule("EMP_TMM");
        inTmm.setNomPrenom("Toamasina Agent");
        inTmm.setRegimeIntervenant("PERMANENT");
        inTmm.setSalaireBaseContractuel(new BigDecimal("1000000.00"));
        inTmm.setNbJoursOuvrables(new BigDecimal("22.0"));
        inTmm.setNbJoursPresents(new BigDecimal("22.0"));
        inTmm.setTranchesIrsa(baremeLf2026());
        inTmm.setCodeSite("TMM");
        inTmm.setCodeSanteOrgane("OMSI");
        inTmm.setTauxSanteSalarial(0.015);
        inTmm.setTauxSantePatronal(0.055);
        inTmm.setForfaitTransportSite(new BigDecimal("53200.00"));
        inTmm.setForfaitRepasSite(BigDecimal.ZERO);

        BulletinPaieComplet resTmm = payrollEngine.calculer(inTmm);
        assertNotNull(resTmm.trouverLigne("COTISATION_OMSI_SAL"));
        assertNull(resTmm.trouverLigne("COTISATION_OSTIE_SAL"));
        assertNull(resTmm.trouverLigne("INDEMNITE_REPAS"));
        assertNotNull(resTmm.trouverLigne("INDEMNITE_TRANSPORT"));
        assertEquals(new BigDecimal("53200.00"), resTmm.trouverLigne("INDEMNITE_TRANSPORT").getMontantSalarial());

        // Site Antananarivo (TNR) : OSTIE, panier 2000/j (44 000), transport 31 200
        PayrollCalculationInput inTnr = new PayrollCalculationInput();
        inTnr.setIdEmploye(2);
        inTnr.setMatricule("EMP_TNR");
        inTnr.setNomPrenom("Antananarivo Agent");
        inTnr.setRegimeIntervenant("PERMANENT");
        inTnr.setSalaireBaseContractuel(new BigDecimal("1000000.00"));
        inTnr.setNbJoursOuvrables(new BigDecimal("22.0"));
        inTnr.setNbJoursPresents(new BigDecimal("22.0"));
        inTnr.setTranchesIrsa(baremeLf2026());
        inTnr.setCodeSite("TNR");
        inTnr.setCodeSanteOrgane("OSTIE");
        inTnr.setTauxSanteSalarial(0.01);
        inTnr.setTauxSantePatronal(0.05);
        inTnr.setForfaitTransportSite(new BigDecimal("31200.00"));
        inTnr.setForfaitRepasSite(new BigDecimal("2000.00"));

        BulletinPaieComplet resTnr = payrollEngine.calculer(inTnr);
        assertNotNull(resTnr.trouverLigne("COTISATION_OSTIE_SAL"));
        assertNull(resTnr.trouverLigne("COTISATION_OMSI_SAL"));
        assertNotNull(resTnr.trouverLigne("INDEMNITE_REPAS"));
        assertEquals(new BigDecimal("44000.00"), resTnr.trouverLigne("INDEMNITE_REPAS").getMontantSalarial());
        assertNotNull(resTnr.trouverLigne("INDEMNITE_TRANSPORT"));
        assertEquals(new BigDecimal("31200.00"), resTnr.trouverLigne("INDEMNITE_TRANSPORT").getMontantSalarial());
    }

    @Test
    @DisplayName("P1-004 : Méthode utilitaire fallback de valorisation congé (StcService.getTauxJournalierDernier)")
    void testFallbackTauxJournalierDernier() {
        BigDecimal salaireBase = new BigDecimal("900000.00");
        // Quand connexion null ou absent de conge_journal_droit -> fallback 1/30ème = 30 000.00
        BigDecimal fallback = StcService.getTauxJournalierDernier(99999, salaireBase, null);
        assertEquals(0, new BigDecimal("30000.00").compareTo(fallback));
    }

    @Test
    @DisplayName("P3-002 : Seuils configurables (absence conversion et retard assiduité)")
    void testSeuilsConfigurablesAssiduiteEtAbsence() {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(10);
        in.setMatricule("EMP_SEUIL");
        in.setNomPrenom("Test Seuil");
        in.setRegimeIntervenant("PERMANENT");
        in.setSalaireBaseContractuel(new BigDecimal("1733300.00")); // Taux horaire = 10 000 Ar
        in.setNbJoursOuvrables(new BigDecimal("22.0"));
        in.setNbJoursPresents(new BigDecimal("21.0"));
        in.setTranchesIrsa(baremeLf2026());

        // Absence : 1 jour avec seuil légal personnalisé à 7.0h (au lieu de 8h)
        in.setModeCalculAbsence("HEURES_REELLES");
        in.setNbJoursAbsence(new BigDecimal("1.0"));
        in.setHeuresJournalieresLegales(new BigDecimal("7.00"));

        // Assiduité : prime de 50 000 avec retard de 30 min (seuil personnalisé à 25 min -> non éligible)
        in.setPrimeAssiduiteForfait(new BigDecimal("50000.00"));
        in.setMinutesRetard(30);
        in.setSeuilRetardAssiduiteMinutes(25); // Moins tolérant que 30 min

        BulletinPaieComplet res = payrollEngine.calculer(in);

        // Retenue absence = 7h x 10 000 = 70 000 Ar (au lieu de 80 000 avec le défaut 8h)
        assertNotNull(res.trouverLigne("RETENUE_ABSENCE"));
        assertEquals(new BigDecimal("70000.00"), res.trouverLigne("RETENUE_ABSENCE").getMontantSalarial());

        // Prime d'assiduité non accordée car 30 min >= seuil 25 min
        assertNull(res.trouverLigne("PRIME_ASSIDUITE"));
    }
}
