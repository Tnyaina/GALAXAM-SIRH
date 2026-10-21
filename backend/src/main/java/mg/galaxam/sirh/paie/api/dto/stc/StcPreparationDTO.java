package mg.galaxam.sirh.paie.api.dto.stc;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO renvoyé lors de l'appel préparatoire au STC.
 * Contient toutes les données administratives, contractuelles et financières
 * pour pré-remplir l'écran STC sans aucune ressaisie.
 */
public class StcPreparationDTO {

    private Integer idEmploye;
    private String matricule;
    private String nomComplet;
    private String cinNumero;
    private Date cinDateDelivrance;
    private String cinLieuDelivrance;
    private String numeroCnaps;
    private String intitulePoste;
    private String departement;
    private String site;
    private String codeSite;
    private String categorieProfessionnelle;
    private String regimeFiscal = "IRSA";
    private int nbEnfantsACharge = 0;
    private Date dateEmbauche;
    private Date dateDepart;
    private String motifRupture;

    private int ancienneteAnnees;
    private int ancienneteMois;

    private BigDecimal salaireBase = BigDecimal.ZERO;
    private BigDecimal tauxHoraire = BigDecimal.ZERO;

    // Prorata dernier mois
    private BigDecimal joursOuvrablesMois = new BigDecimal("22.0");
    private BigDecimal joursTravaillesEstimes = BigDecimal.ZERO;
    private BigDecimal salaireProrataEstime = BigDecimal.ZERO;

    // Congés payés & ICCP
    private BigDecimal soldeCongesRestant = BigDecimal.ZERO;
    private BigDecimal tauxJournalierConge = BigDecimal.ZERO;
    private BigDecimal iccpEstimee = BigDecimal.ZERO;

    // Indemnités de rupture suggérées selon barème légal / ancienneté
    private BigDecimal moisPreavisSuggere = BigDecimal.ZERO;
    private BigDecimal indemnitePreavisEstimee = BigDecimal.ZERO;
    private BigDecimal indemniteLicenciementEstimee = BigDecimal.ZERO;

    // Engagements financiers à apurer
    private BigDecimal avancesEnAttente = BigDecimal.ZERO;

    // Période de paie de rattachement
    private Integer idPeriodePaie;
    private String codePeriodePaie;

    // Métadonnées contrat & source pointage
    private Integer idContrat;
    private String sourcePointage = "ESTIMATION_CALENDAIRE"; // "POINTAGE_REEL" ou "ESTIMATION_CALENDAIRE"
    private BigDecimal joursPointageReels;
    private BigDecimal totalHeuresPointage;
    private boolean pointageGele;
    private String statutBulletinMensuel;
    private Integer idBulletinMensuel;
    private BigDecimal netBulletinMensuel;
    private List<String> controlesApplication = new ArrayList<>();

    public StcPreparationDTO() {}

    // Getters & Setters
    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public String getIntitulePoste() { return intitulePoste; }
    public void setIntitulePoste(String intitulePoste) { this.intitulePoste = intitulePoste; }

    public String getDepartement() { return departement; }
    public void setDepartement(String departement) { this.departement = departement; }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public String getCategorieProfessionnelle() { return categorieProfessionnelle; }
    public void setCategorieProfessionnelle(String categorieProfessionnelle) { this.categorieProfessionnelle = categorieProfessionnelle; }
    public String getRegimeFiscal() { return regimeFiscal; }
    public void setRegimeFiscal(String regimeFiscal) { this.regimeFiscal = regimeFiscal; }

    public Date getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(Date dateEmbauche) { this.dateEmbauche = dateEmbauche; }

    public Date getDateDepart() { return dateDepart; }
    public void setDateDepart(Date dateDepart) { this.dateDepart = dateDepart; }

    public String getMotifRupture() { return motifRupture; }
    public void setMotifRupture(String motifRupture) { this.motifRupture = motifRupture; }

    public int getAncienneteAnnees() { return ancienneteAnnees; }
    public void setAncienneteAnnees(int ancienneteAnnees) { this.ancienneteAnnees = ancienneteAnnees; }

    public int getAncienneteMois() { return ancienneteMois; }
    public void setAncienneteMois(int ancienneteMois) { this.ancienneteMois = ancienneteMois; }

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public BigDecimal getTauxHoraire() { return tauxHoraire; }
    public void setTauxHoraire(BigDecimal tauxHoraire) { this.tauxHoraire = tauxHoraire; }

    public BigDecimal getJoursOuvrablesMois() { return joursOuvrablesMois; }
    public void setJoursOuvrablesMois(BigDecimal joursOuvrablesMois) { this.joursOuvrablesMois = joursOuvrablesMois; }

    public BigDecimal getJoursTravaillesEstimes() { return joursTravaillesEstimes; }
    public void setJoursTravaillesEstimes(BigDecimal joursTravaillesEstimes) { this.joursTravaillesEstimes = joursTravaillesEstimes; }

