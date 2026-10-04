package mg.galaxam.sirh.contrat.application;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.contrat.domain.EmploymentContext;
import org.springframework.stereotype.Service;

/**
 * Service canonique de résolution du contexte d'emploi et de carrière d'un collaborateur.
 * Source commune datée pour contrat et affectation ; chaque domaine résout séparément ses propres règles.
 */
@Service
public class EmploymentContextResolver {

    public record Coverage(LocalDate firstDay, LocalDate lastDay, int days, int lastAssignmentId) {}
    private record EmploymentSegment(int id, LocalDate start, LocalDate end) {}

    /**
     * Résout l'état complet d'un collaborateur à une date de référence précise.
     */
    public EmploymentContext resolveContext(int idEmploye, LocalDate dateRef, Connection c) throws SQLException {
        LocalDate ref = dateRef != null ? dateRef : LocalDate.now();
        verifierUniciteContexte(idEmploye, ref, c);

        String sql = "SELECT " +
                "  e.id_employe, e.matricule, e.nom, e.prenom, " +
                "  TRIM(COALESCE(e.nom, '') || ' ' || COALESCE(e.prenom, '')) AS nom_complet, " +
                "  e.cin_numero, e.num_affiliation_cnaps, e.date_naissance, e.est_actif AS est_actif_emp, " +
                "  ct.id_contrat, ct.numero_contrat, rtc.code AS code_type_ct, rtc.libelle AS lib_type_ct, " +
                "  rsc.code AS code_statut_ct, ct.date_debut AS debut_ct, ct.date_fin AS fin_ct, " +
                "  rf.raison_sociale AS filiale_nom, " +
                "  (SELECT MIN(c_init.date_debut) FROM contrat_travail c_init WHERE c_init.id_employe = e.id_employe) AS debut_embauche_init, " +
                "  eac.id_affectation, eac.date_debut AS debut_aff, eac.date_fin AS fin_aff, eac.salaire_base, " +
                "  s.id_site, s.code_site, s.libelle AS site_nom, " +
                "  d.id_departement, d.libelle AS dept_nom, " +
                "  srv.id_service, srv.libelle AS srv_nom, " +
                "  f.id_fonction, f.code_fonction, f.libelle AS fonc_nom, f.dispense_pointage, " +
                "  cat.id_categorie_professionnelle, cat.code AS cat_code, cat.libelle AS cat_lib, " +
                "  cat.est_eligible_hs, cat.est_cadre, cat.est_eligible_exoneration_hs, " +
                "  mcp.id_mode_calcul_paie, mcp.code AS mcp_code, " +
                "  tri.code AS regime_code, " +
                "  COALESCE(tri.soumis_cnaps, TRUE) AS soumis_cnaps, " +
                "  COALESCE(tri.soumis_sante, TRUE) AS soumis_sante, " +
                "  COALESCE(tri.soumis_irsa, TRUE) AS soumis_irsa, " +
                "  COALESCE(tri.soumis_isi_5, FALSE) AS soumis_isi_5 " +
                "FROM employe e " +
                // Contrat en vigueur à dateRef (priorité à ACTIF couvrant la date, sinon le plus récent)
                "LEFT JOIN LATERAL (" +
                "  SELECT ct_sub.* FROM contrat_travail ct_sub " +
                "  JOIN ref_statut_rh r_st ON ct_sub.id_statut_rh = r_st.id_statut_rh " +
                "  WHERE ct_sub.id_employe = e.id_employe " +
                "    AND ct_sub.date_debut <= ? " +
                "    AND (ct_sub.date_fin IS NULL OR ct_sub.date_fin >= ?) " +
                "    AND r_st.code = 'ACTIF' " +
                "  ORDER BY ct_sub.date_debut DESC, ct_sub.id_contrat DESC LIMIT 1" +
                ") ct ON TRUE " +
                "LEFT JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "LEFT JOIN ref_filiale rf ON rf.id_filiale = ct.id_filiale " +
                "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                "LEFT JOIN ref_type_intervenant tri ON ct.id_type_intervenant = tri.id_type_intervenant " +
                // Affectation en vigueur à dateRef
                "LEFT JOIN LATERAL (" +
                "  SELECT a.* FROM employe_affectation_carriere a " +
                "  WHERE a.id_employe = e.id_employe " +
                "    AND (ct.id_contrat IS NULL OR a.id_contrat = ct.id_contrat) " +
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
            ps.setInt(5, idEmploye);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
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
                + "WHERE a.id_employe=? AND a.date_debut<=? AND (a.date_fin IS NULL OR a.date_fin>=?) "
                + "AND ct.date_debut<=? AND (ct.date_fin IS NULL OR ct.date_fin>=?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(end)); ps.setDate(2, Date.valueOf(end)); ps.setInt(3, idEmploye);
            ps.setDate(4, Date.valueOf(end)); ps.setDate(5, Date.valueOf(start));
            ps.setDate(6, Date.valueOf(end)); ps.setDate(7, Date.valueOf(start));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) segments.add(new EmploymentSegment(rs.getInt(1),
                        rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate()));
            }
        }
        LocalDate first = null, last = null;
        int days = 0, lastAssignment = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            LocalDate target = day;
            List<EmploymentSegment> applicable = segments.stream()
                    .filter(s -> !target.isBefore(s.start()) && !target.isAfter(s.end())).toList();
            if (applicable.size() > 1) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: AFFECTATION_CONTRAT employé="
                    + idEmploye + " au " + day + "; versions=" + applicable);
            if (applicable.size() == 1) {
                if (first == null) first = day;
                last = day; days++; lastAssignment = applicable.get(0).id();
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

    private void verifierUniciteContexte(int idEmploye, LocalDate date, Connection c) throws SQLException {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM contrat_travail ct JOIN ref_statut_rh s ON s.id_statut_rh=ct.id_statut_rh "
                + " WHERE ct.id_employe=? AND s.code='ACTIF' AND ct.date_debut<=? AND (ct.date_fin IS NULL OR ct.date_fin>=?)) AS contrats, "
                + "(SELECT COUNT(*) FROM employe_affectation_carriere a JOIN contrat_travail ct ON ct.id_contrat=a.id_contrat "
                + " JOIN ref_statut_rh s ON s.id_statut_rh=ct.id_statut_rh WHERE a.id_employe=? AND s.code='ACTIF' "
                + " AND a.date_debut<=? AND (a.date_fin IS NULL OR a.date_fin>=?) AND ct.date_debut<=? "
                + " AND (ct.date_fin IS NULL OR ct.date_fin>=?)) AS affectations";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye); ps.setDate(2, Date.valueOf(date)); ps.setDate(3, Date.valueOf(date));
            ps.setInt(4, idEmploye); ps.setDate(5, Date.valueOf(date)); ps.setDate(6, Date.valueOf(date));
            ps.setDate(7, Date.valueOf(date)); ps.setDate(8, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt("contrats") > 1) throw new IllegalStateException(
                        "AMBIGUOUS_CONFIGURATION: CONTRAT employé=" + idEmploye + " au " + date);
                if (rs.getInt("affectations") > 1) throw new IllegalStateException(
                        "AMBIGUOUS_CONFIGURATION: AFFECTATION_CONTRAT employé=" + idEmploye + " au " + date);
            }
        }
    }

    /**
     * Résout la liste de tous les salariés en poste ou actifs sur une période [debut, fin].
     */
    public List<EmploymentContext> listActiveEmploymentContexts(LocalDate debut, LocalDate fin, Connection c) throws SQLException {
        LocalDate dDebut = debut != null ? debut : LocalDate.now().withDayOfMonth(1);
        LocalDate dFin = fin != null ? fin : LocalDate.now();

        String sql = "SELECT " +
                "  e.id_employe, e.matricule, e.nom, e.prenom, " +
                "  TRIM(COALESCE(e.nom, '') || ' ' || COALESCE(e.prenom, '')) AS nom_complet, " +
                "  e.cin_numero, e.num_affiliation_cnaps, e.date_naissance, e.est_actif AS est_actif_emp, " +
                "  ct.id_contrat, ct.numero_contrat, rtc.code AS code_type_ct, rtc.libelle AS lib_type_ct, " +
                "  rsc.code AS code_statut_ct, ct.date_debut AS debut_ct, ct.date_fin AS fin_ct, " +
                "  rf.raison_sociale AS filiale_nom, " +
                "  (SELECT MIN(c_init.date_debut) FROM contrat_travail c_init WHERE c_init.id_employe = e.id_employe) AS debut_embauche_init, " +
                "  eac.id_affectation, eac.date_debut AS debut_aff, eac.date_fin AS fin_aff, eac.salaire_base, " +
                "  s.id_site, s.code_site, s.libelle AS site_nom, " +
                "  d.id_departement, d.libelle AS dept_nom, " +
                "  srv.id_service, srv.libelle AS srv_nom, " +
                "  f.id_fonction, f.code_fonction, f.libelle AS fonc_nom, f.dispense_pointage, " +
                "  cat.id_categorie_professionnelle, cat.code AS cat_code, cat.libelle AS cat_lib, " +
                "  cat.est_eligible_hs, cat.est_cadre, cat.est_eligible_exoneration_hs, " +
                "  mcp.id_mode_calcul_paie, mcp.code AS mcp_code, " +
                "  tri.code AS regime_code, " +
                "  COALESCE(tri.soumis_cnaps, TRUE) AS soumis_cnaps, " +
                "  COALESCE(tri.soumis_sante, TRUE) AS soumis_sante, " +
                "  COALESCE(tri.soumis_irsa, TRUE) AS soumis_irsa, " +
                "  COALESCE(tri.soumis_isi_5, FALSE) AS soumis_isi_5 " +
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
            ps.setDate(2, Date.valueOf(dDebut));
            ps.setDate(3, Date.valueOf(dFin));
            ps.setDate(4, Date.valueOf(dDebut));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs, dFin));
                }
            }
        }
        java.util.Set<Integer> verified = new java.util.HashSet<>();
        for (EmploymentContext context : list) {
            if (verified.add(context.idEmploye())) verifierUniciteContexte(context.idEmploye(), dFin, c);
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

        boolean enPoste = estActifEmp && idContrat != null && "ACTIF".equals(statutCt)
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
                rs.getBoolean("est_eligible_exoneration_hs"),
                (Integer) rs.getObject("id_mode_calcul_paie"),
                rs.getString("mcp_code"),

                rs.getString("regime_code"),
                rs.getBoolean("soumis_cnaps"),
                rs.getBoolean("soumis_sante"),
                rs.getBoolean("soumis_irsa"),
                rs.getBoolean("soumis_isi_5"),

                enPoste
        );
    }
}
