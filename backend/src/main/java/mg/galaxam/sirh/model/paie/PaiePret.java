package mg.galaxam.sirh.model.paie;

import bean.ClassMere;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant un contrat de prêt d'entreprise accordé à un salarié.
 */
public class PaiePret extends ClassMere {

    private Integer idPret;
    private Integer idEmploye;
    private String referencePret;
    private BigDecimal montantTotal;
    private BigDecimal montantMensualite;
    private Integer nbEcheances;
    private Date dateAccord = new Date(System.currentTimeMillis());
    private Date dateDeblocage;
    private Date datePremiereEcheance;
    private BigDecimal capitalRembourse = BigDecimal.ZERO;
    private BigDecimal capitalRestantDu;
    private String statut = "EN_COURS"; // DEMANDE, APPROUVE, EN_COURS, SOLDE, SUSPENDU, ANNULE
    private String motif;
    private Integer accordePar;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Liste des échéances associées
    private List<PaiePretEcheance> echeances = new ArrayList<>();

    public PaiePret() {
        this.setNomTable("PAIE_PRET");
    }

    @Override
    public Integer getTuppleID() {
        return idPret;
    }

    @Override
    public String getAttributIDName() {
        return "id_pret";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        if (capitalRestantDu == null && montantTotal != null) {
            capitalRestantDu = montantTotal.subtract(capitalRembourse != null ? capitalRembourse : BigDecimal.ZERO);
        }
        String sql = "INSERT INTO paie_pret (" +
                "id_employe, reference_pret, montant_total, montant_mensualite, nb_echeances, " +
                "date_accord, date_deblocage, date_premiere_echeance, capital_rembourse, capital_restant_du, " +
                "statut, motif, accorde_par) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_pret";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setInt(p++, idEmploye);
            ps.setString(p++, referencePret);
            ps.setBigDecimal(p++, montantTotal);
            ps.setBigDecimal(p++, montantMensualite);
            ps.setInt(p++, nbEcheances);
            ps.setDate(p++, dateAccord != null ? dateAccord : new Date(System.currentTimeMillis()));
            ps.setDate(p++, dateDeblocage);
            ps.setDate(p++, datePremiereEcheance);
            ps.setBigDecimal(p++, capitalRembourse != null ? capitalRembourse : BigDecimal.ZERO);
            ps.setBigDecimal(p++, capitalRestantDu);
            ps.setString(p++, statut != null ? statut : "EN_COURS");
            ps.setString(p++, motif);
            if (accordePar != null) {
                ps.setInt(p++, accordePar);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idPret = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE paie_pret SET " +
                "id_employe = ?, reference_pret = ?, montant_total = ?, montant_mensualite = ?, nb_echeances = ?, " +
                "date_accord = ?, date_deblocage = ?, date_premiere_echeance = ?, capital_rembourse = ?, capital_restant_du = ?, " +
                "statut = ?, motif = ?, accorde_par = ?, updated_at = CURRENT_TIMESTAMP WHERE id_pret = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setInt(p++, idEmploye);
            ps.setString(p++, referencePret);
            ps.setBigDecimal(p++, montantTotal);
            ps.setBigDecimal(p++, montantMensualite);
            ps.setInt(p++, nbEcheances);
            ps.setDate(p++, dateAccord);
            ps.setDate(p++, dateDeblocage);
            ps.setDate(p++, datePremiereEcheance);
            ps.setBigDecimal(p++, capitalRembourse);
            ps.setBigDecimal(p++, capitalRestantDu);
            ps.setString(p++, statut);
            ps.setString(p++, motif);
            if (accordePar != null) {
                ps.setInt(p++, accordePar);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }
            ps.setInt(p++, idPret);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM paie_pret WHERE id_pret = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPret);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        String sql = "SELECT * FROM paie_pret WHERE id_pret = ?";
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

    public static PaiePret trouverParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM paie_pret WHERE id_pret = ?";
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

    public static PaiePret trouverParReference(String ref, Connection c) throws Exception {
        String sql = "SELECT * FROM paie_pret WHERE reference_pret = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ref);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<PaiePret> listerParEmploye(Integer idEmploye, Connection c) throws Exception {
        List<PaiePret> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_pret WHERE id_employe = ? ORDER BY date_premiere_echeance DESC";
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

    public static List<PaiePret> listerEnCoursParEmploye(Integer idEmploye, Connection c) throws Exception {
        List<PaiePret> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_pret WHERE id_employe = ? AND statut = 'EN_COURS' ORDER BY date_premiere_echeance ASC";
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

    /**
     * Calcule le montant total du capital restant dû pour l'ensemble des prêts en cours d'un salarié.
     * Utilisé pour la simulation et la vérification préalable au départ / Solde de Tout Compte.
     */
    public static BigDecimal calculerCapitalRestantTotal(Integer idEmploye, Connection c) throws Exception {
        if (idEmploye == null) return BigDecimal.ZERO;
        String sql = "SELECT COALESCE(SUM(capital_restant_du), 0) AS total_restant " +
                     "FROM paie_pret WHERE id_employe = ? AND statut = 'EN_COURS'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal tot = rs.getBigDecimal("total_restant");
                    return tot != null ? tot : BigDecimal.ZERO;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * Solde l'ensemble des prêts en cours d'un salarié lors du Solde de Tout Compte (STC).
     * Les échéances résiduelles passent en 'REMBOURSEE_ANTICIPEE' rattachées au bulletin STC,
     * et les prêts passent en statut 'SOLDE'.
     *
     * @return Le montant total du capital restant dû ainsi soldé.
     */
    public static BigDecimal solderPretsPourStc(Integer idEmploye, BigDecimal montantDeductionEffectif, Integer idBulletinStc, Connection c) throws Exception {
        if (idEmploye == null) return BigDecimal.ZERO;
        List<PaiePret> pretsEnCours = listerEnCoursParEmploye(idEmploye, c);
        BigDecimal totalImpute = BigDecimal.ZERO;
        BigDecimal montantRestantAImputer = montantDeductionEffectif != null ? montantDeductionEffectif : null;

        for (PaiePret pret : pretsEnCours) {
            BigDecimal restant = pret.getCapitalRestantDu() != null ? pret.getCapitalRestantDu() : BigDecimal.ZERO;
            if (restant.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal aImputer;
            if (montantRestantAImputer == null) {
                // Mode total sans plafond explicite
                aImputer = restant;
            } else {
                aImputer = montantRestantAImputer.min(restant);
                montantRestantAImputer = montantRestantAImputer.subtract(aImputer);
            }

            if (aImputer.compareTo(BigDecimal.ZERO) <= 0) continue;
            totalImpute = totalImpute.add(aImputer);

            boolean estSoldeTotal = aImputer.compareTo(restant) >= 0;

            if (estSoldeTotal) {
                // Marquer toutes les échéances résiduelles non échues comme remboursées par anticipation sur le STC
                String sqlEch = "UPDATE paie_pret_echeance SET " +
                        "statut = 'REMBOURSEE_ANTICIPEE', " +
                        "montant_preleve = montant_restant_echeance, " +
                        "montant_restant_echeance = 0, " +
                        "id_bulletin = ?, " +
                        "date_prelevement = CURRENT_TIMESTAMP, " +
                        "commentaire = 'Cloture et imputation sur Solde de Tout Compte (STC)' " +
                        "WHERE id_pret = ? AND statut IN ('A_ECHOIR', 'PRELEVEE_PARTIELLEMENT')";
                try (PreparedStatement psEch = c.prepareStatement(sqlEch)) {
                    if (idBulletinStc != null) {
                        psEch.setInt(1, idBulletinStc);
                    } else {
                        psEch.setNull(1, java.sql.Types.INTEGER);
                    }
                    psEch.setInt(2, pret.getIdPret());
                    psEch.executeUpdate();
                }

                pret.setCapitalRembourse(pret.getMontantTotal());
                pret.setCapitalRestantDu(BigDecimal.ZERO);
                pret.setStatut("SOLDE");
                pret.update(c);
            } else {
                // Remboursement partiel : conserver le capital restant pour recouvrement contentieux
                BigDecimal nouveauRembourse = (pret.getCapitalRembourse() != null ? pret.getCapitalRembourse() : BigDecimal.ZERO).add(aImputer);
                BigDecimal nouveauRestant = restant.subtract(aImputer);

                pret.setCapitalRembourse(nouveauRembourse);
                pret.setCapitalRestantDu(nouveauRestant);
                pret.setStatut("SUSPENDU");
                String motifActuel = pret.getMotif() != null ? pret.getMotif() : "";
                if (!motifActuel.contains("CONTENTIEUX")) {
                    pret.setMotif((motifActuel + " [CONTENTIEUX: Solde résiduel post-STC transféré au recouvrement]").trim());
                }
                pret.update(c);
            }
        }

        return totalImpute;
    }

    public static BigDecimal solderPretsPourStc(Integer idEmploye, Integer idBulletinStc, Connection c) throws Exception {
        return solderPretsPourStc(idEmploye, null, idBulletinStc, c);
    }

    /**
     * Alias explicite pour l'exigibilité et la clôture anticipée de prêt lors de la cessation de contrat.
     */
    public static BigDecimal exigerSoldePretFinContrat(Integer idEmploye, Integer idBulletinStc, Connection c) throws Exception {
        return solderPretsPourStc(idEmploye, idBulletinStc, c);
    }

    /**
     * Génère l'échéancier prévisionnel de N mensualités avec décompte du capital restant dû.
     */
    public List<PaiePretEcheance> genererEcheances() {
        if (montantTotal == null || nbEcheances == null || nbEcheances <= 0 || datePremiereEcheance == null) {
            throw new IllegalStateException("Montant total, nombre d'échéances et date de première échéance requis");
        }

        List<PaiePretEcheance> result = new ArrayList<>();
        BigDecimal mensualiteStandard = montantMensualite != null ? montantMensualite 
                : montantTotal.divide(BigDecimal.valueOf(nbEcheances), 2, RoundingMode.HALF_UP);

        BigDecimal reste = montantTotal;
        LocalDate dateCur = datePremiereEcheance.toLocalDate();

        for (int i = 1; i <= nbEcheances; i++) {
            PaiePretEcheance ech = new PaiePretEcheance();
            ech.setIdPret(this.idPret);
            ech.setNumeroEcheance(i);
            ech.setDateEcheance(Date.valueOf(dateCur));

            // Pour la dernière échéance, on ajuste exactement sur le reliquat
            BigDecimal montantEch;
            if (i == nbEcheances) {
                montantEch = reste;
            } else {
                montantEch = mensualiteStandard.min(reste);
            }
            reste = reste.subtract(montantEch).max(BigDecimal.ZERO);

            ech.setMontantEcheance(montantEch);
            ech.setCapitalRestantApres(reste);
            ech.setStatut("A_ECHOIR");

            result.add(ech);
            dateCur = dateCur.plusMonths(1);
        }

        this.echeances = result;
        return result;
    }

    /**
     * Enregistre le prêt et génère l'ensemble de ses échéances dans la base de données.
     */
    public void enregistrerAvecEcheancier(Connection c) throws Exception {
        this.save(c);
        List<PaiePretEcheance> echs = genererEcheances();
        for (PaiePretEcheance ech : echs) {
            ech.setIdPret(this.idPret);
            ech.save(c);
        }
    }

    /**
     * Enregistre le prélèvement d'une mensualité en paie et met à jour le capital restant dû.
     */
    public void enregistrerRemboursement(BigDecimal montantPreleve, Connection c) throws Exception {
        if (montantPreleve == null || montantPreleve.compareTo(BigDecimal.ZERO) <= 0) return;

        this.capitalRembourse = (this.capitalRembourse != null ? this.capitalRembourse : BigDecimal.ZERO).add(montantPreleve);
        this.capitalRestantDu = (this.capitalRestantDu != null ? this.capitalRestantDu : this.montantTotal).subtract(montantPreleve).max(BigDecimal.ZERO);

        if (this.capitalRestantDu.compareTo(BigDecimal.ZERO) == 0) {
            this.statut = "SOLDE";
        }
        this.update(c);
    }

    private static PaiePret mapResultSet(ResultSet rs) throws Exception {
        PaiePret p = new PaiePret();
        p.setIdPret(rs.getInt("id_pret"));
        p.setIdEmploye(rs.getInt("id_employe"));
        p.setReferencePret(rs.getString("reference_pret"));
        p.setMontantTotal(rs.getBigDecimal("montant_total"));
        p.setMontantMensualite(rs.getBigDecimal("montant_mensualite"));
        p.setNbEcheances(rs.getInt("nb_echeances"));
        p.setDateAccord(rs.getDate("date_accord"));
        p.setDateDeblocage(rs.getDate("date_deblocage"));
        p.setDatePremiereEcheance(rs.getDate("date_premiere_echeance"));
        p.setCapitalRembourse(rs.getBigDecimal("capital_rembourse"));
        p.setCapitalRestantDu(rs.getBigDecimal("capital_restant_du"));
        p.setStatut(rs.getString("statut"));
        p.setMotif(rs.getString("motif"));
        int accPar = rs.getInt("accorde_par");
        p.setAccordePar(rs.wasNull() ? null : accPar);
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));
        return p;
    }

    // Getters & Setters
    public Integer getIdPret() { return idPret; }
    public void setIdPret(Integer idPret) { this.idPret = idPret; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getReferencePret() { return referencePret; }
    public void setReferencePret(String referencePret) { this.referencePret = referencePret; }

    public BigDecimal getMontantTotal() { return montantTotal; }
    public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }

    public BigDecimal getMontantMensualite() { return montantMensualite; }
    public void setMontantMensualite(BigDecimal montantMensualite) { this.montantMensualite = montantMensualite; }

    public Integer getNbEcheances() { return nbEcheances; }
    public void setNbEcheances(Integer nbEcheances) { this.nbEcheances = nbEcheances; }

    public Date getDateAccord() { return dateAccord; }
    public void setDateAccord(Date dateAccord) { this.dateAccord = dateAccord; }

    public Date getDateDeblocage() { return dateDeblocage; }
    public void setDateDeblocage(Date dateDeblocage) { this.dateDeblocage = dateDeblocage; }

    public Date getDatePremiereEcheance() { return datePremiereEcheance; }
    public void setDatePremiereEcheance(Date datePremiereEcheance) { this.datePremiereEcheance = datePremiereEcheance; }

    public BigDecimal getCapitalRembourse() { return capitalRembourse; }
    public void setCapitalRembourse(BigDecimal capitalRembourse) { this.capitalRembourse = capitalRembourse; }

    public BigDecimal getCapitalRestantDu() { return capitalRestantDu; }
    public void setCapitalRestantDu(BigDecimal capitalRestantDu) { this.capitalRestantDu = capitalRestantDu; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public Integer getAccordePar() { return accordePar; }
    public void setAccordePar(Integer accordePar) { this.accordePar = accordePar; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public List<PaiePretEcheance> getEcheances() { return echeances; }
    public void setEcheances(List<PaiePretEcheance> echeances) { this.echeances = echeances; }
}
