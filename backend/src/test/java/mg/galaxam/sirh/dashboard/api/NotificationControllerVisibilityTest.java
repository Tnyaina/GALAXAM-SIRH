package mg.galaxam.sirh.dashboard.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationControllerVisibilityTest {
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void individualMarkUsesUserAndRoleVisibility() throws Exception {
        checkVisibility("ROLE_RH", false);
    }

    @Test void adminMarkUsesSameVisibilityAsAdminList() throws Exception {
        checkVisibility("ROLE_ADMIN", true);
    }

    private void checkVisibility(String role, boolean admin) throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection c = mock(Connection.class);
        PreparedStatement roleStatement = mock(PreparedStatement.class);
        PreparedStatement updateStatement = mock(PreparedStatement.class);
        ResultSet roleResult = mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.startsWith("SELECT r.code_role") ? roleStatement : updateStatement;
        });
        when(roleStatement.executeQuery()).thenReturn(roleResult);
        when(roleResult.next()).thenReturn(true);
        when(roleResult.getString("code_role")).thenReturn(role);
        when(updateStatement.executeUpdate()).thenReturn(0); // notification hors périmètre

        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getIdUtilisateur()).thenReturn(17);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
        NotificationController controller = new NotificationController();
        ReflectionTestUtils.setField(controller, "dataSource", ds);
        controller.marquerUne(99);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(c, atLeastOnce()).prepareStatement(sql.capture());
        String updateSql = sql.getAllValues().stream().filter(s -> s.startsWith("UPDATE notification_rh"))
                .findFirst().orElseThrow();
        assertTrue(updateSql.contains("id_notification = ? AND (id_utilisateur = ? OR"));
        if (admin) {
            assertTrue(!updateSql.contains("cible_role"));
        } else {
            assertTrue(updateSql.contains("id_utilisateur IS NULL AND (cible_role IS NULL OR cible_role = ?)"));
            verify(updateStatement).setString(3, role);
        }
        verify(updateStatement).setInt(1, 99);
        verify(updateStatement).setObject(2, 17, java.sql.Types.INTEGER);
        verify(updateStatement).executeUpdate();
        SecurityContextHolder.clearContext();
    }
}
