package mg.galaxam.sirh.conge;

import mg.galaxam.sirh.conge.application.CongeAnnuelExcelExportService;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CongeAnnuelExcelExportServiceTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeAnnuelExcelExportService exportService;

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    private int employeId;
    private int idPeriodeReference;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
            int adminUserId = CongeTestFixtureHelper.createAdminUser(c, "admin.export@galaxam.mg");
            employeId = CongeTestFixtureHelper.createEmploye(c, "EMP-EXP-001", "RAZAFY", "Tojo");
            int contratId = CongeTestFixtureHelper.createContratActif(c, employeId, LocalDate.of(2026, 1, 1));
            CongeTestFixtureHelper.createAffectation(c, employeId, contratId, LocalDate.of(2026, 1, 1), new BigDecimal("1500000.00"));

            // Créer une période de référence 2026
            idPeriodeReference = congeTauxApplicableService.creerPeriodeReference(
                    employeId,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 12, 31),
                    adminUserId,
                    c
            );
        }
    }

    @Test
    @DisplayName("Génération officielle de l'état des congés individuel annuel Excel à partir du modèle")
    void testGenererEtatCongesIndividuel() throws Exception {
        byte[] excelBytes = exportService.genererEtatCongesIndividuel(employeId, idPeriodeReference);

        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 5000, "Le fichier Excel généré doit être supérieur à 5 Ko");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            assertEquals(3, wb.getNumberOfSheets(), "Le classeur doit contenir 3 feuilles (État, Mouvements, README)");
            assertEquals("État congés salarié", wb.getSheetName(0));
            assertEquals("Mouvements", wb.getSheetName(1));
            assertEquals("README", wb.getSheetName(2));

            var sheet1 = wb.getSheetAt(0);
            var rowMatricule = sheet1.getRow(2); // Row 3
            assertNotNull(rowMatricule);
            assertTrue(rowMatricule.getCell(0).getStringCellValue().contains("EMP-EXP-001"),
                    "La cellule A3 doit mentionner le matricule de l'employé");

            var rowTotaux = sheet1.getRow(19); // Row 20
            assertNotNull(rowTotaux);
            assertEquals("TOTAL / SITUATION", rowTotaux.getCell(0).getStringCellValue());
        }

        String filename = exportService.obtenirNomFichierExport(employeId, idPeriodeReference);
        assertTrue(filename.startsWith("ETAT_CONGES_EMP-EXP-001_2026-01-01_2026-12-31"));
    }

    @Test
    @DisplayName("Export automatique sur la période de référence active par défaut")
    void testExportPeriodeParDefaut() throws Exception {
        byte[] excelBytes = exportService.genererEtatCongesIndividuel(employeId, null);
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 5000);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            var sheet1 = wb.getSheetAt(0);
            var rowMatricule = sheet1.getRow(2);
            assertTrue(rowMatricule.getCell(0).getStringCellValue().contains("EMP-EXP-001"));
        }
    }
}
