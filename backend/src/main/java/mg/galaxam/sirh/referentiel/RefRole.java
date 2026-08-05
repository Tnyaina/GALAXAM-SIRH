package mg.galaxam.sirh.referentiel;

import bean.ClassMere;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RefRole extends ClassMere {

    private Integer idRole;
    private String codeRole;
    private String libelle;
    private boolean estActif;
    private Timestamp dateDesactivation;

    public RefRole() {
        this.setNomTable("REF_ROLE");
        this.estActif = true;
    }

    @Override
    public Integer getTuppleID() {
        return this.idRole;
    }

    @Override
    public String getAttributIDName() {
        return "id_role";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (codeRole == null || codeRole.isBlank()) {
            throw new IllegalArgumentException("Le code du rôle est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du rôle est obligatoire.");
        }
        this.codeRole = this.codeRole.trim().toUpperCase();

        RefRole existant = rechercherParCode(this.codeRole, c);
        if (existant != null && !existant.getIdRole().equals(this.idRole)) {
            throw new IllegalArgumentException("Ce code de rôle est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_role (code_role, libelle, est_actif) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codeRole);
            ps.setString(2, libelle);
            ps.setBoolean(3, estActif);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idRole = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_role SET code_role=?, libelle=?, est_actif=?, date_desactivation=? WHERE id_role=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeRole);
            ps.setString(2, libelle);
            ps.setBoolean(3, estActif);
            ps.setTimestamp(4, dateDesactivation);
            ps.setInt(5, idRole);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_role WHERE id_role=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRole);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String idRecherche, Connection c) throws Exception {
        String sql = "SELECT * FROM ref_role WHERE id_role=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(idRecherche));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefRole r = new RefRole();
                    r.idRole = rs.getInt("id_role");
                    r.codeRole = rs.getString("code_role");
                    r.libelle = rs.getString("libelle");
                    r.estActif = rs.getBoolean("est_actif");
                    r.dateDesactivation = rs.getTimestamp("date_desactivation");
                    return r;
                }
            }
        }
        return null;
    }

    // Méthodes de sélection
    public static List<RefRole> lister(Connection c) throws Exception {
        String sql = "SELECT * FROM ref_role ORDER BY code_role";
        List<RefRole> liste = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RefRole r = new RefRole();
                r.idRole = rs.getInt("id_role");
                r.codeRole = rs.getString("code_role");
                r.libelle = rs.getString("libelle");
                r.estActif = rs.getBoolean("est_actif");
                r.dateDesactivation = rs.getTimestamp("date_desactivation");
                liste.add(r);
            }
        }
        return liste;
    }

    public static RefRole rechercherParCode(String code, Connection c) throws Exception {
        if (code == null) return null;
        String sql = "SELECT * FROM ref_role WHERE code_role = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefRole r = new RefRole();
                    r.idRole = rs.getInt("id_role");
                    r.codeRole = rs.getString("code_role");
                    r.libelle = rs.getString("libelle");
                    r.estActif = rs.getBoolean("est_actif");
                    r.dateDesactivation = rs.getTimestamp("date_desactivation");
                    return r;
                }
            }
        }
        return null;
    }

    // Getters / Setters
    public Integer getIdRole() { return idRole; }
    public void setIdRole(Integer idRole) { this.idRole = idRole; }
    public String getCodeRole() { return codeRole; }
    public void setCodeRole(String codeRole) { this.codeRole = codeRole; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
    public Timestamp getDateDesactivation() { return dateDesactivation; }
    public void setDateDesactivation(Timestamp dateDesactivation) { this.dateDesactivation = dateDesactivation; }
}