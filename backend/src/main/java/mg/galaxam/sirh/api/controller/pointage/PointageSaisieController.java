package mg.galaxam.sirh.api.controller.pointage;

import mg.galaxam.sirh.api.dto.pointage.PointageGanttDTO;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import org.springframework.http.ResponseEntity;
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
public class PointageSaisieController {

    private final DataSource dataSource;

    public PointageSaisieController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Charge la liste des employés et leurs pointages pour la saisie quotidienne par site.
     */
    @GetMapping("/saisie")
    public ResponseEntity<Map<String, Object>> getGrilleSaisie(
            @RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) String date
    ) {
        Map<String, Object> responseMap = new LinkedHashMap<>();
        List<SaisiePointageJourDTO> list = new ArrayList<>();

        LocalDate targetDate;
        try {
            targetDate = (date != null && !date.isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        } catch (Exception e) {
            targetDate = LocalDate.now();
        }

        LocalTime hDebutMatin = LocalTime.of(8, 0);
        LocalTime hFinMatin = LocalTime.of(12, 0);
        LocalTime hDebutAm = LocalTime.of(13, 0);
        LocalTime hFinAm = LocalTime.of(17, 0);
        boolean horaireContinu = false;

        boolean siteDeplacementActif = true;
        boolean siteRepasActif = false;

        if (idSite != null && idSite > 0) {
            String sqlSite = "SELECT horaire_continu, heure_debut_matin, heure_fin_matin, " +
                    "heure_debut_apres_midi, heure_fin_apres_midi FROM ref_site WHERE id_site = ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sqlSite)) {
                ps.setInt(1, idSite);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        horaireContinu = rs.getBoolean("horaire_continu");
                        if (rs.getTime("heure_debut_matin") != null) hDebutMatin = rs.getTime("heure_debut_matin").toLocalTime();
                        if (rs.getTime("heure_fin_matin") != null) hFinMatin = rs.getTime("heure_fin_matin").toLocalTime();
                        if (rs.getTime("heure_debut_apres_midi") != null) hDebutAm = rs.getTime("heure_debut_apres_midi").toLocalTime();
                        if (rs.getTime("heure_fin_apres_midi") != null) hFinAm = rs.getTime("heure_fin_apres_midi").toLocalTime();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            String sqlConfigSite = "SELECT i.code AS code_indemnite, c.est_actif, c.montant_reference " +
                    "FROM site_indemnite_config c " +
                    "JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite " +
                    "WHERE c.id_site = ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sqlConfigSite)) {
                ps.setInt(1, idSite);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String code = rs.getString("code_indemnite");
                        boolean actif = rs.getBoolean("est_actif");
                        double montant = rs.getDouble("montant_reference");
                        boolean eligible = actif && (montant > 0);

                        if ("DEPLACEMENT".equalsIgnoreCase(code)) siteDeplacementActif = eligible;
                        if ("REPAS".equalsIgnoreCase(code)) siteRepasActif = eligible;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Map<String, Object> siteParams = new LinkedHashMap<>();
        siteParams.put("heureDebutMatin", hDebutMatin.toString().substring(0, 5));
        siteParams.put("heureFinMatin", hFinMatin.toString().substring(0, 5));
        siteParams.put("heureDebutAm", hDebutAm.toString().substring(0, 5));
        siteParams.put("heureFinAm", hFinAm.toString().substring(0, 5));
        siteParams.put("horaireContinu", horaireContinu);
        siteParams.put("siteDeplacementActif", siteDeplacementActif);
        siteParams.put("siteRepasActif", siteRepasActif);
        responseMap.put("siteParams", siteParams);

        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> periode = getPeriodePaie(c, targetDate.toString());
            responseMap.put("periodeInfo", periode);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String sqlEmployes = "SELECT DISTINCT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, c.id_site, f.dispense_pointage " +
                "FROM employe e " +
                "JOIN ( " +
                "    SELECT DISTINCT ON (id_employe) id_employe, id_site, id_fonction, date_debut, date_fin " +
                "    FROM employe_affectation_carriere " +
                "    ORDER BY id_employe, COALESCE(date_fin, '9999-12-31') DESC, date_debut DESC, id_affectation DESC " +
                ") c ON e.id_employe = c.id_employe " +
                "LEFT JOIN ref_fonction f ON c.id_fonction = f.id_fonction " +
                "WHERE e.est_actif = TRUE " +
                (idSite != null && idSite > 0 ? "AND c.id_site = ? " : "") +
                "AND c.date_debut <= ? " +
                "ORDER BY e.nom ASC, e.prenom ASC";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sqlEmployes)) {

            int paramIndex = 1;
            if (idSite != null && idSite > 0) {
                ps.setInt(paramIndex++, idSite);
            }
            ps.setDate(paramIndex++, java.sql.Date.valueOf(targetDate));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                    int empId = rs.getInt("id_employe");
                    int empSiteId = rs.getInt("id_site");

                    dto.setIdEmploye(empId);
                    dto.setMatricule(rs.getString("matricule"));
                    dto.setNom(rs.getString("nom"));
                    dto.setPrenom(rs.getString("prenom"));
                    dto.setPhotoUrl(rs.getString("photo_url"));
                    dto.setIdSite(empSiteId);
                    dto.setDatePointage(targetDate.toString());

                    boolean isAutonome = false;
                    try {
                        isAutonome = rs.getBoolean("dispense_pointage");
                    } catch (Exception ignored) {}
                    dto.setEstAutonome(isAutonome);

                    dto.setSiteIndemniteDeplacementActif(siteDeplacementActif);
                    dto.setSiteIndemniteRepasActif(siteRepasActif);

                    boolean estDerogeDeplacement = isEmployeDeroge(c, empId, "DEPLACEMENT");
                    boolean estDerogeRepas = isEmployeDeroge(c, empId, "REPAS");

                    dto.setEstDerogeDeplacement(estDerogeDeplacement);
                    dto.setEstDerogeRepas(estDerogeRepas);

                    // Vérifier les congés validés sur la date
                    String typeConge = checkCongeEmploye(c, empId, targetDate);
                    dto.setTypeCongeJour(typeConge);

                    // Récupérer le pointage de ce jour s'il existe déjà
                    loadExistingPointage(c, dto, empId, targetDate, siteDeplacementActif, siteRepasActif, estDerogeDeplacement, estDerogeRepas, isAutonome);

                    list.add(dto);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        responseMap.put("grilleData", list);
        return ResponseEntity.ok(responseMap);
    }

    /**
     * Enregistre par lot les pointages de la journée pour le site.
     */
    @PostMapping("/saisie")
    public ResponseEntity<Map<String, Object>> saveGrilleSaisie(@RequestBody List<SaisiePointageJourDTO> pointages) {
        Map<String, Object> resp = new HashMap<>();
        if (pointages == null || pointages.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "Aucune donnée de pointage soumise.");
            return ResponseEntity.badRequest().body(resp);
        }

        int count = 0;
        try (Connection c = dataSource.getConnection()) {
            String dateCible = pointages.get(0).getDatePointage();
            Map<String, Object> periode = getPeriodePaie(c, dateCible);
            if (periode == null) {
                resp.put("success", false);
                resp.put("message", "Aucune période de paie n'est configurée pour cette date. Veuillez d'abord créer une période de paie.");
                return ResponseEntity.badRequest().body(resp);
            }
            String statutPeriode = (String) periode.get("statut_code");

            boolean isAdmin = org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication().getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if ("BROUILLON".equals(statutPeriode)) {
                resp.put("success", false);
                resp.put("message", "La période de paie est en statut Brouillon. Elle doit être ouverte avant de pouvoir saisir les pointages.");
                return ResponseEntity.badRequest().body(resp);
            }
            if ("A_VALIDER".equals(statutPeriode) && !isAdmin) {
                resp.put("success", false);
                resp.put("message", "La saisie est clôturée pour cette période (en cours de validation DAF). Seule la DAF peut encore apporter des modifications.");
                return ResponseEntity.badRequest().body(resp);
            }
            if ("VALIDEE".equals(statutPeriode) || "CLOTUREE".equals(statutPeriode)) {
                resp.put("success", false);
                resp.put("message", "La période de paie est validée et verrouillée. Aucune modification n'est permise (veuillez la rouvrir si une correction est requise).");
                return ResponseEntity.badRequest().body(resp);
            }
            int idPeriodePaie = (Integer) periode.get("id_periode_paie");

            c.setAutoCommit(false);

            String sqlUpsert = "INSERT INTO pointage_jour (" +
                    "id_employe, id_periode_paie, id_site, date_pointage, " +
                    "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                    "id_statut_jour, heures_matin, heures_am, total_heures, minutes_retard, " +
                    "indemnite_deplacement_recue, deplacement_matin, deplacement_am, " +
                    "indemnite_repas_recue, montant_repas, motif_ajustement) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (id_employe, date_pointage) DO UPDATE SET " +
                    "id_periode_paie = EXCLUDED.id_periode_paie, " +
                    "heure_entree_matin = EXCLUDED.heure_entree_matin, " +
                    "heure_sortie_matin = EXCLUDED.heure_sortie_matin, " +
                    "heure_entree_am = EXCLUDED.heure_entree_am, " +
                    "heure_sortie_am = EXCLUDED.heure_sortie_am, " +
                    "id_statut_jour = EXCLUDED.id_statut_jour, " +
                    "total_heures = EXCLUDED.total_heures, " +
                    "minutes_retard = EXCLUDED.minutes_retard, " +
                    "indemnite_deplacement_recue = EXCLUDED.indemnite_deplacement_recue, " +
                    "deplacement_matin = EXCLUDED.deplacement_matin, " +
                    "deplacement_am = EXCLUDED.deplacement_am, " +
                    "indemnite_repas_recue = EXCLUDED.indemnite_repas_recue, " +
                    "montant_repas = EXCLUDED.montant_repas, " +
                    "motif_ajustement = EXCLUDED.motif_ajustement, " +
                    "updated_at = CURRENT_TIMESTAMP";

            // Charger les configurations des sites pour reconstitution de la pause (RG-PTG-01)
            Map<Integer, Boolean> siteContinuMap = new HashMap<>();
            String sqlSites = "SELECT id_site, horaire_continu FROM ref_site";
            try (PreparedStatement psSites = c.prepareStatement(sqlSites);
                 ResultSet rsSites = psSites.executeQuery()) {
                while (rsSites.next()) {
                    siteContinuMap.put(rsSites.getInt("id_site"), rsSites.getBoolean("horaire_continu"));
                }
            }

            // Charger les barèmes repas par site
            Map<Integer, Double> siteRepasBaseMap = new HashMap<>();
            String sqlRepas = "SELECT id_site, montant_reference FROM site_indemnite_config WHERE id_indemnite = 2 AND est_actif = TRUE";
            try (PreparedStatement psRepas = c.prepareStatement(sqlRepas);
                 ResultSet rsRepas = psRepas.executeQuery()) {
                while (rsRepas.next()) {
                    siteRepasBaseMap.put(rsRepas.getInt("id_site"), rsRepas.getDouble("montant_reference"));
                }
            }

            // Charger les statuts de pointage officiels depuis ref_statut_pointage_jour
            Map<String, Integer> statutMap = new HashMap<>();
            String sqlStatuts = "SELECT id_statut_jour, code FROM ref_statut_pointage_jour";
            try (PreparedStatement psSt = c.prepareStatement(sqlStatuts);
                 ResultSet rsSt = psSt.executeQuery()) {
                while (rsSt.next()) {
                    statutMap.put(rsSt.getString("code"), rsSt.getInt("id_statut_jour"));
                }
            }

            try (PreparedStatement ps = c.prepareStatement(sqlUpsert)) {
                for (SaisiePointageJourDTO dto : pointages) {
                    int empSiteId = dto.getIdSite() != null ? dto.getIdSite() : 1;
                    boolean isContinu = siteContinuMap.getOrDefault(empSiteId, false);

                    ps.setInt(1, dto.getIdEmploye());
                    ps.setInt(2, idPeriodePaie);
                    ps.setInt(3, empSiteId);
                    ps.setDate(4, java.sql.Date.valueOf(dto.getDatePointage()));

                    Time tEntreeMatin = parseTime(dto.getHeureEntreeMatin());
                    Time tSortieMatin = parseTime(dto.getHeureSortieMatin());
                    Time tEntreeAm = parseTime(dto.getHeureEntreeAm());
                    Time tSortieAm = parseTime(dto.getHeureSortieAm());

                    // Reconstitution automatique de la pause par défaut si Entrée Matin + Sortie Soir saisis seuls (RG-PTG-01)
                    if (tEntreeMatin != null && tSortieAm != null && tSortieMatin == null && tEntreeAm == null) {
                        tSortieMatin = Time.valueOf("12:00:00");
                        tEntreeAm = Time.valueOf(isContinu ? "13:00:00" : "14:00:00");
                    }

                    ps.setTime(5, tEntreeMatin);
                    ps.setTime(6, tSortieMatin);
                    ps.setTime(7, tEntreeAm);
                    ps.setTime(8, tSortieAm);

                    double hMatin = calcHours(tEntreeMatin, tSortieMatin);
                    double hAm = calcHours(tEntreeAm, tSortieAm);

                    if ("MATIN".equalsIgnoreCase(dto.getTypeCongeJour())) hMatin = 4.0;
                    if ("APRES_MIDI".equalsIgnoreCase(dto.getTypeCongeJour())) hAm = 4.0;
                    if ("FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())) {
                        hMatin = 4.0;
                        hAm = 4.0;
                    }

                    double totalH = dto.isEstAutonome() ? 8.0 : (hMatin + hAm);

                    int retMins = 0;
                    if (!dto.isEstAutonome()) {
                        if (tEntreeMatin != null) {
                            LocalTime startM = tEntreeMatin.toLocalTime();
                            LocalTime normalM = LocalTime.of(8, 0);
                            if (startM.isAfter(normalM)) {
                                retMins += (int) Duration.between(normalM, startM).toMinutes();
                            }
                        }
                        if (tEntreeAm != null) {
                            LocalTime startA = tEntreeAm.toLocalTime();
                            LocalTime normalA = isContinu ? LocalTime.of(13, 0) : LocalTime.of(14, 0);
                            if (startA.isAfter(normalA)) {
                                retMins += (int) Duration.between(normalA, startA).toMinutes();
                            }
                        }
                    }

                    String targetCode = "PRESENT";
                    if ("FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())) {
                        targetCode = "CONGE";
                    } else if (dto.isEstAutonome()) {
                        targetCode = "DISPENSE_POINTAGE";
                    } else if (totalH == 0.0 && tEntreeMatin == null && tSortieMatin == null && tEntreeAm == null && tSortieAm == null) {
                        targetCode = "ABSENCE_NON_JUSTIFIEE";
                    } else if ((tEntreeMatin != null && tSortieMatin == null) ||
                               (tEntreeAm != null && tSortieAm == null) ||
                               (tEntreeMatin == null && tSortieMatin != null) ||
                               (tEntreeAm == null && tSortieAm != null) ||
                               (tEntreeMatin != null && tSortieMatin != null && tEntreeAm == null && tSortieAm == null && !"APRES_MIDI".equalsIgnoreCase(dto.getTypeCongeJour())) ||
                               (tEntreeMatin == null && tSortieMatin == null && tEntreeAm != null && tSortieAm != null && !"MATIN".equalsIgnoreCase(dto.getTypeCongeJour()))) {
                        targetCode = "ANOMALIE_SAISIE";
                    } else {
                        targetCode = "PRESENT";
                    }

                    int idStatut = statutMap.getOrDefault(targetCode, 1);

                    ps.setInt(9, idStatut);
                    ps.setDouble(10, hMatin);
                    ps.setDouble(11, hAm);
                    ps.setDouble(12, totalH);
                    ps.setInt(13, retMins);

                    boolean depRecue = dto.isDeplacementMatin() || dto.isDeplacementAm();
                    ps.setBoolean(14, depRecue);
                    ps.setBoolean(15, dto.isDeplacementMatin());
                    ps.setBoolean(16, dto.isDeplacementAm());

                    double baseRepas = siteRepasBaseMap.getOrDefault(empSiteId, 0.0);
                    double mRepas = 0.0;
                    if (dto.isIndemniteRepasRecue()) {
                        mRepas = baseRepas;
                        if (tSortieAm != null) {
                            LocalTime sAm = tSortieAm.toLocalTime();
                            if (sAm.isAfter(LocalTime.of(21, 0))) {
                                mRepas += 4000.0;
                            } else if (sAm.isAfter(LocalTime.of(19, 30))) {
                                mRepas += 2000.0;
                            }
                        }
                    }

                    ps.setBoolean(17, dto.isIndemniteRepasRecue());
                    ps.setDouble(18, mRepas);
                    ps.setString(19, dto.getMotifAjustement());

                    ps.addBatch();
                    count++;
                }
                ps.executeBatch();
            }

            c.commit();
        } catch (Exception e) {
            e.printStackTrace();
            resp.put("success", false);
            resp.put("message", "Erreur lors de la sauvegarde : " + e.getMessage());
            return ResponseEntity.internalServerError().body(resp);
        }

        resp.put("success", true);
        resp.put("count", count);
        resp.put("message", count + " pointage(s) enregistrés avec succès.");
        return ResponseEntity.ok(resp);
    }

    /**
     * Charge la vue Gantt.
     */
    @GetMapping("/gantt")
    public ResponseEntity<List<PointageGanttDTO>> getGanttView(
            @RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "semaine") String period
    ) {
        List<PointageGanttDTO> result = new ArrayList<>();

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
        } else if ("mois".equalsIgnoreCase(period)) {
            startDate = targetDate.withDayOfMonth(1);
            endDate = targetDate.withDayOfMonth(targetDate.lengthOfMonth());
        } else {
            startDate = targetDate.with(DayOfWeek.MONDAY);
            endDate = targetDate.with(DayOfWeek.SUNDAY);
        }

        String sqlGantt = "SELECT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, f.libelle AS fonction " +
                "FROM employe e " +
                "JOIN employe_affectation_carriere c ON e.id_employe = c.id_employe AND c.date_fin IS NULL " +
                "LEFT JOIN ref_fonction f ON c.id_fonction = f.id_fonction " +
                "WHERE e.est_actif = TRUE " +
                (idSite != null && idSite > 0 ? "AND c.id_site = ? " : "") +
                "ORDER BY e.nom ASC";

        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd/MM");

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sqlGantt)) {

            if (idSite != null && idSite > 0) ps.setInt(1, idSite);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PointageGanttDTO dto = new PointageGanttDTO();
                    int empId = rs.getInt("id_employe");
                    dto.setIdEmploye(empId);
                    dto.setMatricule(rs.getString("matricule"));
                    dto.setNomPrenom(rs.getString("nom") + " " + rs.getString("prenom"));
                    dto.setPhotoUrl(rs.getString("photo_url"));
                    dto.setFonction(rs.getString("fonction"));

                    List<PointageGanttDTO.GanttBlocDTO> blocs = new ArrayList<>();

                    for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                        PointageGanttDTO.GanttBlocDTO bloc = new PointageGanttDTO.GanttBlocDTO();
                        bloc.setDate(d.toString());

                        String dayName = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.FRENCH);
                        if (dayName != null && !dayName.isEmpty()) dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1) + ".";
                        bloc.setLibelleDate(d.format(dayFormatter) + " (" + dayName + ")");

                        boolean isWeekend = (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY);

                        String sqlP = "SELECT p.heure_entree_matin, p.heure_sortie_am, p.total_heures, s.code AS code_statut " +
                                "FROM pointage_jour p JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour " +
                                "WHERE p.id_employe = ? AND p.date_pointage = ?";

                        try (PreparedStatement psP = c.prepareStatement(sqlP)) {
                            psP.setInt(1, empId);
                            psP.setDate(2, java.sql.Date.valueOf(d));
                            try (ResultSet rsP = psP.executeQuery()) {
                                if (rsP.next()) {
                                    Time entree = rsP.getTime("heure_entree_matin");
                                    Time sortie = rsP.getTime("heure_sortie_am");
                                    double totalH = rsP.getDouble("total_heures");
                                    String code = rsP.getString("code_statut");

                                    bloc.setDebut(entree != null ? entree.toString().substring(0, 5) : "08:00");
                                    bloc.setFin(sortie != null ? sortie.toString().substring(0, 5) : "17:00");
                                    bloc.setHeures(String.format("%02dh%02d", (int) totalH, (int) Math.round((totalH - (int) totalH) * 60)));
                                    bloc.setCodeStatut(code);

                                    if ("PRESENT".equalsIgnoreCase(code)) bloc.setVariantColor("success");
                                    else if ("RETARD".equalsIgnoreCase(code)) bloc.setVariantColor("warning");
                                    else if ("CONGE".equalsIgnoreCase(code)) bloc.setVariantColor("secondary");
                                    else bloc.setVariantColor("danger");
                                } else {
                                    bloc.setDebut("—");
                                    bloc.setFin("—");
                                    bloc.setHeures(isWeekend ? "00h00" : "—");
                                    bloc.setCodeStatut(isWeekend ? "REPOS" : "ABSENT");
                                    bloc.setVariantColor("neutral");
                                }
                            }
                        }
                        blocs.add(bloc);
                    }
                    dto.setBlocs(blocs);
                    result.add(dto);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok(result);
    }

    private boolean isEmployeDeroge(Connection c, int idEmploye, String typeIndemnite) {
        String sql = "SELECT COUNT(*) FROM employe_indemnite_derogation d " +
                "JOIN ref_indemnite t ON d.id_indemnite = t.id_indemnite " +
                "WHERE d.id_employe = ? AND UPPER(t.code) = UPPER(?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setString(2, typeIndemnite);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private String checkCongeEmploye(Connection c, int idEmploye, LocalDate date) {
        String sql = "SELECT est_demi_journee AS demi_journee, moment_demi_journee AS moment FROM demande_conge " +
                "WHERE id_employe = ? AND id_statut_demande_conge = 3 AND ? BETWEEN date_debut AND date_fin";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean isDemi = rs.getBoolean("demi_journee");
                    String moment = rs.getString("moment");
                    if (!isDemi) return "FULL_DAY";
                    if ("MATIN".equalsIgnoreCase(moment)) return "MATIN";
                    if ("APRES_MIDI".equalsIgnoreCase(moment)) return "APRES_MIDI";
                    return "FULL_DAY";
                }
            }
        } catch (Exception e) {
            // Table demande_conge non disponible ou pas de congé
            e.printStackTrace();
        }
        return null;
    }

    private void loadExistingPointage(
            Connection c, SaisiePointageJourDTO dto, int idEmploye, LocalDate date,
            boolean siteDeplacementActif, boolean siteRepasActif,
            boolean estDerogeDeplacement, boolean estDerogeRepas,
            boolean isAutonome
    ) {
        String sql = "SELECT p.id_pointage, p.heure_entree_matin, p.heure_sortie_matin, " +
                "p.heure_entree_am, p.heure_sortie_am, p.total_heures, p.minutes_retard, " +
                "p.indemnite_deplacement_recue, p.deplacement_matin, p.deplacement_am, " +
                "p.indemnite_repas_recue, p.motif_ajustement, " +
                "s.code AS code_statut " +
                "FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour " +
                "WHERE p.id_employe = ? AND p.date_pointage = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dto.setIdPointage(rs.getInt("id_pointage"));

                    Time tEntreeM = rs.getTime("heure_entree_matin");
                    Time tSortieM = rs.getTime("heure_sortie_matin");
                    Time tEntreeA = rs.getTime("heure_entree_am");
                    Time tSortieA = rs.getTime("heure_sortie_am");

                    dto.setHeureEntreeMatin(tEntreeM != null ? tEntreeM.toString().substring(0, 5) : "");
                    dto.setHeureSortieMatin(tSortieM != null ? tSortieM.toString().substring(0, 5) : "");
                    dto.setHeureEntreeAm(tEntreeA != null ? tEntreeA.toString().substring(0, 5) : "");
                    dto.setHeureSortieAm(tSortieA != null ? tSortieA.toString().substring(0, 5) : "");

                    dto.setTotalHeures(rs.getDouble("total_heures"));
                    dto.setMinutesRetard(rs.getInt("minutes_retard"));

                    String codeSt = rs.getString("code_statut");
                    dto.setCodeStatut(codeSt);

                    boolean depRecue = rs.getBoolean("indemnite_deplacement_recue");
                    dto.setIndemniteDeplacementRecue(depRecue);
                    dto.setDeplacementMatin(rs.getObject("deplacement_matin") != null ? rs.getBoolean("deplacement_matin") : depRecue);
                    dto.setDeplacementAm(rs.getObject("deplacement_am") != null ? rs.getBoolean("deplacement_am") : depRecue);

                    dto.setIndemniteRepasRecue(rs.getBoolean("indemnite_repas_recue"));
                    dto.setMotifAjustement(rs.getString("motif_ajustement"));
                } else {
                    // Pas encore de pointage enregistré
                    boolean defDep = !estDerogeDeplacement && siteDeplacementActif;
                    dto.setDeplacementMatin(defDep);
                    dto.setDeplacementAm(defDep);
                    dto.setIndemniteDeplacementRecue(defDep);

                    if ("FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())) {
                        dto.setHeureEntreeMatin("—");
                        dto.setHeureSortieMatin("—");
                        dto.setHeureEntreeAm("—");
                        dto.setHeureSortieAm("—");
                        dto.setTotalHeures(0.0);
                        dto.setMinutesRetard(0);
                        dto.setCodeStatut("CONGE");
                        dto.setDeplacementMatin(false);
                        dto.setDeplacementAm(false);
                        dto.setIndemniteDeplacementRecue(false);
                    } else if (isAutonome) {
                        dto.setHeureEntreeMatin("08:00");
                        dto.setHeureSortieMatin("12:00");
                        dto.setHeureEntreeAm("13:00");
                        dto.setHeureSortieAm("17:00");
                        dto.setTotalHeures(8.0);
                        dto.setMinutesRetard(0);
                        dto.setCodeStatut("DISPENSE_POINTAGE");
                    } else {
                        // Employé normal : Laisser les inputs VIDE !
                        dto.setHeureEntreeMatin("");
                        dto.setHeureSortieMatin("");
                        dto.setHeureEntreeAm("");
                        dto.setHeureSortieAm("");
                        dto.setTotalHeures(0.0);
                        dto.setMinutesRetard(0);
                        dto.setCodeStatut("NON_SAISI");
                    }

                    // Cascade Indemnités : site actif ET NON dérogé (Coché par défaut, mais modifiable!)
                    dto.setIndemniteDeplacementRecue(siteDeplacementActif && !estDerogeDeplacement);
                    dto.setIndemniteRepasRecue(siteRepasActif && !estDerogeRepas);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Time parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty() || "—".equals(timeStr)) return null;
        try {
            String clean = timeStr.trim();
            if (clean.length() == 5) clean += ":00";
            return Time.valueOf(clean);
        } catch (Exception e) {
            return null;
        }
    }

    private double calcHours(Time start, Time end) {
        if (start == null || end == null) return 0.0;
        long mins = Duration.between(start.toLocalTime(), end.toLocalTime()).toMinutes();
        return Math.max(0.0, mins / 60.0);
    }

    private Map<String, Object> getPeriodePaie(Connection c, String date) throws SQLException {
        String sql = "SELECT p.id_periode_paie, p.code AS periode_code, p.libelle AS periode_libelle, " +
                     "p.date_debut, p.date_fin, s.code AS statut_code, s.libelle AS statut_libelle " +
                     "FROM periode_paie p " +
                     "JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                     "WHERE ? >= p.date_debut AND ? <= p.date_fin " +
                     "LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            java.sql.Date d = java.sql.Date.valueOf(date);
            ps.setDate(1, d);
            ps.setDate(2, d);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> res = new HashMap<>();
                    res.put("id_periode_paie", rs.getInt("id_periode_paie"));
                    res.put("periode_code", rs.getString("periode_code"));
                    res.put("periode_libelle", rs.getString("periode_libelle"));
                    res.put("date_debut", rs.getDate("date_debut").toString());
                    res.put("date_fin", rs.getDate("date_fin").toString());
                    res.put("statut_code", rs.getString("statut_code"));
                    res.put("statut_libelle", rs.getString("statut_libelle"));
                    return res;
                }
            }
        }
        return null;
    }
}
