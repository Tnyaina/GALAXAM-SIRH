package mg.galaxam.sirh.paie.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.paie.api.dto.PayrollPopulationMember;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import org.springframework.stereotype.Service;

/** Lecture de campagne pour le cockpit DAF ; aucune mutation de paie. */
@Service
public class PayrollCockpitService {
    private final DataSource dataSource;
    private final PayrollPopulationResolver populationResolver;

    public PayrollCockpitService(DataSource dataSource, PayrollPopulationResolver populationResolver) {
        this.dataSource = dataSource;
        this.populationResolver = populationResolver;
    }

    // Lecture DAF : cockpit de période, population et pagination.
    public Map<String, Object> obtenirCockpitPeriode(Integer idPeriodePaie) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return obtenirCockpitPeriode(idPeriodePaie, c);
        }
    }

    public Map<String, Object> obtenirCockpitPeriode(Integer idPeriodePaie, Connection c) throws Exception {
        return obtenirCockpitPeriode(idPeriodePaie, 1, 25, null, null, null, c);
    }

    public Map<String, Object> obtenirPopulationCockpit(Integer idPeriodePaie, Connection c) throws Exception {
        PayrollPopulationResolver.PayrollPopulation population = populationResolver.resolve(idPeriodePaie, c);
        List<PayrollPopulationMember> members = population.members();
        return Map.of(
                "population", members,
                "populationSummary", population.summary(),
                "populationPage", pageMetadata(members.size(), 1, Math.max(1, members.size())));
    }

    public Map<String, Object> obtenirCockpitPeriode(
            Integer idPeriodePaie, int requestedPage, int requestedSize,
            String query, String site, String filter, Connection c) throws Exception {
        int page = Math.max(1, requestedPage);
        int size = Math.min(100, Math.max(1, requestedSize));
        Map<String, Object> cockpit = new HashMap<>();

        // 1. Informations de la période
        String sqlPeriode = "SELECT p.*, r.code AS code_statut, r.libelle AS libelle_statut " +
                "FROM periode_paie p " +
                "LEFT JOIN ref_statut_periode_paie r ON p.id_statut_periode = r.id_statut_periode " +
                "WHERE p.id_periode_paie = ?";
        Map<String, Object> perMap = new HashMap<>();
        Date dateDebut = null;
        Date dateFin = null;
        try (PreparedStatement ps = c.prepareStatement(sqlPeriode)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    perMap.put("idPeriodePaie", rs.getInt("id_periode_paie"));
                    perMap.put("code", rs.getString("code"));
                    perMap.put("libelle", rs.getString("libelle"));
                    dateDebut = rs.getDate("date_debut");
                    dateFin = rs.getDate("date_fin");
                    perMap.put("dateDebut", dateDebut != null ? dateDebut.toString() : null);
                    perMap.put("dateFin", dateFin != null ? dateFin.toString() : null);
                    perMap.put("statut", rs.getString("code_statut"));
                    perMap.put("libelleStatut", rs.getString("libelle_statut"));
                    perMap.put("version", rs.getInt("version"));
                } else {
                    throw new IllegalArgumentException("Période de paie introuvable (ID " + idPeriodePaie + ")");
                }
            }
        }
        cockpit.put("periode", perMap);

        PayrollPopulationResolver.PayrollPopulation population = populationResolver.resolve(idPeriodePaie, c);
        cockpit.put("populationSummary", population.summary());

        // 2. Préparation amont (Readiness)
        Map<String, Object> readiness = new HashMap<>();
        int nbPointagesScelles = PayrollPointageCoverage.frozenCount(
                idPeriodePaie, population.includedEmployeeIds(), c);
        int effectifAttendu = population.includedEmployeeIds().size();
        readiness.put("effectifAttendu", effectifAttendu);
        readiness.put("nbEmployesPointes", nbPointagesScelles);
        readiness.put("pointageScelle", nbPointagesScelles >= effectifAttendu && effectifAttendu > 0);
        long populationBlocked = population.members().stream()
                .filter(member -> "BLOCKED".equals(member.decision())).count();
        readiness.put("populationBlocked", populationBlocked);

        // 3. Indicateurs financiers & KPIs de la période actuelle
        String sqlKpis = "SELECT COUNT(*) AS effectif_calcule, " +
                "COALESCE(SUM(total_gains_bruts), 0) AS total_brut, " +
                "COALESCE(SUM(total_cotisations_patronales), 0) AS total_cotis_pat, " +
                "COALESCE(SUM(total_cotisations_salariales), 0) AS total_cotis_sal, " +
                "COALESCE(SUM(salaire_net_imposable), 0) AS total_net_imposable, " +
                "COALESCE(SUM(irsa_net), 0) AS total_irsa, " +
                "COALESCE(SUM(net_a_payer), 0) AS total_net_a_payer, " +
                "COALESCE(SUM(total_retenues_acompte_pret), 0) AS total_retenues " +
                "FROM bulletin_paie WHERE id_periode_paie = ?";

        Map<String, Object> kpis = new HashMap<>();
        BigDecimal brutActuel = BigDecimal.ZERO;
        BigDecimal netActuel = BigDecimal.ZERO;
        try (PreparedStatement ps = c.prepareStatement(sqlKpis)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpis.put("effectifCalcule", rs.getInt("effectif_calcule"));
                    brutActuel = rs.getBigDecimal("total_brut");
                    netActuel = rs.getBigDecimal("total_net_a_payer");
                    kpis.put("totalBrut", brutActuel);
                    kpis.put("totalCotisationsPatronales", rs.getBigDecimal("total_cotis_pat"));
                    kpis.put("totalCotisationsSalariales", rs.getBigDecimal("total_cotis_sal"));
                    kpis.put("totalNetImposable", rs.getBigDecimal("total_net_imposable"));
                    kpis.put("totalIrsa", rs.getBigDecimal("total_irsa"));
                    kpis.put("totalNetAPayer", netActuel);
                    kpis.put("totalRetenues", rs.getBigDecimal("total_retenues"));
                }
            }
        }

        // 4. Période M-1 pour calcul de l'écart
        Integer idPeriodeM1 = null;
        BigDecimal brutM1 = BigDecimal.ZERO;
        BigDecimal netM1 = BigDecimal.ZERO;
        String sqlM1 = "SELECT id_periode_paie FROM periode_paie " +
                "WHERE date_fin < (SELECT date_debut FROM periode_paie WHERE id_periode_paie = ?) " +
                "ORDER BY date_fin DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sqlM1)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) idPeriodeM1 = rs.getInt("id_periode_paie");
            }
        }

        if (idPeriodeM1 != null) {
            String sqlKpiM1 = "SELECT COALESCE(SUM(total_gains_bruts), 0) AS brut_m1, " +
                    "COALESCE(SUM(net_a_payer), 0) AS net_m1 FROM bulletin_paie WHERE id_periode_paie = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlKpiM1)) {
                ps.setInt(1, idPeriodeM1);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        brutM1 = rs.getBigDecimal("brut_m1");
                        netM1 = rs.getBigDecimal("net_m1");
                    }
                }
            }
        }

        kpis.put("totalBrutM1", brutM1);
        kpis.put("totalNetM1", netM1);

        double ecartBrutPct = 0.0;
        if (brutM1.compareTo(BigDecimal.ZERO) > 0) {
            ecartBrutPct = brutActuel.subtract(brutM1)
                    .divide(brutM1, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .doubleValue();
        }
        kpis.put("ecartBrutM1Pct", ecartBrutPct);
        cockpit.put("kpis", kpis);

        // Map des nets M-1 par employé
        Map<Integer, BigDecimal> empNetM1Map = new HashMap<>();
        if (idPeriodeM1 != null) {
            String sqlEmpM1 = "SELECT id_employe, net_a_payer FROM bulletin_paie WHERE id_periode_paie = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlEmpM1)) {
                ps.setInt(1, idPeriodeM1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        empNetM1Map.put(rs.getInt("id_employe"), rs.getBigDecimal("net_a_payer"));
                    }
                }
            }
        }

        // 5. Liste des bulletins détaillés
        String sqlBulletins = "SELECT bp.id_bulletin, bp.id_employe, bp.matricule, bp.nom_prenom, " +
                "bp.intitule_poste, bp.nom_site, bp.salaire_base, bp.total_gains_bruts, " +
                "bp.total_cotisations_salariales, bp.irsa_net, bp.net_a_payer, bp.statut, " +
                "COALESCE(bp.type_bulletin, 'MENSUEL') AS type_bulletin " +
                "FROM bulletin_paie bp " +
                "WHERE bp.id_periode_paie = ? " +
                "ORDER BY bp.matricule ASC";

        List<Map<String, Object>> bulletinPage = new ArrayList<>();
        java.util.Set<String> bulletinSites = new java.util.TreeSet<>();
        int totalBulletins = 0;
        int matchingBulletins = 0;
        int requestedOffset = (page - 1) * size;
        int nbAnomalies = 0;
        try (PreparedStatement ps = c.prepareStatement(sqlBulletins)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    int idEmp = rs.getInt("id_employe");
                    BigDecimal net = rs.getBigDecimal("net_a_payer");
                    row.put("idBulletin", rs.getInt("id_bulletin"));
                    row.put("idEmploye", idEmp);
                    row.put("matricule", rs.getString("matricule"));
                    row.put("nomPrenom", rs.getString("nom_prenom"));
                    row.put("intitulePoste", rs.getString("intitule_poste"));
                    row.put("nomSite", rs.getString("nom_site"));
                    row.put("salaireBase", rs.getBigDecimal("salaire_base"));
                    row.put("totalGainsBruts", rs.getBigDecimal("total_gains_bruts"));
                    row.put("totalCotisationsSalariales", rs.getBigDecimal("total_cotisations_salariales"));
                    row.put("irsaNet", rs.getBigDecimal("irsa_net"));
                    row.put("netAPayer", net);
                    row.put("statut", rs.getString("statut"));
                    row.put("typeBulletin", rs.getString("type_bulletin"));

                    BigDecimal prevNet = empNetM1Map.get(idEmp);
                    Double ecartIndivPct = null;
                    boolean aAlerte = false;
                    if (prevNet != null && prevNet.compareTo(BigDecimal.ZERO) > 0) {
                        ecartIndivPct = net.subtract(prevNet)
                                .divide(prevNet, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .doubleValue();
                        if (Math.abs(ecartIndivPct) >= 20.0) {
                            aAlerte = true;
                        }
                    }
                    if (net.compareTo(BigDecimal.ZERO) <= 0) {
                        aAlerte = true;
                    }
                    if (aAlerte) nbAnomalies++;

                    row.put("ecartM1Pct", ecartIndivPct);
                    row.put("aAlerte", aAlerte);
                    totalBulletins++;
                    if (row.get("nomSite") != null) bulletinSites.add(row.get("nomSite").toString());
                    if (matchesCockpitFilters(row, query, site, filter)) {
                        if (matchingBulletins >= requestedOffset && bulletinPage.size() < size) {
                            bulletinPage.add(row);
                        }
                        matchingBulletins++;
                    }
                }
            }
        }
        List<PayrollPopulationMember> populationMembers = population.members();
        int populationFrom = Math.min((page - 1) * size, populationMembers.size());
        int populationTo = Math.min(populationFrom + size, populationMembers.size());
        List<PayrollPopulationMember> populationPage = populationMembers.subList(populationFrom, populationTo);
        readiness.put("nbAnomalies", nbAnomalies);
        List<Map<String, Object>> checks = new ArrayList<>(List.of(
                Map.of(
                        "code", "POINTAGE_FROZEN",
                        "status", Boolean.TRUE.equals(readiness.get("pointageScelle")) ? "PASS" : "BLOCK",
                        "message", Boolean.TRUE.equals(readiness.get("pointageScelle"))
                                ? "Le pointage de toute la population est figé."
                                : "Le pointage doit être figé pour toute la population avant le calcul réel.",
                        "actual", nbPointagesScelles,
                        "expected", effectifAttendu),
                Map.of(
                        "code", "PAYROLL_RESULTS",
                        "status", totalBulletins == 0 ? "WARNING" : "PASS",
                        "message", totalBulletins == 0
                                ? "Aucun résultat de paie n'a encore été calculé."
                                : totalBulletins + " résultat(s) de paie disponible(s)."),
                Map.of(
                        "code", "PAYROLL_POPULATION",
                        "status", populationBlocked > 0 ? "BLOCK" : "PASS",
                        "message", populationBlocked > 0
                                ? populationBlocked + " collaborateur(s) ont une configuration de carrière ambiguë ou absente."
                                : "La population de paie est résolue sans ambiguïté."),
                Map.of(
                        "code", "PAYROLL_CONTROL",
                        "status", nbAnomalies > 0 ? "WARNING" : "PASS",
                        "message", nbAnomalies > 0
                                ? nbAnomalies + " résultat(s) exigent un contrôle."
                                : "Aucune variation bloquante détectée dans les résultats.")));
        if (dateFin != null) {
            PaieConfigLegale legal = PaieConfigLegale.findActiveAt(c, dateFin.toLocalDate());
            boolean configured = legal != null && legal.getPlafondCotisationMensuel() != null;
            checks.add(Map.of("code", "FISCAL_SOCIAL", "status", configured ? "PASS" : "BLOCK",
                    "message", configured ? "Barème légal et plafond social présents à la fin de la campagne ; les taux par site seront vérifiés au calcul."
                            : "Barème légal ou plafond social absent à la fin de la campagne."));
        }
        int pendingAdjustments = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM paie_avance_acompte a JOIN ref_type_mouvement_paie t "
                        + "ON t.id_type_mouvement=a.id_type_mouvement "
                        + "WHERE a.id_periode_paie=? AND t.categorie IN ('ACOMPTE','AVANCE') "
                        + "AND (a.statut='EN_ATTENTE' OR (a.versement_confirme_at IS NULL "
                        + "AND (a.statut='IMPUTE_PAIE' OR a.montant_impute>0)))")) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) pendingAdjustments = rs.getInt(1); }
        }
        checks.add(Map.of("code", "ACOMPTES", "status", pendingAdjustments > 0 ? "WARNING" : "PASS",
                "message", pendingAdjustments > 0
                        ? pendingAdjustments + " dossier(s) d'acompte ou d'avance à traiter."
                        : "Aucun acompte ou avance en attente de traitement."));
        for (String code : List.of("CALENDRIERS", "CONGES", "PRETS", "INDEMNITES")) {
            checks.add(Map.of("code", code, "status", "WARNING",
                    "message", "Aucun contrôle autonome n'est encore disponible à cette étape ; vérifiez le paramétrage avant le calcul."));
        }
        readiness.put("checks", checks);
        cockpit.put("readiness", readiness);
        cockpit.put("population", populationPage);
        cockpit.put("populationPage", pageMetadata(populationMembers.size(), page, size));
        cockpit.put("bulletins", bulletinPage);
        cockpit.put("bulletinsPage", pageMetadata(matchingBulletins, page, size));
        cockpit.put("sites", List.copyOf(bulletinSites));

        Map<String, Object> validationSummary = new HashMap<>();
        validationSummary.put("population", effectifAttendu);
        validationSummary.put("netTotal", kpis.get("totalNetAPayer"));
        validationSummary.put("employerContributions", kpis.get("totalCotisationsPatronales"));
        validationSummary.put("taxes", kpis.get("totalIrsa"));
        validationSummary.put("warnings", nbAnomalies);
        validationSummary.put("version", perMap.get("version"));
        try (PreparedStatement statement = c.prepareStatement(
                "SELECT MAX(date_calcul) AS calculated_at, MAX(date_validation) AS validated_at, "
                        + "CASE WHEN COUNT(DISTINCT calcule_par) = 1 THEN MAX(calcule_par) END AS calculated_by "
                        + "FROM bulletin_paie WHERE id_periode_paie = ?")) {
            statement.setInt(1, idPeriodePaie);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                validationSummary.put("calculatedAt", result.getTimestamp("calculated_at"));
                validationSummary.put("validatedAt", result.getTimestamp("validated_at"));
                validationSummary.put("calculatedBy", result.getObject("calculated_by"));
            }
        }
        cockpit.put("validationSummary", validationSummary);

        return cockpit;
    }

    private boolean matchesCockpitFilters(
            Map<String, Object> row, String query, String site, String filter) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        boolean matchesQuery = normalizedQuery.isEmpty()
                || containsIgnoreCase(row.get("matricule"), normalizedQuery)
                || containsIgnoreCase(row.get("nomPrenom"), normalizedQuery)
                || containsIgnoreCase(row.get("intitulePoste"), normalizedQuery);
        boolean matchesSite = site == null || site.isBlank() || "ALL".equalsIgnoreCase(site)
                || site.equals(row.get("nomSite"));
        String normalizedFilter = filter == null ? "ALL" : filter.toUpperCase(java.util.Locale.ROOT);
        boolean matchesStatus = switch (normalizedFilter) {
            case "ALERTS" -> Boolean.TRUE.equals(row.get("aAlerte"));
            case "STC" -> "STC".equals(row.get("typeBulletin"));
            case "VALIDE" -> "VALIDE".equals(row.get("statut"));
            case "CLOTURE" -> "CLOTURE".equals(row.get("statut"));
            default -> true;
        };
        return matchesQuery && matchesSite && matchesStatus;
    }

    private boolean containsIgnoreCase(Object value, String normalizedQuery) {
        return value != null && value.toString().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery);
    }

    private Map<String, Object> pageMetadata(int totalElements, int page, int size) {
        return Map.of(
                "totalElements", totalElements,
                "totalPages", Math.max(1, (int) Math.ceil((double) totalElements / size)),
                "currentPage", page,
                "pageSize", size);
    }

}
