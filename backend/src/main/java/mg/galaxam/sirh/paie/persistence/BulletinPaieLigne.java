package mg.galaxam.sirh.paie.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Ligne de détail d'un bulletin de paie.
 */
public class BulletinPaieLigne extends ClassMere {

    private Integer idLigne;
    private Integer idBulletin;
    private Integer idRubrique;
    private String codeRubrique;
    private String libelleRubrique;
    private String sens; // GAIN, RETENUE, PATRONAL, INFO
    private String categorie;
    private BigDecimal baseCalcul;
    private BigDecimal tauxSalarial;
    private BigDecimal tauxPatronal;
    private BigDecimal quantite;
    private BigDecimal montantSalarial = BigDecimal.ZERO;
    private BigDecimal montantPatronal = BigDecimal.ZERO;
    private Integer ordre = 10;
    private boolean imposable = true;
    private boolean soumisCotisation = true;
    private String commentaire;
    private Timestamp createdAt;

    public BulletinPaieLigne() {
        this.setNomTable("BULLETIN_PAIE_LIGNE");
    }

    @Override
    public Integer getTuppleID() {
        return idLigne;
    }

    @Override
    public String getAttributIDName() {
        return "id_ligne";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO bulletin_paie_ligne (id_bulletin, id_rubrique, code_rubrique, libelle_rubrique, " +
                     "sens, categorie, base_calcul, taux_salarial, taux_patronal, quantite, " +
                     "montant_salarial, montant_patronal, ordre, imposable, soumis_cotisation, commentaire) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_ligne";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idBulletin);
            if (idRubrique != null) {
                ps.setInt(2, idRubrique);
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setString(3, codeRubrique);
            ps.setString(4, libelleRubrique);
            ps.setString(5, sens);
            ps.setString(6, categorie);
            ps.setBigDecimal(7, baseCalcul);
            ps.setBigDecimal(8, tauxSalarial);
            ps.setBigDecimal(9, tauxPatronal);
            ps.setBigDecimal(10, quantite);
            ps.setBigDecimal(11, montantSalarial != null ? montantSalarial : BigDecimal.ZERO);
            ps.setBigDecimal(12, montantPatronal != null ? montantPatronal : BigDecimal.ZERO);
            ps.setInt(13, ordre != null ? ordre : 10);
            ps.setBoolean(14, imposable);
            ps.setBoolean(15, soumisCotisation);
            ps.setString(16, commentaire);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idLigne = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE bulletin_paie_ligne SET id_bulletin = ?, id_rubrique = ?, code_rubrique = ?, " +
                     "libelle_rubrique = ?, sens = ?, categorie = ?, base_calcul = ?, taux_salarial = ?, " +
                     "taux_patronal = ?, quantite = ?, montant_salarial = ?, montant_patronal = ?, " +
                     "ordre = ?, imposable = ?, soumis_cotisation = ?, commentaire = ? WHERE id_ligne = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idBulletin);
            if (idRubrique != null) {
                ps.setInt(2, idRubrique);
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setString(3, codeRubrique);
            ps.setString(4, libelleRubrique);
            ps.setString(5, sens);
            ps.setString(6, categorie);
            ps.setBigDecimal(7, baseCalcul);
            ps.setBigDecimal(8, tauxSalarial);
            ps.setBigDecimal(9, tauxPatronal);
            ps.setBigDecimal(10, quantite);
            ps.setBigDecimal(11, montantSalarial != null ? montantSalarial : BigDecimal.ZERO);
            ps.setBigDecimal(12, montantPatronal != null ? montantPatronal : BigDecimal.ZERO);
            ps.setInt(13, ordre != null ? ordre : 10);
            ps.setBoolean(14, imposable);
            ps.setBoolean(15, soumisCotisation);
            ps.setString(16, commentaire);
            ps.setInt(17, idLigne);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM bulletin_paie_ligne WHERE id_ligne = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idLigne);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        String sql = "SELECT * FROM bulletin_paie_ligne WHERE id_ligne = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(id));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<BulletinPaieLigne> listerParBulletin(Integer idBulletin, Connection c) throws Exception {
        List<BulletinPaieLigne> list = new ArrayList<>();
        String sql = "SELECT * FROM bulletin_paie_ligne WHERE id_bulletin = ? ORDER BY ordre ASC, id_ligne ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idBulletin);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    public static void supprimerParBulletin(Integer idBulletin, Connection c) throws Exception {
        if (idBulletin == null) return;
        String sql = "DELETE FROM bulletin_paie_ligne WHERE id_bulletin = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idBulletin);
            ps.executeUpdate();
        }
    }

