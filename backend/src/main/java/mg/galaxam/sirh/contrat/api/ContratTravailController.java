package mg.galaxam.sirh.contrat.api;


import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import mg.galaxam.sirh.contrat.api.dto.ContratModificationRequest;
import mg.galaxam.sirh.contrat.api.dto.DateSignatureRequest;
import mg.galaxam.sirh.contrat.api.dto.EtatEssaiDTO;
import mg.galaxam.sirh.contrat.persistence.ContratTravail;
import mg.galaxam.sirh.contrat.persistence.EmployeAffectationCarriere;
import mg.galaxam.sirh.contrat.persistence.WorkflowRh;
import mg.galaxam.sirh.contrat.persistence.WorkflowRhCPL;
import mg.galaxam.sirh.common.transaction.TransactionRunner;
import mg.galaxam.sirh.securite.persistence.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST pour la consultation et modification des contrats d'un
 * employé.
 *
 * La CRÉATION d'un contrat passe toujours par WorkflowRhController (embauche,
 * transition, etc.).
 * Ce controller gère uniquement :
 * - La consultation de l'historique des contrats
 * - La modification d'un contrat en statut BROUILLON (avant validation DAF)
 */
@RestController
@RequestMapping("/employes/{idEmploye}")
public class ContratTravailController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionRunner transactionRunner;

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
            if (contrats.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
            Set<Integer> idsContrats = contrats.stream()
                    .map(ContratTravail::getIdContrat)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Integer, Map<String, Object>> anciennetes =
                    ContratTravail.calculerAncienneteDetailleeBatch(idsContrats, c);
            Map<Integer, ContratReferences> references = chargerReferencesContratsEmploye(idEmploye, c);
            // Enrichissement avec les libellés (filiale, type contrat...) pour chaque
            // contrat
            // de l'historique — nécessaire notamment pour le certificat de travail, qui est
            // généré à partir d'un contrat de l'historique (donc jamais le contrat
            // "actif").
            List<Map<String, Object>> contratsEnrichis = new ArrayList<>();
            for (ContratTravail ct : contrats) {
                contratsEnrichis.add(enrichirContrat(ct, c, references.get(ct.getIdContrat()),
                        anciennetes.get(ct.getIdContrat())));
            }
            return ResponseEntity.ok(contratsEnrichis);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Retourne le contrat actif d'un employé enrichi avec les libellés (filiale,
     * type_contrat, etc.).
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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

            if (contrat == null) {
                return ResponseEntity.ok(EtatEssaiDTO.vide());
            }

            final Integer activeContratId = contrat.getIdContrat();

            // ── Lire le paramètre configurable de renouvellements max ──
            LocalDate refDate = contrat.getDateDebut() != null ? contrat.getDateDebut().toLocalDate() : LocalDate.now();
            int maxRenouvellements = mg.galaxam.sirh.contrat.domain.CarriereTransitionValidator.resolveParametreGlobalInt(
                    "MAX_RENOUVELLEMENTS_ESSAI", refDate, 1, c);

            // ── Lire la période d'essai active depuis contrat_periode_essai ──
            int periodeActuelle = 0;
            int nombreRenouvellements = 0;
            String dateFinEssaiStrFromPE = null;
            boolean aPeriodeEnCours = false;

            try (var psPe = c.prepareStatement(
                    "SELECT numero_periode, date_fin_prevue, statut FROM contrat_periode_essai " +
                            "WHERE id_contrat = ? ORDER BY numero_periode ASC")) {
                psPe.setInt(1, activeContratId);
                try (var rsPe = psPe.executeQuery()) {
                    while (rsPe.next()) {
                        int num = rsPe.getInt("numero_periode");
                        String statut = rsPe.getString("statut");
                        java.sql.Date dateFin = rsPe.getDate("date_fin_prevue");
                        if ("EN_COURS".equals(statut)) {
                            periodeActuelle = num;
                            aPeriodeEnCours = true;
                            if (dateFin != null)
                                dateFinEssaiStrFromPE = dateFin.toString();
                        }
                        if (num > 1)
                            nombreRenouvellements++;
                    }
                }
            } catch (Exception ignored) {
            }

            // ── Détecter si essay confirmé (workflow ACTIF ou PLANIFIE de type
            // CONFIRMATION_ESSAI) ──
            // NOTE : les statuts valides sont ACTIF (appliqué) et PLANIFIE (futur), PAS
            // 'VALIDE'
            boolean estConfirme = false;
            try (var psChkConf = c.prepareStatement(
                    "SELECT COUNT(*) FROM workflow_rh w " +
                            "JOIN ref_statut_rh s ON w.id_statut_rh = s.id_statut_rh " +
                            "JOIN ref_type_evenement_rh ev ON w.id_type_evenement_rh = ev.id_type_evenement_rh " +
                            "WHERE (w.id_contrat_cible = ? OR w.id_contrat_source = ?) AND (" +
                            "  (ev.code = 'CONFIRMATION_ESSAI' AND s.code IN ('ACTIF','PLANIFIE')) OR " +
                            "  (ev.code = 'RENOUVELLEMENT_ESSAI' AND s.code = 'PLANIFIE')" + // Remarque bien la
                                                                                             // différence ici !
                            ")")) {
                psChkConf.setInt(1, activeContratId);
                psChkConf.setInt(2, activeContratId);
                try (var rsConf = psChkConf.executeQuery()) {
                    if (rsConf.next())
                        estConfirme = rsConf.getInt(1) > 0;
                }
            } catch (Exception ignored) {
            }

            // Si la période est confirmée : plus de PE active — retourner état terminal
            if (estConfirme) {
                return ResponseEntity.ok(new EtatEssaiDTO(
                        false, false, false, true, nombreRenouvellements > 0,
                        null, contrat.getDureeEssaiMois(), false, false,
                        0, nombreRenouvellements, maxRenouvellements, false, null));
            }

            // ── Récupérer la date de fin d'essai : priorité à contrat_periode_essai
            // EN_COURS ──
            String dateFinEssaiStr = dateFinEssaiStrFromPE;
            Integer dureeEssai = contrat.getDureeEssaiMois();

            // Fallback 1 : champ date_fin_essai sur le contrat
            if (dateFinEssaiStr == null && contrat.getDateFinEssai() != null) {
                dateFinEssaiStr = contrat.getDateFinEssai().toString();
            }
            // Fallback 2 : calcul depuis dateDebut + dureeEssai
            if (dateFinEssaiStr == null && contrat.getDateDebut() != null && dureeEssai != null && dureeEssai > 0) {
                dateFinEssaiStr = contrat.getDateDebut().toLocalDate().plusMonths(dureeEssai).minusDays(1).toString();
            }

            // Aucune période d'essai configurée sur ce contrat
            if (dateFinEssaiStr == null && !aPeriodeEnCours) {
                return ResponseEntity.ok(EtatEssaiDTO.vide());
            }

            if (dateFinEssaiStr == null) {
                return ResponseEntity.ok(new EtatEssaiDTO(
                        false, false, false, false, nombreRenouvellements > 0,
                        null, dureeEssai, false, false,
                        periodeActuelle, nombreRenouvellements, maxRenouvellements, aPeriodeEnCours, null));
            }

            String todayStr = java.time.LocalDate.now().toString();
            boolean isDernierJour = dateFinEssaiStr.equals(todayStr);
            boolean isEssaiEchu = dateFinEssaiStr.compareTo(todayStr) < 0;

            // canConfirm : vrai si période EN_COURS et aucune confirmation planifiée
            boolean canConfirm = aPeriodeEnCours && !estConfirme;
            // Le renouvellement doit être conclu avant l'échéance. Une confirmation
            // tardive reste possible afin d'acter un oubli administratif.
            boolean canRenew = aPeriodeEnCours && !isEssaiEchu
                    && (nombreRenouvellements < maxRenouvellements) && !estConfirme;

            return ResponseEntity.ok(new EtatEssaiDTO(
                    aPeriodeEnCours, isDernierJour, isEssaiEchu, false, nombreRenouvellements > 0,
                    dateFinEssaiStr, dureeEssai, canConfirm, canRenew,
                    periodeActuelle, nombreRenouvellements, maxRenouvellements, aPeriodeEnCours, dateFinEssaiStr));

        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Contrat introuvable.");
            }
            return ResponseEntity.ok(enrichirContrat(contrat, c));
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Renseigner ou corriger la date de signature sans modifier les autres
     * données du contrat validé.
     * PATCH /employes/{idEmploye}/contrats/{idContrat}/date-signature
     */
    @PatchMapping("/contrats/{idContrat}/date-signature")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> renseignerDateSignature(
            @PathVariable Integer idEmploye,
            @PathVariable Integer idContrat,
            @RequestBody DateSignatureRequest req) {
        return transactionRunner.required(c -> {
            ContratTravail contrat = new ContratTravail().rechercherParId(idContrat, c);
            if (contrat == null || !contrat.getIdEmploye().equals(idEmploye)) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Contrat introuvable.");
            }
            if (req == null || req.getDateSignature() == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                        "La date de signature est obligatoire.");
            }

            String statutCode = resolveCodeStatutContrat(contrat.getIdStatutRh(), c);
            if ("BROUILLON".equals(statutCode) || "ANNULE".equals(statutCode)) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.CONTRAT_TRANSITION_INTERDITE,
                        "La date de signature ne peut être renseignée que sur un contrat validé.");
            }

            Date ancienneDate = contrat.getDateSignature();
            contrat.setDateSignature(req.getDateSignature());
            contrat.update(c);

            Map<String, Object> avant = new HashMap<>();
            avant.put("dateSignature", ancienneDate);
            Map<String, Object> apres = new HashMap<>();
            apres.put("dateSignature", req.getDateSignature());
            AuditLog.audit(c, "RENSEIGNEMENT_DATE_SIGNATURE_CONTRAT", "contrat_travail",
                    String.valueOf(idContrat), avant, apres);

            return ResponseEntity.ok(enrichirContrat(contrat, c));
        });
    }

    // =========================================================================
    // MODIFICATION D'UN BROUILLON
    // =========================================================================

    /**
     * Modifier un contrat en statut BROUILLON (avant validation DAF).
     * Retourne le workflow CPL mis à jour pour rafraîchir la page récapitulatif.
     *
     * Règle métier : Seul un contrat BROUILLON est modifiable.
     * Un contrat ACTIF ne peut pas être modifié directement (passer par un
     * avenant/workflow).
     *
     * PUT /employes/{idEmploye}/contrats/{idContrat}
     */
    @PutMapping("/contrats/{idContrat}")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierBrouillonContrat(
            @PathVariable Integer idEmploye,
            @PathVariable Integer idContrat,
            @RequestBody ContratModificationRequest req) {

        return transactionRunner.required(c -> {
                // Vérifier que le contrat appartient à cet employé
                ContratTravail contrat = new ContratTravail().rechercherParId(idContrat, c);
                if (contrat == null || !contrat.getIdEmploye().equals(idEmploye)) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Contrat introuvable.");
                }

                // Vérifier que c'est un BROUILLON
                String statutCode = resolveCodeStatutContrat(contrat.getIdStatutRh(), c);
                if (!"BROUILLON".equals(statutCode)) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.CONTRAT_TRANSITION_INTERDITE,
                            "Seul un contrat en statut BROUILLON peut être modifié. Statut actuel : "
                                    + statutCode + ". Utilisez un avenant ou un workflow de transition.");
                }

                // Mettre à jour les champs modifiables
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
                contrat.setComptabiliseEffectif(req.isComptabiliseEffectif());
                if (req.getDescription() != null)
                    contrat.setDescription(req.getDescription());
                if (req.getCommentaire() != null)
                    contrat.setCommentaire(req.getCommentaire());

                contrat.update(c);

                // Mettre à jour l'affectation si des données sont fournies
                if (req.hasAffectationData()) {
                    EmployeAffectationCarriere affectation = EmployeAffectationCarriere
                            .rechercherPlanifieeParEmploye(idEmploye, c);
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
                        if (req.getDateDebut() != null)
                            affectation.setDateDebut(req.getDateDebut());
                        if (req.getDescriptionAffectation() != null)
                            affectation.setDescription(req.getDescriptionAffectation());
                        if (req.getCommentaireAffectation() != null)
                            affectation.setCommentaire(req.getCommentaireAffectation());
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
                // Retourner le workflow CPL mis à jour (pour rafraîchir le récapitulatif)
                if (wf != null) {
                    WorkflowRhCPL recap = WorkflowRhCPL.rechercherParId(wf.getIdWorkflow(), c);
                    return ResponseEntity.ok(recap);
                }
                return ResponseEntity.ok(enrichirContrat(contrat, c));
        });
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Affectation liée à un contrat spécifique, quel que soit son statut
     * (ACTIVE, HISTORISE, PLANIFIE...).
     *
     * Contrairement à /affectations/active (qui ne renvoie que l'affectation en
     * cours),
     * cet endpoint permet de reconstituer fonction / site / département / catégorie
     * pro
     * d'un contrat déjà terminé — cas typique du certificat de travail, qui ne se
     * génère
     * justement que pour un contrat qui n'est plus actif.
     *
     * GET /employes/{idEmploye}/contrats/{idContrat}/affectation
     */
    @GetMapping("/contrats/{idContrat}/affectation")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAffectationParContrat(@PathVariable Integer idEmploye,
            @PathVariable Integer idContrat) {
        try (Connection c = dataSource.getConnection()) {
            ContratTravail contrat = new ContratTravail().rechercherParId(idContrat, c);
            if (contrat == null || !contrat.getIdEmploye().equals(idEmploye)) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Contrat introuvable.");
            }
            EmployeAffectationCarriere affectation = EmployeAffectationCarriere.rechercherParContrat(idContrat, c);
            if (affectation == null) {
                return ResponseEntity.ok(null);
            }
            return ResponseEntity.ok(enrichirAffectation(affectation, c));
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // MÉTHODES UTILITAIRES
    // =========================================================================

    private String resolveCodeStatutContrat(Integer id, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT code FROM ref_statut_rh WHERE id_statut_rh = ?")) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString(1);
            }
        }
        throw new IllegalStateException("Statut contrat introuvable (id=" + id + ").");
    }

    // ─── Helpers d'enrichissement de données pour l'IHM ─────────────────────────

    private String fetchLibelle(Connection c, String sql, Integer id) throws SQLException {
        if (id == null || id <= 0)
            return null;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    private Map<String, Object> enrichirContrat(ContratTravail ct, Connection c) throws SQLException {
        return enrichirContrat(ct, c, chargerReferencesContrat(ct.getIdContrat(), c), null);
    }

    private Map<String, Object> enrichirContrat(ContratTravail ct, Connection c, ContratReferences references,
            Map<String, Object> anciennetePrecalculee) {
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
        map.put("motifCddCode", ct.getMotifCddCode());
        map.put("dureeEssaiMois", ct.getDureeEssaiMois());
        map.put("dateFinEssai", ct.getDateFinEssai());
        map.put("comptabiliseEffectif", ct.isComptabiliseEffectif());
        map.put("description", ct.getDescription());
        map.put("commentaire", ct.getCommentaire());

        if (references != null && references.filiale != null) {
            Map<String, Object> filiale = references.filiale;
            if (filiale != null) {
                map.put("libelleFiliale", filiale.get("libelle"));
                // Objet complet transmis au front (adresse/NIF/STAT), utilisé notamment
                // par le certificat de travail dans son en-tête officiel.
                map.put("filiale", filiale);
            }
        }
        if (ct.getIdTypeContrat() != null && ct.getIdTypeContrat() > 0) {
            map.put("libelleTypeContrat", references == null ? null : references.typeContratLibelle);
            String codeType = references == null ? null : references.typeContratCode;
            map.put("codeTypeContrat", codeType);
            if (codeType != null) {
                map.put("isCdi", "CDI".equalsIgnoreCase(codeType));
                map.put("isCdd", "CDD".equalsIgnoreCase(codeType));
                map.put("isStage", "STAGE".equalsIgnoreCase(codeType));
                map.put("isConsultant", "CONSULTANT".equalsIgnoreCase(codeType));
                map.put("isPrestataire", "PRESTATAIRE".equalsIgnoreCase(codeType));
            }
        }
        if (ct.getIdTypeIntervenant() != null && ct.getIdTypeIntervenant() > 0) {
            map.put("libelleTypeIntervenant", references == null ? null : references.typeIntervenantLibelle);
        }
        if (ct.getIdStatutRh() != null && ct.getIdStatutRh() > 0) {
            if (references != null && references.statutRhPresent) {
                String codeStatut = references.statutRhCode;
                map.put("codeStatutContrat", codeStatut);
                map.put("libelleStatutContrat", references.statutRhLibelle);
                map.put("isActif", "ACTIF".equalsIgnoreCase(codeStatut));
                map.put("isBrouillon", "BROUILLON".equalsIgnoreCase(codeStatut));
                map.put("isPlanifie", "PLANIFIE".equalsIgnoreCase(codeStatut));
                map.put("isAnnule", "ANNULE".equalsIgnoreCase(codeStatut));
                map.put("isCloture", "CLOTURE".equalsIgnoreCase(codeStatut) || "HISTORISE".equalsIgnoreCase(codeStatut));
                map.put("isRompu", "ROMPU".equalsIgnoreCase(codeStatut));
            }
        }

        // Métriques d'ancienneté Backend-Driven
        if (ct.getIdContrat() != null && ct.getIdContrat() > 0) {
            Map<String, Object> ancMap = anciennetePrecalculee != null
                    ? anciennetePrecalculee
                    : ContratTravail.calculerAncienneteDetaillee(ct.getIdContrat(), c);
            map.putAll(ancMap);
        }

        // Métriques d'échéance et alertes (Fin de contrat & Fin d'essai) Backend-Driven
        java.time.LocalDate today = java.time.LocalDate.now();
        if (ct.getDateFin() != null) {
            java.time.LocalDate dFin = ct.getDateFin().toLocalDate();
            long diffFin = java.time.temporal.ChronoUnit.DAYS.between(today, dFin);
            map.put("joursRestantsFin", (int) diffFin);
            map.put("isFinEchue", diffFin < 0);
            map.put("isFinAujourdhui", diffFin == 0);
            map.put("isFinProche", diffFin >= 0 && diffFin <= 60);
        } else {
            map.put("joursRestantsFin", null);
            map.put("isFinEchue", false);
            map.put("isFinAujourdhui", false);
            map.put("isFinProche", false);
        }

        if (ct.getDateFinEssai() != null) {
            java.time.LocalDate dEssai = ct.getDateFinEssai().toLocalDate();
            long diffEssai = java.time.temporal.ChronoUnit.DAYS.between(today, dEssai);
            map.put("joursRestantsEssai", (int) diffEssai);
            map.put("isEssaiEchu", diffEssai < 0);
            map.put("isEssaiAujourdhui", diffEssai == 0);
            map.put("isEssaiProche", diffEssai >= 0 && diffEssai <= 60);
        } else {
            map.put("joursRestantsEssai", null);
            map.put("isEssaiEchu", false);
            map.put("isEssaiAujourdhui", false);
            map.put("isEssaiProche", false);
        }

        return map;
    }

    private Map<Integer, ContratReferences> chargerReferencesContratsEmploye(Integer idEmploye, Connection c)
            throws SQLException {
        String sql = "SELECT ct.id_contrat, rf.id_filiale AS filiale_id, rf.raison_sociale, rf.nif, rf.stat, "
                + "rtc.libelle AS type_contrat_libelle, "
                + "rtc.code AS type_contrat_code, rti.libelle AS type_intervenant_libelle, "
                + "rs.id_statut_rh AS statut_id, rs.code AS statut_code, rs.libelle AS statut_libelle "
                + "FROM contrat_travail ct "
                + "LEFT JOIN ref_filiale rf ON rf.id_filiale = ct.id_filiale "
                + "LEFT JOIN ref_type_contrat rtc ON rtc.id_type_contrat = ct.id_type_contrat "
                + "LEFT JOIN ref_type_intervenant rti ON rti.id_type_intervenant = ct.id_type_intervenant "
                + "LEFT JOIN ref_statut_rh rs ON rs.id_statut_rh = ct.id_statut_rh "
                + "WHERE ct.id_employe = ?";
        Map<Integer, ContratReferences> references = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    references.put(rs.getInt("id_contrat"), lireReferences(rs));
                }
            }
        }
        return references;
    }

    private ContratReferences chargerReferencesContrat(Integer idContrat, Connection c) throws SQLException {
        String sql = "SELECT ct.id_contrat, rf.id_filiale AS filiale_id, rf.raison_sociale, rf.nif, rf.stat, "
                + "rtc.libelle AS type_contrat_libelle, "
                + "rtc.code AS type_contrat_code, rti.libelle AS type_intervenant_libelle, "
                + "rs.id_statut_rh AS statut_id, rs.code AS statut_code, rs.libelle AS statut_libelle "
                + "FROM contrat_travail ct "
                + "LEFT JOIN ref_filiale rf ON rf.id_filiale = ct.id_filiale "
                + "LEFT JOIN ref_type_contrat rtc ON rtc.id_type_contrat = ct.id_type_contrat "
                + "LEFT JOIN ref_type_intervenant rti ON rti.id_type_intervenant = ct.id_type_intervenant "
                + "LEFT JOIN ref_statut_rh rs ON rs.id_statut_rh = ct.id_statut_rh "
                + "WHERE ct.id_contrat = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? lireReferences(rs) : new ContratReferences();
            }
        }
    }

    private ContratReferences lireReferences(ResultSet rs) throws SQLException {
        ContratReferences refs = new ContratReferences();
        String raisonSociale = rs.getString("raison_sociale");
        if (rs.getObject("filiale_id") != null) {
            refs.filiale = new HashMap<>();
            refs.filiale.put("libelle", raisonSociale);
            refs.filiale.put("nif", rs.getString("nif"));
            refs.filiale.put("stat", rs.getString("stat"));
        }
        refs.typeContratLibelle = rs.getString("type_contrat_libelle");
        refs.typeContratCode = rs.getString("type_contrat_code");
        refs.typeIntervenantLibelle = rs.getString("type_intervenant_libelle");
        refs.statutRhCode = rs.getString("statut_code");
        refs.statutRhLibelle = rs.getString("statut_libelle");
        refs.statutRhPresent = rs.getObject("statut_id") != null;
        return refs;
    }

    private static final class ContratReferences {
        private Map<String, Object> filiale;
        private String typeContratLibelle;
        private String typeContratCode;
        private String typeIntervenantLibelle;
        private String statutRhCode;
        private String statutRhLibelle;
        private boolean statutRhPresent;
    }

    private Map<String, Object> enrichirAffectation(EmployeAffectationCarriere aff, Connection c) throws SQLException {
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
            map.put("libelleDepartement", fetchLibelle(c,
                    "SELECT libelle FROM ref_departement WHERE id_departement = ?", aff.getIdDepartement()));
        }
        if (aff.getIdService() != null && aff.getIdService() > 0) {
            map.put("libelleService",
                    fetchLibelle(c, "SELECT libelle FROM ref_service WHERE id_service = ?", aff.getIdService()));
        }
        if (aff.getIdFonction() != null && aff.getIdFonction() > 0) {
            map.put("libelleFonction",
                    fetchLibelle(c, "SELECT libelle FROM ref_fonction WHERE id_fonction = ?", aff.getIdFonction()));
        }
        if (aff.getIdCategorieProfessionnelle() != null && aff.getIdCategorieProfessionnelle() > 0) {
            String catLib = fetchLibelle(c,
                    "SELECT libelle FROM ref_categorie_professionnelle WHERE id_categorie_professionnelle = ?",
                    aff.getIdCategorieProfessionnelle());
            map.put("libelleCategorieProfessionnelle", catLib);
            map.put("libelleCategoriePro", catLib);
        }
        if (aff.getIdModeCalculPaie() != null && aff.getIdModeCalculPaie() > 0) {
            String modeLib = fetchLibelle(c, "SELECT libelle FROM ref_mode_calcul_paie WHERE id_mode_calcul_paie = ?",
                    aff.getIdModeCalculPaie());
            map.put("libelleModeCalculPaie", modeLib);
            map.put("libelleModePaie", modeLib);
        }
        if (aff.getIdResponsableHierarchique() != null && aff.getIdResponsableHierarchique() > 0) {
            String respName = fetchLibelle(c,
                    "SELECT nom || ' ' || prenom FROM employe WHERE id_employe = ?",
                    aff.getIdResponsableHierarchique());
            map.put("nomPrenomResponsable", respName);
            map.put("nomResponsableHierarchique", respName);
        }

        return map;
    }

}
