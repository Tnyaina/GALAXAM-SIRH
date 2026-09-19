package mg.galaxam.sirh.api.controller.conge;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.model.conge.MouvementSoldeConge;
import mg.galaxam.sirh.model.conge.VPoolCongeAnnuelConsommation;
import mg.galaxam.sirh.model.conge.VSoldeCongeEmploye;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/conges")
public class CongeSoldeController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private mg.galaxam.sirh.cron.PurgeCongeScheduler purgeCongeScheduler;

    @Autowired
    private mg.galaxam.sirh.model.conge.service.CongeService congeService;

    @Autowired
    private mg.galaxam.sirh.model.conge.service.CongeTauxApplicableService congeTauxApplicableService;

    @GetMapping("/planning")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getPlanning(
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateDebut,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateFin,
            @RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) Integer idDepartement,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "TOUS") String filterMode,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> planning = congeService.getPlanningCollaborateurs(
                    annee, mois, dateDebut, dateFin, idSite, idDepartement,
                    search, filterMode, page, size, c);
            return ResponseEntity.ok(planning);
        } catch (Exception e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.INTERNAL_ERROR);
        }
    }

    @PostMapping("/purge-expiration")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> declencherPurgeManuelle() {
        try {
            purgeCongeScheduler.purgerCongesExpires();
            return ResponseEntity.ok(Map.of("success", true, "message", "Purge triennale des congés exécutée avec succès."));
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/solde/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getSoldeEmploye(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
            if (solde != null && congeTauxApplicableService != null) {
                try {
                    BigDecimal salaireBase = BigDecimal.ZERO;
                    String sqlBase = "SELECT salaire_base FROM employe_affectation_carriere WHERE id_employe = ? ORDER BY date_debut DESC NULLS LAST LIMIT 1";
                    try (java.sql.PreparedStatement psBase = c.prepareStatement(sqlBase)) {
                        psBase.setInt(1, idEmploye);
                        try (java.sql.ResultSet rsBase = psBase.executeQuery()) {
                            if (rsBase.next() && rsBase.getBigDecimal("salaire_base") != null) {
                                salaireBase = rsBase.getBigDecimal("salaire_base");
                            }
                        }
                    }
                    solde.setSalaireBase(salaireBase);
                    var resTaux = congeTauxApplicableService.resoudreTauxCongeApplicable(idEmploye, java.time.LocalDate.now(), salaireBase, c);
                    if (resTaux != null) {
                        solde.setTauxJournalierConge(resTaux.getTauxJournalier());
                        solde.setSourceTauxConge(resTaux.getSourceProvenance() != null ? resTaux.getSourceProvenance().name() : null);
                    }
                } catch (Exception ex) {
                    // Non bloquant : si non calculable au jour j, laisser null
                }
            }
            return ResponseEntity.ok(solde);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/mouvements/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getMouvementsEmploye(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            List<MouvementSoldeConge> list = MouvementSoldeConge.listParEmploye(idEmploye, c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/solde/imputation/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getImputationSolde(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            mg.galaxam.sirh.model.conge.engine.PurgeFifoEngine.ImputationResult res = congeService.getImputationSolde(idEmploye, c);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/pool-consommation/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getConsommationPool(
            @PathVariable Integer idEmploye,
            @RequestParam(value = "annee", required = false) Integer annee) {
        try (Connection c = dataSource.getConnection()) {
            List<VPoolCongeAnnuelConsommation> list = VPoolCongeAnnuelConsommation.listParEmployeEtAnnee(idEmploye, annee, c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/solde/ajustement")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerAjustementManuel(@RequestBody Map<String, Object> req) {
        if (!req.containsKey("idEmploye") || !req.containsKey("nbJours") || !req.containsKey("commentaire")) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "L'idEmploye, le nbJours et le commentaire explicatif sont obligatoires pour un ajustement.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Integer idEmploye = Integer.parseInt(req.get("idEmploye").toString());
                BigDecimal nbJours = new BigDecimal(req.get("nbJours").toString());
                String commentaire = req.get("commentaire").toString();

                Integer currentUserId = getCurrentUserId();

                MouvementSoldeConge mvt = new MouvementSoldeConge();
                mvt.setIdEmploye(idEmploye);
                mvt.setTypeMouvement("AJUSTEMENT");
                mvt.setNbJours(nbJours);
                mvt.setCommentaire(commentaire);
                mvt.setCreatedBy(currentUserId);
                mvt.save(c);

                c.commit();

                VSoldeCongeEmploye soldeMisAJour = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
                return ResponseEntity.ok(soldeMisAJour);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Impossible d'enregistrer cet ajustement de solde.");
        }
    }

    @PostMapping("/solde/initialisation")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> initialiserSoldeHistorique(@RequestBody Map<String, Object> req) {
        if (!req.containsKey("idEmploye") || !req.containsKey("soldeInitial") || !req.containsKey("dateArrete")) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "L'idEmploye, le soldeInitial et la dateArrete sont obligatoires pour l'initialisation de solde.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int idEmploye = Integer.parseInt(req.get("idEmploye").toString());
                BigDecimal soldeInitial = new BigDecimal(req.get("soldeInitial").toString());
                java.time.LocalDate dateArrete = java.time.LocalDate.parse(req.get("dateArrete").toString());
                boolean inclutAcquisitionMoisArrete = req.containsKey("inclutAcquisitionMoisArrete") && Boolean.parseBoolean(req.get("inclutAcquisitionMoisArrete").toString());
                String motif = req.containsKey("motif") && req.get("motif") != null ? req.get("motif").toString() : null;
                Integer currentUserId = getCurrentUserId();

                congeService.initialiserSoldeHistorique(idEmploye, soldeInitial, dateArrete, inclutAcquisitionMoisArrete, motif, currentUserId, c);

                if (req.containsKey("tauxJournalierMigration") && req.get("tauxJournalierMigration") != null && !req.get("tauxJournalierMigration").toString().isBlank()) {
                    BigDecimal tauxMigration = new BigDecimal(req.get("tauxJournalierMigration").toString());
                    if (tauxMigration.compareTo(BigDecimal.ZERO) > 0) {
                        enregistrerOuMettreAJourTauxMigration(idEmploye, tauxMigration, dateArrete, motif, currentUserId, c);
                    }
                }
                c.commit();

                VSoldeCongeEmploye soldeMisAJour = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
                if (soldeMisAJour != null && congeTauxApplicableService != null) {
                    try {
                        var resTaux = congeTauxApplicableService.resoudreTauxCongeApplicable(idEmploye, java.time.LocalDate.now(), null, c);
                        if (resTaux != null) {
                            soldeMisAJour.setTauxJournalierConge(resTaux.getTauxJournalier());
                            soldeMisAJour.setSourceTauxConge(resTaux.getSourceProvenance() != null ? resTaux.getSourceProvenance().name() : null);
                        }
                    } catch (Exception ignored) {}
                }
                return ResponseEntity.ok(soldeMisAJour);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Impossible d'enregistrer cette initialisation de solde : " + e.getMessage());
        }
    }

    @PostMapping("/taux-applicable/migration")
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> enregistrerTauxMigrationStandalone(@RequestBody Map<String, Object> req) {
        if (!req.containsKey("idEmploye") || !req.containsKey("tauxJournalier") || !req.containsKey("dateEffet")) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "idEmploye, tauxJournalier et dateEffet sont obligatoires.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int idEmploye = Integer.parseInt(req.get("idEmploye").toString());
                BigDecimal taux = new BigDecimal(req.get("tauxJournalier").toString());
                java.time.LocalDate dateEffet = java.time.LocalDate.parse(req.get("dateEffet").toString());
                String commentaire = req.containsKey("commentaire") && req.get("commentaire") != null ? req.get("commentaire").toString() : null;
                Integer currentUserId = getCurrentUserId();

                enregistrerOuMettreAJourTauxMigration(idEmploye, taux, dateEffet, commentaire, currentUserId, c);
                c.commit();

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "idEmploye", idEmploye,
                        "tauxJournalier", taux,
                        "dateEffet", dateEffet,
                        "sourceProvenance", "KAJY_MIGRATION"
                ));
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Impossible d'enregistrer le taux de migration : " + e.getMessage());
        }
    }

    private void enregistrerOuMettreAJourTauxMigration(int idEmploye, BigDecimal tauxMigration, java.time.LocalDate dateEffet, String motif, Integer currentUserId, Connection c) throws Exception {
        String sqlClose = "UPDATE conge_taux_applicable SET date_fin = ? WHERE id_employe = ? AND (date_fin IS NULL OR date_fin > ?) AND date_effet < ?";
        try (java.sql.PreparedStatement psClose = c.prepareStatement(sqlClose)) {
            psClose.setDate(1, java.sql.Date.valueOf(dateEffet));
            psClose.setInt(2, idEmploye);
            psClose.setDate(3, java.sql.Date.valueOf(dateEffet));
            psClose.setDate(4, java.sql.Date.valueOf(dateEffet));
            psClose.executeUpdate();
        }
        String sqlDel = "DELETE FROM conge_taux_applicable WHERE id_employe = ? AND date_effet >= ? AND source_provenance = 'KAJY_MIGRATION'";
        try (java.sql.PreparedStatement psDel = c.prepareStatement(sqlDel)) {
            psDel.setInt(1, idEmploye);
            psDel.setDate(2, java.sql.Date.valueOf(dateEffet));
            psDel.executeUpdate();
        }
        congeTauxApplicableService.enregistrerTauxApplicableMigration(
                idEmploye,
                tauxMigration,
                dateEffet,
                null,
                motif != null ? motif : "Reprise historique TCONGE Excel",
                currentUserId,
                c
        );
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getIdUtilisateur();
        }
        return null;
    }
}
