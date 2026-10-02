package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.domain.dto.export.SafiRowDto;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** État IRSA mensuel construit à partir du modèle Excel fourni, pour revue par la DAF. */
@Service
public class IrsaExcelExportService {
    private static final String TEMPLATE = "/templates/mensuel/MODELE_IRSA.xls";
    private static final int FIRST_DATA_ROW = 15;
    private static final int TEMPLATE_TOTAL_ROW = 23;
    private static final DateTimeFormatter INPUT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final SafiExportService safiExportService;

    public IrsaExcelExportService(SafiExportService safiExportService) {
        this.safiExportService = safiExportService;
    }

    public record Result(byte[] bytes, String periodCode, int employeeCount) {}
    private record Header(String code, LocalDate end, String company, String nif, String address) {}

    public Result export(Integer periodId, Connection connection) throws Exception {
        Header header = loadHeader(periodId, connection);
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(periodId, connection);
        if (bulletins.isEmpty()) throw new IllegalStateException("Aucun bulletin pour cette période.");
        if (bulletins.stream().anyMatch(b -> !"VALIDE".equals(b.getStatut()) && !"CLOTURE".equals(b.getStatut()))) {
            throw new IllegalStateException("Tous les bulletins doivent être validés avant l'export IRSA.");
        }
        List<SafiRowDto> data = safiExportService.genererDonneesSafi(periodId, connection);
        if (data.isEmpty()) throw new IllegalStateException("Aucun salarié concerné par l'IRSA pour cette période.");
        if (FIRST_DATA_ROW + data.size() >= 65535) throw new IllegalStateException("Trop de lignes pour le format XLS.");

        try (InputStream source = getClass().getResourceAsStream(TEMPLATE)) {
            if (source == null) throw new IllegalStateException("Modèle IRSA introuvable.");
            try (HSSFWorkbook workbook = new HSSFWorkbook(source); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.getSheetAt(0);
                CellStyle[] sampleStyles = new CellStyle[29];
                Row sample = sheet.getRow(FIRST_DATA_ROW);
                for (int col = 0; col < sampleStyles.length; col++) {
                    Cell cell = sample.getCell(col);
                    sampleStyles[col] = cell == null ? null : cell.getCellStyle();
                }
                short sampleHeight = sample.getHeight();
                int extra = Math.max(0, data.size() - (TEMPLATE_TOTAL_ROW - FIRST_DATA_ROW));
                if (extra > 0) sheet.shiftRows(TEMPLATE_TOTAL_ROW, sheet.getLastRowNum(), extra, true, false);
                int totalRowIndex = TEMPLATE_TOTAL_ROW + extra;

                text(sheet, 8, 5, header.company());
                text(sheet, 8, 9, header.company());
                text(sheet, 9, 5, header.nif());
                text(sheet, 9, 9, header.nif());
                text(sheet, 10, 5, header.address());
                text(sheet, 10, 9, header.address());
                text(sheet, 9, 27, header.end().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)).toUpperCase(Locale.FRENCH));

                for (int i = FIRST_DATA_ROW; i < totalRowIndex; i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) row = sheet.createRow(i);
                    row.setHeight(sampleHeight);
                    for (int col = 0; col < sampleStyles.length; col++) {
                        Cell cell = row.getCell(col);
                        if (cell == null) cell = row.createCell(col);
                        cell.setBlank();
                        if (sampleStyles[col] != null) cell.setCellStyle(sampleStyles[col]);
                    }
                    if (i - FIRST_DATA_ROW < data.size()) writeEmployee(row, data.get(i - FIRST_DATA_ROW));
                }
                Row total = sheet.getRow(totalRowIndex);
                if (total == null) total = sheet.createRow(totalRowIndex);
                for (int col = 9; col <= 28; col++) {
                    Cell cell = total.getCell(col);
                    if (cell == null) cell = total.createCell(col);
                    String letter = CellReference.convertNumToColString(col);
                    cell.setCellFormula("SUM(" + letter + "16:" + letter + (15 + data.size()) + ")");
                }
                // Le modèle contient des notes, chiffres et formules d'exemple sous le total.
                for (int i = sheet.getLastRowNum(); i > totalRowIndex; i--) {
                    Row row = sheet.getRow(i);
                    if (row != null) sheet.removeRow(row);
                }
                for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
                    if (sheet.getMergedRegion(i).getLastRow() > totalRowIndex) sheet.removeMergedRegion(i);
                }
                sheet.createFreezePane(9, FIRST_DATA_ROW);
                workbook.setForceFormulaRecalculation(true);
                workbook.write(out);
                return new Result(out.toByteArray(), header.code(), data.size());
            }
        }
    }

    private static void writeEmployee(Row row, SafiRowDto v) {
        string(row, 1, v.getMatricule()); string(row, 2, v.getAffiliationCnaps());
        // D : NIF personnel absent du snapshot de paie ; ne pas inventer de valeur.
        string(row, 4, v.getNomPrenoms()); string(row, 5, v.getCin());
        date(row, 6, v.getDateEmbauche()); date(row, 7, v.getDateDebauchage());
        string(row, 8, v.getFonction());
        money(row, 9, v.getSalaireBase()); money(row, 10, v.getIndemnitesImposables());
        money(row, 11, v.getIndemnitesNonImposables());
        money(row, 15, v.getAvantagesNatureImposables()); money(row, 16, v.getAvantagesNatureExoneres());
        money(row, 17, v.getHeuresSupplementaires()); money(row, 18, v.getPrimesEtGratifications());
        money(row, 19, v.getAutres()); money(row, 20, v.getSalaireBrut());
        money(row, 21, v.getDeductionsCnaps()); money(row, 22, v.getDeductionsSante());
        money(row, 23, v.getSalaireNet()); money(row, 24, v.getAutreDeduction());
        money(row, 25, v.getMontantImposable()); money(row, 26, v.getImpotCorrespondant());
        money(row, 27, v.getReductionChargeFamille()); money(row, 28, v.getImpotDu());
    }

    private static void text(Sheet sheet, int row, int col, String value) {
        Row r = sheet.getRow(row);
        if (r == null) r = sheet.createRow(row);
        Cell c = r.getCell(col);
        if (c == null) c = r.createCell(col);
        c.setCellValue(value == null ? "" : value);
    }
    private static void string(Row row, int col, String value) {
        if (value != null && !value.isBlank()) row.getCell(col).setCellValue(value);
    }
    private static void money(Row row, int col, BigDecimal value) {
        row.getCell(col).setCellValue(value == null ? 0 : value.doubleValue());
    }
    private static void date(Row row, int col, String value) {
        if (value != null && !value.isBlank()) {
            row.getCell(col).setCellValue(java.sql.Date.valueOf(LocalDate.parse(value, INPUT_DATE)));
        }
    }

    private static Header loadHeader(Integer periodId, Connection c) throws Exception {
        String sql = "SELECT p.code, p.date_fin, rf.raison_sociale, rf.nif, rf.adresse_siege " +
                "FROM periode_paie p JOIN bulletin_paie b ON b.id_periode_paie = p.id_periode_paie " +
                "LEFT JOIN LATERAL (SELECT f.raison_sociale, f.nif, f.adresse_siege " +
                "FROM contrat_travail ct JOIN ref_filiale f ON f.id_filiale = ct.id_filiale " +
                "WHERE ct.id_employe = b.id_employe AND ct.date_debut <= p.date_fin " +
                "ORDER BY ct.date_debut DESC, ct.id_contrat DESC LIMIT 1) rf ON TRUE " +
                "WHERE p.id_periode_paie = ?";
        Header header = null;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getString("raison_sociale") == null) throw new IllegalStateException("Filiale manquante pour un bulletin IRSA.");
                    Header current = new Header(rs.getString("code"), rs.getDate("date_fin").toLocalDate(),
                            rs.getString("raison_sociale"), rs.getString("nif"), rs.getString("adresse_siege"));
                    if (header != null && (!header.company().equals(current.company()) || !java.util.Objects.equals(header.nif(), current.nif()))) {
                        throw new IllegalStateException("Plusieurs filiales dans cette période : générer un état IRSA par filiale.");
                    }
                    header = current;
                }
            }
        }
        if (header == null) throw new IllegalStateException("Aucun bulletin pour cette période.");
        return header;
    }
}
