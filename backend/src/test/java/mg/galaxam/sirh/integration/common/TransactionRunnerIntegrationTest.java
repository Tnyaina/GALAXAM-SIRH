package mg.galaxam.sirh.integration.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.common.transaction.TransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TransactionRunnerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionRunner transactions;

    @BeforeEach
    void clean() {
        databaseCleaner.cleanTransactionalData();
    }

    @Test
    void commitUneOperationComplete() {
        transactions.required(c -> {
            insertPeriod(c, "TX-COMMIT");
            return null;
        });
        assertThat(countPeriod("TX-COMMIT")).isOne();
    }

    @Test
    void rollbackTouteLOperationEnCasDEchec() {
        assertThatThrownBy(() -> transactions.required(c -> {
            insertPeriod(c, "TX-ROLLBACK");
            throw new IllegalStateException("échec métier");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(countPeriod("TX-ROLLBACK")).isZero();
    }

    private static void insertPeriod(Connection c, String code) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO periode_paie(code,libelle,date_debut,date_fin,id_statut_periode) "
                        + "VALUES (?,?,'2030-01-01','2030-01-31',"
                        + "(SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code='BROUILLON'))")) {
            ps.setString(1, code);
            ps.setString(2, code);
            ps.executeUpdate();
        }
    }

    private int countPeriod(String code) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM periode_paie WHERE code=?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
