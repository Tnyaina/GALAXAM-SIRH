package mg.galaxam.sirh.paie.api.dto.stc;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO complet représentant le dossier de sortie d'un salarié,
 * ses données sources en lecture et sa checklist dynamique.
 */
public class DossierSortieDTO {

    private Integer idDossierSortie;
    private Integer idEmploye;
    private String matricule;
    private String nomComplet;
    private String intitulePoste;
    private String departement;
    private String site;
    private String codeSite;
    private Date dateEmbauche;
    private Integer ancienneteAnnees;
    private Integer ancienneteMois;
    private BigDecimal salaireBase;
    private BigDecimal tauxHoraire;

    private Integer idContrat;
    private String typeContrat;
    private Date dateDebutContrat;
    private Date dateFinContrat;

    private Date dateDepart;
    private String motifRupture;
    private String statutDossier; // EN_PREPARATION, PRET_A_VALIDER, VALIDE, REGLE, CLOTURE, ANNULE

    // Données de pointage en lecture seule
    private String sourcePointage;
    private boolean pointageGele;
    private BigDecimal joursPointageReels;
    private BigDecimal totalHeuresPointage;
    private BigDecimal joursSousContrat;

    // Données de congés en lecture seule
    private BigDecimal soldeCongesRestant;
    private BigDecimal tauxJournalierConge;
    private BigDecimal iccpEstimee;
    private int demandesCongesEnAttente;

    // Données de paie & trésorerie en lecture seule
    private Integer idBulletinMensuel;
    private String statutBulletinMensuel;
    private BigDecimal netBulletinMensuel;
    private BigDecimal montantBulletinDejaPaye;
    private BigDecimal soldeBulletinARegler;
    private BigDecimal avancesEnAttente;
    private BigDecimal capitalRestantPrets;
    private int primesOuVariablesEnAttente;

    // Décision sur le prêt
    private String decisionPret; // MAINTIEN_ECHEANCIER, REMBOURSEMENT_SEPARE, RETENUE_STC_AUTORISEE, CONTENTIEUX_RECOUVREMENT
    private String justificationDecisionPret;
    private BigDecimal montantPretRetenu;

    // Documents et délivrances
    private boolean certificatDelivre;
    private Date dateDelivranceCertificat;
    private String modeRemiseCertificat;
    private boolean recuStcRemis;
    private Date dateRemiseRecu;

    // Formalités d'offboarding (restitution badge, matériel, accès)
    private Map<String, Object> checklistOffboarding;

    // Données de calcul / STC lié
    private Integer idStc;
    private String numeroRecu;
    private BigDecimal netAPayerStc;
    private Date dateReglement;
    private String referenceReglement;

    // Checklist dynamique
    private List<ChecklistItemDTO> checklist = new ArrayList<>();
    private boolean pretAValider;

    // Version et simulation
    private Integer version;
    private String referenceSimulation;
    private Map<String, Object> donneesPreparation;

    // Traçabilité
    private Integer creePar;
    private Integer cloturePar;
    private Timestamp dateCloture;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Getters and Setters
    public Integer getIdDossierSortie() { return idDossierSortie; }
    public void setIdDossierSortie(Integer idDossierSortie) { this.idDossierSortie = idDossierSortie; }

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

    public String getCodeSite() { return codeSite; }
    public void setCodeSite(String codeSite) { this.codeSite = codeSite; }

    public Date getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(Date dateEmbauche) { this.dateEmbauche = dateEmbauche; }

    public Integer getAncienneteAnnees() { return ancienneteAnnees; }
    public void setAncienneteAnnees(Integer ancienneteAnnees) { this.ancienneteAnnees = ancienneteAnnees; }

    public Integer getAncienneteMois() { return ancienneteMois; }
    public void setAncienneteMois(Integer ancienneteMois) { this.ancienneteMois = ancienneteMois; }

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public BigDecimal getTauxHoraire() { return tauxHoraire; }
    public void setTauxHoraire(BigDecimal tauxHoraire) { this.tauxHoraire = tauxHoraire; }

    public Integer getIdContrat() { return idContrat; }
    public void setIdContrat(Integer idContrat) { this.idContrat = idContrat; }

    public String getTypeContrat() { return typeContrat; }
    public void setTypeContrat(String typeContrat) { this.typeContrat = typeContrat; }

    public Date getDateDebutContrat() { return dateDebutContrat; }
    public void setDateDebutContrat(Date dateDebutContrat) { this.dateDebutContrat = dateDebutContrat; }

