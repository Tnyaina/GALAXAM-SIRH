package mg.galaxam.sirh.securite.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.transaction.TransactionRunner;
import mg.galaxam.sirh.securite.IdleSessionService;
import mg.galaxam.sirh.securite.api.dto.UtilisateurRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UtilisateurPasswordChangeTest {
    private Connection connection;
    private IdleSessionService sessions;
    private UtilisateurController controller;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(false);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement statement = mock(PreparedStatement.class);
            if (sql.startsWith("SELECT * FROM utilisateur WHERE id_utilisateur=")) {
                ResultSet rows = mock(ResultSet.class);
                when(rows.next()).thenReturn(true);
                when(rows.getInt("id_utilisateur")).thenReturn(41);
                when(rows.getInt("id_role")).thenReturn(1);
                when(rows.getString("identifiant")).thenReturn("Agent");
                when(rows.getString("email")).thenReturn("agent@example.test");
                when(rows.getString("mot_de_passe_hache")).thenReturn("$2a$10$oldHashPlaceholder");
                when(rows.getBoolean("est_actif")).thenReturn(true);
                when(rows.getString("refresh_token")).thenReturn("legacy-token");
                when(statement.executeQuery()).thenReturn(rows);
            } else if (sql.startsWith("SELECT * FROM utilisateur WHERE LOWER(email)")) {
                when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
            } else if (sql.startsWith("SELECT * FROM ref_role WHERE id_role=")) {
                ResultSet rows = mock(ResultSet.class);
                when(rows.next()).thenReturn(true);
                when(rows.getInt("id_role")).thenReturn(1);
                when(rows.getString("code_role")).thenReturn("ROLE_ADMIN");
                when(rows.getString("libelle")).thenReturn("Administrateur");
                when(rows.getBoolean("est_actif")).thenReturn(true);
                when(statement.executeQuery()).thenReturn(rows);
            }
            return statement;
        });

        sessions = mock(IdleSessionService.class);
        controller = new UtilisateurController();
        ReflectionTestUtils.setField(controller, "transactionRunner", new TransactionRunner(dataSource));
        ReflectionTestUtils.setField(controller, "idleSessionService", sessions);
    }

    @Test
    void passwordChangeRevokesAllSessionsInAccountTransaction() throws Exception {
        UtilisateurRequest request = new UtilisateurRequest();
        request.setMotDePasse("NewSecret1");

        controller.updateUser(41, request);

        verify(sessions).revokeAllForUser(41, connection);
        verify(connection).commit();
    }

    @Test
    void profileChangeWithoutPasswordKeepsSessions() throws Exception {
        UtilisateurRequest request = new UtilisateurRequest();
        request.setIdentifiant("Agent modifié");

        controller.updateUser(41, request);

        verify(sessions, never()).revokeAllForUser(anyInt(), any());
        verify(connection).commit();
    }

    @Test
    void failedRevocationRollsBackPasswordChange() throws Exception {
        UtilisateurRequest request = new UtilisateurRequest();
        request.setMotDePasse("NewSecret1");
        doThrow(new IllegalStateException("revocation unavailable"))
                .when(sessions).revokeAllForUser(41, connection);

        assertThrows(RuntimeException.class, () -> controller.updateUser(41, request));

        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
