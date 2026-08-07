package mg.galaxam.sirh.employe;

import bean.ClassMere;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class EmployeAffectationCarriereCPL extends ClassMere {

    private Integer idAffectation;
    private Integer idEmploye;
    private String employeMatricule;
    private String employeNomComplet;

    private Integer idContrat;
    private String numeroContrat;
    private Integer idFiliale;
    private String filialeCode;
    private String filialeRaisonSociale;

    private Integer idMotifMouvement;
    private String motifMouvementCode;
    private String motifMouvementLibelle;

    private Integer idSite;
    private String siteCode;
    private String siteLibelle;

    private Integer idDepartement;
    private String departementCode;
    private String departementLibelle;

    private Integer idService;
    private String serviceCode;
    private String serviceLibelle;

    private Integer idFonction;
    private String fonctionCode;
    private String fonctionLibelle;

    private Integer idResponsableHierarchique;
    private String responsableNomComplet;

    private Integer idCategorieProfessionnelle;
    private String categorieCode;
    private String categorieLibelle;

    private Integer idModeCalculPaie;
    private String modePaieCode;
    private String modePaieLibelle;

    private BigDecimal salaireBase;
    private Date dateDebut;
    private Date dateFin;
    private boolean estAffectationCourante;
    private String commentaire;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public EmployeAffectationCarriereCPL() {
        this.setNomTable("V_AFFECTATION_CARRIERE_CPL");
    }

    @Override
    public Integer getTuppleID() {
        return this.idAffectation;
    }

    @Override
    public String getAttributIDName() {
        return "id_affectation";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_affectation_carriere_cpl est en lecture seule.");
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_affectation_carriere_cpl est en lecture seule.");
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_affectation_carriere_cpl est en lecture seule.");
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public EmployeAffectationCarriereCPL rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM v_affectation_carriere_cpl WHERE id_affectation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<EmployeAffectationCarriereCPL> listByEmployeId(Integer idEmploye, Connection c) throws Exception {
        List<EmployeAffectationCarriereCPL> list = new ArrayList<>();
        String sql = "SELECT * FROM v_affectation_carriere_cpl WHERE id_employe = ? ORDER BY date_debut DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    public static EmployeAffectationCarriereCPL getAffectationCourante(Integer idEmploye, Connection c) throws Exception {
        String sql = "SELECT a.* FROM v_affectation_carriere_cpl a JOIN contrat_travail ct ON a.id_contrat = ct.id_contrat WHERE a.id_employe = ? AND ct.est_actif = TRUE AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE) ORDER BY a.date_debut DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static EmployeAffectationCarriereCPL mapResultSet(ResultSet rs) throws SQLException {
        EmployeAffectationCarriereCPL a = new EmployeAffectationCarriereCPL();
        a.setIdAffectation(rs.getInt("id_affectation"));
        a.setIdEmploye(rs.getInt("id_employe"));
        a.setEmployeMatricule(rs.getString("employe_matricule"));
        a.setEmployeNomComplet(rs.getString("employe_nom_complet"));

        a.setIdContrat(rs.getInt("id_contrat"));
        a.setNumeroContrat(rs.getString("numero_contrat"));
        a.setIdFiliale(rs.getInt("id_filiale"));
        a.setFilialeCode(rs.getString("code_filiale"));
        a.setFilialeRaisonSociale(rs.getString("filiale_raison_sociale"));

        a.setIdMotifMouvement(rs.getInt("id_motif_mouvement"));
        a.setMotifMouvementCode(rs.getString("motif_mouvement_code"));
        a.setMotifMouvementLibelle(rs.getString("motif_mouvement_libelle"));

        a.setIdSite(rs.getInt("id_site"));
        a.setSiteCode(rs.getString("code_site"));
        a.setSiteLibelle(rs.getString("site_libelle"));

        a.setIdDepartement(rs.getInt("id_departement"));
        a.setDepartementCode(rs.getString("code_departement"));
        a.setDepartementLibelle(rs.getString("departement_libelle"));

        a.setIdService(rs.getInt("id_service"));
        a.setServiceCode(rs.getString("code_service"));
        a.setServiceLibelle(rs.getString("service_libelle"));

        a.setIdFonction(rs.getInt("id_fonction"));
        a.setFonctionCode(rs.getString("code_fonction"));
        a.setFonctionLibelle(rs.getString("fonction_libelle"));

        a.setIdResponsableHierarchique((Integer) rs.getObject("id_responsable_hierarchique"));
        a.setResponsableNomComplet(rs.getString("responsable_nom_complet"));

        a.setIdCategorieProfessionnelle(rs.getInt("id_categorie_professionnelle"));
        a.setCategorieCode(rs.getString("categorie_code"));
        a.setCategorieLibelle(rs.getString("categorie_libelle"));

        a.setIdModeCalculPaie(rs.getInt("id_mode_calcul_paie"));
        a.setModePaieCode(rs.getString("mode_paie_code"));
        a.setModePaieLibelle(rs.getString("mode_paie_libelle"));

        a.setSalaireBase(rs.getBigDecimal("salaire_base"));
        a.setDateDebut(rs.getDate("date_debut"));
        a.setDateFin(rs.getDate("date_fin"));
        a.setEstAffectationCourante(rs.getBoolean("est_affectation_courante"));
        a.setCommentaire(rs.getString("commentaire"));

        a.setCreatedAt(rs.getTimestamp("created_at"));
        a.setUpdatedAt(rs.getTimestamp("updated_at"));
        return a;
    }

    // Getters and Setters
    public Integer getIdAffectation() { return idAffectation; }
    public void setIdAffectation(Integer idAffectation) { this.idAffectation = idAffectation; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getEmployeMatricule() { return employeMatricule; }
    public void setEmployeMatricule(String employeMatricule) { this.employeMatricule = employeMatricule; }

    public String getEmployeNomComplet() { return employeNomComplet; }
    public void setEmployeNomComplet(String employeNomComplet) { this.employeNomComplet = employeNomComplet; }

    public Integer getIdContrat() { return idContrat; }
    public void setIdContrat(Integer idContrat) { this.idContrat = idContrat; }

    public String getNumeroContrat() { return numeroContrat; }
    public void setNumeroContrat(String numeroContrat) { this.numeroContrat = numeroContrat; }

    public Integer getIdFiliale() { return idFiliale; }
    public void setIdFiliale(Integer idFiliale) { this.idFiliale = idFiliale; }

    public String getFilialeCode() { return filialeCode; }
    public void setFilialeCode(String filialeCode) { this.filialeCode = filialeCode; }

    public String getFilialeRaisonSociale() { return filialeRaisonSociale; }
    public void setFilialeRaisonSociale(String filialeRaisonSociale) { this.filialeRaisonSociale = filialeRaisonSociale; }

    public Integer getIdMotifMouvement() { return idMotifMouvement; }
    public void setIdMotifMouvement(Integer idMotifMouvement) { this.idMotifMouvement = idMotifMouvement; }

    public String getMotifMouvementCode() { return motifMouvementCode; }
    public void setMotifMouvementCode(String motifMouvementCode) { this.motifMouvementCode = motifMouvementCode; }

    public String getMotifMouvementLibelle() { return motifMouvementLibelle; }
    public void setMotifMouvementLibelle(String motifMouvementLibelle) { this.motifMouvementLibelle = motifMouvementLibelle; }

    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getSiteLibelle() { return siteLibelle; }
    public void setSiteLibelle(String siteLibelle) { this.siteLibelle = siteLibelle; }

    public Integer getIdDepartement() { return idDepartement; }
    public void setIdDepartement(Integer idDepartement) { this.idDepartement = idDepartement; }

    public String getDepartementCode() { return departementCode; }
    public void setDepartementCode(String departementCode) { this.departementCode = departementCode; }

    public String getDepartementLibelle() { return departementLibelle; }
    public void setDepartementLibelle(String departementLibelle) { this.departementLibelle = departementLibelle; }

    public Integer getIdService() { return idService; }
    public void setIdService(Integer idService) { this.idService = idService; }

    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    public String getServiceLibelle() { return serviceLibelle; }
    public void setServiceLibelle(String serviceLibelle) { this.serviceLibelle = serviceLibelle; }

    public Integer getIdFonction() { return idFonction; }
    public void setIdFonction(Integer idFonction) { this.idFonction = idFonction; }

    public String getFonctionCode() { return fonctionCode; }
    public void setFonctionCode(String fonctionCode) { this.fonctionCode = fonctionCode; }

    public String getFonctionLibelle() { return fonctionLibelle; }
    public void setFonctionLibelle(String fonctionLibelle) { this.fonctionLibelle = fonctionLibelle; }

    public Integer getIdResponsableHierarchique() { return idResponsableHierarchique; }
    public void setIdResponsableHierarchique(Integer idResponsableHierarchique) { this.idResponsableHierarchique = idResponsableHierarchique; }

    public String getResponsableNomComplet() { return responsableNomComplet; }
    public void setResponsableNomComplet(String responsableNomComplet) { this.responsableNomComplet = responsableNomComplet; }

    public Integer getIdCategorieProfessionnelle() { return idCategorieProfessionnelle; }
    public void setIdCategorieProfessionnelle(Integer idCategorieProfessionnelle) { this.idCategorieProfessionnelle = idCategorieProfessionnelle; }

    public String getCategorieCode() { return categorieCode; }
    public void setCategorieCode(String categorieCode) { this.categorieCode = categorieCode; }

    public String getCategorieLibelle() { return categorieLibelle; }
    public void setCategorieLibelle(String categorieLibelle) { this.categorieLibelle = categorieLibelle; }

    public Integer getIdModeCalculPaie() { return idModeCalculPaie; }
    public void setIdModeCalculPaie(Integer idModeCalculPaie) { this.idModeCalculPaie = idModeCalculPaie; }

    public String getModePaieCode() { return modePaieCode; }
    public void setModePaieCode(String modePaieCode) { this.modePaieCode = modePaieCode; }

    public String getModePaieLibelle() { return modePaieLibelle; }
    public void setModePaieLibelle(String modePaieLibelle) { this.modePaieLibelle = modePaieLibelle; }

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }

    public boolean isEstAffectationCourante() { return estAffectationCourante; }
    public void setEstAffectationCourante(boolean estAffectationCourante) { this.estAffectationCourante = estAffectationCourante; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
