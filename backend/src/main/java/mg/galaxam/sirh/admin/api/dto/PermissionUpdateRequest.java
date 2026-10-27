package mg.galaxam.sirh.admin.api.dto;

public class PermissionUpdateRequest {
    private Integer idModule;
    private boolean droitLecture;
    private boolean droitEcriture;
    private boolean droitValidation;

    // Getters
    public Integer getIdModule() { return idModule; }
    public boolean isDroitLecture() { return droitLecture; }
    public boolean isDroitEcriture() { return droitEcriture; }
    public boolean isDroitValidation() { return droitValidation; }

    // Setters
    public void setIdModule(Integer idModule) { this.idModule = idModule; }
    public void setDroitLecture(boolean droitLecture) { this.droitLecture = droitLecture; }
    public void setDroitEcriture(boolean droitEcriture) { this.droitEcriture = droitEcriture; }
    public void setDroitValidation(boolean droitValidation) { this.droitValidation = droitValidation; }
}
