package mg.galaxam.sirh.contrat.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RefMotifSortie extends ClassMere {

    private Integer idMotifSortie;
    private String code;
    private String libelle;
    private boolean actif = true;

    public RefMotifSortie() {
        this.setNomTable("REF_MOTIF_SORTIE");
    }

    @Override
    public Integer getTuppleID() {
        return this.idMotifSortie;
    }

    @Override
    public String getAttributIDName() {
        return "id_motif_sortie";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code du motif de sortie est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du motif de sortie est obligatoire.");
        }
        this.code = this.code.trim().toUpperCase();
        this.libelle = this.libelle.trim();
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_motif_sortie (code, libelle, actif) VALUES (?, ?, ?) RETURNING id_motif_sortie";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBoolean(3, actif);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idMotifSortie = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_motif_sortie SET code = ?, libelle = ?, actif = ? WHERE id_motif_sortie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBoolean(3, actif);
            ps.setInt(4, idMotifSortie);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "UPDATE ref_motif_sortie SET actif = FALSE WHERE id_motif_sortie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idMotifSortie);
            ps.executeUpdate();
        }
        this.actif = false;
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public RefMotifSortie rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM ref_motif_sortie WHERE id_motif_sortie = ?";
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

    public static List<RefMotifSortie> listAll(Connection c, Boolean actifUniquement) throws Exception {
        List<RefMotifSortie> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM ref_motif_sortie WHERE 1=1 ");
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

    public static RefMotifSortie mapResultSet(ResultSet rs) throws SQLException {
        RefMotifSortie m = new RefMotifSortie();
        m.setIdMotifSortie(rs.getInt("id_motif_sortie"));
        m.setCode(rs.getString("code"));
        m.setLibelle(rs.getString("libelle"));
        m.setActif(rs.getBoolean("actif"));
        return m;
    }

    // Getters / Setters
    public Integer getIdMotifSortie() { return idMotifSortie; }
    public void setIdMotifSortie(Integer idMotifSortie) { this.idMotifSortie = idMotifSortie; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
}
