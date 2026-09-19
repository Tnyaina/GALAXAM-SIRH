package mg.galaxam.sirh.model.referentiel;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class RefCategorieProfessionnelle extends ClassMere {

    private Integer idCategorieProfessionnelle;
    private String code;
    private String libelle;
    private String description;
    private boolean estEligibleHs = true;
    private boolean estCadre = false;
    private boolean estEligibleExonerationHs = true;
    private boolean actif = true;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public RefCategorieProfessionnelle() {
        this.setNomTable("REF_CATEGORIE_PROFESSIONNELLE");
    }

    @Override
    public Integer getTuppleID() {
        return this.idCategorieProfessionnelle;
    }

    @Override
    public String getAttributIDName() {
        return "id_categorie_professionnelle";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code de la catégorie professionnelle est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé de la catégorie professionnelle est obligatoire.");
        }
        this.code = this.code.trim().toUpperCase();
        this.libelle = this.libelle.trim();
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_categorie_professionnelle (code, libelle, description, est_eligible_hs, est_cadre, est_eligible_exoneration_hs, actif) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_categorie_professionnelle";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, description);
            ps.setBoolean(4, estEligibleHs);
            ps.setBoolean(5, estCadre);
            ps.setBoolean(6, estEligibleExonerationHs);
            ps.setBoolean(7, actif);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idCategorieProfessionnelle = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_categorie_professionnelle SET code = ?, libelle = ?, description = ?, est_eligible_hs = ?, est_cadre = ?, est_eligible_exoneration_hs = ?, actif = ?, updated_at = CURRENT_TIMESTAMP WHERE id_categorie_professionnelle = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, description);
            ps.setBoolean(4, estEligibleHs);
            ps.setBoolean(5, estCadre);
            ps.setBoolean(6, estEligibleExonerationHs);
            ps.setBoolean(7, actif);
            ps.setInt(8, idCategorieProfessionnelle);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "UPDATE ref_categorie_professionnelle SET actif = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id_categorie_professionnelle = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idCategorieProfessionnelle);
            ps.executeUpdate();
        }
        this.actif = false;
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public RefCategorieProfessionnelle rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM ref_categorie_professionnelle WHERE id_categorie_professionnelle = ?";
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

    public static List<RefCategorieProfessionnelle> listAll(Connection c, Boolean actifUniquement) throws Exception {
        List<RefCategorieProfessionnelle> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM ref_categorie_professionnelle WHERE 1=1 ");
        if (Boolean.TRUE.equals(actifUniquement)) {
            sql.append(" AND actif = TRUE ");
        }
        sql.append(" ORDER BY libelle ASC");

        try (PreparedStatement ps = c.prepareStatement(sql.toString());
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public static RefCategorieProfessionnelle mapResultSet(ResultSet rs) throws SQLException {
        RefCategorieProfessionnelle cp = new RefCategorieProfessionnelle();
        cp.setIdCategorieProfessionnelle(rs.getInt("id_categorie_professionnelle"));
        cp.setCode(rs.getString("code"));
        cp.setLibelle(rs.getString("libelle"));
        cp.setDescription(rs.getString("description"));
        cp.setEstEligibleHs(rs.getBoolean("est_eligible_hs"));
        try {
            cp.setEstCadre(rs.getBoolean("est_cadre"));
        } catch (Exception ignored) {}
        try {
            cp.setEstEligibleExonerationHs(rs.getObject("est_eligible_exoneration_hs") != null ? rs.getBoolean("est_eligible_exoneration_hs") : true);
        } catch (Exception ignored) {}
        cp.setActif(rs.getBoolean("actif"));
        cp.setCreatedAt(rs.getTimestamp("created_at"));
        cp.setUpdatedAt(rs.getTimestamp("updated_at"));
        return cp;
    }

    // Getters / Setters
    public Integer getIdCategorieProfessionnelle() { return idCategorieProfessionnelle; }
    public void setIdCategorieProfessionnelle(Integer idCategorieProfessionnelle) { this.idCategorieProfessionnelle = idCategorieProfessionnelle; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isEstEligibleHs() { return estEligibleHs; }
    public void setEstEligibleHs(boolean estEligibleHs) { this.estEligibleHs = estEligibleHs; }

    public boolean isEstCadre() { return estCadre; }
    public void setEstCadre(boolean estCadre) { this.estCadre = estCadre; }

    public boolean isEstEligibleExonerationHs() { return estEligibleExonerationHs; }
    public void setEstEligibleExonerationHs(boolean estEligibleExonerationHs) { this.estEligibleExonerationHs = estEligibleExonerationHs; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
