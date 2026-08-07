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

public class ContratTravail extends ClassMere {

    private Integer idContrat;
    private Integer idEmploye;
    private Integer idFiliale;
    private Integer idTypeContrat;
    private Integer idTypeIntervenant;
    private Integer idContratPrecedent;

    private String numeroContrat;
    private Date dateSignature;
    private Date dateDebut;
    private Date dateFin;
    private Date dateFinEssai;

    private boolean essaiRenouvele = false;
    private boolean comptabiliseEffectif = true;
    private boolean estActif = true;
    private Integer idMotifSortie;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public ContratTravail() {
        this.setNomTable("CONTRAT_TRAVAIL");
    }

    @Override
    public Integer getTuppleID() {
        return this.idContrat;
    }

    @Override
    public String getAttributIDName() {
        return "id_contrat";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'employé est obligatoire pour le contrat.");
        }
        if (idFiliale == null) {
            throw new IllegalArgumentException("La filiale de rattachement est obligatoire.");
        }
        if (idTypeContrat == null) {
            throw new IllegalArgumentException("Le type de contrat est obligatoire.");
        }
        if (idTypeIntervenant == null) {
            throw new IllegalArgumentException("Le type d'intervenant est obligatoire.");
        }
        if (dateDebut == null) {
            throw new IllegalArgumentException("La date de début de contrat est obligatoire.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO contrat_travail (" +
                "id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_contrat_precedent, numero_contrat, date_signature, date_debut, date_fin, date_fin_essai, essai_renouvele, comptabilise_effectif, est_actif, id_motif_sortie" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_contrat";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idFiliale);
            ps.setInt(3, idTypeContrat);
            ps.setInt(4, idTypeIntervenant);
            ps.setObject(5, idContratPrecedent);
            ps.setString(6, numeroContrat);
            ps.setDate(7, dateSignature);
            ps.setDate(8, dateDebut);
            ps.setDate(9, dateFin);
            ps.setDate(10, dateFinEssai);
            ps.setBoolean(11, essaiRenouvele);
            ps.setBoolean(12, comptabiliseEffectif);
            ps.setBoolean(13, estActif);
            ps.setObject(14, idMotifSortie);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idContrat = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE contrat_travail SET " +
                "id_filiale = ?, id_type_contrat = ?, id_type_intervenant = ?, numero_contrat = ?, date_signature = ?, date_debut = ?, date_fin = ?, date_fin_essai = ?, essai_renouvele = ?, comptabilise_effectif = ?, est_actif = ?, id_motif_sortie = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_contrat = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idFiliale);
            ps.setInt(2, idTypeContrat);
            ps.setInt(3, idTypeIntervenant);
            ps.setString(4, numeroContrat);
            ps.setDate(5, dateSignature);
            ps.setDate(6, dateDebut);
            ps.setDate(7, dateFin);
            ps.setDate(8, dateFinEssai);
            ps.setBoolean(9, essaiRenouvele);
            ps.setBoolean(10, comptabiliseEffectif);
            ps.setBoolean(11, estActif);
            ps.setObject(12, idMotifSortie);
            ps.setInt(13, idContrat);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "UPDATE contrat_travail SET est_actif = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id_contrat = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idContrat);
            ps.executeUpdate();
        }
        this.estActif = false;
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ContratTravail rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM contrat_travail WHERE id_contrat = ?";
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

    public static List<ContratTravail> listByEmployeId(Integer idEmploye, Connection c) throws Exception {
        List<ContratTravail> list = new ArrayList<>();
        String sql = "SELECT * FROM contrat_travail WHERE id_employe = ? ORDER BY date_debut DESC";
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

    public static ContratTravail getContratActif(Integer idEmploye, Connection c) throws Exception {
        String sql = "SELECT * FROM contrat_travail WHERE id_employe = ? AND est_actif = TRUE ORDER BY date_debut DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static ContratTravail mapResultSet(ResultSet rs) throws SQLException {
        ContratTravail ct = new ContratTravail();
        ct.setIdContrat(rs.getInt("id_contrat"));
        ct.setIdEmploye(rs.getInt("id_employe"));
        ct.setIdFiliale(rs.getInt("id_filiale"));
        ct.setIdTypeContrat(rs.getInt("id_type_contrat"));
        ct.setIdTypeIntervenant(rs.getInt("id_type_intervenant"));
        ct.setIdContratPrecedent((Integer) rs.getObject("id_contrat_precedent"));
        ct.setNumeroContrat(rs.getString("numero_contrat"));
        ct.setDateSignature(rs.getDate("date_signature"));
        ct.setDateDebut(rs.getDate("date_debut"));
        ct.setDateFin(rs.getDate("date_fin"));
        ct.setDateFinEssai(rs.getDate("date_fin_essai"));
        ct.setEssaiRenouvele(rs.getBoolean("essai_renouvele"));
        ct.setComptabiliseEffectif(rs.getBoolean("comptabilise_effectif"));
        ct.setEstActif(rs.getBoolean("est_actif"));
        ct.setIdMotifSortie((Integer) rs.getObject("id_motif_sortie"));
        ct.setCreatedAt(rs.getTimestamp("created_at"));
        ct.setUpdatedAt(rs.getTimestamp("updated_at"));
        return ct;
    }

    // Getters and Setters
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

    public Date getDateFinEssai() { return dateFinEssai; }
    public void setDateFinEssai(Date dateFinEssai) { this.dateFinEssai = dateFinEssai; }

    public boolean isEssaiRenouvele() { return essaiRenouvele; }
    public void setEssaiRenouvele(boolean essaiRenouvele) { this.essaiRenouvele = essaiRenouvele; }

    public boolean isComptabiliseEffectif() { return comptabiliseEffectif; }
    public void setComptabiliseEffectif(boolean comptabiliseEffectif) { this.comptabiliseEffectif = comptabiliseEffectif; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }

    public Integer getIdMotifSortie() { return idMotifSortie; }
    public void setIdMotifSortie(Integer idMotifSortie) { this.idMotifSortie = idMotifSortie; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
