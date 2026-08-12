package mg.galaxam.sirh.api.dto.admin;

public class MenuRequest {
    private String idMenu;
    private String libelle;
    private String icone;
    private String href;
    private Integer rang;
    private Integer niveau;
    private String idPere;

    // Getters
    public String getIdMenu() { return idMenu; }
    public String getLibelle() { return libelle; }
    public String getIcone() { return icone; }
    public String getHref() { return href; }
    public Integer getRang() { return rang; }
    public Integer getNiveau() { return niveau; }
    public String getIdPere() { return idPere; }

    // Setters
    public void setIdMenu(String idMenu) { this.idMenu = idMenu; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public void setIcone(String icone) { this.icone = icone; }
    public void setHref(String href) { this.href = href; }
    public void setRang(Integer rang) { this.rang = rang; }
    public void setNiveau(Integer niveau) { this.niveau = niveau; }
    public void setIdPere(String idPere) { this.idPere = idPere; }
}
