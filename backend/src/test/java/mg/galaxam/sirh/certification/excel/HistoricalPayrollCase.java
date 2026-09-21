package mg.galaxam.sirh.certification.excel;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Cas de test de paie historique reconstitué à partir des classeurs GALAXAM.
 * Compare chaque rubrique entre l'Excel historique, l'Oracle indépendant canonique et le Runtime SIRH.
 */
public class HistoricalPayrollCase {

    public enum CertificationStatus {
        FULLY_CERTIFIABLE,
        PARTIALLY_CERTIFIABLE,
        NOT_CERTIFIABLE_SOURCE_MISSING,
        BLOCKED_BUSINESS_RULE
    }

    public record RubricComparison(
            String rubricCode,
            String label,
            BigDecimal excelValue,
            BigDecimal oracleValue,
            BigDecimal runtimeValue,
            BigDecimal deltaOracleRuntime,
            BigDecimal deltaExcelOracle,
            ExcelDivergenceClassification classification,
            String explanation
    ) {
        public static RubricComparison of(String rubricCode, String label,
                                          BigDecimal excelValue, BigDecimal oracleValue, BigDecimal runtimeValue,
                                          ExcelDivergenceClassification classification, String explanation) {
            BigDecimal deltaOR = (oracleValue != null && runtimeValue != null) ? runtimeValue.subtract(oracleValue) : BigDecimal.ZERO;
            BigDecimal deltaEO = (excelValue != null && oracleValue != null) ? oracleValue.subtract(excelValue) : BigDecimal.ZERO;
            return new RubricComparison(rubricCode, label, excelValue, oracleValue, runtimeValue, deltaOR, deltaEO, classification, explanation);
        }
    }

    private final String caseId;
    private final String sourceFile;
    private final String sheet;
    private final int row;
    private final String matricule;
    private final String employeeName;
    private final String period;
    private final LocalDate payrollDate;
    private final String siteCode;

    // Inputs bruts / de référence normalisés
    private BigDecimal baseSalary;
    private BigDecimal leaveDaysTaken;
    private BigDecimal tcongeRate;
    private BigDecimal workedHours;
    private BigDecimal absenceMinutes;
    private BigDecimal hs30Hours;
    private BigDecimal hs50Hours;
    private BigDecimal hsAmountDirect;
    private BigDecimal autimpAmount;
    private BigDecimal repasAmount;
    private BigDecimal deplacementAmount;
    private BigDecimal avanceAmount;
    private BigDecimal callPersoAmount;
    private int nbEnfants = 0;
    private String healthRegime = "OSTIE"; // OSTIE (TNR) ou OMSI (TMM)

    private final List<RubricComparison> comparisons = new ArrayList<>();
    private final List<HistoricalExcelRecord> provenanceRecords = new ArrayList<>();
    private CertificationStatus status = CertificationStatus.FULLY_CERTIFIABLE;
    private String inputChecksum;
    private String expectedChecksum;
    private String notes;

    public HistoricalPayrollCase(String caseId, String sourceFile, String sheet, int row,
                                 String matricule, String employeeName, String period,
                                 LocalDate payrollDate, String siteCode) {
        this.caseId = caseId;
        this.sourceFile = sourceFile;
        this.sheet = sheet;
        this.row = row;
        this.matricule = matricule;
        this.employeeName = employeeName;
        this.period = period;
        this.payrollDate = payrollDate;
        this.siteCode = siteCode;
    }

    public void addComparison(RubricComparison comp) {
        this.comparisons.add(comp);
    }

    public void addProvenance(HistoricalExcelRecord record) {
        this.provenanceRecords.add(record);
    }

