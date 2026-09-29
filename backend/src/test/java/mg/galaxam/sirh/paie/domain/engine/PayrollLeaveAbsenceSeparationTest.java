package mg.galaxam.sirh.paie.domain.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.junit.jupiter.api.Test;

class PayrollLeaveAbsenceSeparationTest {
    @Test
    void indemniteDeTroisJoursUtiliseLaSommeHistoriqueDesLots() {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setSalaireBaseContractuel(new BigDecimal("300000"));
        input.setNbJoursConges(new BigDecimal("3"));
        input.setTauxJournalierConge(new BigDecimal("15000"));
        input.setIndemniteCongeLots(new BigDecimal("29000"));
        List<BulletinPaieLigne> lignes = new ArrayList<>();
        new PayrollExecutionEngine().calculerConges(input, new BulletinPaie(), lignes);
        var indemnite = lignes.stream().filter(l -> "INDEMNITE_CONGE_PAYE".equals(l.getCodeRubrique()))
                .findFirst().orElseThrow();
        assertEquals(0, indemnite.getMontantSalarial().compareTo(new BigDecimal("29000")));
    }

    @Test
    void congeHistoriqueEtAbsenceHoraireRestentSurTroisLignesDistinctes() {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setSalaireBaseContractuel(new BigDecimal("350000"));
        input.setDiviseurMensuelHeures(new BigDecimal("173.33"));
        input.setAttendanceInput(new PayrollAttendanceInput(
                1, 1, 1, 1, Instant.parse("2026-08-26T00:00:00Z"),
                0, 0, 0, 0, 0, 0, 0, 20, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of()));
        input.setNbJoursConges(new BigDecimal("3"));
        input.setTauxJournalierConge(new BigDecimal("8223.50"));

        PayrollExecutionEngine engine = new PayrollExecutionEngine();
        BulletinPaie bulletin = new BulletinPaie();
        List<BulletinPaieLigne> lignes = new ArrayList<>();
        BigDecimal absence = engine.calculerRetenueAbsence(input, new BigDecimal("350000"),
                input.getTauxHoraireEffectif(), bulletin, lignes);
        BigDecimal netConges = engine.calculerConges(input, bulletin, lignes);

        BigDecimal expectedAbsence = new BigDecimal("350000").multiply(new BigDecimal("20"))
                .divide(new BigDecimal("173.33").multiply(new BigDecimal("60")), 8, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, expectedAbsence.compareTo(absence));
        assertEquals(0, new BigDecimal("-10329.50").compareTo(netConges));
        assertNotNull(lignes.stream().filter(l -> "RETENUE_ABSENCE".equals(l.getCodeRubrique())).findFirst().orElse(null));
        assertNotNull(lignes.stream().filter(l -> "DEDUCTION_CONGE_BASE".equals(l.getCodeRubrique())).findFirst().orElse(null));
        assertNotNull(lignes.stream().filter(l -> "INDEMNITE_CONGE_PAYE".equals(l.getCodeRubrique())).findFirst().orElse(null));
    }
}
