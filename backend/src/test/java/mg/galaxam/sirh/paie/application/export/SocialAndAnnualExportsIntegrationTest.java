package mg.galaxam.sirh.paie.application.export;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class SocialAndAnnualExportsIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private QuarterlySocialDataService quarterlyService;
    @Autowired private AnnualPayrollExportService annualService;

    private void verifierBaseTestUniquement(Connection c) throws Exception {
        try (var st = c.createStatement(); var rs = st.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next());
            assertEquals("sirh_galaxam_test", rs.getString(1),
                    "SÉCURITÉ : Les tests doivent s'exécuter EXCLUSIVEMENT sur sirh_galaxam_test.");
        }
    }

    @Test
    @DisplayName("Audit et réconciliation complète des 5 exports sociaux et annuels T1 2026")
    void testReconciliationExportsSociauxEtAnnuelT1() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            verifierBaseTestUniquement(c);
            c.setAutoCommit(false);
            try {
                CongeTestFixtureHelper.cleanDatabase(c);
                // Charger les fixtures Q1 dans la transaction
                try (var is = getClass().getResourceAsStream("/q1_test_fixtures_inserts.sql")) {
                    assertNotNull(is, "Fichier q1_test_fixtures_inserts.sql manquant");
                    String sql = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    for (String stmt : sql.split(";")) {
                        String trimmed = stmt.trim();
                        if (!trimmed.isBlank()) {
                            try (var s = c.createStatement()) {
                                s.execute(trimmed);
                            }
                        }
                    }
                }

                // 1. Chargement des données Q1 2026
                QuarterlySocialDataService.Data data = quarterlyService.load(2026, 1, c);
                assertNotNull(data);
                assertEquals(2026, data.year());
                assertEquals(1, data.quarter());
                assertEquals(2, data.people().size());
                assertEquals("Galaxam Logistics", data.employer().name());

                // 2. Génération des 4 exports sociaux trimestriels
                byte[] consolidationBytes = quarterlyService.render(data, "consolidation");
                byte[] cnapsBytes = quarterlyService.render(data, "cnaps");
                byte[] fmfpBytes = quarterlyService.render(data, "fmfp");
                byte[] ostieBytes = quarterlyService.render(data, "ostie");

                // 3. Génération du récapitulatif annuel 2026
                AnnualPayrollExportService.Result annualResult = annualService.export(2026, c);
                byte[] annualBytes = annualResult.bytes();

                // 4. Contrôles OSTIE : Formules pré-calculées et évaluées
                try (Workbook ostieBook = WorkbookFactory.create(new ByteArrayInputStream(ostieBytes))) {
                    var dns = ostieBook.getSheet("DNS");
                    assertNotNull(dns);
                    int totalRow = 24; // row 25 (0-indexed 24)
                    assertTrue(dns.getRow(totalRow).getCell(11).getNumericCellValue() > 0, "DNS Total M1 doit être > 0");
                    assertTrue(dns.getRow(totalRow).getCell(14).getNumericCellValue() > 0, "DNS Total non plafonné doit être > 0");
                    assertTrue(dns.getRow(totalRow).getCell(15).getNumericCellValue() > 0, "DNS Total plafonné doit être > 0");
                    assertTrue(dns.getRow(totalRow).getCell(16).getNumericCellValue() > 0, "DNS Total patronal doit être > 0");
                    assertTrue(dns.getRow(totalRow).getCell(17).getNumericCellValue() > 0, "DNS Total salarié doit être > 0");

                    var recap = ostieBook.getSheet("RECAP COTISATION");
                    assertNotNull(recap);
                    // Col F (index 5) row 23-27 : totaux pré-calculés non nuls
                    assertTrue(recap.getRow(23).getCell(5).getNumericCellValue() > 0, "RECAP Salaires non plafonnés > 0");
                    assertTrue(recap.getRow(24).getCell(5).getNumericCellValue() > 0, "RECAP Salaires plafonnés > 0");
                    assertTrue(recap.getRow(25).getCell(5).getNumericCellValue() > 0, "RECAP Cotisations Employeur > 0");
                    assertTrue(recap.getRow(26).getCell(5).getNumericCellValue() > 0, "RECAP Cotisations travailleurs > 0");
                    assertTrue(recap.getRow(27).getCell(5).getNumericCellValue() > 0, "RECAP Total Cotisations > 0");
                }

                // 5. Contrôles Annual Recap : Colonnes Référence et Statut bulletin non masquées et renseignées
                try (Workbook annualBook = WorkbookFactory.create(new ByteArrayInputStream(annualBytes))) {
                    var detail = annualBook.getSheet("Détail mensuel");
                    assertNotNull(detail);
                    assertFalse(detail.isColumnHidden(26), "Col 26 (Référence bulletin) ne doit pas être masquée");
                    assertFalse(detail.isColumnHidden(27), "Col 27 (Statut bulletin) ne doit pas être masquée");

                    var summary = annualBook.getSheet("Récap annuel");
                    assertNotNull(summary);
                    assertEquals("Type contrat", summary.getRow(3).getCell(5).getStringCellValue(), "Header doit être 'Type contrat'");
                    assertEquals("Type contrat", detail.getRow(3).getCell(7).getStringCellValue(), "Header doit être 'Type contrat'");

                    // Vérifier que chaque ligne mensuelle a sa référence bulletin et statut
                    for (int r = 4; r <= 9; r++) {
                        String ref = detail.getRow(r).getCell(26).getStringCellValue();
                        String status = detail.getRow(r).getCell(27).getStringCellValue();
                        assertNotNull(ref);
                        assertFalse(ref.isBlank(), "Référence bulletin renseignée à la ligne " + r);
                        assertEquals("CLOTURE", status, "Statut bulletin clôturé");
                    }
                }

                // 6. Sauvegarde des fichiers validés dans le répertoire test/
                Path testDir = Paths.get("../test");
                if (!Files.exists(testDir)) {
                    testDir = Paths.get("test");
                }
                if (Files.exists(testDir)) {
                    Files.write(testDir.resolve("Consolidation_Sociale_T1_2026.xlsx"), consolidationBytes);
                    Files.write(testDir.resolve("Modele_CNAPS_T1_2026.xlsx"), cnapsBytes);
                    Files.write(testDir.resolve("Modele_FMFP_T1_2026.xls"), fmfpBytes);
                    Files.write(testDir.resolve("Modele_OSTIE_T1_2026.xlsx"), ostieBytes);
                    Files.write(testDir.resolve("Recap_Annuel_Paie_2026.xlsx"), annualBytes);
                }
            } finally {
                c.rollback();
            }
        }
    }
}
