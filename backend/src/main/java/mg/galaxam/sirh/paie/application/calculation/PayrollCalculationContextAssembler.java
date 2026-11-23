package mg.galaxam.sirh.paie.application.calculation;

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
import java.util.Locale;
import java.text.NumberFormat;
import java.util.Set;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.conge.application.valorisation.CongeLotValorisationService;
import mg.galaxam.sirh.conge.application.demande.CongeService;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver.Coverage;
import mg.galaxam.sirh.contrat.domain.AncienneteEngine;
import mg.galaxam.sirh.contrat.domain.AncienneteEngine.AncienneteBilan;
import mg.galaxam.sirh.contrat.domain.EmploymentContext;
import mg.galaxam.sirh.contrat.persistence.EmployeEnfant;
import mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.DailyPayProration;
import mg.galaxam.sirh.paie.domain.engine.PayrollOvertimeSettlement;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
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
        chargerJoursPayables(in, c);
        chargerMouvements(in, idEmploye, idPeriodePaie, simulation, c);
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

        // 2.1 Résolution de la couverture contractuelle et du poste actif à date
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

        // 2.2 Contrôle d'éligibilité légale au bulletin de paie
        validerEligibiliteAuBulletin(emploi);

        // 2.3 Mapping des données administratives, fiscales et sociales
        peuplerAttributsEmploiEtRegime(in, emploi, idEmploye, dateResolutionEmploi);

        // 2.4 Calcul de l'ancienneté contractuelle continue
        calculerAncienneteSalarie(in, emploi, idEmploye, dateResolutionEmploi, c);

        // 2.5 Détection de prorata pour entrée / sortie en cours de mois
        determinerProrataEntreeSortie(in, employmentCoverage, dateDebutPeriodeSql.toLocalDate(), dateFinPeriodeSql.toLocalDate());

        // 2.6 Grille des salaires journaliers
        in.setSalaireParJour(employmentContextResolver.resolveSalaryByDay(idEmploye,
                dateDebutPeriodeSql.toLocalDate(), dateFinPeriodeSql.toLocalDate(), c));
    }

    /**
     * Valide qu'un intervenant est bien salarié et non un prestataire/consultant soumis à facturation ou ISI 5%.
     */
    private void validerEligibiliteAuBulletin(EmploymentContext emploi) {
        if ("PRESTATION".equalsIgnoreCase(emploi.typeContratCode())
                || "CONSULTANT".equalsIgnoreCase(emploi.regimeCode())
                || emploi.soumisIsi5()) {
            throw new IllegalArgumentException("Un prestataire indépendant ne peut pas recevoir un bulletin de paie. Utilisez le parcours de facturation fournisseur.");
        }
    }

    /**
     * Assigne les métadonnées collaborateur, poste, site, régimes sociaux et fiscaux.
     */
    private void peuplerAttributsEmploiEtRegime(PayrollCalculationInput in,
                                                EmploymentContext emploi,
                                                Integer idEmploye, LocalDate dateResolutionEmploi) {
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
        in.setRegimeIntervenant(exigerValeur(emploi.regimeCode(), "TYPE_INTERVENANT", idEmploye));
        in.setSoumisCnaps(emploi.soumisCnaps());
        in.setSoumisSante(emploi.soumisSante());
        in.setSoumisIrsa(emploi.soumisIrsa());
        in.setSoumisIsi5(false);
        in.setCategorieProfessionnelle(exigerValeur(emploi.categorieProCode(), "CATEGORIE_PROFESSIONNELLE", idEmploye));
        in.setCategorieEligibleExonerationHs(emploi.estEligibleExonerationHs());
        in.setModeCalculPaie(exigerValeur(emploi.modeCalculPaieCode(), "MODE_CALCUL_PAIE", idEmploye));
        in.setSalaireBaseContractuel(emploi.salaireBase());

        if (emploi.dateEmbaucheInitiale() == null)
            throw PayrollConfigurationGuard.missing("DATE_EMBAUCHE", idEmploye, dateResolutionEmploi.toString());
        in.setDateEmbauche(Date.valueOf(emploi.dateEmbaucheInitiale()));
    }

    /**
     * Calcule l'ancienneté contractuelle en mois effectifs selon le moteur AncienneteEngine.
     */
    private void calculerAncienneteSalarie(PayrollCalculationInput in,
                                          EmploymentContext emploi,
                                          Integer idEmploye, LocalDate dateResolutionEmploi, Connection c) throws Exception {
        AncienneteBilan anciennete = new AncienneteEngine().calculerAnciennete(emploi.idContrat(), dateResolutionEmploi, c);
        if (anciennete.nombreContratsChaine() == 0)
            throw PayrollConfigurationGuard.missing("ANCIENNETE_CONTRAT", idEmploye, dateResolutionEmploi.toString());
        in.setAncienneteMois(anciennete.moisEffectifs());
    }

    /**
     * Détermine si le contrat débute ou se termine en cours de mois pour appliquer un prorata temporis.
     * Une mutation interne ne constitue pas une entrée du salarié.
     */
    private void determinerProrataEntreeSortie(PayrollCalculationInput in,
                                               Coverage coverage,
                                               LocalDate dateDebutPeriode, LocalDate dateFinPeriode) {
        boolean partial = coverage.firstDay().isAfter(dateDebutPeriode)
                || coverage.lastDay().isBefore(dateFinPeriode);
        in.setEntreeSortieEnCoursDeMois(partial);
        in.setJoursSousContrat(partial ? BigDecimal.valueOf(coverage.days()) : null);
    }

    void chargerBanque(PayrollCalculationInput in, Integer idEmploye, Connection c) throws Exception {
        // 3. Coordonnées bancaires
        String sqlBanque = "SELECT ecb.num_compte_rib, rb.nom_banque " +
                "FROM employe_compte_banque ecb " +
                "LEFT JOIN ref_banque rb ON ecb.id_banque = rb.id_banque " +
                "WHERE ecb.id_employe = ?";

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
                    construirePointageDepuisSnapshot(in, rs, idEmploye, idPeriodePaie, c);
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

    private record MinutesPointageReconciliees(long rawHs30Minutes, long rawHs50Minutes,
                                               long hs30Minutes, long hs50Minutes,
                                               long absenceMinutes, Integer finalHs30Minutes,
                                               Integer finalHs50Minutes) {}

    /**
     * Rapproche les heures supplémentaires et d'absence figées en minutes avec les soldes finaux de période.
     */
    private MinutesPointageReconciliees reconcilierMinutesPointage(ResultSet rs) throws SQLException {
        BigDecimal hAbs = rs.getBigDecimal("heures_absence_retenues");
        Integer rawHs30Hebdo = (Integer) rs.getObject("minutes_hs30_hebdo");
        Integer rawHs50Hebdo = (Integer) rs.getObject("minutes_hs50_hebdo");
        long rawHs30Minutes = rawHs30Hebdo != null ? rawHs30Hebdo
                : PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_hs_30"));
        long rawHs50Minutes = rawHs50Hebdo != null ? rawHs50Hebdo
                : PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_hs_50"));
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
        return new MinutesPointageReconciliees(rawHs30Minutes, rawHs50Minutes, hs30Minutes, hs50Minutes,
                absenceMinutes, finalHs30Minutes, finalHs50Minutes);
    }

    private record BucketsPointageAjustes(List<QualifiedTimeBucket> buckets, long ecartHs30, long ecartHs50) {}

    /**
     * Charge et audite les tranches horaires (buckets) de majoration et applique les ajustements de période.
     */
    private BucketsPointageAjustes ajusterBucketsPointage(ResultSet rs, int idCumul,
                                                         MinutesPointageReconciliees m,
                                                         Connection c) throws SQLException {
        BigDecimal hDim = rs.getBigDecimal("heures_dimanche_total");
        BigDecimal hFer = rs.getBigDecimal("heures_ferie_total");
        List<QualifiedTimeBucket> buckets = loadSnapshotBuckets(idCumul, c);
        if (buckets.isEmpty() && (m.rawHs30Minutes() > 0
                || PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_hs_50")) > 0
                || PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_nuit_total")) > 0
                || PayrollAttendanceInput.hoursToMinutes(hDim) > 0
                || PayrollAttendanceInput.hoursToMinutes(hFer) > 0)) {
            throw new IllegalStateException("SNAPSHOT_BUCKETS_MANQUANTS: les heures majorées du pointage "
                    + "ne disposent pas de tranches horaires auditables. Réouvrir puis regeler la période.");
        }
        if (m.finalHs30Minutes() != null) {
            buckets = m.finalHs50Minutes() == null
                    ? PayrollOvertimeSettlement.apply(buckets, m.rawHs30Minutes(), m.hs30Minutes())
                    : PayrollOvertimeSettlement.applyPeriod(buckets, m.rawHs30Minutes(), m.rawHs50Minutes(), m.hs30Minutes(), m.hs50Minutes());
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
        long ecartHs30 = m.finalHs50Minutes() == null ? 0L : m.hs30Minutes() - bucketHs30Minutes;
        long ecartHs50 = m.finalHs50Minutes() == null ? 0L : m.hs50Minutes() - bucketHs50Minutes;
        return new BucketsPointageAjustes(buckets, ecartHs30, ecartHs50);
    }

    /**
     * Reconstitue l'ensemble du contexte de présence et d'heures à partir du snapshot scellé.
     */
    private void construirePointageDepuisSnapshot(PayrollCalculationInput in, ResultSet rs,
                                                 Integer idEmploye, Integer idPeriodePaie,
                                                 Connection c) throws Exception {
        int idCumul = rs.getInt("id_cumul");
        MinutesPointageReconciliees minutes = reconcilierMinutesPointage(rs);
        BucketsPointageAjustes buckets = ajusterBucketsPointage(rs, idCumul, minutes, c);

        Timestamp frozenTimestamp = rs.getTimestamp("gele_at");
        Instant frozenAt = frozenTimestamp == null ? Instant.EPOCH : frozenTimestamp.toInstant();

        in.setAttendanceInput(new PayrollAttendanceInput(
                idCumul, rs.getInt("version_snapshot"), idEmploye, idPeriodePaie, frozenAt,
                PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("total_heures_travaillees")),
                minutes.hs30Minutes(),
                minutes.hs50Minutes(),
                PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_nuit_total")),
                PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_dimanche_total")),
                PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_ferie_total")),
                0L,
                minutes.absenceMinutes(), rs.getInt("total_minutes_retard"),
                rs.getBigDecimal("nb_jours_ouvrables"), rs.getBigDecimal("nb_jours_presents"),
                rs.getBigDecimal("nb_jours_absence"), rs.getBigDecimal("nb_jours_conge"), rs.getBigDecimal("nb_jours_repas"),
                rs.getBigDecimal("montant_repas"), rs.getBigDecimal("nb_jours_deplacement"),
                rs.getBigDecimal("montant_deplacement"),
                buckets.buckets(),
                buckets.ecartHs30(),
                buckets.ecartHs50()));

        if (rs.getInt("contrat_indemnites_version") >= 2) {
            // Les anciens snapshots restent lisibles via les deux agrégats historiques.
            // Les nouveaux figent toutes les indemnités, y compris un catalogue ajouté plus tard.
            in.setIndemniteRepas(BigDecimal.ZERO);
            in.setIndemniteTransport(BigDecimal.ZERO);
            chargerIndemnitesSnapshot(in, idCumul, c);
        }
        in.setModeCalculAbsence("HEURES_REELLES");
    }

    private void chargerConges(PayrollCalculationInput in, Integer idEmploye, Connection c) throws Exception {
        // 5.0 Détermination canonique de NBCONGE (RG-CONGE-05) : domaine Congés en jours calendaires
        resoudreJoursCongesPris(in, idEmploye, c);

        // 5.1 Valorisation FIFO des lots de congés et calcul du TCONGE effectif
        valoriserIndemnitesCongesFifo(in, idEmploye, c);
    }

    /**
     * Détermine les jours de congés valorisés et pris sur la période (RG-CONGE-05).
     */
    private void resoudreJoursCongesPris(PayrollCalculationInput in, Integer idEmploye, Connection c) throws Exception {
        if (congeService != null && idEmploye != null && in.getDateDebutPeriode() != null && in.getDateFinPeriode() != null) {
            LocalDate dDebPer = in.getDateDebutPeriode().toLocalDate();
            LocalDate dFinPer = in.getDateFinPeriode().toLocalDate();
            var joursConge = congeService.calculerJoursCongeValorisesParDate(idEmploye, dDebPer, dFinPer, c);
            in.setJoursCongeValorisesParDate(joursConge);
            in.setJoursCongeBaseParDate(congeService.calculerJoursCongeBaseParDate(idEmploye, dDebPer, dFinPer, c));
            in.setNbJoursConges(joursConge.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    /**
     * Calcule l'indemnité de congé via la valorisation FIFO des lots acquis et en déduit le taux journalier effectif (TCONGE).
     * Sans congé sur la période, aucune indemnité n'est calculée.
     */
    private void valoriserIndemnitesCongesFifo(PayrollCalculationInput in, Integer idEmploye, Connection c) throws Exception {
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

    private void chargerJoursPayables(PayrollCalculationInput in, Connection c) throws Exception {
        if (!"JOURNALIER".equalsIgnoreCase(in.getModeCalculPaie())) return;
        PayrollAttendanceInput attendance = in.getAttendanceInput();
        if (attendance.dafOvertime30Minutes() > 0 || attendance.dafOvertime50Minutes() > 0)
            throw new IllegalStateException("PAYROLL_DAILY_DAF_OVERTIME_UNRESOLVED: heures DAF sans journée attribuée");

        List<DailyPayProration.Day> days = chargerJourneesJournalieres(attendance.snapshotId(), in.getHeuresNormalesParJour(), c);
        long finalOvertime = attendance.overtime30Minutes() + attendance.overtime50Minutes();
        long workedMinutes = days.stream().mapToLong(DailyPayProration.Day::workedMinutes).sum();
        long overtimeMinutes = days.stream().mapToLong(DailyPayProration.Day::overtimeMinutes).sum();

        if (days.isEmpty() || workedMinutes != attendance.workedMinutes() || overtimeMinutes != finalOvertime)
            throw new IllegalStateException("PAYROLL_DAILY_SNAPSHOT_INCOMPLETE: journées ou heures supplémentaires non rapprochées");
        in.setJoursPayablesJournaliers(DailyPayProration.payableDays(days));
    }

    /**
     * Charge et valide les journées de travail d'un intervenant journalier en contrôlant le seuil quotidien d'heures (SEUIL_HEURES_JOUR).
     */
    private List<DailyPayProration.Day> chargerJourneesJournalieres(int snapshotId, BigDecimal heuresNormalesParJour, Connection c) throws Exception {
        String sql = "SELECT j.date_pointage, SUM(j.minutes_travaillees) AS minutes_travaillees, "
                + "COALESCE(SUM(hs.minutes_hs), 0) AS minutes_hs "
                + "FROM pointage_snapshot_jour j LEFT JOIN ("
                + "SELECT id_cumul, id_pointage, SUM(minutes) AS minutes_hs FROM pointage_snapshot_bucket "
                + "WHERE overtime_tier IN ('OVERTIME_30', 'OVERTIME_50') GROUP BY id_cumul, id_pointage"
                + ") hs ON hs.id_cumul=j.id_cumul AND hs.id_pointage=j.id_pointage "
                + "WHERE j.id_cumul=? GROUP BY j.date_pointage ORDER BY j.date_pointage";
        List<DailyPayProration.Day> days = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, snapshotId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = rs.getDate("date_pointage").toLocalDate();
                    long expectedMinutes = validerSeuilHeuresQuotidien(date, heuresNormalesParJour, c);
                    long dayWorked = rs.getLong("minutes_travaillees");
                    long dayOvertime = rs.getLong("minutes_hs");
                    days.add(new DailyPayProration.Day(dayWorked, dayOvertime, expectedMinutes));
                }
            }
        }
        return days;
    }

    private long validerSeuilHeuresQuotidien(LocalDate date, BigDecimal heuresNormalesParJour, Connection c) throws SQLException {
        var hours = ParametreGlobal.resolve(
                ParametreGlobal.loadEtatMapAtDate(Date.valueOf(date), c), "SEUIL_HEURES_JOUR");
        if (!hours.isActive())
            throw new IllegalStateException("PAYROLL_DAILY_HOURS_MISSING: seuil quotidien absent au " + date);
        BigDecimal dailyHours;
        try {
            dailyHours = new BigDecimal(hours.valeur().trim());
        } catch (RuntimeException error) {
            throw new IllegalStateException("PAYROLL_DAILY_HOURS_INVALID: seuil quotidien invalide au " + date, error);
        }
        if (dailyHours.signum() <= 0 || dailyHours.compareTo(heuresNormalesParJour) != 0)
            throw new IllegalStateException("PAYROLL_DAILY_HOURS_CHANGE_UNRESOLVED: seuil quotidien incohérent au " + date);
        return dailyHours.multiply(BigDecimal.valueOf(60)).longValueExact();
    }

    private void chargerMouvements(PayrollCalculationInput in, Integer idEmploye, Integer idPeriodePaie, boolean simulation, Connection c) throws Exception {
        // Variables de paie, Primes, Extras et Retenues (paie_avance_acompte & ref_type_mouvement_paie)
        // Règle d'or Trésorerie : un acompte ou une avance n'est imputable QUE si son versement est confirmé (versement_confirme_at IS NOT NULL)
        String sqlMouvements = "SELECT paa.*, choix.montant AS retenue_choisie, " +
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
                "pr.libelle AS rubrique_libelle, " +
                "EXISTS (SELECT 1 FROM paie_mouvement_justificatif pj WHERE pj.id_avance = paa.id_avance) " +
                "AS justificatif_present " +
                "FROM paie_avance_acompte paa " +
                "LEFT JOIN paie_avance_retenue_choix choix ON choix.id_avance=paa.id_avance AND choix.id_periode_paie=? " +
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
            ps.setInt(2, idPeriodePaie);
            ps.setInt(3, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sens = rs.getString("sens_effectif");
                    String codeType = rs.getString("code_effectif");
                    int idAvance = rs.getInt("id_avance");
                    if (!mouvementsVus.add(idAvance))
                        throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: plusieurs versions de rubrique "
                                + "s'appliquent au mouvement " + idAvance + " sur la période " + in.getCodePeriode());

                    // 1. Détermination du montant de retenue (avance échelonnée ou solde complet acompte)
                    BigDecimal montant = determinerMontantRetenueAvance(rs, rs.getString("categorie_type"), rs.getBigDecimal("solde_restant"));

                    // 2. Traitement spécifique des variables DAF calculées dynamiquement
                    String explanation = null;
                    mg.galaxam.sirh.paie.domain.engine.ManualPayrollRules.AmountResult amountCalculation = null;
                    if ("VARIABLE_DAF".equals(rs.getString("nature_type")) && rs.getString("rubrique_code") != null) {
                        VariableDafResult varResult = traiterVariableDaf(rs, codeType, in, idAvance, simulation, c);
                        montant = varResult.montant();
                        explanation = varResult.explanation();
                        amountCalculation = varResult.amountCalculation();
                    }

                    if (montant == null || montant.compareTo(BigDecimal.ZERO) <= 0) continue;

                    verifierRubriqueMouvement(rs, codeType, in);

                    // 3. Ventilation métier selon la nature : GAIN (Brut) ou RETENUE (Net)
                    if ("GAIN".equalsIgnoreCase(sens)) {
                        enregistrerGainVariable(rs, idAvance, codeType, montant, explanation, amountCalculation, in, idsGains, idEmploye);
                    } else {
                        enregistrerRetenueNet(rs, idAvance, codeType, sens, montant, explanation, avances);
                    }
                }
            }
        }
        in.setAvancesAcomptes(avances);
        in.setIdsMouvementsGains(idsGains);
    }

    /**
     * Calcule le montant à déduire pour une avance échelonnée selon la politique AdvanceRepaymentPolicy.
     * Pour un acompte ou une retenue ponctuelle, reprend l'intégralité du solde restant.
     */
    private BigDecimal determinerMontantRetenueAvance(ResultSet rs, String categorieType, BigDecimal soldeRestant) throws SQLException {
        if ("AVANCE".equals(categorieType)) {
            BigDecimal choixDaf = rs.getBigDecimal("retenue_choisie");
            return mg.galaxam.sirh.paie.domain.engine.AdvanceRepaymentPolicy.retenueDemandee(
                    rs.getBigDecimal("montant_initial"), rs.getInt("nombre_mensualites"), soldeRestant, choixDaf);
        }
        return soldeRestant;
    }

    private record VariableDafResult(BigDecimal montant, String explanation,
                                     mg.galaxam.sirh.paie.domain.engine.ManualPayrollRules.AmountResult amountCalculation) {}

    /**
     * Traite et valide le calcul dynamique d'une variable DAF (primes, extras).
     */
    private VariableDafResult traiterVariableDaf(ResultSet rs, String codeType, PayrollCalculationInput in,
                                                 int idAvance, boolean simulation, Connection c) throws Exception {
        try {
            var calculation = mg.galaxam.sirh.paie.domain.engine.ManualPayrollRules.calculateAmount(
                    rs.getString("methode_calcul"), rs.getString("politique_arrondi"), rs.getBigDecimal("montant_initial"),
                    rs.getBigDecimal("quantite_saisie"), rs.getBigDecimal("taux_saisi"),
                    "MENSUEL".equalsIgnoreCase(in.getModeCalculPaie())
                            ? in.getSalaireBaseCampagne() : in.getSalaireBaseNonMensuel());
            BigDecimal montant = calculation.montant().subtract(rs.getBigDecimal("montant_impute")).max(BigDecimal.ZERO);
            String explanation = expliquerMontantVariable(rs.getString("condition_application"),
                    rs.getString("methode_calcul"), calculation);

            if (!simulation && !"MONTANT".equals(rs.getString("methode_calcul"))) {
                try (var update = c.prepareStatement(
                        "UPDATE paie_avance_acompte SET montant=?,montant_initial=?,solde_restant=?,updated_at=CURRENT_TIMESTAMP WHERE id_avance=? AND montant_impute=0")) {
                    update.setBigDecimal(1, montant);
                    update.setBigDecimal(2, montant);
                    update.setBigDecimal(3, montant);
                    update.setInt(4, idAvance);
                    update.executeUpdate();
                }
            }
            return new VariableDafResult(montant, explanation, calculation);
        } catch (IllegalArgumentException e) {
            throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR, codeType + " : " + e.getMessage());
        }
    }

    /**
     * Vérifie la conformité de la rubrique de paie (présence, applicabilité salarié et date d'effet).
     */
    private void verifierRubriqueMouvement(ResultSet rs, String codeType, PayrollCalculationInput in) throws SQLException, SirhBusinessException {
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
        if (rubriqueDateEffet != null && rubriqueDateEffet.toLocalDate().isAfter(in.getDateDebutPeriode().toLocalDate())) {
            throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                    "La rubrique du mouvement " + codeType + " entre en vigueur après la période calculée.");
        }
    }

    /**
     * Enregistre un gain variable (prime performance, assiduité, note de frais, régularisation ou prime libre) dans le contexte.
     */
    private void enregistrerGainVariable(ResultSet rs, int idAvance, String codeType, BigDecimal montant,
                                         String explanation,
                                         mg.galaxam.sirh.paie.domain.engine.ManualPayrollRules.AmountResult amountCalculation,
                                         PayrollCalculationInput in, List<Integer> idsGains, Integer idEmploye) throws Exception {
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
            String motif = rs.getString("motif");
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
            boolean imposable = PayrollConfigurationGuard.requireFlag(rs, "est_imposable", "RUBRIQUE_PAIE", idEmploye);
            boolean soumisCotis = PayrollConfigurationGuard.requireFlag(rs, "est_soumis_cotisation", "RUBRIQUE_PAIE", idEmploye);
            String libelleRubrique = rs.getString("rubrique_libelle");
            String libelleFinal = libelleRubrique != null && !libelleRubrique.isBlank()
                    ? libelleRubrique : (libelleType != null ? libelleType : codeType);

            PayrollCalculationInput.GainVariable gv = new PayrollCalculationInput.GainVariable(
                    idAvance, codeType, libelleFinal, montant, catType, imposable, soumisCotis);
            gv.setSoumisSante(PayrollConfigurationGuard.requireFlag(rs, "rubrique_soumis_sante", "RUBRIQUE_PAIE", idEmploye));
            gv.setSoumisFmfp(rs.getBoolean("rubrique_soumis_fmfp"));
            if (gv.isSoumisFmfp() != soumisCotis) {
                throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                        "La rubrique " + rs.getString("rubrique_code")
                                + " présente des assiettes CNaPS et FMFP incohérentes. Corrigez sa configuration avant le calcul.");
            }
            gv.setExplicationCalcul(explanation);
            if (amountCalculation != null) {
                gv.setBaseCalcul(amountCalculation.base());
                gv.setQuantite(amountCalculation.quantite());
                gv.setTaux(amountCalculation.taux());
            }
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
    }

    /**
     * Enregistre une retenue nette (acompte versé, avance échelonnée ou retenue diverse) dans le contexte.
     */
    private void enregistrerRetenueNet(ResultSet rs, int idAvance, String codeType, String sens,
                                       BigDecimal montant, String explanation,
                                       List<PaieAvanceAcompte> avances) throws Exception {
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
        a.setLibelleRubriqueCalcul(rs.getString("rubrique_libelle"));
        a.setExplicationCalcul(explanation);
        a.setSens(sens != null ? sens : "RETENUE");
        a.setMontant(montant);
        a.setMotif(rs.getString("motif"));
        a.setStatut(rs.getString("statut"));
        avances.add(a);
    }

    private static String expliquerMontantVariable(String condition, String method,
            mg.galaxam.sirh.paie.domain.engine.ManualPayrollRules.AmountResult calculation) {
        String portee = "TOUS".equalsIgnoreCase(condition) ? "tous les salariés"
                : (condition == null || condition.isBlank() ? "condition générale" : condition);
        String montant = formatAr(calculation.montant());
        String details = switch (method) {
            case "QUANTITE_TAUX" -> formatNumber(calculation.quantite()) + " × "
                    + formatAr(calculation.taux()) + " = " + montant;
            case "POURCENTAGE_SALAIRE_BASE" -> formatAr(calculation.base()) + " × "
                    + formatNumber(calculation.taux()) + " % = " + montant;
            default -> "Montant saisi : " + montant;
        };
        return details + " · applicable à " + portee + ".";
    }

    private static String formatAr(BigDecimal amount) {
        return formatNumber(amount) + " Ar";
    }

    private static String formatNumber(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.FRANCE);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
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
