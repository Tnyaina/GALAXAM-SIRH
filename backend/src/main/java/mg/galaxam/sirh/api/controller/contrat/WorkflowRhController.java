package mg.galaxam.sirh.api.controller.contrat;

import java.sql.Connection;
import java.sql.Date;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.contrat.EmbaucheInitialeRequest;
import mg.galaxam.sirh.api.dto.contrat.ValiderWorkflowRequest;
import mg.galaxam.sirh.model.contrat.ContratTravail;
import mg.galaxam.sirh.model.contrat.EmployeAffectationCarriere;
import mg.galaxam.sirh.model.contrat.WorkflowRh;
import mg.galaxam.sirh.model.contrat.WorkflowRhCPL;
import mg.galaxam.sirh.securite.AuditLog;
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
 *
 * Pattern : même architecture que EmployeController (Active Record, Connection manuelle).
 *
 * Endpoints principaux :
 *   POST /workflows/embauche-initiale          → Créer un brouillon d'embauche (contrat + affectation)
 *   PUT  /workflows/{id}/valider               → Valider le brouillon (passe tout en ACTIF/VALIDE)
 *   PUT  /workflows/{id}/annuler               → Annuler un brouillon
 *   GET  /workflows/{id}                       → Détail enrichi d'un workflow
 *   GET  /employes/{idEmploye}/workflows        → Historique des workflows d'un employé
 */
@RestController
public class WorkflowRhController {

    @Autowired
    private DataSource dataSource;

    // =========================================================================
    // EMBAUCHE INITIALE
    // =========================================================================

