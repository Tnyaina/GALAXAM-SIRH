package mg.galaxam.sirh.paie.domain.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import org.junit.jupiter.api.Test;

class PayrollBatchResultTest {

    @Test
    void simulationSummaryKeepsBackendTotalsAndEmployeeOutcomes() {
        var batch = new PayrollBatchResult(7, "2026-08");
        var bulletin = new BulletinPaie();
        bulletin.setIdEmploye(199);
        bulletin.setNomPrenom("Agent Test");
        bulletin.setMatricule("199");
        bulletin.setTotalGainsBruts(new BigDecimal("411361.01"));
        bulletin.setTotalCotisationsSalariales(new BigDecimal("8227.22"));
        bulletin.setIrsaNet(new BigDecimal("3000"));
        bulletin.setNetAPayer(new BigDecimal("399120"));
        var complete = new BulletinPaieComplet();
        complete.setBulletin(bulletin);

        batch.ajouterBulletin(complete);
        batch.ajouterBloque(1, "Employé historique", "1", "LEAVE_RATE_MISSING", "Taux TCONGE absent");

        assertEquals(new BigDecimal("8227.22"), batch.getTotalCotisationsSalariales());
        assertEquals(new BigDecimal("3000"), batch.getTotalIrsa());
        assertEquals(1, batch.getNbBulletinsCalcules());
        assertEquals("Agent Test", batch.getEmployeeOutcomes().get(0).employeeName());
        assertEquals("LEAVE_RATE_MISSING", batch.getEmployeeOutcomes().get(1).reasonCode());
        assertEquals("1", batch.getEmployeeOutcomes().get(1).matricule());
    }
}
