package mg.galaxam.sirh.common.backup;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.Semaphore;
import javax.sql.DataSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Holds a shared backup gate for the full lifetime of each HTTP request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class BackupConsistencyFilter extends OncePerRequestFilter {
    private final DataSource dataSource;
    // The desktop pool is five connections. Keep room for application work that opens
    // its own JDBC connection while this request-level advisory lock is held.
    private final Semaphore admittedRequests = new Semaphore(2, true);

    public BackupConsistencyFilter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean admitted = false;
        try {
            admittedRequests.acquire();
            admitted = true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ServletException("Requête interrompue pendant l’attente de la sauvegarde", interrupted);
        }
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT pg_advisory_lock_shared(?, ?)")) {
                lock.setInt(1, BackupConsistencyGate.LOCK_NAMESPACE);
                lock.setInt(2, BackupConsistencyGate.LOCK_ID);
                lock.execute();
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                try (PreparedStatement unlock = connection.prepareStatement(
                        "SELECT pg_advisory_unlock_shared(?, ?)")) {
                    unlock.setInt(1, BackupConsistencyGate.LOCK_NAMESPACE);
                    unlock.setInt(2, BackupConsistencyGate.LOCK_ID);
                    unlock.execute();
                }
            }
        } catch (Exception error) {
            if (error instanceof ServletException servletException) throw servletException;
            if (error instanceof IOException ioException) throw ioException;
            throw new ServletException("Accès temporairement indisponible pendant une opération de sauvegarde", error);
        } finally {
            if (admitted) admittedRequests.release();
        }
    }
}
