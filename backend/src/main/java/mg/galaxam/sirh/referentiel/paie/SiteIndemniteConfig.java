package mg.galaxam.sirh.referentiel.paie;

import bean.ClassMere;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SiteIndemniteConfig extends ClassMere {

    private Integer idSiteIndemnite;
    private Integer idSite;
    private Integer idIndemnite;
    private String periodicite = "JOURNALIER";
    private BigDecimal montantReference;
    private Date dateDebut;
    private Date dateFin;
    private boolean estActif = true;

    // Propriétés jointes
    private String siteLibelle;
    private String indemniteCode;
    private String indemniteLibelle;

    public SiteIndemniteConfig() {
        this.setNomTable("SITE_INDEMNITE_CONFIG");
    }

    @Override
    public Integer getTuppleID() {
        return idSiteIndemnite;
    }

    @Override
    public String getAttributIDName() {
        return "id_site_indemnite";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO site_indemnite_config (id_site, id_indemnite, periodicite, montant_reference, date_debut, date_fin, est_actif) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_site_indemnite";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSite);
            ps.setInt(2, idIndemnite);
            ps.setString(3, periodicite);
            ps.setBigDecimal(4, montantReference);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);
            ps.setBoolean(7, estActif);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idSiteIndemnite = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE site_indemnite_config SET id_site = ?, id_indemnite = ?, periodicite = ?, " +
                     "montant_reference = ?, date_debut = ?, date_fin = ?, est_actif = ? WHERE id_site_indemnite = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSite);
            ps.setInt(2, idIndemnite);
            ps.setString(3, periodicite);
            ps.setBigDecimal(4, montantReference);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);
            ps.setBoolean(7, estActif);
            ps.setInt(8, idSiteIndemnite);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM site_indemnite_config WHERE id_site_indemnite = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSiteIndemnite);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT sic.id_site_indemnite, sic.id_site, sic.id_indemnite, sic.periodicite, sic.montant_reference, " +
                     "sic.date_debut, sic.date_fin, sic.est_actif, s.libelle as site_libelle, i.code as indemnite_code, i.libelle as indemnite_libelle " +
                     "FROM site_indemnite_config sic " +
                     "JOIN ref_site s ON sic.id_site = s.id_site " +
                     "JOIN ref_indemnite i ON sic.id_indemnite = i.id_indemnite " +
                     "WHERE sic.id_site_indemnite = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SiteIndemniteConfig item = new SiteIndemniteConfig();
                    item.idSiteIndemnite = rs.getInt("id_site_indemnite");
                    item.idSite = rs.getInt("id_site");
                    item.idIndemnite = rs.getInt("id_indemnite");
                    item.periodicite = rs.getString("periodicite");
                    item.montantReference = rs.getBigDecimal("montant_reference");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    item.estActif = rs.getBoolean("est_actif");
                    item.siteLibelle = rs.getString("site_libelle");
                    item.indemniteCode = rs.getString("indemnite_code");
                    item.indemniteLibelle = rs.getString("indemnite_libelle");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<SiteIndemniteConfig> listAll(Connection c) throws Exception {
        String sql = "SELECT sic.id_site_indemnite, sic.id_site, sic.id_indemnite, sic.periodicite, sic.montant_reference, " +
                     "sic.date_debut, sic.date_fin, sic.est_actif, s.libelle as site_libelle, i.code as indemnite_code, i.libelle as indemnite_libelle " +
                     "FROM site_indemnite_config sic " +
                     "JOIN ref_site s ON sic.id_site = s.id_site " +
                     "JOIN ref_indemnite i ON sic.id_indemnite = i.id_indemnite " +
                     "ORDER BY s.libelle, i.code";
        List<SiteIndemniteConfig> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SiteIndemniteConfig item = new SiteIndemniteConfig();
                item.idSiteIndemnite = rs.getInt("id_site_indemnite");
                item.idSite = rs.getInt("id_site");
                item.idIndemnite = rs.getInt("id_indemnite");
                item.periodicite = rs.getString("periodicite");
                item.montantReference = rs.getBigDecimal("montant_reference");
                item.dateDebut = rs.getDate("date_debut");
                item.dateFin = rs.getDate("date_fin");
                item.estActif = rs.getBoolean("est_actif");
                item.siteLibelle = rs.getString("site_libelle");
                item.indemniteCode = rs.getString("indemnite_code");
                item.indemniteLibelle = rs.getString("indemnite_libelle");
                list.add(item);
            }
        }
        return list;
    }

    // Getters / Setters
    public Integer getIdSiteIndemnite() { return idSiteIndemnite; }
    public void setIdSiteIndemnite(Integer idSiteIndemnite) { this.idSiteIndemnite = idSiteIndemnite; }
    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }
    public Integer getIdIndemnite() { return idIndemnite; }
    public void setIdIndemnite(Integer idIndemnite) { this.idIndemnite = idIndemnite; }
    public String getPeriodicite() { return periodicite; }
    public void setPeriodicite(String periodicite) { this.periodicite = periodicite; }
    public BigDecimal getMontantReference() { return montantReference; }
    public void setMontantReference(BigDecimal montantReference) { this.montantReference = montantReference; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
    public String getSiteLibelle() { return siteLibelle; }
    public void setSiteLibelle(String siteLibelle) { this.siteLibelle = siteLibelle; }
    public String getIndemniteCode() { return indemniteCode; }
    public void setIndemniteCode(String indemniteCode) { this.indemniteCode = indemniteCode; }
    public String getIndemniteLibelle() { return indemniteLibelle; }
    public void setIndemniteLibelle(String indemniteLibelle) { this.indemniteLibelle = indemniteLibelle; }
}
