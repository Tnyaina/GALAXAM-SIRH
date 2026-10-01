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

        // Règle de paie confirmée par la DAF : toutes les majorations simultanées
        // s'ajoutent au taux horaire de base (jamais en cascade multiplicative).
        input.setSpecialPremiumCollisionPolicy(SpecialPremiumCollisionPolicy.CUMUL_ADDITIF);

        if (input.isSoumisIsi5()) {
            input.setTauxIsi(TaxConfigurationResolver.resolveIsiRate(targetDate, connection));
        }
        resolveOvertimeRates(input, configId, targetDate, connection);
        input.setTranchesIrsa(IrsaBracketResolver.resolve(configId, targetDate, connection));
        resolveCompanySocialContributions(input, targetDate, connection);
        resolveHealthContribution(input, targetDate, connection);
        resolveDynamicParameters(input, targetDate, connection);
    }

    private void resolveCompanySocialContributions(
            PayrollCalculationInput input, LocalDate targetDate, Connection connection) throws Exception {
        String sql = "SELECT code, categorie, taux_salarial, taux_patronal FROM ref_type_cotisation "
                + "WHERE (code IN ('CNAPS', 'FMFP') OR categorie IN ('PREVOYANCE', 'FORMATION')) AND est_actif = true "
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
                    String cat = resultSet.getString("categorie");
                    if ("CNAPS".equals(code) || "PREVOYANCE".equals(cat)) {
                        input.setTauxCnapsSalarial(toDecimalRate(resultSet.getBigDecimal("taux_salarial")));
                        input.setTauxCnapsPatronal(toDecimalRate(resultSet.getBigDecimal("taux_patronal")));
                        cnapsFound = true;
                    } else if ("FMFP".equals(code) || "FORMATION".equals(cat)) {
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

    void resolveHealthContribution(PayrollCalculationInput input, LocalDate targetDate,
            Connection connection) throws Exception {
        String sql = "SELECT tc.code, tc.taux_salarial, tc.taux_patronal, sc.est_plafonne_sante " +
                "FROM site_cotisation sc " +
                "JOIN ref_site s ON sc.id_site = s.id_site " +
                "JOIN ref_type_cotisation tc ON sc.id_type_cotisation = tc.id_type_cotisation " +
                "WHERE (s.code_site = ? OR UPPER(s.libelle) LIKE ?) " +
                "AND (tc.categorie = 'SANTE' OR tc.code IN ('OSTIE', 'OMSI')) " +
                "AND sc.est_actif = true AND tc.date_debut <= ? " +
                "AND (tc.date_fin IS NULL OR tc.date_fin >= ?) " +
                "ORDER BY tc.code, sc.id_site_cotisation LIMIT 2";
        boolean found = false;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindSite(input, ps);
            Date effectiveDate = Date.valueOf(targetDate);
            ps.setDate(3, effectiveDate);
            ps.setDate(4, effectiveDate);
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
        applyDynamicParameters(input, ParametreGlobal.loadEtatMapAtDate(Date.valueOf(targetDate), connection));
    }

    static void applyDynamicParameters(PayrollCalculationInput input,
            Map<String, ParametreGlobal.ParametreEtat> parameters) {
        var assiduite = ParametreGlobal.resolve(parameters, "SEUIL_RETARD_ASSIDUITE_MIN");
        input.setReglePrimeAssiduiteActive(assiduite.isActive());
        if (assiduite.isActive()) input.setSeuilRetardAssiduiteMinutes(nonNegativeInt(assiduite, "SEUIL_RETARD_ASSIDUITE_MIN"));
        else if (assiduite.isMissing()) throw missingParameter("SEUIL_RETARD_ASSIDUITE_MIN");

        var montantAssiduite = ParametreGlobal.resolve(parameters, "MONTANT_PRIME_ASSIDUITE");
        if (montantAssiduite.isActive()) {
            input.setPrimeAssiduiteForfait(nonNegativeDecimal(montantAssiduite, "MONTANT_PRIME_ASSIDUITE"));
        } else if (montantAssiduite.isDisabled()) {
            input.setPrimeAssiduiteForfait(BigDecimal.ZERO);
        } else {
            // Si non trouvé (missing) : pas de fallback, on ne donne pas (0 Ar)
            if (input.getPrimeAssiduiteForfait() == null) {
                input.setPrimeAssiduiteForfait(BigDecimal.ZERO);
            }
        }

        String[] malusCodes = {"SEUIL_RETARD_PENALITE_PP_MIN", "SEUIL_CONGES_PENALITE_PP_JOURS", "TAUX_PENALITE_PP"};
        boolean malusActive = true;
        for (String code : malusCodes) {
            var state = ParametreGlobal.resolve(parameters, code);
            if (state.isMissing()) throw missingParameter(code);
            if (state.isDisabled()) malusActive = false;
        }
        input.setRegleMalusPerformanceActive(malusActive);
        if (malusActive) {
            input.setSeuilRetardMalusPerformanceMinutes(nonNegativeInt(
                    ParametreGlobal.resolve(parameters, malusCodes[0]), malusCodes[0]));
            input.setSeuilCongesMalusPerformanceJours(nonNegativeDecimal(
                    ParametreGlobal.resolve(parameters, malusCodes[1]), malusCodes[1]));
            input.setTauxMalusPerformance(nonNegativeDecimal(
                    ParametreGlobal.resolve(parameters, malusCodes[2]), malusCodes[2]));
        }

        var exemption = ParametreGlobal.resolve(parameters, "EXONERATION_HS_IRSA_ACTIVE");
        var ceiling = ParametreGlobal.resolve(parameters, "PLAFOND_MENSUEL_HS_EXONEREES");
        if (exemption.isMissing()) throw missingParameter("EXONERATION_HS_IRSA_ACTIVE");
        if (ceiling.isMissing()) throw missingParameter("PLAFOND_MENSUEL_HS_EXONEREES");
        if (exemption.isDisabled() || ceiling.isDisabled()) {
            input.setExonerationHsIrsaActive(false);
            input.setPlafondMensuelHsExonerees(BigDecimal.ZERO);
        } else {
            String value = exemption.valeur() == null ? "" : exemption.valeur().trim();
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw invalidParameter("EXONERATION_HS_IRSA_ACTIVE");
            }
            boolean active = Boolean.parseBoolean(value);
            input.setExonerationHsIrsaActive(active);
            input.setPlafondMensuelHsExonerees(active
                    ? positiveDecimal(ceiling, "PLAFOND_MENSUEL_HS_EXONEREES") : BigDecimal.ZERO);
        }
    }

    private static int nonNegativeInt(ParametreGlobal.ParametreResolution state, String code) {
        try {
            int value = Integer.parseInt(state.valeur().trim());
            if (value >= 0) return value;
        } catch (RuntimeException ignored) { }
        throw invalidParameter(code);
    }

    private static BigDecimal nonNegativeDecimal(ParametreGlobal.ParametreResolution state, String code) {
        try {
            BigDecimal value = new BigDecimal(state.valeur().trim());
            if (value.signum() >= 0) return value;
        } catch (RuntimeException ignored) { }
        throw invalidParameter(code);
    }

    private static BigDecimal positiveDecimal(ParametreGlobal.ParametreResolution state, String code) {
        BigDecimal value = nonNegativeDecimal(state, code);
        if (value.signum() == 0) throw invalidParameter(code);
        return value;
    }

    private static IllegalStateException missingParameter(String code) {
        return new IllegalStateException("Paramètre de paie absent : " + code);
    }

    private static IllegalStateException invalidParameter(String code) {
        return new IllegalStateException("Paramètre de paie actif invalide : " + code);
    }

    private void bindSite(PayrollCalculationInput input, PreparedStatement statement) throws Exception {
        statement.setString(1, input.getCodeSite() != null ? input.getCodeSite() : "");
        statement.setString(2, "%" + (input.getNomSite() != null ? input.getNomSite().toUpperCase() : "") + "%");
    }
}
