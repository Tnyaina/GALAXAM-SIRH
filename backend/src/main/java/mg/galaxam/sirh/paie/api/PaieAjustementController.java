package mg.galaxam.sirh.paie.api;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.dto.BusinessDecision;
import mg.galaxam.sirh.paie.api.dto.PaieAjustementRequest;
import mg.galaxam.sirh.paie.api.dto.ConfirmationVersementRequest;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import mg.galaxam.sirh.paie.application.ManualVariableAmountService;
import mg.galaxam.sirh.paie.application.PayrollAdjustmentPaymentService;
import mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowCommands.PayrollAdjustmentCommand;
import mg.galaxam.sirh.paie.application.workflow.PayrollAdjustmentCommandRequest;
import mg.galaxam.sirh.paie.application.workflow.PayrollAdjustmentWorkflowService;
import mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowCommands.PayrollAction;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodUserContext;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodWorkflowService;
import mg.galaxam.sirh.paieconfig.persistence.RefTypeMouvementPaie;
import mg.galaxam.sirh.securite.AuditLog;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Contrôleur REST pour la saisie et la gestion des variables de paie, acomptes,
 * avances et primes.
 * Base URL : /api/v1/paie/ajustements
 *
 * <p>
 * Le workflow des ajustements passe désormais par
 * {@link PayrollAdjustmentWorkflowService} :
 * le client demande une commande ({@link PayrollAdjustmentCommand}), le backend
 * décide la transition.
 */
@RestController
@RequestMapping({ "/api/v1/paie/ajustements", "/v1/paie/ajustements", "/paie/ajustements" })
public class PaieAjustementController {

    private final DataSource dataSource;
    private final PayrollAdjustmentWorkflowService adjustmentWorkflowService;
    private final PayrollPeriodWorkflowService periodWorkflowService;
    private final ManualVariableAmountService manualVariableAmountService;
    private final PayrollAdjustmentPaymentService paymentService;
    private final mg.galaxam.sirh.paie.application.PayrollAdjustmentCreationService adjustmentCreationService;
    private final mg.galaxam.sirh.paie.application.PayrollAdjustmentSummaryService adjustmentSummaryService;

