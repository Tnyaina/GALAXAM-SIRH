package mg.galaxam.sirh.paie.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.conge.application.CongeLotValorisationService;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
import mg.galaxam.sirh.contrat.domain.AncienneteEngine;
import mg.galaxam.sirh.contrat.persistence.EmployeEnfant;
import mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.PayrollOvertimeSettlement;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import mg.galaxam.sirh.paie.persistence.PaiePretEcheance;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier;

/**
 * Source unique d'assemblage des données applicables au calcul d'un bulletin.
 * Chaque étape reçoit le même PayrollCalculationInput et la même transaction JDBC.
 * Les dates de résolution restent celles de chaque domaine ; le moteur ne lit pas la base.
 */
final class PayrollCalculationContextAssembler {
    private final EmploymentContextResolver employmentContextResolver;
    private final PayrollLegalContextResolver payrollLegalContextResolver;
    private final CongeService congeService;
    private final CongeLotValorisationService congeLotValorisationService;

    PayrollCalculationContextAssembler(EmploymentContextResolver employmentContextResolver,
            PayrollLegalContextResolver payrollLegalContextResolver, CongeService congeService,
            CongeLotValorisationService congeLotValorisationService) {
        this.employmentContextResolver = employmentContextResolver;
        this.payrollLegalContextResolver = payrollLegalContextResolver;
        this.congeService = congeService;
        this.congeLotValorisationService = congeLotValorisationService;
    }

    PayrollCalculationInput assemble(Integer idEmploye, Integer idPeriodePaie,
            boolean simulation, Connection c) throws Exception {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(idEmploye);
        in.setIdPeriodePaie(idPeriodePaie);
        in.setSimulation(simulation);
        chargerPeriode(in, idPeriodePaie, c);
        chargerEmploiEtSalaire(in, idEmploye, c);
        chargerBanque(in, idEmploye, c);
        chargerFamilleFiscale(in, idEmploye, c);
        chargerPointage(in, idEmploye, idPeriodePaie, simulation, c);
        chargerConges(in, idEmploye, c);
        chargerReglesLegales(in, c);
        chargerMouvements(in, idEmploye, idPeriodePaie, simulation, c);
        chargerPret(in, idEmploye, c);
        verifierContexteAssemble(in);
        return in;
    }

    private void verifierContexteAssemble(PayrollCalculationInput in) {
        Integer employe = in.getIdEmploye();
        if (in.getDateDebutPeriode() == null || in.getDateFinPeriode() == null
                || in.getDateFinPeriode().before(in.getDateDebutPeriode()))
            throw PayrollConfigurationGuard.missing("PERIODE_PAIE", employe, "bornes");
        if (in.getAttendanceInput() == null)
            throw PayrollConfigurationGuard.missing("POINTAGE_GELE", employe, in.getDateFinPeriode().toString());
        if (in.getIdConfigLegale() == null || in.getTemporalRateResolver() == null)
            throw PayrollConfigurationGuard.missing("CONFIGURATION_LEGALE", employe, in.getDateFinPeriode().toString());
        if (in.getSalaireParJour().isEmpty())
            throw PayrollConfigurationGuard.missing("SALAIRE", employe, in.getDateFinPeriode().toString());
        in.getSalaireBaseCampagne(); // vérifie la couverture du salaire sur la campagne ou l'entrée/sortie
        exigerValeur(in.getCodeSite(), "SITE", employe);
        exigerValeur(in.getCategorieProfessionnelle(), "CATEGORIE_PROFESSIONNELLE", employe);
        exigerValeur(in.getModeCalculPaie(), "MODE_CALCUL_PAIE", employe);
        exigerValeur(in.getRegimeIntervenant(), "TYPE_INTERVENANT", employe);
        if (in.isSoumisCnaps() && (in.getTauxCnapsSalarialDecimal() == null
                || in.getTauxCnapsPatronalDecimal() == null || in.getTauxFmfpPatronalDecimal() == null))
            throw PayrollConfigurationGuard.missing("COTISATIONS_ENTREPRISE", employe, in.getDateFinPeriode().toString());
        if (in.isSoumisSante() && (in.getTauxSanteSalarialDecimal() == null
                || in.getTauxSantePatronalDecimal() == null))
            throw PayrollConfigurationGuard.missing("COTISATION_SANTE", employe, in.getDateFinPeriode().toString());
        if (in.isSoumisIrsa() && (in.getTranchesIrsa() == null || in.getTranchesIrsa().isEmpty()))
            throw PayrollConfigurationGuard.missing("BAREME_IRSA", employe, in.getDateFinPeriode().toString());
    }

