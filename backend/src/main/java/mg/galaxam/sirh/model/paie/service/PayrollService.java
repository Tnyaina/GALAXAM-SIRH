package mg.galaxam.sirh.model.paie.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import mg.galaxam.sirh.model.employe.EmployeEnfant;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.PaieAvanceAcompte;
import mg.galaxam.sirh.model.paie.PaieHonorairePrestation;
import mg.galaxam.sirh.model.paie.PaiePretEcheance;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet.ImpactPret;
import mg.galaxam.sirh.model.paie.dto.PayrollBatchResult;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.TrancheIrsa;

/**
 * Service d'orchestration de paie.
 * Collecte les données sources amont (RH, pointage, congés, enfants, prêts, acomptes),
 * exécute le moteur déterministe PayrollExecutionEngine, et persiste les résultats de manière immuable.
 */
@Service
public class PayrollService {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PayrollExecutionEngine payrollExecutionEngine;

    // Cache local des id_rubrique par code
    private final Map<String, Integer> cacheRubriques = new HashMap<>();

    /**
     * Calcule le bulletin d'un salarié pour une période donnée (transactionnel avec Connection fournie).
     */
    public BulletinPaieComplet calculerBulletinEmploye(Integer idEmploye, Integer idPeriodePaie,
                                                      boolean simulation, Integer idUtilisateur,
                                                      Connection c) throws Exception {
        if (idEmploye == null || idPeriodePaie == null) {
            throw new IllegalArgumentException("L'id de l'employé et l'id de la période de paie sont obligatoires.");
        }

        // 1. Collecte des données et construction du PayrollCalculationInput
        PayrollCalculationInput input = preparerInput(idEmploye, idPeriodePaie, simulation, c);

        // 2. Exécution du moteur de calcul pur
        BulletinPaieComplet complet = payrollExecutionEngine.calculer(input);
        BulletinPaie bp = complet.getBulletin();

        if (idUtilisateur != null) {
            bp.setCalculePar(idUtilisateur);
            if (!simulation) {
                bp.setValidePar(idUtilisateur);
                bp.setDateValidation(new Timestamp(System.currentTimeMillis()));
            }
        }

        // 3. Persistance du bulletin et de ses lignes
        sauvegarderBulletinEtLignes(complet, c);

        // 4. Si validation réelle (hors simulation) : imputation définitive des avances et prêts
        if (!simulation) {
            imputerAvancesEtPrets(complet, idPeriodePaie, c);
        }

        return complet;
    }

