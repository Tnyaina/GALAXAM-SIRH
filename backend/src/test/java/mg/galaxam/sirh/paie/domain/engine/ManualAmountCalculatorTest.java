package mg.galaxam.sirh.paie.domain.engine;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ManualAmountCalculatorTest {
    private BigDecimal n(String v) {return new BigDecimal(v);}
    @Test void produitExactAvantArrondi() {
        var r=ManualPayrollRules.calculateAmount("QUANTITE_TAUX","CENTIME",null,n("1.5"),n("2500.125"),null);
        assertEquals(n("3750.1875"),r.avantArrondi());assertEquals(n("3750.19"),r.montant());
        assertTrue(r.explication().contains("1.5 × 2500.125"));
    }
    @Test void pourcentageSalaireEtArrondis() {
        var r=ManualPayrollRules.calculateAmount("POURCENTAGE_SALAIRE_BASE","CENTIME",null,null,n("10.125"),n("350000"));
        assertEquals(n("35437.50"),r.montant());
        assertEquals(n("1011.00"),ManualPayrollRules.calculateAmount("MONTANT","ARIARY",n("1010.5"),null,null,null).montant());
        assertEquals(n("1010.00"),ManualPayrollRules.calculateAmount("MONTANT","DIZAINE",n("1013.7"),null,null,null).montant());
    }
    @Test void refuseDonneesIncompletesEtMethodesInconnues() {
        assertThrows(IllegalArgumentException.class,()->ManualPayrollRules.calculateAmount("QUANTITE_TAUX","CENTIME",null,null,n("5"),null));
        assertThrows(IllegalArgumentException.class,()->ManualPayrollRules.calculateAmount("POURCENTAGE_SALAIRE_BASE","CENTIME",null,null,n("101"),n("350000")));
        assertThrows(IllegalArgumentException.class,()->ManualPayrollRules.calculateAmount("MONTANT","CENTIME",n("0"),null,null,null));
        assertThrows(IllegalArgumentException.class,()->ManualPayrollRules.calculateAmount("QUANTITE_TAUX","CENTIME",null,n("1.1234567"),n("2"),null));
        assertThrows(IllegalArgumentException.class,()->ManualPayrollRules.calculateAmount("EXPRESSION_LIBRE","CENTIME",n("1"),null,null,null));
    }
}
