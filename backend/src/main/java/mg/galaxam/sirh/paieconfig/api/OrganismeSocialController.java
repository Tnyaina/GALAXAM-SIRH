package mg.galaxam.sirh.paieconfig.api;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.paieconfig.api.dto.OrganismeSocialRequest;
import mg.galaxam.sirh.paieconfig.persistence.RefOrganismeSocial;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/organismes-sociaux")
public class OrganismeSocialController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllOrganismes(@RequestParam(value = "actifOnly", required = false, defaultValue = "false") boolean actifOnly) {
        try (Connection c = dataSource.getConnection()) {
            List<RefOrganismeSocial> list = actifOnly ? RefOrganismeSocial.listActive(c) : RefOrganismeSocial.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getOrganismeById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefOrganismeSocial dao = new RefOrganismeSocial();
            RefOrganismeSocial found = (RefOrganismeSocial) dao.rechercherParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Organisme social non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createOrganisme(@RequestBody OrganismeSocialRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefOrganismeSocial existing = RefOrganismeSocial.findByCode(request.getCode(), c);
                if (existing != null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                            "Un organisme avec le code " + request.getCode() + " existe déjà.");
                }
                RefOrganismeSocial org = new RefOrganismeSocial();
                org.setCode(request.getCode());
                org.setLibelle(request.getLibelle());
                if (request.getCategorie() != null) org.setCategorie(request.getCategorie());
                if (request.getEstActif() != null) org.setEstActif(request.getEstActif());

                org.save(c);
                AuditLog.audit(c, "CREATION", "ref_organisme_social", String.valueOf(org.getIdOrganisme()), null, org);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(org);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateOrganisme(@PathVariable int id, @RequestBody OrganismeSocialRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefOrganismeSocial dao = new RefOrganismeSocial();
                RefOrganismeSocial existing = (RefOrganismeSocial) dao.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                            "Organisme social non trouvé.");
                }
                if (request.getCode() != null) existing.setCode(request.getCode());
                if (request.getLibelle() != null) existing.setLibelle(request.getLibelle());
                if (request.getCategorie() != null) existing.setCategorie(request.getCategorie());
                if (request.getEstActif() != null) existing.setEstActif(request.getEstActif());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_organisme_social", String.valueOf(id), null, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}/desactiver")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateOrganisme(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefOrganismeSocial dao = new RefOrganismeSocial();
                RefOrganismeSocial existing = (RefOrganismeSocial) dao.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                            "Organisme social non trouvé.");
                }
                existing.setEstActif(false);
                existing.update(c);
                AuditLog.audit(c, "DESACTIVATION", "ref_organisme_social", String.valueOf(id), null, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}/activer")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateOrganisme(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefOrganismeSocial dao = new RefOrganismeSocial();
                RefOrganismeSocial existing = (RefOrganismeSocial) dao.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                            "Organisme social non trouvé.");
                }
                existing.setEstActif(true);
                existing.update(c);
                AuditLog.audit(c, "ACTIVATION", "ref_organisme_social", String.valueOf(id), null, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
