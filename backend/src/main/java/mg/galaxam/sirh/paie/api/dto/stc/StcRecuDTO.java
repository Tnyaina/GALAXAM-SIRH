package mg.galaxam.sirh.paie.api.dto.stc;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

/**
 * Reçu détaillé du solde de tout compte. Sa remise ne constate pas le paiement.
 */
public class StcRecuDTO {

    private String numeroRecu;
    private Timestamp dateEmission;
    private String statut;
    private boolean identiteHistoriqueDisponible;
    private boolean employeurHistoriqueDisponible;
    private Date dateReglement;
    private String referenceReglement;

    // Entreprise
    private String entrepriseNom = "GALAXAM LOGISTICS";
    private String entrepriseNif = null;
    private String entrepriseStat = null;
    private String entrepriseCnaps = null;
    private String entrepriseAdresse = null;

    // Employé
    private Integer idEmploye;
    private Integer idContrat;
    private String matricule;
    private String nomComplet;
    private String cinNumero;
    private Date cinDateDelivrance;
    private String cinLieuDelivrance;
    private String numeroCnaps;
    private String intitulePoste;
    private String departement;
    private String site;
    private Date dateEmbauche;
    private Date dateDepart;
    private String motifRupture;
    private String ancienneteTexte;

    // Décomposition financière
    private BigDecimal salaireProrata = BigDecimal.ZERO;
    private Integer idBulletinMensuel;
    private BigDecimal netBulletinMensuel = BigDecimal.ZERO;
    private BigDecimal montantBulletinDejaPaye = BigDecimal.ZERO;
    private BigDecimal soldeBulletinARegler = BigDecimal.ZERO;
    private Date datePaiementBulletin;
    private String referencePaiementBulletin;
    private BigDecimal soldeCongesRestant = BigDecimal.ZERO;
    private BigDecimal tauxJournalierConge = BigDecimal.ZERO;
    private BigDecimal indemniteCongesPayes = BigDecimal.ZERO;
    private BigDecimal moisPreavis = BigDecimal.ZERO;
    private BigDecimal indemnitePreavis = BigDecimal.ZERO;
    private BigDecimal indemniteLicenciement = BigDecimal.ZERO;
    private BigDecimal autresIndemnites = BigDecimal.ZERO;
    private BigDecimal totalBrut = BigDecimal.ZERO;

    private BigDecimal cotisationsSociales = BigDecimal.ZERO;
    private BigDecimal retenueIrsa = BigDecimal.ZERO;
    private BigDecimal netImposable = BigDecimal.ZERO;

    private BigDecimal soldePretsApure = BigDecimal.ZERO;
    private BigDecimal soldeAvancesApure = BigDecimal.ZERO;
    private BigDecimal autresDeductions = BigDecimal.ZERO;
    private BigDecimal totalRetenues = BigDecimal.ZERO;

    private BigDecimal netAPayer = BigDecimal.ZERO;
    private String netEnLettres;
    private String modeReglement = "VIREMENT";

    // Mentions légales
    private String mentionLiberatoire;
    private String formuleManuscriteObligatoire = "Reçu sous réserve de mes droits";

    public StcRecuDTO() {}

    // Getters & Setters
    public String getNumeroRecu() { return numeroRecu; }
    public void setNumeroRecu(String numeroRecu) { this.numeroRecu = numeroRecu; }

