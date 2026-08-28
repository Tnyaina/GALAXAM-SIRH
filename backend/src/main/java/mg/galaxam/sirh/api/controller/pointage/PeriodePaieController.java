package mg.galaxam.sirh.api.controller.pointage;

import mg.galaxam.sirh.api.dto.pointage.PeriodePaieDTO;
import mg.galaxam.sirh.model.pointage.service.PointageSyntheseService;
import mg.galaxam.sirh.securite.CustomUserDetails;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private PointageSyntheseService pointageSyntheseService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AGENT_POINTAGE')")
    public ResponseEntity<List<PeriodePaieDTO>> getAllPeriodes() {
        List<PeriodePaieDTO> list = new ArrayList<>();
        String sql = "SELECT p.id_periode_paie, p.code, p.libelle, p.date_debut, p.date_fin, " +
                     "p.id_statut_periode, s.code AS statut_code, s.libelle AS statut_libelle " +
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

                if (dDeb != null && dFin != null) {
                    int ouvrables = calculerJoursOuvrables(dDeb, dFin, c);
                    long calendaires = java.time.temporal.ChronoUnit.DAYS.between(dDeb.toLocalDate(), dFin.toLocalDate()) + 1;
                    int feries = compterJoursFeriesChomes(dDeb, dFin, c);
                    dto.setNbJoursOuvrables(ouvrables);
                    dto.setNbJoursCalendaires((int) calendaires);
                    dto.setNbJoursFeries(feries);
                }

                list.add(dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok(list);
    }

    /**
     * Endpoint de simulation / calcul prédictif côté serveur des jours ouvrables pour une période donnée.
     * Respect strict du paradigme Backend-Driven (aucun calcul gratuit en front).
     */
    @GetMapping("/simuler")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AGENT_POINTAGE')")
    public ResponseEntity<Map<String, Object>> simulerPeriode(
            @RequestParam String dateDebut,
            @RequestParam String dateFin) {
        Map<String, Object> resp = new HashMap<>();
        try (Connection c = dataSource.getConnection()) {
            java.sql.Date dDeb = java.sql.Date.valueOf(dateDebut);
            java.sql.Date dFin = java.sql.Date.valueOf(dateFin);
            if (dDeb.after(dFin)) {
                resp.put("error", "La date de début doit être antérieure à la date de fin.");
                return ResponseEntity.badRequest().body(resp);
            }

            int ouvrables = calculerJoursOuvrables(dDeb, dFin, c);
            long calendaires = java.time.temporal.ChronoUnit.DAYS.between(dDeb.toLocalDate(), dFin.toLocalDate()) + 1;
            int feries = compterJoursFeriesChomes(dDeb, dFin, c);

            resp.put("dateDebut", dateDebut);
            resp.put("dateFin", dateFin);
            resp.put("nbJoursOuvrables", ouvrables);
            resp.put("nbJoursCalendaires", (int) calendaires);
            resp.put("nbJoursFeries", feries);
            resp.put("nbWeekends", (int) (calendaires - ouvrables - feries));

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", "Format de date invalide (AAAA-MM-JJ requis) : " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    private static int calculerJoursOuvrables(java.sql.Date dateDebut, java.sql.Date dateFin, Connection c) {
        if (dateDebut == null || dateFin == null || dateDebut.after(dateFin)) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM generate_series(?::date, ?::date, '1 day'::interval) AS d " +
                     "WHERE EXTRACT(ISODOW FROM d) < 6 " +
                     "AND NOT EXISTS (" +
                     "    SELECT 1 FROM ref_calendrier_ferie f " +
                     "    WHERE f.date_ferie = d::date AND f.est_chome = TRUE" +
                     ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, dateDebut);
            ps.setDate(2, dateFin);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            java.time.LocalDate start = dateDebut.toLocalDate();
            java.time.LocalDate end = dateFin.toLocalDate();
            int count = 0;
            for (java.time.LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                java.time.DayOfWeek dow = date.getDayOfWeek();
                if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
                    count++;
                }
            }
            return count;
        }
        return 0;
    }

    private static int compterJoursFeriesChomes(java.sql.Date dateDebut, java.sql.Date dateFin, Connection c) {
        if (dateDebut == null || dateFin == null) return 0;
        String sql = "SELECT COUNT(*) FROM ref_calendrier_ferie " +
                     "WHERE date_ferie BETWEEN ? AND ? AND est_chome = TRUE " +
                     "AND EXTRACT(ISODOW FROM date_ferie) < 6";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, dateDebut);
            ps.setDate(2, dateFin);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> createPeriode(@RequestBody PeriodePaieDTO dto) {
        Map<String, Object> resp = new HashMap<>();
        String sql = "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                     "VALUES (?, ?, ?, ?, (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'BROUILLON'))";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, dto.getCode());
            ps.setString(2, dto.getLibelle());
            ps.setDate(3, java.sql.Date.valueOf(dto.getDateDebut()));
            ps.setDate(4, java.sql.Date.valueOf(dto.getDateFin()));
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AGENT_POINTAGE')")
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

        String sql = "UPDATE periode_paie " +
                     "SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?), " +
                     "date_validation = CASE WHEN ? IN ('VALIDEE', 'CLOTUREE') THEN CURRENT_TIMESTAMP ELSE NULL END, " +
                     "valide_par = CASE WHEN ? IN ('VALIDEE', 'CLOTUREE') THEN ? ELSE NULL END " +
                     "WHERE id_periode_paie = ?";

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, newStatutCode);
                ps.setString(2, newStatutCode);
                ps.setString(3, newStatutCode);
                if (currentUserId != null) ps.setInt(4, currentUserId); else ps.setNull(4, java.sql.Types.INTEGER);
                ps.setInt(5, id);
                int rows = ps.executeUpdate();
                
                if (rows > 0) {
                    // Si VALIDEE ou CLOTUREE -> Gel des données
                    if ("VALIDEE".equals(newStatutCode) || "CLOTUREE".equals(newStatutCode)) {
                        pointageSyntheseService.gelerPeriode(id, currentUserId, c);
                    } else if ("EN_COURS".equals(newStatutCode) || "BROUILLON".equals(newStatutCode) || "A_VALIDER".equals(newStatutCode)) {
                        // Si réouverture -> Dégel pour rendre à nouveau dynamique
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
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.put("success", false);
            resp.put("message", "Erreur lors de la mise à jour : " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }
}
