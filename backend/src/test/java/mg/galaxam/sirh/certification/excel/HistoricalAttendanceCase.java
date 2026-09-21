package mg.galaxam.sirh.certification.excel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.oracle.AttendanceIndependentOracle;

/**
 * Cas de test de pointage historique reconstitué à partir des pointages réels machine.
 */
public class HistoricalAttendanceCase {

    public enum CertificationStatus {
        FULLY_CERTIFIABLE,
        PARTIALLY_CERTIFIABLE,
        NOT_CERTIFIABLE_SOURCE_MISSING,
        BLOCKED_BUSINESS_RULE
    }

    public record DailyPunchItem(
            LocalDate date,
            LocalTime entreeMatin,
            LocalTime sortieMatin,
            LocalTime entreeApresMidi,
            LocalTime sortieApresMidi,
            boolean isConge,
            boolean isFerie,
            String rawExcelDayLabel
    ) {}

    private final String caseId;
    private final String matricule;
    private final String employeeName;
    private final String siteCode;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final List<DailyPunchItem> punches = new ArrayList<>();
    private final List<HistoricalExcelRecord> provenanceRecords = new ArrayList<>();

    // Totaux affichés dans l'Excel historique
    private Double excelWorkedDays;
    private Double excelAbsenceDays;
    private Double excelOvertimeHours;
    private Double excelLateMinutes;

    // Résultats calculés par l'oracle indépendant moderne
    private AttendanceIndependentOracle.AttendanceExpectedResult expectedCanonical;

    // Résultats calculés par le runtime SIRH
    private AttendanceIndependentOracle.AttendanceExpectedResult actualRuntime;

    private CertificationStatus status = CertificationStatus.FULLY_CERTIFIABLE;
    private String notes;

    public HistoricalAttendanceCase(String caseId, String matricule, String employeeName,
                                   String siteCode, LocalDate periodStart, LocalDate periodEnd) {
        this.caseId = caseId;
        this.matricule = matricule;
        this.employeeName = employeeName;
        this.siteCode = siteCode;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public void addPunch(DailyPunchItem punch) {
        this.punches.add(punch);
    }

    public void addProvenance(HistoricalExcelRecord record) {
        this.provenanceRecords.add(record);
    }

    public String getCaseId() { return caseId; }
    public String getMatricule() { return matricule; }
    public String getEmployeeName() { return employeeName; }
    public String getSiteCode() { return siteCode; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public List<DailyPunchItem> getPunches() { return punches; }
    public List<HistoricalExcelRecord> getProvenanceRecords() { return provenanceRecords; }

    public Double getExcelWorkedDays() { return excelWorkedDays; }
    public void setExcelWorkedDays(Double excelWorkedDays) { this.excelWorkedDays = excelWorkedDays; }

    public Double getExcelAbsenceDays() { return excelAbsenceDays; }
    public void setExcelAbsenceDays(Double excelAbsenceDays) { this.excelAbsenceDays = excelAbsenceDays; }

    public Double getExcelOvertimeHours() { return excelOvertimeHours; }
    public void setExcelOvertimeHours(Double excelOvertimeHours) { this.excelOvertimeHours = excelOvertimeHours; }

    public Double getExcelLateMinutes() { return excelLateMinutes; }
    public void setExcelLateMinutes(Double excelLateMinutes) { this.excelLateMinutes = excelLateMinutes; }

    public AttendanceIndependentOracle.AttendanceExpectedResult getExpectedCanonical() { return expectedCanonical; }
    public void setExpectedCanonical(AttendanceIndependentOracle.AttendanceExpectedResult expectedCanonical) { this.expectedCanonical = expectedCanonical; }

    public AttendanceIndependentOracle.AttendanceExpectedResult getActualRuntime() { return actualRuntime; }
    public void setActualRuntime(AttendanceIndependentOracle.AttendanceExpectedResult actualRuntime) { this.actualRuntime = actualRuntime; }

    public CertificationStatus getStatus() { return status; }
    public void setStatus(CertificationStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
