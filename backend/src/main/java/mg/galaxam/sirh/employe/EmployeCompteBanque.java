package mg.galaxam.sirh.employe;

import bean.ClassMere;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EmployeCompteBanque extends ClassMere {

    private Integer idCompteBanque;
    private Integer idEmploye;
    private Integer idBanque;
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
        if (numCompteRib == null || numCompteRib.isBlank()) {
            throw new IllegalArgumentException("Le numéro de compte/RIB est obligatoire.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        if (this.estPrincipal) {
            resetPrincipal(this.idEmploye, c);
        }
        String sql = "INSERT INTO employe_compte_banque (id_employe, id_banque, agence, num_compte_rib, titulaire_compte, est_principal) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, idEmploye);
            ps.setInt(i++, idBanque);
            ps.setString(i++, agence);
            ps.setString(i++, numCompteRib);
            ps.setString(i++, titulaireCompte);
            ps.setBoolean(i++, estPrincipal);

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
        if (this.estPrincipal) {
            resetPrincipalExcept(this.idEmploye, this.idCompteBanque, c);
        }
        String sql = "UPDATE employe_compte_banque SET id_banque = ?, agence = ?, num_compte_rib = ?, titulaire_compte = ?, est_principal = ?, updated_at = CURRENT_TIMESTAMP WHERE id_compte_banque = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, idBanque);
            ps.setString(i++, agence);
            ps.setString(i++, numCompteRib);
            ps.setString(i++, titulaireCompte);
            ps.setBoolean(i++, estPrincipal);
            ps.setInt(i++, idCompteBanque);

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
        String sql = "SELECT * FROM employe_compte_banque WHERE id_compte_banque = ?";
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
        String sql = "SELECT * FROM employe_compte_banque WHERE id_employe = ? ORDER BY est_principal DESC, id_compte_banque ASC";
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
        return cb;
    }

    // Getters and Setters
    public Integer getIdCompteBanque() { return idCompteBanque; }
    public void setIdCompteBanque(Integer idCompteBanque) { this.idCompteBanque = idCompteBanque; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdBanque() { return idBanque; }
    public void setIdBanque(Integer idBanque) { this.idBanque = idBanque; }

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
