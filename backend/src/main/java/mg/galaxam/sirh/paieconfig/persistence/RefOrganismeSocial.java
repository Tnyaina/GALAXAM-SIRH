package mg.galaxam.sirh.paieconfig.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Référentiel des organismes déclaratifs sociaux (CNaPS, FMFP, OSTIE, OMSI, AMIT, etc.).
 */
public class RefOrganismeSocial extends ClassMere {

    private Integer idOrganisme;
    private String code;
    private String libelle;
    private String categorie = "SANTE";
    private boolean estActif = true;
    private Timestamp createdAt;

    public RefOrganismeSocial() {
        this.setNomTable("REF_ORGANISME_SOCIAL");
        this.estActif = true;
    }

    @Override
    public Integer getTuppleID() {
        return idOrganisme;
    }

    @Override
    public String getAttributIDName() {
        return "id_organisme";
    }

    public void valider() {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code de l'organisme social est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé de l'organisme social est obligatoire.");
        }
        if (categorie == null || categorie.isBlank()) {
            categorie = "SANTE";
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        valider();
        String sql = "INSERT INTO ref_organisme_social (code, libelle, categorie, est_actif) " +
                     "VALUES (?, ?, ?, ?) RETURNING id_organisme, created_at";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
            ps.setString(2, libelle.trim());
            ps.setString(3, categorie.trim().toUpperCase());
            ps.setBoolean(4, estActif);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idOrganisme = rs.getInt(1);
                    this.createdAt = rs.getTimestamp(2);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        valider();
        String sql = "UPDATE ref_organisme_social SET code = ?, libelle = ?, categorie = ?, est_actif = ? " +
                     "WHERE id_organisme = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
            ps.setString(2, libelle.trim());
            ps.setString(3, categorie.trim().toUpperCase());
            ps.setBoolean(4, estActif);
            ps.setInt(5, idOrganisme);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_organisme_social WHERE id_organisme = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idOrganisme);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM ref_organisme_social WHERE id_organisme = ?";
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

    public static RefOrganismeSocial findByCode(String code, Connection c) throws Exception {
        if (code == null || code.isBlank()) return null;
        String sql = "SELECT * FROM ref_organisme_social WHERE UPPER(code) = UPPER(?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<RefOrganismeSocial> listAll(Connection c) throws Exception {
        List<RefOrganismeSocial> list = new ArrayList<>();
        String sql = "SELECT * FROM ref_organisme_social ORDER BY code ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public static List<RefOrganismeSocial> listActive(Connection c) throws Exception {
        List<RefOrganismeSocial> list = new ArrayList<>();
        String sql = "SELECT * FROM ref_organisme_social WHERE est_actif = TRUE ORDER BY code ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefOrganismeSocial mapResultSet(ResultSet rs) throws Exception {
        RefOrganismeSocial org = new RefOrganismeSocial();
        org.setIdOrganisme(rs.getInt("id_organisme"));
        org.setCode(rs.getString("code"));
        org.setLibelle(rs.getString("libelle"));
        org.setCategorie(rs.getString("categorie"));
        org.setEstActif(rs.getBoolean("est_actif"));
        org.setCreatedAt(rs.getTimestamp("created_at"));
        return org;
    }

    public Integer getIdOrganisme() { return idOrganisme; }
    public void setIdOrganisme(Integer idOrganisme) { this.idOrganisme = idOrganisme; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