    /**
     * ÉTAPE 1 — Créer un brouillon d'embauche initiale.
     *
     * Crée en une seule transaction :
     *  1. Un contrat en statut BROUILLON
     *  2. Une affectation en statut PLANIFIEE
     *  3. Un workflow EMBAUCHE_INITIALE en statut BROUILLON liant les deux
     *
     * Retourne le workflow CPL (vue complète enrichie) pour la page de récapitulatif DAF.
     * Le DAF peut ensuite corriger ou valider définitivement.
     *
     * POST /employes/{idEmploye}/workflows/embauche-initiale
     */
    @PostMapping("/employes/{idEmploye}/workflows/embauche-initiale")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerBrouillonEmbauche(
            @PathVariable Integer idEmploye,
            @RequestBody EmbaucheInitialeRequest req) {

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // ── Vérification : pas de contrat actif ou brouillon existant ───────────
                ContratTravail contratActif = ContratTravail.rechercherActifParEmploye(idEmploye, c);
                if (contratActif != null) {
                    return ResponseEntity.badRequest()
                            .body("L'employé possède déjà un contrat ACTIF (N° " + contratActif.getNumeroContrat() + "). " +
                                    "Utilisez un workflow de transition (Mutation, Promotion, etc.).");
                }

                WorkflowRh brouillonExiste = WorkflowRh.rechercherBrouillonParEmploye(idEmploye, c);
                if (brouillonExiste != null) {
                    return ResponseEntity.badRequest()
                            .body("L'employé possède déjà un workflow BROUILLON en cours (ID " + brouillonExiste.getIdWorkflow() + "). " +
                                    "Veuillez valider, modifier ou annuler ce brouillon avant d'en créer un nouveau.");
                }

                // ── Résolution du statut BROUILLON (contrat) ────────────────────────────
                Integer idStatutBrouillonContrat = resolveStatutContrat("BROUILLON", c);
                Integer idStatutPlanifieeAffectation = resolveStatutAffectation("PLANIFIEE", c);
                Integer idStatutBrouillonWorkflow = resolveStatutWorkflow("BROUILLON", c);
                Integer idTypeEvenementEmbauche = resolveTypeEvenement("EMBAUCHE_INITIALE", c);
                Integer idMotifMouvement = resolveMotifMouvement("EMBAUCHE", c);

                // ── 1. Création du Contrat BROUILLON ────────────────────────────────────
                ContratTravail contrat = new ContratTravail();
                contrat.setIdEmploye(idEmploye);
                contrat.setIdFiliale(req.getIdFiliale());
                contrat.setIdTypeContrat(req.getIdTypeContrat());
                contrat.setIdTypeIntervenant(req.getIdTypeIntervenant());
                contrat.setIdStatutContrat(idStatutBrouillonContrat);
                contrat.setIdTypeEvenementInitial(idTypeEvenementEmbauche);
                contrat.setNumeroContrat(req.getNumeroContrat()); // null = généré automatiquement
                contrat.setDateSignature(req.getDateSignature());
                contrat.setDateDebut(req.getDateDebut());
                contrat.setDateFin(req.getDateFin());
                contrat.setComptabiliseEffectif(req.isComptabiliseEffectif());
                contrat.setDescription(req.getDescription());
                contrat.setCommentaire(req.getCommentaire());
                contrat.save(c);

                // ── 2. Création de l'Affectation PLANIFIEE ──────────────────────────────
                EmployeAffectationCarriere affectation = new EmployeAffectationCarriere();
                affectation.setIdEmploye(idEmploye);
                affectation.setIdContrat(contrat.getIdContrat());
                affectation.setIdStatutAffectation(idStatutPlanifieeAffectation);
                affectation.setIdMotifMouvement(idMotifMouvement);
                affectation.setIdSite(req.getIdSite());
                affectation.setIdDepartement(req.getIdDepartement());
                affectation.setIdService(req.getIdService());
                affectation.setIdFonction(req.getIdFonction());
                affectation.setIdResponsableHierarchique(req.getIdResponsableHierarchique());
                affectation.setIdCategorieProfessionnelle(req.getIdCategorieProfessionnelle());
                affectation.setIdModeCalculPaie(req.getIdModeCalculPaie());
                affectation.setSalaireBase(req.getSalaireBase());
                affectation.setDateDebut(req.getDateDebut());
                affectation.setDescription(req.getDescriptionAffectation());
                affectation.setCommentaire(req.getCommentaireAffectation());
                affectation.save(c);

                // ── 3. Création du Workflow BROUILLON ────────────────────────────────────
                Date dateEffet = req.getDateEffet() != null ? req.getDateEffet() : req.getDateDebut();
                WorkflowRh workflow = new WorkflowRh();
                workflow.setIdEmploye(idEmploye);
                workflow.setIdTypeEvenementRh(idTypeEvenementEmbauche);
                workflow.setIdStatutWorkflow(idStatutBrouillonWorkflow);
                workflow.setIdContratCible(contrat.getIdContrat());
                workflow.setIdAffectationCible(affectation.getIdAffectation());
                workflow.setDateEffet(dateEffet);
                workflow.setMotif(req.getMotif());
                workflow.setIdDemandeur(getCurrentUserId());
                workflow.save(c);

                AuditLog.audit(c, "CREATION_BROUILLON_EMBAUCHE", "workflow_rh",
                        String.valueOf(workflow.getIdWorkflow()), null, workflow);
                c.commit();

                // Retourne la vue enrichie pour la page de récapitulatif
                WorkflowRhCPL recap = WorkflowRhCPL.rechercherParId(workflow.getIdWorkflow(), c);
                return ResponseEntity.status(HttpStatus.CREATED).body(recap);

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

    /**
     * ÉTAPE 2 — Valider un workflow BROUILLON.
     *
     * Pour EMBAUCHE_INITIALE, cela passe :
     *  - Le contrat de BROUILLON → ACTIF
     *  - L'affectation de PLANIFIEE → ACTIVE
     *  - Le workflow de BROUILLON → VALIDE
     *
     * Règle métier : Un workflow VALIDE est immuable. Toute correction passe par un nouveau workflow.
     *
     * PUT /workflows/{idWorkflow}/valider
     */
    @PutMapping("/workflows/{idWorkflow}/valider")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerWorkflow(
            @PathVariable Integer idWorkflow,
            @RequestBody(required = false) ValiderWorkflowRequest req) {

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // ── Chargement et vérification du workflow ───────────────────────────────
                WorkflowRh workflow = new WorkflowRh().rechercherParId(idWorkflow, c);
                if (workflow == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body("Workflow introuvable (id=" + idWorkflow + ").");
                }

                // Vérifier que le statut est bien BROUILLON
                String statutCode = resolveCodeStatutWorkflow(workflow.getIdStatutWorkflow(), c);
                if (!"BROUILLON".equals(statutCode)) {
                    return ResponseEntity.badRequest()
                            .body("Ce workflow est déjà en statut '" + statutCode + "'. Seul un workflow BROUILLON peut être validé.");
                }

                // ── Identifier le type d'événement pour appliquer la bonne logique ───────
                String typeEvenementCode = resolveCodeTypeEvenement(workflow.getIdTypeEvenementRh(), c);

                switch (typeEvenementCode) {
                    case "EMBAUCHE_INITIALE" -> validerEmbaucheInitiale(workflow, req, c);
                    default -> {
                        return ResponseEntity.badRequest()
                                .body("Type d'événement '" + typeEvenementCode + "' non encore implémenté pour la validation. Bientôt disponible.");
                    }
                }

                AuditLog.audit(c, "VALIDATION_WORKFLOW", "workflow_rh",
                        String.valueOf(idWorkflow), null, workflow);
                c.commit();

                WorkflowRhCPL result = WorkflowRhCPL.rechercherParId(idWorkflow, c);
                return ResponseEntity.ok(result);

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

    /**
     * Logique de validation pour EMBAUCHE_INITIALE.
     * Passe le contrat en ACTIF et l'affectation en ACTIVE.
     */
    private void validerEmbaucheInitiale(WorkflowRh workflow, ValiderWorkflowRequest req, Connection c) throws Exception {
        // Activer le contrat cible
        if (workflow.getIdContratCible() != null) {
            ContratTravail contrat = new ContratTravail().rechercherParId(workflow.getIdContratCible(), c);
            if (contrat == null) {
                throw new IllegalStateException("Le contrat cible (id=" + workflow.getIdContratCible() + ") est introuvable.");
            }
            contrat.activer(c);
        }

        // Activer l'affectation cible
        if (workflow.getIdAffectationCible() != null) {
            EmployeAffectationCarriere affectation = new EmployeAffectationCarriere()
                    .rechercherParId(workflow.getIdAffectationCible(), c);
            if (affectation == null) {
                throw new IllegalStateException("L'affectation cible (id=" + workflow.getIdAffectationCible() + ") est introuvable.");
            }
            affectation.activer(c);
        }

        // Valider le workflow
        String commentaire = (req != null && req.getCommentaireValidation() != null)
                ? req.getCommentaireValidation() : null;
        workflow.valider(getCurrentUserId(), null, null, c);

        // Ajouter le commentaire de validation si fourni
        if (commentaire != null && !commentaire.isBlank()) {
            appendCommentaireWorkflow(workflow.getIdWorkflow(), commentaire, c);
        }
    }

    // =========================================================================
    // ANNULATION
    // =========================================================================

    /**
     * Annuler un workflow BROUILLON.
     * Remet également le contrat et l'affectation à un état annulé.
     *
     * PUT /workflows/{idWorkflow}/annuler
     */
    @PutMapping("/workflows/{idWorkflow}/annuler")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerWorkflow(
            @PathVariable Integer idWorkflow,
            @RequestBody(required = false) ValiderWorkflowRequest req) {

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                WorkflowRh workflow = new WorkflowRh().rechercherParId(idWorkflow, c);
                if (workflow == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Workflow introuvable.");
                }

                String commentaire = (req != null && req.getCommentaireValidation() != null)
                        ? req.getCommentaireValidation() : "Annulé sans motif";

                // Annuler le contrat brouillon s'il existe
                if (workflow.getIdContratCible() != null) {
                    annulerContratBrouillon(workflow.getIdContratCible(), c);
                }

                // Annuler l'affectation planifiée s'il existe
                if (workflow.getIdAffectationCible() != null) {
                    annulerAffectationPlanifiee(workflow.getIdAffectationCible(), c);
                }

                // Annuler le workflow (vérifie que c'est un BROUILLON)
                workflow.annuler(getCurrentUserId(), commentaire, c);

                AuditLog.audit(c, "ANNULATION_WORKFLOW", "workflow_rh",
                        String.valueOf(idWorkflow), workflow, null);
                c.commit();

                WorkflowRhCPL result = WorkflowRhCPL.rechercherParId(idWorkflow, c);
                return ResponseEntity.ok(result);

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

    // =========================================================================
    // CONSULTATIONS
    // =========================================================================

    /**
     * Détail complet d'un workflow (enrichi avec libellés).
     * GET /workflows/{idWorkflow}
     */
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

    /**
     * Historique complet des workflows d'un employé.
     * GET /employes/{idEmploye}/workflows
     */
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

    /**
     * Retourne le workflow BROUILLON en cours d'un employé (pour reprise de saisie).
     * GET /employes/{idEmploye}/workflows/brouillon
     */
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
    // MÉTHODES UTILITAIRES PRIVÉES
    // =========================================================================

    /** Récupère l'ID de statut contrat par son code. */
    private Integer resolveStatutContrat(String code, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Statut contrat introuvable : " + code);
    }

    /** Récupère l'ID de statut affectation par son code. */
    private Integer resolveStatutAffectation(String code, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Statut affectation introuvable : " + code);
    }

    /** Récupère l'ID de statut workflow par son code. */
    private Integer resolveStatutWorkflow(String code, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Statut workflow introuvable : " + code);
    }

    /** Récupère l'ID de type d'événement RH par son code. */
    private Integer resolveTypeEvenement(String code, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Type d'événement RH introuvable : " + code);
    }

    /** Récupère le CODE de type d'événement RH par son ID. */
    private String resolveCodeTypeEvenement(Integer id, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT code FROM ref_type_evenement_rh WHERE id_type_evenement_rh = ?")) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        throw new IllegalStateException("Type d'événement RH introuvable (id=" + id + ").");
    }

    /** Récupère le CODE de statut workflow par son ID. */
    private String resolveCodeStatutWorkflow(Integer id, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT code FROM ref_statut_workflow WHERE id_statut_workflow = ?")) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        throw new IllegalStateException("Statut workflow introuvable (id=" + id + ").");
    }

    /** Récupère l'ID du motif de mouvement par son code. */
    private Integer resolveMotifMouvement(String code, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Motif mouvement introuvable : " + code);
    }

    /** Ajoute un commentaire à un workflow existant. */
    private void appendCommentaireWorkflow(Integer idWorkflow, String commentaire, Connection c) throws Exception {
        String sql = "UPDATE workflow_rh SET commentaire = COALESCE(commentaire || ' | ' || ?, ?) WHERE id_workflow = ?";
        try (var ps = c.prepareStatement(sql)) {
            ps.setString(1, commentaire);
            ps.setString(2, commentaire);
            ps.setInt(3, idWorkflow);
            ps.executeUpdate();
        }
    }

    /** Annule un contrat BROUILLON (lors de l'annulation du workflow). */
    private void annulerContratBrouillon(Integer idContrat, Connection c) throws Exception {
        String sql = "UPDATE contrat_travail SET " +
                "id_statut_contrat = (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ROMPU'), " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_contrat = ? AND id_statut_contrat = (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'BROUILLON')";
        try (var ps = c.prepareStatement(sql)) {
            ps.setInt(1, idContrat);
            ps.executeUpdate();
        }
    }

    /** Annule une affectation PLANIFIEE (lors de l'annulation du workflow). */
    private void annulerAffectationPlanifiee(Integer idAffectation, Connection c) throws Exception {
        String sql = "UPDATE employe_affectation_carriere SET " +
                "id_statut_affectation = (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ANNULEE'), " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_affectation = ? AND id_statut_affectation = (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'PLANIFIEE')";
        try (var ps = c.prepareStatement(sql)) {
            ps.setInt(1, idAffectation);
            ps.executeUpdate();
        }
    }

    /** Récupère l'ID de l'utilisateur connecté depuis le contexte Spring Security. */
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
