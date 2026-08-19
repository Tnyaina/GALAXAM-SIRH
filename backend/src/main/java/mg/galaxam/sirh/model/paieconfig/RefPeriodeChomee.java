package mg.galaxam.sirh.model.paieconfig;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RefPeriodeChomee extends ClassMere {

    private Integer idPeriode;
    private String libelle;
    private Integer idSite;
    private Date dateDebut;
    private Date dateFin;
    private boolean estPaye = true;
    private BigDecimal tauxPaiement = new BigDecimal("100.00");

    public RefPeriodeChomee() {
        this.setNomTable("REF_PERIODE_CHOMEE");
    }

    @Override
    public Integer getTuppleID() {
        return idPeriode;
    }

    @Override
    public String getAttributIDName() {
        return "id_periode";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_periode_chomee (libelle, id_site, date_debut, date_fin, est_paye, taux_paiement) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id_periode";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, libelle);
            if (idSite != null) {
                ps.setInt(2, idSite);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setDate(3, dateDebut);
            if (dateFin != null) {
                ps.setDate(4, dateFin);
            } else {
                ps.setNull(4, Types.DATE);
            }
            ps.setBoolean(5, estPaye);
            ps.setBigDecimal(6, tauxPaiement);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idPeriode = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_periode_chomee SET libelle = ?, id_site = ?, date_debut = ?, date_fin = ?, est_paye = ?, taux_paiement = ? " +
                     "WHERE id_periode = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, libelle);
            if (idSite != null) {
                ps.setInt(2, idSite);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setDate(3, dateDebut);
            if (dateFin != null) {
                ps.setDate(4, dateFin);
            } else {
                ps.setNull(4, Types.DATE);
            }
            ps.setBoolean(5, estPaye);
            ps.setBigDecimal(6, tauxPaiement);
            ps.setInt(7, idPeriode);
            
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_periode_chomee WHERE id_periode = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPeriode);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_periode, libelle, id_site, date_debut, date_fin, est_paye, taux_paiement FROM ref_periode_chomee WHERE id_periode = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefPeriodeChomee item = new RefPeriodeChomee();
                    item.idPeriode = rs.getInt("id_periode");
                    item.libelle = rs.getString("libelle");
                    int siteId = rs.getInt("id_site");
                    if (!rs.wasNull()) {
                        item.idSite = siteId;
                    }
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    item.estPaye = rs.getBoolean("est_paye");
                    item.tauxPaiement = rs.getBigDecimal("taux_paiement");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<RefPeriodeChomee> listAll(Connection c) throws Exception {
        String sql = "SELECT id_periode, libelle, id_site, date_debut, date_fin, est_paye, taux_paiement FROM ref_periode_chomee ORDER BY date_debut DESC";
        List<RefPeriodeChomee> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RefPeriodeChomee item = new RefPeriodeChomee();
                item.idPeriode = rs.getInt("id_periode");
                item.libelle = rs.getString("libelle");
                int siteId = rs.getInt("id_site");
                if (!rs.wasNull()) {
                    item.idSite = siteId;
                }
                item.dateDebut = rs.getDate("date_debut");
                item.dateFin = rs.getDate("date_fin");
                item.estPaye = rs.getBoolean("est_paye");
                item.tauxPaiement = rs.getBigDecimal("taux_paiement");
                list.add(item);
            }
        }
        return list;
    }

    // Getters / Setters
    public Integer getIdPeriode() { return idPeriode; }
    public void setIdPeriode(Integer idPeriode) { this.idPeriode = idPeriode; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
    public boolean isEstPaye() { return estPaye; }
    public void setEstPaye(boolean estPaye) { this.estPaye = estPaye; }
    public BigDecimal getTauxPaiement() { return tauxPaiement; }
    public void setTauxPaiement(BigDecimal tauxPaiement) { this.tauxPaiement = tauxPaiement; }
}