    public Timestamp getDateEmission() { return dateEmission; }
    public void setDateEmission(Timestamp dateEmission) { this.dateEmission = dateEmission; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public boolean isIdentiteHistoriqueDisponible() { return identiteHistoriqueDisponible; }
    public void setIdentiteHistoriqueDisponible(boolean disponible) { this.identiteHistoriqueDisponible = disponible; }
    public boolean isEmployeurHistoriqueDisponible() { return employeurHistoriqueDisponible; }
    public void setEmployeurHistoriqueDisponible(boolean disponible) { this.employeurHistoriqueDisponible = disponible; }
    public Date getDateReglement() { return dateReglement; }
    public void setDateReglement(Date dateReglement) { this.dateReglement = dateReglement; }
    public String getReferenceReglement() { return referenceReglement; }
    public void setReferenceReglement(String referenceReglement) { this.referenceReglement = referenceReglement; }

    public String getEntrepriseNom() { return entrepriseNom; }
    public void setEntrepriseNom(String entrepriseNom) { this.entrepriseNom = entrepriseNom; }

    public String getEntrepriseNif() { return entrepriseNif; }
    public void setEntrepriseNif(String entrepriseNif) { this.entrepriseNif = entrepriseNif; }

    public String getEntrepriseStat() { return entrepriseStat; }
    public void setEntrepriseStat(String entrepriseStat) { this.entrepriseStat = entrepriseStat; }

    public String getEntrepriseCnaps() { return entrepriseCnaps; }
    public void setEntrepriseCnaps(String entrepriseCnaps) { this.entrepriseCnaps = entrepriseCnaps; }

    public String getEntrepriseAdresse() { return entrepriseAdresse; }
    public void setEntrepriseAdresse(String entrepriseAdresse) { this.entrepriseAdresse = entrepriseAdresse; }

    private String sourcePointage;

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdContrat() { return idContrat; }
    public void setIdContrat(Integer idContrat) { this.idContrat = idContrat; }

    public String getSourcePointage() { return sourcePointage; }
    public void setSourcePointage(String sourcePointage) { this.sourcePointage = sourcePointage; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public String getCinNumero() { return cinNumero; }
    public void setCinNumero(String cinNumero) { this.cinNumero = cinNumero; }

    public Date getCinDateDelivrance() { return cinDateDelivrance; }
    public void setCinDateDelivrance(Date cinDateDelivrance) { this.cinDateDelivrance = cinDateDelivrance; }

    public String getCinLieuDelivrance() { return cinLieuDelivrance; }
    public void setCinLieuDelivrance(String cinLieuDelivrance) { this.cinLieuDelivrance = cinLieuDelivrance; }

    public String getNumeroCnaps() { return numeroCnaps; }
    public void setNumeroCnaps(String numeroCnaps) { this.numeroCnaps = numeroCnaps; }

    public String getIntitulePoste() { return intitulePoste; }
    public void setIntitulePoste(String intitulePoste) { this.intitulePoste = intitulePoste; }

    public String getDepartement() { return departement; }
    public void setDepartement(String departement) { this.departement = departement; }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public Date getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(Date dateEmbauche) { this.dateEmbauche = dateEmbauche; }

    public Date getDateDepart() { return dateDepart; }
    public void setDateDepart(Date dateDepart) { this.dateDepart = dateDepart; }

    public String getMotifRupture() { return motifRupture; }
    public void setMotifRupture(String motifRupture) { this.motifRupture = motifRupture; }

    public String getAncienneteTexte() { return ancienneteTexte; }
    public void setAncienneteTexte(String ancienneteTexte) { this.ancienneteTexte = ancienneteTexte; }

    public BigDecimal getSalaireProrata() { return salaireProrata; }
    public void setSalaireProrata(BigDecimal salaireProrata) { this.salaireProrata = salaireProrata; }
    public Integer getIdBulletinMensuel() { return idBulletinMensuel; }
    public void setIdBulletinMensuel(Integer idBulletinMensuel) { this.idBulletinMensuel = idBulletinMensuel; }
    public BigDecimal getNetBulletinMensuel() { return netBulletinMensuel; }
    public void setNetBulletinMensuel(BigDecimal netBulletinMensuel) { this.netBulletinMensuel = netBulletinMensuel; }
    public BigDecimal getMontantBulletinDejaPaye() { return montantBulletinDejaPaye; }
    public void setMontantBulletinDejaPaye(BigDecimal montantBulletinDejaPaye) { this.montantBulletinDejaPaye = montantBulletinDejaPaye; }
    public BigDecimal getSoldeBulletinARegler() { return soldeBulletinARegler; }
    public void setSoldeBulletinARegler(BigDecimal soldeBulletinARegler) { this.soldeBulletinARegler = soldeBulletinARegler; }
    public Date getDatePaiementBulletin() { return datePaiementBulletin; }
    public void setDatePaiementBulletin(Date datePaiementBulletin) { this.datePaiementBulletin = datePaiementBulletin; }
    public String getReferencePaiementBulletin() { return referencePaiementBulletin; }
    public void setReferencePaiementBulletin(String referencePaiementBulletin) { this.referencePaiementBulletin = referencePaiementBulletin; }

    public BigDecimal getSoldeCongesRestant() { return soldeCongesRestant; }
    public void setSoldeCongesRestant(BigDecimal soldeCongesRestant) { this.soldeCongesRestant = soldeCongesRestant; }

    public BigDecimal getTauxJournalierConge() { return tauxJournalierConge; }
    public void setTauxJournalierConge(BigDecimal tauxJournalierConge) { this.tauxJournalierConge = tauxJournalierConge; }

    public BigDecimal getIndemniteCongesPayes() { return indemniteCongesPayes; }
    public void setIndemniteCongesPayes(BigDecimal indemniteCongesPayes) { this.indemniteCongesPayes = indemniteCongesPayes; }

    public BigDecimal getMoisPreavis() { return moisPreavis; }
    public void setMoisPreavis(BigDecimal moisPreavis) { this.moisPreavis = moisPreavis; }

    public BigDecimal getIndemnitePreavis() { return indemnitePreavis; }
    public void setIndemnitePreavis(BigDecimal indemnitePreavis) { this.indemnitePreavis = indemnitePreavis; }

    public BigDecimal getIndemniteLicenciement() { return indemniteLicenciement; }
    public void setIndemniteLicenciement(BigDecimal indemniteLicenciement) { this.indemniteLicenciement = indemniteLicenciement; }

    public BigDecimal getAutresIndemnites() { return autresIndemnites; }
    public void setAutresIndemnites(BigDecimal autresIndemnites) { this.autresIndemnites = autresIndemnites; }

    public BigDecimal getTotalBrut() { return totalBrut; }
    public void setTotalBrut(BigDecimal totalBrut) { this.totalBrut = totalBrut; }

    public BigDecimal getCotisationsSociales() { return cotisationsSociales; }
    public void setCotisationsSociales(BigDecimal cotisationsSociales) { this.cotisationsSociales = cotisationsSociales; }

    public BigDecimal getRetenueIrsa() { return retenueIrsa; }
    public void setRetenueIrsa(BigDecimal retenueIrsa) { this.retenueIrsa = retenueIrsa; }

    public BigDecimal getNetImposable() { return netImposable; }
    public void setNetImposable(BigDecimal netImposable) { this.netImposable = netImposable; }

    public BigDecimal getSoldePretsApure() { return soldePretsApure; }
    public void setSoldePretsApure(BigDecimal soldePretsApure) { this.soldePretsApure = soldePretsApure; }

    public BigDecimal getSoldeAvancesApure() { return soldeAvancesApure; }
    public void setSoldeAvancesApure(BigDecimal soldeAvancesApure) { this.soldeAvancesApure = soldeAvancesApure; }

    public BigDecimal getAutresDeductions() { return autresDeductions; }
    public void setAutresDeductions(BigDecimal autresDeductions) { this.autresDeductions = autresDeductions; }

    public BigDecimal getTotalRetenues() { return totalRetenues; }
    public void setTotalRetenues(BigDecimal totalRetenues) { this.totalRetenues = totalRetenues; }

    public BigDecimal getNetAPayer() { return netAPayer; }
    public void setNetAPayer(BigDecimal netAPayer) { this.netAPayer = netAPayer; }

    public String getNetEnLettres() { return netEnLettres; }
    public void setNetEnLettres(String netEnLettres) { this.netEnLettres = netEnLettres; }

    public String getModeReglement() { return modeReglement; }
    public void setModeReglement(String modeReglement) { this.modeReglement = modeReglement; }

    public String getMentionLiberatoire() { return mentionLiberatoire; }
    public void setMentionLiberatoire(String mentionLiberatoire) { this.mentionLiberatoire = mentionLiberatoire; }

    public String getFormuleManuscriteObligatoire() { return formuleManuscriteObligatoire; }
    public void setFormuleManuscriteObligatoire(String formuleManuscriteObligatoire) { this.formuleManuscriteObligatoire = formuleManuscriteObligatoire; }

    private BigDecimal montantTotalRegle = BigDecimal.ZERO;
    private BigDecimal soldeRestantAPayer = BigDecimal.ZERO;
    private boolean estIntegralementRegle = false;
    private java.util.List<StcReglementDTO> reglements = new java.util.ArrayList<>();

    public BigDecimal getMontantTotalRegle() { return montantTotalRegle; }
    public void setMontantTotalRegle(BigDecimal montantTotalRegle) { this.montantTotalRegle = montantTotalRegle; }

    public BigDecimal getSoldeRestantAPayer() { return soldeRestantAPayer; }
    public void setSoldeRestantAPayer(BigDecimal soldeRestantAPayer) { this.soldeRestantAPayer = soldeRestantAPayer; }

    public boolean isEstIntegralementRegle() { return estIntegralementRegle; }
    public void setEstIntegralementRegle(boolean estIntegralementRegle) { this.estIntegralementRegle = estIntegralementRegle; }

    public java.util.List<StcReglementDTO> getReglements() { return reglements; }
    public void setReglements(java.util.List<StcReglementDTO> reglements) { this.reglements = reglements; }
}
