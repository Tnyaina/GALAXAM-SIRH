package mg.galaxam.sirh.model.employe;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class DocumentRh extends ClassMere {

    private Integer idDocument;
    private Integer idEmploye;
    private Integer idTypeDocument;

    private String nomOriginal;
    private String nomStockage;
    private String cheminFichier;
    private String extension;
    private Long tailleOctet;

    private Date dateDocument;
    private String commentaire;

    private Integer uploadedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public DocumentRh() {
        this.setNomTable("DOCUMENT_RH");
    }

    @Override
    public Integer getTuppleID() {
        return this.idDocument;
    }

    @Override
    public String getAttributIDName() {
        return "id_document";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'employé est obligatoire.");
        }
        if (idTypeDocument == null) {
            throw new IllegalArgumentException("Le type de document est obligatoire.");
        }
        if (nomOriginal == null || nomOriginal.isBlank()) {
            throw new IllegalArgumentException("Le nom original du fichier est obligatoire.");
        }
        if (cheminFichier == null || cheminFichier.isBlank()) {
            throw new IllegalArgumentException("Le chemin du fichier est obligatoire.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO document_rh (" +
                "id_employe, id_type_document, nom_original, nom_stockage, chemin_fichier, extension, taille_octet, date_document, commentaire, uploaded_by" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_document";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idTypeDocument);
            ps.setString(3, nomOriginal);
            ps.setString(4, nomStockage);
            ps.setString(5, cheminFichier);
            ps.setString(6, extension);
            ps.setObject(7, tailleOctet);
            ps.setDate(8, dateDocument);
            ps.setString(9, commentaire);
            ps.setObject(10, uploadedBy);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idDocument = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE document_rh SET " +
                "id_type_document = ?, date_document = ?, commentaire = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_document = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTypeDocument);
            ps.setDate(2, dateDocument);
            ps.setString(3, commentaire);
            ps.setInt(4, idDocument);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM document_rh WHERE id_document = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idDocument);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public DocumentRh rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM document_rh WHERE id_document = ?";
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

    public static List<DocumentRh> listByEmployeId(Integer idEmploye, Connection c) throws Exception {
        List<DocumentRh> list = new ArrayList<>();
        String sql = "SELECT * FROM document_rh WHERE id_employe = ? ORDER BY created_at DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    public static DocumentRh mapResultSet(ResultSet rs) throws SQLException {
        DocumentRh d = new DocumentRh();
        d.setIdDocument(rs.getInt("id_document"));
        d.setIdEmploye(rs.getInt("id_employe"));
        d.setIdTypeDocument(rs.getInt("id_type_document"));
        d.setNomOriginal(rs.getString("nom_original"));
        d.setNomStockage(rs.getString("nom_stockage"));
        d.setCheminFichier(rs.getString("chemin_fichier"));
        d.setExtension(rs.getString("extension"));
        d.setTailleOctet((Long) rs.getObject("taille_octet"));
        d.setDateDocument(rs.getDate("date_document"));
        d.setCommentaire(rs.getString("commentaire"));
        d.setUploadedBy((Integer) rs.getObject("uploaded_by"));
        d.setCreatedAt(rs.getTimestamp("created_at"));
        d.setUpdatedAt(rs.getTimestamp("updated_at"));
        return d;
    }

    // Getters and Setters
    public Integer getIdDocument() { return idDocument; }
    public void setIdDocument(Integer idDocument) { this.idDocument = idDocument; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdTypeDocument() { return idTypeDocument; }
    public void setIdTypeDocument(Integer idTypeDocument) { this.idTypeDocument = idTypeDocument; }

    public String getNomOriginal() { return nomOriginal; }
    public void setNomOriginal(String nomOriginal) { this.nomOriginal = nomOriginal; }

    public String getNomStockage() { return nomStockage; }
    public void setNomStockage(String nomStockage) { this.nomStockage = nomStockage; }

    public String getCheminFichier() { return cheminFichier; }
    public void setCheminFichier(String cheminFichier) { this.cheminFichier = cheminFichier; }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public Long getTailleOctet() { return tailleOctet; }
    public void setTailleOctet(Long tailleOctet) { this.tailleOctet = tailleOctet; }

    public Date getDateDocument() { return dateDocument; }
    public void setDateDocument(Date dateDocument) { this.dateDocument = dateDocument; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Integer getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Integer uploadedBy) { this.uploadedBy = uploadedBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
