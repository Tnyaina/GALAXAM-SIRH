package mg.galaxam.sirh.paieconfig.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SiteCalendrierFerie extends ClassMere {

    private Integer idSiteFerie;
    private Integer idSite;
    private Integer idFerie;

    // Propriétés jointes
    private String siteLibelle;
    private String ferieLibelle;
    private Date ferieDate;

    public SiteCalendrierFerie() {
        this.setNomTable("SITE_CALENDRIER_FERIE");
    }

    @Override
    public Integer getTuppleID() {
        return idSiteFerie;
    }

    @Override
    public String getAttributIDName() {
        return "id_site_ferie";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO site_calendrier_ferie (id_site, id_ferie) VALUES (?, ?) RETURNING id_site_ferie";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSite);
            ps.setInt(2, idFerie);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idSiteFerie = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE site_calendrier_ferie SET id_site = ?, id_ferie = ? WHERE id_site_ferie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSite);
            ps.setInt(2, idFerie);
            ps.setInt(3, idSiteFerie);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM site_calendrier_ferie WHERE id_site_ferie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSiteFerie);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT scf.id_site_ferie, scf.id_site, scf.id_ferie, s.libelle as site_libelle, " +
                     "f.libelle as ferie_libelle, f.date_ferie as ferie_date " +
                     "FROM site_calendrier_ferie scf " +
                     "JOIN ref_site s ON scf.id_site = s.id_site " +
                     "JOIN ref_calendrier_ferie f ON scf.id_ferie = f.id_ferie " +
                     "WHERE scf.id_site_ferie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SiteCalendrierFerie item = new SiteCalendrierFerie();
                    item.idSiteFerie = rs.getInt("id_site_ferie");
                    item.idSite = rs.getInt("id_site");
                    item.idFerie = rs.getInt("id_ferie");
                    item.siteLibelle = rs.getString("site_libelle");
                    item.ferieLibelle = rs.getString("ferie_libelle");
                    item.ferieDate = rs.getDate("ferie_date");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<SiteCalendrierFerie> listAll(Connection c) throws Exception {
        String sql = "SELECT scf.id_site_ferie, scf.id_site, scf.id_ferie, s.libelle as site_libelle, " +
                     "f.libelle as ferie_libelle, f.date_ferie as ferie_date " +
                     "FROM site_calendrier_ferie scf " +
                     "JOIN ref_site s ON scf.id_site = s.id_site " +
                     "JOIN ref_calendrier_ferie f ON scf.id_ferie = f.id_ferie " +
                     "ORDER BY f.date_ferie DESC, s.libelle";
        List<SiteCalendrierFerie> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SiteCalendrierFerie item = new SiteCalendrierFerie();
                item.idSiteFerie = rs.getInt("id_site_ferie");
                item.idSite = rs.getInt("id_site");
                item.idFerie = rs.getInt("id_ferie");
                item.siteLibelle = rs.getString("site_libelle");
                item.ferieLibelle = rs.getString("ferie_libelle");
                item.ferieDate = rs.getDate("ferie_date");
                list.add(item);
            }
        }
        return list;
    }

    // Getters / Setters
    public Integer getIdSiteFerie() { return idSiteFerie; }
    public void setIdSiteFerie(Integer idSiteFerie) { this.idSiteFerie = idSiteFerie; }
    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }
    public Integer getIdFerie() { return idFerie; }
    public void setIdFerie(Integer idFerie) { this.idFerie = idFerie; }
    public String getSiteLibelle() { return siteLibelle; }
    public void setSiteLibelle(String siteLibelle) { this.siteLibelle = siteLibelle; }
    public String getFerieLibelle() { return ferieLibelle; }
    public void setFerieLibelle(String ferieLibelle) { this.ferieLibelle = ferieLibelle; }
    public Date getFerieDate() { return ferieDate; }
    public void setFerieDate(Date ferieDate) { this.ferieDate = ferieDate; }
}
