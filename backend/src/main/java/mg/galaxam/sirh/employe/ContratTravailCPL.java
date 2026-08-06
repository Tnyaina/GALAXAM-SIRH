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

public class ContratTravailCPL extends ClassMere {

    private Integer idContrat;
    private Integer idEmploye;
    private String employeMatricule;
    private String employeNomComplet;

    private Integer idFiliale;
    private String filialeCode;
    private String filialeRaisonSociale;

    private Integer idTypeContrat;
    private String typeContratCode;
    private String typeContratLibelle;

    private Integer idTypeIntervenant;
    private String typeIntervenantCode;
    private String typeIntervenantLibelle;

    private Integer idContratPrecedent;
    private String numeroContrat;
    private Date dateSignature;
    private Date dateDebut;
    private Date dateFin;
    private Date dateFinEssai;

    private boolean essaiRenouvele;
    private boolean comptabiliseEffectif;
    private boolean estActif;

    private Integer idMotifSortie;
    private String motifSortieCode;
    private String motifSortieLibelle;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public ContratTravailCPL() {
        this.setNomTable("V_CONTRAT_CPL");
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
    protected ClassMere inserer(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_contrat_cpl est en lecture seule.");
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_contrat_cpl est en lecture seule.");
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_contrat_cpl est en lecture seule.");
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ContratTravailCPL rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM v_contrat_cpl WHERE id_contrat = ?";
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

    public static List<ContratTravailCPL> listByEmployeId(Integer idEmploye, Connection c) throws Exception {
        List<ContratTravailCPL> list = new ArrayList<>();
        String sql = "SELECT * FROM v_contrat_cpl WHERE id_employe = ? ORDER BY date_debut DESC";
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

    public static ContratTravailCPL getContratActif(Integer idEmploye, Connection c) throws Exception {
        String sql = "SELECT * FROM v_contrat_cpl WHERE id_employe = ? AND est_actif = TRUE ORDER BY date_debut DESC LIMIT 1";
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

    public static ContratTravailCPL mapResultSet(ResultSet rs) throws SQLException {
        ContratTravailCPL c = new ContratTravailCPL();
        c.setIdContrat(rs.getInt("id_contrat"));
        c.setIdEmploye(rs.getInt("id_employe"));
        c.setEmployeMatricule(rs.getString("employe_matricule"));
        c.setEmployeNomComplet(rs.getString("employe_nom_complet"));

        c.setIdFiliale(rs.getInt("id_filiale"));
        c.setFilialeCode(rs.getString("code_filiale"));
        c.setFilialeRaisonSociale(rs.getString("filiale_raison_sociale"));

        c.setIdTypeContrat(rs.getInt("id_type_contrat"));
        c.setTypeContratCode(rs.getString("type_contrat_code"));
        c.setTypeContratLibelle(rs.getString("type_contrat_libelle"));

        c.setIdTypeIntervenant(rs.getInt("id_type_intervenant"));
        c.setTypeIntervenantCode(rs.getString("type_intervenant_code"));
        c.setTypeIntervenantLibelle(rs.getString("type_intervenant_libelle"));

        c.setIdContratPrecedent((Integer) rs.getObject("id_contrat_precedent"));
        c.setNumeroContrat(rs.getString("numero_contrat"));
        c.setDateSignature(rs.getDate("date_signature"));
        c.setDateDebut(rs.getDate("date_debut"));
        c.setDateFin(rs.getDate("date_fin"));
        c.setDateFinEssai(rs.getDate("date_fin_essai"));

        c.setEssaiRenouvele(rs.getBoolean("essai_renouvele"));
        c.setComptabiliseEffectif(rs.getBoolean("comptabilise_effectif"));
        c.setEstActif(rs.getBoolean("est_actif"));

        c.setIdMotifSortie((Integer) rs.getObject("id_motif_sortie"));
        c.setMotifSortieCode(rs.getString("motif_sortie_code"));
        c.setMotifSortieLibelle(rs.getString("motif_sortie_libelle"));

        c.setCreatedAt(rs.getTimestamp("created_at"));
        c.setUpdatedAt(rs.getTimestamp("updated_at"));
        return c;
    }

    // Getters and Setters
    public Integer getIdContrat() { return idContrat; }
    public void setIdContrat(Integer idContrat) { this.idContrat = idContrat; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getEmployeMatricule() { return employeMatricule; }
    public void setEmployeMatricule(String employeMatricule) { this.employeMatricule = employeMatricule; }

    public String getEmployeNomComplet() { return employeNomComplet; }
    public void setEmployeNomComplet(String employeNomComplet) { this.employeNomComplet = employeNomComplet; }

    public Integer getIdFiliale() { return idFiliale; }
    public void setIdFiliale(Integer idFiliale) { this.idFiliale = idFiliale; }

    public String getFilialeCode() { return filialeCode; }
    public void setFilialeCode(String filialeCode) { this.filialeCode = filialeCode; }

    public String getFilialeRaisonSociale() { return filialeRaisonSociale; }
    public void setFilialeRaisonSociale(String filialeRaisonSociale) { this.filialeRaisonSociale = filialeRaisonSociale; }

    public Integer getIdTypeContrat() { return idTypeContrat; }
    public void setIdTypeContrat(Integer idTypeContrat) { this.idTypeContrat = idTypeContrat; }

    public String getTypeContratCode() { return typeContratCode; }
    public void setTypeContratCode(String typeContratCode) { this.typeContratCode = typeContratCode; }

    public String getTypeContratLibelle() { return typeContratLibelle; }
    public void setTypeContratLibelle(String typeContratLibelle) { this.typeContratLibelle = typeContratLibelle; }

    public Integer getIdTypeIntervenant() { return idTypeIntervenant; }
    public void setIdTypeIntervenant(Integer idTypeIntervenant) { this.idTypeIntervenant = idTypeIntervenant; }

    public String getTypeIntervenantCode() { return typeIntervenantCode; }
    public void setTypeIntervenantCode(String typeIntervenantCode) { this.typeIntervenantCode = typeIntervenantCode; }

    public String getTypeIntervenantLibelle() { return typeIntervenantLibelle; }
    public void setTypeIntervenantLibelle(String typeIntervenantLibelle) { this.typeIntervenantLibelle = typeIntervenantLibelle; }

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

    public String getMotifSortieCode() { return motifSortieCode; }
    public void setMotifSortieCode(String motifSortieCode) { this.motifSortieCode = motifSortieCode; }

    public String getMotifSortieLibelle() { return motifSortieLibelle; }
    public void setMotifSortieLibelle(String motifSortieLibelle) { this.motifSortieLibelle = motifSortieLibelle; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
