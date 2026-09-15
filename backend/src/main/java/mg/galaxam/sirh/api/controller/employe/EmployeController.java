package mg.galaxam.sirh.api.controller.employe;

import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.api.dto.common.PageDTO;
import mg.galaxam.sirh.api.dto.employe.DesactivationRequest;
import mg.galaxam.sirh.api.dto.employe.EmployeFicheDetailDTO;
import mg.galaxam.sirh.api.dto.employe.EmployeRequest;
import mg.galaxam.sirh.model.employe.EmployeCPL;
import mg.galaxam.sirh.model.employe.service.EmployeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/employes")
public class EmployeController {

    @Autowired
    private EmployeService employeService;

    // =========================================================================
    // LECTURE — déléguées à EmployeService (source unique de vérité)
    // =========================================================================

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployeStats(@RequestParam(required = false) String q) {
        try {
            return ResponseEntity.ok(employeService.getStats(q));
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployes(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Boolean actif,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        try {
            PageDTO<EmployeCPL> pageDto = employeService.getEmployes(q, actif, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployeOptions(@RequestParam(required = false, defaultValue = "true") Boolean actif) {
        try {
            List<Map<String, Object>> options = employeService.getOptions(actif);
            return ResponseEntity.ok(options);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/matricule/check")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> checkMatriculeExists(@RequestParam String matricule) {
        try {
            boolean exists = employeService.checkMatriculeExists(matricule);
            return ResponseEntity.ok(Map.of("exists", exists));
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/matricule/next")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getNextMatricule() {
        try {
            String matricule = employeService.generateNextMatricule();
            return ResponseEntity.ok(Map.of("matricule", matricule));
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployeFicheDetail(@PathVariable int id) {
        try {
            EmployeFicheDetailDTO detail = employeService.getFicheDetail(id);
            if (detail == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Employé non trouvé.");
            }
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // CRÉATION — déléguée à EmployeService (évite la double notification)
    // =========================================================================

    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createEmploye(@RequestBody EmployeRequest req) {
        try {
            EmployeCPL created = employeService.creerEmploye(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // MUTATIONS — déléguées à EmployeService (source unique de vérité)
    // =========================================================================

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateEmploye(@PathVariable int id, @RequestBody EmployeRequest req) {
        try {
            EmployeCPL updated = employeService.modifierEmploye(id, req);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}/desactiver")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> desactiverEmploye(@PathVariable int id, @RequestBody(required = false) DesactivationRequest req) {
        try {
            EmployeCPL updated = employeService.desactiverEmploye(id, req);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}/activer")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> reactiverEmploye(@PathVariable int id) {
        try {
            EmployeCPL updated = employeService.reactiverEmploye(id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
