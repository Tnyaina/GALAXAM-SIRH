package mg.galaxam.sirh.model.paieconfig;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ParametreGlobal extends ClassMere {

    private Integer idParametre;
    private String codeParametre;
    private String valeur;
    private String description;
    private String categorie;
    private Date dateDebut;
    private Date dateFin;

    public ParametreGlobal() {
        this.setNomTable("PARAMETRE_GLOBAL");
    }

    @Override
    public Integer getTuppleID() {
        return idParametre;
    }

    @Override
    public String getAttributIDName() {
        return "id_parametre";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO parametre_global (code_parametre, valeur, description, categorie, date_debut, date_fin) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id_parametre";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeParametre);
            ps.setString(2, valeur);
            ps.setString(3, description);
            ps.setString(4, categorie);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idParametre = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE parametre_global SET code_parametre = ?, valeur = ?, description = ?, categorie = ?, date_debut = ?, date_fin = ? " +
                     "WHERE id_parametre = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeParametre);
            ps.setString(2, valeur);
            ps.setString(3, description);
            ps.setString(4, categorie);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);
            ps.setInt(7, idParametre);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM parametre_global WHERE id_parametre = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idParametre);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_parametre, code_parametre, valeur, description, categorie, date_debut, date_fin FROM parametre_global WHERE id_parametre = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ParametreGlobal item = new ParametreGlobal();
                    item.idParametre = rs.getInt("id_parametre");
                    item.codeParametre = rs.getString("code_parametre");
                    item.valeur = rs.getString("valeur");
                    item.description = rs.getString("description");
                    item.categorie = rs.getString("categorie");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<ParametreGlobal> listAll(Connection c) throws Exception {
        String sql = "SELECT id_parametre, code_parametre, valeur, description, categorie, date_debut, date_fin FROM parametre_global ORDER BY categorie ASC, code_parametre ASC";
        List<ParametreGlobal> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ParametreGlobal item = new ParametreGlobal();
                item.idParametre = rs.getInt("id_parametre");
                item.codeParametre = rs.getString("code_parametre");
                item.valeur = rs.getString("valeur");
                item.description = rs.getString("description");
                item.categorie = rs.getString("categorie");
                item.dateDebut = rs.getDate("date_debut");
                item.dateFin = rs.getDate("date_fin");
                list.add(item);
            }
        }
        return list;
    }

    public static List<ParametreGlobal> listByCategorie(String categorie, Connection c) throws Exception {
        if (categorie == null || categorie.isBlank()) return listAll(c);
        String sql = "SELECT id_parametre, code_parametre, valeur, description, categorie, date_debut, date_fin FROM parametre_global WHERE UPPER(categorie) = UPPER(?) ORDER BY code_parametre ASC";
        List<ParametreGlobal> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, categorie.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ParametreGlobal item = new ParametreGlobal();
                    item.idParametre = rs.getInt("id_parametre");
                    item.codeParametre = rs.getString("code_parametre");
                    item.valeur = rs.getString("valeur");
                    item.description = rs.getString("description");
                    item.categorie = rs.getString("categorie");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    list.add(item);
                }
            }
        }
        return list;
    }

    // Getters / Setters
    public Integer getIdParametre() { return idParametre; }
    public void setIdParametre(Integer idParametre) { this.idParametre = idParametre; }
    public String getCodeParametre() { return codeParametre; }
    public void setCodeParametre(String codeParametre) { this.codeParametre = codeParametre; }
    public String getValeur() { return valeur; }
    public void setValeur(String valeur) { this.valeur = valeur; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
