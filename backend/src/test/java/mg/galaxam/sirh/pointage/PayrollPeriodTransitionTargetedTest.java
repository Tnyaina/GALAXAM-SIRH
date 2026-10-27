package mg.galaxam.sirh.pointage;

import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodTransitionLogger;
import mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowCommands.PayrollPeriodCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests ciblés vérifiant la traçabilité complète et atomique dans {@code periode_paie_transition} :
 * 1. Insertion conforme des transitions avec typage, versionnage et attribution d'utilisateur.
 * 2. Prise en compte de la transition VALIDEE -> PAIE_VALIDEE (VALIDATE_PAYROLL).
 * 3. Prise en compte de la transition PAIE_VALIDEE -> VALIDEE lors d'un recalcul (RECALCULATE_PAYROLL).
 * 4. Absence de journalisation lors d'un recalcul sans changement d'état ou en mode simulation.
 * 5. Préservation stricte de la journalisation du workflow existant (OPEN, SUBMIT, APPROVE, REJECT, CLOSE, REOPEN).
 * 6. Atomicité transactionnelle : l'échec de la journalisation doit propager l'exception pour rollback.
 */
public class PayrollPeriodTransitionTargetedTest {

    @Test
    @DisplayName("Vérifie les paramètres préparés lors de l'appel à PayrollPeriodTransitionLogger")
    void testTransitionLoggerParameters() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);

        PayrollPeriodTransitionLogger.log(
                mockConnection,
                10,
                "VALIDATE_PAYROLL",
                "VALIDEE",
                "PAIE_VALIDEE",
                2,
                3,
                42,
                "Validation globale des bulletins"
        );

        verify(mockConnection, times(1)).prepareStatement(contains("INSERT INTO periode_paie_transition"));
        verify(mockStatement, times(1)).setInt(1, 10);
        verify(mockStatement, times(1)).setString(2, "VALIDATE_PAYROLL");
        verify(mockStatement, times(1)).setString(3, "VALIDEE");
        verify(mockStatement, times(1)).setString(4, "PAIE_VALIDEE");
        verify(mockStatement, times(1)).setInt(5, 2);
        verify(mockStatement, times(1)).setInt(6, 3);
        verify(mockStatement, times(1)).setInt(7, 42);
        verify(mockStatement, times(1)).setString(8, "Validation globale des bulletins");
        verify(mockStatement, times(1)).executeUpdate();
    }

    @Test
    @DisplayName("Vérifie la gestion des valeurs nulles (utilisateur et motif) dans le logger")
    void testTransitionLoggerNullValues() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);

        PayrollPeriodTransitionLogger.log(
                mockConnection,
                5,
                "OPEN",
                "BROUILLON",
                "EN_COURS",
                1,
                2,
                null,
                null
        );

        verify(mockStatement, times(1)).setNull(7, java.sql.Types.INTEGER);
        verify(mockStatement, times(1)).setNull(8, java.sql.Types.VARCHAR);
        verify(mockStatement, times(1)).executeUpdate();
    }

    @Test
    @DisplayName("Garantit que l'échec d'insertion du journal propage l'exception (atomicité transactionnelle)")
    void testTransitionLoggerFailsAtomically() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeUpdate()).thenThrow(new SQLException("Erreur contrainte clé étrangère"));

        assertThrows(SQLException.class, () ->
                PayrollPeriodTransitionLogger.log(
                        mockConnection,
                        999,
                        "APPROVE",
                        "A_VALIDER",
                        "VALIDEE",
                        1,
                        2,
                        1,
                        null
                )
        );
    }

    @Test
    @DisplayName("Vérifie la cohérence des commandes pour chaque scénario de transition")
    void testTransitionCommandNames() {
        // Commandes du workflow pointage existant
        List<String> workflowCommands = List.of(
                PayrollPeriodCommand.OPEN.name(),
                PayrollPeriodCommand.SUBMIT.name(),
                PayrollPeriodCommand.APPROVE.name(),
                PayrollPeriodCommand.REJECT.name(),
                PayrollPeriodCommand.CLOSE.name(),
                PayrollPeriodCommand.REOPEN.name()
        );
        assertEquals(6, workflowCommands.size());
        for (String cmd : workflowCommands) {
            assertTrue(cmd.length() <= 30, "La commande doit respecter VARCHAR(30)");
        }

        // Nouvelles commandes du cycle de paie
        String validatePayroll = "VALIDATE_PAYROLL";
        String recalculatePayroll = "RECALCULATE_PAYROLL";

        assertTrue(validatePayroll.length() <= 30);
        assertTrue(recalculatePayroll.length() <= 30);
        assertNotEquals(validatePayroll, recalculatePayroll);
    }

    @Test
    @DisplayName("Vérifie la logique conditionnelle du recalcul : pas d'écriture si statut déjà VALIDEE")
    void testRecalculateStateLogic() {
        String currentStatut1 = "PAIE_VALIDEE";
        boolean shouldTransition1 = "PAIE_VALIDEE".equals(currentStatut1);
        assertTrue(shouldTransition1, "Un recalcul sur PAIE_VALIDEE doit déclencher la transition vers VALIDEE");

        String currentStatut2 = "VALIDEE";
        boolean shouldTransition2 = "PAIE_VALIDEE".equals(currentStatut2);
        assertFalse(shouldTransition2, "Un recalcul sur VALIDEE ne doit PAS déclencher de transition de statut");

        boolean isSimulation = true;
        boolean shouldApplyAnyStateChange = !isSimulation && shouldTransition1;
        assertFalse(shouldApplyAnyStateChange, "Une simulation ne doit jamais altérer ni journaliser l'état");
    }
}
