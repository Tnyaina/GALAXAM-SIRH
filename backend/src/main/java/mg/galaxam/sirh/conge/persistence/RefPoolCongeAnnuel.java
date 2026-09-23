package mg.galaxam.sirh.conge.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RefPoolCongeAnnuel extends ClassMere {

    private Integer idPool;
    private String code;
    private String libelle;
    private BigDecimal plafondJoursAn;

    public RefPoolCongeAnnuel() {
        this.setNomTable("REF_POOL_CONGE_ANNUEL");
    }

    @Override
    public Integer getTuppleID() {
        return this.idPool;
    }

    @Override
    public String getAttributIDName() {
        return "id_pool";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code du pool annuel est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du pool annuel est obligatoire.");
        }
        if (plafondJoursAn == null || plafondJoursAn.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le plafond de jours par an doit être supérieur ou égal à 0.");
        }
        this.code = this.code.trim().toUpperCase();

        RefPoolCongeAnnuel existant = rechercherParCode(this.code, c);
        if (existant != null && !existant.getIdPool().equals(this.idPool)) {
            throw new IllegalArgumentException("Ce code de pool est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_pool_conge_annuel (code, libelle, plafond_jours_an) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBigDecimal(3, plafondJoursAn);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idPool = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_pool_conge_annuel SET code = ?, libelle = ?, plafond_jours_an = ? WHERE id_pool = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBigDecimal(3, plafondJoursAn);
            ps.setInt(4, idPool);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_pool_conge_annuel WHERE id_pool = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPool);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public static RefPoolCongeAnnuel rechercherParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT id_pool, code, libelle, plafond_jours_an FROM ref_pool_conge_annuel WHERE id_pool = ?";
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

    public static RefPoolCongeAnnuel rechercherParCode(String code, Connection c) throws Exception {
        if (code == null || code.isBlank()) return null;
        String sql = "SELECT id_pool, code, libelle, plafond_jours_an FROM ref_pool_conge_annuel WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<RefPoolCongeAnnuel> listAll(Connection c) throws Exception {
        List<RefPoolCongeAnnuel> list = new ArrayList<>();
        String sql = "SELECT id_pool, code, libelle, plafond_jours_an FROM ref_pool_conge_annuel ORDER BY id_pool";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefPoolCongeAnnuel mapResultSet(ResultSet rs) throws Exception {
        RefPoolCongeAnnuel obj = new RefPoolCongeAnnuel();
        obj.setIdPool(rs.getInt("id_pool"));
        obj.setCode(rs.getString("code"));
        obj.setLibelle(rs.getString("libelle"));
        obj.setPlafondJoursAn(rs.getBigDecimal("plafond_jours_an"));
        return obj;
    }

    // Getters / Setters
    public Integer getIdPool() { return idPool; }
    public void setIdPool(Integer idPool) { this.idPool = idPool; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public BigDecimal getPlafondJoursAn() { return plafondJoursAn; }
    public void setPlafondJoursAn(BigDecimal plafondJoursAn) { this.plafondJoursAn = plafondJoursAn; }
}
