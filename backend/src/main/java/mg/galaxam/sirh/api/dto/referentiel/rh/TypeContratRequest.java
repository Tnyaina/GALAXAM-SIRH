package mg.galaxam.sirh.api.dto.referentiel.rh;

public class TypeContratRequest {
    private String code;
    private String libelle;
    private boolean exigeDateFin;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public boolean isExigeDateFin() { return exigeDateFin; }
    public void setExigeDateFin(boolean exigeDateFin) { this.exigeDateFin = exigeDateFin; }
}
