package mg.galaxam.sirh.api.dto.contrat;

/**
 * DTO pour la validation d'un workflow BROUILLON par la DAF.
 * Utilisé pour : PUT /workflows/{id}/valider
 */
public class ValiderWorkflowRequest {

    /** Commentaire de validation (optionnel, mais recommandé pour la traçabilité) */
    private String commentaireValidation;

    public String getCommentaireValidation() { return commentaireValidation; }
    public void setCommentaireValidation(String commentaireValidation) { this.commentaireValidation = commentaireValidation; }
}
