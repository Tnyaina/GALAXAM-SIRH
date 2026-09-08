package mg.galaxam.sirh.api.controller.conge;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.common.PageDTO;
import mg.galaxam.sirh.model.conge.DemandeConge;
import mg.galaxam.sirh.model.conge.RefStatutDemandeConge;
import mg.galaxam.sirh.model.conge.RefTypeConge;
import mg.galaxam.sirh.model.conge.VDemandeCongeCpl;
import mg.galaxam.sirh.model.dashboard.service.NotificationRhService;
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
    private mg.galaxam.sirh.model.conge.service.CongeService congeService;

    @PostMapping("/simuler")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> simulerDemande(@RequestBody Map<String, String> req) {
        if (!req.containsKey("dateDebut") || !req.containsKey("dateFin")) {
            return ResponseEntity.badRequest().body("Les dates dateDebut et dateFin sont requises pour la simulation.");
        }

        try {
            Date dateDebut = Date.valueOf(req.get("dateDebut"));
            Date dateFin = Date.valueOf(req.get("dateFin"));
            boolean estDemiJournee = req.containsKey("estDemiJournee")
                    && Boolean.parseBoolean(req.get("estDemiJournee").toString());
            BigDecimal nbJours = congeService.simulerNombreJours(dateDebut, dateFin, estDemiJournee);

            Map<String, Object> resp = new HashMap<>();
            resp.put("dateDebut", dateDebut);
            resp.put("dateFin", dateFin);
            resp.put("estDemiJournee", estDemiJournee);
            resp.put("nbJoursDecomptes", nbJours);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.warn("Erreur simulation congé : {}", e.getMessage());
            return ResponseEntity.badRequest().body("Erreur lors de la simulation : " + e.getMessage());
        }
    }

    @PostMapping("/verifier-conflits")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> verifierConflits(@RequestBody Map<String, Object> req) {
        if (!req.containsKey("dateDebut") || !req.containsKey("dateFin") || req.get("dateDebut") == null || req.get("dateFin") == null) {
            return ResponseEntity.badRequest().body("Les dates dateDebut et dateFin sont requises.");
        }

        try (Connection c = dataSource.getConnection()) {
            Date dateDebut = Date.valueOf(req.get("dateDebut").toString());
            Date dateFin = Date.valueOf(req.get("dateFin").toString());
            boolean estDemiJournee = req.containsKey("estDemiJournee") && Boolean.parseBoolean(req.get("estDemiJournee").toString());
            String momentDemiJournee = req.get("momentDemiJournee") != null ? req.get("momentDemiJournee").toString() : null;
            Integer idEmploye = req.get("idEmploye") != null && !req.get("idEmploye").toString().isBlank() 
                    ? Integer.parseInt(req.get("idEmploye").toString()) : null;
            Integer idDemandeExclue = req.get("idDemandeConge") != null && !req.get("idDemandeConge").toString().isBlank()
                    ? Integer.parseInt(req.get("idDemandeConge").toString()) : null;

            Map<String, Object> result = congeService.verifierConflitsDemande(
                    idEmploye, dateDebut, dateFin, estDemiJournee, momentDemiJournee, idDemandeExclue, c);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Erreur vérification conflits congés : {}", e.getMessage());
            return ResponseEntity.badRequest().body("Erreur vérification conflits : " + e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerDemande(@RequestBody Map<String, Object> req) {
        if (!req.containsKey("idEmploye") || req.get("idEmploye") == null
                || req.get("idEmploye").toString().isBlank()) {
            return ResponseEntity.badRequest().body("L'employé (idEmploye) est obligatoirement requis.");
        }
        if (!req.containsKey("idTypeConge") || req.get("idTypeConge") == null ||
                !req.containsKey("dateDebut") || req.get("dateDebut") == null ||
                !req.containsKey("dateFin") || req.get("dateFin") == null) {
            return ResponseEntity.badRequest().body("Le type de congé, la dateDebut et la dateFin sont obligatoires.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DemandeConge d = new DemandeConge();
                d.setIdEmploye(Integer.parseInt(req.get("idEmploye").toString()));
                d.setIdTypeConge(Integer.parseInt(req.get("idTypeConge").toString()));

                if (req.get("idMotifEvenementFamilial") != null
                        && !req.get("idMotifEvenementFamilial").toString().isBlank()) {
                    d.setIdMotifEvenementFamilial(Integer.parseInt(req.get("idMotifEvenementFamilial").toString()));
                }

                d.setDateDebut(Date.valueOf(req.get("dateDebut").toString()));
                d.setDateFin(Date.valueOf(req.get("dateFin").toString()));

                if (req.get("estDemiJournee") != null) {
                    d.setEstDemiJournee(Boolean.parseBoolean(req.get("estDemiJournee").toString()));
                }
                if (req.get("momentDemiJournee") != null && !req.get("momentDemiJournee").toString().isBlank()) {
                    d.setMomentDemiJournee(req.get("momentDemiJournee").toString());
                }

                // Dérogations RH : prises en compte si transmises dans la requête
                if (req.get("derogationSoldeNegatif") != null) {
                    d.setDerogationSoldeNegatif(Boolean.parseBoolean(req.get("derogationSoldeNegatif").toString()));
                } else {
                    d.setDerogationSoldeNegatif(false);
                }
                if (req.get("derogationDelaiPreavis") != null) {
                    d.setDerogationDelaiPreavis(Boolean.parseBoolean(req.get("derogationDelaiPreavis").toString()));
                } else {
                    d.setDerogationDelaiPreavis(false);
                }
                if (req.get("derogationContinuiteService") != null) {
                    d.setDerogationContinuiteService(Boolean.parseBoolean(req.get("derogationContinuiteService").toString()));
                } else {
                    d.setDerogationContinuiteService(false);
                }
                if (req.get("extensionPathologiqueMaternite") != null) {
                    d.setExtensionPathologiqueMaternite(
                            Boolean.parseBoolean(req.get("extensionPathologiqueMaternite").toString()));
                }
                if (req.get("estRemunere") != null && !req.get("estRemunere").toString().isBlank()) {
                    d.setEstRemunere(Boolean.parseBoolean(req.get("estRemunere").toString()));
                }
                if (req.get("justificatifFourni") != null) {
                    d.setJustificatifFourni(Boolean.parseBoolean(req.get("justificatifFourni").toString()));
                }
                if (req.get("commentaire") != null && !req.get("commentaire").toString().isBlank()) {
                    d.setCommentaire(req.get("commentaire").toString().trim());
                }

                congeService.creerDemande(d, getCurrentUserId(), c);
                c.commit();

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(d.getIdDemandeConge(), c);
                return ResponseEntity.status(HttpStatus.CREATED).body(cpl);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("Erreur création demande congé : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Erreur lors de la création de la demande : " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierDemande(@PathVariable Integer id, @RequestBody Map<String, Object> req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String statutCode;
                Date dateDebutCourante = null;
                String sqlLock = "SELECT s.code, dc.id_statut_demande_conge, dc.date_debut FROM demande_conge dc " +
                        "JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge " +
                        "WHERE dc.id_demande_conge = ? FOR UPDATE";
                try (PreparedStatement psLock = c.prepareStatement(sqlLock)) {
                    psLock.setInt(1, id);
                    try (ResultSet rsLock = psLock.executeQuery()) {
                        if (!rsLock.next()) {
                            return ResponseEntity.notFound().build();
                        }
                        statutCode = rsLock.getString("code");
                        dateDebutCourante = rsLock.getDate("date_debut");
                    }
                }

                Authentication authModif = SecurityContextHolder.getContext().getAuthentication();
                boolean isAdminOrRh = authModif != null && authModif.getAuthorities().stream()
                        .anyMatch(a -> "CONGE_VALIDATE".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));

                boolean etaitValide = "VALIDE".equals(statutCode);

                if (!"EN_ATTENTE".equals(statutCode) && !"BROUILLON".equals(statutCode)) {
                    if (!etaitValide) {
                        return ResponseEntity.badRequest()
                                .body("Seules les demandes en attente, en brouillon ou validées futures peuvent être modifiées.");
                    }
                    if (!isAdminOrRh) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body("Seul un responsable RH ou Administrateur peut modifier un congé déjà validé.");
                    }
                    if (dateDebutCourante != null && !dateDebutCourante.toLocalDate().isAfter(java.time.LocalDate.now())) {
                        return ResponseEntity.badRequest()
                                .body("Ce congé validé a déjà commencé ou est passé (date de début : " + dateDebutCourante + "). Il ne peut plus être modifié directement.");
                    }
                    if (req.containsKey("dateDebut")) {
                        Date newD = Date.valueOf(req.get("dateDebut").toString());
                        if (newD.toLocalDate().isBefore(java.time.LocalDate.now())) {
                            return ResponseEntity.badRequest()
                                    .body("La nouvelle date de début ne peut pas être dans le passé.");
                        }
                    }
                }

                DemandeConge d = DemandeConge.rechercherParId(id, c);
                if (d == null)
                    return ResponseEntity.notFound().build();

                d.setIdDemandeConge(id); // Garantie d'exclusion de son propre ID pour l'anti-overlap

                if (etaitValide) {
                    // Supprime temporairement le mouvement de solde PRISE pour recalculer exactement le solde disponible
                    try (PreparedStatement psDelPrise = c.prepareStatement(
                            "DELETE FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'PRISE'")) {
                        psDelPrise.setInt(1, id);
                        psDelPrise.executeUpdate();
                    }
                }

                if (req.containsKey("idTypeConge"))
                    d.setIdTypeConge(Integer.parseInt(req.get("idTypeConge").toString()));
                if (req.containsKey("idMotifEvenementFamilial")) {
                    Object val = req.get("idMotifEvenementFamilial");
                    d.setIdMotifEvenementFamilial(
                            val != null && !val.toString().isBlank() ? Integer.parseInt(val.toString()) : null);
                }

                if (req.containsKey("dateDebut"))
                    d.setDateDebut(Date.valueOf(req.get("dateDebut").toString()));
                if (req.containsKey("dateFin"))
                    d.setDateFin(Date.valueOf(req.get("dateFin").toString()));

                if (req.containsKey("estDemiJournee"))
                    d.setEstDemiJournee(Boolean.parseBoolean(req.get("estDemiJournee").toString()));
                if (req.containsKey("momentDemiJournee")) {
                    Object mom = req.get("momentDemiJournee");
                    d.setMomentDemiJournee(mom != null && !mom.toString().isBlank() ? mom.toString() : null);
                }

                boolean canManageDerogations = isAdminOrRh;

                if (canManageDerogations) {
                    if (req.containsKey("derogationSoldeNegatif"))
                        d.setDerogationSoldeNegatif(Boolean.parseBoolean(req.get("derogationSoldeNegatif").toString()));
                    if (req.containsKey("derogationDelaiPreavis"))
                        d.setDerogationDelaiPreavis(Boolean.parseBoolean(req.get("derogationDelaiPreavis").toString()));
                    if (req.containsKey("derogationContinuiteService"))
                        d.setDerogationContinuiteService(Boolean.parseBoolean(req.get("derogationContinuiteService").toString()));
                }
                if (req.containsKey("extensionPathologiqueMaternite"))
                    d.setExtensionPathologiqueMaternite(
                            Boolean.parseBoolean(req.get("extensionPathologiqueMaternite").toString()));
                if (req.containsKey("justificatifFourni"))
                    d.setJustificatifFourni(Boolean.parseBoolean(req.get("justificatifFourni").toString()));
                if (req.containsKey("commentaire"))
                    d.setCommentaire(req.get("commentaire") != null ? req.get("commentaire").toString() : null);

                d.controler(c);
                d.update(c);

                if (etaitValide) {
                    RefTypeConge typeConge = RefTypeConge.rechercherParId(d.getIdTypeConge(), c);
                    if (typeConge != null && typeConge.isDecompteSoldeAcquis()) {
                        mg.galaxam.sirh.model.conge.MouvementSoldeConge prise = new mg.galaxam.sirh.model.conge.MouvementSoldeConge();
                        prise.setIdEmploye(d.getIdEmploye());
                        prise.setTypeMouvement("PRISE");
                        prise.setNbJours(d.getNbJoursDecomptes().negate());
                        prise.setIdDemandeConge(d.getIdDemandeConge());
                        prise.setCommentaire("Prise de congé " + typeConge.getLibelle() + " (Demande #" + d.getIdDemandeConge() + " - Dates modifiées)");
                        prise.setCreatedBy(getCurrentUserId());
                        prise.save(c);
                    }

                    NotificationRhService.creer(
                            c,
                            "MOUVEMENT_APPLIQUE",
                            "NORMALE",
                            "Dates de congé modifiées",
                            "Les dates du congé validé (#" + id + ") ont été modifiées (du " + d.getDateDebut() + " au " + d.getDateFin() + ").",
                            "/conges",
                            d.getIdEmploye());
                }

                c.commit();

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(d.getIdDemandeConge(), c);
                return ResponseEntity.ok(cpl);
            } catch (Exception e) {
                c.rollback();
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erreur serveur : " + e.getMessage());
        }
    }

    @GetMapping("/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getDemandesEmploye(@PathVariable Integer idEmploye,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try (Connection c = dataSource.getConnection()) {
            int offset = (page - 1) * size;
            long total = VDemandeCongeCpl.countParEmploye(idEmploye, c);
            List<VDemandeCongeCpl> list = VDemandeCongeCpl.listParEmploye(idEmploye, c, size, offset);

            PageDTO<VDemandeCongeCpl> pageDto = new PageDTO<>(list, total, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des demandes : " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getDemandeParId(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
            if (cpl == null)
                return ResponseEntity.notFound().build();
            return ResponseEntity.ok(cpl);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération de la demande : " + e.getMessage());
        }
    }

    @GetMapping("/validation")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getDemandesAValider(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try (Connection c = dataSource.getConnection()) {
            int offset = (page - 1) * size;
            long total = VDemandeCongeCpl.countAll(c, "EN_ATTENTE", q);
            List<VDemandeCongeCpl> list = VDemandeCongeCpl.listAll(c, "EN_ATTENTE", q, size, offset);

            PageDTO<VDemandeCongeCpl> pageDto = new PageDTO<>(list, total, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des demandes à valider : " + e.getMessage());
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllDemandes(@RequestParam(required = false) String q,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) Integer idTypeConge,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try (Connection c = dataSource.getConnection()) {
            int offset = (page - 1) * size;
            long total = VDemandeCongeCpl.countAll(c, statut, q, idTypeConge);
            List<VDemandeCongeCpl> list = VDemandeCongeCpl.listAll(c, statut, q, idTypeConge, size, offset);

            PageDTO<VDemandeCongeCpl> pageDto = new PageDTO<>(list, total, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération de toutes les demandes : " + e.getMessage());
        }
    }

    @PostMapping("/{id}/valider")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerDemande(@PathVariable Integer id,
            @RequestParam(required = false, defaultValue = "false") boolean justificatifFourni,
            @RequestParam(required = false) Boolean estRemunere,
            @RequestParam(required = false, defaultValue = "false") boolean derogationSoldeNegatif) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DemandeConge d = DemandeConge.rechercherParId(id, c);
                if (d == null)
                    return ResponseEntity.notFound().build();

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

                Integer currentUserId = getCurrentUserId();
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

                c.commit();

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
                return ResponseEntity.ok(cpl);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("Erreur validation demande congé : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Erreur lors de la validation de la demande : " + e.getMessage());
        }
    }

    @PostMapping("/{id}/refuser")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> refuserDemande(@PathVariable Integer id, @RequestBody Map<String, String> req) {
        if (!req.containsKey("motifRefus") || req.get("motifRefus").isBlank()) {
            return ResponseEntity.badRequest().body("Le motif de refus est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DemandeConge d = DemandeConge.rechercherParId(id, c);
                if (d == null)
                    return ResponseEntity.notFound().build();

                Integer currentUserId = getCurrentUserId();
                congeService.refuserDemande(id, currentUserId, req.get("motifRefus"), c);

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
                        "La demande de congé (#" + id + ") a été refusée. Motif : " + req.get("motifRefus"),
                        "/conges",
                        d.getIdEmploye());

                c.commit();

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
                return ResponseEntity.ok(cpl);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("Erreur refus demande congé : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Erreur lors du refus de la demande : " + e.getMessage());
        }
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerDemande(
            @PathVariable Integer id,
            @RequestBody(required = false) Map<String, String> payload) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DemandeConge d = DemandeConge.rechercherParId(id, c);
                if (d == null)
                    return ResponseEntity.notFound().build();

                String motif = payload != null ? payload.get("motif") : null;
                if (motif == null || motif.trim().isEmpty()) {
                    motif = payload != null ? payload.get("motifRefus") : null;
                }

                String ancienStatut = d.getStatutCode(c);
                Integer currentUserId = getCurrentUserId();
                congeService.annulerDemande(id, currentUserId, motif, c);

                if ("VALIDE".equals(ancienStatut)) {
                    String descNotif = "Une demande de congé (ID: " + id + ") qui était déjà validée vient d'être annulée.";
                    if (motif != null && !motif.trim().isEmpty()) {
                        descNotif += " Motif : " + motif.trim();
                    }
                    NotificationRhService.creer(
                            c,
                            "ALERTE_ECHEANCE",
                            "HAUTE",
                            "Annulation d'un congé validé",
                            descNotif,
                            "/conges",
                            null); // Destiné aux RH/Admins
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

                c.commit();

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
                return ResponseEntity.ok(cpl);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("Erreur annulation demande congé : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Erreur lors de l'annulation de la demande : " + e.getMessage());
        }
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getIdUtilisateur();
        }
        return null;
    }
}
