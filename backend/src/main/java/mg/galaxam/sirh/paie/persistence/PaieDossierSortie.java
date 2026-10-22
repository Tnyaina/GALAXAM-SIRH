package mg.galaxam.sirh.paie.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant l'orchestration administrative et préparatoire
 * du dossier de sortie d'un salarié.
 * Table BDD : paie_dossier_sortie
 */
public class PaieDossierSortie extends ClassMere {

    private Integer idDossierSortie;
    private Integer idEmploye;
    private Integer idContrat;
    private Date dateDepart;
    private String motifRupture;
    private String statutDossier = "EN_PREPARATION";
    private String donneesPreparation;
    private Boolean certificatDelivre = Boolean.FALSE;
    private Date dateDelivranceCertificat;
    private String modeRemiseCertificat;
    private Boolean recuStcRemis = Boolean.FALSE;
    private Date dateRemiseRecu;
    private Integer idStc;
    private Integer creePar;
    private Integer cloturePar;
    private Timestamp dateCloture;
    private String motifCloture;
    private Boolean derogationReglement = Boolean.FALSE;
    private String justificationDerogation;
    private String autoriteDecisionDerogation;
    private String referenceDecisionDerogation;
    private Date dateDecisionDerogation;
    private Integer version = 1;
    private String referenceSimulation;
    private String snapshotSimulation;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public PaieDossierSortie() {
        setNomTable("paie_dossier_sortie");
    }

    @Override
    public Integer getTuppleID() {
        return this.idDossierSortie;
    }

    @Override
    public String getAttributIDName() {
        return "id_dossier_sortie";
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return trouverParId(Integer.parseInt(id), c);
    }

