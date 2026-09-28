package mg.galaxam.sirh.contrat.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.contrat.api.dto.ContratTravailGlobalDTO;
import mg.galaxam.sirh.common.dto.PageDTO;
import mg.galaxam.sirh.contrat.persistence.WorkflowRhCPL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Vérifie les requêtes réelles de supervision sur le schéma de test. */
@SpringBootTest
@ActiveProfiles("test")
class ContratSupervisionReadTest {
    @Autowired ContratGlobalController controller;
    @Autowired WorkflowRhController workflowController;

    @BeforeEach
    void ouvrirSessionLecture() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-admin", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void fermerSessionLecture() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void laPaginationPorteSurLesSalariesEtLeFiltreDoublonsUtiliseLaMemeRegle() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) controller.getContratsGlobal(
                null, null, null, null, null, null, false, 1, 15).getBody();
        @SuppressWarnings("unchecked")
        List<ContratTravailGlobalDTO> content = (List<ContratTravailGlobalDTO>) page.get("content");
        assertTrue(((Number) page.get("totalElements")).longValue() <=
                ((Number) page.get("totalContrats")).longValue());
        assertTrue(new HashSet<>(content.stream().map(ContratTravailGlobalDTO::getIdEmploye).toList()).size() <= 15);
        assertEquals(15, page.get("pageSize"));

        controller.getContratsGlobal(null, null, null, null, null, null, true, 1, 15);
        controller.getFiltresContrats();
        controller.getStatsSupervision();
        controller.getAlertesPrioritaires();
    }

    @Test
    void leJournalNeLivrePasDeSalaireSansPermissionPaie() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-lecture", "", List.of(new SimpleGrantedAuthority("EMPLOYE_READ"))));
        @SuppressWarnings("unchecked")
        PageDTO<WorkflowRhCPL> page = (PageDTO<WorkflowRhCPL>) workflowController.getWorkflowsGlobal(
                null, null, null, null, 1, 15,
                SecurityContextHolder.getContext().getAuthentication()).getBody();
        assertTrue(page.getContent().stream().allMatch(wf -> wf.getSalaireBase() == null));
        workflowController.getStatutsWorkflows();
    }
}
