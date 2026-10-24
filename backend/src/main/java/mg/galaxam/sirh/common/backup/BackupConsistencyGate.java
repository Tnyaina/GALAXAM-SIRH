package mg.galaxam.sirh.common.backup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.Callable;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Shared PostgreSQL advisory lock used by every backend operation that may write data. */
@Component
public class BackupConsistencyGate {
    public static final int LOCK_NAMESPACE = 1_793_260_227;
    public static final int LOCK_ID = 1;

    private final DataSource dataSource;

    public BackupConsistencyGate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> T withWriteAccess(Callable<T> operation) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT pg_advisory_lock_shared(?, ?)")) {
                lock.setInt(1, LOCK_NAMESPACE);
                lock.setInt(2, LOCK_ID);
                lock.execute();
            }
            try {
                return operation.call();
            } finally {
                try (PreparedStatement unlock = connection.prepareStatement(
                        "SELECT pg_advisory_unlock_shared(?, ?)")) {
                    unlock.setInt(1, LOCK_NAMESPACE);
                    unlock.setInt(2, LOCK_ID);
                    unlock.execute();
                }
            }
        }
    }

    public void withWriteAccess(CheckedRunnable operation) throws Exception {
        withWriteAccess(() -> {
            operation.run();
            return null;
        });
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }
}
