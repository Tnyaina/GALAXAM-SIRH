package mg.galaxam.sirh.paie.domain.dto.export;

import java.math.BigDecimal;

/**
 * DTO représentant une ligne de l'État 301 SAFI IRSA (25 colonnes réglementaires DGI Madagascar).
 * Conforme au document officiel DGI 'modele declaration iRSA SAFI.ods'.
 */
public class SafiRowDto {
    private int numeroOrdre;                // Col A (1) : N° d'ordre
    private String matricule;               // Col B (2) : N° Mle
    private String affiliationCnaps;        // Col C (3) : N° d'affiliation CNaPS
    private String nomPrenoms;              // Col D (4) : Nom et prénoms
    private String cin;                     // Col E (5) : CIN / Carte de résident
    private String dateEmbauche;            // Col F (6) : Date d'embauche (JJ/MM/AAAA)
    private String dateDebauchage;          // Col G (7) : Date de débauchage (JJ/MM/AAAA ou vide)
    private String fonction;                // Col H (8) : Fonction
    private BigDecimal salaireBase;         // Col I (9) : Salaire de base (proratisé)
    private BigDecimal indemnitesImposables;// Col J (10) : Indemnités imposables
    private BigDecimal indemnitesNonImposables; // Col K (11) : Indemnités non imposables (ex: transport)
    private BigDecimal avantagesNatureImposables; // Col L (12) : Avantages en nature imposables (0.00)
    private BigDecimal avantagesNatureExoneres;   // Col M (13) : Avantages en nature exonérés (0.00)
    private BigDecimal heuresSupplementaires;     // Col N (14) : Heures supplémentaires
    private BigDecimal primesEtGratifications;    // Col O (15) : Primes et gratifications (assiduité, performance)
    private BigDecimal autres;              // Col P (16) : Autres (repas non soumis)
    private BigDecimal salaireBrut;         // Col Q (17) : Salaire brut (I + J + L + N + O + P)
    private BigDecimal deductionsCnaps;     // Col R (18) : Déduction CNaPS (1%)
    private BigDecimal deductionsSante;     // Col S (19) : Déduction OSTIE / OMSI (1% ou 1.5%)
    private BigDecimal salaireNet;          // Col T (20) : Salaire net (Q - R - S)
    private BigDecimal autreDeduction;      // Col U (21) : Autre déduction (0.00)
    private BigDecimal montantImposable;    // Col V (22) : Montant imposable (arrondi à la centaine inf : 100*INT(T/100))
    private BigDecimal impotCorrespondant;  // Col W (23) : Impôt correspondant (barème progressif IRSA)
    private BigDecimal reductionChargeFamille; // Col X (24) : Réduction charge de famille (2 000 * nb enfants)
    private BigDecimal impotDu;             // Col Y (25) : Impôt dû (W - X, avec min perception 3 000 si imposable > 0)

    public SafiRowDto() {}

    // Getters and Setters
    public int getNumeroOrdre() { return numeroOrdre; }
    public void setNumeroOrdre(int numeroOrdre) { this.numeroOrdre = numeroOrdre; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getAffiliationCnaps() { return affiliationCnaps; }
    public void setAffiliationCnaps(String affiliationCnaps) { this.affiliationCnaps = affiliationCnaps; }

    public String getNomPrenoms() { return nomPrenoms; }
    public void setNomPrenoms(String nomPrenoms) { this.nomPrenoms = nomPrenoms; }

    public String getCin() { return cin; }
    public void setCin(String cin) { this.cin = cin; }

    public String getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(String dateEmbauche) { this.dateEmbauche = dateEmbauche; }

    public String getDateDebauchage() { return dateDebauchage; }
    public void setDateDebauchage(String dateDebauchage) { this.dateDebauchage = dateDebauchage; }

    public String getFonction() { return fonction; }
    public void setFonction(String fonction) { this.fonction = fonction; }

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public BigDecimal getIndemnitesImposables() { return indemnitesImposables; }
    public void setIndemnitesImposables(BigDecimal indemnitesImposables) { this.indemnitesImposables = indemnitesImposables; }

    public BigDecimal getIndemnitesNonImposables() { return indemnitesNonImposables; }
    public void setIndemnitesNonImposables(BigDecimal indemnitesNonImposables) { this.indemnitesNonImposables = indemnitesNonImposables; }

    public BigDecimal getAvantagesNatureImposables() { return avantagesNatureImposables; }
    public void setAvantagesNatureImposables(BigDecimal avantagesNatureImposables) { this.avantagesNatureImposables = avantagesNatureImposables; }

    public BigDecimal getAvantagesNatureExoneres() { return avantagesNatureExoneres; }
    public void setAvantagesNatureExoneres(BigDecimal avantagesNatureExoneres) { this.avantagesNatureExoneres = avantagesNatureExoneres; }

    public BigDecimal getHeuresSupplementaires() { return heuresSupplementaires; }
    public void setHeuresSupplementaires(BigDecimal heuresSupplementaires) { this.heuresSupplementaires = heuresSupplementaires; }

    public BigDecimal getPrimesEtGratifications() { return primesEtGratifications; }
    public void setPrimesEtGratifications(BigDecimal primesEtGratifications) { this.primesEtGratifications = primesEtGratifications; }

    public BigDecimal getAutres() { return autres; }
    public void setAutres(BigDecimal autres) { this.autres = autres; }

    public BigDecimal getSalaireBrut() { return salaireBrut; }
    public void setSalaireBrut(BigDecimal salaireBrut) { this.salaireBrut = salaireBrut; }

    public BigDecimal getDeductionsCnaps() { return deductionsCnaps; }
    public void setDeductionsCnaps(BigDecimal deductionsCnaps) { this.deductionsCnaps = deductionsCnaps; }

    public BigDecimal getDeductionsSante() { return deductionsSante; }
    public void setDeductionsSante(BigDecimal deductionsSante) { this.deductionsSante = deductionsSante; }

    public BigDecimal getSalaireNet() { return salaireNet; }
    public void setSalaireNet(BigDecimal salaireNet) { this.salaireNet = salaireNet; }

    public BigDecimal getAutreDeduction() { return autreDeduction; }
    public void setAutreDeduction(BigDecimal autreDeduction) { this.autreDeduction = autreDeduction; }

    public BigDecimal getMontantImposable() { return montantImposable; }
    public void setMontantImposable(BigDecimal montantImposable) { this.montantImposable = montantImposable; }

    public BigDecimal getImpotCorrespondant() { return impotCorrespondant; }
    public void setImpotCorrespondant(BigDecimal impotCorrespondant) { this.impotCorrespondant = impotCorrespondant; }

    public BigDecimal getReductionChargeFamille() { return reductionChargeFamille; }
    public void setReductionChargeFamille(BigDecimal reductionChargeFamille) { this.reductionChargeFamille = reductionChargeFamille; }

    public BigDecimal getImpotDu() { return impotDu; }
    public void setImpotDu(BigDecimal impotDu) { this.impotDu = impotDu; }
}
