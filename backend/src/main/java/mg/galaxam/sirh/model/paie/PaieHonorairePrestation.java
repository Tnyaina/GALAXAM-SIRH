package mg.galaxam.sirh.model.paie;

import bean.ClassMere;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant une prestation externe ou honoraire d'un intervenant / consultant (Régime ISI 5%).
 * Isole juridiquement les honoraires des consultants du registre des salariés pour éviter tout risque de requalification.
 */
public class PaieHonorairePrestation extends ClassMere {

    private Integer idPrestation;
    private Integer idPeriodePaie;
    private Integer idIntervenant;
    private String nomComplet;
    private String cinOuNif;
    private String intitulePrestation;
    private String natureService = "PRESTATIONS DE SERVICES";
    private Date datePrestation;
    private Date datePaiement;
    private BigDecimal montantBrut;
    private BigDecimal tauxIsi;
    private BigDecimal montantRetenueIsi = BigDecimal.ZERO;
    private BigDecimal montantNetPaye = BigDecimal.ZERO;
    private String adresse;
    private String commune = "ANTANANARIVO RENIVOHITRA C.U";
    private String region = "ANALAMANGA";
    private String district = "ANTANANARIVO RENIVOHITRA";
    private String province = "ANTANANARIVO";
    private String referenceFacture;
    private String statut = "VALIDE"; // BROUILLON, VALIDE, PAYE, ANNULE
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public PaieHonorairePrestation() {
        this.setNomTable("PAIE_HONORAIRE_PRESTATION");
    }

    @Override
    public Integer getTuppleID() {
        return idPrestation;
    }

    @Override
    public String getAttributIDName() {
        return "id_prestation";
    }

    /**
     * Calcule automatiquement la retenue ISI (5%) et le net à payer.
     */
    public void calculerMontants() {
        if (tauxIsi == null) throw new IllegalStateException("TAX_CONFIGURATION_MISSING: ISI");
        if (montantBrut != null) {
            this.montantRetenueIsi = montantBrut.multiply(tauxIsi).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            this.montantNetPaye = montantBrut.subtract(this.montantRetenueIsi);
        } else {
            this.montantRetenueIsi = BigDecimal.ZERO;
            this.montantNetPaye = BigDecimal.ZERO;
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        calculerMontants();
        String sql = "INSERT INTO paie_honoraire_prestation (" +
                "id_periode_paie, id_intervenant, nom_complet, cin_ou_nif, intitule_prestation, " +
                "nature_service, date_prestation, date_paiement, montant_brut, taux_isi, " +
                "montant_retenue_isi, montant_net_paye, adresse, commune, region, " +
                "district, province, reference_facture, statut) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_prestation";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            if (idPeriodePaie != null) ps.setInt(p++, idPeriodePaie); else ps.setNull(p++, java.sql.Types.INTEGER);
            if (idIntervenant != null) ps.setInt(p++, idIntervenant); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setString(p++, nomComplet);
            ps.setString(p++, cinOuNif);
            ps.setString(p++, intitulePrestation);
            ps.setString(p++, natureService);
            ps.setDate(p++, datePrestation != null ? datePrestation : new Date(System.currentTimeMillis()));
            ps.setDate(p++, datePaiement);
            ps.setBigDecimal(p++, montantBrut != null ? montantBrut : BigDecimal.ZERO);
            if (tauxIsi == null) throw new IllegalStateException("TAX_CONFIGURATION_MISSING: ISI");
            ps.setBigDecimal(p++, tauxIsi);
            ps.setBigDecimal(p++, montantRetenueIsi != null ? montantRetenueIsi : BigDecimal.ZERO);
            ps.setBigDecimal(p++, montantNetPaye != null ? montantNetPaye : BigDecimal.ZERO);
            ps.setString(p++, adresse);
            ps.setString(p++, commune);
            ps.setString(p++, region);
            ps.setString(p++, district);
            ps.setString(p++, province);
            ps.setString(p++, referenceFacture);
            ps.setString(p++, statut != null ? statut : "VALIDE");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idPrestation = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        calculerMontants();
        String sql = "UPDATE paie_honoraire_prestation SET " +
                "id_periode_paie = ?, id_intervenant = ?, nom_complet = ?, cin_ou_nif = ?, intitule_prestation = ?, " +
                "nature_service = ?, date_prestation = ?, date_paiement = ?, montant_brut = ?, taux_isi = ?, " +
                "montant_retenue_isi = ?, montant_net_paye = ?, adresse = ?, commune = ?, region = ?, " +
                "district = ?, province = ?, reference_facture = ?, statut = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_prestation = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            if (idPeriodePaie != null) ps.setInt(p++, idPeriodePaie); else ps.setNull(p++, java.sql.Types.INTEGER);
            if (idIntervenant != null) ps.setInt(p++, idIntervenant); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setString(p++, nomComplet);
            ps.setString(p++, cinOuNif);
            ps.setString(p++, intitulePrestation);
            ps.setString(p++, natureService);
            ps.setDate(p++, datePrestation);
            ps.setDate(p++, datePaiement);
            ps.setBigDecimal(p++, montantBrut);
            ps.setBigDecimal(p++, tauxIsi);
            ps.setBigDecimal(p++, montantRetenueIsi);
            ps.setBigDecimal(p++, montantNetPaye);
            ps.setString(p++, adresse);
            ps.setString(p++, commune);
            ps.setString(p++, region);
            ps.setString(p++, district);
            ps.setString(p++, province);
            ps.setString(p++, referenceFacture);
            ps.setString(p++, statut);
            ps.setInt(p++, idPrestation);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM paie_honoraire_prestation WHERE id_prestation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPrestation);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        return trouverParId(Integer.parseInt(id), c);
    }

