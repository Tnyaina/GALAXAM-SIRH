package mg.galaxam.sirh.api.controller.contrat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.model.contrat.ContratTravail;
import mg.galaxam.sirh.model.contrat.EmployeAffectationCarriere;
import mg.galaxam.sirh.model.contrat.WorkflowRh;
import mg.galaxam.sirh.model.contrat.WorkflowRhCPL;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST pour la consultation et modification des contrats d'un employé.
 *
 * La CRÉATION d'un contrat passe toujours par WorkflowRhController (embauche, transition, etc.).
 * Ce controller gère uniquement :
 *   - La consultation de l'historique des contrats
 *   - La modification d'un contrat en statut BROUILLON (avant validation DAF)
 */
@RestController
@RequestMapping("/employes/{idEmploye}")
public class ContratTravailController {

    @Autowired
    private DataSource dataSource;

    // =========================================================================
    // CONSULTATIONS
    // =========================================================================

    /**
     * Retourne l'historique complet des contrats d'un employé.
     * GET /employes/{idEmploye}/contrats
     */
    @GetMapping("/contrats")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getContratsEmploye(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            List<ContratTravail> contrats = ContratTravail.listerParEmploye(idEmploye, c);
            return ResponseEntity.ok(contrats);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    /**
     * Retourne le contrat actif d'un employé.
     * GET /employes/{idEmploye}/contrats/actif
     */
    @GetMapping("/contrats/actif")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getContratActif(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            ContratTravail contrat = ContratTravail.rechercherActifParEmploye(idEmploye, c);
            if (contrat == null) {
                return ResponseEntity.ok(null);
            }
            return ResponseEntity.ok(contrat);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    /**
     * Retourne le détail d'un contrat spécifique.
     * GET /employes/{idEmploye}/contrats/{idContrat}
     */
    @GetMapping("/contrats/{idContrat}")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getContrat(@PathVariable Integer idEmploye, @PathVariable Integer idContrat) {
        try (Connection c = dataSource.getConnection()) {
            ContratTravail contrat = new ContratTravail().rechercherParId(idContrat, c);
            if (contrat == null || !contrat.getIdEmploye().equals(idEmploye)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Contrat introuvable.");
            }
            return ResponseEntity.ok(contrat);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    // =========================================================================
    // MODIFICATION D'UN BROUILLON
    // =========================================================================

    /**
     * Modifier un contrat en statut BROUILLON (avant validation DAF).
     * Retourne le workflow CPL mis à jour pour rafraîchir la page récapitulatif.
     *
     * Règle métier : Seul un contrat BROUILLON est modifiable.
     * Un contrat ACTIF ne peut pas être modifié directement (passer par un avenant/workflow).
     *
     * PUT /employes/{idEmploye}/contrats/{idContrat}
     */
    @PutMapping("/contrats/{idContrat}")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierBrouillonContrat(
            @PathVariable Integer idEmploye,
            @PathVariable Integer idContrat,
            @RequestBody ContratModificationRequest req) {

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Vérifier que le contrat appartient à cet employé
                ContratTravail contrat = new ContratTravail().rechercherParId(idContrat, c);
                if (contrat == null || !contrat.getIdEmploye().equals(idEmploye)) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Contrat introuvable.");
                }

                // Vérifier que c'est un BROUILLON
                String statutCode = resolveCodeStatutContrat(contrat.getIdStatutContrat(), c);
                if (!"BROUILLON".equals(statutCode)) {
                    return ResponseEntity.badRequest()
                            .body("Seul un contrat en statut BROUILLON peut être modifié. " +
                                    "Statut actuel : " + statutCode + ". Utilisez un avenant ou un workflow de transition.");
                }

                // Mettre à jour les champs modifiables
                if (req.getIdFiliale() != null) contrat.setIdFiliale(req.getIdFiliale());
                if (req.getIdTypeContrat() != null) contrat.setIdTypeContrat(req.getIdTypeContrat());
                if (req.getIdTypeIntervenant() != null) contrat.setIdTypeIntervenant(req.getIdTypeIntervenant());
                if (req.getDateDebut() != null) contrat.setDateDebut(req.getDateDebut());
                if (req.getDateFin() != null) contrat.setDateFin(req.getDateFin());
                if (req.getDateSignature() != null) contrat.setDateSignature(req.getDateSignature());
                contrat.setComptabiliseEffectif(req.isComptabiliseEffectif());
                if (req.getDescription() != null) contrat.setDescription(req.getDescription());
                if (req.getCommentaire() != null) contrat.setCommentaire(req.getCommentaire());

                contrat.update(c);

                // Mettre à jour l'affectation si des données sont fournies
                if (req.hasAffectationData()) {
                    EmployeAffectationCarriere affectation = EmployeAffectationCarriere
                            .rechercherPlanifieeParEmploye(idEmploye, c);
                    if (affectation != null) {
                        if (req.getIdSite() != null) affectation.setIdSite(req.getIdSite());
                        if (req.getIdDepartement() != null) affectation.setIdDepartement(req.getIdDepartement());
                        if (req.getIdService() != null) affectation.setIdService(req.getIdService());
                        if (req.getIdFonction() != null) affectation.setIdFonction(req.getIdFonction());
                        affectation.setIdResponsableHierarchique(req.getIdResponsableHierarchique());
                        if (req.getIdCategorieProfessionnelle() != null)
                            affectation.setIdCategorieProfessionnelle(req.getIdCategorieProfessionnelle());
                        if (req.getIdModeCalculPaie() != null) affectation.setIdModeCalculPaie(req.getIdModeCalculPaie());
                        if (req.getSalaireBase() != null) affectation.setSalaireBase(req.getSalaireBase());
                        if (req.getDateDebut() != null) affectation.setDateDebut(req.getDateDebut());
                        if (req.getDescriptionAffectation() != null) affectation.setDescription(req.getDescriptionAffectation());
                        if (req.getCommentaireAffectation() != null) affectation.setCommentaire(req.getCommentaireAffectation());
                        affectation.update(c);
                    }
                }

                AuditLog.audit(c, "MODIFICATION_BROUILLON_CONTRAT", "contrat_travail",
                        String.valueOf(idContrat), null, contrat);
                c.commit();

                // Retourner le workflow CPL mis à jour (pour rafraîchir le récapitulatif)
                WorkflowRh wf = WorkflowRh.rechercherBrouillonParEmploye(idEmploye, c);
                if (wf != null) {
                    WorkflowRhCPL recap = WorkflowRhCPL.rechercherParId(wf.getIdWorkflow(), c);
                    return ResponseEntity.ok(recap);
                }
                return ResponseEntity.ok(contrat);

            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    // =========================================================================
    // AFFECTATIONS
    // =========================================================================

    /**
     * Historique complet des affectations d'un employé.
     * GET /employes/{idEmploye}/affectations
     */
    @GetMapping("/affectations")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAffectationsEmploye(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            List<EmployeAffectationCarriere> list = EmployeAffectationCarriere.listerParEmploye(idEmploye, c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    /**
     * Affectation ACTIVE courante d'un employé.
     * GET /employes/{idEmploye}/affectations/active
     */
    @GetMapping("/affectations/active")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAffectationActive(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            EmployeAffectationCarriere affectation = EmployeAffectationCarriere
                    .rechercherActiveParEmploye(idEmploye, c);
            if (affectation == null) {
                return ResponseEntity.ok(null);
            }
            return ResponseEntity.ok(affectation);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    // =========================================================================
    // MÉTHODES UTILITAIRES
    // =========================================================================

    private String resolveCodeStatutContrat(Integer id, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT code FROM ref_statut_contrat WHERE id_statut_contrat = ?")) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        throw new IllegalStateException("Statut contrat introuvable (id=" + id + ").");
    }

    // ─── DTO interne pour la modification d'un brouillon ──────────────────────

    /**
     * DTO de modification d'un contrat BROUILLON.
     * Tous les champs sont optionnels (seuls les non-null sont mis à jour).
     */
    public static class ContratModificationRequest {
        private Integer idFiliale;
        private Integer idTypeContrat;
        private Integer idTypeIntervenant;
        private Date dateDebut;
        private Date dateFin;
        private Date dateSignature;
        private boolean comptabiliseEffectif = true;
        private String description;
        private String commentaire;

        // Affectation (optionnel)
        private Integer idSite;
        private Integer idDepartement;
        private Integer idService;
        private Integer idFonction;
        private Integer idResponsableHierarchique;
        private Integer idCategorieProfessionnelle;
        private Integer idModeCalculPaie;
        private BigDecimal salaireBase;
        private String descriptionAffectation;
        private String commentaireAffectation;

        /** Retourne true si au moins un champ d'affectation est fourni */
        public boolean hasAffectationData() {
            return idSite != null || idDepartement != null || idService != null
                    || idFonction != null || idCategorieProfessionnelle != null
                    || idModeCalculPaie != null || salaireBase != null;
        }

        public Integer getIdFiliale() { return idFiliale; }
        public void setIdFiliale(Integer idFiliale) { this.idFiliale = idFiliale; }
        public Integer getIdTypeContrat() { return idTypeContrat; }
        public void setIdTypeContrat(Integer idTypeContrat) { this.idTypeContrat = idTypeContrat; }
        public Integer getIdTypeIntervenant() { return idTypeIntervenant; }
        public void setIdTypeIntervenant(Integer idTypeIntervenant) { this.idTypeIntervenant = idTypeIntervenant; }
        public Date getDateDebut() { return dateDebut; }
        public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
        public Date getDateFin() { return dateFin; }
        public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
        public Date getDateSignature() { return dateSignature; }
        public void setDateSignature(Date dateSignature) { this.dateSignature = dateSignature; }
        public boolean isComptabiliseEffectif() { return comptabiliseEffectif; }
        public void setComptabiliseEffectif(boolean comptabiliseEffectif) { this.comptabiliseEffectif = comptabiliseEffectif; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCommentaire() { return commentaire; }
        public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
        public Integer getIdSite() { return idSite; }
        public void setIdSite(Integer idSite) { this.idSite = idSite; }
        public Integer getIdDepartement() { return idDepartement; }
        public void setIdDepartement(Integer idDepartement) { this.idDepartement = idDepartement; }
        public Integer getIdService() { return idService; }
        public void setIdService(Integer idService) { this.idService = idService; }
        public Integer getIdFonction() { return idFonction; }
        public void setIdFonction(Integer idFonction) { this.idFonction = idFonction; }
        public Integer getIdResponsableHierarchique() { return idResponsableHierarchique; }
        public void setIdResponsableHierarchique(Integer idResponsableHierarchique) { this.idResponsableHierarchique = idResponsableHierarchique; }
        public Integer getIdCategorieProfessionnelle() { return idCategorieProfessionnelle; }
        public void setIdCategorieProfessionnelle(Integer idCategorieProfessionnelle) { this.idCategorieProfessionnelle = idCategorieProfessionnelle; }
        public Integer getIdModeCalculPaie() { return idModeCalculPaie; }
        public void setIdModeCalculPaie(Integer idModeCalculPaie) { this.idModeCalculPaie = idModeCalculPaie; }
        public BigDecimal getSalaireBase() { return salaireBase; }
        public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }
        public String getDescriptionAffectation() { return descriptionAffectation; }
        public void setDescriptionAffectation(String descriptionAffectation) { this.descriptionAffectation = descriptionAffectation; }
        public String getCommentaireAffectation() { return commentaireAffectation; }
        public void setCommentaireAffectation(String commentaireAffectation) { this.commentaireAffectation = commentaireAffectation; }
    }
}
