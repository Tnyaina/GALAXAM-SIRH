package mg.galaxam.sirh.paie.application.workflow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Composant partagé assurant la persistance atomique et immuable dans {@code periode_paie_transition}.
 * <p>
 * Centralise l'instruction SQL d'écriture du journal des transitions d'état de période
 * sans introduire de dépendance circulaire entre les services applicatifs.
 */
public final class PayrollPeriodTransitionLogger {

    private PayrollPeriodTransitionLogger() {}

    private static final String SQL_INSERT_TRANSITION =
            "INSERT INTO periode_paie_transition "
            + "(id_periode_paie, commande, statut_source, statut_cible, version_source, version_cible, "
            + "id_utilisateur, motif) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Enregistre une transition d'état de période de paie au sein de la transaction JDBC courante.
     *
     * @param connection     connexion JDBC active (autoCommit = false)
     * @param periodId       identifiant de la période de paie
     * @param command        nom explicite de la commande (ex: OPEN, SUBMIT, APPROVE, VALIDATE_PAYROLL, RECALCULATE_PAYROLL...)
     * @param sourceState    statut avant transition
     * @param targetState    statut après transition
     * @param sourceVersion  version optimiste avant transition
     * @param targetVersion  version optimiste après transition
     * @param userId         identifiant de l'utilisateur responsable (nullable)
     * @param motif          commentaire ou motif de la transition (nullable)
     * @throws SQLException  en cas d'échec SQL, pour provoquer l'annulation de la transaction englobante
     */
    public static void log(
            Connection connection,
            int periodId,
            String command,
            String sourceState,
            String targetState,
            int sourceVersion,
            int targetVersion,
            Integer userId,
            String motif) throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(SQL_INSERT_TRANSITION)) {
            statement.setInt(1, periodId);
            statement.setString(2, command);
            statement.setString(3, sourceState);
            statement.setString(4, targetState);
            statement.setInt(5, sourceVersion);
            statement.setInt(6, targetVersion);
            if (userId != null) {
                statement.setInt(7, userId);
            } else {
                statement.setNull(7, Types.INTEGER);
            }
            if (motif != null && !motif.isBlank()) {
                statement.setString(8, motif.trim());
            } else {
                statement.setNull(8, Types.VARCHAR);
            }
            statement.executeUpdate();
        }
    }
}
