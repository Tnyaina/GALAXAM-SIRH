package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.FichePointageEmployeDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageJourDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageTronconSemaineDTO;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import mg.galaxam.sirh.pointage.domain.mapper.PointageTimeBoundaryMapper;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    private final DataSource dataSource;
    private final PointageSyntheseService syntheseService;
    private final CalendrierOuvrableEngine calendrierEngine;

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
                    } else {
                        // Repli sur la période active (OUVERTE / EN_COURS) ou la plus récente si aucune ne contient targetDate
                        String sqlFallback = "SELECT p.id_periode_paie, p.code, p.libelle, p.date_debut, p.date_fin, s.code AS statut_code " +
                                             "FROM periode_paie p " +
                                             "LEFT JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                                             "ORDER BY (s.code IN ('OUVERTE', 'EN_COURS')) DESC, p.date_debut DESC LIMIT 1";
                        try (PreparedStatement psFb = c.prepareStatement(sqlFallback);
                             ResultSet rsFb = psFb.executeQuery()) {
                            if (rsFb.next()) {
                                resolvedIdPeriode = rsFb.getInt("id_periode_paie");
                                resolvedCodePeriode = rsFb.getString("code");
                                resolvedLibellePeriode = rsFb.getString("libelle");
                                resolvedStatutPeriode = rsFb.getString("statut_code");
                                startDate = rsFb.getDate("date_debut").toLocalDate();
                                endDate = rsFb.getDate("date_fin").toLocalDate();
                            }
                        }
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

        result.setIdPeriode(resolvedIdPeriode);
        result.setIdEmploye(idEmploye);
        result.setCodePeriode(resolvedCodePeriode);
        result.setLibellePeriode(resolvedLibellePeriode != null ? resolvedLibellePeriode : ("Période du " + startDate + " au " + endDate));
        result.setDateDebut(startDate.toString());
        result.setDateFin(endDate.toString());
        result.setStatutPeriode(resolvedStatutPeriode);

        // 1. Récupération dynamique des règles du site et des métadonnées collaborateur
        String sqlSite = "SELECT e.matricule, e.nom, e.prenom, " +
                "s.id_site, s.code_site, COALESCE(s.libelle, 'Siège') AS site_nom, " +
                "COALESCE(s.horaire_continu, FALSE) AS site_horaire_continu, " +
                "s.heure_debut_matin, s.heure_fin_matin, " +
                "s.heure_debut_apres_midi, s.heure_fin_apres_midi, t.heure_plafond, " +
                "COALESCE(f.libelle, 'Collaborateur') AS fonction_nom, " +
                "COALESCE(cp.code, '—') AS code_categorie " +
                "FROM employe e " +
                "LEFT JOIN employe_affectation_carriere c ON e.id_employe = c.id_employe " +
                "     AND (c.date_fin IS NULL OR c.date_fin >= CURRENT_DATE) " +
                "LEFT JOIN ref_site s ON c.id_site = s.id_site " +
                "LEFT JOIN ref_fonction f ON c.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_categorie_professionnelle cp ON c.id_categorie_professionnelle = cp.id_categorie_professionnelle " +
                "LEFT JOIN ref_tolerance_arrivee_pointage t ON s.id_site = t.id_site " +
                "WHERE e.id_employe = ? " +
                "ORDER BY c.date_debut DESC LIMIT 1";

        LocalTime hDebutMatin = LocalTime.of(8, 0);
        LocalTime hFinMatin = LocalTime.of(12, 0);
        LocalTime hDebutAm = LocalTime.of(13, 0);
        LocalTime hFinAm = LocalTime.of(17, 0);
        LocalTime hPlafondTolerance = LocalTime.of(7, 30);
        String empMatricule = "—";
        String empNom = null;
        String empPrenom = null;
        String empNomComplet = "—";
        String empSiteNom = "Siège";
        String empFonction = "Collaborateur";
        String empCategorie = "—";

        try (Connection c = dataSource.getConnection();
             PreparedStatement psSite = c.prepareStatement(sqlSite)) {
            psSite.setInt(1, idEmploye);
            try (ResultSet rsSite = psSite.executeQuery()) {
                if (rsSite.next()) {
                    if (rsSite.getString("matricule") != null) empMatricule = rsSite.getString("matricule");
                    empNom = rsSite.getString("nom");
                    empPrenom = rsSite.getString("prenom");
                    empNomComplet = ((empNom != null ? empNom : "") + " " + (empPrenom != null ? empPrenom : "")).trim();
                    if (empNomComplet.isEmpty()) empNomComplet = "Collaborateur";
                    if (rsSite.getString("site_nom") != null) empSiteNom = rsSite.getString("site_nom");
                    if (rsSite.getString("fonction_nom") != null) empFonction = rsSite.getString("fonction_nom");
                    if (rsSite.getString("code_categorie") != null) empCategorie = rsSite.getString("code_categorie");

                    if (rsSite.getTime("heure_debut_matin") != null) hDebutMatin = rsSite.getTime("heure_debut_matin").toLocalTime();
                    if (rsSite.getTime("heure_fin_matin") != null) hFinMatin = rsSite.getTime("heure_fin_matin").toLocalTime();
                    if (rsSite.getTime("heure_debut_apres_midi") != null) hDebutAm = rsSite.getTime("heure_debut_apres_midi").toLocalTime();
                    if (rsSite.getTime("heure_fin_apres_midi") != null) hFinAm = rsSite.getTime("heure_fin_apres_midi").toLocalTime();
                    if (rsSite.getTime("heure_plafond") != null) hPlafondTolerance = rsSite.getTime("heure_plafond").toLocalTime();
                }
            }
        } catch (Exception e) {
            log.warn("[PointageEmployeService] Erreur récupération règles site pour employé {}: {}", idEmploye, e.getMessage());
        }

        result.setMatricule(empMatricule);
        result.setNom(empNom);
        result.setPrenom(empPrenom);
        result.setNomComplet(empNomComplet);
        result.setSiteNom(empSiteNom);
        result.setFonction(empFonction);
        result.setCategorie(empCategorie);

        // Calculs dynamique des règles
        long pauseMins = Duration.between(hFinMatin, hDebutAm).toMinutes();
        long dureeJourMins = Duration.between(hDebutMatin, hFinMatin).toMinutes() + Duration.between(hDebutAm, hFinAm).toMinutes();
        long toleranceMins = Duration.between(hDebutMatin, hPlafondTolerance).toMinutes();
        if (toleranceMins < 0) toleranceMins = 0;

        Map<String, String> regles = new LinkedHashMap<>();
        regles.put("horaireJournalier", String.format("%02dh%02d", dureeJourMins / 60, dureeJourMins % 60));
        regles.put("pause", String.format("%02dh%02d", pauseMins / 60, pauseMins % 60));
        regles.put("debutNormal", hDebutMatin.toString().substring(0, 5));
        regles.put("plafondArrivee", hPlafondTolerance.toString().substring(0, 5));
        regles.put("toleranceRetard", toleranceMins > 0 ? toleranceMins + " min" : "0 min");

        // 1. Récupération des infos salarié (date embauche / fin de contrat robuste & statut autonome en code Java)
        LocalDate dateEmbauche = null;
        LocalDate dateDepart = null;
        boolean isAutonome = false;

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
        String sqlAffs = "SELECT a.date_debut, a.date_fin, " +
                         "COALESCE(f.dispense_pointage, FALSE) AS dispense_pointage " +
                         "FROM employe_affectation_carriere a " +
                         "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                         "WHERE a.id_employe = ? " +
                         "ORDER BY (a.date_fin IS NULL) DESC, a.date_debut DESC";

        try (Connection c = dataSource.getConnection();
             PreparedStatement psA = c.prepareStatement(sqlAffs)) {
            psA.setInt(1, idEmploye);
            try (ResultSet rsA = psA.executeQuery()) {
                boolean first = true;
                while (rsA.next()) {
                    Date dDeb = rsA.getDate("date_debut");
                    boolean disp = rsA.getBoolean("dispense_pointage");

                    if (dateEmbauche == null && dDeb != null) {
                        dateEmbauche = dDeb.toLocalDate();
                    }
                    if (first) {
                        isAutonome = disp;
                        first = false;
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

        // 2. Récupération des pointages enregistrés en base (avec indemnités de repas et déplacement)
        Map<LocalDate, PointageRecord> pointageMap = new HashMap<>();

        String sqlPointage = "SELECT p.id_pointage, p.date_pointage, p.heure_entree_matin, p.heure_sortie_matin, " +
                "p.heure_entree_am, p.heure_sortie_am, p.total_heures, p.heures_nuit, p.minutes_retard, " +
                "p.horaire_continu_jour, p.montant_total_indemnites, s.code AS code_statut, " +
                "pij_repas.accorde AS repas_accorde, pij_repas.montant_calcule AS repas_montant, " +
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

                    r.indemniteRepasRecue = Boolean.TRUE.equals(rs.getObject("repas_accorde"));
                    r.montantRepas = zeroIfNull(rs.getBigDecimal("repas_montant"));
                    r.montantDeplacement = zeroIfNull(rs.getBigDecimal("dep_montant"));
                    r.codeStatut = rs.getString("code_statut");
                    pointageMap.put(r.datePointage, r);
                }
            }
        } catch (Exception e) {
            log.warn("[PointageEmployeService] Erreur récupération pointages pour employé {}: {}", idEmploye, e.getMessage());
        }

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
            boolean isWeekend = CalendrierOuvrableEngine.isWeekend(curr);
            boolean isAvantEmbauche = (dateEmbauche != null && curr.isBefore(dateEmbauche));
            boolean isApresDepart = (dateDepart != null && curr.isAfter(dateDepart));
            boolean isFerie = feriesChomes != null && feriesChomes.contains(curr);

            if (!isWeekend && !isAvantEmbauche && !isApresDepart && !isFerie) {
                totalJoursOuvrables++;
            }

            PointageJourDTO dto = new PointageJourDTO();
            dto.setDate(curr.format(dayFormatter));
            dto.setDateComplete(curr.toString());

            String dayName = curr.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.FRENCH);
            if (dayName != null && !dayName.isEmpty()) {
                dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1) + ".";
            }
            dto.setJour(dayName);

            PointageRecord rec = pointageMap.get(curr);

            if (rec != null) {
                dto.setIdPointage(rec.idPointage);
                dto.setEntree(rec.entreeMatin != null ? rec.entreeMatin.toString().substring(0, 5) : "—");
                dto.setSortiePause(rec.sortieMatin != null ? rec.sortieMatin.toString().substring(0, 5) : "—");
                dto.setRetourPause(rec.entreeAm != null ? rec.entreeAm.toString().substring(0, 5) : "—");
                dto.setSortie(rec.sortieAm != null ? rec.sortieAm.toString().substring(0, 5) : "—");
                dto.setHoraireContinuJour(rec.horaireContinuJour);

                long totalMinutesJour = rec.totalMinutes;
                boolean isWeekendRec = CalendrierOuvrableEngine.isWeekend(curr);

                if ("DISPENSE_POINTAGE".equalsIgnoreCase(rec.codeStatut)) {
                    if (isWeekendRec && totalMinutesJour <= 0) {
                        totalMinutesJour = 0;
                    } else if (!isWeekendRec && totalMinutesJour <= 0) {
                        totalMinutesJour = dureeJourMins;
                    }
                }

                dto.setHeures(PointageTimeBoundaryMapper.formatHoursMinutes(totalMinutesJour));
                dto.setTotalHeuresDecimal(PointageTimeBoundaryMapper.toLegacyDecimalHours(totalMinutesJour));

                totalHeuresNuit += PointageTimeBoundaryMapper.toLegacyDecimalHours(rec.minutesNuit);

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

                int nbRepasJour = aDroitRepas ? 1 : 0;
                BigDecimal mntRepasJour = aDroitRepas ? rec.montantRepas : BigDecimal.ZERO;
                if (aDroitRepas) {
                    total1erRepasCount++;
                    totalRepasMontant = totalRepasMontant.add(mntRepasJour);
                }

                dto.setIndemniteRepas(aDroitRepas);
                dto.setNbRepas(nbRepasJour);
                dto.setMontantRepas(mntRepasJour);

                BigDecimal mntDepJour = (depM || depA) ? rec.montantDeplacement : BigDecimal.ZERO;
                dto.setMontantDeplacement(mntDepJour);

                if ("DIMANCHE_TRAVAILLE".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += PointageTimeBoundaryMapper.toLegacyDecimalHours(totalMinutesJour);
                    totalHeuresDimanche += PointageTimeBoundaryMapper.toLegacyDecimalHours(totalMinutesJour);
                    cumulMinutesEcart += totalMinutesJour;
                    dto.setEcart("+" + dto.getHeures() + " (Maj. 40%)");
                    dto.setEcartPositif(true);
                } else if ("FERIE_TRAVAILLE".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += PointageTimeBoundaryMapper.toLegacyDecimalHours(totalMinutesJour);
                    totalHeuresFerie += PointageTimeBoundaryMapper.toLegacyDecimalHours(totalMinutesJour);
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
                        dto.setEcart("+" + PointageTimeBoundaryMapper.formatHoursMinutes(totalMinutesJour - dureeJourMins));
                        dto.setEcartPositif(true);
                        cumulMinutesEcart += (totalMinutesJour - dureeJourMins);
                    } else if (totalMinutesJour < dureeJourMins && totalMinutesJour > 0) {
                        dto.setEcart("-" + PointageTimeBoundaryMapper.formatHoursMinutes(dureeJourMins - totalMinutesJour));
                        dto.setEcartPositif(false);
                        cumulMinutesEcart += (totalMinutesJour - dureeJourMins);
                    } else {
                        dto.setEcart("00 min");
                        dto.setEcartPositif(true);
                    }
                }

                if ("PRESENT".equalsIgnoreCase(rec.codeStatut) || ("DISPENSE_POINTAGE".equalsIgnoreCase(rec.codeStatut) && !isWeekendRec) || "SAMEDI_TRAVAILLE".equalsIgnoreCase(rec.codeStatut) || "DEMI_JOURNEE".equalsIgnoreCase(rec.codeStatut) || "RETARD".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += PointageTimeBoundaryMapper.toLegacyDecimalHours(totalMinutesJour);
                } else if ("ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(rec.codeStatut)) {
                    nbAbsences++;
                    cumulMinutesEcart -= dureeJourMins;
                }

                if ("DISPENSE_POINTAGE".equalsIgnoreCase(rec.codeStatut) && isWeekendRec && rec.totalMinutes <= 0) {
                    dto.setStatut("REPOS");
                } else {
                    dto.setStatut(rec.codeStatut != null ? rec.codeStatut.toUpperCase() : "PRESENT");
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
                        dto.setHeures(PointageTimeBoundaryMapper.formatHoursMinutes(dureeJourMins));
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
                    dto.setHeures(PointageTimeBoundaryMapper.formatHoursMinutes(dureeJourMins));
                    dto.setTotalHeuresDecimal(PointageTimeBoundaryMapper.toLegacyDecimalHours(dureeJourMins));
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
                    totalHeuresRealisees += PointageTimeBoundaryMapper.toLegacyDecimalHours(dureeJourMins);
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
        Map<String, String> activeParams = Collections.emptyMap();
        try (Connection c = dataSource.getConnection()) {
            Map<String, String> pMap = ParametreGlobal.loadActiveMap(c);
            activeParams = pMap;

            // Récupération des infos carrières de l'employé
            String sqlCarriere = "SELECT e.matricule, e.nom, e.prenom, e.photo_url, " +
                                 "s.id_site, s.code_site AS site_code, s.libelle AS site_nom, " +
                                 "COALESCE(cp.code, '') AS code_categorie, " +
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
                        if (synDto != null && !covers) {
                            continue;
                        }

                        String codeCat = rsCar.getString("code_categorie");
                        boolean estAutonomeEmp = rsCar.getBoolean("est_autonome");
                        boolean isCadre = PointageSyntheseService.isCadreCategorie(codeCat);

                        synDto = syntheseService.calculerSyntheseEmploye(
                                idEmploye,
                                rsCar.getString("matricule"),
                                rsCar.getString("nom"),
                                rsCar.getString("prenom"),
                                rsCar.getString("photo_url"),
                                rsCar.getInt("id_site"),
                                rsCar.getString("site_code"),
                                rsCar.getString("site_nom"),
                                codeCat,
                                isCadre,
                                estAutonomeEmp,
                                startDate,
                                endDate,
                                resolvedIdPeriode,
                                null,
                                resolvedLibellePeriode,
                                resolvedStatutPeriode,
                                totalJoursOuvrables,
                                null,
                                pMap,
                                c
                        );

                        if (covers) {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[PointageEmployeService] Erreur calcul synthèse employe {}: {}", idEmploye, e.getMessage(), e);
        }

        // Calcul unifié de l'objectif d'heures du collaborateur sur la période
        // Source unique de vérité : la somme des seuils de chaque tronçon calculés par WeeklyAttendanceCalculator
        double totalObjectifHeures = 0.0;
        if (synDto != null && synDto.getTronconsSemaine() != null && !synDto.getTronconsSemaine().isEmpty()) {
            totalObjectifHeures = synDto.getTronconsSemaine().stream()
                    .mapToDouble(PointageTronconSemaineDTO::getSeuilHeuresTroncon)
                    .sum();
        }
        int nbCongesVal = synDto != null ? synDto.getNbJoursConge() : 0;
        int nbFerieChomeVal = synDto != null ? synDto.getNbJoursFerieChome() : 0;
        if (totalObjectifHeures <= 0.0) {
            int joursEffectifs = Math.max(0, totalJoursOuvrables - nbCongesVal);
            totalObjectifHeures = joursEffectifs * (dureeJourMins / 60.0);
        }

        int objH = (int) Math.floor(totalObjectifHeures);
        int objM = (int) Math.round((totalObjectifHeures - objH) * 60);
        String heuresObjectifStr = String.format("%02dh%02d", objH, objM);

        int nbJoursAEffectuer = Math.max(0, totalJoursOuvrables - nbCongesVal);
        String joursPrevusStr;
        if (nbCongesVal > 0) {
            joursPrevusStr = String.format("%d prévus (+%d j congé)", nbJoursAEffectuer, nbCongesVal);
        } else {
            joursPrevusStr = String.format("%d prévus", totalJoursOuvrables);
        }

        double heuresRealisees = (synDto != null && synDto.getTotalHeuresTravaillees() > 0) ? synDto.getTotalHeuresTravaillees() : totalHeuresRealisees;
        int retardsMins = synDto != null ? synDto.getTotalMinutesRetard() : totalMinutesRetard;
        double hs30 = synDto != null ? synDto.getHeuresHs30() : 0.0;
        double hs50 = synDto != null ? synDto.getHeuresHs50() : 0.0;
        double hsNettes = hs30 + hs50;
        double hsBrutes = synDto != null ? synDto.getSurplusBrut() : 0.0;
        double retardCompense = synDto != null ? synDto.getRetardCompense() : 0.0;
        double deficitNet = synDto != null ? synDto.getHeuresAbsenceRetenues() : PointageTimeBoundaryMapper.toLegacyDecimalHours(retardsMins);
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
        BigDecimal totalIndemnites = mntRep.add(mntDepl);

        Map<String, String> kpis = new LinkedHashMap<>();
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
        int seuilAssiduite = ParametreGlobal.getInt(activeParams, "SEUIL_RETARD_ASSIDUITE_MIN", 45);
        boolean primeAssiduiteEligible = (retardsMins < seuilAssiduite);
        kpis.put("primeAssiduiteEligible", String.valueOf(primeAssiduiteEligible));
        kpis.put("seuilAssiduiteMinutes", String.valueOf(seuilAssiduite));
        regles.put("seuilAssiduiteMinutes", String.valueOf(seuilAssiduite));

        BigDecimal dureeHebdoBd = ParametreGlobal.getBigDecimal(activeParams, "SEUIL_HEURES_SEMAINE_COMPENSATION", null);
        if (dureeHebdoBd == null) {
            dureeHebdoBd = ParametreGlobal.getBigDecimal(activeParams, "DUREE_HEBDO_LEGALE", new BigDecimal("40"));
        }
        int dureeHebdo = dureeHebdoBd.intValue();
        regles.put("dureeHebdoLegale", String.valueOf(dureeHebdo));
        kpis.put("dureeHebdoLegale", String.valueOf(dureeHebdo));

        BigDecimal tarifRepasMidi = ParametreGlobal.getBigDecimal(activeParams, "MONTANT_REPAS_MIDI", null);
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
        kpis.put("totalRepas", nbRep + " repas");
        kpis.put("total1erRepas", total1erRepasCount + " std");
        kpis.put("total2eRepas", total2eRepasCount > 0 ? total2eRepasCount + " repas x2" : "0");
        kpis.put("montantRepasTotal", String.format("%,.0f Ar", mntRep).replace(',', ' '));
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
        recap.put("indemniteRepas", String.format("%d repas (%s)", nbRep, kpis.get("montantRepasTotal")));
        recap.put("indemniteTrajets", String.format("%.0f trajets aller/retour (%s)", totalTrajetsCount * 2, kpis.get("montantDeplacementTotal")));
        recap.put("totalIndemnites", kpis.get("totalIndemnitesMois"));

        // Détails de calcul décomposés pour la Paie
        BigDecimal montantMoyenRepas = total1erRepasCount > 0
                ? totalRepasMontant.divide(BigDecimal.valueOf(total1erRepasCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        recap.put("repasStdDetail", String.format("%d repas, total %,.0f Ar (moyenne %,.0f Ar)",
                total1erRepasCount, totalRepasMontant, montantMoyenRepas).replace(',', ' '));
        if (total2eRepasCount > 0) {
            recap.put("repas2eDetail", String.format("%d repas (Maj. soir/nuit) = %,.0f Ar", total2eRepasCount, total2eRepasMontant).replace(',', ' '));
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

        result.setKpis(kpis);
        result.setRecap(recap);
        result.setReglesAppliquees(regles);
        result.setPointagesData(list);
        result.setBilansHebdo(synDto != null ? synDto.getTronconsSemaine() : java.util.Collections.emptyList());

        return result;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static class PointageRecord {
        int idPointage;
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
        BigDecimal montantDeplacement = BigDecimal.ZERO;
        String codeStatut;
        boolean horaireContinuJour;
    }
}
