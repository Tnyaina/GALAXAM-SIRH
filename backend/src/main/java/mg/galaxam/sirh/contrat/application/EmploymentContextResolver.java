package mg.galaxam.sirh.contrat.application;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collection;
import mg.galaxam.sirh.contrat.domain.EmploymentContext;
import org.springframework.stereotype.Service;

/**
 * Service canonique de résolution du contexte d'emploi et de carrière d'un collaborateur.
 * Source commune datée pour contrat et affectation ; chaque domaine résout séparément ses propres règles.
 */
@Service
public class EmploymentContextResolver {

    public record Coverage(LocalDate firstDay, LocalDate lastDay, int days, int lastAssignmentId) {}
    public record PointageDay(int siteId, boolean dispensePointage, boolean overtimeEligible) {}
    public record ContinuityDay(boolean enPoste, Integer idAffectation, Integer idFonction, String fonctionNom,
                                Integer idSite, String nomSite, String departementNom) {}
    public record EmploymentLabels(String filialeNom, String nomSite, String fonctionNom) {}
    private record EmploymentSegment(int id, LocalDate start, LocalDate end) {}
    private record SalarySegment(int id, BigDecimal monthlySalary, LocalDate start, LocalDate end) {}
    private record SiteSegment(int id, Integer siteId, boolean dispensePointage, boolean overtimeEligible,
                               LocalDate start, LocalDate end) {}

