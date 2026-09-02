package mg.galaxam.sirh.api.dto.paieconfig;

import java.math.BigDecimal;
import java.sql.Date;

/**
 * DTO pour créer ou revaloriser un exercice fiscal (paie_config_legale).
 *
 * Deux opérations possibles via le contrôleur :
 *  - POST /paie/config-legale         → Créer un nouvel exercice (création initiale)
 *  - POST /paie/config-legale/revaloriser → Clôture l'exercice actif + crée le suivant à la date d'effet
 */
public class PaieConfigLegaleRequest {

    private String codeVersion;
    private String libelle;
    private Date dateDebut;
    private Date dateFin;

    // Salaire Minimum et Plafonds Sociaux
    private BigDecimal smeNational;
    private BigDecimal smeAgricole;
    private BigDecimal plafondCotisationNbSme;

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
    private Integer diviseurValorisationCongeJours;

    // Getters & Setters
    public String getCodeVersion() { return codeVersion; }
    public void setCodeVersion(String v) { this.codeVersion = v; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String v) { this.libelle = v; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date v) { this.dateDebut = v; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date v) { this.dateFin = v; }
    public BigDecimal getSmeNational() { return smeNational; }
    public void setSmeNational(BigDecimal v) { this.smeNational = v; }
    public BigDecimal getSmeAgricole() { return smeAgricole; }
    public void setSmeAgricole(BigDecimal v) { this.smeAgricole = v; }
    public BigDecimal getPlafondCotisationNbSme() { return plafondCotisationNbSme; }
    public void setPlafondCotisationNbSme(BigDecimal v) { this.plafondCotisationNbSme = v; }
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
    public Integer getDiviseurValorisationCongeJours() { return diviseurValorisationCongeJours; }
    public void setDiviseurValorisationCongeJours(Integer v) { this.diviseurValorisationCongeJours = v; }
}
