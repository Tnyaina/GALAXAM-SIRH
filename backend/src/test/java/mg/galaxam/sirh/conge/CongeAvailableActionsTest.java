package mg.galaxam.sirh.conge;

import mg.galaxam.sirh.common.dto.ActionCapability;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.persistence.VDemandeCongeCpl;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CongeAvailableActionsTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeService congeService;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
        }
    }

    @Test
    @DisplayName("26. Matrice availableActions : BROUILLON, EN_ATTENTE, VALIDE, REFUSE, ANNULE")
    void testMatriceAvailableActionsSelonStatutEtRole() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            LocalDate debutFutur = LocalDate.now().plusDays(10);
            LocalDate finFutur = LocalDate.now().plusDays(12);

            // 1. Statut BROUILLON avec droits ADMIN (canWrite=true, canValidate=true)
            VDemandeCongeCpl dBr = new VDemandeCongeCpl();
            dBr.setCodeStatutDemande("BROUILLON");
            dBr.setDateDebut(Date.valueOf(debutFutur));
            dBr.setDateFin(Date.valueOf(finFutur));
            congeService.appliquerActionsDisponibles(dBr, true, true, c);

            Map<String, ActionCapability> actBr = dBr.getAvailableActions();
            assertNotNull(actBr);
            assertTrue(actBr.get("EDIT").allowed());
            assertTrue(actBr.get("SUBMIT").allowed());
            assertFalse(actBr.get("VALIDATE").allowed(), "Une demande brouillon ne peut pas être validée directement");
            assertTrue(actBr.get("VALIDATE").reasonCodes().contains("INVALID_STATE"));
            assertTrue(actBr.get("CANCEL").allowed());

            // 2. Statut EN_ATTENTE avec droits DAF/ADMIN
            VDemandeCongeCpl dAtt = new VDemandeCongeCpl();
            dAtt.setCodeStatutDemande("EN_ATTENTE");
            dAtt.setDateDebut(Date.valueOf(debutFutur));
            dAtt.setDateFin(Date.valueOf(finFutur));
            congeService.appliquerActionsDisponibles(dAtt, true, true, c);

            Map<String, ActionCapability> actAtt = dAtt.getAvailableActions();
            assertTrue(actAtt.get("VALIDATE").allowed());
            assertTrue(actAtt.get("REJECT").allowed());
            assertFalse(actAtt.get("SUBMIT").allowed());
            assertTrue(actAtt.get("CANCEL").allowed());

            // 3. Statut EN_ATTENTE sans droits de validation (collaborateur lambda canValidate=false)
            congeService.appliquerActionsDisponibles(dAtt, true, false, c);
            Map<String, ActionCapability> actAttUser = dAtt.getAvailableActions();
            assertFalse(actAttUser.get("VALIDATE").allowed());
            assertTrue(actAttUser.get("VALIDATE").reasonCodes().contains("FORBIDDEN"));
            assertFalse(actAttUser.get("REJECT").allowed());
            assertTrue(actAttUser.get("REJECT").reasonCodes().contains("FORBIDDEN"));

            // 4. Statut VALIDE (date future)
            VDemandeCongeCpl dVal = new VDemandeCongeCpl();
            dVal.setCodeStatutDemande("VALIDE");
            dVal.setDateDebut(Date.valueOf(debutFutur));
            dVal.setDateFin(Date.valueOf(finFutur));
            congeService.appliquerActionsDisponibles(dVal, true, true, c);

            Map<String, ActionCapability> actVal = dVal.getAvailableActions();
            assertFalse(actVal.get("EDIT").allowed());
            assertFalse(actVal.get("VALIDATE").allowed());
            assertTrue(actVal.get("CANCEL").allowed());
            assertTrue(actVal.get("MODIFY_DATES").allowed(), "Modification autorisée si congé futur");

            // 5. Statut VALIDE (date passée / commencée)
            VDemandeCongeCpl dValPassee = new VDemandeCongeCpl();
            dValPassee.setCodeStatutDemande("VALIDE");
            dValPassee.setDateDebut(Date.valueOf(LocalDate.now().minusDays(3)));
            dValPassee.setDateFin(Date.valueOf(LocalDate.now().plusDays(2)));
            congeService.appliquerActionsDisponibles(dValPassee, true, true, c);

            Map<String, ActionCapability> actValPassee = dValPassee.getAvailableActions();
            assertFalse(actValPassee.get("MODIFY_DATES").allowed());
            assertTrue(actValPassee.get("MODIFY_DATES").reasonCodes().contains("LEAVE_ALREADY_STARTED"));

            // 6. Statut ANNULE ou REFUSE
            VDemandeCongeCpl dAnn = new VDemandeCongeCpl();
            dAnn.setCodeStatutDemande("ANNULE");
            dAnn.setDateDebut(Date.valueOf(debutFutur));
            dAnn.setDateFin(Date.valueOf(finFutur));
            congeService.appliquerActionsDisponibles(dAnn, true, true, c);

            Map<String, ActionCapability> actAnn = dAnn.getAvailableActions();
            assertFalse(actAnn.get("EDIT").allowed());
            assertFalse(actAnn.get("SUBMIT").allowed());
            assertFalse(actAnn.get("VALIDATE").allowed());
            assertFalse(actAnn.get("CANCEL").allowed());
            assertFalse(actAnn.get("MODIFY_DATES").allowed());

            // 7. Blocage strict si Période de paie clôturée
            CongeTestFixtureHelper.createPeriodePaie(c, "2026-FUTUR", debutFutur, finFutur, "CLOTUREE");
            congeService.appliquerActionsDisponibles(dAtt, true, true, c);
            Map<String, ActionCapability> actClosed = dAtt.getAvailableActions();
            assertFalse(actClosed.get("VALIDATE").allowed());
            assertTrue(actClosed.get("VALIDATE").reasonCodes().contains("PAYROLL_PERIOD_CLOSED"));
            assertFalse(actClosed.get("CANCEL").allowed());
            assertTrue(actClosed.get("CANCEL").reasonCodes().contains("PAYROLL_PERIOD_CLOSED"));
        }
    }
}
