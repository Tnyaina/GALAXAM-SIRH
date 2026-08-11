package mg.galaxam.sirh.model.contrat;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente l'état organisationnel et financier d'un employé à un instant T.
 * L'historique est préservé par fermeture de date (date_fin) et changement de statut.
 *
 * Statuts : PLANIFIEE → ACTIVE → HISTORISEE (ou ANNULEE)
 *
 * NOTE : Plus de id_workflow_origine (dépendance circulaire éliminée).
 * Pour retrouver le workflow d'une affectation : SELECT * FROM workflow_rh WHERE id_affectation_cible = :id
 */
public class EmployeAffectationCarriere extends ClassMere {

    private Integer idAffectation;
    private Integer idEmploye;
    private Integer idContrat;
    private Integer idStatutAffectation;
    private Integer idMotifMouvement;

    private Integer idSite;
    private Integer idDepartement;
    private Integer idService;
    private Integer idFonction;
    private Integer idResponsableHierarchique;

    private Integer idCategorieProfessionnelle;
    private Integer idModeCalculPaie;
    private java.math.BigDecimal salaireBase;

    private Date dateDebut;
    private Date dateFin;

    private String description;
    private String commentaire;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public EmployeAffectationCarriere() {
        this.setNomTable("EMPLOYE_AFFECTATION_CARRIERE");
    }

    @Override
    public Integer getTuppleID() { return idAffectation; }

