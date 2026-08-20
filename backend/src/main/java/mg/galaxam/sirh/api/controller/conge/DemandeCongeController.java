package mg.galaxam.sirh.api.controller.conge;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.common.PageDTO;
import mg.galaxam.sirh.model.conge.DemandeConge;
import mg.galaxam.sirh.model.conge.RefStatutDemandeConge;
import mg.galaxam.sirh.model.conge.RefTypeConge;
import mg.galaxam.sirh.model.conge.VDemandeCongeCpl;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/conges/demandes")
public class DemandeCongeController {

    @Autowired
    private DataSource dataSource;

    @PostMapping("/simuler")
    public ResponseEntity<?> simulerDemande(@RequestBody Map<String, String> req) {
        if (!req.containsKey("dateDebut") || !req.containsKey("dateFin")) {
            return ResponseEntity.badRequest().body("Les dates dateDebut et dateFin sont requises pour la simulation.");
        }

        try {
            Date dateDebut = Date.valueOf(req.get("dateDebut"));
            Date dateFin = Date.valueOf(req.get("dateFin"));
            boolean estDemiJournee = req.containsKey("estDemiJournee") && Boolean.parseBoolean(req.get("estDemiJournee").toString());
            BigDecimal nbJours = DemandeConge.calculerNombreJoursDecomptes(dateDebut, dateFin, estDemiJournee);

            Map<String, Object> resp = new HashMap<>();
            resp.put("dateDebut", dateDebut);
            resp.put("dateFin", dateFin);
            resp.put("estDemiJournee", estDemiJournee);
            resp.put("nbJoursDecomptes", nbJours);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erreur lors de la simulation : " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> creerDemande(@RequestBody Map<String, Object> req) {
        if (!req.containsKey("idEmploye") || !req.containsKey("idTypeConge") || !req.containsKey("dateDebut") || !req.containsKey("dateFin")) {
            return ResponseEntity.badRequest().body("L'idEmploye, l'idTypeConge, la dateDebut et la dateFin sont obligatoires.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DemandeConge d = new DemandeConge();
                d.setIdEmploye(Integer.parseInt(req.get("idEmploye").toString()));
                d.setIdTypeConge(Integer.parseInt(req.get("idTypeConge").toString()));

                if (req.containsKey("idMotifEvenementFamilial") && req.get("idMotifEvenementFamilial") != null) {
                    d.setIdMotifEvenementFamilial(Integer.parseInt(req.get("idMotifEvenementFamilial").toString()));
                }

                d.setDateDebut(Date.valueOf(req.get("dateDebut").toString()));
                d.setDateFin(Date.valueOf(req.get("dateFin").toString()));

                if (req.containsKey("estDemiJournee")) {
                    d.setEstDemiJournee(Boolean.parseBoolean(req.get("estDemiJournee").toString()));
                }
                if (req.containsKey("momentDemiJournee") && req.get("momentDemiJournee") != null) {
                    d.setMomentDemiJournee(req.get("momentDemiJournee").toString());
                }

                if (req.containsKey("derogationSoldeNegatif")) {
                    d.setDerogationSoldeNegatif(Boolean.parseBoolean(req.get("derogationSoldeNegatif").toString()));
                }
                if (req.containsKey("derogationDelaiPreavis")) {
                    d.setDerogationDelaiPreavis(Boolean.parseBoolean(req.get("derogationDelaiPreavis").toString()));
                }
                if (req.containsKey("extensionPathologiqueMaternite")) {
                    d.setExtensionPathologiqueMaternite(Boolean.parseBoolean(req.get("extensionPathologiqueMaternite").toString()));
                }
                if (req.containsKey("justificatifFourni")) {
                    d.setJustificatifFourni(Boolean.parseBoolean(req.get("justificatifFourni").toString()));
                }
                if (req.containsKey("commentaire")) {
                    d.setCommentaire(req.get("commentaire").toString());
                }

                // Résolution statut par défaut: EN_ATTENTE
                RefStatutDemandeConge enAttente = RefStatutDemandeConge.rechercherParCode("EN_ATTENTE", c);
                if (enAttente == null) {
                    throw new IllegalStateException("Statut EN_ATTENTE non trouvé dans le référentiel.");
                }
                d.setIdStatutDemandeConge(enAttente.getIdStatutDemandeConge());
                d.setSaisiPar(getCurrentUserId());

                // ── Bascule Automatique Arrêt Maladie (Art. 13 : > 30 jours par épisode) ──
                RefTypeConge typeConge = RefTypeConge.rechercherParId(d.getIdTypeConge(), c);
                Date originalDateFin = d.getDateFin();
                BigDecimal totalJoursOrigine = DemandeConge.calculerNombreJoursDecomptes(d.getDateDebut(), originalDateFin);

                if (typeConge != null && "MALADIE".equals(typeConge.getCode()) && totalJoursOrigine.compareTo(BigDecimal.valueOf(30)) > 0) {
                    // Tranche 1 : 30 premiers jours en MALADIE
                    Date dateFinT1 = Date.valueOf(d.getDateDebut().toLocalDate().plusDays(29));
                    d.setDateFin(dateFinT1);
                    d.save(c);

                    // Tranche 2 : du 31ème jour jusqu'à la fin en MALADIE_SANS_SOLDE
                    if (typeConge.getIdTypeCongeBascule() != null) {
                        DemandeConge d2 = new DemandeConge();
                        d2.setIdEmploye(d.getIdEmploye());
                        d2.setIdTypeConge(typeConge.getIdTypeCongeBascule());
                        d2.setIdStatutDemandeConge(enAttente.getIdStatutDemandeConge());
                        d2.setDateDebut(Date.valueOf(dateFinT1.toLocalDate().plusDays(1)));
                        d2.setDateFin(originalDateFin);
                        d2.setJustificatifFourni(d.isJustificatifFourni());
                        d2.setCommentaire("(Bascule automatique Art. 13) " + (d.getCommentaire() != null ? d.getCommentaire() : ""));
                        d2.setSaisiPar(d.getSaisiPar());
                        d2.save(c);
                    }

                    // Génération d'une alerte RH
                    DemandeConge.creerNotificationRh(
                        "MALADIE_PLAFOND_EPISODE_ATTEINT",
                        "HAUTE",
                        "Bascule Automatique Arrêt Maladie (> 30j)",
                        "L'arrêt maladie continu (" + totalJoursOrigine + "j) a été automatiquement scindé : 30 jours en maintien de salaire et la suite en Maladie Sans Solde (CNaPS).",
                        "/conges/demandes/employe/" + d.getIdEmploye(),
                        d.getIdEmploye(),
                        c
                    );
                } else {
                    // Traitement standard
                    d.save(c);

                    // Génération systématique de la notification RH pour la nouvelle demande
                    DemandeConge.creerNotificationRh(
                        "DEMANDE_CONGE_EN_ATTENTE",
                        "NORMALE",
                        "Nouvelle demande de congé",
                        "Une nouvelle demande de congé (" + (typeConge != null ? typeConge.getLibelle() : "Absence") + ") a été enregistrée et est en attente de validation.",
                        "/conges",
                        d.getIdEmploye(),
                        c
                    );
                }

                c.commit();

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(d.getIdDemandeConge(), c);
                return ResponseEntity.status(HttpStatus.CREATED).body(cpl);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Erreur lors de la création de la demande : " + e.getMessage());
        }
    }

    @GetMapping("/employe/{idEmploye}")
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
    public ResponseEntity<?> getDemandeParId(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
            if (cpl == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(cpl);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération de la demande : " + e.getMessage());
        }
    }

    @GetMapping("/validation")
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
    public ResponseEntity<?> getAllDemandes(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String statut,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        try (Connection c = dataSource.getConnection()) {
            int offset = (page - 1) * size;
            long total = VDemandeCongeCpl.countAll(c, statut, q);
            List<VDemandeCongeCpl> list = VDemandeCongeCpl.listAll(c, statut, q, size, offset);
            
            PageDTO<VDemandeCongeCpl> pageDto = new PageDTO<>(list, total, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération de toutes les demandes : " + e.getMessage());
        }
    }

    @PostMapping("/{id}/valider")
    public ResponseEntity<?> validerDemande(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DemandeConge d = DemandeConge.rechercherParId(id, c);
                if (d == null) return ResponseEntity.notFound().build();

                Integer currentUserId = getCurrentUserId();
                d.valider(currentUserId, c);

                // Marquage de l'alerte en attente comme lue
                try (PreparedStatement psNotif = c.prepareStatement(
                        "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                        "WHERE id_employe_concerne = ? AND type_alerte = 'DEMANDE_CONGE_EN_ATTENTE' AND est_lue = FALSE")) {
                    psNotif.setInt(1, d.getIdEmploye());
                    psNotif.executeUpdate();
                }

                // Création d'une notification de confirmation de la décision
                DemandeConge.creerNotificationRh(
                    "MOUVEMENT_APPLIQUE",
                    "NORMALE",
                    "Demande de congé validée",
                    "La demande de congé (#" + id + ") a été validée avec succès.",
                    "/conges",
                    d.getIdEmploye(),
                    c
                );

                c.commit();

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
                return ResponseEntity.ok(cpl);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Erreur lors de la validation de la demande : " + e.getMessage());
        }
    }

    @PostMapping("/{id}/refuser")
    public ResponseEntity<?> refuserDemande(@PathVariable Integer id, @RequestBody Map<String, String> req) {
        if (!req.containsKey("motifRefus") || req.get("motifRefus").isBlank()) {
            return ResponseEntity.badRequest().body("Le motif de refus est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DemandeConge d = DemandeConge.rechercherParId(id, c);
                if (d == null) return ResponseEntity.notFound().build();

                Integer currentUserId = getCurrentUserId();
                d.refuser(currentUserId, req.get("motifRefus"), c);

                // Marquage de l'alerte en attente comme lue
                try (PreparedStatement psNotif = c.prepareStatement(
                        "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                        "WHERE id_employe_concerne = ? AND type_alerte = 'DEMANDE_CONGE_EN_ATTENTE' AND est_lue = FALSE")) {
                    psNotif.setInt(1, d.getIdEmploye());
                    psNotif.executeUpdate();
                }

                // Création d'une notification d'information du refus avec le motif
                DemandeConge.creerNotificationRh(
                    "MOUVEMENT_APPLIQUE",
                    "HAUTE",
                    "Demande de congé refusée",
                    "La demande de congé (#" + id + ") a été refusée. Motif : " + req.get("motifRefus"),
                    "/conges",
                    d.getIdEmploye(),
                    c
                );

                c.commit();

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
                return ResponseEntity.ok(cpl);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Erreur lors du refus de la demande : " + e.getMessage());
        }
    }

    @PostMapping("/{id}/annuler")
    public ResponseEntity<?> annulerDemande(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DemandeConge d = DemandeConge.rechercherParId(id, c);
                if (d == null) return ResponseEntity.notFound().build();

                Integer currentUserId = getCurrentUserId();
                d.annuler(currentUserId, c);

                // Marquage de l'alerte en attente comme lue
                try (PreparedStatement psNotif = c.prepareStatement(
                        "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                        "WHERE id_employe_concerne = ? AND type_alerte = 'DEMANDE_CONGE_EN_ATTENTE' AND est_lue = FALSE")) {
                    psNotif.setInt(1, d.getIdEmploye());
                    psNotif.executeUpdate();
                }

                // Création d'une notification de confirmation de l'annulation
                DemandeConge.creerNotificationRh(
                    "MOUVEMENT_APPLIQUE",
                    "NORMALE",
                    "Demande de congé annulée",
                    "La demande de congé (#" + id + ") a été annulée.",
                    "/conges",
                    d.getIdEmploye(),
                    c
                );

                c.commit();

                VDemandeCongeCpl cpl = VDemandeCongeCpl.rechercherParId(id, c);
                return ResponseEntity.ok(cpl);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
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
