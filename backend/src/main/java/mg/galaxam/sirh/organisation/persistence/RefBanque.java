package mg.galaxam.sirh.organisation.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RefBanque extends ClassMere {

    private Integer idBanque;
    private String codeBanque;
    private String nomBanque;
    private String codeSwift;
    private boolean estActif;

    public RefBanque() {
        this.setNomTable("REF_BANQUE");
        this.estActif = true;
    }

    @Override
    public Integer getTuppleID() {
        return this.idBanque;
    }

    @Override
    public String getAttributIDName() {
        return "id_banque";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (codeBanque == null || codeBanque.isBlank()) {
            throw new IllegalArgumentException("Le code banque est obligatoire.");
        }
        if (nomBanque == null || nomBanque.isBlank()) {
            throw new IllegalArgumentException("Le nom de la banque est obligatoire.");
        }
        this.codeBanque = this.codeBanque.trim().toUpperCase();

        RefBanque existant = rechercherParCode(this.codeBanque, c);
        if (existant != null && !existant.getIdBanque().equals(this.idBanque)) {
            throw new IllegalArgumentException("Ce code banque est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_banque (code_banque, nom_banque, code_swift, est_actif) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codeBanque);
            ps.setString(2, nomBanque);
            ps.setString(3, codeSwift);
            ps.setBoolean(4, estActif);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idBanque = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_banque SET code_banque = ?, nom_banque = ?, code_swift = ?, est_actif = ? WHERE id_banque = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeBanque);
            ps.setString(2, nomBanque);
            ps.setString(3, codeSwift);
            ps.setBoolean(4, estActif);
            ps.setInt(5, idBanque);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_banque WHERE id_banque = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idBanque);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_banque, code_banque, nom_banque, code_swift, est_actif FROM ref_banque WHERE id_banque = ?";
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

    public static RefBanque rechercherParCode(String code, Connection c) throws Exception {
        String sql = "SELECT id_banque, code_banque, nom_banque, code_swift, est_actif FROM ref_banque WHERE code_banque = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<RefBanque> listAll(Connection c) throws Exception {
        return list(c, false);
    }

    public static List<RefBanque> listActifs(Connection c) throws Exception {
        return list(c, true);
    }

    private static List<RefBanque> list(Connection c, boolean actifsOnly) throws Exception {
        List<RefBanque> list = new ArrayList<>();
        String sql = "SELECT id_banque, code_banque, nom_banque, code_swift, est_actif FROM ref_banque " +
                (actifsOnly ? " WHERE est_actif = TRUE" : "") + " ORDER BY id_banque ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefBanque mapResultSet(ResultSet rs) throws SQLException {
        RefBanque b = new RefBanque();
        b.setIdBanque(rs.getInt("id_banque"));
        b.setCodeBanque(rs.getString("code_banque"));
        b.setNomBanque(rs.getString("nom_banque"));
        b.setCodeSwift(rs.getString("code_swift"));
        b.setEstActif(rs.getBoolean("est_actif"));
        return b;
    }

    // Getters and Setters
    public Integer getIdBanque() { return idBanque; }
    public void setIdBanque(Integer idBanque) { this.idBanque = idBanque; }

    public String getCodeBanque() { return codeBanque; }
    public void setCodeBanque(String codeBanque) { this.codeBanque = codeBanque; }

    public String getNomBanque() { return nomBanque; }
    public void setNomBanque(String nomBanque) { this.nomBanque = nomBanque; }

    public String getCodeSwift() { return codeSwift; }
    public void setCodeSwift(String codeSwift) { this.codeSwift = codeSwift; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
}
