package mg.galaxam.sirh.paie.domain.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.junit.jupiter.api.Test;

/** Valeurs de contrôle des deux lignes de « recap salaire » du classeur 26/07–25/08/2026. */
class PayrollExcelReferenceReconciliationTest {
    private static final PayrollExecutionEngine ENGINE = new PayrollExecutionEngine();

    @Test
    void tmmReconstitueLeBrutExcelAvecLeSoldeHsEtLaVariableAutimp() {
        PayrollCalculationInput input = base("370000", "9", "9663.09");
        input.setHeuresHs30(new BigDecimal("6.45"));
        input.setMajorationHs30(new BigDecimal("0.30"));
        // Le classeur ne sépare pas déplacement et prime exceptionnelle dans AUTIMP.
        input.ajouterGainVariable(new PayrollCalculationInput.GainVariable(null, "AUTIMP_RECONCILIATION",
                "AUTIMP Excel à ventiler", new BigDecimal("35272.73"), "PRIME", true, true));

        List<BulletinPaieLigne> lignes = new ArrayList<>();
        BigDecimal brut = ENGINE.calculerSalaireBrut(input, new BulletinPaie(), lignes);
        assertEquals(0, new BigDecimal("399139.63").compareTo(brut));
        assertEquals(0, new BigDecimal("17899.09").compareTo(montant(lignes, "HEURES_SUP_30")));
        assertEquals(0, new BigDecimal("111000.00").compareTo(montant(lignes, "DEDUCTION_CONGE_BASE")));
        assertEquals(0, new BigDecimal("86967.81").compareTo(montant(lignes, "INDEMNITE_CONGE_PAYE")));
    }

    @Test
    void tnrExposeLEcartDAbsenceCreeParLArrondiExcelDe033Heure() {
        PayrollCalculationInput input = base("350000", "3", "8223.50");
        input.setAttendanceInput(new PayrollAttendanceInput(
                1, 1, 199, 1, Instant.parse("2026-08-26T00:00:00Z"),
                0, 0, 0, 0, 0, 0, 0, 20, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
        input.ajouterGainVariable(new PayrollCalculationInput.GainVariable(null, "AUTIMP_RECONCILIATION",
                "AUTIMP Excel à ventiler", new BigDecimal("28363.64"), "PRIME", true, true));
        input.ajouterGainVariable(new PayrollCalculationInput.GainVariable(null, "REPAS_RECONCILIATION",
                "Repas Excel", new BigDecimal("44000"), "INDEMNITE", true, true));

        List<BulletinPaieLigne> lignes = new ArrayList<>();
        BigDecimal brut = ENGINE.calculerSalaireBrut(input, new BulletinPaie(), lignes);
        assertEquals(0, new BigDecimal("673.09").compareTo(montant(lignes, "RETENUE_ABSENCE")));
        assertEquals(0, new BigDecimal("411361.05").compareTo(brut));
        // Le classeur valorise 0,33 h, et non 20 minutes exactes : 666,36 Ar.
        assertEquals(0, new BigDecimal("6.73").compareTo(
                montant(lignes, "RETENUE_ABSENCE").subtract(new BigDecimal("666.36"))));
    }

    private static PayrollCalculationInput base(String salaire, String joursConges, String tauxConge) {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setSalaireBaseContractuel(new BigDecimal(salaire));
        input.setDiviseurMensuelHeures(new BigDecimal("173.33"));
        input.setNbJoursConges(new BigDecimal(joursConges));
        input.setTauxJournalierConge(new BigDecimal(tauxConge));
        return input;
    }

    private static BigDecimal montant(List<BulletinPaieLigne> lignes, String code) {
        return lignes.stream().filter(ligne -> code.equals(ligne.getCodeRubrique()))
                .findFirst().orElseThrow().getMontantSalarial();
    }
}
