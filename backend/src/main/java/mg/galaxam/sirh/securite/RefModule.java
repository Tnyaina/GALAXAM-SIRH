package mg.galaxam.sirh.securite;

import bean.ClassMere;
import java.sql.*;

public class RefModule extends ClassMere {

    private Integer idModule;
    private String codeModule;
    private String libelle;
    private boolean estActif;

    public RefModule() {
        this.setNomTable("REF_MODULE");
        this.estActif = true;
    }

    @Override
    public Integer getTuppleID() { return this.idModule; }

    @Override
    public String getAttributIDName() { return "id_module"; }

    @Override
    public void controler(Connection c) throws Exception {
        if (codeModule == null || codeModule.isBlank()) {
            throw new IllegalArgumentException("Le code du module est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du module est obligatoire.");
        }
        this.codeModule = this.codeModule.trim().toUpperCase();
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_module (code_module, libelle, est_actif) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codeModule);
            ps.setString(2, libelle);
            ps.setBoolean(3, estActif);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) this.idModule = rs.getInt(1);
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_module SET code_module=?, libelle=?, est_actif=? WHERE id_module=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeModule);
            ps.setString(2, libelle);
            ps.setBoolean(3, estActif);
            ps.setInt(4, idModule);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_module WHERE id_module=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idModule);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String idRecherche, Connection c) throws Exception {
        String sql = "SELECT * FROM ref_module WHERE id_module=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(idRecherche));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefModule m = new RefModule();
                    m.idModule = rs.getInt("id_module");
                    m.codeModule = rs.getString("code_module");
                    m.libelle = rs.getString("libelle");
                    m.estActif = rs.getBoolean("est_actif");
                    return m;
                }
            }
        }
        return null;
    }

    // Getters & Setters
    public Integer getIdModule() { return idModule; }
    public String getCodeModule() { return codeModule; }
    public void setCodeModule(String codeModule) { this.codeModule = codeModule; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
}