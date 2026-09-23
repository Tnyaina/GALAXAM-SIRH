package mg.galaxam.sirh.conge.api;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.api.dto.ActiverTauxCalculeRequest;
import mg.galaxam.sirh.conge.api.dto.CreerPeriodeReferenceRequest;
import mg.galaxam.sirh.conge.domain.TcongeConfigurationDTO;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService.PeriodeReferenceData;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 2A-4C.1 — Contrôleur REST pour la gestion opérationnelle des périodes de référence TCONGE
 * et de l'activation des taux calculés.
 */
@RestController
@RequestMapping("/conges/periodes-reference")
public class CongePeriodeReferenceController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    private Integer getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) {
            return user.getIdUtilisateur();
        }
        return 1;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerPeriodeReference(@RequestBody CreerPeriodeReferenceRequest request) throws Exception {
        if (request == null || request.getIdEmploye() == null || request.getDateDebut() == null || request.getDateFin() == null) {
            throw new IllegalArgumentException("idEmploye, dateDebut et dateFin sont obligatoires.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int idRef = congeTauxApplicableService.creerPeriodeReference(
                        request.getIdEmploye(),
                        request.getDateDebut(),
                        request.getDateFin(),
                        getUserId(),
                        c
                );
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "idPeriodeReference", idRef,
                        "idEmploye", request.getIdEmploye(),
                        "dateDebut", request.getDateDebut(),
                        "dateFin", request.getDateFin(),
                        "statut", "OUVERTE"
                ));
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @GetMapping("/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerPeriodesParEmploye(@PathVariable int idEmploye) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String sql = "SELECT id_periode_reference, date_debut, date_fin, statut, taux_calcule_cloture, " +
                    "cumul_droits_cloture, cumul_valorisation_cloture, created_at " +
                    "FROM conge_periode_reference WHERE id_employe = ? ORDER BY date_debut DESC";
            List<Map<String, Object>> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(Map.of(
                                "idPeriodeReference", rs.getInt("id_periode_reference"),
                                "dateDebut", rs.getDate("date_debut").toString(),
                                "dateFin", rs.getDate("date_fin").toString(),
                                "statut", rs.getString("statut"),
                                "tauxCalculeCloture", rs.getBigDecimal("taux_calcule_cloture") != null ? rs.getBigDecimal("taux_calcule_cloture") : BigDecimal.ZERO,
                                "cumulDroitsCloture", rs.getBigDecimal("cumul_droits_cloture") != null ? rs.getBigDecimal("cumul_droits_cloture") : BigDecimal.ZERO,
                                "cumulValorisationCloture", rs.getBigDecimal("cumul_valorisation_cloture") != null ? rs.getBigDecimal("cumul_valorisation_cloture") : BigDecimal.ZERO
                        ));
                    }
                }
            }
            return ResponseEntity.ok(result);
        }
    }

    @GetMapping("/employe/{idEmploye}/statut-configuration")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getStatutConfiguration(
            @PathVariable int idEmploye,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateReference) throws Exception {
        LocalDate dRef = dateReference != null ? dateReference : LocalDate.now();
        try (Connection c = dataSource.getConnection()) {
            TcongeConfigurationDTO config = congeTauxApplicableService.evaluerConfigurationTconge(idEmploye, dRef, c);
            return ResponseEntity.ok(config);
        }
    }

    @PostMapping("/{idPeriodeReference}/cloturer")
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> cloturerPeriodeReference(@PathVariable int idPeriodeReference) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                BigDecimal tauxCalcule = congeTauxApplicableService.cloturerPeriodeReference(idPeriodeReference, getUserId(), c);
                c.commit();
                return ResponseEntity.ok(Map.of(
                        "idPeriodeReference", idPeriodeReference,
                        "statut", "CLOTUREE",
                        "tauxCalculeCloture", tauxCalcule
                ));
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @PostMapping("/{idPeriodeReference}/activer-taux")
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activerTauxCalcule(
            @PathVariable int idPeriodeReference,
            @RequestBody ActiverTauxCalculeRequest request) throws Exception {
        if (request == null || request.getDateEffet() == null) {
            throw new IllegalArgumentException("dateEffet est obligatoire pour activer le taux.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int idTaux = congeTauxApplicableService.activerTauxCalcule(
                        idPeriodeReference,
                        request.getDateEffet(),
                        getUserId(),
                        c
                );
                c.commit();
                return ResponseEntity.ok(Map.of(
                        "idTauxApplicable", idTaux,
                        "idPeriodeReference", idPeriodeReference,
                        "dateEffet", request.getDateEffet(),
                        "sourceProvenance", "CALCUL_PERIODE_REFERENCE"
                ));
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
