package mg.galaxam.sirh.api.controller.paieconfig;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paieconfig.TauxMajorationRequest;
import mg.galaxam.sirh.model.paieconfig.RefTauxMajoration;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/taux-majoration")
public class TauxMajorationController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllTauxMajoration() {
        try (Connection c = dataSource.getConnection()) {
            List<RefTauxMajoration> list = RefTauxMajoration.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des taux de majoration.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getTauxMajorationById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefTauxMajoration tm = new RefTauxMajoration();
            RefTauxMajoration found = (RefTauxMajoration) tm.rechercherParId(id, c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Taux de majoration non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la recherche du taux de majoration.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createTauxMajoration(@RequestBody TauxMajorationRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTauxMajoration tm = new RefTauxMajoration();
                tm.setTypeMajoration(request.getTypeMajoration());
                tm.setTaux(request.getTaux());
                tm.setDescription(request.getDescription());
                tm.setDateDebut(request.getDateDebut());
                tm.setDateFin(request.getDateFin());

                tm.save(c);
                AuditLog.audit(c, "CREATION", "ref_taux_majoration", String.valueOf(tm.getIdTauxMajoration()), null, tm);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(tm);
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
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateTauxMajoration(@PathVariable int id, @RequestBody TauxMajorationRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTauxMajoration tm = new RefTauxMajoration();
                RefTauxMajoration existing = (RefTauxMajoration) tm.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Taux de majoration non trouvé.");
                }

                existing.setTypeMajoration(request.getTypeMajoration());
                existing.setTaux(request.getTaux());
                existing.setDescription(request.getDescription());
                existing.setDateDebut(request.getDateDebut());
                existing.setDateFin(request.getDateFin());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_taux_majoration", String.valueOf(id), null, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }
}
