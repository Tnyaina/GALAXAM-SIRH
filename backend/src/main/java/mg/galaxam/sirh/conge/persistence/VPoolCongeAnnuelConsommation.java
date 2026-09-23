package mg.galaxam.sirh.conge.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class VPoolCongeAnnuelConsommation extends ClassMere {

    private Integer idEmploye;
    private Integer idPool;
    private Integer annee;
    private BigDecimal joursConsommes;

    public VPoolCongeAnnuelConsommation() {
        this.setNomTable("V_POOL_CONGE_ANNUEL_CONSOMMATION");
    }

    @Override
    public Integer getTuppleID() {
        return this.idEmploye;
    }

    @Override
    public String getAttributIDName() {
        return "id_employe";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        throw new UnsupportedOperationException("Vue en lecture seule.");
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        throw new UnsupportedOperationException("Vue en lecture seule.");
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        throw new UnsupportedOperationException("Vue en lecture seule.");
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return null;
    }

    public static List<VPoolCongeAnnuelConsommation> listParEmployeEtAnnee(Integer idEmploye, Integer annee, Connection c) throws Exception {
        List<VPoolCongeAnnuelConsommation> list = new ArrayList<>();
        if (idEmploye == null) return list;
        String sql = "SELECT id_employe, id_pool, annee, jours_consommes " +
                "FROM v_pool_conge_annuel_consommation " +
                "WHERE id_employe = ? AND annee = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, annee != null ? annee : java.time.Year.now().getValue());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VPoolCongeAnnuelConsommation obj = new VPoolCongeAnnuelConsommation();
                    obj.setIdEmploye(rs.getInt("id_employe"));
                    obj.setIdPool(rs.getInt("id_pool"));
                    obj.setAnnee(rs.getInt("annee"));
                    obj.setJoursConsommes(rs.getBigDecimal("jours_consommes"));
                    list.add(obj);
                }
            }
        }
        return list;
    }

    // Getters / Setters
    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdPool() { return idPool; }
    public void setIdPool(Integer idPool) { this.idPool = idPool; }

    public Integer getAnnee() { return annee; }
    public void setAnnee(Integer annee) { this.annee = annee; }

    public BigDecimal getJoursConsommes() { return joursConsommes; }
    public void setJoursConsommes(BigDecimal joursConsommes) { this.joursConsommes = joursConsommes; }
}
