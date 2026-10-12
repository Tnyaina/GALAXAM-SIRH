package mg.galaxam.sirh.paie.persistence;

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
 * Entité de gestion des mouvements de trésorerie mensuels (Acomptes du 15, Avances spéciales, CALL PERSO).
 * Les plafonds exécutés sur bulletin sont résolus dans la configuration légale datée.
 */
public class PaieAvanceAcompte extends ClassMere {

    /**
     * Ratios historiques utilisés par les méthodes utilitaires de cette entité.
     * Le moteur de paie utilise les pourcentages datés de PaieConfigLegale.
     */
    public static final BigDecimal RATIO_AVANCE_EMPLOYEUR_MAX = new BigDecimal("0.10");
    public static final BigDecimal RATIO_QUOTITE_CESSIBLE_MAX = new BigDecimal("0.3333");

    private Integer idAvance;
    private Integer idEmploye;
    private Integer idPeriodePaie;
    private Integer idTypeMouvement;
    private String typeMouvement; // ACOMPTE_15, AVANCE_SPECIALE, CALL_PERSO, NOTE_DE_FRAIS, PRIME_EXCEPTIONNELLE, RETENUE_DIVERSE
    // Donnée de calcul issue du catalogue lié, sans colonne supplémentaire.
    private String codeRubriqueCalcul;
    private String explicationCalcul;
    private String sens = "RETENUE"; // RETENUE, GAIN
    private BigDecimal montant;
    private BigDecimal quantiteSaisie;
    private BigDecimal tauxSaisi;
    private BigDecimal montantInitial;
    private BigDecimal montantImpute = BigDecimal.ZERO;
    private BigDecimal soldeRestant;
    private String provenance = "VARIABLE_DAF";
    private Date dateDemande;
    private Date dateAccord;
    private Date datePaiement;
    private String modeReglement = "VIREMENT";
    private String motif;
    private String statut = "EN_ATTENTE"; // EN_ATTENTE, APPROUVE, REJETE, IMPUTE_PAIE, ANNULE
    private Integer idBulletin;
    private Integer creePar;
    private Integer approuvePar;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp versementConfirmeAt;

    public PaieAvanceAcompte() {
        this.setNomTable("PAIE_AVANCE_ACOMPTE");
    }

    @Override
    public Integer getTuppleID() {
        return idAvance;
    }

