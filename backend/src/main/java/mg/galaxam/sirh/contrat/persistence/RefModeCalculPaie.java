package mg.galaxam.sirh.contrat.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class RefModeCalculPaie extends ClassMere {

    private Integer idModeCalculPaie;
    private String code;
    private String libelle;
    private String description;
    private boolean actif = true;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public RefModeCalculPaie() {
        this.setNomTable("REF_MODE_CALCUL_PAIE");
    }

    @Override
    public Integer getTuppleID() {
        return this.idModeCalculPaie;
    }

    @Override
    public String getAttributIDName() {
        return "id_mode_calcul_paie";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code du mode de calcul de paie est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé est obligatoire.");
        }
        this.code = this.code.trim().toUpperCase();
        this.libelle = this.libelle.trim();
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_mode_calcul_paie (code, libelle, description, actif) VALUES (?, ?, ?, ?) RETURNING id_mode_calcul_paie";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, description);
            ps.setBoolean(4, actif);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idModeCalculPaie = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_mode_calcul_paie SET code = ?, libelle = ?, description = ?, actif = ?, updated_at = CURRENT_TIMESTAMP WHERE id_mode_calcul_paie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, description);
            ps.setBoolean(4, actif);
            ps.setInt(5, idModeCalculPaie);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "UPDATE ref_mode_calcul_paie SET actif = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id_mode_calcul_paie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idModeCalculPaie);
            ps.executeUpdate();
        }
        this.actif = false;
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public RefModeCalculPaie rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM ref_mode_calcul_paie WHERE id_mode_calcul_paie = ?";
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

    public static List<RefModeCalculPaie> listAll(Connection c, Boolean actifUniquement) throws Exception {
        List<RefModeCalculPaie> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM ref_mode_calcul_paie WHERE 1=1 ");
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

    public static RefModeCalculPaie mapResultSet(ResultSet rs) throws SQLException {
        RefModeCalculPaie m = new RefModeCalculPaie();
        m.setIdModeCalculPaie(rs.getInt("id_mode_calcul_paie"));
        m.setCode(rs.getString("code"));
        m.setLibelle(rs.getString("libelle"));
        m.setDescription(rs.getString("description"));
        m.setActif(rs.getBoolean("actif"));
        m.setCreatedAt(rs.getTimestamp("created_at"));
        m.setUpdatedAt(rs.getTimestamp("updated_at"));
        return m;
    }

    // Getters / Setters
    public Integer getIdModeCalculPaie() { return idModeCalculPaie; }
    public void setIdModeCalculPaie(Integer idModeCalculPaie) { this.idModeCalculPaie = idModeCalculPaie; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
