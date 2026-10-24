package mg.galaxam.sirh.paie.application;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.domain.dto.PayrollBatchResult;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import mg.galaxam.sirh.common.transaction.TransactionRunner;
import mg.galaxam.sirh.paie.api.dto.PayrollPopulationMember;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
import mg.galaxam.sirh.paie.application.export.PayrollEmployerSnapshotService;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodTransitionLogger;

/**
 * Service d'orchestration de paie.
 * Collecte les données sources amont (RH, pointage, congés, enfants, prêts, acomptes),
 * exécute le moteur déterministe PayrollExecutionEngine, et persiste les résultats de manière immuable.
 */
@Service
public class PayrollService {

    @Autowired
    private PayrollExecutionEngine payrollExecutionEngine;

    @Autowired
    private PayrollLegalContextResolver payrollLegalContextResolver;

    @Autowired
    private PayrollPopulationResolver payrollPopulationResolver;

    @Autowired
    private PayrollCockpitService payrollCockpitService;

    @Autowired
    private PayrollEmployeeReadService payrollEmployeeReadService;

    @Autowired
    private EmploymentContextResolver employmentContextResolver;

    @Autowired
    private TransactionRunner transactionRunner;

    @Autowired
    private mg.galaxam.sirh.conge.application.CongeService congeService;

    @Autowired
    private PayrollLeaveJournalService payrollLeaveJournalService;

    @Autowired
    private mg.galaxam.sirh.conge.application.CongeLotValorisationService congeLotValorisationService;

    // Cache local des id_rubrique par code (thread-safe)
    private final Map<String, Integer> cacheRubriques = new ConcurrentHashMap<>();
    private final PayrollEmployerSnapshotService employerSnapshotService = new PayrollEmployerSnapshotService();

    /**
     * Invalide le cache local des rubriques pour forcer un rechargement depuis la base.
     */
    public void invalidateRubriqueCache() {
        cacheRubriques.clear();
    }

