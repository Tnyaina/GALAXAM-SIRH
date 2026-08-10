package mg.galaxam.sirh.model.organisation;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RefDepartement extends ClassMere {

    private Integer idDepartement;
    private String codeDepartement;
    private String libelle;
    private Date dateFinPrevue;
    private boolean estActif;

    public RefDepartement() {
        this.setNomTable("REF_DEPARTEMENT");
        this.estActif = true;
    }

    @Override
    public Integer getTuppleID() {
        return this.idDepartement;
    }

    @Override
    public String getAttributIDName() {
        return "id_departement";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (codeDepartement == null || codeDepartement.isBlank()) {
            throw new IllegalArgumentException("Le code du département est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du département est obligatoire.");
        }
        this.codeDepartement = this.codeDepartement.trim().toUpperCase();

        RefDepartement existant = rechercherParCode(this.codeDepartement, c);
        if (existant != null && !existant.getIdDepartement().equals(this.idDepartement)) {
            throw new IllegalArgumentException("Ce code de département est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_departement (code_departement, libelle, date_fin_prevue, est_actif) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codeDepartement);
            ps.setString(2, libelle);
            ps.setDate(3, dateFinPrevue);
            ps.setBoolean(4, estActif);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idDepartement = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_departement SET code_departement = ?, libelle = ?, date_fin_prevue = ?, est_actif = ? WHERE id_departement = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeDepartement);
            ps.setString(2, libelle);
            ps.setDate(3, dateFinPrevue);
            ps.setBoolean(4, estActif);
            ps.setInt(5, idDepartement);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_departement WHERE id_departement = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idDepartement);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_departement, code_departement, libelle, date_fin_prevue, est_actif FROM ref_departement WHERE id_departement = ?";
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

    public static RefDepartement rechercherParCode(String code, Connection c) throws Exception {
        String sql = "SELECT id_departement, code_departement, libelle, date_fin_prevue, est_actif FROM ref_departement WHERE code_departement = ?";
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

    public static List<RefDepartement> listAll(Connection c) throws Exception {
        List<RefDepartement> list = new ArrayList<>();
        String sql = "SELECT id_departement, code_departement, libelle, date_fin_prevue, est_actif FROM ref_departement ORDER BY id_departement ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefDepartement mapResultSet(ResultSet rs) throws SQLException {
        RefDepartement d = new RefDepartement();
        d.setIdDepartement(rs.getInt("id_departement"));
        d.setCodeDepartement(rs.getString("code_departement"));
        d.setLibelle(rs.getString("libelle"));
        d.setDateFinPrevue(rs.getDate("date_fin_prevue"));
        d.setEstActif(rs.getBoolean("est_actif"));
        return d;
    }

    // Getters and Setters
    public Integer getIdDepartement() { return idDepartement; }
    public void setIdDepartement(Integer idDepartement) { this.idDepartement = idDepartement; }

    public String getCodeDepartement() { return codeDepartement; }
    public void setCodeDepartement(String codeDepartement) { this.codeDepartement = codeDepartement; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public Date getDateFinPrevue() { return dateFinPrevue; }
    public void setDateFinPrevue(Date dateFinPrevue) { this.dateFinPrevue = dateFinPrevue; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
}
