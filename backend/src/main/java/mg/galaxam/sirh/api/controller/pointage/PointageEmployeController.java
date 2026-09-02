package mg.galaxam.sirh.api.controller.pointage;

import mg.galaxam.sirh.api.dto.pointage.FichePointageEmployeDTO;
import mg.galaxam.sirh.api.dto.pointage.PointageJourDTO;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/pointages")
public class PointageEmployeController {

    private final DataSource dataSource;

    public PointageEmployeController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/employes/{idEmploye}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AGENT_POINTAGE')")
    public ResponseEntity<FichePointageEmployeDTO> getFichePointageEmploye(
            @PathVariable Integer idEmploye,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "periode") String period,
            @RequestParam(required = false) Integer idPeriode
    ) {
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
                e.printStackTrace();
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
                e.printStackTrace();
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
            e.printStackTrace();
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

        // 1. Récupération des infos salarié (date embauche / fin de contrat robuste & statut autonome)
        LocalDate dateEmbauche = null;
        LocalDate dateDepart = null;
        boolean isAutonome = false;

        String sqlEmpInfo = "SELECT " +
                            "  (SELECT MIN(date_debut) FROM contrat_travail WHERE id_employe = ?) AS date_embauche, " +
                            "  (CASE WHEN EXISTS (SELECT 1 FROM contrat_travail WHERE id_employe = ? AND (date_fin IS NULL OR statut = 'ACTIF')) THEN NULL " +
                            "        ELSE (SELECT MAX(date_fin) FROM contrat_travail WHERE id_employe = ?) END) AS date_depart, " +
                            "  (SELECT COALESCE(f.dispense_pointage, FALSE) FROM employe_affectation_carriere a " +
                            "   LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                            "   WHERE a.id_employe = ? AND a.date_fin IS NULL LIMIT 1) AS est_autonome";
        try (Connection c = dataSource.getConnection();
             PreparedStatement psEmp = c.prepareStatement(sqlEmpInfo)) {
            psEmp.setInt(1, idEmploye);
            psEmp.setInt(2, idEmploye);
            psEmp.setInt(3, idEmploye);
            psEmp.setInt(4, idEmploye);
            try (ResultSet rsEmp = psEmp.executeQuery()) {
                if (rsEmp.next()) {
                    if (rsEmp.getDate("date_embauche") != null) dateEmbauche = rsEmp.getDate("date_embauche").toLocalDate();
                    if (rsEmp.getDate("date_depart") != null) dateDepart = rsEmp.getDate("date_depart").toLocalDate();
                    isAutonome = rsEmp.getBoolean("est_autonome");
                }
            }
        } catch (Exception e) {
            // Ignorer si contrat_travail non disponible
        }

        if (dateEmbauche == null) {
            String sqlAff = "SELECT " +
                            "  (SELECT MIN(date_debut) FROM employe_affectation_carriere WHERE id_employe = ?) AS date_embauche, " +
                            "  (CASE WHEN EXISTS (SELECT 1 FROM employe_affectation_carriere WHERE id_employe = ? AND date_fin IS NULL) THEN NULL " +
                            "        ELSE (SELECT MAX(date_fin) FROM employe_affectation_carriere WHERE id_employe = ?) END) AS date_depart";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement psAff = c.prepareStatement(sqlAff)) {
                psAff.setInt(1, idEmploye);
                psAff.setInt(2, idEmploye);
                psAff.setInt(3, idEmploye);
                try (ResultSet rsAff = psAff.executeQuery()) {
                    if (rsAff.next()) {
                        if (rsAff.getDate("date_embauche") != null) dateEmbauche = rsAff.getDate("date_embauche").toLocalDate();
                        if (rsAff.getDate("date_depart") != null) dateDepart = rsAff.getDate("date_depart").toLocalDate();
                    }
                }
            } catch (Exception e) {
                // Ignorer
            }
        }

        // 1.5 Récupération des jours fériés sur la période
        Map<LocalDate, String> ferieMap = new HashMap<>();
        String sqlFeries = "SELECT date_ferie, libelle FROM ref_calendrier_ferie WHERE date_ferie BETWEEN ? AND ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement psF = c.prepareStatement(sqlFeries)) {
            psF.setDate(1, java.sql.Date.valueOf(startDate));
            psF.setDate(2, java.sql.Date.valueOf(endDate));
            try (ResultSet rsF = psF.executeQuery()) {
                while (rsF.next()) {
                    ferieMap.put(rsF.getDate("date_ferie").toLocalDate(), rsF.getString("libelle"));
                }
            }
        } catch (Exception e) {
        }

        // 2. Récupération des pointages enregistrés en base (avec indemnités de repas et déplacement)
        Map<LocalDate, PointageRecord> pointageMap = new HashMap<>();

        String sqlPointage = "SELECT p.id_pointage, p.date_pointage, p.heure_entree_matin, p.heure_sortie_matin, " +
                "p.heure_entree_am, p.heure_sortie_am, p.total_heures, p.heures_nuit, p.minutes_retard, " +
                "p.deplacement_matin, p.deplacement_am, p.indemnite_deplacement_recue, " +
                "p.indemnite_repas_recue, p.montant_repas, s.code AS code_statut " +
                "FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour " +
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
                    r.deplacementMatin = rs.getBoolean("deplacement_matin");
                    r.deplacementAm = rs.getBoolean("deplacement_am");
                    r.indemniteDeplacementRecue = rs.getBoolean("indemnite_deplacement_recue");
                    r.indemniteRepasRecue = rs.getBoolean("indemnite_repas_recue");
                    r.montantRepas = rs.getDouble("montant_repas");
                    r.codeStatut = rs.getString("code_statut");
                    pointageMap.put(r.datePointage, r);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        } catch (Exception ignored) {}

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
            boolean isFerie = ferieMap.containsKey(curr);

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

                // Calcul Indemnités journalières
                boolean aDroitRepas = rec.indemniteRepasRecue || (isAutonome && !isWeekendRec);
                boolean depM = rec.deplacementMatin || (isAutonome && !isWeekendRec);
                boolean depA = rec.deplacementAm || (isAutonome && !isWeekendRec);

                dto.setDeplacementMatin(depM);
                dto.setDeplacementAm(depA);
                dto.setIndemniteDeplacement(depM || depA);

                if (depM) totalTrajetsCount += 0.5;
                if (depA) totalTrajetsCount += 0.5;

                // Gestion 1er / 2e repas pilotée par les paramètres globaux
                int nbRepasJour = 0;
                double mntRepasJour = 0.0;

                if (aDroitRepas) {
                    nbRepasJour = 1;
                    total1erRepasCount++;
                    mntRepasJour = (rec.montantRepas > 0 ? rec.montantRepas : tarifRepasDefaut);

                    LocalTime sa = rec.sortieAm != null ? rec.sortieAm.toLocalTime() : null;
                    if (sa != null) {
                        if (!sa.isBefore(seuil2eRepas2)) {
                            nbRepasJour = 2;
                            total2eRepasCount++;
                            total2eRepasMontant += montant2eRepas2;
                            if (rec.montantRepas <= 0) mntRepasJour += montant2eRepas2;
                        } else if (!sa.isBefore(seuil2eRepas1)) {
                            nbRepasJour = 2;
                            total2eRepasCount++;
                            total2eRepasMontant += montant2eRepas1;
                            if (rec.montantRepas <= 0) mntRepasJour += montant2eRepas1;
                        }
                    } else if (rec.heuresNuit >= 4.0 || totalH >= 11.0) {
                        nbRepasJour = 2;
                        total2eRepasCount++;
                        total2eRepasMontant += montant2eRepas1;
                        if (rec.montantRepas <= 0) mntRepasJour += montant2eRepas1;
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
                    dto.setIndemniteRepas(true);
                    dto.setNbRepas(1);
                    dto.setMontantRepas(tarifRepasDefaut);
                    total1erRepasCount += 1;
                    totalRepasMontant += tarifRepasDefaut;
                    totalTrajetsCount += 1.0;
                    totalJoursTravailles++;
                    totalHeuresRealisees += totalH;
                } else if (curr.isAfter(today)) {
                    dto.setHeures("—");
                    dto.setStatut("NON_ECHU");
                } else {
                    dto.setHeures("00h00");
                    dto.setStatut("ABSENT");
                    nbAbsences++;
                }
            }

            list.add(dto);
        }

        double heuresTheoriquesPlein = totalJoursOuvrables * (dureeJourMins / 60.0);
        int hTheoriquesInt = (int) heuresTheoriquesPlein;

        Map<String, String> kpis = new LinkedHashMap<>();
        kpis.put("joursTravailles", totalJoursTravailles + " j");
        kpis.put("joursPrevus", totalJoursOuvrables + " prévus");
        kpis.put("heuresRealisees", String.format("%02dh%02d", (int) Math.floor(totalHeuresRealisees), (int) Math.round((totalHeuresRealisees - Math.floor(totalHeuresRealisees)) * 60)));
        kpis.put("heuresObjectif", String.format("objectif %02dh00", hTheoriquesInt));
        kpis.put("retardCumule", String.format("%02dh%02d", totalMinutesRetard / 60, totalMinutesRetard % 60));
        kpis.put("retardOccurrences", nbOccurrencesRetard + " occurrences");
        kpis.put("heuresSupp", String.format("%02dh%02d", (int) Math.floor(totalHeuresSupp), (int) Math.round((totalHeuresSupp - Math.floor(totalHeuresSupp)) * 60)));
        kpis.put("heuresSuppStatut", "à valider");
        kpis.put("absences", String.format("%02d j", nbAbsences));
        kpis.put("absencesStatut", nbAbsences > 0 ? "justifiée" : "aucune");

        // Détails Repas & Trajets
        int totalRepasCumul = total1erRepasCount + total2eRepasCount;
        kpis.put("totalRepas", totalRepasCumul + " repas");
        kpis.put("total1erRepas", total1erRepasCount + " std");
        kpis.put("total2eRepas", total2eRepasCount > 0 ? total2eRepasCount + " repas x2" : "0");
        kpis.put("montantRepasTotal", String.format("%,.0f Ar", totalRepasMontant).replace(',', ' '));
        kpis.put("totalTrajets", String.format("%.0f trajets", totalTrajetsCount * 2));

        // Détails Majorations
        kpis.put("heuresHs30", String.format("%.1fh", totalHeuresHs30));
        kpis.put("heuresHs50", String.format("%.1fh", totalHeuresHs50));
        kpis.put("heuresNuit", String.format("%.1fh", totalHeuresNuit));
        kpis.put("heuresDimanche", String.format("%.1fh", totalHeuresDimanche));
        kpis.put("heuresFerie", String.format("%.1fh", totalHeuresFerie));

        // Récapitulatif
        Map<String, String> recap = new LinkedHashMap<>();
        recap.put("tempsTheorique", String.format("%02dh00", hTheoriquesInt));
        recap.put("tempsRealise", kpis.get("heuresRealisees"));
        recap.put("tempsAbsence", String.format("%02dh00", nbAbsences * 8));
        recap.put("heuresSupp", kpis.get("heuresSupp"));
        recap.put("retards", kpis.get("retardCumule"));
        recap.put("indemniteRepas", String.format("%d repas (%s)", totalRepasCumul, kpis.get("montantRepasTotal")));
        recap.put("indemniteTrajets", String.format("%.0f trajets aller/retour", totalTrajetsCount * 2));

        // Détails de calcul décomposés pour la Paie
        recap.put("repasStdDetail", String.format("%d × %,.0f Ar = %,.0f Ar", total1erRepasCount, tarifRepasDefaut, total1erRepasCount * tarifRepasDefaut).replace(',', ' '));
        if (total2eRepasCount > 0) {
            recap.put("repas2eDetail", String.format("%d repas (Maj. soir/nuit) = %,.0f Ar", total2eRepasCount, total2eRepasMontant).replace(',', ' '));
        }
        recap.put("hs30Detail", String.format("%.1fh majorées à 30%%", totalHeuresHs30));
        recap.put("hs50Detail", String.format("%.1fh majorées à 50%%", totalHeuresHs50));
        if (totalHeuresNuit > 0) {
            recap.put("nuitDetail", String.format("%.1fh de nuit", totalHeuresNuit));
        }
        if (totalHeuresDimanche > 0) {
            recap.put("dimancheDetail", String.format("%.1fh le dimanche (+40%%)", totalHeuresDimanche));
        }
        if (totalHeuresFerie > 0) {
            recap.put("ferieDetail", String.format("%.1fh jour férié (+100%%)", totalHeuresFerie));
        }

        result.setKpis(kpis);
        result.setRecap(recap);
        result.setReglesAppliquees(regles);
        result.setPointagesData(list);

        return ResponseEntity.ok(result);
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
