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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

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
    record Header(String code, LocalDate end) {}

    public Result export(Integer periodId, Connection connection) throws Exception {
        Header header = loadHeader(periodId, connection);
        PayrollExportEligibility.requireFinalPeriod(periodId, connection);
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(periodId, connection);
        if (bulletins.isEmpty()) throw new IllegalStateException("Aucun bulletin pour cette période.");
        PayrollExportEligibility.requireFinalBulletins(bulletins, header.code());
        List<SafiRowDto> data = safiExportService.genererDonneesSafi(periodId, connection);
        if (data.isEmpty()) throw new IllegalStateException("Aucun salarié concerné par l'IRSA pour cette période.");
        if (FIRST_DATA_ROW + data.size() >= 65535) throw new IllegalStateException("Trop de lignes pour le format XLS.");
        Map<Integer, PayrollExportContextResolver.Company> companyByBulletin =
                PayrollExportContextResolver.companiesByBulletin(periodId, connection);
        Map<PayrollExportContextResolver.Company, List<BulletinPaie>> byCompany = new LinkedHashMap<>();
        for (BulletinPaie bulletin : bulletins) {
            var company = companyByBulletin.get(bulletin.getIdBulletin());
            if (company == null) throw new IllegalStateException("Filiale employeur introuvable pour le bulletin "
                    + bulletin.getReferenceBulletin() + ".");
            List<String> missingCompanyFields = new ArrayList<>();
            if (company.name() == null || company.name().isBlank()) missingCompanyFields.add("raison sociale");
            if (company.nif() == null || company.nif().isBlank()) missingCompanyFields.add("NIF");
            if (!missingCompanyFields.isEmpty()) {
                String companyLabel = company.name() == null || company.name().isBlank()
                        ? "(raison sociale non renseignée)" : "« " + company.name().trim() + " »";
                throw new IllegalStateException("Export impossible pour la filiale " + companyLabel
                        + " : renseignez " + String.join(" et ", missingCompanyFields) + ".");
            }
            byCompany.computeIfAbsent(company, ignored -> new ArrayList<>()).add(bulletin);
        }

        try (InputStream source = getClass().getResourceAsStream(TEMPLATE)) {
            if (source == null) throw new IllegalStateException("Modèle IRSA introuvable.");
            try (HSSFWorkbook workbook = new HSSFWorkbook(source); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet model = workbook.getSheetAt(0);
                workbook.setSheetName(0, "Toutes les filiales");
                // Cloner le modèle vierge avant de remplir la feuille consolidée.
                List<Sheet> companySheets = new ArrayList<>();
                for (var company : byCompany.keySet()) {
                    Sheet companySheet = workbook.cloneSheet(0);
                    workbook.setSheetName(workbook.getSheetIndex(companySheet),
                            PayrollSheetNames.unique(workbook, company.name()));
                    companySheets.add(companySheet);
                }
                writeSheet(model, header, new PayrollExportContextResolver.Company(
                        "Toutes les filiales", "", ""), data);
                int index = 0;
                for (var entry : byCompany.entrySet()) {
                    List<SafiRowDto> rows = safiExportService.genererDonneesSafi(periodId, connection, entry.getValue());
                    if (FIRST_DATA_ROW + rows.size() >= 65535)
                        throw new IllegalStateException("Trop de lignes pour le format XLS.");
                    writeSheet(companySheets.get(index++), header, entry.getKey(), rows);
                }
                evaluateAndMarkForRecalculation(workbook);
                workbook.write(out);
                return new Result(out.toByteArray(), header.code(), data.size());
            }
        }
    }

    static void writeSheet(Sheet sheet, Header header,
            PayrollExportContextResolver.Company company, List<SafiRowDto> data) {
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

                text(sheet, 8, 5, company.name());
                text(sheet, 8, 9, company.name());
                text(sheet, 9, 5, company.nif());
                text(sheet, 9, 9, company.nif());
                text(sheet, 10, 5, company.address());
                text(sheet, 10, 9, company.address());
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
    }

    static void evaluateAndMarkForRecalculation(HSSFWorkbook workbook) {
        workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
        workbook.setForceFormulaRecalculation(true);
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
        String sql = "SELECT code,date_fin FROM periode_paie p WHERE id_periode_paie=? "
                + "AND EXISTS (SELECT 1 FROM bulletin_paie b WHERE b.id_periode_paie=p.id_periode_paie)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Aucun bulletin pour cette période.");
                return new Header(rs.getString("code"), rs.getDate("date_fin").toLocalDate());
            }
        }
    }

}

