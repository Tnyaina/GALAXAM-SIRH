package mg.galaxam.sirh.referentiel;

import bean.ClassMere;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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

        RefModule existant = rechercherParCode(this.codeModule, c);
        if (existant != null && !existant.getIdModule().equals(this.idModule)) {
            throw new IllegalArgumentException("Ce code de module est déjà utilisé.");
        }
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

    // Méthodes de sélection
    public static List<RefModule> lister(Connection c) throws Exception {
        String sql = "SELECT * FROM ref_module ORDER BY code_module";
        List<RefModule> liste = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RefModule m = new RefModule();
                m.idModule = rs.getInt("id_module");
                m.codeModule = rs.getString("code_module");
                m.libelle = rs.getString("libelle");
                m.estActif = rs.getBoolean("est_actif");
                liste.add(m);
            }
        }
        return liste;
    }

    public static RefModule rechercherParCode(String code, Connection c) throws Exception {
        if (code == null) return null;
        String sql = "SELECT * FROM ref_module WHERE code_module = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
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
    public void setIdModule(Integer idModule) { this.idModule = idModule; }
    public String getCodeModule() { return codeModule; }
    public void setCodeModule(String codeModule) { this.codeModule = codeModule; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
}