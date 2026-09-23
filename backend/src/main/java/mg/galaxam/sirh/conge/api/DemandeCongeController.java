package mg.galaxam.sirh.conge.api;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.dto.PageDTO;
import mg.galaxam.sirh.conge.persistence.DemandeConge;
import mg.galaxam.sirh.conge.persistence.RefStatutDemandeConge;
import mg.galaxam.sirh.conge.persistence.RefTypeConge;
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
    public ResponseEntity<?> simulerDemande(@RequestBody Map<String, String> req) {
        if (!req.containsKey("dateDebut") || !req.containsKey("dateFin")) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Les dates dateDebut et dateFin sont requises pour la simulation.");
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/verifier-conflits")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> verifierConflits(@RequestBody Map<String, Object> req) {
        if (!req.containsKey("dateDebut") || !req.containsKey("dateFin") || req.get("dateDebut") == null || req.get("dateFin") == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Les dates dateDebut et dateFin sont requises.");
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerDemande(@RequestBody Map<String, Object> req) {
        if (!req.containsKey("idEmploye") || req.get("idEmploye") == null
                || req.get("idEmploye").toString().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "L'employé (idEmploye) est obligatoirement requis.");
        }
        if (!req.containsKey("idTypeConge") || req.get("idTypeConge") == null ||
                !req.containsKey("dateDebut") || req.get("dateDebut") == null ||
                !req.containsKey("dateFin") || req.get("dateFin") == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le type de congé, la dateDebut et la dateFin sont obligatoires.");
        }

        return transactionRunner.required(c -> {
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
                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(d.getIdDemandeConge(), c);
                return ResponseEntity.status(HttpStatus.CREATED).body(cpl);
        });
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierDemande(@PathVariable Integer id, @RequestBody Map<String, Object> req) {
        return transactionRunner.required(c -> {
                String statutCode;
                Date dateDebutCourante = null;
                String sqlLock = "SELECT s.code, dc.id_statut_demande_conge, dc.date_debut FROM demande_conge dc " +
                        "JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge " +
                        "WHERE dc.id_demande_conge = ? FOR UPDATE";
                try (PreparedStatement psLock = c.prepareStatement(sqlLock)) {
                    psLock.setInt(1, id);
                    try (ResultSet rsLock = psLock.executeQuery()) {
                        if (!rsLock.next()) {
                            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);
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
                        throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                                mg.galaxam.sirh.common.exception.SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                                "Seules les demandes en attente, en brouillon ou validées futures peuvent être modifiées.");
                    }
                    if (!isAdminOrRh) {
                        throw new org.springframework.security.access.AccessDeniedException(
                                "Seul un responsable RH ou Administrateur peut modifier un congé déjà validé.");
                    }
                    if (dateDebutCourante != null && !dateDebutCourante.toLocalDate().isAfter(java.time.LocalDate.now())) {
                        throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                                mg.galaxam.sirh.common.exception.SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                                "Ce congé validé a déjà commencé ou est passé. Il ne peut plus être modifié directement.");
                    }
                    if (req.containsKey("dateDebut")) {
                        Date newD = Date.valueOf(req.get("dateDebut").toString());
                        if (newD.toLocalDate().isBefore(java.time.LocalDate.now())) {
                            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                                    mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_DATES_INVALIDES,
                                    "La nouvelle date de début ne peut pas être dans le passé.");
                        }
                    }
                }

                DemandeConge d = DemandeConge.rechercherParId(id, c);
                if (d == null)
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);

                d.setIdDemandeConge(id); // Garantie d'exclusion de son propre ID pour l'anti-overlap

                // Sécurisation Phase 2A-2C (RG-CONGE) : Interdire la modification sur une période de paie clôturée ou payée
                if (etaitValide && d.getDateDebut() != null && d.getDateFin() != null) {
                    String sqlCheckPeriode = "SELECT p.code FROM periode_paie p " +
                            "JOIN ref_statut_periode_paie r ON p.id_statut_periode = r.id_statut_periode " +
                            "WHERE p.date_debut <= ? AND p.date_fin >= ? AND r.code IN ('CLOTUREE', 'PAIE_VALIDEE')";
                    try (PreparedStatement psP = c.prepareStatement(sqlCheckPeriode)) {
                        psP.setDate(1, d.getDateFin());
                        psP.setDate(2, d.getDateDebut());
                        try (ResultSet rsP = psP.executeQuery()) {
                            if (rsP.next()) {
                                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                                        mg.galaxam.sirh.common.exception.SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                                        "Action interdite : Impossible de modifier un congé sur une période de paie déjà validée ou clôturée ("
                                                + rsP.getString("code") + ").");
                            }
                        }
                    }
                }

                BigDecimal ancienNbJours = d.getNbJoursDecomptes();

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
                        BigDecimal nouveauNbJours = d.getNbJoursDecomptes();
                        BigDecimal delta = nouveauNbJours.subtract(ancienNbJours != null ? ancienNbJours : BigDecimal.ZERO);
                        if (delta.compareTo(BigDecimal.ZERO) != 0) {
                            mg.galaxam.sirh.conge.persistence.MouvementSoldeConge ajustement = new mg.galaxam.sirh.conge.persistence.MouvementSoldeConge();
                            ajustement.setIdEmploye(d.getIdEmploye());
                            ajustement.setTypeMouvement("AJUSTEMENT");
                            ajustement.setNbJours(delta.negate());
                            ajustement.setIdDemandeConge(d.getIdDemandeConge());
                            ajustement.setCommentaire("Ajustement de solde suite modification durée de la demande #" + d.getIdDemandeConge()
                                    + " (" + ancienNbJours + "j -> " + nouveauNbJours + "j)");
                            ajustement.setCreatedBy(getCurrentUserId());
                            ajustement.save(c);
                        }
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

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(d.getIdDemandeConge(), c);
                return ResponseEntity.ok(cpl);
        });
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
            return ResponseEntity.ok(cpl);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/{id}/valider")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerDemande(@PathVariable Integer id,
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

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
                return ResponseEntity.ok(cpl);
        });
    }

    @PostMapping("/{id}/refuser")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> refuserDemande(@PathVariable Integer id, @RequestBody Map<String, String> req) {
        if (!req.containsKey("motifRefus") || req.get("motifRefus").isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le motif de refus est obligatoire.");
        }

        return transactionRunner.required(c -> {
                DemandeConge d = DemandeConge.rechercherParId(id, c);
                if (d == null)
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);

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

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
                return ResponseEntity.ok(cpl);
        });
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerDemande(
            @PathVariable Integer id,
            @RequestBody(required = false) Map<String, String> payload) {
        return transactionRunner.required(c -> {
                DemandeConge d = DemandeConge.rechercherParId(id, c);
                if (d == null)
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);

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

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
                return ResponseEntity.ok(cpl);
        });
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getIdUtilisateur();
        }
        return null;
    }
}
