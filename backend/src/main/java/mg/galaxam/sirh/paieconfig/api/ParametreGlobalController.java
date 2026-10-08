package mg.galaxam.sirh.paieconfig.api;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.paieconfig.api.dto.ParametreGlobalRequest;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/parametres-globaux")
public class ParametreGlobalController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllParametresGlobaux(@RequestParam(required = false) String categorie) {
        try (Connection c = dataSource.getConnection()) {
            List<ParametreGlobal> list = categorie == null || categorie.isBlank()
                    ? ParametreGlobal.listAll(c)
                    : ParametreGlobal.listByCategorieExact(categorie, c);
            return ResponseEntity.ok(list.stream()
                    .filter(p -> !"EXONERATION_HS_IRSA_ACTIVE".equalsIgnoreCase(p.getCodeParametre())
                              && !"PLAFOND_MENSUEL_HS_EXONEREES".equalsIgnoreCase(p.getCodeParametre()))
                    .toList());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getParametreGlobalById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            ParametreGlobal pg = new ParametreGlobal();
            ParametreGlobal found = (ParametreGlobal) pg.rechercherParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Paramètre global non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createParametreGlobal(@RequestBody ParametreGlobalRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                verifierVersionFutureDecompteConges(request, null);
                if (request.getCodeParametre() != null && request.getDateDebut() != null) {
                    ParametreGlobal.cloturerPrecedent(request.getCodeParametre(), request.getDateDebut(), c);
                }
                verifierAbsenceChevauchementFeries(request, null, c);

                ParametreGlobal pg = new ParametreGlobal();
                pg.setCodeParametre(request.getCodeParametre());
                pg.setValeur(request.getValeur());
                pg.setDescription(request.getDescription());
                pg.setCategorie(request.getCategorie());
                pg.setDateDebut(request.getDateDebut());
                pg.setDateFin(request.getDateFin());
                pg.setEstActif(request.getEstActif() != null ? request.getEstActif() : true);
                pg.setEstDesactivable("CONGE_DECOMPTE_FERIES".equalsIgnoreCase(request.getCodeParametre())
                        || Boolean.TRUE.equals(request.getEstDesactivable()));

                verifierDesactivationDocumentee(pg);

                pg.save(c);
                AuditLog.audit(c, "CREATION", "parametre_global", String.valueOf(pg.getIdParametre()), null, pg);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(pg);
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
    public ResponseEntity<?> updateParametreGlobal(@PathVariable int id, @RequestBody ParametreGlobalRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ParametreGlobal pg = new ParametreGlobal();
                ParametreGlobal existing = (ParametreGlobal) pg.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Paramètre global non trouvé.");
                }

                verifierVersionFutureDecompteConges(request, existing);
                verifierAbsenceChevauchementFeries(request, id, c);

                if (request.getEstActif() != null && !request.getEstActif() && !Boolean.TRUE.equals(existing.getEstDesactivable())) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Ce paramètre ne peut pas être désactivé.");
                }

                existing.setCodeParametre(request.getCodeParametre());
                existing.setValeur(request.getValeur());
                existing.setDescription(request.getDescription());
                existing.setCategorie(request.getCategorie());
                existing.setDateDebut(request.getDateDebut());
                existing.setDateFin(request.getDateFin());
                if (request.getEstActif() != null) {
                    existing.setEstActif(request.getEstActif());
                }
                if ("CONGE_DECOMPTE_FERIES".equalsIgnoreCase(request.getCodeParametre())) {
                    existing.setEstDesactivable(true);
                } else if (request.getEstDesactivable() != null) {
                    existing.setEstDesactivable(request.getEstDesactivable());
                }

                verifierDesactivationDocumentee(existing);

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "parametre_global", String.valueOf(id), null, existing);

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

