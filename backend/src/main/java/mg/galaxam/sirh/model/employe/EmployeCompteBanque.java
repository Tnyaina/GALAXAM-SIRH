package mg.galaxam.sirh.model.employe;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class EmployeCompteBanque extends ClassMere {

    private Integer idCompteBanque;
    private Integer idEmploye;
    private Integer idBanque;
    private String nomBanque;
    private String codeBanque;
    private String agence;
    private String numCompteRib;
    private String titulaireCompte;
    private boolean estPrincipal = true;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public EmployeCompteBanque() {
        this.setNomTable("EMPLOYE_COMPTE_BANQUE");
    }

    @Override
    public Integer getTuppleID() {
        return this.idCompteBanque;
    }

    @Override
    public String getAttributIDName() {
        return "id_compte_banque";
    }

    @Override
    public String getLiaisonFille() {
        return "id_employe";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'id employé est obligatoire pour le compte bancaire.");
        }
        if (idBanque == null) {
            throw new IllegalArgumentException("La banque est obligatoire.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        controler(c);
        if (this.estPrincipal) {
            resetPrincipal(this.idEmploye, c);
        }
        String sql = "INSERT INTO employe_compte_banque (id_employe, id_banque, agence, num_compte_rib, titulaire_compte, est_principal) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idBanque);
            ps.setString(3, agence);
            ps.setString(4, numCompteRib);
            ps.setString(5, titulaireCompte);
            ps.setBoolean(6, estPrincipal);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idCompteBanque = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        controler(c);
        if (this.estPrincipal) {
            resetPrincipalExcept(this.idEmploye, this.idCompteBanque, c);
        }
        String sql = "UPDATE employe_compte_banque SET id_employe = ?, id_banque = ?, agence = ?, num_compte_rib = ?, titulaire_compte = ?, est_principal = ?, updated_at = CURRENT_TIMESTAMP WHERE id_compte_banque = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idBanque);
            ps.setString(3, agence);
            ps.setString(4, numCompteRib);
            ps.setString(5, titulaireCompte);
            ps.setBoolean(6, estPrincipal);
            ps.setInt(7, idCompteBanque);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM employe_compte_banque WHERE id_compte_banque = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idCompteBanque);
            ps.executeUpdate();
        }
    }

    public static void deleteByEmployeId(Integer idEmploye, Connection c) throws Exception {
        String sql = "DELETE FROM employe_compte_banque WHERE id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.executeUpdate();
        }
    }

    public static void deletePrincipalByEmployeId(Integer idEmploye, Connection c) throws Exception {
        String sql = "DELETE FROM employe_compte_banque WHERE id_employe = ? AND est_principal = TRUE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.executeUpdate();
        }
    }

    private static void resetPrincipal(Integer idEmploye, Connection c) throws Exception {
        String sql = "UPDATE employe_compte_banque SET est_principal = FALSE WHERE id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.executeUpdate();
        }
    }

    private static void resetPrincipalExcept(Integer idEmploye, Integer idCompteBanque, Connection c) throws Exception {
        String sql = "UPDATE employe_compte_banque SET est_principal = FALSE WHERE id_employe = ? AND id_compte_banque <> ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idCompteBanque);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public EmployeCompteBanque rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT ecb.*, rb.nom_banque, rb.code_banque " +
                     "FROM employe_compte_banque ecb " +
                     "LEFT JOIN ref_banque rb ON ecb.id_banque = rb.id_banque " +
                     "WHERE ecb.id_compte_banque = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<EmployeCompteBanque> listByEmployeId(Integer idEmploye, Connection c) throws Exception {
        List<EmployeCompteBanque> list = new ArrayList<>();
        String sql = "SELECT ecb.*, rb.nom_banque, rb.code_banque " +
                     "FROM employe_compte_banque ecb " +
                     "LEFT JOIN ref_banque rb ON ecb.id_banque = rb.id_banque " +
                     "WHERE ecb.id_employe = ? ORDER BY ecb.est_principal DESC, ecb.id_compte_banque ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    private static EmployeCompteBanque mapResultSet(ResultSet rs) throws SQLException {
        EmployeCompteBanque cb = new EmployeCompteBanque();
        cb.setIdCompteBanque(rs.getInt("id_compte_banque"));
        cb.setIdEmploye(rs.getInt("id_employe"));
        cb.setIdBanque(rs.getInt("id_banque"));
        cb.setAgence(rs.getString("agence"));
        cb.setNumCompteRib(rs.getString("num_compte_rib"));
        cb.setTitulaireCompte(rs.getString("titulaire_compte"));
        cb.setEstPrincipal(rs.getBoolean("est_principal"));
        cb.setCreatedAt(rs.getTimestamp("created_at"));
        cb.setUpdatedAt(rs.getTimestamp("updated_at"));

        try {
            cb.setNomBanque(rs.getString("nom_banque"));
        } catch (SQLException ignored) {}
        try {
            cb.setCodeBanque(rs.getString("code_banque"));
        } catch (SQLException ignored) {}

        return cb;
    }

    // Getters and Setters
    public Integer getIdCompteBanque() { return idCompteBanque; }
    public void setIdCompteBanque(Integer idCompteBanque) { this.idCompteBanque = idCompteBanque; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdBanque() { return idBanque; }
    public void setIdBanque(Integer idBanque) { this.idBanque = idBanque; }

    public String getNomBanque() { return nomBanque; }
    public void setNomBanque(String nomBanque) { this.nomBanque = nomBanque; }

    public String getCodeBanque() { return codeBanque; }
    public void setCodeBanque(String codeBanque) { this.codeBanque = codeBanque; }

    public String getAgence() { return agence; }
    public void setAgence(String agence) { this.agence = agence; }

    public String getNumCompteRib() { return numCompteRib; }
    public void setNumCompteRib(String numCompteRib) { this.numCompteRib = numCompteRib; }

    public String getTitulaireCompte() { return titulaireCompte; }
    public void setTitulaireCompte(String titulaireCompte) { this.titulaireCompte = titulaireCompte; }

    public boolean isEstPrincipal() { return estPrincipal; }
    public void setEstPrincipal(boolean estPrincipal) { this.estPrincipal = estPrincipal; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
