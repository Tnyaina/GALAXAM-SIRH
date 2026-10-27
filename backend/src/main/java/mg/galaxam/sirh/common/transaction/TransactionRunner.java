package mg.galaxam.sirh.common.transaction;

import java.sql.Connection;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhExceptionTranslator;
import org.springframework.stereotype.Component;

/** Frontière transactionnelle JDBC unique pour les cas d'usage applicatifs. */
@Component
public class TransactionRunner {

    private final DataSource dataSource;

    public TransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> T required(Work<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception error) {
                connection.rollback();
                throw SirhExceptionTranslator.propagate(error);
            }
        } catch (Exception error) {
            throw SirhExceptionTranslator.propagate(error);
        }
    }

    @FunctionalInterface
    public interface Work<T> {
        T execute(Connection connection) throws Exception;
    }
}