    public Date getDateFinContrat() { return dateFinContrat; }
    public void setDateFinContrat(Date dateFinContrat) { this.dateFinContrat = dateFinContrat; }

    public Date getDateDepart() { return dateDepart; }
    public void setDateDepart(Date dateDepart) { this.dateDepart = dateDepart; }

    public String getMotifRupture() { return motifRupture; }
    public void setMotifRupture(String motifRupture) { this.motifRupture = motifRupture; }

    public String getStatutDossier() { return statutDossier; }
    public void setStatutDossier(String statutDossier) { this.statutDossier = statutDossier; }

    public String getSourcePointage() { return sourcePointage; }
    public void setSourcePointage(String sourcePointage) { this.sourcePointage = sourcePointage; }

    public boolean isPointageGele() { return pointageGele; }
    public void setPointageGele(boolean pointageGele) { this.pointageGele = pointageGele; }

    public BigDecimal getJoursPointageReels() { return joursPointageReels; }
    public void setJoursPointageReels(BigDecimal joursPointageReels) { this.joursPointageReels = joursPointageReels; }

    public BigDecimal getTotalHeuresPointage() { return totalHeuresPointage; }
    public void setTotalHeuresPointage(BigDecimal totalHeuresPointage) { this.totalHeuresPointage = totalHeuresPointage; }

    public BigDecimal getJoursSousContrat() { return joursSousContrat; }
    public void setJoursSousContrat(BigDecimal joursSousContrat) { this.joursSousContrat = joursSousContrat; }

    public BigDecimal getSoldeCongesRestant() { return soldeCongesRestant; }
    public void setSoldeCongesRestant(BigDecimal soldeCongesRestant) { this.soldeCongesRestant = soldeCongesRestant; }

    public BigDecimal getTauxJournalierConge() { return tauxJournalierConge; }
    public void setTauxJournalierConge(BigDecimal tauxJournalierConge) { this.tauxJournalierConge = tauxJournalierConge; }

    public BigDecimal getIccpEstimee() { return iccpEstimee; }
    public void setIccpEstimee(BigDecimal iccpEstimee) { this.iccpEstimee = iccpEstimee; }

    public int getDemandesCongesEnAttente() { return demandesCongesEnAttente; }
    public void setDemandesCongesEnAttente(int demandesCongesEnAttente) { this.demandesCongesEnAttente = demandesCongesEnAttente; }

    public Integer getIdBulletinMensuel() { return idBulletinMensuel; }
    public void setIdBulletinMensuel(Integer idBulletinMensuel) { this.idBulletinMensuel = idBulletinMensuel; }

    public String getStatutBulletinMensuel() { return statutBulletinMensuel; }
    public void setStatutBulletinMensuel(String statutBulletinMensuel) { this.statutBulletinMensuel = statutBulletinMensuel; }

    public BigDecimal getNetBulletinMensuel() { return netBulletinMensuel; }
    public void setNetBulletinMensuel(BigDecimal netBulletinMensuel) { this.netBulletinMensuel = netBulletinMensuel; }

    public BigDecimal getMontantBulletinDejaPaye() { return montantBulletinDejaPaye; }
    public void setMontantBulletinDejaPaye(BigDecimal montantBulletinDejaPaye) { this.montantBulletinDejaPaye = montantBulletinDejaPaye; }

    public BigDecimal getSoldeBulletinARegler() { return soldeBulletinARegler; }
    public void setSoldeBulletinARegler(BigDecimal soldeBulletinARegler) { this.soldeBulletinARegler = soldeBulletinARegler; }

    public BigDecimal getAvancesEnAttente() { return avancesEnAttente; }
    public void setAvancesEnAttente(BigDecimal avancesEnAttente) { this.avancesEnAttente = avancesEnAttente; }

    public BigDecimal getCapitalRestantPrets() { return capitalRestantPrets; }
    public void setCapitalRestantPrets(BigDecimal capitalRestantPrets) { this.capitalRestantPrets = capitalRestantPrets; }

    public int getPrimesOuVariablesEnAttente() { return primesOuVariablesEnAttente; }
    public void setPrimesOuVariablesEnAttente(int primesOuVariablesEnAttente) { this.primesOuVariablesEnAttente = primesOuVariablesEnAttente; }

    public String getDecisionPret() { return decisionPret; }
    public void setDecisionPret(String decisionPret) { this.decisionPret = decisionPret; }

    public String getJustificationDecisionPret() { return justificationDecisionPret; }
    public void setJustificationDecisionPret(String justificationDecisionPret) { this.justificationDecisionPret = justificationDecisionPret; }