    @Override
    public String getAttributIDName() { return "id_affectation"; }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) throw new IllegalArgumentException("L'employé est obligatoire.");
        if (idContrat == null) throw new IllegalArgumentException("Le contrat est obligatoire.");
        if (idStatutAffectation == null) throw new IllegalArgumentException("Le statut d'affectation est obligatoire.");
        if (idMotifMouvement == null) throw new IllegalArgumentException("Le motif de mouvement est obligatoire.");
        if (idSite == null) throw new IllegalArgumentException("Le site est obligatoire.");
        if (idDepartement == null) throw new IllegalArgumentException("Le département est obligatoire.");
        if (idService == null) throw new IllegalArgumentException("Le service est obligatoire.");
        if (idFonction == null) throw new IllegalArgumentException("La fonction est obligatoire.");
        if (idCategorieProfessionnelle == null) throw new IllegalArgumentException("La catégorie professionnelle est obligatoire.");
        if (idModeCalculPaie == null) throw new IllegalArgumentException("Le mode de calcul de paie est obligatoire.");
        if (salaireBase == null || salaireBase.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le salaire de base est obligatoire et doit être positif.");
        }
        if (dateDebut == null) throw new IllegalArgumentException("La date de début est obligatoire.");
        if (dateFin != null && dateFin.before(dateDebut)) {
            throw new IllegalArgumentException("La date de fin ne peut pas être antérieure à la date de début.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO employe_affectation_carriere (" +
                "id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, " +
                "id_site, id_departement, id_service, id_fonction, id_responsable_hierarchique, " +
                "id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, " +
                "date_debut, date_fin, description, commentaire" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, idEmploye);
            ps.setInt(i++, idContrat);
            ps.setInt(i++, idStatutAffectation);
            ps.setInt(i++, idMotifMouvement);
            ps.setInt(i++, idSite);
            ps.setInt(i++, idDepartement);
            ps.setInt(i++, idService);
            ps.setInt(i++, idFonction);
            setNullableInt(ps, i++, idResponsableHierarchique);
            ps.setInt(i++, idCategorieProfessionnelle);
            ps.setInt(i++, idModeCalculPaie);
            ps.setBigDecimal(i++, salaireBase);
            ps.setDate(i++, dateDebut);
            setNullableDate(ps, i++, dateFin);
            setNullableString(ps, i++, description);
            setNullableString(ps, i++, commentaire);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) this.idAffectation = rs.getInt(1);
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE employe_affectation_carriere SET " +
                "id_statut_affectation = ?, id_motif_mouvement = ?, " +
                "id_site = ?, id_departement = ?, id_service = ?, id_fonction = ?, id_responsable_hierarchique = ?, " +
                "id_categorie_professionnelle = ?, id_mode_calcul_paie = ?, salaire_base = ?, " +
                "date_debut = ?, date_fin = ?, description = ?, commentaire = ?, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_affectation = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, idStatutAffectation);
            ps.setInt(i++, idMotifMouvement);
            ps.setInt(i++, idSite);
            ps.setInt(i++, idDepartement);
            ps.setInt(i++, idService);
            ps.setInt(i++, idFonction);
            setNullableInt(ps, i++, idResponsableHierarchique);
            ps.setInt(i++, idCategorieProfessionnelle);
            ps.setInt(i++, idModeCalculPaie);
            ps.setBigDecimal(i++, salaireBase);
            ps.setDate(i++, dateDebut);
            setNullableDate(ps, i++, dateFin);
            setNullableString(ps, i++, description);
            setNullableString(ps, i++, commentaire);
            ps.setInt(i++, idAffectation);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM employe_affectation_carriere WHERE id_affectation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idAffectation);
            ps.executeUpdate();
        }
    }

    /**
     * Active une affectation PLANIFIEE → ACTIVE.
     * Appelé lors de la validation DAF.
     */
    public void activer(Connection c) throws Exception {
        String sql = "UPDATE employe_affectation_carriere SET " +
                "id_statut_affectation = (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'), " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_affectation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idAffectation);
            ps.executeUpdate();
        }
    }

    /**
     * Ferme l'affectation courante en la passant en HISTORISEE avec une date de fin.
     * Utilisé lors des transitions : mutation, promotion, changement de contrat, etc.
     * La date de fin est conventionnellement J-1 par rapport à la nouvelle affectation.
     *
     * @param dateFermeture date de fin effective de cette affectation
     */
    public void fermer(Date dateFermeture, Connection c) throws Exception {
        String sql = "UPDATE employe_affectation_carriere SET " +
                "id_statut_affectation = COALESCE((SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'HISTORISEE'), id_statut_affectation), " +
                "date_fin = ?, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_affectation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, dateFermeture);
            ps.setInt(2, idAffectation);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public EmployeAffectationCarriere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM employe_affectation_carriere WHERE id_affectation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Retourne l'affectation ACTIVE d'un employé.
     */
    public static EmployeAffectationCarriere rechercherActiveParEmploye(Integer idEmploye, Connection c) throws Exception {
        String sql = "SELECT eac.* FROM employe_affectation_carriere eac " +
                "JOIN ref_statut_affectation rsa ON eac.id_statut_affectation = rsa.id_statut_affectation " +
                "WHERE eac.id_employe = ? AND rsa.code = 'ACTIVE'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Retourne l'affectation PLANIFIEE d'un employé (créée mais pas encore validée).
     */
    public static EmployeAffectationCarriere rechercherPlanifieeParEmploye(Integer idEmploye, Connection c) throws Exception {
        String sql = "SELECT eac.* FROM employe_affectation_carriere eac " +
                "JOIN ref_statut_affectation rsa ON eac.id_statut_affectation = rsa.id_statut_affectation " +
                "WHERE eac.id_employe = ? AND rsa.code = 'PLANIFIEE' " +
                "ORDER BY eac.created_at DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Retourne tout l'historique des affectations d'un employé, de la plus récente à la plus ancienne.
     */
    public static List<EmployeAffectationCarriere> listerParEmploye(Integer idEmploye, Connection c) throws Exception {
        List<EmployeAffectationCarriere> list = new ArrayList<>();
        String sql = "SELECT * FROM employe_affectation_carriere WHERE id_employe = ? ORDER BY date_debut DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public static EmployeAffectationCarriere mapResultSet(ResultSet rs) throws SQLException {
        EmployeAffectationCarriere a = new EmployeAffectationCarriere();
        a.setIdAffectation(rs.getInt("id_affectation"));
        a.setIdEmploye(rs.getInt("id_employe"));
        a.setIdContrat(rs.getInt("id_contrat"));
        a.setIdStatutAffectation(rs.getInt("id_statut_affectation"));
        a.setIdMotifMouvement(rs.getInt("id_motif_mouvement"));
        a.setIdSite(rs.getInt("id_site"));
        a.setIdDepartement(rs.getInt("id_departement"));
        a.setIdService(rs.getInt("id_service"));
        a.setIdFonction(rs.getInt("id_fonction"));

        int idResp = rs.getInt("id_responsable_hierarchique");
        a.setIdResponsableHierarchique(rs.wasNull() ? null : idResp);

        a.setIdCategorieProfessionnelle(rs.getInt("id_categorie_professionnelle"));
        a.setIdModeCalculPaie(rs.getInt("id_mode_calcul_paie"));
        a.setSalaireBase(rs.getBigDecimal("salaire_base"));
        a.setDateDebut(rs.getDate("date_debut"));
        a.setDateFin(rs.getDate("date_fin"));
        a.setDescription(rs.getString("description"));
        a.setCommentaire(rs.getString("commentaire"));
        a.setCreatedAt(rs.getTimestamp("created_at"));
        a.setUpdatedAt(rs.getTimestamp("updated_at"));
        return a;
    }

    // ─── Helpers JDBC ──────────────────────────────────────────────────────────

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER);
        else ps.setInt(index, value);
    }

    private void setNullableDate(PreparedStatement ps, int index, Date value) throws SQLException {
        if (value == null) ps.setNull(index, Types.DATE);
        else ps.setDate(index, value);
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) ps.setNull(index, Types.VARCHAR);
        else ps.setString(index, value.trim());
    }

    // ─── Getters & Setters ─────────────────────────────────────────────────────

    public Integer getIdAffectation() { return idAffectation; }
    public void setIdAffectation(Integer idAffectation) { this.idAffectation = idAffectation; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdContrat() { return idContrat; }
    public void setIdContrat(Integer idContrat) { this.idContrat = idContrat; }

    public Integer getIdStatutAffectation() { return idStatutAffectation; }
    public void setIdStatutAffectation(Integer idStatutAffectation) { this.idStatutAffectation = idStatutAffectation; }

    public Integer getIdMotifMouvement() { return idMotifMouvement; }
    public void setIdMotifMouvement(Integer idMotifMouvement) { this.idMotifMouvement = idMotifMouvement; }

    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }

    public Integer getIdDepartement() { return idDepartement; }
    public void setIdDepartement(Integer idDepartement) { this.idDepartement = idDepartement; }

    public Integer getIdService() { return idService; }
    public void setIdService(Integer idService) { this.idService = idService; }

    public Integer getIdFonction() { return idFonction; }
    public void setIdFonction(Integer idFonction) { this.idFonction = idFonction; }

    public Integer getIdResponsableHierarchique() { return idResponsableHierarchique; }
    public void setIdResponsableHierarchique(Integer idResponsableHierarchique) { this.idResponsableHierarchique = idResponsableHierarchique; }

    public Integer getIdCategorieProfessionnelle() { return idCategorieProfessionnelle; }
    public void setIdCategorieProfessionnelle(Integer idCategorieProfessionnelle) { this.idCategorieProfessionnelle = idCategorieProfessionnelle; }

    public Integer getIdModeCalculPaie() { return idModeCalculPaie; }
    public void setIdModeCalculPaie(Integer idModeCalculPaie) { this.idModeCalculPaie = idModeCalculPaie; }

    public java.math.BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(java.math.BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
