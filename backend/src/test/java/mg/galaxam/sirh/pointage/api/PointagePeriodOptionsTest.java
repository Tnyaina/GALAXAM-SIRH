package mg.galaxam.sirh.pointage.api;

import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodWorkflowService;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointagePeriodOptionsTest {
    @Test void periodSelectorDoesNotRunPayrollOrAttendanceChecks() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getInt("id_periode_paie")).thenReturn(7);
        when(rows.getString("statut_code")).thenReturn("EN_COURS");
        when(rows.getDate("date_debut")).thenReturn(Date.valueOf("2026-08-26"));
        when(rows.getDate("date_fin")).thenReturn(Date.valueOf("2026-09-25"));
        var workflow = mock(PayrollPeriodWorkflowService.class);
        var calendar = mock(CalendrierOuvrableEngine.class);
        var controller = new PointagePeriodeController();
        ReflectionTestUtils.setField(controller, "dataSource", source);
        ReflectionTestUtils.setField(controller, "payrollPeriodWorkflowService", workflow);
        ReflectionTestUtils.setField(controller, "calendrierEngine", calendar);
        var result = controller.getAllPeriodes(null, true).getBody();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("2026-08-26", result.get(0).getDateDebut());
        assertTrue(result.get(0).getAvailableActions().isEmpty());
        verifyNoInteractions(workflow, calendar);
        verify(connection, times(1)).prepareStatement(anyString());
    }
}
