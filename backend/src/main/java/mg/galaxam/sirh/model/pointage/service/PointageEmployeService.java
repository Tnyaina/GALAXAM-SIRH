package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.FichePointageEmployeDTO;
import mg.galaxam.sirh.api.dto.pointage.PointageJourDTO;
import mg.galaxam.sirh.api.dto.pointage.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.model.pointage.engine.PointageCalculateurEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
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
    private final PointageCalculateurEngine calculateurEngine;

    @org.springframework.beans.factory.annotation.Autowired
    public PointageEmployeService(DataSource dataSource,
                                  PointageSyntheseService syntheseService,
                                  CalendrierOuvrableEngine calendrierEngine,
                                  PointageCalculateurEngine calculateurEngine) {
        this.dataSource = dataSource;
        this.syntheseService = syntheseService;
        this.calendrierEngine = calendrierEngine != null ? calendrierEngine : new CalendrierOuvrableEngine();
        this.calculateurEngine = calculateurEngine != null ? calculateurEngine : new PointageCalculateurEngine();
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

        result.setIdPeriode(resolvedIdPeriode);
        result.setLibellePeriode(resolvedLibellePeriode != null ? resolvedLibellePeriode : ("Période du " + startDate + " au " + endDate));
        result.setDateDebut(startDate.toString());
        result.setDateFin(endDate.toString());
        result.setStatutPeriode(resolvedStatutPeriode);

        // 1. Récupération dynamique des règles du site de l'employé
        String sqlSite = "SELECT s.code_site, s.libelle, s.heure_debut_matin, s.heure_fin_matin, " +
                "s.heure_debut_apres_midi, s.heure_fin_apres_midi, t.heure_plafond " +
                "FROM employe_affectation_carriere c " +
                "JOIN ref_site s ON c.id_site = s.id_site " +
                "LEFT JOIN ref_tolerance_arrivee_pointage t ON s.id_site = t.id_site " +
                "WHERE c.id_employe = ? AND c.date_fin IS NULL LIMIT 1";

        LocalTime hDebutMatin = LocalTime.of(8, 0);
        LocalTime hFinMatin = LocalTime.of(12, 0);
        LocalTime hDebutAm = LocalTime.of(13, 0);
        LocalTime hFinAm = LocalTime.of(17, 0);
        LocalTime hPlafondTolerance = LocalTime.of(7, 30);

        try (Connection c = dataSource.getConnection();
             PreparedStatement psSite = c.prepareStatement(sqlSite)) {
            psSite.setInt(1, idEmploye);
            try (ResultSet rsSite = psSite.executeQuery()) {
                if (rsSite.next()) {
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

        // Calculs dynamique des règles
        long pauseMins = Duration.between(hFinMatin, hDebutAm).toMinutes();
        long dureeJourMins = Duration.between(hDebutMatin, hFinMatin).toMinutes() + Duration.between(hDebutAm, hFinAm).toMinutes();

        Map<String, String> regles = new LinkedHashMap<>();
        regles.put("horaireJournalier", String.format("%02dh%02d", dureeJourMins / 60, dureeJourMins % 60));
        regles.put("pause", String.format("%02dh%02d", pauseMins / 60, pauseMins % 60));
        regles.put("debutNormal", hDebutMatin.toString().substring(0, 5));
        regles.put("plafondArrivee", hPlafondTolerance.toString().substring(0, 5));
        regles.put("toleranceRetard", "0 min");

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
                "p.montant_total_indemnites, s.code AS code_statut, " +
                "pij_repas.accorde AS repas_accorde, pij_repas.montant_calcule AS repas_montant, " +
                "pij_dep.accorde AS dep_accorde, pij_dep.quantite AS dep_quantite, pij_dep.fraction AS dep_fraction " +
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
                    r.totalHeures = rs.getDouble("total_heures");
                    r.heuresNuit = rs.getDouble("heures_nuit");
                    r.minutesRetard = rs.getInt("minutes_retard");

                    boolean depAccorde = Boolean.TRUE.equals(rs.getObject("dep_accorde"));
                    r.indemniteDeplacementRecue = depAccorde;
                    double depQte = rs.getDouble("dep_quantite");
                    String depFrac = rs.getString("dep_fraction");
                    if (depAccorde) {
                        if ("MATIN".equalsIgnoreCase(depFrac)) {
                            r.deplacementMatin = true;
                            r.deplacementAm = false;
                        } else if ("APRES_MIDI".equalsIgnoreCase(depFrac)) {
                            r.deplacementMatin = false;
                            r.deplacementAm = true;
                        } else {
                            r.deplacementMatin = depQte > 0;
                            r.deplacementAm = depQte >= 1.0;
                        }
                    } else {
                        r.deplacementMatin = false;
                        r.deplacementAm = false;
                    }

                    r.indemniteRepasRecue = Boolean.TRUE.equals(rs.getObject("repas_accorde"));
                    r.montantRepas = rs.getDouble("repas_montant");
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
        double totalHeuresSupp = 0.0;
        int nbAbsences = 0;

        // Totaux Indemnités & Majorations détaillées
        Map<String, String> params = new HashMap<>();
        try (Connection c = dataSource.getConnection()) {
            params = ParametreGlobal.loadActiveMap(c);
        } catch (Exception e) {
            log.warn("[PointageEmployeService] Erreur chargement paramètres globaux: {}", e.getMessage());
        }

        double tarifRepasDefaut = ParametreGlobal.getDouble(params, "MONTANT_REPAS_MIDI", 2000.0);
        String seuil2eRepas1Str = ParametreGlobal.getString(params, "SEUIL_2ND_REPAS_1", "19:30");
        LocalTime seuil2eRepas1 = LocalTime.parse(seuil2eRepas1Str.length() == 5 ? seuil2eRepas1Str : seuil2eRepas1Str.substring(0, 5));
        double montant2eRepas1 = ParametreGlobal.getDouble(params, "MONTANT_2ND_REPAS_1", 2000.0);
        String seuil2eRepas2Str = ParametreGlobal.getString(params, "SEUIL_2ND_REPAS_2", "21:00");
        LocalTime seuil2eRepas2 = LocalTime.parse(seuil2eRepas2Str.length() == 5 ? seuil2eRepas2Str : seuil2eRepas2Str.substring(0, 5));
        double montant2eRepas2 = ParametreGlobal.getDouble(params, "MONTANT_2ND_REPAS_2", 4000.0);

        int total1erRepasCount = 0;
        int total2eRepasCount = 0;
        double totalRepasMontant = 0.0;
        double total2eRepasMontant = 0.0;
        double totalTrajetsCount = 0.0;

        double totalHeuresNuit = 0.0;
        double totalHeuresDimanche = 0.0;
        double totalHeuresFerie = 0.0;
        double totalHeuresHs30 = 0.0;
        double totalHeuresHs50 = 0.0;

        LocalDate today = LocalDate.now();

        for (LocalDate curr = startDate; !curr.isAfter(endDate); curr = curr.plusDays(1)) {
            boolean isWeekend = (curr.getDayOfWeek() == DayOfWeek.SATURDAY || curr.getDayOfWeek() == DayOfWeek.SUNDAY);
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

                double totalH = rec.totalHeures;
                boolean isWeekendRec = (curr.getDayOfWeek() == DayOfWeek.SATURDAY || curr.getDayOfWeek() == DayOfWeek.SUNDAY);

                if ("DISPENSE_POINTAGE".equalsIgnoreCase(rec.codeStatut)) {
                    if (isWeekendRec && totalH <= 0.0) {
                        totalH = 0.0;
                    } else if (!isWeekendRec && totalH <= 0.0) {
                        totalH = (dureeJourMins / 60.0);
                    }
                }

                int intHours = (int) totalH;
                int intMins = (int) Math.round((totalH - intHours) * 60);
                dto.setHeures(String.format("%02dh%02d", intHours, intMins));

                totalHeuresNuit += rec.heuresNuit;

                // Calcul Indemnités journalières : strictement conditionné à une présence ou travail effectif
                boolean isNonTravaille = "CONGE".equalsIgnoreCase(rec.codeStatut)
                        || "FERIE_CHOME".equalsIgnoreCase(rec.codeStatut)
                        || "ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(rec.codeStatut)
                        || "ABSENCE_JUSTIFIEE".equalsIgnoreCase(rec.codeStatut)
                        || "REPOS".equalsIgnoreCase(rec.codeStatut)
                        || "NON_CONCERNE".equalsIgnoreCase(rec.codeStatut)
                        || (isWeekendRec && totalH <= 0.0);

                boolean estTravailleOuPresent = !isNonTravaille && (totalH > 0.0 || (isAutonome && !isWeekendRec));

                boolean aDroitRepas = estTravailleOuPresent && (rec.indemniteRepasRecue || isAutonome);
                boolean depM = estTravailleOuPresent && (rec.deplacementMatin || isAutonome);
                boolean depA = estTravailleOuPresent && (rec.deplacementAm || isAutonome);

                dto.setDeplacementMatin(depM);
                dto.setDeplacementAm(depA);
                dto.setIndemniteDeplacement(depM || depA);

                if (depM) totalTrajetsCount += 0.5;
                if (depA) totalTrajetsCount += 0.5;

                // Gestion 1er / 2e repas pilotée par les paramètres globaux
                int nbRepasJour = 0;
                double mntRepasJour = 0.0;

                if (aDroitRepas) {
                    LocalTime sa = rec.sortieAm != null ? rec.sortieAm.toLocalTime() : null;
                    mntRepasJour = calculateurEngine.calculerTotalRepasJour(
                            true,
                            tarifRepasDefaut,
                            sa,
                            seuil2eRepas1,
                            montant2eRepas1,
                            seuil2eRepas2,
                            montant2eRepas2
                    );
                    if (sa == null && (rec.heuresNuit >= 4.0 || totalH >= 11.0)) {
                        mntRepasJour += montant2eRepas1;
                    }

                    nbRepasJour = mntRepasJour > tarifRepasDefaut ? 2 : 1;
                    total1erRepasCount++;
                    if (nbRepasJour == 2) {
                        total2eRepasCount++;
                        total2eRepasMontant += (mntRepasJour - tarifRepasDefaut);
                    }
                    totalRepasMontant += mntRepasJour;
                }

                dto.setIndemniteRepas(aDroitRepas);
                dto.setNbRepas(nbRepasJour);
                dto.setMontantRepas(mntRepasJour);

                if ("DIMANCHE_TRAVAILLE".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += totalH;
                    totalHeuresSupp += totalH;
                    totalHeuresDimanche += totalH;
                    dto.setEcart("+" + dto.getHeures() + " (Maj. 40%)");
                    dto.setEcartPositif(true);
                } else if ("FERIE_TRAVAILLE".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += totalH;
                    totalHeuresSupp += totalH;
                    totalHeuresFerie += totalH;
                    dto.setEcart("+" + dto.getHeures() + " (Gratif. 100%)");
                    dto.setEcartPositif(true);
                } else if (rec.minutesRetard > 0 && !isAutonome) {
                    dto.setEcart("-" + rec.minutesRetard + " min");
                    dto.setEcartPositif(false);
                    totalMinutesRetard += rec.minutesRetard;
                    nbOccurrencesRetard++;
                } else if (totalH > (dureeJourMins / 60.0)) {
                    double diff = totalH - (dureeJourMins / 60.0);
                    int dH = (int) diff;
                    int dM = (int) Math.round((diff - dH) * 60);
                    dto.setEcart(String.format("+%02dh%02d", dH, dM));
                    dto.setEcartPositif(true);
                    totalHeuresSupp += diff;

                    if (diff <= 8.0) {
                        totalHeuresHs30 += diff;
                    } else {
                        totalHeuresHs30 += 8.0;
                        totalHeuresHs50 += (diff - 8.0);
                    }
                } else {
                    dto.setEcart("00 min");
                    dto.setEcartPositif(true);
                }

                if ("PRESENT".equalsIgnoreCase(rec.codeStatut) || ("DISPENSE_POINTAGE".equalsIgnoreCase(rec.codeStatut) && !isWeekendRec) || "SAMEDI_TRAVAILLE".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += totalH;
                } else if ("ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(rec.codeStatut)) {
                    nbAbsences++;
                }

                if ("DISPENSE_POINTAGE".equalsIgnoreCase(rec.codeStatut) && isWeekendRec && rec.totalHeures <= 0.0) {
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
                dto.setMontantRepas(0.0);

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
                        double totalH = (dureeJourMins / 60.0);
                        int intHours = (int) totalH;
                        int intMins = (int) Math.round((totalH - intHours) * 60);
                        dto.setHeures(String.format("%02dh%02d", intHours, intMins));
                        dto.setStatut("PLANIFIE");
                        dto.setEcart("—");
                        dto.setEcartPositif(true);
                        dto.setDeplacementMatin(false);
                        dto.setDeplacementAm(false);
                        dto.setIndemniteDeplacement(false);
                        dto.setIndemniteRepas(false);
                        dto.setNbRepas(0);
                        dto.setMontantRepas(0.0);
                    } else {
                        dto.setHeures("—");
                        dto.setStatut("NON_ECHU");
                    }
                } else if (isAutonome) {
                    double totalH = (dureeJourMins / 60.0);
                    int intHours = (int) totalH;
                    int intMins = (int) Math.round((totalH - intHours) * 60);
                    dto.setHeures(String.format("%02dh%02d", intHours, intMins));
                    dto.setStatut("DISPENSE_POINTAGE");
                    dto.setEcart("00 min");
                    dto.setEcartPositif(true);
                    dto.setDeplacementMatin(true);
                    dto.setDeplacementAm(true);
                    dto.setIndemniteDeplacement(true);
                    dto.setIndemniteRepas(true);
                    dto.setNbRepas(1);
                    dto.setMontantRepas(tarifRepasDefaut);
                    total1erRepasCount += 1;
                    totalRepasMontant += tarifRepasDefaut;
                    totalTrajetsCount += 1.0;
                    totalJoursTravailles++;
                    totalHeuresRealisees += totalH;
                } else {
                    dto.setHeures("00h00");
                    dto.setStatut("ABSENT");
                    nbAbsences++;
                }
            }

            list.add(dto);
        }

        // 3. Calcul de la synthèse exacte unifiée via le moteur métier centralisé
        PointageSyntheseMensuelleDTO synDto = null;
        try (Connection c = dataSource.getConnection()) {
            Map<String, String> pMap = ParametreGlobal.loadActiveMap(c);

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
                    boolean foundCovering = false;
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

        double heuresTheoriquesPlein = totalJoursOuvrables * (dureeJourMins / 60.0);
        int hTheoriquesInt = (int) heuresTheoriquesPlein;

        double heuresRealisees = (synDto != null && synDto.getTotalHeuresTravaillees() > 0) ? synDto.getTotalHeuresTravaillees() : totalHeuresRealisees;
        int retardsMins = synDto != null ? synDto.getTotalMinutesRetard() : totalMinutesRetard;
        double hs30 = synDto != null ? synDto.getHeuresHs30() : totalHeuresHs30;
        double hs50 = synDto != null ? synDto.getHeuresHs50() : totalHeuresHs50;
        double hNuit = synDto != null ? synDto.getHeuresNuitTotal() : totalHeuresNuit;
        double hDim = synDto != null ? synDto.getHeuresDimancheTotal() : totalHeuresDimanche;
        double hFer = synDto != null ? synDto.getHeuresFerieTotal() : totalHeuresFerie;

        // Repas : priorité à synDto si renseigné en base, sinon total calculé en mémoire (ex: cadres/autonomes)
        double mntRep = (synDto != null && synDto.getMontantRepas() > 0) ? synDto.getMontantRepas() : totalRepasMontant;
        int nbRep = (synDto != null && synDto.getNbJoursRepas() > 0) ? synDto.getNbJoursRepas() : (total1erRepasCount + total2eRepasCount);

        // Déplacement : priorité à synDto si renseigné, sinon prorata basé sur totalTrajetsCount et forfait de référence
        double mntDepl = (synDto != null && synDto.getMontantDeplacement() > 0) ? synDto.getMontantDeplacement() : 0.0;
        if (mntDepl <= 0.0 && totalTrajetsCount > 0) {
            double tarifJourRef = (totalJoursOuvrables > 0) ? (53200.0 / totalJoursOuvrables) : 2418.0;
            mntDepl = Math.round(totalTrajetsCount * tarifJourRef);
        }
        double totalIndemnites = mntRep + mntDepl;

        Map<String, String> kpis = new LinkedHashMap<>();
        kpis.put("joursTravailles", ((synDto != null && synDto.getNbJoursPresents() > 0) ? synDto.getNbJoursPresents() : totalJoursTravailles) + " j");
        kpis.put("joursPrevus", totalJoursOuvrables + " prévus");
        kpis.put("heuresRealisees", String.format("%02dh%02d", (int) Math.floor(heuresRealisees), (int) Math.round((heuresRealisees - Math.floor(heuresRealisees)) * 60)));
        kpis.put("heuresObjectif", String.format("objectif %02dh00", hTheoriquesInt));
        kpis.put("retardCumule", String.format("%02dh%02d", retardsMins / 60, retardsMins % 60));
        kpis.put("retardOccurrences", nbOccurrencesRetard + " occurrences");
        kpis.put("heuresSupp", String.format("%02dh%02d", (int) Math.floor(hs30 + hs50), (int) Math.round(((hs30 + hs50) - Math.floor(hs30 + hs50)) * 60)));
        kpis.put("heuresSuppStatut", "à valider");
        kpis.put("absences", String.format("%02d j", synDto != null ? synDto.getNbJoursAbsence() : nbAbsences));
        kpis.put("absencesStatut", nbAbsences > 0 ? "justifiée" : "aucune");

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

        // Récapitulatif
        Map<String, String> recap = new LinkedHashMap<>();
        recap.put("tempsTheorique", String.format("%02dh00", hTheoriquesInt));
        recap.put("tempsRealise", kpis.get("heuresRealisees"));
        recap.put("tempsAbsence", String.format("%02dh00", (synDto != null ? synDto.getNbJoursAbsence() : nbAbsences) * 8));
        recap.put("heuresSupp", kpis.get("heuresSupp"));
        recap.put("retards", kpis.get("retardCumule"));
        recap.put("indemniteRepas", String.format("%d repas (%s)", nbRep, kpis.get("montantRepasTotal")));
        recap.put("indemniteTrajets", String.format("%.0f trajets aller/retour (%s)", totalTrajetsCount * 2, kpis.get("montantDeplacementTotal")));
        recap.put("totalIndemnites", kpis.get("totalIndemnitesMois"));

        // Détails de calcul décomposés pour la Paie
        recap.put("repasStdDetail", String.format("%d × %,.0f Ar = %,.0f Ar", total1erRepasCount, tarifRepasDefaut, total1erRepasCount * tarifRepasDefaut).replace(',', ' '));
        if (total2eRepasCount > 0) {
            recap.put("repas2eDetail", String.format("%d repas (Maj. soir/nuit) = %,.0f Ar", total2eRepasCount, total2eRepasMontant).replace(',', ' '));
        }

        double tarifJourDeplacementRef = (totalJoursOuvrables > 0 && totalTrajetsCount > 0) ? (mntDepl / totalTrajetsCount) : (totalJoursOuvrables > 0 ? 53200.0 / totalJoursOuvrables : 2418.0);
        double tarifTrajetRef = tarifJourDeplacementRef / 2.0;
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

        return result;
    }

    private static class PointageRecord {
        int idPointage;
        LocalDate datePointage;
        Time entreeMatin;
        Time sortieMatin;
        Time entreeAm;
        Time sortieAm;
        double totalHeures;
        double heuresNuit;
        int minutesRetard;
        boolean deplacementMatin;
        boolean deplacementAm;
        boolean indemniteDeplacementRecue;
        boolean indemniteRepasRecue;
        double montantRepas;
        String codeStatut;
    }
}
