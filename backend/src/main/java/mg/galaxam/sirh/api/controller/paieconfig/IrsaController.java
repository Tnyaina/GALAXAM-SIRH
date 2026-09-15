package mg.galaxam.sirh.api.controller.paieconfig;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paieconfig.IrsaRequest;
import mg.galaxam.sirh.api.dto.paieconfig.IrsaRevalorisationRequest;
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
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllBaremesIrsa() {
        try (Connection c = dataSource.getConnection()) {
            List<BaremeIrsa> list = BaremeIrsa.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getIrsaById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            BaremeIrsa b = new BaremeIrsa();
            BaremeIrsa found = (BaremeIrsa) b.rechercherParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Tranche IRSA non trouvée.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
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
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Revalorisation du barème IRSA :
     * - Ferme toutes les tranches EN VIGUEUR (date_fin = nouvelleDateDebut - 1 jour)
     * - Crée les nouvelles tranches avec la nouvelleDateDebut
     * Tout dans une seule transaction.
     */
    @PostMapping("/revaloriser")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> revaloriserBareme(@RequestBody IrsaRevalorisationRequest request) {
        if (request.getNouvelleDateDebut() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "La date d'application est obligatoire.");
        }
        if (request.getTranches() == null || request.getTranches().isEmpty()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le nouveau barème doit contenir au moins une tranche.");
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                List<BaremeIrsa> nouvellesTranches = new ArrayList<>();
                for (IrsaRequest req : request.getTranches()) {
                    BaremeIrsa b = new BaremeIrsa();
                    b.setNumeroTranche(req.getNumeroTranche());
                    b.setTrancheMin(req.getTrancheMin());
                    b.setTrancheMax(req.getTrancheMax());
                    b.setTaux(req.getTaux());
                    nouvellesTranches.add(b);
                }

                BaremeIrsa.revaloriser(c, request.getNouvelleDateDebut(), nouvellesTranches);

                AuditLog.audit(c, "REVALORISATION", "bareme_irsa", "BATCH",
                        null, "Revalorisation de " + nouvellesTranches.size() + " tranches à partir du " + request.getNouvelleDateDebut());

                c.commit();
                List<BaremeIrsa> updated = BaremeIrsa.listAll(c);
                return ResponseEntity.ok(updated);
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
    public ResponseEntity<?> updateIrsa(@PathVariable int id, @RequestBody IrsaRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                BaremeIrsa b = new BaremeIrsa();
                BaremeIrsa existing = (BaremeIrsa) b.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Tranche IRSA non trouvée.");
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
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