    public BigDecimal getMontantPretRetenu() { return montantPretRetenu; }
    public void setMontantPretRetenu(BigDecimal montantPretRetenu) { this.montantPretRetenu = montantPretRetenu; }

    public boolean isCertificatDelivre() { return certificatDelivre; }
    public void setCertificatDelivre(boolean certificatDelivre) { this.certificatDelivre = certificatDelivre; }

    public Date getDateDelivranceCertificat() { return dateDelivranceCertificat; }
    public void setDateDelivranceCertificat(Date dateDelivranceCertificat) { this.dateDelivranceCertificat = dateDelivranceCertificat; }

    public String getModeRemiseCertificat() { return modeRemiseCertificat; }
    public void setModeRemiseCertificat(String modeRemiseCertificat) { this.modeRemiseCertificat = modeRemiseCertificat; }

    public boolean isRecuStcRemis() { return recuStcRemis; }
    public void setRecuStcRemis(boolean recuStcRemis) { this.recuStcRemis = recuStcRemis; }

    public Date getDateRemiseRecu() { return dateRemiseRecu; }
    public void setDateRemiseRecu(Date dateRemiseRecu) { this.dateRemiseRecu = dateRemiseRecu; }

    public Map<String, Object> getChecklistOffboarding() { return checklistOffboarding; }
    public void setChecklistOffboarding(Map<String, Object> checklistOffboarding) { this.checklistOffboarding = checklistOffboarding; }

    public Integer getIdStc() { return idStc; }
    public void setIdStc(Integer idStc) { this.idStc = idStc; }

    public String getNumeroRecu() { return numeroRecu; }
    public void setNumeroRecu(String numeroRecu) { this.numeroRecu = numeroRecu; }

    public BigDecimal getNetAPayerStc() { return netAPayerStc; }
    public void setNetAPayerStc(BigDecimal netAPayerStc) { this.netAPayerStc = netAPayerStc; }

    public Date getDateReglement() { return dateReglement; }
    public void setDateReglement(Date dateReglement) { this.dateReglement = dateReglement; }

    public String getReferenceReglement() { return referenceReglement; }
    public void setReferenceReglement(String referenceReglement) { this.referenceReglement = referenceReglement; }

    public List<ChecklistItemDTO> getChecklist() { return checklist; }
    public void setChecklist(List<ChecklistItemDTO> checklist) { this.checklist = checklist; }

    public boolean isPretAValider() { return pretAValider; }
    public void setPretAValider(boolean pretAValider) { this.pretAValider = pretAValider; }

    public Integer getCreePar() { return creePar; }
    public void setCreePar(Integer creePar) { this.creePar = creePar; }

    public Integer getCloturePar() { return cloturePar; }
    public void setCloturePar(Integer cloturePar) { this.cloturePar = cloturePar; }

    public Timestamp getDateCloture() { return dateCloture; }
    public void setDateCloture(Timestamp dateCloture) { this.dateCloture = dateCloture; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getReferenceSimulation() { return referenceSimulation; }
    public void setReferenceSimulation(String referenceSimulation) { this.referenceSimulation = referenceSimulation; }

    public Map<String, Object> getDonneesPreparation() { return donneesPreparation; }
    public void setDonneesPreparation(Map<String, Object> donneesPreparation) { this.donneesPreparation = donneesPreparation; }

    private BigDecimal montantTotalRegle = BigDecimal.ZERO;
    private BigDecimal soldeRestantAPayer = BigDecimal.ZERO;
    private boolean estIntegralementRegle = false;
    private List<StcReglementDTO> reglements = new ArrayList<>();

    public BigDecimal getMontantTotalRegle() { return montantTotalRegle; }
    public void setMontantTotalRegle(BigDecimal montantTotalRegle) { this.montantTotalRegle = montantTotalRegle; }

    public BigDecimal getSoldeRestantAPayer() { return soldeRestantAPayer; }
    public void setSoldeRestantAPayer(BigDecimal soldeRestantAPayer) { this.soldeRestantAPayer = soldeRestantAPayer; }

    public boolean isEstIntegralementRegle() { return estIntegralementRegle; }
    public void setEstIntegralementRegle(boolean estIntegralementRegle) { this.estIntegralementRegle = estIntegralementRegle; }

    public List<StcReglementDTO> getReglements() { return reglements; }
    public void setReglements(List<StcReglementDTO> reglements) { this.reglements = reglements; }
}
