package mg.galaxam.sirh.paie.application.workflow;

import mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowCommands.PayrollAdjustmentCommand;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.dto.ActionCapability;
import mg.galaxam.sirh.common.dto.BusinessDecision;
import mg.galaxam.sirh.common.dto.DecisionStatus;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.stereotype.Service;

/**
 * Owner canonique des transitions d'état des ajustements de paie.
 *
 * <p>Le client demande une commande ({@link PayrollAdjustmentCommand}) ;
 * ce service décide si la transition est possible selon la matrice
 * {@link TransitionMatrix} et l'exécute si autorisée.
 *
 * <p>Principe : COMMANDE → MATRICE → DÉCISION. Jamais : CLIENT_STATUT → APPLY_DIRECT.
 */
@Service
public class PayrollAdjustmentWorkflowService {

    private final DataSource dataSource;
    private final TransitionMatrix matrix;

    public PayrollAdjustmentWorkflowService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.matrix = new TransitionMatrix();
    }

    /** Projection of workflow commands for list rows; the transition matrix remains authoritative. */
    public Map<String, Boolean> describeActions(String statut, boolean canWrite, boolean paymentConfirmed,
            boolean imputed, boolean canCancelImputed, boolean noteDeFrais, boolean proofPresent) {
        String state = mg.galaxam.sirh.paie.domain.PayrollStatuses.canonicalAdjustment(statut);
        boolean approve = !matrix.apply(state, PayrollAdjustmentCommand.APPROVE).blocked()
                && (!noteDeFrais || proofPresent);
        boolean reject = !matrix.apply(state, PayrollAdjustmentCommand.REJECT).blocked();
        boolean cancel = !matrix.apply(state, PayrollAdjustmentCommand.CANCEL).blocked()
                && (!paymentConfirmed) && (!imputed || canCancelImputed);
        boolean delete = "EN_ATTENTE".equals(state) && !paymentConfirmed && !imputed;
        return Map.of("APPROVE", canWrite && approve, "REJECT", canWrite && reject,
                "CANCEL", canWrite && cancel, "DELETE", canWrite && delete);
    }

    /** Même critère pour projeter l'action et la revérifier sous verrou lors de l'exécution. */
    public boolean canCancelImputedVariable(Connection connection, int adjustmentId, int periodId, int bulletinId,
            boolean lock) throws Exception {
        String sql = "SELECT 1 FROM periode_paie p "
                + "JOIN ref_statut_periode_paie s ON s.id_statut_periode=p.id_statut_periode "
                + "JOIN bulletin_paie b ON b.id_periode_paie=p.id_periode_paie "
                + "JOIN paie_avance_acompte a ON a.id_bulletin=b.id_bulletin "
                + "JOIN ref_type_mouvement_paie t ON t.id_type_mouvement=a.id_type_mouvement "
                + "WHERE p.id_periode_paie=? AND b.id_bulletin=? AND a.id_avance=? "
                + "AND s.code='VALIDEE' AND b.statut='CALCULE' AND t.owner_code='DAF_PAIE' "
                + "AND a.provenance='VARIABLE_DAF'" + (lock ? " FOR UPDATE OF p, b" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, periodId); statement.setInt(2, bulletinId); statement.setInt(3, adjustmentId);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Exécute une commande sur un ajustement de paie.
     *
     * @param adjustmentId    identifiant de l'ajustement
     * @param command         commande métier demandée par le client
     * @param requestingUserId utilisateur demandant l'action
     * @param reason          motif optionnel (obligatoire pour REJECT)
     * @param expectedVersion version courante attendue (optimistic locking)
     * @return décision métier avec état cible et actions disponibles
     */
    public BusinessDecision execute(
            int adjustmentId,
            PayrollAdjustmentCommand command,
            Integer requestingUserId,
            String reason,
            Integer expectedVersion) throws Exception {
        return execute(adjustmentId, command, requestingUserId, reason, expectedVersion, null);
    }

    public BusinessDecision execute(
            int adjustmentId,
            PayrollAdjustmentCommand command,
            Integer requestingUserId,
            String reason,
            Integer expectedVersion,
            java.time.LocalDate dateAccord) throws Exception {

        if (command == PayrollAdjustmentCommand.REJECT && (reason == null || reason.isBlank())) {
            throw new SirhBusinessException(SirhErrorCode.PAYROLL_PERIOD_REASON_REQUIRED,
                    "Un motif est obligatoire pour rejeter un ajustement.");
        }
        if (expectedVersion == null || expectedVersion < 1) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "La version courante de l'ajustement est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieAvanceAcompte entity = PaieAvanceAcompte.trouverParId(adjustmentId, c);
                if (entity == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,
                            "Ajustement de paie introuvable.");
                }

                // Vérification de version (optimistic locking via updatedAt milliseconds)
                int dbVersion = deriveVersion(entity);
                if (dbVersion != expectedVersion) {
                    throw new SirhBusinessException(SirhErrorCode.PAYROLL_ADJUSTMENT_VERSION_CONFLICT,
                            "L'ajustement a été modifié (version " + dbVersion
                                    + " != " + expectedVersion + "). Rechargez les données.");
                }

                // Application de la matrice de transitions
                AdjustmentState transition = matrix.apply(entity.getStatut(), command);
                if (transition.blocked()) {
                    throw new SirhBusinessException(transition.errorCode(), transition.blockingMessage());
                }
                if (command == PayrollAdjustmentCommand.APPROVE) {
                    java.time.LocalDate dateDecision = dateAccord != null ? dateAccord : mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today();
                    if (dateDecision.isAfter(mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today())) {
                        throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                                "La date d'approbation ne peut pas être future.");
                    }
                    if (entity.getDateDemande() != null
                            && dateDecision.isBefore(entity.getDateDemande().toLocalDate())) {
                        throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                                "La date d'approbation ne peut pas précéder la date de demande.");
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT t.code, EXISTS(SELECT 1 FROM paie_mouvement_justificatif j "
                                    + "WHERE j.id_avance=a.id_avance) AS justificatif_present "
                                    + "FROM paie_avance_acompte a JOIN ref_type_mouvement_paie t "
                                    + "ON t.id_type_mouvement=a.id_type_mouvement WHERE a.id_avance=?")) {
                        ps.setInt(1, adjustmentId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next() && "NOTE_DE_FRAIS".equals(rs.getString("code"))
                                    && !rs.getBoolean("justificatif_present")) {
                                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                                        "Ajoutez un justificatif avant d’approuver la note de frais.");
                            }
                        }
                    }
                }
                if (command == PayrollAdjustmentCommand.CANCEL && entity.getVersementConfirmeAt() != null) {
                    throw new SirhBusinessException(SirhErrorCode.STATE_CONFLICT,
                            "Un acompte ou une avance déjà versé ne peut pas être annulé par cette commande.");
                }
                if (command == PayrollAdjustmentCommand.CANCEL && "IMPUTE_PAIE".equals(entity.getStatut())) {
                    if (reason == null || reason.isBlank()) {
                        throw new SirhBusinessException(SirhErrorCode.PAYROLL_PERIOD_REASON_REQUIRED,
                                "Un motif est obligatoire pour annuler une variable imputée.");
                    }
                    if (entity.getIdTypeMouvement() == null || entity.getIdBulletin() == null
                            || entity.getIdPeriodePaie() == null) {
                        throw new SirhBusinessException(SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED,
                                "Le type de cette variable ne permet pas son annulation après imputation.");
                    }
                    if (!canCancelImputedVariable(c, adjustmentId, entity.getIdPeriodePaie(), entity.getIdBulletin(), true)) {
                        throw new SirhBusinessException(SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED,
                                "La variable ne peut être annulée que sur un bulletin calculé non validé.");
                    }
                }

                // Exécution de la transition
                entity.setStatut(transition.targetStatut());
                if (command == PayrollAdjustmentCommand.APPROVE) {
                    entity.setApprouvePar(requestingUserId);
                    entity.setDateAccord(java.sql.Date.valueOf(
                            dateAccord != null ? dateAccord : mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today()));
                }
                if (reason != null && !reason.isBlank()) {
                    entity.setMotif((entity.getMotif() != null ? entity.getMotif() + " | " : "") + reason);
                }

                entity.update(c);
                AuditLog.audit(c, "COMMAND_" + command.name(), "paie_avance_acompte",
                        String.valueOf(adjustmentId), null, entity);
                c.commit();

                // Calcul des actions disponibles après transition
                Map<String, ActionCapability> nextActions = availableActionsFor(entity.getStatut());

                return new BusinessDecision(
                        adjustmentId,
                        entity.getStatut(),
                        DecisionStatus.ALLOWED,
                        "Ajustement " + command.name().toLowerCase() + " avec succès.",
                        List.of(),
                        List.of(),
                        List.of(),
                        nextActions,
                        deriveVersion(entity));
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    // =========================================================================
    // Matrice de transitions (testable indépendamment de la DB)
    // =========================================================================

    /**
     * Matrice de transitions des ajustements de paie.
     * Peut être instanciée directement dans les tests unitaires sans DB.
     */
    public static final class TransitionMatrix {

        /** Applique la commande depuis l'état courant et retourne l'état résultant. */
        public AdjustmentState apply(String currentStatut, PayrollAdjustmentCommand command) {
            String s = mg.galaxam.sirh.paie.domain.PayrollStatuses.canonicalAdjustment(currentStatut);
            return switch (command) {
                case APPROVE -> switch (s) {
                    case "EN_ATTENTE" -> AdjustmentState.allowed("APPROUVE");
                    case "APPROUVE"   -> AdjustmentState.blocked(
                            SirhErrorCode.PAYROLL_ADJUSTMENT_ALREADY_APPROVED,
                            "Cet ajustement est déjà approuvé.");
                    case "IMPUTE_PAIE" -> AdjustmentState.blocked(
                            SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED,
                            "Impossible de modifier un ajustement déjà imputé sur un bulletin.");
                    default -> AdjustmentState.blocked(
                            SirhErrorCode.PAYROLL_ADJUSTMENT_COMMAND_BLOCKED,
                            "APPROVE non autorisé depuis l'état : " + s);
                };
                case REJECT -> switch (s) {
                    case "EN_ATTENTE" -> AdjustmentState.allowed("REJETE");
                    case "IMPUTE_PAIE" -> AdjustmentState.blocked(
                            SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED,
                            "Impossible de rejeter un ajustement déjà imputé sur un bulletin.");
                    default -> AdjustmentState.blocked(
                            SirhErrorCode.PAYROLL_ADJUSTMENT_COMMAND_BLOCKED,
                            "REJECT non autorisé depuis l'état : " + s);
                };
                case CANCEL -> switch (s) {
                    case "EN_ATTENTE", "APPROUVE" -> AdjustmentState.allowed("ANNULE");
                    case "IMPUTE_PAIE" -> AdjustmentState.allowed("ANNULE");
                    default -> AdjustmentState.blocked(
                            SirhErrorCode.PAYROLL_ADJUSTMENT_COMMAND_BLOCKED,
                            "CANCEL non autorisé depuis l'état : " + s);
                };
            };
        }
    }

    /**
     * Résultat d'une transition dans la matrice.
     */
    public record AdjustmentState(
            boolean blocked,
            String targetStatut,
            SirhErrorCode errorCode,
            String blockingMessage) {

        static AdjustmentState allowed(String targetStatut) {
            return new AdjustmentState(false, targetStatut, null, null);
        }

        static AdjustmentState blocked(SirhErrorCode code, String message) {
            return new AdjustmentState(true, null, code, message);
        }
    }

    // =========================================================================
    // Helpers privés
    // =========================================================================

    /** Calcule un numéro de version à partir du timestamp de mise à jour (millisecondes). */
    private static int deriveVersion(PaieAvanceAcompte entity) {
        if (entity.getUpdatedAt() != null) {
            // Version = millisecondes depuis epoch % Integer.MAX_VALUE → entier stable
            // Note : on utilise les millisecondes (pas les secondes) pour éviter le décalage
            // floor (Java) vs ceil (PostgreSQL EXTRACT(EPOCH)) sur la troncature à la seconde.
            long ms = entity.getUpdatedAt().getTime();
            return (int) (ms % Integer.MAX_VALUE);
        }
        return 1;
    }

    private static Map<String, ActionCapability> availableActionsFor(String statut) {
        String s = mg.galaxam.sirh.paie.domain.PayrollStatuses.canonicalAdjustment(statut);
        return switch (s) {
            case "EN_ATTENTE" -> Map.of(
                    "APPROVE", ActionCapability.permit(),
                    "REJECT",  ActionCapability.permit(),
                    "CANCEL",  ActionCapability.permit());
            case "APPROUVE" -> Map.of(
                    "APPROVE", ActionCapability.denied("ALREADY_APPROVED"),
                    "CANCEL",  ActionCapability.permit());
            default -> Map.of();
        };
    }
}
