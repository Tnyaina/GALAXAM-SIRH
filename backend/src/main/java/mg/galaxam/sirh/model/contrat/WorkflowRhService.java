package mg.galaxam.sirh.model.contrat;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import mg.galaxam.sirh.api.dto.contrat.EmbaucheInitialeRequest;
import mg.galaxam.sirh.api.dto.contrat.TransitionCarriereRequest;
import mg.galaxam.sirh.api.dto.contrat.ValiderWorkflowRequest;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.stereotype.Service;

/**
 * Service de gestion et d'orchestration des Workflows RH.
 * Centralise les règles de gestion transactionnelles pour l'embauche,
 * les transitions de carrière (mutation, promotion) et la rupture de contrat.
 */
@Service
public class WorkflowRhService {

    /**
     * ÉTAPE 1 — Créer un brouillon d'embauche initiale.
     */
    public WorkflowRhCPL creerBrouillonEmbauche(Integer idEmploye, EmbaucheInitialeRequest req, Integer currentUserId,
            Connection c) throws Exception {
        // ── Vérification : pas de contrat actif ou brouillon existant ───────────
        ContratTravail contratActif = ContratTravail.rechercherActifParEmploye(idEmploye, c);
        if (contratActif != null) {
            throw new IllegalArgumentException(
                    "L'employé possède déjà un contrat ACTIF (N° " + contratActif.getNumeroContrat() + "). " +
                            "Utilisez un workflow de transition (Mutation, Promotion, etc.).");
        }

        WorkflowRh brouillonExiste = WorkflowRh.rechercherBrouillonParEmploye(idEmploye, c);
        if (brouillonExiste != null) {
            throw new IllegalArgumentException("L'employé possède déjà un workflow BROUILLON en cours (ID "
                    + brouillonExiste.getIdWorkflow() + "). " +
                    "Veuillez valider, modifier ou annuler ce brouillon avant d'en créer un nouveau.");
        }

        // ── Résolution des statuts ────────────────────────────
        Integer idStatutBrouillon = resolveStatut("BROUILLON", c);
        Integer idStatutPlanifie = resolveStatut("PLANIFIE", c);
        String codeTypeEv = (req.getCodeTypeEvenement() != null && !req.getCodeTypeEvenement().isBlank())
                ? req.getCodeTypeEvenement()
                : "EMBAUCHE_INITIALE";
        Integer idTypeEvenementEmbauche = resolveTypeEvenement(codeTypeEv, c);
        Integer idMotifMouvement = resolveMotifMouvement("EMBAUCHE", c);

        validerExigenceDateFin(req.getIdTypeContrat(), req.getDateFin(), c);

        // ── 1. Création du Contrat BROUILLON ────────────────────────────────────
        ContratTravail contrat = new ContratTravail();
        contrat.setIdEmploye(idEmploye);
        contrat.setIdFiliale(req.getIdFiliale());
        contrat.setIdTypeContrat(req.getIdTypeContrat());
        contrat.setIdTypeIntervenant(req.getIdTypeIntervenant());
        contrat.setIdStatutRh(idStatutBrouillon);
        contrat.setIdTypeEvenementInitial(idTypeEvenementEmbauche);
        contrat.setNumeroContrat(req.getNumeroContrat());
        contrat.setDateSignature(req.getDateSignature());
        contrat.setDateDebut(req.getDateDebut());
        contrat.setDateFin(req.getDateFin());
        contrat.setDureeEssaiMois(req.getDureeEssaiMois());
        contrat.setDateFinEssai(req.getDateFinEssai());
        contrat.setComptabiliseEffectif(req.isComptabiliseEffectif());
        contrat.setDescription(req.getDescription());
        contrat.setCommentaire(req.getCommentaire());
        if (req.getIdContratPrecedent() != null) {
            contrat.setIdContratPrecedent(req.getIdContratPrecedent());
        }
        contrat.save(c);

        if (contrat.getDureeEssaiMois() != null && contrat.getDureeEssaiMois() > 0) {
            enregistrerPeriodeEssaiInitiale(contrat, c);
        }

        // ── 2. Création de l'Affectation PLANIFIE ──────────────────────────────
        EmployeAffectationCarriere affectation = new EmployeAffectationCarriere();
        affectation.setIdEmploye(idEmploye);
        affectation.setIdContrat(contrat.getIdContrat());
        affectation.setIdStatutRh(idStatutPlanifie);
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
        workflow.setIdStatutRh(idStatutBrouillon);
        workflow.setIdContratCible(contrat.getIdContrat());
        workflow.setIdAffectationCible(affectation.getIdAffectation());
        workflow.setDateEffet(dateEffet);
        workflow.setMotif(req.getMotif());
        workflow.setIdDemandeur(currentUserId);
        workflow.save(c);

        AuditLog.audit(c, "CREATION_BROUILLON_EMBAUCHE", "workflow_rh",
                String.valueOf(workflow.getIdWorkflow()), null, workflow);

        String empNomBrouillon = "";
        try (var psEmp = c.prepareStatement("SELECT nom || ' ' || prenom FROM employe WHERE id_employe = ?")) {
            psEmp.setInt(1, idEmploye);
            try (var rsEmp = psEmp.executeQuery()) {
                if (rsEmp.next())
                    empNomBrouillon = rsEmp.getString(1);
            }
        }
        creerNotificationRh(c, null, "BROUILLON_CREE", "HAUTE",
                "Nouveau brouillon d'embauche - " + empNomBrouillon,
                "Un brouillon d'embauche initiale pour " + empNomBrouillon + " a ete cree et attend votre validation.",
                "/employes/" + idEmploye + "?tab=carriere&wf=" + workflow.getIdWorkflow(), idEmploye);

        return WorkflowRhCPL.rechercherParId(workflow.getIdWorkflow(), c);
    }

    /**
     * ÉTAPE 2 — Valider un workflow BROUILLON.
     */
    public WorkflowRhCPL validerWorkflow(Integer idWorkflow, ValiderWorkflowRequest req, Integer currentUserId,
            Connection c) throws Exception {
        WorkflowRh workflow = new WorkflowRh().rechercherParId(idWorkflow, c);
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow introuvable (id=" + idWorkflow + ").");
        }

        String statutCode = resolveCodeStatutWorkflow(workflow.getIdStatutRh(), c);
        if (!"BROUILLON".equals(statutCode)) {
            throw new IllegalArgumentException("Ce workflow est déjà en statut '" + statutCode
                    + "'. Seul un workflow BROUILLON peut être validé.");
        }

        String typeEvenementCode = resolveCodeTypeEvenement(workflow.getIdTypeEvenementRh(), c);

        switch (typeEvenementCode) {
            case "EMBAUCHE_INITIALE" -> validerEmbaucheInitiale(workflow, req, currentUserId, c);
            case "RENOUVELLEMENT_CDD", "TRANSFORMATION_CDI", "EMBAUCHE_APRES_STAGE", "MUTATION", "PROMOTION",
                    "REVALORISATION", "CONFIRMATION_ESSAI", "RENOUVELLEMENT_ESSAI", "REPRISE_CONSULTANT" ->
                validerTransition(workflow, req, currentUserId, c);
            case "FIN_CONTRAT", "RUPTURE_ESSAI", "DEMISSION", "LICENCIEMENT", "RUPTURE_CONVENTIONNELLE",
                    "DEPART_RETRAITE" ->
                validerRupture(workflow, req, currentUserId, c);
            default -> throw new IllegalArgumentException(
                    "Type d'événement '" + typeEvenementCode + "' non encore implémenté pour la validation.");
        }