    private static void verifierVersionFutureDecompteConges(ParametreGlobalRequest request, ParametreGlobal existing) {
        boolean speciale = "CONGE_DECOMPTE_FERIES".equalsIgnoreCase(request.getCodeParametre())
                || (existing != null && "CONGE_DECOMPTE_FERIES".equalsIgnoreCase(existing.getCodeParametre()));
        if (!speciale) return;
        java.time.LocalDate demain = java.time.LocalDate.now().plusDays(1);
        if (request.getDateDebut() == null || request.getDateDebut().toLocalDate().isBefore(demain)
                || (existing != null && (existing.getDateDebut() == null
                    || existing.getDateDebut().toLocalDate().isBefore(demain)))
                || (existing != null && !request.getDateDebut().equals(existing.getDateDebut()))
                || !"CONGE_DECOMPTE_FERIES".equalsIgnoreCase(request.getCodeParametre())
                || !("INCLUS".equals(request.getValeur()) || "EXCLUS".equals(request.getValeur()))) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "CONGE_DECOMPTE_FERIES : créez ou modifiez uniquement une version future de valeur "
                    + "INCLUS ou EXCLUS. L'historique ne peut pas être réécrit.");
        }
    }

    private static void verifierAbsenceChevauchementFeries(ParametreGlobalRequest request, Integer exclureId,
            Connection c) throws java.sql.SQLException {
        if (!"CONGE_DECOMPTE_FERIES".equalsIgnoreCase(request.getCodeParametre())) return;
        if (request.getDateDebut() == null) throw new IllegalArgumentException("dateDebut obligatoire.");
        String sql = "SELECT id_parametre FROM parametre_global WHERE code_parametre='CONGE_DECOMPTE_FERIES' "
                + "AND date_debut <= COALESCE(?, DATE '9999-12-31') "
                + "AND COALESCE(date_fin, DATE '9999-12-31') >= ? "
                + (exclureId != null ? "AND id_parametre<>? " : "") + "FOR UPDATE";
        try (var ps = c.prepareStatement(sql)) {
            if (request.getDateFin() == null) ps.setNull(1, java.sql.Types.DATE);
            else ps.setDate(1, request.getDateFin());
            ps.setDate(2, request.getDateDebut());
            if (exclureId != null) ps.setInt(3, exclureId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) throw new IllegalArgumentException("AMBIGUOUS_CONFIGURATION: CONGE_DECOMPTE_FERIES "
                        + "chevauche la version " + rs.getInt(1) + "; bornez explicitement les versions.");
            }
        }
    }

    @RequestMapping(value = "/{id}/toggle-actif", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN') or hasAuthority('ORGANISATION_WRITE') or hasAuthority('POINTAGE_WRITE')")
    public ResponseEntity<?> toggleActif(@PathVariable int id, @RequestBody(required = false) java.util.Map<String, Boolean> body) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ParametreGlobal pg = new ParametreGlobal();
                ParametreGlobal existing = (ParametreGlobal) pg.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Paramètre global non trouvé.");
                }

                if ("CONGE_DECOMPTE_FERIES".equalsIgnoreCase(existing.getCodeParametre())) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                            "CONGE_DECOMPTE_FERIES se désactive par une version datée future, jamais par bascule immédiate.");
                }
                if (!Boolean.TRUE.equals(existing.getEstDesactivable())) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Ce paramètre ne peut pas être désactivé.");
                }

                verifierDesactivationDocumentee(existing);
                java.time.LocalDate aujourdHui = java.time.LocalDate.now();
                if (existing.getDateDebut() == null
                        || existing.getDateDebut().toLocalDate().isAfter(aujourdHui)
                        || (existing.getDateFin() != null && existing.getDateFin().toLocalDate().isBefore(aujourdHui))) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                            "Seule une version du paramètre en vigueur peut être basculée directement.");
                }

                boolean nouveauStatut;
                if (body != null && body.containsKey("estActif")) {
                    nouveauStatut = Boolean.TRUE.equals(body.get("estActif"));
                } else {
                    nouveauStatut = !Boolean.TRUE.equals(existing.getEstActif());
                }

                existing.setEstActif(nouveauStatut);
                existing.update(c);
                AuditLog.audit(c, nouveauStatut ? "ACTIVATION" : "DESACTIVATION", "parametre_global", String.valueOf(id), null, existing);

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

    private static void verifierDesactivationDocumentee(ParametreGlobal parametre) {
        if (Boolean.TRUE.equals(parametre.getEstDesactivable())
                && parametre.getEffetDesactivation() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "La désactivation de ce paramètre n'a aucun effet métier vérifié. Corrigez d'abord son consommateur backend.");
        }
    }

    /**
     * Annulation d'une version PROGRAMMÉE (date_debut > aujourd'hui).
     * Interdit sur les versions EN_VIGUEUR ou ARCHIVÉE.
     * Restaure automatiquement le date_fin=NULL de la version précédente.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerProgramme(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ParametreGlobal pg = new ParametreGlobal();
                ParametreGlobal cible = (ParametreGlobal) pg.rechercherParId(id, c);
                if (cible == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Paramètre global non trouvé.");
                }
                ParametreGlobal.annulerProgramme(id, c);
                AuditLog.audit(c, "ANNULATION_PROGRAMME", "parametre_global", String.valueOf(id), cible, null);
                c.commit();
                return ResponseEntity.ok("La version programmée du paramètre \"" + cible.getCodeParametre() + "\" a été annulée et la version précédente a été réactivée.");
            } catch (IllegalStateException ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
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
