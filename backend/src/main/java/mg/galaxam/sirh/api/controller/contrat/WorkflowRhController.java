package mg.galaxam.sirh.api.controller.contrat;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.common.PageDTO;
import mg.galaxam.sirh.api.dto.contrat.EmbaucheInitialeRequest;
import mg.galaxam.sirh.api.dto.contrat.TransitionCarriereRequest;
import mg.galaxam.sirh.api.dto.contrat.ValiderWorkflowRequest;
import mg.galaxam.sirh.model.contrat.WorkflowRh;
import mg.galaxam.sirh.model.contrat.WorkflowRhCPL;
import mg.galaxam.sirh.model.contrat.WorkflowRhService;
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
 * Délègue l'orchestration métier transactionnelle au service WorkflowRhService.
 */
@RestController
public class WorkflowRhController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private WorkflowRhService workflowRhService;

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

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                WorkflowRhCPL result = workflowRhService.creerBrouillonEmbauche(idEmploye, req, getCurrentUserId(), c);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(result);
            } catch (IllegalArgumentException ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création du brouillon d'embauche.");
        }
    }

    // =========================================================================
    // VALIDATION DAF
    // =========================================================================

    @PutMapping("/workflows/{idWorkflow}/valider")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerWorkflow(
            @PathVariable Integer idWorkflow,
            @RequestBody(required = false) ValiderWorkflowRequest req) {

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                WorkflowRhCPL result = workflowRhService.validerWorkflow(idWorkflow, req, getCurrentUserId(), c);
                c.commit();
                return ResponseEntity.ok(result);
            } catch (IllegalArgumentException ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la validation du workflow.");
        }
    }

    // =========================================================================
    // TRANSITION DE CARRIÈRE & MUTATION
    // =========================================================================

    @PostMapping("/employes/{idEmploye}/workflows/transition")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerBrouillonTransition(
            @PathVariable Integer idEmploye,
            @RequestBody TransitionCarriereRequest req) {

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                WorkflowRhCPL result = workflowRhService.creerBrouillonTransition(idEmploye, req, getCurrentUserId(), c);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(result);
            } catch (IllegalArgumentException ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création de la transition.");
        }
    }

    @PutMapping("/workflows/{idWorkflow}")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierBrouillonWorkflow(
            @PathVariable Integer idWorkflow,
            @RequestBody TransitionCarriereRequest req) {

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                WorkflowRhCPL detail = workflowRhService.modifierBrouillonWorkflow(idWorkflow, req, c);
                c.commit();
                return ResponseEntity.ok(detail);
            } catch (IllegalArgumentException ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la modification du brouillon.");
        }
    }

    // =========================================================================
    // ANNULATION
    // =========================================================================

    @PutMapping("/workflows/{idWorkflow}/annuler")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerWorkflow(
            @PathVariable Integer idWorkflow,
            @RequestBody(required = false) ValiderWorkflowRequest req) {

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                WorkflowRhCPL result = workflowRhService.annulerWorkflow(idWorkflow, req, getCurrentUserId(), c);
                c.commit();
                return ResponseEntity.ok(result);
            } catch (IllegalArgumentException ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de l'annulation du workflow.");
        }
    }

    @PutMapping("/workflows/{idWorkflow}/annuler-planification")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerWorkflowPlanifie(
            @PathVariable Integer idWorkflow,
            @RequestBody(required = false) ValiderWorkflowRequest req) {

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                WorkflowRhCPL result = workflowRhService.annulerWorkflowPlanifie(idWorkflow, req, getCurrentUserId(), c);
                c.commit();
                return ResponseEntity.ok(result);
            } catch (IllegalArgumentException ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de l'annulation de la planification.");
        }
    }

    // =========================================================================
    // CONSULTATIONS
    // =========================================================================

    @GetMapping("/workflows/{idWorkflow}")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getWorkflow(@PathVariable Integer idWorkflow) {
        try (Connection c = dataSource.getConnection()) {
            WorkflowRhCPL wf = WorkflowRhCPL.rechercherParId(idWorkflow, c);
            if (wf == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Workflow introuvable.");
            }
            return ResponseEntity.ok(wf);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
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
        try (Connection c = dataSource.getConnection()) {
            PageDTO<WorkflowRhCPL> result = WorkflowRhCPL.listerGlobal(q, codeTypeEvenement, codeStatut, idSite, page, size, c);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur récupération liste globale workflows.");
        }
    }

    @GetMapping("/employes/{idEmploye}/workflows")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getWorkflowsEmploye(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            List<WorkflowRhCPL> list = WorkflowRhCPL.listerParEmploye(idEmploye, c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    @GetMapping("/employes/{idEmploye}/workflows/brouillon")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getBrouillonEmploye(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            WorkflowRh wf = WorkflowRh.rechercherBrouillonParEmploye(idEmploye, c);
            if (wf == null) {
                return ResponseEntity.ok(null);
            }
            WorkflowRhCPL detail = WorkflowRhCPL.rechercherParId(wf.getIdWorkflow(), c);
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    // =========================================================================
    // CRON SCHEDULER DELEGATION
    // =========================================================================

    public static void autoAppliquerMouvementsEchus(Connection c) {
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du rattrapage : " + e.getMessage());
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
