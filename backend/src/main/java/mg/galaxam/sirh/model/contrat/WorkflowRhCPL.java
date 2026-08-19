package mg.galaxam.sirh.model.contrat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Vue complète (CPL) du workflow RH enrichie par JOINs.
 * Retourne les libellés lisibles au lieu des ID techniques.
 * Utilisé pour les réponses API et les pages de récapitulatif DAF.
 */
public class WorkflowRhCPL {

    // ─── Workflow ───────────────────────────────────────────────────────────────
    private Integer idWorkflow;
    private Integer idEmploye;
    private String  nomPrenomEmploye;
    private String  matriculeEmploye;
    private String  codeTypeEvenement;
    private String  libelleTypeEvenement;
    private String  codeStatutWorkflow;
    private String  libelleStatutWorkflow;

    private Integer idContratSource;
    private String  numeroContratSource;
    private Integer idContratCible;
    private String  numeroContratCible;
    private Integer idAffectationSource;
    private Integer idAffectationCible;

    private Date    dateDemande;
    private Date    dateEffet;
    private String  motif;
    private String  commentaire;
    private Integer idDemandeur;
    private Integer idValideur;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // ─── Contrat cible enrichi ──────────────────────────────────────────────────
    private Integer idFiliale;
    private Integer idTypeContrat;
    private Integer idTypeIntervenant;
    private String  libelleTypeContrat;
    private String  libelleTypeIntervenant;
    private String  libelleFiliale;
    private Date    dateDebutContrat;
    private Date    dateFinContrat;
    private Integer dureeEssaiMois;
    private Date    dateFinEssai;
    private boolean comptabiliseEffectif;
    private Date    dateSignature;

    // ─── Affectation cible enrichie ─────────────────────────────────────────────
    private Integer    idSite;
    private Integer    idDepartement;
    private Integer    idService;
    private Integer    idFonction;
    private Integer    idResponsableHierarchique;
    private Integer    idCategorieProfessionnelle;
    private Integer    idModeCalculPaie;
    private String     libelleSite;
    private String     libelleDepartement;
    private String     libelleService;
    private String     libelleFonction;
    private String     nomPrenomResponsable;
    private String     libelleCategoriePro;
    private String     libelleModeCalculPaie;
    private BigDecimal salaireBase;
    private Date       dateDebutAffectation;
    private Date       dateFinAffectation;
    private String     libelleStatutAffectation;

    // ─── Requête SQL principale (sur vue PostgreSQL v_workflow_rh_cpl) ────────

    private static final String SQL_SELECT_FULL = "SELECT * FROM v_workflow_rh_cpl";

    // ─── Méthodes de recherche ─────────────────────────────────────────────────

    public static WorkflowRhCPL rechercherParId(Integer idWorkflow, Connection c) throws Exception {
        String sql = SQL_SELECT_FULL + " WHERE id_workflow = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idWorkflow);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    public static List<WorkflowRhCPL> listerParEmploye(Integer idEmploye, Connection c) throws Exception {
        List<WorkflowRhCPL> list = new ArrayList<>();
        String sql = SQL_SELECT_FULL + " WHERE id_employe = ? ORDER BY created_at DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static Integer getIntegerSafe(ResultSet rs, String colName) {
        try {
            int val = rs.getInt(colName);
            return rs.wasNull() ? null : val;
        } catch (SQLException e) {
            return null;
        }
    }

    private static Date getDateSafe(ResultSet rs, String colName) {
        try {
            return rs.getDate(colName);
        } catch (SQLException e) {
            return null;
        }
    }

    // ─── Mapper ────────────────────────────────────────────────────────────────

    private static WorkflowRhCPL mapResultSet(ResultSet rs) throws SQLException {
        WorkflowRhCPL w = new WorkflowRhCPL();
        w.idWorkflow = rs.getInt("id_workflow");
        w.idEmploye = rs.getInt("id_employe");
        w.nomPrenomEmploye = rs.getString("nom_prenom_employe");
        w.matriculeEmploye = rs.getString("matricule_employe");
        w.codeTypeEvenement = rs.getString("code_type_evenement");
        w.libelleTypeEvenement = rs.getString("libelle_type_evenement");
        w.codeStatutWorkflow = rs.getString("code_statut_workflow");
        w.libelleStatutWorkflow = rs.getString("libelle_statut_workflow");

        int idCtSrc = rs.getInt("id_contrat_source"); w.idContratSource = rs.wasNull() ? null : idCtSrc;
        w.numeroContratSource = rs.getString("numero_contrat_source");
        int idCtCbl = rs.getInt("id_contrat_cible"); w.idContratCible = rs.wasNull() ? null : idCtCbl;
        w.numeroContratCible = rs.getString("numero_contrat_cible");
        int idAffSrc = rs.getInt("id_affectation_source"); w.idAffectationSource = rs.wasNull() ? null : idAffSrc;
        int idAffCbl = rs.getInt("id_affectation_cible"); w.idAffectationCible = rs.wasNull() ? null : idAffCbl;

        w.dateDemande = rs.getDate("date_demande");
        w.dateEffet = rs.getDate("date_effet");
        w.motif = rs.getString("motif");
        w.commentaire = rs.getString("commentaire");
        w.idDemandeur = getIntegerSafe(rs, "id_demandeur");
        w.idValideur = getIntegerSafe(rs, "id_valideur");
        w.createdAt = rs.getTimestamp("created_at");
        w.updatedAt = rs.getTimestamp("updated_at");

        w.idFiliale = getIntegerSafe(rs, "id_filiale");
        w.idTypeContrat = getIntegerSafe(rs, "id_type_contrat");
        w.idTypeIntervenant = getIntegerSafe(rs, "id_type_intervenant");
        w.libelleTypeContrat = rs.getString("libelle_type_contrat");
        w.libelleTypeIntervenant = rs.getString("libelle_type_intervenant");
        w.libelleFiliale = rs.getString("libelle_filiale");
        w.dateDebutContrat = rs.getDate("date_debut_contrat");
        w.dateFinContrat = rs.getDate("date_fin_contrat");
        w.dureeEssaiMois = getIntegerSafe(rs, "duree_essai_mois");
        w.dateFinEssai = getDateSafe(rs, "date_fin_essai");
        w.comptabiliseEffectif = rs.getBoolean("comptabilise_effectif");
        w.dateSignature = rs.getDate("date_signature_contrat");

        w.idSite = getIntegerSafe(rs, "id_site");
        w.idDepartement = getIntegerSafe(rs, "id_departement");
        w.idService = getIntegerSafe(rs, "id_service");
        w.idFonction = getIntegerSafe(rs, "id_fonction");
        w.idResponsableHierarchique = getIntegerSafe(rs, "id_responsable_hierarchique");
        w.idCategorieProfessionnelle = getIntegerSafe(rs, "id_categorie_professionnelle");
        w.idModeCalculPaie = getIntegerSafe(rs, "id_mode_calcul_paie");
        w.libelleSite = rs.getString("libelle_site");
        w.libelleDepartement = rs.getString("libelle_departement");
        w.libelleService = rs.getString("libelle_service");
        w.libelleFonction = rs.getString("libelle_fonction");
        w.nomPrenomResponsable = rs.getString("nom_prenom_responsable");
        w.libelleCategoriePro = rs.getString("libelle_categorie_pro");
        w.libelleModeCalculPaie = rs.getString("libelle_mode_calcul_paie");
        w.salaireBase = rs.getBigDecimal("salaire_base");
        w.dateDebutAffectation = rs.getDate("date_debut_affectation");
        w.dateFinAffectation = rs.getDate("date_fin_affectation");
        w.libelleStatutAffectation = rs.getString("libelle_statut_affectation");
        return w;
    }

