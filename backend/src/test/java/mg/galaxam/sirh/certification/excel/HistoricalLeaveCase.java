package mg.galaxam.sirh.certification.excel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Cas de test de congés et droits KAJY reconstitué sur 12 mois à partir de l'historique réel.
 */
public class HistoricalLeaveCase {

    public enum CertificationStatus {
        FULLY_CERTIFIABLE,
        PARTIALLY_CERTIFIABLE,
        NOT_CERTIFIABLE_SOURCE_MISSING,
        BLOCKED_BUSINESS_RULE
    }

    public record MonthlyLeaveEntry(
            int moisIndex,
            BigDecimal baseSalary,
            BigDecimal presenceDays,
            BigDecimal absenceDays,
            BigDecimal leaveDaysTaken,
            BigDecimal tcongeApplied,
            BigDecimal leaveIndemnityExcel,
            BigDecimal hsAmount,
            BigDecimal autimpAmount,
            BigDecimal grossSalaryExcel,
            BigDecimal droitAcquisExcel,
            BigDecimal valorisationExcel,
            BigDecimal cumulDroitsExcel,
            BigDecimal cumulValorisationExcel,
            // Valeurs recalculées canoniques (Oracle)
            BigDecimal canonicalDroitAcquis,
            BigDecimal canonicalValorisation,
            BigDecimal canonicalCumulDroits,
            BigDecimal canonicalCumulValorisation
    ) {}

    private final String caseId;
    private final String matricule;
    private final String employeeName;
    private final List<MonthlyLeaveEntry> months = new ArrayList<>();
    private final List<HistoricalExcelRecord> provenanceRecords = new ArrayList<>();

    private BigDecimal historicalFinalTconge;
    private BigDecimal canonicalFinalTconge;
    private BigDecimal actualRuntimeFinalTconge;
    private CertificationStatus status = CertificationStatus.FULLY_CERTIFIABLE;
    private String notes;

    public HistoricalLeaveCase(String caseId, String matricule, String employeeName) {
        this.caseId = caseId;
        this.matricule = matricule;
        this.employeeName = employeeName;
    }

    public void addMonth(MonthlyLeaveEntry entry) {
        this.months.add(entry);
    }

    public void addProvenance(HistoricalExcelRecord record) {
        this.provenanceRecords.add(record);
    }

    public String getCaseId() { return caseId; }
    public String getMatricule() { return matricule; }
    public String getEmployeeName() { return employeeName; }
    public List<MonthlyLeaveEntry> getMonths() { return months; }
    public List<HistoricalExcelRecord> getProvenanceRecords() { return provenanceRecords; }

    public BigDecimal getHistoricalFinalTconge() { return historicalFinalTconge; }
    public void setHistoricalFinalTconge(BigDecimal historicalFinalTconge) { this.historicalFinalTconge = historicalFinalTconge; }

    public BigDecimal getCanonicalFinalTconge() { return canonicalFinalTconge; }
    public void setCanonicalFinalTconge(BigDecimal canonicalFinalTconge) { this.canonicalFinalTconge = canonicalFinalTconge; }

    public BigDecimal getActualRuntimeFinalTconge() { return actualRuntimeFinalTconge; }
    public void setActualRuntimeFinalTconge(BigDecimal actualRuntimeFinalTconge) { this.actualRuntimeFinalTconge = actualRuntimeFinalTconge; }

    public CertificationStatus getStatus() { return status; }
    public void setStatus(CertificationStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
