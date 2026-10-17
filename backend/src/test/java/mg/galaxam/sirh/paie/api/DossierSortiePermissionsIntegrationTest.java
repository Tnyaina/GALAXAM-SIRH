package mg.galaxam.sirh.paie.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieSaveRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DossierSortiePermissionsIntegrationTest {

    @Autowired private DossierSortieController controller;

    private void as(String... authorities) {
        var granted = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("recette", "-", granted));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lecteurPeutLireMaisPasEnregistrer() throws Exception {
        as("PAIE_READ");
        assertDoesNotThrow(() -> controller.listerDossiers());
        assertDoesNotThrow(() -> controller.rechercherEmployes(""));
        assertThrows(AccessDeniedException.class,
                () -> controller.sauvegarderDossier(new DossierSortieSaveRequest()));
    }

    @Test
    void redacteurPeutEntrerDansEcritureMaisPasLireSansDroitLecture() {
        as("PAIE_WRITE");
        assertThrows(AccessDeniedException.class, () -> controller.listerDossiers());
        assertThrows(SirhBusinessException.class,
                () -> controller.sauvegarderDossier(new DossierSortieSaveRequest()));
    }

    @Test
    void utilisateurSansDroitEstRefuse() {
        as();
        assertThrows(AccessDeniedException.class, () -> controller.listerDossiers());
        assertThrows(AccessDeniedException.class, () -> controller.rechercherEmployes(""));
        assertThrows(AccessDeniedException.class,
                () -> controller.sauvegarderDossier(new DossierSortieSaveRequest()));
    }

    @Test
    void administrateurPeutLireEtEntrerDansEcriture() throws Exception {
        as("ROLE_ADMIN");
        assertDoesNotThrow(() -> controller.listerDossiers());
        assertThrows(SirhBusinessException.class,
                () -> controller.sauvegarderDossier(new DossierSortieSaveRequest()));
    }
}
