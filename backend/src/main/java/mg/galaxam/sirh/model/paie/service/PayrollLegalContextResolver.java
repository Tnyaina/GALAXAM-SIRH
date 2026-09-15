package mg.galaxam.sirh.model.paie.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Component;

import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.IrsaBracketResolver;
import mg.galaxam.sirh.model.paie.engine.TaxConfigurationResolver;
import mg.galaxam.sirh.model.paieconfig.PaieConfigLegale;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import mg.galaxam.sirh.model.paieconfig.RefTauxMajoration;

/** Résout, à la date métier de la période, l'unique contexte légal consommé par le moteur de paie. */
@Component
public class PayrollLegalContextResolver {

    public void resolve(PayrollCalculationInput input, Connection connection) throws Exception {
        if (input.getDateFinPeriode() == null) {
            throw PayrollConfigurationGuard.missing("PERIODE_PAIE", input.getIdEmploye(), "dateFinPeriode");
        }
        LocalDate targetDate = input.getDateFinPeriode().toLocalDate();
        PaieConfigLegale config = PaieConfigLegale.findActiveAt(connection, targetDate);
        if (config == null) {
            throw PayrollConfigurationGuard.missing("CONFIGURATION_LEGALE", input.getIdEmploye(), targetDate.toString());
        }

        int configId = config.getIdConfigLegale();
        input.setSmeNational(config.getSmeNational());
        input.setMinimumPerceptionIrsa(config.getMinimumPerceptionIrsa());
        input.setDeductionParEnfant(config.getDeductionChargeFamille());
        if (config.getDiviseurMensuelHeures() != null
                && config.getDiviseurMensuelHeures().compareTo(BigDecimal.ZERO) > 0) {
            input.setDiviseurMensuelHeures(config.getDiviseurMensuelHeures());
        }
        input.setIdConfigLegale(configId);

        if (input.isSoumisIsi5()) {
            input.setTauxIsi(TaxConfigurationResolver.resolveIsiRate(targetDate, connection));
        }
        resolveOvertimeRates(input, configId, targetDate, connection);
        input.setTranchesIrsa(IrsaBracketResolver.resolve(configId, targetDate, connection));
        resolveHealthContribution(input, connection);
        resolveSiteAllowances(input, connection);
        resolveDynamicParameters(input, targetDate, connection);
    }

    private void resolveOvertimeRates(
            PayrollCalculationInput input, int configId, LocalDate targetDate, Connection connection) throws Exception {
        input.setMajorationHs30(RefTauxMajoration.resolveApplicable(configId, "HS_30", targetDate, connection).getTaux());
        input.setMajorationHs50(RefTauxMajoration.resolveApplicable(configId, "HS_50", targetDate, connection).getTaux());
        input.setMajorationNuit(RefTauxMajoration.resolveApplicable(configId, "NUIT", targetDate, connection).getTaux());
        input.setMajorationFerie(RefTauxMajoration.resolveApplicable(configId, "FERIE", targetDate, connection).getTaux());
    }

    private void resolveHealthContribution(PayrollCalculationInput input, Connection connection) throws Exception {
        String sql = "SELECT tc.code, tc.taux_salarial, tc.taux_patronal, sc.est_plafonne_sante " +
                "FROM site_cotisation sc " +
                "JOIN ref_site s ON sc.id_site = s.id_site " +
                "JOIN ref_type_cotisation tc ON sc.id_type_cotisation = tc.id_type_cotisation " +
                "WHERE (s.code_site = ? OR UPPER(s.libelle) LIKE ?) AND tc.code != 'CNAPS' AND sc.est_actif = true " +
                "ORDER BY tc.code, sc.id_site_cotisation LIMIT 2";
        boolean found = false;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindSite(input, ps);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    input.setCodeSanteOrgane(rs.getString("code"));
                    BigDecimal employeeRate = rs.getBigDecimal("taux_salarial");
                    BigDecimal employerRate = rs.getBigDecimal("taux_patronal");
                    input.setTauxSanteSalarial(employeeRate == null ? null : employeeRate.movePointLeft(2));
                    input.setTauxSantePatronal(employerRate == null ? null : employerRate.movePointLeft(2));
                    input.setEstPlafonneSante(rs.getBoolean("est_plafonne_sante"));
                    found = true;
                    if (rs.next()) {
                        throw PayrollConfigurationGuard.missing(
                                "COTISATION_SANTE_AMBIGUE", input.getIdEmploye(), input.getCodeSite());
                    }
                }
            }
        }
        if (input.isSoumisSante() && !found) {
            throw PayrollConfigurationGuard.missing("COTISATION_SANTE", input.getIdEmploye(), input.getCodeSite());
        }
    }

    private void resolveSiteAllowances(PayrollCalculationInput input, Connection connection) throws Exception {
        String sql = "SELECT i.code, sc.montant_reference " +
                "FROM site_indemnite_config sc " +
                "JOIN ref_site s ON sc.id_site = s.id_site " +
                "JOIN ref_indemnite i ON sc.id_indemnite = i.id_indemnite " +
                "WHERE (s.code_site = ? OR UPPER(s.libelle) LIKE ?) AND sc.est_actif = true";
        BigDecimal transport = BigDecimal.ZERO;
        BigDecimal meal = BigDecimal.ZERO;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindSite(input, ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code");
                    BigDecimal amount = rs.getBigDecimal("montant_reference");
                    if ("DEPLACEMENT".equalsIgnoreCase(code) || "TRANSPORT".equalsIgnoreCase(code)) {
                        transport = amount != null ? amount : BigDecimal.ZERO;
                    } else if ("REPAS".equalsIgnoreCase(code)) {
                        meal = amount != null ? amount : BigDecimal.ZERO;
                    }
                }
            }
        }
        input.setForfaitTransportSite(transport);
        input.setForfaitRepasSite(meal);
    }

    private void resolveDynamicParameters(
            PayrollCalculationInput input, LocalDate targetDate, Connection connection) throws Exception {
        Map<String, String> parameters = ParametreGlobal.loadActiveMapAtDate(Date.valueOf(targetDate), connection);
        input.setSeuilRetardAssiduiteMinutes(ParametreGlobal.getInt(parameters, "SEUIL_RETARD_ASSIDUITE_MIN", 45));
        input.setSeuilRetardMalusPerformanceMinutes(
                ParametreGlobal.getInt(parameters, "SEUIL_RETARD_PENALITE_PP_MIN", 70));
        input.setSeuilCongesMalusPerformanceJours(
                new BigDecimal(parameters.getOrDefault("SEUIL_CONGES_PENALITE_PP_JOURS", "1.0")));
        try {
            input.setTauxMalusPerformance(new BigDecimal(parameters.getOrDefault("TAUX_PENALITE_PP", "0.25")));
        } catch (NumberFormatException ignored) {
            input.setTauxMalusPerformance(new BigDecimal("0.25"));
        }
    }

    private void bindSite(PayrollCalculationInput input, PreparedStatement statement) throws Exception {
        statement.setString(1, input.getCodeSite() != null ? input.getCodeSite() : "");
        statement.setString(2, "%" + (input.getNomSite() != null ? input.getNomSite().toUpperCase() : "") + "%");
    }
}
