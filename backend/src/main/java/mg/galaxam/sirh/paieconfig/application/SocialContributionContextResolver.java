package mg.galaxam.sirh.paieconfig.application;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/** Résout les référentiels sociaux à la date métier du calcul qui l'appelle. */
public final class SocialContributionContextResolver {

    public record CompanyRates(
            BigDecimal cnapsEmployee, BigDecimal cnapsEmployer, BigDecimal fmfpEmployer,
            Integer cnapsOrganismeId, String cnapsOrganismeCode, Integer cnapsTypeCotisationId,
            Integer fmfpOrganismeId, String fmfpOrganismeCode, Integer fmfpTypeCotisationId
    ) {
        public CompanyRates(BigDecimal cnapsEmployee, BigDecimal cnapsEmployer, BigDecimal fmfpEmployer) {
            this(cnapsEmployee, cnapsEmployer, fmfpEmployer, null, "CNAPS", null, null, "FMFP", null);
        }
    }

    public record HealthRate(
            String code, BigDecimal employee, BigDecimal employer, boolean capped,
            Integer organismeId, String organismeCode, Integer typeCotisationId
    ) {
        public HealthRate(String code, BigDecimal employee, BigDecimal employer, boolean capped) {
            this(code, employee, employer, capped, null, code, null);
        }
    }

    public CompanyRates companyRates(LocalDate date, Connection connection) throws SQLException {
        String sql = "SELECT tc.id_type_cotisation, tc.code, tc.categorie, tc.taux_salarial, tc.taux_patronal, "
                + "tc.id_organisme, o.code AS organisme_code "
                + "FROM ref_type_cotisation tc "
                + "LEFT JOIN ref_organisme_social o ON tc.id_organisme = o.id_organisme "
                + "WHERE (tc.code IN ('CNAPS', 'FMFP') OR tc.categorie IN ('PREVOYANCE', 'FORMATION')) "
                + "AND tc.est_actif = true AND tc.date_debut <= ? AND (tc.date_fin IS NULL OR tc.date_fin >= ?)";
        BigDecimal cnapsEmployee = null, cnapsEmployer = null, fmfpEmployer = null;
        Integer cnapsOrgId = null, fmfpOrgId = null;
        String cnapsOrgCode = null, fmfpOrgCode = null;
        Integer cnapsTypeId = null, fmfpTypeId = null;
        boolean cnapsFound = false, fmfpFound = false;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(date));
            statement.setDate(2, Date.valueOf(date));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code"), category = rs.getString("categorie");
                    int typeId = rs.getInt("id_type_cotisation");
                    int orgId = rs.getInt("id_organisme");
                    Integer resolvedOrgId = rs.wasNull() ? null : orgId;
                    String resolvedOrgCode = rs.getString("organisme_code");

                    if ("CNAPS".equals(code) || "PREVOYANCE".equals(category)) {
                        if (cnapsFound) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: CNAPS au " + date);
                        cnapsEmployee = decimal(rs.getBigDecimal("taux_salarial"));
                        cnapsEmployer = decimal(rs.getBigDecimal("taux_patronal"));
                        cnapsFound = true;
                        cnapsTypeId = typeId;
                        cnapsOrgId = resolvedOrgId;
                        cnapsOrgCode = resolvedOrgCode != null ? resolvedOrgCode : "CNAPS";
                    } else if ("FMFP".equals(code) || "FORMATION".equals(category)) {
                        if (fmfpFound) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: FMFP au " + date);
                        fmfpEmployer = decimal(rs.getBigDecimal("taux_patronal"));
                        fmfpFound = true;
                        fmfpTypeId = typeId;
                        fmfpOrgId = resolvedOrgId;
                        fmfpOrgCode = resolvedOrgCode != null ? resolvedOrgCode : "FMFP";
                    }
                }
            }
        }
        if (!cnapsFound || cnapsEmployee == null || cnapsEmployer == null || !fmfpFound || fmfpEmployer == null)
            throw new IllegalStateException("MISSING_CONFIGURATION: COTISATIONS_ENTREPRISE au " + date);
        return new CompanyRates(cnapsEmployee, cnapsEmployer, fmfpEmployer,
                cnapsOrgId, cnapsOrgCode, cnapsTypeId,
                fmfpOrgId, fmfpOrgCode, fmfpTypeId);
    }

    public HealthRate healthRate(String siteCode, LocalDate date, Connection connection) throws SQLException {
        if (siteCode == null || siteCode.isBlank())
            throw new IllegalArgumentException("Code site obligatoire pour résoudre la cotisation santé.");
        String sql = "SELECT tc.id_type_cotisation, tc.code, tc.taux_salarial, tc.taux_patronal, sc.est_plafonne_sante, "
                + "COALESCE(sc.id_organisme, tc.id_organisme) AS id_organisme, "
                + "COALESCE(osc.code, otc.code, tc.code) AS organisme_code "
                + "FROM site_cotisation sc "
                + "JOIN ref_site s ON sc.id_site = s.id_site "
                + "JOIN ref_type_cotisation tc ON sc.id_type_cotisation = tc.id_type_cotisation "
                + "LEFT JOIN ref_organisme_social osc ON sc.id_organisme = osc.id_organisme "
                + "LEFT JOIN ref_organisme_social otc ON tc.id_organisme = otc.id_organisme "
                + "WHERE s.code_site = ? AND (tc.categorie = 'SANTE' OR tc.code IN ('OSTIE', 'OMSI', 'AMIT')) "
                + "AND sc.est_actif = true AND tc.date_debut <= ? "
                + "AND (tc.date_fin IS NULL OR tc.date_fin >= ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, siteCode);
            statement.setDate(2, Date.valueOf(date));
            statement.setDate(3, Date.valueOf(date));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                int typeId = rs.getInt("id_type_cotisation");
                int orgId = rs.getInt("id_organisme");
                Integer resolvedOrgId = rs.wasNull() ? null : orgId;
                String resolvedOrgCode = rs.getString("organisme_code");

                HealthRate rate = new HealthRate(
                        rs.getString("code"),
                        decimal(rs.getBigDecimal("taux_salarial")),
                        decimal(rs.getBigDecimal("taux_patronal")),
                        rs.getBoolean("est_plafonne_sante"),
                        resolvedOrgId,
                        resolvedOrgCode != null ? resolvedOrgCode : rs.getString("code"),
                        typeId
                );
                if (rs.next()) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: COTISATION_SANTE au "
                        + date + " site=" + siteCode);
                if (rate.employee() == null || rate.employer() == null)
                    throw new IllegalStateException("MISSING_CONFIGURATION: COTISATION_SANTE au "
                            + date + " site=" + siteCode + " taux manquant");
                return rate;
            }
        }
    }

    private static BigDecimal decimal(BigDecimal percent) {
        return percent == null ? null : percent.movePointLeft(2);
    }
}