    public void computeChecksums() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String inString = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
                    matricule, period, baseSalary, leaveDaysTaken, tcongeRate,
                    hsAmountDirect, autimpAmount, repasAmount, deplacementAmount,
                    avanceAmount, callPersoAmount);
            this.inputChecksum = HexFormat.of().formatHex(md.digest(inString.getBytes(StandardCharsets.UTF_8))).substring(0, 16);

            StringBuilder expSb = new StringBuilder();
            for (RubricComparison c : comparisons) {
                expSb.append(c.rubricCode()).append(":").append(c.oracleValue()).append(";");
            }
            this.expectedChecksum = HexFormat.of().formatHex(md.digest(expSb.toString().getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            this.inputChecksum = "CHECKSUM_ERROR";
            this.expectedChecksum = "CHECKSUM_ERROR";
        }
    }

    // Getters and Setters
    public String getCaseId() { return caseId; }
    public String getSourceFile() { return sourceFile; }
    public String getSheet() { return sheet; }
    public int getRow() { return row; }
    public String getMatricule() { return matricule; }
    public String getEmployeeName() { return employeeName; }
    public String getPeriod() { return period; }
    public LocalDate getPayrollDate() { return payrollDate; }
    public String getSiteCode() { return siteCode; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }

    public BigDecimal getLeaveDaysTaken() { return leaveDaysTaken; }
    public void setLeaveDaysTaken(BigDecimal leaveDaysTaken) { this.leaveDaysTaken = leaveDaysTaken; }

    public BigDecimal getTcongeRate() { return tcongeRate; }
    public void setTcongeRate(BigDecimal tcongeRate) { this.tcongeRate = tcongeRate; }

    public BigDecimal getWorkedHours() { return workedHours; }
    public void setWorkedHours(BigDecimal workedHours) { this.workedHours = workedHours; }

    public BigDecimal getAbsenceMinutes() { return absenceMinutes; }
    public void setAbsenceMinutes(BigDecimal absenceMinutes) { this.absenceMinutes = absenceMinutes; }

    public BigDecimal getHs30Hours() { return hs30Hours; }
    public void setHs30Hours(BigDecimal hs30Hours) { this.hs30Hours = hs30Hours; }

    public BigDecimal getHs50Hours() { return hs50Hours; }
    public void setHs50Hours(BigDecimal hs50Hours) { this.hs50Hours = hs50Hours; }

    public BigDecimal getHsAmountDirect() { return hsAmountDirect; }
    public void setHsAmountDirect(BigDecimal hsAmountDirect) { this.hsAmountDirect = hsAmountDirect; }

    public BigDecimal getAutimpAmount() { return autimpAmount; }
    public void setAutimpAmount(BigDecimal autimpAmount) { this.autimpAmount = autimpAmount; }

    public BigDecimal getRepasAmount() { return repasAmount; }
    public void setRepasAmount(BigDecimal repasAmount) { this.repasAmount = repasAmount; }

    public BigDecimal getDeplacementAmount() { return deplacementAmount; }
    public void setDeplacementAmount(BigDecimal deplacementAmount) { this.deplacementAmount = deplacementAmount; }

    public BigDecimal getAvanceAmount() { return avanceAmount; }
    public void setAvanceAmount(BigDecimal avanceAmount) { this.avanceAmount = avanceAmount; }

    public BigDecimal getCallPersoAmount() { return callPersoAmount; }
    public void setCallPersoAmount(BigDecimal callPersoAmount) { this.callPersoAmount = callPersoAmount; }

    public int getNbEnfants() { return nbEnfants; }
    public void setNbEnfants(int nbEnfants) { this.nbEnfants = nbEnfants; }

    public String getHealthRegime() { return healthRegime; }
    public void setHealthRegime(String healthRegime) { this.healthRegime = healthRegime; }

    public List<RubricComparison> getComparisons() { return comparisons; }
    public List<HistoricalExcelRecord> getProvenanceRecords() { return provenanceRecords; }
    public CertificationStatus getStatus() { return status; }
    public void setStatus(CertificationStatus status) { this.status = status; }

    public String getInputChecksum() { return inputChecksum; }
    public String getExpectedChecksum() { return expectedChecksum; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
