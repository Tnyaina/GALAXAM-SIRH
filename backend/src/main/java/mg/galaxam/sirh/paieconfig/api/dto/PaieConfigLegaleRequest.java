package mg.galaxam.sirh.paieconfig.api.dto;

import java.math.BigDecimal;
import java.sql.Date;

import java.util.List;

/**
 * DTO pour créer ou revaloriser un exercice fiscal (paie_config_legale).
 *
 * Opérations possibles via le contrôleur :
 *  - POST /paie/config-legale         → Créer un nouvel exercice (création initiale) avec tranches IRSA
 *  - POST /paie/config-legale/revaloriser → Clôture l'exercice actif + crée le suivant à la date d'effet avec nouvelles tranches
 *  - PUT /paie/config-legale/{id}     → Correction de l'exercice et de ses tranches associées
 */
public class PaieConfigLegaleRequest {

    private String codeVersion;
    private String libelle;
    private Date dateDebut;
    private Date dateFin;

    // Tranches IRSA associées
    private List<IrsaRequest> tranchesIrsa;

    // Taux de Majoration associés
    private List<TauxMajorationRequest> majorations;

    // Salaire Minimum et Plafonds Sociaux
    private BigDecimal smeNational;
    private BigDecimal smeAgricole;
    private BigDecimal plafondCotisationNbSme;
    private BigDecimal plafondAvanceEmployeurPct;
    private BigDecimal quotiteCessiblePct;

    // Règles Fiscales IRSA
    private BigDecimal seuilExonerationIrsa;
    private BigDecimal minimumPerceptionIrsa;
    private BigDecimal deductionChargeFamille;
    private String arrondiBaseIrsa;

    // Temps de Travail
    private BigDecimal dureeHebdoLegale;
    private BigDecimal diviseurMensuelHeures;
    private BigDecimal seuilHsTranche1;
    private BigDecimal plafondHsHebdo;

    // Congés Payés
    private BigDecimal acquisitionCongeMensuelJours;

    // Exonération fiscale Heures Supplémentaires (CGI 01.03.03-9°)
    private Boolean exonerationHsIrsaActive;
    private BigDecimal plafondMensuelHsExonerees;

    // Getters & Setters
    public String getCodeVersion() { return codeVersion; }
    public void setCodeVersion(String v) { this.codeVersion = v; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String v) { this.libelle = v; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date v) { this.dateDebut = v; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date v) { this.dateFin = v; }
    public List<IrsaRequest> getTranchesIrsa() { return tranchesIrsa; }
    public void setTranchesIrsa(List<IrsaRequest> tranchesIrsa) { this.tranchesIrsa = tranchesIrsa; }
    public List<TauxMajorationRequest> getMajorations() { return majorations; }
    public void setMajorations(List<TauxMajorationRequest> majorations) { this.majorations = majorations; }
    public BigDecimal getSmeNational() { return smeNational; }
    public void setSmeNational(BigDecimal v) { this.smeNational = v; }
    public BigDecimal getSmeAgricole() { return smeAgricole; }
    public void setSmeAgricole(BigDecimal v) { this.smeAgricole = v; }
    public BigDecimal getPlafondCotisationNbSme() { return plafondCotisationNbSme; }
    public void setPlafondCotisationNbSme(BigDecimal v) { this.plafondCotisationNbSme = v; }
    public BigDecimal getPlafondAvanceEmployeurPct() { return plafondAvanceEmployeurPct; }
    public void setPlafondAvanceEmployeurPct(BigDecimal v) { this.plafondAvanceEmployeurPct = v; }
    public BigDecimal getQuotiteCessiblePct() { return quotiteCessiblePct; }
    public void setQuotiteCessiblePct(BigDecimal v) { this.quotiteCessiblePct = v; }
    public BigDecimal getSeuilExonerationIrsa() { return seuilExonerationIrsa; }
    public void setSeuilExonerationIrsa(BigDecimal v) { this.seuilExonerationIrsa = v; }
    public BigDecimal getMinimumPerceptionIrsa() { return minimumPerceptionIrsa; }
    public void setMinimumPerceptionIrsa(BigDecimal v) { this.minimumPerceptionIrsa = v; }
    public BigDecimal getDeductionChargeFamille() { return deductionChargeFamille; }
    public void setDeductionChargeFamille(BigDecimal v) { this.deductionChargeFamille = v; }
    public String getArrondiBaseIrsa() { return arrondiBaseIrsa; }
    public void setArrondiBaseIrsa(String v) { this.arrondiBaseIrsa = v; }
    public BigDecimal getDureeHebdoLegale() { return dureeHebdoLegale; }
    public void setDureeHebdoLegale(BigDecimal v) { this.dureeHebdoLegale = v; }
    public BigDecimal getDiviseurMensuelHeures() { return diviseurMensuelHeures; }
    public void setDiviseurMensuelHeures(BigDecimal v) { this.diviseurMensuelHeures = v; }
    public BigDecimal getSeuilHsTranche1() { return seuilHsTranche1; }
    public void setSeuilHsTranche1(BigDecimal v) { this.seuilHsTranche1 = v; }
    public BigDecimal getPlafondHsHebdo() { return plafondHsHebdo; }
    public void setPlafondHsHebdo(BigDecimal v) { this.plafondHsHebdo = v; }
    public BigDecimal getAcquisitionCongeMensuelJours() { return acquisitionCongeMensuelJours; }
    public void setAcquisitionCongeMensuelJours(BigDecimal v) { this.acquisitionCongeMensuelJours = v; }
    public Boolean getExonerationHsIrsaActive() { return exonerationHsIrsaActive; }
    public void setExonerationHsIrsaActive(Boolean v) { this.exonerationHsIrsaActive = v; }
    public BigDecimal getPlafondMensuelHsExonerees() { return plafondMensuelHsExonerees; }
    public void setPlafondMensuelHsExonerees(BigDecimal v) { this.plafondMensuelHsExonerees = v; }
}
