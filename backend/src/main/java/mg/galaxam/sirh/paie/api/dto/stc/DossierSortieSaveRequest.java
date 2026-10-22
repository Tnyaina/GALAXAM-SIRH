package mg.galaxam.sirh.paie.api.dto.stc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.sql.Date;
import java.util.Map;

/**
 * Payload de sauvegarde ou mise à jour du dossier préparatoire de sortie.
 */
public class DossierSortieSaveRequest {

    private Integer idDossierSortie;
    private Integer version;

    @NotNull(message = "L'identifiant de l'employé est obligatoire.")
    @Positive(message = "L'identifiant de l'employé doit être strictement positif.")
    private Integer idEmploye;

    @Positive(message = "L'identifiant du contrat doit être strictement positif.")
    private Integer idContrat;

    @NotNull(message = "La date de départ effectif est obligatoire.")
    private Date dateDepart;

    @NotBlank(message = "Le motif de rupture est obligatoire.")
    @Size(max = 50, message = "Le motif de rupture ne doit pas dépasser 50 caractères.")
    private String motifRupture;

    @Pattern(regexp = "^(EN_PREPARATION|PRET_A_VALIDER|ANNULE)?$",
            message = "Le statut soumis en préparation doit être EN_PREPARATION, PRET_A_VALIDER ou ANNULE.")
    private String statutDossier;

    // Données de saisie du formulaire (mémorisées au format JSON)
    private Map<String, Object> donneesPreparation;

    // Certificat de travail
    private Boolean certificatDelivre;
    private Date dateDelivranceCertificat;

    @Pattern(regexp = "^(MAIN_PROPRE|COURRIER_RECOMMANDE|EMAIL_SECURISE)?$",
            message = "Mode de remise de certificat invalide.")
    private String modeRemiseCertificat;

    // Reçu STC
    private Boolean recuStcRemis;
    private Date dateRemiseRecu;

    public DossierSortieSaveRequest() {}

    public Integer getIdDossierSortie() { return idDossierSortie; }
    public void setIdDossierSortie(Integer idDossierSortie) { this.idDossierSortie = idDossierSortie; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdContrat() { return idContrat; }
    public void setIdContrat(Integer idContrat) { this.idContrat = idContrat; }

    public Date getDateDepart() { return dateDepart; }
    public void setDateDepart(Date dateDepart) { this.dateDepart = dateDepart; }

    public String getMotifRupture() { return motifRupture; }
    public void setMotifRupture(String motifRupture) { this.motifRupture = motifRupture; }

    public String getStatutDossier() { return statutDossier; }
    public void setStatutDossier(String statutDossier) { this.statutDossier = statutDossier; }

    public Map<String, Object> getDonneesPreparation() { return donneesPreparation; }
    public void setDonneesPreparation(Map<String, Object> donneesPreparation) { this.donneesPreparation = donneesPreparation; }

    public Boolean getCertificatDelivre() { return certificatDelivre; }
    public void setCertificatDelivre(Boolean certificatDelivre) { this.certificatDelivre = certificatDelivre; }

    public Date getDateDelivranceCertificat() { return dateDelivranceCertificat; }
    public void setDateDelivranceCertificat(Date dateDelivranceCertificat) { this.dateDelivranceCertificat = dateDelivranceCertificat; }

    public String getModeRemiseCertificat() { return modeRemiseCertificat; }
    public void setModeRemiseCertificat(String modeRemiseCertificat) { this.modeRemiseCertificat = modeRemiseCertificat; }

    public Boolean getRecuStcRemis() { return recuStcRemis; }
    public void setRecuStcRemis(Boolean recuStcRemis) { this.recuStcRemis = recuStcRemis; }

    public Date getDateRemiseRecu() { return dateRemiseRecu; }
    public void setDateRemiseRecu(Date dateRemiseRecu) { this.dateRemiseRecu = dateRemiseRecu; }
}
