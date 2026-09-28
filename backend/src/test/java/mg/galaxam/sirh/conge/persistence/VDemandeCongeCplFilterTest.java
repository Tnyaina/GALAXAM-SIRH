package mg.galaxam.sirh.conge.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VDemandeCongeCplFilterTest {
    @Test
    void appliqueLesMemesFiltresAvantLeTotalEtLaPagination() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement count = mock(PreparedStatement.class);
        PreparedStatement page = mock(PreparedStatement.class);
        ResultSet countResult = mock(ResultSet.class);
        ResultSet pageResult = mock(ResultSet.class);
        when(connection.prepareStatement(startsWith("SELECT COUNT(*)"))).thenReturn(count);
        when(connection.prepareStatement(startsWith("SELECT *"))).thenReturn(page);
        when(count.executeQuery()).thenReturn(countResult);
        when(page.executeQuery()).thenReturn(pageResult);
        when(countResult.next()).thenReturn(true);
        when(countResult.getLong(1)).thenReturn(12L);
        when(pageResult.next()).thenReturn(false);

        assertEquals(12, VDemandeCongeCpl.countParEmploye(7, "EN_ATTENTE", "mariage", connection));
        assertTrue(VDemandeCongeCpl.listParEmploye(7, "EN_ATTENTE", "mariage", connection, 10, 10).isEmpty());
        verify(connection, times(2)).prepareStatement(contains("code_statut_demande IN ('EN_ATTENTE', 'SOUMIS', 'EN_ATTENTE_VALIDATION')"));
        verify(count).setInt(1, 7);
        verify(count, times(3)).setString(anyInt(), eq("%mariage%"));
        verify(page).setInt(5, 10);
        verify(page).setInt(6, 10);
    }

    @Test
    void leSoldeHistoriqueDuRegistreVientDeLaRequeteBackend() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getInt("id_mouvement")).thenReturn(3);
        when(result.getBigDecimal("nb_jours")).thenReturn(new java.math.BigDecimal("-2"));
        when(result.getBigDecimal("solde_apres")).thenReturn(new java.math.BigDecimal("10.5"));
        var rows = MouvementSoldeConge.listParEmploye(7, connection);
        assertEquals(new java.math.BigDecimal("10.5"), rows.get(0).getSoldeApres());
        verify(connection).prepareStatement(contains("SUM(m.nb_jours) OVER"));
    }

    @Test
    void filtreUnStatutPrecisSansAssemblerDuSqlDepuisLaSaisie() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        VDemandeCongeCpl.countParEmploye(7, "VALIDE", null, connection);
        verify(connection).prepareStatement(contains("code_statut_demande = ?"));
        verify(statement).setString(2, "VALIDE");
    }
}
