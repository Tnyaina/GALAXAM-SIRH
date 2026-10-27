package mg.galaxam.sirh.paieconfig.api;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.paieconfig.api.dto.IrsaRequest;
import mg.galaxam.sirh.paieconfig.api.dto.PaieConfigLegaleRequest;
import mg.galaxam.sirh.paieconfig.api.dto.TauxMajorationRequest;
import mg.galaxam.sirh.paieconfig.persistence.BaremeIrsa;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import mg.galaxam.sirh.paieconfig.persistence.RefTauxMajoration;
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('EMPLOYE_READ') or hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getActiveConfig() {
        try (Connection c = dataSource.getConnection()) {
            PaieConfigLegale config = PaieConfigLegale.findActiveAt(c, LocalDate.now());
            if (config == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Aucune configuration légale active trouvée.");
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/active-at")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('EMPLOYE_READ') or hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getConfigAt(@RequestParam("date") String dateStr) {
        try (Connection c = dataSource.getConnection()) {
            LocalDate date = LocalDate.parse(dateStr);
            PaieConfigLegale config = PaieConfigLegale.findActiveAt(c, date);
            if (config == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                        "Aucune configuration légale active pour la date " + dateStr + ".");
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "La date d'effet est obligatoire pour une revalorisation.");
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
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Exercice fiscal introuvable.");
                }
                PaieConfigLegale updated = mapFromRequest(request);
                updated.setIdConfigLegale(id);
                if (!java.util.Objects.equals(existing.getExonerationHsIrsaActive(), updated.getExonerationHsIrsaActive())
                        || compareNullable(existing.getPlafondMensuelHsExonerees(), updated.getPlafondMensuelHsExonerees()) != 0) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                            "Les paramètres d'exonération HS doivent être modifiés par revalorisation avec une date d'effet.");
                }
                if (updated.getCodeVersion() == null || updated.getCodeVersion().isBlank()) {
                    updated.setCodeVersion(existing.getCodeVersion());
                }
                Date oldDateDebut = existing.getDateDebut();
                Date newDateDebut = updated.getDateDebut();
                boolean dateDebutChanged = (oldDateDebut != null && newDateDebut != null && !oldDateDebut.equals(newDateDebut));

                PaieConfigLegale predecessor = findPredecessor(id, oldDateDebut, c);
                PaieConfigLegale successor = findSuccessor(id, oldDateDebut, c);

                if (successor != null) {
                    Date succDebut = successor.getDateDebut();
                    if (newDateDebut != null && !newDateDebut.toLocalDate().isBefore(succDebut.toLocalDate())) {
                        throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                            "La date de début (" + newDateDebut + ") ne peut pas dépasser ou égaler celle de l'exercice suivant (" + succDebut + ")."
                        );
                    }
                    if (updated.getDateFin() == null) {
                        updated.setDateFin(Date.valueOf(succDebut.toLocalDate().minusDays(1)));
                    } else if (updated.getDateFin().toLocalDate().isAfter(succDebut.toLocalDate().minusDays(1))) {
                        throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                            "La date de fin (" + updated.getDateFin() + ") ne peut pas empiéter sur l'exercice suivant (" + succDebut + ")."
                        );
                    }
                }

                if (predecessor != null && dateDebutChanged) {
                    Date predDebut = predecessor.getDateDebut();
                    if (!newDateDebut.toLocalDate().isAfter(predDebut.toLocalDate())) {
                        throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                            "La date de début (" + newDateDebut + ") ne peut pas être antérieure ou égale à celle de l'exercice précédent (" + predDebut + ")."
                        );
                    }

                    Date newVeille = Date.valueOf(newDateDebut.toLocalDate().minusDays(1));
                    boolean movingEarlier = newDateDebut.toLocalDate().isBefore(oldDateDebut.toLocalDate());

                    if (movingEarlier) {
                        ajusterFinExercice(predecessor.getIdConfigLegale(), newVeille, c);
                        updated.update(c);
                    } else {
                        updated.update(c);
                        ajusterFinExercice(predecessor.getIdConfigLegale(), newVeille, c);
                    }
                } else {
                    updated.update(c);
                }

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
                } else if (dateDebutChanged) {
                    String updIrsa = "UPDATE bareme_irsa SET date_debut = ?, date_fin = ? WHERE id_config_legale = ?";
                    try (PreparedStatement ps = c.prepareStatement(updIrsa)) {
                        ps.setDate(1, updated.getDateDebut());
                        ps.setDate(2, updated.getDateFin());
                        ps.setInt(3, id);
                        ps.executeUpdate();
                    }
                }

                if (request.getMajorations() != null && !request.getMajorations().isEmpty()) {
                    enregistrerMajorations(id, updated.getDateDebut(), updated.getDateFin(), request.getMajorations(), c);
                } else if (dateDebutChanged) {
                    String updMaj = "UPDATE ref_taux_majoration SET date_debut = ?, date_fin = ? WHERE id_config_legale = ?";
                    try (PreparedStatement ps = c.prepareStatement(updMaj)) {
                        ps.setDate(1, updated.getDateDebut());
                        ps.setDate(2, updated.getDateFin());
                        ps.setInt(3, id);
                        ps.executeUpdate();
                    }
                }

                AuditLog.audit(c, "CORRECTION", "paie_config_legale", String.valueOf(id), existing, updated);
                c.commit();
                PaieConfigLegale reloaded = PaieConfigLegale.findById(id, c);
                return ResponseEntity.ok(reloaded != null ? reloaded : updated);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Exercice fiscal introuvable.");
                }
                PaieConfigLegale.annulerProgramme(id, c);
                AuditLog.audit(c, "ANNULATION_PROGRAMME", "paie_config_legale",
                        String.valueOf(id), cible, null);
                c.commit();
                return ResponseEntity.ok("L'exercice programmé \"" + cible.getCodeVersion() + "\" a été annulé et l'exercice précédent a été réactivé.");
            } catch (IllegalStateException ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED, ex.getMessage());
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
        if (r.getDateDebut() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "La date de début ou d'effet est obligatoire.");
        }
        if (r.getSeuilHsTranche1() == null || r.getSeuilHsTranche1().signum() < 0) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                "Le palier de la première tranche HS doit être renseigné et positif ou nul.");
        }
        PaieConfigLegale p = new PaieConfigLegale();
        p.setCodeVersion(r.getCodeVersion());
        p.setLibelle(r.getLibelle());
        p.setDateDebut(r.getDateDebut());
        p.setDateFin(r.getDateFin());
        p.setSmeNational(r.getSmeNational());
        p.setSmeAgricole(r.getSmeAgricole());
        p.setPlafondCotisationNbSme(r.getPlafondCotisationNbSme() != null ? r.getPlafondCotisationNbSme() : new BigDecimal("8.00"));
        BigDecimal avancePct = r.getPlafondAvanceEmployeurPct() != null ? r.getPlafondAvanceEmployeurPct() : new BigDecimal("10.000000");
        BigDecimal quotitePct = r.getQuotiteCessiblePct() != null ? r.getQuotiteCessiblePct() : new BigDecimal("33.333333");
        if (avancePct.signum() < 0 || avancePct.compareTo(new BigDecimal("100")) > 0
                || quotitePct.signum() < 0 || quotitePct.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Les quotités de retenue doivent être comprises entre 0 et 100 %.");
        p.setPlafondAvanceEmployeurPct(avancePct);
        p.setQuotiteCessiblePct(quotitePct);
        p.setSeuilExonerationIrsa(r.getSeuilExonerationIrsa());
        p.setMinimumPerceptionIrsa(r.getMinimumPerceptionIrsa());
        p.setDeductionChargeFamille(r.getDeductionChargeFamille());
        p.setArrondiBaseIrsa(r.getArrondiBaseIrsa() != null ? r.getArrondiBaseIrsa() : "CENTAINE_INF");
        p.setDureeHebdoLegale(r.getDureeHebdoLegale() != null ? r.getDureeHebdoLegale() : new BigDecimal("40.00"));
        p.setDiviseurMensuelHeures(r.getDiviseurMensuelHeures() != null ? r.getDiviseurMensuelHeures() : new BigDecimal("173.33"));
        p.setSeuilHsTranche1(r.getSeuilHsTranche1());
        p.setPlafondHsHebdo(r.getPlafondHsHebdo() != null ? r.getPlafondHsHebdo() : new BigDecimal("20.00"));
        p.setAcquisitionCongeMensuelJours(r.getAcquisitionCongeMensuelJours() != null ? r.getAcquisitionCongeMensuelJours() : new BigDecimal("2.50"));
        boolean exoActive = Boolean.TRUE.equals(r.getExonerationHsIrsaActive());
        BigDecimal plafondExonere = r.getPlafondMensuelHsExonerees() != null
                ? r.getPlafondMensuelHsExonerees() : BigDecimal.ZERO;
        if (plafondExonere.signum() < 0 || (exoActive && plafondExonere.signum() <= 0)) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Un plafond HS positif est requis lorsque l'exonération est activée; le plafond ne peut pas être négatif.");
        }
        p.setExonerationHsIrsaActive(exoActive);
        p.setPlafondMensuelHsExonerees(plafondExonere);
        return p;
    }

    private static int compareNullable(BigDecimal left, BigDecimal right) {
        if (left == null) left = BigDecimal.ZERO;
        if (right == null) right = BigDecimal.ZERO;
        return left.compareTo(right);
    }

    private PaieConfigLegale findPredecessor(int currentId, Date currentDebut, Connection c) throws Exception {
        if (currentDebut == null) return null;
        String sql = "SELECT * FROM paie_config_legale " +
                     "WHERE id_config_legale != ? AND date_debut < ? " +
                     "ORDER BY date_debut DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, currentId);
            ps.setDate(2, currentDebut);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return PaieConfigLegale.findById(rs.getInt("id_config_legale"), c);
                }
            }
        }
        return null;
    }

    private PaieConfigLegale findSuccessor(int currentId, Date currentDebut, Connection c) throws Exception {
        if (currentDebut == null) return null;
        String sql = "SELECT * FROM paie_config_legale " +
                     "WHERE id_config_legale != ? AND date_debut > ? " +
                     "ORDER BY date_debut ASC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, currentId);
            ps.setDate(2, currentDebut);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return PaieConfigLegale.findById(rs.getInt("id_config_legale"), c);
                }
            }
        }
        return null;
    }

    private void ajusterFinExercice(int idConfig, Date nouvelleDateFin, Connection c) throws Exception {
        String updConfig = "UPDATE paie_config_legale SET date_fin = ?, updated_at = NOW() WHERE id_config_legale = ?";
        try (PreparedStatement ps = c.prepareStatement(updConfig)) {
            ps.setDate(1, nouvelleDateFin);
            ps.setInt(2, idConfig);
            ps.executeUpdate();
        }
        String updIrsa = "UPDATE bareme_irsa SET date_fin = ? WHERE id_config_legale = ?";
        try (PreparedStatement ps = c.prepareStatement(updIrsa)) {
            ps.setDate(1, nouvelleDateFin);
            ps.setInt(2, idConfig);
            ps.executeUpdate();
        }
        String updMaj = "UPDATE ref_taux_majoration SET date_fin = ? WHERE id_config_legale = ?";
        try (PreparedStatement ps = c.prepareStatement(updMaj)) {
            ps.setDate(1, nouvelleDateFin);
            ps.setInt(2, idConfig);
            ps.executeUpdate();
        }
    }
}
