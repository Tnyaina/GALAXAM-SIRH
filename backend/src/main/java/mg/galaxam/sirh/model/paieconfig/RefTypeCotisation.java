package mg.galaxam.sirh.model.paieconfig;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class RefTypeCotisation extends ClassMere {

    private Integer idTypeCotisation;
    private String code;
    private String libelle;
    private BigDecimal tauxSalarial;
    private BigDecimal tauxPatronal;
    private Date dateDebut;
    private Date dateFin;

    public RefTypeCotisation() {
        this.setNomTable("REF_TYPE_COTISATION");
    }

    @Override
    public Integer getTuppleID() {
        return idTypeCotisation;
    }

    @Override
    public String getAttributIDName() {
        return "id_type_cotisation";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_type_cotisation (code, libelle, taux_salarial, taux_patronal, date_debut, date_fin) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id_type_cotisation";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBigDecimal(3, tauxSalarial);
            ps.setBigDecimal(4, tauxPatronal);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idTypeCotisation = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_type_cotisation SET code = ?, libelle = ?, taux_salarial = ?, taux_patronal = ?, " +
                     "date_debut = ?, date_fin = ? WHERE id_type_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBigDecimal(3, tauxSalarial);
            ps.setBigDecimal(4, tauxPatronal);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);
            ps.setInt(7, idTypeCotisation);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_type_cotisation WHERE id_type_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTypeCotisation);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_type_cotisation, code, libelle, taux_salarial, taux_patronal, date_debut, date_fin FROM ref_type_cotisation WHERE id_type_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefTypeCotisation item = new RefTypeCotisation();
                    item.idTypeCotisation = rs.getInt("id_type_cotisation");
                    item.code = rs.getString("code");
                    item.libelle = rs.getString("libelle");
                    item.tauxSalarial = rs.getBigDecimal("taux_salarial");
                    item.tauxPatronal = rs.getBigDecimal("taux_patronal");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<RefTypeCotisation> listAll(Connection c) throws Exception {
        String sql = "SELECT id_type_cotisation, code, libelle, taux_salarial, taux_patronal, date_debut, date_fin FROM ref_type_cotisation ORDER BY code";
        List<RefTypeCotisation> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RefTypeCotisation item = new RefTypeCotisation();
                item.idTypeCotisation = rs.getInt("id_type_cotisation");
                item.code = rs.getString("code");
                item.libelle = rs.getString("libelle");
                item.tauxSalarial = rs.getBigDecimal("taux_salarial");
                item.tauxPatronal = rs.getBigDecimal("taux_patronal");
                item.dateDebut = rs.getDate("date_debut");
                item.dateFin = rs.getDate("date_fin");
                list.add(item);
            }
        }
        return list;
    }

    // Getters / Setters
    public Integer getIdTypeCotisation() { return idTypeCotisation; }
    public void setIdTypeCotisation(Integer idTypeCotisation) { this.idTypeCotisation = idTypeCotisation; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public BigDecimal getTauxSalarial() { return tauxSalarial; }
    public void setTauxSalarial(BigDecimal tauxSalarial) { this.tauxSalarial = tauxSalarial; }
    public BigDecimal getTauxPatronal() { return tauxPatronal; }
    public void setTauxPatronal(BigDecimal tauxPatronal) { this.tauxPatronal = tauxPatronal; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
