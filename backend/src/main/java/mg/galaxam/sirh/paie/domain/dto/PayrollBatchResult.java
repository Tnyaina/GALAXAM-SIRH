package mg.galaxam.sirh.paie.domain.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Résumé de l'exécution collective du calcul de paie d'une période.
 */
public class PayrollBatchResult {

    private Integer idPeriodePaie;
    private String codePeriode;
    private int nbBulletinsCalcules = 0;
    private BigDecimal totalMasseSalarialeBrute = BigDecimal.ZERO;
    private BigDecimal totalNetAPayer = BigDecimal.ZERO;
    private BigDecimal totalChargesPatronales = BigDecimal.ZERO;
    private BigDecimal totalCoutGlobal = BigDecimal.ZERO;
    private List<String> referencesBulletins = new ArrayList<>();
    private List<EmployeePayrollOutcome> employeeOutcomes = new ArrayList<>();

    public record EmployeePayrollOutcome(Integer employeeId, String status, String message,
                                         BulletinPaieComplet result) {}

    public PayrollBatchResult() {}

    public PayrollBatchResult(Integer idPeriodePaie, String codePeriode) {
        this.idPeriodePaie = idPeriodePaie;
        this.codePeriode = codePeriode;
    }

    public void ajouterBulletin(BulletinPaieComplet bpc) {
        if (bpc == null || bpc.getBulletin() == null) return;
        this.nbBulletinsCalcules++;
        if (bpc.getBulletin().getTotalGainsBruts() != null) {
            this.totalMasseSalarialeBrute = this.totalMasseSalarialeBrute.add(bpc.getBulletin().getTotalGainsBruts());
        }
        if (bpc.getBulletin().getNetAPayer() != null) {
            this.totalNetAPayer = this.totalNetAPayer.add(bpc.getBulletin().getNetAPayer());
        }
        if (bpc.getBulletin().getTotalCotisationsPatronales() != null) {
            this.totalChargesPatronales = this.totalChargesPatronales.add(bpc.getBulletin().getTotalCotisationsPatronales());
        }
        if (bpc.getBulletin().getCoutGlobalEmployeur() != null) {
            this.totalCoutGlobal = this.totalCoutGlobal.add(bpc.getBulletin().getCoutGlobalEmployeur());
        }
        if (bpc.getBulletin().getReferenceBulletin() != null) {
            this.referencesBulletins.add(bpc.getBulletin().getReferenceBulletin());
        }
        this.employeeOutcomes.add(new EmployeePayrollOutcome(
                bpc.getBulletin().getIdEmploye(), "OK", null, bpc));
    }

    public void ajouterBloque(Integer employeeId, String message) {
        this.employeeOutcomes.add(new EmployeePayrollOutcome(employeeId, "BLOCKED", message, null));
    }

    // Getters & Setters
    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public String getCodePeriode() { return codePeriode; }
    public void setCodePeriode(String codePeriode) { this.codePeriode = codePeriode; }

    public int getNbBulletinsCalcules() { return nbBulletinsCalcules; }
    public void setNbBulletinsCalcules(int nbBulletinsCalcules) { this.nbBulletinsCalcules = nbBulletinsCalcules; }

    public BigDecimal getTotalMasseSalarialeBrute() { return totalMasseSalarialeBrute; }
    public void setTotalMasseSalarialeBrute(BigDecimal totalMasseSalarialeBrute) { this.totalMasseSalarialeBrute = totalMasseSalarialeBrute; }

    public BigDecimal getTotalNetAPayer() { return totalNetAPayer; }
    public void setTotalNetAPayer(BigDecimal totalNetAPayer) { this.totalNetAPayer = totalNetAPayer; }

    public BigDecimal getTotalChargesPatronales() { return totalChargesPatronales; }
    public void setTotalChargesPatronales(BigDecimal totalChargesPatronales) { this.totalChargesPatronales = totalChargesPatronales; }

    public BigDecimal getTotalCoutGlobal() { return totalCoutGlobal; }
    public void setTotalCoutGlobal(BigDecimal totalCoutGlobal) { this.totalCoutGlobal = totalCoutGlobal; }

    public List<String> getReferencesBulletins() { return referencesBulletins; }
    public void setReferencesBulletins(List<String> referencesBulletins) { this.referencesBulletins = referencesBulletins; }

    public List<EmployeePayrollOutcome> getEmployeeOutcomes() { return employeeOutcomes; }
    public void setEmployeeOutcomes(List<EmployeePayrollOutcome> employeeOutcomes) { this.employeeOutcomes = employeeOutcomes; }
}
