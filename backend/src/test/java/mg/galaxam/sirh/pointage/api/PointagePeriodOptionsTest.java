package mg.galaxam.sirh.pointage.api;

import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodWorkflowService;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Date;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointagePeriodOptionsTest {
    @Test void creationNePeutPasCourtCircuiterOuvertureHistorisee() {
        var controller = new PointagePeriodeController();
        var error = assertThrows(SirhBusinessException.class, () -> controller.createPeriode(Map.of(
                "code", "2026-10", "dateDebut", "2026-09-26", "dateFin", "2026-10-25",
                "statutCode", "EN_COURS")));
        assertEquals(SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED, error.getErrorCode());
    }

    @Test void currentDateRefusesTwoMatchingPeriods() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, true);
        when(rows.getInt("id_periode_paie")).thenReturn(7);
        when(rows.getString("statut_code")).thenReturn("EN_COURS");
        when(rows.getDate("date_debut")).thenReturn(Date.valueOf("2026-08-26"));
        when(rows.getDate("date_fin")).thenReturn(Date.valueOf("2026-09-25"));
        var controller = new PointagePeriodeController();
        ReflectionTestUtils.setField(controller, "dataSource", source);
        ReflectionTestUtils.setField(controller, "payrollPeriodWorkflowService", mock(PayrollPeriodWorkflowService.class));
        ReflectionTestUtils.setField(controller, "calendrierEngine", mock(CalendrierOuvrableEngine.class));
        var error = assertThrows(SirhBusinessException.class,
                () -> controller.getCurrentPeriode("2026-09-01"));
        assertEquals(SirhErrorCode.POINTAGE_PERIOD_CONTEXT_AMBIGUOUS, error.getErrorCode());
    }

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
        var result = controller.getAllPeriodes(null, true, false).getBody();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("2026-08-26", result.get(0).getDateDebut());
        assertTrue(result.get(0).getAvailableActions().isEmpty());
        verifyNoInteractions(workflow, calendar);
        verify(connection, times(1)).prepareStatement(anyString());
    }

    @Test void payrollSelectorReceivesDerivedStatusWithoutLoadingFullContext() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getInt("id_periode_paie")).thenReturn(7);
        when(rows.getString("statut_code")).thenReturn("VALIDEE");
        when(rows.getDate("date_debut")).thenReturn(Date.valueOf("2026-07-26"));
        when(rows.getDate("date_fin")).thenReturn(Date.valueOf("2026-08-25"));
        var workflow = mock(PayrollPeriodWorkflowService.class);
        var statuses = mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowStatusResolver.resolve(
                "VALIDEE", 0, 0, 0, false, false);
        when(workflow.describeWorkflowStatuses(7, "VALIDEE", connection)).thenReturn(statuses);
        var controller = new PointagePeriodeController();
        ReflectionTestUtils.setField(controller, "dataSource", source);
        ReflectionTestUtils.setField(controller, "payrollPeriodWorkflowService", workflow);
        ReflectionTestUtils.setField(controller, "calendrierEngine", mock(CalendrierOuvrableEngine.class));

        var result = controller.getAllPeriodes(null, true, true).getBody();

        assertNotNull(result);
        assertEquals("A_PREPARER", result.get(0).getWorkflowStatuses().paie().code());
        assertTrue(result.get(0).getAvailableActions().isEmpty());
        verify(workflow).describeWorkflowStatuses(7, "VALIDEE", connection);
        verifyNoMoreInteractions(workflow);
    }
}
