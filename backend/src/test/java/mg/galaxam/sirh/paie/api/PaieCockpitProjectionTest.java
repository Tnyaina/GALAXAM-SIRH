package mg.galaxam.sirh.paie.api;

import mg.galaxam.sirh.paie.application.PayrollService;
import mg.galaxam.sirh.paie.application.export.*;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodWorkflowService;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PaieCockpitProjectionTest {
    @Test
    void populationProjectionSkipsFullCockpitAndWorkflowContext() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        PayrollService payroll = mock(PayrollService.class);
        when(payroll.obtenirPopulationCockpit(41, connection))
                .thenReturn(Map.of("population", java.util.List.of("salarié")));
        PayrollPeriodWorkflowService workflow = mock(PayrollPeriodWorkflowService.class);
        PaieController controller = new PaieController(
                source, payroll, mock(PaieExportService.class), mock(BulletinDocumentService.class),
                mock(GrandLivrePaieExportService.class), mock(IrsaExcelExportService.class),
                mock(EtatSocialMensuelExportService.class), mock(QuarterlySocialDataService.class),
                mock(QuarterlySocialWorkbookService.class), mock(AnnualPayrollExportService.class),
                workflow);

        Map<?, ?> body = (Map<?, ?>) controller.obtenirCockpitPeriode(
                41, 1, 25, null, null, null, "population", false, null).getBody();

        assertEquals(java.util.List.of("salarié"), body.get("population"));
        verify(payroll).obtenirPopulationCockpit(41, connection);
        verify(payroll, never()).obtenirCockpitPeriode(anyInt(), anyInt(), anyInt(), any(), any(), any(), any());
        verifyNoInteractions(workflow);
    }
}
