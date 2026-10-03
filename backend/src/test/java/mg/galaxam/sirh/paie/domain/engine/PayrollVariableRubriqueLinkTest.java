package mg.galaxam.sirh.paie.domain.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import org.junit.jupiter.api.Test;

class PayrollVariableRubriqueLinkTest {
    @Test
    void cotisationUtiliseLePlafondLegalFourniEtNonHuitFoisLeSme() {
        var rules = new SocialContributionCalculator.Rules(
                new BigDecimal("0.01"), new BigDecimal("0.13"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000"), true);
        var result = new SocialContributionCalculator().calculate(new BigDecimal("900000"), rules);
        assertEquals(0, new BigDecimal("500000").compareTo(result.cnaps().assiette()));
        assertEquals(0, new BigDecimal("5000").compareTo(result.cnaps().partSalariale()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.fmfp()));
    }

    @Test
    void fmfpUtiliseLaMemeLimiteMensuelleQueLaCotisationSociale() {
        var rules = new SocialContributionCalculator.Rules(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("0.01"), new BigDecimal("500000"), true);
        var result = new SocialContributionCalculator().calculate(
                new BigDecimal("900000"), BigDecimal.ZERO, new BigDecimal("900000"), rules);
        assertEquals(0, new BigDecimal("500000").compareTo(result.cnaps().assiette()));
        assertEquals(0, new BigDecimal("5000").compareTo(result.fmfp()));
    }

    @Test
    void remboursementDeFraisResteUnCircuitDedie() {
        var frais = PayrollCalculatorRegistry.describe("REMBOURSEMENT_NOTE_FRAIS", "INDEMNITE_NON_IMPOSABLE", false, "GAIN_NET");
        var manuel = PayrollCalculatorRegistry.describe("INDEMNITE_MANUELLE", "INDEMNITE_NON_IMPOSABLE", false, "GAIN_NET");
        assertEquals("SEMI_CONFIGURABLE", frais.niveau());
        assertEquals("NOTES_DE_FRAIS", frais.calculateur());
        assertEquals("CONFIGURABLE", manuel.niveau());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "true,true,110000,110000,3300",
        "true,false,110000,100000,3100",
        "false,true,100000,110000,3200",
        "false,false,100000,100000,3000"
    })
    void cnapsEtSanteUtilisentLeursPropresAssiettes(boolean cnaps, boolean health,
            String cnapsBase, String healthBase, String deductions) {
        var input = new PayrollCalculationInput();
        input.setSmeNational(new BigDecimal("100000"));
        input.setPlafondCotisationMensuel(new BigDecimal("800000"));
        input.setSoumisCnaps(true); input.setSoumisSante(true);
        input.setTauxCnapsSalarial(new BigDecimal("0.01"));
        input.setTauxCnapsPatronal(new BigDecimal("0.13"));
        input.setTauxSanteSalarial(new BigDecimal("0.02"));
        input.setTauxSantePatronal(new BigDecimal("0.05"));
        input.setTauxFmfpPatronal(new BigDecimal("0.01"));
        var gain = new PayrollCalculationInput.GainVariable(1, "TYPE", "Prime",
                new BigDecimal("10000"), "PRIME", true, cnaps);
        gain.setEtapeCalcul("BRUT"); gain.setSoumisSante(health); gain.setSoumisFmfp(!cnaps);
        input.ajouterGainVariable(gain);
        var bulletin = new BulletinPaie();
        var lines = new ArrayList<BulletinPaieLigne>();
        new PayrollExecutionEngine().calculerCotisationsSociales(new BigDecimal("110000"), input, bulletin, lines);
        assertEquals(0, new BigDecimal(deductions).compareTo(bulletin.getTotalCotisationsSalariales()));
        for (var line : lines) {
            if ("COTISATION_FMFP_PAT".equals(line.getCodeRubrique()))
                assertEquals(0, new BigDecimal(cnaps ? "100000" : "110000").compareTo(line.getBaseCalcul()));
            if ("COTISATION_CNAPS_SAL".equals(line.getCodeRubrique()))
                assertEquals(0, new BigDecimal(cnapsBase).compareTo(line.getBaseCalcul()));
            if ("COTISATION_OSTIE_SAL".equals(line.getCodeRubrique()))
                assertEquals(0, new BigDecimal(healthBase).compareTo(line.getBaseCalcul()));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "true,true,110000,1100,108900,10890,98010",
        "true,false,110000,1000,109000,10900,98100",
        "false,true,110000,1100,98900,9890,99010",
        "false,false,110000,1000,99000,9900,99100"
    })
    void gainDansBrutRespecteLesAssiettesIndependantes(boolean taxable, boolean social,
            String gross, String contributions, String taxableIncome, String tax, String net) {
        var input = new PayrollCalculationInput();
        input.setSalaireBaseContractuel(new BigDecimal("100000"));
        input.setSmeNational(new BigDecimal("100000"));
        input.setPlafondCotisationMensuel(new BigDecimal("800000"));
        input.setTauxCnapsSalarial(new BigDecimal("0.01"));
        input.setTauxCnapsPatronal(BigDecimal.ZERO);
        input.setTauxFmfpPatronal(BigDecimal.ZERO);
        input.setTauxSanteSalarial(BigDecimal.ZERO);
        input.setTauxSantePatronal(BigDecimal.ZERO);
        input.setSoumisSante(false);
        input.setMinimumPerceptionIrsa(BigDecimal.ZERO);
        input.setDeductionParEnfant(BigDecimal.ZERO);
        input.setTranchesIrsa(List.of(new IrsaCalculator.Tranche(BigDecimal.ZERO, null, new BigDecimal("0.10"))));
        var gain = new PayrollCalculationInput.GainVariable(1, "TYPE_PRIME", "Prime",
                new BigDecimal("10000"), "PRIME", taxable, social);
        gain.setCodeRubrique("PRIME_CONFIGUREE"); gain.setEtapeCalcul("BRUT");
        input.ajouterGainVariable(gain);
        var result = new PayrollExecutionEngine().calculer(input);
        var b = result.getBulletin();
        assertEquals(0, new BigDecimal(gross).compareTo(b.getTotalGainsBruts()));
        assertEquals(0, new BigDecimal(contributions).compareTo(b.getTotalCotisationsSalariales()));
        assertEquals(0, new BigDecimal(taxableIncome).compareTo(b.getRevenuNetImposable()));
        assertEquals(0, new BigDecimal(tax).compareTo(b.getIrsaNet()));
        assertEquals(0, new BigDecimal(net).compareTo(b.getNetAvantArrondi()));
        assertEquals(0, b.getTotalIndemnitesNonImposables().signum(), "Un gain du brut n'est pas ajouté une seconde fois au net");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void gainUtiliseLaRubriqueLieeEtPasLeCodeDuType(boolean taxable) {
        var input = new PayrollCalculationInput();
        input.setSalaireBaseContractuel(new BigDecimal("350000"));
        var gain = new PayrollCalculationInput.GainVariable(1, "TYPE_MISSION", "Mission",
                new BigDecimal("10000"), taxable ? "PRIME" : "INDEMNITE_NON_IMPOSABLE", taxable, taxable);
        gain.setCodeRubrique("RUBRIQUE_MISSION");
        input.ajouterGainVariable(gain);
        var lignes = new ArrayList<BulletinPaieLigne>();
        var engine = new PayrollExecutionEngine();
        if (taxable) engine.calculerSalaireBrut(input, new BulletinPaie(), lignes);
        else engine.calculerIndemnitesNonImposables(input, new BulletinPaie(), lignes);
        var ligne = lignes.stream().filter(l -> "RUBRIQUE_MISSION".equals(l.getCodeRubrique())).findFirst().orElseThrow();
        assertEquals(taxable, ligne.isImposable());
        assertEquals(taxable, ligne.isSoumisCotisation());
        assertFalse(lignes.stream().anyMatch(l -> "TYPE_MISSION".equals(l.getCodeRubrique())));
    }

    @Test
    void gainHorsBrutConfigureAjouteAuNetSansModifierBrutNiAssiettes() {
        var engine = new PayrollExecutionEngine();
        var base = new PayrollCalculationInput();
        base.setSalaireBaseContractuel(new BigDecimal("350000"));
        configurerTauxNeutres(base);
        var bulletinSansGain = engine.calculer(base).getBulletin();

        var avecGain = new PayrollCalculationInput();
        avecGain.setSalaireBaseContractuel(new BigDecimal("350000"));
        configurerTauxNeutres(avecGain);
        var gain = new PayrollCalculationInput.GainVariable(1, "TYPE_MISSION", "Mission ponctuelle",
                new BigDecimal("10000"), "INDEMNITE_NON_IMPOSABLE", false, false);
        gain.setCodeRubrique("EXTRA_DEPLACEMENT");
        gain.setEtapeCalcul("GAIN_NET");
        avecGain.ajouterGainVariable(gain);
        var resultat = engine.calculer(avecGain);
        var bulletin = resultat.getBulletin();

        assertEquals(0, bulletinSansGain.getTotalGainsBruts().compareTo(bulletin.getTotalGainsBruts()));
        assertEquals(0, bulletinSansGain.getTotalCotisationsSalariales().compareTo(bulletin.getTotalCotisationsSalariales()));
        assertEquals(0, bulletinSansGain.getIrsaNet().compareTo(bulletin.getIrsaNet()));
        assertEquals(0, new BigDecimal("10000").compareTo(bulletin.getNetAvantArrondi().subtract(bulletinSansGain.getNetAvantArrondi())));
        var ligne = resultat.getLignes().stream().filter(l -> "EXTRA_DEPLACEMENT".equals(l.getCodeRubrique())).findFirst().orElseThrow();
        assertEquals("GAIN_NET", ligne.getEtapeCalcul());
        assertFalse(ligne.isImposable());
        assertFalse(ligne.isSoumisCotisation());
    }

    private void configurerTauxNeutres(PayrollCalculationInput input) {
        input.setSmeNational(new BigDecimal("100000"));
        input.setPlafondCotisationMensuel(new BigDecimal("800000"));
        input.setTauxCnapsSalarial(BigDecimal.ZERO);
        input.setTauxCnapsPatronal(BigDecimal.ZERO);
        input.setTauxSanteSalarial(BigDecimal.ZERO);
        input.setTauxSantePatronal(BigDecimal.ZERO);
        input.setTauxFmfpPatronal(BigDecimal.ZERO);
        input.setMinimumPerceptionIrsa(BigDecimal.ZERO);
        input.setDeductionParEnfant(BigDecimal.ZERO);
        input.setTranchesIrsa(List.of(new IrsaCalculator.Tranche(BigDecimal.ZERO, null, BigDecimal.ZERO)));
    }

    @Test
    void retenuePersonnaliseeNeDevientPasUnAcompte() {
        var input = new PayrollCalculationInput();
        var movement = new PaieAvanceAcompte();
        movement.setTypeMouvement("TYPE_RETENUE_MISSION");
        movement.setCodeRubriqueCalcul("RETENUE_MISSION");
        movement.setMontant(new BigDecimal("10000"));
        input.setAvancesAcomptes(List.of(movement));
        var lignes = new ArrayList<BulletinPaieLigne>();
        new PayrollExecutionEngine().calculerRetenuesEtAvances(new BigDecimal("400000"), input, new BulletinPaie(), lignes);
        assertEquals(1, lignes.size());
        assertEquals("RETENUE_MISSION", lignes.get(0).getCodeRubrique());
        assertFalse(lignes.get(0).isImposable());
        assertFalse(lignes.get(0).isSoumisCotisation());
    }
}
