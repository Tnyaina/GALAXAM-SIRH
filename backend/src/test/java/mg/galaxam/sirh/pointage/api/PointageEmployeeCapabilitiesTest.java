package mg.galaxam.sirh.pointage.api;

import mg.galaxam.sirh.pointage.api.dto.FichePointageEmployeDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageJourDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.pointage.application.PointageEmployeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointageEmployeeCapabilitiesTest {
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    private FichePointageEmployeDTO sheet(String state, String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", "", List.of(new SimpleGrantedAuthority(authority))));
        FichePointageEmployeDTO dto = new FichePointageEmployeDTO();
        dto.setIdPeriode(9); dto.setStatutPeriode(state); dto.setSoldePeriode(new PointageSyntheseMensuelleDTO());
        PointageJourDTO past = new PointageJourDTO(); past.setDateComplete(LocalDate.now().minusDays(1).toString()); past.setStatut("PRESENT");
        PointageJourDTO future = new PointageJourDTO(); future.setDateComplete(LocalDate.now().plusDays(1).toString()); future.setStatut("NON_ECHU");
        dto.setPointagesData(List.of(past, future));
        PointageEmployeService service = mock(PointageEmployeService.class);
        when(service.getFichePointageEmploye(7, null, "periode", 9)).thenReturn(dto);
        PointageController controller = new PointageController();
        ReflectionTestUtils.setField(controller, "pointageEmployeService", service);
        return controller.getFichePointageEmploye(7, null, "periode", 9).getBody();
    }
    @Test void adminCanCorrectPastDayAndAdjustMutablePeriod() {
        var dto = sheet("TRANSMIS_DAF", "ROLE_ADMIN");
        assertTrue(dto.getAvailableActions().get("ADJUST_BALANCE"));
        assertTrue(dto.getPointagesData().get(0).isCanCorrect());
        assertFalse(dto.getPointagesData().get(1).isCanCorrect());
    }
    @Test void frozenPeriodRemainsReadOnlyEvenForAdmin() {
        for (String state : List.of("VALIDEE", "PAIE_VALIDEE", "CLOTUREE")) {
            var dto = sheet(state, "ROLE_ADMIN");
            assertFalse(dto.getAvailableActions().get("ADJUST_BALANCE"));
            assertFalse(dto.getAvailableActions().get("CORRECT_DAY"));
        }
    }
    @Test void readPermissionDoesNotGrantCorrections() {
        var dto = sheet("EN_COURS", "POINTAGE_READ");
        assertFalse(dto.getAvailableActions().get("ADJUST_BALANCE"));
        assertFalse(dto.getPointagesData().get(0).isCanCorrect());
    }
    @Test void dailyWriterCannotAdjustBalanceOrCorrectSubmittedPeriod() {
        assertTrue(sheet("EN_COURS", "POINTAGE_WRITE").getAvailableActions().get("CORRECT_DAY"));
        var dto = sheet("A_VALIDER", "POINTAGE_WRITE");
        assertFalse(dto.getAvailableActions().get("CORRECT_DAY"));
        assertFalse(dto.getAvailableActions().get("ADJUST_BALANCE"));
    }
}
