package mg.galaxam.sirh.contrat;

import mg.galaxam.sirh.contrat.api.*;
import mg.galaxam.sirh.contrat.application.DocumentRhService;
import javax.sql.DataSource;
import java.sql.Connection;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractDocumentPermissionsTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean DataSource source() throws Exception { var ds=mock(DataSource.class); when(ds.getConnection()).thenReturn(mock(Connection.class)); return ds; }
        @Bean DocumentRhService service() { return mock(DocumentRhService.class); }
        @Bean DocumentRhController documentController(DataSource ds, DocumentRhService service) {
            var controller = new DocumentRhController();
            ReflectionTestUtils.setField(controller, "dataSource", ds);
            ReflectionTestUtils.setField(controller, "documentRhService", service);
            return controller;
        }
    }
    AnnotationConfigApplicationContext context;
    @BeforeEach void setup() { context=new AnnotationConfigApplicationContext(Config.class); }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    void login(String... permissions) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user","password",java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList())); }
    @Test void contractGenerationRequiresEmployeeAndPayrollRead() throws Exception {
        var controller=context.getBean(DocumentRhController.class);
        login("EMPLOYE_READ"); assertThrows(AccessDeniedException.class,()->controller.getContratData(42)); assertThrows(AccessDeniedException.class,()->controller.downloadContratDocx(42));

        login("PAIE_READ"); assertThrows(AccessDeniedException.class,()->controller.getContratData(42));
        login("EMPLOYE_READ","PAIE_READ"); assertDoesNotThrow(()->controller.getContratData(42));
        login("ROLE_ADMIN"); assertDoesNotThrow(()->controller.getContratData(42));
    }
    @Test void signedUploadRequiresWritePermission() {
        var controller=context.getBean(DocumentRhController.class); login("EMPLOYE_READ");
        assertThrows(AccessDeniedException.class,()->controller.uploadDocument(7,new MockMultipartFile("file","signe.pdf","application/pdf",new byte[]{1}),6,null,null,42,null,null));
        verifyNoInteractions(context.getBean(DataSource.class));
    }
}
