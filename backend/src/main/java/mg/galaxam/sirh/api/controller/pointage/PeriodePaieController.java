package mg.galaxam.sirh.api.controller.pointage;

import mg.galaxam.sirh.api.dto.pointage.PeriodePaieDTO;
import mg.galaxam.sirh.securite.CustomUserDetails;
import mg.galaxam.sirh.service.pointage.PointageSyntheseService;
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
                dto.setDateDebut(rs.getDate("date_debut").toString());
                dto.setDateFin(rs.getDate("date_fin").toString());
                dto.setIdStatutPeriode(rs.getInt("id_statut_periode"));
                dto.setStatutCode(rs.getString("statut_code"));
                dto.setStatutLibelle(rs.getString("statut_libelle"));
                list.add(dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok(list);
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
