package mg.galaxam.sirh.api.controller.paie;

import jakarta.validation.Valid;
import java.sql.Connection;
import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculRequest;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculResultDTO;
import mg.galaxam.sirh.api.dto.paie.stc.StcPreparationDTO;
import mg.galaxam.sirh.api.dto.paie.stc.StcRecuDTO;
import mg.galaxam.sirh.model.paie.PaieStc;
import mg.galaxam.sirh.model.paie.service.StcService;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour la gestion, simulation et émission du Solde de Tout Compte (STC).
 * Base URL : /api/v1/paie/stc
 */
@RestController
@RequestMapping({"/api/v1/paie/stc", "/v1/paie/stc", "/paie/stc"})
public class StcController {

    private final DataSource dataSource;
    private final StcService stcService;

    public StcController(DataSource dataSource, StcService stcService) {
        this.dataSource = dataSource;
        this.stcService = stcService;
    }

    /**
     * Récupère l'ensemble des données préparatoires pour établir le STC d'un collaborateur
     * (ancienneté, prorata dernier mois, solde congés, taux journalier valorisé, solde des prêts).
     */
    @GetMapping("/preparer/{idEmploye}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> preparerStc(
            @PathVariable("idEmploye") Integer idEmploye,
            @RequestParam(name = "dateDepart", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate dateDepart,
            @RequestParam(name = "motif", required = false, defaultValue = "DEMISSION") String motif) {
        try (Connection c = dataSource.getConnection()) {
            Date dateSql = dateDepart != null ? Date.valueOf(dateDepart) : new Date(System.currentTimeMillis());
            StcPreparationDTO prep = stcService.preparerStc(idEmploye, dateSql, motif, c);
            return ResponseEntity.ok(prep);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(createErrorResponse(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de la préparation du STC : " + ex.getMessage()));
        }
    }

    /**
     * Simule le calcul du STC sans impacter les prêts ni persister d'enregistrement.
     */
    @PostMapping("/calculer")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> simulerStc(@Valid @RequestBody StcCalculRequest req) {
        try (Connection c = dataSource.getConnection()) {
            req.setSimulation(true);
            StcCalculResultDTO result = stcService.simulerStc(req, c);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(createErrorResponse(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de la simulation du STC : " + ex.getMessage()));
        }
    }

    /**
     * Valide et émet définitivement le STC :
     * - Génère le reçu numéroté officiel (ex: STC-2026-0001)
     * - Clôture et apure intégralement les prêts d'entreprise en cours (SOLDE)
     * - Solde les avances sur salaire
     * - Enregistre la quittance libératoire dans paie_stc
     */
    @PostMapping("/valider")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerStc(@Valid @RequestBody StcCalculRequest req) {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                StcCalculResultDTO result = stcService.validerStc(req, userId, c);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(result);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(createErrorResponse("Échec de validation du STC : " + ex.getMessage()));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur de connexion : " + e.getMessage()));
        }
    }

    /**
     * Liste tous les reçus pour solde de tout compte émis.
     */
    @GetMapping("/liste")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerStc() {
        try (Connection c = dataSource.getConnection()) {
            List<PaieStc> list = stcService.listerStc(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur de liste : " + e.getMessage()));
        }
    }

    /**
     * Récupère la quittance libératoire financière officielle d'un STC validé.
     */
    @GetMapping("/{idStc}/recu")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenirRecuStc(@PathVariable("idStc") Integer idStc) {
        try (Connection c = dataSource.getConnection()) {
            StcRecuDTO recu = stcService.obtenirRecuStc(idStc, c);
            if (recu == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createErrorResponse("STC introuvable pour l'id: " + idStc));
            }
            return ResponseEntity.ok(recu);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur de génération du reçu : " + e.getMessage()));
        }
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getIdUtilisateur();
        }
        return 1;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", true);
        err.put("message", message);
        return err;
    }
}
