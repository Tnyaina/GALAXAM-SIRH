package mg.galaxam.sirh.referentiel.paie;

import bean.ClassMere;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RefIndemnite extends ClassMere {

    private Integer idIndemnite;
    private String code;
    private String libelle;
    private boolean imposableIrsa = false;
    private boolean estActif = true;

    public RefIndemnite() {
        this.setNomTable("REF_INDEMNITE");
    }

    @Override
    public Integer getTuppleID() {
        return idIndemnite;
    }

    @Override
    public String getAttributIDName() {
        return "id_indemnite";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_indemnite (code, libelle, imposable_irsa, est_actif) " +
                     "VALUES (?, ?, ?, ?) RETURNING id_indemnite";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBoolean(3, imposableIrsa);
            ps.setBoolean(4, estActif);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idIndemnite = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_indemnite SET code = ?, libelle = ?, imposable_irsa = ?, est_actif = ? " +
                     "WHERE id_indemnite = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBoolean(3, imposableIrsa);
            ps.setBoolean(4, estActif);
            ps.setInt(5, idIndemnite);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_indemnite WHERE id_indemnite = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idIndemnite);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_indemnite, code, libelle, imposable_irsa, est_actif FROM ref_indemnite WHERE id_indemnite = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefIndemnite item = new RefIndemnite();
                    item.idIndemnite = rs.getInt("id_indemnite");
                    item.code = rs.getString("code");
                    item.libelle = rs.getString("libelle");
                    item.imposableIrsa = rs.getBoolean("imposable_irsa");
                    item.estActif = rs.getBoolean("est_actif");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<RefIndemnite> listAll(Connection c) throws Exception {
        String sql = "SELECT id_indemnite, code, libelle, imposable_irsa, est_actif FROM ref_indemnite ORDER BY code";
        List<RefIndemnite> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RefIndemnite item = new RefIndemnite();
                item.idIndemnite = rs.getInt("id_indemnite");
                item.code = rs.getString("code");
                item.libelle = rs.getString("libelle");
                item.imposableIrsa = rs.getBoolean("imposable_irsa");
                item.estActif = rs.getBoolean("est_actif");
                list.add(item);
            }
        }
        return list;
    }

    // Getters / Setters
    public Integer getIdIndemnite() { return idIndemnite; }
    public void setIdIndemnite(Integer idIndemnite) { this.idIndemnite = idIndemnite; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public boolean isImposableIrsa() { return imposableIrsa; }
    public void setImposableIrsa(boolean imposableIrsa) { this.imposableIrsa = imposableIrsa; }
    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
}
