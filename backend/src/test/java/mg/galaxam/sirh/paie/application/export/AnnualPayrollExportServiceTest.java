package mg.galaxam.sirh.paie.application.export;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AnnualPayrollExportServiceTest {
    @Test
    void exportsPartialYearWithTwoValidatedMonths() throws Exception {
        BigDecimal[] values = new BigDecimal[18];
        java.util.Arrays.fill(values, BigDecimal.ZERO);
        String[] identity = {"REC-1", "Nom", "Prénom", "Site", "Fonction", "CDI"};
        var rows = new ArrayList<AnnualPayrollExportService.PayRow>();
        rows.add(new AnnualPayrollExportService.PayRow(1, "2026-08", identity, values));
        rows.add(new AnnualPayrollExportService.PayRow(1, "2026-09", identity, values));
        var result = new AnnualPayrollExportService().render(2026, List.of("2026-08", "2026-09"), rows);
        assertEquals(2, result.coveredMonths());
        assertEquals(1, result.employees());
        assertTrue(result.bytes().length > 1000);
    }

    @Test
    void exportsSyntheticCampaignFromDisposableDatabase() throws Exception {
        String url = System.getenv("GALAXAM_FIX_TEST_DB_URL");
        assumeTrue(url != null && url.contains("127.0.0.1:15441/galaxam_recette"));
        try (var connection = DriverManager.getConnection(url,
                System.getenv("GALAXAM_FIX_TEST_DB_USER"), System.getenv("GALAXAM_FIX_TEST_DB_PASSWORD"))) {
            var result = new AnnualPayrollExportService().export(2026, connection);
            assertEquals(2, result.coveredMonths());
            assertTrue(result.bytes().length > 1000);
        }
    }
}
