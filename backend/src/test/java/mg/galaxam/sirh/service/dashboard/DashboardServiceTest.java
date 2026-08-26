package mg.galaxam.sirh.service.dashboard;

import mg.galaxam.sirh.model.dashboard.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DashboardServiceTest {

    @Test
    @DisplayName("Instanciation et contrat du service DashboardService")
    void testDashboardServiceContract() {
        DashboardService service = new DashboardService(null);
        assertNotNull(service);
    }
}
