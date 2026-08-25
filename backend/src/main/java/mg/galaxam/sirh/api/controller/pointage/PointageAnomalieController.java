package mg.galaxam.sirh.api.controller.pointage;

import mg.galaxam.sirh.api.dto.pointage.PointageAnomalieDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/pointages/anomalies")
public class PointageAnomalieController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AGENT_POINTAGE')")
    public ResponseEntity<Map<String, Object>> getAnomalies(
            @RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) Integer idPeriodePaie,
            @RequestParam(required = false) String datePointage,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            @RequestParam(required = false) String typeAnomalie,
            @RequestParam(required = false) String search
    ) {
        Map<String, Object> response = new HashMap<>();
        List<PointageAnomalieDTO> list = new ArrayList<>();

        int countIncomplets = 0;
        int countRetards = 0;
        int countAbsences = 0;

        try (Connection c = dataSource.getConnection()) {

            // 1. Récupération des informations de la période
            LocalDate dateDebutPeriode = null;
            LocalDate dateFinPeriode = null;
            String periodeCode = "";
            String periodeLibelle = "";
            String statutPeriodeCode = "EN_COURS";
            int activeIdPeriode = (idPeriodePaie != null && idPeriodePaie > 0) ? idPeriodePaie : 0;

            if (activeIdPeriode > 0) {
                String sqlPer = "SELECT p.date_debut, p.date_fin, p.code, p.libelle, st.code AS statut_code " +
                                "FROM periode_paie p " +
                                "LEFT JOIN ref_statut_periode_paie st ON p.id_statut_periode = st.id_statut_periode " +
                                "WHERE p.id_periode_paie = ?";
                try (PreparedStatement psPer = c.prepareStatement(sqlPer)) {
                    psPer.setInt(1, activeIdPeriode);
                    try (ResultSet rsPer = psPer.executeQuery()) {
                        if (rsPer.next()) {
                            dateDebutPeriode = rsPer.getDate("date_debut").toLocalDate();
                            dateFinPeriode = rsPer.getDate("date_fin").toLocalDate();
                            periodeCode = rsPer.getString("code");
                            periodeLibelle = rsPer.getString("libelle");
                            statutPeriodeCode = rsPer.getString("statut_code");
                        }
                    }
                }
            }

            // 2. Anomalies enregistrées dans pointage_jour
            StringBuilder sql = new StringBuilder(
                    "SELECT p.id_pointage, p.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                    "p.id_site, s_site.libelle AS site_nom, p.date_pointage, " +
                    "p.heure_entree_matin, p.heure_sortie_matin, p.heure_entree_am, p.heure_sortie_am, " +
                    "p.total_heures, p.minutes_retard, p.id_statut_jour, st.code AS statut_code, st.libelle AS statut_libelle, " +
                    "p.motif_ajustement, p.indemnite_deplacement_recue, p.indemnite_repas_recue, " +
                    "p.id_periode_paie, per.code AS periode_code, per.libelle AS periode_libelle, per_st.code AS statut_periode_code " +
                    "FROM pointage_jour p " +
                    "JOIN employe e ON p.id_employe = e.id_employe " +
                    "LEFT JOIN ref_site s_site ON p.id_site = s_site.id_site " +
                    "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                    "LEFT JOIN periode_paie per ON p.id_periode_paie = per.id_periode_paie " +
                    "LEFT JOIN ref_statut_periode_paie per_st ON per.id_statut_periode = per_st.id_statut_periode " +
                    "WHERE (st.code IN ('ANOMALIE_SAISIE', 'ABSENCE_NON_JUSTIFIEE') " +
                    "   OR (p.minutes_retard > 0 AND (p.motif_ajustement IS NULL OR TRIM(p.motif_ajustement) = ''))) "
            );

            List<Object> params = new ArrayList<>();
            if (idSite != null && idSite > 0) {
                sql.append("AND p.id_site = ? ");
                params.add(idSite);
            }
            if (activeIdPeriode > 0) {
                sql.append("AND p.id_periode_paie = ? ");
                params.add(activeIdPeriode);
            }
            if (datePointage != null && !datePointage.trim().isEmpty()) {
                sql.append("AND p.date_pointage = ? ");
                params.add(java.sql.Date.valueOf(datePointage.trim()));
            } else {
                if (dateDebut != null && !dateDebut.trim().isEmpty()) {
                    sql.append("AND p.date_pointage >= ? ");
                    params.add(java.sql.Date.valueOf(dateDebut.trim()));
                }
                if (dateFin != null && !dateFin.trim().isEmpty()) {
                    sql.append("AND p.date_pointage <= ? ");
                    params.add(java.sql.Date.valueOf(dateFin.trim()));
                }
            }
            if (search != null && !search.trim().isEmpty()) {
                sql.append("AND (LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.matricule) LIKE ?) ");
                String q = "%" + search.trim().toLowerCase() + "%";
                params.add(q);
                params.add(q);
                params.add(q);
            }
            sql.append("ORDER BY p.date_pointage DESC, e.nom ASC");

            Set<String> alreadyRecordedKeys = new HashSet<>();

            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        PointageAnomalieDTO dto = new PointageAnomalieDTO();
                        dto.setIdPointage(rs.getInt("id_pointage"));
                        dto.setIdEmploye(rs.getInt("id_employe"));
                        dto.setMatricule(rs.getString("matricule"));
                        dto.setNom(rs.getString("nom"));
                        dto.setPrenom(rs.getString("prenom"));
                        dto.setPhotoUrl(rs.getString("photo_url"));
                        dto.setIdSite(rs.getInt("id_site"));
                        dto.setSiteNom(rs.getString("site_nom"));
                        dto.setDatePointage(rs.getDate("date_pointage").toString());

                        Time em = rs.getTime("heure_entree_matin");
                        Time sm = rs.getTime("heure_sortie_matin");
                        Time ea = rs.getTime("heure_entree_am");
                        Time sa = rs.getTime("heure_sortie_am");

                        dto.setHeureEntreeMatin(em != null ? em.toString().substring(0, 5) : "");
                        dto.setHeureSortieMatin(sm != null ? sm.toString().substring(0, 5) : "");
                        dto.setHeureEntreeAm(ea != null ? ea.toString().substring(0, 5) : "");
                        dto.setHeureSortieAm(sa != null ? sa.toString().substring(0, 5) : "");

                        dto.setTotalHeures(rs.getDouble("total_heures"));
                        int retMins = rs.getInt("minutes_retard");
                        dto.setMinutesRetard(retMins);

                        dto.setIdStatutJour(rs.getInt("id_statut_jour"));
                        String stCode = rs.getString("statut_code");
                        dto.setStatutCode(stCode);
                        dto.setStatutLibelle(rs.getString("statut_libelle"));

                        dto.setMotifAjustement(rs.getString("motif_ajustement"));
                        dto.setIndemniteDeplacementRecue(rs.getBoolean("indemnite_deplacement_recue"));
                        dto.setIndemniteRepasRecue(rs.getBoolean("indemnite_repas_recue"));

                        dto.setIdPeriodePaie(rs.getInt("id_periode_paie"));
                        dto.setPeriodeCode(rs.getString("periode_code"));
                        dto.setPeriodeLibelle(rs.getString("periode_libelle"));
                        dto.setStatutPeriodeCode(rs.getString("statut_periode_code"));

                        String type = "INCOMPLET";
                        String desc = "Horaires incomplets (entrée ou sortie manquante)";

                        if ("ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(stCode)) {
                            type = "ABSENCE";
                            desc = "Absence injustifiée (aucun pointage ni congé validé)";
                            countAbsences++;
                        } else if (retMins > 0 && !"ANOMALIE_SAISIE".equalsIgnoreCase(stCode)) {
                            type = "RETARD";
                            desc = "Retard de " + retMins + " minutes non justifié";
                            countRetards++;
                        } else {
                            countIncomplets++;
                        }

                        dto.setTypeAnomalie(type);
                        dto.setDescriptionAnomalie(desc);

                        alreadyRecordedKeys.add(dto.getIdEmploye() + "_" + dto.getDatePointage());

                        boolean matchesType = (typeAnomalie == null || typeAnomalie.isEmpty() || "ALL".equalsIgnoreCase(typeAnomalie) ||
                                               typeAnomalie.equalsIgnoreCase(type) ||
                                               typeAnomalie.equalsIgnoreCase(stCode) ||
                                               ("INCOMPLET".equalsIgnoreCase(typeAnomalie) && "ANOMALIE_SAISIE".equalsIgnoreCase(stCode)) ||
                                               ("ABSENCE".equalsIgnoreCase(typeAnomalie) && "ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(stCode)));

                        if (matchesType) {
                            list.add(dto);
                        }
                    }
                }
            }

            // 3. Détection des Jours Ouvrés Passés Non Saisis (Absences non pointées)
            if (dateDebutPeriode != null && dateFinPeriode != null) {
                LocalDate today = LocalDate.now();
                LocalDate dateLimite = dateFinPeriode.isBefore(today) ? dateFinPeriode : today;

                // Récupération des employés actifs du site avec leur date de début d'affectation
                StringBuilder sqlEmp = new StringBuilder(
                        "SELECT DISTINCT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, c.id_site, s.libelle AS site_nom, c.date_debut AS date_debut_emp " +
                        "FROM employe e " +
                        "JOIN employe_affectation_carriere c ON e.id_employe = c.id_employe AND c.date_fin IS NULL " +
                        "LEFT JOIN ref_fonction f ON c.id_fonction = f.id_fonction " +
                        "LEFT JOIN ref_site s ON c.id_site = s.id_site " +
                        "WHERE e.est_actif = TRUE AND (f.dispense_pointage IS NULL OR f.dispense_pointage = FALSE) "
                );
                List<Object> empParams = new ArrayList<>();
                if (idSite != null && idSite > 0) {
                    sqlEmp.append("AND c.id_site = ? ");
                    empParams.add(idSite);
                }
                if (search != null && !search.trim().isEmpty()) {
                    sqlEmp.append("AND (LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.matricule) LIKE ?) ");
                    String q = "%" + search.trim().toLowerCase() + "%";
                    empParams.add(q);
                    empParams.add(q);
                    empParams.add(q);
                }
                sqlEmp.append("ORDER BY e.nom ASC");

                List<Map<String, Object>> activeEmployees = new ArrayList<>();
                try (PreparedStatement psEmp = c.prepareStatement(sqlEmp.toString())) {
                    for (int i = 0; i < empParams.size(); i++) {
                        psEmp.setObject(i + 1, empParams.get(i));
                    }
                    try (ResultSet rsEmp = psEmp.executeQuery()) {
                        while (rsEmp.next()) {
                            Map<String, Object> em = new HashMap<>();
                            em.put("idEmploye", rsEmp.getInt("id_employe"));
                            em.put("matricule", rsEmp.getString("matricule"));
                            em.put("nom", rsEmp.getString("nom"));
                            em.put("prenom", rsEmp.getString("prenom"));
                            em.put("photoUrl", rsEmp.getString("photo_url"));
                            em.put("idSite", rsEmp.getInt("id_site"));
                            em.put("siteNom", rsEmp.getString("site_nom"));
                            java.sql.Date dDebEmp = rsEmp.getDate("date_debut_emp");
                            em.put("dateDebutEmp", dDebEmp != null ? dDebEmp.toLocalDate() : null);
                            activeEmployees.add(em);
                        }
                    }
                }

                // Récupération de tous les pointages existants pour cette période
                Set<String> allExistingPointages = new HashSet<>();
                String sqlAllPtg = "SELECT id_employe, date_pointage FROM pointage_jour WHERE id_periode_paie = ?";
                try (PreparedStatement psPtg = c.prepareStatement(sqlAllPtg)) {
                    psPtg.setInt(1, activeIdPeriode);
                    try (ResultSet rsPtg = psPtg.executeQuery()) {
                        while (rsPtg.next()) {
                            allExistingPointages.add(rsPtg.getInt("id_employe") + "_" + rsPtg.getDate("date_pointage").toString());
                        }
                    }
                }

                // Récupération des congés validés
                Set<String> approvedLeaves = new HashSet<>();
                String sqlLeaves = "SELECT id_employe, date_debut, date_fin FROM demande_conge WHERE id_statut_demande_conge = (SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code = 'VALIDE' LIMIT 1)";
                try (PreparedStatement psL = c.prepareStatement(sqlLeaves);
                     ResultSet rsL = psL.executeQuery()) {
                    while (rsL.next()) {
                        int empId = rsL.getInt("id_employe");
                        LocalDate lDeb = rsL.getDate("date_debut").toLocalDate();
                        LocalDate lFin = rsL.getDate("date_fin").toLocalDate();
                        for (LocalDate cur = lDeb; !cur.isAfter(lFin); cur = cur.plusDays(1)) {
                            approvedLeaves.add(empId + "_" + cur.toString());
                        }
                    }
                }

                // Parcourir les jours ouvrés passés
                for (LocalDate cur = dateDebutPeriode; !cur.isAfter(dateLimite); cur = cur.plusDays(1)) {
                    DayOfWeek dow = cur.getDayOfWeek();
                    if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;

                    String curStr = cur.toString();

                    for (Map<String, Object> emp : activeEmployees) {
                        int empId = (int) emp.get("idEmploye");
                        LocalDate dateDebutEmp = (LocalDate) emp.get("dateDebutEmp");

                        // Si la date du jour est antérieure à l'arrivée/embauche de l'employé dans l'entreprise, pas d'anomalie
                        if (dateDebutEmp != null && cur.isBefore(dateDebutEmp)) {
                            continue;
                        }

                        String key = empId + "_" + curStr;

                        // Si aucun pointage et aucun congé -> C'est une absence / pointage manquant !
                        if (!allExistingPointages.contains(key) && !approvedLeaves.contains(key)) {
                            countAbsences++;

                            if (typeAnomalie == null || typeAnomalie.isEmpty() || "ALL".equalsIgnoreCase(typeAnomalie) || "ABSENCE".equalsIgnoreCase(typeAnomalie)) {
                                PointageAnomalieDTO dto = new PointageAnomalieDTO();
                                dto.setIdPointage(0); // non encore créé en base
                                dto.setIdEmploye(empId);
                                dto.setMatricule((String) emp.get("matricule"));
                                dto.setNom((String) emp.get("nom"));
                                dto.setPrenom((String) emp.get("prenom"));
                                dto.setPhotoUrl((String) emp.get("photoUrl"));
                                dto.setIdSite((int) emp.get("idSite"));
                                dto.setSiteNom((String) emp.get("siteNom"));
                                dto.setDatePointage(curStr);

                                dto.setHeureEntreeMatin("");
                                dto.setHeureSortieMatin("");
                                dto.setHeureEntreeAm("");
                                dto.setHeureSortieAm("");
                                dto.setTotalHeures(0.0);
                                dto.setMinutesRetard(0);

                                dto.setIdStatutJour(2); // ABSENCE_NON_JUSTIFIEE
                                dto.setStatutCode("ABSENCE_NON_JUSTIFIEE");
                                dto.setStatutLibelle("Non saisi (Absence)");

                                dto.setTypeAnomalie("ABSENCE");
                                dto.setDescriptionAnomalie("Jour ouvré passé sans aucun pointage ni congé enregistré");
                                dto.setMotifAjustement("");

                                dto.setIdPeriodePaie(activeIdPeriode);
                                dto.setPeriodeCode(periodeCode);
                                dto.setPeriodeLibelle(periodeLibelle);
                                dto.setStatutPeriodeCode(statutPeriodeCode);

                                list.add(dto);
                            }
                        }
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }

        // Tri décroissant par date
        list.sort((a, b) -> b.getDatePointage().compareTo(a.getDatePointage()));

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", countIncomplets + countRetards + countAbsences);
        stats.put("incomplets", countIncomplets);
        stats.put("retards", countRetards);
        stats.put("absences", countAbsences);

        response.put("anomalies", list);
        response.put("stats", stats);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{idPointage}/regulariser")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AGENT_POINTAGE')")
    public ResponseEntity<Map<String, Object>> regulariserAnomalie(
            @PathVariable Integer idPointage,
            @RequestBody Map<String, Object> payload
    ) {
        Map<String, Object> resp = new HashMap<>();

        try (Connection c = dataSource.getConnection()) {

            Integer idPeriode = (Integer) payload.get("idPeriodePaie");
            Integer idEmploye = (Integer) payload.get("idEmploye");
            String datePointageStr = (String) payload.get("datePointage");

            // Si idPointage > 0, récupérer id_periode_paie depuis le pointage
            if (idPointage > 0) {
                String sqlCheck = "SELECT p.id_periode_paie, p.id_employe, p.date_pointage, per_st.code AS statut_periode_code " +
                                  "FROM pointage_jour p " +
                                  "LEFT JOIN periode_paie per ON p.id_periode_paie = per.id_periode_paie " +
                                  "LEFT JOIN ref_statut_periode_paie per_st ON per.id_statut_periode = per_st.id_statut_periode " +
                                  "WHERE p.id_pointage = ?";
                try (PreparedStatement psCheck = c.prepareStatement(sqlCheck)) {
                    psCheck.setInt(1, idPointage);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next()) {
                            idPeriode = rs.getInt("id_periode_paie");
                            idEmploye = rs.getInt("id_employe");
                            datePointageStr = rs.getDate("date_pointage").toString();
                            String statutPeriode = rs.getString("statut_periode_code");
                            boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

                            if ("VALIDEE".equals(statutPeriode) || "CLOTUREE".equals(statutPeriode)) {
                                resp.put("success", false);
                                resp.put("message", "La période de paie est verrouillée. Impossible de régulariser.");
                                return ResponseEntity.badRequest().body(resp);
                            }
                            if ("A_VALIDER".equals(statutPeriode) && !isAdmin) {
                                resp.put("success", false);
                                resp.put("message", "Période en cours de validation par la DAF. Seule la DAF peut régulariser.");
                                return ResponseEntity.badRequest().body(resp);
                            }
                        }
                    }
                }
            }

            if (idEmploye == null || datePointageStr == null) {
                resp.put("success", false);
                resp.put("message", "Identifiant employé ou date de pointage manquant.");
                return ResponseEntity.badRequest().body(resp);
            }

            if (idPeriode == null || idPeriode == 0) {
                String sqlFindPer = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
                try (PreparedStatement psFind = c.prepareStatement(sqlFindPer)) {
                    psFind.setDate(1, java.sql.Date.valueOf(datePointageStr));
                    try (ResultSet rsFind = psFind.executeQuery()) {
                        if (rsFind.next()) {
                            idPeriode = rsFind.getInt("id_periode_paie");
                        }
                    }
                }
            }

            // Récupération des données du formulaire
            String emStr = (String) payload.get("heureEntreeMatin");
            String smStr = (String) payload.get("heureSortieMatin");
            String eaStr = (String) payload.get("heureEntreeAm");
            String saStr = (String) payload.get("heureSortieAm");
            String motif = (String) payload.get("motifAjustement");
            String actionType = (String) payload.getOrDefault("actionType", "PRESENCE");

            Time tEm = parseTime(emStr);
            Time tSm = parseTime(smStr);
            Time tEa = parseTime(eaStr);
            Time tSa = parseTime(saStr);

            double hMatin = calcHours(tEm, tSm);
            double hAm = calcHours(tEa, tSa);
            double totalH = hMatin + hAm;

            int retMins = 0;
            if (tEm != null && tEm.toLocalTime().isAfter(LocalTime.of(8, 0))) {
                retMins += (int) Duration.between(LocalTime.of(8, 0), tEm.toLocalTime()).toMinutes();
            }
            if (tEa != null && tEa.toLocalTime().isAfter(LocalTime.of(13, 0))) {
                retMins += (int) Duration.between(LocalTime.of(13, 0), tEa.toLocalTime()).toMinutes();
            }

            String targetStatutCode = "ABSENCE_JUSTIFIEE".equalsIgnoreCase(actionType) ? "ABSENCE_JUSTIFIEE" : "PRESENT";

            // UPSERT pour insérer si nouveau ou mettre à jour si existant
            String sqlUpsert = "INSERT INTO pointage_jour (" +
                               "id_employe, date_pointage, id_periode_paie, id_site, " +
                               "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                               "heures_matin, heures_am, total_heures, minutes_retard, motif_ajustement, id_statut_jour, updated_at" +
                               ") VALUES (?, ?, ?, (SELECT id_site FROM employe_affectation_carriere WHERE id_employe = ? AND date_fin IS NULL LIMIT 1), ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                               "(SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), CURRENT_TIMESTAMP) " +
                               "ON CONFLICT (id_employe, date_pointage) DO UPDATE SET " +
                               "heure_entree_matin = EXCLUDED.heure_entree_matin, " +
                               "heure_sortie_matin = EXCLUDED.heure_sortie_matin, " +
                               "heure_entree_am = EXCLUDED.heure_entree_am, " +
                               "heure_sortie_am = EXCLUDED.heure_sortie_am, " +
                               "heures_matin = EXCLUDED.heures_matin, " +
                               "heures_am = EXCLUDED.heures_am, " +
                               "total_heures = EXCLUDED.total_heures, " +
                               "minutes_retard = EXCLUDED.minutes_retard, " +
                               "motif_ajustement = EXCLUDED.motif_ajustement, " +
                               "id_statut_jour = EXCLUDED.id_statut_jour, " +
                               "updated_at = CURRENT_TIMESTAMP";

            try (PreparedStatement psUpsert = c.prepareStatement(sqlUpsert)) {
                psUpsert.setInt(1, idEmploye);
                psUpsert.setDate(2, Date.valueOf(datePointageStr));
                psUpsert.setObject(3, idPeriode);
                psUpsert.setInt(4, idEmploye);
                psUpsert.setTime(5, tEm);
                psUpsert.setTime(6, tSm);
                psUpsert.setTime(7, tEa);
                psUpsert.setTime(8, tSa);
                psUpsert.setDouble(9, hMatin);
                psUpsert.setDouble(10, hAm);
                psUpsert.setDouble(11, totalH);
                psUpsert.setInt(12, retMins);
                psUpsert.setString(13, motif);
                psUpsert.setString(14, targetStatutCode);

                psUpsert.executeUpdate();
            }

            resp.put("success", true);
            resp.put("message", "Anomalie régularisée avec succès.");
            return ResponseEntity.ok(resp);

        } catch (SQLException e) {
            e.printStackTrace();
            resp.put("success", false);
            resp.put("message", "Erreur lors de la régularisation : " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PutMapping("/regulariser-batch")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AGENT_POINTAGE')")
    public ResponseEntity<Map<String, Object>> regulariserBatch(
            @RequestBody List<Map<String, Object>> items
    ) {
        Map<String, Object> resp = new HashMap<>();
        if (items == null || items.isEmpty()) {
            resp.put("success", true);
            resp.put("count", 0);
            return ResponseEntity.ok(resp);
        }

        int successCount = 0;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);

            String sqlUpsert = "INSERT INTO pointage_jour (" +
                               "id_employe, date_pointage, id_periode_paie, id_site, " +
                               "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                               "heures_matin, heures_am, total_heures, minutes_retard, motif_ajustement, id_statut_jour, updated_at" +
                               ") VALUES (?, ?, " +
                               "COALESCE(?, (SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1)), " +
                               "COALESCE(?, (SELECT id_site FROM employe_affectation_carriere WHERE id_employe = ? AND date_fin IS NULL LIMIT 1), 1), " +
                               "?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                               "(SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), CURRENT_TIMESTAMP) " +
                               "ON CONFLICT (id_employe, date_pointage) DO UPDATE SET " +
                               "heure_entree_matin = EXCLUDED.heure_entree_matin, " +
                               "heure_sortie_matin = EXCLUDED.heure_sortie_matin, " +
                               "heure_entree_am = EXCLUDED.heure_entree_am, " +
                               "heure_sortie_am = EXCLUDED.heure_sortie_am, " +
                               "heures_matin = EXCLUDED.heures_matin, " +
                               "heures_am = EXCLUDED.heures_am, " +
                               "total_heures = EXCLUDED.total_heures, " +
                               "minutes_retard = EXCLUDED.minutes_retard, " +
                               "motif_ajustement = EXCLUDED.motif_ajustement, " +
                               "id_statut_jour = EXCLUDED.id_statut_jour, " +
                               "updated_at = CURRENT_TIMESTAMP";

            try (PreparedStatement psUpsert = c.prepareStatement(sqlUpsert)) {
                for (Map<String, Object> item : items) {
                    Integer idEmploye = (Integer) item.get("idEmploye");
                    String datePointageStr = (String) item.get("datePointage");
                    if (idEmploye == null || datePointageStr == null) continue;

                    Integer idPeriode = (Integer) item.get("idPeriodePaie");
                    Integer idSite = (Integer) item.get("idSite");

                    String emStr = (String) item.get("heureEntreeMatin");
                    String smStr = (String) item.get("heureSortieMatin");
                    String eaStr = (String) item.get("heureEntreeAm");
                    String saStr = (String) item.get("heureSortieAm");
                    String motif = (String) item.get("motifAjustement");
                    String actionType = (String) item.getOrDefault("actionType", "PRESENCE");

                    Time tEm = parseTime(emStr);
                    Time tSm = parseTime(smStr);
                    Time tEa = parseTime(eaStr);
                    Time tSa = parseTime(saStr);

                    double hMatin = calcHours(tEm, tSm);
                    double hAm = calcHours(tEa, tSa);
                    double totalH = hMatin + hAm;

                    int retMins = 0;
                    if (tEm != null && tEm.toLocalTime().isAfter(LocalTime.of(8, 0))) {
                        retMins += (int) Duration.between(LocalTime.of(8, 0), tEm.toLocalTime()).toMinutes();
                    }
                    if (tEa != null && tEa.toLocalTime().isAfter(LocalTime.of(13, 0))) {
                        retMins += (int) Duration.between(LocalTime.of(13, 0), tEa.toLocalTime()).toMinutes();
                    }

                    String targetStatutCode = "ABSENCE_JUSTIFIEE".equalsIgnoreCase(actionType) ? "ABSENCE_JUSTIFIEE" : "PRESENT";

                    psUpsert.setInt(1, idEmploye);
                    psUpsert.setDate(2, Date.valueOf(datePointageStr));
                    if (idPeriode != null && idPeriode > 0) {
                        psUpsert.setInt(3, idPeriode);
                    } else {
                        psUpsert.setNull(3, java.sql.Types.INTEGER);
                    }
                    psUpsert.setDate(4, Date.valueOf(datePointageStr));
                    if (idSite != null && idSite > 0) {
                        psUpsert.setInt(5, idSite);
                    } else {
                        psUpsert.setNull(5, java.sql.Types.INTEGER);
                    }
                    psUpsert.setInt(6, idEmploye);

                    psUpsert.setTime(7, tEm);
                    psUpsert.setTime(8, tSm);
                    psUpsert.setTime(9, tEa);
                    psUpsert.setTime(10, tSa);

                    psUpsert.setDouble(11, hMatin);
                    psUpsert.setDouble(12, hAm);
                    psUpsert.setDouble(13, totalH);
                    psUpsert.setInt(14, retMins);
                    psUpsert.setString(15, motif != null ? motif : "Régularisation");
                    psUpsert.setString(16, targetStatutCode);

                    psUpsert.addBatch();
                    successCount++;
                }

                psUpsert.executeBatch();
            }

            c.commit();
            resp.put("success", true);
            resp.put("count", successCount);
            resp.put("message", successCount + " pointage(s) régularisé(s) avec succès.");
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            e.printStackTrace();
            resp.put("success", false);
            resp.put("message", "Erreur lors du traitement par lot : " + e.getMessage());
            return ResponseEntity.internalServerError().body(resp);
        }
    }

    private Time parseTime(String s) {
        if (s == null || s.trim().isEmpty() || "—".equals(s)) return null;
        try {
            String clean = s.trim();
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
}
