package mg.galaxam.sirh.conge.api;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.dto.PageDTO;
import mg.galaxam.sirh.conge.application.RemunerationCongePolicy;
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
    private mg.galaxam.sirh.conge.application.CongeLotValorisationService congeLotValorisationService;

    @Autowired
    private mg.galaxam.sirh.common.transaction.TransactionRunner transactionRunner;

    @PostMapping("/simuler")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> simulerDemande(@RequestBody SimulerDemandeRequest req) {
        if (req == null || req.dateDebut() == null || req.idTypeConge() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Le type de congé et la dateDebut sont requis pour la simulation.");
        }

        try (Connection c = dataSource.getConnection()) {
            verifierAccesEmploye(req.idEmploye(), "CONGE_READ", c);
            verifierDemandeSimulation(req.idDemandeConge(), req.idEmploye(), c);
            Date dateDebut = Date.valueOf(req.dateDebut());
            Date dateFin = req.dateFin() != null ? Date.valueOf(req.dateFin()) : null;
            return ResponseEntity.ok(congeService.simulerDemande(
                    req.idEmploye(), req.idTypeConge(), req.idMotifEvenementFamilial(), dateDebut, dateFin,
                    Boolean.TRUE.equals(req.estDemiJournee()),
                    Boolean.TRUE.equals(req.derogationSoldeNegatif()),
                    Boolean.TRUE.equals(req.derogationDelaiPreavis()),
                    Boolean.TRUE.equals(req.extensionPathologiqueMaternite()), req.idDemandeConge(), c));
        } catch (Exception e) {
            log.warn("Erreur simulation congé : {}", e.getMessage());
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/simuler-valorisation")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> simulerValorisation(@RequestBody SimulerDemandeRequest req) {
        if (req == null || req.idEmploye() == null || req.dateDebut() == null
                || req.idTypeConge() == null || req.dateFin() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "L'employé, le type et les dates de la demande sont requis pour l'estimation.");
        }
        try (Connection c = dataSource.getConnection()) {
            verifierAccesEmploye(req.idEmploye(), "CONGE_READ", c);
            verifierDemandeSimulation(req.idDemandeConge(), req.idEmploye(), c);
            var type = mg.galaxam.sirh.conge.persistence.RefTypeConge.rechercherParId(req.idTypeConge(), c);
            if (type == null || !type.isEstActif()) throw new IllegalArgumentException("Type de congé inactif ou introuvable.");
            if (!type.isDecompteSoldeAcquis()) {
                return ResponseEntity.ok(Map.of("applicable", false, "message", "Ce type de congé ne débite pas les droits acquis."));
            }
            java.sql.Date debut = java.sql.Date.valueOf(req.dateDebut());
            java.sql.Date fin = java.sql.Date.valueOf(req.dateFin());
            var report = congeService.simulerDemande(req.idEmploye(), req.idTypeConge(),
                    req.idMotifEvenementFamilial(), debut, fin,
                    Boolean.TRUE.equals(req.estDemiJournee()), Boolean.TRUE.equals(req.derogationSoldeNegatif()),
                    Boolean.TRUE.equals(req.derogationDelaiPreavis()),
                    Boolean.TRUE.equals(req.extensionPathologiqueMaternite()), req.idDemandeConge(), c);
            var estimate = congeLotValorisationService.simulerPrise(req.idEmploye(), req.dateDebut(),
                    report.joursDebites(), c);
            var demandesConcurrentes = congeService.demandesSoldeEnAttente(req.idEmploye(), req.dateDebut(), c);
            return ResponseEntity.ok(Map.of("applicable", true, "joursDemandes", report.joursDebites(),
                    "soldeAvant", estimate.soldeAvant(), "soldeApres", estimate.soldeApres(),
                    "valeurEstimee", estimate.valeurEstimee(), "soldeSuffisant", estimate.soldeSuffisant(),
                    "lots", estimate.lots(), "avertissement", estimate.avertissement(),
                    "demandesEnAttente", demandesConcurrentes));
        } catch (Exception e) {
            log.warn("Erreur estimation valorisation congé : {}", e.getMessage());
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/verifier-conflits")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> verifierConflits(@RequestBody VerifierConflitsRequest req) {
        if (req == null || req.dateDebut() == null || req.dateFin() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Les dates dateDebut et dateFin sont requises.");
        }

        try (Connection c = dataSource.getConnection()) {
            verifierAccesEmploye(req.idEmploye(), "CONGE_READ", c);
            verifierDemandeSimulation(req.idDemandeConge(), req.idEmploye(), c);
            Date dateDebut = Date.valueOf(req.dateDebut());
            Date dateFin = Date.valueOf(req.dateFin());
            Map<String, Object> result = congeService.verifierConflitsDemande(
                    req.idEmploye(), req.idTypeConge(), dateDebut, dateFin,
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
    @PreAuthorize("isAuthenticated()")
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

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().noneMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) || "CONGE_WRITE".equals(a.getAuthority()))) {
            try (Connection c = dataSource.getConnection()) {
                if (!idEmployeConnecte(c).equals(req.idEmploye())) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "Vous pouvez créer une demande uniquement pour votre dossier.");
                }
            } catch (Exception e) {
                throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
            }
        }

        return transactionRunner.required(c -> {
            verifierAccesEmploye(req.idEmploye(), "CONGE_WRITE", c);
            boolean administrator = isAdministrator();
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
            d.setEstRemunere(RemunerationCongePolicy.resolve(null, req.estRemunere(), false, administrator));
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> modifierDemande(
            @PathVariable Integer id,
            @RequestBody ModifierDemandeRequest req) {
        return transactionRunner.required(c -> {
            Authentication authModif = SecurityContextHolder.getContext().getAuthentication();
            boolean administrator = isAdministrator();
            boolean isAdminOrRh = authModif != null && authModif.getAuthorities().stream()
                    .anyMatch(a -> "CONGE_VALIDATE".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));

            RemunerationCongePolicy.resolve(null, req == null ? null : req.estRemunere(),
                    req != null && Boolean.TRUE.equals(req.utiliserRemunerationParDefaut()), administrator);

            verifierAccesDemande(id, c, isAdminOrRh ? new String[] {"CONGE_VALIDATE"} : new String[] {"CONGE_WRITE"});

            DemandeConge d = congeService.modifierDemande(
                    id, req, CustomUserDetails.getCurrentUserId(), isAdminOrRh, administrator, c);
            VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(d.getIdDemandeConge(), c);
            enrichirActions(cpl, c);
            return ResponseEntity.ok(cpl);
        });
    }

    @GetMapping("/employe/{idEmploye}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDemandesParEmploye(
            @PathVariable Integer idEmploye,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try (Connection c = dataSource.getConnection()) {
            verifierAccesEmploye(idEmploye, "CONGE_READ", c);
            page = Math.max(1, page);
            size = Math.max(1, Math.min(100, size));
            int offset = (page - 1) * size;
            long total = VDemandeCongeCpl.countParEmploye(idEmploye, statut, q, c);
            List<VDemandeCongeCpl> list = VDemandeCongeCpl.listParEmploye(idEmploye, statut, q, c, size, offset);
            Integer employeConnecte = idEmployeConnecteOuNull(c);
            for (VDemandeCongeCpl demande : list) enrichirActions(demande, c, employeConnecte);

            PageDTO<VDemandeCongeCpl> pageDto = new PageDTO<>(list, total, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDemandeParId(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            verifierAccesDemande(id, "CONGE_READ", c);
            VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
            if (cpl == null)
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);
            enrichirActions(cpl, c);
            return ResponseEntity.ok(cpl);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMesDemandes(
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try (Connection c = dataSource.getConnection()) {
            Integer idEmploye = idEmployeConnecte(c);
            page = Math.max(1, page);
            size = Math.max(1, Math.min(100, size));
            long total = VDemandeCongeCpl.countParEmploye(idEmploye, statut, q, c);
            List<VDemandeCongeCpl> list = VDemandeCongeCpl.listParEmploye(idEmploye, statut, q, c, size, (page - 1) * size);
            for (VDemandeCongeCpl demande : list) enrichirActions(demande, c, idEmploye);
            return ResponseEntity.ok(new PageDTO<>(list, total, page, size));
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/me/dossier")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMonDossierEmploye() {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok(Map.of("idEmploye", idEmployeConnecte(c)));
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
            Integer employeConnecte = idEmployeConnecteOuNull(c);
            for (VDemandeCongeCpl demande : list) enrichirActions(demande, c, employeConnecte);

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
            Integer employeConnecte = idEmployeConnecteOuNull(c);
            for (VDemandeCongeCpl demande : list) enrichirActions(demande, c, employeConnecte);

            PageDTO<VDemandeCongeCpl> pageDto = new PageDTO<>(list, total, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /** Compatibilité des appels Java existants; le point d'entrée HTTP est la surcharge ci-dessous. */
    public ResponseEntity<?> validerDemande(Integer id, boolean justificatifFourni,
            Boolean estRemunere, boolean derogationSoldeNegatif) {
        return validerDemande(id, justificatifFourni, estRemunere, false, derogationSoldeNegatif);
    }

    @PostMapping("/{id}/valider")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerDemande(
            @PathVariable Integer id,
            @RequestParam(required = false, defaultValue = "false") boolean justificatifFourni,
            @RequestParam(required = false) Boolean estRemunere,
            @RequestParam(required = false, defaultValue = "false") boolean utiliserRemunerationParDefaut,
            @RequestParam(required = false, defaultValue = "false") boolean derogationSoldeNegatif) {
        return transactionRunner.required(c -> {
            boolean administrator = isAdministrator();
            RemunerationCongePolicy.resolve(null, estRemunere, utiliserRemunerationParDefaut, administrator);
            DemandeConge d = DemandeConge.rechercherParId(id, c);
            if (d == null)
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);

            if (justificatifFourni) {
                d.setJustificatifFourni(true);
            }
            d.setEstRemunere(RemunerationCongePolicy.resolve(
                    d.getEstRemunere(), estRemunere, utiliserRemunerationParDefaut, administrator));
            if (derogationSoldeNegatif) {
                throw new IllegalArgumentException("La prise par anticipation avec solde négatif est désactivée.");
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
            verifierAccesDemande(id, c, "CONGE_WRITE", "CONGE_VALIDATE");
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

    private void verifierAccesDemande(Integer idDemande, String permission, Connection c) throws Exception {
        verifierAccesDemande(idDemande, c, permission);
    }

    private void verifierDemandeSimulation(Integer idDemande, Integer idEmploye, Connection c) throws Exception {
        if (idDemande == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM demande_conge WHERE id_demande_conge=? AND id_employe=?")) {
            ps.setInt(1, idDemande);
            ps.setInt(2, idEmploye);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new org.springframework.security.access.AccessDeniedException(
                        "La demande exclue de la simulation ne correspond pas au salarié ciblé.");
            }
        }
    }

    private void verifierAccesDemande(Integer idDemande, Connection c, String... permissions) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT id_employe FROM demande_conge WHERE id_demande_conge=?")) {
            ps.setInt(1, idDemande);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);
                verifierAccesEmploye(rs.getInt(1), c, permissions);
            }
        }
    }

    private void verifierAccesEmploye(Integer idEmploye, String permission, Connection c) throws Exception {
        verifierAccesEmploye(idEmploye, c, permission);
    }

    private void verifierAccesEmploye(Integer idEmploye, Connection c, String... permissions) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Authentification requise.");
        }
        boolean privilegie = auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) || java.util.Arrays.asList(permissions).contains(a.getAuthority()));
        if (privilegie) return;
        if (idEmploye == null || !(auth.getPrincipal() instanceof CustomUserDetails user)) {
            throw new org.springframework.security.access.AccessDeniedException("Accès limité au dossier personnel.");
        }
        Integer employeeId = idEmployeConnecte(c, user);
        if (!employeeId.equals(idEmploye)) throw new org.springframework.security.access.AccessDeniedException(
                "Accès limité au dossier personnel.");
    }

    private Integer idEmployeConnecte(Connection c) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails user)) {
            throw new org.springframework.security.access.AccessDeniedException("Compte salarié non associé.");
        }
        return idEmployeConnecte(c, user);
    }

    private Integer idEmployeConnecte(Connection c, CustomUserDetails user) throws Exception {
        String email = user.getUsername();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id_employe FROM employe WHERE email IS NOT NULL AND LOWER(TRIM(email))=LOWER(TRIM(?))")) {
            ps.setString(1, email);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new org.springframework.security.access.AccessDeniedException(
                        "Aucun dossier salarié ne correspond au compte connecté.");
                int employeConnecte = rs.getInt(1);
                if (rs.next()) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "L'adresse du compte correspond à plusieurs dossiers salariés.");
                }
                return employeConnecte;
            }
        }
    }

    private Integer idEmployeConnecteOuNull(Connection c) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails user)) return null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id_employe FROM employe WHERE email IS NOT NULL AND LOWER(TRIM(email))=LOWER(TRIM(?))")) {
            ps.setString(1, user.getUsername());
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int idEmploye = rs.getInt(1);
                return rs.next() ? null : idEmploye;
            }
        }
    }

    private void enrichirActions(VDemandeCongeCpl demande, Connection c) throws Exception {
        enrichirActions(demande, c, idEmployeConnecteOuNull(c));
    }

    private void enrichirActions(VDemandeCongeCpl demande, Connection c, Integer employeConnecte) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean write = admin || (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "CONGE_WRITE".equals(a.getAuthority())));
        boolean validate = admin || (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "CONGE_VALIDATE".equals(a.getAuthority())));
        boolean isOwner = employeConnecte != null && employeConnecte.equals(demande.getIdEmploye());
        congeService.appliquerActionsDisponibles(demande, write, validate, isOwner, c);
    }

    private static boolean isAdministrator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
