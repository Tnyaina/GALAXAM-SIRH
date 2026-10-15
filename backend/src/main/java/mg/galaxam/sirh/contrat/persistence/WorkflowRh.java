package mg.galaxam.sirh.contrat.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Moteur d'événements RH : orchestre et trace toutes les transitions de carrière.
 *
 * Cycle de vie :
 *   BROUILLON → PLANIFIE : La DAF valide avec date d'effet future
 *   BROUILLON → ACTIF    : La DAF valide avec date d'effet immédiate (les opérations SQL s'exécutent)
 *   PLANIFIE  → ACTIF    : Le scheduler applique le mouvement à la date d'effet
 *   BROUILLON → ANNULE   : La DAF annule avant validation
 *   PLANIFIE  → ANNULE   : La DAF annule une planification pas encore appliquée
 *
 * Liens source/cible :
 *   - id_contrat_source / id_contrat_cible : contrats concernés par la transition
 *   - id_affectation_source / id_affectation_cible : affectations concernées
 *   (sens unique workflow → affectation, jamais l'inverse)
 */
public class WorkflowRh extends ClassMere {

    private Integer idWorkflow;
    private Integer idEmploye;
    private Integer idTypeEvenementRh;
    private Integer idStatutRh;

    private Integer idContratSource;
    private Integer idContratCible;
    private Integer idAffectationSource;
    private Integer idAffectationCible;

    private Date dateDemande;
    private Date dateEffet;

    private String motif;
    private String commentaire;

    private Integer idDemandeur;   // id_utilisateur du compte DAF/admin
    private Integer idValideur;    // id_utilisateur du compte DAF/admin

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public WorkflowRh() {
        this.setNomTable("WORKFLOW_RH");
    }

    @Override
    public Integer getTuppleID() { return idWorkflow; }

    @Override
    public String getAttributIDName() { return "id_workflow"; }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) throw new IllegalArgumentException("L'employé est obligatoire.");
        if (idTypeEvenementRh == null) throw new IllegalArgumentException("Le type d'événement RH est obligatoire.");
        if (idStatutRh == null) throw new IllegalArgumentException("Le statut du workflow est obligatoire.");
        if (dateEffet == null) throw new IllegalArgumentException("La date d'effet est obligatoire.");
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO workflow_rh (" +
                "id_employe, id_type_evenement_rh, id_statut_rh, " +
                "id_contrat_source, id_contrat_cible, id_affectation_source, id_affectation_cible, " +
                "date_demande, date_effet, motif, commentaire, id_demandeur, id_valideur" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_DATE), ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, idEmploye);
            ps.setInt(i++, idTypeEvenementRh);
            ps.setInt(i++, idStatutRh);
            setNullableInt(ps, i++, idContratSource);
            setNullableInt(ps, i++, idContratCible);
            setNullableInt(ps, i++, idAffectationSource);
            setNullableInt(ps, i++, idAffectationCible);
            setNullableDate(ps, i++, dateDemande);
            ps.setDate(i++, dateEffet);
            setNullableString(ps, i++, motif);
            setNullableString(ps, i++, commentaire);
            setNullableInt(ps, i++, idDemandeur);
            setNullableInt(ps, i++, idValideur);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) this.idWorkflow = rs.getInt(1);
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE workflow_rh SET " +
                "id_contrat_source = ?, id_contrat_cible = ?, " +
                "id_affectation_source = ?, id_affectation_cible = ?, " +
                "date_effet = ?, motif = ?, commentaire = ?, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_workflow = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            setNullableInt(ps, i++, idContratSource);
            setNullableInt(ps, i++, idContratCible);
            setNullableInt(ps, i++, idAffectationSource);
            setNullableInt(ps, i++, idAffectationCible);
            ps.setDate(i++, dateEffet);
            setNullableString(ps, i++, motif);
            setNullableString(ps, i++, commentaire);
            ps.setInt(i++, idWorkflow);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        // Seul un workflow BROUILLON peut être supprimé
        String sql = "DELETE FROM workflow_rh WHERE id_workflow = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idWorkflow);
            ps.executeUpdate();
        }
    }

    /**
     * Met à jour le workflow après la validation DAF.
     * Enregistre les IDs cibles (contrat et affectation créés lors de la validation),
     * le valideur, et passe le statut à VALIDE.
     *
     * @param idValideurUtilisateur ID de l'utilisateur DAF qui valide
     * @param idContratCibleFinal   ID du nouveau contrat créé (peut être null si pas de nouveau contrat)
     * @param idAffectationCibleFinal ID de la nouvelle affectation créée
     */
    public void valider(Integer idValideurUtilisateur, Integer idContratCibleFinal, Integer idAffectationCibleFinal, Connection c) throws Exception {
        String sql = "UPDATE workflow_rh SET " +
                "id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), " +
                "id_contrat_cible = COALESCE(?, id_contrat_cible), " +
                "id_affectation_cible = COALESCE(?, id_affectation_cible), " +
                "id_valideur = ?, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_workflow = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            setNullableInt(ps, 1, idContratCibleFinal);
            setNullableInt(ps, 2, idAffectationCibleFinal);
            setNullableInt(ps, 3, idValideurUtilisateur);
            ps.setInt(4, idWorkflow);
            ps.executeUpdate();
        }
    }

    /**
     * Annule un workflow BROUILLON.
     * Ne peut PAS annuler un workflow déjà VALIDE (il faudrait un workflow correctif).
     *
     * @param idValideurUtilisateur ID de l'utilisateur qui annule
     * @param commentaireAnnulation Raison de l'annulation
     */
    public void annuler(Integer idValideurUtilisateur, String commentaireAnnulation, Connection c) throws Exception {
        // Vérification que le workflow est bien en BROUILLON
        String checkSql = "SELECT rsw.code FROM workflow_rh w " +
                "JOIN ref_statut_rh rsw ON w.id_statut_rh = rsw.id_statut_rh " +
                "WHERE w.id_workflow = ?";
        String currentCode = null;
        try (PreparedStatement ps = c.prepareStatement(checkSql)) {
            ps.setInt(1, idWorkflow);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) currentCode = rs.getString("code");
            }
        }
        if (!"BROUILLON".equals(currentCode)) {
            throw new IllegalStateException("Seul un workflow en statut BROUILLON peut être annulé. Statut actuel : " + currentCode);
        }

        String sql = "UPDATE workflow_rh SET " +
                "id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ANNULE'), " +
                "commentaire = COALESCE(commentaire || ' | Annulé : ' || ?, 'Annulé : ' || ?), " +
                "id_valideur = ?, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_workflow = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            setNullableString(ps, 1, commentaireAnnulation);
            setNullableString(ps, 2, commentaireAnnulation);
            setNullableInt(ps, 3, idValideurUtilisateur);
            ps.setInt(4, idWorkflow);
            ps.executeUpdate();
        }
    }

    /**
     * Annule un workflow VALIDÉ dont le mouvement est encore PLANIFIÉ à date ultérieure (date_effet > CURRENT_DATE).
     */
    public void annulerPlanifie(Integer idValideurUtilisateur, String commentaireAnnulation, Connection c) throws Exception {
        String sql = "UPDATE workflow_rh SET " +
                "id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ANNULE'), " +
                "commentaire = COALESCE(commentaire || ' | Annulation Planification : ' || ?, 'Annulation Planification : ' || ?), " +
                "id_valideur = ?, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_workflow = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            setNullableString(ps, 1, commentaireAnnulation);
            setNullableString(ps, 2, commentaireAnnulation);
            setNullableInt(ps, 3, idValideurUtilisateur);
            ps.setInt(4, idWorkflow);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public WorkflowRh rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM workflow_rh WHERE id_workflow = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Retourne tous les workflows d'un employé, du plus récent au plus ancien.
     */
    public static List<WorkflowRh> listerParEmploye(Integer idEmploye, Connection c) throws Exception {
        List<WorkflowRh> list = new ArrayList<>();
        String sql = "SELECT * FROM workflow_rh WHERE id_employe = ? ORDER BY created_at DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    /**
     * Retourne le workflow BROUILLON en cours pour un employé (s'il existe).
     * Un employé ne devrait pas avoir plus d'un workflow BROUILLON à la fois.
     */
    public static WorkflowRh rechercherBrouillonParEmploye(Integer idEmploye, Connection c) throws Exception {
        String sql = "SELECT w.* FROM workflow_rh w " +
                "JOIN ref_statut_rh rsw ON w.id_statut_rh = rsw.id_statut_rh " +
                "WHERE w.id_employe = ? AND rsw.code = 'BROUILLON' " +
                "ORDER BY w.created_at DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    public static WorkflowRh mapResultSet(ResultSet rs) throws SQLException {
        WorkflowRh w = new WorkflowRh();
        w.setIdWorkflow(rs.getInt("id_workflow"));
        w.setIdEmploye(rs.getInt("id_employe"));
        w.setIdTypeEvenementRh(rs.getInt("id_type_evenement_rh"));
        w.setIdStatutRh(rs.getInt("id_statut_rh"));

        int idContratSrc = rs.getInt("id_contrat_source");
        w.setIdContratSource(rs.wasNull() ? null : idContratSrc);
        int idContratCbl = rs.getInt("id_contrat_cible");
        w.setIdContratCible(rs.wasNull() ? null : idContratCbl);
        int idAffSrc = rs.getInt("id_affectation_source");
        w.setIdAffectationSource(rs.wasNull() ? null : idAffSrc);
        int idAffCbl = rs.getInt("id_affectation_cible");
        w.setIdAffectationCible(rs.wasNull() ? null : idAffCbl);

        w.setDateDemande(rs.getDate("date_demande"));
        w.setDateEffet(rs.getDate("date_effet"));
        w.setMotif(rs.getString("motif"));
        w.setCommentaire(rs.getString("commentaire"));

        int idDem = rs.getInt("id_demandeur");
        w.setIdDemandeur(rs.wasNull() ? null : idDem);
        int idVal = rs.getInt("id_valideur");
        w.setIdValideur(rs.wasNull() ? null : idVal);

        w.setCreatedAt(rs.getTimestamp("created_at"));
        w.setUpdatedAt(rs.getTimestamp("updated_at"));
        return w;
    }

    // ─── Helpers JDBC ──────────────────────────────────────────────────────────

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER);
        else ps.setInt(index, value);
    }

    private void setNullableDate(PreparedStatement ps, int index, Date value) throws SQLException {
        if (value == null) ps.setNull(index, Types.DATE);
        else ps.setDate(index, value);
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) ps.setNull(index, Types.VARCHAR);
        else ps.setString(index, value.trim());
    }

    // ─── Getters & Setters ─────────────────────────────────────────────────────

    public Integer getIdWorkflow() { return idWorkflow; }
    public void setIdWorkflow(Integer idWorkflow) { this.idWorkflow = idWorkflow; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdTypeEvenementRh() { return idTypeEvenementRh; }
    public void setIdTypeEvenementRh(Integer idTypeEvenementRh) { this.idTypeEvenementRh = idTypeEvenementRh; }

    public Integer getIdStatutRh() { return idStatutRh; }
    public void setIdStatutRh(Integer idStatutRh) { this.idStatutRh = idStatutRh; }

    public Integer getIdContratSource() { return idContratSource; }
    public void setIdContratSource(Integer idContratSource) { this.idContratSource = idContratSource; }

    public Integer getIdContratCible() { return idContratCible; }
    public void setIdContratCible(Integer idContratCible) { this.idContratCible = idContratCible; }

    public Integer getIdAffectationSource() { return idAffectationSource; }
    public void setIdAffectationSource(Integer idAffectationSource) { this.idAffectationSource = idAffectationSource; }

    public Integer getIdAffectationCible() { return idAffectationCible; }
    public void setIdAffectationCible(Integer idAffectationCible) { this.idAffectationCible = idAffectationCible; }

    public Date getDateDemande() { return dateDemande; }
    public void setDateDemande(Date dateDemande) { this.dateDemande = dateDemande; }

    public Date getDateEffet() { return dateEffet; }
    public void setDateEffet(Date dateEffet) { this.dateEffet = dateEffet; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Integer getIdDemandeur() { return idDemandeur; }
    public void setIdDemandeur(Integer idDemandeur) { this.idDemandeur = idDemandeur; }

    public Integer getIdValideur() { return idValideur; }
    public void setIdValideur(Integer idValideur) { this.idValideur = idValideur; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
