package mg.galaxam.sirh.referentiel.organisation;

import bean.ClassMere;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RefFonction extends ClassMere {

    private Integer idFonction;
    private String codeFonction;
    private String libelle;
    private boolean estActif;

    public RefFonction() {
        this.setNomTable("REF_FONCTION");
        this.estActif = true;
    }

    @Override
    public Integer getTuppleID() {
        return this.idFonction;
    }

    @Override
    public String getAttributIDName() {
        return "id_fonction";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (codeFonction == null || codeFonction.isBlank()) {
            throw new IllegalArgumentException("Le code de la fonction est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé de la fonction est obligatoire.");
        }
        this.codeFonction = this.codeFonction.trim().toUpperCase();

        RefFonction existant = rechercherParCode(this.codeFonction, c);
        if (existant != null && !existant.getIdFonction().equals(this.idFonction)) {
            throw new IllegalArgumentException("Ce code de fonction est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_fonction (code_fonction, libelle, est_actif) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codeFonction);
            ps.setString(2, libelle);
            ps.setBoolean(3, estActif);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idFonction = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_fonction SET code_fonction = ?, libelle = ?, est_actif = ? WHERE id_fonction = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeFonction);
            ps.setString(2, libelle);
            ps.setBoolean(3, estActif);
            ps.setInt(4, idFonction);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_fonction WHERE id_fonction = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idFonction);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_fonction, code_fonction, libelle, est_actif FROM ref_fonction WHERE id_fonction = ?";
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

    public static RefFonction rechercherParCode(String code, Connection c) throws Exception {
        String sql = "SELECT id_fonction, code_fonction, libelle, est_actif FROM ref_fonction WHERE code_fonction = ?";
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

    public static List<RefFonction> listAll(Connection c) throws Exception {
        List<RefFonction> list = new ArrayList<>();
        String sql = "SELECT id_fonction, code_fonction, libelle, est_actif FROM ref_fonction ORDER BY id_fonction ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefFonction mapResultSet(ResultSet rs) throws SQLException {
        RefFonction f = new RefFonction();
        f.setIdFonction(rs.getInt("id_fonction"));
        f.setCodeFonction(rs.getString("code_fonction"));
        f.setLibelle(rs.getString("libelle"));
        f.setEstActif(rs.getBoolean("est_actif"));
        return f;
    }

    // Getters and Setters
    public Integer getIdFonction() { return idFonction; }
    public void setIdFonction(Integer idFonction) { this.idFonction = idFonction; }

    public String getCodeFonction() { return codeFonction; }
    public void setCodeFonction(String codeFonction) { this.codeFonction = codeFonction; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
}
