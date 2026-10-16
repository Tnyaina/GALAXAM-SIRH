package mg.galaxam.sirh.paie.application.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.dto.ActionCapability;
import mg.galaxam.sirh.paie.api.dto.PayrollPeriodContextView;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodUserContext;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodWorkflowService;
import org.junit.jupiter.api.Test;

class PayrollDocumentCatalogServiceTest {
    @Test
    void leMensuelReutiliseLaDecisionDeSortieDuWorkflow() throws Exception {
        var workflow = mock(PayrollPeriodWorkflowService.class);
        var user = mock(PayrollPeriodUserContext.class);
        var view = mock(PayrollPeriodContextView.class);
        when(view.availableActions()).thenReturn(Map.of("GENERATE_OUTPUT",
                ActionCapability.denied("PAYROLL_CALCULATION_STALE")));
        when(workflow.describeContext(17, null, user)).thenReturn(view);
        var catalog = new PayrollDocumentCatalogService(mock(DataSource.class), workflow,
                mock(QuarterlySocialDataService.class), mock(AnnualPayrollExportService.class));
        var result = catalog.monthly(17, user);
        assertFalse(result.documents().get(0).available());
        assertEquals("Recalcul requis", result.documents().get(0).label());
        verify(workflow).describeContext(17, null, user);
    }

    @Test
    void leMensuelSansPeriodeNAnnonceAucunExport() throws Exception {
        var catalog = new PayrollDocumentCatalogService(mock(DataSource.class),
                mock(PayrollPeriodWorkflowService.class), mock(QuarterlySocialDataService.class),
                mock(AnnualPayrollExportService.class));
        var result = catalog.monthly(null, mock(PayrollPeriodUserContext.class));
        assertEquals(8, result.documents().size());
        assertTrue(result.documents().stream().anyMatch(document ->
                "safi-xls".equals(document.id()) && "Excel (.xls)".equals(document.format())));
        assertTrue(result.documents().stream().noneMatch(PayrollDocumentCatalogService.Document::available));
        assertEquals("Choisir une période", result.documents().get(0).label());
    }

    @Test
    void disponibiliteAnnuelleSuitLaCouvertureDuGenerateur() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        AnnualPayrollExportService annual = mock(AnnualPayrollExportService.class);
        when(annual.coverage(2026, connection)).thenReturn(new AnnualPayrollExportService.Coverage(7, 83));
        var catalog = new PayrollDocumentCatalogService(source, mock(PayrollPeriodWorkflowService.class),
                mock(QuarterlySocialDataService.class), annual);
        var result = catalog.annual(2026);
        assertEquals(7, result.coveredMonths());
        assertTrue(result.documents().get(0).available());
        assertEquals("Partiel · 7/12 mois", result.documents().get(0).label());
    }

    @Test
    void unTrimestreIncompletExposeLeMotifDuGenerateur() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        QuarterlySocialDataService quarterly = mock(QuarterlySocialDataService.class);
        when(quarterly.load(2026, 3, connection)).thenThrow(new IllegalStateException("La période 2026-09 est absente."));
        var catalog = new PayrollDocumentCatalogService(source, mock(PayrollPeriodWorkflowService.class),
                quarterly, mock(AnnualPayrollExportService.class));
        var result = catalog.quarterly(2026, 3);
        assertFalse(result.documents().get(0).available());
        assertEquals("La période 2026-09 est absente.", result.documents().get(0).label());
    }
}
