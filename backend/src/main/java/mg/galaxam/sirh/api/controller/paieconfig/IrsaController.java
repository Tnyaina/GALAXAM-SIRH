package mg.galaxam.sirh.api.controller.paieconfig;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paieconfig.IrsaRequest;
import mg.galaxam.sirh.model.paieconfig.BaremeIrsa;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/bareme-irsa")
public class IrsaController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllBaremesIrsa() {
        try (Connection c = dataSource.getConnection()) {
            List<BaremeIrsa> list = BaremeIrsa.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération du barème IRSA.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getIrsaById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            BaremeIrsa b = new BaremeIrsa();
            BaremeIrsa found = (BaremeIrsa) b.rechercherParId(id, c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Tranche IRSA non trouvée.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la recherche de la tranche IRSA.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createIrsa(@RequestBody IrsaRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                BaremeIrsa b = new BaremeIrsa();
                b.setNumeroTranche(request.getNumeroTranche());
                b.setTrancheMin(request.getTrancheMin());
                b.setTrancheMax(request.getTrancheMax());
                b.setTaux(request.getTaux());
                b.setDateDebut(request.getDateDebut());
                b.setDateFin(request.getDateFin());

                b.save(c);
                AuditLog.audit(c, "CREATION", "bareme_irsa", String.valueOf(b.getIdTranche()), null, b);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(b);
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
    public ResponseEntity<?> updateIrsa(@PathVariable int id, @RequestBody IrsaRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                BaremeIrsa b = new BaremeIrsa();
                BaremeIrsa existing = (BaremeIrsa) b.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Tranche IRSA non trouvée.");
                }

                existing.setNumeroTranche(request.getNumeroTranche());
                existing.setTrancheMin(request.getTrancheMin());
                existing.setTrancheMax(request.getTrancheMax());
                existing.setTaux(request.getTaux());
                existing.setDateDebut(request.getDateDebut());
                existing.setDateFin(request.getDateFin());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "bareme_irsa", String.valueOf(id), null, existing);

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
