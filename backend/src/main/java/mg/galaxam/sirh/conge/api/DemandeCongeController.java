package mg.galaxam.sirh.conge.api;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.dto.PageDTO;
import mg.galaxam.sirh.conge.domain.DemandeCongeRequests.CreerDemandeRequest;
import mg.galaxam.sirh.conge.domain.DemandeCongeRequests.DecisionDemandeRequest;
import mg.galaxam.sirh.conge.domain.DemandeCongeRequests.ModifierDemandeRequest;
import mg.galaxam.sirh.conge.domain.DemandeCongeRequests.SimulerDemandeRequest;
import mg.galaxam.sirh.conge.domain.DemandeCongeRequests.VerifierConflitsRequest;
import mg.galaxam.sirh.conge.persistence.DemandeConge;
import mg.galaxam.sirh.conge.persistence.VDemandeCongeCpl;
import mg.galaxam.sirh.dashboard.application.NotificationRhService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/conges/demandes")
public class DemandeCongeController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DemandeCongeController.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private mg.galaxam.sirh.conge.application.CongeService congeService;

    @Autowired
    private mg.galaxam.sirh.common.transaction.TransactionRunner transactionRunner;

    @PostMapping("/simuler")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> simulerDemande(@RequestBody SimulerDemandeRequest req) {
        if (req == null || req.dateDebut() == null || req.idTypeConge() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Le type de congé et la dateDebut sont requis pour la simulation.");
        }

        try (Connection c = dataSource.getConnection()) {
            Date dateDebut = Date.valueOf(req.dateDebut());
            Date dateFin = req.dateFin() != null ? Date.valueOf(req.dateFin()) : null;
            return ResponseEntity.ok(congeService.simulerDemande(
                    req.idEmploye(), req.idTypeConge(), req.idMotifEvenementFamilial(), dateDebut, dateFin,
                    Boolean.TRUE.equals(req.estDemiJournee()),
                    Boolean.TRUE.equals(req.derogationSoldeNegatif()),
                    Boolean.TRUE.equals(req.derogationDelaiPreavis()),
                    Boolean.TRUE.equals(req.extensionPathologiqueMaternite()), c));
        } catch (Exception e) {
            log.warn("Erreur simulation congé : {}", e.getMessage());
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/verifier-conflits")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> verifierConflits(@RequestBody VerifierConflitsRequest req) {
        if (req == null || req.dateDebut() == null || req.dateFin() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Les dates dateDebut et dateFin sont requises.");
        }

        try (Connection c = dataSource.getConnection()) {
            Date dateDebut = Date.valueOf(req.dateDebut());
            Date dateFin = Date.valueOf(req.dateFin());
            Map<String, Object> result = congeService.verifierConflitsDemande(
                    req.idEmploye(), dateDebut, dateFin,
                    Boolean.TRUE.equals(req.estDemiJournee()),
                    req.momentDemiJournee(),
                    req.idDemandeConge(), c);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Erreur vérification conflits congés : {}", e.getMessage());
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerDemande(@RequestBody CreerDemandeRequest req) {
        if (req == null || req.idEmploye() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "L'employé (idEmploye) est obligatoirement requis.");
        }
        if (req.idTypeConge() == null || req.dateDebut() == null || req.dateFin() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Le type de congé, la dateDebut et la dateFin sont obligatoires.");
        }

        return transactionRunner.required(c -> {
            DemandeConge d = new DemandeConge();
            d.setIdEmploye(req.idEmploye());
            d.setIdTypeConge(req.idTypeConge());
            if (req.idMotifEvenementFamilial() != null) {
                d.setIdMotifEvenementFamilial(req.idMotifEvenementFamilial());
            }
            d.setDateDebut(Date.valueOf(req.dateDebut()));
            d.setDateFin(Date.valueOf(req.dateFin()));
            d.setEstDemiJournee(Boolean.TRUE.equals(req.estDemiJournee()));
            if (req.momentDemiJournee() != null && !req.momentDemiJournee().isBlank()) {
                d.setMomentDemiJournee(req.momentDemiJournee());
            }
            d.setDerogationSoldeNegatif(Boolean.TRUE.equals(req.derogationSoldeNegatif()));
            d.setDerogationDelaiPreavis(Boolean.TRUE.equals(req.derogationDelaiPreavis()));
            d.setDerogationContinuiteService(Boolean.TRUE.equals(req.derogationContinuiteService()));
            if (req.extensionPathologiqueMaternite() != null) {
                d.setExtensionPathologiqueMaternite(req.extensionPathologiqueMaternite());
            }
            if (req.estRemunere() != null) {
                d.setEstRemunere(req.estRemunere());
            }
            d.setJustificatifFourni(Boolean.TRUE.equals(req.justificatifFourni()));
            if (req.commentaire() != null && !req.commentaire().isBlank()) {
                d.setCommentaire(req.commentaire().trim());
            }

            congeService.creerDemande(d, CustomUserDetails.getCurrentUserId(), c);
            VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(d.getIdDemandeConge(), c);
            enrichirActions(cpl, c);
            return ResponseEntity.status(HttpStatus.CREATED).body(cpl);
        });
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierDemande(
            @PathVariable Integer id,
            @RequestBody ModifierDemandeRequest req) {
        return transactionRunner.required(c -> {
            Authentication authModif = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdminOrRh = authModif != null && authModif.getAuthorities().stream()
                    .anyMatch(a -> "CONGE_VALIDATE".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));

            DemandeConge d = congeService.modifierDemande(id, req, CustomUserDetails.getCurrentUserId(), isAdminOrRh, c);
            VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(d.getIdDemandeConge(), c);
            enrichirActions(cpl, c);
            return ResponseEntity.ok(cpl);
        });
    }

    @GetMapping("/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getDemandesParEmploye(
            @PathVariable Integer idEmploye,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try (Connection c = dataSource.getConnection()) {
            page = Math.max(1, page);
            size = Math.max(1, Math.min(100, size));
            int offset = (page - 1) * size;
            long total = VDemandeCongeCpl.countParEmploye(idEmploye, statut, q, c);
            List<VDemandeCongeCpl> list = VDemandeCongeCpl.listParEmploye(idEmploye, statut, q, c, size, offset);
            for (VDemandeCongeCpl demande : list) enrichirActions(demande, c);

            PageDTO<VDemandeCongeCpl> pageDto = new PageDTO<>(list, total, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getDemandeParId(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
            if (cpl == null)
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);
            enrichirActions(cpl, c);
            return ResponseEntity.ok(cpl);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/validation")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getDemandesAValider(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try (Connection c = dataSource.getConnection()) {
            int offset = (page - 1) * size;
            long total = VDemandeCongeCpl.countAll(c, "EN_ATTENTE", q);
            List<VDemandeCongeCpl> list = VDemandeCongeCpl.listAll(c, "EN_ATTENTE", q, size, offset);
            for (VDemandeCongeCpl demande : list) enrichirActions(demande, c);

            PageDTO<VDemandeCongeCpl> pageDto = new PageDTO<>(list, total, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllDemandes(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) Integer idTypeConge,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try (Connection c = dataSource.getConnection()) {
            int offset = (page - 1) * size;
            long total = VDemandeCongeCpl.countAll(c, statut, q, idTypeConge);
            List<VDemandeCongeCpl> list = VDemandeCongeCpl.listAll(c, statut, q, idTypeConge, size, offset);
            for (VDemandeCongeCpl demande : list) enrichirActions(demande, c);

            PageDTO<VDemandeCongeCpl> pageDto = new PageDTO<>(list, total, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/{id}/valider")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerDemande(
            @PathVariable Integer id,
            @RequestParam(required = false, defaultValue = "false") boolean justificatifFourni,
            @RequestParam(required = false) Boolean estRemunere,
            @RequestParam(required = false, defaultValue = "false") boolean derogationSoldeNegatif) {
        return transactionRunner.required(c -> {
            DemandeConge d = DemandeConge.rechercherParId(id, c);
            if (d == null)
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);

            if (justificatifFourni) {
                d.setJustificatifFourni(true);
            }
            if (estRemunere != null) {
                d.setEstRemunere(estRemunere);
            }
            if (derogationSoldeNegatif) {
                d.setDerogationSoldeNegatif(true);
            }
            d.update(c);

            Integer currentUserId = CustomUserDetails.getCurrentUserId();
            congeService.validerDemande(id, currentUserId, null, c);

            // Marquage de l'alerte en attente comme lue
            try (PreparedStatement psNotif = c.prepareStatement(
                    "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                            "WHERE id_employe_concerne = ? AND type_alerte = 'CONGE_EN_ATTENTE' AND est_lue = FALSE")) {
                psNotif.setInt(1, d.getIdEmploye());
                psNotif.executeUpdate();
            }

            // Création d'une notification de confirmation de la décision
            NotificationRhService.creer(
                    c,
                    "MOUVEMENT_APPLIQUE",
                    "NORMALE",
                    "Demande de congé validée",
                    "La demande de congé (#" + id + ") a été validée avec succès.",
                    "/conges",
                    d.getIdEmploye());

            VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
            enrichirActions(cpl, c);
            return ResponseEntity.ok(cpl);
        });
    }

    @PostMapping("/{id}/refuser")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> refuserDemande(
            @PathVariable Integer id,
            @RequestBody DecisionDemandeRequest req) {
        String motif = req != null ? req.motifEffectif() : null;
        if (motif == null || motif.isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Le motif de refus est obligatoire.");
        }

        return transactionRunner.required(c -> {
            DemandeConge d = DemandeConge.rechercherParId(id, c);
            if (d == null)
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);

            Integer currentUserId = CustomUserDetails.getCurrentUserId();
            congeService.refuserDemande(id, currentUserId, motif, c);

            // Marquage de l'alerte en attente comme lue
            try (PreparedStatement psNotif = c.prepareStatement(
                    "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                            "WHERE id_employe_concerne = ? AND type_alerte = 'CONGE_EN_ATTENTE' AND est_lue = FALSE")) {
                psNotif.setInt(1, d.getIdEmploye());
                psNotif.executeUpdate();
            }

            // Création d'une notification d'information du refus avec le motif
            NotificationRhService.creer(
                    c,
                    "MOUVEMENT_APPLIQUE",
                    "HAUTE",
                    "Demande de congé refusée",
                    "La demande de congé (#" + id + ") a été refusée. Motif : " + motif,
                    "/conges",
                    d.getIdEmploye());

            VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
            enrichirActions(cpl, c);
            return ResponseEntity.ok(cpl);
        });
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerDemande(
            @PathVariable Integer id,
            @RequestBody(required = false) DecisionDemandeRequest payload) {
        return transactionRunner.required(c -> {
            DemandeConge d = DemandeConge.rechercherParId(id, c);
            if (d == null)
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);

            String motif = payload != null ? payload.motifEffectif() : null;
            String ancienStatut = d.getStatutCode(c);
            Integer currentUserId = CustomUserDetails.getCurrentUserId();
            congeService.annulerDemande(id, currentUserId, motif, c);

            if ("VALIDE".equals(ancienStatut)) {
                String descNotif = "Une demande de congé (ID: " + id + ") qui était déjà validée vient d'être annulée.";
                if (motif != null && !motif.isBlank()) {
                    descNotif += " Motif : " + motif;
                }
                NotificationRhService.creer(
                        c,
                        "ALERTE_ECHEANCE",
                        "HAUTE",
                        "Annulation d'un congé validé",
                        descNotif,
                        "/conges",
                        null);
            }

            // Marquage de l'alerte en attente comme lue
            try (PreparedStatement psNotif = c.prepareStatement(
                    "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                            "WHERE id_employe_concerne = ? AND type_alerte = 'CONGE_EN_ATTENTE' AND est_lue = FALSE")) {
                psNotif.setInt(1, d.getIdEmploye());
                psNotif.executeUpdate();
            }

            // Création d'une notification de confirmation de l'annulation
            NotificationRhService.creer(
                    c,
                    "MOUVEMENT_APPLIQUE",
                    "NORMALE",
                    "Demande de congé annulée",
                    "La demande de congé (#" + id + ") a été annulée.",
                    "/conges",
                    d.getIdEmploye());

            VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
            enrichirActions(cpl, c);
            return ResponseEntity.ok(cpl);
        });
    }

    private void enrichirActions(VDemandeCongeCpl demande, Connection c) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean write = admin || (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "CONGE_WRITE".equals(a.getAuthority())));
        boolean validate = admin || (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "CONGE_VALIDATE".equals(a.getAuthority())));
        congeService.appliquerActionsDisponibles(demande, write, validate, c);
    }
}
