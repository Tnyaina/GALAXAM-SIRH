package mg.galaxam.sirh.paie.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant un Solde de Tout Compte (STC) & Quittance Libératoire.
 * Table BDD : paie_stc
 */
public class PaieStc extends ClassMere {

    private Integer idStc;
    private Integer idEmploye;
    private Integer idPeriodePaie;
    private Integer idBulletin;
    private String numeroRecu;
    private Date dateDepart;
    private String motifRupture;

    private BigDecimal salaireBase = BigDecimal.ZERO;
    private BigDecimal tauxHoraire = BigDecimal.ZERO;
    private Integer ancienneteMois = 0;

    // 1. Dernier salaire proratisé
    private BigDecimal joursTravaillesDernierMois = BigDecimal.ZERO;
    private BigDecimal salaireProrata = BigDecimal.ZERO;

    // 2. Congés payés (ICCP)
    private BigDecimal soldeCongesRestant = BigDecimal.ZERO;
    private BigDecimal tauxJournalierConge = BigDecimal.ZERO;
    private BigDecimal indemniteCongesPayes = BigDecimal.ZERO;

    // 3. Indemnités de rupture
    private BigDecimal moisPreavis = BigDecimal.ZERO;
    private BigDecimal indemnitePreavis = BigDecimal.ZERO;
    private BigDecimal indemniteLicenciement = BigDecimal.ZERO;
    private BigDecimal autresIndemnites = BigDecimal.ZERO;
    private String motifAutresIndemnites;

    // 4. Totaux Bruts & Fiscaux
    private BigDecimal totalBrutStc = BigDecimal.ZERO;
    private BigDecimal totalCotisationsSociales = BigDecimal.ZERO;
    private BigDecimal totalIrsa = BigDecimal.ZERO;
    private BigDecimal netImposable = BigDecimal.ZERO;

    // 5. Apurements et Déductions
    private BigDecimal soldePretsDeduit = BigDecimal.ZERO;
    private BigDecimal soldeAvancesDeduit = BigDecimal.ZERO;
    private BigDecimal autresDeductions = BigDecimal.ZERO;
    private String motifAutresDeductions;

    // 6. Net Final STC
    private BigDecimal netAPayerStc = BigDecimal.ZERO;

    // Statut et cycle de vie
    private String statut = "SIMULATION"; // SIMULATION, VALIDE, REGLE, ANNULE
    private String modeReglement = "VIREMENT";
    private Integer idModeReglement;
    private Integer idMotifSortie;
    private String commentaire;

    private Integer creePar;
    private Integer validePar;
    private Timestamp dateValidation;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public PaieStc() {
        this.setNomTable("PAIE_STC");
    }

    @Override
    public Integer getTuppleID() {
        return this.idStc;
    }

