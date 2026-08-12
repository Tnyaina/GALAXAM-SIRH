package mg.galaxam.sirh.model.admin;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RolePermission extends ClassMere {

    private Integer idPermission;
    private Integer idRole;
    private Integer idModule;
    private boolean droitLecture;
    private boolean droitEcriture;
    private boolean droitValidation;

    public RolePermission() {
        this.setNomTable("ROLE_PERMISSION");
    }

    @Override
    public Integer getTuppleID() { return this.idPermission; }

    @Override
    public String getAttributIDName() { return "id_permission"; }

    @Override
    public void controler(Connection c) throws Exception {
        if (idRole == null) throw new IllegalArgumentException("Le rôle est obligatoire.");
        if (idModule == null) throw new IllegalArgumentException("Le module est obligatoire.");
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO role_permission (id_role, id_module, droit_lecture, droit_ecriture, droit_validation) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idRole);
            ps.setInt(2, idModule);
            ps.setBoolean(3, droitLecture);
            ps.setBoolean(4, droitEcriture);
            ps.setBoolean(5, droitValidation);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) this.idPermission = rs.getInt(1);
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE role_permission SET id_role=?, id_module=?, droit_lecture=?, droit_ecriture=?, droit_validation=? WHERE id_permission=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRole);
            ps.setInt(2, idModule);
            ps.setBoolean(3, droitLecture);
            ps.setBoolean(4, droitEcriture);
            ps.setBoolean(5, droitValidation);
            ps.setInt(6, idPermission);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM role_permission WHERE id_permission=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPermission);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String idRecherche, Connection c) throws Exception {
        String sql = "SELECT * FROM role_permission WHERE id_permission=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(idRecherche));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RolePermission p = new RolePermission();
                    p.idPermission = rs.getInt("id_permission");
                    p.idRole = rs.getInt("id_role");
                    p.idModule = rs.getInt("id_module");
                    p.droitLecture = rs.getBoolean("droit_lecture");
                    p.droitEcriture = rs.getBoolean("droit_ecriture");
                    p.droitValidation = rs.getBoolean("droit_validation");
                    return p;
                }
            }
        }
        return null;
    }

    // Méthodes de sélection et suppression
    public static List<RolePermission> rechercherParRole(int idRole, Connection c) throws Exception {
        String sql = "SELECT * FROM role_permission WHERE id_role = ?";
        List<RolePermission> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRole);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RolePermission p = new RolePermission();
                    p.idPermission = rs.getInt("id_permission");
                    p.idRole = rs.getInt("id_role");
                    p.idModule = rs.getInt("id_module");
                    p.droitLecture = rs.getBoolean("droit_lecture");
                    p.droitEcriture = rs.getBoolean("droit_ecriture");
                    p.droitValidation = rs.getBoolean("droit_validation");
                    list.add(p);
                }
            }
        }
        return list;
    }

    public static void supprimerParRole(int idRole, Connection c) throws Exception {
        String sql = "DELETE FROM role_permission WHERE id_role = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRole);
            ps.executeUpdate();
        }
    }

    // Getters & Setters
    public Integer getIdPermission() { return idPermission; }
    public void setIdPermission(Integer idPermission) { this.idPermission = idPermission; }
    public Integer getIdRole() { return idRole; }
    public void setIdRole(Integer idRole) { this.idRole = idRole; }
    public Integer getIdModule() { return idModule; }
    public void setIdModule(Integer idModule) { this.idModule = idModule; }
    public boolean isDroitLecture() { return droitLecture; }
    public void setDroitLecture(boolean droitLecture) { this.droitLecture = droitLecture; }
    public boolean isDroitEcriture() { return droitEcriture; }
    public void setDroitEcriture(boolean droitEcriture) { this.droitEcriture = droitEcriture; }
    public boolean isDroitValidation() { return droitValidation; }
    public void setDroitValidation(boolean droitValidation) { this.droitValidation = droitValidation; }
}
