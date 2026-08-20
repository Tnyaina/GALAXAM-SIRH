package mg.galaxam.sirh.model.paieconfig;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class RefTauxMajoration extends ClassMere {

    private Integer idTauxMajoration;
    private String typeMajoration;
    private BigDecimal taux;
    private String description;
    private Date dateDebut;
    private Date dateFin;

    public RefTauxMajoration() {
        this.setNomTable("REF_TAUX_MAJORATION");
    }

    @Override
    public Integer getTuppleID() {
        return idTauxMajoration;
    }

    @Override
    public String getAttributIDName() {
        return "id_taux_majoration";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_taux_majoration (type_majoration, taux, description, date_debut, date_fin) " +
                     "VALUES (?, ?, ?, ?, ?) RETURNING id_taux_majoration";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, typeMajoration);
            ps.setBigDecimal(2, taux);
            ps.setString(3, description);
            ps.setDate(4, dateDebut);
            ps.setDate(5, dateFin);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idTauxMajoration = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_taux_majoration SET type_majoration = ?, taux = ?, description = ?, date_debut = ?, date_fin = ? " +
                     "WHERE id_taux_majoration = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, typeMajoration);
            ps.setBigDecimal(2, taux);
            ps.setString(3, description);
            ps.setDate(4, dateDebut);
            ps.setDate(5, dateFin);
            ps.setInt(6, idTauxMajoration);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_taux_majoration WHERE id_taux_majoration = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTauxMajoration);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_taux_majoration, type_majoration, taux, description, date_debut, date_fin FROM ref_taux_majoration WHERE id_taux_majoration = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefTauxMajoration item = new RefTauxMajoration();
                    item.idTauxMajoration = rs.getInt("id_taux_majoration");
                    item.typeMajoration = rs.getString("type_majoration");
                    item.taux = rs.getBigDecimal("taux");
                    item.description = rs.getString("description");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<RefTauxMajoration> listAll(Connection c) throws Exception {
        String sql = "SELECT id_taux_majoration, type_majoration, taux, description, date_debut, date_fin FROM ref_taux_majoration ORDER BY type_majoration";
        List<RefTauxMajoration> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RefTauxMajoration item = new RefTauxMajoration();
                item.idTauxMajoration = rs.getInt("id_taux_majoration");
                item.typeMajoration = rs.getString("type_majoration");
                item.taux = rs.getBigDecimal("taux");
                item.description = rs.getString("description");
                item.dateDebut = rs.getDate("date_debut");
                item.dateFin = rs.getDate("date_fin");
                list.add(item);
            }
        }
        return list;
    }

    // Getters / Setters
    public Integer getIdTauxMajoration() { return idTauxMajoration; }
    public void setIdTauxMajoration(Integer idTauxMajoration) { this.idTauxMajoration = idTauxMajoration; }
    public String getTypeMajoration() { return typeMajoration; }
    public void setTypeMajoration(String typeMajoration) { this.typeMajoration = typeMajoration; }
    public BigDecimal getTaux() { return taux; }
    public void setTaux(BigDecimal taux) { this.taux = taux; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
