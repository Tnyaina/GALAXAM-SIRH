package mg.galaxam.sirh.api.controller.paie;

import jakarta.validation.Valid;
import java.sql.Connection;
import java.sql.Date;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculRequest;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculResultDTO;
import mg.galaxam.sirh.api.dto.paie.stc.StcPreparationDTO;
import mg.galaxam.sirh.api.dto.paie.stc.StcRecuDTO;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
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
            @RequestParam(name = "motif", required = false, defaultValue = "DEMISSION") String motif) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Date dateSql = dateDepart != null ? Date.valueOf(dateDepart) : new Date(System.currentTimeMillis());
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
     * - Clôture et apure intégralement les prêts d'entreprise en cours (SOLDE)
     * - Solde les avances sur salaire
     * - Enregistre la quittance libératoire dans paie_stc
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
     * Récupère la quittance libératoire financière officielle d'un STC validé.
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

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getIdUtilisateur();
        }
        return 1;
    }

}
