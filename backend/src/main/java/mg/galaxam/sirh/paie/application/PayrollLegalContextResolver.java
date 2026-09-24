package mg.galaxam.sirh.paie.application;


import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.IrsaBracketResolver;
import mg.galaxam.sirh.paie.domain.engine.OvertimePayCalculator;
import mg.galaxam.sirh.paie.domain.engine.SpecialPremiumCollisionPolicy;
import mg.galaxam.sirh.paie.domain.engine.TaxConfigurationResolver;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import mg.galaxam.sirh.paieconfig.persistence.RefTauxMajoration;

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
        Boolean exoActive = config.getExonerationHsIrsaActive();
        boolean estActif = Boolean.TRUE.equals(exoActive);
        input.setExonerationHsIrsaActive(estActif);
        input.setPlafondMensuelHsExonerees(estActif && config.getPlafondMensuelHsExonerees() != null ? config.getPlafondMensuelHsExonerees() : BigDecimal.ZERO);
        input.setIdConfigLegale(configId);

        if (input.isSoumisIsi5()) {
            input.setTauxIsi(TaxConfigurationResolver.resolveIsiRate(targetDate, connection));
        }
        resolveOvertimeRates(input, configId, targetDate, connection);
        input.setTranchesIrsa(IrsaBracketResolver.resolve(configId, targetDate, connection));
        resolveCompanySocialContributions(input, targetDate, connection);
        resolveHealthContribution(input, connection);
        resolveDynamicParameters(input, targetDate, connection);
    }

    private void resolveCompanySocialContributions(
            PayrollCalculationInput input, LocalDate targetDate, Connection connection) throws Exception {
        String sql = "SELECT code, taux_salarial, taux_patronal FROM ref_type_cotisation "
                + "WHERE code IN ('CNAPS', 'FMFP') AND est_actif = true "
                + "AND date_debut <= ? AND (date_fin IS NULL OR date_fin >= ?) ORDER BY code";
        boolean cnapsFound = false;
        boolean fmfpFound = false;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Date date = Date.valueOf(targetDate);
            statement.setDate(1, date);
            statement.setDate(2, date);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String code = resultSet.getString("code");
                    if ("CNAPS".equals(code)) {
                        input.setTauxCnapsSalarial(toDecimalRate(resultSet.getBigDecimal("taux_salarial")));
                        input.setTauxCnapsPatronal(toDecimalRate(resultSet.getBigDecimal("taux_patronal")));
                        cnapsFound = true;
                    } else if ("FMFP".equals(code)) {
                        input.setTauxFmfpPatronal(toDecimalRate(resultSet.getBigDecimal("taux_patronal")));
                        fmfpFound = true;
                    }
                }
            }
        }
        if (input.isSoumisCnaps() && (!cnapsFound || !fmfpFound)) {
            throw PayrollConfigurationGuard.missing("COTISATIONS_ENTREPRISE", input.getIdEmploye(), targetDate.toString());
        }
    }

    private static BigDecimal toDecimalRate(BigDecimal percentage) {
        return percentage == null ? null : percentage.movePointLeft(2);
    }

    private void resolveOvertimeRates(
            PayrollCalculationInput input, int configId, LocalDate targetDate, Connection connection) throws Exception {
        List<PaieConfigLegale> allConfigs = PaieConfigLegale.listAll(connection);
        Map<Integer, List<RefTauxMajoration>> ratesByConfig = new java.util.HashMap<>();
        for (RefTauxMajoration r : RefTauxMajoration.listAll(connection)) {
            if (r.getIdConfigLegale() != null) {
                ratesByConfig.computeIfAbsent(r.getIdConfigLegale(), k -> new java.util.ArrayList<>()).add(r);
            }
        }

        List<RefTauxMajoration> currentConfigRates = ratesByConfig.getOrDefault(configId, List.of());
        input.setMajorationHs30(RefTauxMajoration.resolveApplicableFromList(currentConfigRates, "HS_30", targetDate).getTaux());
        input.setMajorationHs50(RefTauxMajoration.resolveApplicableFromList(currentConfigRates, "HS_50", targetDate).getTaux());
        input.setMajorationNuit(RefTauxMajoration.resolveApplicableFromList(currentConfigRates, "NUIT", targetDate).getTaux());
        input.setMajorationDimanche(RefTauxMajoration.resolveApplicableFromList(currentConfigRates, "DIMANCHE", targetDate).getTaux());
        input.setMajorationFerie(RefTauxMajoration.resolveApplicableFromList(currentConfigRates, "FERIE", targetDate).getTaux());

        input.setTemporalRateResolver(date -> {
            try {
                int activeCfgId = resolveConfigIdAtDate(allConfigs, date, configId);
                List<RefTauxMajoration> configRates = ratesByConfig.getOrDefault(activeCfgId, List.of());

                BigDecimal hs30 = RefTauxMajoration.resolveApplicableFromList(configRates, "HS_30", date).getTaux();
                BigDecimal hs50 = RefTauxMajoration.resolveApplicableFromList(configRates, "HS_50", date).getTaux();
                BigDecimal nuit = RefTauxMajoration.resolveApplicableFromList(configRates, "NUIT", date).getTaux();
                BigDecimal dim = RefTauxMajoration.resolveApplicableFromList(configRates, "DIMANCHE", date).getTaux();
                BigDecimal ferie = RefTauxMajoration.resolveApplicableFromList(configRates, "FERIE", date).getTaux();
                return new OvertimePayCalculator.Rates(
                        hs30,
                        hs50,
                        nuit,
                        null,
                        dim,
                        ferie,
                        input.getSpecialPremiumCollisionPolicy() != null ? input.getSpecialPremiumCollisionPolicy() : SpecialPremiumCollisionPolicy.UNRESOLVED,
                        null
                );
            } catch (Exception e) {
                throw new IllegalStateException("Erreur de résolution des taux de majoration à la date " + date + " : " + e.getMessage(), e);
            }
        });
    }

    private static int resolveConfigIdAtDate(List<PaieConfigLegale> configs, LocalDate date, int defaultId) {
        if (date == null || configs == null) return defaultId;
        for (PaieConfigLegale cfg : configs) {
            LocalDate from = cfg.getDateDebut() != null ? cfg.getDateDebut().toLocalDate() : LocalDate.MIN;
            LocalDate to = cfg.getDateFin() != null ? cfg.getDateFin().toLocalDate() : LocalDate.MAX;
            if (!date.isBefore(from) && !date.isAfter(to)) {
                return cfg.getIdConfigLegale();
            }
        }
        return defaultId;
    }

    private void resolveHealthContribution(PayrollCalculationInput input, Connection connection) throws Exception {
        String sql = "SELECT tc.code, tc.taux_salarial, tc.taux_patronal, sc.est_plafonne_sante " +
                "FROM site_cotisation sc " +
                "JOIN ref_site s ON sc.id_site = s.id_site " +
                "JOIN ref_type_cotisation tc ON sc.id_type_cotisation = tc.id_type_cotisation " +
                "WHERE (s.code_site = ? OR UPPER(s.libelle) LIKE ?) AND tc.code IN ('OSTIE', 'OMSI') AND sc.est_actif = true " +
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
        if (parameters.containsKey("EXONERATION_HS_IRSA_ACTIVE")) {
            boolean active = Boolean.parseBoolean(parameters.get("EXONERATION_HS_IRSA_ACTIVE"));
            input.setExonerationHsIrsaActive(active);
            if (!active) {
                input.setPlafondMensuelHsExonerees(BigDecimal.ZERO);
            }
        }
        if (parameters.containsKey("PLAFOND_MENSUEL_HS_EXONEREES")) {
            if (input.isExonerationHsIrsaActive()) {
                try {
                    input.setPlafondMensuelHsExonerees(new BigDecimal(parameters.get("PLAFOND_MENSUEL_HS_EXONEREES")));
                } catch (Exception ignored) {
                    input.setPlafondMensuelHsExonerees(BigDecimal.ZERO);
                }
            }
        }
    }

    private void bindSite(PayrollCalculationInput input, PreparedStatement statement) throws Exception {
        statement.setString(1, input.getCodeSite() != null ? input.getCodeSite() : "");
        statement.setString(2, "%" + (input.getNomSite() != null ? input.getNomSite().toUpperCase() : "") + "%");
    }
}
