package mg.galaxam.sirh.paie.domain.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.junit.jupiter.api.Test;

class PayrollMealTravelGrossTest {
    @Test
    void repasEtDeplacementEntrentDansLeBrutUneSeuleFois() {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setIndemniteRepas(new BigDecimal("44000"));
        input.setIndemniteTransport(new BigDecimal("28000"));
        var lignes = new ArrayList<BulletinPaieLigne>();
        var engine = new PayrollExecutionEngine();

        BigDecimal brut = engine.calculerPrimes(input, BigDecimal.ZERO, new BulletinPaie(), lignes);
        BigDecimal netHorsBrut = engine.calculerIndemnitesNonImposables(input, new BulletinPaie(), lignes);

        assertEquals(0, brut.compareTo(new BigDecimal("72000")));
        assertEquals(0, netHorsBrut.compareTo(BigDecimal.ZERO));
        assertEquals(1, lignes.stream().filter(l -> "INDEMNITE_REPAS".equals(l.getCodeRubrique())).count());
        assertEquals(1, lignes.stream().filter(l -> "INDEMNITE_TRANSPORT".equals(l.getCodeRubrique())).count());
        assertFalse(lignes.stream().filter(l -> "INDEMNITE_REPAS".equals(l.getCodeRubrique())
                || "INDEMNITE_TRANSPORT".equals(l.getCodeRubrique()))
                .anyMatch(l -> "INDEMNITE_NON_IMPOSABLE".equals(l.getCategorie())));
        assertTrue(lignes.stream().filter(l -> "INDEMNITE_REPAS".equals(l.getCodeRubrique())
                || "INDEMNITE_TRANSPORT".equals(l.getCodeRubrique()))
                .allMatch(l -> l.isImposable() && l.isSoumisCotisation()));
    }
}
