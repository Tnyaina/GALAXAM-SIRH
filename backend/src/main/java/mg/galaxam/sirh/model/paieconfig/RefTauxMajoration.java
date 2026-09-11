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
    private Integer idConfigLegale;
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
        String sql = "INSERT INTO ref_taux_majoration (id_config_legale, type_majoration, taux, description, date_debut, date_fin) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id_taux_majoration";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (idConfigLegale != null) {
                ps.setInt(1, idConfigLegale);
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setString(2, typeMajoration);
            ps.setBigDecimal(3, taux);
            ps.setString(4, description);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);

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
        String sql = "UPDATE ref_taux_majoration SET id_config_legale = ?, type_majoration = ?, taux = ?, description = ?, date_debut = ?, date_fin = ? " +
                     "WHERE id_taux_majoration = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (idConfigLegale != null) {
                ps.setInt(1, idConfigLegale);
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setString(2, typeMajoration);
            ps.setBigDecimal(3, taux);
            ps.setString(4, description);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);
            ps.setInt(7, idTauxMajoration);
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
        String sql = "SELECT id_taux_majoration, id_config_legale, type_majoration, taux, description, date_debut, date_fin FROM ref_taux_majoration WHERE id_taux_majoration = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapFromResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<RefTauxMajoration> listAll(Connection c) throws Exception {
        String sql = "SELECT id_taux_majoration, id_config_legale, type_majoration, taux, description, date_debut, date_fin FROM ref_taux_majoration ORDER BY id_config_legale, type_majoration";
        List<RefTauxMajoration> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapFromResultSet(rs));
            }
        }
        return list;
    }

    public static List<RefTauxMajoration> listByConfig(int idConfigLegale, Connection c) throws Exception {
        String sql = "SELECT id_taux_majoration, id_config_legale, type_majoration, taux, description, date_debut, date_fin " +
                     "FROM ref_taux_majoration WHERE id_config_legale = ? " +
                     "ORDER BY CASE type_majoration " +
                     "  WHEN 'HS_30' THEN 1 " +
                     "  WHEN 'HS_50' THEN 2 " +
                     "  WHEN 'NUIT' THEN 3 " +
                     "  WHEN 'DIMANCHE' THEN 4 " +
                     "  WHEN 'FERIE' THEN 5 " +
                     "  ELSE 6 END, id_taux_majoration ASC";
        List<RefTauxMajoration> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idConfigLegale);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFromResultSet(rs));
                }
            }
        }
        return list;
    }

    private static RefTauxMajoration mapFromResultSet(ResultSet rs) throws Exception {
        RefTauxMajoration item = new RefTauxMajoration();
        item.idTauxMajoration = rs.getInt("id_taux_majoration");
        int cfg = rs.getInt("id_config_legale");
        item.idConfigLegale = rs.wasNull() ? null : cfg;
        item.typeMajoration = rs.getString("type_majoration");
        item.taux = rs.getBigDecimal("taux");
        item.description = rs.getString("description");
        item.dateDebut = rs.getDate("date_debut");
        item.dateFin = rs.getDate("date_fin");
        return item;
    }

    // Getters / Setters
    public Integer getIdTauxMajoration() { return idTauxMajoration; }
    public void setIdTauxMajoration(Integer idTauxMajoration) { this.idTauxMajoration = idTauxMajoration; }
    public Integer getIdConfigLegale() { return idConfigLegale; }
    public void setIdConfigLegale(Integer idConfigLegale) { this.idConfigLegale = idConfigLegale; }
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
