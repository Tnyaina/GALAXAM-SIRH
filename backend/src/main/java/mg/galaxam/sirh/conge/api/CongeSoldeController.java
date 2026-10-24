package mg.galaxam.sirh.conge.api;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.transaction.TransactionRunner;
import mg.galaxam.sirh.conge.application.CongeExpirationExamenService;
import mg.galaxam.sirh.conge.application.CongeLotValorisationService;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.conge.domain.CongeSituationEmployeDTO;
import mg.galaxam.sirh.conge.domain.CongeSoldeRequests.AjustementSoldeRequest;
import mg.galaxam.sirh.conge.domain.CongeSoldeRequests.ConfirmerExpirationRequest;
import mg.galaxam.sirh.conge.domain.CongeSoldeRequests.CorrigerCutoverRequest;
import mg.galaxam.sirh.conge.domain.CongeSoldeRequests.ReporterExamenRequest;
import mg.galaxam.sirh.conge.persistence.MouvementSoldeConge;
import mg.galaxam.sirh.conge.persistence.RefPoolCongeAnnuel;
import mg.galaxam.sirh.conge.persistence.VSoldeCongeEmploye;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private CongeService congeService;

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    @Autowired
    private CongeLotValorisationService congeLotValorisationService;

    @Autowired
    private CongeExpirationExamenService congeExpirationExamenService;

    @Autowired
    private TransactionRunner transactionRunner;

    @GetMapping("/solde/valorisation-lots/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getValorisationLots(
            @PathVariable Integer idEmploye,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok(congeLotValorisationService.situation(
                    idEmploye, date != null ? date : LocalDate.now(), c));
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/solde/cutover/{idEmploye}/corriger")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> corrigerReliquatCutover(
            @PathVariable Integer idEmploye,
            @RequestBody CorrigerCutoverRequest req) {
        if (req == null || req.nouveauReliquatJours() == null || req.nouveauReliquatValeur() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "nouveauReliquatJours et nouveauReliquatValeur sont obligatoires.");
        }

        return transactionRunner.required(c -> {
            var result = congeLotValorisationService.corrigerReliquatCutover(
                    idEmploye,
                    req.nouveauReliquatJours(),
                    req.nouveauReliquatValeur(),
                    req.motif(),
                    CustomUserDetails.getCurrentUserId(), c);
            return ResponseEntity.ok(result);
        });
    }

    @GetMapping("/planning")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getPlanning(
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
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

    @GetMapping("/expiration/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getExamenExpiration(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok(congeExpirationExamenService.obtenir(idEmploye, LocalDate.now(), c));
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/expiration/employe/{idEmploye}/reporter")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> reporterExamenExpiration(
            @PathVariable Integer idEmploye,
            @RequestBody ReporterExamenRequest req) {
        return transactionRunner.required(c -> {
            LocalDate prochaineDate = req != null ? req.prochaineDate() : null;
            String motif = req != null ? req.motif() : null;
            var result = congeExpirationExamenService.reporter(
                    idEmploye, prochaineDate, motif, CustomUserDetails.getCurrentUserId(), c);
            return ResponseEntity.ok(result);
        });
    }

    @PostMapping("/expiration/employe/{idEmploye}/confirmer")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> confirmerExpiration(
            @PathVariable Integer idEmploye,
            @RequestBody ConfirmerExpirationRequest req) {
        return transactionRunner.required(c -> {
            boolean planification = req != null && Boolean.TRUE.equals(req.planificationVerifiee());
            String motif = req != null ? req.motif() : null;
            var result = congeExpirationExamenService.confirmerExpiration(
                    idEmploye, motif, planification, CustomUserDetails.getCurrentUserId(), c);
            return ResponseEntity.ok(result);
        });
    }

    @GetMapping("/solde/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getSoldeEmploye(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
            if (solde != null) {
                var situation = congeTauxApplicableService.obtenirSituationComplete(idEmploye, LocalDate.now(), c);
                solde.setSalaireBase(situation.getSalaireBase());
                if (situation.getTauxApplicable() != null) {
                    solde.setTauxJournalierConge(situation.getTauxApplicable().getMontant());
                    solde.setSourceTauxConge(situation.getTauxApplicable().getSource());
                }
            }
            return ResponseEntity.ok(solde);
        } catch (Exception e) {
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/solde/imputation/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getImputationSolde(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok(congeService.getImputationSolde(idEmploye, c));
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/pool-consommation/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getConsommationsPoolEmploye(
            @PathVariable Integer idEmploye,
            @RequestParam(value = "annee", required = false) Integer annee) {
        try (Connection c = dataSource.getConnection()) {
            List<RefPoolCongeAnnuel.Consommation> list = RefPoolCongeAnnuel.listerConsommations(idEmploye, annee, c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/solde/ajustement")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerAjustementManuel(@RequestBody AjustementSoldeRequest req) {
        if (req == null || req.idEmploye() == null || req.nbJours() == null || req.commentaire() == null || req.commentaire().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "L'idEmploye, le nbJours et le commentaire explicatif sont obligatoires pour un ajustement.");
        }

        return transactionRunner.required(c -> {
            try (var lock = c.prepareStatement("SELECT id_employe FROM employe WHERE id_employe = ? FOR UPDATE")) {
                lock.setInt(1, req.idEmploye());
                try (var rows = lock.executeQuery()) {
                    if (!rows.next()) throw new IllegalArgumentException("Employé introuvable.");
                }
            }
            VSoldeCongeEmploye soldeActuel = VSoldeCongeEmploye.rechercherParEmploye(req.idEmploye(), c);
            if (soldeActuel.getSoldeActuel().add(req.nbJours()).signum() < 0) {
                throw new IllegalArgumentException("Ajustement refusé : le solde de congés deviendrait négatif.");
            }
            MouvementSoldeConge mvt = new MouvementSoldeConge();
            mvt.setIdEmploye(req.idEmploye());
            mvt.setTypeMouvement("AJUSTEMENT");
            mvt.setNbJours(req.nbJours());
            if (req.dateMouvement() != null) {
                mvt.setDateMouvement(java.sql.Date.valueOf(req.dateMouvement()));
            }
            mvt.setCommentaire(req.commentaire().trim());
            if (req.nbJours().signum() > 0 && req.valorisationSpecifique() != null) {
                mvt.setValeurSource(req.valorisationSpecifique());
            }
            mvt.setCreatedBy(CustomUserDetails.getCurrentUserId());
            mvt.save(c);

            VSoldeCongeEmploye soldeMisAJour = VSoldeCongeEmploye.rechercherParEmploye(req.idEmploye(), c);
            return ResponseEntity.ok(soldeMisAJour);
        });
    }

    @GetMapping("/solde/employe/{idEmploye}/situation")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getSituationEmploye(
            @PathVariable Integer idEmploye,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateReference) {
        try (Connection c = dataSource.getConnection()) {
            LocalDate refDate = dateReference != null ? dateReference : LocalDate.now();
            CongeSituationEmployeDTO situation = congeTauxApplicableService.obtenirSituationComplete(idEmploye, refDate, c);
            return ResponseEntity.ok(situation);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/solde/cutover-opening")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> enregistrerCutoverOpening(@RequestBody CongeTauxApplicableService.CutoverOpeningRequest req) {
        if (req == null || req.getIdEmploye() == null || req.getSoldeJours() == null
                || req.getTauxJournalierApplicable() == null || req.getDateDebutReference() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "idEmploye, soldeJours, tauxJournalierApplicable et dateDebutReference sont obligatoires pour le cutover.");
        }

        return transactionRunner.required(c -> {
            Integer currentUserId = CustomUserDetails.getCurrentUserId();
            if (req.getDateCutover() == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                        "dateCutover explicite obligatoire : aucune date système implicite pour une reprise historique.");
            }
            congeTauxApplicableService.enregistrerCutoverSnapshot(req, currentUserId, c);

            CongeSituationEmployeDTO situation = congeTauxApplicableService.obtenirSituationComplete(
                    req.getIdEmploye(), req.getDateCutover(), c);
            return ResponseEntity.status(HttpStatus.CREATED).body(situation);
        });
    }

}
