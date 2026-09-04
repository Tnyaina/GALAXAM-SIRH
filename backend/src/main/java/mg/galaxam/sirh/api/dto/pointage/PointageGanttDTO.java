package mg.galaxam.sirh.api.dto.pointage;

import java.util.List;

public class PointageGanttDTO {
    private Integer idEmploye;
    private String matricule;
    private String nomPrenom;
    private String photoUrl;
    private String fonction;
    private List<GanttBlocDTO> blocs;

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNomPrenom() { return nomPrenom; }
    public void setNomPrenom(String nomPrenom) { this.nomPrenom = nomPrenom; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getFonction() { return fonction; }
    public void setFonction(String fonction) { this.fonction = fonction; }

    public List<GanttBlocDTO> getBlocs() { return blocs; }
    public void setBlocs(List<GanttBlocDTO> blocs) { this.blocs = blocs; }

    public static class GanttBlocDTO {
        private String date; // YYYY-MM-DD
        private String libelleDate; // ex "24/08 (Lun)"
        private String debut; // HH:mm
        private String fin;   // HH:mm
        private String heures; // ex "08h00"
        private String codeStatut; // PRESENT, RETARD, CONGE, REPOS, ABSENT
        private String variantColor; // success, warning, danger, secondary, neutral, primary

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public String getLibelleDate() { return libelleDate; }
        public void setLibelleDate(String libelleDate) { this.libelleDate = libelleDate; }

        public String getDebut() { return debut; }
        public void setDebut(String debut) { this.debut = debut; }

        public String getFin() { return fin; }
        public void setFin(String fin) { this.fin = fin; }

        public String getHeures() { return heures; }
        public void setHeures(String heures) { this.heures = heures; }

        public String getCodeStatut() { return codeStatut; }
        public void setCodeStatut(String codeStatut) { this.codeStatut = codeStatut; }

        public String getVariantColor() { return variantColor; }
        public void setVariantColor(String variantColor) { this.variantColor = variantColor; }
    }
}
