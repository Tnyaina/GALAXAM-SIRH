package mg.galaxam.sirh.model.paieconfig;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class RefCalendrierFerie extends ClassMere {

    private Integer idFerie;
    private Date dateFerie;
    private String libelle;
    private boolean estChome = true;
    private boolean estNational = true;

    public RefCalendrierFerie() {
        this.setNomTable("REF_CALENDRIER_FERIE");
    }

    @Override
    public Integer getTuppleID() {
        return idFerie;
    }

    @Override
    public String getAttributIDName() {
        return "id_ferie";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_calendrier_ferie (date_ferie, libelle, est_chome, est_national) " +
                     "VALUES (?, ?, ?, ?) RETURNING id_ferie";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, dateFerie);
            ps.setString(2, libelle);
            ps.setBoolean(3, estChome);
            ps.setBoolean(4, estNational);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idFerie = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_calendrier_ferie SET date_ferie = ?, libelle = ?, est_chome = ?, est_national = ? " +
                     "WHERE id_ferie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, dateFerie);
            ps.setString(2, libelle);
            ps.setBoolean(3, estChome);
            ps.setBoolean(4, estNational);
            ps.setInt(5, idFerie);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_calendrier_ferie WHERE id_ferie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idFerie);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_ferie, date_ferie, libelle, est_chome, est_national FROM ref_calendrier_ferie WHERE id_ferie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefCalendrierFerie item = new RefCalendrierFerie();
                    item.idFerie = rs.getInt("id_ferie");
                    item.dateFerie = rs.getDate("date_ferie");
                    item.libelle = rs.getString("libelle");
                    item.estChome = rs.getBoolean("est_chome");
                    item.estNational = rs.getBoolean("est_national");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<RefCalendrierFerie> listAll(Connection c) throws Exception {
        String sql = "SELECT id_ferie, date_ferie, libelle, est_chome, est_national FROM ref_calendrier_ferie ORDER BY date_ferie DESC";
        List<RefCalendrierFerie> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RefCalendrierFerie item = new RefCalendrierFerie();
                item.idFerie = rs.getInt("id_ferie");
                item.dateFerie = rs.getDate("date_ferie");
                item.libelle = rs.getString("libelle");
                item.estChome = rs.getBoolean("est_chome");
                item.estNational = rs.getBoolean("est_national");
                list.add(item);
            }
        }
        return list;
    }

    // Getters / Setters
    public Integer getIdFerie() { return idFerie; }
    public void setIdFerie(Integer idFerie) { this.idFerie = idFerie; }
    public Date getDateFerie() { return dateFerie; }
    public void setDateFerie(Date dateFerie) { this.dateFerie = dateFerie; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public boolean isEstChome() { return estChome; }
    public void setEstChome(boolean estChome) { this.estChome = estChome; }
    public boolean isEstNational() { return estNational; }
    public void setEstNational(boolean estNational) { this.estNational = estNational; }
}
