package mg.galaxam.sirh.paie.api.dto.stc;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * Résultat détaillé chiffré du Solde de Tout Compte.
 */
public class StcCalculResultDTO {

    private Integer idStc;
    private Integer idBulletin;
    private String numeroRecu;

    // Identité & Contrat
    private Integer idEmploye;
    private String matricule;
    private String nomComplet;
    private String intitulePoste;
    private String departement;
    private String site;
    private Date dateEmbauche;
    private Date dateDepart;
    private String motifRupture;
    private int ancienneteAnnees;
    private int ancienneteMois;

    // Paramètres
    private BigDecimal salaireBase = BigDecimal.ZERO;
    private BigDecimal tauxHoraire = BigDecimal.ZERO;

    // 1. Dernier salaire proratisé
    private BigDecimal joursTravaillesDernierMois = BigDecimal.ZERO;
    private BigDecimal salaireProrata = BigDecimal.ZERO;

    // 2. ICCP (Congés payés)
    private BigDecimal soldeCongesRestant = BigDecimal.ZERO;
    private BigDecimal tauxJournalierConge = BigDecimal.ZERO;
    private BigDecimal indemniteCongesPayes = BigDecimal.ZERO;

    // 3. Indemnités de rupture
    private BigDecimal moisPreavis = BigDecimal.ZERO;
    private BigDecimal indemnitePreavis = BigDecimal.ZERO;
    private BigDecimal indemniteLicenciement = BigDecimal.ZERO;
    private BigDecimal autresIndemnites = BigDecimal.ZERO;
    private String motifAutresIndemnites;

    // 4. Totaux Bruts & Assiette Fiscale
    private BigDecimal totalBrutStc = BigDecimal.ZERO;
    private BigDecimal cotisationCnaps = BigDecimal.ZERO;
    private BigDecimal cotisationSante = BigDecimal.ZERO;
    private BigDecimal totalCotisationsSociales = BigDecimal.ZERO;
    private BigDecimal totalCotisationsPatronales = BigDecimal.ZERO;
    private BigDecimal netImposable = BigDecimal.ZERO;
    private BigDecimal irsaNet = BigDecimal.ZERO;

    // 5. Apurements et Déductions
    private BigDecimal soldePretsDeduit = BigDecimal.ZERO;
    private BigDecimal soldeAvancesDeduit = BigDecimal.ZERO;
    private BigDecimal autresDeductions = BigDecimal.ZERO;
    private String motifAutresDeductions;

    // 6. Net Final
    private BigDecimal netAPayerStc = BigDecimal.ZERO;

    private String statut;
    private String modeReglement;
    private String commentaire;

    private List<StcLigneDTO> lignes = new ArrayList<>();

    public StcCalculResultDTO() {}

    public void ajouterLigne(String code, String libelle, String sens, BigDecimal base, BigDecimal taux, BigDecimal montant) {
        lignes.add(new StcLigneDTO(code, libelle, sens, base, taux, montant));
    }

    public static class StcLigneDTO {
        private String code;
        private String libelle;
        private String sens; // GAIN, RETENUE, INFO
        private BigDecimal base;
        private BigDecimal taux;
        private BigDecimal montant;

        public StcLigneDTO(String code, String libelle, String sens, BigDecimal base, BigDecimal taux, BigDecimal montant) {
            this.code = code;
            this.libelle = libelle;
            this.sens = sens;
            this.base = base;
            this.taux = taux;
            this.montant = montant;
        }

        public String getCode() { return code; }
        public String getLibelle() { return libelle; }
        public String getSens() { return sens; }
        public BigDecimal getBase() { return base; }
        public BigDecimal getTaux() { return taux; }
        public BigDecimal getMontant() { return montant; }
    }

    // Getters & Setters
    public Integer getIdStc() { return idStc; }
    public void setIdStc(Integer idStc) { this.idStc = idStc; }

    public String getNumeroRecu() { return numeroRecu; }
    public void setNumeroRecu(String numeroRecu) { this.numeroRecu = numeroRecu; }

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

    public BigDecimal getJoursTravaillesDernierMois() { return joursTravaillesDernierMois; }
    public void setJoursTravaillesDernierMois(BigDecimal joursTravaillesDernierMois) { this.joursTravaillesDernierMois = joursTravaillesDernierMois; }

    public BigDecimal getSalaireProrata() { return salaireProrata; }
    public void setSalaireProrata(BigDecimal salaireProrata) { this.salaireProrata = salaireProrata; }

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

    public String getMotifAutresIndemnites() { return motifAutresIndemnites; }
    public void setMotifAutresIndemnites(String motifAutresIndemnites) { this.motifAutresIndemnites = motifAutresIndemnites; }

    public BigDecimal getTotalBrutStc() { return totalBrutStc; }
    public void setTotalBrutStc(BigDecimal totalBrutStc) { this.totalBrutStc = totalBrutStc; }

    public BigDecimal getCotisationCnaps() { return cotisationCnaps; }
    public void setCotisationCnaps(BigDecimal cotisationCnaps) { this.cotisationCnaps = cotisationCnaps; }

    public BigDecimal getCotisationSante() { return cotisationSante; }
    public void setCotisationSante(BigDecimal cotisationSante) { this.cotisationSante = cotisationSante; }

    public BigDecimal getTotalCotisationsSociales() { return totalCotisationsSociales; }
    public void setTotalCotisationsSociales(BigDecimal totalCotisationsSociales) { this.totalCotisationsSociales = totalCotisationsSociales; }

    public BigDecimal getTotalCotisationsPatronales() { return totalCotisationsPatronales; }
    public void setTotalCotisationsPatronales(BigDecimal totalCotisationsPatronales) { this.totalCotisationsPatronales = totalCotisationsPatronales; }

    public BigDecimal getNetImposable() { return netImposable; }
    public void setNetImposable(BigDecimal netImposable) { this.netImposable = netImposable; }

    public BigDecimal getIrsaNet() { return irsaNet; }
    public void setIrsaNet(BigDecimal irsaNet) { this.irsaNet = irsaNet; }

    public BigDecimal getSoldePretsDeduit() { return soldePretsDeduit; }
    public void setSoldePretsDeduit(BigDecimal soldePretsDeduit) { this.soldePretsDeduit = soldePretsDeduit; }

    public BigDecimal getSoldeAvancesDeduit() { return soldeAvancesDeduit; }
    public void setSoldeAvancesDeduit(BigDecimal soldeAvancesDeduit) { this.soldeAvancesDeduit = soldeAvancesDeduit; }

    public BigDecimal getAutresDeductions() { return autresDeductions; }
    public void setAutresDeductions(BigDecimal autresDeductions) { this.autresDeductions = autresDeductions; }

    public String getMotifAutresDeductions() { return motifAutresDeductions; }
    public void setMotifAutresDeductions(String motifAutresDeductions) { this.motifAutresDeductions = motifAutresDeductions; }

    public BigDecimal getNetAPayerStc() { return netAPayerStc; }
    public void setNetAPayerStc(BigDecimal netAPayerStc) { this.netAPayerStc = netAPayerStc; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getModeReglement() { return modeReglement; }
    public void setModeReglement(String modeReglement) { this.modeReglement = modeReglement; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Integer getIdBulletin() { return idBulletin; }
    public void setIdBulletin(Integer idBulletin) { this.idBulletin = idBulletin; }

    public List<StcLigneDTO> getLignes() { return lignes; }
    public void setLignes(List<StcLigneDTO> lignes) { this.lignes = lignes; }
}
