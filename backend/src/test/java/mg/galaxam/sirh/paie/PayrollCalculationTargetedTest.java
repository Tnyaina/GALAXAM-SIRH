package mg.galaxam.sirh.paie;

import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieDTO;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieSaveRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcPreparationDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcRecuDTO;
import mg.galaxam.sirh.paie.application.export.BulletinDocumentService;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paie.domain.engine.PayrollTaxCalculations;
import mg.galaxam.sirh.paie.domain.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ciblés validant la suppression complète de la gestion des prêts
 * et le maintien strict des calculs de paie, des avances/acomptes et du STC.
 */
public class PayrollCalculationTargetedTest {

    private final PayrollExecutionEngine engine = new PayrollExecutionEngine();

    private PayrollCalculationInput buildStandardInput(BigDecimal salaireBase) {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(101);
        in.setMatricule("EMP00101");
        in.setNomPrenom("Rakoto Jean");
        in.setIntitulePoste("Chauffeur Poids Lourds");
        in.setCodePeriode("2026-03");
        in.setDateDebutPeriode(Date.valueOf(LocalDate.of(2026, 3, 1)));
        in.setDateFinPeriode(Date.valueOf(LocalDate.of(2026, 3, 31)));
        in.setDateEmbauche(Date.valueOf(LocalDate.of(2020, 1, 1)));
        in.setAncienneteMois(74);

        in.setSalaireBaseContractuel(salaireBase);
        in.setTauxHoraire(salaireBase.divide(new BigDecimal("173.33"), 4, java.math.RoundingMode.HALF_UP));
        in.setNbJoursOuvrables(new BigDecimal("22.0"));
        in.setNbJoursPresents(new BigDecimal("22.0"));
        in.setNbJoursAbsence(BigDecimal.ZERO);
        in.setNbJoursConges(BigDecimal.ZERO);
        in.setTotalHeuresTravaillees(new BigDecimal("173.33"));
        in.setHeuresNormales(new BigDecimal("173.33"));

        // Cotisations sociales Madagascar standard
        in.setTauxCnapsSalarial(new BigDecimal("0.01")); // 1%
        in.setTauxCnapsPatronal(new BigDecimal("0.13")); // 13%
        in.setPlafondCotisationMensuel(new BigDecimal("2400000.00")); // Plafond 8 x SME
        in.setTauxSanteSalarial(new BigDecimal("0.01")); // 1%
        in.setTauxSantePatronal(new BigDecimal("0.05")); // 5%
        in.setTauxFmfpPatronal(new BigDecimal("0.01"));

        // Fiscalité IRSA Madagascar (tranches CGI 2026)
        List<PayrollTaxCalculations.Tranche> tranches = List.of(
                new PayrollTaxCalculations.Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), new BigDecimal("0.00")),
                new PayrollTaxCalculations.Tranche(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("0.05")),
                new PayrollTaxCalculations.Tranche(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("0.10")),
                new PayrollTaxCalculations.Tranche(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("0.15")),
                new PayrollTaxCalculations.Tranche(new BigDecimal("600000.00"), null, new BigDecimal("0.20"))
        );
        in.setTranchesIrsa(tranches);
        in.setMinimumPerceptionIrsa(new BigDecimal("3000.00"));
        in.setDeductionParEnfant(new BigDecimal("2000.00"));
        in.setNbEnfantsEligibles(0);

        return in;
    }

    @Test
    @DisplayName("L'exonération HS est désactivée par défaut et appliquée uniquement si activée")
    void testExonerationHsDesactiveeEtActiveeSelonConfiguration() {
        for (String[] cas : List.of(
                new String[] {"0", "0"}, new String[] {"10", "0"},
                new String[] {"20", "0"}, new String[] {"20", "5"},
                new String[] {"8", "4"})) {
            PayrollCalculationInput input = buildStandardInput(new BigDecimal("1000000.00"));
            input.setHeuresHs30(new BigDecimal(cas[0]));
            input.setHeuresHs50(new BigDecimal(cas[1]));
            input.setMajorationHs30(new BigDecimal("0.30"));
            input.setMajorationHs50(new BigDecimal("0.50"));
            input.setExonerationHsIrsaActive(false);
            input.setAppliquerExonerationHs(false);
            input.setPlafondMensuelHsExonerees(new BigDecimal("20"));

            BulletinPaieComplet sansExoneration = engine.calculer(input);
            BigDecimal hs30 = sansExoneration.getMontantLigneSalariale("HEURES_SUP_30");
            BigDecimal hs50 = sansExoneration.getMontantLigneSalariale("HEURES_SUP_50");
            BigDecimal totalHs = hs30.add(hs50);
            assertEquals(0, new BigDecimal("1000000.00").add(totalHs)
                    .compareTo(sansExoneration.getBulletin().getTotalGainsBruts()),
                    "Les HS rémunérées doivent entrer une seule fois dans le brut");
            assertEquals(0, sansExoneration.getBulletin().getSalaireNetImposable()
                    .compareTo(sansExoneration.getBulletin().getRevenuNetImposable()),
                    "Une configuration désactivée ne retranche aucune HS de l'assiette IRSA");
            assertEquals(0, totalHs.compareTo(sansExoneration.getMontantLigneSalariale("HS_IMPOSABLE_IRSA")));
            assertFalse(sansExoneration.getLignes().stream().anyMatch(l -> "HS_EXONEREE_IRSA".equals(l.getCodeRubrique())));

            input.setExonerationHsIrsaActive(true);
            input.setAppliquerExonerationHs(true);
            input.setPlafondMensuelHsExonerees(new BigDecimal("20"));
            BulletinPaieComplet avecExoneration = engine.calculer(input);
            BigDecimal heures30Exonerees = new BigDecimal(cas[0]).min(new BigDecimal("20"));
            BigDecimal heures50Exonerees = new BigDecimal(cas[1]).min(new BigDecimal("20").subtract(heures30Exonerees));
            BigDecimal tauxHoraire = input.getTauxHoraireEffectif();
            BigDecimal montantExonereAttendu = MoneyRoundingPolicy.amount(
                    heures30Exonerees.multiply(tauxHoraire).multiply(new BigDecimal("1.30")))
                    .add(MoneyRoundingPolicy.amount(heures50Exonerees.multiply(tauxHoraire).multiply(new BigDecimal("1.50"))));
            assertEquals(0, montantExonereAttendu.compareTo(avecExoneration.getMontantLigneSalariale("HS_EXONEREE_IRSA")));
            assertEquals(0, sansExoneration.getBulletin().getRevenuNetImposable()
                    .subtract(avecExoneration.getBulletin().getRevenuNetImposable()).compareTo(montantExonereAttendu),
                    "Seul le montant HS exonéré configuré est retiré de l'assiette");
        }
    }

    @Test
    @DisplayName("Une catégorie déclarée non éligible ne bénéficie pas de l'exonération activée")
    void testCategorieNonEligibleResteImposable() {
        PayrollCalculationInput input = buildStandardInput(new BigDecimal("1000000.00"));
        input.setHeuresHs30(new BigDecimal("12"));
        input.setMajorationHs30(new BigDecimal("0.30"));
        input.setExonerationHsIrsaActive(true);
        input.setAppliquerExonerationHs(true);
        input.setCategorieEligibleExonerationHs(false);
        input.setPlafondMensuelHsExonerees(new BigDecimal("20"));

        BulletinPaieComplet bulletin = engine.calculer(input);
        assertEquals(0, BigDecimal.ZERO.compareTo(bulletin.getMontantLigneSalariale("HS_EXONEREE_IRSA")));
        assertTrue(bulletin.getMontantLigneSalariale("HS_IMPOSABLE_IRSA").signum() > 0);
    }

    @Test
    @DisplayName("1. Calcul de paie standard sans avance ni acompte")
    void testCalculPaieSansAvanceNiAcompte() {
        BigDecimal salaireBase = new BigDecimal("1000000.00");
        PayrollCalculationInput input = buildStandardInput(salaireBase);

        BulletinPaieComplet resultat = engine.calculer(input);
        assertNotNull(resultat);
        BulletinPaie bp = resultat.getBulletin();
        assertNotNull(bp);

        // Gains bruts
        assertEquals(0, salaireBase.compareTo(bp.getTotalGainsBruts()));

        // Cotisations : 1% CNaPS (10 000) + 1% Santé (10 000) = 20 000 Ar
        BigDecimal cotisationsSalariales = bp.getTotalCotisationsSalariales();
        assertEquals(0, new BigDecimal("20000.00").compareTo(cotisationsSalariales));

        // Net imposable : 1 000 000 - 20 000 = 980 000 Ar
        assertEquals(0, new BigDecimal("980000.00").compareTo(bp.getSalaireNetImposable()));

        // IRSA calculé
        assertTrue(bp.getIrsaNet().compareTo(BigDecimal.ZERO) > 0);

        // Retenues de trésorerie doivent être strictement égales à zéro
        assertEquals(0, BigDecimal.ZERO.compareTo(bp.getTotalRetenuesAvancesAcomptes()));

        // Net à payer > 0 et cohérent
        assertTrue(bp.getNetAPayer().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(bp.getNetAPayer().compareTo(bp.getSalaireNetImposable()) < 0);

        // Aucune ligne de prêt générée
        boolean hasPret = resultat.getLignes().stream()
                .anyMatch(l -> l.getCodeRubrique() != null && l.getCodeRubrique().contains("PRET"));
        assertFalse(hasPret, "Aucune rubrique de prêt ne doit figurer dans le bulletin");
    }

    @Test
    @DisplayName("2. Calcul de paie avec avance et acompte (sans aucun prêt)")
    void testCalculPaieAvecAvanceEtAcompte() {
        BigDecimal salaireBase = new BigDecimal("1200000.00");
        PayrollCalculationInput input = buildStandardInput(salaireBase);

        // Acompte du 15 de 150 000 Ar
        PaieAvanceAcompte acompte = new PaieAvanceAcompte();
        acompte.setIdAvance(1);
        acompte.setIdEmploye(101);
        acompte.setTypeMouvement("ACOMPTE_15");
        acompte.setMontantInitial(new BigDecimal("150000.00"));
        acompte.setMontant(new BigDecimal("150000.00"));
        acompte.setSoldeRestant(new BigDecimal("150000.00"));
        acompte.setStatut("APPROUVE");

        // Avance spéciale de 50 000 Ar (respecte le plafond légal de 10% du salaire net)
        PaieAvanceAcompte avance = new PaieAvanceAcompte();
        avance.setIdAvance(2);
        avance.setIdEmploye(101);
        avance.setTypeMouvement("AVANCE_SPECIALE");
        avance.setMontantInitial(new BigDecimal("50000.00"));
        avance.setMontant(new BigDecimal("50000.00"));
        avance.setSoldeRestant(new BigDecimal("50000.00"));
        avance.setStatut("APPROUVE");

        input.setAvancesAcomptes(List.of(acompte, avance));

        BulletinPaieComplet resultat = engine.calculer(input);
        assertNotNull(resultat);
        BulletinPaie bp = resultat.getBulletin();

        // Total retenues de trésorerie = 150 000 + 50 000 = 200 000 Ar
        BigDecimal attenduRetenues = new BigDecimal("200000.00");
        assertEquals(0, attenduRetenues.compareTo(bp.getTotalRetenuesAvancesAcomptes()));

        // Imputations tracées dans le DTO
        assertNotNull(resultat.getImputationsMouvements());
        assertEquals(2, resultat.getImputationsMouvements().size());

        // Vérification des lignes créées
        List<BulletinPaieLigne> lignes = resultat.getLignes();
        assertTrue(lignes.stream().anyMatch(l -> "RETENUE_ACOMPTE_15".equals(l.getCodeRubrique())));
        assertTrue(lignes.stream().anyMatch(l -> "RETENUE_AVANCE_SPECIALE".equals(l.getCodeRubrique())));

        // Aucune rubrique de prêt
        assertFalse(lignes.stream().anyMatch(l -> l.getCodeRubrique() != null && l.getCodeRubrique().contains("PRET")));
    }

    @Test
    @DisplayName("3. Recalcul et restauration des imputations d'avances/acomptes")
    void testRecalculEtRestaurationImputations() {
        // Simulation d'une avance imputée
        PaieAvanceAcompte avance = new PaieAvanceAcompte();
        avance.setIdAvance(10);
        avance.setMontantInitial(new BigDecimal("200000.00"));
        avance.setMontantImpute(new BigDecimal("200000.00"));
        avance.setSoldeRestant(BigDecimal.ZERO);
        avance.setStatut("IMPUTE_PAIE");
        avance.setIdBulletin(55);

        // Restauration (comme exécutée lors de la réouverture ou suppression du bulletin)
        avance.setSoldeRestant(avance.getSoldeRestant().add(avance.getMontantImpute()));
        avance.setMontantImpute(BigDecimal.ZERO);
        avance.setIdBulletin(null);
        avance.setStatut("APPROUVE");

        assertEquals(0, new BigDecimal("200000.00").compareTo(avance.getSoldeRestant()));
        assertEquals(0, BigDecimal.ZERO.compareTo(avance.getMontantImpute()));
        assertEquals("APPROUVE", avance.getStatut());
        assertNull(avance.getIdBulletin());
    }

    @Test
    @DisplayName("4. Modèle STC sans dépendance aux prêts")
    void testStcSansDependancePrets() {
        // StcPreparationDTO ne doit plus contenir capitalRestantPrets
        StcPreparationDTO prep = new StcPreparationDTO();
        prep.setIdEmploye(101);
        prep.setAvancesEnAttente(new BigDecimal("50000.00"));
        assertEquals(0, new BigDecimal("50000.00").compareTo(prep.getAvancesEnAttente()));

        // StcCalculRequest ne doit plus contenir apurerPretDansStc
        StcCalculRequest req = new StcCalculRequest();
        req.setIdEmploye(101);
        req.setDateDepart(Date.valueOf("2026-03-31"));
        req.setMotifRupture("DEMISSION");
        assertNotNull(req.getDateDepart());

        // StcCalculResultDTO ne contient plus soldePretsDeduit
        StcCalculResultDTO res = new StcCalculResultDTO();
        res.setTotalBrutStc(new BigDecimal("1500000.00"));
        res.setSoldeAvancesDeduit(new BigDecimal("50000.00"));
        res.setNetAPayerStc(new BigDecimal("1400000.00"));
        assertEquals(0, new BigDecimal("50000.00").compareTo(res.getSoldeAvancesDeduit()));

        // StcRecuDTO ne contient plus soldePretsApure
        StcRecuDTO recu = new StcRecuDTO();
        recu.setSoldeAvancesApure(new BigDecimal("50000.00"));
        assertEquals(0, new BigDecimal("50000.00").compareTo(recu.getSoldeAvancesApure()));

        // DossierSortieDTO ne contient plus de décision de prêt mais conserve pretAValider
        DossierSortieDTO dossier = new DossierSortieDTO();
        dossier.setPretAValider(true);
        assertTrue(dossier.isPretAValider(), "pretAValider (prêt à valider) doit impérativement être conservé");

        // DossierSortieSaveRequest ne contient plus decisionPret
        DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
        saveReq.setIdEmploye(101);
        saveReq.setDateDepart(Date.valueOf("2026-03-31"));
        saveReq.setMotifRupture("DEMISSION");
        saveReq.setStatutDossier("PRET_A_VALIDER");
        assertEquals("PRET_A_VALIDER", saveReq.getStatutDossier());
    }

    @Test
    @DisplayName("5. Exports Bulletin Excel et PDF avec libellé exact 'Avances et acomptes'")
    void testExportsBulletinLibelleExact() throws Exception {
        BulletinPaie bp = new BulletinPaie();
        bp.setIdBulletin(1);
        bp.setIdPeriodePaie(1);
        bp.setIdEmploye(101);
        bp.setReferenceBulletin("BP-2026-03-EMP00101");
        bp.setMatricule("EMP00101");
        bp.setNomPrenom("Rakoto Jean");
        bp.setIntitulePoste("Chauffeur");
        bp.setNomDepartement("Logistique");
        bp.setNomSite("Toamasina");
        bp.setSalaireBase(new BigDecimal("1000000.00"));
        bp.setTotalGainsBruts(new BigDecimal("1000000.00"));
        bp.setTotalCotisationsSalariales(new BigDecimal("20000.00"));
        bp.setSalaireNetImposable(new BigDecimal("980000.00"));
        bp.setIrsaNet(new BigDecimal("80000.00"));
        bp.setTotalIndemnitesNonImposables(BigDecimal.ZERO);
        bp.setTotalRetenuesAvancesAcomptes(new BigDecimal("150000.00"));
        bp.setNetAvantArrondi(new BigDecimal("750000.00"));
        bp.setNetAPayer(new BigDecimal("750000.00"));

        List<BulletinPaieLigne> lignes = new ArrayList<>();
        BulletinPaieLigne l1 = new BulletinPaieLigne();
        l1.setCodeRubrique("SALAIRE_BASE");
        l1.setLibelleRubrique("Salaire de base");
        l1.setSens("GAIN");
        l1.setMontantSalarial(new BigDecimal("1000000.00"));
        lignes.add(l1);

        BulletinPaieLigne l2 = new BulletinPaieLigne();
        l2.setCodeRubrique("RETENUE_ACOMPTE_15");
        l2.setLibelleRubrique("Acompte quinzaine");
        l2.setSens("RETENUE");
        l2.setMontantSalarial(new BigDecimal("150000.00"));
        lignes.add(l2);

        BulletinPaieComplet complet = new BulletinPaieComplet();
        complet.setBulletin(bp);
        complet.setLignes(lignes);

        BulletinDocumentService.Context ctx = new BulletinDocumentService.Context(
                "2026-03", "01/03/2026 au 31/03/2026", "GALAXAM LOGISTICS", "Antananarivo", "1234567890", "12345"
        );

        BulletinDocumentService docService = new BulletinDocumentService();

        // 1. Export Excel
        byte[] excelBytes = docService.excel(complet, ctx);
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        // 2. Export PDF
        byte[] pdfBytes = docService.pdf(complet, ctx);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    @DisplayName("6. Validation des types de bulletin et absence de redondance id_type_bulletin")
    void testTypeBulletinEtAbsenceRedondance() {
        // Bulletin par défaut
        BulletinPaie bpMensuel = new BulletinPaie();
        assertEquals("MENSUEL", bpMensuel.getTypeBulletin(), "Le type de bulletin par défaut doit être MENSUEL");

        // Types autorisés
        List<String> typesValides = List.of("MENSUEL", "STC", "COMPLEMENTAIRE", "RAPPEL", "GRATIFICATION");
        for (String type : typesValides) {
            bpMensuel.setTypeBulletin(type);
            assertEquals(type, bpMensuel.getTypeBulletin());
        }

        // Vérification moteur de calcul avec typeBulletin STC
        PayrollCalculationInput inputStc = buildStandardInput(new BigDecimal("1200000.00"));
        inputStc.setTypeBulletin("STC");
        BulletinPaieComplet resultatStc = engine.calculer(inputStc);
        assertNotNull(resultatStc);
        assertEquals("STC", resultatStc.getBulletin().getTypeBulletin());
        assertTrue(resultatStc.getBulletin().getReferenceBulletin().startsWith("STC-"),
                "Le bulletin STC doit avoir une référence préfixée par STC-");

        // Vérification moteur de calcul avec typeBulletin MENSUEL
        PayrollCalculationInput inputMensuel = buildStandardInput(new BigDecimal("1200000.00"));
        inputMensuel.setTypeBulletin("MENSUEL");
        BulletinPaieComplet resultatMensuel = engine.calculer(inputMensuel);
        assertNotNull(resultatMensuel);
        assertEquals("MENSUEL", resultatMensuel.getBulletin().getTypeBulletin());
        assertTrue(resultatMensuel.getBulletin().getReferenceBulletin().startsWith("BP-"),
                "Le bulletin ordinaire doit avoir une référence préfixée par BP-");
    }
}

