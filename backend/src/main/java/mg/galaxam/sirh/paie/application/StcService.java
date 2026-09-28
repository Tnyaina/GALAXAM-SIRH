package mg.galaxam.sirh.paie.application;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.persistence.VSoldeCongeEmploye;
import mg.galaxam.sirh.contrat.domain.AncienneteEngine;
import mg.galaxam.sirh.contrat.persistence.EmployeEnfant;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.persistence.PaiePret;
import mg.galaxam.sirh.paie.persistence.PaieStc;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paie.domain.engine.SocialContributionCalculator;
import mg.galaxam.sirh.paie.domain.engine.IrsaCalculator;
import mg.galaxam.sirh.paie.domain.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.paie.domain.engine.IsiCalculator;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import mg.galaxam.sirh.paie.domain.engine.IrsaCalculator.Tranche;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcPreparationDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcRecuDTO;
import mg.galaxam.sirh.paie.domain.engine.IrsaBracketResolver;
import mg.galaxam.sirh.paie.domain.engine.TaxConfigurationResolver;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.stereotype.Service;

/**
 * Service Métier pour le traitement, la simulation, la validation et l'émission
 * du Solde de Tout Compte (STC) & Quittance Libératoire (Loi n° 2003-044).
 */
@Service
public class StcService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StcService.class);

    private final AncienneteEngine ancienneteEngine;

    public StcService(DataSource dataSource, Object ignoredLegacyCalculator, AncienneteEngine ancienneteEngine) {
        this.ancienneteEngine = ancienneteEngine != null ? ancienneteEngine : new AncienneteEngine();
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StcService(DataSource dataSource) {
        this(dataSource, null, new AncienneteEngine());
    }

    public StcService() {
        this(null, null, new AncienneteEngine());
    }


    // =========================================================================
    // 1. PRÉPARATION ADMINISTRATIVE & COLLECTE DES DROITS
    // =========================================================================

    public StcPreparationDTO preparerStc(Integer idEmploye, Date dateDepart, String motif, Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'identifiant de l'employé est obligatoire.");
        }
        if (dateDepart == null) {
            throw new IllegalArgumentException("La date de départ métier est obligatoire.");
        }
        Date dateDepartEffective = dateDepart;

        StcPreparationDTO prep = new StcPreparationDTO();
        prep.setIdEmploye(idEmploye);
        prep.setDateDepart(dateDepartEffective);
        prep.setMotifRupture(motif != null ? motif : "DEMISSION");

        // 1. Informations de l'employé et affectation
        String sqlEmp = "SELECT e.matricule, e.nom, e.prenom, e.cin_numero, e.cin_date_delivrance, e.cin_lieu_delivrance, e.num_affiliation_cnaps, " +
                "eac.salaire_base, f.libelle AS fonction_nom, d.libelle AS dept_nom, s.libelle AS site_nom, s.code_site, " +
                "cat.code AS cat_code, COALESCE(c_emb.date_embauche_initiale, ct.date_debut) AS date_embauche " +
                "FROM employe e " +
                "JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe " +
                "LEFT JOIN (SELECT id_employe, MIN(date_debut) AS date_embauche_initiale FROM contrat_travail GROUP BY id_employe) c_emb ON e.id_employe = c_emb.id_employe " +
                "LEFT JOIN contrat_travail ct ON eac.id_contrat = ct.id_contrat " +
                "LEFT JOIN ref_fonction f ON eac.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_departement d ON eac.id_departement = d.id_departement " +
                "LEFT JOIN ref_site s ON eac.id_site = s.id_site " +
                "LEFT JOIN ref_categorie_professionnelle cat ON eac.id_categorie_professionnelle = cat.id_categorie_professionnelle " +
                "WHERE e.id_employe = ? AND eac.date_debut <= ? " +
                "AND (eac.date_fin IS NULL OR eac.date_fin >= ?) " +
                "ORDER BY eac.date_debut DESC, eac.id_affectation DESC LIMIT 2";

        Date dateEmbauche = null;
        BigDecimal salaireBase = BigDecimal.ZERO;
        String catPro = "AGENT";

        boolean foundEmp = false;
        try (PreparedStatement ps = c.prepareStatement(sqlEmp)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, dateDepartEffective);
            ps.setDate(3, dateDepartEffective);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    foundEmp = true;
                    prep.setMatricule(rs.getString("matricule"));
                    String nom = rs.getString("nom");
                    String prenom = rs.getString("prenom");
                    prep.setNomComplet(((nom != null ? nom : "") + " " + (prenom != null ? prenom : "")).trim());
                    prep.setCinNumero(rs.getString("cin_numero"));
                    prep.setCinDateDelivrance(rs.getDate("cin_date_delivrance"));
                    prep.setCinLieuDelivrance(rs.getString("cin_lieu_delivrance"));
                    prep.setNumeroCnaps(rs.getString("num_affiliation_cnaps"));
                    prep.setIntitulePoste(rs.getString("fonction_nom") != null ? rs.getString("fonction_nom") : "Collaborateur");
                    prep.setDepartement(rs.getString("dept_nom"));
                    prep.setSite(rs.getString("site_nom"));
                    prep.setCodeSite(rs.getString("code_site"));
                    catPro = rs.getString("cat_code") != null ? rs.getString("cat_code") : "AGENT";
                    prep.setCategorieProfessionnelle(catPro);
                    prep.setRegimeFiscal(catPro != null && catPro.toUpperCase().contains("CONSULT") ? "ISI" : "IRSA");
                    salaireBase = rs.getBigDecimal("salaire_base") != null ? rs.getBigDecimal("salaire_base") : BigDecimal.ZERO;
                    prep.setSalaireBase(salaireBase);
                    dateEmbauche = rs.getDate("date_embauche");
                    prep.setDateEmbauche(dateEmbauche);
                    if (rs.next()) {
                        throw new IllegalStateException("Plusieurs affectations couvrent la date de départ " + dateDepartEffective + ".");
                    }
                }
            }
        }

        if (!foundEmp) {
            throw new IllegalArgumentException("Dossier de carrière introuvable pour l'employé ID: " + idEmploye);
        }

        // Nombre d'enfants à charge éligibles à la déduction IRSA
        try {
            int nbEnfants = EmployeEnfant.nbEnfantsEligibles(idEmploye, dateDepartEffective, c);
            prep.setNbEnfantsACharge(nbEnfants);
        } catch (Exception e) {
            log.warn("[StcService] Impossible de récupérer les enfants à charge pour employé {}: {}", idEmploye, e.getMessage());
        }


        // Taux horaire légal résolu depuis la configuration légale applicable
        PaieConfigLegale cfgLegale = chargerConfigLegale(dateDepartEffective, c);
        BigDecimal diviseurHoraire = (cfgLegale != null && cfgLegale.getDiviseurMensuelHeures() != null)
                ? cfgLegale.getDiviseurMensuelHeures()
                : PayrollExecutionEngine.DIVISEUR_HEURES_MENSUELLES;
        BigDecimal tauxHoraire = salaireBase.divide(diviseurHoraire, 4, RoundingMode.HALF_UP);
        prep.setTauxHoraire(tauxHoraire);

        // 2. Ancienneté via AncienneteEngine (décompte complet des contrats successifs)
        AncienneteEngine.AncienneteBilan bilan = calculerAncienneteEmploye(idEmploye, dateDepartEffective, c);
        if (bilan != null && (bilan.ansEffectifs() > 0 || bilan.moisEffectifs() > 0 || bilan.dateOrigineChaine() != null)) {
            prep.setAncienneteAnnees(bilan.ansEffectifs());
            prep.setAncienneteMois(bilan.moisEffectifs());
            if (bilan.dateOrigineChaine() != null) {
                dateEmbauche = Date.valueOf(bilan.dateOrigineChaine());
                prep.setDateEmbauche(dateEmbauche);
            }
        } else if (dateEmbauche != null) {
            LocalDate dDebut = dateEmbauche.toLocalDate();
            LocalDate dFin = dateDepartEffective.toLocalDate();
            if (!dFin.isBefore(dDebut)) {
                Period p = Period.between(dDebut, dFin);
                prep.setAncienneteAnnees(p.getYears());
                prep.setAncienneteMois(p.getYears() * 12 + p.getMonths());
            }
        }

        // 3. Période de paie correspondante
        String sqlPer = "SELECT id_periode_paie, code FROM periode_paie " +
                "WHERE ? BETWEEN date_debut AND date_fin ORDER BY date_debut DESC, id_periode_paie DESC LIMIT 2";
        try (PreparedStatement psPer = c.prepareStatement(sqlPer)) {
            psPer.setDate(1, dateDepartEffective);
            try (ResultSet rsPer = psPer.executeQuery()) {
                if (rsPer.next()) {
                    prep.setIdPeriodePaie(rsPer.getInt("id_periode_paie"));
                    prep.setCodePeriodePaie(rsPer.getString("code"));
                    if (rsPer.next()) {
                        throw new IllegalStateException("Plusieurs périodes de paie couvrent la date de départ " + dateDepartEffective + ".");
                    }
                } else {
                    // Pour un départ historique ou anticipé (hors calendrier de paie actif),
                    // on ne bloque pas la préparation / simulation STC : l'imputation se fera sur la période de régularisation
                    prep.setIdPeriodePaie(null);
                    prep.setCodePeriodePaie("HORS_CALENDRIER");
                }
            }
        }

        // 4. Estimation du salaire au prorata du dernier mois
        LocalDate dDep = dateDepartEffective.toLocalDate();
        int jourDuMois = Math.min(dDep.getDayOfMonth(), 30);
        BigDecimal joursTravaillesEstimes = new BigDecimal(String.valueOf(jourDuMois));
        prep.setJoursTravaillesEstimes(joursTravaillesEstimes);
        BigDecimal prorataEstime = salaireBase.multiply(joursTravaillesEstimes)
                .divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP);
        prep.setSalaireProrataEstime(prorataEstime);

        // 5. Congés restants & Taux valorisé dynamique (conge_journal_droit)
        VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
        BigDecimal soldeJours = solde != null && solde.getSoldeActuel() != null
                ? solde.getSoldeActuel()
                : BigDecimal.ZERO;
        prep.setSoldeCongesRestant(soldeJours);

        // Taux journalier valorisé TCONGE issu du Grand-Livre (mutualisé avec résolution temporelle)
        BigDecimal tauxConge = getTauxJournalierApplicable(idEmploye, dDep, salaireBase, c);
        prep.setTauxJournalierConge(tauxConge);

        BigDecimal iccpEstimee = MoneyRoundingPolicy.amount(soldeJours.multiply(tauxConge));
        prep.setIccpEstimee(iccpEstimee);

        // 6. Préavis suggéré et Indemnité de licenciement légale
        boolean estCadre = "CADRE".equalsIgnoreCase(catPro) || "CADRE_DIRIGEANT".equalsIgnoreCase(catPro);
        boolean estLicenciement = motif != null && (motif.toUpperCase().contains("LICENCIEMENT") || "DEPART_RETRAITE".equalsIgnoreCase(motif));

        if (estLicenciement) {
            // Préavis légal / conventionnel (Cadre = 3 mois, Non-Cadre = 1 à 2 mois)
            BigDecimal moisPreavis = estCadre ? new BigDecimal("3.0") : (prep.getAncienneteAnnees() >= 3 ? new BigDecimal("2.0") : new BigDecimal("1.0"));
            prep.setMoisPreavisSuggere(moisPreavis);
            prep.setIndemnitePreavisEstimee(MoneyRoundingPolicy.amount(salaireBase.multiply(moisPreavis)));

            // Indemnité légale de licenciement (ex: 10 jours de salaire par année complète)
            int annees = prep.getAncienneteAnnees();
            if (annees >= 1) {
                BigDecimal tauxJourLic = salaireBase.divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP);
                BigDecimal montantLic = MoneyRoundingPolicy.amount(tauxJourLic.multiply(new BigDecimal(annees * 10L)));
                prep.setIndemniteLicenciementEstimee(montantLic);
            }
        } else {
            prep.setMoisPreavisSuggere(BigDecimal.ZERO);
            prep.setIndemnitePreavisEstimee(BigDecimal.ZERO);
            prep.setIndemniteLicenciementEstimee(BigDecimal.ZERO);
        }

        // 7. Engagements financiers (Prêts & Avances)
        BigDecimal totalPrets = PaiePret.calculerCapitalRestantTotal(idEmploye, c);
        prep.setCapitalRestantPrets(totalPrets);

        String sqlAvances = "SELECT COALESCE(SUM(a.solde_restant), 0) FROM paie_avance_acompte a " +
                "JOIN ref_type_mouvement_paie t ON t.id_type_mouvement = a.id_type_mouvement " +
                "WHERE a.id_employe = ? AND t.categorie IN ('ACOMPTE','AVANCE') " +
                "AND a.versement_confirme_at IS NOT NULL AND a.statut = 'APPROUVE'";
        try (PreparedStatement psAv = c.prepareStatement(sqlAvances)) {
            psAv.setInt(1, idEmploye);
            try (ResultSet rsAv = psAv.executeQuery()) {
                if (rsAv.next()) {
                    prep.setAvancesEnAttente(rsAv.getBigDecimal(1));
                }
            }
        }

        return prep;
    }

    // =========================================================================
    // 2. SIMULATION DU CALCUL DU STC
    // =========================================================================

    public StcCalculResultDTO simulerStc(StcCalculRequest req, Connection c) throws Exception {
        StcPreparationDTO prep = preparerStc(req.getIdEmploye(), req.getDateDepart(), req.getMotifRupture(), c);

        StcCalculResultDTO res = new StcCalculResultDTO();
        res.setIdEmploye(prep.getIdEmploye());
        res.setMatricule(prep.getMatricule());
        res.setNomComplet(prep.getNomComplet());
        res.setIntitulePoste(prep.getIntitulePoste());
        res.setDepartement(prep.getDepartement());
        res.setSite(prep.getSite());
        res.setDateEmbauche(prep.getDateEmbauche());
        res.setDateDepart(req.getDateDepart());
        res.setMotifRupture(req.getMotifRupture());
        res.setAncienneteAnnees(prep.getAncienneteAnnees());
        res.setAncienneteMois(prep.getAncienneteMois());
        res.setSalaireBase(prep.getSalaireBase());
        res.setTauxHoraire(prep.getTauxHoraire());

        // 1. Salaire prorata dernier mois
        BigDecimal joursTravailles = req.getJoursTravaillesDernierMois() != null
                ? req.getJoursTravaillesDernierMois()
                : prep.getJoursTravaillesEstimes();
        res.setJoursTravaillesDernierMois(joursTravailles);

        BigDecimal salaireProrata = prep.getSalaireBase().multiply(joursTravailles)
                .divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP);
        res.setSalaireProrata(salaireProrata);
        res.ajouterLigne("SALAIRE_BASE_PRORATA", "Salaire au prorata du dernier mois (" + joursTravailles + "j/30)",
                "GAIN", prep.getSalaireBase(), null, salaireProrata);

        // 2. ICCP (Indemnité Compensatrice de Congés Payés) ou Retenue pour congés par anticipation
        BigDecimal soldeConges = req.getSoldeConges() != null ? req.getSoldeConges() : prep.getSoldeCongesRestant();
        BigDecimal tauxConge = prep.getTauxJournalierConge();
        BigDecimal iccp = MoneyRoundingPolicy.amount(soldeConges.multiply(tauxConge));
        res.setSoldeCongesRestant(soldeConges);
        res.setTauxJournalierConge(tauxConge);
        res.setIndemniteCongesPayes(iccp);

        BigDecimal gainConge = BigDecimal.ZERO;
        BigDecimal retenueCongeAnticipe = BigDecimal.ZERO;
        if (iccp.compareTo(BigDecimal.ZERO) > 0) {
            gainConge = iccp;
            res.ajouterLigne("INDEMNITE_COMPENSATRICE_CONGE", "Indemnité Compensatrice de Congés Payés (" + soldeConges + "j x " + tauxConge + " Ar)",
                    "GAIN", tauxConge, null, iccp);
        } else if (iccp.compareTo(BigDecimal.ZERO) < 0) {
            retenueCongeAnticipe = iccp.abs();
            res.ajouterLigne("RETENUE_CONGE_ANTICIPE", "Retenue pour congés pris par anticipation (" + soldeConges.abs() + "j x " + tauxConge + " Ar)",
                    "RETENUE", tauxConge, null, retenueCongeAnticipe);
        }

        // 3. Indemnités de rupture (Préavis & Licenciement)
        BigDecimal moisPreavis = req.getMoisPreavis() != null ? req.getMoisPreavis() : prep.getMoisPreavisSuggere();
        BigDecimal indemnitePreavis = req.getIndemnitePreavis() != null
                ? req.getIndemnitePreavis()
                : MoneyRoundingPolicy.amount(prep.getSalaireBase().multiply(moisPreavis));
        res.setMoisPreavis(moisPreavis);
        res.setIndemnitePreavis(indemnitePreavis);
        if (indemnitePreavis.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("INDEMNITE_PREAVIS", "Indemnité Compensatrice de Préavis (" + moisPreavis + " mois)",
                    "GAIN", prep.getSalaireBase(), null, indemnitePreavis);
        }

        BigDecimal indemniteLicenciement = req.getIndemniteLicenciement() != null
                ? req.getIndemniteLicenciement()
                : prep.getIndemniteLicenciementEstimee();
        res.setIndemniteLicenciement(indemniteLicenciement);
        if (indemniteLicenciement.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("INDEMNITE_LICENCIEMENT", "Indemnité Légale de Rupture / Licenciement",
                    "GAIN", null, null, indemniteLicenciement);
        }

        BigDecimal autresIndemnites = req.getAutresIndemnites() != null ? req.getAutresIndemnites() : BigDecimal.ZERO;
        res.setAutresIndemnites(autresIndemnites);
        res.setMotifAutresIndemnites(req.getMotifAutresIndemnites());
        if (autresIndemnites.compareTo(BigDecimal.ZERO) > 0) {
            String libAutre = req.getMotifAutresIndemnites() != null && !req.getMotifAutresIndemnites().isBlank()
                    ? req.getMotifAutresIndemnites()
                    : "Autres indemnités exceptionnelles de départ";
            res.ajouterLigne("AUTRES_INDEMNITES_DEPART", libAutre, "GAIN", null, null, autresIndemnites);
        }

        // Total Brut STC (Somme des gains bruts)
        BigDecimal totalBrut = salaireProrata.add(gainConge).add(indemnitePreavis).add(indemniteLicenciement).add(autresIndemnites);
        res.setTotalBrutStc(totalBrut);

        // 4. Cotisations Sociales & Assiette Fiscale
        // Note légale : L'indemnité de licenciement est exonérée de cotisations et d'IRSA.
        // Les congés pris par anticipation s'imputent sur l'assiette brute soumise.
        BigDecimal assietteBruteSoumise = salaireProrata.add(gainConge).subtract(retenueCongeAnticipe).max(BigDecimal.ZERO);
        BigDecimal assietteSociale = assietteBruteSoumise.add(indemnitePreavis);

        // Chargement dynamique des règles légales et plafonds
        PaieConfigLegale configLegale = chargerConfigLegale(req.getDateDepart(), c);
        // P1-03 : Supprimer le fallback 262680.00. Si aucune config légale datée n'est trouvée,
        // BLOQUER le calcul plutôt que d'utiliser une valeur inventée. LEGAL_REVIEW_REQUIRED.
        if (configLegale == null || configLegale.getSmeNational() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.LEGAL_CONFIG_MISSING,
                    "Aucun barème réglementaire (SME/SMIG) n'est configuré pour la date du "
                    + req.getDateDepart() + ". Veuillez renseigner l'exercice réglementaire correspondant dans les Paramètres de Paie (LEGAL_REVIEW_REQUIRED).");
        }
        BigDecimal smigRef = configLegale.getSmeNational();

        // CNaPS : taux valides à la date de départ (P1-03 : filtre de date historique)
        BigDecimal tauxCnapsSal = null;
        BigDecimal tauxCnapsPat = null;
        String sqlCnaps = "SELECT taux_salarial, taux_patronal FROM ref_type_cotisation "
                + "WHERE code = 'CNAPS' "
                + "AND (date_debut IS NULL OR date_debut <= ?) "
                + "AND (date_fin IS NULL OR date_fin >= ?) "
                + "ORDER BY date_debut DESC NULLS LAST LIMIT 1";
        try (PreparedStatement psCn = c.prepareStatement(sqlCnaps)) {
            psCn.setDate(1, req.getDateDepart());
            psCn.setDate(2, req.getDateDepart());
            try (ResultSet rsCn = psCn.executeQuery()) {
                if (rsCn.next()) { tauxCnapsSal = rsCn.getBigDecimal(1).movePointLeft(2); tauxCnapsPat = rsCn.getBigDecimal(2).movePointLeft(2); }
            }
        }
        if (tauxCnapsSal == null || tauxCnapsPat == null) throw new IllegalStateException("SOCIAL_CONFIGURATION_MISSING: CNAPS pour date=" + req.getDateDepart());
        // FMFP : taux valides à la date de départ (P1-03 : filtre de date historique)
        BigDecimal tauxFmfp = null;
        String sqlFmfp = "SELECT taux_patronal FROM ref_type_cotisation "
                + "WHERE code = 'FMFP' "
                + "AND (date_debut IS NULL OR date_debut <= ?) "
                + "AND (date_fin IS NULL OR date_fin >= ?) "
                + "ORDER BY date_debut DESC NULLS LAST LIMIT 1";
        try (PreparedStatement psFm = c.prepareStatement(sqlFmfp)) {
            psFm.setDate(1, req.getDateDepart());
            psFm.setDate(2, req.getDateDepart());
            try (ResultSet rsFm = psFm.executeQuery()) { if (rsFm.next() && rsFm.getBigDecimal(1) != null) tauxFmfp = rsFm.getBigDecimal(1).movePointLeft(2); }
        }
        if (tauxFmfp == null) throw new IllegalStateException("SOCIAL_CONFIGURATION_MISSING: FMFP pour date=" + req.getDateDepart());
        SocialContributionCalculator.Result socialResult = new SocialContributionCalculator().calculate(
                assietteSociale, new SocialContributionCalculator.Rules(tauxCnapsSal, tauxCnapsPat,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, smigRef, true));
        var resCnaps = socialResult.cnaps();
        BigDecimal baseCnaps = resCnaps.assiette();
        BigDecimal cnapsSal = resCnaps.partSalariale();
        res.setCotisationCnaps(cnapsSal);
        if (cnapsSal.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("COTISATION_CNAPS_SAL", "Cotisation CNaPS Salariale (1%)", "RETENUE", baseCnaps, new BigDecimal("1.00"), cnapsSal);
        }

        // Santé : Organisme et taux récupérés dynamiquement selon le site du collaborateur
        String codeSante = "OSTIE";
        BigDecimal tauxSanteSal = null;
        BigDecimal tauxSantePat = null;
        String sqlCotisSante = "SELECT tc.code, tc.taux_salarial, tc.taux_patronal " +
                "FROM site_cotisation sc " +
                "JOIN ref_site s ON sc.id_site = s.id_site " +
                "JOIN ref_type_cotisation tc ON sc.id_type_cotisation = tc.id_type_cotisation " +
                "WHERE (s.code_site = ? OR UPPER(s.libelle) LIKE ?) AND (tc.categorie = 'SANTE' OR (tc.code != 'CNAPS' AND tc.code != 'FMFP')) AND sc.est_actif = true LIMIT 1";
        try (PreparedStatement psCs = c.prepareStatement(sqlCotisSante)) {
            psCs.setString(1, prep.getCodeSite() != null ? prep.getCodeSite() : "");
            psCs.setString(2, "%" + (prep.getSite() != null ? prep.getSite().toUpperCase() : "") + "%");
            try (ResultSet rsCs = psCs.executeQuery()) {
                if (rsCs.next()) {
                    codeSante = rsCs.getString("code");
                    tauxSanteSal = rsCs.getBigDecimal("taux_salarial").movePointLeft(2);
                    tauxSantePat = rsCs.getBigDecimal("taux_patronal").movePointLeft(2);
                }
            }
        } catch (Exception ignored) {}

        if (tauxSanteSal == null || tauxSantePat == null) {
            throw new IllegalStateException("SOCIAL_CONFIGURATION_MISSING: santé");
        }

        var resSante = new SocialContributionCalculator().calculate(assietteSociale,
                new SocialContributionCalculator.Rules(tauxCnapsSal, tauxCnapsPat,
                        tauxSanteSal, tauxSantePat, tauxFmfp, smigRef, true)).health();
        BigDecimal santeSal = resSante.partSalariale();
        res.setCotisationSante(santeSal);
        if (santeSal.compareTo(BigDecimal.ZERO) > 0) {
            String libelleSante = "Cotisation " + codeSante + " Salariale (" + tauxSanteSal.movePointRight(2) + "%)";
            res.ajouterLigne("COTISATION_SANTE_SAL", libelleSante, "RETENUE", assietteSociale, tauxSanteSal.movePointRight(2), santeSal);
        }

        BigDecimal totalCotis = cnapsSal.add(santeSal);
        res.setTotalCotisationsSociales(totalCotis);

        // Cotisations patronales (CNaPS 13%, Santé patronale, FMFP 1%)
        BigDecimal cnapsPat = resCnaps.partPatronale();
        BigDecimal santePat = resSante.partPatronale();
        BigDecimal fmfpPat = MoneyRoundingPolicy.contribution(socialResult.fmfp());
        BigDecimal totalCotisPat = cnapsPat.add(santePat).add(fmfpPat);
        res.setTotalCotisationsPatronales(totalCotisPat);

        if (cnapsPat.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("COTISATION_CNAPS_PAT", "Cotisation CNaPS Patronale (13%)", "PATRONAL", baseCnaps, new BigDecimal("13.00"), cnapsPat);
        }
        if (santePat.compareTo(BigDecimal.ZERO) > 0) {
            String libelleSantePat = "Cotisation " + codeSante + " Patronale (" + tauxSantePat.movePointRight(2) + "%)";
            res.ajouterLigne("COTISATION_SANTE_PAT", libelleSantePat, "PATRONAL", assietteSociale, tauxSantePat.movePointRight(2), santePat);
        }
        if (fmfpPat.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("COTISATION_FMFP_PAT", "Cotisation FMFP Patronale (1%)", "PATRONAL", assietteSociale, new BigDecimal("1.00"), fmfpPat);
        }

        // Assiette IRSA
        BigDecimal netImposable = assietteSociale.subtract(totalCotis).max(BigDecimal.ZERO);
        res.setNetImposable(netImposable);

        BigDecimal irsaNet = BigDecimal.ZERO;
        // Régime fiscal résolu une seule fois dans la préparation STC.
        if ("ISI".equalsIgnoreCase(prep.getRegimeFiscal())) {
            BigDecimal tauxIsi = TaxConfigurationResolver.resolveIsiRate(req.getDateDepart().toLocalDate(), c);
            BigDecimal isi = new IsiCalculator().calculate(assietteSociale, tauxIsi);
            irsaNet = isi;
            res.setIrsaNet(BigDecimal.ZERO);
            res.ajouterLigne("RETENUE_ISI_5", "Retenue ISI", "RETENUE", assietteSociale, tauxIsi, isi);
        }

        // Calcul IRSA selon barème progressif dynamique unifié
        if (!"ISI".equalsIgnoreCase(prep.getRegimeFiscal())) {
        LocalDate dateRefIrsa = req.getDateDepart().toLocalDate();
        List<Tranche> tranches = chargerTranchesIrsa(configLegale, dateRefIrsa, c);
        int nbEnfants = prep.getNbEnfantsACharge();
        if (configLegale == null || configLegale.getDeductionChargeFamille() == null
                || configLegale.getMinimumPerceptionIrsa() == null) {
            throw new IllegalStateException("IRSA_CONFIGURATION_MISSING");
        }
        BigDecimal deducParEnfant = configLegale.getDeductionChargeFamille();
        BigDecimal minPercep = configLegale.getMinimumPerceptionIrsa();

        var irsaRes = new IrsaCalculator().calculate(
                netImposable, tranches, nbEnfants, deducParEnfant, minPercep
        );
        irsaNet = MoneyRoundingPolicy.tax(irsaRes.irsaNet().min(netImposable));
        res.setIrsaNet(irsaNet);
        if (irsaNet.compareTo(BigDecimal.ZERO) > 0) {
            String libelleIrsa = "Retenue à la Source IRSA (Barème Progressif " +
                    (configLegale != null && configLegale.getCodeVersion() != null ? configLegale.getCodeVersion() : "LF") + ")";
            res.ajouterLigne("IMPOT_IRSA_NET", libelleIrsa, "RETENUE", irsaRes.baseIrsaArrondie(), null, irsaNet);
        }
        if (irsaRes.reductionEnfants().compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("DEDUCTION_CHARGE_FAMILLE", "Déduction IRSA Charges Familiales (" + nbEnfants + " enfant(s))", "INFO", null, null, irsaRes.reductionEnfants());
        }
        }

        // Net avant déductions d'engagements personnels
        BigDecimal netAvantEngagements = totalBrut.subtract(retenueCongeAnticipe).subtract(totalCotis).subtract(irsaNet).max(BigDecimal.ZERO);

        // 5. Apurements des Engagements Financiers
        // Apurement Intégral des Prêts (PaiePret)
        BigDecimal soldePrets = prep.getCapitalRestantPrets();
        BigDecimal pretsADeduire = MoneyRoundingPolicy.amount(soldePrets.min(netAvantEngagements));
        res.setSoldePretsDeduit(pretsADeduire);
        if (pretsADeduire.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("SOLDE_PRET_STC", "Apurement Intégral Prêt d'Entreprise (Remboursement Anticipé)", "RETENUE", soldePrets, null, pretsADeduire);
        }

        BigDecimal netApresPret = netAvantEngagements.subtract(pretsADeduire).max(BigDecimal.ZERO);

        // Apurement des Avances en cours
        BigDecimal soldeAvances = prep.getAvancesEnAttente();
        BigDecimal avancesADeduire = MoneyRoundingPolicy.amount(soldeAvances.min(netApresPret));
        res.setSoldeAvancesDeduit(avancesADeduire);
        if (avancesADeduire.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("SOLDE_AVANCES_STC", "Apurement des Avances sur Salaire en Attente", "RETENUE", soldeAvances, null, avancesADeduire);
        }

        BigDecimal netApresAvances = netApresPret.subtract(avancesADeduire).max(BigDecimal.ZERO);

        // Autres déductions (ex: matériel non rendu)
        BigDecimal autresDeductions = req.getAutresDeductions() != null ? req.getAutresDeductions() : BigDecimal.ZERO;
        BigDecimal autresADeduire = MoneyRoundingPolicy.amount(autresDeductions.min(netApresAvances));
        res.setAutresDeductions(autresADeduire);
        res.setMotifAutresDeductions(req.getMotifAutresDeductions());
        if (autresADeduire.compareTo(BigDecimal.ZERO) > 0) {
            String libAutreDed = req.getMotifAutresDeductions() != null && !req.getMotifAutresDeductions().isBlank()
                    ? req.getMotifAutresDeductions()
                    : "Autres retenues finales";
            res.ajouterLigne("AUTRES_RETENUES_STC", libAutreDed, "RETENUE", null, null, autresADeduire);
        }

        // 6. Net Final à Payer (arrondi bancaire officiel Ariary à la dizaine la plus proche)
        BigDecimal netFinalNonArrondi = netApresAvances.subtract(autresADeduire).max(BigDecimal.ZERO);
        BigDecimal netFinal = BulletinPaie.arrondirDizaineAriary(netFinalNonArrondi);
        BigDecimal ecartArrondi = netFinal.subtract(netFinalNonArrondi);
        if (ecartArrondi.compareTo(BigDecimal.ZERO) != 0) {
            if (ecartArrondi.compareTo(BigDecimal.ZERO) > 0) {
                res.ajouterLigne("AJUSTEMENT_ARRONDI", "Ajustement d'arrondi monétaire", "GAIN", null, null, ecartArrondi);
            } else {
                res.ajouterLigne("AJUSTEMENT_ARRONDI", "Ajustement d'arrondi monétaire", "RETENUE", null, null, ecartArrondi.abs());
            }
        }
        res.setNetAPayerStc(netFinal);

        res.setStatut(req.isSimulation() ? "SIMULATION" : "VALIDE");
        res.setModeReglement(req.getModeReglement() != null ? req.getModeReglement() : "VIREMENT");
        res.setCommentaire(req.getCommentaire());

        return res;
    }

    // =========================================================================
    // 3. VALIDATION & ÉMISSION FORMELLE DU SOLDE DE TOUT COMPTE
    // =========================================================================

    public StcCalculResultDTO validerStc(StcCalculRequest req, Integer userId, Connection c) throws Exception {
        req.setSimulation(false);
        StcCalculResultDTO simulation = simulerStc(req, c);

        // 1. Génération du numéro de reçu officiel
        String numRecu = PaieStc.genererNumeroRecu(c);
        simulation.setNumeroRecu(numRecu);
        simulation.setStatut("VALIDE");

        // 2. Enregistrement en base de données dans paie_stc
        PaieStc entity = new PaieStc();
        entity.setIdEmploye(req.getIdEmploye());

        // Résolution dynamique de la période si non spécifiée
        Integer idPeriode = req.getIdPeriodePaie();
        if (idPeriode == null && req.getDateDepart() != null) {
            String sqlPer = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
            try (PreparedStatement psPer = c.prepareStatement(sqlPer)) {
                psPer.setDate(1, req.getDateDepart());
                try (ResultSet rsPer = psPer.executeQuery()) {
                    if (rsPer.next()) {
                        idPeriode = rsPer.getInt(1);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (idPeriode == null) idPeriode = 1;
        entity.setIdPeriodePaie(idPeriode);
        entity.setNumeroRecu(numRecu);
        entity.setDateDepart(req.getDateDepart());
        entity.setMotifRupture(req.getMotifRupture());

        entity.setSalaireBase(simulation.getSalaireBase());
        entity.setTauxHoraire(simulation.getTauxHoraire());
        entity.setAncienneteMois(simulation.getAncienneteMois());

        entity.setJoursTravaillesDernierMois(simulation.getJoursTravaillesDernierMois());
        entity.setSalaireProrata(simulation.getSalaireProrata());

        entity.setSoldeCongesRestant(simulation.getSoldeCongesRestant());
        entity.setTauxJournalierConge(simulation.getTauxJournalierConge());
        entity.setIndemniteCongesPayes(simulation.getIndemniteCongesPayes());

        entity.setMoisPreavis(simulation.getMoisPreavis());
        entity.setIndemnitePreavis(simulation.getIndemnitePreavis());
        entity.setIndemniteLicenciement(simulation.getIndemniteLicenciement());
        entity.setAutresIndemnites(simulation.getAutresIndemnites());
        entity.setMotifAutresIndemnites(simulation.getMotifAutresIndemnites());

        entity.setTotalBrutStc(simulation.getTotalBrutStc());
        entity.setTotalCotisationsSociales(simulation.getTotalCotisationsSociales());
        entity.setTotalIrsa(simulation.getIrsaNet());
        entity.setNetImposable(simulation.getNetImposable());

        entity.setSoldePretsDeduit(simulation.getSoldePretsDeduit());
        entity.setSoldeAvancesDeduit(simulation.getSoldeAvancesDeduit());
        entity.setAutresDeductions(simulation.getAutresDeductions());
        entity.setMotifAutresDeductions(simulation.getMotifAutresDeductions());

        entity.setNetAPayerStc(simulation.getNetAPayerStc());
        entity.setStatut("VALIDE");
        entity.setModeReglement(simulation.getModeReglement());
        entity.setCommentaire(simulation.getCommentaire());

        // Résolution dynamique ref_mode_paiement
        String modeReg = simulation.getModeReglement() != null ? simulation.getModeReglement() : "VIREMENT";
        try (PreparedStatement psMp = c.prepareStatement("SELECT id_mode_paiement FROM ref_mode_paiement WHERE UPPER(code) = UPPER(?) LIMIT 1")) {
            psMp.setString(1, modeReg);
            try (ResultSet rsMp = psMp.executeQuery()) {
                if (rsMp.next()) {
                    entity.setIdModeReglement(rsMp.getInt(1));
                }
            }
        } catch (Exception ignored) {}

        // Résolution dynamique ref_motif_sortie
        if (entity.getMotifRupture() != null && !entity.getMotifRupture().isBlank()) {
            try (PreparedStatement psMot = c.prepareStatement("SELECT id_motif_sortie FROM ref_motif_sortie WHERE UPPER(code) = UPPER(?) OR UPPER(libelle) LIKE ? LIMIT 1")) {
                psMot.setString(1, entity.getMotifRupture());
                psMot.setString(2, "%" + entity.getMotifRupture().toUpperCase() + "%");
                try (ResultSet rsMot = psMot.executeQuery()) {
                    if (rsMot.next()) {
                        entity.setIdMotifSortie(rsMot.getInt(1));
                    }
                }
            } catch (Exception ignored) {}
        }

        entity.setCreePar(userId);
        entity.setValidePar(userId);
        entity.setDateValidation(new Timestamp(System.currentTimeMillis()));

        entity.save(c);
        simulation.setIdStc(entity.getIdStc());

        // 2.1 Émission synchrone du Bulletin de Paie de sortie (STC) pour inclusion déclarative (DGI SAFI & CNaPS DNTS)
        BulletinPaie bpStc = new BulletinPaie();
        bpStc.setIdPeriodePaie(entity.getIdPeriodePaie());
        bpStc.setIdEmploye(entity.getIdEmploye());
        bpStc.setTypeBulletin("STC");

        // Résolution dynamique id_type_bulletin et id_mode_paiement
        try (PreparedStatement psTb = c.prepareStatement("SELECT id_type_bulletin FROM ref_type_bulletin WHERE code = 'STC' LIMIT 1");
             ResultSet rsTb = psTb.executeQuery()) {
            if (rsTb.next()) {
                bpStc.setIdTypeBulletin(rsTb.getInt(1));
            }
        } catch (Exception ignored) {}
        if (entity.getIdModeReglement() != null) {
            bpStc.setIdModePaiement(entity.getIdModeReglement());
        }

        bpStc.setReferenceBulletin("STC-" + entity.getNumeroRecu());
        bpStc.setMatricule(simulation.getMatricule() != null ? simulation.getMatricule() : "STC-" + req.getIdEmploye());
        bpStc.setNomPrenom(simulation.getNomComplet() != null ? simulation.getNomComplet() : "Salarié Sortant");
        bpStc.setIntitulePoste(simulation.getIntitulePoste() != null ? simulation.getIntitulePoste() : "Salarié");
        bpStc.setNomDepartement(simulation.getDepartement() != null ? simulation.getDepartement() : "Direction");
        bpStc.setNomSite(simulation.getSite() != null ? simulation.getSite() : "Antananarivo");
        bpStc.setRegimeIntervenant("PERMANENT");
        bpStc.setModeCalculPaie("STC");
        bpStc.setDateEmbauche(simulation.getDateEmbauche());
        bpStc.setAncienneteMois(simulation.getAncienneteMois());
        bpStc.setModePaiement(simulation.getModeReglement() != null ? simulation.getModeReglement() : "VIREMENT");

        bpStc.setSalaireBase(simulation.getSalaireBase());
        bpStc.setTauxHoraire(simulation.getTauxHoraire());
        bpStc.setTotalGainsBruts(simulation.getTotalBrutStc());
        bpStc.setTotalCotisationsSalariales(simulation.getTotalCotisationsSociales());
        bpStc.setTotalCotisationsPatronales(simulation.getTotalCotisationsPatronales());
        PaieConfigLegale configLegaleActive = chargerConfigLegale(req.getDateDepart(), c);
        if (configLegaleActive != null) {
            bpStc.setIdConfigLegale(configLegaleActive.getIdConfigLegale());
        }
        bpStc.setSalaireNetImposable(simulation.getNetImposable());
        bpStc.setIrsaNet(simulation.getIrsaNet());
        BigDecimal totalRetenuesStc = zeroIfNull(simulation.getSoldePretsDeduit())
                .add(zeroIfNull(simulation.getSoldeAvancesDeduit()))
                .add(zeroIfNull(simulation.getAutresDeductions()));
        bpStc.setTotalRetenuesAcomptePret(totalRetenuesStc);
        bpStc.setNetAvantArrondi(simulation.getNetAPayerStc());
        bpStc.setNetAPayer(simulation.getNetAPayerStc());
        bpStc.setStatut("VALIDE");
        bpStc.setDateCalcul(new Timestamp(System.currentTimeMillis()));
        bpStc.setCalculePar(userId);
        bpStc.setDateValidation(new Timestamp(System.currentTimeMillis()));
        bpStc.setValidePar(userId);
        bpStc.setCommentaire("Solde de Tout Compte : " + entity.getMotifRupture());

        // Nettoyage préalable d'un ancien bulletin STC pour la même période/employé si recalcul
        String sqlCleanStcBp = "DELETE FROM bulletin_paie WHERE id_periode_paie = ? AND id_employe = ? AND type_bulletin = 'STC'";
        try (PreparedStatement psClean = c.prepareStatement(sqlCleanStcBp)) {
            psClean.setInt(1, entity.getIdPeriodePaie());
            psClean.setInt(2, entity.getIdEmploye());
            psClean.executeUpdate();
        }

        bpStc.save(c);
        entity.setIdBulletin(bpStc.getIdBulletin());
        simulation.setIdBulletin(bpStc.getIdBulletin());

        // Mise à jour de la clé étrangère dans paie_stc
        String sqlUpdStcBp = "UPDATE paie_stc SET id_bulletin = ? WHERE id_stc = ?";
        try (PreparedStatement psUpdBp = c.prepareStatement(sqlUpdStcBp)) {
            psUpdBp.setInt(1, bpStc.getIdBulletin());
            psUpdBp.setInt(2, entity.getIdStc());
            psUpdBp.executeUpdate();
        }

        // Cache des rubriques pour lier id_rubrique
        Map<String, Integer> cacheRubriques = new HashMap<>();
        try (PreparedStatement psRub = c.prepareStatement("SELECT id_rubrique, code FROM paie_rubrique");
             ResultSet rsRub = psRub.executeQuery()) {
            while (rsRub.next()) {
                cacheRubriques.put(rsRub.getString("code"), rsRub.getInt("id_rubrique"));
            }
        } catch (Exception ignored) {}

        // Lignes complètes du bulletin STC ventilées depuis la simulation
        int ordre = 10;
        for (StcCalculResultDTO.StcLigneDTO sl : simulation.getLignes()) {
            if (sl.getMontant() == null || sl.getMontant().compareTo(BigDecimal.ZERO) == 0) continue;
            BulletinPaieLigne l = new BulletinPaieLigne();
            l.setIdBulletin(bpStc.getIdBulletin());
            l.setCodeRubrique(sl.getCode());
            l.setIdRubrique(resoudreIdRubrique(sl.getCode(), cacheRubriques));
            l.setLibelleRubrique(sl.getLibelle());
            l.setSens(sl.getSens());
            l.setCategorie(determinerCategorieRubrique(sl.getCode()));
            l.setBaseCalcul(sl.getBase());
            if ("PATRONAL".equalsIgnoreCase(sl.getSens())) {
                l.setTauxPatronal(sl.getTaux());
                l.setMontantPatronal(sl.getMontant());
            } else {
                l.setTauxSalarial(sl.getTaux());
                l.setMontantSalarial(sl.getMontant());
            }
            l.setOrdre(ordre);
            ordre += 10;
            l.save(c);
        }

        // 3. Apurement effectif des prêts en cours (PaiePret)
        if (simulation.getSoldePretsDeduit().compareTo(BigDecimal.ZERO) > 0) {
            PaiePret.solderPretsPourStc(req.getIdEmploye(), simulation.getSoldePretsDeduit(), bpStc.getIdBulletin(), c);
        }

        // 4. Le STC n'impute que la somme réellement retenue, sur les fonds versés.
        BigDecimal resteAImputer = simulation.getSoldeAvancesDeduit();
        if (resteAImputer != null && resteAImputer.signum() > 0) {
            String sqlDossiers = "SELECT a.id_avance, a.solde_restant FROM paie_avance_acompte a " +
                    "JOIN ref_type_mouvement_paie t ON t.id_type_mouvement = a.id_type_mouvement " +
                    "WHERE a.id_employe = ? AND t.categorie IN ('ACOMPTE','AVANCE') " +
                    "AND a.versement_confirme_at IS NOT NULL AND a.statut = 'APPROUVE' " +
                    "AND a.solde_restant > 0 ORDER BY a.date_paiement, a.id_avance FOR UPDATE OF a";
            try (PreparedStatement ps = c.prepareStatement(sqlDossiers)) {
                ps.setInt(1, req.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next() && resteAImputer.signum() > 0) {
                        int idAvance = rs.getInt("id_avance");
                        BigDecimal montant = rs.getBigDecimal("solde_restant").min(resteAImputer);
                        try (PreparedStatement update = c.prepareStatement(
                                "UPDATE paie_avance_acompte SET montant_impute = montant_impute + ?, " +
                                "solde_restant = solde_restant - ?, " +
                                "statut = CASE WHEN solde_restant - ? <= 0 THEN 'IMPUTE_PAIE' ELSE 'APPROUVE' END, " +
                                "id_bulletin = ?, updated_at = CURRENT_TIMESTAMP WHERE id_avance = ?")) {
                            update.setBigDecimal(1, montant);
                            update.setBigDecimal(2, montant);
                            update.setBigDecimal(3, montant);
                            update.setInt(4, bpStc.getIdBulletin());
                            update.setInt(5, idAvance);
                            update.executeUpdate();
                        }
                        try (PreparedStatement journal = c.prepareStatement(
                                "INSERT INTO paie_mouvement_imputation (id_avance, id_bulletin, montant) VALUES (?, ?, ?)")) {
                            journal.setInt(1, idAvance);
                            journal.setInt(2, bpStc.getIdBulletin());
                            journal.setBigDecimal(3, montant);
                            journal.executeUpdate();
                        }
                        resteAImputer = resteAImputer.subtract(montant);
                    }
                }
            }
            if (resteAImputer.signum() > 0) {
                throw new IllegalStateException("STC_AVANCES_INCOHERENTES: montant à imputer supérieur aux soldes versés.");
            }
        }

        // 5. Désactivation optionnelle de l'employé et clôture carrière
        // Note d'architecture (Règle Métier SIRH ACT-018 / ACT-025) :
        // La désactivation administrative standard de l'employé incombe au Workflow de Rupture RH (WorkflowRhService).
        // Si aucun workflow formel n'a été exécuté en amont et que le gestionnaire a requis `desactiverEmploye = true`,
        // le statut de l'employé, son contrat et son affectation carrière active sont clos pour éviter les salariés "fantômes".
        if (req.isDesactiverEmploye()) {
            try (PreparedStatement psEmp = c.prepareStatement(
                    "UPDATE employe SET est_actif = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id_employe = ?")) {
                psEmp.setInt(1, req.getIdEmploye());
                psEmp.executeUpdate();
            }
            // Clôture synchrone de l'affectation active pour cohérence stricte de la base
            String sqlClotureAff = "UPDATE employe_affectation_carriere SET " +
                    "id_statut_rh = COALESCE((SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'INACTIF' LIMIT 1), id_statut_rh), " +
                    "date_fin = COALESCE(date_fin, ?), updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id_employe = ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF' LIMIT 1)";
            try (PreparedStatement psAff = c.prepareStatement(sqlClotureAff)) {
                psAff.setDate(1, req.getDateDepart());
                psAff.setInt(2, req.getIdEmploye());
                psAff.executeUpdate();
            }
            // Clôture synchrone du contrat de travail actif pour éliminer les contrats fantômes
            String sqlClotureContrat = "UPDATE contrat_travail SET " +
                    "id_statut_rh = COALESCE((SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'CLOTURE' OR code = 'ROMPU' LIMIT 1), id_statut_rh), " +
                    "date_fin = COALESCE(date_fin, ?), updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id_employe = ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF' LIMIT 1)";
            try (PreparedStatement psCt = c.prepareStatement(sqlClotureContrat)) {
                psCt.setDate(1, req.getDateDepart());
                psCt.setInt(2, req.getIdEmploye());
                psCt.executeUpdate();
            }
        }

        // 6. Audit Log
        AuditLog.audit(c, "VALIDATION_STC", "paie_stc", String.valueOf(entity.getIdStc()), null, entity);

        return simulation;
    }

    // =========================================================================
    // 4. REÇU & QUITTANCE LIBÉRATOIRE OFFICIELLE
    // =========================================================================

    public StcRecuDTO obtenirRecuStc(Integer idStc, Connection c) throws Exception {
        PaieStc s = PaieStc.trouverParId(idStc, c);
        if (s == null) return null;

        StcPreparationDTO prep = preparerStc(s.getIdEmploye(), s.getDateDepart(), s.getMotifRupture(), c);

        StcRecuDTO r = new StcRecuDTO();
        r.setNumeroRecu(s.getNumeroRecu());
        r.setDateEmission(s.getDateValidation() != null ? s.getDateValidation() : new Timestamp(System.currentTimeMillis()));
        r.setStatut(s.getStatut());

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT raison_sociale, nif, stat FROM ref_filiale WHERE est_actif = TRUE ORDER BY id_filiale ASC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (rs.getString("raison_sociale") != null) r.setEntrepriseNom(rs.getString("raison_sociale"));
                    if (rs.getString("nif") != null) r.setEntrepriseNif(rs.getString("nif"));
                    if (rs.getString("stat") != null) r.setEntrepriseStat(rs.getString("stat"));
                }
            }
        } catch (Exception ignored) {}

        r.setIdEmploye(s.getIdEmploye());
        r.setMatricule(prep.getMatricule());
        r.setNomComplet(prep.getNomComplet());
        r.setCinNumero(prep.getCinNumero());
        r.setCinDateDelivrance(prep.getCinDateDelivrance());
        r.setCinLieuDelivrance(prep.getCinLieuDelivrance());
        r.setNumeroCnaps(prep.getNumeroCnaps());
        r.setIntitulePoste(prep.getIntitulePoste());
        r.setDepartement(prep.getDepartement());
        r.setSite(prep.getSite());
        r.setDateEmbauche(prep.getDateEmbauche());
        r.setDateDepart(s.getDateDepart());
        r.setMotifRupture(s.getMotifRupture());
        r.setAncienneteTexte(prep.getAncienneteAnnees() + " an(s) et " + (prep.getAncienneteMois() % 12) + " mois");

        r.setSalaireProrata(s.getSalaireProrata());
        r.setSoldeCongesRestant(s.getSoldeCongesRestant());
        r.setTauxJournalierConge(s.getTauxJournalierConge());

        BigDecimal iccp = s.getIndemniteCongesPayes() != null ? s.getIndemniteCongesPayes() : BigDecimal.ZERO;
        BigDecimal retenueCongeAnticipe = BigDecimal.ZERO;
        if (iccp.compareTo(BigDecimal.ZERO) < 0) {
            retenueCongeAnticipe = iccp.abs();
            r.setIndemniteCongesPayes(BigDecimal.ZERO);
            r.setAutresDeductions((s.getAutresDeductions() != null ? s.getAutresDeductions() : BigDecimal.ZERO).add(retenueCongeAnticipe));
        } else {
            r.setIndemniteCongesPayes(iccp);
            r.setAutresDeductions(s.getAutresDeductions() != null ? s.getAutresDeductions() : BigDecimal.ZERO);
        }

        r.setMoisPreavis(s.getMoisPreavis());
        r.setIndemnitePreavis(s.getIndemnitePreavis());
        r.setIndemniteLicenciement(s.getIndemniteLicenciement());
        r.setAutresIndemnites(s.getAutresIndemnites());
        r.setTotalBrut(s.getTotalBrutStc());

        r.setCotisationsSociales(s.getTotalCotisationsSociales());
        r.setRetenueIrsa(s.getTotalIrsa());
        r.setNetImposable(s.getNetImposable());

        r.setSoldePretsApure(s.getSoldePretsDeduit());
        r.setSoldeAvancesApure(s.getSoldeAvancesDeduit());
        BigDecimal totalRetenues = s.getTotalCotisationsSociales().add(s.getTotalIrsa())
                .add(s.getSoldePretsDeduit()).add(s.getSoldeAvancesDeduit()).add(r.getAutresDeductions());

        // Réconciliation stricte de l'arrondi légal à la dizaine d'Ariary pour la quittance imprimée
        BigDecimal deltaArrondi = s.getNetAPayerStc().subtract(s.getTotalBrutStc().subtract(totalRetenues));
        if (deltaArrondi.compareTo(BigDecimal.ZERO) > 0) {
            r.setTotalBrut(s.getTotalBrutStc().add(deltaArrondi));
            r.setTotalRetenues(totalRetenues);
        } else if (deltaArrondi.compareTo(BigDecimal.ZERO) < 0) {
            r.setTotalBrut(s.getTotalBrutStc());
            r.setTotalRetenues(totalRetenues.add(deltaArrondi.abs()));
        } else {
            r.setTotalBrut(s.getTotalBrutStc());
            r.setTotalRetenues(totalRetenues);
        }

        r.setNetAPayer(s.getNetAPayerStc());
        r.setModeReglement(s.getModeReglement());
        r.setNetEnLettres(convertirEnLettres(s.getNetAPayerStc()));

        r.setMentionLiberatoire(
                "Le salarié soussigné reconnaît avoir reçu de la société GALAXAM LOGISTICS la somme de " +
                s.getNetAPayerStc().toPlainString() + " Ariary pour solde de tout compte et règlement définitif " +
                "de tous salaires, indemnités et accessoires qui lui étaient dus au titre de l'exécution et de la résiliation " +
                "de son contrat de travail, conformément aux dispositions du Code du Travail Malgache."
        );

        return r;
    }

    public List<PaieStc> listerStc(Connection c) throws Exception {
        return PaieStc.lister(c);
    }

    public List<PaieStc> listerStcEmploye(Integer idEmploye, Connection c) throws Exception {
        if (idEmploye == null) return List.of();
        return PaieStc.listerParEmploye(idEmploye, c);
    }

    /**
     * Charge les tranches IRSA liées à la configLegale fournie.
     * Si configLegale est null, la date de référence est utilisée pour chercher
     * la LF active à cette date (et non CURRENT_DATE), garantissant la cohérence
     * lors des recalculs historiques (STC rétroactifs).
     */
    private List<Tranche> chargerTranchesIrsa(PaieConfigLegale configLegale, LocalDate dateRef, Connection c) throws Exception {
        if (dateRef == null) throw new IllegalArgumentException("La date métier IRSA est obligatoire.");
        return IrsaBracketResolver.resolve(configLegale != null ? configLegale.getIdConfigLegale() : null,
                dateRef, c);
    }

    private PaieConfigLegale chargerConfigLegale(Date dateDepart, Connection c) {
        if (dateDepart == null) throw new IllegalArgumentException("La date métier de la configuration légale est obligatoire.");
        try {
            PaieConfigLegale config = PaieConfigLegale.findActiveAt(c, dateDepart.toLocalDate());
            if (config == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.LEGAL_CONFIG_MISSING,
                        "Aucun barème réglementaire fiscal ou social (SME, CNaPS, IRSA) n'est configuré pour la date du " + dateDepart + ". "
                        + "Veuillez enregistrer la réglementation applicable dans les Paramètres de Paie > Réglementation Fiscale & Sociale (LEGAL_REVIEW_REQUIRED).");
            }
            return config;
        } catch (mg.galaxam.sirh.common.exception.SirhBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de résoudre la configuration légale au " + dateDepart + ".", e);
        }
    }

    private static final mg.galaxam.sirh.conge.application.CongeTauxApplicableService congeTauxApplicableService =
            new mg.galaxam.sirh.conge.application.CongeTauxApplicableService();

    public static BigDecimal getTauxJournalierApplicable(int idEmploye, LocalDate dateReference, BigDecimal salaireBaseFallback, Connection c) {
        if (dateReference == null) {
            throw new IllegalArgumentException("La date de référence temporelle est obligatoire pour résoudre TCONGE.");
        }
        return congeTauxApplicableService.resoudreTauxCongeApplicable(idEmploye, dateReference, salaireBaseFallback, c).getTauxJournalier();
    }

    public static BigDecimal getTauxJournalierDernier(int idEmploye, BigDecimal salaireBaseFallback, Connection c) {
        return getTauxJournalierApplicable(idEmploye, LocalDate.now(), salaireBaseFallback, c);
    }

    private AncienneteEngine.AncienneteBilan calculerAncienneteEmploye(int idEmploye, Date dateDepart, Connection c) {
        if (dateDepart == null) throw new IllegalArgumentException("La date métier d'ancienneté est obligatoire.");
        LocalDate refDate = dateDepart.toLocalDate();
        return this.ancienneteEngine.calculerAncienneteEmploye(idEmploye, refDate, c);
    }

    private String convertirEnLettres(BigDecimal montant) {
        if (montant == null) return "Zéro Ariary";
        long val = montant.longValue();
        return val + " Ariary";
    }

    private String determinerCategorieRubrique(String code) {
        if (code == null) return "AUTRE";
        if (code.startsWith("SALAIRE_BASE") || code.contains("CONGE")) return "SALAIRE_BASE";
        if (code.startsWith("INDEMNITE_")) return "INDEMNITE_RUPTURE";
        if (code.startsWith("COTISATION_") && code.endsWith("_PAT")) return "COTISATION_PATRONALE";
        if (code.startsWith("COTISATION_")) return "COTISATION_SALARIALE";
        if (code.startsWith("IMPOT_") || code.contains("IRSA")) return "IMPOT";
        if (code.startsWith("SOLDE_") || code.startsWith("AUTRES_RETENUES")) return "RETENUE_DIVERSE";
        if (code.contains("ARRONDI")) return "NET";
        return "AUTRE";
    }

    private Integer resoudreIdRubrique(String codeRubrique, Map<String, Integer> cache) {
        if (codeRubrique == null || cache == null) return null;
        Integer id = cache.get(codeRubrique);
        if (id != null) return id;

        if ("SALAIRE_BASE_PRORATA".equalsIgnoreCase(codeRubrique)) return cache.get("SALAIRE_BASE");
        if ("RETENUE_CONGE_ANTICIPE".equalsIgnoreCase(codeRubrique)) return cache.get("RETENUE_CONGE");
        if ("COTISATION_SANTE_SAL".equalsIgnoreCase(codeRubrique)) {
            Integer idOstie = cache.get("COTISATION_OSTIE_SAL");
            return idOstie != null ? idOstie : cache.get("COTISATION_OMSI_SAL");
        }
        if ("COTISATION_SANTE_PAT".equalsIgnoreCase(codeRubrique)) {
            Integer idOstiePat = cache.get("COTISATION_OSTIE_PAT");
            return idOstiePat != null ? idOstiePat : cache.get("COTISATION_OMSI_PAT");
        }
        if ("SOLDE_PRET_STC".equalsIgnoreCase(codeRubrique)) return cache.get("RETENUE_ECHEANCE_PRET");
        if ("SOLDE_AVANCES_STC".equalsIgnoreCase(codeRubrique)) return cache.get("RETENUE_AVANCE_SPECIALE");
        if ("AUTRES_RETENUES_STC".equalsIgnoreCase(codeRubrique)) return cache.get("RETENUE_DIVERSE");
        if ("AUTRES_INDEMNITES_DEPART".equalsIgnoreCase(codeRubrique)) return cache.get("AUTIMP");

        return null;
    }

    private BigDecimal zeroIfNull(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
}
