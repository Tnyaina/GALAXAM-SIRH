package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.FichePointageEmployeDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageJourDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageTronconSemaineDTO;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Service gérant la consultation détaillée de la fiche de pointage d'un employé.
 * Extrait du contrôleur pour respecter le découplage MVC et la centralisation métier.
 */
@Service
public class PointageEmployeService {

    private static final Logger log = LoggerFactory.getLogger(PointageEmployeService.class);

    private static double toLegacyDecimalHours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String formatHoursMinutes(long minutes) {
        long safe = Math.max(0, minutes);
        return String.format("%02dh%02d", safe / 60, safe % 60);
    }

    private final DataSource dataSource;
    private final PointageSyntheseService syntheseService;
    private final CalendrierOuvrableEngine calendrierEngine;
    private final EmploymentContextResolver employmentContextResolver = new EmploymentContextResolver();

    @org.springframework.beans.factory.annotation.Autowired
    public PointageEmployeService(DataSource dataSource,
                                  PointageSyntheseService syntheseService,
                                  CalendrierOuvrableEngine calendrierEngine) {
        this.dataSource = dataSource;
        this.syntheseService = syntheseService;
        this.calendrierEngine = calendrierEngine != null ? calendrierEngine : new CalendrierOuvrableEngine();
    }

    public FichePointageEmployeDTO getFichePointageEmploye(Integer idEmploye, String date, String period, Integer idPeriode) {
        FichePointageEmployeDTO result = new FichePointageEmployeDTO();

        LocalDate targetDate;
        try {
            targetDate = (date != null && !date.isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        } catch (Exception e) {
            targetDate = LocalDate.now();
        }

        LocalDate startDate = targetDate.withDayOfMonth(1);
        LocalDate endDate = targetDate.withDayOfMonth(targetDate.lengthOfMonth());
        Integer resolvedIdPeriode = null;
        String resolvedCodePeriode = null;
        String resolvedLibellePeriode = null;
        String resolvedStatutPeriode = null;

        if (idPeriode != null && idPeriode > 0) {
            String sqlP = "SELECT p.id_periode_paie, p.code, p.libelle, p.date_debut, p.date_fin, s.code AS statut_code " +
                          "FROM periode_paie p " +
                          "LEFT JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                          "WHERE p.id_periode_paie = ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement psP = c.prepareStatement(sqlP)) {
                psP.setInt(1, idPeriode);
                try (ResultSet rsP = psP.executeQuery()) {
                    if (rsP.next()) {
                        resolvedIdPeriode = rsP.getInt("id_periode_paie");
                        resolvedCodePeriode = rsP.getString("code");
                        resolvedLibellePeriode = rsP.getString("libelle");
                        resolvedStatutPeriode = rsP.getString("statut_code");
                        startDate = rsP.getDate("date_debut").toLocalDate();
                        endDate = rsP.getDate("date_fin").toLocalDate();
                    }
                }
            } catch (Exception e) {
                log.warn("[PointageEmployeService] Erreur récupération période par id {}: {}", idPeriode, e.getMessage());
            }
        } else if ("periode".equalsIgnoreCase(period)) {
            String sqlP = "SELECT p.id_periode_paie, p.code, p.libelle, p.date_debut, p.date_fin, s.code AS statut_code " +
                          "FROM periode_paie p " +
                          "LEFT JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                          "WHERE ? BETWEEN p.date_debut AND p.date_fin " +
                          "ORDER BY p.date_debut DESC LIMIT 1";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement psP = c.prepareStatement(sqlP)) {
                psP.setDate(1, java.sql.Date.valueOf(targetDate));
                try (ResultSet rsP = psP.executeQuery()) {
                    if (rsP.next()) {
                        resolvedIdPeriode = rsP.getInt("id_periode_paie");
                        resolvedCodePeriode = rsP.getString("code");
                        resolvedLibellePeriode = rsP.getString("libelle");
                        resolvedStatutPeriode = rsP.getString("statut_code");
                        startDate = rsP.getDate("date_debut").toLocalDate();
                        endDate = rsP.getDate("date_fin").toLocalDate();
                    }
                }
            } catch (Exception e) {
                log.warn("[PointageEmployeService] Erreur recherche période pour date {}: {}", targetDate, e.getMessage());
            }
        } else if ("jour".equalsIgnoreCase(period)) {
            startDate = targetDate;
            endDate = targetDate;
        } else if ("semaine".equalsIgnoreCase(period)) {
            startDate = targetDate.with(DayOfWeek.MONDAY);
            endDate = targetDate.with(DayOfWeek.SUNDAY);
        }

