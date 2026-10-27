package mg.galaxam.sirh.conge.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RefMotifEvenementFamilial extends ClassMere {

    private Integer idMotif;
    private String code;
    private String libelle;
    private BigDecimal nbJoursAutorises;
    private boolean justificatifRequis = true;
    private boolean estActif = true;

    public RefMotifEvenementFamilial() {
        this.setNomTable("REF_MOTIF_EVENEMENT_FAMILIAL");
    }

    @Override
    public Integer getTuppleID() {
        return this.idMotif;
    }

    @Override
    public String getAttributIDName() {
        return "id_motif";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code du motif est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du motif est obligatoire.");
        }
        this.code = this.code.trim().toUpperCase();

        RefMotifEvenementFamilial existant = rechercherParCode(this.code, c);
        if (existant != null && !existant.getIdMotif().equals(this.idMotif)) {
            throw new IllegalArgumentException("Ce code de motif est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_motif_evenement_familial (code, libelle, nb_jours_autorises, justificatif_requis, est_actif) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBigDecimal(3, nbJoursAutorises);
            ps.setBoolean(4, justificatifRequis);
            ps.setBoolean(5, estActif);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idMotif = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_motif_evenement_familial SET code = ?, libelle = ?, nb_jours_autorises = ?, justificatif_requis = ?, est_actif = ? WHERE id_motif = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBigDecimal(3, nbJoursAutorises);
            ps.setBoolean(4, justificatifRequis);
            ps.setBoolean(5, estActif);
            ps.setInt(6, idMotif);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_motif_evenement_familial WHERE id_motif = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idMotif);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public static RefMotifEvenementFamilial rechercherParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM ref_motif_evenement_familial WHERE id_motif = ?";
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

    public static RefMotifEvenementFamilial rechercherParCode(String code, Connection c) throws Exception {
        if (code == null || code.isBlank()) return null;
        String sql = "SELECT * FROM ref_motif_evenement_familial WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<RefMotifEvenementFamilial> listAll(Connection c, boolean actifUniquement) throws Exception {
        List<RefMotifEvenementFamilial> list = new ArrayList<>();
        String sql = "SELECT * FROM ref_motif_evenement_familial " +
                (actifUniquement ? "WHERE est_actif = TRUE " : "") +
                "ORDER BY id_motif";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefMotifEvenementFamilial mapResultSet(ResultSet rs) throws Exception {
        RefMotifEvenementFamilial obj = new RefMotifEvenementFamilial();
        obj.setIdMotif(rs.getInt("id_motif"));
        obj.setCode(rs.getString("code"));
        obj.setLibelle(rs.getString("libelle"));
        obj.setNbJoursAutorises(rs.getBigDecimal("nb_jours_autorises"));
        obj.setJustificatifRequis(rs.getBoolean("justificatif_requis"));
        obj.setEstActif(rs.getBoolean("est_actif"));
        return obj;
    }

    // Getters / Setters
    public Integer getIdMotif() { return idMotif; }
    public void setIdMotif(Integer idMotif) { this.idMotif = idMotif; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public BigDecimal getNbJoursAutorises() { return nbJoursAutorises; }
    public void setNbJoursAutorises(BigDecimal nbJoursAutorises) { this.nbJoursAutorises = nbJoursAutorises; }

    public boolean isJustificatifRequis() { return justificatifRequis; }
    public void setJustificatifRequis(boolean justificatifRequis) { this.justificatifRequis = justificatifRequis; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
}
