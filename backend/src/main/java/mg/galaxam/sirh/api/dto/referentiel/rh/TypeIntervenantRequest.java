package mg.galaxam.sirh.api.dto.referentiel.rh;

public class TypeIntervenantRequest {
    private String code;
    private String libelle;
    private boolean soumisIrsa;
    private boolean soumisIsi5;
    private boolean soumisCnaps;
    private boolean soumisSante;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public boolean isSoumisIrsa() { return soumisIrsa; }
    public void setSoumisIrsa(boolean soumisIrsa) { this.soumisIrsa = soumisIrsa; }

    public boolean isSoumisIsi5() { return soumisIsi5; }
    public void setSoumisIsi5(boolean soumisIsi5) { this.soumisIsi5 = soumisIsi5; }

    public boolean isSoumisCnaps() { return soumisCnaps; }
    public void setSoumisCnaps(boolean soumisCnaps) { this.soumisCnaps = soumisCnaps; }

    public boolean isSoumisSante() { return soumisSante; }
    public void setSoumisSante(boolean soumisSante) { this.soumisSante = soumisSante; }
}
