package mg.galaxam.sirh.model.paie.dto.export;

import java.math.BigDecimal;

/**
 * DTO représentant une ligne de la Déclaration Nominative Trimestrielle des Salaires (DNTS CNaPS & FMFP).
 */
public class DntsRowDto {
    private int numeroOrdre;
    private String matricule;
    private String numAffiliationCnaps;
    private String nomPrenom;
    private String refCin;
    private String dateEmbauche;
    private String dateDebauchage;
    private BigDecimal salaireBrutReel;
    private BigDecimal assiettePlafonneeCnaps;
    private BigDecimal cnapsSalarial;       // 1%
    private BigDecimal cnapsPatronal;       // 13%
    private BigDecimal fmfpPatronal;        // 1%
    private BigDecimal santeSalarial;       // 1% OSTIE ou 1.5% OMSI
    private BigDecimal santePatronal;       // 5% OSTIE ou 5.5% OMSI
    private BigDecimal totalCotisations;

    public DntsRowDto() {}

    // Getters and Setters
    public int getNumeroOrdre() { return numeroOrdre; }
    public void setNumeroOrdre(int numeroOrdre) { this.numeroOrdre = numeroOrdre; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNumAffiliationCnaps() { return numAffiliationCnaps; }
    public void setNumAffiliationCnaps(String numAffiliationCnaps) { this.numAffiliationCnaps = numAffiliationCnaps; }

    public String getNomPrenom() { return nomPrenom; }
    public void setNomPrenom(String nomPrenom) { this.nomPrenom = nomPrenom; }

    public String getRefCin() { return refCin; }
    public void setRefCin(String refCin) { this.refCin = refCin; }

    public String getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(String dateEmbauche) { this.dateEmbauche = dateEmbauche; }

    public String getDateDebauchage() { return dateDebauchage; }
    public void setDateDebauchage(String dateDebauchage) { this.dateDebauchage = dateDebauchage; }

    public BigDecimal getSalaireBrutReel() { return salaireBrutReel; }
    public void setSalaireBrutReel(BigDecimal salaireBrutReel) { this.salaireBrutReel = salaireBrutReel; }

    public BigDecimal getAssiettePlafonneeCnaps() { return assiettePlafonneeCnaps; }
    public void setAssiettePlafonneeCnaps(BigDecimal assiettePlafonneeCnaps) { this.assiettePlafonneeCnaps = assiettePlafonneeCnaps; }

    public BigDecimal getCnapsSalarial() { return cnapsSalarial; }
    public void setCnapsSalarial(BigDecimal cnapsSalarial) { this.cnapsSalarial = cnapsSalarial; }

    public BigDecimal getCnapsPatronal() { return cnapsPatronal; }
    public void setCnapsPatronal(BigDecimal cnapsPatronal) { this.cnapsPatronal = cnapsPatronal; }

    public BigDecimal getFmfpPatronal() { return fmfpPatronal; }
    public void setFmfpPatronal(BigDecimal fmfpPatronal) { this.fmfpPatronal = fmfpPatronal; }

    public BigDecimal getSanteSalarial() { return santeSalarial; }
    public void setSanteSalarial(BigDecimal santeSalarial) { this.santeSalarial = santeSalarial; }

    public BigDecimal getSantePatronal() { return santePatronal; }
    public void setSantePatronal(BigDecimal santePatronal) { this.santePatronal = santePatronal; }

    public BigDecimal getTotalCotisations() { return totalCotisations; }
    public void setTotalCotisations(BigDecimal totalCotisations) { this.totalCotisations = totalCotisations; }
}