        AuditLog.audit(c, "VALIDATION_WORKFLOW", "workflow_rh",
                String.valueOf(idWorkflow), null, workflow);

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
                if (rsEmp.next())
                    empNomValide = rsEmp.getString(1);
            }
        }
        String typeEvNomValide = "Mouvement RH";
        try (var psEv = c
                .prepareStatement("SELECT libelle FROM ref_type_evenement_rh WHERE id_type_evenement_rh = ?")) {
            psEv.setInt(1, workflow.getIdTypeEvenementRh());
            try (var rsEv = psEv.executeQuery()) {
                if (rsEv.next())
                    typeEvNomValide = rsEv.getString(1);
            }
        }
        creerNotificationRh(c, null, "MOUVEMENT_APPLIQUE", "NORMALE",
                "Mouvement RH validé : " + typeEvNomValide + " - " + empNomValide,
                "Le mouvement RH (" + typeEvNomValide + ") pour " + empNomValide
                        + " a ete valide et confirme avec succes.",
                "/employes/" + workflow.getIdEmploye() + "?tab=carriere&wf=" + idWorkflow, workflow.getIdEmploye());

        return WorkflowRhCPL.rechercherParId(idWorkflow, c);
    }

    /**
     * Valider un brouillon d'embauche initiale.
     */
    private void validerEmbaucheInitiale(WorkflowRh workflow, ValiderWorkflowRequest req, Integer currentUserId,
            Connection c) throws Exception {
        if (workflow.getIdContratCible() != null) {
            ContratTravail contrat = new ContratTravail().rechercherParId(workflow.getIdContratCible(), c);
            if (contrat == null) {
                throw new IllegalStateException(
                        "Le contrat cible (id=" + workflow.getIdContratCible() + ") est introuvable.");
            }
            validerExigenceDateFin(contrat.getIdTypeContrat(), contrat.getDateFin(), c);

            // Clôturer tout ancien contrat actif restant pour cet employé pour éviter les
            // doublons
            try (PreparedStatement psCloseOld = c.prepareStatement(
                    "UPDATE contrat_travail SET id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'CLOTURE'), updated_at = CURRENT_TIMESTAMP WHERE id_employe = ? AND id_contrat <> ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF')")) {
                psCloseOld.setInt(1, workflow.getIdEmploye());
                psCloseOld.setInt(2, workflow.getIdContratCible());
                psCloseOld.executeUpdate();
            }

            contrat.activer(c);
        }

        if (workflow.getIdAffectationCible() != null) {
            EmployeAffectationCarriere affectation = new EmployeAffectationCarriere()
                    .rechercherParId(workflow.getIdAffectationCible(), c);
            if (affectation == null) {
                throw new IllegalStateException(
                        "L'affectation cible (id=" + workflow.getIdAffectationCible() + ") est introuvable.");
            }

            // Clôturer toute ancienne affectation active
            try (PreparedStatement psCloseOldAff = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'HISTORISE'), updated_at = CURRENT_TIMESTAMP WHERE id_employe = ? AND id_affectation <> ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF')")) {
                psCloseOldAff.setInt(1, workflow.getIdEmploye());
                psCloseOldAff.setInt(2, workflow.getIdAffectationCible());
                psCloseOldAff.executeUpdate();
            }

            affectation.activer(c);
        }

        // Réactiver automatiquement l'employé s'il était inactif
        try (PreparedStatement psEmp = c.prepareStatement(
                "UPDATE employe SET est_actif = TRUE, date_desactivation = NULL, motif_desactivation = NULL, updated_at = CURRENT_TIMESTAMP WHERE id_employe = ?")) {
            psEmp.setInt(1, workflow.getIdEmploye());
            psEmp.executeUpdate();
        }

        String commentaire = (req != null && req.getCommentaireValidation() != null)
                ? req.getCommentaireValidation()
                : null;
        workflow.valider(currentUserId, null, null, c);

        if (commentaire != null && !commentaire.isBlank()) {
            appendCommentaireWorkflow(workflow.getIdWorkflow(), commentaire, c);
        }
    }

    /**
     * ÉTAPE 1 (Transition) — Créer un brouillon de transition de carrière.
     */
    public WorkflowRhCPL creerBrouillonTransition(Integer idEmploye, TransitionCarriereRequest req,
            Integer currentUserId, Connection c) throws Exception {
        ContratTravail contratActif = ContratTravail.rechercherActifParEmploye(idEmploye, c);
        EmployeAffectationCarriere affectationActive = EmployeAffectationCarriere.rechercherActiveParEmploye(idEmploye,
                c);

        WorkflowRh brouillonExiste = WorkflowRh.rechercherBrouillonParEmploye(idEmploye, c);
        if (brouillonExiste != null) {
            throw new IllegalArgumentException("L'employé possède déjà un workflow BROUILLON en cours (ID "
                    + brouillonExiste.getIdWorkflow() + "). " +
                    "Veuillez valider, modifier ou annuler ce brouillon avant d'en créer un nouveau.");
        }

        Integer idTypeEvenement = resolveTypeEvenement(req.getCodeTypeEvenement(), c);

        boolean exigeNouveauContrat = List
                .of("TRANSFORMATION_CDI", "RENOUVELLEMENT_CDD", "EMBAUCHE_APRES_STAGE", "REPRISE_CONSULTANT")
                .contains(req.getCodeTypeEvenement());

        if (exigeNouveauContrat) {
            if (req.getIdTypeContrat() == null) {
                throw new IllegalArgumentException(
                        "Le type de contrat de destination est obligatoire pour cette transition.");
            }
            if (req.getIdTypeIntervenant() == null) {
                throw new IllegalArgumentException("Le type d'intervenant est obligatoire pour cette transition.");
            }

            String codeTypeContrat = "";
            try (var psTC = c.prepareStatement("SELECT code FROM ref_type_contrat WHERE id_type_contrat = ?")) {
                psTC.setInt(1, req.getIdTypeContrat());
                try (var rsTC = psTC.executeQuery()) {
                    if (rsTC.next())
                        codeTypeContrat = rsTC.getString("code");
                }
            }

            String codeTypeIntervenant = "";
            try (var psTI = c.prepareStatement("SELECT code FROM ref_type_intervenant WHERE id_type_intervenant = ?")) {
                psTI.setInt(1, req.getIdTypeIntervenant());
                try (var rsTI = psTI.executeQuery()) {
                    if (rsTI.next())
                        codeTypeIntervenant = rsTI.getString("code");
                }
            }

            if ("STAGIAIRE".equals(codeTypeIntervenant) && !"STAGE".equals(codeTypeContrat)) {
                throw new IllegalArgumentException(
                        "Un intervenant Stagiaire doit obligatoirement être sous un contrat de type Convention / Contrat de Stage.");
            }
            if (!"STAGIAIRE".equals(codeTypeIntervenant) && "STAGE".equals(codeTypeContrat)) {
                throw new IllegalArgumentException("Le contrat de Stage est réservé aux stagiaires.");
            }

            // ── Règles Spécifiques : RENOUVELLEMENT_CDD ──────────────────────────
            if ("RENOUVELLEMENT_CDD".equals(req.getCodeTypeEvenement())) {
                if (!"CDD".equals(codeTypeContrat)) {
                    throw new IllegalArgumentException("Le renouvellement de CDD requiert un contrat de type CDD.");
                }
                // Règle 1 : Période d'essai strictement interdite sur renouvellement CDD
                if (req.getDureeEssaiMois() != null && req.getDureeEssaiMois() > 0) {
                    throw new IllegalArgumentException(
                            "Une période d'essai est strictement interdite lors d'un renouvellement de CDD.");
                }

                // Règle 2 : Nombre maximal de renouvellements (2 maximum)
                int maxRenouvellements = resolveParametreGlobalInt("NB_MAX_RENOUVELLEMENTS_CDD", 2, c);
                int nbRenouvellementsExistants = compterRenouvellementsCDDPrecedents(
                        contratActif != null ? contratActif.getIdContrat() : req.getIdContratSource(), c);
                if (nbRenouvellementsExistants >= maxRenouvellements) {
                    throw new IllegalArgumentException(
                            "Le CDD a déjà atteint le nombre maximal de renouvellements autorisés ("
                                    + maxRenouvellements + "). Au-delà, le contrat doit être requalifié en CDI.");
                }

                // Règle 3 : Durée cumulée maximale (24 mois maximum)
                if (req.getDateDebut() == null || req.getDateFin() == null) {
                    throw new IllegalArgumentException(
                            "Les dates de début et de fin sont obligatoires pour le renouvellement du CDD.");
                }
                int maxMoisCumule = resolveParametreGlobalInt("DUREE_MAX_CDD_MOIS", 24, c);
                double dureeCumuleeMois = calculerDureeCumuleeCDDMois(
                        contratActif != null ? contratActif.getIdContrat() : req.getIdContratSource(),
                        req.getDateDebut(), req.getDateFin(), c);
                if (dureeCumuleeMois > maxMoisCumule) {
                    throw new IllegalArgumentException("La durée totale cumulée des CDD ("
                            + String.format("%.1f", dureeCumuleeMois) + " mois) dépasse le plafond légal de "
                            + maxMoisCumule + " mois (2 ans). Le contrat doit être requalifié en CDI.");
                }
            }
        }

        Integer idStatutBrouillon = resolveStatut("BROUILLON", c);
        Integer idStatutPlanifie = resolveStatut("PLANIFIE", c);

        String codeMotif = switch (req.getCodeTypeEvenement() != null ? req.getCodeTypeEvenement() : "") {
            case "PROMOTION" -> "PROMOTION";
            case "MUTATION" -> "MUTATION";
            case "REVALORISATION" -> "REVALORISATION";
            case "RENOUVELLEMENT_CDD", "TRANSFORMATION_CDI", "EMBAUCHE_APRES_STAGE", "REPRISE_CONSULTANT" -> "AVENANT";
            default -> "AUTRE";
        };
        Integer idMotifMouvement = resolveMotifMouvement(codeMotif, c);

        boolean estEvenementEssai = "CONFIRMATION_ESSAI".equals(req.getCodeTypeEvenement())
                || "RENOUVELLEMENT_ESSAI".equals(req.getCodeTypeEvenement());
        boolean estEvenementRupture = List.of("FIN_CONTRAT", "RUPTURE_ESSAI", "DEMISSION", "LICENCIEMENT",
                "RUPTURE_CONVENTIONNELLE", "DEPART_RETRAITE").contains(req.getCodeTypeEvenement());

        Integer idContratCibleId = contratActif != null ? contratActif.getIdContrat() : req.getIdContratSource();

        // ── Guard métier : CONFIRMATION_ESSAI ──────────────────────────────────
        // Bloquer si une confirmation a déjà été appliquée (statut ACTIF ou PLANIFIE)
        // pour ce contrat
        if ("CONFIRMATION_ESSAI".equals(req.getCodeTypeEvenement()) && idContratCibleId != null) {
            String sqlCheckConfirm = "SELECT COUNT(*) FROM workflow_rh w " +
                    "JOIN ref_statut_rh s ON w.id_statut_rh = s.id_statut_rh " +
                    "JOIN ref_type_evenement_rh ev ON w.id_type_evenement_rh = ev.id_type_evenement_rh " +
                    "WHERE ev.code = 'CONFIRMATION_ESSAI' " +
                    "AND s.code IN ('ACTIF','PLANIFIE') " +
                    "AND (w.id_contrat_cible = ? OR w.id_contrat_source = ?)";
            try (var psChk = c.prepareStatement(sqlCheckConfirm)) {
                psChk.setInt(1, idContratCibleId);
                psChk.setInt(2, idContratCibleId);
                try (var rsChk = psChk.executeQuery()) {
                    if (rsChk.next() && rsChk.getInt(1) > 0) {
                        throw new IllegalStateException(
                                "La période d'essai de ce contrat a déjà été confirmée (ou une confirmation est déjà planifiée). "
                                        +
                                        "Une double confirmation n'est pas autorisée.");
                    }
                }
            }
            // Vérifier aussi qu'il existe bien une période d'essai EN_COURS pour ce contrat
            String sqlChkPe = "SELECT COUNT(*) FROM contrat_periode_essai WHERE id_contrat = ? AND statut = 'EN_COURS'";
            try (var psPe = c.prepareStatement(sqlChkPe)) {
                psPe.setInt(1, idContratCibleId);
                try (var rsPe = psPe.executeQuery()) {
                    if (rsPe.next() && rsPe.getInt(1) == 0) {
                        throw new IllegalStateException(
                                "Aucune période d'essai EN_COURS trouvée pour ce contrat. " +
                                        "Impossible de confirmer une période déjà terminée ou inexistante.");
                    }
                }
            }
        }

        // ── Guard métier : RENOUVELLEMENT_ESSAI ───────────────────────────────
        // Bloquer si le nombre de périodes existantes >= MAX_RENOUVELLEMENTS_ESSAI
        if ("RENOUVELLEMENT_ESSAI".equals(req.getCodeTypeEvenement()) && idContratCibleId != null) {
            int maxRenouvellements = resolveParametreGlobalInt("MAX_RENOUVELLEMENTS_ESSAI", 1, c);
            // Compter les périodes de renouvellement déjà créées (numéro_periode > 1)
            String sqlCountRenew = "SELECT COUNT(*) FROM contrat_periode_essai WHERE id_contrat = ? AND numero_periode > 1";
            try (var psCount = c.prepareStatement(sqlCountRenew)) {
                psCount.setInt(1, idContratCibleId);
                try (var rsCount = psCount.executeQuery()) {
                    if (rsCount.next() && rsCount.getInt(1) >= maxRenouvellements) {
                        throw new IllegalArgumentException(
                                "Le nombre maximal de renouvellements de la période d'essai (" + maxRenouvellements
                                        + ") a déjà été atteint. " +
                                        "Pour modifier cette limite, ajustez le paramètre MAX_RENOUVELLEMENTS_ESSAI.");
                    }
                }
            }
            // Vérifier qu'il existe bien une période EN_COURS à renouveler
            String sqlChkPe = "SELECT COUNT(*) FROM contrat_periode_essai WHERE id_contrat = ? AND statut = 'EN_COURS'";
            try (var psPe = c.prepareStatement(sqlChkPe)) {
                psPe.setInt(1, idContratCibleId);
                try (var rsPe = psPe.executeQuery()) {
                    if (rsPe.next() && rsPe.getInt(1) == 0) {
                        throw new IllegalStateException(
                                "Aucune période d'essai EN_COURS trouvée pour ce contrat. " +
                                        "Impossible de renouveler une période déjà terminée, confirmée ou inexistante.");
                    }
                }
            }
            // Vérifier qu'aucune confirmation n'est déjà planifiée/active
            String sqlCheckConfirm = "SELECT COUNT(*) FROM workflow_rh w " +
                    "JOIN ref_statut_rh s ON w.id_statut_rh = s.id_statut_rh " +
                    "JOIN ref_type_evenement_rh ev ON w.id_type_evenement_rh = ev.id_type_evenement_rh " +
                    "WHERE ev.code = 'CONFIRMATION_ESSAI' " +
                    "AND s.code IN ('ACTIF','PLANIFIE') " +
                    "AND (w.id_contrat_cible = ? OR w.id_contrat_source = ?)";
            try (var psChk = c.prepareStatement(sqlCheckConfirm)) {
                psChk.setInt(1, idContratCibleId);
                psChk.setInt(2, idContratCibleId);
                try (var rsChk = psChk.executeQuery()) {
                    if (rsChk.next() && rsChk.getInt(1) > 0) {
                        throw new IllegalStateException(
                                "Une confirmation de la période d'essai est déjà planifiée ou appliquée pour ce contrat. "
                                        +
                                        "Impossible de renouveler.");
                    }
                }
            }
        }

        if (!estEvenementEssai && !estEvenementRupture && req.getIdTypeContrat() != null && (exigeNouveauContrat
                || contratActif == null || !req.getIdTypeContrat().equals(contratActif.getIdTypeContrat()))) {
            validerExigenceDateFin(req.getIdTypeContrat(), req.getDateFin(), c);
            ContratTravail contratCbl = new ContratTravail();
            contratCbl.setIdEmploye(idEmploye);
            contratCbl.setIdFiliale(req.getIdFiliale());
            contratCbl.setIdTypeContrat(req.getIdTypeContrat());
            contratCbl.setIdTypeIntervenant(req.getIdTypeIntervenant());
            contratCbl.setIdStatutRh(idStatutBrouillon);
            contratCbl.setIdTypeEvenementInitial(idTypeEvenement);
            contratCbl.setNumeroContrat(req.getNumeroContrat());
            contratCbl.setDateSignature(req.getDateSignature());
            contratCbl.setDateDebut(req.getDateDebut() != null ? req.getDateDebut() : req.getDateEffet());
            contratCbl.setDateFin(req.getDateFin());
            contratCbl.setDureeEssaiMois(req.getDureeEssaiMois());
            contratCbl.setDateFinEssai(req.getDateFinEssai());
            contratCbl.setComptabiliseEffectif(
                    req.getComptabiliseEffectif() != null ? req.getComptabiliseEffectif() : true);
            contratCbl.setDescription(req.getDescriptionContrat());
            contratCbl.setCommentaire(req.getCommentaireContrat());
            contratCbl.setIdContratPrecedent(
                    contratActif != null ? contratActif.getIdContrat() : req.getIdContratSource());
            contratCbl.save(c);

            if (contratCbl.getDureeEssaiMois() != null && contratCbl.getDureeEssaiMois() > 0) {
                enregistrerPeriodeEssaiInitiale(contratCbl, c);
            }

            idContratCibleId = contratCbl.getIdContrat();
        }

        Integer idAffectationCibleId = null;
        if (estEvenementEssai || estEvenementRupture) {
            idAffectationCibleId = affectationActive != null ? affectationActive.getIdAffectation()
                    : req.getIdAffectationSource();
            if (idAffectationCibleId == null && idContratCibleId != null) {
                try (var psAff = c.prepareStatement(
                        "SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = ? ORDER BY id_affectation DESC LIMIT 1")) {
                    psAff.setInt(1, idContratCibleId);
                    try (var rsAff = psAff.executeQuery()) {
                        if (rsAff.next())
                            idAffectationCibleId = rsAff.getInt(1);
                    }
                }
            }
            if (idAffectationCibleId == null) {
                try (var psAff = c.prepareStatement(
                        "SELECT id_affectation FROM employe_affectation_carriere WHERE id_employe = ? ORDER BY id_affectation DESC LIMIT 1")) {
                    psAff.setInt(1, idEmploye);
                    try (var rsAff = psAff.executeQuery()) {
                        if (rsAff.next())
                            idAffectationCibleId = rsAff.getInt(1);
                    }
                }
            }
        } else {
            EmployeAffectationCarriere affectationCbl = new EmployeAffectationCarriere();
            affectationCbl.setIdEmploye(idEmploye);
            affectationCbl.setIdContrat(idContratCibleId);
            affectationCbl.setIdStatutRh(idStatutPlanifie);
            affectationCbl.setIdMotifMouvement(idMotifMouvement);
            affectationCbl.setIdSite(req.getIdSite());
            affectationCbl.setIdDepartement(req.getIdDepartement());
            affectationCbl.setIdService(req.getIdService());
            affectationCbl.setIdFonction(req.getIdFonction());
            affectationCbl.setIdResponsableHierarchique(req.getIdResponsableHierarchique());
            affectationCbl.setIdCategorieProfessionnelle(req.getIdCategorieProfessionnelle());
            affectationCbl.setIdModeCalculPaie(req.getIdModeCalculPaie());
            affectationCbl.setSalaireBase(req.getSalaireBase());
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
        workflow.setIdStatutRh(idStatutBrouillon);
        workflow.setIdContratSource(contratActif != null ? contratActif.getIdContrat() : req.getIdContratSource());
        workflow.setIdContratCible(idContratCibleId);
        workflow.setIdAffectationSource(
                affectationActive != null ? affectationActive.getIdAffectation() : req.getIdAffectationSource());
        workflow.setIdAffectationCible(idAffectationCibleId);
        workflow.setDateDemande(new Date(System.currentTimeMillis()));
        workflow.setDateEffet(req.getDateEffet());
        workflow.setMotif(req.getMotif());
        workflow.setIdDemandeur(currentUserId);
        workflow.save(c);

        AuditLog.audit(c, "CREATION_WORKFLOW_TRANSITION", "workflow_rh",
                String.valueOf(workflow.getIdWorkflow()), null, workflow);

        String empNomTrans = "";
        try (var psEmp = c.prepareStatement("SELECT nom || ' ' || prenom FROM employe WHERE id_employe = ?")) {
            psEmp.setInt(1, idEmploye);
            try (var rsEmp = psEmp.executeQuery()) {
                if (rsEmp.next())
                    empNomTrans = rsEmp.getString(1);
            }
        }
        String typeEvNomTrans = "Transition";
        try (var psEv = c
                .prepareStatement("SELECT libelle FROM ref_type_evenement_rh WHERE id_type_evenement_rh = ?")) {
            psEv.setInt(1, idTypeEvenement);
            try (var rsEv = psEv.executeQuery()) {
                if (rsEv.next())
                    typeEvNomTrans = rsEv.getString(1);
            }
        }
        creerNotificationRh(c, null, "BROUILLON_CREE", "HAUTE",
                "Nouveau brouillon : " + typeEvNomTrans + " - " + empNomTrans,
                "Un brouillon de type " + typeEvNomTrans + " pour " + empNomTrans
                        + " a ete cree et attend votre validation.",
                "/employes/" + idEmploye + "?tab=carriere&wf=" + workflow.getIdWorkflow(), idEmploye);

        return WorkflowRhCPL.rechercherParId(workflow.getIdWorkflow(), c);
    }

    /**
     * Modification d'un brouillon de workflow.
     */
    public WorkflowRhCPL modifierBrouillonWorkflow(Integer idWorkflow, TransitionCarriereRequest req, Connection c)
            throws Exception {
        WorkflowRh workflow = new WorkflowRh().rechercherParId(idWorkflow, c);
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow introuvable.");
        }

        String statutCode = "";
        try (var psStat = c.prepareStatement("SELECT code FROM ref_statut_rh WHERE id_statut_rh = ?")) {
            psStat.setInt(1, workflow.getIdStatutRh());
            try (var rsStat = psStat.executeQuery()) {
                if (rsStat.next())
                    statutCode = rsStat.getString("code");
            }
        }

        if (!"BROUILLON".equals(statutCode)) {
            throw new IllegalArgumentException(
                    "Seul un workflow en statut BROUILLON peut être modifié. Statut actuel : " + statutCode);
        }

        if (workflow.getIdAffectationCible() != null && req.getIdSite() != null) {
            EmployeAffectationCarriere affectation = new EmployeAffectationCarriere()
                    .rechercherParId(workflow.getIdAffectationCible(), c);
            if (affectation != null) {
                if (req.getIdSite() != null)
                    affectation.setIdSite(req.getIdSite());
                if (req.getIdDepartement() != null)
                    affectation.setIdDepartement(req.getIdDepartement());
                if (req.getIdService() != null)
                    affectation.setIdService(req.getIdService());
                if (req.getIdFonction() != null)
                    affectation.setIdFonction(req.getIdFonction());
                affectation.setIdResponsableHierarchique(req.getIdResponsableHierarchique());
                if (req.getIdCategorieProfessionnelle() != null)
                    affectation.setIdCategorieProfessionnelle(req.getIdCategorieProfessionnelle());
                if (req.getIdModeCalculPaie() != null)
                    affectation.setIdModeCalculPaie(req.getIdModeCalculPaie());
                if (req.getSalaireBase() != null)
                    affectation.setSalaireBase(req.getSalaireBase());
                if (req.getDateEffet() != null)
                    affectation.setDateDebut(req.getDateEffet());
                if (req.getDescriptionAffectation() != null)
                    affectation.setDescription(req.getDescriptionAffectation());
                if (req.getCommentaireAffectation() != null)
                    affectation.setCommentaire(req.getCommentaireAffectation());
                affectation.update(c);
            }
        }

        if (workflow.getIdContratCible() != null && req.getIdTypeContrat() != null) {
            ContratTravail contrat = new ContratTravail().rechercherParId(workflow.getIdContratCible(), c);
            if (contrat != null) {
                Integer targetTypeId = req.getIdTypeContrat() != null ? req.getIdTypeContrat()
                        : contrat.getIdTypeContrat();
                Date targetDateFin = req.getDateFin() != null ? req.getDateFin() : contrat.getDateFin();
                validerExigenceDateFin(targetTypeId, targetDateFin, c);

                if (req.getIdFiliale() != null)
                    contrat.setIdFiliale(req.getIdFiliale());
                if (req.getIdTypeContrat() != null)
                    contrat.setIdTypeContrat(req.getIdTypeContrat());
                if (req.getIdTypeIntervenant() != null)
                    contrat.setIdTypeIntervenant(req.getIdTypeIntervenant());
                if (req.getDateDebut() != null)
                    contrat.setDateDebut(req.getDateDebut());
                if (req.getDateFin() != null)
                    contrat.setDateFin(req.getDateFin());
                if (req.getDateSignature() != null)
                    contrat.setDateSignature(req.getDateSignature());
                if (req.getDureeEssaiMois() != null)
                    contrat.setDureeEssaiMois(req.getDureeEssaiMois());
                if (req.getDateFinEssai() != null)
                    contrat.setDateFinEssai(req.getDateFinEssai());
                if (req.getComptabiliseEffectif() != null)
                    contrat.setComptabiliseEffectif(req.getComptabiliseEffectif());
                if (req.getDescriptionContrat() != null)
                    contrat.setDescription(req.getDescriptionContrat());
                if (req.getCommentaireContrat() != null)
                    contrat.setCommentaire(req.getCommentaireContrat());
                if (req.getNumeroContrat() != null)
                    contrat.setNumeroContrat(req.getNumeroContrat());
                contrat.update(c);
            }
        }

        if (req.getCodeTypeEvenement() != null) {
            Integer idTypeEvt = resolveTypeEvenement(req.getCodeTypeEvenement(), c);
            if (idTypeEvt != null)
                workflow.setIdTypeEvenementRh(idTypeEvt);
        }
        Date dateEffetToSet = req.getDateEffet() != null ? req.getDateEffet() : req.getDateDebut();
        if (dateEffetToSet != null)
            workflow.setDateEffet(dateEffetToSet);
        if (req.getMotif() != null)
            workflow.setMotif(req.getMotif());
        workflow.update(c);

        AuditLog.audit(c, "MODIFICATION_BROUILLON_WORKFLOW", "workflow_rh",
                String.valueOf(idWorkflow), null, workflow);

        return WorkflowRhCPL.rechercherParId(idWorkflow, c);
    }

    /**
     * Applique l'effet fonctionnel réel d'un workflow RH
     * (confirmation/renouvellement d'essai, rupture ou transition).
     */
    private static void appliquerWorkflowEvenement(WorkflowRh workflow, Connection c) throws Exception {
        String typeEvenementCode = resolveCodeTypeEvenement(workflow.getIdTypeEvenementRh(), c);
        Integer idCt = workflow.getIdContratCible() != null ? workflow.getIdContratCible()
                : workflow.getIdContratSource();

        if ("CONFIRMATION_ESSAI".equals(typeEvenementCode) && idCt != null) {
            String sqlUpdateEssai = "UPDATE contrat_periode_essai SET statut = 'CONFIRMEE', date_fin_reelle = ?, updated_at = CURRENT_TIMESTAMP "
                    +
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
        } else if ("RENOUVELLEMENT_ESSAI".equals(typeEvenementCode) && idCt != null) {
            // Clore la période EN_COURS actuelle
            String sqlUpdatePCurrent = "UPDATE contrat_periode_essai SET statut = 'RENOUVELEE', updated_at = CURRENT_TIMESTAMP "
                    +
                    "WHERE id_contrat = ? AND statut = 'EN_COURS'";
            try (PreparedStatement psU = c.prepareStatement(sqlUpdatePCurrent)) {
                psU.setInt(1, idCt);
                int rows = psU.executeUpdate();
                if (rows == 0) {
                    throw new IllegalStateException(
                            "Aucune période d'essai EN_COURS n'a été trouvée au moment de l'application réelle. Mouvement annulé.");
                }
            }
            // Calculer le numéro de la prochaine période (dynamique, pas hardcodé à 2)
            int prochainNumero = 2;
            try (var psMaxNum = c.prepareStatement(
                    "SELECT COALESCE(MAX(numero_periode), 1) + 1 FROM contrat_periode_essai WHERE id_contrat = ?")) {
                psMaxNum.setInt(1, idCt);
                try (var rsMaxNum = psMaxNum.executeQuery()) {
                    if (rsMaxNum.next())
                        prochainNumero = rsMaxNum.getInt(1);
                }
            }
            ContratTravail ct = new ContratTravail().rechercherParId(idCt, c);
            if (ct != null) {
                Date dateFinPrev = ct.getDateFinEssai() != null ? ct.getDateFinEssai() : ct.getDateDebut();
                Date nouvelleFin = workflow.getDateEffet();
                if (nouvelleFin == null || !nouvelleFin.after(dateFinPrev)) {
                    nouvelleFin = Date.valueOf(dateFinPrev.toLocalDate().plusMonths(1));
                }
                // Vérifier que la période suivante n'existe pas déjà (idempotence)
                boolean nextPeriodExists = false;
                try (var psChkNext = c.prepareStatement(
                        "SELECT 1 FROM contrat_periode_essai WHERE id_contrat = ? AND numero_periode = ?")) {
                    psChkNext.setInt(1, idCt);
                    psChkNext.setInt(2, prochainNumero);
                    try (var rsChkNext = psChkNext.executeQuery()) {
                        if (rsChkNext.next())
                            nextPeriodExists = true;
                    }
                }
                if (!nextPeriodExists) {
                    String sqlInsNext = "INSERT INTO contrat_periode_essai (id_contrat, numero_periode, date_debut, date_fin_prevue, statut, commentaire) "
                            +
                            "VALUES (?, ?, ?, ?, 'EN_COURS', ?)";
                    try (PreparedStatement psIns = c.prepareStatement(sqlInsNext)) {
                        psIns.setInt(1, idCt);
                        psIns.setInt(2, prochainNumero);
                        psIns.setDate(3, dateFinPrev);
                        psIns.setDate(4, nouvelleFin);
                        psIns.setString(5, "Renouvellement d'essai N°" + (prochainNumero - 1) +
                                " (" + (workflow.getMotif() != null ? workflow.getMotif() : "Prolongation") + ")");
                        psIns.executeUpdate();
                    }
                }
                // Mettre à jour la date de fin d'essai sur le contrat
                String sqlUpCt = "UPDATE contrat_travail SET date_fin_essai = ?, updated_at = CURRENT_TIMESTAMP WHERE id_contrat = ?";
                try (PreparedStatement psUpCt = c.prepareStatement(sqlUpCt)) {
                    psUpCt.setDate(1, nouvelleFin);
                    psUpCt.setInt(2, idCt);
                    psUpCt.executeUpdate();
                }
            }
        } else if (List.of("FIN_CONTRAT", "RUPTURE_ESSAI", "DEMISSION", "LICENCIEMENT", "RUPTURE_CONVENTIONNELLE",
                "DEPART_RETRAITE").contains(typeEvenementCode)) {
            Date dateEffetRupture = workflow.getDateEffet() != null ? workflow.getDateEffet()
                    : new Date(System.currentTimeMillis());
            appliquerRuptureFormelle(workflow, dateEffetRupture, c);
        } else {
            appliquerTransitionDirecte(workflow, c);
        }
    }

    /**
     * Valide et applique une transition de carrière.
     */
    private void validerTransition(WorkflowRh workflow, ValiderWorkflowRequest req, Integer currentUserId, Connection c)
            throws Exception {
        Date dateEffet = workflow.getDateEffet();
        Date today = new Date(System.currentTimeMillis());
        boolean estFutur = dateEffet != null && dateEffet.after(today);

        if (!estFutur) {
            appliquerWorkflowEvenement(workflow, c);
        } else {
            // Date future : le contrat passe A_SIGNER si c'est un nouveau contrat
            if (workflow.getIdContratCible() != null
                    && !workflow.getIdContratCible().equals(workflow.getIdContratSource())) {
                Integer idStatutASigner = resolveStatut("A_SIGNER", c);
                try (PreparedStatement psUp = c.prepareStatement(
                        "UPDATE contrat_travail SET id_statut_rh = ? WHERE id_contrat = ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'BROUILLON')")) {
                    psUp.setInt(1, idStatutASigner);
                    psUp.setInt(2, workflow.getIdContratCible());
                    psUp.executeUpdate();
                }
            }
            // Le workflow passe explicitement PLANIFIE (date future confirmed)
            Integer idStatutPlanifie = resolveStatut("PLANIFIE", c);
            try (PreparedStatement psWf = c.prepareStatement(
                    "UPDATE workflow_rh SET id_statut_rh = ?, id_valideur = ?, updated_at = CURRENT_TIMESTAMP WHERE id_workflow = ?")) {
                psWf.setInt(1, idStatutPlanifie);
                if (currentUserId != null)
                    psWf.setInt(2, currentUserId);
                else
                    psWf.setNull(2, java.sql.Types.INTEGER);
                psWf.setInt(3, workflow.getIdWorkflow());
                psWf.executeUpdate();
            }
            String commentaire = (req != null && req.getCommentaireValidation() != null)
                    ? req.getCommentaireValidation()
                    : null;
            if (commentaire != null && !commentaire.isBlank()) {
                appendCommentaireWorkflow(workflow.getIdWorkflow(), commentaire, c);
            }
            return; // sortie anticipée — pas d'appel à workflow.valider() générique
        }

        String commentaire = (req != null && req.getCommentaireValidation() != null)
                ? req.getCommentaireValidation()
                : null;
        workflow.valider(currentUserId, null, null, c);

        if (commentaire != null && !commentaire.isBlank()) {
            appendCommentaireWorkflow(workflow.getIdWorkflow(), commentaire, c);
        }
    }

    /**
     * Valide et applique une rupture de contrat.
     * Si la date d'effet est dans le futur → le workflow passe en PLANIFIÉ (le cron
     * l'appliquera).
     * Si la date d'effet est présente ou passée → application immédiate.
     */
    private void validerRupture(WorkflowRh workflow, ValiderWorkflowRequest req, Integer currentUserId, Connection c)
            throws Exception {
        Date dateEffet = workflow.getDateEffet() != null ? workflow.getDateEffet()
                : new Date(System.currentTimeMillis());
        Date today = new Date(System.currentTimeMillis());
        boolean estFutur = dateEffet.after(today);

        String commentaire = (req != null && req.getCommentaireValidation() != null) ? req.getCommentaireValidation()
                : null;

        if (estFutur) {
            // Date future : le workflow passe en PLANIFIÉ, sera appliqué par le cron à la
            // date d'effet
            Integer idStatutPlanifie = resolveStatut("PLANIFIE", c);
            try (PreparedStatement psWf = c.prepareStatement(
                    "UPDATE workflow_rh SET id_statut_rh = ?, id_valideur = ?, updated_at = CURRENT_TIMESTAMP WHERE id_workflow = ?")) {
                psWf.setInt(1, idStatutPlanifie);
                if (currentUserId != null)
                    psWf.setInt(2, currentUserId);
                else
                    psWf.setNull(2, java.sql.Types.INTEGER);
                psWf.setInt(3, workflow.getIdWorkflow());
                psWf.executeUpdate();
            }
            if (commentaire != null && !commentaire.isBlank()) {
                appendCommentaireWorkflow(workflow.getIdWorkflow(), commentaire, c);
            }
            return; // sortie anticipée — pas d'application immédiate
        }

        // Date passée ou aujourd'hui : application immédiate
        appliquerWorkflowEvenement(workflow, c);
        workflow.valider(currentUserId, null, null, c);
        if (commentaire != null && !commentaire.isBlank()) {
            appendCommentaireWorkflow(workflow.getIdWorkflow(), commentaire, c);
        }
    }

    private static void appliquerRuptureFormelle(WorkflowRh workflow, Date dateEffet, Connection c) throws Exception {
        Integer idStatutRompu = resolveStatut("ROMPU", c);

        if (workflow.getIdContratSource() != null) {
            ContratTravail src = new ContratTravail().rechercherParId(workflow.getIdContratSource(), c);
            if (src != null) {
                src.cloturerContrat(dateEffet, null,
                        "Rupture / Fin de contrat : " + (workflow.getMotif() != null ? workflow.getMotif() : ""), c);
                try (PreparedStatement psStat = c.prepareStatement(
                        "UPDATE contrat_travail SET id_statut_rh = ?, date_fin = ?, updated_at = CURRENT_TIMESTAMP WHERE id_contrat = ?")) {
                    psStat.setInt(1, idStatutRompu);
                    psStat.setDate(2, dateEffet);
                    psStat.setInt(3, workflow.getIdContratSource());
                    psStat.executeUpdate();
                }
            }
        }

        if (workflow.getIdAffectationSource() != null) {
            EmployeAffectationCarriere srcAff = new EmployeAffectationCarriere()
                    .rechercherParId(workflow.getIdAffectationSource(), c);
            if (srcAff != null) {
                srcAff.fermer(dateEffet, c);
            }
        }

        boolean aAutreContratActif = false;
        try (PreparedStatement psCheck = c.prepareStatement(
                "SELECT COUNT(*) FROM contrat_travail c " +
                        "JOIN ref_statut_rh s ON c.id_statut_rh = s.id_statut_rh " +
                        "WHERE c.id_employe = ? AND s.code = 'ACTIF' AND c.id_contrat <> ?")) {
            psCheck.setInt(1, workflow.getIdEmploye());
            psCheck.setInt(2, workflow.getIdContratSource() != null ? workflow.getIdContratSource() : 0);
            try (ResultSet rsCheck = psCheck.executeQuery()) {
                if (rsCheck.next() && rsCheck.getInt(1) > 0) {
                    aAutreContratActif = true;
                }
            }
        }

        if (!aAutreContratActif) {
            try (PreparedStatement psEmp = c.prepareStatement(
                    "UPDATE employe SET est_actif = FALSE WHERE id_employe = ?")) {
                psEmp.setInt(1, workflow.getIdEmploye());
                psEmp.executeUpdate();
            }
        }
    }

    /**
     * Annule un workflow BROUILLON.
     */
    public WorkflowRhCPL annulerWorkflow(Integer idWorkflow, ValiderWorkflowRequest req, Integer currentUserId,
            Connection c) throws Exception {
        WorkflowRh workflow = new WorkflowRh().rechercherParId(idWorkflow, c);
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow introuvable.");
        }

        String statutCode = resolveCodeStatutWorkflow(workflow.getIdStatutRh(), c);
        if (!"BROUILLON".equals(statutCode)) {
            throw new IllegalArgumentException("Ce workflow est déjà en statut '" + statutCode
                    + "'. Seul un workflow BROUILLON peut être annulé via cette route.");
        }

        String commentaire = (req != null && req.getCommentaireValidation() != null)
                ? req.getCommentaireValidation()
                : "Annulé sans motif";

        if (workflow.getIdContratCible() != null) {
            annulerContratBrouillon(workflow.getIdContratCible(), c);
        }

        if (workflow.getIdAffectationCible() != null) {
            annulerAffectationPlanifiee(workflow.getIdAffectationCible(), c);
        }

        workflow.annuler(currentUserId, commentaire, c);

        AuditLog.audit(c, "ANNULATION_WORKFLOW", "workflow_rh",
                String.valueOf(idWorkflow), workflow, null);

        try (PreparedStatement psClear = c.prepareStatement(
                "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                        "WHERE id_employe_concerne = ? AND lien LIKE ?")) {
            psClear.setInt(1, workflow.getIdEmploye());
            psClear.setString(2, "%wf=" + idWorkflow);
            psClear.executeUpdate();
        }

        String empNomAnnule = "";
        try (var psEmp = c.prepareStatement("SELECT nom || ' ' || prenom FROM employe WHERE id_employe = ?")) {
            psEmp.setInt(1, workflow.getIdEmploye());
            try (var rsEmp = psEmp.executeQuery()) {
                if (rsEmp.next())
                    empNomAnnule = rsEmp.getString(1);
            }
        }

        creerNotificationRh(c, null, "MOUVEMENT_APPLIQUE", "NORMALE",
                "Brouillon de mouvement rejeté - " + empNomAnnule,
                "Le brouillon de mouvement pour " + empNomAnnule
                        + " a été rejeté ou annulé.",
                "/employes/" + workflow.getIdEmploye() + "?tab=carriere", workflow.getIdEmploye());

        return WorkflowRhCPL.rechercherParId(idWorkflow, c);
    }

    /**
     * Annule la planification d'un workflow validé à date ultérieure.
     */
    public WorkflowRhCPL annulerWorkflowPlanifie(Integer idWorkflow, ValiderWorkflowRequest req, Integer currentUserId,
            Connection c) throws Exception {
        WorkflowRh workflow = new WorkflowRh().rechercherParId(idWorkflow, c);
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow introuvable.");
        }

        String commentaire = (req != null && req.getCommentaireValidation() != null)
                ? req.getCommentaireValidation()
                : "Annulation de la planification RH";

        if (workflow.getIdContratCible() != null
                && !workflow.getIdContratCible().equals(workflow.getIdContratSource())) {
            annulerContratBrouillon(workflow.getIdContratCible(), c);
        }

        if (workflow.getIdAffectationCible() != null) {
            annulerAffectationPlanifiee(workflow.getIdAffectationCible(), c);
        }

        workflow.annulerPlanifie(currentUserId, commentaire, c);

        AuditLog.audit(c, "ANNULATION_PLANIFICATION_WORKFLOW", "workflow_rh",
                String.valueOf(idWorkflow), workflow, null);

        String empNomPlanif = "";
        try (var psEmp = c.prepareStatement("SELECT nom || ' ' || prenom FROM employe WHERE id_employe = ?")) {
            psEmp.setInt(1, workflow.getIdEmploye());
            try (var rsEmp = psEmp.executeQuery()) {
                if (rsEmp.next())
                    empNomPlanif = rsEmp.getString(1);
            }
        }

        creerNotificationRh(c, null, "MOUVEMENT_APPLIQUE", "HAUTE",
                "Planification de mouvement annulée - " + empNomPlanif,
                "Le mouvement RH planifié pour " + empNomPlanif + " a été annulé.",
                "/employes/" + workflow.getIdEmploye() + "?tab=carriere", workflow.getIdEmploye());

        return WorkflowRhCPL.rechercherParId(idWorkflow, c);
    }

    /**
     * Applique directement les modifications de contrats et d'affectations liées à
     * une transition.
     */
    private static void appliquerTransitionDirecte(WorkflowRh workflow, Connection c) throws Exception {
        Date dateEffet = workflow.getDateEffet();
        Date today = new Date(System.currentTimeMillis());
        // Calcul standard de J-1
        Date dateCloture = dateEffet != null
                ? Date.valueOf(dateEffet.toLocalDate().minusDays(1))
                : today;

        if (workflow.getIdContratCible() != null
                && !workflow.getIdContratCible().equals(workflow.getIdContratSource())) {
            if (workflow.getIdContratSource() != null) {
                ContratTravail contratSrc = new ContratTravail().rechercherParId(workflow.getIdContratSource(), c);
                if (contratSrc != null) {
                    // PROTECTION CAS 2 : On empêche la date_fin d'être antérieure à date_debut
                    Date dateFinCtSecurisee = dateCloture;
                    if (contratSrc.getDateDebut() != null && dateCloture.before(contratSrc.getDateDebut())) {
                        dateFinCtSecurisee = contratSrc.getDateDebut();
                    }

                    // Synchroniser la date_fin du contrat source UNIQUEMENT s'il est rompu avant
                    // son terme (Correction Bug 1 intégrée)
                    try (PreparedStatement psSyncDate = c.prepareStatement(
                            "UPDATE contrat_travail SET date_fin = ?, updated_at = CURRENT_TIMESTAMP " +
                                    "WHERE id_contrat = ? AND (date_fin IS NULL OR date_fin > ?)")) {
                        psSyncDate.setDate(1, dateFinCtSecurisee);
                        psSyncDate.setInt(2, workflow.getIdContratSource());
                        psSyncDate.setDate(3, dateFinCtSecurisee);
                        psSyncDate.executeUpdate();
                    }
                    contratSrc.cloturerContrat(dateFinCtSecurisee, null,
                            "Clôturé suite à transition (" + workflow.getIdWorkflow() + ")", c);
                }
            }
            ContratTravail contratCbl = new ContratTravail().rechercherParId(workflow.getIdContratCible(), c);
            if (contratCbl != null) {
                if (workflow.getIdContratSource() != null) {
                    contratCbl.setIdContratPrecedent(workflow.getIdContratSource());
                }
                validerExigenceDateFin(contratCbl.getIdTypeContrat(), contratCbl.getDateFin(), c);
                contratCbl.activer(c);
            }
        }

        if (workflow.getIdAffectationSource() != null) {
            EmployeAffectationCarriere affSrc = new EmployeAffectationCarriere()
                    .rechercherParId(workflow.getIdAffectationSource(), c);
            if (affSrc != null) {
                // PROTECTION CAS 2 : Même chose pour l'affectation
                Date dateFinAffSecurisee = dateCloture;
                if (affSrc.getDateDebut() != null && dateCloture.before(affSrc.getDateDebut())) {
                    dateFinAffSecurisee = affSrc.getDateDebut();
                }
                affSrc.fermer(dateFinAffSecurisee, c);
            }
        }

        if (workflow.getIdAffectationCible() != null) {
            EmployeAffectationCarriere affCbl = new EmployeAffectationCarriere()
                    .rechercherParId(workflow.getIdAffectationCible(), c);
            if (affCbl != null) {
                affCbl.activer(c);
            }
        }

        // Réactiver automatiquement l'employé s'il était inactif (ex: Reprise Consultant, Réintégration, etc.)
        try (PreparedStatement psEmp = c.prepareStatement(
                "UPDATE employe SET est_actif = TRUE, date_desactivation = NULL, motif_desactivation = NULL, updated_at = CURRENT_TIMESTAMP WHERE id_employe = ?")) {
            psEmp.setInt(1, workflow.getIdEmploye());
            psEmp.executeUpdate();
        }
    }

    /**
     * Applique automatiquement tous les workflows validés dont la date d'effet est
     * arrivée.
     */
    public static void autoAppliquerMouvementsEchus(Connection c) {
        try {
            boolean initialAutoCommit = c.getAutoCommit();
            String sql = "SELECT w.* FROM workflow_rh w " +
                    "JOIN ref_statut_rh s ON w.id_statut_rh = s.id_statut_rh " +
                    "JOIN ref_type_evenement_rh ev ON w.id_type_evenement_rh = ev.id_type_evenement_rh " +
                    "WHERE s.code = 'PLANIFIE' AND w.date_effet <= CURRENT_DATE " +
                    "AND (" +
                    "   (ev.code = 'CONFIRMATION_ESSAI' AND EXISTS (" +
                    "       SELECT 1 FROM contrat_periode_essai pe " +
                    "       WHERE pe.id_contrat = COALESCE(w.id_contrat_cible, w.id_contrat_source) AND pe.statut = 'EN_COURS'"
                    +
                    "   )) OR " +
                    "   (ev.code = 'RENOUVELLEMENT_ESSAI' AND EXISTS (" +
                    "       SELECT 1 FROM contrat_periode_essai pe " +
                    "       WHERE pe.id_contrat = COALESCE(w.id_contrat_cible, w.id_contrat_source) AND pe.numero_periode = 1 AND pe.statut = 'EN_COURS'"
                    +
                    "   )) OR " +
                    "   (ev.code IN ('FIN_CONTRAT', 'RUPTURE_ESSAI', 'DEMISSION', 'LICENCIEMENT', 'RUPTURE_CONVENTIONNELLE', 'DEPART_RETRAITE') AND EXISTS ("
                    +
                    "       SELECT 1 FROM contrat_travail ct " +
                    "       JOIN ref_statut_rh sc ON ct.id_statut_rh = sc.id_statut_rh " +
                    "       WHERE ct.id_contrat = w.id_contrat_source AND sc.code = 'ACTIF'" +
                    "   )) OR " +
                    "   (w.id_affectation_cible IN (" +
                    "       SELECT id_affectation FROM employe_affectation_carriere " +
                    "       WHERE id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'PLANIFIE')" +
                    "   ) OR w.id_contrat_cible IN (" +
                    "       SELECT id_contrat FROM contrat_travail " +
                    "       WHERE id_statut_rh != (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF')" +
                    "   ))" +
                    ")";
            try (var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
                while (rs.next()) {
                    c.setAutoCommit(false);
                    try {
                        WorkflowRh wf = new WorkflowRh();
                        wf.setIdWorkflow(rs.getInt("id_workflow"));
                        wf.setIdEmploye(rs.getInt("id_employe"));
                        wf.setIdTypeEvenementRh(rs.getInt("id_type_evenement_rh"));
                        wf.setIdContratSource(
                                rs.getObject("id_contrat_source") != null ? rs.getInt("id_contrat_source") : null);
                        wf.setIdContratCible(
                                rs.getObject("id_contrat_cible") != null ? rs.getInt("id_contrat_cible") : null);
                        wf.setIdAffectationSource(
                                rs.getObject("id_affectation_source") != null ? rs.getInt("id_affectation_source")
                                        : null);
                        wf.setIdAffectationCible(
                                rs.getObject("id_affectation_cible") != null ? rs.getInt("id_affectation_cible")
                                        : null);
                        wf.setDateEffet(rs.getDate("date_effet"));
                        wf.setMotif(rs.getString("motif"));

                        Date dateEffet = wf.getDateEffet();

                        // 1. Appliquer l'effet réel du workflow
                        appliquerWorkflowEvenement(wf, c);

                        // 2. Mettre à jour le statut du workflow_rh vers ACTIF
                        Integer idStatutActif = resolveStatut("ACTIF", c);
                        try (PreparedStatement psUpWf = c.prepareStatement(
                                "UPDATE workflow_rh SET id_statut_rh = ?, updated_at = CURRENT_TIMESTAMP WHERE id_workflow = ?")) {
                            psUpWf.setInt(1, idStatutActif);
                            psUpWf.setInt(2, wf.getIdWorkflow());
                            psUpWf.executeUpdate();
                        }

                        String nomPrenom = "";
                        String typeEv = "Mouvement RH";
                        try (var ps2 = c.prepareStatement(
                                "SELECT e.nom || ' ' || e.prenom AS np, t.libelle AS ev " +
                                        "FROM employe e " +
                                        "JOIN workflow_rh w ON w.id_employe = e.id_employe " +
                                        "JOIN ref_type_evenement_rh t ON w.id_type_evenement_rh = t.id_type_evenement_rh "
                                        +
                                        "WHERE w.id_workflow = ?")) {
                            ps2.setInt(1, wf.getIdWorkflow());
                            try (var rs2 = ps2.executeQuery()) {
                                if (rs2.next()) {
                                    nomPrenom = rs2.getString("np");
                                    typeEv = rs2.getString("ev");
                                }
                            }
                        }

                        creerNotificationRh(c, null, "MOUVEMENT_APPLIQUE", "NORMALE",
                                "Prise d'effet - " + typeEv + " - " + nomPrenom,
                                "Le mouvement RH de type " + typeEv + " pour " + nomPrenom
                                        + " est désormais actif (sa date d'effet au "
                                        + (dateEffet != null ? dateEffet.toString() : "") + " a ete atteinte).",
                                "/employes/" + wf.getIdEmploye() + "?tab=carriere", wf.getIdEmploye());

                        c.commit();
                    } catch (Exception exWf) {
                        c.rollback();
                        System.err.println("Erreur autoAppliquerMouvement (id=" + rs.getInt("id_workflow") + ") : "
                                + exWf.getMessage());
                    } finally {
                        c.setAutoCommit(initialAutoCommit);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur autoAppliquerMouvementsEchus : " + e.getMessage());
        }
    }

    /**
     * Compte le nombre de renouvellements CDD précédents en remontant la chaîne des
     * contrats.
     */
    private static int compterRenouvellementsCDDPrecedents(Integer idContrat, Connection c) {
        if (idContrat == null)
            return 0;
        int count = 0;
        Integer currentId = idContrat;
        try {
            while (currentId != null) {
                String sql = "SELECT ct.id_contrat_precedent, tc.code " +
                        "FROM contrat_travail ct " +
                        "JOIN ref_type_contrat tc ON ct.id_type_contrat = tc.id_type_contrat " +
                        "WHERE ct.id_contrat = ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, currentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String typeCode = rs.getString("code");
                            Integer precedentId = rs.getObject("id_contrat_precedent") != null
                                    ? rs.getInt("id_contrat_precedent")
                                    : null;
                            if ("CDD".equals(typeCode) && precedentId != null) {
                                count++;
                                currentId = precedentId;
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur compterRenouvellementsCDDPrecedents : " + e.getMessage());
        }
        return count;
    }

    /**
     * Calcule la durée totale cumulée en mois de toute la lignée CDD (contrats
     * passés + nouveau renouvellement).
     */
    private static double calculerDureeCumuleeCDDMois(Integer idContratActif, Date nouvelleDateDebut,
            Date nouvelleDateFin, Connection c) {
        double totalJours = 0.0;
        if (nouvelleDateDebut != null && nouvelleDateFin != null) {
            long joursNouveau = java.time.temporal.ChronoUnit.DAYS.between(
                    nouvelleDateDebut.toLocalDate(),
                    nouvelleDateFin.toLocalDate().plusDays(1));
            totalJours += Math.max(0, joursNouveau);
        }

        Integer currentId = idContratActif;
        try {
            while (currentId != null) {
                String sql = "SELECT ct.date_debut, ct.date_fin, ct.id_contrat_precedent, tc.code " +
                        "FROM contrat_travail ct " +
                        "JOIN ref_type_contrat tc ON ct.id_type_contrat = tc.id_type_contrat " +
                        "WHERE ct.id_contrat = ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, currentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String typeCode = rs.getString("code");
                            Date debut = rs.getDate("date_debut");
                            Date fin = rs.getDate("date_fin");
                            Integer precedentId = rs.getObject("id_contrat_precedent") != null
                                    ? rs.getInt("id_contrat_precedent")
                                    : null;

                            if ("CDD".equals(typeCode) && debut != null && fin != null) {
                                long jours = java.time.temporal.ChronoUnit.DAYS.between(
                                        debut.toLocalDate(),
                                        fin.toLocalDate().plusDays(1));
                                totalJours += Math.max(0, jours);
                            }
                            currentId = precedentId;
                        } else {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur calculerDureeCumuleeCDDMois : " + e.getMessage());
        }

        // 1 mois moyen = 30.4375 jours (365.25 / 12)
        return totalJours / 30.4375;
    }

    // ─── Resolveurs & Helpers Privés ─────────────────────────────────────────────

    /**
     * Lit un paramètre entier depuis parametre_global (retourne defaultValue si
     * absent).
     */
    private static int resolveParametreGlobalInt(String code, int defaultValue, Connection c) throws Exception {
        try (var ps = c.prepareStatement(
                "SELECT valeur FROM parametre_global WHERE code_parametre = ? AND (date_fin IS NULL OR date_fin >= CURRENT_DATE) ORDER BY date_debut DESC LIMIT 1")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        return Integer.parseInt(rs.getString(1).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    /**
     * Résout l'id_statut_rh depuis ref_statut_rh par son code.
     * Remplace les anciens
     * resolveStatutContrat/resolveStatutAffectation/resolveStatutWorkflow.
     * La table ref_statut_rh est un ref fixe — on ne crée jamais de nouvelles
     * lignes dynamiquement.
     */
    private static Integer resolveStatut(String code, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT id_statut_rh FROM ref_statut_rh WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Statut RH introuvable dans ref_statut_rh : '" + code + "'. " +
                "Valeurs attendues : BROUILLON, PLANIFIE, A_SIGNER, ACTIF, SUSPENDU, HISTORISE, CLOTURE, ROMPU, ANNULE");
    }

    private static Integer resolveTypeEvenement(String code, Connection c) throws Exception {
        if (code == null || code.isBlank())
            code = "FIN_CONTRAT";
        try (var ps = c.prepareStatement("SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        }
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
                if (rs.next())
                    return rs.getInt(1);
            }
        }
        try (var ps = c.prepareStatement(
                "SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'FIN_CONTRAT'")) {
            try (var rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Type d'événement RH introuvable : " + code);
    }

    private static String resolveCodeTypeEvenement(Integer id, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT code FROM ref_type_evenement_rh WHERE id_type_evenement_rh = ?")) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString(1);
            }
        }
        throw new IllegalStateException("Type d'événement RH introuvable (id=" + id + ").");
    }

    private static String resolveCodeStatutWorkflow(Integer id, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT code FROM ref_statut_rh WHERE id_statut_rh = ?")) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString(1);
            }
        }
        throw new IllegalStateException("Statut RH introuvable (id=" + id + ").");
    }

    private static Integer resolveMotifMouvement(String code, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = ?")) {
            ps.setString(1, code);
            try (var rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        }
        try (var ps = c.prepareStatement(
                "SELECT id_motif_mouvement FROM ref_motif_mouvement ORDER BY id_motif_mouvement ASC LIMIT 1")) {
            try (var rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Motif mouvement introuvable : " + code);
    }

    private void appendCommentaireWorkflow(Integer idWorkflow, String commentaire, Connection c) throws Exception {
        String sql = "UPDATE workflow_rh SET commentaire = COALESCE(commentaire || ' | ' || ?, ?) WHERE id_workflow = ?";
        try (var ps = c.prepareStatement(sql)) {
            ps.setString(1, commentaire);
            ps.setString(2, commentaire);
            ps.setInt(3, idWorkflow);
            ps.executeUpdate();
        }
    }

    private void annulerContratBrouillon(Integer idContrat, Connection c) throws Exception {
        String sql = "UPDATE contrat_travail SET " +
                "id_statut_rh = COALESCE((SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ANNULE'), " +
                "(SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ROMPU')), " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_contrat = ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'BROUILLON')";
        try (var ps = c.prepareStatement(sql)) {
            ps.setInt(1, idContrat);
            ps.executeUpdate();
        }
    }

    private void annulerAffectationPlanifiee(Integer idAffectation, Connection c) throws Exception {
        String sql = "UPDATE employe_affectation_carriere SET " +
                "id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ANNULE'), " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_affectation = ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'PLANIFIE')";
        try (var ps = c.prepareStatement(sql)) {
            ps.setInt(1, idAffectation);
            ps.executeUpdate();
        }
    }

    private static void enregistrerPeriodeEssaiInitiale(ContratTravail contrat, Connection c) throws Exception {
        String sql = "INSERT INTO contrat_periode_essai (id_contrat, numero_periode, date_debut, date_fin_prevue, statut, commentaire) "
                +
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

    private static void creerNotificationRh(Connection c, Integer idUtilisateur, String typeAlerte, String priorite,
            String titre, String message, String lien, Integer idEmployeConcerne) throws Exception {
        String sql = "INSERT INTO notification_rh (id_utilisateur, type_alerte, priorite, titre, message, lien, id_employe_concerne) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (idUtilisateur != null)
                ps.setInt(1, idUtilisateur);
            else
                ps.setNull(1, Types.INTEGER);
            ps.setString(2, typeAlerte);
            ps.setString(3, priorite);
            ps.setString(4, titre);
            ps.setString(5, message);
            ps.setString(6, lien);
            if (idEmployeConcerne != null)
                ps.setInt(7, idEmployeConcerne);
            else
                ps.setNull(7, Types.INTEGER);
            ps.executeUpdate();
        }
    }

    private static void validerExigenceDateFin(Integer idTypeContrat, Date dateFin, Connection c) throws Exception {
        if (idTypeContrat == null)
            return;
        String sql = "SELECT libelle, exige_date_fin FROM ref_type_contrat WHERE id_type_contrat = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTypeContrat);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean exigeDateFin = rs.getBoolean("exige_date_fin");
                    String libelle = rs.getString("libelle");
                    if (exigeDateFin && dateFin == null) {
                        throw new IllegalArgumentException(
                                "La date de fin est obligatoire pour le type de contrat \"" + libelle + "\".");
                    }
                }
            }
        }
    }
}
