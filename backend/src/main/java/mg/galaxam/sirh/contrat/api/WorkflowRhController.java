package mg.galaxam.sirh.contrat.api;


import java.util.List;
import mg.galaxam.sirh.common.dto.PageDTO;
import mg.galaxam.sirh.contrat.api.dto.EmbaucheInitialeRequest;
import mg.galaxam.sirh.contrat.api.dto.TransitionCarriereRequest;
import mg.galaxam.sirh.contrat.api.dto.ValiderWorkflowRequest;
import mg.galaxam.sirh.common.transaction.TransactionRunner;
import mg.galaxam.sirh.contrat.persistence.WorkflowRh;
import mg.galaxam.sirh.contrat.persistence.WorkflowRhCPL;
import mg.galaxam.sirh.contrat.application.WorkflowRhService;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST pour la gestion des Workflows RH.
 * Délègue l'orchestration métier transactionnelle à TransactionRunner (frontière JDBC unique).
 */
@RestController
public class WorkflowRhController {

    @Autowired
    private WorkflowRhService workflowRhService;

    @Autowired
    private TransactionRunner transactionRunner;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private mg.galaxam.sirh.cron.RattrapageAutomatiqueService rattrapageAutomatiqueService;

    // =========================================================================
    // EMBAUCHE INITIALE
    // =========================================================================

    @PostMapping("/employes/{idEmploye}/workflows/embauche-initiale")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerBrouillonEmbauche(
            @PathVariable Integer idEmploye,
            @RequestBody EmbaucheInitialeRequest req) {
        WorkflowRhCPL result = transactionRunner.required(c ->
                workflowRhService.creerBrouillonEmbauche(idEmploye, req, getCurrentUserId(), c));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // =========================================================================
    // VALIDATION DAF
    // =========================================================================

    @PutMapping("/workflows/{idWorkflow}/valider")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerWorkflow(
            @PathVariable Integer idWorkflow,
            @RequestBody(required = false) ValiderWorkflowRequest req) {
        WorkflowRhCPL result = transactionRunner.required(c ->
                workflowRhService.validerWorkflow(idWorkflow, req, getCurrentUserId(), c));
        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // TRANSITION DE CARRIÈRE & MUTATION
    // =========================================================================

    @PostMapping("/employes/{idEmploye}/workflows/transition")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerBrouillonTransition(
            @PathVariable Integer idEmploye,
            @RequestBody TransitionCarriereRequest req) {
        WorkflowRhCPL result = transactionRunner.required(c ->
                workflowRhService.creerBrouillonTransition(idEmploye, req, getCurrentUserId(), c));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/employes/{idEmploye}/workflows/essai/confirmer-directement")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> confirmerEssaiDirectement(
            @PathVariable Integer idEmploye, @RequestBody TransitionCarriereRequest req) {
        WorkflowRhCPL result = transactionRunner.required(c ->
                workflowRhService.confirmerEssaiDirectement(idEmploye, req, getCurrentUserId(), c));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/workflows/{idWorkflow}")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierBrouillonWorkflow(
            @PathVariable Integer idWorkflow,
            @RequestBody TransitionCarriereRequest req) {
        WorkflowRhCPL detail = transactionRunner.required(c ->
                workflowRhService.modifierBrouillonWorkflow(idWorkflow, req, c));
        return ResponseEntity.ok(detail);
    }

    // =========================================================================
    // ANNULATION
    // =========================================================================

    @PutMapping("/workflows/{idWorkflow}/annuler")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerWorkflow(
            @PathVariable Integer idWorkflow,
            @RequestBody(required = false) ValiderWorkflowRequest req) {
        WorkflowRhCPL result = transactionRunner.required(c ->
                workflowRhService.annulerWorkflow(idWorkflow, req, getCurrentUserId(), c));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/workflows/{idWorkflow}/annuler-planification")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerWorkflowPlanifie(
            @PathVariable Integer idWorkflow,
            @RequestBody(required = false) ValiderWorkflowRequest req) {
        WorkflowRhCPL result = transactionRunner.required(c ->
                workflowRhService.annulerWorkflowPlanifie(idWorkflow, req, getCurrentUserId(), c));
        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // CONSULTATIONS (lecture seule — pas de transaction d'écriture)
    // =========================================================================

    @GetMapping("/workflows/{idWorkflow}")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getWorkflow(@PathVariable Integer idWorkflow) {
        return transactionRunner.required(c -> {
            WorkflowRhCPL wf = WorkflowRhCPL.rechercherParId(idWorkflow, c);
            if (wf == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Workflow introuvable.");
            }
            return ResponseEntity.ok(wf);
        });
    }

    @GetMapping("/workflows")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getWorkflowsGlobal(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String codeTypeEvenement,
            @RequestParam(required = false) String codeStatut,
            @RequestParam(required = false) Integer idSite,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        return transactionRunner.required(c -> {
            PageDTO<WorkflowRhCPL> result = WorkflowRhCPL.listerGlobal(q, codeTypeEvenement, codeStatut, idSite, page, size, c);
            return ResponseEntity.ok(result);
        });
    }

    @GetMapping("/employes/{idEmploye}/workflows")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getWorkflowsEmploye(@PathVariable Integer idEmploye) {
        return transactionRunner.required(c -> {
            List<WorkflowRhCPL> list = WorkflowRhCPL.listerParEmploye(idEmploye, c);
            return ResponseEntity.ok(list);
        });
    }

    @GetMapping("/employes/{idEmploye}/workflows/brouillon")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getBrouillonEmploye(@PathVariable Integer idEmploye) {
        return transactionRunner.required(c -> {
            WorkflowRh wf = WorkflowRh.rechercherBrouillonParEmploye(idEmploye, c);
            if (wf == null) {
                return ResponseEntity.ok(null);
            }
            WorkflowRhCPL detail = WorkflowRhCPL.rechercherParId(wf.getIdWorkflow(), c);
            return ResponseEntity.ok(detail);
        });
    }

    // =========================================================================
    // CRON SCHEDULER DELEGATION
    // =========================================================================

    public static void autoAppliquerMouvementsEchus(java.sql.Connection c) {
        WorkflowRhService.autoAppliquerMouvementsEchus(c);
    }

    /**
     * Déclenchement manuel ou explicite du rattrapage quotidien des mouvements RH et alertes.
     */
    @PostMapping("/workflows-rh/rattrapage")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> forcerRattrapage() {
        try {
            if (rattrapageAutomatiqueService != null) {
                rattrapageAutomatiqueService.verifierEtExecuterRattrapage(true);
            }
            return ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "Rattrapage quotidien des mouvements RH, alertes et congés exécuté avec succès."
            ));
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // MÉTHODES UTILITAIRES PRIVÉES
    // =========================================================================

    private Integer getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) {
                return details.getIdUtilisateur();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