    public static PaieHonorairePrestation trouverParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM paie_honoraire_prestation WHERE id_prestation = ?";
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

    public static List<PaieHonorairePrestation> listerParPeriode(Integer idPeriode, Connection c) throws Exception {
        List<PaieHonorairePrestation> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_honoraire_prestation WHERE id_periode_paie = ? ORDER BY date_prestation ASC, nom_complet ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    public static List<PaieHonorairePrestation> listerParIntervenant(Integer idIntervenant, Connection c) throws Exception {
        List<PaieHonorairePrestation> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_honoraire_prestation WHERE id_intervenant = ? ORDER BY date_prestation DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idIntervenant);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    private static PaieHonorairePrestation mapResultSet(ResultSet rs) throws Exception {
        PaieHonorairePrestation h = new PaieHonorairePrestation();
        h.setIdPrestation(rs.getInt("id_prestation"));
        int perId = rs.getInt("id_periode_paie");
        h.setIdPeriodePaie(rs.wasNull() ? null : perId);
        int intId = rs.getInt("id_intervenant");
        h.setIdIntervenant(rs.wasNull() ? null : intId);
        h.setNomComplet(rs.getString("nom_complet"));
        h.setCinOuNif(rs.getString("cin_ou_nif"));
        h.setIntitulePrestation(rs.getString("intitule_prestation"));
        h.setNatureService(rs.getString("nature_service"));
        h.setDatePrestation(rs.getDate("date_prestation"));
        h.setDatePaiement(rs.getDate("date_paiement"));
        h.setMontantBrut(rs.getBigDecimal("montant_brut"));
        h.setTauxIsi(rs.getBigDecimal("taux_isi"));
        h.setMontantRetenueIsi(rs.getBigDecimal("montant_retenue_isi"));
        h.setMontantNetPaye(rs.getBigDecimal("montant_net_paye"));
        h.setAdresse(rs.getString("adresse"));
        h.setCommune(rs.getString("commune"));
        h.setRegion(rs.getString("region"));
        h.setDistrict(rs.getString("district"));
        h.setProvince(rs.getString("province"));
        h.setReferenceFacture(rs.getString("reference_facture"));
        h.setStatut(rs.getString("statut"));
        h.setCreatedAt(rs.getTimestamp("created_at"));
        h.setUpdatedAt(rs.getTimestamp("updated_at"));
        return h;
    }

    // Getters & Setters
    public Integer getIdPrestation() { return idPrestation; }
    public void setIdPrestation(Integer idPrestation) { this.idPrestation = idPrestation; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public Integer getIdIntervenant() { return idIntervenant; }
    public void setIdIntervenant(Integer idIntervenant) { this.idIntervenant = idIntervenant; }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public String getCinOuNif() { return cinOuNif; }
    public void setCinOuNif(String cinOuNif) { this.cinOuNif = cinOuNif; }

    public String getIntitulePrestation() { return intitulePrestation; }
    public void setIntitulePrestation(String intitulePrestation) { this.intitulePrestation = intitulePrestation; }

    public String getNatureService() { return natureService; }
    public void setNatureService(String natureService) { this.natureService = natureService; }

    public Date getDatePrestation() { return datePrestation; }
    public void setDatePrestation(Date datePrestation) { this.datePrestation = datePrestation; }

    public Date getDatePaiement() { return datePaiement; }
    public void setDatePaiement(Date datePaiement) { this.datePaiement = datePaiement; }

    public BigDecimal getMontantBrut() { return montantBrut; }
    public void setMontantBrut(BigDecimal montantBrut) { this.montantBrut = montantBrut; }

    public BigDecimal getTauxIsi() { return tauxIsi; }
    public void setTauxIsi(BigDecimal tauxIsi) { this.tauxIsi = tauxIsi; }

    public BigDecimal getMontantRetenueIsi() { return montantRetenueIsi; }
    public void setMontantRetenueIsi(BigDecimal montantRetenueIsi) { this.montantRetenueIsi = montantRetenueIsi; }

    public BigDecimal getMontantNetPaye() { return montantNetPaye; }
    public void setMontantNetPaye(BigDecimal montantNetPaye) { this.montantNetPaye = montantNetPaye; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public String getCommune() { return commune; }
    public void setCommune(String commune) { this.commune = commune; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getReferenceFacture() { return referenceFacture; }
    public void setReferenceFacture(String referenceFacture) { this.referenceFacture = referenceFacture; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