    /** Libellés utiles aux exports pour une population complète, en conservant les validations du contexte daté. */
    public Map<Integer, EmploymentLabels> resolveLabels(Collection<Integer> employeeIds, LocalDate date,
            Connection c) throws SQLException {
        if (date == null) throw new IllegalArgumentException("La date du contexte employé est obligatoire.");
        List<Integer> ids = employeeIds == null ? List.of() : employeeIds.stream()
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String sql = "SELECT e.id_employe,ct.id_contrat,ct.id_filiale,f.raison_sociale AS filiale_nom, "
                + "a.id_affectation,s.libelle AS site_nom,fn.libelle AS fonction_nom, "
                + "(SELECT COUNT(*) FROM contrat_travail ctv JOIN ref_statut_rh sv ON sv.id_statut_rh=ctv.id_statut_rh "
                + "WHERE ctv.id_employe=e.id_employe AND sv.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND ctv.date_debut<=? AND (ctv.date_fin IS NULL OR ctv.date_fin>=?)) AS contracts_applicables, "
                + "(SELECT COUNT(*) FROM employe_affectation_carriere av JOIN contrat_travail ctv ON ctv.id_contrat=av.id_contrat "
                + "JOIN ref_statut_rh sv ON sv.id_statut_rh=ctv.id_statut_rh JOIN ref_statut_rh sav ON sav.id_statut_rh=av.id_statut_rh "
                + "WHERE av.id_employe=e.id_employe AND sv.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND sav.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') AND av.date_debut<=? "
                + "AND (av.date_fin IS NULL OR av.date_fin>=?) AND ctv.date_debut<=? "
                + "AND (ctv.date_fin IS NULL OR ctv.date_fin>=?)) AS assignments_applicables, "
                + "tri.soumis_cnaps,tri.soumis_sante,tri.soumis_irsa,tri.soumis_isi_5 "
                + "FROM employe e LEFT JOIN LATERAL (SELECT ct_sub.* FROM contrat_travail ct_sub "
                + "JOIN ref_statut_rh st ON st.id_statut_rh=ct_sub.id_statut_rh "
                + "WHERE ct_sub.id_employe=e.id_employe AND ct_sub.date_debut<=? "
                + "AND (ct_sub.date_fin IS NULL OR ct_sub.date_fin>=?) AND st.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "ORDER BY ct_sub.date_debut DESC,ct_sub.id_contrat DESC LIMIT 1) ct ON TRUE "
                + "LEFT JOIN ref_filiale f ON f.id_filiale=ct.id_filiale "
                + "LEFT JOIN ref_type_intervenant tri ON tri.id_type_intervenant=ct.id_type_intervenant "
                + "LEFT JOIN LATERAL (SELECT a_sub.* FROM employe_affectation_carriere a_sub "
                + "JOIN ref_statut_rh sa ON sa.id_statut_rh=a_sub.id_statut_rh "
                + "WHERE a_sub.id_employe=e.id_employe AND a_sub.id_contrat=ct.id_contrat "
                + "AND sa.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') AND a_sub.date_debut<=? "
                + "AND (a_sub.date_fin IS NULL OR a_sub.date_fin>=?) "
                + "ORDER BY a_sub.date_debut DESC,a_sub.id_affectation DESC LIMIT 1) a ON TRUE "
                + "LEFT JOIN ref_site s ON s.id_site=a.id_site LEFT JOIN ref_fonction fn ON fn.id_fonction=a.id_fonction "
                + "WHERE e.id_employe=ANY(?) ORDER BY e.id_employe";
        Map<Integer, EmploymentLabels> result = new LinkedHashMap<>();
        java.sql.Array employeeArray = c.createArrayOf("integer", ids.toArray());
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int index = 1; index <= 10; index++) ps.setDate(index, Date.valueOf(date));
            ps.setArray(11, employeeArray);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int employeeId = rs.getInt("id_employe");
                    if (rs.getInt("contracts_applicables") > 1) throw new IllegalStateException(
                            "AMBIGUOUS_CONFIGURATION: CONTRAT employé=" + employeeId + " au " + date);
                    if (rs.getInt("assignments_applicables") > 1) throw new IllegalStateException(
                            "AMBIGUOUS_CONFIGURATION: AFFECTATION_CONTRAT employé=" + employeeId + " au " + date);
                    if (rs.getObject("id_contrat") != null) {
                        for (String column : List.of("soumis_cnaps", "soumis_sante", "soumis_irsa", "soumis_isi_5")) {
                            if (rs.getObject(column) == null) throw new IllegalStateException(
                                    "MISSING_CONFIGURATION: TYPE_INTERVENANT " + column + " employé=" + employeeId + " au " + date);
                        }
                    }
                    result.put(employeeId, new EmploymentLabels(rs.getString("filiale_nom"),
                            rs.getString("site_nom"), rs.getString("fonction_nom")));
                }
            }
        } finally {
            employeeArray.free();
        }
        return result;
    }

    /** Contexte utile à la continuité de service sur toute une plage, sans requête par jour. */
    public Map<LocalDate, ContinuityDay> resolveContinuityDays(int idEmploye, LocalDate start, LocalDate end,
            Connection c) throws SQLException {
        if (start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("Bornes du contexte de continuité invalides.");
        String sql = "WITH days AS (SELECT generate_series(?::date, ?::date, INTERVAL '1 day')::date AS day) "
                + "SELECT days.day,ct.id_contrat,ct.matches AS contracts_applicables, "
                + "a.id_affectation,a.matches AS affectations_applicables,a.id_fonction,f.libelle AS fonction_nom, "
                + "a.id_site,s.libelle AS site_nom,d.libelle AS departement_nom "
                + "FROM days "
                + "LEFT JOIN LATERAL (SELECT ct_sub.id_contrat,COUNT(*) OVER() AS matches FROM contrat_travail ct_sub "
                + "JOIN ref_statut_rh st ON st.id_statut_rh=ct_sub.id_statut_rh "
                + "WHERE ct_sub.id_employe=? AND st.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND ct_sub.date_debut<=days.day AND (ct_sub.date_fin IS NULL OR ct_sub.date_fin>=days.day) "
                + "ORDER BY ct_sub.date_debut DESC,ct_sub.id_contrat DESC LIMIT 1) ct ON TRUE "
                + "LEFT JOIN LATERAL (SELECT a_sub.id_affectation,a_sub.id_fonction,a_sub.id_site,a_sub.id_departement, "
                + "COUNT(*) OVER() AS matches FROM employe_affectation_carriere a_sub "
                + "JOIN ref_statut_rh sa ON sa.id_statut_rh=a_sub.id_statut_rh "
                + "WHERE a_sub.id_employe=? AND a_sub.id_contrat=ct.id_contrat "
                + "AND sa.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND a_sub.date_debut<=days.day AND (a_sub.date_fin IS NULL OR a_sub.date_fin>=days.day) "
                + "ORDER BY a_sub.date_debut DESC,a_sub.id_affectation DESC LIMIT 1) a ON TRUE "
                + "LEFT JOIN ref_fonction f ON f.id_fonction=a.id_fonction "
                + "LEFT JOIN ref_site s ON s.id_site=a.id_site "
                + "LEFT JOIN ref_departement d ON d.id_departement=a.id_departement ORDER BY days.day";
        Map<LocalDate, ContinuityDay> result = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(start));
            ps.setDate(2, Date.valueOf(end));
            ps.setInt(3, idEmploye);
            ps.setInt(4, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate day = rs.getDate("day").toLocalDate();
                    if (rs.getInt("contracts_applicables") > 1) throw new IllegalStateException(
                            "AMBIGUOUS_CONFIGURATION: CONTRAT employé=" + idEmploye + " au " + day);
                    if (rs.getInt("affectations_applicables") > 1) throw new IllegalStateException(
                            "AMBIGUOUS_CONFIGURATION: AFFECTATION_CONTRAT employé=" + idEmploye + " au " + day);
                    Integer contract = (Integer) rs.getObject("id_contrat");
                    result.put(day, new ContinuityDay(contract != null,
                            (Integer) rs.getObject("id_affectation"), (Integer) rs.getObject("id_fonction"),
                            rs.getString("fonction_nom"), (Integer) rs.getObject("id_site"),
                            rs.getString("site_nom"), rs.getString("departement_nom")));
                }
            }
        }
        return result;
    }

    /** Sites applicables jour par jour, chargés en une requête pour les écrans et calculs de pointage. */
    public Map<LocalDate, Integer> resolveSitesByDay(int idEmploye, LocalDate start, LocalDate end,
                                                     Connection c) throws SQLException {
        Map<LocalDate, Integer> sites = new LinkedHashMap<>();
        resolvePointageDays(idEmploye, start, end, c)
                .forEach((date, contexte) -> sites.put(date, contexte.siteId()));
        return sites;
    }

    /** Site et dispense applicables par jour, sans requête SQL par journée. */
    public Map<LocalDate, PointageDay> resolvePointageDays(int idEmploye, LocalDate start, LocalDate end,
                                                            Connection c) throws SQLException {
        if (start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("Bornes du contexte journalier invalides.");
        List<SiteSegment> segments = new ArrayList<>();
        String sql = "SELECT a.id_affectation, a.id_site, COALESCE(f.dispense_pointage,FALSE) AS dispense_pointage, "
                + "cp.est_eligible_hs, "
                + "GREATEST(a.date_debut, ct.date_debut) AS debut, "
                + "LEAST(COALESCE(a.date_fin, ?), COALESCE(ct.date_fin, ?)) AS fin "
                + "FROM employe_affectation_carriere a JOIN contrat_travail ct ON ct.id_contrat=a.id_contrat "
                + "LEFT JOIN ref_fonction f ON f.id_fonction=a.id_fonction "
                + "JOIN ref_categorie_professionnelle cp ON cp.id_categorie_professionnelle=a.id_categorie_professionnelle "
                + "JOIN ref_statut_rh sa ON sa.id_statut_rh=a.id_statut_rh "
                + "JOIN ref_statut_rh sc ON sc.id_statut_rh=ct.id_statut_rh "
                + "WHERE a.id_employe=? AND sa.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND sc.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND a.date_debut<=? AND (a.date_fin IS NULL OR a.date_fin>=?) "
                + "AND ct.date_debut<=? AND (ct.date_fin IS NULL OR ct.date_fin>=?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(end)); ps.setDate(2, Date.valueOf(end)); ps.setInt(3, idEmploye);
            ps.setDate(4, Date.valueOf(end)); ps.setDate(5, Date.valueOf(start));
            ps.setDate(6, Date.valueOf(end)); ps.setDate(7, Date.valueOf(start));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) segments.add(new SiteSegment(rs.getInt("id_affectation"),
                        (Integer) rs.getObject("id_site"), rs.getBoolean("dispense_pointage"),
                        rs.getBoolean("est_eligible_hs"),
                        rs.getDate("debut").toLocalDate(),
                        rs.getDate("fin").toLocalDate()));
            }
        }
        Map<LocalDate, PointageDay> sites = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            LocalDate target = day;
            List<SiteSegment> applicable = segments.stream()
                    .filter(segment -> !target.isBefore(segment.start()) && !target.isAfter(segment.end())).toList();
            if (applicable.size() > 1) throw new IllegalStateException(
                    "AMBIGUOUS_CONFIGURATION: AFFECTATION_SITE employé=" + idEmploye + " au " + day
                            + "; versions=" + applicable);
            if (applicable.size() == 1) {
                Integer site = applicable.get(0).siteId();
                if (site == null) throw new IllegalStateException(
                        "MISSING_CONFIGURATION: AFFECTATION_SITE employé=" + idEmploye + " au " + day);
                sites.put(day, new PointageDay(site, applicable.get(0).dispensePointage(),
                        applicable.get(0).overtimeEligible()));
            }
        }
        return sites;
    }

    /**
     * Résout l'état complet d'un collaborateur à une date de référence précise.
     */
    public EmploymentContext resolveContext(int idEmploye, LocalDate dateRef, Connection c) throws SQLException {
        LocalDate ref = Objects.requireNonNull(dateRef, "La date métier du contexte employé est obligatoire.");

        String sql = "SELECT " +
                "  e.id_employe, e.matricule, e.nom, e.prenom, e.cin_date_delivrance, e.cin_lieu_delivrance, " +
                "  TRIM(COALESCE(e.nom, '') || ' ' || COALESCE(e.prenom, '')) AS nom_complet, " +
                "  e.cin_numero, e.num_affiliation_cnaps, e.date_naissance, e.est_actif AS est_actif_emp, " +
                "  ct.id_contrat, ct.numero_contrat, rtc.code AS code_type_ct, rtc.libelle AS lib_type_ct, " +
                "  rsc.code AS code_statut_ct, ct.date_debut AS debut_ct, ct.date_fin AS fin_ct, " +
                "  rf.raison_sociale AS filiale_nom, " +
                "  (SELECT MIN(c_init.date_debut) FROM contrat_travail c_init "
                + "JOIN ref_statut_rh si ON si.id_statut_rh=c_init.id_statut_rh "
                + "WHERE c_init.id_employe=e.id_employe "
                + "AND si.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU')) AS debut_embauche_init, " +
                "  eac.id_affectation, eac.date_debut AS debut_aff, eac.date_fin AS fin_aff, eac.salaire_base, " +
                "  s.id_site, s.code_site, s.libelle AS site_nom, " +
                "  d.id_departement, d.libelle AS dept_nom, " +
                "  srv.id_service, srv.libelle AS srv_nom, " +
                "  f.id_fonction, f.code_fonction, f.libelle AS fonc_nom, f.dispense_pointage, " +
                "  cat.id_categorie_professionnelle, cat.code AS cat_code, cat.libelle AS cat_lib, " +
                "  cat.est_eligible_hs, cat.est_cadre, cat.est_eligible_exoneration_hs, " +
                "  mcp.id_mode_calcul_paie, mcp.code AS mcp_code, " +
                "  tri.code AS regime_code, " +
                "  tri.soumis_cnaps, tri.soumis_sante, tri.soumis_irsa, tri.soumis_isi_5, " +
                "  (SELECT COUNT(*) FROM contrat_travail ctv JOIN ref_statut_rh sv ON sv.id_statut_rh=ctv.id_statut_rh " +
                "   WHERE ctv.id_employe=e.id_employe AND sv.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') " +
                "   AND ctv.date_debut<=? AND (ctv.date_fin IS NULL OR ctv.date_fin>=?)) AS contrats_applicables, " +
                "  (SELECT COUNT(*) FROM employe_affectation_carriere av " +
                "   JOIN contrat_travail ctv ON ctv.id_contrat=av.id_contrat " +
                "   JOIN ref_statut_rh sv ON sv.id_statut_rh=ctv.id_statut_rh " +
                "   JOIN ref_statut_rh sav ON sav.id_statut_rh=av.id_statut_rh " +
                "   WHERE av.id_employe=e.id_employe AND sv.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') " +
                "   AND sav.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') " +
                "   AND av.date_debut<=? AND (av.date_fin IS NULL OR av.date_fin>=?) " +
                "   AND ctv.date_debut<=? AND (ctv.date_fin IS NULL OR ctv.date_fin>=?)) AS affectations_applicables " +
                "FROM employe e " +
                // Un contrat clôturé aujourd'hui reste applicable aux dates qu'il couvrait.
                "LEFT JOIN LATERAL (" +
                "  SELECT ct_sub.* FROM contrat_travail ct_sub " +
                "  JOIN ref_statut_rh r_st ON ct_sub.id_statut_rh = r_st.id_statut_rh " +
                "  WHERE ct_sub.id_employe = e.id_employe " +
                "    AND ct_sub.date_debut <= ? " +
                "    AND (ct_sub.date_fin IS NULL OR ct_sub.date_fin >= ?) " +
                "    AND r_st.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') " +
                "  ORDER BY ct_sub.date_debut DESC, ct_sub.id_contrat DESC LIMIT 1" +
                ") ct ON TRUE " +
                "LEFT JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "LEFT JOIN ref_filiale rf ON rf.id_filiale = ct.id_filiale " +
                "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                "LEFT JOIN ref_type_intervenant tri ON ct.id_type_intervenant = tri.id_type_intervenant " +
                // Affectation en vigueur à dateRef
                "LEFT JOIN LATERAL (" +
                "  SELECT a.* FROM employe_affectation_carriere a " +
                "  JOIN ref_statut_rh r_aff ON r_aff.id_statut_rh = a.id_statut_rh " +
                "  WHERE a.id_employe = e.id_employe " +
                "    AND a.id_contrat = ct.id_contrat " +
                "    AND r_aff.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') " +
                "    AND a.date_debut <= ? " +
                "    AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                "  ORDER BY a.date_debut DESC, a.id_affectation DESC LIMIT 1" +
                ") eac ON TRUE " +
                "LEFT JOIN ref_site s ON eac.id_site = s.id_site " +
                "LEFT JOIN ref_departement d ON eac.id_departement = d.id_departement " +
                "LEFT JOIN ref_service srv ON eac.id_service = srv.id_service " +
                "LEFT JOIN ref_fonction f ON eac.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_categorie_professionnelle cat ON eac.id_categorie_professionnelle = cat.id_categorie_professionnelle " +
                "LEFT JOIN ref_mode_calcul_paie mcp ON eac.id_mode_calcul_paie = mcp.id_mode_calcul_paie " +
                "WHERE e.id_employe = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(ref));
            ps.setDate(2, Date.valueOf(ref));
            ps.setDate(3, Date.valueOf(ref));
            ps.setDate(4, Date.valueOf(ref));
            ps.setDate(5, Date.valueOf(ref));
            ps.setDate(6, Date.valueOf(ref));
            ps.setDate(7, Date.valueOf(ref));
            ps.setDate(8, Date.valueOf(ref));
            ps.setDate(9, Date.valueOf(ref));
            ps.setDate(10, Date.valueOf(ref));
            ps.setInt(11, idEmploye);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                if (rs.getInt("contrats_applicables") > 1) throw new IllegalStateException(
                        "AMBIGUOUS_CONFIGURATION: CONTRAT employé=" + idEmploye + " au " + ref);
                if (rs.getInt("affectations_applicables") > 1) throw new IllegalStateException(
                        "AMBIGUOUS_CONFIGURATION: AFFECTATION_CONTRAT employé=" + idEmploye + " au " + ref);
                return mapResultSet(rs, ref);
            }
        }
    }

    /**
     * Résout la couverture contrat/affectation jour par jour d'une campagne.
     * Cette méthode est la source commune aux calculs qui couvrent une plage (notamment la paie).
     */
    public Coverage resolveCoverage(int idEmploye, LocalDate start, LocalDate end, Connection c) throws SQLException {
        if (start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("Bornes de campagne invalides.");
        List<EmploymentSegment> segments = new ArrayList<>();
        String sql = "SELECT a.id_affectation, GREATEST(a.date_debut, ct.date_debut) AS debut, "
                + "LEAST(COALESCE(a.date_fin, ?), COALESCE(ct.date_fin, ?)) AS fin "
                + "FROM employe_affectation_carriere a JOIN contrat_travail ct ON ct.id_contrat=a.id_contrat "
                + "JOIN ref_statut_rh sa ON sa.id_statut_rh=a.id_statut_rh "
                + "JOIN ref_statut_rh sc ON sc.id_statut_rh=ct.id_statut_rh "
                + "WHERE a.id_employe=? AND a.date_debut<=? AND (a.date_fin IS NULL OR a.date_fin>=?) "
                + "AND ct.date_debut<=? AND (ct.date_fin IS NULL OR ct.date_fin>=?) "
                + "AND sa.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND sc.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU')";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(end)); ps.setDate(2, Date.valueOf(end)); ps.setInt(3, idEmploye);
            ps.setDate(4, Date.valueOf(end)); ps.setDate(5, Date.valueOf(start));
            ps.setDate(6, Date.valueOf(end)); ps.setDate(7, Date.valueOf(start));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) segments.add(new EmploymentSegment(rs.getInt(1),
                        rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate()));
            }
        }
        return validateCoverage(idEmploye, start, end, segments);
    }

    private Coverage validateCoverage(int idEmploye, LocalDate start, LocalDate end,
            List<EmploymentSegment> segments) {
        LocalDate first = null, last = null;
        int days = 0, lastAssignment = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            LocalDate targetDay = day;
            EmploymentSegment applicable = null;
            for (EmploymentSegment segment : segments) {
                if (!day.isBefore(segment.start()) && !day.isAfter(segment.end())) {
                    if (applicable != null) {
                        List<EmploymentSegment> versions = segments.stream()
                                .filter(candidate -> !targetDay.isBefore(candidate.start()) && !targetDay.isAfter(candidate.end()))
                                .toList();
                        throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: AFFECTATION_CONTRAT employé="
                                + idEmploye + " au " + day + "; versions=" + versions);
                    }
                    applicable = segment;
                }
            }
            if (applicable != null) {
                if (first == null) first = day;
                last = day; days++; lastAssignment = applicable.id();
            }
        }
        if (first == null) throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                mg.galaxam.sirh.common.exception.SirhErrorCode.PAYROLL_EMPLOYEE_INACTIVE,
                "MISSING_CONFIGURATION: AFFECTATION_CONTRAT employé=" + idEmploye
                        + " campagne=[" + start + ", " + end + "]; versions=" + segments);
        if (days != java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1)
            throw new IllegalStateException("MISSING_CONFIGURATION: AFFECTATION_CONTRAT trou entre "
                    + first + " et " + last + " employé=" + idEmploye + "; versions=" + segments);
        return new Coverage(first, last, days, lastAssignment);
    }

    /** Salaire contractuel de chaque jour couvert par la campagne, bornes inclusives. */
    public Map<LocalDate, BigDecimal> resolveSalaryByDay(int idEmploye, LocalDate start,
            LocalDate end, Connection c) throws SQLException {
        if (start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("Bornes de campagne invalides.");
        List<SalarySegment> segments = new ArrayList<>();
        String sql = "SELECT a.id_affectation,a.salaire_base,"
                + "GREATEST(a.date_debut,ct.date_debut) AS debut,"
                + "LEAST(COALESCE(a.date_fin,?),COALESCE(ct.date_fin,?)) AS fin "
                + "FROM employe_affectation_carriere a "
                + "JOIN contrat_travail ct ON ct.id_contrat=a.id_contrat "
                + "JOIN ref_statut_rh sa ON sa.id_statut_rh=a.id_statut_rh "
                + "JOIN ref_statut_rh sc ON sc.id_statut_rh=ct.id_statut_rh "
                + "WHERE a.id_employe=? AND sa.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND sc.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') "
                + "AND a.date_debut<=? AND (a.date_fin IS NULL OR a.date_fin>=?) "
                + "AND ct.date_debut<=? AND (ct.date_fin IS NULL OR ct.date_fin>=?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(end)); ps.setDate(2, Date.valueOf(end)); ps.setInt(3, idEmploye);
            ps.setDate(4, Date.valueOf(end)); ps.setDate(5, Date.valueOf(start));
            ps.setDate(6, Date.valueOf(end)); ps.setDate(7, Date.valueOf(start));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) segments.add(new SalarySegment(rs.getInt(1), rs.getBigDecimal(2),
                        rs.getDate(3).toLocalDate(), rs.getDate(4).toLocalDate()));
            }
        }
        List<EmploymentSegment> coverageSegments = segments.stream()
                .map(segment -> new EmploymentSegment(segment.id(), segment.start(), segment.end()))
                .toList();
        Coverage coverage = validateCoverage(idEmploye, start, end, coverageSegments);
        Map<LocalDate, BigDecimal> salaries = new LinkedHashMap<>();
        for (LocalDate day = coverage.firstDay(); !day.isAfter(coverage.lastDay()); day = day.plusDays(1)) {
            LocalDate targetDay = day;
            SalarySegment match = null;
            for (SalarySegment segment : segments) {
                if (!day.isBefore(segment.start()) && !day.isAfter(segment.end())) {
                    if (match != null) {
                        List<SalarySegment> versions = segments.stream()
                                .filter(candidate -> !targetDay.isBefore(candidate.start()) && !targetDay.isAfter(candidate.end()))
                                .toList();
                        throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: SALAIRE employé="
                                + idEmploye + " au " + day + "; versions=" + versions);
                    }
                    match = segment;
                }
            }
            if (match == null || match.monthlySalary() == null
                    || match.monthlySalary().signum() <= 0) throw new IllegalStateException(
                    "MISSING_CONFIGURATION: SALAIRE employé=" + idEmploye + " au " + day + "; versions=" + segments);
            salaries.put(day, match.monthlySalary());
        }
        return java.util.Collections.unmodifiableMap(salaries);
    }

    /**
     * Résout la liste de tous les salariés en poste ou actifs sur une période [debut, fin].
     */
    public List<EmploymentContext> listActiveEmploymentContexts(LocalDate debut, LocalDate fin, Connection c) throws SQLException {
        LocalDate dDebut = Objects.requireNonNull(debut, "La date de début du contexte employé est obligatoire.");
        LocalDate dFin = Objects.requireNonNull(fin, "La date de fin du contexte employé est obligatoire.");
        if (dFin.isBefore(dDebut)) throw new IllegalArgumentException("Bornes du contexte employé invalides.");

        String sql = "SELECT " +
                "  e.id_employe, e.matricule, e.nom, e.prenom, e.cin_date_delivrance, e.cin_lieu_delivrance, " +
                "  TRIM(COALESCE(e.nom, '') || ' ' || COALESCE(e.prenom, '')) AS nom_complet, " +
                "  e.cin_numero, e.num_affiliation_cnaps, e.date_naissance, e.est_actif AS est_actif_emp, " +
                "  ct.id_contrat, ct.numero_contrat, rtc.code AS code_type_ct, rtc.libelle AS lib_type_ct, " +
                "  rsc.code AS code_statut_ct, ct.date_debut AS debut_ct, ct.date_fin AS fin_ct, " +
                "  rf.raison_sociale AS filiale_nom, " +
                "  (SELECT MIN(c_init.date_debut) FROM contrat_travail c_init "
                + "JOIN ref_statut_rh si ON si.id_statut_rh=c_init.id_statut_rh "
                + "WHERE c_init.id_employe=e.id_employe "
                + "AND si.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU')) AS debut_embauche_init, " +
                "  eac.id_affectation, eac.date_debut AS debut_aff, eac.date_fin AS fin_aff, eac.salaire_base, " +
                "  s.id_site, s.code_site, s.libelle AS site_nom, " +
                "  d.id_departement, d.libelle AS dept_nom, " +
                "  srv.id_service, srv.libelle AS srv_nom, " +
                "  f.id_fonction, f.code_fonction, f.libelle AS fonc_nom, f.dispense_pointage, " +
                "  cat.id_categorie_professionnelle, cat.code AS cat_code, cat.libelle AS cat_lib, " +
                "  cat.est_eligible_hs, cat.est_cadre, cat.est_eligible_exoneration_hs, " +
                "  mcp.id_mode_calcul_paie, mcp.code AS mcp_code, " +
                "  tri.code AS regime_code, " +
                "  tri.soumis_cnaps, tri.soumis_sante, tri.soumis_irsa, tri.soumis_isi_5, " +
                "  (SELECT COUNT(*) FROM contrat_travail ctv JOIN ref_statut_rh sv ON sv.id_statut_rh=ctv.id_statut_rh " +
                "   WHERE ctv.id_employe=e.id_employe AND sv.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') " +
                "   AND ctv.date_debut<=? AND (ctv.date_fin IS NULL OR ctv.date_fin>=?)) AS contrats_applicables, " +
                "  (SELECT COUNT(*) FROM employe_affectation_carriere av " +
                "   JOIN contrat_travail ctv ON ctv.id_contrat=av.id_contrat " +
                "   JOIN ref_statut_rh sv ON sv.id_statut_rh=ctv.id_statut_rh " +
                "   JOIN ref_statut_rh sav ON sav.id_statut_rh=av.id_statut_rh " +
                "   WHERE av.id_employe=e.id_employe AND sv.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') " +
                "   AND sav.code IN ('ACTIF','HISTORISE','CLOTURE','ROMPU') " +
                "   AND av.date_debut<=? AND (av.date_fin IS NULL OR av.date_fin>=?) " +
                "   AND ctv.date_debut<=? AND (ctv.date_fin IS NULL OR ctv.date_fin>=?)) AS affectations_applicables " +
                "FROM employe e " +
                "JOIN contrat_travail ct ON ct.id_employe = e.id_employe " +
                "  AND ct.date_debut <= ? " +
                "  AND (ct.date_fin IS NULL OR ct.date_fin >= ?) " +
                "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh AND rsc.code = 'ACTIF' " +
                "LEFT JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "LEFT JOIN ref_filiale rf ON rf.id_filiale = ct.id_filiale " +
                "LEFT JOIN ref_type_intervenant tri ON ct.id_type_intervenant = tri.id_type_intervenant " +
                "LEFT JOIN LATERAL (" +
                "  SELECT a.* FROM employe_affectation_carriere a " +
                "  WHERE a.id_employe = e.id_employe AND a.id_contrat = ct.id_contrat " +
                "    AND a.date_debut <= ? " +
                "    AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                "  ORDER BY a.date_debut DESC, a.id_affectation DESC LIMIT 1" +
                ") eac ON TRUE " +
                "LEFT JOIN ref_site s ON eac.id_site = s.id_site " +
                "LEFT JOIN ref_departement d ON eac.id_departement = d.id_departement " +
                "LEFT JOIN ref_service srv ON eac.id_service = srv.id_service " +
                "LEFT JOIN ref_fonction f ON eac.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_categorie_professionnelle cat ON eac.id_categorie_professionnelle = cat.id_categorie_professionnelle " +
                "LEFT JOIN ref_mode_calcul_paie mcp ON eac.id_mode_calcul_paie = mcp.id_mode_calcul_paie " +
                "WHERE e.est_actif = TRUE " +
                "ORDER BY s.libelle ASC, e.nom ASC, e.prenom ASC";

        List<EmploymentContext> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(dFin));
            ps.setDate(2, Date.valueOf(dFin));
            ps.setDate(3, Date.valueOf(dFin));
            ps.setDate(4, Date.valueOf(dFin));
            ps.setDate(5, Date.valueOf(dFin));
            ps.setDate(6, Date.valueOf(dFin));
            ps.setDate(7, Date.valueOf(dFin));
            ps.setDate(8, Date.valueOf(dDebut));
            ps.setDate(9, Date.valueOf(dFin));
            ps.setDate(10, Date.valueOf(dDebut));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int employeeId = rs.getInt("id_employe");
                    if (rs.getInt("contrats_applicables") > 1) throw new IllegalStateException(
                            "AMBIGUOUS_CONFIGURATION: CONTRAT employé=" + employeeId + " au " + dFin);
                    if (rs.getInt("affectations_applicables") > 1) throw new IllegalStateException(
                            "AMBIGUOUS_CONFIGURATION: AFFECTATION_CONTRAT employé=" + employeeId + " au " + dFin);
                    list.add(mapResultSet(rs, dFin));
                }
            }
        }
        return list;
    }

    /**
     * Résout l'état courant (date du jour) d'un collaborateur.
     */
    public EmploymentContext resolveCurrent(int idEmploye, Connection c) throws SQLException {
        return resolveContext(idEmploye, LocalDate.now(), c);
    }

    private EmploymentContext mapResultSet(ResultSet rs, LocalDate dateRef) throws SQLException {
        int idEmp = rs.getInt("id_employe");
        boolean estActifEmp = rs.getBoolean("est_actif_emp");
        Integer idContrat = (Integer) rs.getObject("id_contrat");
        String statutCt = rs.getString("code_statut_ct");
        Date dDebutCt = rs.getDate("debut_ct");
        Date dFinCt = rs.getDate("fin_ct");
        LocalDate debutCt = dDebutCt != null ? dDebutCt.toLocalDate() : null;
        LocalDate finCt = dFinCt != null ? dFinCt.toLocalDate() : null;

        boolean enPoste = idContrat != null && statutCt != null
                && (debutCt == null || !dateRef.isBefore(debutCt))
                && (finCt == null || !dateRef.isAfter(finCt));

        Date dNaiss = rs.getDate("date_naissance");
        Date dEmbaucheInit = rs.getDate("debut_embauche_init");
        Date dDebutAff = rs.getDate("debut_aff");
        Date dFinAff = rs.getDate("fin_aff");

        return new EmploymentContext(
                idEmp,
                rs.getString("matricule"),
                rs.getString("nom"),
                rs.getString("prenom"),
                rs.getString("nom_complet"),
                rs.getString("cin_numero"),
                rs.getDate("cin_date_delivrance") != null ? rs.getDate("cin_date_delivrance").toLocalDate() : null,
                rs.getString("cin_lieu_delivrance"),
                rs.getString("num_affiliation_cnaps"),
                dNaiss != null ? dNaiss.toLocalDate() : null,
                estActifEmp,

                idContrat,
                rs.getString("numero_contrat"),
                rs.getString("code_type_ct"),
                rs.getString("lib_type_ct"),
                statutCt,
                debutCt,
                finCt,
                dEmbaucheInit != null ? dEmbaucheInit.toLocalDate() : debutCt,
                rs.getString("filiale_nom"),

                (Integer) rs.getObject("id_affectation"),
                dDebutAff != null ? dDebutAff.toLocalDate() : null,
                dFinAff != null ? dFinAff.toLocalDate() : null,
                rs.getBigDecimal("salaire_base"),

                (Integer) rs.getObject("id_site"),
                rs.getString("code_site"),
                rs.getString("site_nom"),
                (Integer) rs.getObject("id_departement"),
                rs.getString("dept_nom"),
                (Integer) rs.getObject("id_service"),
                rs.getString("srv_nom"),
                (Integer) rs.getObject("id_fonction"),
                rs.getString("code_fonction"),
                rs.getString("fonc_nom"),
                rs.getBoolean("dispense_pointage"),

                (Integer) rs.getObject("id_categorie_professionnelle"),
                rs.getString("cat_code"),
                rs.getString("cat_lib"),
                rs.getBoolean("est_eligible_hs"),
                rs.getBoolean("est_cadre"),
                rs.getObject("est_eligible_exoneration_hs") == null
                        ? rs.getBoolean("est_eligible_hs") && !rs.getBoolean("est_cadre")
                        : rs.getBoolean("est_eligible_exoneration_hs"),
                (Integer) rs.getObject("id_mode_calcul_paie"),
                rs.getString("mcp_code"),

                rs.getString("regime_code"),
                requiredFlag(rs, "soumis_cnaps", idEmp, dateRef),
                requiredFlag(rs, "soumis_sante", idEmp, dateRef),
                requiredFlag(rs, "soumis_irsa", idEmp, dateRef),
                requiredFlag(rs, "soumis_isi_5", idEmp, dateRef),

                enPoste
        );
    }

    private boolean requiredFlag(ResultSet rs, String column, int idEmploye, LocalDate date) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null && rs.getObject("id_contrat") != null)
            throw new IllegalStateException("MISSING_CONFIGURATION: TYPE_INTERVENANT " + column
                    + " employé=" + idEmploye + " au " + date);
        return Boolean.TRUE.equals(value);
    }
}
