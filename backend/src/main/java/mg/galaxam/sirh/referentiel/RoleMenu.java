package mg.galaxam.sirh.referentiel;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

public class RoleMenu extends ClassMere {

    private Integer idRole;
    private String idMenu;

    public RoleMenu() {
        this.setNomTable("ROLE_MENU");
    }

    @Override
    public Integer getTuppleID() {
        // Clé composite, on retourne le idRole comme ID technique entier pour la signature
        return this.idRole;
    }

    @Override
    public String getAttributIDName() {
        return "id_role, id_menu";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO role_menu (id_role, id_menu) VALUES (?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRole);
            ps.setString(2, idMenu);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        throw new UnsupportedOperationException("Les liaisons rôle-menu ne se modifient pas, elles se suppriment et s'insèrent.");
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM role_menu WHERE id_role=? AND id_menu=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRole);
            ps.setString(2, idMenu);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        return null;
    }

    // Méthodes de sélection et suppression
    public static void supprimerParRole(int idRole, Connection c) throws Exception {
        String sql = "DELETE FROM role_menu WHERE id_role = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRole);
            ps.executeUpdate();
        }
    }

    public static List<RoleMenu> listerParRole(int idRole, Connection c) throws Exception {
        String sql = "SELECT * FROM role_menu WHERE id_role = ?";
        List<RoleMenu> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRole);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RoleMenu rm = new RoleMenu();
                    rm.setIdRole(rs.getInt("id_role"));
                    rm.setIdMenu(rs.getString("id_menu"));
                    list.add(rm);
                }
            }
        }
        return list;
    }

    // Getters & Setters
    public Integer getIdRole() { return idRole; }
    public void setIdRole(Integer idRole) { this.idRole = idRole; }
    public String getIdMenu() { return idMenu; }
    public void setIdMenu(String idMenu) { this.idMenu = idMenu; }
}
