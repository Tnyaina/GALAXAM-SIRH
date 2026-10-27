package mg.galaxam.sirh.paie.application.export;

import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Recette opt-in : exclusivement sur une copie jetable de la base applicative. */
class PayrollWorkbookDatabaseTest {
    @Test void generatesAndReconcilesAllWorkbooksFromApplicationFixture() throws Exception {
        String url = System.getenv("GALAXAM_EXPORT_TEST_URL");
        assumeTrue(url != null && url.endsWith("/galaxam_export_recette"));
        try (Connection c = DriverManager.getConnection(url, "postgres", "")) {
            c.setAutoCommit(false);
            try {
                int period;
                try (var stmt = c.createStatement(); var rs = stmt.executeQuery("SELECT id_periode_paie FROM periode_paie WHERE code='2026-08'")) {
                    assertTrue(rs.next()); period = rs.getInt(1);
                }
                var grand = new GrandLivrePaieExportService().export(period, c);
                var social = new EtatSocialMensuelExportService().export(period, c);
                var irsa = new IrsaExcelExportService(new SafiExportService()).export(period, c);
                var annual = new AnnualPayrollExportService().export(2026, c);
                verify("mensuel-grand-livre.xlsx", grand.bytes());
                verify("mensuel-social.xlsx", social.bytes());
                verify("mensuel-irsa.xls", irsa.bytes());
                verify("annuel-partiel.xlsx", annual.bytes());
                try (var book = WorkbookFactory.create(new ByteArrayInputStream(grand.bytes()));
                     var stmt = c.createStatement(); var rs = stmt.executeQuery("SELECT SUM(cout_global_employeur) FROM bulletin_paie WHERE id_periode_paie=" + period)) {
                    rs.next(); assertEquals(rs.getBigDecimal(1).doubleValue(), book.getSheetAt(0).getRow(8).getCell(1).getNumericCellValue(), .001);
                    assertNull(book.getSheetAt(0).getRow(5).getCell(2));
                }
                var quarterly = new QuarterlySocialDataService();
                assertThrows(IllegalStateException.class, () -> quarterly.load(2026, 3, c));
                for (String month : List.of("2026-04", "2026-05", "2026-06", "2026-07", "2026-09")) cloneMonth(c, period, month);
                var all = quarterly.load(2026, 3, c);
                byte[] quarter = PayrollWorkbookAnalysis.quarterly(quarterly.renderConsolidation(all, quarterly.loadAll(2026, 3, c)), 2026, 3, c);
                verify("trimestriel-complet.xlsx", quarter);
                try (var book = WorkbookFactory.create(new ByteArrayInputStream(quarter))) {
                    assertEquals(0, book.getSheetAt(0).getRow(5).getCell(3).getNumericCellValue(), .001);
                    assertEquals(0, book.getSheetAt(0).getRow(5).getCell(4).getNumericCellValue(), .001);
                }
                verify("mensuel-avec-comparaison.xlsx", new GrandLivrePaieExportService().export(period, c).bytes());
                verify("annuel-six-mois.xlsx", new AnnualPayrollExportService().export(2026, c).bytes());
            } finally { c.rollback(); }
        }
    }
    private static void verify(String name, byte[] bytes) throws Exception {
        Path directory = Path.of("target/export-recette"); Files.createDirectories(directory); Files.write(directory.resolve(name), bytes);
        try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals("Vue de pilotage", book.getSheetAt(0).getSheetName());
            assertNotNull(book.getSheet("Traçabilité"));
            assertNotNull(book.getSheet("Analyse filiales"));
            FormulaEvaluator evaluator = book.getCreationHelper().createFormulaEvaluator();
            for (Sheet sheet : book) {
                assertNull(sheet.getPaneInformation(), name + ": " + sheet.getSheetName());
                for (Row row : sheet) for (Cell cell : row) {
                    assertNotEquals(CellType.ERROR, cell.getCellType(), sheet.getSheetName() + cell.getAddress());
                    if (cell.getCellType() == CellType.FORMULA)
                        assertNotEquals(CellType.ERROR, evaluator.evaluate(cell).getCellType(), name + ": " + sheet.getSheetName() + cell.getAddress());
                }
            }
            System.out.println("EXPORT_VERIFIED " + name + " sheets=" + book.getNumberOfSheets());
        }
    }
    private static void cloneMonth(Connection c, int source, String month) throws Exception {
        // Les dates de campagne sont déplacées ensemble ; les montants de la copie restent identiques.
        String sql = """
                DO $$ DECLARE p integer; b record; new_b integer; shift interval;
                BEGIN
                  shift := age(DATE '%s-01', (SELECT (code || '-01')::date FROM periode_paie WHERE id_periode_paie=%d));
                  p := nextval('periode_paie_id_periode_paie_seq');
                  INSERT INTO periode_paie SELECT (jsonb_populate_record(NULL::periode_paie, to_jsonb(src) || jsonb_build_object(
                    'id_periode_paie',p,'code','%s','date_debut',src.date_debut + shift,'date_fin',src.date_fin + shift))).*
                    FROM periode_paie src WHERE src.id_periode_paie=%d;
                  FOR b IN SELECT * FROM bulletin_paie WHERE id_periode_paie=%d LOOP
                    new_b := nextval('bulletin_paie_id_bulletin_seq');
                    INSERT INTO bulletin_paie SELECT (jsonb_populate_record(NULL::bulletin_paie, to_jsonb(b) || jsonb_build_object(
                      'id_bulletin',new_b,'id_periode_paie',p,'statut','CALCULE','reference_bulletin','RECETTE-' || p || '-' || new_b))).*;
                    INSERT INTO bulletin_paie_ligne SELECT (jsonb_populate_record(NULL::bulletin_paie_ligne, to_jsonb(l) || jsonb_build_object(
                      'id_ligne',nextval('bulletin_paie_ligne_id_ligne_seq'),'id_bulletin',new_b))).*
                      FROM bulletin_paie_ligne l WHERE l.id_bulletin=b.id_bulletin;
                    INSERT INTO bulletin_paie_employeur_snapshot SELECT (jsonb_populate_record(NULL::bulletin_paie_employeur_snapshot,
                      to_jsonb(s) || jsonb_build_object('id_bulletin',new_b))).* FROM bulletin_paie_employeur_snapshot s WHERE s.id_bulletin=b.id_bulletin;
                    UPDATE bulletin_paie SET statut=b.statut WHERE id_bulletin=new_b;
                  END LOOP;
                END $$;
                """.formatted(month, source, month, source, source);
        try (var stmt = c.createStatement()) { stmt.execute(sql); }
    }
}
