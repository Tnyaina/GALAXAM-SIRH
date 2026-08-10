package mg.galaxam.sirh.model.contrat;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente la relation juridique/contractuelle entre un employé et la société.
 * Gère le chaînage via id_contrat_precedent pour tracer les transitions
 * (Stage → CDD/CDI, CDD → CDI, Retraite → Consultant).
 *
 * Statuts : BROUILLON → A_SIGNER → ACTIF → (SUSPENDU | CLOTURE | ROMPU)
 */
public class ContratTravail extends ClassMere {

    private Integer idContrat;
    private Integer idEmploye;
    private Integer idFiliale;
    private Integer idTypeContrat;
    private Integer idTypeIntervenant;
    private Integer idStatutContrat;
    private Integer idTypeEvenementInitial;
    private Integer idContratPrecedent;

    private String numeroContrat;
    private Date dateSignature;
    private Date dateDebut;
    private Date dateFin;

    private boolean comptabiliseEffectif = true;
    private Integer idMotifSortie;

    private String description;
    private String commentaire;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public ContratTravail() {
        this.setNomTable("CONTRAT_TRAVAIL");
    }

    @Override
    public Integer getTuppleID() { return idContrat; }

    @Override
    public String getAttributIDName() { return "id_contrat"; }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) throw new IllegalArgumentException("L'employé est obligatoire.");
        if (idFiliale == null) throw new IllegalArgumentException("La filiale est obligatoire.");
        if (idTypeContrat == null) throw new IllegalArgumentException("Le type de contrat est obligatoire.");
        if (idTypeIntervenant == null) throw new IllegalArgumentException("Le type d'intervenant est obligatoire.");
        if (idStatutContrat == null) throw new IllegalArgumentException("Le statut du contrat est obligatoire.");
        if (dateDebut == null) throw new IllegalArgumentException("La date de début est obligatoire.");

        // Vérification cohérence des dates
        if (dateFin != null && dateFin.before(dateDebut)) {
            throw new IllegalArgumentException("La date de fin ne peut pas être antérieure à la date de début.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        // Génération du numéro de contrat si non fourni
        if (numeroContrat == null || numeroContrat.isBlank()) {
            numeroContrat = genererNumeroContrat(c);
        }

        String sql = "INSERT INTO contrat_travail (" +
                "id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, " +
                "id_type_evenement_initial, id_contrat_precedent, " +
                "numero_contrat, date_signature, date_debut, date_fin, " +
                "comptabilise_effectif, id_motif_sortie, description, commentaire" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, idEmploye);
            ps.setInt(i++, idFiliale);
            ps.setInt(i++, idTypeContrat);
            ps.setInt(i++, idTypeIntervenant);
            ps.setInt(i++, idStatutContrat);
            setNullableInt(ps, i++, idTypeEvenementInitial);
            setNullableInt(ps, i++, idContratPrecedent);
            ps.setString(i++, numeroContrat);
            setNullableDate(ps, i++, dateSignature);
            ps.setDate(i++, dateDebut);
            setNullableDate(ps, i++, dateFin);
            ps.setBoolean(i++, comptabiliseEffectif);
            setNullableInt(ps, i++, idMotifSortie);
            setNullableString(ps, i++, description);
            setNullableString(ps, i++, commentaire);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) this.idContrat = rs.getInt(1);
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE contrat_travail SET " +
                "id_filiale = ?, id_type_contrat = ?, id_type_intervenant = ?, id_statut_contrat = ?, " +
                "id_type_evenement_initial = ?, id_contrat_precedent = ?, " +
                "numero_contrat = ?, date_signature = ?, date_debut = ?, date_fin = ?, " +
                "comptabilise_effectif = ?, id_motif_sortie = ?, description = ?, commentaire = ?, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_contrat = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, idFiliale);
            ps.setInt(i++, idTypeContrat);
            ps.setInt(i++, idTypeIntervenant);
            ps.setInt(i++, idStatutContrat);
            setNullableInt(ps, i++, idTypeEvenementInitial);
            setNullableInt(ps, i++, idContratPrecedent);
            ps.setString(i++, numeroContrat);
            setNullableDate(ps, i++, dateSignature);
            ps.setDate(i++, dateDebut);
            setNullableDate(ps, i++, dateFin);
            ps.setBoolean(i++, comptabiliseEffectif);
            setNullableInt(ps, i++, idMotifSortie);
            setNullableString(ps, i++, description);
            setNullableString(ps, i++, commentaire);
            ps.setInt(i++, idContrat);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        // Un contrat BROUILLON peut être supprimé. Un contrat ACTIF ne peut pas.
        String sql = "DELETE FROM contrat_travail WHERE id_contrat = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idContrat);
            ps.executeUpdate();
        }
    }

    /**
     * Active un contrat (BROUILLON/A_SIGNER → ACTIF).
     * Appelé lors de la validation DAF.
     */
    public void activer(Connection c) throws Exception {
        String sql = "UPDATE contrat_travail SET " +
                "id_statut_contrat = (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'), " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_contrat = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idContrat);
            ps.executeUpdate();
        }
    }

    /**
     * Clôture un contrat arrivé à terme (→ CLOTURE).
     * Utilisé lors des transitions : CDD→CDI, Stage→Embauche, Retraite, etc.
     *
     * @param dateFermeture  date de fin effective du contrat
     * @param idMotif        motif de sortie (ex: FIN_CONTRAT, RETRAITE)
     * @param commentaireFin commentaire de clôture
     */
    public void cloturerContrat(Date dateFermeture, Integer idMotif, String commentaireFin, Connection c) throws Exception {
        String sql = "UPDATE contrat_travail SET " +
                "id_statut_contrat = (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'CLOTURE'), " +
                "date_fin = ?, " +
                "id_motif_sortie = ?, " +
                "commentaire = COALESCE(commentaire || ' | ' || ?, ?), " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_contrat = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, dateFermeture);
            setNullableInt(ps, 2, idMotif);
            setNullableString(ps, 3, commentaireFin);
            setNullableString(ps, 4, commentaireFin);
            ps.setInt(5, idContrat);
            ps.executeUpdate();
        }
    }

    /**
     * Rompt un contrat de façon anticipée (→ ROMPU).
     * Utilisé lors des ruptures de période d'essai, licenciements, etc.
     */
    public void rompreContrat(Date dateRupture, Integer idMotif, String commentaireRupture, Connection c) throws Exception {
        String sql = "UPDATE contrat_travail SET " +
                "id_statut_contrat = (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ROMPU'), " +
                "date_fin = ?, " +
                "id_motif_sortie = ?, " +
                "commentaire = COALESCE(commentaire || ' | ' || ?, ?), " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_contrat = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, dateRupture);
            setNullableInt(ps, 2, idMotif);
            setNullableString(ps, 3, commentaireRupture);
            setNullableString(ps, 4, commentaireRupture);
            ps.setInt(5, idContrat);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ContratTravail rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM contrat_travail WHERE id_contrat = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Retourne le contrat ACTIF d'un employé (il ne peut en avoir qu'un seul à la fois).
     */
    public static ContratTravail rechercherActifParEmploye(Integer idEmploye, Connection c) throws Exception {
        String sql = "SELECT ct.* FROM contrat_travail ct " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE ct.id_employe = ? AND rsc.code = 'ACTIF'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Retourne tous les contrats d'un employé (historique complet), du plus récent au plus ancien.
     */
    public static List<ContratTravail> listerParEmploye(Integer idEmploye, Connection c) throws Exception {
        List<ContratTravail> list = new ArrayList<>();
        String sql = "SELECT * FROM contrat_travail WHERE id_employe = ? ORDER BY date_debut DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    /**
     * Génère un numéro de contrat unique au format CTR-{ANNEE}-{SEQUENCE:05d}.
     * Ex : CTR-2026-00042
     */
    private String genererNumeroContrat(Connection c) throws Exception {
        String annee = String.valueOf(LocalDate.now().getYear());
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(numero_contrat FROM 10) AS INTEGER)), 0) + 1 " +
                "FROM contrat_travail WHERE numero_contrat LIKE 'CTR-" + annee + "-%'";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int seq = rs.getInt(1);
                return String.format("CTR-%s-%05d", annee, seq);
            }
        }
        return String.format("CTR-%s-00001", annee);
    }

    public static ContratTravail mapResultSet(ResultSet rs) throws SQLException {
        ContratTravail ct = new ContratTravail();
        ct.setIdContrat(rs.getInt("id_contrat"));
        ct.setIdEmploye(rs.getInt("id_employe"));
        ct.setIdFiliale(rs.getInt("id_filiale"));
        ct.setIdTypeContrat(rs.getInt("id_type_contrat"));
        ct.setIdTypeIntervenant(rs.getInt("id_type_intervenant"));
        ct.setIdStatutContrat(rs.getInt("id_statut_contrat"));

        int idTypeEvt = rs.getInt("id_type_evenement_initial");
        ct.setIdTypeEvenementInitial(rs.wasNull() ? null : idTypeEvt);

        int idContratPrec = rs.getInt("id_contrat_precedent");
        ct.setIdContratPrecedent(rs.wasNull() ? null : idContratPrec);

        ct.setNumeroContrat(rs.getString("numero_contrat"));
        ct.setDateSignature(rs.getDate("date_signature"));
        ct.setDateDebut(rs.getDate("date_debut"));
        ct.setDateFin(rs.getDate("date_fin"));
        ct.setComptabiliseEffectif(rs.getBoolean("comptabilise_effectif"));

        int idMotif = rs.getInt("id_motif_sortie");
        ct.setIdMotifSortie(rs.wasNull() ? null : idMotif);

        ct.setDescription(rs.getString("description"));
        ct.setCommentaire(rs.getString("commentaire"));
        ct.setCreatedAt(rs.getTimestamp("created_at"));
        ct.setUpdatedAt(rs.getTimestamp("updated_at"));
        return ct;
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

    public Integer getIdContrat() { return idContrat; }
    public void setIdContrat(Integer idContrat) { this.idContrat = idContrat; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdFiliale() { return idFiliale; }
    public void setIdFiliale(Integer idFiliale) { this.idFiliale = idFiliale; }

    public Integer getIdTypeContrat() { return idTypeContrat; }
    public void setIdTypeContrat(Integer idTypeContrat) { this.idTypeContrat = idTypeContrat; }

    public Integer getIdTypeIntervenant() { return idTypeIntervenant; }
    public void setIdTypeIntervenant(Integer idTypeIntervenant) { this.idTypeIntervenant = idTypeIntervenant; }

    public Integer getIdStatutContrat() { return idStatutContrat; }
    public void setIdStatutContrat(Integer idStatutContrat) { this.idStatutContrat = idStatutContrat; }

    public Integer getIdTypeEvenementInitial() { return idTypeEvenementInitial; }
    public void setIdTypeEvenementInitial(Integer idTypeEvenementInitial) { this.idTypeEvenementInitial = idTypeEvenementInitial; }

    public Integer getIdContratPrecedent() { return idContratPrecedent; }
    public void setIdContratPrecedent(Integer idContratPrecedent) { this.idContratPrecedent = idContratPrecedent; }

    public String getNumeroContrat() { return numeroContrat; }
    public void setNumeroContrat(String numeroContrat) { this.numeroContrat = numeroContrat; }

    public Date getDateSignature() { return dateSignature; }
    public void setDateSignature(Date dateSignature) { this.dateSignature = dateSignature; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }

    public boolean isComptabiliseEffectif() { return comptabiliseEffectif; }
    public void setComptabiliseEffectif(boolean comptabiliseEffectif) { this.comptabiliseEffectif = comptabiliseEffectif; }

    public Integer getIdMotifSortie() { return idMotifSortie; }
    public void setIdMotifSortie(Integer idMotifSortie) { this.idMotifSortie = idMotifSortie; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
