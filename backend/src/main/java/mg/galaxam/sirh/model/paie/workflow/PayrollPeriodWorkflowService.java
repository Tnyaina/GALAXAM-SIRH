package mg.galaxam.sirh.model.paie.workflow;

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
import mg.galaxam.sirh.api.dto.common.ActionCapability;
import mg.galaxam.sirh.api.dto.common.BusinessDecision;
import mg.galaxam.sirh.api.dto.common.BusinessIssue;
import mg.galaxam.sirh.api.dto.common.DecisionStatus;
import mg.galaxam.sirh.api.dto.common.RuleResult;
import mg.galaxam.sirh.api.dto.paie.PayrollPeriodContextView;
import mg.galaxam.sirh.api.dto.paie.PayrollPeriodSummary;
import mg.galaxam.sirh.api.dto.paie.PayrollOutputView;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.model.dashboard.service.NotificationRhService;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.service.PayrollPopulationResolver;
import mg.galaxam.sirh.model.pointage.service.PointageSyntheseService;
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
                                "Sélectionnez explicitement une période de paie.", Map.of())), List.of(), List.of());
            }
            return new PayrollPeriodContextView(
                    targetDate,
                    toSummary(current),
                    toSummary(selected),
                    availableActions(selected.id(), selected.state(), user, connection),
                    contextIssues(selected.id(), selected.state(), connection),
                    List.of(), outputViews(selected, user, connection));
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

    private String targetState(String currentState, PayrollPeriodCommand command) {
        return switch (command) {
            case OPEN -> requireTransition(currentState, "BROUILLON", "EN_COURS", command);
            case SUBMIT -> requireTransition(currentState, "EN_COURS", "A_VALIDER", command);
            case APPROVE -> requireTransition(currentState, List.of("A_VALIDER", "TRANSMIS_DAF"), "VALIDEE", command);
            case REJECT -> requireTransition(currentState, List.of("A_VALIDER", "TRANSMIS_DAF"), "EN_COURS", command);
            case CLOSE -> requireTransition(currentState, "VALIDEE", "CLOTUREE", command);
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
                case CLOSE -> "VALIDEE".equals(state);
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
            return ActionCapability.permit();
        }
        if (!"VALIDEE".equals(state)) return ActionCapability.denied("POINTAGE_NOT_FROZEN");

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
        if (!"VALIDEE".equals(state)) {
            issues.add(new BusinessIssue("POINTAGE_NOT_FROZEN",
                    "Le pointage doit être validé et figé avant le calcul réel.", Map.of("periodId", periodId)));
        } else if (count(connection,
                "SELECT COUNT(*) FROM pointage_cumul_periode WHERE id_periode_paie = ? "
                        + "AND statut_snapshot IN ('GELE','CONSOMME')", periodId) == 0) {
            issues.add(new BusinessIssue("POINTAGE_NOT_FROZEN",
                    "Aucun snapshot de pointage figé n'est disponible.", Map.of("periodId", periodId)));
        }
        if ("VALIDEE".equals(state)) {
            int total = count(connection,
                    "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ?", periodId);
            int calculated = count(connection,
                    "SELECT COUNT(*) FROM bulletin_paie WHERE id_periode_paie = ? AND statut = 'CALCULE'", periodId);
            if (total == 0) {
                issues.add(new BusinessIssue("PAYROLL_NOT_CALCULATED",
                        "La paie doit être calculée avant validation.", Map.of("periodId", periodId)));
            } else if (calculated == 0) {
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
