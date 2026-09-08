package mg.galaxam.sirh.api.dto.paieconfig;

public class RefTypeMouvementPaieRequest {
    private String code;
    private String libelle;
    private String sens; // 'GAIN' ou 'RETENUE'
    private String categorie;
    private Integer idRubrique;
    private boolean estProratise;
    private boolean estPlafonneDixieme;
    private boolean estActif = true;
    private Integer ordreAffichage = 10;
    private String description;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getSens() { return sens; }
    public void setSens(String sens) { this.sens = sens; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public Integer getIdRubrique() { return idRubrique; }
    public void setIdRubrique(Integer idRubrique) { this.idRubrique = idRubrique; }

    public boolean isEstProratise() { return estProratise; }
    public void setEstProratise(boolean estProratise) { this.estProratise = estProratise; }

    public boolean isEstPlafonneDixieme() { return estPlafonneDixieme; }
    public void setEstPlafonneDixieme(boolean estPlafonneDixieme) { this.estPlafonneDixieme = estPlafonneDixieme; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }

    public Integer getOrdreAffichage() { return ordreAffichage; }
    public void setOrdreAffichage(Integer ordreAffichage) { this.ordreAffichage = ordreAffichage; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