    public static PaieDossierSortie trouverParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM paie_dossier_sortie WHERE id_dossier_sortie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper(rs);
                }
            }
        }
        return null;
    }

    public static PaieDossierSortie trouverParIdPourMiseAJour(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM paie_dossier_sortie WHERE id_dossier_sortie = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper(rs);
                }
            }
        }
        return null;
    }

    public static PaieDossierSortie trouverParEmployeEtDate(Integer idEmploye, Date dateDepart, Connection c) throws Exception {
        if (idEmploye == null || dateDepart == null) return null;
        String sql = "SELECT * FROM paie_dossier_sortie WHERE id_employe = ? AND date_depart = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, dateDepart);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper(rs);
                }
            }
        }
        return null;
    }

    public static PaieDossierSortie trouverDernierParEmploye(Integer idEmploye, Connection c) throws Exception {
        if (idEmploye == null) return null;
        String sql = "SELECT * FROM paie_dossier_sortie WHERE id_employe = ? ORDER BY date_depart DESC, id_dossier_sortie DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper(rs);
                }
            }
        }
        return null;
    }

    public static List<PaieDossierSortie> listerTous(Connection c) throws Exception {
        List<PaieDossierSortie> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_dossier_sortie ORDER BY date_depart DESC, id_dossier_sortie DESC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapper(rs));
            }
        }
        return list;
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO paie_dossier_sortie (" +
                "id_employe, id_contrat, date_depart, motif_rupture, statut_dossier, " +
                "donnees_preparation, " +
                "certificat_delivre, date_delivrance_certificat, mode_remise_certificat, " +
                "recu_stc_remis, date_remise_recu, id_stc, " +
                "cree_par, cloture_par, date_cloture, motif_cloture, derogation_reglement, justification_derogation, " +
                "autorite_decision_derogation, reference_decision_derogation, date_decision_derogation, " +
                "version, reference_simulation, snapshot_simulation, " +
                "created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                "RETURNING id_dossier_sortie, version, created_at, updated_at";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            if (idContrat != null) ps.setInt(2, idContrat); else ps.setNull(2, java.sql.Types.INTEGER);
            ps.setDate(3, dateDepart);
            ps.setString(4, motifRupture != null ? motifRupture : "DEMISSION");
            ps.setString(5, statutDossier != null ? statutDossier : "EN_PREPARATION");
            ps.setString(6, donneesPreparation != null ? donneesPreparation : "{}");
            ps.setBoolean(7, Boolean.TRUE.equals(certificatDelivre));
            ps.setDate(8, dateDelivranceCertificat);
            ps.setString(9, modeRemiseCertificat);
            ps.setBoolean(10, Boolean.TRUE.equals(recuStcRemis));
            ps.setDate(11, dateRemiseRecu);
            if (idStc != null) ps.setInt(12, idStc); else ps.setNull(12, java.sql.Types.INTEGER);
            if (creePar != null) ps.setInt(13, creePar); else ps.setNull(13, java.sql.Types.INTEGER);
            if (cloturePar != null) ps.setInt(14, cloturePar); else ps.setNull(14, java.sql.Types.INTEGER);
            ps.setTimestamp(15, dateCloture);
            ps.setString(16, motifCloture);
            ps.setBoolean(17, Boolean.TRUE.equals(derogationReglement));
            ps.setString(18, justificationDerogation);
            ps.setString(19, autoriteDecisionDerogation);
            ps.setString(20, referenceDecisionDerogation);
            ps.setDate(21, dateDecisionDerogation);
            ps.setInt(22, version != null ? version : 1);
            ps.setString(23, referenceSimulation);
            ps.setString(24, snapshotSimulation != null ? snapshotSimulation : "{}");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idDossierSortie = rs.getInt("id_dossier_sortie");
                    this.version = rs.getInt("version");
                    this.createdAt = rs.getTimestamp("created_at");
                    this.updatedAt = rs.getTimestamp("updated_at");
                }
            }
        }
        return this;
    }

    @Override
    public ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE paie_dossier_sortie SET " +
                "id_contrat = ?, date_depart = ?, motif_rupture = ?, statut_dossier = ?, " +
                "donnees_preparation = ?::jsonb, " +
                "certificat_delivre = ?, date_delivrance_certificat = ?, " +
                "mode_remise_certificat = ?, recu_stc_remis = ?, date_remise_recu = ?, " +
                "id_stc = ?, cloture_par = ?, date_cloture = ?, " +
                "motif_cloture = ?, derogation_reglement = ?, justification_derogation = ?, " +
                "autorite_decision_derogation = ?, reference_decision_derogation = ?, date_decision_derogation = ?, " +
                "reference_simulation = ?, snapshot_simulation = ?::jsonb, " +
                "version = version + 1, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_dossier_sortie = ? AND version = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (idContrat != null) ps.setInt(1, idContrat); else ps.setNull(1, java.sql.Types.INTEGER);
            ps.setDate(2, dateDepart);
            ps.setString(3, motifRupture);
            ps.setString(4, statutDossier);
            ps.setString(5, donneesPreparation != null ? donneesPreparation : "{}");
            ps.setBoolean(6, Boolean.TRUE.equals(certificatDelivre));
            ps.setDate(7, dateDelivranceCertificat);
            ps.setString(8, modeRemiseCertificat);
            ps.setBoolean(9, Boolean.TRUE.equals(recuStcRemis));
            ps.setDate(10, dateRemiseRecu);
            if (idStc != null) ps.setInt(11, idStc); else ps.setNull(11, java.sql.Types.INTEGER);
            if (cloturePar != null) ps.setInt(12, cloturePar); else ps.setNull(12, java.sql.Types.INTEGER);
            ps.setTimestamp(13, dateCloture);
            ps.setString(14, motifCloture);
            ps.setBoolean(15, Boolean.TRUE.equals(derogationReglement));
            ps.setString(16, justificationDerogation);
            ps.setString(17, autoriteDecisionDerogation);
            ps.setString(18, referenceDecisionDerogation);
            ps.setDate(19, dateDecisionDerogation);
            ps.setString(20, referenceSimulation);
            ps.setString(21, snapshotSimulation != null ? snapshotSimulation : "{}");
            ps.setInt(22, idDossierSortie);
            int currentVersion = this.version != null ? this.version : 1;
            ps.setInt(23, currentVersion);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.DATA_INTEGRITY_VIOLATION,
                        "Conflit de concurrence : le dossier de sortie (id=" + idDossierSortie
                                + ") a été modifié par un autre processus (version attendue : " + currentVersion + ").");
            }
            this.version = currentVersion + 1;
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        if (this.idDossierSortie == null) return;
        String sql = "DELETE FROM paie_dossier_sortie WHERE id_dossier_sortie = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, this.idDossierSortie);
            ps.executeUpdate();
        }
    }

    public void enregistrer(Connection c) throws Exception {
        if (this.idDossierSortie == null) {
            inserer(c);
        } else {
            modifier(c);
        }
    }

    private static PaieDossierSortie mapper(ResultSet rs) throws Exception {
        PaieDossierSortie d = new PaieDossierSortie();
        d.setIdDossierSortie(rs.getInt("id_dossier_sortie"));
        d.setIdEmploye(rs.getInt("id_employe"));
        int cId = rs.getInt("id_contrat");
        if (!rs.wasNull()) d.setIdContrat(cId);
        d.setDateDepart(rs.getDate("date_depart"));
        d.setMotifRupture(rs.getString("motif_rupture"));
        d.setStatutDossier(rs.getString("statut_dossier"));
        d.setDonneesPreparation(rs.getString("donnees_preparation"));
        d.setCertificatDelivre(rs.getBoolean("certificat_delivre"));
        d.setDateDelivranceCertificat(rs.getDate("date_delivrance_certificat"));
        d.setModeRemiseCertificat(rs.getString("mode_remise_certificat"));
        d.setRecuStcRemis(rs.getBoolean("recu_stc_remis"));
        d.setDateRemiseRecu(rs.getDate("date_remise_recu"));
        int stcId = rs.getInt("id_stc");
        if (!rs.wasNull()) d.setIdStc(stcId);
        int cp = rs.getInt("cree_par");
        if (!rs.wasNull()) d.setCreePar(cp);
        int clp = rs.getInt("cloture_par");
        if (!rs.wasNull()) d.setCloturePar(clp);
        d.setDateCloture(rs.getTimestamp("date_cloture"));
        try {
            d.setMotifCloture(rs.getString("motif_cloture"));
            d.setDerogationReglement(rs.getBoolean("derogation_reglement"));
            d.setJustificationDerogation(rs.getString("justification_derogation"));
            d.setAutoriteDecisionDerogation(rs.getString("autorite_decision_derogation"));
            d.setReferenceDecisionDerogation(rs.getString("reference_decision_derogation"));
            d.setDateDecisionDerogation(rs.getDate("date_decision_derogation"));
        } catch (Exception ignored) {}
        d.setVersion(rs.getInt("version"));
        if (rs.wasNull()) d.setVersion(1);
        d.setReferenceSimulation(rs.getString("reference_simulation"));
        d.setSnapshotSimulation(rs.getString("snapshot_simulation"));
        d.setCreatedAt(rs.getTimestamp("created_at"));
        d.setUpdatedAt(rs.getTimestamp("updated_at"));
        return d;
    }

    // Getters and Setters
    public Integer getIdDossierSortie() { return idDossierSortie; }
    public void setIdDossierSortie(Integer idDossierSortie) { this.idDossierSortie = idDossierSortie; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdContrat() { return idContrat; }
    public void setIdContrat(Integer idContrat) { this.idContrat = idContrat; }

    public Date getDateDepart() { return dateDepart; }
    public void setDateDepart(Date dateDepart) { this.dateDepart = dateDepart; }

    public String getMotifRupture() { return motifRupture; }
    public void setMotifRupture(String motifRupture) { this.motifRupture = motifRupture; }

    public String getStatutDossier() { return statutDossier; }
    public void setStatutDossier(String statutDossier) { this.statutDossier = statutDossier; }

    public String getDonneesPreparation() { return donneesPreparation; }
    public void setDonneesPreparation(String donneesPreparation) { this.donneesPreparation = donneesPreparation; }


    public Boolean getCertificatDelivre() { return certificatDelivre; }
    public void setCertificatDelivre(Boolean certificatDelivre) { this.certificatDelivre = certificatDelivre; }

    public Date getDateDelivranceCertificat() { return dateDelivranceCertificat; }
    public void setDateDelivranceCertificat(Date dateDelivranceCertificat) { this.dateDelivranceCertificat = dateDelivranceCertificat; }

    public String getModeRemiseCertificat() { return modeRemiseCertificat; }
    public void setModeRemiseCertificat(String modeRemiseCertificat) { this.modeRemiseCertificat = modeRemiseCertificat; }

    public Boolean getRecuStcRemis() { return recuStcRemis; }
    public void setRecuStcRemis(Boolean recuStcRemis) { this.recuStcRemis = recuStcRemis; }

    public Date getDateRemiseRecu() { return dateRemiseRecu; }
    public void setDateRemiseRecu(Date dateRemiseRecu) { this.dateRemiseRecu = dateRemiseRecu; }

    public Integer getIdStc() { return idStc; }
    public void setIdStc(Integer idStc) { this.idStc = idStc; }

    public Integer getCreePar() { return creePar; }
    public void setCreePar(Integer creePar) { this.creePar = creePar; }

    public Integer getCloturePar() { return cloturePar; }
    public void setCloturePar(Integer cloturePar) { this.cloturePar = cloturePar; }

    public Timestamp getDateCloture() { return dateCloture; }
    public void setDateCloture(Timestamp dateCloture) { this.dateCloture = dateCloture; }

    public String getMotifCloture() { return motifCloture; }
    public void setMotifCloture(String motifCloture) { this.motifCloture = motifCloture; }

    public Boolean getDerogationReglement() { return derogationReglement; }
    public void setDerogationReglement(Boolean derogationReglement) { this.derogationReglement = derogationReglement; }

    public String getJustificationDerogation() { return justificationDerogation; }
    public void setJustificationDerogation(String justificationDerogation) { this.justificationDerogation = justificationDerogation; }

    public String getAutoriteDecisionDerogation() { return autoriteDecisionDerogation; }
    public void setAutoriteDecisionDerogation(String autoriteDecisionDerogation) { this.autoriteDecisionDerogation = autoriteDecisionDerogation; }

    public String getReferenceDecisionDerogation() { return referenceDecisionDerogation; }
    public void setReferenceDecisionDerogation(String referenceDecisionDerogation) { this.referenceDecisionDerogation = referenceDecisionDerogation; }

    public Date getDateDecisionDerogation() { return dateDecisionDerogation; }
    public void setDateDecisionDerogation(Date dateDecisionDerogation) { this.dateDecisionDerogation = dateDecisionDerogation; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getReferenceSimulation() { return referenceSimulation; }
    public void setReferenceSimulation(String referenceSimulation) { this.referenceSimulation = referenceSimulation; }

    public String getSnapshotSimulation() { return snapshotSimulation; }
    public void setSnapshotSimulation(String snapshotSimulation) { this.snapshotSimulation = snapshotSimulation; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
