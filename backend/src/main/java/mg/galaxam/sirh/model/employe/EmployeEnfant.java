package mg.galaxam.sirh.model.employe;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class EmployeEnfant extends ClassMere {

    private Integer idEnfant;
    private Integer idEmploye;
    private String nom;
    private String prenom;
    private String sexe; // 'M', 'F'
    private Date dateNaissance;
    private String lieuNaissance;
    private boolean aCharge = true;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public EmployeEnfant() {
        this.setNomTable("EMPLOYE_ENFANT");
    }

    @Override
    public Integer getTuppleID() {
        return this.idEnfant;
    }

    @Override
    public String getAttributIDName() {
        return "id_enfant";
    }

    @Override
    public String getLiaisonFille() {
        return "id_employe";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'id employé est obligatoire pour l'enfant.");
        }
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom de l'enfant est obligatoire.");
        }
        if (prenom == null || prenom.isBlank()) {
            throw new IllegalArgumentException("Le prénom de l'enfant est obligatoire.");
        }
        if (dateNaissance == null) {
            throw new IllegalArgumentException("La date de naissance de l'enfant est obligatoire.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO employe_enfant (id_employe, nom, prenom, sexe, date_naissance, lieu_naissance, a_charge) VALUES (?, ?, ?, ?::sexe_enum, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, idEmploye);
            ps.setString(i++, nom);
            ps.setString(i++, prenom);
            ps.setString(i++, sexe);
            ps.setDate(i++, dateNaissance);
            ps.setString(i++, lieuNaissance);
            ps.setBoolean(i++, aCharge);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idEnfant = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE employe_enfant SET nom = ?, prenom = ?, sexe = ?::sexe_enum, date_naissance = ?, lieu_naissance = ?, a_charge = ?, updated_at = CURRENT_TIMESTAMP WHERE id_enfant = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, nom);
            ps.setString(i++, prenom);
            ps.setString(i++, sexe);
            ps.setDate(i++, dateNaissance);
            ps.setString(i++, lieuNaissance);
            ps.setBoolean(i++, aCharge);
            ps.setInt(i++, idEnfant);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM employe_enfant WHERE id_enfant = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEnfant);
            ps.executeUpdate();
        }
    }

    public static void deleteByEmployeId(Integer idEmploye, Connection c) throws Exception {
        String sql = "DELETE FROM employe_enfant WHERE id_employe = ?";
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

    public EmployeEnfant rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM employe_enfant WHERE id_enfant = ?";
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

    public static List<EmployeEnfant> listByEmployeId(Integer idEmploye, Connection c) throws Exception {
        List<EmployeEnfant> list = new ArrayList<>();
        String sql = "SELECT * FROM employe_enfant WHERE id_employe = ? ORDER BY date_naissance ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    private static EmployeEnfant mapResultSet(ResultSet rs) throws SQLException {
        EmployeEnfant enf = new EmployeEnfant();
        enf.setIdEnfant(rs.getInt("id_enfant"));
        enf.setIdEmploye(rs.getInt("id_employe"));
        enf.setNom(rs.getString("nom"));
        enf.setPrenom(rs.getString("prenom"));
        enf.setSexe(rs.getString("sexe"));
        enf.setDateNaissance(rs.getDate("date_naissance"));
        enf.setLieuNaissance(rs.getString("lieu_naissance"));
        enf.setACharge(rs.getBoolean("a_charge"));
        enf.setCreatedAt(rs.getTimestamp("created_at"));
        enf.setUpdatedAt(rs.getTimestamp("updated_at"));
        return enf;
    }

    // Getters and Setters
    public Integer getIdEnfant() { return idEnfant; }
    public void setIdEnfant(Integer idEnfant) { this.idEnfant = idEnfant; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getSexe() { return sexe; }
    public void setSexe(String sexe) { this.sexe = sexe; }

    public Date getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(Date dateNaissance) { this.dateNaissance = dateNaissance; }

    public String getLieuNaissance() { return lieuNaissance; }
    public void setLieuNaissance(String lieuNaissance) { this.lieuNaissance = lieuNaissance; }

    public boolean isACharge() { return aCharge; }
    public void setACharge(boolean aCharge) { this.aCharge = aCharge; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
