package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.domain.dto.export.SafiRowDto;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/** Présentation XLS des mêmes lignes SAFI que le CSV et l'état IRSA. */
@Service
public class SafiXlsExportService {
    private static final String[] HEADERS = {
            "N° Ordre", "N° Mle", "N° d'affiliation CNaPS", "Nom et prénoms", "CIN / Carte de résident",
            "Date d'embauche", "Date de débauchage", "Fonction", "Salaire de base", "Indemnités imposables",
            "Indemnités non imposables", "Avantages en nature imposables", "Avantages en nature exonérés",
            "Heures supplémentaires", "Primes et gratifications", "Autres", "Salaire brut", "CNaPS",
            "OSTIE / OMSI", "Salaire net", "Autre déduction", "Montant imposable",
            "Impôt correspondant", "Réduction charge famille", "Impôt dû"
    };
    private final SafiExportService safiExportService;

    public SafiXlsExportService(SafiExportService safiExportService) {
        this.safiExportService = safiExportService;
    }

    public byte[] export(Integer periodId, Connection connection) throws Exception {
        List<SafiRowDto> data = safiExportService.genererDonneesSafi(periodId, connection);
        if (data.size() > 65530) throw new IllegalStateException("Trop de lignes pour le format XLS.");
        var company = PayrollExportContextResolver.forPeriod(periodId, connection);
        String period = "";
        try (PreparedStatement ps = connection.prepareStatement("SELECT libelle FROM periode_paie WHERE id_periode_paie = ?")) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) period = rs.getString(1); }
        }
        try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("État 301 SAFI");
            CellStyle title = workbook.createCellStyle();
            Font titleFont = workbook.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 14);
            title.setFont(titleFont);
            CellStyle header = workbook.createCellStyle();
            Font headerFont = workbook.createFont(); headerFont.setBold(true); header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle amount = workbook.createCellStyle();
            amount.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            cell(sheet.createRow(0), 0, "ÉTAT 301 — ANNEXE DE DÉCLARATION IRSA (SAFI)", title);
            cell(sheet.createRow(1), 0, company.name(), null);
            cell(sheet.createRow(2), 0, "NIF : " + company.nifCompact(), null);
            cell(sheet.createRow(3), 0, "Période : " + (period == null ? "" : period), null);
            Row headings = sheet.createRow(5);
            for (int i = 0; i < HEADERS.length; i++) cell(headings, i, HEADERS[i], header);
            for (int index = 0; index < data.size(); index++) {
                SafiRowDto d = data.get(index);
                Row row = sheet.createRow(6 + index);
                number(row, 0, BigDecimal.valueOf(d.getNumeroOrdre()), null);
                cell(row, 1, d.getMatricule(), null); cell(row, 2, d.getAffiliationCnaps(), null);
                cell(row, 3, d.getNomPrenoms(), null); cell(row, 4, d.getCin(), null);
                cell(row, 5, d.getDateEmbauche(), null); cell(row, 6, d.getDateDebauchage(), null);
                cell(row, 7, d.getFonction(), null);
                BigDecimal[] values = {d.getSalaireBase(), d.getIndemnitesImposables(), d.getIndemnitesNonImposables(),
                        d.getAvantagesNatureImposables(), d.getAvantagesNatureExoneres(), d.getHeuresSupplementaires(),
                        d.getPrimesEtGratifications(), d.getAutres(), d.getSalaireBrut(), d.getDeductionsCnaps(),
                        d.getDeductionsSante(), d.getSalaireNet(), d.getAutreDeduction(), d.getMontantImposable(),
                        d.getImpotCorrespondant(), d.getReductionChargeFamille(), d.getImpotDu()};
                for (int col = 0; col < values.length; col++) number(row, col + 8, values[col], amount);
            }
            sheet.createFreezePane(8, 6);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(5, Math.max(5, data.size() + 5), 0, 24));
            for (int i = 0; i < HEADERS.length; i++) sheet.setColumnWidth(i, i == 3 ? 9000 : 5500);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) cell.setCellStyle(style);
    }

    private static void number(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0 : value.doubleValue());
        if (style != null) cell.setCellStyle(style);
    }
}
