package mg.galaxam.sirh.conge.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class MouvementSoldeConge extends ClassMere {

    private Integer idMouvement;
    private BigDecimal soldeApres;
    private Integer idEmploye;
    private String typeMouvement; // ACQUISITION, BONIFICATION, PRISE, AJUSTEMENT, ANNULATION, EXPIRATION
    private BigDecimal nbJours;
    private Integer idDemandeConge;
    private Date dateMouvement;
    private String commentaire;
    private Integer createdBy;
    private Timestamp createdAt;

    public MouvementSoldeConge() {
        this.setNomTable("MOUVEMENT_SOLDE_CONGE");
    }

    @Override
    public Integer getTuppleID() {
        return this.idMouvement;
    }

    @Override
    public String getAttributIDName() {
        return "id_mouvement";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'employé est obligatoire pour enregistrer un mouvement de solde.");
        }
        if (typeMouvement == null || typeMouvement.isBlank()) {
            throw new IllegalArgumentException("Le type de mouvement de solde est obligatoire.");
        }
        this.typeMouvement = this.typeMouvement.trim().toUpperCase();

        List<String> typesValides = List.of("ACQUISITION", "BONIFICATION", "PRISE", "AJUSTEMENT", "ANNULATION", "EXPIRATION");
        if (!typesValides.contains(this.typeMouvement)) {
            throw new IllegalArgumentException("Type de mouvement de solde invalide : " + this.typeMouvement);
        }

        if (nbJours == null) {
            throw new IllegalArgumentException("Le nombre de jours du mouvement de solde est obligatoire.");
        }

        // Pour un mouvement de type PRISE ou EXPIRATION, le signe doit être négatif (ou rendu négatif)
        if (("PRISE".equals(typeMouvement) || "EXPIRATION".equals(typeMouvement)) && nbJours.compareTo(BigDecimal.ZERO) > 0) {
            this.nbJours = nbJours.negate();
        }

        if ("AJUSTEMENT".equals(typeMouvement) && (commentaire == null || commentaire.isBlank())) {
            throw new IllegalArgumentException("Un commentaire explicatif est obligatoire pour tout mouvement d'AJUSTEMENT.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO mouvement_solde_conge (" +
                "id_employe, type_mouvement, nb_jours, id_demande_conge, date_mouvement, commentaire, created_by" +
                ") VALUES (?, ?, ?, ?, COALESCE(?, CURRENT_DATE), ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, idEmploye);
            ps.setString(i++, typeMouvement);
            ps.setBigDecimal(i++, nbJours);
            setNullableInt(ps, i++, idDemandeConge);
            ps.setDate(i++, dateMouvement);
            setNullableString(ps, i++, commentaire);
            setNullableInt(ps, i++, createdBy);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idMouvement = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE mouvement_solde_conge SET " +
                "type_mouvement = ?, nb_jours = ?, id_demande_conge = ?, date_mouvement = ?, commentaire = ?, created_by = ? " +
                "WHERE id_mouvement = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, typeMouvement);
            ps.setBigDecimal(i++, nbJours);
            setNullableInt(ps, i++, idDemandeConge);
            ps.setDate(i++, dateMouvement);
            setNullableString(ps, i++, commentaire);
            setNullableInt(ps, i++, createdBy);
            ps.setInt(i++, idMouvement);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM mouvement_solde_conge WHERE id_mouvement = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idMouvement);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public static MouvementSoldeConge rechercherParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM mouvement_solde_conge WHERE id_mouvement = ?";
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

    public static List<MouvementSoldeConge> listParEmploye(Integer idEmploye, Connection c) throws Exception {
        List<MouvementSoldeConge> list = new ArrayList<>();
        if (idEmploye == null) return list;
        String sql = "SELECT m.*, SUM(m.nb_jours) OVER (PARTITION BY m.id_employe " +
                "ORDER BY m.date_mouvement, m.id_mouvement ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS solde_apres " +
                "FROM mouvement_solde_conge m WHERE m.id_employe = ? " +
                "ORDER BY m.date_mouvement DESC, m.id_mouvement DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MouvementSoldeConge mouvement = mapResultSet(rs);
                    mouvement.setSoldeApres(rs.getBigDecimal("solde_apres"));
                    list.add(mouvement);
                }
            }
        }
        return list;
    }

    private static MouvementSoldeConge mapResultSet(ResultSet rs) throws Exception {
        MouvementSoldeConge obj = new MouvementSoldeConge();
        obj.setIdMouvement(rs.getInt("id_mouvement"));
        obj.setIdEmploye(rs.getInt("id_employe"));
        obj.setTypeMouvement(rs.getString("type_mouvement"));
        obj.setNbJours(rs.getBigDecimal("nb_jours"));
        obj.setIdDemandeConge(getNullableIntFromRs(rs, "id_demande_conge"));
        obj.setDateMouvement(rs.getDate("date_mouvement"));
        obj.setCommentaire(rs.getString("commentaire"));
        obj.setCreatedBy(getNullableIntFromRs(rs, "created_by"));
        obj.setCreatedAt(rs.getTimestamp("created_at"));
        return obj;
    }

    public BigDecimal getSoldeApres() { return soldeApres; }
    public void setSoldeApres(BigDecimal soldeApres) { this.soldeApres = soldeApres; }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws Exception {
        if (value != null) ps.setInt(index, value);
        else ps.setNull(index, Types.INTEGER);
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws Exception {
        if (value != null && !value.isBlank()) ps.setString(index, value.trim());
        else ps.setNull(index, Types.VARCHAR);
    }

    private static Integer getNullableIntFromRs(ResultSet rs, String colName) throws Exception {
        int val = rs.getInt(colName);
        return rs.wasNull() ? null : val;
    }

    // Getters / Setters
    public Integer getIdMouvement() { return idMouvement; }
    public void setIdMouvement(Integer idMouvement) { this.idMouvement = idMouvement; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getTypeMouvement() { return typeMouvement; }
    public void setTypeMouvement(String typeMouvement) { this.typeMouvement = typeMouvement; }

    public BigDecimal getNbJours() { return nbJours; }
    public void setNbJours(BigDecimal nbJours) { this.nbJours = nbJours; }

    public Integer getIdDemandeConge() { return idDemandeConge; }
    public void setIdDemandeConge(Integer idDemandeConge) { this.idDemandeConge = idDemandeConge; }

    public Date getDateMouvement() { return dateMouvement; }
    public void setDateMouvement(Date dateMouvement) { this.dateMouvement = dateMouvement; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
