package mg.galaxam.sirh.referentiel.rh;

import bean.ClassMere;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RefTypeContrat extends ClassMere {

    private Integer idTypeContrat;
    private String code;
    private String libelle;
    private boolean exigeDateFin;
    private boolean estActif;

    public RefTypeContrat() {
        this.setNomTable("REF_TYPE_CONTRAT");
        this.estActif = true;
        this.exigeDateFin = false;
    }

    @Override
    public Integer getTuppleID() {
        return this.idTypeContrat;
    }

    @Override
    public String getAttributIDName() {
        return "id_type_contrat";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code du type de contrat est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du type de contrat est obligatoire.");
        }
        this.code = this.code.trim().toUpperCase();

        RefTypeContrat existant = rechercherParCode(this.code, c);
        if (existant != null && !existant.getIdTypeContrat().equals(this.idTypeContrat)) {
            throw new IllegalArgumentException("Ce code de type de contrat est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_type_contrat (code, libelle, exige_date_fin, est_actif) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBoolean(3, exigeDateFin);
            ps.setBoolean(4, estActif);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idTypeContrat = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_type_contrat SET code = ?, libelle = ?, exige_date_fin = ?, est_actif = ? WHERE id_type_contrat = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBoolean(3, exigeDateFin);
            ps.setBoolean(4, estActif);
            ps.setInt(5, idTypeContrat);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_type_contrat WHERE id_type_contrat = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTypeContrat);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_type_contrat, code, libelle, exige_date_fin, est_actif FROM ref_type_contrat WHERE id_type_contrat = ?";
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

    public static RefTypeContrat rechercherParCode(String code, Connection c) throws Exception {
        String sql = "SELECT id_type_contrat, code, libelle, exige_date_fin, est_actif FROM ref_type_contrat WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<RefTypeContrat> listAll(Connection c) throws Exception {
        List<RefTypeContrat> list = new ArrayList<>();
        String sql = "SELECT id_type_contrat, code, libelle, exige_date_fin, est_actif FROM ref_type_contrat ORDER BY id_type_contrat ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefTypeContrat mapResultSet(ResultSet rs) throws SQLException {
        RefTypeContrat tc = new RefTypeContrat();
        tc.setIdTypeContrat(rs.getInt("id_type_contrat"));
        tc.setCode(rs.getString("code"));
        tc.setLibelle(rs.getString("libelle"));
        tc.setExigeDateFin(rs.getBoolean("exige_date_fin"));
        tc.setEstActif(rs.getBoolean("est_actif"));
        return tc;
    }

    // Getters and Setters
    public Integer getIdTypeContrat() { return idTypeContrat; }
    public void setIdTypeContrat(Integer idTypeContrat) { this.idTypeContrat = idTypeContrat; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public boolean isExigeDateFin() { return exigeDateFin; }
    public void setExigeDateFin(boolean exigeDateFin) { this.exigeDateFin = exigeDateFin; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
}
