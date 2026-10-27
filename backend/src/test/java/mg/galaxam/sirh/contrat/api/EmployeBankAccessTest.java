package mg.galaxam.sirh.contrat.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import mg.galaxam.sirh.common.dto.PageDTO;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.contrat.api.dto.EmployeFicheDetailDTO;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import mg.galaxam.sirh.contrat.application.EmployeService;
import mg.galaxam.sirh.contrat.persistence.EmployeCPL;
import mg.galaxam.sirh.contrat.persistence.EmployeCompteBanque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

class EmployeBankAccessTest {
    private final EmployeService service = mock(EmployeService.class);
    private final EmployeController controller = new EmployeController();
    private final ObjectMapper mapper = new ObjectMapper();

    EmployeBankAccessTest() {
        ReflectionTestUtils.setField(controller, "employeService", service);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void as(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "synthetic-user", "unused", java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new).toList()));
    }

    private EmployeFicheDetailDTO detail() {
        EmployeCPL employee = new EmployeCPL();
        employee.setNumCompteRibPrincipal("SYNTHETIC-RIB-001");
        employee.setTitulaireComptePrincipal("Synthetic holder");
        EmployeCompteBanque account = new EmployeCompteBanque();
        account.setNumCompteRib("SYNTHETIC-RIB-001");
        return new EmployeFicheDetailDTO(employee, null, List.of(), List.of(), List.of(account));
    }

    @Test
    void employeeOnlyResponseDoesNotContainBankingData() throws Exception {
        as("EMPLOYE_READ", "EMPLOYE_WRITE");
        when(service.getFicheDetail(7, false)).thenReturn(new EmployeFicheDetailDTO(
                detail().getEmploye(), null, List.of(), List.of(), List.of()));
        String json = mapper.writeValueAsString(controller.getEmployeFicheDetail(7).getBody());
        assertFalse(json.contains("SYNTHETIC-RIB"));
        assertFalse(json.contains("numCompteRibPrincipal"));
        assertTrue(json.contains("\"comptesBancaires\":[]"));
        verify(service).getFicheDetail(7, false);
    }

    @Test
    void employeeListNeverSerializesBankSummary() throws Exception {
        as("EMPLOYE_READ");
        when(service.getEmployes(null, null, null, null, null, "HIERARCHICAL", "asc", 1, 10))
                .thenReturn(new PageDTO<>(List.of(detail().getEmploye()), 1, 1, 10));
        String json = mapper.writeValueAsString(controller.getEmployes(
                null, null, null, null, null, "HIERARCHICAL", "asc", 1, 10).getBody());
        assertFalse(json.contains("SYNTHETIC-RIB"));
        assertFalse(json.contains("titulaireComptePrincipal"));
        assertFalse(json.contains("idCompteBanquePrincipal"));
    }

    @Test
    void payrollReaderSeesAccountsButSummaryNeverLeaksInEmployeeProjection() throws Exception {
        as("EMPLOYE_READ", "PAIE_READ");
        when(service.getFicheDetail(7, true)).thenReturn(detail());
        String json = mapper.writeValueAsString(controller.getEmployeFicheDetail(7).getBody());
        assertTrue(json.contains("SYNTHETIC-RIB-001"));
        assertFalse(json.contains("numCompteRibPrincipal"));
        assertFalse(mapper.writeValueAsString(detail().getEmploye()).contains("SYNTHETIC-RIB"));
    }

    @Test
    void employeeWriterCannotChangeBankAccountThroughGeneralUpdate() throws Exception {
        as("EMPLOYE_WRITE", "PAIE_READ");
        EmployeRequest request = new EmployeRequest();
        request.setCompteBanquePrincipal(new EmployeRequest.CompteBanqueRequest());
        assertThrows(SirhBusinessException.class, () -> controller.updateEmploye(7, request));
        verifyNoInteractions(service);
    }

    @Test
    void adminCanUpdateBankThroughGeneralEmployeePath() throws Exception {
        as("ROLE_ADMIN");
        EmployeRequest request = new EmployeRequest();
        request.setCompteBanquePrincipal(new EmployeRequest.CompteBanqueRequest());
        when(service.modifierEmploye(7, request)).thenReturn(new EmployeCPL());
        assertEquals(200, controller.updateEmploye(7, request).getStatusCode().value());
        verify(service).modifierEmploye(7, request);
    }

    @Test
    void payrollWriterCanUseDedicatedEndpointWithoutBankingResponse() throws Exception {
        as("PAIE_WRITE");
        EmployeRequest request = new EmployeRequest();
        request.setSupprimerCompteBanque(true);
        ResponseEntity<?> response = controller.updateCompteBancaire(7, request);
        assertEquals(204, response.getStatusCode().value());
        verify(service).modifierCompteBancaire(7, request);
    }

    @Test
    void emptyDedicatedBankRequestIsRejectedBeforeOpeningDatabase() {
        EmployeRequest request = new EmployeRequest();
        request.setCompteBanquePrincipal(new EmployeRequest.CompteBanqueRequest());
        assertThrows(IllegalArgumentException.class,
                () -> new EmployeService(null, null, null).modifierCompteBancaire(7, request));
    }
}
