package mg.galaxam.sirh.api.controller.contrat;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.contrat.EmbaucheInitialeRequest;
import mg.galaxam.sirh.api.dto.contrat.TransitionCarriereRequest;
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
                contrat.setDureeEssaiMois(req.getDureeEssaiMois());
                contrat.setDateFinEssai(req.getDateFinEssai());
                contrat.setComptabiliseEffectif(req.isComptabiliseEffectif());
                contrat.setDescription(req.getDescription());
                contrat.setCommentaire(req.getCommentaire());
                contrat.save(c);

                // Enregistrer la période d'essai initiale dans contrat_periode_essai si renseignée
                if (contrat.getDureeEssaiMois() != null && contrat.getDureeEssaiMois() > 0) {
                    enregistrerPeriodeEssaiInitiale(contrat, c);
                }

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

                String empNomBrouillon = "";
                try (var psEmp = c.prepareStatement("SELECT nom || ' ' || prenom FROM employe WHERE id_employe = ?")) {
                    psEmp.setInt(1, idEmploye);
                    try (var rsEmp = psEmp.executeQuery()) {
                        if (rsEmp.next()) empNomBrouillon = rsEmp.getString(1);
                    }
                }
                creerNotificationRh(c, null, "BROUILLON_CREE", "HAUTE",
                        "Nouveau brouillon d'embauche - " + empNomBrouillon,
                        "Un brouillon d'embauche initiale pour " + empNomBrouillon + " a ete cree et attend votre validation.",
                        "/employes/" + idEmploye + "?tab=carriere&wf=" + workflow.getIdWorkflow(), idEmploye);

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
                    case "RENOUVELLEMENT_CDD", "TRANSFORMATION_CDI", "EMBAUCHE_APRES_STAGE", "MUTATION", "PROMOTION", "REVALORISATION", "CONFIRMATION_ESSAI", "RENOUVELLEMENT_ESSAI", "REPRISE_CONSULTANT" -> validerTransition(workflow, req, c);
                    case "FIN_CONTRAT", "RUPTURE_ESSAI", "DEMISSION", "LICENCIEMENT", "RUPTURE_CONVENTIONNELLE", "DEPART_RETRAITE" -> validerRupture(workflow, req, c);
                    default -> {
                        return ResponseEntity.badRequest()
                                .body("Type d'événement '" + typeEvenementCode + "' non encore implémenté pour la validation.");
                    }
                }

                AuditLog.audit(c, "VALIDATION_WORKFLOW", "workflow_rh",
                        String.valueOf(idWorkflow), null, workflow);

                // Marquer les notifications de brouillon relatives comme lues
                try (PreparedStatement psClear = c.prepareStatement(
                        "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                        "WHERE id_employe_concerne = ? AND (lien LIKE ? OR type_alerte = 'BROUILLON_CREE')")) {
                    psClear.setInt(1, workflow.getIdEmploye());
                    psClear.setString(2, "%wf=" + idWorkflow);
                    psClear.executeUpdate();
                }

                String empNomValide = "";
                try (var psEmp = c.prepareStatement("SELECT nom || ' ' || prenom FROM employe WHERE id_employe = ?")) {
                    psEmp.setInt(1, workflow.getIdEmploye());
                    try (var rsEmp = psEmp.executeQuery()) {
                        if (rsEmp.next()) empNomValide = rsEmp.getString(1);
                    }
                }
                String typeEvNomValide = "Mouvement RH";
                try (var psEv = c.prepareStatement("SELECT libelle FROM ref_type_evenement_rh WHERE id_type_evenement_rh = ?")) {
                    psEv.setInt(1, workflow.getIdTypeEvenementRh());
                    try (var rsEv = psEv.executeQuery()) {
                        if (rsEv.next()) typeEvNomValide = rsEv.getString(1);
                    }
                }
                creerNotificationRh(c, null, "MOUVEMENT_APPLIQUE", "NORMALE",
                        "Mouvement RH validé : " + typeEvNomValide + " - " + empNomValide,
                        "Le mouvement RH (" + typeEvNomValide + ") pour " + empNomValide + " a ete valide et confirme avec succes.",
                        "/employes/" + workflow.getIdEmploye() + "?tab=carriere&wf=" + idWorkflow, workflow.getIdEmploye());

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
    // TRANSITION DE CARRIÈRE & MUTATION (SPRINT 2)
    // =========================================================================

    /**
     * ÉTAPE 1 (Transition) — Créer un brouillon de transition de carrière (Mutation, Promotion, Transformation CDI, etc.).
     *
     * POST /employes/{idEmploye}/workflows/transition
     */
    @PostMapping("/employes/{idEmploye}/workflows/transition")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerBrouillonTransition(
            @PathVariable Integer idEmploye,
            @RequestBody TransitionCarriereRequest req) {

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ContratTravail contratActif = ContratTravail.rechercherActifParEmploye(idEmploye, c);
                EmployeAffectationCarriere affectationActive = EmployeAffectationCarriere.rechercherActiveParEmploye(idEmploye, c);

                WorkflowRh brouillonExiste = WorkflowRh.rechercherBrouillonParEmploye(idEmploye, c);
                if (brouillonExiste != null) {
                    return ResponseEntity.badRequest()
                            .body("L'employé possède déjà un workflow BROUILLON en cours (ID " + brouillonExiste.getIdWorkflow() + "). " +
                                    "Veuillez valider, modifier ou annuler ce brouillon avant d'en créer un nouveau.");
                }

                Integer idTypeEvenement = resolveTypeEvenement(req.getCodeTypeEvenement(), c);

                // --- Règles de gestion Backend ---
                boolean exigeNouveauContrat = List.of("TRANSFORMATION_CDI", "RENOUVELLEMENT_CDD", "EMBAUCHE_APRES_STAGE", "REPRISE_CONSULTANT")
                        .contains(req.getCodeTypeEvenement());

                if (exigeNouveauContrat) {
                    if (req.getIdTypeContrat() == null) {
                        return ResponseEntity.badRequest().body("Le type de contrat de destination est obligatoire pour cette transition.");
                    }
                    if (req.getIdTypeIntervenant() == null) {
                        return ResponseEntity.badRequest().body("Le type d'intervenant est obligatoire pour cette transition.");
                    }

                    // Vérification de la cohérence Stagiaire / Stage
                    String codeTypeContrat = "";
                    try (var psTC = c.prepareStatement("SELECT code FROM ref_type_contrat WHERE id_type_contrat = ?")) {
                        psTC.setInt(1, req.getIdTypeContrat());
                        try (var rsTC = psTC.executeQuery()) {
                            if (rsTC.next()) codeTypeContrat = rsTC.getString("code");
                        }
                    }

                    String codeTypeIntervenant = "";
                    try (var psTI = c.prepareStatement("SELECT code FROM ref_type_intervenant WHERE id_type_intervenant = ?")) {
                        psTI.setInt(1, req.getIdTypeIntervenant());
                        try (var rsTI = psTI.executeQuery()) {
                            if (rsTI.next()) codeTypeIntervenant = rsTI.getString("code");
                        }
                    }

                    if ("STAGIAIRE".equals(codeTypeIntervenant) && !"STAGE".equals(codeTypeContrat)) {
                        return ResponseEntity.badRequest().body("Un intervenant Stagiaire doit obligatoirement être sous un contrat de type Convention / Contrat de Stage.");
                    }
                    if (!"STAGIAIRE".equals(codeTypeIntervenant) && "STAGE".equals(codeTypeContrat)) {
                        return ResponseEntity.badRequest().body("Le contrat de Stage est réservé aux stagiaires.");
                    }
                }

                Integer idStatutBrouillonContrat = resolveStatutContrat("BROUILLON", c);
                Integer idStatutPlanifieeAffectation = resolveStatutAffectation("PLANIFIEE", c);
                Integer idStatutBrouillonWorkflow = resolveStatutWorkflow("BROUILLON", c);

                String codeMotif = switch (req.getCodeTypeEvenement() != null ? req.getCodeTypeEvenement() : "") {
                    case "PROMOTION" -> "PROMOTION";
                    case "MUTATION" -> "MUTATION";
                    case "REVALORISATION" -> "REVALORISATION";
                    case "RENOUVELLEMENT_CDD", "TRANSFORMATION_CDI", "EMBAUCHE_APRES_STAGE", "REPRISE_CONSULTANT" -> "AVENANT";
                    default -> "AUTRE";
                };
                Integer idMotifMouvement = resolveMotifMouvement(codeMotif, c);

                boolean estEvenementEssai = "CONFIRMATION_ESSAI".equals(req.getCodeTypeEvenement()) || "RENOUVELLEMENT_ESSAI".equals(req.getCodeTypeEvenement());
                boolean estEvenementRupture = List.of("FIN_CONTRAT", "RUPTURE_ESSAI", "DEMISSION", "LICENCIEMENT", "RUPTURE_CONVENTIONNELLE", "DEPART_RETRAITE").contains(req.getCodeTypeEvenement());

                Integer idContratCibleId = contratActif != null ? contratActif.getIdContrat() : req.getIdContratSource();

                // Création d'un nouveau contrat UNIQUEMENT si transition de contrat réelle (CDD -> CDI, etc.)
                if (!estEvenementEssai && !estEvenementRupture && req.getIdTypeContrat() != null && (contratActif == null || !req.getIdTypeContrat().equals(contratActif.getIdTypeContrat()))) {
                    ContratTravail contratCbl = new ContratTravail();
                    contratCbl.setIdEmploye(idEmploye);
                    contratCbl.setIdFiliale(req.getIdFiliale());
                    contratCbl.setIdTypeContrat(req.getIdTypeContrat());
                    contratCbl.setIdTypeIntervenant(req.getIdTypeIntervenant());
                    contratCbl.setIdStatutContrat(idStatutBrouillonContrat);
                    contratCbl.setIdTypeEvenementInitial(idTypeEvenement);
                    contratCbl.setNumeroContrat(req.getNumeroContrat());
                    contratCbl.setDateSignature(req.getDateSignature());
                    contratCbl.setDateDebut(req.getDateDebut() != null ? req.getDateDebut() : req.getDateEffet());
                    contratCbl.setDateFin(req.getDateFin());
                    contratCbl.setDureeEssaiMois(req.getDureeEssaiMois());
                    contratCbl.setDateFinEssai(req.getDateFinEssai());
                    contratCbl.setComptabiliseEffectif(req.getComptabiliseEffectif() != null ? req.getComptabiliseEffectif() : true);
                    contratCbl.setDescription(req.getDescriptionContrat());
                    contratCbl.setCommentaire(req.getCommentaireContrat());
                    contratCbl.setIdContratPrecedent(contratActif != null ? contratActif.getIdContrat() : req.getIdContratSource());
                    contratCbl.save(c);

                    if (contratCbl.getDureeEssaiMois() != null && contratCbl.getDureeEssaiMois() > 0) {
                        enregistrerPeriodeEssaiInitiale(contratCbl, c);
                    }

                    idContratCibleId = contratCbl.getIdContrat();
                }

                Integer idAffectationCibleId = null;
                if (estEvenementEssai || estEvenementRupture) {
                    idAffectationCibleId = affectationActive != null ? affectationActive.getIdAffectation() : req.getIdAffectationSource();
                    if (idAffectationCibleId == null && idContratCibleId != null) {
                        try (var psAff = c.prepareStatement("SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = ? ORDER BY id_affectation DESC LIMIT 1")) {
                            psAff.setInt(1, idContratCibleId);
                            try (var rsAff = psAff.executeQuery()) {
                                if (rsAff.next()) idAffectationCibleId = rsAff.getInt(1);
                            }
                        }
                    }
                    if (idAffectationCibleId == null) {
                        try (var psAff = c.prepareStatement("SELECT id_affectation FROM employe_affectation_carriere WHERE id_employe = ? ORDER BY id_affectation DESC LIMIT 1")) {
                            psAff.setInt(1, idEmploye);
                            try (var rsAff = psAff.executeQuery()) {
                                if (rsAff.next()) idAffectationCibleId = rsAff.getInt(1);
                            }
                        }
                    }
                } else {
                    EmployeAffectationCarriere affectationCbl = new EmployeAffectationCarriere();
                    affectationCbl.setIdEmploye(idEmploye);
                    affectationCbl.setIdContrat(idContratCibleId);
                    affectationCbl.setIdStatutAffectation(idStatutPlanifieeAffectation);
                    affectationCbl.setIdMotifMouvement(idMotifMouvement);
                    affectationCbl.setIdSite(req.getIdSite());
                    affectationCbl.setIdDepartement(req.getIdDepartement());
                    affectationCbl.setIdService(req.getIdService());
                    affectationCbl.setIdFonction(req.getIdFonction());
                    affectationCbl.setIdResponsableHierarchique(req.getIdResponsableHierarchique());
                    affectationCbl.setIdCategorieProfessionnelle(req.getIdCategorieProfessionnelle());
                    affectationCbl.setIdModeCalculPaie(req.getIdModeCalculPaie());
                    affectationCbl.setSalaireBase(req.getSalaireBase() != null ? java.math.BigDecimal.valueOf(req.getSalaireBase()) : null);
                    affectationCbl.setDateDebut(req.getDateEffet());
                    affectationCbl.setDateFin(null);
                    affectationCbl.setDescription(req.getDescriptionAffectation());
                    affectationCbl.setCommentaire(req.getCommentaireAffectation());
                    affectationCbl.save(c);
                    idAffectationCibleId = affectationCbl.getIdAffectation();
                }

                WorkflowRh workflow = new WorkflowRh();
                workflow.setIdEmploye(idEmploye);
                workflow.setIdTypeEvenementRh(idTypeEvenement);
                workflow.setIdStatutWorkflow(idStatutBrouillonWorkflow);
                workflow.setIdContratSource(contratActif != null ? contratActif.getIdContrat() : req.getIdContratSource());
                workflow.setIdContratCible(idContratCibleId);
                workflow.setIdAffectationSource(affectationActive != null ? affectationActive.getIdAffectation() : req.getIdAffectationSource());
                workflow.setIdAffectationCible(idAffectationCibleId);
                workflow.setDateDemande(new Date(System.currentTimeMillis()));
                workflow.setDateEffet(req.getDateEffet());
                workflow.setMotif(req.getMotif());
                workflow.setIdDemandeur(getCurrentUserId());
                workflow.save(c);

                AuditLog.audit(c, "CREATION_WORKFLOW_TRANSITION", "workflow_rh",
                        String.valueOf(workflow.getIdWorkflow()), null, workflow);

                String empNomTrans = "";
                try (var psEmp = c.prepareStatement("SELECT nom || ' ' || prenom FROM employe WHERE id_employe = ?")) {
                    psEmp.setInt(1, idEmploye);
                    try (var rsEmp = psEmp.executeQuery()) {
                        if (rsEmp.next()) empNomTrans = rsEmp.getString(1);
                    }
                }
                String typeEvNomTrans = "Transition";
                try (var psEv = c.prepareStatement("SELECT libelle FROM ref_type_evenement_rh WHERE id_type_evenement_rh = ?")) {
                    psEv.setInt(1, idTypeEvenement);
                    try (var rsEv = psEv.executeQuery()) {
                        if (rsEv.next()) typeEvNomTrans = rsEv.getString(1);
                    }
                }
                creerNotificationRh(c, null, "BROUILLON_CREE", "HAUTE",
                        "Nouveau brouillon : " + typeEvNomTrans + " - " + empNomTrans,
                        "Un brouillon de type " + typeEvNomTrans + " pour " + empNomTrans + " a ete cree et attend votre validation.",
                        "/employes/" + idEmploye + "?tab=carriere&wf=" + workflow.getIdWorkflow(), idEmploye);

                c.commit();
                WorkflowRhCPL detail = WorkflowRhCPL.rechercherParId(workflow.getIdWorkflow(), c);
                return ResponseEntity.status(HttpStatus.CREATED).body(detail);

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

    /**
     * Logique de validation pour les transitions de carrière.
     */
    private void validerTransition(WorkflowRh workflow, ValiderWorkflowRequest req, Connection c) throws Exception {
        Date dateEffet = workflow.getDateEffet();
        Date today = new Date(System.currentTimeMillis());
        
        // Est-ce une date d'effet future ? (strictement supérieure à aujourd'hui)
        boolean estFutur = dateEffet != null && dateEffet.after(today);

        // N'appliquer immédiatement que si la date d'effet est AUJOURD'HUI ou PASSÉE
        if (!estFutur) {
            Date dateCloture = dateEffet != null
                    ? Date.valueOf(dateEffet.toLocalDate().minusDays(1))
                    : today;

            if (workflow.getIdContratCible() != null && !workflow.getIdContratCible().equals(workflow.getIdContratSource())) {
                if (workflow.getIdContratSource() != null) {
                    ContratTravail contratSrc = new ContratTravail().rechercherParId(workflow.getIdContratSource(), c);
                    if (contratSrc != null) {
                        contratSrc.cloturerContrat(dateCloture, null, "Clôturé suite à transition (" + workflow.getIdWorkflow() + ")", c);
                    }
                }
                ContratTravail contratCbl = new ContratTravail().rechercherParId(workflow.getIdContratCible(), c);
                if (contratCbl != null) {
                    if (workflow.getIdContratSource() != null) {
                        contratCbl.setIdContratPrecedent(workflow.getIdContratSource());
                    }
                    contratCbl.activer(c);
                }
            }

            if (workflow.getIdAffectationSource() != null) {
                EmployeAffectationCarriere affSrc = new EmployeAffectationCarriere().rechercherParId(workflow.getIdAffectationSource(), c);
                if (affSrc != null) {
                    affSrc.fermer(dateCloture, c);
                }
            }

            if (workflow.getIdAffectationCible() != null) {
                EmployeAffectationCarriere affCbl = new EmployeAffectationCarriere().rechercherParId(workflow.getIdAffectationCible(), c);
                if (affCbl != null) {
                    affCbl.activer(c);
                }
            }
        }

        // ─── Traitement des Événements Spécifiques Période d'Essai ──────────────────
        String sqlEvt = "SELECT code FROM ref_type_evenement_rh WHERE id_type_evenement_rh = ?";
        try (PreparedStatement psEvt = c.prepareStatement(sqlEvt)) {
            psEvt.setInt(1, workflow.getIdTypeEvenementRh());
            try (ResultSet rsEvt = psEvt.executeQuery()) {
                if (rsEvt.next()) {
                    String codeEvt = rsEvt.getString("code");
                    Integer idCt = workflow.getIdContratCible() != null ? workflow.getIdContratCible() : workflow.getIdContratSource();

                    if ("CONFIRMATION_ESSAI".equals(codeEvt) && idCt != null) {
                        String sqlUpdateEssai = "UPDATE contrat_periode_essai SET statut = 'CONFIRMEE', date_fin_reelle = ?, updated_at = CURRENT_TIMESTAMP " +
                                                "WHERE id_contrat = ? AND statut = 'EN_COURS'";
                        try (PreparedStatement psU = c.prepareStatement(sqlUpdateEssai)) {
                            psU.setDate(1, workflow.getDateEffet());
                            psU.setInt(2, idCt);
                            psU.executeUpdate();
                        }
                        String sqlUpdateCt = "UPDATE contrat_travail SET date_fin_essai = NULL, updated_at = CURRENT_TIMESTAMP WHERE id_contrat = ?";
                        try (PreparedStatement psUpCt = c.prepareStatement(sqlUpdateCt)) {
                            psUpCt.setInt(1, idCt);
                            psUpCt.executeUpdate();
                        }
                    } else if ("RENOUVELLEMENT_ESSAI".equals(codeEvt) && idCt != null) {
                        String sqlUpdateP1 = "UPDATE contrat_periode_essai SET statut = 'RENOUVELEE', updated_at = CURRENT_TIMESTAMP " +
                                             "WHERE id_contrat = ? AND statut = 'EN_COURS'";
                        try (PreparedStatement psU = c.prepareStatement(sqlUpdateP1)) {
                            psU.setInt(1, idCt);
                            psU.executeUpdate();
                        }
                        ContratTravail ct = new ContratTravail().rechercherParId(idCt, c);
                        if (ct != null) {
                            Date dateFinPrev = ct.getDateFinEssai() != null ? ct.getDateFinEssai() : ct.getDateDebut();
                            Date nouvelleFin = workflow.getDateEffet();
                            if (nouvelleFin == null || !nouvelleFin.after(dateFinPrev)) {
                                nouvelleFin = Date.valueOf(dateFinPrev.toLocalDate().plusMonths(1));
                            }
                            String sqlInsP2 = "INSERT INTO contrat_periode_essai (id_contrat, numero_periode, date_debut, date_fin_prevue, statut, commentaire) " +
                                             "VALUES (?, 2, ?, ?, 'EN_COURS', ?)";
                            try (PreparedStatement psIns = c.prepareStatement(sqlInsP2)) {
                                psIns.setInt(1, idCt);
                                psIns.setDate(2, dateFinPrev);
                                psIns.setDate(3, nouvelleFin);
                                psIns.setString(4, "Renouvellement d'essai (" + (workflow.getMotif() != null ? workflow.getMotif() : "Prolongation") + ")");
                                psIns.executeUpdate();
                            }
                            String sqlUpCt = "UPDATE contrat_travail SET date_fin_essai = ?, updated_at = CURRENT_TIMESTAMP WHERE id_contrat = ?";
                            try (PreparedStatement psUpCt = c.prepareStatement(sqlUpCt)) {
                                psUpCt.setDate(1, nouvelleFin);
                                psUpCt.setInt(2, idCt);
                                psUpCt.executeUpdate();
                            }
                        }
                    }
                }
            }
        }

        String commentaire = (req != null && req.getCommentaireValidation() != null)
                ? req.getCommentaireValidation() : null;
        workflow.valider(getCurrentUserId(), null, null, c);

        if (commentaire != null && !commentaire.isBlank()) {
            appendCommentaireWorkflow(workflow.getIdWorkflow(), commentaire, c);
        }
    }

    /**
     * Valide et applique un workflow RH de type RUPTURE / FIN DE CONTRAT.
     */
    private void validerRupture(WorkflowRh workflow, ValiderWorkflowRequest req, Connection c) throws Exception {
        Date dateEffet = workflow.getDateEffet() != null ? workflow.getDateEffet() : new Date(System.currentTimeMillis());
        Date today = new Date(System.currentTimeMillis());

        boolean estFutur = dateEffet.after(today);

        // N'appliquer la fermeture de contrat et la désactivation que si la date d'effet est AUJOURD'HUI ou PASSÉE.
        // Si c'est une date future, le workflow reste VALIDE et l'employé demeure ACTIF jusqu'à la date d'effet.
        if (!estFutur) {
            appliquerRuptureFormelle(workflow, dateEffet, c);
        }

        // Marquer le workflow comme VALIDE
        String commentaire = (req != null && req.getCommentaireValidation() != null) ? req.getCommentaireValidation() : null;
        workflow.valider(getCurrentUserId(), null, null, c);
        if (commentaire != null && !commentaire.isBlank()) {
            appendCommentaireWorkflow(workflow.getIdWorkflow(), commentaire, c);
        }
    }

    private void appliquerRuptureFormelle(WorkflowRh workflow, Date dateEffet, Connection c) throws Exception {
        Integer idStatutRompu = resolveStatutContrat("ROMPU", c);

        // 1. Clôturer / Rompre le contrat source
        if (workflow.getIdContratSource() != null) {
            ContratTravail src = new ContratTravail().rechercherParId(workflow.getIdContratSource(), c);
            if (src != null) {
                src.cloturerContrat(dateEffet, null, "Rupture / Fin de contrat : " + (workflow.getMotif() != null ? workflow.getMotif() : ""), c);
                try (PreparedStatement psStat = c.prepareStatement(
                        "UPDATE contrat_travail SET id_statut_contrat = ?, date_fin = ?, updated_at = CURRENT_TIMESTAMP WHERE id_contrat = ?")) {
                    psStat.setInt(1, idStatutRompu);
                    psStat.setDate(2, dateEffet);
                    psStat.setInt(3, workflow.getIdContratSource());
                    psStat.executeUpdate();
                }
            }
        }

        // 2. Clôturer l'affectation source
        if (workflow.getIdAffectationSource() != null) {
            EmployeAffectationCarriere srcAff = new EmployeAffectationCarriere().rechercherParId(workflow.getIdAffectationSource(), c);
            if (srcAff != null) {
                srcAff.fermer(dateEffet, c);
            }
        }

        // 3. Règle de Gestion : Vérifier s'il reste au moins un autre contrat ACTIF pour cet employé
        boolean aAutreContratActif = false;
        try (PreparedStatement psCheck = c.prepareStatement(
                "SELECT COUNT(*) FROM contrat_travail c " +
                "JOIN ref_statut_contrat s ON c.id_statut_contrat = s.id_statut_contrat " +
                "WHERE c.id_employe = ? AND s.code = 'ACTIF' AND c.id_contrat <> ?")) {
            psCheck.setInt(1, workflow.getIdEmploye());
            psCheck.setInt(2, workflow.getIdContratSource() != null ? workflow.getIdContratSource() : 0);
            try (ResultSet rsCheck = psCheck.executeQuery()) {
                if (rsCheck.next() && rsCheck.getInt(1) > 0) {
                    aAutreContratActif = true;
                }
            }
        }

        // 4. Si aucun autre contrat actif -> Passer l'employé au statut INACTIF (est_actif = FALSE)
        if (!aAutreContratActif) {
            try (PreparedStatement psEmp = c.prepareStatement(
                    "UPDATE employe SET est_actif = FALSE WHERE id_employe = ?")) {
                psEmp.setInt(1, workflow.getIdEmploye());
                psEmp.executeUpdate();
            }
        }
    }

    /**
     * Applique automatiquement tous les workflows validés dont la date d'effet est arrivée (date_effet <= CURRENT_DATE).
     */
    public static void autoAppliquerMouvementsEchus(Connection c) {
        try {
            boolean initialAutoCommit = c.getAutoCommit();
            String sql = "SELECT w.* FROM workflow_rh w " +
                         "JOIN ref_statut_workflow s ON w.id_statut_workflow = s.id_statut_workflow " +
                         "WHERE s.code = 'VALIDE' AND w.date_effet <= CURRENT_DATE " +
                         "AND w.id_affectation_cible IN (" +
                         "   SELECT id_affectation FROM employe_affectation_carriere " +
                         "   WHERE id_statut_affectation = (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'PLANIFIEE')" +
                         ")";
            try (var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
                while (rs.next()) {
                    c.setAutoCommit(false);
                    try {
                        WorkflowRh wf = new WorkflowRh();
                        wf.setIdWorkflow(rs.getInt("id_workflow"));
                        wf.setIdEmploye(rs.getInt("id_employe"));
                        wf.setIdContratSource(rs.getObject("id_contrat_source") != null ? rs.getInt("id_contrat_source") : null);
                        wf.setIdContratCible(rs.getObject("id_contrat_cible") != null ? rs.getInt("id_contrat_cible") : null);
                        wf.setIdAffectationSource(rs.getObject("id_affectation_source") != null ? rs.getInt("id_affectation_source") : null);
                        wf.setIdAffectationCible(rs.getObject("id_affectation_cible") != null ? rs.getInt("id_affectation_cible") : null);
                        wf.setDateEffet(rs.getDate("date_effet"));

                        Date dateEffet = wf.getDateEffet();
                        Date dateCloture = dateEffet != null ? Date.valueOf(dateEffet.toLocalDate().minusDays(1)) : new Date(System.currentTimeMillis());

                        if (wf.getIdContratCible() != null && !wf.getIdContratCible().equals(wf.getIdContratSource())) {
                            if (wf.getIdContratSource() != null) {
                                ContratTravail src = new ContratTravail().rechercherParId(wf.getIdContratSource(), c);
                                if (src != null) src.cloturerContrat(dateCloture, null, "Clôture auto à date d'effet", c);
                            }
                            ContratTravail cbl = new ContratTravail().rechercherParId(wf.getIdContratCible(), c);
                            if (cbl != null) cbl.activer(c);
                        }

                        if (wf.getIdAffectationSource() != null) {
                            EmployeAffectationCarriere src = new EmployeAffectationCarriere().rechercherParId(wf.getIdAffectationSource(), c);
                            if (src != null) src.fermer(dateCloture, c);
                        }

                        if (wf.getIdAffectationCible() != null) {
                            EmployeAffectationCarriere cbl = new EmployeAffectationCarriere().rechercherParId(wf.getIdAffectationCible(), c);
                            if (cbl != null) cbl.activer(c);
                        }

                        // Récupérer le nom de l'employé et le type d'événement pour la notification
                        String nomPrenom = "";
                        String typeEv = "Mouvement RH";
                        try (var ps2 = c.prepareStatement(
                                "SELECT e.nom || ' ' || e.prenom AS np, t.libelle AS ev " +
                                "FROM employe e " +
                                "JOIN workflow_rh w ON w.id_employe = e.id_employe " +
                                "JOIN ref_type_evenement_rh t ON w.id_type_evenement_rh = t.id_type_evenement_rh " +
                                "WHERE w.id_workflow = ?")) {
                            ps2.setInt(1, wf.getIdWorkflow());
                            try (var rs2 = ps2.executeQuery()) {
                                if (rs2.next()) {
                                    nomPrenom = rs2.getString("np");
                                    typeEv = rs2.getString("ev");
                                }
                            }
                        }

                        // Insérer la notification de prise d'effet
                        creerNotificationRh(c, null, "MOUVEMENT_APPLIQUE", "NORMALE",
                                "Prise d'effet - " + typeEv + " - " + nomPrenom,
                                "Le mouvement RH de type " + typeEv + " pour " + nomPrenom + " est désormais actif (sa date d'effet au " + (dateEffet != null ? dateEffet.toString() : "") + " a ete atteinte).",
                                "/employes/" + wf.getIdEmploye() + "?tab=carriere", wf.getIdEmploye());

                        c.commit();
                    } catch (Exception exWf) {
                        c.rollback();
                        System.err.println("Erreur autoAppliquerMouvement (id=" + rs.getInt("id_workflow") + ") : " + exWf.getMessage());
                    } finally {
                        c.setAutoCommit(initialAutoCommit);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur autoAppliquerMouvementsEchus : " + e.getMessage());
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

                String statutCode = resolveCodeStatutWorkflow(workflow.getIdStatutWorkflow(), c);
                if (!"BROUILLON".equals(statutCode)) {
                    return ResponseEntity.badRequest()
                            .body("Ce workflow est déjà en statut '" + statutCode + "'. Seul un workflow BROUILLON peut être annulé via cette route.");
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

                // Marquer les notifications de ce brouillon comme lues suite à l'annulation
                try (PreparedStatement psClear = c.prepareStatement(
                        "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                        "WHERE id_employe_concerne = ? AND lien LIKE ?")) {
                    psClear.setInt(1, workflow.getIdEmploye());
                    psClear.setString(2, "%wf=" + idWorkflow);
                    psClear.executeUpdate();
                }

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

    /**
     * Annuler un workflow planifié à date ultérieure (action DAF/RH).
     * PUT /workflows/{idWorkflow}/annuler-planification
     */
    @PutMapping("/workflows/{idWorkflow}/annuler-planification")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerWorkflowPlanifie(
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
                        ? req.getCommentaireValidation() : "Annulation de la planification RH";

                if (workflow.getIdContratCible() != null && !workflow.getIdContratCible().equals(workflow.getIdContratSource())) {
                    annulerContratBrouillon(workflow.getIdContratCible(), c);
                }

                if (workflow.getIdAffectationCible() != null) {
                    annulerAffectationPlanifiee(workflow.getIdAffectationCible(), c);
                }

                workflow.annulerPlanifie(getCurrentUserId(), commentaire, c);

                AuditLog.audit(c, "ANNULATION_PLANIFICATION_WORKFLOW", "workflow_rh",
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
                    .body("Erreur lors de l'annulation de la planification.");
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

    /** Récupère l'ID de statut contrat par son code (avec auto-insertion si manquant). */
    private Integer resolveStatutContrat(String code, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        try (var psIns = c.prepareStatement("INSERT INTO ref_statut_contrat (code, libelle) VALUES (?, ?) ON CONFLICT (code) DO NOTHING")) {
            psIns.setString(1, code);
            psIns.setString(2, code);
            psIns.executeUpdate();
        }
        try (var ps = c.prepareStatement("SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Statut contrat introuvable : " + code);
    }

    /** Récupère l'ID de statut affectation par son code (avec auto-insertion si manquant). */
    private Integer resolveStatutAffectation(String code, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        try (var psIns = c.prepareStatement("INSERT INTO ref_statut_affectation (code, libelle) VALUES (?, ?) ON CONFLICT (code) DO NOTHING")) {
            psIns.setString(1, code);
            psIns.setString(2, code);
            psIns.executeUpdate();
        }
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

    /** Récupère l'ID de type d'événement RH par son code (avec auto-insertion si manquant). */
    private Integer resolveTypeEvenement(String code, Connection c) throws Exception {
        if (code == null || code.isBlank()) code = "FIN_CONTRAT";
        try (var ps = c.prepareStatement("SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        // Auto-insertion du code s'il n'existe pas encore en BDD
        String libelle = switch (code) {
            case "DEMISSION" -> "Démission du salarié";
            case "LICENCIEMENT" -> "Licenciement du salarié";
            case "RUPTURE_CONVENTIONNELLE" -> "Rupture conventionnelle";
            case "DEPART_RETRAITE" -> "Départ à la retraite";
            case "RUPTURE_ESSAI" -> "Rupture de la période d'essai";
            case "FIN_CONTRAT" -> "Fin de contrat / Sortie de l'effectif";
            default -> code;
        };
        try (var psIns = c.prepareStatement(
                "INSERT INTO ref_type_evenement_rh (code, libelle) VALUES (?, ?) ON CONFLICT (code) DO NOTHING")) {
            psIns.setString(1, code);
            psIns.setString(2, libelle);
            psIns.executeUpdate();
        }
        try (var ps = c.prepareStatement("SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        // Repli ultime sur FIN_CONTRAT
        try (var ps = c.prepareStatement("SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'FIN_CONTRAT'")) {
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
        try (var ps = c.prepareStatement("SELECT id_motif_mouvement FROM ref_motif_mouvement ORDER BY id_motif_mouvement ASC LIMIT 1")) {
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
                "id_statut_contrat = COALESCE((SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ANNULE'), " +
                "(SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ROMPU')), " +
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

    /**
     * Enregistre une ligne dans contrat_periode_essai lors de la création d'un contrat avec période d'essai.
     */
    private static void enregistrerPeriodeEssaiInitiale(ContratTravail contrat, Connection c) throws Exception {
        String sql = "INSERT INTO contrat_periode_essai (id_contrat, numero_periode, date_debut, date_fin_prevue, statut, commentaire) " +
                     "VALUES (?, 1, ?, ?, 'EN_COURS', ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, contrat.getIdContrat());
            ps.setDate(2, contrat.getDateDebut());

            Date dateFin = contrat.getDateFinEssai();
            if (dateFin == null && contrat.getDateDebut() != null && contrat.getDureeEssaiMois() != null) {
                dateFin = Date.valueOf(contrat.getDateDebut().toLocalDate().plusMonths(contrat.getDureeEssaiMois()));
            }
            ps.setDate(3, dateFin != null ? dateFin : contrat.getDateDebut());
            ps.setString(4, "Période d'essai initiale (" + contrat.getDureeEssaiMois() + " mois)");
            ps.executeUpdate();
        }
    }

    /**
     * Insère une notification RH de manière réutilisable.
     */
    private static void creerNotificationRh(Connection c, Integer idUtilisateur, String typeAlerte, String priorite,
                                              String titre, String message, String lien, Integer idEmployeConcerne) throws Exception {
        String sql = "INSERT INTO notification_rh (id_utilisateur, type_alerte, priorite, titre, message, lien, id_employe_concerne) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (idUtilisateur != null) ps.setInt(1, idUtilisateur); else ps.setNull(1, java.sql.Types.INTEGER);
            ps.setString(2, typeAlerte);
            ps.setString(3, priorite);
            ps.setString(4, titre);
            ps.setString(5, message);
            ps.setString(6, lien);
            if (idEmployeConcerne != null) ps.setInt(7, idEmployeConcerne); else ps.setNull(7, java.sql.Types.INTEGER);
            ps.executeUpdate();
        }
    }
}
