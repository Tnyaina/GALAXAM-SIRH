package mg.galaxam.sirh.paie.api;


import jakarta.validation.Valid;
import java.sql.Connection;
import java.sql.Date;
import java.math.BigDecimal;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcPreparationDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcRecuDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcReglementRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcSituationReglementDTO;
import mg.galaxam.sirh.paie.api.dto.stc.AnnulationReglementRequest;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.persistence.PaieStc;
import mg.galaxam.sirh.paie.application.StcService;
import mg.galaxam.sirh.paie.application.StcPaymentReconciliationService;
import mg.galaxam.sirh.paie.application.PaymentEvidence;
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
 * Contrôleur REST pour la gestion, simulation et émission du Solde de Tout Compte (STC).
 * Base URL : /api/v1/paie/stc
 */
@RestController
@RequestMapping({"/api/v1/paie/stc", "/v1/paie/stc", "/paie/stc"})
public class StcController {

    private final DataSource dataSource;
    private final StcService stcService;
    private final StcPaymentReconciliationService reconciliation;

    public StcController(DataSource dataSource, StcService stcService, StcPaymentReconciliationService reconciliation) {
        this.dataSource = dataSource;
        this.stcService = stcService;
        this.reconciliation = reconciliation;
    }

    /**
     * Récupère l'ensemble des données préparatoires pour établir le STC d'un collaborateur
     * (ancienneté, prorata dernier mois, solde congés, taux journalier valorisé, solde des prêts).
     */
    @GetMapping("/preparer/{idEmploye}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> preparerStc(
            @PathVariable("idEmploye") Integer idEmploye,
            @RequestParam(name = "dateDepart", required = false) String dateDepart,
            @RequestParam(name = "motif", required = false, defaultValue = "DEMISSION") String motif) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Date dateSql;
            if (dateDepart != null && !dateDepart.isBlank()) {
                String cleanDate = dateDepart.contains("T") ? dateDepart.split("T")[0] : dateDepart.trim();
                try {
                    dateSql = Date.valueOf(java.time.LocalDate.parse(cleanDate));
                } catch (Exception e) {
                    throw new IllegalArgumentException("La date de départ doit être au format AAAA-MM-JJ.", e);
                }
            } else {
                dateSql = new Date(System.currentTimeMillis());
            }
            StcPreparationDTO prep = stcService.preparerStc(idEmploye, dateSql, motif, c);
            return ResponseEntity.ok(prep);
        }
    }

    /**
     * Simule le calcul du STC sans impacter les prêts ni persister d'enregistrement.
     */
    @PostMapping("/calculer")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> simulerStc(@Valid @RequestBody StcCalculRequest req) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            req.setSimulation(true);
            StcCalculResultDTO result = stcService.simulerStc(req, c);
            return ResponseEntity.ok(result);
        }
    }

    /**
     * Valide et émet définitivement le STC :
     * - Génère le reçu numéroté officiel (ex: STC-2026-0001)
     * - Impute les seuls acomptes versés et non imputés
     * - Enregistre le décompte détaillé dans paie_stc
     */
    @PostMapping("/valider")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerStc(@Valid @RequestBody StcCalculRequest req) throws Exception {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                StcCalculResultDTO result = stcService.validerStc(req, userId, c);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(result);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Liste tous les reçus pour solde de tout compte émis.
     */
    @GetMapping("/liste")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerStc() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            List<PaieStc> list = stcService.listerStc(c);
            return ResponseEntity.ok(list);
        }
    }

    /**
     * Liste les STC (historique / en cours) d'un collaborateur spécifique.
     */
    @GetMapping("/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerStcEmploye(@PathVariable("idEmploye") Integer idEmploye) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            List<PaieStc> list = stcService.listerStcEmploye(idEmploye, c);
            return ResponseEntity.ok(list);
        }
    }

    /**
     * Récupère le décompte détaillé d'un STC validé.
     */
    @GetMapping("/{idStc}/recu")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenirRecuStc(@PathVariable("idStc") Integer idStc) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            StcRecuDTO recu = stcService.obtenirRecuStc(idStc, c);
            if (recu == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,
                        "STC introuvable pour l'identifiant " + idStc + ".");
            }
            return ResponseEntity.ok(recu);
        }
    }

    /**
     * Enregistre un règlement (complet, partiel ou acquittement solde nul) sur le STC scellé.
     */
    @PostMapping({"/{idStc}/regler", "/{idStc}/reglement"})
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<StcSituationReglementDTO> enregistrerReglement(
            @PathVariable("idStc") Integer idStc,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody StcReglementRequest req) throws Exception {
        throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                "Utilisez le règlement STC justifié avec une pièce réelle et la part mensuelle.");
    }

    @PostMapping(value = "/{idStc}/reglement-justifie", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<StcSituationReglementDTO> enregistrerReglementJustifie(
            @PathVariable("idStc") Integer idStc,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestParam String dateReglement, @RequestParam String referenceReglement,
            @RequestParam BigDecimal montantRegle, @RequestParam String modeReglement,
            @RequestParam(defaultValue = "0") BigDecimal partBulletinMensuel,
            @RequestParam(value = "file", required = false) MultipartFile file) throws Exception {
        PaymentEvidence preuve = montantRegle != null && montantRegle.signum() > 0 ? PaymentEvidence.from(file) : null;
        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "Clé d'idempotence obligatoire.");
        Integer userId = getCurrentUserId();
        StcReglementRequest req = new StcReglementRequest(Date.valueOf(dateReglement), referenceReglement,
                montantRegle, modeReglement, null, idempotencyKey.trim());
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                StcSituationReglementDTO sit = stcService.enregistrerReglement(idStc, req, userId, c);
                if (preuve != null) reconciliation.affecter(c, idStc,
                        reconciliation.idReglement(c, idStc, idempotencyKey.trim()), partBulletinMensuel, preuve, userId);
                else if (partBulletinMensuel != null && partBulletinMensuel.signum() > 0)
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "Une affectation mensuelle exige une preuve réelle.");
                sit = stcService.obtenirSituationReglements(idStc, c);
                c.commit();
                return ResponseEntity.ok(sit);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Récupère la situation financière complète des règlements du STC (total réglé, reste à payer, historique).
     */
    @GetMapping("/{idStc}/reglements")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<StcSituationReglementDTO> obtenirSituationReglements(@PathVariable("idStc") Integer idStc) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            StcSituationReglementDTO sit = stcService.obtenirSituationReglements(idStc, c);
            if (sit == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "STC introuvable : " + idStc);
            }
            return ResponseEntity.ok(sit);
        }
    }

    /**
     * Annule de manière motivée et traçable une ligne de versement de STC.
     */
    @PostMapping("/{idStc}/reglement/{idReglement}/annuler")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<StcSituationReglementDTO> annulerReglement(
            @PathVariable("idStc") Integer idStc,
            @PathVariable("idReglement") Integer idReglement,
            @Valid @RequestBody AnnulationReglementRequest req) throws Exception {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                StcSituationReglementDTO sit = stcService.annulerReglement(idStc, idReglement, req, userId, c);
                c.commit();
                return ResponseEntity.ok(sit);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getIdUtilisateur();
        }
        return 1;
    }

}
