package mg.galaxam.sirh.model.paieconfig;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SiteCotisation extends ClassMere {

    private Integer idSiteCotisation;
    private Integer idSite;
    private Integer idTypeCotisation;
    private boolean estActif = true;

    // Propriétés jointes
    private String siteLibelle;
    private String cotisationCode;
    private String cotisationLibelle;

    public SiteCotisation() {
        this.setNomTable("SITE_COTISATION");
    }

    @Override
    public Integer getTuppleID() {
        return idSiteCotisation;
    }

    @Override
    public String getAttributIDName() {
        return "id_site_cotisation";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO site_cotisation (id_site, id_type_cotisation, est_actif) VALUES (?, ?, ?) RETURNING id_site_cotisation";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSite);
            ps.setInt(2, idTypeCotisation);
            ps.setBoolean(3, estActif);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idSiteCotisation = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE site_cotisation SET id_site = ?, id_type_cotisation = ?, est_actif = ? WHERE id_site_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSite);
            ps.setInt(2, idTypeCotisation);
            ps.setBoolean(3, estActif);
            ps.setInt(4, idSiteCotisation);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM site_cotisation WHERE id_site_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSiteCotisation);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT sc.id_site_cotisation, sc.id_site, sc.id_type_cotisation, sc.est_actif, " +
                     "s.libelle as site_libelle, tc.code as cotisation_code, tc.libelle as cotisation_libelle " +
                     "FROM site_cotisation sc " +
                     "JOIN ref_site s ON sc.id_site = s.id_site " +
                     "JOIN ref_type_cotisation tc ON sc.id_type_cotisation = tc.id_type_cotisation " +
                     "WHERE sc.id_site_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SiteCotisation item = new SiteCotisation();
                    item.idSiteCotisation = rs.getInt("id_site_cotisation");
                    item.idSite = rs.getInt("id_site");
                    item.idTypeCotisation = rs.getInt("id_type_cotisation");
                    item.estActif = rs.getBoolean("est_actif");
                    item.siteLibelle = rs.getString("site_libelle");
                    item.cotisationCode = rs.getString("cotisation_code");
                    item.cotisationLibelle = rs.getString("cotisation_libelle");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<SiteCotisation> listAll(Connection c) throws Exception {
        String sql = "SELECT sc.id_site_cotisation, sc.id_site, sc.id_type_cotisation, sc.est_actif, " +
                     "s.libelle as site_libelle, tc.code as cotisation_code, tc.libelle as cotisation_libelle " +
                     "FROM site_cotisation sc " +
                     "JOIN ref_site s ON sc.id_site = s.id_site " +
                     "JOIN ref_type_cotisation tc ON sc.id_type_cotisation = tc.id_type_cotisation " +
                     "ORDER BY s.libelle, tc.code";
        List<SiteCotisation> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SiteCotisation item = new SiteCotisation();
                item.idSiteCotisation = rs.getInt("id_site_cotisation");
                item.idSite = rs.getInt("id_site");
                item.idTypeCotisation = rs.getInt("id_type_cotisation");
                item.estActif = rs.getBoolean("est_actif");
                item.siteLibelle = rs.getString("site_libelle");
                item.cotisationCode = rs.getString("cotisation_code");
                item.cotisationLibelle = rs.getString("cotisation_libelle");
                list.add(item);
            }
        }
        return list;
    }

    /**
     * Désactive en masse toutes les instances de site_cotisation rattachées à un type de cotisation donné.
     *
     * @param idTypeCotisation identifiant du type de cotisation
     * @param c connexion JDBC active et transactionnelle
     * @return le nombre de lignes mises à jour
     * @throws Exception en cas d'erreur SQL
     */
    public static int desactiverParTypeCotisation(int idTypeCotisation, Connection c) throws Exception {
        String sql = "UPDATE site_cotisation SET est_actif = false WHERE id_type_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTypeCotisation);
            return ps.executeUpdate();
        }
    }

    // Getters / Setters
    public Integer getIdSiteCotisation() { return idSiteCotisation; }
    public void setIdSiteCotisation(Integer idSiteCotisation) { this.idSiteCotisation = idSiteCotisation; }
    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }
    public Integer getIdTypeCotisation() { return idTypeCotisation; }
    public void setIdTypeCotisation(Integer idTypeCotisation) { this.idTypeCotisation = idTypeCotisation; }
    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
    public String getSiteLibelle() { return siteLibelle; }
    public void setSiteLibelle(String siteLibelle) { this.siteLibelle = siteLibelle; }
    public String getCotisationCode() { return cotisationCode; }
    public void setCotisationCode(String cotisationCode) { this.cotisationCode = cotisationCode; }
    public String getCotisationLibelle() { return cotisationLibelle; }
    public void setCotisationLibelle(String cotisationLibelle) { this.cotisationLibelle = cotisationLibelle; }
}
