package mg.galaxam.sirh.model.organisation;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class RefFiliale extends ClassMere {

    private Integer idFiliale;
    private String codeFiliale;
    private String raisonSociale;
    private String nif;
    private String stat;
    private Integer seuilEffectifAlerte;
    private boolean estActif;
    private Timestamp dateDesactivation;
    private Timestamp createdAt;

    public RefFiliale() {
        this.setNomTable("REF_FILIALE");
        this.estActif = true;
        this.seuilEffectifAlerte = 19;
    }

    @Override
    public Integer getTuppleID() {
        return this.idFiliale;
    }

    @Override
    public String getAttributIDName() {
        return "id_filiale";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (codeFiliale == null || codeFiliale.isBlank()) {
            throw new IllegalArgumentException("Le code de la filiale est obligatoire.");
        }
        if (raisonSociale == null || raisonSociale.isBlank()) {
            throw new IllegalArgumentException("La raison sociale est obligatoire.");
        }
        this.codeFiliale = this.codeFiliale.trim().toUpperCase();

        RefFiliale existant = rechercherParCode(this.codeFiliale, c);
        if (existant != null && !existant.getIdFiliale().equals(this.idFiliale)) {
            throw new IllegalArgumentException("Ce code de filiale est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_filiale (code_filiale, raison_sociale, nif, stat, seuil_effectif_alerte, est_actif) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codeFiliale);
            ps.setString(2, raisonSociale);
            ps.setString(3, nif);
            ps.setString(4, stat);
            ps.setInt(5, seuilEffectifAlerte != null ? seuilEffectifAlerte : 19);
            ps.setBoolean(6, estActif);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idFiliale = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_filiale SET code_filiale = ?, raison_sociale = ?, nif = ?, stat = ?, seuil_effectif_alerte = ?, est_actif = ?, date_desactivation = ? WHERE id_filiale = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeFiliale);
            ps.setString(2, raisonSociale);
            ps.setString(3, nif);
            ps.setString(4, stat);
            ps.setInt(5, seuilEffectifAlerte != null ? seuilEffectifAlerte : 19);
            ps.setBoolean(6, estActif);
            ps.setTimestamp(7, dateDesactivation);
            ps.setInt(8, idFiliale);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_filiale WHERE id_filiale = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idFiliale);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_filiale, code_filiale, raison_sociale, nif, stat, seuil_effectif_alerte, est_actif, date_desactivation, created_at FROM ref_filiale WHERE id_filiale = ?";
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

    public static RefFiliale rechercherParCode(String code, Connection c) throws Exception {
        String sql = "SELECT id_filiale, code_filiale, raison_sociale, nif, stat, seuil_effectif_alerte, est_actif, date_desactivation, created_at FROM ref_filiale WHERE code_filiale = ?";
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

    public static List<RefFiliale> listAll(Connection c) throws Exception {
        List<RefFiliale> list = new ArrayList<>();
        String sql = "SELECT id_filiale, code_filiale, raison_sociale, nif, stat, seuil_effectif_alerte, est_actif, date_desactivation, created_at FROM ref_filiale ORDER BY id_filiale ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefFiliale mapResultSet(ResultSet rs) throws SQLException {
        RefFiliale f = new RefFiliale();
        f.setIdFiliale(rs.getInt("id_filiale"));
        f.setCodeFiliale(rs.getString("code_filiale"));
        f.setRaisonSociale(rs.getString("raison_sociale"));
        f.setNif(rs.getString("nif"));
        f.setStat(rs.getString("stat"));
        f.setSeuilEffectifAlerte(rs.getInt("seuil_effectif_alerte"));
        f.setEstActif(rs.getBoolean("est_actif"));
        f.setDateDesactivation(rs.getTimestamp("date_desactivation"));
        f.setCreatedAt(rs.getTimestamp("created_at"));
        return f;
    }

    // Getters and Setters
    public Integer getIdFiliale() { return idFiliale; }
    public void setIdFiliale(Integer idFiliale) { this.idFiliale = idFiliale; }

    public String getCodeFiliale() { return codeFiliale; }
    public void setCodeFiliale(String codeFiliale) { this.codeFiliale = codeFiliale; }

    public String getRaisonSociale() { return raisonSociale; }
    public void setRaisonSociale(String raisonSociale) { this.raisonSociale = raisonSociale; }

    public String getNif() { return nif; }
    public void setNif(String nif) { this.nif = nif; }

    public String getStat() { return stat; }
    public void setStat(String stat) { this.stat = stat; }

    public Integer getSeuilEffectifAlerte() { return seuilEffectifAlerte; }
    public void setSeuilEffectifAlerte(Integer seuilEffectifAlerte) { this.seuilEffectifAlerte = seuilEffectifAlerte; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }

    public Timestamp getDateDesactivation() { return dateDesactivation; }
    public void setDateDesactivation(Timestamp dateDesactivation) { this.dateDesactivation = dateDesactivation; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
