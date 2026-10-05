package mg.galaxam.sirh.paie.application;


import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
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
import mg.galaxam.sirh.paieconfig.application.SocialContributionContextResolver;

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
            throw PayrollConfigurationGuard.missing("CONFIGURATION_LEGALE", input.getIdEmploye(),
                    targetDate + "; versions=" + PaieConfigLegale.listAll(connection).stream()
                            .map(cfg -> "[" + cfg.getDateDebut() + ", " + cfg.getDateFin() + "]").toList());
        }

        int configId = config.getIdConfigLegale();
        if (config.getSmeNational() == null || config.getSmeNational().signum() <= 0)
            throw PayrollConfigurationGuard.missing("SME_NATIONAL", input.getIdEmploye(), targetDate.toString());
        if (config.getPlafondAvanceEmployeurPct() == null)
            throw PayrollConfigurationGuard.missing("PLAFOND_AVANCE_EMPLOYEUR", input.getIdEmploye(), targetDate.toString());
        if (config.getQuotiteCessiblePct() == null)
            throw PayrollConfigurationGuard.missing("QUOTITE_CESSIBLE", input.getIdEmploye(), targetDate.toString());
        if (input.isSoumisIrsa() && (config.getMinimumPerceptionIrsa() == null
                || config.getDeductionChargeFamille() == null))
            throw PayrollConfigurationGuard.missing("PARAMETRES_IRSA", input.getIdEmploye(), targetDate.toString());
        input.setSmeNational(config.getSmeNational());
        if (config.getPlafondCotisationMensuel() == null || config.getPlafondCotisationMensuel().signum() <= 0)
            throw PayrollConfigurationGuard.missing("PLAFOND_COTISATION_MENSUEL", input.getIdEmploye(), targetDate.toString());
        input.setPlafondCotisationMensuel(config.getPlafondCotisationMensuel());
        input.setPlafondAvanceEmployeur(config.getPlafondAvanceEmployeurPct().movePointLeft(2));
        input.setQuotiteCessible(config.getQuotiteCessiblePct().movePointLeft(2));
        input.setMinimumPerceptionIrsa(config.getMinimumPerceptionIrsa());
        input.setDeductionParEnfant(config.getDeductionChargeFamille());
        if (config.getDiviseurMensuelHeures() == null
                || config.getDiviseurMensuelHeures().compareTo(BigDecimal.ZERO) <= 0)
            throw PayrollConfigurationGuard.missing("DIVISEUR_MENSUEL_HEURES", input.getIdEmploye(), targetDate.toString());
        input.setDiviseurMensuelHeures(config.getDiviseurMensuelHeures());
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
        if (!input.isSoumisCnaps()) return;
        var rates = new SocialContributionContextResolver().companyRates(targetDate, connection);
        input.setTauxCnapsSalarial(rates.cnapsEmployee());
        input.setTauxCnapsPatronal(rates.cnapsEmployer());
        input.setTauxFmfpPatronal(rates.fmfpEmployer());
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
                int activeCfgId = resolveConfigIdAtDate(allConfigs, date);
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
                        dim,
                        ferie,
                        input.getSpecialPremiumCollisionPolicy() != null ? input.getSpecialPremiumCollisionPolicy() : SpecialPremiumCollisionPolicy.UNRESOLVED
                );
            } catch (Exception e) {
                throw new IllegalStateException("Erreur de résolution des taux de majoration à la date " + date + " : " + e.getMessage(), e);
            }
        });
    }

    static int resolveConfigIdAtDate(List<PaieConfigLegale> configs, LocalDate date) {
        if (date == null || configs == null) {
            throw new IllegalArgumentException("Date métier et configurations légales obligatoires.");
        }
        List<PaieConfigLegale> matches = configs.stream().filter(cfg ->
                cfg.getDateDebut() != null && !date.isBefore(cfg.getDateDebut().toLocalDate())
                && (cfg.getDateFin() == null || !date.isAfter(cfg.getDateFin().toLocalDate()))).toList();
        String existing = configs.stream().map(cfg -> cfg.getIdConfigLegale() + ": ["
                + cfg.getDateDebut() + ", " + cfg.getDateFin() + "]").toList().toString();
        if (matches.isEmpty()) throw new IllegalStateException(
                "MISSING_CONFIGURATION: CONFIGURATION_LEGALE au " + date + "; versions=" + existing);
        if (matches.size() > 1) throw new IllegalStateException(
                "AMBIGUOUS_CONFIGURATION: CONFIGURATION_LEGALE au " + date + "; versions=" + existing);
        return matches.get(0).getIdConfigLegale();
    }

    void resolveHealthContribution(PayrollCalculationInput input, LocalDate targetDate,
            Connection connection) throws Exception {
        if (!input.isSoumisSante()) return;
        var rate = new SocialContributionContextResolver().healthRate(input.getCodeSite(), targetDate, connection);
        if (rate == null)
            throw PayrollConfigurationGuard.missing("COTISATION_SANTE", input.getIdEmploye(),
                    targetDate + " site=" + input.getCodeSite());
        input.setCodeSanteOrgane(rate.code());
        input.setTauxSanteSalarial(rate.employee());
        input.setTauxSantePatronal(rate.employer());
        input.setEstPlafonneSante(rate.capped());
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
        } else throw missingParameter("MONTANT_PRIME_ASSIDUITE");

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

}
