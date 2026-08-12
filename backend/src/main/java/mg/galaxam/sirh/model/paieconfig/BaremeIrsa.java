package mg.galaxam.sirh.model.paieconfig;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class BaremeIrsa extends ClassMere {

    private Integer idTranche;
    private Integer numeroTranche;
    private BigDecimal trancheMin;
    private BigDecimal trancheMax;
    private BigDecimal taux;
    private Date dateDebut;
    private Date dateFin;

    public BaremeIrsa() {
        this.setNomTable("BAREME_IRSA");
    }

    @Override
    public Integer getTuppleID() {
        return idTranche;
    }

    @Override
    public String getAttributIDName() {
        return "id_tranche";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO bareme_irsa (numero_tranche, tranche_min, tranche_max, taux, date_debut, date_fin) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id_tranche";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, numeroTranche);
            ps.setBigDecimal(2, trancheMin);
            ps.setBigDecimal(3, trancheMax);
            ps.setBigDecimal(4, taux);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idTranche = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE bareme_irsa SET numero_tranche = ?, tranche_min = ?, tranche_max = ?, taux = ?, " +
                     "date_debut = ?, date_fin = ? WHERE id_tranche = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, numeroTranche);
            ps.setBigDecimal(2, trancheMin);
            ps.setBigDecimal(3, trancheMax);
            ps.setBigDecimal(4, taux);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);
            ps.setInt(7, idTranche);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM bareme_irsa WHERE id_tranche = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTranche);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_tranche, numero_tranche, tranche_min, tranche_max, taux, date_debut, date_fin FROM bareme_irsa WHERE id_tranche = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BaremeIrsa item = new BaremeIrsa();
                    item.idTranche = rs.getInt("id_tranche");
                    item.numeroTranche = rs.getInt("numero_tranche");
                    item.trancheMin = rs.getBigDecimal("tranche_min");
                    item.trancheMax = rs.getBigDecimal("tranche_max");
                    item.taux = rs.getBigDecimal("taux");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<BaremeIrsa> listAll(Connection c) throws Exception {
        String sql = "SELECT id_tranche, numero_tranche, tranche_min, tranche_max, taux, date_debut, date_fin FROM bareme_irsa ORDER BY numero_tranche";
        List<BaremeIrsa> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BaremeIrsa item = new BaremeIrsa();
                item.idTranche = rs.getInt("id_tranche");
                item.numeroTranche = rs.getInt("numero_tranche");
                item.trancheMin = rs.getBigDecimal("tranche_min");
                item.trancheMax = rs.getBigDecimal("tranche_max");
                item.taux = rs.getBigDecimal("taux");
                item.dateDebut = rs.getDate("date_debut");
                item.dateFin = rs.getDate("date_fin");
                list.add(item);
            }
        }
        return list;
    }

    // Getters / Setters
    public Integer getIdTranche() { return idTranche; }
    public void setIdTranche(Integer idTranche) { this.idTranche = idTranche; }
    public Integer getNumeroTranche() { return numeroTranche; }
    public void setNumeroTranche(Integer numeroTranche) { this.numeroTranche = numeroTranche; }
    public BigDecimal getTrancheMin() { return trancheMin; }
    public void setTrancheMin(BigDecimal trancheMin) { this.trancheMin = trancheMin; }
    public BigDecimal getTrancheMax() { return trancheMax; }
    public void setTrancheMax(BigDecimal trancheMax) { this.trancheMax = trancheMax; }
    public BigDecimal getTaux() { return taux; }
    public void setTaux(BigDecimal taux) { this.taux = taux; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
