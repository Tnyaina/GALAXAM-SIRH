package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.PointageSyntheseMensuelleDTO;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointagePreflightOvertimeWithoutWeekTest {

    @Test
    void reportsBlockerWhenOvertimeExistsWithoutWeeklyBreakdown() throws Exception {
        int periodId = 73;
        LocalDate weekend = LocalDate.of(2028, 9, 2); // Samedi : aucune journée ouvrable à compléter.

        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet rows = mock(ResultSet.class);
            when(statement.executeQuery()).thenReturn(rows);

            boolean hasPeriodDates = sql.contains("SELECT date_debut") || sql.contains("SELECT date_fin");
            when(rows.next()).thenReturn(hasPeriodDates, false);
            if (sql.contains("SELECT date_debut,date_fin") || sql.contains("SELECT date_debut, date_fin")) {
                when(rows.getDate(1)).thenReturn(Date.valueOf(weekend));
                when(rows.getDate(2)).thenReturn(Date.valueOf(weekend));
            } else if (sql.contains("SELECT date_fin")) {
                when(rows.getDate(1)).thenReturn(Date.valueOf(weekend));
            }
            return statement;
        });

        PointageSyntheseMensuelleDTO employee = new PointageSyntheseMensuelleDTO();
        employee.setIdEmploye(9001);
        employee.setMatricule("TEST-HS-SANS-SEMAINE");
        employee.setMinutesHs30Hebdo(60);
        employee.setTronconsSemaine(List.of());

        PointageSyntheseService service = org.mockito.Mockito.spy(new PointageSyntheseService());
        doReturn(List.of(employee)).when(service).getSynthesePeriode(periodId, null, connection);

        var preflight = service.preflightPeriode(periodId, connection);

        assertEquals(1, preflight.blocages().size(),
                "Le précontrôle doit refuser un cumul HS dépourvu de détail hebdomadaire.");
        assertEquals(60, preflight.blocages().get(0).minutesHsSansSemaine());
    }
}
