package mg.galaxam.sirh.paie.api.dto.stc;

/**
 * Élément de contrôle dynamique de la checklist du dossier de sortie.
 */
public class ChecklistItemDTO {

    public enum Niveau { BLOQUANT, AVERTISSEMENT, INFO }
    public enum SourceNorme { LEGAL_MADAGASCAR_2024, REGLE_INTERNE_GALAXAM, TECHNIQUE_SI }
    public enum StatutCheck { CONFORME, A_VERIFIER, NON_CONFORME }

    private String code;
    private String titre;
    private Niveau niveau;
    private SourceNorme sourceNorme;
    private StatutCheck statut;
    private String message;
    private String actionRecommandee;
    private String lienModule;

    public ChecklistItemDTO() {}

    public ChecklistItemDTO(String code, String titre, Niveau niveau, SourceNorme sourceNorme,
                            StatutCheck statut, String message, String actionRecommandee, String lienModule) {
        this.code = code;
        this.titre = titre;
        this.niveau = niveau;
        this.sourceNorme = sourceNorme;
        this.statut = statut;
        this.message = message;
        this.actionRecommandee = actionRecommandee;
        this.lienModule = lienModule;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public Niveau getNiveau() { return niveau; }
    public void setNiveau(Niveau niveau) { this.niveau = niveau; }

    public SourceNorme getSourceNorme() { return sourceNorme; }
    public void setSourceNorme(SourceNorme sourceNorme) { this.sourceNorme = sourceNorme; }

    public StatutCheck getStatut() { return statut; }
    public void setStatut(StatutCheck statut) { this.statut = statut; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getActionRecommandee() { return actionRecommandee; }
    public void setActionRecommandee(String actionRecommandee) { this.actionRecommandee = actionRecommandee; }

    public String getLienModule() { return lienModule; }
    public void setLienModule(String lienModule) { this.lienModule = lienModule; }
}