    /**
     * Surcharge sans Connection pour appel de service autonome (gère sa propre transaction).
     */
    public BulletinPaieComplet calculerBulletinEmploye(Integer idEmploye, Integer idPeriodePaie,
                                                      boolean simulation, Integer idUtilisateur) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                BulletinPaieComplet res = calculerBulletinEmploye(idEmploye, idPeriodePaie, simulation, idUtilisateur, c);
                c.commit();
                return res;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Calcule la paie de tous les salariés actifs pour une période donnée.
     */
    public PayrollBatchResult calculerPaiePeriode(Integer idPeriodePaie, boolean simulation,
                                                 Integer idUtilisateur, Connection c) throws Exception {
        String sqlPeriode = "SELECT code FROM periode_paie WHERE id_periode_paie = ?";
        String codePeriode = "PER";
        try (PreparedStatement ps = c.prepareStatement(sqlPeriode)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    codePeriode = rs.getString("code");
                }
            }
        }

        PayrollBatchResult batch = new PayrollBatchResult(idPeriodePaie, codePeriode);

        // Liste des salariés permanents / temporaires actifs (exclusion stricte des consultants / ISI 5%)
        String sqlEmployes = "SELECT DISTINCT e.id_employe, e.matricule FROM employe e " +
                "JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe " +
                "JOIN ref_statut_rh rsa ON eac.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
                "LEFT JOIN contrat_travail ct ON eac.id_contrat = ct.id_contrat " +
                "LEFT JOIN ref_type_intervenant tri ON ct.id_type_intervenant = tri.id_type_intervenant " +
                "WHERE e.est_actif = true " +
                "AND COALESCE(tri.code, 'PERMANENT') NOT IN ('CONSULTANT', 'PRESTATAIRE', 'ISI_5') " +
                "ORDER BY e.matricule ASC";

        List<Integer> idsEmployes = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sqlEmployes);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                idsEmployes.add(rs.getInt("id_employe"));
            }
        }

        for (Integer idEmp : idsEmployes) {
            BulletinPaieComplet bpc = calculerBulletinEmploye(idEmp, idPeriodePaie, simulation, idUtilisateur, c);
            batch.ajouterBulletin(bpc);
        }

        // Si validation réelle : génération automatique des bordereaux d'honoraires pour les consultants
        if (!simulation) {
            genererHonorairesPeriode(idPeriodePaie, idUtilisateur, c);
        }

        return batch;
    }

    /**
     * Génère ou actualise les bordereaux d'honoraires et prestations externes (Régime ISI 5%)
     * pour tous les intervenants externes / consultants de la période.
     */
    public List<PaieHonorairePrestation> genererHonorairesPeriode(Integer idPeriodePaie, Integer idUtilisateur, Connection c) throws Exception {
        String sql = "SELECT DISTINCT e.id_employe, e.nom, e.prenom, e.cin_numero, e.adresse, e.ville, " +
                "eac.salaire_base, f.libelle AS fonction_libelle, p.date_fin " +
                "FROM employe e " +
                "JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe " +
                "JOIN ref_statut_rh rsa ON eac.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
                "LEFT JOIN contrat_travail ct ON eac.id_contrat = ct.id_contrat " +
                "JOIN ref_type_intervenant tri ON ct.id_type_intervenant = tri.id_type_intervenant " +
                "LEFT JOIN ref_fonction f ON eac.id_fonction = f.id_fonction " +
                "CROSS JOIN periode_paie p " +
                "WHERE e.est_actif = true " +
                "AND tri.code IN ('CONSULTANT', 'PRESTATAIRE', 'ISI_5') " +
                "AND p.id_periode_paie = ?";

        List<PaieHonorairePrestation> honoraires = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idEmp = rs.getInt("id_employe");
                    String nom = rs.getString("nom");
                    String prenom = rs.getString("prenom");
                    String cin = rs.getString("cin_numero");
                    String adr = rs.getString("adresse");
                    String ville = rs.getString("ville");
                    BigDecimal brut = rs.getBigDecimal("salaire_base");
                    String fonction = rs.getString("fonction_libelle");
                    Date dateFin = rs.getDate("date_fin");

                    // Vérifier si un enregistrement existe déjà pour cet intervenant et cette période
                    String checkSql = "SELECT id_prestation FROM paie_honoraire_prestation WHERE id_periode_paie = ? AND id_intervenant = ?";
                    Integer existId = null;
                    try (PreparedStatement psChk = c.prepareStatement(checkSql)) {
                        psChk.setInt(1, idPeriodePaie);
                        psChk.setInt(2, idEmp);
                        try (ResultSet rsChk = psChk.executeQuery()) {
                            if (rsChk.next()) {
                                existId = rsChk.getInt("id_prestation");
                            }
                        }
                    }

                    PaieHonorairePrestation hono = existId != null ? PaieHonorairePrestation.trouverParId(existId, c) : new PaieHonorairePrestation();
                    hono.setIdPeriodePaie(idPeriodePaie);
                    hono.setIdIntervenant(idEmp);
                    hono.setNomComplet(((nom != null ? nom : "") + " " + (prenom != null ? prenom : "")).trim());
                    hono.setCinOuNif(cin != null ? cin : "NIF-A-PRECISER");
                    hono.setIntitulePrestation(fonction != null ? fonction : "Prestation de Conseil / Expertise");
                    hono.setNatureService("PRESTATIONS DE SERVICES");
                    hono.setDatePrestation(dateFin != null ? dateFin : new Date(System.currentTimeMillis()));
                    hono.setDatePaiement(dateFin != null ? dateFin : new Date(System.currentTimeMillis()));
                    hono.setMontantBrut(brut != null ? brut : BigDecimal.ZERO);
                    hono.setTauxIsi(new BigDecimal("5.00"));
                    hono.setAdresse(adr != null ? adr : "Antananarivo");
                    hono.setCommune(ville != null ? ville : "ANTANANARIVO RENIVOHITRA C.U");
                    hono.calculerMontants();

                    if (existId != null) {
                        hono.update(c);
                    } else {
                        hono.save(c);
                    }
                    honoraires.add(hono);
                }
            }
        }
        return honoraires;
    }

    public List<PaieHonorairePrestation> genererHonorairesPeriode(Integer idPeriodePaie, Integer idUtilisateur) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                List<PaieHonorairePrestation> list = genererHonorairesPeriode(idPeriodePaie, idUtilisateur, c);
                c.commit();
                return list;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Surcharge sans Connection pour calcul collectif autonome.
     */
    public PayrollBatchResult calculerPaiePeriode(Integer idPeriodePaie, boolean simulation,
                                                 Integer idUtilisateur) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PayrollBatchResult res = calculerPaiePeriode(idPeriodePaie, simulation, idUtilisateur, c);
                c.commit();
                return res;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Valide l'ensemble des bulletins calculés d'une période.
     */
    public PayrollBatchResult validerPaiePeriode(Integer idPeriodePaie, Integer idUtilisateur, Connection c) throws Exception {
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriodePaie, c);
        PayrollBatchResult batch = new PayrollBatchResult(idPeriodePaie, "");

        for (BulletinPaie b : bulletins) {
            if ("CALCULE".equalsIgnoreCase(b.getStatut())) {
                BulletinPaieComplet bpc = calculerBulletinEmploye(b.getIdEmploye(), idPeriodePaie, false, idUtilisateur, c);
                batch.ajouterBulletin(bpc);
            }
        }
        return batch;
    }

    /**
     * Clôture définitivement une période de paie et tous ses bulletins.
     * Active le verrou d'immutabilité PostgreSQL.
     */
    public void cloturerPaiePeriode(Integer idPeriodePaie, Integer idUtilisateur, Connection c) throws Exception {
        // 1. Clôture des bulletins
        String sqlUpdateBulletins = "UPDATE bulletin_paie SET " +
                "statut = 'CLOTURE', " +
                "date_cloture = CURRENT_TIMESTAMP, " +
                "cloture_par = ?, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_periode_paie = ? AND statut IN ('CALCULE', 'VALIDE', 'ROUVERT')";

        try (PreparedStatement ps = c.prepareStatement(sqlUpdateBulletins)) {
            if (idUtilisateur != null) {
                ps.setInt(1, idUtilisateur);
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setInt(2, idPeriodePaie);
            ps.executeUpdate();
        }

        // 2. Mise à jour du statut de la période_paie en CLOTUREE si applicable
        String sqlUpdatePeriode = "UPDATE periode_paie SET " +
                "id_statut_periode = COALESCE((SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'CLOTUREE'), id_statut_periode), " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_periode_paie = ?";

        try (PreparedStatement ps = c.prepareStatement(sqlUpdatePeriode)) {
            ps.setInt(1, idPeriodePaie);
            ps.executeUpdate();
        }
    }

    /**
     * Procédure de réouverture exceptionnelle d'urgence d'une période de paie clôturée (Contre-Audit).
     */
    public void rouvrirPaiePeriode(Integer idPeriodePaie, String motif, Integer idUtilisateur, Connection c) throws Exception {
        if (motif == null || motif.trim().length() < 5) {
            throw new IllegalArgumentException("Le motif de réouverture est obligatoire (min 5 caractères).");
        }
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriodePaie, c);
        for (BulletinPaie bp : bulletins) {
            bp.rouvrirBulletin(motif, idUtilisateur, c);
        }

        String sql = "UPDATE periode_paie SET " +
                "id_statut_periode = COALESCE((SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'OUVERTE'), id_statut_periode), " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_periode_paie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPeriodePaie);
            ps.executeUpdate();
        }
    }

    /**
     * Récupère un bulletin de paie complet avec toutes ses lignes.
     */
    public BulletinPaieComplet obtenirBulletinComplet(Integer idBulletin, Connection c) throws Exception {
        BulletinPaie bp = BulletinPaie.trouverParId(idBulletin, c);
        if (bp == null) return null;

        List<BulletinPaieLigne> lignes = BulletinPaieLigne.listerParBulletin(idBulletin, c);
        return new BulletinPaieComplet(bp, lignes);
    }

    // ==========================================================================
    // MÉTHODES PRIVÉES DE COLLECTE ET DE PERSISTANCE
    // ==========================================================================

    private PayrollCalculationInput preparerInput(Integer idEmploye, Integer idPeriodePaie,
                                                 boolean simulation, Connection c) throws Exception {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(idEmploye);
        in.setIdPeriodePaie(idPeriodePaie);
        in.setSimulation(simulation);

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

        // 2. Employé et Affectation carrière active
        String sqlEmploye = "SELECT e.*, " +
                "eac.salaire_base, eac.date_debut AS affectation_date_debut, " +
                "s.code_site, s.libelle AS site_nom, " +
                "f.libelle AS fonction_libelle, " +
                "d.libelle AS departement_nom, " +
                "cat.code AS categorie_code, " +
                "mcp.code AS mode_calcul_code, " +
                "fil.raison_sociale AS filiale_nom, " +
                "tri.code AS regime_code " +
                "FROM employe e " +
                "JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe " +
                "JOIN ref_statut_rh rsa ON eac.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF' " +
                "LEFT JOIN contrat_travail ct ON eac.id_contrat = ct.id_contrat " +
                "LEFT JOIN ref_site s ON eac.id_site = s.id_site " +
                "LEFT JOIN ref_fonction f ON eac.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_departement d ON eac.id_departement = d.id_departement " +
                "LEFT JOIN ref_categorie_professionnelle cat ON eac.id_categorie_professionnelle = cat.id_categorie_professionnelle " +
                "LEFT JOIN ref_mode_calcul_paie mcp ON eac.id_mode_calcul_paie = mcp.id_mode_calcul_paie " +
                "LEFT JOIN ref_filiale fil ON ct.id_filiale = fil.id_filiale " +
                "LEFT JOIN ref_type_intervenant tri ON ct.id_type_intervenant = tri.id_type_intervenant " +
                "WHERE e.id_employe = ?";

        try (PreparedStatement ps = c.prepareStatement(sqlEmploye)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    in.setMatricule(rs.getString("matricule"));
                    String nom = rs.getString("nom");
                    String prenom = rs.getString("prenom");
                    in.setNomPrenom(((nom != null ? nom : "") + " " + (prenom != null ? prenom : "")).trim());
                    in.setCinNumero(rs.getString("cin_numero"));
                    in.setCinDateDelivrance(rs.getDate("cin_date_delivrance"));
                    in.setCinLieuDelivrance(rs.getString("cin_lieu_delivrance"));
                    in.setNumeroCnaps(rs.getString("num_affiliation_cnaps"));
                    in.setIntitulePoste(rs.getString("fonction_libelle") != null ? rs.getString("fonction_libelle") : "Collaborateur");
                    in.setNomDepartement(rs.getString("departement_nom"));
                    in.setNomSite(rs.getString("site_nom") != null ? rs.getString("site_nom") : "Antananarivo - Siège");
                    in.setCodeSite(rs.getString("code_site") != null ? rs.getString("code_site") : "TNR");
                    in.setNomFiliale(rs.getString("filiale_nom") != null ? rs.getString("filiale_nom") : "GALAXAM LOGISTICS");
                    in.setRegimeIntervenant(rs.getString("regime_code") != null ? rs.getString("regime_code") : "PERMANENT");
                    in.setCategorieProfessionnelle(rs.getString("categorie_code") != null ? rs.getString("categorie_code") : "AGENT");
                    in.setModeCalculPaie(rs.getString("mode_calcul_code") != null ? rs.getString("mode_calcul_code") : "MENSUEL");
                    in.setSalaireBaseContractuel(rs.getBigDecimal("salaire_base") != null ? rs.getBigDecimal("salaire_base") : BigDecimal.ZERO);
                    in.setDateEmbauche(rs.getDate("date_declaration")); // Ou date affectation initiale
                } else {
                    throw new IllegalArgumentException("Dossier employé ou affectation active introuvable pour l'id: " + idEmploye);
                }
            }
        }

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

        // 4. Enfants à charge éligibles fiscalement (Règle des 21 ans à la date de fin de période)
        Date dateRefFiscale = in.getDateFinPeriode() != null ? in.getDateFinPeriode() : new Date(System.currentTimeMillis());
        int nbEnfants = EmployeEnfant.nbEnfantsEligibles(idEmploye, dateRefFiscale, c);
        in.setNbEnfantsEligibles(nbEnfants);

        // 5. Compteurs de pointage scellé (pointage_cumul_periode)
        String sqlCumul = "SELECT * FROM pointage_cumul_periode WHERE id_employe = ? AND id_periode_paie = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlCumul)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    in.setNbJoursOuvrables(rs.getBigDecimal("nb_jours_ouvrables"));
                    in.setNbJoursPresents(rs.getBigDecimal("nb_jours_presents"));
                    in.setNbJoursAbsence(rs.getBigDecimal("nb_jours_absence"));
                    in.setNbJoursConges(rs.getBigDecimal("nb_jours_conge"));
                    in.setTotalHeuresTravaillees(rs.getBigDecimal("total_heures_travaillees"));
                    in.setHeuresNormales(rs.getBigDecimal("total_heures_travaillees"));
                    in.setHeuresHs30(rs.getBigDecimal("heures_hs_30"));
                    in.setHeuresHs50(rs.getBigDecimal("heures_hs_50"));
                    in.setHeuresNuit(rs.getBigDecimal("heures_nuit_total"));
                    in.setIndemniteRepas(rs.getBigDecimal("montant_repas"));
                    in.setIndemniteTransport(rs.getBigDecimal("montant_deplacement"));

                    BigDecimal hAbs = rs.getBigDecimal("heures_absence_retenues");
                    if (hAbs != null && hAbs.compareTo(BigDecimal.ZERO) > 0) {
                        in.setNbHeuresAbsence(hAbs);
                    } else if (in.getNbJoursAbsence() != null && in.getNbJoursAbsence().compareTo(BigDecimal.ZERO) > 0) {
                        in.setNbHeuresAbsence(in.getNbJoursAbsence().multiply(new BigDecimal("8.0")));
                    }
                    in.setModeCalculAbsence("HEURES_REELLES");
                } else {
                    // Fallback si la période de pointage n'a pas encore été gelée (simulation par défaut)
                    in.setNbJoursOuvrables(new BigDecimal("22.0"));
                    in.setNbJoursPresents(new BigDecimal("22.0"));
                    in.setTotalHeuresTravaillees(new BigDecimal("173.33"));
                    in.setHeuresNormales(new BigDecimal("173.33"));
                    in.setModeCalculAbsence("HEURES_REELLES");
                }
            }
        }

        // 6. Paramètres légaux et fiscaux (LF 2026 par défaut)
        chargerParametresLegaux(in, c);

        // 7. Avances et acomptes du mois
        String sqlAvances = "SELECT * FROM paie_avance_acompte " +
                "WHERE id_employe = ? AND (id_periode_paie = ? OR id_periode_paie IS NULL) " +
                "AND statut IN ('EN_ATTENTE', 'APPROUVE') AND sens = 'RETENUE'";

        List<PaieAvanceAcompte> avances = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sqlAvances)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PaieAvanceAcompte a = new PaieAvanceAcompte();
                    a.setIdAvance(rs.getInt("id_avance"));
                    a.setIdEmploye(rs.getInt("id_employe"));
                    a.setTypeMouvement(rs.getString("type_mouvement"));
                    a.setSens(rs.getString("sens"));
                    a.setMontant(rs.getBigDecimal("montant"));
                    a.setMotif(rs.getString("motif"));
                    a.setStatut(rs.getString("statut"));
                    avances.add(a);
                }
            }
        }
        in.setAvancesAcomptes(avances);

        // 8. Échéance de prêt active
        String sqlPret = "SELECT e.* FROM paie_pret_echeance e " +
                "JOIN paie_pret p ON e.id_pret = p.id_pret " +
                "WHERE p.id_employe = ? AND p.statut = 'EN_COURS' " +
                "AND e.statut IN ('A_ECHOIR', 'PRELEVEE_PARTIELLEMENT') " +
                "ORDER BY e.numero_echeance ASC LIMIT 1";

        try (PreparedStatement ps = c.prepareStatement(sqlPret)) {
            ps.setInt(1, idEmploye);
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

        return in;
    }

    private void chargerParametresLegaux(PayrollCalculationInput in, Connection c) throws Exception {
        String sqlConfig = "SELECT * FROM paie_config_legale WHERE code_version = 'LF_2026'";
        int idConfig = 2;
        try (PreparedStatement ps = c.prepareStatement(sqlConfig);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                idConfig = rs.getInt("id_config_legale");
                in.setSmeNational(rs.getBigDecimal("sme_national"));
                in.setMinimumPerceptionIrsa(rs.getBigDecimal("minimum_perception_irsa"));
                in.setDeductionParEnfant(rs.getBigDecimal("deduction_charge_famille"));
            }
        }

        // Tranches IRSA
        String sqlTranches = "SELECT tranche_min, tranche_max, taux FROM bareme_irsa " +
                "WHERE id_config_legale = ? ORDER BY numero_tranche ASC";

        List<TrancheIrsa> tranches = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sqlTranches)) {
            ps.setInt(1, idConfig);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tranches.add(new TrancheIrsa(
                            rs.getBigDecimal("tranche_min"),
                            rs.getBigDecimal("tranche_max"),
                            rs.getDouble("taux")
                    ));
                }
            }
        }
        in.setTranchesIrsa(tranches);

        // Taux Santé selon site
        boolean estToamasina = "TMM".equalsIgnoreCase(in.getCodeSite());
        if (estToamasina) {
            in.setTauxSanteSalarial(0.015);
            in.setTauxSantePatronal(0.055);
        } else {
            in.setTauxSanteSalarial(0.01);
            in.setTauxSantePatronal(0.05);
        }

        // Plafonnement des cotisations santé à 8x SME (Contre-Audit Axe 5)
        String sqlPlafSante = "SELECT sc.est_plafonne_sante " +
                "FROM site_cotisation sc " +
                "JOIN ref_site s ON sc.id_site = s.id_site " +
                "WHERE s.code_site = ? AND sc.est_actif = true LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sqlPlafSante)) {
            ps.setString(1, in.getCodeSite());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    in.setEstPlafonneSante(rs.getBoolean("est_plafonne_sante"));
                } else {
                    in.setEstPlafonneSante(true);
                }
            }
        }
    }

    private void sauvegarderBulletinEtLignes(BulletinPaieComplet complet, Connection c) throws Exception {
        BulletinPaie bp = complet.getBulletin();

        // 1. Vérifier si un bulletin existe déjà pour cette période et cet employé
        BulletinPaie existant = BulletinPaie.trouverParPeriodeEtEmploye(bp.getIdPeriodePaie(), bp.getIdEmploye(), c);

        if (existant != null) {
            if ("CLOTURE".equalsIgnoreCase(existant.getStatut())) {
                throw new IllegalStateException("Le bulletin " + existant.getReferenceBulletin() + " est clôturé et strictement immuable.");
            }
            // Réutilisation de l'id existant
            bp.setIdBulletin(existant.getIdBulletin());
            bp.setReferenceBulletin(existant.getReferenceBulletin());

            // Suppression des anciennes lignes
            BulletinPaieLigne.supprimerParBulletin(existant.getIdBulletin(), c);

            // Mise à jour de l'en-tête
            bp.update(c);
        } else {
            // Insertion d'un nouveau bulletin
            bp.save(c);
        }

        // 2. Insertion des lignes ventilées
        chargerCacheRubriques(c);

        for (BulletinPaieLigne ligne : complet.getLignes()) {
            ligne.setIdBulletin(bp.getIdBulletin());
            Integer idRub = cacheRubriques.get(ligne.getCodeRubrique());
            ligne.setIdRubrique(idRub);
            ligne.save(c);
        }
    }

    private void imputerAvancesEtPrets(BulletinPaieComplet complet, Integer idPeriodePaie, Connection c) throws Exception {
        BulletinPaie bp = complet.getBulletin();

        // 1. Imputation des avances et acomptes
        if (complet.getIdsAvancesImputees() != null && !complet.getIdsAvancesImputees().isEmpty()) {
            String sqlAvance = "UPDATE paie_avance_acompte SET " +
                    "statut = 'IMPUTE_PAIE', " +
                    "id_bulletin = ?, " +
                    "id_periode_paie = ?, " +
                    "date_paiement = CURRENT_DATE, " +
                    "updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id_avance = ?";

            try (PreparedStatement ps = c.prepareStatement(sqlAvance)) {
                for (Integer idAv : complet.getIdsAvancesImputees()) {
                    ps.setInt(1, bp.getIdBulletin());
                    ps.setInt(2, idPeriodePaie);
                    ps.setInt(3, idAv);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        // 2. Prélèvement de l'échéance de prêt
        ImpactPret impact = complet.getImpactPret();
        if (impact != null && impact.montantPreleve() != null && impact.montantPreleve().compareTo(BigDecimal.ZERO) > 0) {
            PaiePretEcheance ech = PaiePretEcheance.trouverParId(impact.idEcheance(), c);
            if (ech != null) {
                ech.preleverEnPaie(impact.montantPreleve(), bp.getIdBulletin(), idPeriodePaie, c);
            }
        }
    }

    private synchronized void chargerCacheRubriques(Connection c) throws SQLException {
        if (!cacheRubriques.isEmpty()) return;
        String sql = "SELECT id_rubrique, code FROM paie_rubrique";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cacheRubriques.put(rs.getString("code"), rs.getInt("id_rubrique"));
            }
        }
    }
}
