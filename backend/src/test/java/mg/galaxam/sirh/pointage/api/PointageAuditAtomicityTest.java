package mg.galaxam.sirh.pointage.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import mg.galaxam.sirh.securite.AuditLog;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

class PointageAuditAtomicityTest {
    @Test void auditFailureRollsBackToleranceWithoutCommit() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection c = mock(Connection.class);
        PreparedStatement update = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.prepareStatement(anyString())).thenReturn(update);
        when(update.executeUpdate()).thenReturn(1);
        PointageController controller = new PointageController();
        ReflectionTestUtils.setField(controller, "dataSource", ds);
        PointageController.ToleranceRequest request = new PointageController.ToleranceRequest();
        request.setIdSite(3);
        request.setEstActif(false);
        try (MockedStatic<AuditLog> audit = mockStatic(AuditLog.class)) {
            audit.when(() -> AuditLog.audit(eq(c), anyString(), anyString(), anyString(), isNull(), any()))
                    .thenAnswer(invocation -> { verify(c, never()).commit(); throw new IllegalStateException("audit failed"); });
            assertThrows(IllegalStateException.class, () -> controller.saveTolerance(request));
        }
        verify(c).setAutoCommit(false);
        verify(c).rollback();
        verify(c, never()).commit();
    }

    @Test void successCommitsOnlyAfterAudit() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection c = mock(Connection.class);
        PreparedStatement update = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.prepareStatement(anyString())).thenReturn(update);
        when(update.executeUpdate()).thenReturn(1);
        PointageController controller = new PointageController();
        ReflectionTestUtils.setField(controller, "dataSource", ds);
        PointageController.ToleranceRequest request = new PointageController.ToleranceRequest();
        request.setIdSite(3);
        request.setEstActif(false);
        try (MockedStatic<AuditLog> audit = mockStatic(AuditLog.class)) {
            audit.when(() -> AuditLog.audit(eq(c), anyString(), anyString(), anyString(), isNull(), any()))
                    .thenAnswer(invocation -> { verify(c, never()).commit(); return null; });
            controller.saveTolerance(request);
            audit.verify(() -> AuditLog.audit(eq(c), eq("SAVED_PLANCHER_ARRIVEE_SITE"), eq("ref_site"), eq("3"), isNull(), any()));
        }
        verify(c).commit();
        verify(c, never()).rollback();
    }
}
