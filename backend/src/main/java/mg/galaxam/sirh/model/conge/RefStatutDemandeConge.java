package mg.galaxam.sirh.model.conge;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RefStatutDemandeConge extends ClassMere {

    private Integer idStatutDemandeConge;
    private String code;
    private String libelle;

    public RefStatutDemandeConge() {
        this.setNomTable("REF_STATUT_DEMANDE_CONGE");
    }

    @Override
    public Integer getTuppleID() {
        return this.idStatutDemandeConge;
    }

    @Override
    public String getAttributIDName() {
        return "id_statut_demande_conge";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code du statut est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du statut est obligatoire.");
        }
        this.code = this.code.trim().toUpperCase();

        RefStatutDemandeConge existant = rechercherParCode(this.code, c);
        if (existant != null && !existant.getIdStatutDemandeConge().equals(this.idStatutDemandeConge)) {
            throw new IllegalArgumentException("Ce code de statut est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_statut_demande_conge (code, libelle) VALUES (?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idStatutDemandeConge = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_statut_demande_conge SET code = ?, libelle = ? WHERE id_statut_demande_conge = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setInt(3, idStatutDemandeConge);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_statut_demande_conge WHERE id_statut_demande_conge = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idStatutDemandeConge);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public static RefStatutDemandeConge rechercherParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM ref_statut_demande_conge WHERE id_statut_demande_conge = ?";
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

    public static RefStatutDemandeConge rechercherParCode(String code, Connection c) throws Exception {
        if (code == null || code.isBlank()) return null;
        String sql = "SELECT * FROM ref_statut_demande_conge WHERE code = ?";
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

    public static List<RefStatutDemandeConge> listAll(Connection c) throws Exception {
        List<RefStatutDemandeConge> list = new ArrayList<>();
        String sql = "SELECT * FROM ref_statut_demande_conge ORDER BY id_statut_demande_conge";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefStatutDemandeConge mapResultSet(ResultSet rs) throws Exception {
        RefStatutDemandeConge obj = new RefStatutDemandeConge();
        obj.setIdStatutDemandeConge(rs.getInt("id_statut_demande_conge"));
        obj.setCode(rs.getString("code"));
        obj.setLibelle(rs.getString("libelle"));
        return obj;
    }

    // Getters / Setters
    public Integer getIdStatutDemandeConge() { return idStatutDemandeConge; }
    public void setIdStatutDemandeConge(Integer idStatutDemandeConge) { this.idStatutDemandeConge = idStatutDemandeConge; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
}
