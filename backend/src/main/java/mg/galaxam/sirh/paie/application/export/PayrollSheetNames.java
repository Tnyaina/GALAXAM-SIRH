package mg.galaxam.sirh.paie.application.export;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;

/** Noms de feuilles Excel sûrs et distincts, même pour des raisons sociales proches. */
final class PayrollSheetNames {
    private PayrollSheetNames() {}

    static String unique(Workbook workbook, String name) {
        String base = WorkbookUtil.createSafeSheetName(name == null || name.isBlank() ? "Filiale" : name);
        if (base.length() > 31) base = base.substring(0, 31);
        String candidate = base;
        for (int suffix = 2; workbook.getSheet(candidate) != null; suffix++) {
            String addition = " (" + suffix + ")";
            candidate = base.substring(0, Math.min(base.length(), 31 - addition.length())) + addition;
        }
        return candidate;
    }
}
