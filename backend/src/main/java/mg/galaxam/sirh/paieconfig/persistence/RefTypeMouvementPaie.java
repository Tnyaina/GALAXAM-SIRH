package mg.galaxam.sirh.paieconfig.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité de configuration référentielle pour les variables dynamiques de paie
 * (Acomptes, Avances, Primes, Extras, Retenues, Notes de frais).
 */
public class RefTypeMouvementPaie extends ClassMere {

    private Integer idTypeMouvement;
    private String code;
    private String libelle;
    private String sens; // 'GAIN' ou 'RETENUE'
    private String categorie; // 'ACOMPTE', 'AVANCE', 'PRIME', 'RETENUE_DIVERSE', 'NOTE_DE_FRAIS', 'EXTRA'
    private Integer idRubrique;
    private boolean estProratise = false;
    private boolean estPlafonneDixieme = false;
    private String nature;
    private String ownerCode;
    private boolean saisieManuelleAutorisee = true;
    private boolean periodeObligatoire = true;
    private boolean motifObligatoire = true;
    private String provenanceDefaut;
    private boolean estActif = true;
    private Integer ordreAffichage = 10;
    private String description;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public RefTypeMouvementPaie() {
        this.setNomTable("REF_TYPE_MOUVEMENT_PAIE");
    }

    @Override
    public Integer getTuppleID() {
        return idTypeMouvement;
    }

    @Override
    public String getAttributIDName() {
        return "id_type_mouvement";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_type_mouvement_paie (" +
                     "code, libelle, sens, categorie, id_rubrique, est_proratise, est_plafonne_dixieme, " +
                     "nature, owner_code, saisie_manuelle_autorisee, periode_obligatoire, motif_obligatoire, provenance_defaut, " +
                     "est_actif, ordre_affichage, description" +
                     ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_type_mouvement";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, sens);
            ps.setString(4, categorie);
            if (idRubrique != null) ps.setInt(5, idRubrique); else ps.setNull(5, java.sql.Types.INTEGER);
            ps.setBoolean(6, estProratise);
            ps.setBoolean(7, estPlafonneDixieme);
            ps.setString(8, nature);
            ps.setString(9, ownerCode);
            ps.setBoolean(10, saisieManuelleAutorisee);
            ps.setBoolean(11, periodeObligatoire);
            ps.setBoolean(12, motifObligatoire);
            ps.setString(13, provenanceDefaut);
            ps.setBoolean(14, estActif);
            ps.setInt(15, ordreAffichage != null ? ordreAffichage : 10);
            ps.setString(16, description);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idTypeMouvement = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_type_mouvement_paie SET " +
                     "code = ?, libelle = ?, sens = ?, categorie = ?, id_rubrique = ?, " +
                     "est_proratise = ?, est_plafonne_dixieme = ?, nature = ?, owner_code = ?, " +
                     "saisie_manuelle_autorisee = ?, periode_obligatoire = ?, motif_obligatoire = ?, provenance_defaut = ?, " +
                     "est_actif = ?, ordre_affichage = ?, description = ?, " +
                     "updated_at = CURRENT_TIMESTAMP WHERE id_type_mouvement = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, sens);
            ps.setString(4, categorie);
            if (idRubrique != null) ps.setInt(5, idRubrique); else ps.setNull(5, java.sql.Types.INTEGER);
            ps.setBoolean(6, estProratise);
            ps.setBoolean(7, estPlafonneDixieme);
            ps.setString(8, nature);
            ps.setString(9, ownerCode);
            ps.setBoolean(10, saisieManuelleAutorisee);
            ps.setBoolean(11, periodeObligatoire);
            ps.setBoolean(12, motifObligatoire);
            ps.setString(13, provenanceDefaut);
            ps.setBoolean(14, estActif);
            ps.setInt(15, ordreAffichage != null ? ordreAffichage : 10);
            ps.setString(16, description);
            ps.setInt(17, idTypeMouvement);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_type_mouvement_paie WHERE id_type_mouvement = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTypeMouvement);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return trouverParId(Integer.parseInt(id), c);
    }

    public static RefTypeMouvementPaie trouverParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM ref_type_mouvement_paie WHERE id_type_mouvement = ?";
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