    private static BulletinPaieLigne mapResultSet(ResultSet rs) throws Exception {
        BulletinPaieLigne l = new BulletinPaieLigne();
        l.setIdLigne(rs.getInt("id_ligne"));
        l.setIdBulletin(rs.getInt("id_bulletin"));
        int idRub = rs.getInt("id_rubrique");
        l.setIdRubrique(rs.wasNull() ? null : idRub);
        l.setCodeRubrique(rs.getString("code_rubrique"));
        l.setLibelleRubrique(rs.getString("libelle_rubrique"));
        l.setSens(rs.getString("sens"));
        l.setCategorie(rs.getString("categorie"));
        l.setBaseCalcul(rs.getBigDecimal("base_calcul"));
        l.setTauxSalarial(rs.getBigDecimal("taux_salarial"));
        l.setTauxPatronal(rs.getBigDecimal("taux_patronal"));
        l.setQuantite(rs.getBigDecimal("quantite"));
        l.setMontantSalarial(rs.getBigDecimal("montant_salarial"));
        l.setMontantPatronal(rs.getBigDecimal("montant_patronal"));
        l.setOrdre(rs.getInt("ordre"));
        l.setImposable(rs.getBoolean("imposable"));
        l.setSoumisCotisation(rs.getBoolean("soumis_cotisation"));
        l.setCommentaire(rs.getString("commentaire"));
        l.setCreatedAt(rs.getTimestamp("created_at"));
        return l;
    }

    // Getters & Setters
    public Integer getIdLigne() { return idLigne; }
    public void setIdLigne(Integer idLigne) { this.idLigne = idLigne; }

    public Integer getIdBulletin() { return idBulletin; }
    public void setIdBulletin(Integer idBulletin) { this.idBulletin = idBulletin; }

    public Integer getIdRubrique() { return idRubrique; }
    public void setIdRubrique(Integer idRubrique) { this.idRubrique = idRubrique; }

    public String getCodeRubrique() { return codeRubrique; }
    public void setCodeRubrique(String codeRubrique) { this.codeRubrique = codeRubrique; }

    public String getLibelleRubrique() { return libelleRubrique; }
    public void setLibelleRubrique(String libelleRubrique) { this.libelleRubrique = libelleRubrique; }

    public String getSens() { return sens; }
    public void setSens(String sens) { this.sens = sens; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public BigDecimal getBaseCalcul() { return baseCalcul; }
    public void setBaseCalcul(BigDecimal baseCalcul) { this.baseCalcul = baseCalcul; }

    public BigDecimal getTauxSalarial() { return tauxSalarial; }
    public void setTauxSalarial(BigDecimal tauxSalarial) { this.tauxSalarial = tauxSalarial; }

    public BigDecimal getTauxPatronal() { return tauxPatronal; }
    public void setTauxPatronal(BigDecimal tauxPatronal) { this.tauxPatronal = tauxPatronal; }

    public BigDecimal getQuantite() { return quantite; }
    public void setQuantite(BigDecimal quantite) { this.quantite = quantite; }

    public BigDecimal getMontantSalarial() { return montantSalarial; }
    public void setMontantSalarial(BigDecimal montantSalarial) { this.montantSalarial = montantSalarial; }

    public BigDecimal getMontantPatronal() { return montantPatronal; }
    public void setMontantPatronal(BigDecimal montantPatronal) { this.montantPatronal = montantPatronal; }

    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }

    public boolean isImposable() { return imposable; }
    public void setImposable(boolean imposable) { this.imposable = imposable; }

    public boolean isSoumisCotisation() { return soumisCotisation; }
    public void setSoumisCotisation(boolean soumisCotisation) { this.soumisCotisation = soumisCotisation; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