    @Override
    public String getAttributIDName() {
        return "id_stc";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        if (this.numeroRecu == null || this.numeroRecu.isBlank()) {
            LocalDate dDep = this.dateDepart != null ? this.dateDepart.toLocalDate() : null;
            this.numeroRecu = genererNumeroRecu(dDep, c);
        }

        String sql = "INSERT INTO paie_stc (" +
                "id_employe, id_periode_paie, id_bulletin, numero_recu, date_depart, motif_rupture, " +
                "salaire_base, taux_horaire, anciennete_mois, jours_travailles_dernier_mois, salaire_prorata, " +
                "solde_conges_restant, taux_journalier_conge, indemnite_conges_payes, mois_preavis, indemnite_preavis, " +
                "indemnite_licenciement, autres_indemnites, motif_autres_indemnites, total_brut_stc, " +
                "total_cotisations_sociales, total_irsa, net_imposable, solde_prets_deduit, solde_avances_deduit, " +
                "autres_deductions, motif_autres_deductions, net_a_payer_stc, statut, mode_reglement, commentaire, " +
                "cree_par, valide_par, date_validation, id_mode_reglement, id_motif_sortie) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "RETURNING id_stc";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setInt(p++, this.idEmploye);
            ps.setInt(p++, this.idPeriodePaie);
            if (this.idBulletin != null) ps.setInt(p++, this.idBulletin); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setString(p++, this.numeroRecu);
            ps.setDate(p++, this.dateDepart);
            ps.setString(p++, this.motifRupture);

            ps.setBigDecimal(p++, this.salaireBase);
            ps.setBigDecimal(p++, this.tauxHoraire);
            ps.setInt(p++, this.ancienneteMois != null ? this.ancienneteMois : 0);
            ps.setBigDecimal(p++, this.joursTravaillesDernierMois);
            ps.setBigDecimal(p++, this.salaireProrata);

            ps.setBigDecimal(p++, this.soldeCongesRestant);
            ps.setBigDecimal(p++, this.tauxJournalierConge);
            ps.setBigDecimal(p++, this.indemniteCongesPayes);
            ps.setBigDecimal(p++, this.moisPreavis);
            ps.setBigDecimal(p++, this.indemnitePreavis);

            ps.setBigDecimal(p++, this.indemniteLicenciement);
            ps.setBigDecimal(p++, this.autresIndemnites);
            ps.setString(p++, this.motifAutresIndemnites);
            ps.setBigDecimal(p++, this.totalBrutStc);

            ps.setBigDecimal(p++, this.totalCotisationsSociales);
            ps.setBigDecimal(p++, this.totalIrsa);
            ps.setBigDecimal(p++, this.netImposable);
            ps.setBigDecimal(p++, this.soldePretsDeduit);
            ps.setBigDecimal(p++, this.soldeAvancesDeduit);

            ps.setBigDecimal(p++, this.autresDeductions);
            ps.setString(p++, this.motifAutresDeductions);
            ps.setBigDecimal(p++, this.netAPayerStc);
            ps.setString(p++, this.statut != null ? this.statut : "SIMULATION");
            ps.setString(p++, this.modeReglement != null ? this.modeReglement : "VIREMENT");
            ps.setString(p++, this.commentaire);

            if (this.creePar != null) ps.setInt(p++, this.creePar); else ps.setNull(p++, java.sql.Types.INTEGER);
            if (this.validePar != null) ps.setInt(p++, this.validePar); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setTimestamp(p++, this.dateValidation);
            if (this.idModeReglement != null) ps.setInt(p++, this.idModeReglement); else ps.setNull(p++, java.sql.Types.INTEGER);
            if (this.idMotifSortie != null) ps.setInt(p++, this.idMotifSortie); else ps.setNull(p++, java.sql.Types.INTEGER);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idStc = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE paie_stc SET " +
                "id_bulletin = ?, date_depart = ?, motif_rupture = ?, salaire_base = ?, taux_horaire = ?, " +
                "anciennete_mois = ?, jours_travailles_dernier_mois = ?, salaire_prorata = ?, " +
                "solde_conges_restant = ?, taux_journalier_conge = ?, indemnite_conges_payes = ?, " +
                "mois_preavis = ?, indemnite_preavis = ?, indemnite_licenciement = ?, autres_indemnites = ?, " +
                "motif_autres_indemnites = ?, total_brut_stc = ?, total_cotisations_sociales = ?, " +
                "total_irsa = ?, net_imposable = ?, solde_prets_deduit = ?, solde_avances_deduit = ?, " +
                "autres_deductions = ?, motif_autres_deductions = ?, net_a_payer_stc = ?, " +
                "statut = ?, mode_reglement = ?, commentaire = ?, valide_par = ?, date_validation = ?, " +
                "id_mode_reglement = ?, id_motif_sortie = ?, " +
                "updated_at = CURRENT_TIMESTAMP WHERE id_stc = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            if (this.idBulletin != null) ps.setInt(p++, this.idBulletin); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setDate(p++, this.dateDepart);
            ps.setString(p++, this.motifRupture);
            ps.setBigDecimal(p++, this.salaireBase);
            ps.setBigDecimal(p++, this.tauxHoraire);

            ps.setInt(p++, this.ancienneteMois != null ? this.ancienneteMois : 0);
            ps.setBigDecimal(p++, this.joursTravaillesDernierMois);
            ps.setBigDecimal(p++, this.salaireProrata);

            ps.setBigDecimal(p++, this.soldeCongesRestant);
            ps.setBigDecimal(p++, this.tauxJournalierConge);
            ps.setBigDecimal(p++, this.indemniteCongesPayes);

            ps.setBigDecimal(p++, this.moisPreavis);
            ps.setBigDecimal(p++, this.indemnitePreavis);
            ps.setBigDecimal(p++, this.indemniteLicenciement);
            ps.setBigDecimal(p++, this.autresIndemnites);

            ps.setString(p++, this.motifAutresIndemnites);
            ps.setBigDecimal(p++, this.totalBrutStc);
            ps.setBigDecimal(p++, this.totalCotisationsSociales);

            ps.setBigDecimal(p++, this.totalIrsa);
            ps.setBigDecimal(p++, this.netImposable);
            ps.setBigDecimal(p++, this.soldePretsDeduit);
            ps.setBigDecimal(p++, this.soldeAvancesDeduit);

            ps.setBigDecimal(p++, this.autresDeductions);
            ps.setString(p++, this.motifAutresDeductions);
            ps.setBigDecimal(p++, this.netAPayerStc);

            ps.setString(p++, this.statut);
            ps.setString(p++, this.modeReglement);
            ps.setString(p++, this.commentaire);

            if (this.validePar != null) ps.setInt(p++, this.validePar); else ps.setNull(p++, java.sql.Types.INTEGER);
            ps.setTimestamp(p++, this.dateValidation);
            if (this.idModeReglement != null) ps.setInt(p++, this.idModeReglement); else ps.setNull(p++, java.sql.Types.INTEGER);
            if (this.idMotifSortie != null) ps.setInt(p++, this.idMotifSortie); else ps.setNull(p++, java.sql.Types.INTEGER);

            ps.setInt(p++, this.idStc);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM paie_stc WHERE id_stc = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, this.idStc);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return trouverParId(Integer.parseInt(id), c);
    }

    public static PaieStc trouverParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM paie_stc WHERE id_stc = ?";
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

    public static PaieStc trouverParNumeroRecu(String numeroRecu, Connection c) throws Exception {
        if (numeroRecu == null || numeroRecu.isBlank()) return null;
        String sql = "SELECT * FROM paie_stc WHERE numero_recu = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, numeroRecu.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<PaieStc> lister(Connection c) throws Exception {
        List<PaieStc> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_stc ORDER BY date_depart DESC, id_stc DESC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public static List<PaieStc> listerParEmploye(Integer idEmploye, Connection c) throws Exception {
        List<PaieStc> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_stc WHERE id_employe = ? ORDER BY date_depart DESC, id_stc DESC";
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

    public static String genererNumeroRecu(Connection c) throws Exception {
        return genererNumeroRecu(null, c);
    }

    public static String genererNumeroRecu(LocalDate dateReference, Connection c) throws Exception {
        int annee = dateReference != null ? dateReference.getYear() : LocalDate.now().getYear();
        String sqlSeq = "SELECT nextval('seq_numero_recu_stc')";
        long nextId = 1;
        try (PreparedStatement ps = c.prepareStatement(sqlSeq);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                nextId = rs.getLong(1);
            }
        }
        return String.format("STC-%d-%04d", annee, nextId);
    }

    private static PaieStc mapResultSet(ResultSet rs) throws Exception {
        PaieStc s = new PaieStc();
        s.idStc = rs.getInt("id_stc");
        s.idEmploye = rs.getInt("id_employe");
        s.idPeriodePaie = rs.getInt("id_periode_paie");
        s.idBulletin = (Integer) rs.getObject("id_bulletin");
        s.numeroRecu = rs.getString("numero_recu");
        s.dateDepart = rs.getDate("date_depart");
        s.motifRupture = rs.getString("motif_rupture");

        s.salaireBase = rs.getBigDecimal("salaire_base");
        s.tauxHoraire = rs.getBigDecimal("taux_horaire");
        s.ancienneteMois = rs.getInt("anciennete_mois");

        s.joursTravaillesDernierMois = rs.getBigDecimal("jours_travailles_dernier_mois");
        s.salaireProrata = rs.getBigDecimal("salaire_prorata");

        s.soldeCongesRestant = rs.getBigDecimal("solde_conges_restant");
        s.tauxJournalierConge = rs.getBigDecimal("taux_journalier_conge");
        s.indemniteCongesPayes = rs.getBigDecimal("indemnite_conges_payes");

        s.moisPreavis = rs.getBigDecimal("mois_preavis");
        s.indemnitePreavis = rs.getBigDecimal("indemnite_preavis");
        s.indemniteLicenciement = rs.getBigDecimal("indemnite_licenciement");
        s.autresIndemnites = rs.getBigDecimal("autres_indemnites");
        s.motifAutresIndemnites = rs.getString("motif_autres_indemnites");

        s.totalBrutStc = rs.getBigDecimal("total_brut_stc");
        s.totalCotisationsSociales = rs.getBigDecimal("total_cotisations_sociales");
        s.totalIrsa = rs.getBigDecimal("total_irsa");
        s.netImposable = rs.getBigDecimal("net_imposable");

        s.soldePretsDeduit = rs.getBigDecimal("solde_prets_deduit");
        s.soldeAvancesDeduit = rs.getBigDecimal("solde_avances_deduit");
        s.autresDeductions = rs.getBigDecimal("autres_deductions");
        s.motifAutresDeductions = rs.getString("motif_autres_deductions");

        s.netAPayerStc = rs.getBigDecimal("net_a_payer_stc");

        s.statut = rs.getString("statut");
        s.modeReglement = rs.getString("mode_reglement");
        s.idModeReglement = (Integer) rs.getObject("id_mode_reglement");
        s.idMotifSortie = (Integer) rs.getObject("id_motif_sortie");
        s.commentaire = rs.getString("commentaire");

        s.creePar = (Integer) rs.getObject("cree_par");
        s.validePar = (Integer) rs.getObject("valide_par");
        s.dateValidation = rs.getTimestamp("date_validation");
        s.createdAt = rs.getTimestamp("created_at");
        s.updatedAt = rs.getTimestamp("updated_at");

        return s;
    }

    // Getters & Setters
    public Integer getIdStc() { return idStc; }
    public void setIdStc(Integer idStc) { this.idStc = idStc; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public Integer getIdBulletin() { return idBulletin; }
    public void setIdBulletin(Integer idBulletin) { this.idBulletin = idBulletin; }

    public String getNumeroRecu() { return numeroRecu; }
    public void setNumeroRecu(String numeroRecu) { this.numeroRecu = numeroRecu; }

    public Date getDateDepart() { return dateDepart; }
    public void setDateDepart(Date dateDepart) { this.dateDepart = dateDepart; }

    public String getMotifRupture() { return motifRupture; }
    public void setMotifRupture(String motifRupture) { this.motifRupture = motifRupture; }

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public BigDecimal getTauxHoraire() { return tauxHoraire; }
    public void setTauxHoraire(BigDecimal tauxHoraire) { this.tauxHoraire = tauxHoraire; }

    public Integer getAncienneteMois() { return ancienneteMois; }
    public void setAncienneteMois(Integer ancienneteMois) { this.ancienneteMois = ancienneteMois; }

    public BigDecimal getJoursTravaillesDernierMois() { return joursTravaillesDernierMois; }
    public void setJoursTravaillesDernierMois(BigDecimal joursTravaillesDernierMois) { this.joursTravaillesDernierMois = joursTravaillesDernierMois; }

    public BigDecimal getSalaireProrata() { return salaireProrata; }
    public void setSalaireProrata(BigDecimal salaireProrata) { this.salaireProrata = salaireProrata; }

    public BigDecimal getSoldeCongesRestant() { return soldeCongesRestant; }
    public void setSoldeCongesRestant(BigDecimal soldeCongesRestant) { this.soldeCongesRestant = soldeCongesRestant; }

    public BigDecimal getTauxJournalierConge() { return tauxJournalierConge; }
    public void setTauxJournalierConge(BigDecimal tauxJournalierConge) { this.tauxJournalierConge = tauxJournalierConge; }

    public BigDecimal getIndemniteCongesPayes() { return indemniteCongesPayes; }
    public void setIndemniteCongesPayes(BigDecimal indemniteCongesPayes) { this.indemniteCongesPayes = indemniteCongesPayes; }

    public BigDecimal getMoisPreavis() { return moisPreavis; }
    public void setMoisPreavis(BigDecimal moisPreavis) { this.moisPreavis = moisPreavis; }

    public BigDecimal getIndemnitePreavis() { return indemnitePreavis; }
    public void setIndemnitePreavis(BigDecimal indemnitePreavis) { this.indemnitePreavis = indemnitePreavis; }

    public BigDecimal getIndemniteLicenciement() { return indemniteLicenciement; }
    public void setIndemniteLicenciement(BigDecimal indemniteLicenciement) { this.indemniteLicenciement = indemniteLicenciement; }

    public BigDecimal getAutresIndemnites() { return autresIndemnites; }
    public void setAutresIndemnites(BigDecimal autresIndemnites) { this.autresIndemnites = autresIndemnites; }

    public String getMotifAutresIndemnites() { return motifAutresIndemnites; }
    public void setMotifAutresIndemnites(String motifAutresIndemnites) { this.motifAutresIndemnites = motifAutresIndemnites; }

    public BigDecimal getTotalBrutStc() { return totalBrutStc; }
    public void setTotalBrutStc(BigDecimal totalBrutStc) { this.totalBrutStc = totalBrutStc; }

    public BigDecimal getTotalCotisationsSociales() { return totalCotisationsSociales; }
    public void setTotalCotisationsSociales(BigDecimal totalCotisationsSociales) { this.totalCotisationsSociales = totalCotisationsSociales; }

    public BigDecimal getTotalIrsa() { return totalIrsa; }
    public void setTotalIrsa(BigDecimal totalIrsa) { this.totalIrsa = totalIrsa; }

    public BigDecimal getNetImposable() { return netImposable; }
    public void setNetImposable(BigDecimal netImposable) { this.netImposable = netImposable; }

    public BigDecimal getSoldePretsDeduit() { return soldePretsDeduit; }
    public void setSoldePretsDeduit(BigDecimal soldePretsDeduit) { this.soldePretsDeduit = soldePretsDeduit; }

    public BigDecimal getSoldeAvancesDeduit() { return soldeAvancesDeduit; }
    public void setSoldeAvancesDeduit(BigDecimal soldeAvancesDeduit) { this.soldeAvancesDeduit = soldeAvancesDeduit; }

    public BigDecimal getAutresDeductions() { return autresDeductions; }
    public void setAutresDeductions(BigDecimal autresDeductions) { this.autresDeductions = autresDeductions; }

    public String getMotifAutresDeductions() { return motifAutresDeductions; }
    public void setMotifAutresDeductions(String motifAutresDeductions) { this.motifAutresDeductions = motifAutresDeductions; }

    public BigDecimal getNetAPayerStc() { return netAPayerStc; }
    public void setNetAPayerStc(BigDecimal netAPayerStc) { this.netAPayerStc = netAPayerStc; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getModeReglement() { return modeReglement; }
    public void setModeReglement(String modeReglement) { this.modeReglement = modeReglement; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Integer getIdModeReglement() { return idModeReglement; }
    public void setIdModeReglement(Integer idModeReglement) { this.idModeReglement = idModeReglement; }

    public Integer getIdMotifSortie() { return idMotifSortie; }
    public void setIdMotifSortie(Integer idMotifSortie) { this.idMotifSortie = idMotifSortie; }

    public Integer getCreePar() { return creePar; }
    public void setCreePar(Integer creePar) { this.creePar = creePar; }

    public Integer getValidePar() { return validePar; }
    public void setValidePar(Integer validePar) { this.validePar = validePar; }

    public Timestamp getDateValidation() { return dateValidation; }
    public void setDateValidation(Timestamp dateValidation) { this.dateValidation = dateValidation; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
