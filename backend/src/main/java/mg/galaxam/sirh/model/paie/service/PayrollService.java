package mg.galaxam.sirh.model.paie.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import mg.galaxam.sirh.model.paie.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.model.pointage.calculation.QualifiedTimeBucket;
import mg.galaxam.sirh.model.pointage.calculation.OvertimeTier;
import mg.galaxam.sirh.model.pointage.calculation.DaySpecialType;
import mg.galaxam.sirh.model.pointage.calculation.NightWorkType;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.model.paieconfig.PaieConfigLegale;
import mg.galaxam.sirh.model.paie.engine.IrsaCalculator.Tranche;
import mg.galaxam.sirh.model.paie.engine.TaxConfigurationResolver;
import mg.galaxam.sirh.common.transaction.TransactionRunner;
import mg.galaxam.sirh.api.dto.paie.PayrollPopulationMember;

/**
 * Service d'orchestration de paie.
 * Collecte les données sources amont (RH, pointage, congés, enfants, prêts, acomptes),
 * exécute le moteur déterministe PayrollExecutionEngine, et persiste les résultats de manière immuable.
 */
@Service
public class PayrollService {

    private static final Logger log = LoggerFactory.getLogger(PayrollService.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PayrollExecutionEngine payrollExecutionEngine;

    @Autowired
    private PayrollLegalContextResolver payrollLegalContextResolver;

    @Autowired
    private PayrollPopulationResolver payrollPopulationResolver;

    @Autowired
    private TransactionRunner transactionRunner;

    // Cache local des id_rubrique par code (thread-safe)
    private final Map<String, Integer> cacheRubriques = new ConcurrentHashMap<>();

    /**
     * Invalide le cache local des rubriques pour forcer un rechargement depuis la base.
     */
    public void invalidateRubriqueCache() {
        cacheRubriques.clear();
    }

    private void lierBulletinAuSnapshot(Integer bulletinId, PayrollCalculationInput input, Connection c)
            throws SQLException {
        if (bulletinId == null || input.getAttendanceInput() == null) return;
        var attendance = input.getAttendanceInput();
        String sql = "UPDATE bulletin_paie SET id_snapshot_pointage = ?, version_snapshot_pointage = ? " +
                "WHERE id_bulletin = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, attendance.snapshotId());
            ps.setInt(2, attendance.snapshotVersion());
            ps.setInt(3, bulletinId);
            if (ps.executeUpdate() != 1) throw new SQLException("Impossible de rattacher le snapshot au bulletin.");
        }
        marquerSnapshotConsomme(bulletinId, c);
    }

