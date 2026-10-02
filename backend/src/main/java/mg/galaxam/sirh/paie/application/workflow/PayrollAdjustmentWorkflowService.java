package mg.galaxam.sirh.paie.application.workflow;


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
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT p.id_periode_paie FROM periode_paie p "
                                    + "JOIN ref_statut_periode_paie s ON s.id_statut_periode = p.id_statut_periode "
                                    + "JOIN bulletin_paie b ON b.id_periode_paie = p.id_periode_paie "
                                    + "WHERE p.id_periode_paie = ? AND b.id_bulletin = ? "
                                    + "AND s.code = 'VALIDEE' AND b.statut = 'CALCULE' FOR UPDATE OF p, b")) {
                        ps.setInt(1, entity.getIdPeriodePaie());
                        ps.setInt(2, entity.getIdBulletin());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new SirhBusinessException(SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED,
                                        "La variable ne peut être annulée que sur un bulletin calculé non validé.");
                            }
                        }
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT owner_code FROM ref_type_mouvement_paie WHERE id_type_mouvement = ?")) {
                        ps.setInt(1, entity.getIdTypeMouvement());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next() || !"DAF_PAIE".equals(rs.getString(1))
                                    || !"VARIABLE_DAF".equals(entity.getProvenance())) {
                                throw new SirhBusinessException(SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED,
                                        "Seule une variable manuelle peut être annulée après imputation.");
                            }
                        }
                    }
                }

                // Exécution de la transition
                entity.setStatut(transition.targetStatut());
                if (command == PayrollAdjustmentCommand.APPROVE) {
                    entity.setApprouvePar(requestingUserId);
                    entity.setDateAccord(new java.sql.Date(System.currentTimeMillis()));
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
            String s = currentStatut != null ? currentStatut.toUpperCase() : "";
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
        String s = statut != null ? statut.toUpperCase() : "";
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
