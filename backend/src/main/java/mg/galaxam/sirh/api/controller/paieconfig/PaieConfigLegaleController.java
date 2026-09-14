package mg.galaxam.sirh.api.controller.paieconfig;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paieconfig.IrsaRequest;
import mg.galaxam.sirh.api.dto.paieconfig.PaieConfigLegaleRequest;
import mg.galaxam.sirh.api.dto.paieconfig.TauxMajorationRequest;
import mg.galaxam.sirh.model.paieconfig.BaremeIrsa;
import mg.galaxam.sirh.model.paieconfig.PaieConfigLegale;
import mg.galaxam.sirh.model.paieconfig.RefTauxMajoration;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Gestion des exercices fiscaux et sociaux (paie_config_legale).
 * Base URL : /paie/config-legale
 */
@RestController
@RequestMapping("/paie/config-legale")
public class PaieConfigLegaleController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllConfigs() {
        try (Connection c = dataSource.getConnection()) {
            List<PaieConfigLegale> list = PaieConfigLegale.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des configurations légales.");
        }
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('EMPLOYE_READ') or hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getActiveConfig() {
        try (Connection c = dataSource.getConnection()) {
            PaieConfigLegale config = PaieConfigLegale.findActiveAt(c, LocalDate.now());
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Aucune configuration légale active trouvée.");
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    @GetMapping("/active-at")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('EMPLOYE_READ') or hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getConfigAt(@RequestParam("date") String dateStr) {
        try (Connection c = dataSource.getConnection()) {
            LocalDate date = LocalDate.parse(dateStr);
            PaieConfigLegale config = PaieConfigLegale.findActiveAt(c, date);
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Aucune configuration légale active pour la date " + dateStr);
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createConfig(@RequestBody PaieConfigLegaleRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieConfigLegale config = mapFromRequest(request);
                config.save(c);

                if (request.getTranchesIrsa() != null && !request.getTranchesIrsa().isEmpty()) {
                    for (IrsaRequest irsaReq : request.getTranchesIrsa()) {
                        BaremeIrsa b = new BaremeIrsa();
                        b.setIdConfigLegale(config.getIdConfigLegale());
                        b.setNumeroTranche(irsaReq.getNumeroTranche());
                        b.setTrancheMin(irsaReq.getTrancheMin());
                        b.setTrancheMax(irsaReq.getTrancheMax());
                        b.setTaux(irsaReq.getTaux());
                        b.setDateDebut(config.getDateDebut());
                        b.setDateFin(config.getDateFin());
                        b.save(c);
                    }
                }

                enregistrerMajorations(config.getIdConfigLegale(), config.getDateDebut(), config.getDateFin(), request.getMajorations(), c);

                AuditLog.audit(c, "CREATION", "paie_config_legale",
                        String.valueOf(config.getIdConfigLegale()), null, config);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(config);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    /**
     * Revalorisation : clôt l'exercice actif + crée le suivant à la date d'effet.
     * Revalorise également le barème IRSA de façon atomique.
     */
    @PostMapping("/revaloriser")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> revaloriser(@RequestBody PaieConfigLegaleRequest request) {
        if (request.getDateDebut() == null) {
            return ResponseEntity.badRequest().body("La date d'effet est obligatoire pour une revalorisation.");
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieConfigLegale nouvConfig = mapFromRequest(request);
                PaieConfigLegale result = PaieConfigLegale.revaloriser(c, request.getDateDebut(), nouvConfig);

                if (request.getTranchesIrsa() != null && !request.getTranchesIrsa().isEmpty()) {
                    List<BaremeIrsa> nouvellesTranches = new ArrayList<>();
                    for (IrsaRequest irsaReq : request.getTranchesIrsa()) {
                        BaremeIrsa b = new BaremeIrsa();
                        b.setIdConfigLegale(result.getIdConfigLegale());
                        b.setNumeroTranche(irsaReq.getNumeroTranche());
                        b.setTrancheMin(irsaReq.getTrancheMin());
                        b.setTrancheMax(irsaReq.getTrancheMax());
                        b.setTaux(irsaReq.getTaux());
                        nouvellesTranches.add(b);
                    }
                    BaremeIrsa.revaloriser(c, request.getDateDebut(), nouvellesTranches);
                }

                if (request.getMajorations() != null && !request.getMajorations().isEmpty()) {
                    enregistrerMajorations(result.getIdConfigLegale(), result.getDateDebut(), result.getDateFin(), request.getMajorations(), c);
                } else {
                    dupliquerMajorations(result.getIdConfigLegale(), result.getDateDebut(), result.getDateFin(), c);
                }

                AuditLog.audit(c, "REVALORISATION", "paie_config_legale",
                        String.valueOf(result.getIdConfigLegale()), null, result);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(result);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateConfig(@PathVariable int id, @RequestBody PaieConfigLegaleRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieConfigLegale existing = PaieConfigLegale.findById(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Exercice fiscal introuvable.");
                }
                PaieConfigLegale updated = mapFromRequest(request);
                updated.setIdConfigLegale(id);
                if (updated.getCodeVersion() == null || updated.getCodeVersion().isBlank()) {
                    updated.setCodeVersion(existing.getCodeVersion());
                }
                updated.update(c);

                if (request.getTranchesIrsa() != null && !request.getTranchesIrsa().isEmpty()) {
                    // Nettoyer les tranches existantes pour cet exercice
                    String delSql = "DELETE FROM bareme_irsa WHERE id_config_legale = ? OR (date_debut = ? AND (date_fin = ? OR (date_fin IS NULL AND ? IS NULL)))";
                    try (PreparedStatement ps = c.prepareStatement(delSql)) {
                        ps.setInt(1, id);
                        ps.setDate(2, updated.getDateDebut());
                        ps.setDate(3, updated.getDateFin());
                        ps.setDate(4, updated.getDateFin());
                        ps.executeUpdate();
                    }
                    for (IrsaRequest irsaReq : request.getTranchesIrsa()) {
                        BaremeIrsa b = new BaremeIrsa();
                        b.setIdConfigLegale(id);
                        b.setNumeroTranche(irsaReq.getNumeroTranche());
                        b.setTrancheMin(irsaReq.getTrancheMin());
                        b.setTrancheMax(irsaReq.getTrancheMax());
                        b.setTaux(irsaReq.getTaux());
                        b.setDateDebut(updated.getDateDebut());
                        b.setDateFin(updated.getDateFin());
                        b.save(c);
                    }
                }

                if (request.getMajorations() != null && !request.getMajorations().isEmpty()) {
                    enregistrerMajorations(id, updated.getDateDebut(), updated.getDateFin(), request.getMajorations(), c);
                }

                AuditLog.audit(c, "CORRECTION", "paie_config_legale", String.valueOf(id), existing, updated);
                c.commit();
                PaieConfigLegale reloaded = PaieConfigLegale.findById(id, c);
                return ResponseEntity.ok(reloaded != null ? reloaded : updated);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    /**
     * Annulation d'un exercice PROGRAMMÉ (date_debut > aujourd'hui).
     * Interdit sur les exercices EN_VIGUEUR ou ARCHIVÉ.
     * Restaure automatiquement le date_fin=NULL de l'exercice précédent.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerProgramme(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieConfigLegale cible = PaieConfigLegale.findById(id, c);
                if (cible == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Exercice fiscal introuvable.");
                }
                PaieConfigLegale.annulerProgramme(id, c);
                AuditLog.audit(c, "ANNULATION_PROGRAMME", "paie_config_legale",
                        String.valueOf(id), cible, null);
                c.commit();
                return ResponseEntity.ok("L'exercice programmé \"" + cible.getCodeVersion() + "\" a été annulé et l'exercice précédent a été réactivé.");
            } catch (IllegalStateException ex) {
                c.rollback();
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    private void enregistrerMajorations(int idConfigLegale, Date dateDebut, Date dateFin, List<TauxMajorationRequest> majorations, Connection c) throws Exception {
        String delSql = "DELETE FROM ref_taux_majoration WHERE id_config_legale = ?";
        try (PreparedStatement ps = c.prepareStatement(delSql)) {
            ps.setInt(1, idConfigLegale);
            ps.executeUpdate();
        }
        if (majorations != null && !majorations.isEmpty()) {
            for (TauxMajorationRequest maj : majorations) {
                RefTauxMajoration m = new RefTauxMajoration();
                m.setIdConfigLegale(idConfigLegale);
                m.setTypeMajoration(maj.getTypeMajoration());
                m.setTaux(maj.getTaux());
                m.setDescription(maj.getDescription());
                m.setDateDebut(dateDebut);
                m.setDateFin(dateFin);
                m.save(c);
            }
        } else {
            creerMajorationsParDefaut(idConfigLegale, dateDebut, dateFin, c);
        }
    }

    private void dupliquerMajorations(int nouveauIdConfig, Date dateDebut, Date dateFin, Connection c) throws Exception {
        String sql = "SELECT type_majoration, taux, description FROM ref_taux_majoration " +
                     "WHERE id_config_legale = (SELECT id_config_legale FROM paie_config_legale WHERE id_config_legale != ? ORDER BY date_debut DESC LIMIT 1)";
        List<TauxMajorationRequest> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, nouveauIdConfig);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TauxMajorationRequest req = new TauxMajorationRequest();
                    req.setTypeMajoration(rs.getString("type_majoration"));
                    req.setTaux(rs.getBigDecimal("taux"));
                    req.setDescription(rs.getString("description"));
                    list.add(req);
                }
            }
        }
        if (!list.isEmpty()) {
            enregistrerMajorations(nouveauIdConfig, dateDebut, dateFin, list, c);
        } else {
            creerMajorationsParDefaut(nouveauIdConfig, dateDebut, dateFin, c);
        }
    }

    private void creerMajorationsParDefaut(int idConfigLegale, Date dateDebut, Date dateFin, Connection c) throws Exception {
        String[][] defs = {
            {"HS_30", "0.30", "41ᵉ à 48ᵉ heure de la semaine"},
            {"HS_50", "0.50", "Au-delà de la 48ᵉ heure"},
            {"NUIT", "0.30", "Heures de nuit (22h - 05h)"},
            {"DIMANCHE", "0.40", "Travail le dimanche"},
            {"FERIE", "1.00", "Travail jour férié"}
        };
        for (String[] def : defs) {
            RefTauxMajoration m = new RefTauxMajoration();
            m.setIdConfigLegale(idConfigLegale);
            m.setTypeMajoration(def[0]);
            m.setTaux(new BigDecimal(def[1]));
            m.setDescription(def[2]);
            m.setDateDebut(dateDebut);
            m.setDateFin(dateFin);
            m.save(c);
        }
    }

    private PaieConfigLegale mapFromRequest(PaieConfigLegaleRequest r) {
        PaieConfigLegale p = new PaieConfigLegale();
        p.setCodeVersion(r.getCodeVersion());
        p.setLibelle(r.getLibelle());
        p.setDateDebut(r.getDateDebut());
        p.setDateFin(r.getDateFin());
        p.setSmeNational(r.getSmeNational());
        p.setSmeAgricole(r.getSmeAgricole());
        p.setPlafondCotisationNbSme(r.getPlafondCotisationNbSme() != null ? r.getPlafondCotisationNbSme() : new BigDecimal("8.00"));
        p.setSeuilExonerationIrsa(r.getSeuilExonerationIrsa());
        p.setMinimumPerceptionIrsa(r.getMinimumPerceptionIrsa());
        p.setDeductionChargeFamille(r.getDeductionChargeFamille());
        p.setArrondiBaseIrsa(r.getArrondiBaseIrsa() != null ? r.getArrondiBaseIrsa() : "CENTAINE_INF");
        p.setDureeHebdoLegale(r.getDureeHebdoLegale() != null ? r.getDureeHebdoLegale() : new BigDecimal("40.00"));
        p.setDiviseurMensuelHeures(r.getDiviseurMensuelHeures() != null ? r.getDiviseurMensuelHeures() : new BigDecimal("173.33"));
        p.setSeuilHsTranche1(r.getSeuilHsTranche1() != null ? r.getSeuilHsTranche1() : new BigDecimal("8.00"));
        p.setPlafondHsHebdo(r.getPlafondHsHebdo() != null ? r.getPlafondHsHebdo() : new BigDecimal("20.00"));
        p.setAcquisitionCongeMensuelJours(r.getAcquisitionCongeMensuelJours() != null ? r.getAcquisitionCongeMensuelJours() : new BigDecimal("2.50"));
        p.setDiviseurValorisationCongeJours(r.getDiviseurValorisationCongeJours() != null ? r.getDiviseurValorisationCongeJours() : 30);
        return p;
    }
}
