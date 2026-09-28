package mg.galaxam.sirh.conge;

import java.sql.Connection;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.cron.AcquisitionCongeScheduler;
import mg.galaxam.sirh.cron.SchedulerExecutionRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AcquisitionCongeSchedulerTest {

    @Test
    void cronTraiteUniquementLaBonificationDuMoisClos() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        CongeService congeService = mock(CongeService.class);
        SchedulerExecutionRegistry registry = mock(SchedulerExecutionRegistry.class);
        when(dataSource.getConnection()).thenReturn(connection);

        AcquisitionCongeScheduler scheduler = new AcquisitionCongeScheduler();
        ReflectionTestUtils.setField(scheduler, "dataSource", dataSource);
        ReflectionTestUtils.setField(scheduler, "congeService", congeService);
        ReflectionTestUtils.setField(scheduler, "executionRegistry", registry);

        scheduler.acquerirCongesMensuels();

        LocalDate moisClos = LocalDate.now().withDayOfMonth(1).minusDays(1);
        ArgumentCaptor<SchedulerExecutionRegistry.ScheduledWork> work =
                ArgumentCaptor.forClass(SchedulerExecutionRegistry.ScheduledWork.class);
        verify(registry).run(eq("BONIFICATION_CONGES"), eq(moisClos.withDayOfMonth(1)), work.capture());
        work.getValue().run();

        ArgumentCaptor<LocalDate> date = ArgumentCaptor.forClass(LocalDate.class);
        verify(congeService).executerBonificationMensuelle(date.capture(), eq(connection));
        assertEquals(moisClos, date.getValue());
        verify(connection).commit();
        verifyNoMoreInteractions(congeService);
    }
}