        if (idPeriode != null && resolvedIdPeriode == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.POINTAGE_PERIOD_CONTEXT_REQUIRED,
                    "La période de paie demandée est introuvable. Sélectionnez une période existante.");
        }

        result.setIdPeriode(resolvedIdPeriode);
        result.setIdEmploye(idEmploye);
        result.setCodePeriode(resolvedCodePeriode);
        result.setLibellePeriode(resolvedLibellePeriode != null ? resolvedLibellePeriode : ("Période du " + startDate + " au " + endDate));
        result.setDateDebut(startDate.toString());
        result.setDateFin(endDate.toString());
        result.setStatutPeriode(resolvedStatutPeriode);

        // En-tête au dernier jour affiché ; les lignes quotidiennes utilisent leur propre date.
        String sqlSite = "SELECT " +
                "COALESCE(s.horaire_continu, FALSE) AS site_horaire_continu, " +
                "s.heure_debut_matin, s.heure_fin_matin, " +
                "s.heure_debut_apres_midi, s.heure_fin_apres_midi, " +
                "CASE WHEN s.plancher_arrivee_actif = TRUE THEN s.heure_entree_minimale_valorisee ELSE NULL END AS heure_plafond " +
                "FROM ref_site s WHERE s.id_site = ?";

        LocalTime hDebutMatin = null;
        LocalTime hFinMatin = null;
        LocalTime hDebutAm = null;
        LocalTime hFinAm = null;
        LocalTime hPlafondTolerance = null;
        boolean siteContinu = false;
        String empMatricule = "—";
        String empNom = null;
        String empPrenom = null;
        String empNomComplet = "—";
        String empSiteNom = "Aucun site à cette date";
        String empFonction = "Collaborateur";
        String empCategorie = "—";

        try (Connection c = dataSource.getConnection()) {
            var contexte = employmentContextResolver.resolveContext(idEmploye, endDate, c);
            if (contexte != null) {
                if (contexte.matricule() != null) empMatricule = contexte.matricule();
                empNom = contexte.nom();
                empPrenom = contexte.prenom();
                empNomComplet = contexte.nomComplet() == null || contexte.nomComplet().isBlank()
                        ? "Collaborateur" : contexte.nomComplet();
                if (contexte.nomSite() != null) empSiteNom = contexte.nomSite();
                if (contexte.fonctionNom() != null) empFonction = contexte.fonctionNom();
                if (contexte.categorieProCode() != null) empCategorie = contexte.categorieProCode();
            }
            if (contexte != null && contexte.idSite() != null) {
                try (PreparedStatement psSite = c.prepareStatement(sqlSite)) {
                    psSite.setInt(1, contexte.idSite());
                    try (ResultSet rsSite = psSite.executeQuery()) {
                        if (rsSite.next()) {
                            if (rsSite.getTime("heure_debut_matin") != null) hDebutMatin = rsSite.getTime("heure_debut_matin").toLocalTime();
                            if (rsSite.getTime("heure_fin_matin") != null) hFinMatin = rsSite.getTime("heure_fin_matin").toLocalTime();
                            if (rsSite.getTime("heure_debut_apres_midi") != null) hDebutAm = rsSite.getTime("heure_debut_apres_midi").toLocalTime();
                            if (rsSite.getTime("heure_fin_apres_midi") != null) hFinAm = rsSite.getTime("heure_fin_apres_midi").toLocalTime();
                            if (rsSite.getTime("heure_plafond") != null) hPlafondTolerance = rsSite.getTime("heure_plafond").toLocalTime();
                            siteContinu = rsSite.getBoolean("site_horaire_continu");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Impossible de résoudre le contexte du salarié au " + endDate + ".", e);
        }
        if (hDebutMatin == null || hFinAm == null || (!siteContinu && (hFinMatin == null || hDebutAm == null))) {
            hDebutMatin = LocalTime.of(8, 0);
            hFinMatin = LocalTime.of(12, 0);
            hDebutAm = LocalTime.of(13, 0);
            hFinAm = LocalTime.of(17, 0);
            siteContinu = false;
        }

        result.setMatricule(empMatricule);
        result.setNom(empNom);
        result.setPrenom(empPrenom);
        result.setNomComplet(empNomComplet);
        result.setSiteNom(empSiteNom);
        result.setFonction(empFonction);
        result.setCategorie(empCategorie);

        // Calculs dynamique des règles
        Map<String, String> regles = new LinkedHashMap<>();
        if (hFinMatin != null && hDebutAm != null) {
            long pauseMins = Duration.between(hFinMatin, hDebutAm).toMinutes();
            regles.put("pause", String.format("%02dh%02d", pauseMins / 60, pauseMins % 60));
        }
        long dureeJourMins = hFinMatin != null && hDebutAm != null
                ? Duration.between(hDebutMatin, hFinMatin).toMinutes()
                    + Duration.between(hDebutAm, hFinAm).toMinutes()
                : Duration.between(hDebutMatin, hFinAm).toMinutes();
        if (dureeJourMins <= 0) {
            dureeJourMins = 480;
        }
        regles.put("horaireJournalier", String.format("%02dh%02d", dureeJourMins / 60, dureeJourMins % 60));
        if (hDebutMatin != null) regles.put("debutNormal", hDebutMatin.toString().substring(0, 5));
        if (hPlafondTolerance != null) regles.put("plafondArrivee", hPlafondTolerance.toString().substring(0, 5));

        // 1. Récupération des infos salarié (date embauche / fin de contrat robuste & statut autonome en code Java)
        LocalDate dateEmbauche = null;
        LocalDate dateDepart = null;

        // A. Contrats du salarié : date d'embauche et date de départ
        String sqlContrats = "SELECT ct.date_debut, ct.date_fin, rsc.code AS code_statut " +
                             "FROM contrat_travail ct " +
                             "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                             "WHERE ct.id_employe = ? ORDER BY ct.date_debut ASC";
        boolean hasContratActif = false;
        LocalDate maxDateFin = null;

        try (Connection c = dataSource.getConnection();
             PreparedStatement psC = c.prepareStatement(sqlContrats)) {
            psC.setInt(1, idEmploye);
            try (ResultSet rsC = psC.executeQuery()) {
                while (rsC.next()) {
                    Date dDeb = rsC.getDate("date_debut");
                    Date dFin = rsC.getDate("date_fin");
                    String st = rsC.getString("code_statut");

                    if (dDeb != null) {
                        LocalDate ldDeb = dDeb.toLocalDate();
                        if (dateEmbauche == null || ldDeb.isBefore(dateEmbauche)) {
                            dateEmbauche = ldDeb;
                        }
                    }
                    if (dFin == null || "ACTIF".equalsIgnoreCase(st)) {
                        hasContratActif = true;
                    } else {
                        LocalDate ldFin = dFin.toLocalDate();
                        if (maxDateFin == null || ldFin.isAfter(maxDateFin)) {
                            maxDateFin = ldFin;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[PointageEmployeService] Erreur récupération contrats pour employé {}: {}", idEmploye, e.getMessage());
        }

        if (!hasContratActif && maxDateFin != null) {
            dateDepart = maxDateFin;
        }

        // B. Affectations & Fonction : Détection de la dispense de pointage (Cadre autonome)
        String sqlAffs = "SELECT a.date_debut, a.date_fin " +
                         "FROM employe_affectation_carriere a " +
                         "WHERE a.id_employe = ? " +
                         "ORDER BY (a.date_fin IS NULL) DESC, a.date_debut DESC";

        try (Connection c = dataSource.getConnection();
             PreparedStatement psA = c.prepareStatement(sqlAffs)) {
            psA.setInt(1, idEmploye);
            try (ResultSet rsA = psA.executeQuery()) {
                while (rsA.next()) {
                    Date dDeb = rsA.getDate("date_debut");

                    if (dateEmbauche == null && dDeb != null) {
                        dateEmbauche = dDeb.toLocalDate();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[PointageEmployeService] Erreur vérification statut cadre/autonome pour employé {}: {}", idEmploye, e.getMessage());
        }

        // 1.5 Récupération des jours fériés/chômés sur la période via CalendrierOuvrableEngine
        Set<LocalDate> feriesChomes = null;
        try (Connection c = dataSource.getConnection()) {
            feriesChomes = calendrierEngine.getDatesFeriesEtChomeesEmploye(startDate, endDate, idEmploye, c);
        } catch (Exception e) {
            log.warn("[PointageEmployeService] Erreur récupération jours fériés pour employé {}: {}", idEmploye, e.getMessage());
        }

        Map<LocalDate, EmploymentContextResolver.PointageDay> pointageByDate;
        try (Connection c = dataSource.getConnection()) {
            pointageByDate = employmentContextResolver.resolvePointageDays(idEmploye, startDate, endDate, c);
        } catch (SQLException error) {
            throw new IllegalStateException("Impossible de résoudre le site historique de l'employé.", error);
        }

        // 2. Récupération des pointages enregistrés en base (avec indemnités de repas et déplacement)
        Map<LocalDate, PointageRecord> pointageMap = new HashMap<>();

        String sqlPointage = "SELECT p.id_pointage, p.id_site, p.version, p.date_pointage, p.heure_entree_matin, p.heure_sortie_matin, " +
                "p.heure_entree_am, p.heure_sortie_am, p.total_heures, p.heures_nuit, p.minutes_retard, " +
                "p.horaire_continu_jour, p.motif_ajustement, p.montant_total_indemnites, " +
                "p.code_second_repas, p.montant_second_repas, s.code AS code_statut, " +
                "pij_repas.accorde AS repas_accorde, pij_repas.quantite AS repas_quantite, " +
                "pij_repas.montant_calcule AS repas_montant, " +
                "pij_dep.accorde AS dep_accorde, pij_dep.quantite AS dep_quantite, pij_dep.fraction AS dep_fraction, " +
                "pij_dep.montant_calcule AS dep_montant " +
                "FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour " +
                "LEFT JOIN pointage_indemnite_jour pij_repas ON p.id_pointage = pij_repas.id_pointage " +
                "    AND pij_repas.id_indemnite = (SELECT id_indemnite FROM ref_indemnite WHERE code = 'REPAS' LIMIT 1) " +
                "LEFT JOIN pointage_indemnite_jour pij_dep ON p.id_pointage = pij_dep.id_pointage " +
                "    AND pij_dep.id_indemnite IN (SELECT id_indemnite FROM ref_indemnite WHERE code IN ('DEPLACEMENT', 'TRANSPORT') LIMIT 1) " +
                "WHERE p.id_employe = ? AND p.date_pointage BETWEEN ? AND ?";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sqlPointage)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, java.sql.Date.valueOf(startDate));
            ps.setDate(3, java.sql.Date.valueOf(endDate));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PointageRecord r = new PointageRecord();
                    r.idPointage = rs.getInt("id_pointage");
                    r.idSite = rs.getInt("id_site");
                    r.version = rs.getInt("version");
                    r.observation = rs.getString("motif_ajustement");
                    r.datePointage = rs.getDate("date_pointage").toLocalDate();
                    r.entreeMatin = rs.getTime("heure_entree_matin");
                    r.sortieMatin = rs.getTime("heure_sortie_matin");
                    r.entreeAm = rs.getTime("heure_entree_am");
                    r.sortieAm = rs.getTime("heure_sortie_am");
                    BigDecimal totalHeuresLegacy = rs.getBigDecimal("total_heures");
                    BigDecimal heuresNuitLegacy = rs.getBigDecimal("heures_nuit");
                    r.totalMinutes = totalHeuresLegacy == null ? 0 : totalHeuresLegacy.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValueExact();
                    r.minutesNuit = heuresNuitLegacy == null ? 0 : heuresNuitLegacy.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValueExact();
                    r.minutesRetard = rs.getInt("minutes_retard");
                    r.horaireContinuJour = Boolean.TRUE.equals(rs.getObject("horaire_continu_jour"));

                    boolean depAccorde = Boolean.TRUE.equals(rs.getObject("dep_accorde"));
                    BigDecimal depQte = zeroIfNull(rs.getBigDecimal("dep_quantite"));
                    String depFrac = rs.getString("dep_fraction");
                    if (depAccorde) {
                        if ("MATIN".equalsIgnoreCase(depFrac)) {
                            r.deplacementMatin = true;
                            r.deplacementAm = false;
                        } else if ("APRES_MIDI".equalsIgnoreCase(depFrac)) {
                            r.deplacementMatin = false;
                            r.deplacementAm = true;
                        } else {
                            r.deplacementMatin = depQte.signum() > 0;
                            r.deplacementAm = depQte.compareTo(BigDecimal.ONE) >= 0;
                        }
                    } else {
                        r.deplacementMatin = false;
                        r.deplacementAm = false;
                    }

                    r.codeSecondRepas = rs.getString("code_second_repas");
                    r.montantSecondRepas = zeroIfNull(rs.getBigDecimal("montant_second_repas"));
                    r.indemniteRepasRecue = Boolean.TRUE.equals(rs.getObject("repas_accorde"))
                            && zeroIfNull(rs.getBigDecimal("repas_quantite")).signum() > 0;
                    r.montantRepas = zeroIfNull(rs.getBigDecimal("repas_montant"))
                            .subtract(r.montantSecondRepas).max(BigDecimal.ZERO);
                    r.montantDeplacement = zeroIfNull(rs.getBigDecimal("dep_montant"));
                    r.codeStatut = rs.getString("code_statut");
                    pointageMap.put(r.datePointage, r);
                }
            }
        } catch (Exception e) {
            log.warn("[PointageEmployeService] Erreur récupération pointages pour employé {}: {}", idEmploye, e.getMessage());
        }

        // Le journal conserve les indemnités par code : la fiche ne doit pas limiter son affichage à REPAS/TRANSPORT.
        String sqlIndemnites = "SELECT p.date_pointage, i.code, "
                + "BOOL_OR(pij.accorde) AS accorde, "
                + "SUM(CASE WHEN pij.accorde THEN pij.quantite ELSE 0 END) AS quantite, "
                + "COALESCE(SUM(CASE WHEN pij.accorde THEN pij.montant_calcule ELSE 0 END), 0) AS montant_calcule "
                + "FROM pointage_jour p JOIN pointage_indemnite_jour pij ON pij.id_pointage = p.id_pointage "
                + "JOIN ref_indemnite i ON i.id_indemnite = pij.id_indemnite "
                + "WHERE p.id_employe = ? AND p.date_pointage BETWEEN ? AND ? "
                + "GROUP BY p.date_pointage, i.code ORDER BY i.code";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sqlIndemnites)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, java.sql.Date.valueOf(startDate));
            ps.setDate(3, java.sql.Date.valueOf(endDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PointageRecord record = pointageMap.get(rs.getDate("date_pointage").toLocalDate());
                    if (record == null) continue;
                    String code = rs.getString("code");
                    record.indemnites.put(code, new PointageJourDTO.IndemniteJourDTO(
                            code, rs.getBoolean("accorde"), zeroIfNull(rs.getBigDecimal("montant_calcule")), rs.getBigDecimal("quantite")));
                }
            }
        } catch (Exception e) {
            log.warn("[PointageEmployeService] Erreur récupération indemnités détaillées pour employé {}: {}", idEmploye, e.getMessage());
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.POINTAGE_EXPORT_SOURCE_UNAVAILABLE);
        }

        for (PointageRecord record : pointageMap.values()) {
            if (record.codeSecondRepas != null) {
                record.indemnites.put("REPAS", new PointageJourDTO.IndemniteJourDTO(
                        "REPAS", record.indemniteRepasRecue, record.montantRepas,
                        record.indemniteRepasRecue ? BigDecimal.ONE : BigDecimal.ZERO));
                record.indemnites.put(record.codeSecondRepas, new PointageJourDTO.IndemniteJourDTO(
                        record.codeSecondRepas, true, record.montantSecondRepas, BigDecimal.ONE));
            }
        }

        // Le catalogue du journal est résolu depuis les tarifs site applicables à la période.
        // Les forfaits FIXE_MENSUEL sont exclus : ils sont indépendants du pointage journalier.
        String sqlIndemnitesDisponibles = "SELECT DISTINCT i.code, i.libelle, c.periodicite "
                + "FROM site_indemnite_config c JOIN ref_indemnite i ON i.id_indemnite = c.id_indemnite "
                + "JOIN employe_affectation_carriere a ON a.id_site = c.id_site AND a.id_employe = ? "
                + "WHERE c.est_actif = TRUE AND i.est_actif = TRUE "
                + "AND upper(c.periodicite) IN ('JOURNALIER', 'MENSUEL_PRORATISE', 'MENSUEL') "
                + "AND c.date_debut <= ? AND (c.date_fin IS NULL OR c.date_fin >= ?) "
                + "AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) "
                + "ORDER BY i.code";
        Map<String, FichePointageEmployeDTO.IndemniteFicheDTO> indemnitesDisponibles = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sqlIndemnitesDisponibles)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(endDate));
            ps.setDate(3, Date.valueOf(startDate));
            ps.setDate(4, Date.valueOf(endDate));
            ps.setDate(5, Date.valueOf(startDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code");
                    if (code != null) {
                        indemnitesDisponibles.put(code, new FichePointageEmployeDTO.IndemniteFicheDTO(
                                code, rs.getString("periodicite"),rs.getString("libelle")));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[PointageEmployeService] Erreur récupération catalogue indemnités période employé {}: {}", idEmploye, e.getMessage());
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.POINTAGE_EXPORT_SOURCE_UNAVAILABLE);
        }
        if (pointageMap.values().stream().anyMatch(record -> "REPAS_2".equals(record.codeSecondRepas))) {
            indemnitesDisponibles.put("REPAS_2", new FichePointageEmployeDTO.IndemniteFicheDTO(
                    "REPAS_2", "AUTOMATIQUE", "Deuxième repas"));
        }
        if (pointageMap.values().stream().anyMatch(record -> "REPAS_2_MAJORE".equals(record.codeSecondRepas))) {
            indemnitesDisponibles.put("REPAS_2_MAJORE", new FichePointageEmployeDTO.IndemniteFicheDTO(
                    "REPAS_2_MAJORE", "AUTOMATIQUE", "Deuxième repas majoré"));
        }
        result.setIndemnitesDisponibles(indemnitesDisponibles);

        Map<LocalDate, String> congeMap = new HashMap<>();
        String sqlConges = "SELECT dc.date_debut, dc.date_fin, dc.est_demi_journee, dc.moment_demi_journee FROM demande_conge dc " +
                "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                "WHERE dc.id_employe = ? AND sc.code IN ('VALIDE', 'APPROUVEE') AND dc.date_debut <= ? AND dc.date_fin >= ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement psC = c.prepareStatement(sqlConges)) {
            psC.setInt(1, idEmploye);
            psC.setDate(2, java.sql.Date.valueOf(endDate));
            psC.setDate(3, java.sql.Date.valueOf(startDate));
            try (ResultSet rsC = psC.executeQuery()) {
                while (rsC.next()) {
                    LocalDate deb = rsC.getDate("date_debut").toLocalDate();
                    LocalDate fn = rsC.getDate("date_fin").toLocalDate();
                    boolean demi = rsC.getBoolean("est_demi_journee");
                    for (LocalDate d = deb; !d.isAfter(fn); d = d.plusDays(1)) {
                        congeMap.put(d, demi ? "HALF_DAY" : "FULL_DAY");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[PointageEmployeService] Erreur récupération congés validés pour employé {}: {}", idEmploye, e.getMessage());
        }

        List<PointageJourDTO> list = new ArrayList<>();
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd/MM");

        int totalJoursOuvrables = 0;
        int totalJoursTravailles = 0;
        double totalHeuresRealisees = 0.0;
        int totalMinutesRetard = 0;
        int nbOccurrencesRetard = 0;
        int nbAbsences = 0;

        int total1erRepasCount = 0;
        int total2eRepasCount = 0;
        BigDecimal totalRepasMontant = BigDecimal.ZERO;
        BigDecimal total2eRepasMontant = BigDecimal.ZERO;
        double totalTrajetsCount = 0.0;
        double totalHeuresNuit = 0.0;
        double totalHeuresDimanche = 0.0;
        double totalHeuresFerie = 0.0;
        int cumulMinutesEcart = 0;

        LocalDate today = LocalDate.now();

        for (LocalDate curr = startDate; !curr.isAfter(endDate); curr = curr.plusDays(1)) {
            EmploymentContextResolver.PointageDay contexteJour = pointageByDate.get(curr);
            boolean isAutonome = contexteJour != null && contexteJour.dispensePointage();
            boolean isWeekend = CalendrierOuvrableEngine.isWeekend(curr);
            boolean isHorsContrat = contexteJour == null;
            boolean isAvantEmbauche = (dateEmbauche != null && curr.isBefore(dateEmbauche)) || isHorsContrat;
            boolean isApresDepart = (dateDepart != null && curr.isAfter(dateDepart)) || isHorsContrat;
            boolean isFerie = feriesChomes != null && feriesChomes.contains(curr);

            if (!isWeekend && !isHorsContrat && !isFerie) {
                totalJoursOuvrables++;
            }

            PointageJourDTO dto = new PointageJourDTO();
            dto.setDate(curr.format(dayFormatter));
            dto.setDateComplete(curr.toString());
            dto.setIdSite(contexteJour == null ? null : contexteJour.siteId());

            String dayName = curr.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.FRENCH);
            if (dayName != null && !dayName.isEmpty()) {
                dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1) + ".";
            }
            dto.setJour(dayName);
            dto.setWeekend(isWeekend);

            PointageRecord rec = pointageMap.get(curr);

            if (rec != null) {
                dto.setIdPointage(rec.idPointage);
                dto.setIdSite(rec.idSite);
                dto.setVersion(rec.version);
                dto.setObservation(rec.observation);
                dto.setEntree(rec.entreeMatin != null ? rec.entreeMatin.toString().substring(0, 5) : "—");
                dto.setSortiePause(rec.sortieMatin != null ? rec.sortieMatin.toString().substring(0, 5) : "—");
                dto.setRetourPause(rec.entreeAm != null ? rec.entreeAm.toString().substring(0, 5) : "—");
                dto.setSortie(rec.sortieAm != null ? rec.sortieAm.toString().substring(0, 5) : "—");
                dto.setHoraireContinuJour(rec.horaireContinuJour);
                dto.setIndemnites(rec.indemnites);

                long totalMinutesJour = rec.totalMinutes;
                boolean isWeekendRec = CalendrierOuvrableEngine.isWeekend(curr);

                dto.setHeures(formatHoursMinutes(totalMinutesJour));
                dto.setTotalHeuresDecimal(toLegacyDecimalHours(totalMinutesJour));

                totalHeuresNuit += toLegacyDecimalHours(rec.minutesNuit);

                // Les indemnités sont des résultats persistés du calcul journalier, jamais recalculés ici.
                boolean isNonTravaille = "CONGE".equalsIgnoreCase(rec.codeStatut)
                        || "FERIE_CHOME".equalsIgnoreCase(rec.codeStatut)
                        || "ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(rec.codeStatut)
                        || "ABSENCE_JUSTIFIEE".equalsIgnoreCase(rec.codeStatut)
                        || "REPOS".equalsIgnoreCase(rec.codeStatut)
                        || "NON_CONCERNE".equalsIgnoreCase(rec.codeStatut)
                        || (isWeekendRec && totalMinutesJour <= 0);

                boolean estTravailleOuPresent = !isNonTravaille && (totalMinutesJour > 0 || (isAutonome && !isWeekendRec));

                boolean aDroitRepas = estTravailleOuPresent && (rec.indemniteRepasRecue || isAutonome);
                boolean depM = estTravailleOuPresent && (rec.deplacementMatin || isAutonome);
                boolean depA = estTravailleOuPresent && (rec.deplacementAm || isAutonome);

                dto.setDeplacementMatin(depM);
                dto.setDeplacementAm(depA);
                dto.setIndemniteDeplacement(depM || depA);

                if (depM) totalTrajetsCount += 0.5;
                if (depA) totalTrajetsCount += 0.5;

                PointageJourDTO.IndemniteJourDTO secondMeal = rec.indemnites.get("REPAS_2");
                PointageJourDTO.IndemniteJourDTO majorSecondMeal = rec.indemnites.get("REPAS_2_MAJORE");
                boolean aDroitSecondRepas = secondMeal != null && secondMeal.accordee();
                boolean aDroitSecondRepasMajore = majorSecondMeal != null && majorSecondMeal.accordee();
                BigDecimal mntSecondRepasJour = aDroitSecondRepas ? zeroIfNull(secondMeal.montant())
                        : (aDroitSecondRepasMajore ? zeroIfNull(majorSecondMeal.montant()) : BigDecimal.ZERO);
                if (aDroitSecondRepas || aDroitSecondRepasMajore) {
                    total2eRepasCount++;
                    total2eRepasMontant = total2eRepasMontant.add(mntSecondRepasJour);
                }
                int nbRepasJour = (aDroitRepas ? 1 : 0) + ((aDroitSecondRepas || aDroitSecondRepasMajore) ? 1 : 0);
                BigDecimal mntRepasJour = (aDroitRepas ? rec.montantRepas : BigDecimal.ZERO)
                        .add(mntSecondRepasJour);
                if (aDroitRepas) {
                    total1erRepasCount++;
                    totalRepasMontant = totalRepasMontant.add(rec.montantRepas);
                }

                dto.setIndemniteRepas(nbRepasJour > 0);
                dto.setNbRepas(nbRepasJour);
                dto.setMontantRepas(mntRepasJour);

                BigDecimal mntDepJour = (depM || depA) ? rec.montantDeplacement : BigDecimal.ZERO;
                dto.setMontantDeplacement(mntDepJour);

                if ("DIMANCHE_TRAVAILLE".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += toLegacyDecimalHours(totalMinutesJour);
                    totalHeuresDimanche += toLegacyDecimalHours(totalMinutesJour);
                    cumulMinutesEcart += totalMinutesJour;
                    dto.setEcart("+" + dto.getHeures() + " (Maj. 40%)");
                    dto.setEcartPositif(true);
                } else if ("FERIE_TRAVAILLE".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += toLegacyDecimalHours(totalMinutesJour);
                    totalHeuresFerie += toLegacyDecimalHours(totalMinutesJour);
                    cumulMinutesEcart += totalMinutesJour;
                    dto.setEcart("+" + dto.getHeures() + " (Gratif. 100%)");
                    dto.setEcartPositif(true);
                } else {
                    if (rec.minutesRetard > 0 && !isAutonome) {
                        dto.setMinutesRetard(rec.minutesRetard);
                        totalMinutesRetard += rec.minutesRetard;
                        nbOccurrencesRetard++;
                    }
                    if (totalMinutesJour > dureeJourMins) {
                        dto.setEcart("+" + formatHoursMinutes(totalMinutesJour - dureeJourMins));
                        dto.setEcartPositif(true);
                        cumulMinutesEcart += (totalMinutesJour - dureeJourMins);
                    } else if (totalMinutesJour < dureeJourMins && totalMinutesJour > 0) {
                        dto.setEcart("-" + formatHoursMinutes(dureeJourMins - totalMinutesJour));
                        dto.setEcartPositif(false);
                        cumulMinutesEcart += (totalMinutesJour - dureeJourMins);
                    } else {
                        dto.setEcart("00 min");
                        dto.setEcartPositif(true);
                    }
                }

                String typeCongeRec = congeMap.get(curr);
                boolean isCongeValideRec = typeCongeRec != null && (rec.totalMinutes <= 0 
                        || "ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(rec.codeStatut) 
                        || "ABSENCE_JUSTIFIEE".equalsIgnoreCase(rec.codeStatut) 
                        || "ANOMALIE_SAISIE".equalsIgnoreCase(rec.codeStatut));

                if (isCongeValideRec) {
                    dto.setCongeApprouve(true);
                    dto.setStatut("HALF_DAY".equals(typeCongeRec) ? "DEMI_JOURNEE" : "CONGE");
                    if ("HALF_DAY".equals(typeCongeRec)) {
                        totalJoursTravailles++;
                        totalHeuresRealisees += toLegacyDecimalHours(totalMinutesJour);
                    }
                } else if ("PRESENT".equalsIgnoreCase(rec.codeStatut) || ("DISPENSE_POINTAGE".equalsIgnoreCase(rec.codeStatut) && !isWeekendRec) || "SAMEDI_TRAVAILLE".equalsIgnoreCase(rec.codeStatut) || "DEMI_JOURNEE".equalsIgnoreCase(rec.codeStatut) || "RETARD".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += toLegacyDecimalHours(totalMinutesJour);
                } else if ("ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(rec.codeStatut)) {
                    nbAbsences++;
                    cumulMinutesEcart -= dureeJourMins;
                }

                if (!isCongeValideRec) {
                    if ("DISPENSE_POINTAGE".equalsIgnoreCase(rec.codeStatut) && isWeekendRec && rec.totalMinutes <= 0) {
                        dto.setStatut("REPOS");
                    } else {
                        dto.setStatut(rec.codeStatut != null ? rec.codeStatut.toUpperCase() : "PRESENT");
                    }
                }
            } else {
                dto.setEntree("—");
                dto.setSortiePause("—");
                dto.setRetourPause("—");
                dto.setSortie("—");
                dto.setEcart("—");
                dto.setDeplacementMatin(false);
                dto.setDeplacementAm(false);
                dto.setIndemniteDeplacement(false);
                dto.setIndemniteRepas(false);
                dto.setNbRepas(0);
                dto.setMontantRepas(BigDecimal.ZERO);
                dto.setMontantDeplacement(BigDecimal.ZERO);

                String typeConge = congeMap.get(curr);

                if (isAvantEmbauche || isApresDepart) {
                    dto.setHeures("—");
                    dto.setStatut("NON_CONCERNE");
                } else if (typeConge != null && "FULL_DAY".equals(typeConge)) {
                    dto.setCongeApprouve(true);
                    dto.setHeures("00h00");
                    dto.setStatut("CONGE");
                } else if (isFerie) {
                    dto.setHeures("00h00");
                    dto.setStatut("FERIE_CHOME");
                } else if (isWeekend) {
                    dto.setHeures("00h00");
                    dto.setStatut("REPOS");
                } else if (curr.isAfter(today)) {
                    // Jour futur dans le mois en cours (non échu)
                    // Pour un autonome : planification indicative sans incrémenter les compteurs à date
                    if (isAutonome) {
                        dto.setHeures(formatHoursMinutes(dureeJourMins));
                        dto.setStatut("PLANIFIE");
                        dto.setEcart("—");
                        dto.setEcartPositif(true);
                        dto.setDeplacementMatin(false);
                        dto.setDeplacementAm(false);
                        dto.setIndemniteDeplacement(false);
                        dto.setIndemniteRepas(false);
                        dto.setNbRepas(0);
                        dto.setMontantRepas(BigDecimal.ZERO);
                        dto.setMontantDeplacement(BigDecimal.ZERO);
                    } else {
                        dto.setHeures("—");
                        dto.setStatut("NON_ECHU");
                    }
                } else if (isAutonome) {
                    dto.setHeures(formatHoursMinutes(dureeJourMins));
                    dto.setTotalHeuresDecimal(toLegacyDecimalHours(dureeJourMins));
                    dto.setStatut("DISPENSE_POINTAGE");
                    dto.setEcart("00 min");
                    dto.setEcartPositif(true);
                    dto.setDeplacementMatin(false);
                    dto.setDeplacementAm(false);
                    dto.setIndemniteDeplacement(false);
                    dto.setIndemniteRepas(false);
                    dto.setNbRepas(0);
                    dto.setMontantRepas(BigDecimal.ZERO);
                    dto.setMontantDeplacement(BigDecimal.ZERO);
                    totalJoursTravailles++;
                    totalHeuresRealisees += toLegacyDecimalHours(dureeJourMins);
                } else {
                    dto.setHeures("00h00");
                    dto.setTotalHeuresDecimal(0.0);
                    dto.setStatut("ABSENT");
                    dto.setMontantDeplacement(BigDecimal.ZERO);
                    nbAbsences++;
                }
            }

            list.add(dto);
        }

        // 3. Calcul de la synthèse exacte unifiée via le moteur métier centralisé
        PointageSyntheseMensuelleDTO synDto = null;
        Map<String, ParametreGlobal.ParametreEtat> parameterStates = Collections.emptyMap();
        try (Connection c = dataSource.getConnection()) {
            Map<String, String> pMap = ParametreGlobal.loadActiveMapAtDate(Date.valueOf(startDate), c);

            // Récupération des infos carrières de l'employé
            String sqlCarriere = "SELECT e.matricule, e.nom, e.prenom, e.photo_url, " +
                                 "s.id_site, s.code_site AS site_code, s.libelle AS site_nom, " +
                                 "COALESCE(cp.code, '') AS code_categorie, COALESCE(cp.est_cadre, FALSE) AS categorie_est_cadre, " +
                                 "COALESCE(f.dispense_pointage, FALSE) AS est_autonome, " +
                                 "a.date_debut, a.date_fin " +
                                 "FROM employe e " +
                                 "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                                 "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                                 "LEFT JOIN ref_categorie_professionnelle cp ON a.id_categorie_professionnelle = cp.id_categorie_professionnelle " +
                                 "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
                                 "WHERE e.id_employe = ? " +
                                 "ORDER BY (a.date_fin IS NULL) DESC, a.date_debut DESC";

            try (PreparedStatement psCar = c.prepareStatement(sqlCarriere)) {
                psCar.setInt(1, idEmploye);
                try (ResultSet rsCar = psCar.executeQuery()) {
                    while (rsCar.next()) {
                        Date dDeb = rsCar.getDate("date_debut");
                        Date dFin = rsCar.getDate("date_fin");
                        LocalDate ldDeb = dDeb != null ? dDeb.toLocalDate() : null;
                        LocalDate ldFin = dFin != null ? dFin.toLocalDate() : null;

                        boolean covers = (ldDeb == null || !ldDeb.isAfter(endDate)) && (ldFin == null || !ldFin.isBefore(startDate));
                        if (!covers) {
                            continue;
                        }

                        String codeCat = rsCar.getString("code_categorie");
                        boolean estAutonomeEmp = rsCar.getBoolean("est_autonome");
                        boolean isCadre = rsCar.getBoolean("categorie_est_cadre");

                        synDto = syntheseService.calculerSyntheseEmploye(
                                idEmploye,
                                rsCar.getString("matricule"),
                                rsCar.getString("nom"),
                                rsCar.getString("prenom"),
                                rsCar.getString("photo_url"),
                                rsCar.getInt("id_site"),
                                rsCar.getString("site_code"),
                                rsCar.getString("site_nom"),
                                null,
                                codeCat,
                                isCadre,
                                estAutonomeEmp,
                                startDate,
                                endDate,
                                ldDeb,
                                ldFin,
                                resolvedIdPeriode,
                                null,
                                resolvedLibellePeriode,
                                resolvedStatutPeriode,
                                totalJoursOuvrables,
                                null,
                                pMap,
                                c
                        );

                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("[PointageEmployeService] Erreur calcul synthèse employe {}: {}", idEmploye, e.getMessage(), e);
        }
        try (Connection configConnection = dataSource.getConnection()) {
            parameterStates = ParametreGlobal.loadEtatMapAtDate(
                    Date.valueOf(startDate != null ? startDate : LocalDate.now()), configConnection);
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de résoudre la configuration Pointage de la fiche.", exception);
        }

        // Calcul unifié de l'objectif d'heures du collaborateur sur la période
        // Source unique de vérité : la somme des seuils de chaque tronçon calculés par AttendanceEngine
        double totalObjectifHeures = 0.0;
        if (synDto != null && synDto.getTronconsSemaine() != null && !synDto.getTronconsSemaine().isEmpty()) {
            totalObjectifHeures = synDto.getTronconsSemaine().stream()
                    .mapToDouble(PointageTronconSemaineDTO::getSeuilHeuresTroncon)
                    .sum();
        }
        int nbCongesVal = synDto != null ? synDto.getNbJoursConge() : 0;
        int nbFerieChomeVal = synDto != null ? synDto.getNbJoursFerieChome() : 0;
        if (totalObjectifHeures <= 0.0 && synDto != null) {
            int joursEffectifs = Math.max(0, totalJoursOuvrables - nbCongesVal);
            totalObjectifHeures = joursEffectifs * (dureeJourMins / 60.0);
        }

        int objH = (int) Math.floor(totalObjectifHeures);
        int objM = (int) Math.round((totalObjectifHeures - objH) * 60);
        String heuresObjectifStr = String.format("%02dh%02d", objH, objM);

        int nbJoursAEffectuer = Math.max(0, totalJoursOuvrables - nbCongesVal);
        String joursPrevusStr;
        if (synDto == null) {
            joursPrevusStr = "0 prévu (inactif sur la période)";
        } else if (nbCongesVal > 0) {
            joursPrevusStr = String.format("%d prévus (+%d j congé)", nbJoursAEffectuer, nbCongesVal);
        } else {
            joursPrevusStr = String.format("%d prévus", totalJoursOuvrables);
        }

        double heuresRealisees = (synDto != null && synDto.getTotalHeuresTravaillees() > 0) ? synDto.getTotalHeuresTravaillees() : totalHeuresRealisees;
        int retardsMins = synDto != null ? synDto.getTotalMinutesRetard() : totalMinutesRetard;
        double hs30 = synDto != null && synDto.getMinutesHs30Finales() != null ? toLegacyDecimalHours(synDto.getMinutesHs30Finales()) : (synDto != null ? synDto.getHeuresHs30() : 0.0);
        double hs50 = synDto != null && synDto.getMinutesHs50Finales() != null ? toLegacyDecimalHours(synDto.getMinutesHs50Finales()) : (synDto != null ? synDto.getHeuresHs50() : 0.0);
        double hsNettes = synDto != null && synDto.getMinutesHs30Finales() != null
                ? toLegacyDecimalHours(synDto.getMinutesHs30Finales()) + hs50 : hs30 + hs50;
        double hsBrutes = synDto != null ? synDto.getSurplusBrut() : 0.0;
        double retardCompense = synDto != null ? synDto.getRetardCompense() : 0.0;
        double deficitNet = synDto != null && synDto.getMinutesAbsenceFinale() != null
                ? toLegacyDecimalHours(synDto.getMinutesAbsenceFinale()) : (synDto != null ? synDto.getHeuresAbsenceRetenues() : toLegacyDecimalHours(retardsMins));
        double hNuit = synDto != null ? synDto.getHeuresNuitTotal() : totalHeuresNuit;
        double hDim = synDto != null ? synDto.getHeuresDimancheTotal() : totalHeuresDimanche;
        double hFer = synDto != null ? synDto.getHeuresFerieTotal() : totalHeuresFerie;

        int retH = retardsMins / 60;
        int retM = retardsMins % 60;
        String retardCumuleStr = String.format("%02dh%02d", retH, retM);

        int compH = (int) Math.floor(retardCompense);
        int compM = (int) Math.round((retardCompense - compH) * 60);
        String retardCompenseStr = String.format("%02dh%02d", compH, compM);

        int defH = (int) Math.floor(deficitNet);
        int defM = (int) Math.round((deficitNet - defH) * 60);
        String deficitNetStr = String.format("%02dh%02d", defH, defM);

        int hsBrutH = (int) Math.floor(hsBrutes);
        int hsBrutM = (int) Math.round((hsBrutes - hsBrutH) * 60);
        String hsBrutesStr = String.format("%02dh%02d", hsBrutH, hsBrutM);

        int hsNetH = (int) Math.floor(hsNettes);
        int hsNetM = (int) Math.round((hsNettes - hsNetH) * 60);
        String hsNettesStr = String.format("%02dh%02d", hsNetH, hsNetM);

        // Les montants viennent exclusivement du résultat Pointage synthétisé.
        BigDecimal mntRep = synDto != null ? synDto.getMontantRepas() : BigDecimal.ZERO;
        int nbRep = synDto != null ? synDto.getNbJoursRepas() : 0;

        // Déplacement : priorité à synDto si renseigné, sinon prorata basé sur totalTrajetsCount et forfait de référence
        BigDecimal mntDepl = synDto != null ? synDto.getMontantDeplacement() : BigDecimal.ZERO;
        // La synthèse est la source du total de période : elle inclut aussi les forfaits mensuels.
        BigDecimal totalIndemnites = synDto != null && synDto.getMontantsIndemnites() != null
                ? synDto.getMontantsIndemnites().values().stream()
                        .map(PointageEmployeService::zeroIfNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : pointageMap.values().stream()
                        .flatMap(record -> record.indemnites.values().stream())
                        .filter(PointageJourDTO.IndemniteJourDTO::accordee)
                        .map(indemnite -> zeroIfNull(indemnite.montant()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, String> kpis = new LinkedHashMap<>();
        kpis.put("statutEmployePeriode", synDto != null ? "ACTIF" : "INACTIF");
        regles.put("statutEmployePeriode", synDto != null ? "ACTIF" : "INACTIF");
        kpis.put("joursTravailles", ((synDto != null && synDto.getNbJoursPresents() > 0) ? synDto.getNbJoursPresents() : totalJoursTravailles) + " j");
        kpis.put("joursPrevus", joursPrevusStr);
        kpis.put("heuresRealisees", String.format("%02dh%02d", (int) Math.floor(heuresRealisees), (int) Math.round((heuresRealisees - Math.floor(heuresRealisees)) * 60)));
        kpis.put("heuresObjectif", heuresObjectifStr);
        kpis.put("retardCumule", retardCumuleStr);
        kpis.put("retardOccurrences", nbOccurrencesRetard + " occurrences");
        kpis.put("retardCompense", retardCompenseStr);
        kpis.put("retardNet", deficitNetStr);
        kpis.put("heuresDeficit", deficitNetStr);
        kpis.put("heuresSupp", hsNettesStr);
        kpis.put("heuresSuppBrutes", hsBrutesStr);
        kpis.put("heuresSuppNettes", hsNettesStr);
        kpis.put("heuresSuppStatut", (hsNettes > 0) ? "à valider" : (hsBrutes > 0 ? "compensées" : "aucune"));
        kpis.put("absences", String.format("%02d j", synDto != null ? synDto.getNbJoursAbsence() : nbAbsences));
        kpis.put("absencesStatut", nbAbsences > 0 ? "justifiée" : "aucune");
        kpis.put("retardBrutMinutes", String.valueOf(retardsMins));
        kpis.put("retardCompenseMinutes", String.valueOf((int) Math.round(retardCompense * 60)));
        kpis.put("deficitNetMinutes", String.valueOf((int) Math.round(deficitNet * 60)));
        kpis.put("heuresHsNettesMinutes", String.valueOf((int) Math.round(hsNettes * 60)));
        kpis.put("heuresHsBrutesMinutes", String.valueOf((int) Math.round(hsBrutes * 60)));
        ParametreGlobal.ParametreResolution assiduiteRule =
                ParametreGlobal.resolve(parameterStates, "SEUIL_RETARD_ASSIDUITE_MIN");
        if (assiduiteRule.isDisabled()) {
            kpis.put("primeAssiduiteEligible", "DISABLED");
            kpis.put("seuilAssiduiteMinutes", "");
            regles.put("seuilAssiduiteMinutes", "");
        } else {
            if (assiduiteRule.isMissing()) {
                throw new IllegalStateException("Paramètre de pointage absent : SEUIL_RETARD_ASSIDUITE_MIN");
            }
            int seuilAssiduite;
            try {
                seuilAssiduite = Integer.parseInt(assiduiteRule.valeur().trim());
                if (seuilAssiduite < 0) throw new NumberFormatException();
            } catch (RuntimeException e) {
                throw new IllegalStateException("Paramètre de pointage actif invalide : SEUIL_RETARD_ASSIDUITE_MIN", e);
            }
            boolean primeAssiduiteEligible = retardsMins < seuilAssiduite;
            kpis.put("primeAssiduiteEligible", String.valueOf(primeAssiduiteEligible));
            kpis.put("seuilAssiduiteMinutes", String.valueOf(seuilAssiduite));
            regles.put("seuilAssiduiteMinutes", String.valueOf(seuilAssiduite));
        }

        BigDecimal dureeHebdoBd;
        try (Connection legalConnection = dataSource.getConnection()) {
            PaieConfigLegale legal = PaieConfigLegale.findActiveAt(legalConnection,
                    startDate != null ? startDate : LocalDate.now());
            if (legal == null || legal.getDureeHebdoLegale() == null
                    || legal.getDureeHebdoLegale().signum() <= 0) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.LEGAL_CONFIG_MISSING,
                        "Durée hebdomadaire légale absente pour la fiche Pointage.");
            }
            dureeHebdoBd = legal.getDureeHebdoLegale();
        } catch (mg.galaxam.sirh.common.exception.SirhBusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.LEGAL_CONFIG_MISSING,
                    "Impossible de charger la durée hebdomadaire légale de la fiche Pointage.");
        }
        int dureeHebdo = dureeHebdoBd.intValue();
        regles.put("dureeHebdoLegale", String.valueOf(dureeHebdo));
        kpis.put("dureeHebdoLegale", String.valueOf(dureeHebdo));

        ParametreGlobal.ParametreResolution mealRule =
                ParametreGlobal.resolve(parameterStates, "MONTANT_REPAS_MIDI");
        BigDecimal tarifRepasMidi = mealRule.isDisabled() ? BigDecimal.ZERO
                : (mealRule.isMissing() ? null : new BigDecimal(mealRule.valeur()));
        if (tarifRepasMidi == null || tarifRepasMidi.signum() < 0) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.PAYROLL_ALLOWANCE_CONFIGURATION_MISSING,
                    "Configuration d'indemnité absente : MONTANT_REPAS_MIDI.");
        }
        kpis.put("tarifRepas", String.format("%,.0f Ar", tarifRepasMidi).replace(',', ' '));
        int nbPresentsVal = (synDto != null && synDto.getNbJoursPresents() > 0) ? synDto.getNbJoursPresents() : totalJoursTravailles;
        kpis.put("nbJoursPresents", String.valueOf(nbPresentsVal));
        kpis.put("nbJoursConges", String.valueOf(nbCongesVal));
        kpis.put("nbJoursFerieChome", String.valueOf(nbFerieChomeVal));
        kpis.put("nbJoursOuvrables", String.valueOf(totalJoursOuvrables));

        // Détails Repas & Trajets
        kpis.put("totalRepas", (nbRep + total2eRepasCount) + " repas");
        kpis.put("total1erRepas", total1erRepasCount + " std");
        kpis.put("total2eRepas", total2eRepasCount + " deuxièmes repas");
        kpis.put("montantRepasTotal", String.format("%,.0f Ar", mntRep.add(total2eRepasMontant)).replace(',', ' '));
        kpis.put("montantDeplacementTotal", String.format("%,.0f Ar", mntDepl).replace(',', ' '));
        kpis.put("totalIndemnitesMois", String.format("%,.0f Ar", totalIndemnites).replace(',', ' '));
        kpis.put("totalTrajets", String.format("%.0f trajets", totalTrajetsCount * 2));

        // Détails Majorations
        kpis.put("heuresHs30", String.format("%.1fh", hs30));
        kpis.put("heuresHs50", String.format("%.1fh", hs50));
        kpis.put("heuresNuit", String.format("%.1fh", hNuit));
        kpis.put("heuresDimanche", String.format("%.1fh", hDim));
        kpis.put("heuresFerie", String.format("%.1fh", hFer));

        // Récapitulatif Unifié
        int absEcartMins = Math.abs(cumulMinutesEcart);
        int ecartH = absEcartMins / 60;
        int ecartM = absEcartMins % 60;
        String ecartSign = cumulMinutesEcart > 0 ? "+" : (cumulMinutesEcart < 0 ? "-" : "");
        String ecartCumuleStr = String.format("%s%02dh%02d", ecartSign, ecartH, ecartM);

        Map<String, String> recap = new LinkedHashMap<>();
        recap.put("tempsTheorique", heuresObjectifStr);
        recap.put("totalJoursConge", String.valueOf(nbCongesVal));
        recap.put("totalJoursFerieChome", String.valueOf(nbFerieChomeVal));
        recap.put("tempsRealise", kpis.get("heuresRealisees"));
        recap.put("ecartCumule", ecartCumuleStr);
        recap.put("tempsAbsence", String.format("%02dh00", (synDto != null ? synDto.getNbJoursAbsence() : nbAbsences) * 8));
        recap.put("retards", retardCumuleStr);
        recap.put("retardCompense", retardCompenseStr);
        recap.put("retardNet", deficitNetStr);
        recap.put("heuresDeficit", deficitNetStr);
        recap.put("heuresSupp", hsNettesStr);
        recap.put("heuresSuppBrutes", hsBrutesStr);
        recap.put("heuresSuppNettes", hsNettesStr);
        recap.put("hasCompensation", String.valueOf(retardCompense > 0 || hsBrutes > 0));
        recap.put("compensationSolde", (deficitNet > 0) ? ("-" + deficitNetStr) : (hsNettes > 0 ? ("+" + hsNettesStr) : "00h00"));
        recap.put("indemniteRepas", String.format("%d repas (%s)", nbRep + total2eRepasCount, kpis.get("montantRepasTotal")));
        recap.put("indemniteTrajets", String.format("%.0f trajets aller/retour (%s)", totalTrajetsCount * 2, kpis.get("montantDeplacementTotal")));
        recap.put("totalIndemnites", kpis.get("totalIndemnitesMois"));

        // Détails de calcul décomposés pour la Paie
        BigDecimal montantMoyenRepas = total1erRepasCount > 0
                ? totalRepasMontant.divide(BigDecimal.valueOf(total1erRepasCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        recap.put("repasStdDetail", String.format("%d repas, total %,.0f Ar (moyenne %,.0f Ar)",
                total1erRepasCount, totalRepasMontant, montantMoyenRepas).replace(',', ' '));
        if (total2eRepasCount > 0) {
            recap.put("repas2eDetail", String.format("%d deuxièmes repas (normal ou majoré) = %,.0f Ar", total2eRepasCount, total2eRepasMontant).replace(',', ' '));
        }

        BigDecimal tarifJourDeplacementRef = totalTrajetsCount > 0
                ? mntDepl.divide(BigDecimal.valueOf(totalTrajetsCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal tarifTrajetRef = tarifJourDeplacementRef.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        recap.put("deplacementDetail", String.format("%.0f trajets × %,.0f Ar = %,.0f Ar", totalTrajetsCount * 2, tarifTrajetRef, mntDepl).replace(',', ' '));
        recap.put("deplacementFormule", String.format("Prorata %d j ouvrables (%,.0f Ar/j, soit %,.0f Ar/trajet)", totalJoursOuvrables, tarifJourDeplacementRef, tarifTrajetRef).replace(',', ' '));

        recap.put("hs30Detail", String.format("%.1fh majorées à 30%%", hs30));
        recap.put("hs50Detail", String.format("%.1fh majorées à 50%%", hs50));
        if (hNuit > 0) {
            recap.put("nuitDetail", String.format("%.1fh de nuit", hNuit));
        }
        if (hDim > 0) {
            recap.put("dimancheDetail", String.format("%.1fh le dimanche (+40%%)", hDim));
        }
        if (hFer > 0) {
            recap.put("ferieDetail", String.format("%.1fh jour férié (+100%%)", hFer));
        }

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT code, libelle FROM ref_statut_pointage_jour "
                     + "WHERE code IN ('PRESENT', 'DEMI_JOURNEE', 'DIMANCHE_TRAVAILLE', 'SAMEDI_TRAVAILLE', "
                     + "'AUTONOME', 'CONGE', 'ABSENCE_JUSTIFIEE', 'ABSENCE_NON_JUSTIFIEE', 'REPOS') ORDER BY id_statut_jour");
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, String>> statuts = new ArrayList<>();
            while (rs.next()) statuts.add(Map.of("value", rs.getString("code"), "label", rs.getString("libelle")));
            result.setStatutsCorrection(statuts);
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossible de charger les statuts de correction du pointage.", exception);
        }
        result.setSoldePeriode(synDto);
        regles.put("dureeJourNormale", String.valueOf(dureeJourMins / 60.0));
        recap.put("deficitHebdo", synDto == null ? "—" : formatHoursMinutes(synDto.getMinutesDeficitHebdo()));
        recap.put("hs50Hebdo", synDto == null ? "—" : formatHoursMinutes(synDto.getMinutesHs50Hebdo()));
        recap.put("hs50Finales", synDto == null ? "—" : formatHoursMinutes(synDto.getMinutesHs50Finales()));
        recap.put("hsTotalHebdo", synDto == null || synDto.getMinutesHs30Hebdo() == null || synDto.getMinutesHs50Hebdo() == null ? "—" : formatHoursMinutes(Math.addExact(synDto.getMinutesHs30Hebdo(), synDto.getMinutesHs50Hebdo())));
        recap.put("hs30Hebdo", synDto == null ? "—" : formatHoursMinutes(synDto.getMinutesHs30Hebdo()));
        recap.put("ajustements", synDto == null ? "—" : signedHours(synDto.getMinutesAjustementPeriode()));
        recap.put("soldeFinal", synDto == null ? "—" : signedHours(synDto.getMinutesSoldePeriode()));
        recap.put("absenceFinale", synDto == null ? "—" : formatHoursMinutes(synDto.getMinutesAbsenceFinale()));
        recap.put("hs30Finales", synDto == null ? "—" : formatHoursMinutes(synDto.getMinutesHs30Finales()));
        Map<String, BigDecimal> totauxIndemnites = new LinkedHashMap<>();
        list.forEach(jour -> jour.getIndemnites().values().stream()
                .filter(PointageJourDTO.IndemniteJourDTO::accordee)
                .forEach(indemnite -> totauxIndemnites.merge(indemnite.code(), zeroIfNull(indemnite.montant()), BigDecimal::add)));
        totauxIndemnites.replaceAll((k, v) -> v.setScale(0, RoundingMode.HALF_UP));
        result.setTotauxIndemnites(synDto != null && synDto.getMontantsIndemnites() != null
                ? synDto.getMontantsIndemnites() : totauxIndemnites);
        if (synDto != null && resolvedIdPeriode != null
                && startDate.toString().equals(result.getDateDebut())
                && endDate.toString().equals(result.getDateFin())) {
            result.setIndemnitesFixesMensuelles(synDto.getIndemnitesFixesMensuelles());
            result.setIndemnitesFixesEstimees(!Set.of("VALIDEE", "PAIE_VALIDEE", "CLOTUREE")
                    .contains(resolvedStatutPeriode == null ? "" : resolvedStatutPeriode));
        }
        result.setKpis(kpis);
        result.setRecap(recap);
        regles.put("palierHs30", synDto == null ? "—" : formatHoursMinutes(synDto.getMinutesPremierPalierHs()));
        recap.put("regleVentilation", synDto == null || synDto.getMinutesPremierPalierHs() == null
                ? "Palier non historisé pour cette période."
                : "Solde positif : jusqu’à " + formatHoursMinutes(synDto.getMinutesPremierPalierHs())
                    + " à 30 %, puis le reste à 50 %.");
        result.setReglesAppliquees(regles);
        result.setPointagesData(list);
        result.setBilansHebdo(synDto != null ? synDto.getTronconsSemaine() : java.util.Collections.emptyList());

        return result;
    }

    private static String signedHours(Integer minutes) {
        if (minutes == null) return "—";
        return (minutes > 0 ? "+" : minutes < 0 ? "−" : "") + formatHoursMinutes(Math.abs((long) minutes));
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static class PointageRecord {
        String observation;
        int idPointage;
        int idSite;
        int version;
        LocalDate datePointage;
        Time entreeMatin;
        Time sortieMatin;
        Time entreeAm;
        Time sortieAm;
        int totalMinutes;
        int minutesNuit;
        int minutesRetard;
        boolean deplacementMatin;
        boolean deplacementAm;
        boolean indemniteRepasRecue;
        BigDecimal montantRepas = BigDecimal.ZERO;
        String codeSecondRepas;
        BigDecimal montantSecondRepas = BigDecimal.ZERO;
        BigDecimal montantDeplacement = BigDecimal.ZERO;
        String codeStatut;
        boolean horaireContinuJour;
        Map<String, PointageJourDTO.IndemniteJourDTO> indemnites = new LinkedHashMap<>();
    }
}
