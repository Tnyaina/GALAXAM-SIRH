package mg.galaxam.sirh.api.controller.paie;

import jakarta.validation.Valid;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paie.PaieAjustementRequest;
import mg.galaxam.sirh.api.dto.paie.StatutAjustementRequest;
import mg.galaxam.sirh.model.paie.PaieAvanceAcompte;
import mg.galaxam.sirh.model.paieconfig.RefTypeMouvementPaie;
import mg.galaxam.sirh.securite.AuditLog;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour la saisie et la gestion des variables de paie, acomptes, avances et primes.
 * Base URL : /api/v1/paie/ajustements
 */
@RestController
@RequestMapping("/api/v1/paie/ajustements")
public class PaieAjustementController {

    private final DataSource dataSource;

    public PaieAjustementController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Liste les ajustements, primes et avances filtrés par période et/ou employé.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerAjustements(
            @RequestParam(name = "idPeriode", required = false) Integer idPeriode,
            @RequestParam(name = "idEmploye", required = false) Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            StringBuilder sql = new StringBuilder(
                    "SELECT paa.*, " +
                    "e.matricule, TRIM(CONCAT(COALESCE(e.nom, ''), ' ', COALESCE(e.prenom, ''))) AS nom_complet, " +
                    "COALESCE(rtmp.code, paa.type_mouvement) AS type_code, " +
                    "COALESCE(rtmp.libelle, paa.type_mouvement) AS type_libelle, " +
                    "COALESCE(rtmp.sens, paa.sens) AS type_sens, " +
                    "rtmp.categorie AS type_categorie " +
                    "FROM paie_avance_acompte paa " +
                    "JOIN employe e ON paa.id_employe = e.id_employe " +
                    "LEFT JOIN ref_type_mouvement_paie rtmp ON paa.id_type_mouvement = rtmp.id_type_mouvement " +
                    "WHERE 1=1 "
            );

            List<Object> params = new ArrayList<>();
            if (idPeriode != null) {
                sql.append("AND (paa.id_periode_paie = ? OR paa.id_periode_paie IS NULL) ");
                params.add(idPeriode);
            }
            if (idEmploye != null) {
                sql.append("AND paa.id_employe = ? ");
                params.add(idEmploye);
            }
            sql.append("ORDER BY paa.date_demande DESC, paa.id_avance DESC");

            List<Map<String, Object>> resultats = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("idAvance", rs.getInt("id_avance"));
                        map.put("idEmploye", rs.getInt("id_employe"));
                        map.put("matricule", rs.getString("matricule"));
                        map.put("nomComplet", rs.getString("nom_complet"));
                        map.put("idPeriodePaie", rs.getObject("id_periode_paie"));
                        map.put("idTypeMouvement", rs.getObject("id_type_mouvement"));
                        map.put("typeMouvement", rs.getString("type_code"));
                        map.put("typeLibelle", rs.getString("type_libelle"));
                        map.put("sens", rs.getString("type_sens"));
                        map.put("categorie", rs.getString("type_categorie"));
                        map.put("montant", rs.getBigDecimal("montant"));
                        map.put("dateDemande", rs.getDate("date_demande"));
                        map.put("datePaiement", rs.getDate("date_paiement"));
                        map.put("motif", rs.getString("motif"));
                        map.put("statut", rs.getString("statut"));
                        map.put("idBulletin", rs.getObject("id_bulletin"));
                        map.put("modeReglement", rs.getString("mode_reglement"));
                        resultats.add(map);
                    }
                }
            }
            return ResponseEntity.ok(resultats);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de la récupération des ajustements : " + e.getMessage()));
        }
    }

    /**
     * Enregistre un nouvel ajustement, avance ou prime.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerAjustement(@Valid @RequestBody PaieAjustementRequest req) {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Résolution du type de mouvement
                RefTypeMouvementPaie typeMvt = null;
                if (req.getIdTypeMouvement() != null) {
                    typeMvt = RefTypeMouvementPaie.trouverParId(req.getIdTypeMouvement(), c);
                } else if (req.getTypeMouvement() != null && !req.getTypeMouvement().isBlank()) {
                    typeMvt = RefTypeMouvementPaie.trouverParCode(req.getTypeMouvement().trim(), c);
                }

                String codeType = typeMvt != null ? typeMvt.getCode() : (req.getTypeMouvement() != null ? req.getTypeMouvement() : "AUTIMP");
                String sens = typeMvt != null ? typeMvt.getSens() : (req.getSens() != null ? req.getSens() : "RETENUE");
                Integer idType = typeMvt != null ? typeMvt.getIdTypeMouvement() : null;

                PaieAvanceAcompte entity = new PaieAvanceAcompte();
                entity.setIdEmploye(req.getIdEmploye());
                entity.setIdPeriodePaie(req.getIdPeriodePaie());
                entity.setIdTypeMouvement(idType);
                entity.setTypeMouvement(codeType);
                entity.setSens(sens);
                entity.setMontant(req.getMontant());
                entity.setDateDemande(req.getDateDemande() != null ? req.getDateDemande() : new java.sql.Date(System.currentTimeMillis()));
                entity.setMotif(req.getMotif());
                entity.setModeReglement(req.getModeReglement() != null ? req.getModeReglement() : "VIREMENT");
                entity.setStatut(req.getStatut() != null ? req.getStatut() : "APPROUVE");
                entity.setCreePar(userId);
                if ("APPROUVE".equalsIgnoreCase(entity.getStatut())) {
                    entity.setApprouvePar(userId);
                    entity.setDateAccord(new java.sql.Date(System.currentTimeMillis()));
                }

                entity.save(c);
                AuditLog.audit(c, "CREATION", "paie_avance_acompte", String.valueOf(entity.getIdAvance()), null, entity);

                c.commit();

                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("message", "Ajustement créé avec succès.");
                resp.put("idAvance", entity.getIdAvance());
                resp.put("typeMouvement", codeType);
                resp.put("sens", sens);
                resp.put("montant", entity.getMontant());
                resp.put("statut", entity.getStatut());

                return ResponseEntity.status(HttpStatus.CREATED).body(resp);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(createErrorResponse("Erreur de création : " + ex.getMessage()));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur de connexion : " + e.getMessage()));
        }
    }

    /**
     * Met à jour le statut d'un ajustement (Validation, Refus, Annulation).
     */
    @PutMapping("/{id}/statut")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> changerStatut(@PathVariable("id") int id, @Valid @RequestBody StatutAjustementRequest req) {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieAvanceAcompte entity = PaieAvanceAcompte.trouverParId(id, c);
                if (entity == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createErrorResponse("Ajustement introuvable."));
                }

                if ("IMPUTE_PAIE".equalsIgnoreCase(entity.getStatut())) {
                    return ResponseEntity.badRequest().body(createErrorResponse("Impossible de modifier un mouvement déjà imputé sur un bulletin de paie clôturé ou validé."));
                }

                entity.setStatut(req.getStatut().toUpperCase());
                if ("APPROUVE".equalsIgnoreCase(req.getStatut())) {
                    entity.setApprouvePar(userId);
                    entity.setDateAccord(new java.sql.Date(System.currentTimeMillis()));
                }
                if (req.getMotif() != null && !req.getMotif().isBlank()) {
                    entity.setMotif((entity.getMotif() != null ? entity.getMotif() + " | " : "") + req.getMotif());
                }

                entity.update(c);
                AuditLog.audit(c, "MODIFICATION_STATUT", "paie_avance_acompte", String.valueOf(id), null, entity);

                c.commit();
                return ResponseEntity.ok(Map.of("success", true, "idAvance", id, "statut", entity.getStatut()));
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(createErrorResponse("Erreur lors du changement de statut : " + ex.getMessage()));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur de connexion : " + e.getMessage()));
        }
    }

    /**
     * Supprime un ajustement (si non encore imputé).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> supprimerAjustement(@PathVariable("id") int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieAvanceAcompte entity = PaieAvanceAcompte.trouverParId(id, c);
                if (entity == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createErrorResponse("Ajustement introuvable."));
                }

                if ("IMPUTE_PAIE".equalsIgnoreCase(entity.getStatut())) {
                    return ResponseEntity.badRequest().body(createErrorResponse("Impossible de supprimer un mouvement déjà imputé sur un bulletin de paie."));
                }

                entity.delete(c);
                AuditLog.audit(c, "SUPPRESSION", "paie_avance_acompte", String.valueOf(id), entity, null);

                c.commit();
                return ResponseEntity.ok(Map.of("success", true, "message", "Ajustement supprimé avec succès."));
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(createErrorResponse("Erreur lors de la suppression : " + ex.getMessage()));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur de connexion : " + e.getMessage()));
        }
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getIdUtilisateur();
        }
        return 1;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("error", message);
        return resp;
    }
}
