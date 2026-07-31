package mg.galaxam.sirh.referentiel;

import bean.ClassMere;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RefMenu extends ClassMere {

    private String idMenu;
    private String libelle;
    private String icone;
    private String href;
    private Integer rang;
    private Integer niveau;
    private String idPere;

    // Représentation de l'arborescence (exclus de la persistance directe)
    private List<RefMenu> sousMenus = new ArrayList<>();

    public RefMenu() {
        this.setNomTable("REF_MENU");
    }

    @Override
    public Integer getTuppleID() {
        return null;
    }

    @Override
    public String getAttributIDName() {
        return "id_menu";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idMenu == null || idMenu.isBlank()) {
            throw new IllegalArgumentException("L'identifiant du menu est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du menu est obligatoire.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, idMenu);
            ps.setString(2, libelle);
            ps.setString(3, icone);
            ps.setString(4, href);
            ps.setInt(5, rang != null ? rang : 1);
            ps.setInt(6, niveau != null ? niveau : 1);
            ps.setString(7, idPere);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_menu SET libelle=?, icone=?, href=?, rang=?, niveau=?, id_pere=? WHERE id_menu=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, libelle);
            ps.setString(2, icone);
            ps.setString(3, href);
            ps.setInt(4, rang != null ? rang : 1);
            ps.setInt(5, niveau != null ? niveau : 1);
            ps.setString(6, idPere);
            ps.setString(7, idMenu);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_menu WHERE id_menu=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, idMenu);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String idRecherche, Connection c) throws Exception {
        String sql = "SELECT * FROM ref_menu WHERE id_menu=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, idRecherche);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefMenu m = new RefMenu();
                    m.idMenu = rs.getString("id_menu");
                    m.libelle = rs.getString("libelle");
                    m.icone = rs.getString("icone");
                    m.href = rs.getString("href");
                    m.rang = rs.getInt("rang");
                    m.niveau = rs.getInt("niveau");
                    m.idPere = rs.getString("id_pere");
                    return m;
                }
            }
        }
        return null;
    }

    // Méthode de sélection par Rôle
    public static List<RefMenu> rechercherParRole(int idRole, Connection c) throws Exception {
        String sql = "SELECT m.* FROM ref_menu m " +
                     "JOIN role_menu rm ON m.id_menu = rm.id_menu " +
                     "WHERE rm.id_role = ? " +
                     "ORDER BY m.niveau, m.rang";
        List<RefMenu> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRole);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RefMenu m = new RefMenu();
                    m.idMenu = rs.getString("id_menu");
                    m.libelle = rs.getString("libelle");
                    m.icone = rs.getString("icone");
                    m.href = rs.getString("href");
                    m.rang = rs.getInt("rang");
                    m.niveau = rs.getInt("niveau");
                    m.idPere = rs.getString("id_pere");
                    list.add(m);
                }
            }
        }
        return list;
    }

    // Getters & Setters
    public String getIdMenu() { return idMenu; }
    public void setIdMenu(String idMenu) { this.idMenu = idMenu; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getIcone() { return icone; }
    public void setIcone(String icone) { this.icone = icone; }
    public String getHref() { return href; }
    public void setHref(String href) { this.href = href; }
    public Integer getRang() { return rang; }
    public void setRang(Integer rang) { this.rang = rang; }
    public Integer getNiveau() { return niveau; }
    public void setNiveau(Integer niveau) { this.niveau = niveau; }
    public String getIdPere() { return idPere; }
    public void setIdPere(String idPere) { this.idPere = idPere; }
    public List<RefMenu> getSousMenus() { return sousMenus; }
    public void setSousMenus(List<RefMenu> sousMenus) { this.sousMenus = sousMenus; }
}
