package mg.galaxam.sirh.paie.application.workflow;

import mg.galaxam.sirh.paie.persistence.*;
import mg.galaxam.sirh.paie.domain.engine.*;
import mg.galaxam.sirh.paie.domain.dto.*;
import mg.galaxam.sirh.paie.domain.dto.export.*;
import mg.galaxam.sirh.paie.application.*;
import mg.galaxam.sirh.paie.application.workflow.*;
import mg.galaxam.sirh.paie.application.export.*;
import mg.galaxam.sirh.paie.api.dto.*;
import mg.galaxam.sirh.paie.api.dto.stc.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.dto.ActionCapability;
import mg.galaxam.sirh.common.dto.BusinessDecision;
import mg.galaxam.sirh.common.dto.BusinessIssue;
import mg.galaxam.sirh.common.dto.DecisionStatus;
import mg.galaxam.sirh.common.dto.RuleResult;
import mg.galaxam.sirh.paie.api.dto.PayrollCampaignStep;
import mg.galaxam.sirh.paie.api.dto.PayrollPeriodContextView;
import mg.galaxam.sirh.paie.api.dto.PayrollPeriodSummary;
import mg.galaxam.sirh.paie.api.dto.PayrollOutputView;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.dashboard.application.NotificationRhService;
import mg.galaxam.sirh.paie.application.PayrollService;
import mg.galaxam.sirh.paie.application.PayrollPopulationResolver;
import mg.galaxam.sirh.pointage.application.PointageSyntheseService;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

