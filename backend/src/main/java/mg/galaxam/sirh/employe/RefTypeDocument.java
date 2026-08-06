package mg.galaxam.sirh.employe;

import bean.ClassMere;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class RefTypeDocument extends ClassMere {

    private Integer idTypeDocument;
    private String libelle;
    private String categorie;
    private String description;
    private boolean obligatoire = false;
    private String extensionAutorisee = "pdf,jpg,jpeg,png";
    private Integer tailleMaxMo = 10;
    private boolean actif = true;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public RefTypeDocument() {
        this.setNomTable("REF_TYPE_DOCUMENT");
    }

    @Override
    public Integer getTuppleID() {
        return this.idTypeDocument;
    }

    @Override
    public String getAttributIDName() {
        return "id_type_document";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du type de document est obligatoire.");
        }
        if (categorie == null || categorie.isBlank()) {
            throw new IllegalArgumentException("La catégorie est obligatoire.");
        }
        this.libelle = this.libelle.trim();
        this.categorie = this.categorie.trim();
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_type_document (" +
                "libelle, categorie, description, obligatoire, extension_autorisee, taille_max_mo, actif" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_type_document";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, libelle);
            ps.setString(2, categorie);
            ps.setString(3, description);
            ps.setBoolean(4, obligatoire);
            ps.setString(5, extensionAutorisee);
            ps.setObject(6, tailleMaxMo);
            ps.setBoolean(7, actif);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idTypeDocument = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_type_document SET " +
                "libelle = ?, categorie = ?, description = ?, obligatoire = ?, extension_autorisee = ?, taille_max_mo = ?, actif = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_type_document = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, libelle);
            ps.setString(2, categorie);
            ps.setString(3, description);
            ps.setBoolean(4, obligatoire);
            ps.setString(5, extensionAutorisee);
            ps.setObject(6, tailleMaxMo);
            ps.setBoolean(7, actif);
            ps.setInt(8, idTypeDocument);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "UPDATE ref_type_document SET actif = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id_type_document = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTypeDocument);
            ps.executeUpdate();
        }
        this.actif = false;
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public RefTypeDocument rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM ref_type_document WHERE id_type_document = ?";
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

    public static List<RefTypeDocument> listAll(Connection c, Boolean actifUniquement) throws Exception {
        List<RefTypeDocument> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM ref_type_document WHERE 1=1 ");
        if (Boolean.TRUE.equals(actifUniquement)) {
            sql.append(" AND actif = TRUE ");
        }
        sql.append(" ORDER BY categorie ASC, libelle ASC");

        try (PreparedStatement ps = c.prepareStatement(sql.toString());
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public static RefTypeDocument mapResultSet(ResultSet rs) throws SQLException {
        RefTypeDocument t = new RefTypeDocument();
        t.setIdTypeDocument(rs.getInt("id_type_document"));
        t.setLibelle(rs.getString("libelle"));
        t.setCategorie(rs.getString("categorie"));
        t.setDescription(rs.getString("description"));
        t.setObligatoire(rs.getBoolean("obligatoire"));
        t.setExtensionAutorisee(rs.getString("extension_autorisee"));
        t.setTailleMaxMo((Integer) rs.getObject("taille_max_mo"));
        t.setActif(rs.getBoolean("actif"));
        t.setCreatedAt(rs.getTimestamp("created_at"));
        t.setUpdatedAt(rs.getTimestamp("updated_at"));
        return t;
    }

    // Getters and Setters
    public Integer getIdTypeDocument() { return idTypeDocument; }
    public void setIdTypeDocument(Integer idTypeDocument) { this.idTypeDocument = idTypeDocument; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isObligatoire() { return obligatoire; }
    public void setObligatoire(boolean obligatoire) { this.obligatoire = obligatoire; }

    public String getExtensionAutorisee() { return extensionAutorisee; }
    public void setExtensionAutorisee(String extensionAutorisee) { this.extensionAutorisee = extensionAutorisee; }

    public Integer getTailleMaxMo() { return tailleMaxMo; }
    public void setTailleMaxMo(Integer tailleMaxMo) { this.tailleMaxMo = tailleMaxMo; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
