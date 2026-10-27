package mg.galaxam.sirh.common.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class BackupConsistencyGateTest {
    @Test
    void holdsSharedDatabaseLockAroundOperationAndReleasesIt() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(acquire, release);

        BackupConsistencyGate gate = new BackupConsistencyGate(dataSource);
        assertEquals("done", gate.withWriteAccess(() -> "done"));

        InOrder order = inOrder(acquire, release, connection);
        order.verify(acquire).execute();
        order.verify(release).execute();
        order.verify(connection).close();
        verify(acquire).setInt(1, BackupConsistencyGate.LOCK_NAMESPACE);
        verify(acquire).setInt(2, BackupConsistencyGate.LOCK_ID);
        verify(release).setInt(1, BackupConsistencyGate.LOCK_NAMESPACE);
        verify(release).setInt(2, BackupConsistencyGate.LOCK_ID);
    }

    @Test
    void releasesSharedDatabaseLockWhenOperationFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(acquire, release);

        BackupConsistencyGate gate = new BackupConsistencyGate(dataSource);
        assertThrows(IllegalStateException.class, () -> gate.withWriteAccess(
                (java.util.concurrent.Callable<Void>) () -> {
                    throw new IllegalStateException("synthetic failure");
                }));

        verify(acquire).execute();
        verify(release).execute();
        verify(connection).close();
    }
}
