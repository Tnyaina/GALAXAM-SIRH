package mg.galaxam.sirh.referentiel.rh;

import bean.ClassMere;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RefTypeIntervenant extends ClassMere {

    private Integer idTypeIntervenant;
    private String code;
    private String libelle;
    private boolean soumisIrsa;
    private boolean soumisIsi5;
    private boolean soumisCnaps;
    private boolean soumisSante;
    private boolean estActif;

    public RefTypeIntervenant() {
        this.setNomTable("REF_TYPE_INTERVENANT");
        this.soumisIrsa = true;
        this.soumisIsi5 = false;
        this.soumisCnaps = true;
        this.soumisSante = true;
        this.estActif = true;
    }

    @Override
    public Integer getTuppleID() {
        return this.idTypeIntervenant;
    }

    @Override
    public String getAttributIDName() {
        return "id_type_intervenant";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code du type d'intervenant est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du type d'intervenant est obligatoire.");
        }
        this.code = this.code.trim().toUpperCase();

        RefTypeIntervenant existant = rechercherParCode(this.code, c);
        if (existant != null && !existant.getIdTypeIntervenant().equals(this.idTypeIntervenant)) {
            throw new IllegalArgumentException("Ce code de type d'intervenant est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_type_intervenant (code, libelle, soumis_irsa, soumis_isi_5, soumis_cnaps, soumis_sante, est_actif) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBoolean(3, soumisIrsa);
            ps.setBoolean(4, soumisIsi5);
            ps.setBoolean(5, soumisCnaps);
            ps.setBoolean(6, soumisSante);
            ps.setBoolean(7, estActif);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idTypeIntervenant = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_type_intervenant SET code = ?, libelle = ?, soumis_irsa = ?, soumis_isi_5 = ?, soumis_cnaps = ?, soumis_sante = ?, est_actif = ? WHERE id_type_intervenant = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBoolean(3, soumisIrsa);
            ps.setBoolean(4, soumisIsi5);
            ps.setBoolean(5, soumisCnaps);
            ps.setBoolean(6, soumisSante);
            ps.setBoolean(7, estActif);
            ps.setInt(8, idTypeIntervenant);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_type_intervenant WHERE id_type_intervenant = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTypeIntervenant);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_type_intervenant, code, libelle, soumis_irsa, soumis_isi_5, soumis_cnaps, soumis_sante, est_actif FROM ref_type_intervenant WHERE id_type_intervenant = ?";
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

    public static RefTypeIntervenant rechercherParCode(String code, Connection c) throws Exception {
        String sql = "SELECT id_type_intervenant, code, libelle, soumis_irsa, soumis_isi_5, soumis_cnaps, soumis_sante, est_actif FROM ref_type_intervenant WHERE code = ?";
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

    public static List<RefTypeIntervenant> listAll(Connection c) throws Exception {
        List<RefTypeIntervenant> list = new ArrayList<>();
        String sql = "SELECT id_type_intervenant, code, libelle, soumis_irsa, soumis_isi_5, soumis_cnaps, soumis_sante, est_actif FROM ref_type_intervenant ORDER BY id_type_intervenant ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefTypeIntervenant mapResultSet(ResultSet rs) throws SQLException {
        RefTypeIntervenant ti = new RefTypeIntervenant();
        ti.setIdTypeIntervenant(rs.getInt("id_type_intervenant"));
        ti.setCode(rs.getString("code"));
        ti.setLibelle(rs.getString("libelle"));
        ti.setSoumisIrsa(rs.getBoolean("soumis_irsa"));
        ti.setSoumisIsi5(rs.getBoolean("soumis_isi_5"));
        ti.setSoumisCnaps(rs.getBoolean("soumis_cnaps"));
        ti.setSoumisSante(rs.getBoolean("soumis_sante"));
        ti.setEstActif(rs.getBoolean("est_actif"));
        return ti;
    }

    // Getters and Setters
    public Integer getIdTypeIntervenant() { return idTypeIntervenant; }
    public void setIdTypeIntervenant(Integer idTypeIntervenant) { this.idTypeIntervenant = idTypeIntervenant; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public boolean isSoumisIrsa() { return soumisIrsa; }
    public void setSoumisIrsa(boolean soumisIrsa) { this.soumisIrsa = soumisIrsa; }

    public boolean isSoumisIsi5() { return soumisIsi5; }
    public void setSoumisIsi5(boolean soumisIsi5) { this.soumisIsi5 = soumisIsi5; }

    public boolean isSoumisCnaps() { return soumisCnaps; }
    public void setSoumisCnaps(boolean soumisCnaps) { this.soumisCnaps = soumisCnaps; }

    public boolean isSoumisSante() { return soumisSante; }
    public void setSoumisSante(boolean soumisSante) { this.soumisSante = soumisSante; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
}