    @Override
    public String getAttributIDName() {
        return "id_avance";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO paie_avance_acompte (" +
                "id_employe, id_periode_paie, type_mouvement, sens, montant, montant_initial, montant_impute, solde_restant, provenance, " +
                "date_demande, date_accord, date_paiement, mode_reglement, motif, " +
                "statut, id_bulletin, cree_par, approuve_par, id_type_mouvement, quantite_saisie, taux_saisi" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_avance";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setInt(p++, idEmploye);
            if (idPeriodePaie != null) ps.setInt(p++, idPeriodePaie); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setString(p++, typeMouvement);
            ps.setString(p++, sens != null ? sens : "RETENUE");
            ps.setBigDecimal(p++, montant);
            ps.setBigDecimal(p++, montantInitial != null ? montantInitial : montant);
            ps.setBigDecimal(p++, montantImpute != null ? montantImpute : BigDecimal.ZERO);
            ps.setBigDecimal(p++, soldeRestant != null ? soldeRestant : montant);
            ps.setString(p++, provenance != null ? provenance : "VARIABLE_DAF");

            ps.setDate(p++, dateDemande != null ? dateDemande : new Date(System.currentTimeMillis()));
            ps.setDate(p++, dateAccord);
            ps.setDate(p++, datePaiement);
            ps.setString(p++, modeReglement != null ? modeReglement : "VIREMENT");
            ps.setString(p++, motif);

            ps.setString(p++, statut != null ? statut : "EN_ATTENTE");
            if (idBulletin != null) ps.setInt(p++, idBulletin); else ps.setNull(p++, java.sql.Types.INTEGER);

            if (creePar != null) ps.setInt(p++, creePar); else ps.setNull(p++, java.sql.Types.INTEGER);
            if (approuvePar != null) ps.setInt(p++, approuvePar); else ps.setNull(p++, java.sql.Types.INTEGER);
            if (idTypeMouvement != null) ps.setInt(p++, idTypeMouvement); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setBigDecimal(p++,quantiteSaisie); ps.setBigDecimal(p++,tauxSaisi);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idAvance = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE paie_avance_acompte SET " +
                "id_employe = ?, id_periode_paie = ?, type_mouvement = ?, sens = ?, montant = ?, " +
                "date_demande = ?, date_accord = ?, date_paiement = ?, mode_reglement = ?, motif = ?, " +
                "statut = ?, id_bulletin = ?, cree_par = ?, approuve_par = ?, id_type_mouvement = ?, quantite_saisie = ?, taux_saisi = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_avance = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setInt(p++, idEmploye);
            if (idPeriodePaie != null) ps.setInt(p++, idPeriodePaie); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setString(p++, typeMouvement);
            ps.setString(p++, sens != null ? sens : "RETENUE");
            ps.setBigDecimal(p++, montant);

            ps.setDate(p++, dateDemande);
            ps.setDate(p++, dateAccord);
            ps.setDate(p++, datePaiement);
            ps.setString(p++, modeReglement);
            ps.setString(p++, motif);

            ps.setString(p++, statut);
            if (idBulletin != null) ps.setInt(p++, idBulletin); else ps.setNull(p++, java.sql.Types.INTEGER);

            if (creePar != null) ps.setInt(p++, creePar); else ps.setNull(p++, java.sql.Types.INTEGER);
            if (approuvePar != null) ps.setInt(p++, approuvePar); else ps.setNull(p++, java.sql.Types.INTEGER);
            if (idTypeMouvement != null) ps.setInt(p++, idTypeMouvement); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setBigDecimal(p++,quantiteSaisie); ps.setBigDecimal(p++,tauxSaisi);
            ps.setInt(p++, idAvance);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM paie_avance_acompte WHERE id_avance = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idAvance);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        String sql = "SELECT * FROM paie_avance_acompte WHERE id_avance = ?";
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

    public static PaieAvanceAcompte trouverParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM paie_avance_acompte WHERE id_avance = ?";
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

    public static List<PaieAvanceAcompte> listerParEmployeEtPeriode(Integer idEmploye, Integer idPeriode, Connection c) throws Exception {
        List<PaieAvanceAcompte> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_avance_acompte WHERE id_employe = ? AND id_periode_paie = ? ORDER BY date_demande ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    public static List<PaieAvanceAcompte> listerEnAttenteOuApprouves(Integer idPeriode, Connection c) throws Exception {
        List<PaieAvanceAcompte> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_avance_acompte WHERE id_periode_paie = ? " +
                     "AND statut IN ('EN_ATTENTE', 'APPROUVE') ORDER BY id_employe, date_demande ASC";
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

    /**
     * Contrôle utilitaire de la quotité d'un tiers du net disponible.
     */
    public static boolean isQuotiteCessibleRespectee(BigDecimal netDisponible, BigDecimal totalRetenuesProposees) {
        if (netDisponible == null || netDisponible.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (totalRetenuesProposees == null || totalRetenuesProposees.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        BigDecimal plafondCessible = netDisponible.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
        return totalRetenuesProposees.compareTo(plafondCessible) <= 0;
    }

    public static BigDecimal calculerQuotiteCessibleMax(BigDecimal netDisponible) {
        if (netDisponible == null || netDisponible.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return netDisponible.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
    }

    /**
     * Contrôle utilitaire du ratio historique de 10 % pour une avance employeur.
     */
    public static boolean isAvanceEmployeurRespectee(BigDecimal netDisponible, BigDecimal montantAvance) {
        if (netDisponible == null || netDisponible.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (montantAvance == null || montantAvance.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        BigDecimal plafondAvance = netDisponible.multiply(RATIO_AVANCE_EMPLOYEUR_MAX).setScale(2, RoundingMode.HALF_UP);
        return montantAvance.compareTo(plafondAvance) <= 0;
    }

    public static BigDecimal calculerAvanceEmployeurMax(BigDecimal netDisponible) {
        if (netDisponible == null || netDisponible.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return netDisponible.multiply(RATIO_AVANCE_EMPLOYEUR_MAX).setScale(2, RoundingMode.HALF_UP);
    }

    public static List<PaieAvanceAcompte> listerParPeriode(Integer idPeriode, Connection c) throws Exception {
        List<PaieAvanceAcompte> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_avance_acompte WHERE id_periode_paie = ? ORDER BY id_employe, date_demande ASC";
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

    private static PaieAvanceAcompte mapResultSet(ResultSet rs) throws Exception {
        PaieAvanceAcompte a = new PaieAvanceAcompte();
        a.setIdAvance(rs.getInt("id_avance"));
        a.setIdEmploye(rs.getInt("id_employe"));
        int pId = rs.getInt("id_periode_paie");
        a.setIdPeriodePaie(rs.wasNull() ? null : pId);
        int tmId = rs.getInt("id_type_mouvement");
        a.setIdTypeMouvement(rs.wasNull() ? null : tmId);
        a.setTypeMouvement(rs.getString("type_mouvement"));
        a.setSens(rs.getString("sens"));
        a.setMontant(rs.getBigDecimal("montant"));
        a.setMontantInitial(rs.getBigDecimal("montant_initial"));
        a.setMontantImpute(rs.getBigDecimal("montant_impute"));
        a.setSoldeRestant(rs.getBigDecimal("solde_restant"));
        a.setQuantiteSaisie(rs.getBigDecimal("quantite_saisie")); a.setTauxSaisi(rs.getBigDecimal("taux_saisi"));
        a.setProvenance(rs.getString("provenance"));
        a.setDateDemande(rs.getDate("date_demande"));
        a.setDateAccord(rs.getDate("date_accord"));
        a.setDatePaiement(rs.getDate("date_paiement"));
        a.setModeReglement(rs.getString("mode_reglement"));
        a.setMotif(rs.getString("motif"));
        a.setStatut(rs.getString("statut"));
        int bId = rs.getInt("id_bulletin");
        a.setIdBulletin(rs.wasNull() ? null : bId);
        int crPar = rs.getInt("cree_par");
        a.setCreePar(rs.wasNull() ? null : crPar);
        int appPar = rs.getInt("approuve_par");
        a.setApprouvePar(rs.wasNull() ? null : appPar);
        a.setCreatedAt(rs.getTimestamp("created_at"));
        a.setUpdatedAt(rs.getTimestamp("updated_at"));
        a.setVersementConfirmeAt(rs.getTimestamp("versement_confirme_at"));
        return a;
    }

    // Getters & Setters
    public Integer getIdAvance() { return idAvance; }
    public void setIdAvance(Integer idAvance) { this.idAvance = idAvance; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public Integer getIdTypeMouvement() { return idTypeMouvement; }
    public void setIdTypeMouvement(Integer idTypeMouvement) { this.idTypeMouvement = idTypeMouvement; }

    public String getTypeMouvement() { return typeMouvement; }
    public void setTypeMouvement(String typeMouvement) { this.typeMouvement = typeMouvement; }
    public String getCodeRubriqueCalcul() { return codeRubriqueCalcul; }
    public void setCodeRubriqueCalcul(String codeRubriqueCalcul) { this.codeRubriqueCalcul = codeRubriqueCalcul; }

    public String getSens() { return sens; }
    public void setSens(String sens) { this.sens = sens; }

    public BigDecimal getQuantiteSaisie() { return quantiteSaisie; }
    public void setQuantiteSaisie(BigDecimal v) { quantiteSaisie=v; }
    public BigDecimal getTauxSaisi() { return tauxSaisi; }
    public void setTauxSaisi(BigDecimal v) { tauxSaisi=v; }
    public String getExplicationCalcul() { return explicationCalcul; }
    public void setExplicationCalcul(String v) { explicationCalcul=v; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public BigDecimal getMontantInitial() { return montantInitial; }
    public void setMontantInitial(BigDecimal montantInitial) { this.montantInitial = montantInitial; }
    public BigDecimal getMontantImpute() { return montantImpute; }
    public void setMontantImpute(BigDecimal montantImpute) { this.montantImpute = montantImpute; }
    public BigDecimal getSoldeRestant() { return soldeRestant; }
    public void setSoldeRestant(BigDecimal soldeRestant) { this.soldeRestant = soldeRestant; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }

    public Date getDateDemande() { return dateDemande; }
    public void setDateDemande(Date dateDemande) { this.dateDemande = dateDemande; }

    public Date getDateAccord() { return dateAccord; }
    public void setDateAccord(Date dateAccord) { this.dateAccord = dateAccord; }

    public Date getDatePaiement() { return datePaiement; }
    public void setDatePaiement(Date datePaiement) { this.datePaiement = datePaiement; }

    public String getModeReglement() { return modeReglement; }
    public void setModeReglement(String modeReglement) { this.modeReglement = modeReglement; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Integer getIdBulletin() { return idBulletin; }
    public void setIdBulletin(Integer idBulletin) { this.idBulletin = idBulletin; }

    public Integer getCreePar() { return creePar; }
    public void setCreePar(Integer creePar) { this.creePar = creePar; }

    public Integer getApprouvePar() { return approuvePar; }
    public void setApprouvePar(Integer approuvePar) { this.approuvePar = approuvePar; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    public Timestamp getVersementConfirmeAt() { return versementConfirmeAt; }
    public void setVersementConfirmeAt(Timestamp versementConfirmeAt) { this.versementConfirmeAt = versementConfirmeAt; }
}
