package mg.galaxam.sirh.paie.api.dto;

import java.time.LocalDate;

/**
 * DTO de requête pour la création et modification d'une rubrique de paie.
 */
public class PaieRubriqueRequest {

    private String code;
    private String libelle;
    private String categorie;
    private String typeValeur = "MONTANT";
    private String sens;
    private Boolean soumisCnaps;
    private Boolean soumisSante;
    private Boolean soumisIrsa;
    private Boolean estFixe = false;
    private Integer ordreAffichage = 10;
    private Boolean estActif = true;
    private String description;
    private String referenceReglementaire;
    private LocalDate dateDebutEffet;
    private String etapeCalcul;
    private LocalDate dateFinEffet;
    private Integer idRubriqueOrigine;
    private String conditionApplication = "TOUS";

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public String getTypeValeur() { return typeValeur; }
    public void setTypeValeur(String typeValeur) { this.typeValeur = typeValeur; }

    public String getSens() { return sens; }
    public void setSens(String sens) { this.sens = sens; }

    public Boolean getSoumisCnaps() { return soumisCnaps; }
    public void setSoumisCnaps(Boolean soumisCnaps) { this.soumisCnaps = soumisCnaps; }

    public Boolean getSoumisSante() { return soumisSante; }
    public void setSoumisSante(Boolean soumisSante) { this.soumisSante = soumisSante; }

    public Boolean getSoumisIrsa() { return soumisIrsa; }
    public void setSoumisIrsa(Boolean soumisIrsa) { this.soumisIrsa = soumisIrsa; }

    public Boolean getEstFixe() { return estFixe; }
    public void setEstFixe(Boolean estFixe) { this.estFixe = estFixe; }

    public Integer getOrdreAffichage() { return ordreAffichage; }
    public void setOrdreAffichage(Integer ordreAffichage) { this.ordreAffichage = ordreAffichage; }

    public Boolean getEstActif() { return estActif; }
    public void setEstActif(Boolean estActif) { this.estActif = estActif; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReferenceReglementaire() { return referenceReglementaire; }
    public void setReferenceReglementaire(String referenceReglementaire) { this.referenceReglementaire = referenceReglementaire; }
    public LocalDate getDateDebutEffet() { return dateDebutEffet; }
    public void setDateDebutEffet(LocalDate dateDebutEffet) { this.dateDebutEffet = dateDebutEffet; }
    public LocalDate getDateFinEffet() { return dateFinEffet; }
    public void setDateFinEffet(LocalDate v) { dateFinEffet = v; }
    public Integer getIdRubriqueOrigine() { return idRubriqueOrigine; }
    public void setIdRubriqueOrigine(Integer v) { idRubriqueOrigine = v; }
    public String getConditionApplication() { return conditionApplication; }
    public void setConditionApplication(String v) { conditionApplication = v; }
    public String getEtapeCalcul() { return etapeCalcul; }
    public void setEtapeCalcul(String etapeCalcul) { this.etapeCalcul = etapeCalcul; }
}
