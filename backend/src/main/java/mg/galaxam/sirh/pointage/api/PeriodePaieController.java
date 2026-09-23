package mg.galaxam.sirh.pointage.api;

import mg.galaxam.sirh.pointage.api.dto.PeriodePaieDTO;
import jakarta.validation.Valid;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodCommand;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodCommandRequest;
import mg.galaxam.sirh.common.dto.BusinessDecision;
import mg.galaxam.sirh.paie.api.dto.PayrollPeriodContextView;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodUserContext;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodWorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/periodes")
public class PeriodePaieController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PayrollPeriodWorkflowService payrollPeriodWorkflowService;

    @Autowired
    private mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine calendrierEngine;

    @GetMapping
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<PeriodePaieDTO>> getAllPeriodes(Authentication authentication) throws SQLException {
        List<PeriodePaieDTO> list = new ArrayList<>();
        String sql = "SELECT p.id_periode_paie, p.code, p.libelle, p.date_debut, p.date_fin, " +
                     "p.id_statut_periode, s.code AS statut_code, s.libelle AS statut_libelle, p.motif_rejet, p.version " +
                     "FROM periode_paie p " +
                     "JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                     "ORDER BY p.date_debut DESC";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                PeriodePaieDTO dto = new PeriodePaieDTO();
                dto.setIdPeriodePaie(rs.getInt("id_periode_paie"));
                dto.setCode(rs.getString("code"));
                dto.setLibelle(rs.getString("libelle"));
                java.sql.Date dDeb = rs.getDate("date_debut");
                java.sql.Date dFin = rs.getDate("date_fin");
                dto.setDateDebut(dDeb != null ? dDeb.toString() : null);
                dto.setDateFin(dFin != null ? dFin.toString() : null);
                dto.setIdStatutPeriode(rs.getInt("id_statut_periode"));
                dto.setStatutCode(rs.getString("statut_code"));
                dto.setStatutLibelle(rs.getString("statut_libelle"));
                dto.setMotifRejet(rs.getString("motif_rejet"));
                dto.setVersion(rs.getInt("version"));
                dto.setAvailableActions(payrollPeriodWorkflowService.describeActions(
                        dto.getIdPeriodePaie(), dto.getStatutCode(),
                        PayrollPeriodUserContext.from(authentication), c));

                if (dDeb != null && dFin != null) {
                    dto.setNbJoursOuvrables(calendrierEngine.compterJoursOuvrables(dDeb.toLocalDate(), dFin.toLocalDate(), null, c));
                    dto.setNbJoursCalendaires(calendrierEngine.compterJoursCalendaires(dDeb.toLocalDate(), dFin.toLocalDate()));
                    dto.setNbJoursFeries(calendrierEngine.compterJoursFeriesChomes(dDeb.toLocalDate(), dFin.toLocalDate(), null, c));
                }

                list.add(dto);
            }
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PeriodePaieDTO> getCurrentPeriode(@RequestParam(required = false) String date)
            throws SQLException {
        String targetDate = (date != null && !date.trim().isEmpty()) ? date : java.time.LocalDate.now().toString();
        String sql = "SELECT p.id_periode_paie, p.code, p.libelle, p.date_debut, p.date_fin, " +
                     "p.id_statut_periode, s.code AS statut_code, s.libelle AS statut_libelle, p.motif_rejet, p.version " +
                     "FROM periode_paie p " +
                     "JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                     "WHERE ? BETWEEN p.date_debut AND p.date_fin " +
                     "ORDER BY p.date_debut DESC LIMIT 1";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(targetDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PeriodePaieDTO dto = new PeriodePaieDTO();
                    dto.setIdPeriodePaie(rs.getInt("id_periode_paie"));
                    dto.setCode(rs.getString("code"));
                    dto.setLibelle(rs.getString("libelle"));
                    java.sql.Date dDeb = rs.getDate("date_debut");
                    java.sql.Date dFin = rs.getDate("date_fin");
                    dto.setDateDebut(dDeb != null ? dDeb.toString() : null);
                    dto.setDateFin(dFin != null ? dFin.toString() : null);
                    dto.setIdStatutPeriode(rs.getInt("id_statut_periode"));
                    dto.setStatutCode(rs.getString("statut_code"));
                    dto.setStatutLibelle(rs.getString("statut_libelle"));
                    dto.setMotifRejet(rs.getString("motif_rejet"));
                    dto.setVersion(rs.getInt("version"));

                    if (dDeb != null && dFin != null) {
                        dto.setNbJoursOuvrables(calendrierEngine.compterJoursOuvrables(dDeb.toLocalDate(), dFin.toLocalDate(), null, c));
                        dto.setNbJoursCalendaires(calendrierEngine.compterJoursCalendaires(dDeb.toLocalDate(), dFin.toLocalDate()));
                        dto.setNbJoursFeries(calendrierEngine.compterJoursFeriesChomes(dDeb.toLocalDate(), dFin.toLocalDate(), null, c));
                    }
                    return ResponseEntity.ok(dto);
                }
            }
        }
        throw new SirhBusinessException(SirhErrorCode.PAYROLL_PERIOD_NOT_FOUND);
    }

    @GetMapping("/context")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public PayrollPeriodContextView getPeriodContext(
            @RequestParam(required = false) Integer periodId,
            @RequestParam(required = false) String date,
            Authentication authentication) throws Exception {
        java.time.LocalDate businessDate = date == null || date.isBlank()
                ? null
                : java.time.LocalDate.parse(date);
        return payrollPeriodWorkflowService.describeContext(
                periodId, businessDate, PayrollPeriodUserContext.from(authentication));
    }

    @GetMapping("/simuler")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> simulerPeriode(
            @RequestParam String dateDebut,
            @RequestParam String dateFin
    ) throws Exception {
        if (dateDebut == null || dateFin == null || dateDebut.trim().isEmpty() || dateFin.trim().isEmpty()) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "Les dates de début et de fin sont obligatoires.");
        }

        java.time.LocalDate start = java.time.LocalDate.parse(dateDebut.trim());
        java.time.LocalDate end = java.time.LocalDate.parse(dateFin.trim());

        if (end.isBefore(start)) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "La date de fin ne peut pas précéder la date de début.");
        }

        try (Connection c = dataSource.getConnection()) {
                // Délégation à CalendrierOuvrableEngine : source unique de vérité pour les jours ouvrables.
                // Inclut weekends + fériés nationaux + fériés par site + périodes chômées exceptionnelles.
                // Ancienne logique inline supprimée car incomplète (sans site, sans périodes chômées).
                int nbJoursOuvrables = calendrierEngine.compterJoursOuvrables(start, end, null, c);
                int calendaires = calendrierEngine.compterJoursCalendaires(start, end);
                int nbWeekends = (int) start.datesUntil(end.plusDays(1))
                    .filter(d -> d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                              || d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY)
                    .count();
                int nbFeries = calendaires - nbWeekends - nbJoursOuvrables;

                Map<String, Object> resp = new HashMap<>();
                resp.put("nbJoursOuvrables", nbJoursOuvrables);
                resp.put("nbJoursCalendaires", calendaires);
                resp.put("nbJoursFeries", Math.max(0, nbFeries));
                resp.put("nbWeekends", nbWeekends);

            return ResponseEntity.ok(resp);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('PAIE_WRITE') or hasAuthority('PAIE_VALIDATE') or hasAuthority('POINTAGE_VALIDATE')")
    public ResponseEntity<Map<String, Object>> createPeriode(@RequestBody Map<String, Object> payload) {
        Map<String, Object> resp = new HashMap<>();
        String code = (String) payload.get("code");
        String libelle = (String) payload.get("libelle");
        String dateDebut = (String) payload.get("dateDebut");
        String dateFin = (String) payload.get("dateFin");

        if (code == null || dateDebut == null || dateFin == null) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "Le code, la date de début et la date de fin sont requis.");
        }

        // Une création ne rend jamais la saisie modifiable par elle-même :
        // l'ouverture explicite (OPEN) constitue la première transition auditée.
        String statutInitial = (String) payload.getOrDefault("statutCode", "BROUILLON");
        if (!"BROUILLON".equals(statutInitial) && !"EN_COURS".equals(statutInitial)) {
            statutInitial = "EN_COURS";
        }

        String sql = "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                     "VALUES (?, ?, ?, ?, (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?))";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, code);
                ps.setString(2, libelle != null ? libelle : code);
                ps.setDate(3, java.sql.Date.valueOf(dateDebut));
                ps.setDate(4, java.sql.Date.valueOf(dateFin));
                ps.setString(5, statutInitial);
                ps.executeUpdate();

                resp.put("success", true);
                resp.put("message", "Période de paie créée avec succès.");
                return ResponseEntity.ok(resp);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new SirhBusinessException(SirhErrorCode.DATA_DUPLICATE,
                        "Une période possédant ce code ou ces dates existe déjà.");
            }
            throw new SirhBusinessException(SirhErrorCode.PERSISTENCE_ERROR);
        }
    }

    @PostMapping("/{id}/actions/{command}")
    @PreAuthorize("hasAuthority('POINTAGE_WRITE') or hasAuthority('POINTAGE_VALIDATE') or hasAuthority('PAIE_WRITE') or hasAuthority('PAIE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public BusinessDecision executeCommand(
            @PathVariable int id,
            @PathVariable PayrollPeriodCommand command,
            @Valid @RequestBody PayrollPeriodCommandRequest request,
            Authentication authentication) throws Exception {
        return payrollPeriodWorkflowService.execute(
                id, command, request, PayrollPeriodUserContext.from(authentication));
    }

    /** Compatibilité explicite : l'affectation directe d'un état est définitivement retirée. */
    @PutMapping("/{id}/statut")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> retiredGenericStatusMutation(@PathVariable int id) {
        return ResponseEntity.status(410).build();
    }
}