    private void lierBulletinAuSnapshot(Integer bulletinId, PayrollCalculationInput input, Connection c)
            throws SQLException {
        if (bulletinId == null) throw new IllegalArgumentException("Bulletin de paie non enregistré.");
        if (input.getAttendanceInput() == null) {
            // Un recalcul sans snapshot ne doit pas conserver la référence de l'ancien calcul.
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE bulletin_paie SET id_snapshot_pointage = NULL, version_snapshot_pointage = NULL "
                    + "WHERE id_bulletin = ?")) {
                ps.setInt(1, bulletinId);
                if (ps.executeUpdate() != 1) throw new SQLException("Bulletin introuvable pendant le recalcul.");
            }
            return;
        }
        var attendance = input.getAttendanceInput();
        if (input.getIdEmploye() != null && input.getIdEmploye() != attendance.employeeId()
                || input.getIdPeriodePaie() != null && input.getIdPeriodePaie() != attendance.payrollPeriodId()) {
            throw new IllegalArgumentException("Snapshot de pointage étranger au calcul demandé.");
        }
        String sql = "UPDATE bulletin_paie b SET id_snapshot_pointage = s.id_cumul, " +
                "version_snapshot_pointage = s.version_snapshot FROM pointage_cumul_periode s " +
                "WHERE b.id_bulletin = ? AND s.id_cumul = ? AND s.version_snapshot = ? " +
                "AND s.id_employe = b.id_employe AND s.id_periode_paie = b.id_periode_paie " +
                "AND s.statut_snapshot IN ('GELE', 'CONSOMME')";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, bulletinId);
            ps.setInt(2, attendance.snapshotId());
            ps.setInt(3, attendance.snapshotVersion());
            if (ps.executeUpdate() != 1) throw new SQLException(
                    "Snapshot de pointage incompatible avec le bulletin, sa période ou sa version.");
        }
        marquerSnapshotConsomme(bulletinId, c);
    }

    private void verifierBulletinNonObsolete(Integer bulletinId, Connection c) throws Exception {
        String sql = "SELECT b.id_snapshot_pointage, b.version_snapshot_pointage, b.id_config_legale, " +
                "b.id_employe, b.id_periode_paie, b.salaire_base, b.date_calcul, p.date_debut, p.date_fin FROM bulletin_paie b " +
                "JOIN periode_paie p ON p.id_periode_paie = b.id_periode_paie WHERE b.id_bulletin = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, bulletinId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Bulletin introuvable pendant le contrôle d'obsolescence.");
                // Les éléments RH datés peuvent changer entre le calcul et la validation.
                // Le salaire de clôture est comparé directement ; seuls les changements
                // qui recouvrent la campagne rendent le bulletin obsolète.
                try {
                    var coverage = employmentContextResolver.resolveCoverage(rs.getInt("id_employe"),
                            rs.getDate("date_debut").toLocalDate(), rs.getDate("date_fin").toLocalDate(), c);
                    var employment = employmentContextResolver.resolveContext(rs.getInt("id_employe"), coverage.lastDay(), c);
                    BigDecimal calculatedSalary = rs.getBigDecimal("salaire_base");
                    if (employment == null || employment.salaireBase() == null || calculatedSalary == null
                            || employment.salaireBase().compareTo(calculatedSalary) != 0) {
                        throw new IllegalStateException("PAYROLL_RESULT_OBSOLETE: le salaire ou l'emploi a changé; recalcul explicite requis.");
                    }
                } catch (IllegalStateException error) {
                    if (error.getMessage() != null && error.getMessage().startsWith("PAYROLL_RESULT_OBSOLETE")) throw error;
                    throw new IllegalStateException("PAYROLL_RESULT_OBSOLETE: le contexte d'emploi a changé; recalcul explicite requis.", error);
                }
                Timestamp calculatedAt = rs.getTimestamp("date_calcul");
                if (calculatedAt != null) {
                    try (PreparedStatement changed = c.prepareStatement(
                            "SELECT EXISTS (SELECT 1 FROM employe_affectation_carriere a "
                            + "WHERE a.id_employe = ? AND a.updated_at > ? "
                            + "AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?)) "
                            + "OR EXISTS (SELECT 1 FROM contrat_travail ct WHERE ct.id_employe = ? AND ct.updated_at > ? "
                            + "AND ct.date_debut <= ? AND (ct.date_fin IS NULL OR ct.date_fin >= ?))")) {
                        changed.setInt(1, rs.getInt("id_employe")); changed.setTimestamp(2, calculatedAt);
                        changed.setDate(3, rs.getDate("date_fin")); changed.setDate(4, rs.getDate("date_debut"));
                        changed.setInt(5, rs.getInt("id_employe")); changed.setTimestamp(6, calculatedAt);
                        changed.setDate(7, rs.getDate("date_fin")); changed.setDate(8, rs.getDate("date_debut"));
                        try (ResultSet change = changed.executeQuery()) {
                            change.next();
                            if (change.getBoolean(1)) throw new IllegalStateException(
                                    "PAYROLL_RESULT_OBSOLETE: le contrat ou l'affectation a changé; recalcul explicite requis.");
                        }
                    }
                }
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
                "UPDATE pointage_cumul_periode s SET statut_snapshot = 'CONSOMME' " +
                "FROM bulletin_paie b WHERE b.id_bulletin = ? AND b.id_snapshot_pointage = s.id_cumul " +
                "AND b.version_snapshot_pointage = s.version_snapshot " +
                "AND b.id_employe = s.id_employe AND b.id_periode_paie = s.id_periode_paie " +
                "AND s.statut_snapshot IN ('GELE', 'CONSOMME')")) {
            ps.setInt(1, bulletinId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Snapshot de pointage modifié pendant le rattachement au bulletin.");
            }
        }
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    // Bulletin individuel et restauration des imputations avant recalcul.
    /**
     * Calcule le bulletin d'un salarié pour une période donnée (transactionnel avec Connection fournie).
     */
    public BulletinPaieComplet calculerBulletinEmploye(Integer idEmploye, Integer idPeriodePaie,
                                                      boolean simulation, Integer idUtilisateur,
                                                      Connection c) throws Exception {
        if (idEmploye == null || idPeriodePaie == null) {
            throw new IllegalArgumentException("L'id de l'employé et l'id de la période de paie sont obligatoires.");
        }

        if (c.getAutoCommit()) {
            throw new IllegalStateException("Le calcul de paie requiert une transaction JDBC.");
        }
        try (PreparedStatement lock = c.prepareStatement(
                "SELECT id_periode_paie FROM periode_paie WHERE id_periode_paie = ? FOR UPDATE")) {
            lock.setInt(1, idPeriodePaie);
            try (ResultSet rows = lock.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Période de paie introuvable.");
            }
        }
        Savepoint recalculation = c.setSavepoint();
        try {
        if (!simulation) {
            try (PreparedStatement pending = c.prepareStatement(
                    "SELECT COUNT(*) FROM paie_avance_acompte WHERE id_employe = ? "
                    + "AND id_periode_paie = ? AND statut = 'EN_ATTENTE'")) {
                pending.setInt(1, idEmploye);
                pending.setInt(2, idPeriodePaie);
                try (ResultSet rows = pending.executeQuery()) {
                    rows.next();
                    if (rows.getInt(1) > 0) {
                        throw new IllegalStateException("PAYROLL_VARIABLES_PENDING_APPROVAL: valider ou rejeter les variables avant calcul.");
                    }
                }
            }
        }
        // Remettre à disposition, dans cette transaction, les variables déjà
        // imputées par ce bulletin. La simulation annule cette remise en état.
        restaurerImputationsBulletin(idEmploye, idPeriodePaie, simulation, c);

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
            if (input.getAttendanceInput() != null) {
                bp.setIdSnapshotPointage(input.getAttendanceInput().snapshotId());
                bp.setVersionSnapshotPointage(input.getAttendanceInput().snapshotVersion());
            }
            imputerAvances(complet, idPeriodePaie, c);
        }

        if (simulation) c.rollback(recalculation);
        else c.releaseSavepoint(recalculation);
        return complet;
        } catch (Exception failure) {
            c.rollback(recalculation);
            throw failure;
        }
    }

    private void restaurerImputationsBulletin(int employeId, int periodeId, boolean simulation, Connection c) throws Exception {
        BulletinPaie precedent = BulletinPaie.trouverParPeriodeEtEmployeEtType(periodeId, employeId, "MENSUEL", c);
        if (precedent == null) return;
        if (!simulation && ("CLOTURE".equalsIgnoreCase(precedent.getStatut())
                || "VALIDE".equalsIgnoreCase(precedent.getStatut()))) {
            throw new IllegalStateException("BULLETIN_VALIDE_IMMUABLE: une réouverture contrôlée est requise avant recalcul.");
        }
        int bulletinId = precedent.getIdBulletin();
        restaurerImputationsMouvements(bulletinId, employeId, periodeId, c);
    }

    private void restaurerImputationsMouvements(int bulletinId, int employeId, int periodeId, Connection c) throws Exception {
        String annule = mg.galaxam.sirh.paie.domain.PayrollStatuses.cancelledAdjustmentSql("a.statut");
        String annuleUnqual = mg.galaxam.sirh.paie.domain.PayrollStatuses.cancelledAdjustmentSql("statut");
        // La ligne de journal permet de retirer exactement la retenue de CE bulletin.
        // Les dossiers historiques sans ligne gardent le comportement de reprise initial.
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE paie_avance_acompte a SET montant_impute = GREATEST(a.montant_impute - i.montant, 0), " +
                "solde_restant = LEAST(a.solde_restant + i.montant, a.montant_initial), " +
                "statut = CASE WHEN " + annule + " THEN 'ANNULE' ELSE 'APPROUVE' END, " +
                "id_bulletin = NULL, updated_at = CURRENT_TIMESTAMP " +
                "FROM paie_mouvement_imputation i WHERE i.id_avance = a.id_avance " +
                "AND i.id_bulletin = ? AND a.id_employe = ?")) {
            ps.setInt(1, bulletinId);
            ps.setInt(2, employeId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM paie_mouvement_imputation WHERE id_bulletin = ?")) {
            ps.setInt(1, bulletinId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE paie_avance_acompte a SET id_bulletin = (" +
                "SELECT i.id_bulletin FROM paie_mouvement_imputation i WHERE i.id_avance = a.id_avance " +
                "ORDER BY i.created_at DESC, i.id_imputation DESC LIMIT 1) " +
                "WHERE a.id_employe = ? AND a.id_bulletin IS NULL " +
                "AND EXISTS (SELECT 1 FROM paie_mouvement_imputation i WHERE i.id_avance = a.id_avance)")) {
            ps.setInt(1, employeId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE paie_avance_acompte SET montant_impute = 0, solde_restant = montant_initial, " +
                "statut = CASE WHEN " + annuleUnqual + " THEN 'ANNULE' ELSE 'APPROUVE' END, " +
                "id_bulletin = NULL, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_bulletin = ? AND id_employe = ? AND id_periode_paie = ? " +
                "AND (statut IN ('APPROUVE','IMPUTE_PAIE') OR " + annuleUnqual + ") " +
                "AND NOT EXISTS (SELECT 1 FROM paie_mouvement_imputation i WHERE i.id_avance = paie_avance_acompte.id_avance)")) {
            ps.setInt(1, bulletinId);
            ps.setInt(2, employeId);
            ps.setInt(3, periodeId);
            ps.executeUpdate();
        }
    }


    /**
     * Surcharge sans Connection pour appel de service autonome (gère sa propre transaction).
     */
    public BulletinPaieComplet calculerBulletinEmploye(Integer idEmploye, Integer idPeriodePaie,
                                                      boolean simulation, Integer idUtilisateur) throws Exception {
        return transactionRunner.required(c ->
                calculerBulletinEmploye(idEmploye, idPeriodePaie, simulation, idUtilisateur, c));
    }

    // Campagne : calcul collectif, honoraires, validation et clôture.
    /**
     * Calcule la paie de tous les salariés actifs pour une période donnée.
     */
    public PayrollBatchResult calculerPaiePeriode(Integer idPeriodePaie, boolean simulation,
                                                 Integer idUtilisateur, Connection c) throws Exception {
        PayrollPopulationResolver.PayrollPopulation population = payrollPopulationResolver.resolve(idPeriodePaie, c);
        PayrollBatchResult batch = new PayrollBatchResult(idPeriodePaie, population.periodCode());
        Map<Integer, PayrollPopulationMember> membersById = population.members().stream()
                .collect(java.util.stream.Collectors.toMap(
                        member -> member.employeeId(), member -> member, (first, ignored) -> first));
        population.members().stream()
                .filter(member -> "BLOCKED".equals(member.decision()))
                .forEach(member -> batch.ajouterBloque(member.employeeId(), member.employeeName(),
                        member.matricule(), member.reasonCode(), null));
        List<Integer> idsEmployes = population.includedEmployeeIds();

        for (Integer idEmp : idsEmployes) {
            Savepoint employeeSavepoint = c.getAutoCommit() ? null : c.setSavepoint("payroll_employee_" + idEmp);
            try {
                BulletinPaie dejaValide = BulletinPaie.trouverParPeriodeEtEmployeEtType(
                        idPeriodePaie, idEmp, "MENSUEL", c);
                if (dejaValide != null && java.util.Set.of("VALIDE", "CLOTURE").contains(dejaValide.getStatut())) {
                    verifierBulletinNonObsolete(dejaValide.getIdBulletin(), c);
                    batch.ajouterBulletin(obtenirBulletinComplet(dejaValide.getIdBulletin(), c));
                    if (employeeSavepoint != null) c.releaseSavepoint(employeeSavepoint);
                    continue;
                }
                BulletinPaieComplet bpc = calculerBulletinEmploye(idEmp, idPeriodePaie, simulation, idUtilisateur, c);
                batch.ajouterBulletin(bpc);
                if (employeeSavepoint != null) c.releaseSavepoint(employeeSavepoint);
            } catch (Exception employeeFailure) {
                if (employeeSavepoint != null) c.rollback(employeeSavepoint);
                var member = membersById.get(idEmp);
                String message = employeeFailure.getMessage();
                String reasonCode = null;
                if (message != null) {
                    int separator = message.indexOf(':');
                    String prefix = separator > 0 ? message.substring(0, separator) : message;
                    if (prefix.matches("[A-Z][A-Z0-9_]+")) reasonCode = prefix;
                }
                batch.ajouterBloque(idEmp, member == null ? null : member.employeeName(),
                        member == null ? null : member.matricule(), reasonCode, message);
            }
        }

        // Les prestations indépendantes sont réglées sur facture, hors campagne de paie.
        if (!simulation) {
            // Si la période était en PAIE_VALIDEE, un recalcul la replace en VALIDEE (bulletins en attente d'approbation)
            String sqlReset = "UPDATE periode_paie SET " +
                    "statut = 'VALIDEE', " +
                    "version = version + 1, " +
                    "updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id_periode_paie = ? AND statut = 'PAIE_VALIDEE' " +
                    "RETURNING version - 1 AS version_source, version AS version_cible";
            try (PreparedStatement psReset = c.prepareStatement(sqlReset)) {
                psReset.setInt(1, idPeriodePaie);
                try (ResultSet rsReset = psReset.executeQuery()) {
                    if (rsReset.next()) {
                        int vSource = rsReset.getInt("version_source");
                        int vCible = rsReset.getInt("version_cible");
                        PayrollPeriodTransitionLogger.log(
                                c, idPeriodePaie, "RECALCULATE_PAYROLL",
                                "PAIE_VALIDEE", "VALIDEE", vSource, vCible,
                                resolveUserId(idUtilisateur),
                                "Recalcul de paie : remise en validation");
                    }
                }
            }
        }

        return batch;
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
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriodePaie, c).stream()
                .filter(b -> !"STC".equalsIgnoreCase(b.getTypeBulletin())).toList();
        var population = payrollPopulationResolver.resolve(idPeriodePaie, c);
        java.util.Set<Integer> attendus = new java.util.HashSet<>(population.includedEmployeeIds());
        java.util.Set<Integer> presentes = new java.util.HashSet<>();
        if (attendus.isEmpty() || population.members().stream()
                .anyMatch(member -> "BLOCKED".equals(member.decision()))) {
            throw new IllegalStateException("PAYROLL_POPULATION_INCOMPLETE: la population de la période doit être résolue avant validation.");
        }
        for (BulletinPaie bulletin : bulletins) {
            if (!attendus.contains(bulletin.getIdEmploye()) || !presentes.add(bulletin.getIdEmploye())
                    || !java.util.Set.of("CALCULE", "VALIDE").contains(bulletin.getStatut())) {
                throw new IllegalStateException("PAYROLL_POPULATION_INCOMPLETE: bulletin inattendu, doublon ou statut invalide.");
            }
        }
        if (!presentes.equals(attendus)) {
            throw new IllegalStateException("PAYROLL_POPULATION_INCOMPLETE: un ou plusieurs salariés inclus n'ont pas de bulletin.");
        }
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
                employerSnapshotService.capture(b.getIdBulletin(), c);
                if (b.getIdSnapshotPointage() != null) marquerSnapshotConsomme(b.getIdBulletin(), c);
                batch.ajouterBulletin(obtenirBulletinComplet(b.getIdBulletin(), c));
            } else {
                // Le bulletin individuel validé avant la campagne reste visible dans
                // les totaux de validation, sans nouvelle écriture ni nouveau paiement.
                verifierBulletinNonObsolete(b.getIdBulletin(), c);
                batch.ajouterBulletin(obtenirBulletinComplet(b.getIdBulletin(), c));
            }
        }

        // Transition du statut de la période vers PAIE_VALIDEE (Paie Validée & Approuvée)
        String sqlPeriode = "UPDATE periode_paie SET " +
                "statut = 'PAIE_VALIDEE', " +
                "version = version + 1, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_periode_paie = ? AND statut = 'VALIDEE' " +
                "RETURNING version - 1 AS version_source, version AS version_cible";
        try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
            psP.setInt(1, idPeriodePaie);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    int vSource = rsP.getInt("version_source");
                    int vCible = rsP.getInt("version_cible");
                    PayrollPeriodTransitionLogger.log(
                            c, idPeriodePaie, "VALIDATE_PAYROLL",
                            "VALIDEE", "PAIE_VALIDEE", vSource, vCible,
                            resolveUserId(idUtilisateur),
                            "Validation globale des bulletins de paie");
                }
            }
        }

        return batch;
    }

    /** Valide un bulletin final de sortie sans changer le statut de la campagne entière. */
    public BulletinPaieComplet validerBulletinSortie(int periodeId, int employeId, Integer utilisateurId,
                                                     Connection c) throws Exception {
        BulletinPaie bulletin = BulletinPaie.trouverParPeriodeEtEmployeEtType(periodeId, employeId, "MENSUEL", c);
        if (bulletin == null || !"CALCULE".equals(bulletin.getStatut()))
            throw new IllegalStateException("BULLETIN_SORTIE_NON_CALCULE: calculez le bulletin individuel avant validation.");
        verifierBulletinNonObsolete(bulletin.getIdBulletin(), c);
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE bulletin_paie SET statut='VALIDE', valide_par=?, date_validation=CURRENT_TIMESTAMP, "
                + "updated_at=CURRENT_TIMESTAMP WHERE id_bulletin=? AND statut='CALCULE'")) {
            if (utilisateurId == null) ps.setNull(1, java.sql.Types.INTEGER);
            else ps.setInt(1, utilisateurId);
            ps.setInt(2, bulletin.getIdBulletin());
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Le bulletin a changé pendant sa validation.");
        }
        employerSnapshotService.capture(bulletin.getIdBulletin(), c);
        if (bulletin.getIdSnapshotPointage() != null) marquerSnapshotConsomme(bulletin.getIdBulletin(), c);
        return obtenirBulletinComplet(bulletin.getIdBulletin(), c);
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
        // Même verrou que l'enregistrement des règlements : empêche qu'un
        // paiement arrive entre le contrôle et la réouverture du bulletin.
        try (PreparedStatement lock = c.prepareStatement(
                "SELECT id_bulletin FROM bulletin_paie WHERE id_periode_paie=? "
                + "AND COALESCE(type_bulletin,'MENSUEL')='MENSUEL' ORDER BY id_bulletin FOR UPDATE")) {
            lock.setInt(1, idPeriodePaie);
            try (ResultSet ignored = lock.executeQuery()) { while (ignored.next()) { /* verrouiller chaque ligne */ } }
        }
        Integer bloque = bulletinBloquantReouverture(idPeriodePaie, c);
        if (bloque != null) throw new IllegalStateException(
                "PERIODE_REOUVERTURE_REGLEMENT: le bulletin " + bloque
                + " possède un règlement, un virement en attente ou un STC actif. "
                + "Utilisez une régularisation auditée sur une autre période.");
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriodePaie, c);
        for (BulletinPaie bp : bulletins) {
            if ("STC".equalsIgnoreCase(bp.getTypeBulletin())) {
                continue; // Le bulletin STC validé reste distinct de la paie mensuelle.
            }
            bp.rouvrirBulletin(motif, idUtilisateur, c);
        }

        // 1. Dé-imputer uniquement les retenues de ces bulletins : un reliquat peut
        // déjà avoir été prélevé sur d'autres périodes.
        for (BulletinPaie bp : bulletins) {
            if (!"STC".equalsIgnoreCase(bp.getTypeBulletin())) {
                restaurerImputationsMouvements(bp.getIdBulletin(), bp.getIdEmploye(), idPeriodePaie, c);
            }
        }

    }

    /** Bulletin dont la dette est déjà engagée par un règlement, un lot ou un STC. */
    public Integer bulletinBloquantReouverture(Integer idPeriodePaie, Connection c) throws SQLException {
        // Un règlement ou un virement préparé se réfère au montant figé du
        // bulletin. Le recalculer en place ferait diverger dette, preuve et solde.
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT b.id_bulletin FROM bulletin_paie b WHERE b.id_periode_paie=? "
                + "AND COALESCE(b.type_bulletin,'MENSUEL')='MENSUEL' AND ("
                + "EXISTS (SELECT 1 FROM paie_bulletin_reglement r WHERE r.id_bulletin=b.id_bulletin "
                + "AND r.annule_at IS NULL) OR EXISTS (SELECT 1 FROM paie_bulletin_virement_ligne l "
                + "WHERE l.id_bulletin=b.id_bulletin AND l.statut='EN_ATTENTE') OR EXISTS ("
                + "SELECT 1 FROM paie_stc s WHERE s.id_bulletin_mensuel=b.id_bulletin "
                + "AND s.statut IN ('VALIDE','REGLE'))) LIMIT 1")) {
            ps.setInt(1, idPeriodePaie);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /**
     * Récupère un bulletin de paie complet avec toutes ses lignes.
     */
    public BulletinPaieComplet obtenirBulletinComplet(Integer idBulletin, Connection c) throws Exception {
        BulletinPaie bp = BulletinPaie.trouverParId(idBulletin, c);
        if (bp == null) return null;

        List<BulletinPaieLigne> lignes = BulletinPaieLigne.listerParBulletin(idBulletin, c);
        BulletinPaieComplet complet = new BulletinPaieComplet(bp, lignes);

        // Reconstruire l'explication canonique backend-driven depuis les données du bulletin et de ses lignes
        BigDecimal base = bp.getSalaireBase() != null ? bp.getSalaireBase() : BigDecimal.ZERO;
        BigDecimal txHor = bp.getTauxHoraire() != null ? bp.getTauxHoraire() : BigDecimal.ZERO;
        BigDecimal diviseur = (base.signum() > 0 && txHor.signum() > 0)
                ? base.divide(txHor, 2, RoundingMode.HALF_UP)
                : mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine.DIVISEUR_HEURES_MENSUELLES;

        BulletinPaieLigne lAbs = complet.trouverLigne("RETENUE_ABSENCE");
        BigDecimal hAbs = lAbs != null && lAbs.getQuantite() != null ? lAbs.getQuantite() : BigDecimal.ZERO;
        long minAbs = hAbs.multiply(new BigDecimal("60")).setScale(0, RoundingMode.HALF_UP).longValue();
        int minRetard = bp.getMinutesRetard() != null ? bp.getMinutesRetard() : 0;
        BigDecimal mntAbs = lAbs != null && lAbs.getMontantSalarial() != null ? lAbs.getMontantSalarial() : BigDecimal.ZERO;

        BulletinPaieLigne lDed = complet.trouverLigne("DEDUCTION_CONGE_BASE");
        BulletinPaieLigne lInd = complet.trouverLigne("INDEMNITE_CONGE_PAYE");
        BigDecimal nbConge = bp.getJoursConges() != null ? bp.getJoursConges()
                : (lDed != null && lDed.getQuantite() != null ? lDed.getQuantite() : BigDecimal.ZERO);
        BigDecimal tx30 = base.divide(new BigDecimal("30.0"), 4, RoundingMode.HALF_UP);
        BigDecimal dedConge = lDed != null && lDed.getMontantSalarial() != null ? lDed.getMontantSalarial() : BigDecimal.ZERO;
        BigDecimal txConge = lInd != null && lInd.getBaseCalcul() != null ? lInd.getBaseCalcul() : tx30;
        BigDecimal indConge = lInd != null && lInd.getMontantSalarial() != null ? lInd.getMontantSalarial() : BigDecimal.ZERO;
        BigDecimal netConge = indConge.subtract(dedConge);

        BulletinPaieLigne l30 = complet.trouverLigne("HEURES_SUP_30");
        BigDecimal h30 = l30 != null && l30.getQuantite() != null ? l30.getQuantite() : bp.getHeuresHs30();
        BigDecimal mnt30 = l30 != null && l30.getMontantSalarial() != null ? l30.getMontantSalarial() : BigDecimal.ZERO;

        BulletinPaieLigne l50 = complet.trouverLigne("HEURES_SUP_50");
        BigDecimal h50 = l50 != null && l50.getQuantite() != null ? l50.getQuantite() : bp.getHeuresHs50();
        BigDecimal mnt50 = l50 != null && l50.getMontantSalarial() != null ? l50.getMontantSalarial() : BigDecimal.ZERO;

        BulletinPaieLigne lNuit = complet.trouverLigne("MAJORATION_NUIT");
        BigDecimal hNuit = lNuit != null && lNuit.getQuantite() != null ? lNuit.getQuantite() : bp.getHeuresNuit();
        BigDecimal mntNuit = lNuit != null && lNuit.getMontantSalarial() != null ? lNuit.getMontantSalarial() : BigDecimal.ZERO;

        BulletinPaieLigne lDim = complet.trouverLigne("HEURES_DIMANCHE");
        BigDecimal hDim = lDim != null && lDim.getQuantite() != null ? lDim.getQuantite() : BigDecimal.ZERO;
        BigDecimal mntDim = lDim != null && lDim.getMontantSalarial() != null ? lDim.getMontantSalarial() : BigDecimal.ZERO;

        BulletinPaieLigne lFer = complet.trouverLigne("MAJORATION_FERIE");
        BigDecimal hFer = lFer != null && lFer.getQuantite() != null ? lFer.getQuantite() : BigDecimal.ZERO;
        BigDecimal mntFer = lFer != null && lFer.getMontantSalarial() != null ? lFer.getMontantSalarial() : BigDecimal.ZERO;

        complet.setExplication(new BulletinPaieComplet.ExplicationPaieDTO(
                minAbs, hAbs, minRetard, diviseur, txHor, mntAbs,
                nbConge, base, tx30, dedConge, txConge, indConge, netConge,
                h30 != null ? h30 : BigDecimal.ZERO, mnt30,
                h50 != null ? h50 : BigDecimal.ZERO, mnt50,
                hNuit != null ? hNuit : BigDecimal.ZERO, mntNuit,
                hDim, mntDim, hFer, mntFer
        ));

        return complet;
    }

    /**
     * Point d'entrée public d'inspection et de calcul. L'assemblage reste dans la transaction
     * appelante : en calcul réel, les mouvements dérivés peuvent être verrouillés et actualisés.
     */
    public PayrollCalculationInput preparerInput(Integer idEmploye, Integer idPeriodePaie,
                                                 boolean simulation, Connection c) throws Exception {
        return new PayrollCalculationContextAssembler(employmentContextResolver,
                payrollLegalContextResolver, congeService, congeLotValorisationService)
                .assemble(idEmploye, idPeriodePaie, simulation, c);
    }

    private void sauvegarderBulletinEtLignes(BulletinPaieComplet complet, Connection c) throws Exception {
        BulletinPaie bp = complet.getBulletin();

        // 1. Vérifier si un bulletin de même type existe déjà pour cette période et cet employé
        String typeBulletin = bp.getTypeBulletin() != null ? bp.getTypeBulletin() : "MENSUEL";
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
            if ("VALIDE".equalsIgnoreCase(existant.getStatut())) {
                throw new IllegalStateException("Le bulletin " + existant.getReferenceBulletin() + " est validé et immuable. Une réouverture préalable est obligatoire avant tout recalcul.");
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

    private void imputerAvances(BulletinPaieComplet complet, Integer idPeriodePaie, Connection c) throws Exception {
        BulletinPaie bp = complet.getBulletin();

        // 1. Imputation des avances et acomptes
        if (complet.getImputationsMouvements() != null && !complet.getImputationsMouvements().isEmpty()) {
            String sqlAvance = "UPDATE paie_avance_acompte SET " +
                    "montant_impute = montant_impute + ?, " +
                    "solde_restant = GREATEST(solde_restant - ?, 0), " +
                    "statut = CASE WHEN solde_restant - ? <= 0 THEN 'IMPUTE_PAIE' ELSE 'APPROUVE' END, " +
                    "id_bulletin = ?, " +
                    "updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id_avance = ?";

            try (PreparedStatement ps = c.prepareStatement(sqlAvance)) {
                for (var imputation : complet.getImputationsMouvements()) {
                    ps.setBigDecimal(1, imputation.montantImpute());
                    ps.setBigDecimal(2, imputation.montantImpute());
                    ps.setBigDecimal(3, imputation.montantImpute());
                    ps.setInt(4, bp.getIdBulletin());
                    ps.setInt(5, imputation.idMouvement());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO paie_mouvement_imputation (id_avance, id_bulletin, montant) VALUES (?, ?, ?)")) {
                for (var imputation : complet.getImputationsMouvements()) {
                    ps.setInt(1, imputation.idMouvement());
                    ps.setInt(2, bp.getIdBulletin());
                    ps.setBigDecimal(3, imputation.montantImpute());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        if (complet.getIdsAvancesImputees() != null) {
            java.util.Set<Integer> retenues = complet.getImputationsMouvements().stream()
                    .map(BulletinPaieComplet.MouvementImputation::idMouvement)
                    .collect(java.util.stream.Collectors.toSet());
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE paie_avance_acompte SET montant_impute = montant_initial, solde_restant = 0, " +
                            "statut = 'IMPUTE_PAIE', id_bulletin = ?, id_periode_paie = ?, updated_at = CURRENT_TIMESTAMP WHERE id_avance = ?")) {
                for (Integer id : complet.getIdsAvancesImputees()) {
                    if (retenues.contains(id)) continue;
                    ps.setInt(1, bp.getIdBulletin());
                    ps.setInt(2, idPeriodePaie);
                    ps.setInt(3, id);
                    ps.addBatch();
                }
                ps.executeBatch();
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

    /** Déclenché pendant la clôture, dans la transaction fournie par l'appelant. */
    public void mettreAJourJournalConge(Integer idPeriodePaie, Connection c) throws Exception {
        payrollLeaveJournalService.mettreAJourJournalConge(idPeriodePaie, c);
    }

    // Compatibilité des appelants : les projections de lecture sont dans PayrollCockpitService.
    public Map<String, Object> obtenirCockpitPeriode(Integer idPeriodePaie) throws Exception {
        return payrollCockpitService.obtenirCockpitPeriode(idPeriodePaie);
    }

    public Map<String, Object> obtenirCockpitPeriode(Integer idPeriodePaie, Connection c) throws Exception {
        return payrollCockpitService.obtenirCockpitPeriode(idPeriodePaie, c);
    }

    public Map<String, Object> obtenirPopulationCockpit(Integer idPeriodePaie, Connection c) throws Exception {
        return payrollCockpitService.obtenirPopulationCockpit(idPeriodePaie, c);
    }

    public Map<String, Object> obtenirCockpitPeriode(Integer idPeriodePaie, int requestedPage, int requestedSize,
            String query, String site, String filter, Connection c) throws Exception {
        return payrollCockpitService.obtenirCockpitPeriode(idPeriodePaie, requestedPage, requestedSize,
                query, site, filter, c);
    }

    public List<Map<String, Object>> listerBulletinsEmploye(Integer idEmploye, Connection c) throws Exception {
        return payrollEmployeeReadService.listerBulletinsEmploye(idEmploye, c);
    }

    public Map<String, Object> obtenirSynthesePaieEmploye(Integer idEmploye, Connection c) throws Exception {
        return payrollEmployeeReadService.obtenirSynthesePaieEmploye(idEmploye, c);
    }

    private Integer resolveUserId(Integer explicitUserId) {
        if (explicitUserId != null) return explicitUserId;
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof mg.galaxam.sirh.securite.CustomUserDetails cud) {
                return cud.getIdUtilisateur();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
