package mg.galaxam.sirh.api.controller.pointage;

import mg.galaxam.sirh.api.dto.pointage.PeriodePaieDTO;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
    private mg.galaxam.sirh.model.pointage.service.PointageSyntheseService pointageSyntheseService;

    @Autowired
    private mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine calendrierEngine;

    @GetMapping
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<PeriodePaieDTO>> getAllPeriodes() {
        List<PeriodePaieDTO> list = new ArrayList<>();
        String sql = "SELECT p.id_periode_paie, p.code, p.libelle, p.date_debut, p.date_fin, " +
                     "p.id_statut_periode, s.code AS statut_code, s.libelle AS statut_libelle, p.motif_rejet " +
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

                if (dDeb != null && dFin != null) {
                    dto.setNbJoursOuvrables(calendrierEngine.compterJoursOuvrables(dDeb.toLocalDate(), dFin.toLocalDate(), null, c));
                    dto.setNbJoursCalendaires(calendrierEngine.compterJoursCalendaires(dDeb.toLocalDate(), dFin.toLocalDate()));
                    dto.setNbJoursFeries(calendrierEngine.compterJoursFeriesChomes(dDeb.toLocalDate(), dFin.toLocalDate(), null, c));
                }

                list.add(dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PeriodePaieDTO> getCurrentPeriode(@RequestParam(required = false) String date) {
        String targetDate = (date != null && !date.trim().isEmpty()) ? date : java.time.LocalDate.now().toString();
        String sql = "SELECT p.id_periode_paie, p.code, p.libelle, p.date_debut, p.date_fin, " +
                     "p.id_statut_periode, s.code AS statut_code, s.libelle AS statut_libelle, p.motif_rejet " +
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

                    if (dDeb != null && dFin != null) {
                        dto.setNbJoursOuvrables(calendrierEngine.compterJoursOuvrables(dDeb.toLocalDate(), dFin.toLocalDate(), null, c));
                        dto.setNbJoursCalendaires(calendrierEngine.compterJoursCalendaires(dDeb.toLocalDate(), dFin.toLocalDate()));
                        dto.setNbJoursFeries(calendrierEngine.compterJoursFeriesChomes(dDeb.toLocalDate(), dFin.toLocalDate(), null, c));
                    }
                    return ResponseEntity.ok(dto);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/simuler")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> simulerPeriode(
            @RequestParam String dateDebut,
            @RequestParam String dateFin
    ) {
        if (dateDebut == null || dateFin == null || dateDebut.trim().isEmpty() || dateFin.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            java.time.LocalDate start = java.time.LocalDate.parse(dateDebut.trim());
            java.time.LocalDate end = java.time.LocalDate.parse(dateFin.trim());

            if (end.isBefore(start)) {
                return ResponseEntity.badRequest().build();
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
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('POINTAGE_VALIDATE')")
    public ResponseEntity<Map<String, Object>> createPeriode(@RequestBody Map<String, Object> payload) {
        Map<String, Object> resp = new HashMap<>();
        String code = (String) payload.get("code");
        String libelle = (String) payload.get("libelle");
        String dateDebut = (String) payload.get("dateDebut");
        String dateFin = (String) payload.get("dateFin");

        if (code == null || dateDebut == null || dateFin == null) {
            resp.put("success", false);
            resp.put("message", "Le code, la date de début et la date de fin sont requis.");
            return ResponseEntity.badRequest().body(resp);
        }

        String sql = "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                     "VALUES (?, ?, ?, ?, (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'BROUILLON'))";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, code);
                ps.setString(2, libelle != null ? libelle : code);
                ps.setDate(3, java.sql.Date.valueOf(dateDebut));
                ps.setDate(4, java.sql.Date.valueOf(dateFin));
                ps.executeUpdate();

                resp.put("success", true);
                resp.put("message", "Période de paie créée avec succès.");
                return ResponseEntity.ok(resp);
        } catch (SQLException e) {
            e.printStackTrace();
            resp.put("success", false);
            resp.put("message", "Erreur lors de la création de la période : " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PutMapping("/{id}/statut")
    @PreAuthorize("hasAuthority('POINTAGE_VALIDATE') or hasAuthority('POINTAGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateStatut(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        Map<String, Object> resp = new HashMap<>();
        String newStatutCode = body.get("statutCode");
        
        if (newStatutCode == null || newStatutCode.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "Le code du nouveau statut est requis.");
            return ResponseEntity.badRequest().body(resp);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // Un agent de pointage ne peut que transmettre la période pour validation (A_VALIDER)
        if (!isAdmin && !"A_VALIDER".equals(newStatutCode)) {
            resp.put("success", false);
            resp.put("message", "Action non autorisée : seule la DAF / Administrateur peut changer ce statut.");
            return ResponseEntity.status(403).body(resp);
        }

        Integer currentUserId = null;
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            currentUserId = ud.getIdUtilisateur();
        }

        boolean isValidee = "VALIDEE".equals(newStatutCode) || "CLOTUREE".equals(newStatutCode);
        Timestamp dateValidation = isValidee ? new Timestamp(System.currentTimeMillis()) : null;
        Integer valideurId = isValidee ? currentUserId : null;

        String rawMotifRejet = body.containsKey("motifRejet") && body.get("motifRejet") != null ? ((String) body.get("motifRejet")).trim() : null;

        // Décision métier Java sur la mise à jour du motif de rejet
        String sql;
        if (isValidee) {
            sql = "UPDATE periode_paie " +
                  "SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?), " +
                  "date_validation = ?, valide_par = ?, motif_rejet = NULL " +
                  "WHERE id_periode_paie = ?";
        } else if (rawMotifRejet != null) {
            sql = "UPDATE periode_paie " +
                  "SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?), " +
                  "date_validation = ?, valide_par = ?, motif_rejet = ? " +
                  "WHERE id_periode_paie = ?";
        } else {
            sql = "UPDATE periode_paie " +
                  "SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?), " +
                  "date_validation = ?, valide_par = ? " +
                  "WHERE id_periode_paie = ?";
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    int pIdx = 1;
                    ps.setString(pIdx++, newStatutCode);
                    if (dateValidation != null) ps.setTimestamp(pIdx++, dateValidation); else ps.setNull(pIdx++, java.sql.Types.TIMESTAMP);
                    if (valideurId != null) ps.setInt(pIdx++, valideurId); else ps.setNull(pIdx++, java.sql.Types.INTEGER);
                    if (!isValidee && rawMotifRejet != null) {
                        ps.setString(pIdx++, rawMotifRejet);
                    }
                    ps.setInt(pIdx++, id);
                    rows = ps.executeUpdate();
                }

                if (rows > 0) {
                    if ("VALIDEE".equals(newStatutCode) || "CLOTUREE".equals(newStatutCode)) {
                        pointageSyntheseService.gelerPeriode(id, currentUserId, c);
                    } else if ("BROUILLON".equals(newStatutCode)) {
                        pointageSyntheseService.degelerPeriode(id, c);
                    }
                    c.commit();
                    resp.put("success", true);
                    resp.put("message", "Statut de la période mis à jour avec succès.");
                    return ResponseEntity.ok(resp);
                } else {
                    c.rollback();
                    resp.put("success", false);
                    resp.put("message", "Période introuvable.");
                    return ResponseEntity.badRequest().body(resp);
                }
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.put("success", false);
            resp.put("message", "Erreur lors de la mise à jour : " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }
}
