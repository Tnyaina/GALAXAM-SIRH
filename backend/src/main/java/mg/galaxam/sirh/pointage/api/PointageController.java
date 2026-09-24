package mg.galaxam.sirh.pointage.api;

import mg.galaxam.sirh.pointage.api.dto.*;
import mg.galaxam.sirh.pointage.application.PointageAnomalieService;
import mg.galaxam.sirh.pointage.application.PointageEmployeService;
import mg.galaxam.sirh.pointage.application.PointageExcelExportService;
import mg.galaxam.sirh.pointage.application.PointageService;
import mg.galaxam.sirh.pointage.application.PointageSyntheseService;
import mg.galaxam.sirh.pointage.persistence.EmployeIndemniteDerogation;
import mg.galaxam.sirh.securite.AuditLog;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Time;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/pointages")
public class PointageController {

    @Autowired
    private PointageSyntheseService pointageSyntheseService;
    @Autowired private PointageService pointageSaisieService;
    @Autowired private PointageAnomalieService pointageAnomalieService;
    @Autowired private PointageEmployeService pointageEmployeService;
    @Autowired private PointageExcelExportService pointageExcelExportService;
    @Autowired private DataSource dataSource;

    // Synthèse et semaines calculées en mémoire
    @GetMapping("/synthese")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSynthese(
            @RequestParam(defaultValue = "0") int idPeriodePaie,
            @RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "ALL") String filtre,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "25") int size
    ) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        if (idPeriodePaie <= 0) {
            resp.put("synthese", java.util.Collections.emptyList());
            resp.put("totalElements", 0);
            resp.put("totalPages", 0);
            resp.put("currentPage", 1);
            resp.put("pageSize", size);
            return ResponseEntity.ok(resp);
        }
        if (page <= 0 && size <= 0) {
            // Mode non-paginé pour compatibilité ascendante si demandé
            List<PointageSyntheseMensuelleDTO> data = pointageSyntheseService.getSynthesePeriode(idPeriodePaie, idSite);
            resp.put("synthese", data);
            resp.put("totalElements", data.size());
            resp.put("totalPages", 1);
            resp.put("currentPage", 1);
            resp.put("pageSize", data.size());
            return ResponseEntity.ok(resp);
        }
        Map<String, Object> data = pointageSyntheseService.getSynthesePeriodePaginee(
                idPeriodePaie, idSite, search, filtre, page, size);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/synthese/semaines")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSemaines(
            @RequestParam(defaultValue = "0") int idPeriodePaie,
            @RequestParam(required = false) Integer idSite
    ) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        if (idPeriodePaie <= 0) {
            resp.put("semaines", java.util.Collections.emptyList());
            return ResponseEntity.ok(resp);
        }
        List<Map<String, Object>> data = pointageSyntheseService.getSemainesPeriode(idPeriodePaie, idSite);
        resp.put("semaines", data);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/synthese/semaine-detail")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSemaineDetail(
            @RequestParam(defaultValue = "0") int idPeriodePaie,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam(required = false) Integer idSite
    ) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        if (idPeriodePaie <= 0 || dateDebut == null || dateFin == null) {
            resp.put("detail", java.util.Collections.emptyList());
            return ResponseEntity.ok(resp);
        }
        java.time.LocalDate dDebut = java.time.LocalDate.parse(dateDebut);
        java.time.LocalDate dFin = java.time.LocalDate.parse(dateFin);
        List<PointageSyntheseMensuelleDTO> data = pointageSyntheseService.getSyntheseSemaineDetail(idPeriodePaie, dDebut, dFin, idSite);
        resp.put("detail", data);
        return ResponseEntity.ok(resp);
    }

    /**
     * Calcule et actualise toutes les semaines de la periode en 1 action transactionnelle.
     * La periode reste EN_COURS pour consultation et corrections par l'agent.
     */
    @PostMapping("/synthese/calculer-tout")
    @PreAuthorize("hasAuthority('POINTAGE_WRITE') or hasAuthority('POINTAGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> calculerTout(
            @RequestBody Map<String, Object> body
    ) throws Exception {
        Integer idPeriode = parseInteger(body.get("idPeriodePaie"));
        if (idPeriode == null || idPeriode <= 0) {
            throw new IllegalArgumentException("L'identifiant de la période de paie est obligatoire.");
        }
        Integer idSite = parseInteger(body.get("idSite"));
        if (idSite != null && idSite <= 0) idSite = null;

        Integer currentUserId = resolveCurrentUserId();
        Map<String, Object> res = pointageSyntheseService.calculerToutesSemainesPeriode(idPeriode, idSite, currentUserId);
        return ResponseEntity.ok(res);
    }

    // Saisie quotidienne
    @GetMapping("/saisie")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getGrilleSaisie(@RequestParam(defaultValue = "0") int idSite,
            @RequestParam String date) throws Exception {
        return ResponseEntity.ok(pointageSaisieService.getGrilleSaisie(idSite, date));
    }

    @PostMapping("/saisie/preview")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('POINTAGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<SaisiePointageJourDTO> previsualiserJour(@RequestBody SaisiePointageJourDTO pointage)
            throws Exception {
        return ResponseEntity.ok(pointageSaisieService.previsualiserPointage(pointage));
    }

    @PostMapping("/saisie/batch")
    @PreAuthorize("hasAuthority('POINTAGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> enregistrerBatch(@RequestBody List<SaisiePointageJourDTO> pointages)
            throws Exception {
        Map<String, Object> resp = new HashMap<>();
        if (pointages == null || pointages.isEmpty()) {
            resp.put("success", true);
            resp.put("message", "Aucun pointage à enregistrer.");
            return ResponseEntity.ok(resp);
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Map<String, Object> result = pointageSaisieService.enregistrerPointagesBatch(pointages, c);
                c.commit();
                return ResponseEntity.ok(result);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    // Anomalies et régularisation
    @GetMapping("/anomalies")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAnomalies(@RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) Integer idPeriode,
            @RequestParam(required = false) Integer idPeriodePaie,
            @RequestParam(required = false) String datePointage,
            @RequestParam(required = false) String search) throws SQLException {
        Integer per = (idPeriode != null && idPeriode > 0) ? idPeriode : idPeriodePaie;
        return ResponseEntity.ok(pointageAnomalieService.getAnomalies(idSite, per, datePointage, search));
    }

    @PutMapping("/anomalies/{idPointage}/regulariser")
    @PreAuthorize("hasAuthority('POINTAGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> regulariser(@PathVariable int idPointage,
            @RequestBody Map<String, Object> payload) throws Exception {
        boolean ok = pointageAnomalieService.regulariserPointage(idPointage, payload);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", ok);
        resp.put("message", ok ? "Pointage régularisé avec succès." : "Échec de la régularisation.");
        return ResponseEntity.ok(resp);
    }

    // Fiche employé et export
    @GetMapping("/employes/{idEmploye}")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<FichePointageEmployeDTO> getFichePointageEmploye(@PathVariable Integer idEmploye,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "periode") String period,
            @RequestParam(required = false) Integer idPeriode) {
        return ResponseEntity.ok(pointageEmployeService.getFichePointageEmploye(idEmploye, date, period, idPeriode));
    }

    @GetMapping("/employes/{idEmploye}/export/excel")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> exportExcelFicheEmploye(@PathVariable Integer idEmploye,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "periode") String period,
            @RequestParam(required = false) Integer idPeriode) throws java.io.IOException {
        FichePointageEmployeDTO fiche = pointageEmployeService.getFichePointageEmploye(idEmploye, date, period, idPeriode);
        byte[] excelBytes = pointageExcelExportService.genererExcelFicheEmploye(fiche);
        String filename = PointageExcelExportService.buildExportFilename(fiche, "xlsx");
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    // Paramètres administratifs
    @GetMapping("/parametres/tolerances")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getTolerances() {
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "SELECT id_site, libelle, heure_entree_minimale_valorisee, plancher_arrivee_actif " +
                     "FROM ref_site ORDER BY id_site ASC");
             java.sql.ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> list = new java.util.ArrayList<>();
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("idSite", rs.getInt("id_site"));
                map.put("siteNom", rs.getString("libelle"));
                Time h = rs.getTime("heure_entree_minimale_valorisee");
                map.put("heurePlafond", h != null ? h.toString() : null);
                map.put("estActif", rs.getBoolean("plancher_arrivee_actif"));
                list.add(map);
            }
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/parametres/tolerances")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> saveTolerance(@RequestBody ToleranceRequest request) throws Exception {
        if (request.getIdSite() == null) {
            throw new IllegalArgumentException("L'identifiant du site est obligatoire.");
        }
        if (request.getEstActif() == null) {
            throw new IllegalArgumentException("L'état actif/inactif du plancher d'arrivée est obligatoire.");
        }
        String timeStr = request.getHeurePlafond();
        Time timeVal = null;
        if (timeStr != null && !timeStr.isBlank()) {
            if (timeStr.length() == 5) timeStr += ":00";
            timeVal = Time.valueOf(timeStr);
        }
        if (request.getEstActif() && timeVal == null) {
            throw new IllegalArgumentException("L'heure d'entrée minimale valorisée est obligatoire lorsque le plancher d'arrivée est actif.");
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String sql = "UPDATE ref_site SET plancher_arrivee_actif = ?, heure_entree_minimale_valorisee = ? WHERE id_site = ?";
                try (java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setBoolean(1, request.getEstActif());
                    ps.setTime(2, timeVal);
                    ps.setInt(3, request.getIdSite());
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        throw new IllegalArgumentException("Site introuvable : " + request.getIdSite());
                    }
                }
                c.commit();
                Map<String, Object> resp = new HashMap<>();
                resp.put("idSite", request.getIdSite());
                resp.put("estActif", request.getEstActif());
                resp.put("heurePlafond", timeVal != null ? timeVal.toString() : null);
                AuditLog.audit(c, "SAVED_PLANCHER_ARRIVEE_SITE", "ref_site", String.valueOf(request.getIdSite()), null, resp);
                return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(resp);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @GetMapping("/parametres/derogations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDerogations() {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok(EmployeIndemniteDerogation.listAllActive(c));
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/parametres/derogations")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createDerogation(@RequestBody DerogationRequest request) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                EmployeIndemniteDerogation d = buildDerogation(request);
                d.save(c);
                c.commit();
                AuditLog.audit(c, "CREATION_DEROGATION_INDEMNITE", "employe_indemnite_derogation", String.valueOf(d.getIdDerogation()), null, d);
                return ResponseEntity.status(HttpStatus.CREATED).body(d);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @PutMapping("/parametres/derogations/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateDerogation(@PathVariable int id, @RequestBody DerogationRequest request) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                EmployeIndemniteDerogation existing = new EmployeIndemniteDerogation().rechercherParId(id, c);
                if (existing == null) throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Dérogation non trouvée.");
                applyDerogation(existing, request);
                existing.update(c);
                c.commit();
                AuditLog.audit(c, "MODIFICATION_DEROGATION_INDEMNITE", "employe_indemnite_derogation", String.valueOf(id), null, existing);
                return ResponseEntity.ok(existing);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @DeleteMapping("/parametres/derogations/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteDerogation(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                EmployeIndemniteDerogation existing = new EmployeIndemniteDerogation().rechercherParId(id, c);
                if (existing == null) throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Dérogation non trouvée.");
                existing.delete(c);
                c.commit();
                AuditLog.audit(c, "SUPPRESSION_DEROGATION_INDEMNITE", "employe_indemnite_derogation", String.valueOf(id), existing, null);
                return ResponseEntity.ok("Dérogation supprimée avec succès.");
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    private EmployeIndemniteDerogation buildDerogation(DerogationRequest request) {
        EmployeIndemniteDerogation d = new EmployeIndemniteDerogation();
        applyDerogation(d, request);
        return d;
    }

    private void applyDerogation(EmployeIndemniteDerogation d, DerogationRequest request) {
        d.setIdEmploye(request.getIdEmploye());
        d.setIdIndemnite(request.getIdIndemnite());
        d.setBeneficie(Boolean.TRUE.equals(request.getBeneficie()));
        d.setMotif(request.getMotif());
        d.setDateDebut(request.getDateDebut());
        d.setDateFin(request.getDateFin());
    }

    private Integer parseInteger(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) return null;
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getIdUtilisateur();
        }
        return null;
    }

    /** Requête locale au paramétrage des tolérances. */
    public static class ToleranceRequest {
        private Integer idSite;
        private String heurePlafond;
        private Boolean estActif;
        public Integer getIdSite() { return idSite; }
        public void setIdSite(Integer idSite) { this.idSite = idSite; }
        public String getHeurePlafond() { return heurePlafond; }
        public void setHeurePlafond(String heurePlafond) { this.heurePlafond = heurePlafond; }
        public Boolean getEstActif() { return estActif; }
        public void setEstActif(Boolean estActif) { this.estActif = estActif; }
    }

    /** Requête locale aux dérogations d'indemnités. */
    public static class DerogationRequest {
        private Integer idEmploye;
        private Integer idIndemnite;
        private Boolean beneficie;
        private String motif;
        private java.sql.Date dateDebut;
        private java.sql.Date dateFin;
        public Integer getIdEmploye() { return idEmploye; }
        public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }
        public Integer getIdIndemnite() { return idIndemnite; }
        public void setIdIndemnite(Integer idIndemnite) { this.idIndemnite = idIndemnite; }
        public Boolean getBeneficie() { return beneficie; }
        public void setBeneficie(Boolean beneficie) { this.beneficie = beneficie; }
        public String getMotif() { return motif; }
        public void setMotif(String motif) { this.motif = motif; }
        public java.sql.Date getDateDebut() { return dateDebut; }
        public void setDateDebut(java.sql.Date dateDebut) { this.dateDebut = dateDebut; }
        public java.sql.Date getDateFin() { return dateFin; }
        public void setDateFin(java.sql.Date dateFin) { this.dateFin = dateFin; }
    }
}
