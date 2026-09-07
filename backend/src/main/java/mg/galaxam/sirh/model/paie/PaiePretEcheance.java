package mg.galaxam.sirh.model.paie;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant une mensualité/échéance de remboursement d'un prêt d'entreprise.
 * Gère le prélèvement total ou partiel (en cas de quotité cessible 1/3 atteinte).
 */
public class PaiePretEcheance extends ClassMere {

    private Integer idEcheance;
    private Integer idPret;
    private Integer numeroEcheance;
    private Integer idPeriodePaie;
    private Date dateEcheance;
    private BigDecimal montantEcheance;
    private BigDecimal capitalRestantApres;
    private BigDecimal montantPreleve = BigDecimal.ZERO;
    private BigDecimal montantRestantEcheance;
    private String statut = "A_ECHOIR"; // A_ECHOIR, PRELEVEE_EN_PAIE, PRELEVEE_PARTIELLEMENT, REMBOURSEE_ANTICIPEE, REPORTEE, ANNULEE
    private Integer idBulletin;
    private Timestamp datePrelevement;
    private String commentaire;
    private Timestamp createdAt;

    public PaiePretEcheance() {
        this.setNomTable("PAIE_PRET_ECHEANCE");
    }

    @Override
    public Integer getTuppleID() {
        return idEcheance;
    }

    @Override
    public String getAttributIDName() {
        return "id_echeance";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        if (montantRestantEcheance == null && montantEcheance != null) {
            montantRestantEcheance = montantEcheance.subtract(montantPreleve != null ? montantPreleve : BigDecimal.ZERO);
        }
        String sql = "INSERT INTO paie_pret_echeance (" +
                "id_pret, numero_echeance, id_periode_paie, date_echeance, montant_echeance, " +
                "capital_restant_apres, montant_preleve, montant_restant_echeance, " +
                "statut, id_bulletin, date_prelevement, commentaire) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_echeance";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setInt(p++, idPret);
            ps.setInt(p++, numeroEcheance);
            if (idPeriodePaie != null) ps.setInt(p++, idPeriodePaie); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setDate(p++, dateEcheance);
            ps.setBigDecimal(p++, montantEcheance);
            ps.setBigDecimal(p++, capitalRestantApres);
            ps.setBigDecimal(p++, montantPreleve != null ? montantPreleve : BigDecimal.ZERO);
            ps.setBigDecimal(p++, montantRestantEcheance);
            ps.setString(p++, statut != null ? statut : "A_ECHOIR");
            if (idBulletin != null) ps.setInt(p++, idBulletin); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setTimestamp(p++, datePrelevement);
            ps.setString(p++, commentaire);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idEcheance = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE paie_pret_echeance SET " +
                "id_pret = ?, numero_echeance = ?, id_periode_paie = ?, date_echeance = ?, montant_echeance = ?, " +
                "capital_restant_apres = ?, montant_preleve = ?, montant_restant_echeance = ?, " +
                "statut = ?, id_bulletin = ?, date_prelevement = ?, commentaire = ? " +
                "WHERE id_echeance = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setInt(p++, idPret);
            ps.setInt(p++, numeroEcheance);
            if (idPeriodePaie != null) ps.setInt(p++, idPeriodePaie); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setDate(p++, dateEcheance);
            ps.setBigDecimal(p++, montantEcheance);
            ps.setBigDecimal(p++, capitalRestantApres);
            ps.setBigDecimal(p++, montantPreleve);
            ps.setBigDecimal(p++, montantRestantEcheance);
            ps.setString(p++, statut);
            if (idBulletin != null) ps.setInt(p++, idBulletin); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setTimestamp(p++, datePrelevement);
            ps.setString(p++, commentaire);
            ps.setInt(p++, idEcheance);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM paie_pret_echeance WHERE id_echeance = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEcheance);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        String sql = "SELECT * FROM paie_pret_echeance WHERE id_echeance = ?";
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

    public static PaiePretEcheance trouverParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM paie_pret_echeance WHERE id_echeance = ?";
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

