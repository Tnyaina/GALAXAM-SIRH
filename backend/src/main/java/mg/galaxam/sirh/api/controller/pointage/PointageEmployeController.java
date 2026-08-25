package mg.galaxam.sirh.api.controller.pointage;

import mg.galaxam.sirh.api.dto.pointage.FichePointageEmployeDTO;
import mg.galaxam.sirh.api.dto.pointage.PointageJourDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

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
            @RequestParam(required = false, defaultValue = "mois") String period
    ) {
        FichePointageEmployeDTO result = new FichePointageEmployeDTO();

        LocalDate targetDate;
        try {
            targetDate = (date != null && !date.isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        } catch (Exception e) {
            targetDate = LocalDate.now();
        }

        LocalDate startDate;
        LocalDate endDate;

        if ("jour".equalsIgnoreCase(period)) {
            startDate = targetDate;
            endDate = targetDate;
        } else if ("semaine".equalsIgnoreCase(period)) {
            startDate = targetDate.with(DayOfWeek.MONDAY);
            endDate = targetDate.with(DayOfWeek.SUNDAY);
        } else {
            // mois par défaut
            startDate = targetDate.withDayOfMonth(1);
            endDate = targetDate.withDayOfMonth(targetDate.lengthOfMonth());
        }

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

        // 2. Récupération des pointages enregistrés en base
        Map<LocalDate, PointageRecord> pointageMap = new HashMap<>();

        String sqlPointage = "SELECT p.id_pointage, p.date_pointage, p.heure_entree_matin, p.heure_sortie_matin, " +
                "p.heure_entree_am, p.heure_sortie_am, p.total_heures, p.minutes_retard, s.code AS code_statut " +
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
                    r.minutesRetard = rs.getInt("minutes_retard");
                    r.codeStatut = rs.getString("code_statut");
                    pointageMap.put(r.datePointage, r);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2.5 Récupération des congés validés sur la période
        Map<LocalDate, String> congeMap = new HashMap<>();
        String sqlConges = "SELECT date_debut, date_fin, est_demi_journee, moment_demi_journee FROM demande_conge " +
                "WHERE id_employe = ? AND id_statut_demande_conge = 3 AND date_debut <= ? AND date_fin >= ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement psC = c.prepareStatement(sqlConges)) {
            psC.setInt(1, idEmploye);
            psC.setDate(2, java.sql.Date.valueOf(endDate));
            psC.setDate(3, java.sql.Date.valueOf(startDate));
            try (ResultSet rsC = psC.executeQuery()) {
                while (rsC.next()) {
                    LocalDate dDebut = rsC.getDate("date_debut").toLocalDate();
                    LocalDate dFin = rsC.getDate("date_fin").toLocalDate();
                    boolean isDemi = rsC.getBoolean("est_demi_journee");
                    String moment = rsC.getString("moment_demi_journee");
                    
                    String type = "FULL_DAY";
                    if (isDemi) {
                        if ("MATIN".equalsIgnoreCase(moment)) type = "MATIN";
                        else if ("APRES_MIDI".equalsIgnoreCase(moment)) type = "APRES_MIDI";
                    }
                    
                    for (LocalDate d = dDebut; !d.isAfter(dFin); d = d.plusDays(1)) {
                        if (!d.isBefore(startDate) && !d.isAfter(endDate)) {
                            congeMap.put(d, type);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Table non disponible
        }

        // 3. Expansion dynamique de tous les jours du calendrier de la période
        List<PointageJourDTO> list = new ArrayList<>();
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd/MM");

        int totalJoursOuvrables = 0;
        int totalJoursTravailles = 0;
        double totalHeuresRealisees = 0.0;
        int totalMinutesRetard = 0;
        int nbOccurrencesRetard = 0;
        double totalHeuresSupp = 0.0;
        int nbAbsences = 0;

        LocalDate today = LocalDate.now();

        for (LocalDate curr = startDate; !curr.isAfter(endDate); curr = curr.plusDays(1)) {
            boolean isWeekend = (curr.getDayOfWeek() == DayOfWeek.SATURDAY || curr.getDayOfWeek() == DayOfWeek.SUNDAY);
            if (!isWeekend) {
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
                int intHours = (int) totalH;
                int intMins = (int) Math.round((totalH - intHours) * 60);
                dto.setHeures(String.format("%02dh%02d", intHours, intMins));

                if ("DIMANCHE_TRAVAILLE".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += totalH;
                    totalHeuresSupp += totalH;
                    dto.setEcart("+" + dto.getHeures() + " (HS 100%)");
                    dto.setEcartPositif(true);
                } else if ("FERIE_TRAVAILLE".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += totalH;
                    totalHeuresSupp += totalH;
                    dto.setEcart("+" + dto.getHeures() + " (Gratif. 100%)");
                    dto.setEcartPositif(true);
                } else if (rec.minutesRetard > 0) {
                    dto.setEcart("-" + rec.minutesRetard + " min");
                    dto.setEcartPositif(false);
                    totalMinutesRetard += rec.minutesRetard;
                    nbOccurrencesRetard++;
                } else if (totalH > (dureeJourMins / 60.0)) {
                    double diff = totalH - (dureeJourMins / 60.0);
                    int diffMins = (int) Math.round(diff * 60);
                    dto.setEcart("+" + String.format("%02d min", diffMins));
                    dto.setEcartPositif(true);
                    totalHeuresSupp += diff;
                } else {
                    dto.setEcart("00 min");
                    dto.setEcartPositif(true);
                }

                if ("PRESENT".equalsIgnoreCase(rec.codeStatut) || "ANOMALIE_SAISIE".equalsIgnoreCase(rec.codeStatut) || "SAMEDI_TRAVAILLE".equalsIgnoreCase(rec.codeStatut)) {
                    totalJoursTravailles++;
                    totalHeuresRealisees += totalH;
                } else if ("ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(rec.codeStatut)) {
                    nbAbsences++;
                }

                dto.setStatut(rec.codeStatut != null ? rec.codeStatut.toUpperCase() : "PRESENT");
            } else {
                // Pas de pointage enregistré pour cette date
                dto.setEntree("—");
                dto.setSortiePause("—");
                dto.setRetourPause("—");
                dto.setSortie("—");
                dto.setEcart("—");

                String typeConge = congeMap.get(curr);

                if (typeConge != null && "FULL_DAY".equals(typeConge)) {
                    dto.setHeures("00h00");
                    dto.setStatut("CONGE");
                } else if (isWeekend) {
                    dto.setHeures("00h00");
                    dto.setStatut("REPOS");
                } else if (curr.isAfter(today)) {
                    dto.setHeures("—");
                    dto.setStatut("NON_ECHU");
                } else {
                    // Jour ouvré passé sans pointage
                    dto.setHeures("00h00");
                    dto.setStatut("ABSENT");
                    nbAbsences++;
                }
            }

            list.add(dto);
        }

        // Objectifs et temps théoriques calculés dynamiquement selon les jours ouvrables réels
        double heuresTheoriquesPlein = totalJoursOuvrables * (dureeJourMins / 60.0);
        int hTheoriquesInt = (int) heuresTheoriquesPlein;

        // KPIs
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

        // Récapitulatif
        Map<String, String> recap = new LinkedHashMap<>();
        recap.put("tempsTheorique", String.format("%02dh00", hTheoriquesInt));
        recap.put("tempsRealise", kpis.get("heuresRealisees"));
        recap.put("tempsAbsence", String.format("%02dh00", nbAbsences * 8));
        recap.put("heuresSupp", kpis.get("heuresSupp"));
        recap.put("retards", kpis.get("retardCumule"));

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
        int minutesRetard;
        String codeStatut;
    }
}
