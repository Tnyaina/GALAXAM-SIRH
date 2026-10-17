package mg.galaxam.sirh.paie.api;

import jakarta.validation.Valid;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieDTO;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieSaveRequest;
import mg.galaxam.sirh.paie.api.dto.stc.ScellementStcRequest;
import mg.galaxam.sirh.paie.api.dto.stc.SimulationStcResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcReglementRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcSituationReglementDTO;
import mg.galaxam.sirh.paie.api.dto.stc.AnnulationReglementRequest;
import mg.galaxam.sirh.paie.api.dto.stc.ClotureDossierRequest;
import mg.galaxam.sirh.paie.application.DossierSortieService;
import mg.galaxam.sirh.paie.application.export.StcDocumentService;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour la gestion et le suivi du dossier de sortie du salarié.
 * URL canonique : /api/paie/dossier-sortie (avec server.servlet.context-path=/api).
 */
@RestController
@RequestMapping({"/api/v1/paie/dossier-sortie", "/v1/paie/dossier-sortie", "/paie/dossier-sortie"})
public class DossierSortieController {

    private final DataSource dataSource;
    private final DossierSortieService dossierSortieService;
    private final StcDocumentService stcDocumentService;

    public DossierSortieController(DataSource dataSource, DossierSortieService dossierSortieService,
                                  StcDocumentService stcDocumentService) {
        this.dataSource = dataSource;
        this.dossierSortieService = dossierSortieService;
        this.stcDocumentService = stcDocumentService;
    }

