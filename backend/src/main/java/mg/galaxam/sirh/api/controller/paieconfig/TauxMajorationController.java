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
    public ResponseEntity<?> getAllTauxMajoration(@RequestParam(value = "idConfigLegale", required = false) Integer idConfigLegale) {
        try (Connection c = dataSource.getConnection()) {
            List<RefTauxMajoration> list;
            if (idConfigLegale != null) {
                list = RefTauxMajoration.listByConfig(idConfigLegale, c);
            } else {
                list = RefTauxMajoration.listAll(c);
            }
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getTauxMajorationById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefTauxMajoration tm = new RefTauxMajoration();
            RefTauxMajoration found = (RefTauxMajoration) tm.rechercherParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Taux de majoration non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createTauxMajoration(@RequestBody TauxMajorationRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTauxMajoration tm = new RefTauxMajoration();
                tm.setIdConfigLegale(request.getIdConfigLegale());
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
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Taux de majoration non trouvé.");
                }

                if (request.getIdConfigLegale() != null) {
                    existing.setIdConfigLegale(request.getIdConfigLegale());
                }
                existing.setTypeMajoration(request.getTypeMajoration());
                existing.setTaux(request.getTaux());
                existing.setDescription(request.getDescription());
                if (request.getDateDebut() != null) existing.setDateDebut(request.getDateDebut());
                if (request.getDateFin() != null) existing.setDateFin(request.getDateFin());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_taux_majoration", String.valueOf(id), null, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
