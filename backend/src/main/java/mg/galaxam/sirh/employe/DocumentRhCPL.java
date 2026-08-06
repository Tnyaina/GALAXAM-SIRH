package mg.galaxam.sirh.employe;

import bean.ClassMere;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class DocumentRhCPL extends ClassMere {

    private Integer idDocument;
    private Integer idEmploye;
    private Integer idTypeDocument;

    private String typeDocumentLibelle;
    private String typeDocumentCategorie;
    private String typeDocumentDescription;
    private boolean typeDocumentObligatoire;

    private String nomOriginal;
    private String nomStockage;
    private String cheminFichier;
    private String extension;
    private Long tailleOctet;

    private Date dateDocument;
    private String commentaire;

    private Integer uploadedBy;
    private String uploadedByIdentifiant;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public DocumentRhCPL() {
        this.setNomTable("V_DOCUMENT_RH_CPL");
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
    protected ClassMere inserer(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_document_rh_cpl est en lecture seule.");
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_document_rh_cpl est en lecture seule.");
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_document_rh_cpl est en lecture seule.");
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public DocumentRhCPL rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM v_document_rh_cpl WHERE id_document = ?";
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

    public static List<DocumentRhCPL> listByEmployeId(Integer idEmploye, Connection c) throws Exception {
        List<DocumentRhCPL> list = new ArrayList<>();
        String sql = "SELECT * FROM v_document_rh_cpl WHERE id_employe = ? ORDER BY created_at DESC";
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

    public static DocumentRhCPL mapResultSet(ResultSet rs) throws SQLException {
        DocumentRhCPL d = new DocumentRhCPL();
        d.setIdDocument(rs.getInt("id_document"));
        d.setIdEmploye(rs.getInt("id_employe"));
        d.setIdTypeDocument(rs.getInt("id_type_document"));

        d.setTypeDocumentLibelle(rs.getString("type_document_libelle"));
        d.setTypeDocumentCategorie(rs.getString("type_document_categorie"));
        d.setTypeDocumentDescription(rs.getString("type_document_description"));
        d.setTypeDocumentObligatoire(rs.getBoolean("type_document_obligatoire"));

        d.setNomOriginal(rs.getString("nom_original"));
        d.setNomStockage(rs.getString("nom_stockage"));
        d.setCheminFichier(rs.getString("chemin_fichier"));
        d.setExtension(rs.getString("extension"));
        d.setTailleOctet((Long) rs.getObject("taille_octet"));

        d.setDateDocument(rs.getDate("date_document"));
        d.setCommentaire(rs.getString("commentaire"));

        d.setUploadedBy((Integer) rs.getObject("uploaded_by"));
        d.setUploadedByIdentifiant(rs.getString("uploaded_by_identifiant"));

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

    public String getTypeDocumentLibelle() { return typeDocumentLibelle; }
    public void setTypeDocumentLibelle(String typeDocumentLibelle) { this.typeDocumentLibelle = typeDocumentLibelle; }

    public String getTypeDocumentCategorie() { return typeDocumentCategorie; }
    public void setTypeDocumentCategorie(String typeDocumentCategorie) { this.typeDocumentCategorie = typeDocumentCategorie; }

    public String getTypeDocumentDescription() { return typeDocumentDescription; }
    public void setTypeDocumentDescription(String typeDocumentDescription) { this.typeDocumentDescription = typeDocumentDescription; }

    public boolean isTypeDocumentObligatoire() { return typeDocumentObligatoire; }
    public void setTypeDocumentObligatoire(boolean typeDocumentObligatoire) { this.typeDocumentObligatoire = typeDocumentObligatoire; }

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

    public String getUploadedByIdentifiant() { return uploadedByIdentifiant; }
    public void setUploadedByIdentifiant(String uploadedByIdentifiant) { this.uploadedByIdentifiant = uploadedByIdentifiant; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
