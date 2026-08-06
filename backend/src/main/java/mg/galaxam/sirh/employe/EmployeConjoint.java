package mg.galaxam.sirh.employe;

import bean.ClassMere;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EmployeConjoint extends ClassMere {

    private Integer idConjoint;
    private Integer idEmploye;
    private String nom;
    private String prenom;
    private Date dateNaissance;
    private String lieuNaissance;
    private String societe;
    private String fonction;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public EmployeConjoint() {
        this.setNomTable("EMPLOYE_CONJOINT");
    }

    @Override
    public Integer getTuppleID() {
        return this.idConjoint;
    }

    @Override
    public String getAttributIDName() {
        return "id_conjoint";
    }

    @Override
    public String getLiaisonFille() {
        return "id_employe";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'id employé est obligatoire pour le conjoint.");
        }
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom du conjoint est obligatoire.");
        }
        if (prenom == null || prenom.isBlank()) {
            throw new IllegalArgumentException("Le prénom du conjoint est obligatoire.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO employe_conjoint (id_employe, nom, prenom, date_naissance, lieu_naissance, societe, fonction) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, idEmploye);
            ps.setString(i++, nom);
            ps.setString(i++, prenom);
            ps.setDate(i++, dateNaissance);
            ps.setString(i++, lieuNaissance);
            ps.setString(i++, societe);
            ps.setString(i++, fonction);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idConjoint = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE employe_conjoint SET nom = ?, prenom = ?, date_naissance = ?, lieu_naissance = ?, societe = ?, fonction = ?, updated_at = CURRENT_TIMESTAMP WHERE id_conjoint = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, nom);
            ps.setString(i++, prenom);
            ps.setDate(i++, dateNaissance);
            ps.setString(i++, lieuNaissance);
            ps.setString(i++, societe);
            ps.setString(i++, fonction);
            ps.setInt(i++, idConjoint);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM employe_conjoint WHERE id_conjoint = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idConjoint);
            ps.executeUpdate();
        }
    }

    public static void deleteByEmployeId(Integer idEmploye, Connection c) throws Exception {
        String sql = "DELETE FROM employe_conjoint WHERE id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public EmployeConjoint rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM employe_conjoint WHERE id_conjoint = ?";
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

    public static EmployeConjoint findByEmployeId(Integer idEmploye, Connection c) throws Exception {
        String sql = "SELECT * FROM employe_conjoint WHERE id_employe = ?";
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

    private static EmployeConjoint mapResultSet(ResultSet rs) throws SQLException {
        EmployeConjoint cj = new EmployeConjoint();
        cj.setIdConjoint(rs.getInt("id_conjoint"));
        cj.setIdEmploye(rs.getInt("id_employe"));
        cj.setNom(rs.getString("nom"));
        cj.setPrenom(rs.getString("prenom"));
        cj.setDateNaissance(rs.getDate("date_naissance"));
        cj.setLieuNaissance(rs.getString("lieu_naissance"));
        cj.setSociete(rs.getString("societe"));
        cj.setFonction(rs.getString("fonction"));
        cj.setCreatedAt(rs.getTimestamp("created_at"));
        cj.setUpdatedAt(rs.getTimestamp("updated_at"));
        return cj;
    }

    // Getters and Setters
    public Integer getIdConjoint() { return idConjoint; }
    public void setIdConjoint(Integer idConjoint) { this.idConjoint = idConjoint; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public Date getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(Date dateNaissance) { this.dateNaissance = dateNaissance; }

    public String getLieuNaissance() { return lieuNaissance; }
    public void setLieuNaissance(String lieuNaissance) { this.lieuNaissance = lieuNaissance; }

    public String getSociete() { return societe; }
    public void setSociete(String societe) { this.societe = societe; }

    public String getFonction() { return fonction; }
    public void setFonction(String fonction) { this.fonction = fonction; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
