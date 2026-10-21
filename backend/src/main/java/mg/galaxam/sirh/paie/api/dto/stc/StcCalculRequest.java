package mg.galaxam.sirh.paie.api.dto.stc;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.sql.Date;

/**
 * Paramètres pour la simulation ou la validation d'un Solde de Tout Compte.
 */
public class StcCalculRequest {

    @NotNull(message = "L'identifiant de l'employé est obligatoire.")
    private Integer idEmploye;

    private Integer idPeriodePaie;

    @NotNull(message = "La date de départ effectif est obligatoire.")
    private Date dateDepart;

    @NotNull(message = "Le motif de rupture est obligatoire.")
    private String motifRupture; // DEMISSION, LICENCIEMENT, FIN_CONTRAT, RETRAITE, RUPTURE_CONVENTIONNELLE, AUTRE

    // Surcharges optionnelles par le gestionnaire RH
    private BigDecimal joursTravaillesDernierMois;
    private String justificationSalaireDernierMois;
    private BigDecimal montantBulletinDejaPaye;
    private Date datePaiementBulletin;
    private String referencePaiementBulletin;
    private BigDecimal soldeConges; // Si null, utilise le solde réel de VSoldeCongeEmploye
    private BigDecimal moisPreavis; // 0, 1, 2 ou 3 mois
    private BigDecimal indemnitePreavis; // Surcharge du montant
    private BigDecimal indemniteLicenciement; // Surcharge du montant
    private BigDecimal remunerationReferenceLicenciement; // Dernière rémunération avec accessoires, hors frais
    private String justificationIndemnites; // Référence de la décision et du calcul RH
    private BigDecimal autresIndemnites;
    private String motifAutresIndemnites;

    private BigDecimal autresDeductions;
    private String motifAutresDeductions;

    private String modeReglement = "VIREMENT";
    private String commentaire;
    private boolean simulation = true;
    private boolean desactiverEmploye = false;

    public StcCalculRequest() {}

    // Getters & Setters
    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public Date getDateDepart() { return dateDepart; }
    public void setDateDepart(Date dateDepart) { this.dateDepart = dateDepart; }

    public String getMotifRupture() { return motifRupture; }
    public void setMotifRupture(String motifRupture) { this.motifRupture = motifRupture; }

    public BigDecimal getJoursTravaillesDernierMois() { return joursTravaillesDernierMois; }
    public void setJoursTravaillesDernierMois(BigDecimal joursTravaillesDernierMois) { this.joursTravaillesDernierMois = joursTravaillesDernierMois; }
    public String getJustificationSalaireDernierMois() { return justificationSalaireDernierMois; }
    public void setJustificationSalaireDernierMois(String justificationSalaireDernierMois) { this.justificationSalaireDernierMois = justificationSalaireDernierMois; }
    public BigDecimal getMontantBulletinDejaPaye() { return montantBulletinDejaPaye; }
    public void setMontantBulletinDejaPaye(BigDecimal montantBulletinDejaPaye) { this.montantBulletinDejaPaye = montantBulletinDejaPaye; }
    public Date getDatePaiementBulletin() { return datePaiementBulletin; }
    public void setDatePaiementBulletin(Date datePaiementBulletin) { this.datePaiementBulletin = datePaiementBulletin; }
    public String getReferencePaiementBulletin() { return referencePaiementBulletin; }
    public void setReferencePaiementBulletin(String referencePaiementBulletin) { this.referencePaiementBulletin = referencePaiementBulletin; }

    public BigDecimal getSoldeConges() { return soldeConges; }
    public void setSoldeConges(BigDecimal soldeConges) { this.soldeConges = soldeConges; }

    public BigDecimal getMoisPreavis() { return moisPreavis; }
    public void setMoisPreavis(BigDecimal moisPreavis) { this.moisPreavis = moisPreavis; }

    public BigDecimal getIndemnitePreavis() { return indemnitePreavis; }
    public void setIndemnitePreavis(BigDecimal indemnitePreavis) { this.indemnitePreavis = indemnitePreavis; }

    public BigDecimal getIndemniteLicenciement() { return indemniteLicenciement; }
    public void setIndemniteLicenciement(BigDecimal indemniteLicenciement) { this.indemniteLicenciement = indemniteLicenciement; }
    public BigDecimal getRemunerationReferenceLicenciement() { return remunerationReferenceLicenciement; }
    public void setRemunerationReferenceLicenciement(BigDecimal remunerationReferenceLicenciement) { this.remunerationReferenceLicenciement = remunerationReferenceLicenciement; }
    public String getJustificationIndemnites() { return justificationIndemnites; }
    public void setJustificationIndemnites(String justificationIndemnites) { this.justificationIndemnites = justificationIndemnites; }

    public BigDecimal getAutresIndemnites() { return autresIndemnites; }
    public void setAutresIndemnites(BigDecimal autresIndemnites) { this.autresIndemnites = autresIndemnites; }

    public String getMotifAutresIndemnites() { return motifAutresIndemnites; }
    public void setMotifAutresIndemnites(String motifAutresIndemnites) { this.motifAutresIndemnites = motifAutresIndemnites; }

    public BigDecimal getAutresDeductions() { return autresDeductions; }
    public void setAutresDeductions(BigDecimal autresDeductions) { this.autresDeductions = autresDeductions; }

    public String getMotifAutresDeductions() { return motifAutresDeductions; }
    public void setMotifAutresDeductions(String motifAutresDeductions) { this.motifAutresDeductions = motifAutresDeductions; }

    public String getModeReglement() { return modeReglement; }
    public void setModeReglement(String modeReglement) { this.modeReglement = modeReglement; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public boolean isSimulation() { return simulation; }
    public void setSimulation(boolean simulation) { this.simulation = simulation; }

    public boolean isDesactiverEmploye() { return desactiverEmploye; }
    public void setDesactiverEmploye(boolean desactiverEmploye) { this.desactiverEmploye = desactiverEmploye; }
}
