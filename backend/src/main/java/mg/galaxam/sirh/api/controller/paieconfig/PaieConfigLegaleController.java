package mg.galaxam.sirh.api.controller.paieconfig;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paieconfig.PaieConfigLegaleRequest;
import mg.galaxam.sirh.model.paieconfig.PaieConfigLegale;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Gestion des exercices fiscaux et sociaux (paie_config_legale).
 * Base URL : /paie/config-legale
 */
@RestController
@RequestMapping("/paie/config-legale")
public class PaieConfigLegaleController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllConfigs() {
        try (Connection c = dataSource.getConnection()) {
            List<PaieConfigLegale> list = PaieConfigLegale.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des configurations légales.");
        }
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getActiveConfig() {
        try (Connection c = dataSource.getConnection()) {
            PaieConfigLegale config = PaieConfigLegale.findActiveAt(c, LocalDate.now());
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Aucune configuration légale active trouvée.");
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    @GetMapping("/active-at")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getConfigAt(@RequestParam("date") String dateStr) {
        try (Connection c = dataSource.getConnection()) {
            LocalDate date = LocalDate.parse(dateStr);
            PaieConfigLegale config = PaieConfigLegale.findActiveAt(c, date);
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Aucune configuration légale active pour la date " + dateStr);
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createConfig(@RequestBody PaieConfigLegaleRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieConfigLegale config = mapFromRequest(request);
                config.save(c);
                AuditLog.audit(c, "CREATION", "paie_config_legale",
                        String.valueOf(config.getIdConfigLegale()), null, config);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(config);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    /**
     * Revalorisation : clôt l'exercice actif + crée le suivant à la date d'effet.
     */
    @PostMapping("/revaloriser")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> revaloriser(@RequestBody PaieConfigLegaleRequest request) {
        if (request.getDateDebut() == null) {
            return ResponseEntity.badRequest().body("La date d'effet est obligatoire pour une revalorisation.");
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieConfigLegale nouvConfig = mapFromRequest(request);
                PaieConfigLegale result = PaieConfigLegale.revaloriser(c, request.getDateDebut(), nouvConfig);
                AuditLog.audit(c, "REVALORISATION", "paie_config_legale",
                        String.valueOf(result.getIdConfigLegale()), null, result);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(result);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateConfig(@PathVariable int id, @RequestBody PaieConfigLegaleRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieConfigLegale existing = PaieConfigLegale.findById(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Exercice fiscal introuvable.");
                }
                PaieConfigLegale updated = mapFromRequest(request);
                updated.setIdConfigLegale(id);
                if (updated.getCodeVersion() == null || updated.getCodeVersion().isBlank()) {
                    updated.setCodeVersion(existing.getCodeVersion());
                }
                updated.update(c);
                AuditLog.audit(c, "CORRECTION", "paie_config_legale", String.valueOf(id), existing, updated);
                c.commit();
                PaieConfigLegale reloaded = PaieConfigLegale.findById(id, c);
                return ResponseEntity.ok(reloaded != null ? reloaded : updated);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    private PaieConfigLegale mapFromRequest(PaieConfigLegaleRequest r) {
        PaieConfigLegale p = new PaieConfigLegale();
        p.setCodeVersion(r.getCodeVersion());
        p.setLibelle(r.getLibelle());
        p.setDateDebut(r.getDateDebut());
        p.setDateFin(r.getDateFin());
        p.setSmeNational(r.getSmeNational());
        p.setSmeAgricole(r.getSmeAgricole());
        p.setPlafondCotisationNbSme(r.getPlafondCotisationNbSme() != null ? r.getPlafondCotisationNbSme() : new BigDecimal("8.00"));
        p.setSeuilExonerationIrsa(r.getSeuilExonerationIrsa());
        p.setMinimumPerceptionIrsa(r.getMinimumPerceptionIrsa());
        p.setDeductionChargeFamille(r.getDeductionChargeFamille());
        p.setArrondiBaseIrsa(r.getArrondiBaseIrsa() != null ? r.getArrondiBaseIrsa() : "CENTAINE_INF");
        p.setDureeHebdoLegale(r.getDureeHebdoLegale() != null ? r.getDureeHebdoLegale() : new BigDecimal("40.00"));
        p.setDiviseurMensuelHeures(r.getDiviseurMensuelHeures() != null ? r.getDiviseurMensuelHeures() : new BigDecimal("173.3333"));
        p.setSeuilHsTranche1(r.getSeuilHsTranche1() != null ? r.getSeuilHsTranche1() : new BigDecimal("8.00"));
        p.setPlafondHsHebdo(r.getPlafondHsHebdo() != null ? r.getPlafondHsHebdo() : new BigDecimal("20.00"));
        p.setAcquisitionCongeMensuelJours(r.getAcquisitionCongeMensuelJours() != null ? r.getAcquisitionCongeMensuelJours() : new BigDecimal("2.50"));
        p.setDiviseurValorisationCongeJours(r.getDiviseurValorisationCongeJours() != null ? r.getDiviseurValorisationCongeJours() : 30);
        return p;
    }
}