    public static RefTypeMouvementPaie trouverParCode(String code, Connection c) throws Exception {
        if (code == null || code.isBlank()) return null;
        String sql = "SELECT * FROM ref_type_mouvement_paie WHERE code = ?";
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

    public static List<RefTypeMouvementPaie> listerTous(Connection c) throws Exception {
        List<RefTypeMouvementPaie> list = new ArrayList<>();
        String sql = "SELECT * FROM ref_type_mouvement_paie ORDER BY ordre_affichage ASC, code ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public static List<RefTypeMouvementPaie> listerActifs(Connection c) throws Exception {
        List<RefTypeMouvementPaie> list = new ArrayList<>();
        String sql = "SELECT * FROM ref_type_mouvement_paie WHERE est_actif = true ORDER BY ordre_affichage ASC, code ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefTypeMouvementPaie mapResultSet(ResultSet rs) throws Exception {
        RefTypeMouvementPaie item = new RefTypeMouvementPaie();
        item.setIdTypeMouvement(rs.getInt("id_type_mouvement"));
        item.setCode(rs.getString("code"));
        item.setLibelle(rs.getString("libelle"));
        item.setSens(rs.getString("sens"));
        item.setCategorie(rs.getString("categorie"));
        int rubId = rs.getInt("id_rubrique");
        item.setIdRubrique(rs.wasNull() ? null : rubId);
        item.setEstProratise(rs.getBoolean("est_proratise"));
        item.setEstPlafonneDixieme(rs.getBoolean("est_plafonne_dixieme"));
        item.setNature(rs.getString("nature"));
        item.setOwnerCode(rs.getString("owner_code"));
        item.setSaisieManuelleAutorisee(rs.getBoolean("saisie_manuelle_autorisee"));
        item.setPeriodeObligatoire(rs.getBoolean("periode_obligatoire"));
        item.setMotifObligatoire(rs.getBoolean("motif_obligatoire"));
        item.setProvenanceDefaut(rs.getString("provenance_defaut"));
        item.setEstActif(rs.getBoolean("est_actif"));
        item.setOrdreAffichage(rs.getInt("ordre_affichage"));
        item.setDescription(rs.getString("description"));
        item.setCreatedAt(rs.getTimestamp("created_at"));
        item.setUpdatedAt(rs.getTimestamp("updated_at"));
        return item;
    }

    // Getters & Setters
    public Integer getIdTypeMouvement() { return idTypeMouvement; }
    public void setIdTypeMouvement(Integer idTypeMouvement) { this.idTypeMouvement = idTypeMouvement; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getSens() { return sens; }
    public void setSens(String sens) { this.sens = sens; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public Integer getIdRubrique() { return idRubrique; }
    public void setIdRubrique(Integer idRubrique) { this.idRubrique = idRubrique; }

    public boolean isEstProratise() { return estProratise; }
    public void setEstProratise(boolean estProratise) { this.estProratise = estProratise; }

    public boolean isEstPlafonneDixieme() { return estPlafonneDixieme; }
    public void setEstPlafonneDixieme(boolean estPlafonneDixieme) { this.estPlafonneDixieme = estPlafonneDixieme; }

    public String getNature() { return nature; }
    public void setNature(String nature) { this.nature = nature; }
    public String getOwnerCode() { return ownerCode; }
    public void setOwnerCode(String ownerCode) { this.ownerCode = ownerCode; }
    public boolean isSaisieManuelleAutorisee() { return saisieManuelleAutorisee; }
    public void setSaisieManuelleAutorisee(boolean value) { this.saisieManuelleAutorisee = value; }
    public boolean isPeriodeObligatoire() { return periodeObligatoire; }
    public void setPeriodeObligatoire(boolean value) { this.periodeObligatoire = value; }
    public boolean isMotifObligatoire() { return motifObligatoire; }
    public void setMotifObligatoire(boolean value) { this.motifObligatoire = value; }
    public String getProvenanceDefaut() { return provenanceDefaut; }
    public void setProvenanceDefaut(String provenanceDefaut) { this.provenanceDefaut = provenanceDefaut; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }

    public Integer getOrdreAffichage() { return ordreAffichage; }
    public void setOrdreAffichage(Integer ordreAffichage) { this.ordreAffichage = ordreAffichage; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
