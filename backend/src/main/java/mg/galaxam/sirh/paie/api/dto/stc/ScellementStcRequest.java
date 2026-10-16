package mg.galaxam.sirh.paie.api.dto.stc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Payload de scellement transactionnel du Solde de Tout Compte lié au dossier de sortie.
 * Exige la référence exacte de la simulation validée par la DAF et la version du dossier
 * pour garantir le contrôle optimiste et l'intégrité financière.
 */
public class ScellementStcRequest {

    @NotBlank(message = "La référence de la simulation financière vérifiée est obligatoire pour sceller le STC.")
    private String referenceSimulation;

    @NotNull(message = "La version du dossier préparatoire est obligatoire pour le verrouillage optimiste.")
    @Positive(message = "Le numéro de version doit être strictement positif.")
    private Integer version;

    private String commentaire;

    public ScellementStcRequest() {}

    public ScellementStcRequest(String referenceSimulation, Integer version, String commentaire) {
        this.referenceSimulation = referenceSimulation;
        this.version = version;
        this.commentaire = commentaire;
    }

    public String getReferenceSimulation() {
        return referenceSimulation;
    }

    public void setReferenceSimulation(String referenceSimulation) {
        this.referenceSimulation = referenceSimulation;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }
}
