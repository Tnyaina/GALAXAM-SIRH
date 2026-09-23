package mg.galaxam.sirh.contrat.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class EmployeContactUrgence extends ClassMere {

    private Integer idContact;
    private Integer idEmploye;
    private String nom;
    private String prenom;
    private String lienParente; // 'Pere', 'Mere', 'Conjoint', 'Frere', 'Soeur', 'Enfant', 'Autre'
    private String adresse;
    private String telephone;
    private String societe;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public EmployeContactUrgence() {
        this.setNomTable("EMPLOYE_CONTACT_URGENCE");
    }

    @Override
    public Integer getTuppleID() {
        return this.idContact;
    }

    @Override
    public String getAttributIDName() {
        return "id_contact";
    }

    @Override
    public String getLiaisonFille() {
        return "id_employe";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'id employé est obligatoire pour le contact d'urgence.");
        }
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom du contact d'urgence est obligatoire.");
        }
        if (prenom == null || prenom.isBlank()) {
            throw new IllegalArgumentException("Le prénom du contact d'urgence est obligatoire.");
        }
        if (telephone == null || telephone.isBlank()) {
            throw new IllegalArgumentException("Le numéro de téléphone du contact d'urgence est obligatoire.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO employe_contact_urgence (id_employe, nom, prenom, lien_parente, adresse, telephone, societe) VALUES (?, ?, ?, ?::lien_parente_enum, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, idEmploye);
            ps.setString(i++, nom);
            ps.setString(i++, prenom);
            ps.setString(i++, lienParente);
            ps.setString(i++, adresse);
            ps.setString(i++, telephone);
            ps.setString(i++, societe);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idContact = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE employe_contact_urgence SET nom = ?, prenom = ?, lien_parente = ?::lien_parente_enum, adresse = ?, telephone = ?, societe = ?, updated_at = CURRENT_TIMESTAMP WHERE id_contact = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, nom);
            ps.setString(i++, prenom);
            ps.setString(i++, lienParente);
            ps.setString(i++, adresse);
            ps.setString(i++, telephone);
            ps.setString(i++, societe);
            ps.setInt(i++, idContact);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM employe_contact_urgence WHERE id_contact = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idContact);
            ps.executeUpdate();
        }
    }

    public static void deleteByEmployeId(Integer idEmploye, Connection c) throws Exception {
        String sql = "DELETE FROM employe_contact_urgence WHERE id_employe = ?";
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

    public EmployeContactUrgence rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM employe_contact_urgence WHERE id_contact = ?";
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

    public static List<EmployeContactUrgence> listByEmployeId(Integer idEmploye, Connection c) throws Exception {
        List<EmployeContactUrgence> list = new ArrayList<>();
        String sql = "SELECT * FROM employe_contact_urgence WHERE id_employe = ? ORDER BY id_contact ASC";
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

    private static EmployeContactUrgence mapResultSet(ResultSet rs) throws SQLException {
        EmployeContactUrgence ct = new EmployeContactUrgence();
        ct.setIdContact(rs.getInt("id_contact"));
        ct.setIdEmploye(rs.getInt("id_employe"));
        ct.setNom(rs.getString("nom"));
        ct.setPrenom(rs.getString("prenom"));
        ct.setLienParente(rs.getString("lien_parente"));
        ct.setAdresse(rs.getString("adresse"));
        ct.setTelephone(rs.getString("telephone"));
        ct.setSociete(rs.getString("societe"));
        ct.setCreatedAt(rs.getTimestamp("created_at"));
        ct.setUpdatedAt(rs.getTimestamp("updated_at"));
        return ct;
    }

    // Getters and Setters
    public Integer getIdContact() { return idContact; }
    public void setIdContact(Integer idContact) { this.idContact = idContact; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getLienParente() { return lienParente; }
    public void setLienParente(String lienParente) {
        if (lienParente == null || lienParente.isBlank()) {
            this.lienParente = "Autre";
            return;
        }
        String clean = lienParente.trim();
        switch (clean.toLowerCase()) {
            case "pere":
            case "père":
                this.lienParente = "Pere";
                break;
            case "mere":
            case "mère":
                this.lienParente = "Mere";
                break;
            case "conjoint":
            case "conjointe":
            case "conjoint(e)":
            case "epoux":
            case "époux":
            case "epouse":
            case "épouse":
                this.lienParente = "Conjoint";
                break;
            case "frere":
            case "frère":
                this.lienParente = "Frere";
                break;
            case "soeur":
            case "sœur":
                this.lienParente = "Soeur";
                break;
            case "enfant":
            case "fils":
            case "fille":
                this.lienParente = "Enfant";
                break;
            case "autre":
            default:
                this.lienParente = "Autre";
                break;
        }
    }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getSociete() { return societe; }
    public void setSociete(String societe) { this.societe = societe; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