    public PaieAjustementController(
            DataSource dataSource,
            PayrollAdjustmentWorkflowService adjustmentWorkflowService,
            PayrollPeriodWorkflowService periodWorkflowService,
            ManualVariableAmountService manualVariableAmountService,
            PayrollAdjustmentPaymentService paymentService,
            mg.galaxam.sirh.paie.application.PayrollAdjustmentCreationService adjustmentCreationService,
            mg.galaxam.sirh.paie.application.PayrollAdjustmentSummaryService adjustmentSummaryService) {
        this.dataSource = dataSource;
        this.adjustmentWorkflowService = adjustmentWorkflowService;
        this.periodWorkflowService = periodWorkflowService;
        this.manualVariableAmountService = manualVariableAmountService;
        this.paymentService = paymentService;
        this.adjustmentCreationService = adjustmentCreationService;
        this.adjustmentSummaryService = adjustmentSummaryService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> resumeAjustements(@RequestParam(name = "idPeriode", required = false) Integer idPeriode)
            throws Exception {
        return ResponseEntity.ok(adjustmentSummaryService.summarize(idPeriode));
    }

    @PostMapping("/variables/preview")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> previewVariable(@Valid @RequestBody PaieAjustementRequest request,
            Authentication authentication) throws Exception {
        if (request.getIdPeriodePaie() == null) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "La période de paie est obligatoire pour simuler une variable.");
        }
        periodWorkflowService.requirePayrollAction(request.getIdPeriodePaie(), PayrollAction.ADD_VARIABLE,
                PayrollPeriodUserContext.from(authentication));
        try (Connection connection = dataSource.getConnection()) {
            RefTypeMouvementPaie type = request.getIdTypeMouvement() != null
                    ? RefTypeMouvementPaie.trouverParId(request.getIdTypeMouvement(), connection)
                    : RefTypeMouvementPaie.trouverParCode(request.getTypeMouvement(), connection);
            return ResponseEntity.ok(manualVariableAmountService.calculate(request, type, connection));
        }
    }

    /**
     * Liste les ajustements, primes et avances filtrés par période et/ou employé.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerAjustements(
            @RequestParam(name = "idPeriode", required = false) Integer idPeriode,
            @RequestParam(name = "idEmploye", required = false) Integer idEmploye,
            @RequestParam(name = "domaine", required = false) String domaine,
            Authentication authentication) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            StringBuilder sql = new StringBuilder(
                    "SELECT paa.*, " +
                            "e.matricule, TRIM(CONCAT(COALESCE(e.nom, ''), ' ', COALESCE(e.prenom, ''))) AS nom_complet, "
                            +
                            "COALESCE(rtmp.code, paa.type_mouvement) AS type_code, " +
                            "COALESCE(rtmp.libelle, paa.type_mouvement) AS type_libelle, " +
                            "COALESCE(rtmp.sens, paa.sens) AS type_sens, " +
                            "rtmp.categorie AS type_categorie, rtmp.nature AS type_nature, pr.soumis_irsa AS type_imposable, "
                            +
                            "pr.soumis_cnaps AS type_cotisable " +
                            ", pj.nom_fichier AS justificatif_nom, pj.type_mime AS justificatif_mime, " +
                            "pj.created_at AS justificatif_date_depot " +
                            "FROM paie_avance_acompte paa " +
                            "JOIN employe e ON paa.id_employe = e.id_employe " +
                            "LEFT JOIN ref_type_mouvement_paie rtmp ON paa.id_type_mouvement = rtmp.id_type_mouvement "
                            +
                            "LEFT JOIN paie_rubrique pr ON pr.id_rubrique = rtmp.id_rubrique " +
                            "LEFT JOIN paie_mouvement_justificatif pj ON pj.id_avance = paa.id_avance " +
                            "WHERE 1=1 ");

            List<Object> params = new ArrayList<>();
            if (idPeriode != null) {
                sql.append("AND paa.id_periode_paie = ? ");
                params.add(idPeriode);
            }
            if (idEmploye != null) {
                sql.append("AND paa.id_employe = ? ");
                params.add(idEmploye);
            }
            if ("ACOMPTE_AVANCE".equals(domaine)) {
                sql.append("AND rtmp.categorie IN ('ACOMPTE', 'AVANCE') ");
            }
            sql.append("ORDER BY paa.date_demande DESC, paa.id_avance DESC");

            List<Map<String, Object>> resultats = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("idAvance", rs.getInt("id_avance"));
                        map.put("idEmploye", rs.getInt("id_employe"));
                        map.put("matricule", rs.getString("matricule"));
                        map.put("nomComplet", rs.getString("nom_complet"));
                        map.put("idPeriodePaie", rs.getObject("id_periode_paie"));
                        map.put("idTypeMouvement", rs.getObject("id_type_mouvement"));
                        map.put("idCongeCutoverCorrection", rs.getObject("id_conge_cutover_correction"));
                        map.put("typeMouvement", rs.getString("type_code"));
                        map.put("typeLibelle", rs.getString("type_libelle"));
                        map.put("sens", rs.getString("type_sens"));
                        map.put("categorie", rs.getString("type_categorie"));
                        map.put("nature", rs.getString("type_nature"));
                        map.put("imposable", rs.getObject("type_imposable"));
                        map.put("cotisable", rs.getObject("type_cotisable"));
                        map.put("montant", rs.getBigDecimal("montant"));
                        map.put("quantiteSaisie",rs.getBigDecimal("quantite_saisie"));map.put("tauxSaisi",rs.getBigDecimal("taux_saisi"));
                        map.put("montantInitial", rs.getBigDecimal("montant_initial"));
                        map.put("montantImpute", rs.getBigDecimal("montant_impute"));
                        map.put("soldeRestant", rs.getBigDecimal("solde_restant"));
                        map.put("provenance", rs.getString("provenance"));
                        map.put("dateDemande", rs.getDate("date_demande"));
                        map.put("datePaiement", rs.getDate("date_paiement"));
                        map.put("versementConfirmeAt", rs.getTimestamp("versement_confirme_at"));
                        map.put("dateVersementConfirme",
                                rs.getTimestamp("versement_confirme_at") != null ? rs.getDate("date_paiement") : null);
                        map.put("referenceVersement", rs.getString("reference_versement"));
                        map.put("dateAccord", rs.getDate("date_accord"));
                        map.put("motif", rs.getString("motif"));
                        String justificatifNom = rs.getString("justificatif_nom");
                        if (justificatifNom != null) {
                            justificatifNom = nomAffichageJustificatif(justificatifNom,
                                    rs.getTimestamp("justificatif_date_depot"), rs.getString("justificatif_mime"));
                        }
                        map.put("justificatifNom", justificatifNom);
                        map.put("justificatifPresent", justificatifNom != null);
                        map.put("statut", rs.getString("statut"));
                        String movementCode = rs.getString("type_code");
                        String movementStatus = rs.getString("statut");
                        boolean paymentConfirmed = rs.getTimestamp("versement_confirme_at") != null;
                        BigDecimal amountApplied = rs.getBigDecimal("montant_impute");
                        boolean imputed = "IMPUTE_PAIE".equals(movementStatus)
                                || amountApplied != null && amountApplied.signum() > 0;
                        boolean manualDafVariable = "VARIABLE_DAF".equals(rs.getString("provenance"));
                        boolean canCancelImputed = imputed && manualDafVariable && rs.getObject("id_type_mouvement") != null
                                && rs.getObject("id_bulletin") != null
                                && rs.getObject("id_periode_paie") != null
                                && adjustmentWorkflowService.canCancelImputedVariable(c, rs.getInt("id_avance"),
                                        rs.getInt("id_periode_paie"), rs.getInt("id_bulletin"), false);
                        boolean hasProof = justificatifNom != null;
                        map.put("workflowActions", adjustmentWorkflowService.describeActions(movementStatus,
                                PayrollPeriodUserContext.from(authentication).hasAny("PAIE_WRITE", "ROLE_ADMIN"),
                                paymentConfirmed, imputed, canCancelImputed,
                                "NOTE_DE_FRAIS".equals(movementCode), hasProof));
                        Timestamp uAt = rs.getTimestamp("updated_at");
                        map.put("updatedAt", uAt);
                        map.put("version", uAt != null ? (int) (uAt.getTime() % Integer.MAX_VALUE) : 1);
                        map.put("idBulletin", rs.getObject("id_bulletin"));
                        map.put("modeReglement", rs.getString("mode_reglement"));
                        String categorie = rs.getString("type_categorie");
                        // Les attributs de versement concernent seulement les dossiers d'acompte/avance.
                        // Toutes les lignes, dont les variables manuelles, doivent rester dans la liste.
                        if ("ACOMPTE".equals(categorie) || "AVANCE".equals(categorie)) {
                            String statut = rs.getString("statut");
                            boolean canWrite = PayrollPeriodUserContext.from(authentication).hasAny("PAIE_WRITE");
                            var dossier = paymentService.describe(categorie, statut,
                                    rs.getTimestamp("versement_confirme_at"), rs.getBigDecimal("montant_impute"),
                                    rs.getBigDecimal("solde_restant"), canWrite);
                            map.put("etatDossier", dossier.etat());
                            map.put("actions", dossier.actions());
                            boolean isGain = "GAIN".equalsIgnoreCase(rs.getString("type_sens"));
                            map.put("isGain", isGain);
                            map.put("isRetenue", !isGain);
                            map.put("isAcompte", "ACOMPTE".equalsIgnoreCase(categorie));
                            map.put("isAvance", "AVANCE".equalsIgnoreCase(categorie));
                        }
                        resultats.add(map);
                    }
                }
            }
            return ResponseEntity.ok(resultats);
        }
    }

    /**
     * Liste les types de mouvements de paie actifs (primes, déductions, acomptes).
     */
    @GetMapping("/conge-cutover-sources")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerCorrectionsConges(@RequestParam int idEmploye) throws Exception {
        List<Map<String, Object>> sources = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT cc.id_correction, cc.date_correction, cc.regularisation_a_examiner, cc.motif, "
                        + "a.id_avance, a.statut FROM conge_cutover_correction cc "
                        + "LEFT JOIN paie_avance_acompte a ON a.id_conge_cutover_correction=cc.id_correction "
                        + "AND a.statut NOT IN ('REJETE','ANNULE') "
                        + "WHERE cc.id_employe=? AND cc.regularisation_a_examiner > 0 "
                        + "ORDER BY cc.date_correction DESC, cc.id_correction DESC")) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> source = new HashMap<>();
                    source.put("idCorrection", rs.getInt("id_correction"));
                    source.put("dateCorrection", rs.getTimestamp("date_correction"));
                    source.put("montant", rs.getBigDecimal("regularisation_a_examiner"));
                    source.put("motif", rs.getString("motif"));
                    source.put("idAjustement", rs.getObject("id_avance"));
                    source.put("statutAjustement", rs.getString("statut"));
                    sources.add(source);
                }
            }
        }
        return ResponseEntity.ok(sources);
    }

    @GetMapping("/types-mouvements")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerTypesMouvements(
            @RequestParam(name = "idPeriode", required = false) Integer idPeriode,
            Authentication authentication) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            List<Map<String, Object>> types = new ArrayList<>();
            String sql = "SELECT t.code, t.libelle, t.sens, t.categorie, t.nature, t.description, " +
                    "r.soumis_irsa AS imposable, r.soumis_cnaps AS cotisable, t.id_rubrique " +
                    "FROM ref_type_mouvement_paie t " +
                    "LEFT JOIN paie_rubrique r ON r.id_rubrique = t.id_rubrique " +
                    "WHERE t.est_actif = TRUE AND t.saisie_manuelle_autorisee = TRUE " +
                    "AND (t.owner_code IN ('DAF_PAIE', 'TRESORERIE') OR t.code = 'REGULARISATION_CONGE') " +
                    "ORDER BY t.ordre_affichage, t.libelle";
            try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> type = new HashMap<>();
                    type.put("code", rs.getString("code"));
                    type.put("libelle", rs.getString("libelle"));
                    type.put("sens", rs.getString("sens"));
                    type.put("categorie", rs.getString("categorie"));
                    type.put("nature", rs.getString("nature"));
                    type.put("description", rs.getString("description"));
                    type.put("imposable", rs.getObject("imposable"));
                    type.put("cotisable", rs.getObject("cotisable"));
                    if("VARIABLE_DAF".equals(rs.getString("nature")) && rs.getObject("id_rubrique")!=null) {
                        var rule=mg.galaxam.sirh.paie.persistence.PaieRubrique.trouverParId(rs.getInt("id_rubrique"),c);
                        if(idPeriode!=null) {
                            try(var period=c.prepareStatement("SELECT date_debut,date_fin FROM periode_paie WHERE id_periode_paie=?")) {
                                period.setInt(1,idPeriode); try(var dates=period.executeQuery()) {
                                    if(dates.next()) {
                                        try { rule=mg.galaxam.sirh.paie.application.ManualRubriqueResolver.resolve(c,rs.getInt("id_rubrique"),dates.getDate(1).toLocalDate(),dates.getDate(2).toLocalDate()); }
                                        catch(SirhBusinessException e) { type.put("configurationError",e.getMessage()); }
                                    }
                                }
                            }
                        }
                        type.put("methodeCalcul",rule.getMethodeCalcul()); type.put("politiqueArrondi",rule.getPolitiqueArrondi());
                        type.put("imposable",rule.isSoumisIrsa()); type.put("cotisable",rule.isSoumisCnaps()); type.put("soumisSante",rule.isSoumisSante());
                        type.put("rubriqueVersion",rule.getCode());
                    }
                    types.add(type);
                }
            }
            return ResponseEntity.ok(types);
        }
    }

    @GetMapping("/modes-reglement")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerModesReglement() throws Exception {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT code, libelle FROM ref_mode_paiement WHERE actif = TRUE ORDER BY libelle");
                ResultSet rs = ps.executeQuery()) {
            List<Map<String, String>> modes = new ArrayList<>();
            while (rs.next())
                modes.add(Map.of("code", rs.getString("code"), "libelle", rs.getString("libelle")));
            return ResponseEntity.ok(modes);
        }
    }

    @GetMapping("/modes-paiement")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerTousModesPaiement() throws Exception {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id_mode_paiement, code, libelle, exige_rib, inclus_fichier_virement, actif " +
                                "FROM ref_mode_paiement ORDER BY id_mode_paiement");
                ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> modes = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("idModePaiement", rs.getInt("id_mode_paiement"));
                map.put("code", rs.getString("code"));
                map.put("libelle", rs.getString("libelle"));
                map.put("exigeRib", rs.getBoolean("exige_rib"));
                map.put("inclusFichierVirement", rs.getBoolean("inclus_fichier_virement"));
                map.put("actif", rs.getBoolean("actif"));
                modes.add(map);
            }
            return ResponseEntity.ok(modes);
        }
    }

    @PutMapping("/modes-paiement/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierModePaiement(
            @PathVariable("id") int id,
            @RequestBody Map<String, Object> payload) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String checkSql = "SELECT id_mode_paiement, code, libelle, exige_rib, inclus_fichier_virement, actif FROM ref_mode_paiement WHERE id_mode_paiement = ?";
                Map<String, Object> old = new HashMap<>();
                try (PreparedStatement psCheck = c.prepareStatement(checkSql)) {
                    psCheck.setInt(1, id);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (!rs.next()) {
                            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                    .body(Map.of("message", "Mode de paiement introuvable"));
                        }
                        old.put("libelle", rs.getString("libelle"));
                        old.put("exigeRib", rs.getBoolean("exige_rib"));
                        old.put("inclusFichierVirement", rs.getBoolean("inclus_fichier_virement"));
                        old.put("actif", rs.getBoolean("actif"));
                    }
                }

                String libelle = payload.containsKey("libelle") && payload.get("libelle") != null
                        ? ((String) payload.get("libelle")).trim()
                        : (String) old.get("libelle");
                boolean exigeRib = payload.containsKey("exigeRib") ? Boolean.TRUE.equals(payload.get("exigeRib"))
                        : (boolean) old.get("exigeRib");
                boolean inclusVirement = payload.containsKey("inclusFichierVirement")
                        ? Boolean.TRUE.equals(payload.get("inclusFichierVirement"))
                        : (boolean) old.get("inclusFichierVirement");
                boolean actif = payload.containsKey("actif") ? Boolean.TRUE.equals(payload.get("actif"))
                        : (boolean) old.get("actif");

                String updateSql = "UPDATE ref_mode_paiement SET libelle = ?, exige_rib = ?, inclus_fichier_virement = ?, actif = ? WHERE id_mode_paiement = ?";
                try (PreparedStatement psUp = c.prepareStatement(updateSql)) {
                    psUp.setString(1, libelle);
                    psUp.setBoolean(2, exigeRib);
                    psUp.setBoolean(3, inclusVirement);
                    psUp.setBoolean(4, actif);
                    psUp.setInt(5, id);
                    psUp.executeUpdate();
                }

                AuditLog.audit(c, "MODIFICATION", "ref_mode_paiement", String.valueOf(id), old, payload);
                c.commit();
                return ResponseEntity.ok(Map.of("success", true, "message", "Mode de paiement mis à jour"));
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @GetMapping("/acomptes-avances/synthese")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> syntheseAcomptesAvances() throws Exception {
        String sql = "SELECT " +
                "COUNT(*) FILTER (WHERE a.statut = 'EN_ATTENTE') AS a_approuver, " +
                "COUNT(*) FILTER (WHERE a.statut = 'APPROUVE' AND a.versement_confirme_at IS NULL " +
                "AND a.montant_impute = 0) AS a_verser, " +
                "COUNT(*) FILTER (WHERE a.versement_confirme_at IS NULL AND a.montant_impute > 0) AS a_verifier, " +
                "COUNT(*) FILTER (WHERE a.versement_confirme_at IS NOT NULL AND a.solde_restant > 0) AS en_recouvrement, "
                +
                "COALESCE(SUM(a.solde_restant) FILTER (WHERE a.versement_confirme_at IS NOT NULL " +
                "AND a.statut IN ('APPROUVE','IMPUTE_PAIE')), 0) AS encours_verse " +
                "FROM paie_avance_acompte a JOIN ref_type_mouvement_paie t " +
                "ON t.id_type_mouvement = a.id_type_mouvement " +
                "WHERE t.categorie IN ('ACOMPTE','AVANCE')";
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return ResponseEntity.ok(Map.of(
                    "aApprouver", rs.getInt("a_approuver"),
                    "aVerser", rs.getInt("a_verser"),
                    "aVerifier", rs.getInt("a_verifier"),
                    "enRecouvrement", rs.getInt("en_recouvrement"),
                    "encoursVerse", rs.getBigDecimal("encours_verse")));
        }
    }

    @GetMapping("/{id}/imputations")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerImputations(@PathVariable("id") int id) throws Exception {
        String sql = "SELECT i.id_bulletin, i.montant, p.code AS code_periode, b.statut AS statut_bulletin " +
                "FROM paie_mouvement_imputation i JOIN bulletin_paie b ON b.id_bulletin = i.id_bulletin " +
                "JOIN periode_paie p ON p.id_periode_paie = b.id_periode_paie " +
                "WHERE i.id_avance = ? ORDER BY p.date_debut, i.id_bulletin";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next())
                    rows.add(Map.of(
                            "idBulletin", rs.getInt("id_bulletin"),
                            "montant", rs.getBigDecimal("montant"),
                            "codePeriode", rs.getString("code_periode"),
                            "statutBulletin", rs.getString("statut_bulletin")));
                return ResponseEntity.ok(rows);
            }
        }
    }

    @PostMapping("/{id}/versement")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> confirmerVersement(@PathVariable("id") int id,
            @Valid @RequestBody ConfirmationVersementRequest request) throws Exception {
        paymentService.confirm(id, request, getCurrentUserId());
        return ResponseEntity.ok(Map.of("idAvance", id, "versementConfirme", true));
    }

    /**
     * Enregistre un nouvel ajustement, avance ou prime.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerAjustement(
            @Valid @RequestBody PaieAjustementRequest req,
            Authentication authentication) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                adjustmentCreationService.create(req, authentication, getCurrentUserId()));
    }

    @PostMapping(value = "/{id}/justificatif-frais", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deposerJustificatifFrais(@PathVariable("id") int id,
            @RequestParam("file") MultipartFile file, Authentication authentication) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "Le justificatif doit être un fichier non vide de 5 Mo maximum.");
        }
        byte[] bytes = file.getBytes();
        String mime;
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') {
            mime = "application/pdf";
        } else if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            mime = "image/png";
        } else if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            mime = "image/jpeg";
        } else {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "Utilisez un justificatif PDF, PNG ou JPEG.");
        }
        String extension = "application/pdf".equals(mime) ? "pdf" : "image/png".equals(mime) ? "png" : "jpg";
        String uploadDate = mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today()
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String name = "JUSTIFICATIF_FRAIS_" + uploadDate + "_" + shortId + "." + extension;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT a.statut, a.id_periode_paie, t.code FROM paie_avance_acompte a JOIN ref_type_mouvement_paie t "
                                + "ON t.id_type_mouvement=a.id_type_mouvement WHERE a.id_avance=? FOR UPDATE OF a")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || !"NOTE_DE_FRAIS".equals(rs.getString("code"))
                                || !"EN_ATTENTE".equals(rs.getString("statut"))) {
                            throw new SirhBusinessException(SirhErrorCode.STATE_CONFLICT,
                                    "Le justificatif ne peut être déposé que sur une note de frais en attente.");
                        }
                        periodWorkflowService.requirePayrollAction(rs.getInt("id_periode_paie"),
                                PayrollAction.ADD_VARIABLE, PayrollPeriodUserContext.from(authentication));
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO paie_mouvement_justificatif "
                                + "(id_avance,nom_fichier,type_mime,contenu,taille_octets,depose_par) "
                                + "VALUES (?,?,?,?,?,?) ON CONFLICT (id_avance) DO UPDATE SET "
                                + "nom_fichier=EXCLUDED.nom_fichier,type_mime=EXCLUDED.type_mime,"
                                + "contenu=EXCLUDED.contenu,taille_octets=EXCLUDED.taille_octets,"
                                + "depose_par=EXCLUDED.depose_par,created_at=CURRENT_TIMESTAMP")) {
                    ps.setInt(1, id); ps.setString(2, name); ps.setString(3, mime);
                    ps.setBytes(4, bytes); ps.setLong(5, bytes.length);
                    Integer userId = getCurrentUserId();
                    if (userId == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setInt(6, userId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE paie_avance_acompte SET updated_at=CURRENT_TIMESTAMP WHERE id_avance=?")) {
                    ps.setInt(1, id); ps.executeUpdate();
                }
                AuditLog.audit(c, "DEPOT_JUSTIFICATIF", "paie_avance_acompte", String.valueOf(id), null, name);
                c.commit();
                return ResponseEntity.ok(Map.of("idAvance", id, "justificatifNom", name));
            } catch (Exception ex) { c.rollback(); throw ex; }
        }
    }

    @GetMapping("/{id}/justificatif-frais")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> telechargerJustificatifFrais(@PathVariable("id") int id) throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT j.nom_fichier,j.type_mime,j.contenu,j.created_at FROM paie_mouvement_justificatif j "
                        + "JOIN paie_avance_acompte a ON a.id_avance=j.id_avance "
                        + "JOIN ref_type_mouvement_paie t ON t.id_type_mouvement=a.id_type_mouvement "
                        + "WHERE j.id_avance=? AND t.code='NOTE_DE_FRAIS'")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Justificatif de frais introuvable.");
                String mime = rs.getString("type_mime");
                String downloadName = nomAffichageJustificatif(rs.getString("nom_fichier"),
                        rs.getTimestamp("created_at"), mime);
                return ResponseEntity.ok().contentType(MediaType.parseMediaType(rs.getString("type_mime")))
                        .header("Content-Disposition", "attachment; filename=\"" + downloadName + "\"")
                        .header("Cache-Control", "private, no-store")
                        .body(rs.getBytes("contenu"));
            }
        }
    }

    /**
     * Exécute une commande métier sur un ajustement de paie.
     *
     * <p>
     * Remplace l'ancien endpoint PUT /{id}/statut (mutation directe d'état).
     * Le client fournit une commande explicite (APPROVE, REJECT, CANCEL) ;
     * le backend décide si la transition est autorisée.
     */
    @PostMapping("/{id}/commands")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BusinessDecision> executeCommand(
            @PathVariable("id") int id,
            @Valid @RequestBody PayrollAdjustmentCommandRequest req,
            Authentication authentication) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            PaieAvanceAcompte entity = PaieAvanceAcompte.trouverParId(id, c);
            if (entity == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Ajustement de paie introuvable.");
            }
            if (entity.getIdPeriodePaie() == null) {
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                        "Un mouvement sans période ne peut pas être modifié par le workflow de campagne.");
            }
            periodWorkflowService.requirePayrollAction(entity.getIdPeriodePaie(), PayrollAction.ADD_VARIABLE,
                    PayrollPeriodUserContext.from(authentication));
        }
        Integer userId = getCurrentUserId();
        BusinessDecision decision = adjustmentWorkflowService.execute(
                id, req.getCommand(), userId, req.getReason(), req.getExpectedVersion(), req.getDateAccord());
        return ResponseEntity.ok(decision);
    }


    /**
     * Supprime un ajustement (si non encore imputé).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> supprimerAjustement(@PathVariable("id") int id) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieAvanceAcompte entity = PaieAvanceAcompte.trouverParId(id, c);
                if (entity == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,
                            "Ajustement de paie introuvable.");
                }
                if (entity.getIdPeriodePaie() == null) {
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                            "Un mouvement sans période ne peut pas être modifié par le workflow de campagne.");
                }
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                periodWorkflowService.requirePayrollAction(entity.getIdPeriodePaie(), PayrollAction.ADD_VARIABLE,
                        PayrollPeriodUserContext.from(authentication));

                if ("IMPUTE_PAIE".equalsIgnoreCase(entity.getStatut())
                        || entity.getVersementConfirmeAt() != null
                        || (entity.getMontantImpute() != null && entity.getMontantImpute().signum() > 0)) {
                    throw new SirhBusinessException(SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED,
                            "Impossible de supprimer un mouvement déjà versé ou imputé sur un bulletin de paie.");
                }

                entity.delete(c);
                AuditLog.audit(c, "SUPPRESSION", "paie_avance_acompte", String.valueOf(id), entity, null);

                c.commit();
                return ResponseEntity.ok(Map.of("success", true, "message", "Ajustement supprimé avec succès."));
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    private static String nomAffichageJustificatif(String storedName, Timestamp uploadedAt, String mime) {
        if (storedName != null && storedName.matches("JUSTIFICATIF_FRAIS_[0-9]{8}_[a-f0-9]{8}\\.(pdf|png|jpg)")) {
            return storedName;
        }
        String extension = "application/pdf".equals(mime) ? "pdf" : "image/png".equals(mime) ? "png" : "jpg";
        LocalDate uploadedDate = uploadedAt == null ? mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today()
                : uploadedAt.toLocalDateTime().toLocalDate();
        String shortHash = Integer.toUnsignedString(storedName == null ? 0 : storedName.hashCode(), 16);
        if (shortHash.length() > 8) shortHash = shortHash.substring(shortHash.length() - 8);
        return "JUSTIFICATIF_FRAIS_" + uploadedDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                + "_HISTORIQUE_" + shortHash + "." + extension;
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getIdUtilisateur();
        }
        return null;
    }

}
