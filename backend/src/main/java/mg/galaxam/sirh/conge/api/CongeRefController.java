package mg.galaxam.sirh.conge.api;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.persistence.RefMotifEvenementFamilial;
import mg.galaxam.sirh.conge.persistence.RefPoolCongeAnnuel;
import mg.galaxam.sirh.conge.persistence.RefStatutDemandeConge;
import mg.galaxam.sirh.conge.persistence.RefTypeConge;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/conges/referentiels")
public class CongeRefController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllReferentielsConge() {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> result = new HashMap<>();
            result.put("pools", RefPoolCongeAnnuel.listAll(c));
            result.put("typesConge", RefTypeConge.listAll(c, true));
            result.put("motifsFamiliaux", RefMotifEvenementFamilial.listAll(c, true));
            result.put("statutsDemande", RefStatutDemandeConge.listAll(c));
            result.put("parametresGlobal", ParametreGlobal.listByCategorie("CONGE", c));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getTypesConge(@RequestParam(value = "actifUniquement", required = false, defaultValue = "true") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeConge> list = RefTypeConge.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/motifs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMotifsFamiliaux(@RequestParam(value = "actifUniquement", required = false, defaultValue = "true") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefMotifEvenementFamilial> list = RefMotifEvenementFamilial.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/pools")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPools() {
        try (Connection c = dataSource.getConnection()) {
            List<RefPoolCongeAnnuel> list = RefPoolCongeAnnuel.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/statuts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getStatutsDemande() {
        try (Connection c = dataSource.getConnection()) {
            List<RefStatutDemandeConge> list = RefStatutDemandeConge.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // CRUD TYPE DE CONGE
    // =========================================================================

    @PostMapping("/types")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createTypeConge(@RequestBody RefTypeConge typeConge) {
        try (Connection c = dataSource.getConnection()) {
            typeConge.controler(c);
            typeConge.save(c);
            return ResponseEntity.status(HttpStatus.CREATED).body(typeConge);
        } catch (IllegalArgumentException e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateTypeConge(@PathVariable Integer id, @RequestBody RefTypeConge typeConge) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeConge existant = RefTypeConge.rechercherParId(id, c);
            if (existant == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de congé introuvable.");
            }
            typeConge.setIdTypeConge(id);
            typeConge.controler(c);
            typeConge.update(c);
            return ResponseEntity.ok(typeConge);
        } catch (IllegalArgumentException e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteTypeConge(@PathVariable Integer id) {
        return deactivateTypeConge(id);
    }

    @PutMapping("/types/{id}/desactiver")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateTypeConge(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeConge existant = RefTypeConge.rechercherParId(id, c);
            if (existant == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de congé introuvable.");
            }
            existant.setEstActif(false);
            existant.update(c); // On fait une désactivation logique (soft delete)
            return ResponseEntity.ok(existant);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/types/{id}/activer")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateTypeConge(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeConge existant = RefTypeConge.rechercherParId(id, c);
            if (existant == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de congé introuvable.");
            }
            existant.setEstActif(true);
            existant.update(c);
            return ResponseEntity.ok(existant);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // CRUD MOTIF EVENEMENT FAMILIAL
    // =========================================================================

    @PostMapping("/motifs")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createMotifFamilial(@RequestBody RefMotifEvenementFamilial motif) {
        try (Connection c = dataSource.getConnection()) {
            motif.controler(c);
            motif.save(c);
            return ResponseEntity.status(HttpStatus.CREATED).body(motif);
        } catch (IllegalArgumentException e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/motifs/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateMotifFamilial(@PathVariable Integer id, @RequestBody RefMotifEvenementFamilial motif) {
        try (Connection c = dataSource.getConnection()) {
            RefMotifEvenementFamilial existant = RefMotifEvenementFamilial.rechercherParId(id, c);
            if (existant == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Motif familial introuvable.");
            }
            motif.setIdMotif(id);
            motif.controler(c);
            motif.update(c);
            return ResponseEntity.ok(motif);
        } catch (IllegalArgumentException e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @DeleteMapping("/motifs/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteMotifFamilial(@PathVariable Integer id) {
        return deactivateMotifFamilial(id);
    }

    @PutMapping("/motifs/{id}/desactiver")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateMotifFamilial(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            RefMotifEvenementFamilial existant = RefMotifEvenementFamilial.rechercherParId(id, c);
            if (existant == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Motif familial introuvable.");
            }
            existant.setEstActif(false);
            existant.update(c); // Désactivation logique
            return ResponseEntity.ok(existant);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/motifs/{id}/activer")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateMotifFamilial(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            RefMotifEvenementFamilial existant = RefMotifEvenementFamilial.rechercherParId(id, c);
            if (existant == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Motif familial introuvable.");
            }
            existant.setEstActif(true);
            existant.update(c);
            return ResponseEntity.ok(existant);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
