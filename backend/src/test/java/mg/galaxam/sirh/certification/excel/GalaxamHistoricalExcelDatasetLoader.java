package mg.galaxam.sirh.certification.excel;

import java.io.IOException;
import java.util.List;

/**
 * Façade principale d'ingestion des jeux de données réels historiques GALAXAM.
 * Orchestre le chargement des données de pointage, congés KAJY et paie.
 */
public class GalaxamHistoricalExcelDatasetLoader {

    public static final String PATH_PRESENCE = "model/ohatra presence svt clocker.xlsx";
    public static final String PATH_GITA = "model/ohatra gita fichiers-23jan26.xlsx";
    public static final String PATH_KAJY = "model/OHATRA KAJY DROITS CONGES-given on sept26.xls";

    public static List<HistoricalAttendanceCase> loadAllAttendanceCases() throws Exception {
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(PATH_PRESENCE)) {
            return PresenceHistoricalAdapter.loadAttendanceCases(reader);
        }
    }

    public static HistoricalLeaveCase loadHavanaLeaveCase() throws Exception {
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(PATH_KAJY)) {
            return KajyHistoricalAdapter.loadHavanaCase(reader);
        }
    }

    public static List<HistoricalPayrollCase> loadAllPayrollCases() throws Exception {
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(PATH_PRESENCE)) {
            return PayrollHistoricalAdapter.loadPayrollCases(reader);
        }
    }
}
