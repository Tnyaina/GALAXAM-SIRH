package mg.galaxam.sirh.api.controller.contrat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            // Enrichissement avec les libellés (filiale, type contrat...) pour chaque contrat
            // de l'historique — nécessaire notamment pour le certificat de travail, qui est
            // généré à partir d'un contrat de l'historique (donc jamais le contrat "actif").
            List<Map<String, Object>> contratsEnrichis = new ArrayList<>();
            for (ContratTravail ct : contrats) {
                contratsEnrichis.add(enrichirContrat(ct, c));
            }
            return ResponseEntity.ok(contratsEnrichis);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    /**
     * Retourne le contrat actif d'un employé enrichi avec les libellés (filiale, type_contrat, etc.).
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
            Map<String, Object> map = enrichirContrat(contrat, c);
            return ResponseEntity.ok(map);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    /**
     * Évalue et retourne l'état de la période d'essai d'un employé.
     * GET /employes/{idEmploye}/essai/etat
     */
    @GetMapping("/essai/etat")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEtatEssaiEmploye(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            ContratTravail contrat = ContratTravail.rechercherActifParEmploye(idEmploye, c);
            List<WorkflowRhCPL> workflows = WorkflowRhCPL.listerParEmploye(idEmploye, c);

            if (contrat == null) {
                return ResponseEntity.ok(new EtatEssaiDTO(false, false, false, false, false, null, null, false, false));
            }

            final Integer activeContratId = contrat.getIdContrat();

            boolean estConfirme = workflows.stream().anyMatch(w ->
                "VALIDE".equals(w.getCodeStatutWorkflow()) &&
                "CONFIRMATION_ESSAI".equals(w.getCodeTypeEvenement()) &&
                (activeContratId.equals(w.getIdContratCible()) || activeContratId.equals(w.getIdContratSource())) &&
                (w.getDateEffet() != null && !w.getDateEffet().after(new java.sql.Date(System.currentTimeMillis())))
            );

            boolean estRenouvele = workflows.stream().anyMatch(w ->
                ("VALIDE".equals(w.getCodeStatutWorkflow()) || "BROUILLON".equals(w.getCodeStatutWorkflow())) &&
                "RENOUVELLEMENT_ESSAI".equals(w.getCodeTypeEvenement()) &&
                (activeContratId.equals(w.getIdContratCible()) || activeContratId.equals(w.getIdContratSource()))
            );

            if (estConfirme) {
                return ResponseEntity.ok(new EtatEssaiDTO(false, false, false, estConfirme, estRenouvele, null, null, false, false));
            }

            String dateFinEssaiStr = null;
            Integer dureeEssai = contrat.getDureeEssaiMois();

            if (contrat.getDateFinEssai() != null) {
                dateFinEssaiStr = contrat.getDateFinEssai().toString();
            }

            // 1. Chercher dans contrat_periode_essai
            if (dateFinEssaiStr == null) {
                try (var psPe = c.prepareStatement(
                        "SELECT date_fin_prevue FROM contrat_periode_essai WHERE id_contrat = ? AND statut IN ('EN_COURS', 'RENOUVELEE') ORDER BY numero_periode DESC LIMIT 1")) {
                    psPe.setInt(1, contrat.getIdContrat());
                    try (var rsPe = psPe.executeQuery()) {
                        if (rsPe.next() && rsPe.getDate(1) != null) {
                            dateFinEssaiStr = rsPe.getDate(1).toString();
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 2. Chercher d'après contrat.getDateDebut() + dureeEssai
            if (dateFinEssaiStr == null && contrat.getDateDebut() != null && dureeEssai != null && dureeEssai > 0) {
                dateFinEssaiStr = contrat.getDateDebut().toLocalDate().plusMonths(dureeEssai).minusDays(1).toString();
            }

            // 3. Fallback workflow_rh / workflow_rh_cpl (uniquement pour le contrat actif)
            if (dateFinEssaiStr == null) {
                WorkflowRhCPL wfWithEssai = workflows.stream()
                    .filter(w -> "VALIDE".equals(w.getCodeStatutWorkflow()) &&
                                 (activeContratId.equals(w.getIdContratCible()) || 
                                  (activeContratId.equals(w.getIdContratSource()) && w.getIdContratCible() == null)) &&
                                 (w.getDateFinEssai() != null || w.getDureeEssaiMois() != null))
                    .findFirst().orElse(null);
                if (wfWithEssai != null) {
                    if (wfWithEssai.getDateFinEssai() != null) {
                        dateFinEssaiStr = wfWithEssai.getDateFinEssai().toString();
                    } else if (wfWithEssai.getDateDebutContrat() != null && wfWithEssai.getDureeEssaiMois() != null) {
                        if (dureeEssai == null) dureeEssai = wfWithEssai.getDureeEssaiMois();
                        dateFinEssaiStr = wfWithEssai.getDateDebutContrat().toLocalDate().plusMonths(dureeEssai).minusDays(1).toString();
                    }
                }
            }

            if (dateFinEssaiStr == null) {
                return ResponseEntity.ok(new EtatEssaiDTO(false, false, false, false, estRenouvele, null, dureeEssai, false, false));
            }

            String todayStr = java.time.LocalDate.now().toString();
            boolean isDernierJour = dateFinEssaiStr.equals(todayStr);
            boolean isEssaiEchu = dateFinEssaiStr.compareTo(todayStr) < 0;

            boolean canConfirm = true;
            boolean canRenew = !estRenouvele;

            return ResponseEntity.ok(new EtatEssaiDTO(
                true, isDernierJour, isEssaiEchu, false, estRenouvele,
                dateFinEssaiStr, dureeEssai, canConfirm, canRenew
            ));

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
            return ResponseEntity.ok(enrichirContrat(contrat, c));
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
                String statutCode = resolveCodeStatutContrat(contrat.getIdStatutRh(), c);
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

                // Mettre à jour la date d'effet du workflow s'il existe
                WorkflowRh wf = WorkflowRh.rechercherBrouillonParEmploye(idEmploye, c);
                if (wf != null) {
                    if (req.getDateDebut() != null) {
                        wf.setDateEffet(req.getDateDebut());
                        wf.update(c);
                    }
                }

                AuditLog.audit(c, "MODIFICATION_BROUILLON_CONTRAT", "contrat_travail",
                        String.valueOf(idContrat), null, contrat);
                c.commit();

                // Retourner le workflow CPL mis à jour (pour rafraîchir le récapitulatif)
                if (wf != null) {
                    WorkflowRhCPL recap = WorkflowRhCPL.rechercherParId(wf.getIdWorkflow(), c);
                    return ResponseEntity.ok(recap);
                }
                return ResponseEntity.ok(enrichirContrat(contrat, c));

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
     * Affectation ACTIVE courante d'un employé enrichie avec les libellés.
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
            return ResponseEntity.ok(enrichirAffectation(affectation, c));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    /**
     * Affectation liée à un contrat spécifique, quel que soit son statut
     * (ACTIVE, HISTORISE, PLANIFIE...).
     *
     * Contrairement à /affectations/active (qui ne renvoie que l'affectation en cours),
     * cet endpoint permet de reconstituer fonction / site / département / catégorie pro
     * d'un contrat déjà terminé — cas typique du certificat de travail, qui ne se génère
     * justement que pour un contrat qui n'est plus actif.
     *
     * GET /employes/{idEmploye}/contrats/{idContrat}/affectation
     */
    @GetMapping("/contrats/{idContrat}/affectation")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAffectationParContrat(@PathVariable Integer idEmploye, @PathVariable Integer idContrat) {
        try (Connection c = dataSource.getConnection()) {
            ContratTravail contrat = new ContratTravail().rechercherParId(idContrat, c);
            if (contrat == null || !contrat.getIdEmploye().equals(idEmploye)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Contrat introuvable.");
            }
            EmployeAffectationCarriere affectation = EmployeAffectationCarriere.rechercherParContrat(idContrat, c);
            if (affectation == null) {
                return ResponseEntity.ok(null);
            }
            return ResponseEntity.ok(enrichirAffectation(affectation, c));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur.");
        }
    }

    // =========================================================================
    // MÉTHODES UTILITAIRES
    // =========================================================================

    private String resolveCodeStatutContrat(Integer id, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT code FROM ref_statut_rh WHERE id_statut_rh = ?")) {
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

    // ─── Helpers d'enrichissement de données pour l'IHM ─────────────────────────

    private String fetchLibelle(Connection c, String sql, Integer id) {
        if (id == null || id <= 0) return null;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Récupère le libellé et les informations légales (adresse, NIF, STAT) d'une filiale.
     * Nécessaire pour l'en-tête officiel du certificat de travail.
     *
     * ATTENTION : adapter les noms de colonnes (adresse / nif / stat) si le schéma réel
     * de ref_filiale diffère — cette table n'était pas incluse dans le script SQL fourni.
     */
    private Map<String, Object> fetchFiliale(Connection c, Integer id) {
        if (id == null || id <= 0) return null;
        String sql = "SELECT raison_sociale, nif, stat FROM ref_filiale WHERE id_filiale = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("libelle", rs.getString("raison_sociale"));
                    m.put("nif", rs.getString("nif"));
                    m.put("stat", rs.getString("stat"));
                    return m;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Map<String, Object> enrichirContrat(ContratTravail ct, Connection c) {
        Map<String, Object> map = new HashMap<>();
        map.put("idContrat", ct.getIdContrat());
        map.put("idEmploye", ct.getIdEmploye());
        map.put("idFiliale", ct.getIdFiliale());
        map.put("idTypeContrat", ct.getIdTypeContrat());
        map.put("idTypeIntervenant", ct.getIdTypeIntervenant());
        map.put("idStatutRh", ct.getIdStatutRh());
        map.put("numeroContrat", ct.getNumeroContrat());
        map.put("dateSignature", ct.getDateSignature());
        map.put("dateDebut", ct.getDateDebut());
        map.put("dateFin", ct.getDateFin());
        map.put("dureeEssaiMois", ct.getDureeEssaiMois());
        map.put("dateFinEssai", ct.getDateFinEssai());
        map.put("comptabiliseEffectif", ct.isComptabiliseEffectif());
        map.put("description", ct.getDescription());
        map.put("commentaire", ct.getCommentaire());

        if (ct.getIdFiliale() != null && ct.getIdFiliale() > 0) {
            Map<String, Object> filiale = fetchFiliale(c, ct.getIdFiliale());
            if (filiale != null) {
                map.put("libelleFiliale", filiale.get("libelle"));
                // Objet complet transmis au front (adresse/NIF/STAT), utilisé notamment
                // par le certificat de travail dans son en-tête officiel.
                map.put("filiale", filiale);
            }
        }
        if (ct.getIdTypeContrat() != null && ct.getIdTypeContrat() > 0) {
            map.put("libelleTypeContrat", fetchLibelle(c, "SELECT libelle FROM ref_type_contrat WHERE id_type_contrat = ?", ct.getIdTypeContrat()));
        }
        if (ct.getIdTypeIntervenant() != null && ct.getIdTypeIntervenant() > 0) {
            map.put("libelleTypeIntervenant", fetchLibelle(c, "SELECT libelle FROM ref_type_intervenant WHERE id_type_intervenant = ?", ct.getIdTypeIntervenant()));
        }
        if (ct.getIdStatutRh() != null && ct.getIdStatutRh() > 0) {
            try (PreparedStatement ps = c.prepareStatement("SELECT code, libelle FROM ref_statut_rh WHERE id_statut_rh = ?")) {
                ps.setInt(1, ct.getIdStatutRh());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        map.put("codeStatutContrat", rs.getString("code"));
                        map.put("libelleStatutContrat", rs.getString("libelle"));
                    }
                }
            } catch (SQLException e) {
                // Ignore failure to fetch optional statut libelle
            }
        }

        return map;
    }

    private Map<String, Object> enrichirAffectation(EmployeAffectationCarriere aff, Connection c) {
        Map<String, Object> map = new HashMap<>();
        map.put("idAffectation", aff.getIdAffectation());
        map.put("idEmploye", aff.getIdEmploye());
        map.put("idContrat", aff.getIdContrat());
        map.put("idSite", aff.getIdSite());
        map.put("idDepartement", aff.getIdDepartement());
        map.put("idService", aff.getIdService());
        map.put("idFonction", aff.getIdFonction());
        map.put("idResponsableHierarchique", aff.getIdResponsableHierarchique());
        map.put("idCategorieProfessionnelle", aff.getIdCategorieProfessionnelle());
        map.put("idModeCalculPaie", aff.getIdModeCalculPaie());
        map.put("salaireBase", aff.getSalaireBase());
        map.put("dateDebut", aff.getDateDebut());
        map.put("dateFin", aff.getDateFin());
        map.put("description", aff.getDescription());
        map.put("commentaire", aff.getCommentaire());

        if (aff.getIdSite() != null && aff.getIdSite() > 0) {
            map.put("libelleSite", fetchLibelle(c, "SELECT libelle FROM ref_site WHERE id_site = ?", aff.getIdSite()));
        }
        if (aff.getIdDepartement() != null && aff.getIdDepartement() > 0) {
            map.put("libelleDepartement", fetchLibelle(c, "SELECT libelle FROM ref_departement WHERE id_departement = ?", aff.getIdDepartement()));
        }
        if (aff.getIdService() != null && aff.getIdService() > 0) {
            map.put("libelleService", fetchLibelle(c, "SELECT libelle FROM ref_service WHERE id_service = ?", aff.getIdService()));
        }
        if (aff.getIdFonction() != null && aff.getIdFonction() > 0) {
            map.put("libelleFonction", fetchLibelle(c, "SELECT libelle FROM ref_fonction WHERE id_fonction = ?", aff.getIdFonction()));
        }
        if (aff.getIdCategorieProfessionnelle() != null && aff.getIdCategorieProfessionnelle() > 0) {
            String catLib = fetchLibelle(c, "SELECT libelle FROM ref_categorie_professionnelle WHERE id_categorie_professionnelle = ?", aff.getIdCategorieProfessionnelle());
            map.put("libelleCategorieProfessionnelle", catLib);
            map.put("libelleCategoriePro", catLib);
        }
        if (aff.getIdModeCalculPaie() != null && aff.getIdModeCalculPaie() > 0) {
            String modeLib = fetchLibelle(c, "SELECT libelle FROM ref_mode_calcul_paie WHERE id_mode_calcul_paie = ?", aff.getIdModeCalculPaie());
            map.put("libelleModeCalculPaie", modeLib);
            map.put("libelleModePaie", modeLib);
        }

        return map;
    }

    /**
     * DTO de l'évaluation de la période d'essai calculée côté serveur.
     */
    public static class EtatEssaiDTO {
        private boolean isEssaiActif;
        private boolean isDernierJour;
        private boolean isEssaiEchu;
        private boolean estConfirme;
        private boolean estRenouvele;
        private String dateFinEssaiStr;
        private Integer dureeEssaiMois;
        private boolean canConfirm;
        private boolean canRenew;

        public EtatEssaiDTO() {}

        public EtatEssaiDTO(boolean isEssaiActif, boolean isDernierJour, boolean isEssaiEchu,
                            boolean estConfirme, boolean estRenouvele, String dateFinEssaiStr,
                            Integer dureeEssaiMois, boolean canConfirm, boolean canRenew) {
            this.isEssaiActif = isEssaiActif;
            this.isDernierJour = isDernierJour;
            this.isEssaiEchu = isEssaiEchu;
            this.estConfirme = estConfirme;
            this.estRenouvele = estRenouvele;
            this.dateFinEssaiStr = dateFinEssaiStr;
            this.dureeEssaiMois = dureeEssaiMois;
            this.canConfirm = canConfirm;
            this.canRenew = canRenew;
        }

        public boolean isIsEssaiActif() { return isEssaiActif; }
        public void setIsEssaiActif(boolean isEssaiActif) { this.isEssaiActif = isEssaiActif; }
        public boolean isIsDernierJour() { return isDernierJour; }
        public void setIsDernierJour(boolean isDernierJour) { this.isDernierJour = isDernierJour; }
        public boolean isIsEssaiEchu() { return isEssaiEchu; }
        public void setIsEssaiEchu(boolean isEssaiEchu) { this.isEssaiEchu = isEssaiEchu; }
        public boolean isEstConfirme() { return estConfirme; }
        public void setEstConfirme(boolean estConfirme) { this.estConfirme = estConfirme; }
        public boolean isEstRenouvele() { return estRenouvele; }
        public void setEstRenouvele(boolean estRenouvele) { this.estRenouvele = estRenouvele; }
        public String getDateFinEssaiStr() { return dateFinEssaiStr; }
        public void setDateFinEssaiStr(String dateFinEssaiStr) { this.dateFinEssaiStr = dateFinEssaiStr; }
        public Integer getDureeEssaiMois() { return dureeEssaiMois; }
        public void setDureeEssaiMois(Integer dureeEssaiMois) { this.dureeEssaiMois = dureeEssaiMois; }
        public boolean isCanConfirm() { return canConfirm; }
        public void setCanConfirm(boolean canConfirm) { this.canConfirm = canConfirm; }
        public boolean isCanRenew() { return canRenew; }
        public void setCanRenew(boolean canRenew) { this.canRenew = canRenew; }
    }
}