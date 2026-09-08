package mg.galaxam.sirh.model.paie.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paie.stc.*;
import mg.galaxam.sirh.model.conge.VSoldeCongeEmploye;
import mg.galaxam.sirh.model.paie.PaiePret;
import mg.galaxam.sirh.model.paie.PaieStc;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.stereotype.Service;

/**
 * Service Métier pour le traitement, la simulation, la validation et l'émission
 * du Solde de Tout Compte (STC) & Quittance Libératoire (Loi n° 2003-044).
 */
@Service
public class StcService {

    private final DataSource dataSource;

    public StcService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // =========================================================================
    // 1. PRÉPARATION ADMINISTRATIVE & COLLECTE DES DROITS
    // =========================================================================

    public StcPreparationDTO preparerStc(Integer idEmploye, Date dateDepart, String motif, Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'identifiant de l'employé est obligatoire.");
        }
        Date dateDepartEffective = dateDepart != null ? dateDepart : new Date(System.currentTimeMillis());

        StcPreparationDTO prep = new StcPreparationDTO();
        prep.setIdEmploye(idEmploye);
        prep.setDateDepart(dateDepartEffective);
        prep.setMotifRupture(motif != null ? motif : "DEMISSION");

        // 1. Informations de l'employé et affectation
        String sqlEmp = "SELECT e.matricule, e.nom, e.prenom, eac.salaire_base, " +
                "f.libelle AS fonction_nom, d.libelle AS dept_nom, s.libelle AS site_nom, " +
                "cat.code AS cat_code, ct.date_debut AS date_embauche " +
                "FROM employe e " +
                "JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe " +
                "JOIN ref_statut_rh rsa ON eac.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
                "LEFT JOIN contrat_travail ct ON eac.id_contrat = ct.id_contrat " +
                "LEFT JOIN ref_fonction f ON eac.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_departement d ON eac.id_departement = d.id_departement " +
                "LEFT JOIN ref_site s ON eac.id_site = s.id_site " +
                "LEFT JOIN ref_categorie_professionnelle cat ON eac.id_categorie_professionnelle = cat.id_categorie_professionnelle " +
                "WHERE e.id_employe = ?";

        Date dateEmbauche = null;
        BigDecimal salaireBase = BigDecimal.ZERO;
        String catPro = "AGENT";