/** Propriétaire unique des transitions d'état d'une période de paie. */
@Service
public class PayrollPeriodWorkflowService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Indian/Antananarivo");

    private final DataSource dataSource;
    private final PointageSyntheseService pointageSyntheseService;
    private final PayrollService payrollService;
    private final PayrollPopulationResolver payrollPopulationResolver;

    public PayrollPeriodWorkflowService(
            DataSource dataSource,
            PointageSyntheseService pointageSyntheseService,
            PayrollService payrollService,
            PayrollPopulationResolver payrollPopulationResolver) {
        this.dataSource = dataSource;
        this.pointageSyntheseService = pointageSyntheseService;
        this.payrollService = payrollService;
        this.payrollPopulationResolver = payrollPopulationResolver;
    }

    public BusinessDecision execute(
            int periodId,
            PayrollPeriodCommand command,
            PayrollPeriodCommandRequest request,
            PayrollPeriodUserContext user) throws Exception {
        if (request == null || request.getVersion() == null || request.getVersion() < 1) {
            throw failure(SirhErrorCode.VALIDATION_ERROR,
                    "La version courante de la période est obligatoire.", Map.of("field", "version"));
        }
        if (command == PayrollPeriodCommand.REJECT) {
            requireReason(request.getMotif(), 1, "Le motif de rejet est obligatoire.");
        } else if (command == PayrollPeriodCommand.REOPEN) {
            requireReason(request.getMotif(), 5,
                    "Le motif de réouverture est obligatoire (5 caractères minimum).");
        }
        requireAuthority(command, user);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                PeriodState current = loadForUpdate(periodId, connection);
                if (current.version() != request.getVersion()) {
                    throw failure(SirhErrorCode.PAYROLL_PERIOD_VERSION_CONFLICT,
                            "Conflit de modification : la période est en version " + current.version()
                                    + " et non " + request.getVersion() + ". Rechargez les données.",
                            Map.of("expectedVersion", request.getVersion(), "actualVersion", current.version()));
                }

                String targetState = targetState(current.state(), command);
                applyRulesAndEffects(current, command, request, user, connection);
                int targetVersion = updateState(current, command, targetState, request, user, connection);
                appendHistory(current, command, targetState, targetVersion, request, user, connection);
                notifyWorkflow(current, command, request, connection);
                connection.commit();

                return new BusinessDecision(
                        current.id(),
                        targetState,
                        DecisionStatus.ALLOWED,
                        successMessage(command),
                        List.of(RuleResult.pass("TRANSITION_ALLOWED")),
                        List.of(),
                        List.of(),
                        availableActions(current.id(), targetState, user, connection),
                        targetVersion);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public PayrollPeriodContextView describeContext(
            Integer selectedPeriodId, LocalDate businessDate, PayrollPeriodUserContext user) throws Exception {
        LocalDate targetDate = businessDate != null ? businessDate : LocalDate.now(BUSINESS_ZONE);
        try (Connection connection = dataSource.getConnection()) {
            PeriodState current = loadAtDate(targetDate, connection);
            PeriodState selected = selectedPeriodId != null
                    ? load(selectedPeriodId, connection, false)
                    : current;
            if (selected == null) {
                return new PayrollPeriodContextView(targetDate, null, null, Map.of(),
                        List.of(new BusinessIssue("PAYROLL_PERIOD_CONTEXT_REQUIRED",
                                "Sélectionnez explicitement une période de paie.", Map.of())),
                        List.of(), List.of(), null,
                        null, List.of(), false, null);
            }
            Map<String, ActionCapability> actions = availableActions(selected.id(), selected.state(), user, connection);
            // Sprint 2B : champs enrichis pour le stepper wizard frontend
            String currentStep = computeCurrentStep(selected, connection);
            List<PayrollCampaignStep> campaignSteps = computeCampaignSteps(selected, actions, connection);
            int recalcCount = computeRecalculationCount(selected.id(), selected.state(), connection);
            boolean recalcRequired = recalcCount > 0;
            String recalcReason = recalcRequired
                    ? recalcCount + " source(s) modifiée(s) depuis le dernier calcul de la campagne."
                    : null;
            return new PayrollPeriodContextView(
                    targetDate,
                    toSummary(current),
                    toSummary(selected),
                    actions,
                    contextIssues(selected.id(), selected.state(), connection),
                    List.of(),
                    outputViews(selected, user, connection),
                    // P1-02 : nextStep calculé par le backend selon statut + actions disponibles
                    computeNextStep(selected.id(), selected.state(), actions),
                    currentStep,
                    campaignSteps,
                    recalcRequired,
                    recalcReason);
        }
    }

    public void requirePayrollAction(
            int periodId, PayrollAction action, PayrollPeriodUserContext user) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            PeriodState period = load(periodId, connection, false);
            ActionCapability capability = payrollCapability(periodId, period.state(), action, user, connection);
            if (!capability.allowed()) {
                SirhErrorCode errorCode = capability.reasonCodes().contains("PERIOD_CLOSED")
                        ? SirhErrorCode.STATE_CONFLICT
                        : SirhErrorCode.PAYROLL_PERIOD_PREREQUISITE_BLOCKED;
                String message = capability.reasonCodes().contains("PERIOD_CLOSED")
                        ? "Le calcul est clôturé et strictement immuable."
                        : "Action " + action + " indisponible pour cette période.";
                throw failure(errorCode, message,
                        Map.of("periodId", periodId, "action", action.name(),
                                "reasonCodes", capability.reasonCodes()));
            }
        }
    }

    public Map<String, ActionCapability> describeActions(
            int periodId, String state, PayrollPeriodUserContext user, Connection connection) throws SQLException {
        return availableActions(periodId, state, user, connection);
    }

    private PeriodState loadForUpdate(int periodId, Connection connection) throws SQLException {
        return load(periodId, connection, true);
    }

    private PeriodState load(int periodId, Connection connection, boolean forUpdate) throws SQLException {
        String sql = "SELECT id_periode_paie, code, libelle, date_debut, date_fin, version, id_statut_periode "
                + "FROM periode_paie WHERE id_periode_paie = ?" + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, periodId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw failure(SirhErrorCode.PAYROLL_PERIOD_NOT_FOUND,
                            "Période de paie introuvable : " + periodId + ".", Map.of("periodId", periodId));
                }
                int statusId = result.getInt("id_statut_periode");
                return new PeriodState(
                        result.getInt("id_periode_paie"),
                        result.getString("code"),
                        result.getString("libelle"),
                        result.getDate("date_debut").toLocalDate(),
                        result.getDate("date_fin").toLocalDate(),
                        statusId,
                        loadStatusCode(statusId, connection),
                        result.getInt("version"));
            }
        }
    }

    private PeriodState loadAtDate(LocalDate date, Connection connection) throws SQLException {
        String sql = "SELECT p.id_periode_paie, p.code, p.libelle, p.date_debut, p.date_fin, p.version, "
                + "p.id_statut_periode, s.code AS state FROM periode_paie p "
                + "JOIN ref_statut_periode_paie s ON s.id_statut_periode = p.id_statut_periode "
                + "WHERE ? BETWEEN p.date_debut AND p.date_fin ORDER BY p.date_debut DESC LIMIT 2";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                PeriodState period = new PeriodState(
                        result.getInt("id_periode_paie"), result.getString("code"), result.getString("libelle"),
                        result.getDate("date_debut").toLocalDate(), result.getDate("date_fin").toLocalDate(),
                        result.getInt("id_statut_periode"), result.getString("state"), result.getInt("version"));
                if (result.next()) {
                    throw failure(SirhErrorCode.PAYROLL_PERIOD_PREREQUISITE_BLOCKED,
                            "Plusieurs périodes couvrent la date métier " + date + ".",
                            Map.of("businessDate", date, "reasonCode", "PAYROLL_PERIOD_CONTEXT_AMBIGUOUS"));
                }
                return period;
            }
        }
    }

    private String loadStatusCode(int statusId, Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT code FROM ref_statut_periode_paie WHERE id_statut_periode = ?")) {
            statement.setInt(1, statusId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return result.getString(1);
            }
        }
        throw new IllegalStateException("Statut de période inconnu : " + statusId + ".");
    }

    /**
     * P1-02 : Calcule l'étape de navigation suivante selon l'état de la période et les actions disponibles.
     * Le frontend utilise {@code nextStep.destination} sans reconstruire cette logique par rôle/statut.
     *
     * <p>Matrice :
     * <ul>
     *   <li>EDIT_POINTAGE disponible → /pointages?tab=saisie (RH en saisie)</li>
     *   <li>SUBMIT disponible → /pointages?tab=validation (Agent en cours de clôture)</li>
     *   <li>APPROVE disponible → validation Pointage</li>
     *   <li>Pointage figé → préparation Paie</li>
     *   <li>Paie validée / clôturée → sorties de Paie</li>
     *   <li>aucune action → null</li>
     * </ul>
     */
    private PayrollPeriodContextView.NextStep computeNextStep(
            int periodId, String state, Map<String, ActionCapability> actions) {
        if (actions == null || actions.isEmpty()) return null;
        if (actions.getOrDefault("EDIT_POINTAGE", ActionCapability.denied("NA")).allowed()) {
            return new PayrollPeriodContextView.NextStep("SAISIE", "/pointages?tab=saisie");
        }
        if (actions.getOrDefault("SUBMIT", ActionCapability.denied("NA")).allowed()) {
            return new PayrollPeriodContextView.NextStep("TRANSMISSION", "/pointages?tab=validation");
        }
        if (actions.getOrDefault("APPROVE", ActionCapability.denied("NA")).allowed()) {
            return new PayrollPeriodContextView.NextStep("VALIDATION_POINTAGE", "/pointages?tab=validation");
        }
        if (actions.getOrDefault("CLOSE", ActionCapability.denied("NA")).allowed()) {
            return new PayrollPeriodContextView.NextStep("CLOTURE", "/paie/periodes/" + periodId + "/sorties");
        }
        if ("VALIDEE".equals(state)) {
            return new PayrollPeriodContextView.NextStep("PREPARATION_PAIE", "/paie/periodes/" + periodId + "/preparation");
        }
        return new PayrollPeriodContextView.NextStep(
                "CLOTUREE".equals(state) || "PAIE_VALIDEE".equals(state) ? "SORTIES" : "POINTAGE",
                "CLOTUREE".equals(state) || "PAIE_VALIDEE".equals(state)
                        ? "/paie/periodes/" + periodId + "/sorties"
                        : "/pointages?tab=saisie");
    }

    /**
     * Sprint 2B : Calcule le code de l'étape courante dans le workflow de campagne.
     * <p>Le cycle commence au Pointage : le front peut donc toujours orienter
     * l'utilisateur vers l'étape suivante, même avant le gel DAF.
     */
    private String computeCurrentStep(PeriodState period, Connection connection) throws SQLException {
        String state = period.state();
        if (List.of("BROUILLON", "EN_COURS").contains(state)) return "POINTAGE";
        if (List.of("A_VALIDER", "TRANSMIS_DAF").contains(state)) return "VALIDATION_POINTAGE";
        if ("CLOTUREE".equals(state)) return "OUTPUTS";

        int total = count(connection, "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ?", period.id());
        if (total == 0) return "PREPARATION";

        if (computeRecalculationCount(period.id(), state, connection) > 0) return "CALCULATION";

        int valide = count(connection,
                "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND statut IN ('VALIDE','CLOTURE')", period.id());
        if (valide == total) return "VALIDATION";

        int calcule = count(connection,
                "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND statut = 'CALCULE'", period.id());
        return calcule > 0 ? "CONTROL" : "CALCULATION";
    }

    /**
     * Sprint 2B : Construit la liste ordonnée des étapes du workflow de campagne avec leur état.
     * Le frontend utilise cette liste directement pour le stepper — aucune logique côté client.
     */
    private List<PayrollCampaignStep> computeCampaignSteps(
            PeriodState period, Map<String, ActionCapability> actions, Connection connection) throws SQLException {
        String state = period.state();
        int periodId = period.id();

        boolean paieOk = List.of("VALIDEE", "PAIE_VALIDEE", "CLOTUREE").contains(state);
        boolean cloturee = "CLOTUREE".equals(state);

        int total = paieOk ? count(connection,
                "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ?", periodId) : 0;
        int calcule = paieOk && total > 0 ? count(connection,
                "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND statut = 'CALCULE'", periodId) : 0;
        int valide = paieOk && total > 0 ? count(connection,
                "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND statut IN ('VALIDE','CLOTURE')", periodId) : 0;
        boolean hasSnapshot = paieOk && count(connection,
                "SELECT COUNT(*) FROM pointage_cumul_periode WHERE id_periode_paie = ? "
                + "AND statut_snapshot IN ('GELE','CONSOMME')", periodId) > 0;

        boolean hasBulletins = total > 0;
        boolean allValidated = hasBulletins && valide == total;
        boolean someCalcOrValid = calcule > 0 || valide > 0;

        int recalcCount = computeRecalculationCount(periodId, state, connection);
        boolean recalcNeeded = recalcCount > 0;

        String base = "/paie/periodes/" + periodId;
        List<String> noIssues = List.of();
        List<String> frozenIssue = List.of("Pointage non figé — en attente de validation DAF.");

        List<PayrollCampaignStep> steps = new java.util.ArrayList<>();

        // Le workflow est un cycle unique : Pointage puis Paie. Ces deux étapes
        // ne sont pas des raccourcis UI, elles suivent le statut canonique de la période.
        boolean pointageComplete = List.of("VALIDEE", "PAIE_VALIDEE", "CLOTUREE").contains(state);
        boolean pointageInValidation = List.of("A_VALIDER", "TRANSMIS_DAF").contains(state);
        String pointageState = pointageComplete ? "COMPLETED"
                : pointageInValidation ? "COMPLETED"
                : "EN_COURS".equals(state) ? "CURRENT" : "AVAILABLE";
        steps.add(new PayrollCampaignStep("POINTAGE", "Pointage — Saisie", pointageState,
                "/pointages?tab=saisie", noIssues, noIssues));

        String pointageValidationState = pointageComplete ? "COMPLETED"
                : pointageInValidation ? "CURRENT"
                : "BROUILLON".equals(state) ? "BLOCKED" : "AVAILABLE";
        steps.add(new PayrollCampaignStep("VALIDATION_POINTAGE", "Validation Pointage", pointageValidationState,
                "/pointages?tab=validation",
                "BROUILLON".equals(state) ? List.of("Ouvrez d’abord la période à la saisie.") : noIssues,
                noIssues));

        // 1. PREPARATION — vérification des prérequis
        steps.add(new PayrollCampaignStep("PREPARATION", "Préparation",
                !paieOk ? "BLOCKED" : cloturee ? "LOCKED" : hasBulletins ? "COMPLETED" : "CURRENT",
                base + "/preparation",
                !paieOk ? frozenIssue : noIssues, noIssues));

        // 2. POPULATION — liste des salariés à payer
        steps.add(new PayrollCampaignStep("POPULATION", "Population",
                !paieOk ? "BLOCKED" : cloturee ? "LOCKED" : hasBulletins ? "COMPLETED" : "AVAILABLE",
                base + "/population",
                !paieOk ? frozenIssue : noIssues, noIssues));

        // 3. VARIABLES — saisie des éléments variables
        String varState;
        if (!paieOk) varState = "BLOCKED";
        else if (cloturee) varState = "LOCKED";
        else if (recalcNeeded && hasBulletins) varState = "WARNING";
        else if (hasBulletins) varState = "COMPLETED";
        else varState = "AVAILABLE";
        steps.add(new PayrollCampaignStep("VARIABLES", "Variables", varState, base + "/variables",
                !paieOk ? frozenIssue : noIssues,
                recalcNeeded && hasBulletins ? List.of(recalcCount + " source(s) modifiée(s) après calcul.") : noIssues));

        // 4. CALCULATION — lancement du moteur de calcul
        String calcState;
        if (!paieOk || !hasSnapshot) calcState = "BLOCKED";
        else if (cloturee) calcState = "LOCKED";
        else if (recalcNeeded && hasBulletins) calcState = "STALE";
        else if (hasBulletins) calcState = "COMPLETED";
        else calcState = "AVAILABLE";
        steps.add(new PayrollCampaignStep("CALCULATION", "Calcul", calcState, base + "/calcul",
                !paieOk ? frozenIssue : !hasSnapshot ? List.of("Aucun snapshot de pointage figé.") : noIssues,
                recalcNeeded && hasBulletins ? List.of("Recalcul recommandé.") : noIssues));

        // 5. CONTROL — revue des bulletins et anomalies
        String ctrlState;
        if (!paieOk || !hasBulletins) ctrlState = "BLOCKED";
        else if (cloturee) ctrlState = "LOCKED";
        else if (recalcNeeded) ctrlState = "STALE";
        else if (allValidated) ctrlState = "COMPLETED";
        else if (someCalcOrValid) ctrlState = "CURRENT";
        else ctrlState = "AVAILABLE";
        steps.add(new PayrollCampaignStep("CONTROL", "Contrôle", ctrlState, base + "/controle",
                !hasBulletins ? List.of("Aucun bulletin calculé.") : noIssues, noIssues));

        // 6. VALIDATION — approbation des bulletins
        String validState;
        if (!paieOk || !hasBulletins || recalcNeeded) validState = "BLOCKED";
        else if (cloturee) validState = "LOCKED";
        else if (allValidated) validState = "CURRENT";
        else if (someCalcOrValid) validState = "AVAILABLE";
        else validState = "BLOCKED";
        steps.add(new PayrollCampaignStep("VALIDATION", "Validation", validState, base + "/validation",
                !hasBulletins ? List.of("Aucun bulletin calculé.")
                        : recalcNeeded ? List.of("Le calcul est obsolète. Recalculez avant validation.") : noIssues,
                noIssues));

        // 7. OUTPUTS — bulletins de paie et exports réglementaires
        boolean outputAllowed = actions.getOrDefault("GENERATE_OUTPUT",
                ActionCapability.denied("NA")).allowed();
        String outState;
        if (!paieOk) outState = "BLOCKED";
        else if (cloturee) outState = "CURRENT";
        else if (outputAllowed) outState = "AVAILABLE";
        else outState = "BLOCKED";
        steps.add(new PayrollCampaignStep("OUTPUTS", "Bulletins & Exports", outState, base + "/sorties",
                !outputAllowed && !cloturee ? List.of("Bulletins non encore validés.") : noIssues, noIssues));

        return List.copyOf(steps);
    }

    /**
     * Compte les sources de calcul devenues plus récentes que les bulletins : variables
     * datées et snapshots Pointage versionnés.
     * Retourne 0 si le pointage n'est pas figé, si aucun bulletin n'existe, ou si aucune
     * modification n'est détectée.
     */
    private int computeRecalculationCount(int periodId, String state, Connection connection) throws SQLException {
        if (!List.of("VALIDEE", "PAIE_VALIDEE", "CLOTUREE").contains(state)) return 0;
        int bulletinCount = count(connection,
                "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? "
                + "AND statut IN ('CALCULE','VALIDE','CLOTURE')", periodId);
        if (bulletinCount == 0) return 0;

        String sql = "SELECT COUNT(*) FROM paie_avance_acompte a "
                + "WHERE a.id_periode_paie = ? "
                + "AND a.statut NOT IN ('ANNULE','REJETE') "
                + "AND a.updated_at > ("
                + "  SELECT COALESCE(MAX(b.updated_at), '1970-01-01'::timestamptz) "
                + "  FROM bulletin_paie b WHERE b.id_periode_paie = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, periodId);
            statement.setInt(2, periodId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                int changedVariables = result.getInt(1);
                String staleSnapshotsSql = "SELECT COUNT(*) FROM bulletin_paie b "
                        + "WHERE b.id_periode_paie = ? "
                        + "AND b.statut IN ('CALCULE','VALIDE','CLOTURE') "
                        + "AND b.id_snapshot_pointage IS NOT NULL "
                        + "AND NOT EXISTS ("
                        + "  SELECT 1 FROM pointage_cumul_periode p "
                        + "  WHERE p.id_employe = b.id_employe "
                        + "  AND p.id_periode_paie = b.id_periode_paie "
                        + "  AND p.statut_snapshot IN ('GELE','CONSOMME') "
                        + "  AND p.id_cumul = b.id_snapshot_pointage "
                        + "  AND p.version_snapshot = b.version_snapshot_pointage "
                        + "  AND p.version_snapshot = ("
                        + "    SELECT MAX(latest.version_snapshot) FROM pointage_cumul_periode latest "
                        + "    WHERE latest.id_employe = b.id_employe "
                        + "    AND latest.id_periode_paie = b.id_periode_paie "
                        + "    AND latest.statut_snapshot IN ('GELE','CONSOMME')"
                        + "  )"
                        + ")";
                try (PreparedStatement snapshots = connection.prepareStatement(staleSnapshotsSql)) {
                    snapshots.setInt(1, periodId);
                    try (ResultSet stale = snapshots.executeQuery()) {
                        stale.next();
                        return changedVariables + stale.getInt(1);
                    }
                }
            }
        }
    }

    private String targetState(String currentState, PayrollPeriodCommand command) {
        return switch (command) {
            case OPEN -> requireTransition(currentState, "BROUILLON", "EN_COURS", command);
            case SUBMIT -> requireTransition(currentState, "EN_COURS", "TRANSMIS_DAF", command);
            case APPROVE -> requireTransition(currentState, List.of("A_VALIDER", "TRANSMIS_DAF"), "VALIDEE", command);
            case REJECT -> requireTransition(currentState, List.of("A_VALIDER", "TRANSMIS_DAF"), "EN_COURS", command);
            case CLOSE -> requireTransition(currentState, List.of("VALIDEE", "PAIE_VALIDEE"), "CLOTUREE", command);
            case REOPEN -> requireTransition(currentState, "CLOTUREE", "EN_COURS", command);
        };
    }

    private String requireTransition(String current, String expected, String target, PayrollPeriodCommand command) {
        return requireTransition(current, List.of(expected), target, command);
    }

    private String requireTransition(
            String current, List<String> expected, String target, PayrollPeriodCommand command) {
        if (!expected.contains(current)) {
            throw failure(SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED,
                    "Commande " + command + " impossible depuis l'état " + current + ".",
                    Map.of("command", command.name(), "currentState", current, "allowedStates", expected));
        }
        return target;
    }

    private void applyRulesAndEffects(
            PeriodState period,
            PayrollPeriodCommand command,
            PayrollPeriodCommandRequest request,
            PayrollPeriodUserContext user,
            Connection connection) throws Exception {
        switch (command) {
            case OPEN -> {
                // La matrice porte toute la décision ; aucune donnée dérivée à produire.
            }
            case SUBMIT -> {
                ActionCapability capability = submissionCapability(period.id(), connection);
                if (!capability.allowed()) {
                    throw failure(SirhErrorCode.PAYROLL_PERIOD_PREREQUISITE_BLOCKED,
                            "Transmission impossible : les prérequis Pointage ne sont pas satisfaits.",
                            Map.of("periodId", period.id(), "reasonCodes", capability.reasonCodes()));
                }
                preparePointageSubmission(period.id(), request.getIdSite(), user.userId(), connection);
            }
            case APPROVE -> pointageSyntheseService.gelerPeriode(period.id(), user.userId(), connection);
            case REJECT -> requireReason(request.getMotif(), 1, "Le motif de rejet est obligatoire.");
            case CLOSE -> {
                if (period.endDate().isAfter(LocalDate.now(BUSINESS_ZONE))) {
                    throw failure(SirhErrorCode.PAYROLL_PERIOD_PREREQUISITE_BLOCKED,
                            "Clôture impossible avant la fin de la période (" + period.endDate() + ").",
                            Map.of("periodEndDate", period.endDate()));
                }
                requireValidatedPayroll(period.id(), connection);
                payrollService.cloturerDonneesPaie(period.id(), user.userId(), connection);
            }
            case REOPEN -> {
                requireReason(request.getMotif(), 5,
                        "Le motif de réouverture est obligatoire (5 caractères minimum).");
                payrollService.rouvrirDonneesPaie(period.id(), request.getMotif().trim(), user.userId(), connection);
                pointageSyntheseService.degelerPeriode(period.id(), connection);
            }
        }
    }

    private void preparePointageSubmission(
            int periodId, Integer siteId, Integer userId, Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM pointage_jour WHERE id_periode_paie = ?")) {
            statement.setInt(1, periodId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getInt(1) == 0) {
                    throw failure(SirhErrorCode.PAYROLL_PERIOD_PREREQUISITE_BLOCKED,
                            "Transmission impossible : aucun pointage n'a été saisi pour cette période.",
                            Map.of("periodId", periodId));
                }
            }
        }

        String blockingIssues = "SELECT COUNT(*) FROM pointage_jour p "
                + "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour "
                + "WHERE p.id_periode_paie = ? AND (st.code = 'ANOMALIE_SAISIE' "
                + "OR (st.code = 'ABSENCE_NON_JUSTIFIEE' "
                + "AND (p.motif_ajustement IS NULL OR TRIM(p.motif_ajustement) = '')))";
        try (PreparedStatement statement = connection.prepareStatement(blockingIssues)) {
            statement.setInt(1, periodId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                int count = result.getInt(1);
                if (count > 0) {
                    throw failure(SirhErrorCode.PAYROLL_PERIOD_PREREQUISITE_BLOCKED,
                            "Transmission impossible : " + count + " anomalie(s) bloquante(s) restent à régulariser.",
                            Map.of("periodId", periodId, "blockingCount", count));
                }
            }
        }

        pointageSyntheseService.calculerToutesSemainesPeriode(periodId, null, userId, connection);
    }

    private void requireValidatedPayroll(int periodId, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE statut = 'VALIDE') AS valides "
                + "FROM bulletin_paie WHERE id_periode_paie = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, periodId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                int total = result.getInt("total");
                int validated = result.getInt("valides");
                if (total == 0 || validated != total) {
                    throw failure(SirhErrorCode.PAYROLL_PERIOD_PREREQUISITE_BLOCKED,
                            "Clôture impossible : tous les bulletins de la période doivent être calculés et validés.",
                            Map.of("periodId", periodId, "totalBulletins", total,
                                    "validatedBulletins", validated));
                }
            }
        }
    }

    private int updateState(
            PeriodState current,
            PayrollPeriodCommand command,
            String targetState,
            PayrollPeriodCommandRequest request,
            PayrollPeriodUserContext user,
            Connection connection) throws SQLException {
        String sql = "UPDATE periode_paie SET "
                + "id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?), "
                + "version = version + 1, updated_at = CURRENT_TIMESTAMP, "
                + "date_transmission = CASE WHEN ? = 'SUBMIT' THEN CURRENT_TIMESTAMP ELSE date_transmission END, "
                + "transmis_par = CASE WHEN ? = 'SUBMIT' THEN ? ELSE transmis_par END, "
                + "date_validation = CASE WHEN ? = 'APPROVE' THEN CURRENT_TIMESTAMP "
                + "WHEN ? IN ('REJECT', 'REOPEN') THEN NULL ELSE date_validation END, "
                + "valide_par = CASE WHEN ? = 'APPROVE' THEN ? "
                + "WHEN ? IN ('REJECT', 'REOPEN') THEN NULL ELSE valide_par END, "
                + "motif_rejet = CASE WHEN ? = 'REJECT' THEN ? "
                + "WHEN ? IN ('OPEN', 'SUBMIT', 'APPROVE') THEN NULL ELSE motif_rejet END "
                + "WHERE id_periode_paie = ? AND version = ? AND id_statut_periode = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String commandName = command.name();
            statement.setString(1, targetState);
            statement.setString(2, commandName);
            statement.setString(3, commandName);
            setNullableInteger(statement, 4, user.userId());
            statement.setString(5, commandName);
            statement.setString(6, commandName);
            statement.setString(7, commandName);
            setNullableInteger(statement, 8, user.userId());
            statement.setString(9, commandName);
            statement.setString(10, commandName);
            statement.setString(11, request.getMotif() == null ? null : request.getMotif().trim());
            statement.setString(12, commandName);
            statement.setInt(13, current.id());
            statement.setInt(14, current.version());
            statement.setInt(15, current.statusId());
            if (statement.executeUpdate() != 1) {
                throw failure(SirhErrorCode.PAYROLL_PERIOD_VERSION_CONFLICT,
                        "Conflit de modification concurrente sur la période. Rechargez les données.",
                        Map.of("periodId", current.id(), "expectedVersion", current.version()));
            }
        }
        return current.version() + 1;
    }

    private void appendHistory(
            PeriodState current,
            PayrollPeriodCommand command,
            String targetState,
            int targetVersion,
            PayrollPeriodCommandRequest request,
            PayrollPeriodUserContext user,
            Connection connection) throws SQLException {
        String sql = "INSERT INTO periode_paie_transition "
                + "(id_periode_paie, commande, statut_source, statut_cible, version_source, version_cible, "
                + "id_utilisateur, motif) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, current.id());
            statement.setString(2, command.name());
            statement.setString(3, current.state());
            statement.setString(4, targetState);
            statement.setInt(5, current.version());
            statement.setInt(6, targetVersion);
            setNullableInteger(statement, 7, user.userId());
            statement.setString(8, request.getMotif() == null ? null : request.getMotif().trim());
            statement.executeUpdate();
        }
    }

    private void notifyWorkflow(
            PeriodState period,
            PayrollPeriodCommand command,
            PayrollPeriodCommandRequest request,
            Connection connection) throws SQLException {
        if (command == PayrollPeriodCommand.SUBMIT) {
            NotificationRhService.creerPourRole(
                    connection, "ROLE_ADMIN", "PERIODE_POINTAGE_A_VALIDER", "NORMALE",
                    "Période transmise pour validation - " + period.label(),
                    "La synthèse de pointage est prête pour contrôle.",
                    "/pointages?tab=validation", null);
        } else if (command == PayrollPeriodCommand.REJECT) {
            NotificationRhService.creerPourRole(
                    connection, "ROLE_AGENT_POINTAGE", "PERIODE_POINTAGE_REJETEE", "HAUTE",
                    "Période renvoyée pour correction - " + period.label(),
                    "Corrections demandées : " + request.getMotif().trim(),
                    "/pointages?tab=suivi", null);
        } else if (command == PayrollPeriodCommand.APPROVE) {
            NotificationRhService.creerPourRole(
                    connection, "ROLE_AGENT_POINTAGE", "PERIODE_POINTAGE_VALIDEE", "INFO",
                    "Période validée - " + period.label(),
                    "La synthèse de pointage a été figée et transmise à la paie.",
                    "/pointages?tab=validation", null);
        }
    }

    private Map<String, ActionCapability> availableActions(
            int periodId, String state, PayrollPeriodUserContext user, Connection connection) throws SQLException {
        Map<String, ActionCapability> actions = new java.util.LinkedHashMap<>();
        for (PayrollPeriodCommand command : PayrollPeriodCommand.values()) {
            boolean transitionAllowed = switch (command) {
                case OPEN -> "BROUILLON".equals(state);
                case SUBMIT -> "EN_COURS".equals(state);
                case APPROVE, REJECT -> List.of("A_VALIDER", "TRANSMIS_DAF").contains(state);
                case CLOSE -> List.of("VALIDEE", "PAIE_VALIDEE").contains(state);
                case REOPEN -> "CLOTUREE".equals(state);
            };
            boolean authorized = isAuthorized(command, user);
            ActionCapability capability = transitionAllowed && authorized
                    ? ActionCapability.permit()
                    : ActionCapability.denied(authorized ? "INVALID_STATE" : "FORBIDDEN");
            if (command == PayrollPeriodCommand.SUBMIT && capability.allowed()) {
                capability = submissionCapability(periodId, connection);
            }
            actions.put(command.name(), capability);
        }
        for (PayrollAction action : PayrollAction.values()) {
            actions.put(action.name(), payrollCapability(periodId, state, action, user, connection));
        }
        for (PointagePeriodAction action : PointagePeriodAction.values()) {
            boolean authorized = user.hasAny("POINTAGE_WRITE");
            actions.put(action.name(), authorized && "EN_COURS".equals(state)
                    ? ActionCapability.permit()
                    : ActionCapability.denied(authorized ? "INVALID_STATE" : "FORBIDDEN"));
        }
        return Map.copyOf(actions);
    }

    private ActionCapability payrollCapability(
            int periodId,
            String state,
            PayrollAction action,
            PayrollPeriodUserContext user,
            Connection connection) throws SQLException {
        boolean authorized = switch (action) {
            case SIMULATE, CALCULATE, ADD_VARIABLE -> user.hasAny("PAIE_WRITE");
            case VALIDATE -> user.hasAny("PAIE_VALIDATE");
            case GENERATE_OUTPUT -> user.hasAny("PAIE_READ");
        };
        if (!authorized) return ActionCapability.denied("FORBIDDEN");
        if (action == PayrollAction.SIMULATE) return ActionCapability.permit();
        if (action == PayrollAction.GENERATE_OUTPUT) {
            int total = count(connection,
                    "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ?", periodId);
            int validated = count(connection,
                    "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND statut IN ('VALIDE','CLOTURE')",
                    periodId);
            return total > 0 && total == validated
                    ? ActionCapability.permit()
                    : ActionCapability.denied("PAYROLL_NOT_VALIDATED");
        }
        if ("CLOTUREE".equals(state)) return ActionCapability.denied("PERIOD_CLOSED");
        if (action == PayrollAction.ADD_VARIABLE) {
            if (!List.of("EN_COURS", "VALIDEE").contains(state)) {
                return ActionCapability.denied("PAYROLL_VARIABLE_STATE_LOCKED");
            }
            int calculated = count(connection,
                    "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND statut IN ('CALCULE','VALIDE','CLOTURE')",
                    periodId);
            return calculated == 0
                    ? ActionCapability.permit()
                    : ActionCapability.denied("PAYROLL_ALREADY_CALCULATED");
        }
        if (!List.of("VALIDEE", "PAIE_VALIDEE").contains(state)) return ActionCapability.denied("POINTAGE_NOT_FROZEN");

        if (action == PayrollAction.CALCULATE) {
            boolean populationBlocked = payrollPopulationResolver.resolve(periodId, connection).members().stream()
                    .anyMatch(member -> "BLOCKED".equals(member.decision()));
            if (populationBlocked) return ActionCapability.denied("PAYROLL_POPULATION_BLOCKED");
            return count(connection,
                    "SELECT COUNT(*) FROM pointage_cumul_periode WHERE id_periode_paie = ? "
                            + "AND statut_snapshot IN ('GELE','CONSOMME')",
                    periodId) > 0
                    ? ActionCapability.permit()
                    : ActionCapability.denied("POINTAGE_NOT_FROZEN");
        }

        int calculated = count(connection,
                "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND statut = 'CALCULE'", periodId);
        if (action == PayrollAction.VALIDATE && calculated > 0
                && computeRecalculationCount(periodId, state, connection) > 0) {
            return ActionCapability.denied("PAYROLL_CALCULATION_STALE");
        }
        return calculated > 0
                ? ActionCapability.permit()
                : ActionCapability.denied("PAYROLL_NOT_CALCULATED");
    }

    private ActionCapability submissionCapability(int periodId, Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT date_fin FROM periode_paie WHERE id_periode_paie = ?")) {
            statement.setInt(1, periodId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getDate(1).toLocalDate().isAfter(LocalDate.now(BUSINESS_ZONE))) {
                    return ActionCapability.denied("PERIOD_NOT_ENDED");
                }
            }
        }
        if (count(connection, "SELECT COUNT(*) FROM pointage_jour WHERE id_periode_paie = ?", periodId) == 0) {
            return ActionCapability.denied("POINTAGE_EMPTY");
        }
        String blockingSql = "SELECT COUNT(*) FROM pointage_jour p "
                + "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour "
                + "WHERE p.id_periode_paie = ? AND (st.code = 'ANOMALIE_SAISIE' "
                + "OR (st.code = 'ABSENCE_NON_JUSTIFIEE' "
                + "AND (p.motif_ajustement IS NULL OR TRIM(p.motif_ajustement) = '')))";
        return count(connection, blockingSql, periodId) == 0
                ? ActionCapability.permit()
                : ActionCapability.denied("BLOCKING_ANOMALIES");
    }

    private List<BusinessIssue> contextIssues(int periodId, String state, Connection connection) throws SQLException {
        List<BusinessIssue> issues = new java.util.ArrayList<>();
        if (!List.of("VALIDEE", "PAIE_VALIDEE", "CLOTUREE").contains(state)) {
            issues.add(new BusinessIssue("POINTAGE_NOT_FROZEN",
                    "Le pointage doit être validé et figé avant le calcul réel.", Map.of("periodId", periodId)));
        } else if (count(connection,
                "SELECT COUNT(*) FROM pointage_cumul_periode WHERE id_periode_paie = ? "
                        + "AND statut_snapshot IN ('GELE','CONSOMME')", periodId) == 0) {
            issues.add(new BusinessIssue("POINTAGE_NOT_FROZEN",
                    "Aucun snapshot de pointage figé n'est disponible.", Map.of("periodId", periodId)));
        }
        if (List.of("VALIDEE", "PAIE_VALIDEE").contains(state)) {
            int total = count(connection,
                    "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ?", periodId);
            int calculated = count(connection,
                    "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND statut = 'CALCULE'", periodId);
            if (total == 0) {
                issues.add(new BusinessIssue("PAYROLL_NOT_CALCULATED",
                        "La paie doit être calculée avant validation.", Map.of("periodId", periodId)));
            } else if (calculated == 0 && !"PAIE_VALIDEE".equals(state)) {
                issues.add(new BusinessIssue("PAYROLL_ALREADY_VALIDATED",
                        "Aucun bulletin calculé ne reste à valider.", Map.of("periodId", periodId)));
            }
        }
        return List.copyOf(issues);
    }

    private int count(Connection connection, String sql, int periodId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, periodId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private List<PayrollOutputView> outputViews(
            PeriodState period, PayrollPeriodUserContext user, Connection connection) throws SQLException {
        ActionCapability capability = payrollCapability(
                period.id(), period.state(), PayrollAction.GENERATE_OUTPUT, user, connection);
        String status = capability.allowed() ? "AVAILABLE" : "BLOCKED";
        String checksum = payrollSourceChecksum(period, connection);
        return List.of("PAYSLIPS", "BANK_TRANSFER", "SAFI", "ISI", "DNTS", "HEALTH").stream()
                .map(code -> new PayrollOutputView(
                        code, status, period.version(), checksum, period.code()))
                .toList();
    }

    private String payrollSourceChecksum(PeriodState period, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*), COALESCE(SUM(total_gains_bruts), 0), "
                + "COALESCE(SUM(total_cotisations_salariales), 0), COALESCE(SUM(irsa_net), 0), "
                + "COALESCE(SUM(net_a_payer), 0) FROM bulletin_paie WHERE id_periode_paie = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, period.id());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                String source = period.id() + "|" + period.version() + "|" + result.getInt(1)
                        + "|" + result.getBigDecimal(2) + "|" + result.getBigDecimal(3)
                        + "|" + result.getBigDecimal(4) + "|" + result.getBigDecimal(5);
                try {
                    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(source.getBytes(StandardCharsets.UTF_8)));
                } catch (NoSuchAlgorithmException exception) {
                    throw new IllegalStateException("SHA-256 indisponible.", exception);
                }
            }
        }
    }

    private PayrollPeriodSummary toSummary(PeriodState period) {
        if (period == null) return null;
        return new PayrollPeriodSummary(period.id(), period.code(), period.label(), period.startDate(),
                period.endDate(), period.state(), period.version());
    }

    private void requireAuthority(PayrollPeriodCommand command, PayrollPeriodUserContext user) {
        if (!isAuthorized(command, user)) {
            throw new AccessDeniedException("Commande " + command + " non autorisée pour cet utilisateur.");
        }
    }

    private boolean isAuthorized(PayrollPeriodCommand command, PayrollPeriodUserContext user) {
        return switch (command) {
            case OPEN -> user.hasAny("PAIE_WRITE", "PAIE_VALIDATE", "POINTAGE_VALIDATE");
            case SUBMIT -> user.hasAny("POINTAGE_WRITE", "POINTAGE_VALIDATE");
            case APPROVE, REJECT -> user.hasAny("POINTAGE_VALIDATE");
            case CLOSE -> user.hasAny("PAIE_VALIDATE");
            case REOPEN -> user.authorities().contains("ROLE_ADMIN");
        };
    }

    private void requireReason(String reason, int minimumLength, String message) {
        if (reason == null || reason.trim().length() < minimumLength) {
            throw failure(SirhErrorCode.PAYROLL_PERIOD_REASON_REQUIRED, message,
                    Map.of("minimumLength", minimumLength));
        }
    }

    private SirhBusinessException failure(
            SirhErrorCode code, String message, Map<String, Object> details) {
        return new SirhBusinessException(code, message, details,
                List.of(new BusinessIssue(code.name(), message, details)));
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER);
        else statement.setInt(index, value);
    }

    private String successMessage(PayrollPeriodCommand command) {
        return switch (command) {
            case OPEN -> "Période ouverte à la saisie.";
            case SUBMIT -> "Période transmise pour validation.";
            case APPROVE -> "Pointage validé et figé pour la paie.";
            case REJECT -> "Période renvoyée pour correction.";
            case CLOSE -> "Période de paie clôturée.";
            case REOPEN -> "Période réouverte explicitement.";
        };
    }

    private record PeriodState(
            int id,
            String code,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            int statusId,
            String state,
            int version) {
    }
}