    /**
     * Liste tous les dossiers de sortie en cours ou passés.
     */
    @GetMapping("/liste")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<DossierSortieDTO>> listerDossiers() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok(dossierSortieService.listerDossiers(c));
        }
    }

    /** Recherche minimale des salariés pour ouvrir un dossier, sans exposer leur fiche RH complète. */
    @GetMapping("/employes")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> rechercherEmployes(@RequestParam(name = "q", defaultValue = "") String recherche) throws Exception {
        String terme = "%" + recherche.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String sql = "SELECT id_employe, matricule, nom, prenom FROM employe "
                + "WHERE est_actif IS TRUE AND (LOWER(nom) LIKE ? ESCAPE '\\' OR LOWER(prenom) LIKE ? ESCAPE '\\' "
                + "OR LOWER(matricule) LIKE ? ESCAPE '\\') ORDER BY nom, prenom, id_employe LIMIT 20";
        List<Map<String, Object>> resultat = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= 3; i++) ps.setString(i, terme);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) resultat.add(Map.of(
                        "idEmploye", rs.getInt("id_employe"), "matricule", rs.getString("matricule"),
                        "nom", rs.getString("nom"), "prenom", rs.getString("prenom")));
            }
        }
        return ResponseEntity.ok(resultat);
    }

    /**
     * Récupère un dossier de sortie par son identifiant avec sa checklist dynamique et ses données sources en lecture.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<DossierSortieDTO> obtenirDossier(@PathVariable("id") Integer id) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DossierSortieDTO dto = dossierSortieService.obtenirDossier(id, c);
            if (dto == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Dossier de sortie introuvable pour l'identifiant " + id);
            }
            return ResponseEntity.ok(dto);
        }
    }

    /**
     * Récupère ou initialise la vue d'un dossier de sortie pour un collaborateur spécifique.
     */
    @GetMapping("/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<DossierSortieDTO> obtenirDossierEmploye(
            @PathVariable("idEmploye") Integer idEmploye,
            @RequestParam(name = "dateDepart", required = false) String dateDepart,
            @RequestParam(name = "motif", required = false) String motif) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Date dateSql = null;
            if (dateDepart != null && !dateDepart.isBlank()) {
                String clean = dateDepart.contains("T") ? dateDepart.split("T")[0] : dateDepart.trim();
                dateSql = Date.valueOf(LocalDateParseHelper(clean));
            }
            DossierSortieDTO dto = dossierSortieService.obtenirDossierParEmploye(idEmploye, dateSql, motif, c);
            return ResponseEntity.ok(dto);
        }
    }

    /**
     * Sauvegarde (création ou mise à jour) d'un dossier de sortie préparatoire.
     * Mémorise les décisions et justifications sans figer le pointage ni impacter la paie.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<DossierSortieDTO> sauvegarderDossier(
            @Valid @RequestBody DossierSortieSaveRequest req) throws Exception {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DossierSortieDTO dto = dossierSortieService.sauvegarderDossier(req, userId, c);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(dto);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Constate la délivrance du certificat de travail légal (Art. 50 Code du travail malgache 2024).
     */
    @PostMapping("/{id}/certificat")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> enregistrerDelivranceCertificat(
            @PathVariable("id") Integer id,
            @RequestBody Map<String, Object> payload) throws Exception {
        Integer userId = getCurrentUserId();
        Date dateDelivrance = null;
        if (payload.get("dateDelivrance") != null) {
            dateDelivrance = Date.valueOf(LocalDateParseHelper(payload.get("dateDelivrance").toString()));
        }
        String modeRemise = payload.get("modeRemise") != null ? payload.get("modeRemise").toString() : "MAIN_PROPRE";

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                dossierSortieService.enregistrerDelivranceCertificat(id, dateDelivrance, modeRemise, userId, c);
                c.commit();
                return ResponseEntity.noContent().build();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Met à jour les formalités d'offboarding (restitution matériel, badge, clés).
     */
    @PutMapping("/{id}/offboarding")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> mettreAJourOffboarding(
            @PathVariable("id") Integer id,
            @RequestBody Map<String, Object> formalites) throws Exception {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                dossierSortieService.mettreAJourOffboarding(id, formalites, userId, c);
                c.commit();
                return ResponseEntity.noContent().build();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Lance la simulation financière du STC depuis le dossier préparatoire.
     * Génère la référence vérifiable et capture le snapshot des données métiers.
     */
    @PostMapping("/{id}/simuler")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<SimulationStcResultDTO> simulerStc(@PathVariable("id") Integer id) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                SimulationStcResultDTO res = dossierSortieService.simulerStcDepuisDossier(id, c);
                c.commit();
                return ResponseEntity.ok(res);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Valide et scelle transactionnellement le STC après vérification explicite du décompte.
     * Exige la référence de simulation et le numéro de version pour contrôle optimiste et fraîcheur.
     */
    @PostMapping("/{id}/sceller")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<DossierSortieDTO> scellerStc(
            @PathVariable("id") Integer id,
            @Valid @RequestBody ScellementStcRequest req) throws Exception {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DossierSortieDTO dto = dossierSortieService.scellerDossierEtStc(id, req, userId, c);
                c.commit();
                return ResponseEntity.ok(dto);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Enregistre un versement de règlement STC directement depuis le dossier de sortie scellé.
     */
    @PostMapping("/{id}/reglement")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<StcSituationReglementDTO> enregistrerReglement(
            @PathVariable("id") Integer id,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody StcReglementRequest req) throws Exception {
        Integer userId = getCurrentUserId();
        StcReglementRequest effectiveReq = req;
        if ((req.idempotencyKey() == null || req.idempotencyKey().isBlank())
                && idempotencyHeader != null && !idempotencyHeader.isBlank()) {
            effectiveReq = new StcReglementRequest(
                    req.dateReglement(),
                    req.referenceReglement(),
                    req.montantRegle(),
                    req.modeReglement(),
                    req.commentaire(),
                    idempotencyHeader.trim()
            );
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                StcSituationReglementDTO sit = dossierSortieService.enregistrerReglementDepuisDossier(id, effectiveReq, userId, c);
                c.commit();
                return ResponseEntity.ok(sit);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Récupère la situation financière détaillée des règlements pour un dossier de sortie.
     */
    @GetMapping("/{id}/reglements")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<StcSituationReglementDTO> obtenirSituationReglements(@PathVariable("id") Integer id) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            StcSituationReglementDTO sit = dossierSortieService.obtenirSituationReglementsDepuisDossier(id, c);
            if (sit == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Aucun règlement ou STC associé au dossier " + id);
            }
            return ResponseEntity.ok(sit);
        }
    }

    /**
     * Annule une ligne de versement depuis le dossier de sortie.
     */
    @PostMapping("/{id}/reglement/{idReglement}/annuler")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<StcSituationReglementDTO> annulerReglement(
            @PathVariable("id") Integer id,
            @PathVariable("idReglement") Integer idReglement,
            @Valid @RequestBody AnnulationReglementRequest req) throws Exception {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                StcSituationReglementDTO sit = dossierSortieService.annulerReglementDepuisDossier(id, idReglement, req, userId, c);
                c.commit();
                return ResponseEntity.ok(sit);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Clôture administrative du dossier de sortie (Phase 3.2).
     */
    @PostMapping("/{id}/cloturer")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<DossierSortieDTO> cloturerDossier(
            @PathVariable("id") Integer id,
            @RequestBody(required = false) ClotureDossierRequest req) throws Exception {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DossierSortieDTO dto = dossierSortieService.cloturerDossier(id, req, userId, c);
                c.commit();
                return ResponseEntity.ok(dto);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    @GetMapping("/{id}/documents/decompte/pdf")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> decomptePdf(@PathVariable Integer id) throws Exception {
        return document(id, StcDocumentService.Type.DECOMPTE, "decompte-stc");
    }

    @GetMapping("/{id}/documents/recu-stc/pdf")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> recuPdf(@PathVariable Integer id) throws Exception {
        return document(id, StcDocumentService.Type.RECU, "recu-stc-projet");
    }

    @GetMapping("/{id}/documents/certificat/pdf")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> certificatPdf(@PathVariable Integer id) throws Exception {
        return document(id, StcDocumentService.Type.CERTIFICAT, "certificat-travail");
    }

    @GetMapping("/{id}/documents/certificat/docx")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> certificatDocx(@PathVariable Integer id) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setReadOnly(true);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"certificat-travail-" + id + ".docx\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(stcDocumentService.certificatDocx(id, c));
        }
    }

    @GetMapping("/{id}/documents/bordereau-remise/pdf")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> bordereauPdf(@PathVariable Integer id) throws Exception {
        return document(id, StcDocumentService.Type.BORDEREAU, "bordereau-remise");
    }

    @GetMapping("/{id}/documents/quittance/{idReglement}/pdf")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> justificatifPdf(@PathVariable Integer id,
                                                  @PathVariable Integer idReglement) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setReadOnly(true);
            return pdf(stcDocumentService.justificatif(id, idReglement, c),
                    "justificatif-stc-" + id + "-" + idReglement + ".pdf");
        }
    }

    private ResponseEntity<byte[]> document(Integer id, StcDocumentService.Type type,
                                            String prefix) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setReadOnly(true);
            return pdf(stcDocumentService.pdf(id, type, c), prefix + "-" + id + ".pdf");
        }
    }

    private ResponseEntity<byte[]> pdf(byte[] body, String filename) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF).body(body);
    }

    private java.time.LocalDate LocalDateParseHelper(String s) {
        return java.time.LocalDate.parse(s.trim());
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getIdUtilisateur();
        }
        return 1;
    }
}