    private void verifierBulletinNonObsolete(Integer bulletinId, Connection c) throws Exception {
        String sql = "SELECT b.id_snapshot_pointage, b.version_snapshot_pointage, b.id_config_legale, " +
                "b.id_employe, b.id_periode_paie, p.date_fin FROM bulletin_paie b " +
                "JOIN periode_paie p ON p.id_periode_paie = b.id_periode_paie WHERE b.id_bulletin = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, bulletinId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Bulletin introuvable pendant le contrôle d'obsolescence.");
                Integer snapshotId = nullableInt(rs, "id_snapshot_pointage");
                Integer snapshotVersion = nullableInt(rs, "version_snapshot_pointage");
                if (snapshotId != null) {
                    String latest = "SELECT id_cumul, version_snapshot FROM pointage_cumul_periode " +
                            "WHERE id_employe = ? AND id_periode_paie = ? AND statut_snapshot IN ('GELE','CONSOMME') " +
                            "ORDER BY version_snapshot DESC LIMIT 1";
                    try (PreparedStatement current = c.prepareStatement(latest)) {
                        current.setInt(1, rs.getInt("id_employe")); current.setInt(2, rs.getInt("id_periode_paie"));
                        try (ResultSet now = current.executeQuery()) {
                            if (!now.next() || now.getInt("id_cumul") != snapshotId
                                    || now.getInt("version_snapshot") != snapshotVersion) {
                                throw new IllegalStateException("PAYROLL_RESULT_OBSOLETE: le snapshot Pointage a changé; recalcul explicite requis.");
                            }
                        }
                    }
                }
                Integer configId = nullableInt(rs, "id_config_legale");
                if (configId != null) {
                    PaieConfigLegale currentConfig = PaieConfigLegale.findActiveAt(c, rs.getDate("date_fin").toLocalDate());
                    if (currentConfig == null || !configId.equals(currentConfig.getIdConfigLegale())) {
                        throw new IllegalStateException("PAYROLL_RESULT_OBSOLETE: la configuration légale a changé; recalcul explicite requis.");
                    }
                }
            }
        }
    }

    private void marquerSnapshotConsomme(Integer bulletinId, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE pointage_cumul_periode SET statut_snapshot = 'CONSOMME' " +
                "WHERE id_cumul = (SELECT id_snapshot_pointage FROM bulletin_paie WHERE id_bulletin = ?)")) {
            ps.setInt(1, bulletinId); ps.executeUpdate();
        }
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

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

        // Les métadonnées techniques et le workflow sont appliqués hors du moteur pur.
        bp.setStatut("CALCULE");
        bp.setDateCalcul(new Timestamp(System.currentTimeMillis()));

        if (idUtilisateur != null) {
            bp.setCalculePar(idUtilisateur);
        }

        // La simulation est sans effet de bord. Le même résultat pourra être persisté
        // explicitement lors d'un calcul réel.
        if (!simulation) {
            sauvegarderBulletinEtLignes(complet, c);
            lierBulletinAuSnapshot(bp.getIdBulletin(), input, c);
            imputerAvancesEtPrets(complet, idPeriodePaie, c);
        }

        return complet;
    }

    /**
     * Surcharge sans Connection pour appel de service autonome (gère sa propre transaction).
     */
    public BulletinPaieComplet calculerBulletinEmploye(Integer idEmploye, Integer idPeriodePaie,
                                                      boolean simulation, Integer idUtilisateur) throws Exception {
        return transactionRunner.required(c ->
                calculerBulletinEmploye(idEmploye, idPeriodePaie, simulation, idUtilisateur, c));
    }

    /**
     * Calcule la paie de tous les salariés actifs pour une période donnée.
     */
    public PayrollBatchResult calculerPaiePeriode(Integer idPeriodePaie, boolean simulation,
                                                 Integer idUtilisateur, Connection c) throws Exception {
        PayrollPopulationResolver.PayrollPopulation population = payrollPopulationResolver.resolve(idPeriodePaie, c);
        PayrollBatchResult batch = new PayrollBatchResult(idPeriodePaie, population.periodCode());
        population.members().stream()
                .filter(member -> "BLOCKED".equals(member.decision()))
                .forEach(member -> batch.ajouterBloque(member.employeeId(), member.reasonCode()));
        List<Integer> idsEmployes = population.includedEmployeeIds();

        for (Integer idEmp : idsEmployes) {
            Savepoint employeeSavepoint = c.getAutoCommit() ? null : c.setSavepoint("payroll_employee_" + idEmp);
            try {
                BulletinPaieComplet bpc = calculerBulletinEmploye(idEmp, idPeriodePaie, simulation, idUtilisateur, c);
                batch.ajouterBulletin(bpc);
                if (employeeSavepoint != null) c.releaseSavepoint(employeeSavepoint);
            } catch (Exception employeeFailure) {
                if (employeeSavepoint != null) c.rollback(employeeSavepoint);
                batch.ajouterBloque(idEmp, employeeFailure.getMessage());
            }
        }

        // Si validation réelle : génération automatique des bordereaux d'honoraires pour les consultants
        if (!simulation) {
            genererHonorairesPeriode(idPeriodePaie, idUtilisateur, c);

            // Si la période était en PAIE_VALIDEE, un recalcul la replace en VALIDEE (bulletins en attente d'approbation)
            String sqlReset = "UPDATE periode_paie SET " +
                    "id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'VALIDEE'), " +
                    "updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id_periode_paie = ? AND id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'PAIE_VALIDEE')";
            try (PreparedStatement psReset = c.prepareStatement(sqlReset)) {
                psReset.setInt(1, idPeriodePaie);
                psReset.executeUpdate();
            }
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
                    if (dateFin == null) throw new IllegalStateException("La date de fin de période est obligatoire.");
                    hono.setDatePrestation(dateFin);
                    hono.setDatePaiement(dateFin);
                    hono.setMontantBrut(brut != null ? brut : BigDecimal.ZERO);
                    BigDecimal tauxIsi = chargerTauxIsiApplicable(c, dateFin.toLocalDate());
                    hono.setTauxIsi(tauxIsi);
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

    private BigDecimal chargerTauxIsiApplicable(Connection c, java.time.LocalDate date) throws Exception {
        return TaxConfigurationResolver.resolveIsiRate(date, c);
    }

    public List<PaieHonorairePrestation> genererHonorairesPeriode(Integer idPeriodePaie, Integer idUtilisateur) throws Exception {
        return transactionRunner.required(c -> genererHonorairesPeriode(idPeriodePaie, idUtilisateur, c));
    }

    /**
     * Surcharge sans Connection pour calcul collectif autonome.
     */
    public PayrollBatchResult calculerPaiePeriode(Integer idPeriodePaie, boolean simulation,
                                                 Integer idUtilisateur) throws Exception {
        return transactionRunner.required(c -> calculerPaiePeriode(idPeriodePaie, simulation, idUtilisateur, c));
    }

    /**
     * Valide l'ensemble des bulletins calculés d'une période.
     */
    public PayrollBatchResult validerPaiePeriode(Integer idPeriodePaie, Integer idUtilisateur, Connection c) throws Exception {
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriodePaie, c);
        PayrollBatchResult batch = new PayrollBatchResult(idPeriodePaie, "");

        for (BulletinPaie b : bulletins) {
            if ("CALCULE".equalsIgnoreCase(b.getStatut())) {
                verifierBulletinNonObsolete(b.getIdBulletin(), c);
                String sql = "UPDATE bulletin_paie SET statut = 'VALIDE', valide_par = ?, " +
                        "date_validation = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE id_bulletin = ? AND statut = 'CALCULE'";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    if (idUtilisateur == null) ps.setNull(1, java.sql.Types.INTEGER);
                    else ps.setInt(1, idUtilisateur);
                    ps.setInt(2, b.getIdBulletin());
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException("Le bulletin " + b.getIdBulletin() + " a changé pendant sa validation.");
                    }
                }
                marquerSnapshotConsomme(b.getIdBulletin(), c);
                batch.ajouterBulletin(obtenirBulletinComplet(b.getIdBulletin(), c));
            }
        }

        // Transition du statut de la période vers PAIE_VALIDEE (Paie Validée & Approuvée)
        String sqlPeriode = "UPDATE periode_paie SET " +
                "id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'PAIE_VALIDEE'), " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_periode_paie = ? AND id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'VALIDEE')";
        try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
            psP.setInt(1, idPeriodePaie);
            psP.executeUpdate();
        }

        return batch;
    }

    public PayrollBatchResult validerPaiePeriode(Integer idPeriodePaie, Integer idUtilisateur) {
        return transactionRunner.required(c -> validerPaiePeriode(idPeriodePaie, idUtilisateur, c));
    }

    /**
     * Clôture définitivement une période de paie et tous ses bulletins.
     * Active le verrou d'immutabilité PostgreSQL.
     */
    public void cloturerDonneesPaie(Integer idPeriodePaie, Integer idUtilisateur, Connection c) throws Exception {
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

        // 2. Alimentation automatique du grand-livre de valorisation des congés (OHATRA KAJY DROITS CONGES)
        mettreAJourJournalConge(idPeriodePaie, c);
    }

    /**
     * Procédure de réouverture exceptionnelle d'urgence d'une période de paie clôturée (Contre-Audit).
     */
    public void rouvrirDonneesPaie(Integer idPeriodePaie, String motif, Integer idUtilisateur, Connection c) throws Exception {
        if (motif == null || motif.trim().length() < 5) {
            throw new IllegalArgumentException("Le motif de réouverture est obligatoire (min 5 caractères).");
        }
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriodePaie, c);
        for (BulletinPaie bp : bulletins) {
            if ("STC".equalsIgnoreCase(bp.getTypeBulletin())) {
                continue; // Un Solde de Tout Compte est libératoire et sanctuarisé : il n'est pas rouvert avec la paie mensuelle
            }
            bp.rouvrirBulletin(motif, idUtilisateur, c);
        }

        // 1. Dé-imputer les avances et mouvements variables de la période pour permettre le re-calcul
        String sqlUnfreezeAvances = "UPDATE paie_avance_acompte SET statut = 'APPROUVE', id_bulletin = NULL " +
                "WHERE id_periode_paie = ? AND statut = 'IMPUTE_PAIE'";
        try (PreparedStatement ps = c.prepareStatement(sqlUnfreezeAvances)) {
            ps.setInt(1, idPeriodePaie);
            ps.executeUpdate();
        }

        // 2. Dé-imputer les échéances de prêts prélevées sur cette période
        String sqlUnfreezePrets = "UPDATE paie_pret_echeance SET statut = 'A_ECHOIR', montant_preleve = 0, " +
                "montant_restant_echeance = montant_echeance, id_bulletin = NULL, id_periode_paie = NULL " +
                "WHERE id_periode_paie = ? AND statut IN ('PRELEVEE_EN_PAIE', 'PRELEVEE_PARTIELLEMENT')";
        try (PreparedStatement ps = c.prepareStatement(sqlUnfreezePrets)) {
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

    /**
     * Assemble de manière idempotente le PayrollCalculationInput à partir des snapshots et référentiels BDD.
     * Cette méthode constitue une API publique d'inspection pré-vol (pre-flight checks), permettant
     * d'auditer les données assemblées (pointage qualifié, carrière, fiscalité) sans exécuter
     * le moteur de valorisation ni créer d'effets de bord transactionnels.
     */
    public PayrollCalculationInput preparerInput(Integer idEmploye, Integer idPeriodePaie,
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

        // 2. Employé et affectation applicables à la fin de la période calculée.
        Date dateReferenceEmploi = in.getDateFinPeriode();
        if (dateReferenceEmploi == null) {
            throw new IllegalStateException("La date de fin de période est obligatoire pour résoudre l'emploi.");
        }
        String sqlEmploye = "SELECT e.*, " +
                "eac.salaire_base, eac.date_debut AS affectation_date_debut, " +
                "s.code_site, s.libelle AS site_nom, " +
                "f.libelle AS fonction_libelle, " +
                "d.libelle AS departement_nom, " +
                "cat.code AS categorie_code, " +
                "mcp.code AS mode_calcul_code, " +
                "fil.raison_sociale AS filiale_nom, " +
                "tri.code AS regime_code, " +
                "tri.soumis_cnaps, tri.soumis_sante, tri.soumis_irsa, tri.soumis_isi_5, " +
                "COALESCE(c_emb.date_embauche_initiale, ct.date_debut, e.date_declaration) AS date_embauche_resolue " +
                "FROM employe e " +
                "LEFT JOIN (SELECT id_employe, MIN(date_debut) AS date_embauche_initiale FROM contrat_travail GROUP BY id_employe) c_emb ON e.id_employe = c_emb.id_employe " +
                "JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe " +
                "JOIN ref_statut_rh rsa ON eac.id_statut_rh = rsa.id_statut_rh " +
                "LEFT JOIN contrat_travail ct ON eac.id_contrat = ct.id_contrat " +
                "LEFT JOIN ref_site s ON eac.id_site = s.id_site " +
                "LEFT JOIN ref_fonction f ON eac.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_departement d ON eac.id_departement = d.id_departement " +
                "LEFT JOIN ref_categorie_professionnelle cat ON eac.id_categorie_professionnelle = cat.id_categorie_professionnelle " +
                "LEFT JOIN ref_mode_calcul_paie mcp ON eac.id_mode_calcul_paie = mcp.id_mode_calcul_paie " +
                "LEFT JOIN ref_filiale fil ON ct.id_filiale = fil.id_filiale " +
                "LEFT JOIN ref_type_intervenant tri ON ct.id_type_intervenant = tri.id_type_intervenant " +
                "WHERE e.id_employe = ? " +
                "AND eac.date_debut <= ? AND (eac.date_fin IS NULL OR eac.date_fin >= ?) " +
                "AND (ct.id_contrat IS NULL OR (ct.date_debut <= ? AND (ct.date_fin IS NULL OR ct.date_fin >= ?))) " +
                "ORDER BY eac.date_debut DESC";

        try (PreparedStatement ps = c.prepareStatement(sqlEmploye)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, dateReferenceEmploi);
            ps.setDate(3, dateReferenceEmploi);
            ps.setDate(4, dateReferenceEmploi);
            ps.setDate(5, dateReferenceEmploi);
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
                    String regimeCode = rs.getString("regime_code");
                    if (regimeCode == null || regimeCode.isBlank()) {
                        throw PayrollConfigurationGuard.missing("TYPE_INTERVENANT", idEmploye, null);
                    }
                    in.setRegimeIntervenant(regimeCode);
                    in.setSoumisCnaps(PayrollConfigurationGuard.requireFlag(rs, "soumis_cnaps", "TYPE_INTERVENANT", idEmploye));
                    in.setSoumisSante(PayrollConfigurationGuard.requireFlag(rs, "soumis_sante", "TYPE_INTERVENANT", idEmploye));
                    in.setSoumisIrsa(PayrollConfigurationGuard.requireFlag(rs, "soumis_irsa", "TYPE_INTERVENANT", idEmploye));
                    in.setSoumisIsi5(PayrollConfigurationGuard.requireFlag(rs, "soumis_isi_5", "TYPE_INTERVENANT", idEmploye));
                    in.setCategorieProfessionnelle(rs.getString("categorie_code") != null ? rs.getString("categorie_code") : "AGENT");
                    in.setModeCalculPaie(rs.getString("mode_calcul_code") != null ? rs.getString("mode_calcul_code") : "MENSUEL");
                    in.setSalaireBaseContractuel(rs.getBigDecimal("salaire_base") != null ? rs.getBigDecimal("salaire_base") : BigDecimal.ZERO);
                    in.setDateEmbauche(rs.getDate("date_embauche_resolue") != null ? rs.getDate("date_embauche_resolue") : rs.getDate("date_declaration"));
                    if (rs.next()) {
                        throw new IllegalStateException("Plusieurs affectations concurrentes sont applicables à l'employé "
                                + idEmploye + " au " + dateReferenceEmploi + ".");
                    }
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
        String sqlCumul = "SELECT * FROM pointage_cumul_periode WHERE id_employe = ? AND id_periode_paie = ? " +
                "AND statut_snapshot IN ('GELE', 'CONSOMME') ORDER BY version_snapshot DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sqlCumul)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal hAbs = rs.getBigDecimal("heures_absence_retenues");
                    BigDecimal absenceDays = rs.getBigDecimal("nb_jours_absence");
                    if ((hAbs == null || hAbs.signum() == 0) && absenceDays != null) {
                        hAbs = absenceDays.multiply(new BigDecimal("8.0"));
                    }
                    Timestamp frozenTimestamp = rs.getTimestamp("gele_at");
                    Instant frozenAt = frozenTimestamp == null ? Instant.EPOCH : frozenTimestamp.toInstant();
                    BigDecimal hDim = rs.getBigDecimal("heures_dimanche_total");
                    BigDecimal hFer = rs.getBigDecimal("heures_ferie_total");
                    List<QualifiedTimeBucket> buckets = loadSnapshotBuckets(rs.getInt("id_cumul"), c);
                    in.setAttendanceInput(new PayrollAttendanceInput(
                            rs.getInt("id_cumul"), rs.getInt("version_snapshot"), idEmploye, idPeriodePaie, frozenAt,
                            PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("total_heures_travaillees")),
                            PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_hs_30")),
                            PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_hs_50")),
                            PayrollAttendanceInput.hoursToMinutes(rs.getBigDecimal("heures_nuit_total")),
                            PayrollAttendanceInput.hoursToMinutes(hDim),
                            PayrollAttendanceInput.hoursToMinutes(hFer),
                            0L,
                            PayrollAttendanceInput.hoursToMinutes(hAbs), rs.getInt("total_minutes_retard"),
                            rs.getBigDecimal("nb_jours_ouvrables"), rs.getBigDecimal("nb_jours_presents"),
                            absenceDays, rs.getBigDecimal("nb_jours_conge"), rs.getBigDecimal("nb_jours_repas"),
                            rs.getBigDecimal("montant_repas"), rs.getBigDecimal("nb_jours_deplacement"),
                            rs.getBigDecimal("montant_deplacement"),
                            buckets));
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

        // 5.1 Taux journalier de congé dynamique (conge_journal_droit - OHATRA KAJY DROITS CONGES mutualisé)
        BigDecimal tauxConge = StcService.getTauxJournalierDernier(idEmploye, in.getSalaireBaseContractuel(), c);
        if (tauxConge.compareTo(BigDecimal.ZERO) > 0) {
            in.setTauxJournalierConge(tauxConge);
        }

        // 6. Paramètres légaux et fiscaux (LF 2026 par défaut)
        payrollLegalContextResolver.resolve(in, c);

        // 7. Variables de paie, Primes, Extras et Retenues (paie_avance_acompte & ref_type_mouvement_paie)
        String sqlMouvements = "SELECT paa.*, " +
                "COALESCE(rtmp.code, paa.type_mouvement) AS code_effectif, " +
                "COALESCE(rtmp.sens, paa.sens) AS sens_effectif, " +
                "rtmp.libelle AS libelle_type, " +
                "COALESCE(rtmp.categorie, 'PRIME') AS categorie_type, " +
                "pr.soumis_irsa AS est_imposable, " +
                "pr.soumis_cnaps AS est_soumis_cotisation, " +
                "pr.code AS rubrique_code " +
                "FROM paie_avance_acompte paa " +
                "LEFT JOIN ref_type_mouvement_paie rtmp ON paa.id_type_mouvement = rtmp.id_type_mouvement " +
                "LEFT JOIN paie_rubrique pr ON rtmp.id_rubrique = pr.id_rubrique " +
                "WHERE paa.id_employe = ? AND (paa.id_periode_paie = ? OR paa.id_periode_paie IS NULL) " +
                "AND paa.statut IN ('EN_ATTENTE', 'APPROUVE')";

        List<PaieAvanceAcompte> avances = new ArrayList<>();
        List<Integer> idsGains = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(sqlMouvements)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sens = rs.getString("sens_effectif");
                    String codeType = rs.getString("code_effectif");
                    BigDecimal montant = rs.getBigDecimal("montant");
                    String motif = rs.getString("motif");
                    int idAvance = rs.getInt("id_avance");

                    if (montant == null || montant.compareTo(BigDecimal.ZERO) <= 0) continue;

                    if ("GAIN".equalsIgnoreCase(sens)) {
                        idsGains.add(idAvance);
                        if ("PRIME_PERFORMANCE".equalsIgnoreCase(codeType)) {
                            in.setPrimePerformanceBase(in.getPrimePerformanceBase().add(montant));
                        } else if ("PRIME_ASSIDUITE".equalsIgnoreCase(codeType)) {
                            in.setPrimeAssiduiteForfait(in.getPrimeAssiduiteForfait().add(montant));
                        } else if ("PRIME_EXCEPTIONNELLE".equalsIgnoreCase(codeType)) {
                            in.setPrimeExceptionnelle(in.getPrimeExceptionnelle().add(montant));
                            if (motif != null && !motif.isBlank()) {
                                in.setMotifPrimeExceptionnelle(motif);
                            }
                        } else if ("AUTIMP".equalsIgnoreCase(codeType)) {
                            in.setAutimp(in.getAutimp().add(montant));
                        } else if ("EXTRA_DEPLACEMENT".equalsIgnoreCase(codeType)) {
                            in.setExtraDeplacement(in.getExtraDeplacement().add(montant));
                        } else if ("NOTE_DE_FRAIS".equalsIgnoreCase(codeType) || "REMBOURSEMENT_NOTE_FRAIS".equalsIgnoreCase(codeType)) {
                            in.setRemboursementNotesFrais(in.getRemboursementNotesFrais().add(montant));
                        } else if ("REGULARISATION_CONGE".equalsIgnoreCase(codeType) || "REGULARISATION_PAIE".equalsIgnoreCase(codeType)) {
                            in.setRegularisationPaie(in.getRegularisationPaie().add(montant));
                            if (motif != null && !motif.isBlank()) {
                                in.setMotifRegularisationPaie(motif);
                            }
                        } else {
                            // Variable / Prime dynamique configurée en base de données
                            String libelleType = rs.getString("libelle_type");
                            String catType = rs.getString("categorie_type");
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
                            in.ajouterGainVariable(gv);
                        }
                    } else {
                        // RETENUE (ACOMPTE_15, AVANCE_SPECIALE, CALL_PERSO, RETENUE_DIVERSE)
                        PaieAvanceAcompte a = new PaieAvanceAcompte();
                        a.setIdAvance(idAvance);
                        a.setIdEmploye(rs.getInt("id_employe"));
                        a.setTypeMouvement(codeType);
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

    private void sauvegarderBulletinEtLignes(BulletinPaieComplet complet, Connection c) throws Exception {
        BulletinPaie bp = complet.getBulletin();

        // 1. Vérifier si un bulletin de même type existe déjà pour cette période et cet employé
        String typeBulletin = bp.getTypeBulletin() != null ? bp.getTypeBulletin() : "MENSUEL";
        if (bp.getIdTypeBulletin() == null && bp.getTypeBulletin() != null) {
            String sqlTb = "SELECT id_type_bulletin FROM ref_type_bulletin WHERE code = ?";
            try (PreparedStatement psTb = c.prepareStatement(sqlTb)) {
                psTb.setString(1, bp.getTypeBulletin());
                try (ResultSet rsTb = psTb.executeQuery()) {
                    if (rsTb.next()) bp.setIdTypeBulletin(rsTb.getInt(1));
                }
            }
        }
        if (bp.getIdModePaiement() == null && bp.getModePaiement() != null) {
            String sqlMp = "SELECT id_mode_paiement FROM ref_mode_paiement WHERE code = ?";
            try (PreparedStatement psMp = c.prepareStatement(sqlMp)) {
                psMp.setString(1, bp.getModePaiement());
                try (ResultSet rsMp = psMp.executeQuery()) {
                    if (rsMp.next()) bp.setIdModePaiement(rsMp.getInt(1));
                }
            }
        }
        BulletinPaie existant = BulletinPaie.trouverParPeriodeEtEmployeEtType(bp.getIdPeriodePaie(), bp.getIdEmploye(), typeBulletin, c);

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
            if (idRub == null && ligne.getCodeRubrique() != null) {
                String sqlFind = "SELECT id_rubrique FROM paie_rubrique WHERE code = ?";
                try (PreparedStatement psF = c.prepareStatement(sqlFind)) {
                    psF.setString(1, ligne.getCodeRubrique());
                    try (ResultSet rsF = psF.executeQuery()) {
                        if (rsF.next()) {
                            idRub = rsF.getInt(1);
                            cacheRubriques.put(ligne.getCodeRubrique(), idRub);
                        }
                    }
                }
            }
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

    private void chargerCacheRubriques(Connection c) throws SQLException {
        if (!cacheRubriques.isEmpty()) return;
        String sql = "SELECT id_rubrique, code FROM paie_rubrique";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            Map<String, Integer> temp = new HashMap<>();
            while (rs.next()) {
                temp.put(rs.getString("code"), rs.getInt("id_rubrique"));
            }
            cacheRubriques.putAll(temp);
        }
    }

    /**
     * Alimente le grand-livre de valorisation des droits à congés (OHATRA KAJY DROITS CONGES)
     * lors de la clôture de la paie.
     */
    private void mettreAJourJournalConge(Integer idPeriodePaie, Connection c) throws Exception {
        String sqlPeriode = "SELECT EXTRACT(YEAR FROM date_fin)::INT AS annee, EXTRACT(MONTH FROM date_fin)::INT AS mois " +
                "FROM periode_paie WHERE id_periode_paie = ?";
        int annee = 2026;
        int mois = 1;
        try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
            psP.setInt(1, idPeriodePaie);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    annee = rsP.getInt("annee");
                    mois = rsP.getInt("mois");
                }
            }
        }

        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriodePaie, c);
        for (BulletinPaie bp : bulletins) {
            // Ignorer les bulletins STC : le Solde de Tout Compte valorise et liquide les congés
            // dans le reçu de départ, il ne doit pas générer d'acquisition indue ni écraser le journal
            if ("STC".equalsIgnoreCase(bp.getTypeBulletin())) {
                continue;
            }

            // Uniquement les salariés soumis aux congés payés légaux
            if ("CONSULTANT".equalsIgnoreCase(bp.getRegimeIntervenant()) || "ISI_5".equalsIgnoreCase(bp.getRegimeIntervenant())) {
                continue;
            }

            int idEmp = bp.getIdEmploye();
            BigDecimal base = bp.getSalaireBase() != null ? bp.getSalaireBase() : BigDecimal.ZERO;
            BigDecimal jAbs = bp.getJoursAbsence() != null ? bp.getJoursAbsence() : BigDecimal.ZERO;
            BigDecimal jCge = bp.getJoursConges() != null ? bp.getJoursConges() : BigDecimal.ZERO;

            // Formule jours travaillés/assimilés : 30 - max(0, absence - conge)
            BigDecimal absInjustifiees = jAbs.subtract(jCge).max(BigDecimal.ZERO);
            BigDecimal formule = new BigDecimal("30.0").subtract(absInjustifiees).max(BigDecimal.ZERO);

            // Droits acquis : 2.5 * formule / 30
            BigDecimal droitMois = new BigDecimal("2.5").multiply(formule)
                    .divide(new BigDecimal("30.0"), 4, RoundingMode.HALF_UP);

            // Récupérer les lignes du bulletin pour net_base et hs
            List<BulletinPaieLigne> lignes = BulletinPaieLigne.listerParBulletin(bp.getIdBulletin(), c);
            BigDecimal netBase = base;
            BigDecimal mntHs = BigDecimal.ZERO;
            for (BulletinPaieLigne l : lignes) {
                String codeRub = l.getCodeRubrique() != null ? l.getCodeRubrique().toUpperCase() : "";
                if ("RETENUE_ABSENCE".equals(codeRub) || "RETENUE_CONGE".equals(codeRub) || "DEDUCTION_CONGE_BASE".equals(codeRub)) {
                    if (l.getMontantSalarial() != null) netBase = netBase.subtract(l.getMontantSalarial());
                } else if ("INDEMNITE_CONGE".equals(codeRub) || "INDEMNITE_CONGE_PAYE".equals(codeRub)) {
                    if (l.getMontantSalarial() != null) netBase = netBase.add(l.getMontantSalarial());
                } else if ("HEURES_SUP".equalsIgnoreCase(l.getCategorie())) {
                    if (l.getMontantSalarial() != null) mntHs = mntHs.add(l.getMontantSalarial());
                }
            }

            BigDecimal baseTotale = netBase.add(mntHs).max(BigDecimal.ZERO);
            BigDecimal valMois = baseTotale.multiply(droitMois).divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP);

            // Cumul précédent
            BigDecimal cumulJoursPrec = BigDecimal.ZERO;
            BigDecimal cumulValPrec = BigDecimal.ZERO;
            String sqlPrec = "SELECT cumul_jours_droit, cumul_valorisation FROM conge_journal_droit " +
                    "WHERE id_employe = ? AND (annee < ? OR (annee = ? AND mois < ?)) " +
                    "ORDER BY annee DESC, mois DESC LIMIT 1";
            try (PreparedStatement psPrec = c.prepareStatement(sqlPrec)) {
                psPrec.setInt(1, idEmp);
                psPrec.setInt(2, annee);
                psPrec.setInt(3, annee);
                psPrec.setInt(4, mois);
                try (ResultSet rsPrec = psPrec.executeQuery()) {
                    if (rsPrec.next()) {
                        cumulJoursPrec = rsPrec.getBigDecimal("cumul_jours_droit");
                        cumulValPrec = rsPrec.getBigDecimal("cumul_valorisation");
                    }
                }
            }

            BigDecimal cumulJours = (cumulJoursPrec != null ? cumulJoursPrec : BigDecimal.ZERO).add(droitMois);
            BigDecimal cumulVal = (cumulValPrec != null ? cumulValPrec : BigDecimal.ZERO).add(valMois);
            BigDecimal tauxJournalier = cumulJours.compareTo(BigDecimal.ZERO) > 0
                    ? cumulVal.divide(cumulJours, 2, RoundingMode.HALF_UP)
                    : base.divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP);

            String sqlUpsert = "INSERT INTO conge_journal_droit (" +
                    "id_employe, id_periode_paie, annee, mois, salaire_base, jours_travailles_formule, " +
                    "jours_droit_acquis, base_valorisation, valorisation_mensuelle, cumul_jours_droit, " +
                    "cumul_valorisation, taux_journalier_conge" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (id_employe, annee, mois) DO UPDATE SET " +
                    "id_periode_paie = EXCLUDED.id_periode_paie, " +
                    "salaire_base = EXCLUDED.salaire_base, " +
                    "jours_travailles_formule = EXCLUDED.jours_travailles_formule, " +
                    "jours_droit_acquis = EXCLUDED.jours_droit_acquis, " +
                    "base_valorisation = EXCLUDED.base_valorisation, " +
                    "valorisation_mensuelle = EXCLUDED.valorisation_mensuelle, " +
                    "cumul_jours_droit = EXCLUDED.cumul_jours_droit, " +
                    "cumul_valorisation = EXCLUDED.cumul_valorisation, " +
                    "taux_journalier_conge = EXCLUDED.taux_journalier_conge";

            try (PreparedStatement psUp = c.prepareStatement(sqlUpsert)) {
                psUp.setInt(1, idEmp);
                psUp.setInt(2, idPeriodePaie);
                psUp.setInt(3, annee);
                psUp.setInt(4, mois);
                psUp.setBigDecimal(5, base);
                psUp.setBigDecimal(6, formule);
                psUp.setBigDecimal(7, droitMois);
                psUp.setBigDecimal(8, baseTotale);
                psUp.setBigDecimal(9, valMois);
                psUp.setBigDecimal(10, cumulJours);
                psUp.setBigDecimal(11, cumulVal);
                psUp.setBigDecimal(12, tauxJournalier);
                psUp.executeUpdate();
            }
        }
    }

    public Map<String, Object> obtenirCockpitPeriode(Integer idPeriodePaie) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return obtenirCockpitPeriode(idPeriodePaie, c);
        }
    }

    public Map<String, Object> obtenirCockpitPeriode(Integer idPeriodePaie, Connection c) throws Exception {
        return obtenirCockpitPeriode(idPeriodePaie, 1, 25, null, null, null, c);
    }

    public Map<String, Object> obtenirCockpitPeriode(
            Integer idPeriodePaie, int requestedPage, int requestedSize,
            String query, String site, String filter, Connection c) throws Exception {
        int page = Math.max(1, requestedPage);
        int size = Math.min(100, Math.max(1, requestedSize));
        Map<String, Object> cockpit = new HashMap<>();

        // 1. Informations de la période
        String sqlPeriode = "SELECT p.*, r.code AS code_statut, r.libelle AS libelle_statut " +
                "FROM periode_paie p " +
                "LEFT JOIN ref_statut_periode_paie r ON p.id_statut_periode = r.id_statut_periode " +
                "WHERE p.id_periode_paie = ?";
        Map<String, Object> perMap = new HashMap<>();
        Date dateDebut = null;
        Date dateFin = null;
        try (PreparedStatement ps = c.prepareStatement(sqlPeriode)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    perMap.put("idPeriodePaie", rs.getInt("id_periode_paie"));
                    perMap.put("code", rs.getString("code"));
                    perMap.put("libelle", rs.getString("libelle"));
                    dateDebut = rs.getDate("date_debut");
                    dateFin = rs.getDate("date_fin");
                    perMap.put("dateDebut", dateDebut != null ? dateDebut.toString() : null);
                    perMap.put("dateFin", dateFin != null ? dateFin.toString() : null);
                    perMap.put("statut", rs.getString("code_statut"));
                    perMap.put("libelleStatut", rs.getString("libelle_statut"));
                    perMap.put("version", rs.getInt("version"));
                } else {
                    throw new IllegalArgumentException("Période de paie introuvable (ID " + idPeriodePaie + ")");
                }
            }
        }
        cockpit.put("periode", perMap);

        PayrollPopulationResolver.PayrollPopulation population = payrollPopulationResolver.resolve(idPeriodePaie, c);

        // 2. Préparation amont (Readiness)
        Map<String, Object> readiness = new HashMap<>();
        int nbPointagesScelles = 0;
        String sqlPtg = "SELECT COUNT(DISTINCT id_employe) FROM pointage_cumul_periode WHERE id_periode_paie = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlPtg)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) nbPointagesScelles = rs.getInt(1);
            }
        }
        int effectifAttendu = population.includedEmployeeIds().size();
        readiness.put("effectifAttendu", effectifAttendu);
        readiness.put("nbEmployesPointes", nbPointagesScelles);
        readiness.put("pointageScelle", nbPointagesScelles >= effectifAttendu && effectifAttendu > 0);
        long populationBlocked = population.members().stream()
                .filter(member -> "BLOCKED".equals(member.decision())).count();
        readiness.put("populationBlocked", populationBlocked);

        // 3. Indicateurs financiers & KPIs de la période actuelle
        String sqlKpis = "SELECT COUNT(*) AS effectif_calcule, " +
                "COALESCE(SUM(total_gains_bruts), 0) AS total_brut, " +
                "COALESCE(SUM(total_cotisations_patronales), 0) AS total_cotis_pat, " +
                "COALESCE(SUM(total_cotisations_salariales), 0) AS total_cotis_sal, " +
                "COALESCE(SUM(salaire_net_imposable), 0) AS total_net_imposable, " +
                "COALESCE(SUM(irsa_net), 0) AS total_irsa, " +
                "COALESCE(SUM(net_a_payer), 0) AS total_net_a_payer, " +
                "COALESCE(SUM(total_retenues_acompte_pret), 0) AS total_retenues " +
                "FROM bulletin_paie WHERE id_periode_paie = ?";

        Map<String, Object> kpis = new HashMap<>();
        BigDecimal brutActuel = BigDecimal.ZERO;
        BigDecimal netActuel = BigDecimal.ZERO;
        try (PreparedStatement ps = c.prepareStatement(sqlKpis)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpis.put("effectifCalcule", rs.getInt("effectif_calcule"));
                    brutActuel = rs.getBigDecimal("total_brut");
                    netActuel = rs.getBigDecimal("total_net_a_payer");
                    kpis.put("totalBrut", brutActuel);
                    kpis.put("totalCotisationsPatronales", rs.getBigDecimal("total_cotis_pat"));
                    kpis.put("totalCotisationsSalariales", rs.getBigDecimal("total_cotis_sal"));
                    kpis.put("totalNetImposable", rs.getBigDecimal("total_net_imposable"));
                    kpis.put("totalIrsa", rs.getBigDecimal("total_irsa"));
                    kpis.put("totalNetAPayer", netActuel);
                    kpis.put("totalRetenues", rs.getBigDecimal("total_retenues"));
                }
            }
        }

        // 4. Période M-1 pour calcul de l'écart
        Integer idPeriodeM1 = null;
        BigDecimal brutM1 = BigDecimal.ZERO;
        BigDecimal netM1 = BigDecimal.ZERO;
        String sqlM1 = "SELECT id_periode_paie FROM periode_paie " +
                "WHERE date_fin < (SELECT date_debut FROM periode_paie WHERE id_periode_paie = ?) " +
                "ORDER BY date_fin DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sqlM1)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) idPeriodeM1 = rs.getInt("id_periode_paie");
            }
        }

        if (idPeriodeM1 != null) {
            String sqlKpiM1 = "SELECT COALESCE(SUM(total_gains_bruts), 0) AS brut_m1, " +
                    "COALESCE(SUM(net_a_payer), 0) AS net_m1 FROM bulletin_paie WHERE id_periode_paie = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlKpiM1)) {
                ps.setInt(1, idPeriodeM1);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        brutM1 = rs.getBigDecimal("brut_m1");
                        netM1 = rs.getBigDecimal("net_m1");
                    }
                }
            }
        }

        kpis.put("totalBrutM1", brutM1);
        kpis.put("totalNetM1", netM1);

        double ecartBrutPct = 0.0;
        if (brutM1.compareTo(BigDecimal.ZERO) > 0) {
            ecartBrutPct = brutActuel.subtract(brutM1)
                    .divide(brutM1, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .doubleValue();
        }
        kpis.put("ecartBrutM1Pct", ecartBrutPct);
        cockpit.put("kpis", kpis);

        // Map des nets M-1 par employé
        Map<Integer, BigDecimal> empNetM1Map = new HashMap<>();
        if (idPeriodeM1 != null) {
            String sqlEmpM1 = "SELECT id_employe, net_a_payer FROM bulletin_paie WHERE id_periode_paie = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlEmpM1)) {
                ps.setInt(1, idPeriodeM1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        empNetM1Map.put(rs.getInt("id_employe"), rs.getBigDecimal("net_a_payer"));
                    }
                }
            }
        }

        // 5. Liste des bulletins détaillés
        String sqlBulletins = "SELECT bp.id_bulletin, bp.id_employe, bp.matricule, bp.nom_prenom, " +
                "bp.intitule_poste, bp.nom_site, bp.salaire_base, bp.total_gains_bruts, " +
                "bp.total_cotisations_salariales, bp.irsa_net, bp.net_a_payer, bp.statut, " +
                "COALESCE(bp.type_bulletin, 'MENSUEL') AS type_bulletin " +
                "FROM bulletin_paie bp " +
                "WHERE bp.id_periode_paie = ? " +
                "ORDER BY bp.matricule ASC";

        List<Map<String, Object>> bulletinPage = new ArrayList<>();
        java.util.Set<String> bulletinSites = new java.util.TreeSet<>();
        int totalBulletins = 0;
        int matchingBulletins = 0;
        int requestedOffset = (page - 1) * size;
        int nbAnomalies = 0;
        try (PreparedStatement ps = c.prepareStatement(sqlBulletins)) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    int idEmp = rs.getInt("id_employe");
                    BigDecimal net = rs.getBigDecimal("net_a_payer");
                    row.put("idBulletin", rs.getInt("id_bulletin"));
                    row.put("idEmploye", idEmp);
                    row.put("matricule", rs.getString("matricule"));
                    row.put("nomPrenom", rs.getString("nom_prenom"));
                    row.put("intitulePoste", rs.getString("intitule_poste"));
                    row.put("nomSite", rs.getString("nom_site"));
                    row.put("salaireBase", rs.getBigDecimal("salaire_base"));
                    row.put("totalGainsBruts", rs.getBigDecimal("total_gains_bruts"));
                    row.put("totalCotisationsSalariales", rs.getBigDecimal("total_cotisations_salariales"));
                    row.put("irsaNet", rs.getBigDecimal("irsa_net"));
                    row.put("netAPayer", net);
                    row.put("statut", rs.getString("statut"));
                    row.put("typeBulletin", rs.getString("type_bulletin"));

                    BigDecimal prevNet = empNetM1Map.get(idEmp);
                    Double ecartIndivPct = null;
                    boolean aAlerte = false;
                    if (prevNet != null && prevNet.compareTo(BigDecimal.ZERO) > 0) {
                        ecartIndivPct = net.subtract(prevNet)
                                .divide(prevNet, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .doubleValue();
                        if (Math.abs(ecartIndivPct) >= 20.0) {
                            aAlerte = true;
                        }
                    }
                    if (net.compareTo(BigDecimal.ZERO) <= 0) {
                        aAlerte = true;
                    }
                    if (aAlerte) nbAnomalies++;

                    row.put("ecartM1Pct", ecartIndivPct);
                    row.put("aAlerte", aAlerte);
                    totalBulletins++;
                    if (row.get("nomSite") != null) bulletinSites.add(row.get("nomSite").toString());
                    if (matchesCockpitFilters(row, query, site, filter)) {
                        if (matchingBulletins >= requestedOffset && bulletinPage.size() < size) {
                            bulletinPage.add(row);
                        }
                        matchingBulletins++;
                    }
                }
            }
        }
        List<PayrollPopulationMember> populationMembers = population.members();
        int populationFrom = Math.min((page - 1) * size, populationMembers.size());
        int populationTo = Math.min(populationFrom + size, populationMembers.size());
        List<PayrollPopulationMember> populationPage = populationMembers.subList(populationFrom, populationTo);
        readiness.put("nbAnomalies", nbAnomalies);
        readiness.put("checks", List.of(
                Map.of(
                        "code", "POINTAGE_FROZEN",
                        "status", Boolean.TRUE.equals(readiness.get("pointageScelle")) ? "PASS" : "BLOCK",
                        "message", Boolean.TRUE.equals(readiness.get("pointageScelle"))
                                ? "Le pointage de toute la population est figé."
                                : "Le pointage doit être figé pour toute la population avant le calcul réel.",
                        "actual", nbPointagesScelles,
                        "expected", effectifAttendu),
                Map.of(
                        "code", "PAYROLL_RESULTS",
                        "status", totalBulletins == 0 ? "WARNING" : "PASS",
                        "message", totalBulletins == 0
                                ? "Aucun résultat de paie n'a encore été calculé."
                                : totalBulletins + " résultat(s) de paie disponible(s)."),
                Map.of(
                        "code", "PAYROLL_POPULATION",
                        "status", populationBlocked > 0 ? "BLOCK" : "PASS",
                        "message", populationBlocked > 0
                                ? populationBlocked + " collaborateur(s) ont une configuration de carrière ambiguë ou absente."
                                : "La population de paie est résolue sans ambiguïté."),
                Map.of(
                        "code", "PAYROLL_CONTROL",
                        "status", nbAnomalies > 0 ? "WARNING" : "PASS",
                        "message", nbAnomalies > 0
                                ? nbAnomalies + " résultat(s) exigent un contrôle."
                                : "Aucune variation bloquante détectée dans les résultats.")));
        cockpit.put("readiness", readiness);
        cockpit.put("population", populationPage);
        cockpit.put("populationPage", pageMetadata(populationMembers.size(), page, size));
        cockpit.put("bulletins", bulletinPage);
        cockpit.put("bulletinsPage", pageMetadata(matchingBulletins, page, size));
        cockpit.put("sites", List.copyOf(bulletinSites));

        Map<String, Object> validationSummary = new HashMap<>();
        validationSummary.put("population", effectifAttendu);
        validationSummary.put("netTotal", kpis.get("totalNetAPayer"));
        validationSummary.put("employerContributions", kpis.get("totalCotisationsPatronales"));
        validationSummary.put("taxes", kpis.get("totalIrsa"));
        validationSummary.put("warnings", nbAnomalies);
        validationSummary.put("version", perMap.get("version"));
        try (PreparedStatement statement = c.prepareStatement(
                "SELECT MAX(date_calcul) AS calculated_at, MAX(date_validation) AS validated_at, "
                        + "CASE WHEN COUNT(DISTINCT calcule_par) = 1 THEN MAX(calcule_par) END AS calculated_by "
                        + "FROM bulletin_paie WHERE id_periode_paie = ?")) {
            statement.setInt(1, idPeriodePaie);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                validationSummary.put("calculatedAt", result.getTimestamp("calculated_at"));
                validationSummary.put("validatedAt", result.getTimestamp("validated_at"));
                validationSummary.put("calculatedBy", result.getObject("calculated_by"));
            }
        }
        cockpit.put("validationSummary", validationSummary);

        return cockpit;
    }

    private boolean matchesCockpitFilters(
            Map<String, Object> row, String query, String site, String filter) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        boolean matchesQuery = normalizedQuery.isEmpty()
                || containsIgnoreCase(row.get("matricule"), normalizedQuery)
                || containsIgnoreCase(row.get("nomPrenom"), normalizedQuery)
                || containsIgnoreCase(row.get("intitulePoste"), normalizedQuery);
        boolean matchesSite = site == null || site.isBlank() || "ALL".equalsIgnoreCase(site)
                || site.equals(row.get("nomSite"));
        String normalizedFilter = filter == null ? "ALL" : filter.toUpperCase(java.util.Locale.ROOT);
        boolean matchesStatus = switch (normalizedFilter) {
            case "ALERTS" -> Boolean.TRUE.equals(row.get("aAlerte"));
            case "STC" -> "STC".equals(row.get("typeBulletin"));
            case "VALIDE" -> "VALIDE".equals(row.get("statut"));
            case "CLOTURE" -> "CLOTURE".equals(row.get("statut"));
            default -> true;
        };
        return matchesQuery && matchesSite && matchesStatus;
    }

    private boolean containsIgnoreCase(Object value, String normalizedQuery) {
        return value != null && value.toString().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery);
    }

    private Map<String, Object> pageMetadata(int totalElements, int page, int size) {
        return Map.of(
                "totalElements", totalElements,
                "totalPages", Math.max(1, (int) Math.ceil((double) totalElements / size)),
                "currentPage", page,
                "pageSize", size);
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
