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
import java.sql.Date;
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
            assertEquals(4, wb.getNumberOfSheets(), "Le classeur doit contenir la ventilation des droits.");
            assertEquals("État congés salarié", wb.getSheetName(0));
            assertEquals("Mouvements", wb.getSheetName(1));
            assertEquals("README", wb.getSheetName(2));
            assertEquals("Origine des droits", wb.getSheetName(3));
            assertEquals("Valeur du stock après mvt (Ar)",
                    wb.getSheet("Mouvements").getRow(2).getCell(8).getStringCellValue());
            assertTrue(wb.getSheet("README").getRow(9).getCell(1).getStringCellValue()
                    .contains("FIFO"));

            var sheet1 = wb.getSheetAt(0);
            var rowMatricule = sheet1.getRow(2); // Row 3
            assertNotNull(rowMatricule);
            assertTrue(rowMatricule.getCell(0).getStringCellValue().contains("EMP-EXP-001"),
                    "A3=" + rowMatricule.getCell(0).getStringCellValue());

            // Ligne TOTAL = index 18 (ligne 19 Excel) dans le nouveau canevas
            var rowTotaux = sheet1.getRow(18);
            assertNotNull(rowTotaux);
            assertEquals("TOTAL ENREGISTRÉ / SITUATION", rowTotaux.getCell(0).getStringCellValue());
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
            assertTrue(rowMatricule.getCell(0).getStringCellValue().contains("EMP-EXP-001"),
                    "Valeur de row 2 cell 0 : '" + rowMatricule.getCell(0).getStringCellValue() + "'");
        }
    }

    @Test
    @DisplayName("L'état individuel restitue le solde du ledger, les ajustements et le taux historisé")
    void testSoldeEtTauxHistoriques() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (var ps = c.prepareStatement(
                    "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire, valeur_source) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, employeId);
                ps.setString(5, "Test d'export");
                ps.setString(2, "ACQUISITION");
                ps.setBigDecimal(6, new BigDecimal("22500"));
                ps.setBigDecimal(3, new BigDecimal("2.50"));
                ps.setDate(4, Date.valueOf("2026-01-15"));
                ps.executeUpdate();
                ps.setString(2, "PRISE");
                ps.setBigDecimal(6, null);
                ps.setBigDecimal(3, new BigDecimal("-1.00"));
                ps.setDate(4, Date.valueOf("2026-01-20"));
                ps.executeUpdate();
                ps.setString(2, "AJUSTEMENT");
                ps.setBigDecimal(6, new BigDecimal("5000"));
                ps.setBigDecimal(3, new BigDecimal("0.50"));
                ps.setDate(4, Date.valueOf("2026-02-10"));
                ps.executeUpdate();
            }
            try (var ps = c.prepareStatement(
                    "INSERT INTO conge_taux_applicable (id_employe, taux_journalier, date_effet, date_fin, source_provenance) VALUES (?, ?, ?, ?, 'INITIALISATION_BASE_30')")) {
                ps.setInt(1, employeId);
                ps.setBigDecimal(2, new BigDecimal("9000"));
                ps.setDate(3, Date.valueOf("2026-01-01"));
                ps.setDate(4, Date.valueOf("2026-02-01"));
                ps.executeUpdate();
                ps.setBigDecimal(2, new BigDecimal("10000"));
                ps.setDate(3, Date.valueOf("2026-02-01"));
                ps.setDate(4, null);
                ps.executeUpdate();
            }
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                exportService.genererEtatCongesIndividuel(employeId, idPeriodeReference)))) {
            var sheet = wb.getSheetAt(0);
            // Nouveau mapping colonnes (après insertion col C=2, décalage +1 partout)
            // Ligne 7 (index 6) = mois 1, Ligne 8 (index 7) = mois 2
            // Col P(15) = solde disponible (j), Col Q(16) = valeur stock FIFO
            // Col G(6)  = TCONGE applicable, Col H(7) = indemnité congé
            assertEquals(1.5, sheet.getRow(6).getCell(15).getNumericCellValue(),
                    "Solde dispo mois 1 (col P=15) doit être 1.5");
            assertEquals(2.0, sheet.getRow(7).getCell(15).getNumericCellValue(),
                    "Solde dispo mois 2 (col P=15) doit être 2.0");
            assertEquals(10000, sheet.getRow(7).getCell(6).getNumericCellValue(),
                    "TCONGE mois 2 (col G=6) doit être 10000");
            assertEquals(18500, sheet.getRow(7).getCell(16).getNumericCellValue(),
                    "Valeur stock FIFO mois 2 (col Q=16) doit être 18500 (1.5×10000+0.5×5000+0.5×10000)");
            // Col H(7) = indemnité congé — BLANK si aucun bulletin de paie
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, sheet.getRow(6).getCell(7).getCellType(),
                    "Indemnité congé (col H=7) doit être vide si pas de bulletin");
        }
    }

    @Test
    @DisplayName("Une période de référence d'un autre salarié ne peut pas alimenter cet état")
    void testPeriodeAppartientEmploye() {
        assertThrows(IllegalStateException.class,
                () -> exportService.genererEtatCongesIndividuel(employeId + 1, idPeriodeReference));
    }
}
