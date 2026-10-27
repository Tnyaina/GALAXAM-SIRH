package mg.galaxam.sirh.paie.api;


import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.paie.api.dto.PaieRubriqueRequest;
import mg.galaxam.sirh.paie.persistence.PaieRubrique;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/paie/rubriques", "/v1/paie/rubriques"})
public class PaieRubriqueController {

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PaieRubrique> creerVersion(@PathVariable int id, @RequestBody PaieRubriqueRequest req) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(rubriqueManagementService.createVersion(id, req));
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private mg.galaxam.sirh.paie.application.PayrollRubriqueCycleService rubriqueCycleService;

    @Autowired
    private mg.galaxam.sirh.paie.application.ManualRubriqueManagementService rubriqueManagementService;

    @GetMapping("/cycle-preview")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> apercuCycles(@RequestParam java.time.LocalDate debut,
            @RequestParam java.time.LocalDate fin) {
        return ResponseEntity.ok(rubriqueCycleService.preview(debut, fin));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerRubriques(@RequestParam(name = "tous", defaultValue = "false") boolean tous) {
        try (Connection c = dataSource.getConnection()) {
            List<PaieRubrique> list = tous ? PaieRubrique.listerTous(c) : PaieRubrique.listerActifs(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getParId(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            PaieRubrique found = PaieRubrique.trouverParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rubrique introuvable.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerRubrique(@RequestBody PaieRubriqueRequest req) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(rubriqueManagementService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierRubrique(@PathVariable int id, @RequestBody PaieRubriqueRequest req) throws Exception {
        return ResponseEntity.ok(rubriqueManagementService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> supprimerRubrique(@PathVariable int id) throws Exception {
        return ResponseEntity.ok(rubriqueManagementService.delete(id));
    }

}