    public BigDecimal getSalaireProrataEstime() { return salaireProrataEstime; }
    public void setSalaireProrataEstime(BigDecimal salaireProrataEstime) { this.salaireProrataEstime = salaireProrataEstime; }

    public BigDecimal getSoldeCongesRestant() { return soldeCongesRestant; }
    public void setSoldeCongesRestant(BigDecimal soldeCongesRestant) { this.soldeCongesRestant = soldeCongesRestant; }

    public BigDecimal getTauxJournalierConge() { return tauxJournalierConge; }
    public void setTauxJournalierConge(BigDecimal tauxJournalierConge) { this.tauxJournalierConge = tauxJournalierConge; }

    public BigDecimal getIccpEstimee() { return iccpEstimee; }
    public void setIccpEstimee(BigDecimal iccpEstimee) { this.iccpEstimee = iccpEstimee; }

    public BigDecimal getMoisPreavisSuggere() { return moisPreavisSuggere; }
    public void setMoisPreavisSuggere(BigDecimal moisPreavisSuggere) { this.moisPreavisSuggere = moisPreavisSuggere; }

    public BigDecimal getIndemnitePreavisEstimee() { return indemnitePreavisEstimee; }
    public void setIndemnitePreavisEstimee(BigDecimal indemnitePreavisEstimee) { this.indemnitePreavisEstimee = indemnitePreavisEstimee; }

    public BigDecimal getIndemniteLicenciementEstimee() { return indemniteLicenciementEstimee; }
    public void setIndemniteLicenciementEstimee(BigDecimal indemniteLicenciementEstimee) { this.indemniteLicenciementEstimee = indemniteLicenciementEstimee; }

    public BigDecimal getAvancesEnAttente() { return avancesEnAttente; }
    public void setAvancesEnAttente(BigDecimal avancesEnAttente) { this.avancesEnAttente = avancesEnAttente; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public String getCodePeriodePaie() { return codePeriodePaie; }
    public void setCodePeriodePaie(String codePeriodePaie) { this.codePeriodePaie = codePeriodePaie; }

    public String getCodeSite() { return codeSite; }
    public void setCodeSite(String codeSite) { this.codeSite = codeSite; }

    public int getNbEnfantsACharge() { return nbEnfantsACharge; }
    public void setNbEnfantsACharge(int nbEnfantsACharge) { this.nbEnfantsACharge = nbEnfantsACharge; }

    public String getCinNumero() { return cinNumero; }
    public void setCinNumero(String cinNumero) { this.cinNumero = cinNumero; }

    public Date getCinDateDelivrance() { return cinDateDelivrance; }
    public void setCinDateDelivrance(Date cinDateDelivrance) { this.cinDateDelivrance = cinDateDelivrance; }

    public String getCinLieuDelivrance() { return cinLieuDelivrance; }
    public void setCinLieuDelivrance(String cinLieuDelivrance) { this.cinLieuDelivrance = cinLieuDelivrance; }

    public String getNumeroCnaps() { return numeroCnaps; }
    public void setNumeroCnaps(String numeroCnaps) { this.numeroCnaps = numeroCnaps; }

    public Integer getIdContrat() { return idContrat; }
    public void setIdContrat(Integer idContrat) { this.idContrat = idContrat; }

    public String getSourcePointage() { return sourcePointage; }
    public void setSourcePointage(String sourcePointage) { this.sourcePointage = sourcePointage; }

    public BigDecimal getJoursPointageReels() { return joursPointageReels; }
    public void setJoursPointageReels(BigDecimal joursPointageReels) { this.joursPointageReels = joursPointageReels; }

    public BigDecimal getTotalHeuresPointage() { return totalHeuresPointage; }
    public void setTotalHeuresPointage(BigDecimal totalHeuresPointage) { this.totalHeuresPointage = totalHeuresPointage; }
    public boolean isPointageGele() { return pointageGele; }
    public void setPointageGele(boolean pointageGele) { this.pointageGele = pointageGele; }
    public String getStatutBulletinMensuel() { return statutBulletinMensuel; }
    public void setStatutBulletinMensuel(String statutBulletinMensuel) { this.statutBulletinMensuel = statutBulletinMensuel; }
    public Integer getIdBulletinMensuel() { return idBulletinMensuel; }
    public void setIdBulletinMensuel(Integer idBulletinMensuel) { this.idBulletinMensuel = idBulletinMensuel; }
    public BigDecimal getNetBulletinMensuel() { return netBulletinMensuel; }
    public void setNetBulletinMensuel(BigDecimal netBulletinMensuel) { this.netBulletinMensuel = netBulletinMensuel; }
    public List<String> getControlesApplication() { return controlesApplication; }
    public void setControlesApplication(List<String> controlesApplication) {
        this.controlesApplication = controlesApplication == null ? new ArrayList<>() : controlesApplication;
    }
}