        try (PreparedStatement ps = c.prepareStatement(sqlEmp)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    prep.setMatricule(rs.getString("matricule"));
                    String nom = rs.getString("nom");
                    String prenom = rs.getString("prenom");
                    prep.setNomComplet(((nom != null ? nom : "") + " " + (prenom != null ? prenom : "")).trim());
                    prep.setIntitulePoste(rs.getString("fonction_nom") != null ? rs.getString("fonction_nom") : "Collaborateur");
                    prep.setDepartement(rs.getString("dept_nom"));
                    prep.setSite(rs.getString("site_nom"));
                    catPro = rs.getString("cat_code") != null ? rs.getString("cat_code") : "AGENT";
                    prep.setCategorieProfessionnelle(catPro);
                    salaireBase = rs.getBigDecimal("salaire_base") != null ? rs.getBigDecimal("salaire_base") : BigDecimal.ZERO;
                    prep.setSalaireBase(salaireBase);
                    dateEmbauche = rs.getDate("date_embauche");
                    prep.setDateEmbauche(dateEmbauche);
                } else {
                    throw new IllegalArgumentException("Dossier actif introuvable pour l'employé ID: " + idEmploye);
                }
            }
        }

        // Taux horaire légal (173.33h mensuelles)
        BigDecimal tauxHoraire = salaireBase.divide(new BigDecimal("173.33"), 4, RoundingMode.HALF_UP);
        prep.setTauxHoraire(tauxHoraire);

        // 2. Ancienneté
        if (dateEmbauche != null) {
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
                "WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
        try (PreparedStatement psPer = c.prepareStatement(sqlPer)) {
            psPer.setDate(1, dateDepartEffective);
            try (ResultSet rsPer = psPer.executeQuery()) {
                if (rsPer.next()) {
                    prep.setIdPeriodePaie(rsPer.getInt("id_periode_paie"));
                    prep.setCodePeriodePaie(rsPer.getString("code"));
                } else {
                    // Fallback sur la dernière période ouverte
                    String sqlLastPer = "SELECT id_periode_paie, code FROM periode_paie ORDER BY date_debut DESC LIMIT 1";
                    try (PreparedStatement psL = c.prepareStatement(sqlLastPer);
                         ResultSet rsL = psL.executeQuery()) {
                        if (rsL.next()) {
                            prep.setIdPeriodePaie(rsL.getInt("id_periode_paie"));
                            prep.setCodePeriodePaie(rsL.getString("code"));
                        }
                    }
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

        // Taux journalier valorisé TCONGE issu du Grand-Livre
        BigDecimal tauxConge = BigDecimal.ZERO;
        String sqlTauxConge = "SELECT taux_journalier_conge FROM conge_journal_droit " +
                "WHERE id_employe = ? ORDER BY annee DESC, mois DESC LIMIT 1";
        try (PreparedStatement psTC = c.prepareStatement(sqlTauxConge)) {
            psTC.setInt(1, idEmploye);
            try (ResultSet rsTC = psTC.executeQuery()) {
                if (rsTC.next()) {
                    tauxConge = rsTC.getBigDecimal("taux_journalier_conge");
                }
            }
        }
        if (tauxConge == null || tauxConge.compareTo(BigDecimal.ZERO) <= 0) {
            tauxConge = salaireBase.divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP);
        }
        prep.setTauxJournalierConge(tauxConge);

        BigDecimal iccpEstimee = soldeJours.multiply(tauxConge).setScale(2, RoundingMode.HALF_UP);
        prep.setIccpEstimee(iccpEstimee);

        // 6. Préavis suggéré et Indemnité de licenciement légale
        boolean estCadre = "CADRE".equalsIgnoreCase(catPro) || "CADRE_DIRIGEANT".equalsIgnoreCase(catPro);
        boolean estLicenciement = "LICENCIEMENT".equalsIgnoreCase(motif) || "DEPART_RETRAITE".equalsIgnoreCase(motif);

        if (estLicenciement) {
            // Préavis légal / conventionnel (Cadre = 3 mois, Non-Cadre = 1 à 2 mois)
            BigDecimal moisPreavis = estCadre ? new BigDecimal("3.0") : (prep.getAncienneteAnnees() >= 3 ? new BigDecimal("2.0") : new BigDecimal("1.0"));
            prep.setMoisPreavisSuggere(moisPreavis);
            prep.setIndemnitePreavisEstimee(salaireBase.multiply(moisPreavis).setScale(2, RoundingMode.HALF_UP));

            // Indemnité légale de licenciement (ex: 10 jours de salaire par année complète)
            int annees = prep.getAncienneteAnnees();
            if (annees >= 1) {
                BigDecimal tauxJourLic = salaireBase.divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP);
                BigDecimal montantLic = tauxJourLic.multiply(new BigDecimal(annees * 10L)).setScale(2, RoundingMode.HALF_UP);
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

        String sqlAvances = "SELECT COALESCE(SUM(montant), 0) FROM paie_avance_acompte " +
                "WHERE id_employe = ? AND statut IN ('EN_ATTENTE', 'APPROUVE')";
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
        res.ajouterLigne("SALAIRE_PRORATA", "Salaire au prorata du dernier mois (" + joursTravailles + "j/30)",
                "GAIN", prep.getSalaireBase(), null, salaireProrata);

        // 2. ICCP (Indemnité Compensatrice de Congés Payés)
        BigDecimal soldeConges = req.getSoldeConges() != null ? req.getSoldeConges() : prep.getSoldeCongesRestant();
        BigDecimal tauxConge = prep.getTauxJournalierConge();
        BigDecimal iccp = soldeConges.multiply(tauxConge).setScale(2, RoundingMode.HALF_UP);
        res.setSoldeCongesRestant(soldeConges);
        res.setTauxJournalierConge(tauxConge);
        res.setIndemniteCongesPayes(iccp);
        if (iccp.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("INDEMNITE_COMPENSATRICE_CONGE", "Indemnité Compensatrice de Congés Payés (" + soldeConges + "j x " + tauxConge + " Ar)",
                    "GAIN", tauxConge, null, iccp);
        }

        // 3. Indemnités de rupture (Préavis & Licenciement)
        BigDecimal moisPreavis = req.getMoisPreavis() != null ? req.getMoisPreavis() : prep.getMoisPreavisSuggere();
        BigDecimal indemnitePreavis = req.getIndemnitePreavis() != null
                ? req.getIndemnitePreavis()
                : prep.getSalaireBase().multiply(moisPreavis).setScale(2, RoundingMode.HALF_UP);
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

        // Total Brut STC
        BigDecimal totalBrut = salaireProrata.add(iccp).add(indemnitePreavis).add(indemniteLicenciement).add(autresIndemnites);
        res.setTotalBrutStc(totalBrut);

        // 4. Cotisations Sociales & Assiette Fiscale
        // Note légale : L'indemnité de licenciement est exonérée de cotisations et d'IRSA
        BigDecimal assietteSociale = salaireProrata.add(iccp).add(indemnitePreavis);

        // CNaPS 1% plafonnée (8 x SME national = 8 x 262 680 = 2 101 440 Ar)
        BigDecimal plafondCnaps = new BigDecimal("2101440.00");
        BigDecimal baseCnaps = assietteSociale.min(plafondCnaps);
        BigDecimal cnapsSal = baseCnaps.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        res.setCotisationCnaps(cnapsSal);
        if (cnapsSal.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("COTISATION_CNAPS_SAL", "Cotisation CNaPS Salariale (1%)", "RETENUE", baseCnaps, new BigDecimal("1.00"), cnapsSal);
        }

        // Santé 1% (non plafonnée)
        BigDecimal santeSal = assietteSociale.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        res.setCotisationSante(santeSal);
        if (santeSal.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("COTISATION_SANTE_SAL", "Cotisation Santé Salariale (1%)", "RETENUE", assietteSociale, new BigDecimal("1.00"), santeSal);
        }

        BigDecimal totalCotis = cnapsSal.add(santeSal);
        res.setTotalCotisationsSociales(totalCotis);

        // Assiette IRSA
        BigDecimal netImposable = assietteSociale.subtract(totalCotis).max(BigDecimal.ZERO);
        res.setNetImposable(netImposable);

        // Calcul IRSA selon barème progressif (via CalculateurPaieEngine)
        CalculateurPaieEngine engine = new CalculateurPaieEngine();
        List<CalculateurPaieEngine.TrancheIrsa> tranches = chargerTranchesIrsa(c);
        BigDecimal irsaNet = engine.calculerIrsa(netImposable, tranches, 0);
        res.setIrsaNet(irsaNet);
        if (irsaNet.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("IMPOT_IRSA_NET", "Retenue à la Source IRSA (Barème Progressif LF 2026)", "RETENUE", netImposable, null, irsaNet);
        }

        // Net avant déductions d'engagements personnels
        BigDecimal netAvantEngagements = totalBrut.subtract(totalCotis).subtract(irsaNet).max(BigDecimal.ZERO);

        // 5. Apurements des Engagements Financiers
        // Apurement Intégral des Prêts (PaiePret)
        BigDecimal soldePrets = prep.getCapitalRestantPrets();
        BigDecimal pretsADeduire = soldePrets.min(netAvantEngagements).setScale(2, RoundingMode.HALF_UP);
        res.setSoldePretsDeduit(pretsADeduire);
        if (pretsADeduire.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("SOLDE_PRET_STC", "Apurement Intégral Prêt d'Entreprise (Remboursement Anticipé)", "RETENUE", soldePrets, null, pretsADeduire);
        }

        BigDecimal netApresPret = netAvantEngagements.subtract(pretsADeduire).max(BigDecimal.ZERO);

        // Apurement des Avances en cours
        BigDecimal soldeAvances = prep.getAvancesEnAttente();
        BigDecimal avancesADeduire = soldeAvances.min(netApresPret).setScale(2, RoundingMode.HALF_UP);
        res.setSoldeAvancesDeduit(avancesADeduire);
        if (avancesADeduire.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("SOLDE_AVANCES_STC", "Apurement des Avances sur Salaire en Attente", "RETENUE", soldeAvances, null, avancesADeduire);
        }

        BigDecimal netApresAvances = netApresPret.subtract(avancesADeduire).max(BigDecimal.ZERO);

        // Autres déductions (ex: matériel non rendu)
        BigDecimal autresDeductions = req.getAutresDeductions() != null ? req.getAutresDeductions() : BigDecimal.ZERO;
        BigDecimal autresADeduire = autresDeductions.min(netApresAvances).setScale(2, RoundingMode.HALF_UP);
        res.setAutresDeductions(autresADeduire);
        res.setMotifAutresDeductions(req.getMotifAutresDeductions());
        if (autresADeduire.compareTo(BigDecimal.ZERO) > 0) {
            String libAutreDed = req.getMotifAutresDeductions() != null && !req.getMotifAutresDeductions().isBlank()
                    ? req.getMotifAutresDeductions()
                    : "Autres retenues finales";
            res.ajouterLigne("AUTRES_RETENUES_STC", libAutreDed, "RETENUE", null, null, autresADeduire);
        }

        // 6. Net Final à Payer (arrondi bancaire à la dizaine la plus proche)
        BigDecimal netFinalNonArrondi = netApresAvances.subtract(autresADeduire).max(BigDecimal.ZERO);
        long centimes = netFinalNonArrondi.multiply(BigDecimal.ONE).longValue();
        long arrondiDizaine = (centimes / 10L) * 10L;
        BigDecimal netFinal = new BigDecimal(arrondiDizaine).setScale(2, RoundingMode.HALF_UP);
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
        entity.setIdPeriodePaie(req.getIdPeriodePaie() != null ? req.getIdPeriodePaie() : 1);
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

        entity.setCreePar(userId);
        entity.setValidePar(userId);
        entity.setDateValidation(new Timestamp(System.currentTimeMillis()));

        entity.save(c);
        simulation.setIdStc(entity.getIdStc());

        // 3. Apurement effectif des prêts en cours (PaiePret)
        if (simulation.getSoldePretsDeduit().compareTo(BigDecimal.ZERO) > 0) {
            PaiePret.solderPretsPourStc(req.getIdEmploye(), null, c);
        }

        // 4. Clôture des avances en cours
        String sqlCloseAvances = "UPDATE paie_avance_acompte SET statut = 'IMPUTE_PAIE', " +
                "motif = CONCAT(COALESCE(motif, ''), ' [Soldé par STC ', ?, ']') " +
                "WHERE id_employe = ? AND statut IN ('EN_ATTENTE', 'APPROUVE')";
        try (PreparedStatement psAv = c.prepareStatement(sqlCloseAvances)) {
            psAv.setString(1, numRecu);
            psAv.setInt(2, req.getIdEmploye());
            psAv.executeUpdate();
        }

        // 5. Audit Log
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

        r.setIdEmploye(s.getIdEmploye());
        r.setMatricule(prep.getMatricule());
        r.setNomComplet(prep.getNomComplet());
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
        r.setIndemniteCongesPayes(s.getIndemniteCongesPayes());
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
        r.setAutresDeductions(s.getAutresDeductions());
        BigDecimal totalRetenues = s.getTotalCotisationsSociales().add(s.getTotalIrsa())
                .add(s.getSoldePretsDeduit()).add(s.getSoldeAvancesDeduit()).add(s.getAutresDeductions());
        r.setTotalRetenues(totalRetenues);

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

    private List<CalculateurPaieEngine.TrancheIrsa> chargerTranchesIrsa(Connection c) {
        List<CalculateurPaieEngine.TrancheIrsa> list = new ArrayList<>();
        String sql = "SELECT tranche_min, tranche_max, taux FROM bareme_irsa " +
                "WHERE id_config_legale = (SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026' LIMIT 1) " +
                "ORDER BY numero_tranche ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new CalculateurPaieEngine.TrancheIrsa(
                        rs.getBigDecimal("tranche_min"),
                        rs.getBigDecimal("tranche_max"),
                        rs.getDouble("taux")
                ));
            }
        } catch (Exception ignored) {
            // Utilise le barème par défaut de CalculateurPaieEngine
        }
        return list;
    }

    private String convertirEnLettres(BigDecimal montant) {
        if (montant == null) return "Zéro Ariary";
        long val = montant.longValue();
        return val + " Ariary";
    }
}
