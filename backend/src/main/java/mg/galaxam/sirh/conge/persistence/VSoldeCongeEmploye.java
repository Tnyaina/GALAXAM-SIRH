package mg.galaxam.sirh.conge.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class VSoldeCongeEmploye extends ClassMere {

    private Integer idEmploye;
    private BigDecimal soldeActuel;
    private BigDecimal totalAcquis;
    private BigDecimal totalBonification;
    private BigDecimal totalPris;
    private BigDecimal totalExpire;
    private Date derniereAcquisition;
    private BigDecimal tauxJournalierConge;
    private String sourceTauxConge;
    private BigDecimal salaireBase;

    public VSoldeCongeEmploye() {
        this.setNomTable("V_SOLDE_CONGE_EMPLOYE");
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
        return rechercherParEmploye(Integer.parseInt(id), c);
    }

    public static VSoldeCongeEmploye rechercherParEmploye(Integer idEmploye, Connection c) throws Exception {
        if (idEmploye == null) return null;
        String sql = "SELECT id_employe, solde_actuel, total_acquis, total_bonification, total_pris, total_expire, derniere_acquisition " +
                "FROM v_solde_conge_employe WHERE id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        // Si aucun mouvement enregistré pour cet employé, retourner un solde à 0 par défaut
        VSoldeCongeEmploye soldeDefaut = new VSoldeCongeEmploye();
        soldeDefaut.setIdEmploye(idEmploye);
        soldeDefaut.setSoldeActuel(BigDecimal.ZERO);
        soldeDefaut.setTotalAcquis(BigDecimal.ZERO);
        soldeDefaut.setTotalBonification(BigDecimal.ZERO);
        soldeDefaut.setTotalPris(BigDecimal.ZERO);
        soldeDefaut.setTotalExpire(BigDecimal.ZERO);
        return soldeDefaut;
    }

    private static VSoldeCongeEmploye mapResultSet(ResultSet rs) throws Exception {
        VSoldeCongeEmploye obj = new VSoldeCongeEmploye();
        obj.setIdEmploye(rs.getInt("id_employe"));
        obj.setSoldeActuel(rs.getBigDecimal("solde_actuel"));
        obj.setTotalAcquis(rs.getBigDecimal("total_acquis"));
        obj.setTotalBonification(rs.getBigDecimal("total_bonification"));
        obj.setTotalPris(rs.getBigDecimal("total_pris"));
        obj.setTotalExpire(rs.getBigDecimal("total_expire"));
        obj.setDerniereAcquisition(rs.getDate("derniere_acquisition"));
        return obj;
    }

    // Getters / Setters
    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public BigDecimal getSoldeActuel() { return soldeActuel; }
    public void setSoldeActuel(BigDecimal soldeActuel) { this.soldeActuel = soldeActuel; }

    public BigDecimal getTotalAcquis() { return totalAcquis; }
    public void setTotalAcquis(BigDecimal totalAcquis) { this.totalAcquis = totalAcquis; }

    public BigDecimal getTotalBonification() { return totalBonification; }
    public void setTotalBonification(BigDecimal totalBonification) { this.totalBonification = totalBonification; }

    public BigDecimal getTotalPris() { return totalPris; }
    public void setTotalPris(BigDecimal totalPris) { this.totalPris = totalPris; }

    public BigDecimal getTotalExpire() { return totalExpire; }
    public void setTotalExpire(BigDecimal totalExpire) { this.totalExpire = totalExpire; }

    public Date getDerniereAcquisition() { return derniereAcquisition; }
    public void setDerniereAcquisition(Date derniereAcquisition) { this.derniereAcquisition = derniereAcquisition; }

    public BigDecimal getTauxJournalierConge() { return tauxJournalierConge; }
    public void setTauxJournalierConge(BigDecimal tauxJournalierConge) { this.tauxJournalierConge = tauxJournalierConge; }

    public String getSourceTauxConge() { return sourceTauxConge; }
    public void setSourceTauxConge(String sourceTauxConge) { this.sourceTauxConge = sourceTauxConge; }

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }
}