    public static List<PaiePretEcheance> listerParPret(Integer idPret, Connection c) throws Exception {
        List<PaiePretEcheance> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_pret_echeance WHERE id_pret = ? ORDER BY numero_echeance ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPret);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    public static PaiePretEcheance trouverProchaineAEchoir(Integer idPret, Connection c) throws Exception {
        String sql = "SELECT * FROM paie_pret_echeance WHERE id_pret = ? AND statut IN ('A_ECHOIR', 'PRELEVEE_PARTIELLEMENT') ORDER BY numero_echeance ASC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPret);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * Valide le prélèvement total d'une échéance lors du calcul de bulletin et met à jour le prêt parent.
     */
    public void preleverEnPaie(Integer idBulletin, Integer idPeriode, Connection c) throws Exception {
        preleverEnPaie(this.montantEcheance, idBulletin, idPeriode, c);
    }

    /**
     * Valide le prélèvement total ou partiel d'une échéance (ex: quotité cessible 1/3 atteinte).
     */
    public void preleverEnPaie(BigDecimal montantEffectif, Integer idBulletin, Integer idPeriode, Connection c) throws Exception {
        if (montantEffectif == null || montantEffectif.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal cumulPreleve = (this.montantPreleve != null ? this.montantPreleve : BigDecimal.ZERO).add(montantEffectif);
        this.montantPreleve = cumulPreleve;
        this.montantRestantEcheance = this.montantEcheance.subtract(cumulPreleve).max(BigDecimal.ZERO);

        if (this.montantRestantEcheance.compareTo(BigDecimal.ZERO) == 0) {
            this.statut = "PRELEVEE_EN_PAIE";
        } else {
            this.statut = "PRELEVEE_PARTIELLEMENT";
        }

        this.idBulletin = idBulletin;
        this.idPeriodePaie = idPeriode;
        this.datePrelevement = new Timestamp(System.currentTimeMillis());
        this.update(c);

        // Mise à jour du capital restant dû sur le prêt parent
        PaiePret pret = PaiePret.trouverParId(this.idPret, c);
        if (pret != null) {
            pret.enregistrerRemboursement(montantEffectif, c);
        }
    }

    private static PaiePretEcheance mapResultSet(ResultSet rs) throws Exception {
        PaiePretEcheance e = new PaiePretEcheance();
        e.setIdEcheance(rs.getInt("id_echeance"));
        e.setIdPret(rs.getInt("id_pret"));
        e.setNumeroEcheance(rs.getInt("numero_echeance"));
        int pId = rs.getInt("id_periode_paie");
        e.setIdPeriodePaie(rs.wasNull() ? null : pId);
        e.setDateEcheance(rs.getDate("date_echeance"));
        e.setMontantEcheance(rs.getBigDecimal("montant_echeance"));
        e.setCapitalRestantApres(rs.getBigDecimal("capital_restant_apres"));
        e.setMontantPreleve(rs.getBigDecimal("montant_preleve"));
        e.setMontantRestantEcheance(rs.getBigDecimal("montant_restant_echeance"));
        e.setStatut(rs.getString("statut"));
        int bId = rs.getInt("id_bulletin");
        e.setIdBulletin(rs.wasNull() ? null : bId);
        e.setDatePrelevement(rs.getTimestamp("date_prelevement"));
        e.setCommentaire(rs.getString("commentaire"));
        e.setCreatedAt(rs.getTimestamp("created_at"));
        return e;
    }

    // Getters & Setters
    public Integer getIdEcheance() { return idEcheance; }
    public void setIdEcheance(Integer idEcheance) { this.idEcheance = idEcheance; }

    public Integer getIdPret() { return idPret; }
    public void setIdPret(Integer idPret) { this.idPret = idPret; }

    public Integer getNumeroEcheance() { return numeroEcheance; }
    public void setNumeroEcheance(Integer numeroEcheance) { this.numeroEcheance = numeroEcheance; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public Date getDateEcheance() { return dateEcheance; }
    public void setDateEcheance(Date dateEcheance) { this.dateEcheance = dateEcheance; }

    public BigDecimal getMontantEcheance() { return montantEcheance; }
    public void setMontantEcheance(BigDecimal montantEcheance) { this.montantEcheance = montantEcheance; }

    public BigDecimal getCapitalRestantApres() { return capitalRestantApres; }
    public void setCapitalRestantApres(BigDecimal capitalRestantApres) { this.capitalRestantApres = capitalRestantApres; }

    public BigDecimal getMontantPreleve() { return montantPreleve; }
    public void setMontantPreleve(BigDecimal montantPreleve) { this.montantPreleve = montantPreleve; }

    public BigDecimal getMontantRestantEcheance() { return montantRestantEcheance; }
    public void setMontantRestantEcheance(BigDecimal montantRestantEcheance) { this.montantRestantEcheance = montantRestantEcheance; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Integer getIdBulletin() { return idBulletin; }
    public void setIdBulletin(Integer idBulletin) { this.idBulletin = idBulletin; }

    public Timestamp getDatePrelevement() { return datePrelevement; }
    public void setDatePrelevement(Timestamp datePrelevement) { this.datePrelevement = datePrelevement; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