    // ─── Getters ────────────────────────────────────────────────────────────────

    public Integer getIdWorkflow() { return idWorkflow; }
    public Integer getIdEmploye() { return idEmploye; }
    public String getNomPrenomEmploye() { return nomPrenomEmploye; }
    public String getMatriculeEmploye() { return matriculeEmploye; }
    public String getCodeTypeEvenement() { return codeTypeEvenement; }
    public String getLibelleTypeEvenement() { return libelleTypeEvenement; }
    public String getCodeStatutWorkflow() { return codeStatutWorkflow; }
    public String getLibelleStatutWorkflow() { return libelleStatutWorkflow; }
    public Integer getIdContratSource() { return idContratSource; }
    public String getNumeroContratSource() { return numeroContratSource; }
    public Integer getIdContratCible() { return idContratCible; }
    public String getNumeroContratCible() { return numeroContratCible; }
    public Integer getIdAffectationSource() { return idAffectationSource; }
    public Integer getIdAffectationCible() { return idAffectationCible; }
    public Date getDateDemande() { return dateDemande; }
    public Date getDateEffet() { return dateEffet; }
    public String getMotif() { return motif; }
    public String getCommentaire() { return commentaire; }
    public Integer getIdDemandeur() { return idDemandeur; }
    public Integer getIdValideur() { return idValideur; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public Integer getIdFiliale() { return idFiliale; }
    public Integer getIdTypeContrat() { return idTypeContrat; }
    public Integer getIdTypeIntervenant() { return idTypeIntervenant; }
    public String getLibelleTypeContrat() { return libelleTypeContrat; }
    public String getLibelleTypeIntervenant() { return libelleTypeIntervenant; }
    public String getLibelleFiliale() { return libelleFiliale; }
    public Date getDateDebutContrat() { return dateDebutContrat; }
    public Date getDateFinContrat() { return dateFinContrat; }
    public Integer getDureeEssaiMois() { return dureeEssaiMois; }
    public Date getDateFinEssai() { return dateFinEssai; }
    public void setDateFinEssai(Date dateFinEssai) { this.dateFinEssai = dateFinEssai; }
    public boolean isComptabiliseEffectif() { return comptabiliseEffectif; }
    public void setComptabiliseEffectif(boolean comptabiliseEffectif) { this.comptabiliseEffectif = comptabiliseEffectif; }
    public Date getDateSignature() { return dateSignature; }
    public void setDateSignature(Date dateSignature) { this.dateSignature = dateSignature; }
    public Integer getIdSite() { return idSite; }
    public Integer getIdDepartement() { return idDepartement; }
    public Integer getIdService() { return idService; }
    public Integer getIdFonction() { return idFonction; }
    public Integer getIdResponsableHierarchique() { return idResponsableHierarchique; }
    public Integer getIdCategorieProfessionnelle() { return idCategorieProfessionnelle; }
    public Integer getIdModeCalculPaie() { return idModeCalculPaie; }
    public String getLibelleSite() { return libelleSite; }
    public String getLibelleDepartement() { return libelleDepartement; }
    public String getLibelleService() { return libelleService; }
    public String getLibelleFonction() { return libelleFonction; }
    public String getNomPrenomResponsable() { return nomPrenomResponsable; }
    public String getLibelleCategoriePro() { return libelleCategoriePro; }
    public String getLibelleModeCalculPaie() { return libelleModeCalculPaie; }
    public BigDecimal getSalaireBase() { return salaireBase; }
    public Date getDateDebutAffectation() { return dateDebutAffectation; }
    public Date getDateFinAffectation() { return dateFinAffectation; }
    public String getLibelleStatutAffectation() { return libelleStatutAffectation; }
}
