package mg.galaxam.sirh.contrat.api;


import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import jakarta.servlet.http.HttpServletResponse;
import mg.galaxam.sirh.contrat.api.dto.ContratTravailGlobalDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contrats")
public class ContratGlobalController {

    // Un même type de contrat avec la même date de début pour un salarié est à vérifier.
    // La règle est identique pour le filtre, le badge, les statistiques et les alertes.
    private static final String DOUBLON_POTENTIEL =
            "COALESCE(rsc.code, '') <> 'ANNULE' AND EXISTS (" +
            "SELECT 1 FROM contrat_travail dup JOIN ref_statut_rh sdup ON sdup.id_statut_rh = dup.id_statut_rh " +
            "WHERE dup.id_employe = ct.id_employe AND dup.id_contrat <> ct.id_contrat " +
            "AND dup.id_type_contrat = ct.id_type_contrat AND dup.date_debut = ct.date_debut " +
            "AND sdup.code <> 'ANNULE')";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private mg.galaxam.sirh.contrat.application.CarriereExportService carriereExportService;

    @GetMapping("/filtres")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getFiltresContrats() throws Exception {
        List<Map<String, String>> statuts = new ArrayList<>();
        statuts.add(Map.of("code", "ALL", "libelle", "Tous les états"));
        statuts.add(Map.of("code", "ACTIF", "libelle", "Actifs"));
        statuts.add(Map.of("code", "PENDING", "libelle", "À préparer / effet futur"));
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT DISTINCT r.code, r.libelle FROM contrat_travail ct " +
                "JOIN ref_statut_rh r ON r.id_statut_rh = ct.id_statut_rh " +
                "WHERE r.code <> 'ACTIF' ORDER BY r.libelle");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) statuts.add(Map.of("code", rs.getString(1), "libelle", rs.getString(2)));
        }
        List<Map<String, String>> echeances = List.of(
                Map.of("code", "ALL", "libelle", "Toutes les échéances"),
                Map.of("code", "7J", "libelle", "Moins de 7 jours"),
                Map.of("code", "30J", "libelle", "Moins de 30 jours"),
                Map.of("code", "60J", "libelle", "Moins de 60 jours"),
                Map.of("code", "ESSAI", "libelle", "Période d'essai en cours"));
        return ResponseEntity.ok(Map.of("statuts", statuts, "echeances", echeances));
    }

    /** Recherche paginée par salarié, avec les affectations propres à chaque contrat. */
    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getContratsGlobal(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer idEmploye,
            @RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) Integer idTypeContrat,
            @RequestParam(required = false) String echeance,
            @RequestParam(required = false, defaultValue = "false") boolean doublonsOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {

        try (Connection c = dataSource.getConnection()) {
            StringBuilder whereSql = new StringBuilder(" WHERE 1=1 ");
            List<Object> params = new ArrayList<>();
            LocalDate today = LocalDate.now();

            if (idEmploye != null) {
                whereSql.append(" AND ct.id_employe = ? ");
                params.add(idEmploye);
            }

            if (q != null && !q.isBlank()) {
                String searchLike = "%" + q.trim().toUpperCase() + "%";
                whereSql.append(
                        " AND (UPPER(e.matricule) LIKE ? OR UPPER(e.nom) LIKE ? OR UPPER(e.prenom) LIKE ? OR UPPER(ct.numero_contrat) LIKE ?) ");
                params.add(searchLike);
                params.add(searchLike);
                params.add(searchLike);
                params.add(searchLike);
            }

            if (idSite != null && idSite > 0) {
                whereSql.append(" AND eac.id_site = ? ");
                params.add(idSite);
            }

            if (statut != null && !statut.isBlank() && !"ALL".equalsIgnoreCase(statut)) {
                if ("PENDING".equalsIgnoreCase(statut)) {
                    whereSql.append(
                            " AND (rsc.code = 'BROUILLON' OR rsc.code = 'A_SIGNER' OR (rsc.code = 'ACTIF' AND ct.date_debut > CURRENT_DATE)) ");
                } else if ("ACTIF".equalsIgnoreCase(statut)) {
                    whereSql.append(" AND rsc.code = 'ACTIF' AND (ct.date_debut <= CURRENT_DATE) ");
                } else {
                    whereSql.append(" AND rsc.code = ? ");
                    params.add(statut.trim().toUpperCase());
                }
            }

            if (idTypeContrat != null && idTypeContrat > 0) {
                whereSql.append(" AND ct.id_type_contrat = ? ");
                params.add(idTypeContrat);
            }

            if ("60J".equalsIgnoreCase(echeance)) {
                whereSql.append(
                        " AND ((ct.date_fin IS NOT NULL AND ct.date_fin >= ? AND ct.date_fin <= ?) OR (ct.date_fin_essai IS NOT NULL AND ct.date_fin_essai >= ? AND ct.date_fin_essai <= ?)) ");
                params.add(Date.valueOf(today));
                params.add(Date.valueOf(today.plusDays(60)));
                params.add(Date.valueOf(today));
                params.add(Date.valueOf(today.plusDays(60)));
            } else if ("30J".equalsIgnoreCase(echeance)) {
                whereSql.append(
                        " AND ((ct.date_fin IS NOT NULL AND ct.date_fin >= ? AND ct.date_fin <= ?) OR (ct.date_fin_essai IS NOT NULL AND ct.date_fin_essai >= ? AND ct.date_fin_essai <= ?)) ");
                params.add(Date.valueOf(today));
                params.add(Date.valueOf(today.plusDays(30)));
                params.add(Date.valueOf(today));
                params.add(Date.valueOf(today.plusDays(30)));
            } else if ("7J".equalsIgnoreCase(echeance)) {
                whereSql.append(
                        " AND ((ct.date_fin IS NOT NULL AND ct.date_fin >= ? AND ct.date_fin <= ?) OR (ct.date_fin_essai IS NOT NULL AND ct.date_fin_essai >= ? AND ct.date_fin_essai <= ?)) ");
                params.add(Date.valueOf(today));
                params.add(Date.valueOf(today.plusDays(7)));
                params.add(Date.valueOf(today));
                params.add(Date.valueOf(today.plusDays(7)));
            } else if ("ESSAI".equalsIgnoreCase(echeance)) {
                whereSql.append(
                        " AND ct.date_fin_essai IS NOT NULL AND ct.date_fin_essai >= ? AND ct.date_fin_essai <= ? ");
                params.add(Date.valueOf(today));
                params.add(Date.valueOf(today.plusDays(60)));
            }

            if (doublonsOnly) {
                whereSql.append(" AND (").append(DOUBLON_POTENTIEL).append(") ");
            }

            String fromSql = " FROM contrat_travail ct " +
                    "JOIN employe e ON ct.id_employe = e.id_employe " +
                    "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                    "LEFT JOIN LATERAL (" +
                    "  SELECT id_site FROM employe_affectation_carriere " +
                    "  WHERE id_contrat = ct.id_contrat ORDER BY date_debut DESC, id_affectation DESC LIMIT 1" +
                    ") eac ON TRUE " + whereSql;
            String countSql = "SELECT COUNT(DISTINCT ct.id_employe), COUNT(*)" + fromSql;

            long totalElements = 0;
            long totalContrats = 0;
            try (PreparedStatement psCount = c.prepareStatement(countSql)) {
                for (int i = 0; i < params.size(); i++) {
                    psCount.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = psCount.executeQuery()) {
                    if (rs.next()) {
                        totalElements = rs.getLong(1);
                        totalContrats = rs.getLong(2);
                    }
                }
            }

            int safePage = Math.max(1, page);
            int safeSize = Math.max(1, size);
            int offset = (safePage - 1) * safeSize;
            List<Integer> employeeIds = new ArrayList<>();
            String employeesSql = "SELECT ct.id_employe" + fromSql +
                    " GROUP BY ct.id_employe ORDER BY MAX(ct.date_debut) DESC NULLS LAST, MAX(ct.id_contrat) DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = c.prepareStatement(employeesSql)) {
                int p = 1;
                for (Object param : params) ps.setObject(p++, param);
                ps.setInt(p++, safeSize);
                ps.setInt(p, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) employeeIds.add(rs.getInt(1));
                }
            }
            if (employeeIds.isEmpty()) {
                return ResponseEntity.ok(pageContrats(List.of(), totalElements, totalContrats, safePage, safeSize));
            }
            String placeholders = String.join(",", java.util.Collections.nCopies(employeeIds.size(), "?"));
            String querySql = "SELECT ct.id_contrat, ct.id_employe, ct.numero_contrat, ct.date_debut, ct.date_fin, ct.date_fin_essai, "
                    +
                    "e.matricule, e.nom, e.prenom, e.photo_url, " +
                    "rtc.code AS code_type_contrat, rtc.libelle AS libelle_type_contrat, " +
                    "rsc.code AS code_statut_contrat, rsc.libelle AS libelle_statut_contrat, " +
                    "rs.libelle AS nom_site, rd.libelle AS nom_departement, rf.libelle AS nom_fonction, " +
                    "(" + DOUBLON_POTENTIEL + ") AS is_duplicate "
                    +
                    "FROM contrat_travail ct " +
                    "JOIN employe e ON ct.id_employe = e.id_employe " +
                    "LEFT JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                    "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                    "LEFT JOIN LATERAL (" +
                    "  SELECT eac_sub.id_site, eac_sub.id_departement, eac_sub.id_fonction " +
                    "  FROM employe_affectation_carriere eac_sub " +
                    "  WHERE eac_sub.id_contrat = ct.id_contrat " +
                    "  ORDER BY eac_sub.date_debut DESC, eac_sub.id_affectation DESC LIMIT 1" +
                    ") eac ON TRUE " +
                    "LEFT JOIN ref_site rs ON eac.id_site = rs.id_site " +
                    "LEFT JOIN ref_departement rd ON eac.id_departement = rd.id_departement " +
                    "LEFT JOIN ref_fonction rf ON eac.id_fonction = rf.id_fonction " +
                    whereSql + " AND ct.id_employe IN (" + placeholders + ") " +
                    " ORDER BY ct.date_debut DESC NULLS LAST, ct.id_contrat DESC";

            List<ContratTravailGlobalDTO> list = new ArrayList<>();

            try (PreparedStatement ps = c.prepareStatement(querySql)) {
                int paramIdx = 1;
                for (Object param : params) {
                    ps.setObject(paramIdx++, param);
                }
                for (Integer employeeId : employeeIds) ps.setInt(paramIdx++, employeeId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ContratTravailGlobalDTO dto = new ContratTravailGlobalDTO();
                        dto.setIdContrat(rs.getInt("id_contrat"));
                        dto.setIdEmploye(rs.getInt("id_employe"));
                        dto.setNumeroContrat(rs.getString("numero_contrat"));
                        dto.setDateDebut(rs.getDate("date_debut"));
                        dto.setDateFin(rs.getDate("date_fin"));
                        dto.setDateFinEssai(rs.getDate("date_fin_essai"));

                        dto.setMatricule(rs.getString("matricule"));
                        dto.setNomPrenom(rs.getString("nom") + " "
                                + (rs.getString("prenom") != null ? rs.getString("prenom") : ""));
                        dto.setPhotoUrl(rs.getString("photo_url"));

                        dto.setCodeTypeContrat(rs.getString("code_type_contrat"));
                        dto.setLibelleTypeContrat(rs.getString("libelle_type_contrat"));
                        dto.setCodeStatutContrat(rs.getString("code_statut_contrat"));
                        dto.setLibelleStatutContrat(rs.getString("libelle_statut_contrat"));

                        dto.setNomSite(rs.getString("nom_site") != null ? rs.getString("nom_site") : "Non affecté");
                        dto.setNomDepartement(rs.getString("nom_departement"));
                        dto.setNomFonction(rs.getString("nom_fonction"));
                        dto.setIsDuplicate(rs.getBoolean("is_duplicate"));
                        dto.setBrouillon("BROUILLON".equals(dto.getCodeStatutContrat()));
                        dto.setPlanifie("A_SIGNER".equals(dto.getCodeStatutContrat()) ||
                                ("ACTIF".equals(dto.getCodeStatutContrat()) && dto.getDateDebut() != null &&
                                 dto.getDateDebut().toLocalDate().isAfter(today)));

                        if (dto.getDateFin() != null) {
                            LocalDate fin = dto.getDateFin().toLocalDate();
                            dto.setJoursRestantsFin((int) ChronoUnit.DAYS.between(today, fin));
                        }
                        if (dto.getDateFinEssai() != null) {
                            LocalDate finEssai = dto.getDateFinEssai().toLocalDate();
                            dto.setJoursRestantsEssai((int) ChronoUnit.DAYS.between(today, finEssai));
                        }
                        dto.setEcheanceProche((dto.getJoursRestantsFin() != null && dto.getJoursRestantsFin() >= 0 && dto.getJoursRestantsFin() <= 60)
                                || (dto.getJoursRestantsEssai() != null && dto.getJoursRestantsEssai() >= 0 && dto.getJoursRestantsEssai() <= 60));

                        list.add(dto);
                    }
                }
            }

            return ResponseEntity.ok(pageContrats(list, totalElements, totalContrats, safePage, safeSize));

        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    private Map<String, Object> pageContrats(List<ContratTravailGlobalDTO> content, long totalEmployes,
            long totalContrats, int page, int size) {
        return Map.of("content", content, "totalElements", totalEmployes,
                "totalContrats", totalContrats, "currentPage", page, "pageSize", size,
                "totalPages", (int) Math.ceil((double) totalEmployes / size));
    }

    /**
     * Statistiques consolidées pour les 5 KPI cards cliquables.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getStatsSupervision() {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> stats = new LinkedHashMap<>();

            // 1. Total Contrats
            String sqlTotal = "SELECT COUNT(*) FROM contrat_travail";
            try (PreparedStatement ps = c.prepareStatement(sqlTotal); ResultSet rs = ps.executeQuery()) {
                stats.put("totalContrats", rs.next() ? rs.getInt(1) : 0);
            }

            // 2. Contrats Actifs
            String sqlActifs = "SELECT COUNT(*) FROM contrat_travail ct " +
                    "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                    "WHERE rsc.code = 'ACTIF' AND (ct.date_debut <= CURRENT_DATE)";
            try (PreparedStatement ps = c.prepareStatement(sqlActifs); ResultSet rs = ps.executeQuery()) {
                stats.put("contratsActifs", rs.next() ? rs.getInt(1) : 0);
            }

            // 3. En attente de signature / Planifiés
            String sqlAttente = "SELECT COUNT(*) FROM contrat_travail ct " +
                    "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                    "WHERE rsc.code IN ('BROUILLON', 'A_SIGNER') OR (rsc.code = 'ACTIF' AND ct.date_debut > CURRENT_DATE)";
            try (PreparedStatement ps = c.prepareStatement(sqlAttente); ResultSet rs = ps.executeQuery()) {
                stats.put("enAttenteSignature", rs.next() ? rs.getInt(1) : 0);
            }

            // 4. Échéances < 60 jours (Fin de contrat ou Période d'essai)
            String sqlEcheances = "SELECT COUNT(*) FROM contrat_travail ct " +
                    "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                    "WHERE rsc.code = 'ACTIF' AND (" +
                    "  (ct.date_fin IS NOT NULL AND ct.date_fin >= ? AND ct.date_fin <= ?) OR " +
                    "  (ct.date_fin_essai IS NOT NULL AND ct.date_fin_essai >= ? AND ct.date_fin_essai <= ?)" +
                    ")";
            LocalDate todayStats = LocalDate.now();
            LocalDate d60 = todayStats.plusDays(60);
            try (PreparedStatement ps = c.prepareStatement(sqlEcheances)) {
                ps.setDate(1, Date.valueOf(todayStats));
                ps.setDate(2, Date.valueOf(d60));
                ps.setDate(3, Date.valueOf(todayStats));
                ps.setDate(4, Date.valueOf(d60));
                try (ResultSet rs = ps.executeQuery()) {
                    stats.put("echeances60Jours", rs.next() ? rs.getInt(1) : 0);
                }
            }

            // 5. Doublons potentiels
            String sqlDoublons = "SELECT COUNT(DISTINCT ct.id_employe) FROM contrat_travail ct " +
                    "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh WHERE " + DOUBLON_POTENTIEL;
            try (PreparedStatement ps = c.prepareStatement(sqlDoublons); ResultSet rs = ps.executeQuery()) {
                stats.put("doublonsPotentiels", rs.next() ? rs.getInt(1) : 0);
            }

            // 6. Périodes d'essai actives
            String sqlEssais = "SELECT COUNT(*) FROM contrat_travail ct " +
                    "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                    "WHERE rsc.code = 'ACTIF' AND ct.date_fin_essai IS NOT NULL";
            try (PreparedStatement ps = c.prepareStatement(sqlEssais); ResultSet rs = ps.executeQuery()) {
                stats.put("essaisEnCours", rs.next() ? rs.getInt(1) : 0);
            }

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Alertes prioritaires pour le panneau « À traiter en priorité ».
     */
    @GetMapping("/alertes-prioritaires")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAlertesPrioritaires() {
        try (Connection c = dataSource.getConnection()) {
            List<Map<String, Object>> alertes = new ArrayList<>();
            LocalDate today = LocalDate.now();

            // 0. Brouillons RH en attente de finalisation
            String sqlBrouillons = "SELECT e.id_employe, e.matricule, e.nom || ' ' || e.prenom AS nom_prenom, "
                    + "w.id_workflow, COALESCE(rte.libelle, 'Contrat / Mouvement RH') AS type_ev, w.created_at "
                    + "FROM workflow_rh w "
                    + "JOIN employe e ON w.id_employe = e.id_employe "
                    + "LEFT JOIN ref_type_evenement_rh rte ON w.id_type_evenement_rh = rte.id_type_evenement_rh "
                    + "JOIN ref_statut_rh rsc ON w.id_statut_rh = rsc.id_statut_rh "
                    + "WHERE rsc.code = 'BROUILLON' "
                    + "ORDER BY w.created_at DESC LIMIT 3";
            try (PreparedStatement ps = c.prepareStatement(sqlBrouillons); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("type", "BROUILLON_RH");
                    a.put("variant", "warning");
                    a.put("idEmploye", rs.getInt("id_employe"));
                    a.put("idWorkflow", rs.getInt("id_workflow"));
                    a.put("titre", "Brouillon RH en attente de finalisation");
                    a.put("description", rs.getString("nom_prenom") + " (#" + rs.getString("matricule")
                            + ") — " + rs.getString("type_ev") + " non finalisé.");
                    a.put("actionLabel", "Finaliser le dossier");
                    a.put("actionUrl", "/employes/" + rs.getInt("id_employe") + "?tab=carriere");
                    alertes.add(a);
                }
            }

            // 1. Doublons détectés
            String sqlDup = "SELECT DISTINCT e.id_employe, e.matricule, e.nom || ' ' || e.prenom AS nom_prenom " +
                    "FROM contrat_travail ct " +
                    "JOIN employe e ON ct.id_employe = e.id_employe " +
                    "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                    "WHERE " + DOUBLON_POTENTIEL + " LIMIT 3";
            try (PreparedStatement ps = c.prepareStatement(sqlDup); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("type", "DOUBLON");
                    a.put("variant", "danger");
                    a.put("idEmploye", rs.getInt("id_employe"));
                    a.put("titre", "Doublon de contrat détecté");
                    a.put("description", rs.getString("nom_prenom") + " (#" + rs.getString("matricule")
                            + ") possède des contrats de même type et de même date de début à vérifier.");
                    a.put("actionLabel", "Vérifier les doublons");
                    a.put("actionUrl", "/carrieres?doublonsOnly=true");
                    alertes.add(a);
                }
            }

            // 2. Fins d'essai imminentes (< 45 jours)
            String sqlEssai = "SELECT ct.id_contrat, e.id_employe, e.matricule, e.nom || ' ' || e.prenom AS nom_prenom, "
                    +
                    "rtc.libelle as type_ct, ct.date_fin_essai, " +
                    "(ct.date_fin_essai - ?) as jours_restants " +
                    "FROM contrat_travail ct " +
                    "JOIN employe e ON ct.id_employe = e.id_employe " +
                    "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                    "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                    "WHERE rsc.code = 'ACTIF' AND ct.date_fin_essai IS NOT NULL " +
                    "AND ct.date_fin_essai >= ? AND ct.date_fin_essai <= ? "
                    +
                    "ORDER BY ct.date_fin_essai ASC LIMIT 3";
            try (PreparedStatement ps = c.prepareStatement(sqlEssai)) {
                ps.setDate(1, Date.valueOf(today));
                ps.setDate(2, Date.valueOf(today));
                ps.setDate(3, Date.valueOf(today.plusDays(45)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int jours = rs.getInt("jours_restants");
                        Map<String, Object> a = new LinkedHashMap<>();
                        a.put("type", "ESSAI_IMMINENT");
                        a.put("variant", "warning");
                        a.put("idEmploye", rs.getInt("id_employe"));
                        a.put("idContrat", rs.getInt("id_contrat"));
                        a.put("titre", "Fin de période d'essai imminente");
                        a.put("description",
                                rs.getString("nom_prenom") + " (#" + rs.getString("matricule")
                                        + ") — période d'essai du " + rs.getString("type_ct") + " expire le "
                                        + rs.getDate("date_fin_essai") + " (dans " + jours + " jours).");
                        a.put("actionLabel", "Confirmer la titularisation");
                        a.put("actionUrl", "/employes/" + rs.getInt("id_employe") + "?tab=carriere");
                        alertes.add(a);
                    }
                }
            }

            // 3. Fins de CDD / Stage imminentes (< 60 jours)
            String sqlFin = "SELECT ct.id_contrat, e.id_employe, e.matricule, e.nom || ' ' || e.prenom AS nom_prenom, "
                    +
                    "rtc.libelle as type_ct, ct.date_fin, " +
                    "(ct.date_fin - ?) as jours_restants " +
                    "FROM contrat_travail ct " +
                    "JOIN employe e ON ct.id_employe = e.id_employe " +
                    "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                    "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                    "WHERE rsc.code = 'ACTIF' AND rtc.exige_date_fin = TRUE " +
                    "AND ct.date_fin IS NOT NULL AND ct.date_fin >= ? AND ct.date_fin <= ? "
                    +
                    "ORDER BY ct.date_fin ASC LIMIT 3";
            try (PreparedStatement ps = c.prepareStatement(sqlFin)) {
                ps.setDate(1, Date.valueOf(today));
                ps.setDate(2, Date.valueOf(today));
                ps.setDate(3, Date.valueOf(today.plusDays(60)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int jours = rs.getInt("jours_restants");
                        Map<String, Object> a = new LinkedHashMap<>();
                        a.put("type", "FIN_CONTRAT");
                        a.put("variant", "brand");
                        a.put("idEmploye", rs.getInt("id_employe"));
                        a.put("idContrat", rs.getInt("id_contrat"));
                        a.put("titre", "Contrat " + rs.getString("type_ct") + " arrivant à échéance");
                        a.put("description",
                                rs.getString("nom_prenom") + " (#" + rs.getString("matricule") + ") — "
                                        + rs.getString("type_ct") + " expire le " + rs.getDate("date_fin") + " (dans "
                                        + jours + " jours).");
                        a.put("actionLabel", "Préparer le renouvellement");
                        a.put("actionUrl", "/employes/" + rs.getInt("id_employe") + "?tab=carriere");
                        alertes.add(a);
                    }
                }
            }

            return ResponseEntity.ok(alertes);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Export CSV complet du registre des contrats.
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public void exportContratsCsv(HttpServletResponse response) {
        try (Connection c = dataSource.getConnection()) {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"registre_contrats_" + LocalDate.now() + ".csv\"");
            carriereExportService.exportContratsCsv(response.getOutputStream(), c);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