    private static String exigerValeur(String valeur, String type, Integer employe) {
        if (valeur == null || valeur.isBlank()) throw PayrollConfigurationGuard.missing(type, employe, null);
        return valeur;
    }

    private void chargerPeriode(PayrollCalculationInput in, Integer idPeriodePaie, Connection c) throws Exception {
        // 1. Données de la période
        String sqlPeriode = "SELECT id_periode_paie, code, libelle, date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlPeriode)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    in.setCodePeriode(rs.getString("code"));
                    in.setDateDebutPeriode(rs.getDate("date_debut"));
                    in.setDateFinPeriode(rs.getDate("date_fin"));
                } else {
                    throw new IllegalArgumentException("Période de paie introuvable pour l'id: " + idPeriodePaie);
                }
            }
        }
    }

    private void chargerEmploiEtSalaire(PayrollCalculationInput in, Integer idEmploye, Connection c) throws Exception {
        // 2. La couverture d'emploi choisit l'affectation applicable ; cette lecture ne
        // sélectionne que ses attributs de bulletin, sans refaire une résolution concurrente.
        Date dateDebutPeriodeSql = in.getDateDebutPeriode();
        Date dateFinPeriodeSql = in.getDateFinPeriode();
        if (dateDebutPeriodeSql == null || dateFinPeriodeSql == null) {
            throw new IllegalStateException("Les dates de début et de fin de période sont obligatoires pour résoudre l'emploi.");
        }
        var employmentCoverage = employmentContextResolver.resolveCoverage(idEmploye,
                dateDebutPeriodeSql.toLocalDate(), dateFinPeriodeSql.toLocalDate(), c);
        LocalDate dateResolutionEmploi = employmentCoverage.lastDay();
        var emploi = employmentContextResolver.resolveContext(idEmploye, dateResolutionEmploi, c);
        if (emploi == null || emploi.idAffectation() == null
                || emploi.idAffectation() != employmentCoverage.lastAssignmentId()
                || emploi.idContrat() == null) {
            throw PayrollConfigurationGuard.missing("AFFECTATION_CONTRAT", idEmploye,
                    dateResolutionEmploi.toString());
        }
        in.setMatricule(emploi.matricule());
        in.setNomPrenom(emploi.nomComplet());
        in.setCinNumero(emploi.cinNumero());
        in.setCinDateDelivrance(emploi.cinDateDelivrance() == null ? null : Date.valueOf(emploi.cinDateDelivrance()));
        in.setCinLieuDelivrance(emploi.cinLieuDelivrance());
        in.setNumeroCnaps(emploi.numAffiliationCnaps());
        in.setIntitulePoste(emploi.fonctionNom() == null ? "Collaborateur" : emploi.fonctionNom());
        in.setNomDepartement(emploi.departementNom());
        in.setNomSite(exigerValeur(emploi.nomSite(), "SITE", idEmploye));
        in.setCodeSite(exigerValeur(emploi.codeSite(), "SITE", idEmploye));
        in.setNomFiliale(exigerValeur(emploi.filialeNom(), "FILIALE", idEmploye));
        if ("PRESTATION".equalsIgnoreCase(emploi.typeContratCode())
                || "CONSULTANT".equalsIgnoreCase(emploi.regimeCode())
                || emploi.soumisIsi5()) {
            throw new IllegalArgumentException("Un prestataire indépendant ne peut pas recevoir un bulletin de paie. Utilisez le parcours de facturation fournisseur.");
        }
        in.setRegimeIntervenant(exigerValeur(emploi.regimeCode(), "TYPE_INTERVENANT", idEmploye));
        in.setSoumisCnaps(emploi.soumisCnaps());
        in.setSoumisSante(emploi.soumisSante());
        in.setSoumisIrsa(emploi.soumisIrsa());
        in.setSoumisIsi5(false);
        in.setCategorieProfessionnelle(exigerValeur(emploi.categorieProCode(),
                "CATEGORIE_PROFESSIONNELLE", idEmploye));
        in.setCategorieEligibleExonerationHs(emploi.estEligibleExonerationHs());
        in.setModeCalculPaie(exigerValeur(emploi.modeCalculPaieCode(), "MODE_CALCUL_PAIE", idEmploye));
        in.setSalaireBaseContractuel(emploi.salaireBase());
        if (emploi.dateEmbaucheInitiale() == null)
            throw PayrollConfigurationGuard.missing("DATE_EMBAUCHE", idEmploye, dateResolutionEmploi.toString());
        in.setDateEmbauche(Date.valueOf(emploi.dateEmbaucheInitiale()));
        var anciennete = new AncienneteEngine().calculerAnciennete(
                emploi.idContrat(), dateResolutionEmploi, c);
        if (anciennete.nombreContratsChaine() == 0)
            throw PayrollConfigurationGuard.missing("ANCIENNETE_CONTRAT", idEmploye, dateResolutionEmploi.toString());
        in.setAncienneteMois(anciennete.moisEffectifs());

        // Une mutation interne ne constitue pas une entrée du salarié.
        boolean partial = employmentCoverage.firstDay().isAfter(dateDebutPeriodeSql.toLocalDate())
                || employmentCoverage.lastDay().isBefore(dateFinPeriodeSql.toLocalDate());
        in.setEntreeSortieEnCoursDeMois(partial);
        in.setJoursSousContrat(partial ? BigDecimal.valueOf(employmentCoverage.days()) : null);

        in.setSalaireParJour(employmentContextResolver.resolveSalaryByDay(idEmploye,
                dateDebutPeriodeSql.toLocalDate(), dateFinPeriodeSql.toLocalDate(), c));
    }

    private void chargerBanque(PayrollCalculationInput in, Integer idEmploye, Connection c) throws Exception {
        // 3. Coordonnées bancaires
        String sqlBanque = "SELECT ecb.num_compte_rib, rb.nom_banque " +
                "FROM employe_compte_banque ecb " +
                "LEFT JOIN ref_banque rb ON ecb.id_banque = rb.id_banque " +
                "WHERE ecb.id_employe = ? AND ecb.est_principal = true LIMIT 1";

        try (PreparedStatement ps = c.prepareStatement(sqlBanque)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    in.setBanqueRib(rs.getString("num_compte_rib"));
                    in.setBanqueNom(rs.getString("nom_banque"));
                    in.setModePaiement("VIREMENT");
                }
            }
        }
    }

    private void chargerFamilleFiscale(PayrollCalculationInput in, Integer idEmploye, Connection c) throws Exception {
        // 4. Enfants à charge éligibles fiscalement (Règle des 21 ans à la date de fin de période)
        Date dateRefFiscale = in.getDateFinPeriode();
        int nbEnfants = EmployeEnfant.nbEnfantsEligibles(idEmploye, dateRefFiscale, c);
        in.setNbEnfantsEligibles(nbEnfants);
    }

    private void chargerPointage(PayrollCalculationInput in, Integer idEmploye, Integer idPeriodePaie, boolean simulation, Connection c) throws Exception {
        // 5. Compteurs de pointage scellé (pointage_cumul_periode)
        String sqlCumul = "SELECT * FROM pointage_cumul_periode WHERE id_employe = ? AND id_periode_paie = ? " +
                "AND statut_snapshot IN ('GELE', 'CONSOMME') ORDER BY version_snapshot DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sqlCumul)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal hAbs = rs.getBigDecimal("heures_absence_retenues");
                    long rawHs30Minutes = PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_hs_30"));
                    long rawHs50Minutes = PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_hs_50"));
                    long hs50Minutes = rawHs50Minutes;
                    long hs30Minutes = rawHs30Minutes;
                    long absenceMinutes = PayrollAttendanceInput.hoursToMinutes(hAbs);
                    Integer finalHs30Minutes = (Integer) rs.getObject("minutes_hs30_finales");
                    Integer finalAbsenceMinutes = (Integer) rs.getObject("minutes_absence_finale");
                    Integer finalHs50Minutes = (Integer) rs.getObject("minutes_hs50_finales");
                    if (finalHs50Minutes != null) {
                        if (finalHs30Minutes == null || finalAbsenceMinutes == null)
                            throw new IllegalStateException("SNAPSHOT_SOLDE_INCOMPLET: ventilation finale absente.");
                        hs50Minutes = finalHs50Minutes;
                    }
                    if (finalHs30Minutes != null || finalAbsenceMinutes != null) {
                        if (finalHs30Minutes == null || finalAbsenceMinutes == null) {
                            throw new IllegalStateException("SNAPSHOT_SOLDE_INCOMPLET: le rapprochement de période doit être complet.");
                        }
                        hs30Minutes = finalHs30Minutes;
                        absenceMinutes = finalAbsenceMinutes;
                    }
                    BigDecimal absenceDays = rs.getBigDecimal("nb_jours_absence");
                    Timestamp frozenTimestamp = rs.getTimestamp("gele_at");
                    Instant frozenAt = frozenTimestamp == null ? Instant.EPOCH : frozenTimestamp.toInstant();
                    BigDecimal hDim = rs.getBigDecimal("heures_dimanche_total");
                    BigDecimal hFer = rs.getBigDecimal("heures_ferie_total");
                    List<QualifiedTimeBucket> buckets = loadSnapshotBuckets(rs.getInt("id_cumul"), c);
                    if (buckets.isEmpty() && (rawHs30Minutes > 0
                            || PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_hs_50")) > 0
                            || PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_nuit_total")) > 0
                            || PayrollAttendanceInput.hoursToMinutes(hDim) > 0
                            || PayrollAttendanceInput.hoursToMinutes(hFer) > 0)) {
                        throw new IllegalStateException("SNAPSHOT_BUCKETS_MANQUANTS: les heures majorées du pointage "
                                + "ne disposent pas de tranches horaires auditables. Réouvrir puis regeler la période.");
                    }
                    if (finalHs30Minutes != null) {
                        buckets = finalHs50Minutes == null
                                ? PayrollOvertimeSettlement.apply(buckets, rawHs30Minutes, hs30Minutes)
                                : PayrollOvertimeSettlement.applyPeriod(buckets, rawHs30Minutes, rawHs50Minutes, hs30Minutes, hs50Minutes);
                    }
                    long bucketHs30Minutes = 0L;
                    long bucketHs50Minutes = 0L;
                    for (QualifiedTimeBucket bucket : buckets) {
                        if (bucket.overtimeTier() == QualifiedTimeBucket.OvertimeTier.OVERTIME_30) {
                            bucketHs30Minutes += bucket.minutes();
                        } else if (bucket.overtimeTier() == QualifiedTimeBucket.OvertimeTier.OVERTIME_50) {
                            bucketHs50Minutes += bucket.minutes();
                        }
                    }
                    in.setAttendanceInput(new PayrollAttendanceInput(
                            rs.getInt("id_cumul"), rs.getInt("version_snapshot"), idEmploye, idPeriodePaie, frozenAt,
                            PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("total_heures_travaillees")),
                            hs30Minutes,
                            hs50Minutes,
                            PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_nuit_total")),
                            PayrollAttendanceInput.hoursToMinutes(hDim),
                            PayrollAttendanceInput.hoursToMinutes(hFer),
                            0L,
                            absenceMinutes, rs.getInt("total_minutes_retard"),
                            rs.getBigDecimal("nb_jours_ouvrables"), rs.getBigDecimal("nb_jours_presents"),
                            absenceDays, rs.getBigDecimal("nb_jours_conge"), rs.getBigDecimal("nb_jours_repas"),
                            rs.getBigDecimal("montant_repas"), rs.getBigDecimal("nb_jours_deplacement"),
                            rs.getBigDecimal("montant_deplacement"),
                            buckets,
                            finalHs50Minutes == null ? 0L : hs30Minutes - bucketHs30Minutes,
                            finalHs50Minutes == null ? 0L : hs50Minutes - bucketHs50Minutes));
                    if (rs.getInt("contrat_indemnites_version") >= 2) {
                        // Les anciens snapshots restent lisibles via les deux agrégats historiques.
                        // Les nouveaux figent toutes les indemnités, y compris un catalogue ajouté plus tard.
                        in.setIndemniteRepas(BigDecimal.ZERO);
                        in.setIndemniteTransport(BigDecimal.ZERO);
                        chargerIndemnitesSnapshot(in, rs.getInt("id_cumul"), c);
                    }
                    in.setModeCalculAbsence("HEURES_REELLES");
                } else {
                    if (!simulation) {
                        throw new IllegalStateException("Le pointage de la période n'a pas encore été validé et gelé. Calcul réel impossible pour l'employé " + (in.getMatricule() != null ? in.getMatricule() : idEmploye) + ".");
                    }
                    // Une simulation sans snapshot ne doit pas inventer un calendrier
                    // conventionnel : le contexte est explicitement incomplet.
                    throw new IllegalStateException("MISSING_ATTENDANCE_SNAPSHOT: aucun snapshot Pointage applicable");
                }
            }
        }
    }

    private void chargerConges(PayrollCalculationInput in, Integer idEmploye, Connection c) throws Exception {
        // 5.0 Détermination canonique de NBCONGE (RG-CONGE-05) : domaine Congés en jours calendaires
        if (congeService != null && idEmploye != null && in.getDateDebutPeriode() != null && in.getDateFinPeriode() != null) {
            LocalDate dDebPer = in.getDateDebutPeriode().toLocalDate();
            LocalDate dFinPer = in.getDateFinPeriode().toLocalDate();
            var joursConge = congeService.calculerJoursCongeValorisesParDate(idEmploye, dDebPer, dFinPer, c);
            in.setJoursCongeValorisesParDate(joursConge);
            in.setNbJoursConges(joursConge.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        // 5.1 Le TCONGE est nécessaire uniquement lorsqu'une prise est valorisée.
        // Sans congé sur la période, aucune ligne d'indemnité de congé n'est calculée.
        if (in.getNbJoursConges() != null && in.getNbJoursConges().signum() > 0) {
            // La date de début de campagne n'est pas la date du droit : les lots portent leur valeur historique.
            BigDecimal indemnity = congeLotValorisationService.valorisationPrisesSurPeriode(
                    idEmploye, in.getDateDebutPeriode().toLocalDate(),
                    in.getDateFinPeriode().toLocalDate(), c);
            in.setIndemniteCongeLots(indemnity);
            in.setTauxJournalierConge(indemnity.divide(in.getNbJoursConges(), 8, RoundingMode.HALF_UP));
        }
    }

    private void chargerReglesLegales(PayrollCalculationInput in, Connection c) throws Exception {
        // 6. Paramètres légaux et fiscaux à la date métier de la campagne.
        payrollLegalContextResolver.resolve(in, c);
    }

    private void chargerMouvements(PayrollCalculationInput in, Integer idEmploye, Integer idPeriodePaie, boolean simulation, Connection c) throws Exception {
        // 7. Variables de paie, Primes, Extras et Retenues (paie_avance_acompte & ref_type_mouvement_paie)
        String sqlMouvements = "SELECT paa.*, " +
                "COALESCE(rtmp.code, paa.type_mouvement) AS code_effectif, " +
                "COALESCE(rtmp.sens, paa.sens) AS sens_effectif, " +
                "rtmp.libelle AS libelle_type, " +
                "rtmp.nature AS nature_type, " +
                "COALESCE(rtmp.categorie, 'PRIME') AS categorie_type, " +
                "pr.soumis_irsa AS est_imposable, " +
                "pr.soumis_cnaps AS est_soumis_cotisation, pr.soumis_sante AS rubrique_soumis_sante, " +
                "pr.methode_calcul, pr.politique_arrondi, COALESCE(pr.soumis_fmfp,pr.soumis_cnaps) AS rubrique_soumis_fmfp, pr.date_debut_effet AS rubrique_date_debut_effet, pr.date_fin_effet AS rubrique_date_fin_effet, pr.condition_application, " +
                "pr.categorie AS rubrique_categorie, " +
                "pr.etape_calcul AS rubrique_etape_calcul, " +
                "pr.code AS rubrique_code, " +
                "EXISTS (SELECT 1 FROM paie_mouvement_justificatif pj WHERE pj.id_avance = paa.id_avance) " +
                "AS justificatif_present " +
                "FROM paie_avance_acompte paa " +
                "LEFT JOIN ref_type_mouvement_paie rtmp ON paa.id_type_mouvement = rtmp.id_type_mouvement " +
                "JOIN periode_paie periode_cible ON periode_cible.id_periode_paie = ? " +
                "LEFT JOIN paie_rubrique linked ON linked.id_rubrique=rtmp.id_rubrique " +
                "LEFT JOIN LATERAL (SELECT r.* FROM paie_rubrique r WHERE " +
                "(CASE WHEN rtmp.nature='VARIABLE_DAF' THEN COALESCE(r.id_rubrique_origine,r.id_rubrique)=COALESCE(linked.id_rubrique_origine,linked.id_rubrique) " +
                "AND (r.date_debut_effet IS NULL OR r.date_debut_effet<=periode_cible.date_debut) " +
                "AND (r.date_fin_effet IS NULL OR r.date_fin_effet>=periode_cible.date_fin) " +
                "ELSE r.id_rubrique=linked.id_rubrique END)) pr ON TRUE " +
                "JOIN periode_paie periode_origine ON periode_origine.id_periode_paie = paa.id_periode_paie " +
                "WHERE paa.id_employe = ? AND paa.statut = 'APPROUVE' " +
                "AND paa.solde_restant > 0 AND (" +
                "(rtmp.categorie IN ('ACOMPTE','AVANCE') AND paa.versement_confirme_at IS NOT NULL " +
                "AND paa.date_paiement <= periode_cible.date_fin " +
                "AND periode_origine.date_debut <= periode_cible.date_debut) " +
                "OR (paa.id_periode_paie = periode_cible.id_periode_paie " +
                "AND COALESCE(rtmp.categorie, '') NOT IN ('ACOMPTE','AVANCE'))) " +
                "ORDER BY paa.date_demande, paa.id_avance FOR UPDATE OF paa";

        List<PaieAvanceAcompte> avances = new ArrayList<>();
        List<Integer> idsGains = new ArrayList<>();
        Set<Integer> mouvementsVus = new HashSet<>();

        try (PreparedStatement ps = c.prepareStatement(sqlMouvements)) {
            ps.setInt(1, idPeriodePaie);
            ps.setInt(2, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sens = rs.getString("sens_effectif");
                    String codeType = rs.getString("code_effectif");
                    BigDecimal montant = rs.getBigDecimal("solde_restant");
                    String motif = rs.getString("motif");
                    int idAvance = rs.getInt("id_avance");
                    if (!mouvementsVus.add(idAvance))
                        throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: plusieurs versions de rubrique "
                                + "s'appliquent au mouvement " + idAvance + " sur la période " + in.getCodePeriode());

                    String explanation=null;
                    mg.galaxam.sirh.paie.domain.engine.ManualPayrollRules.AmountResult amountCalculation=null;
                    if("VARIABLE_DAF".equals(rs.getString("nature_type")) && rs.getString("rubrique_code")!=null) {
                        try {
                            var calculation=mg.galaxam.sirh.paie.domain.engine.ManualPayrollRules.calculateAmount(
                                    rs.getString("methode_calcul"),rs.getString("politique_arrondi"),rs.getBigDecimal("montant_initial"),
                                    rs.getBigDecimal("quantite_saisie"),rs.getBigDecimal("taux_saisi"),in.getSalaireBaseCampagne());
                            amountCalculation=calculation;
                            montant=calculation.montant().subtract(rs.getBigDecimal("montant_impute")).max(BigDecimal.ZERO);
                            explanation="Version "+rs.getString("rubrique_code")+" ; condition "+rs.getString("condition_application")+" ; "+calculation.explication();
                            if(!simulation && !"MONTANT".equals(rs.getString("methode_calcul"))) {
                                try(var update=c.prepareStatement("UPDATE paie_avance_acompte SET montant=?,montant_initial=?,solde_restant=?,updated_at=CURRENT_TIMESTAMP WHERE id_avance=? AND montant_impute=0")) {
                                    update.setBigDecimal(1,montant);update.setBigDecimal(2,montant);update.setBigDecimal(3,montant);update.setInt(4,idAvance);update.executeUpdate();
                                }
                            }
                        } catch(IllegalArgumentException e) { throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,codeType+" : "+e.getMessage()); }
                    }
                    if (montant == null || montant.compareTo(BigDecimal.ZERO) <= 0) continue;

                    if ("VARIABLE_DAF".equals(rs.getString("nature_type"))) {
                        if (rs.getString("rubrique_code") == null) {
                            throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                                    "Aucune version de rubrique ne couvre toute la période pour " + codeType + ". Vérifiez les dates de validité.");
                        }
                        if (!mg.galaxam.sirh.paie.domain.engine.ManualPayrollRules.applies(
                                rs.getString("condition_application"), in.isSoumisIrsa(), in.isSoumisCnaps(), in.isSoumisSante())) {
                            throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                                    "Le mouvement " + codeType + " n’est pas applicable à ce salarié (" + rs.getString("condition_application") + "). Corrigez ou annulez le mouvement.");
                        }
                    }

                    java.sql.Date rubriqueDateEffet = rs.getDate("rubrique_date_debut_effet");
                    if (rubriqueDateEffet != null
                            && rubriqueDateEffet.toLocalDate().isAfter(in.getDateDebutPeriode().toLocalDate())) {
                        throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                                mg.galaxam.sirh.common.exception.SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                                "La rubrique du mouvement " + codeType + " entre en vigueur après la période calculée.");
                    }

                    if ("GAIN".equalsIgnoreCase(sens)) {
                        idsGains.add(idAvance);
                        if ("PRIME_PERFORMANCE".equalsIgnoreCase(codeType)) {
                            in.setPrimePerformanceBase(in.getPrimePerformanceBase().add(montant));
                        } else if ("PRIME_ASSIDUITE".equalsIgnoreCase(codeType)) {
                            in.setPrimeAssiduiteForfait(in.getPrimeAssiduiteForfait().add(montant));
                        } else if ("NOTE_DE_FRAIS".equalsIgnoreCase(codeType) || "REMBOURSEMENT_NOTE_FRAIS".equalsIgnoreCase(codeType)) {
                            if (!rs.getBoolean("justificatif_present")) {
                                throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                                        "La note de frais " + idAvance + " est approuvée sans justificatif.");
                            }
                            in.setRemboursementNotesFrais(in.getRemboursementNotesFrais().add(montant));
                        } else if ("REGULARISATION_CONGE".equalsIgnoreCase(codeType) || "REGULARISATION_PAIE".equalsIgnoreCase(codeType)) {
                            in.setRegularisationPaie(in.getRegularisationPaie().add(montant));
                            if (motif != null && !motif.isBlank()) {
                                in.setMotifRegularisationPaie(motif);
                            }
                        } else {
                            // Variable / Prime dynamique configurée en base de données
                            String libelleType = rs.getString("libelle_type");
                            String catType = rs.getString("rubrique_categorie");
                            if (rs.getString("rubrique_code") == null) {
                                throw PayrollConfigurationGuard.missing("RUBRIQUE_PAIE", idEmploye, codeType);
                            }
                            boolean imposable = PayrollConfigurationGuard.requireFlag(
                                    rs, "est_imposable", "RUBRIQUE_PAIE", idEmploye);
                            boolean soumisCotis = PayrollConfigurationGuard.requireFlag(
                                    rs, "est_soumis_cotisation", "RUBRIQUE_PAIE", idEmploye);
                            String libelleFinal = motif != null && !motif.isBlank() ? motif : (libelleType != null ? libelleType : codeType);

                            PayrollCalculationInput.GainVariable gv = new PayrollCalculationInput.GainVariable(
                                    idAvance, codeType, libelleFinal, montant, catType, imposable, soumisCotis);
                            gv.setSoumisSante(PayrollConfigurationGuard.requireFlag(
                                    rs, "rubrique_soumis_sante", "RUBRIQUE_PAIE", idEmploye));
                            gv.setSoumisFmfp(rs.getBoolean("rubrique_soumis_fmfp"));
                            if (gv.isSoumisFmfp() != soumisCotis) {
                                throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                                        "La rubrique " + rs.getString("rubrique_code")
                                                + " présente des assiettes CNaPS et FMFP incohérentes. Corrigez sa configuration avant le calcul.");
                            }
                            gv.setExplicationCalcul(explanation);
                            if(amountCalculation!=null) { gv.setBaseCalcul(amountCalculation.base()); gv.setQuantite(amountCalculation.quantite()); gv.setTaux(amountCalculation.taux()); }
                            gv.setCodeRubrique(rs.getString("rubrique_code"));
                            String etape = rs.getString("rubrique_etape_calcul");
                            if (etape == null) {
                                throw PayrollConfigurationGuard.missing("ETAPE_RUBRIQUE_PAIE", idEmploye, codeType);
                            }
                            if ("GAIN_NET".equals(etape) && (imposable || soumisCotis || gv.isSoumisSante() || gv.isSoumisFmfp())) {
                                throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                                        "La variable " + codeType + " est ajoutée après impôt mais affecte une assiette. Choisissez l'étape brut.");
                            }
                            gv.setEtapeCalcul(etape);
                            in.ajouterGainVariable(gv);
                        }
                    } else {
                        // RETENUE (ACOMPTE_15, AVANCE_SPECIALE, CALL_PERSO, RETENUE_DIVERSE)
                        if ("VARIABLE_DAF".equals(rs.getString("nature_type"))
                                && (!"RETENUE_NET".equals(rs.getString("rubrique_etape_calcul"))
                                || rs.getBoolean("est_imposable") || rs.getBoolean("est_soumis_cotisation")
                                || rs.getBoolean("rubrique_soumis_sante"))) {
                            throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                                    "La retenue manuelle " + codeType + " doit intervenir sur le net sans modifier les assiettes.");
                        }
                        PaieAvanceAcompte a = new PaieAvanceAcompte();
                        a.setIdAvance(idAvance);
                        a.setIdEmploye(rs.getInt("id_employe"));
                        a.setTypeMouvement(codeType);
                        a.setCodeRubriqueCalcul(rs.getString("rubrique_code"));
                        a.setExplicationCalcul(explanation);
                        a.setSens(sens != null ? sens : "RETENUE");
                        a.setMontant(montant);
                        a.setMotif(motif);
                        a.setStatut(rs.getString("statut"));
                        avances.add(a);
                    }
                }
            }
        }
        in.setAvancesAcomptes(avances);
        in.setIdsMouvementsGains(idsGains);
    }

    private void chargerPret(PayrollCalculationInput in, Integer idEmploye, Connection c) throws Exception {
        // 8. Échéance de prêt active
        String sqlPret = "SELECT e.* FROM paie_pret_echeance e " +
                "JOIN paie_pret p ON e.id_pret = p.id_pret " +
                "WHERE p.id_employe = ? AND p.statut = 'EN_COURS' " +
                "AND e.statut IN ('A_ECHOIR', 'PRELEVEE_PARTIELLEMENT') " +
                "AND e.date_echeance <= ? " +
                "ORDER BY e.date_echeance ASC, e.id_pret ASC, e.numero_echeance ASC LIMIT 1";

        try (PreparedStatement ps = c.prepareStatement(sqlPret)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, in.getDateFinPeriode());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PaiePretEcheance ech = new PaiePretEcheance();
                    ech.setIdEcheance(rs.getInt("id_echeance"));
                    ech.setIdPret(rs.getInt("id_pret"));
                    ech.setNumeroEcheance(rs.getInt("numero_echeance"));
                    ech.setMontantEcheance(rs.getBigDecimal("montant_echeance"));
                    ech.setCapitalRestantApres(rs.getBigDecimal("capital_restant_apres"));
                    ech.setMontantPreleve(rs.getBigDecimal("montant_preleve"));
                    ech.setMontantRestantEcheance(rs.getBigDecimal("montant_restant_echeance"));
                    ech.setStatut(rs.getString("statut"));
                    in.setEcheancePretActive(ech);
                }
            }
        }
    }

    private void chargerIndemnitesSnapshot(PayrollCalculationInput input, int snapshotId, Connection connection)
            throws SQLException {
        String sql = "SELECT code_rubrique_paie, libelle, imposable_irsa, soumis_cotisation, montant "
                + "FROM pointage_snapshot_indemnite WHERE id_cumul = ? ORDER BY code";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, snapshotId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    BigDecimal amount = result.getBigDecimal("montant");
                    if (amount == null || amount.signum() <= 0) continue;
                    boolean taxable = result.getBoolean("imposable_irsa");
                    boolean social = result.getBoolean("soumis_cotisation");
                    input.ajouterGainVariable(new PayrollCalculationInput.GainVariable(
                            null, result.getString("code_rubrique_paie"), result.getString("libelle"),
                            amount, taxable ? "INDEMNITE_IMPOSABLE" : "INDEMNITE_NON_IMPOSABLE", taxable, social));
                }
            }
        }
    }

    private List<QualifiedTimeBucket> loadSnapshotBuckets(int snapshotId, Connection c) throws SQLException {
        String sql = "SELECT date_debut, date_fin, minutes, overtime_tier, day_special_type, night_work_type, id_periode_paie, id_pointage " +
                     "FROM pointage_snapshot_bucket WHERE id_cumul = ? ORDER BY date_debut ASC, id_snapshot_bucket ASC";
        List<QualifiedTimeBucket> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, snapshotId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new QualifiedTimeBucket(
                            rs.getTimestamp("date_debut").toLocalDateTime(),
                            rs.getTimestamp("date_fin").toLocalDateTime(),
                            rs.getLong("minutes"),
                            OvertimeTier.valueOf(rs.getString("overtime_tier")),
                            DaySpecialType.valueOf(rs.getString("day_special_type")),
                            NightWorkType.valueOf(rs.getString("night_work_type")),
                            rs.getInt("id_periode_paie"),
                            rs.getObject("id_pointage") != null ? rs.getInt("id_pointage") : null
                    ));
                }
            }
        }
        return Collections.unmodifiableList(list);
    }

}
