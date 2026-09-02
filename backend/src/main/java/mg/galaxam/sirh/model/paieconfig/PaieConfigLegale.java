package mg.galaxam.sirh.model.paieconfig;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Exercice fiscal / millésime légal de la paie.
 * Chaque ligne = 1 version des règles fiscales et sociales en vigueur
 * (SME, plafonds CNaPS, seuils IRSA, diviseur horaire, congés).
 *
 * Le moteur de paie charge toujours la ligne active à la date de la période :
 *   WHERE date_debut <= :date AND (date_fin IS NULL OR date_fin >= :date)
 */
public class PaieConfigLegale extends ClassMere {

    private Integer idConfigLegale;
    private String codeVersion;       // ex: 'LF_2026'
    private String libelle;
    private Date dateDebut;
    private Date dateFin;             // NULL = toujours en vigueur

    // Salaire Minimum & Plafonds Sociaux
    private BigDecimal smeNational;
    private BigDecimal smeAgricole;
    private BigDecimal plafondCotisationNbSme;  // Ex: 8.0 → plafond = 8 × SME

    // Règles Fiscales IRSA
    private BigDecimal seuilExonerationIrsa;
    private BigDecimal minimumPerceptionIrsa;
    private BigDecimal deductionChargeFamille;
    private String arrondiBaseIrsa;

    // Temps de Travail
    private BigDecimal dureeHebdoLegale;
    private BigDecimal diviseurMensuelHeures;
    private BigDecimal seuilHsTranche1;
    private BigDecimal plafondHsHebdo;

    // Congés Payés
    private BigDecimal acquisitionCongeMensuelJours;
    private Integer diviseurValorisationCongeJours;

    // Champ calculé (non stocké, calculé côté SQL)
    private BigDecimal plafondCotisationMensuel;

    public PaieConfigLegale() {
        this.setNomTable("PAIE_CONFIG_LEGALE");
    }

    @Override
    public Integer getTuppleID() {
        return idConfigLegale;
    }

    @Override
    public String getAttributIDName() {
        return "id_config_legale";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO paie_config_legale (code_version, libelle, date_debut, date_fin, " +
                "sme_national, sme_agricole, plafond_cotisation_nb_sme, " +
                "seuil_exoneration_irsa, minimum_perception_irsa, deduction_charge_famille, arrondi_base_irsa, " +
                "duree_hebdo_legale, diviseur_mensuel_heures, seuil_hs_tranche1, plafond_hs_hebdo, " +
                "acquisition_conge_mensuel_jours, diviseur_valorisation_conge_jours) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id_config_legale";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeVersion);
            ps.setString(2, libelle);
            ps.setDate(3, dateDebut);
            ps.setDate(4, dateFin);
            ps.setBigDecimal(5, smeNational);
            ps.setBigDecimal(6, smeAgricole);
            ps.setBigDecimal(7, plafondCotisationNbSme);
            ps.setBigDecimal(8, seuilExonerationIrsa);
            ps.setBigDecimal(9, minimumPerceptionIrsa);
            ps.setBigDecimal(10, deductionChargeFamille);
            ps.setString(11, arrondiBaseIrsa != null ? arrondiBaseIrsa : "CENTAINE_INF");
            ps.setBigDecimal(12, dureeHebdoLegale);
            ps.setBigDecimal(13, diviseurMensuelHeures);
            ps.setBigDecimal(14, seuilHsTranche1);
            ps.setBigDecimal(15, plafondHsHebdo);
            ps.setBigDecimal(16, acquisitionCongeMensuelJours);
            ps.setInt(17, diviseurValorisationCongeJours != null ? diviseurValorisationCongeJours : 30);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) this.idConfigLegale = rs.getInt(1);
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE paie_config_legale SET libelle=?, date_debut=?, date_fin=?, " +
                "sme_national=?, sme_agricole=?, plafond_cotisation_nb_sme=?, " +
                "seuil_exoneration_irsa=?, minimum_perception_irsa=?, deduction_charge_famille=?, arrondi_base_irsa=?, " +
                "duree_hebdo_legale=?, diviseur_mensuel_heures=?, seuil_hs_tranche1=?, plafond_hs_hebdo=?, " +
                "acquisition_conge_mensuel_jours=?, diviseur_valorisation_conge_jours=?, updated_at=NOW() " +
                "WHERE id_config_legale=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, libelle);
            ps.setDate(2, dateDebut);
            ps.setDate(3, dateFin);
            ps.setBigDecimal(4, smeNational);
            ps.setBigDecimal(5, smeAgricole);
            ps.setBigDecimal(6, plafondCotisationNbSme);
            ps.setBigDecimal(7, seuilExonerationIrsa);
            ps.setBigDecimal(8, minimumPerceptionIrsa);
            ps.setBigDecimal(9, deductionChargeFamille);
            ps.setString(10, arrondiBaseIrsa);
            ps.setBigDecimal(11, dureeHebdoLegale);
            ps.setBigDecimal(12, diviseurMensuelHeures);
            ps.setBigDecimal(13, seuilHsTranche1);
            ps.setBigDecimal(14, plafondHsHebdo);
            ps.setBigDecimal(15, acquisitionCongeMensuelJours);
            ps.setInt(16, diviseurValorisationCongeJours != null ? diviseurValorisationCongeJours : 30);
            ps.setInt(17, idConfigLegale);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        throw new UnsupportedOperationException(
            "La suppression d'un exercice fiscal est interdite. Utilisez la clôture par date_fin.");
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return findById(Integer.parseInt(id), c);
    }

    public static PaieConfigLegale findById(int id, Connection c) throws Exception {
        String sql = "SELECT *, ROUND(sme_national * plafond_cotisation_nb_sme, 2) AS plafond_cotisation_mensuel " +
                     "FROM paie_config_legale WHERE id_config_legale = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public static List<PaieConfigLegale> listAll(Connection c) throws Exception {
        List<PaieConfigLegale> list = new ArrayList<>();
        String sql = "SELECT *, ROUND(sme_national * plafond_cotisation_nb_sme, 2) AS plafond_cotisation_mensuel " +
                     "FROM paie_config_legale ORDER BY date_debut DESC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    /**
     * Charge la configuration légale active pour une date donnée.
     * Utilisé par le moteur de paie à chaque calcul de bulletin.
     */
    public static PaieConfigLegale findActiveAt(Connection c, java.time.LocalDate date) throws Exception {
        String sql = "SELECT *, ROUND(sme_national * plafond_cotisation_nb_sme, 2) AS plafond_cotisation_mensuel " +
                     "FROM paie_config_legale " +
                     "WHERE date_debut <= ? AND (date_fin IS NULL OR date_fin >= ?) " +
                     "ORDER BY date_debut DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            java.sql.Date sqlDate = java.sql.Date.valueOf(date);
            ps.setDate(1, sqlDate);
            ps.setDate(2, sqlDate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /**
     * Revalorisation par date d'effet :
     * 1. Clôt l'exercice actif (date_fin = dateDebutNouveau - 1 jour)
     * 2. Insère le nouvel exercice à partir de dateDebutNouveau
     * La transaction est gérée par l'appelant (controller).
     */
    public static PaieConfigLegale revaloriser(Connection c, java.sql.Date dateDebutNouveau,
                                                PaieConfigLegale nouvConfig) throws Exception {
        // Clôturer l'exercice actif
        String closeSql = "UPDATE paie_config_legale SET date_fin = ? " +
                          "WHERE date_fin IS NULL AND date_debut < ?";
        try (PreparedStatement ps = c.prepareStatement(closeSql)) {
            ps.setDate(1, java.sql.Date.valueOf(dateDebutNouveau.toLocalDate().minusDays(1)));
            ps.setDate(2, dateDebutNouveau);
            ps.executeUpdate();
        }
        // Insérer le nouvel exercice
        nouvConfig.setDateDebut(dateDebutNouveau);
        nouvConfig.setDateFin(null);
        return (PaieConfigLegale) nouvConfig.inserer(c);
    }

    private static PaieConfigLegale mapRow(ResultSet rs) throws Exception {
        PaieConfigLegale p = new PaieConfigLegale();
        p.idConfigLegale = rs.getInt("id_config_legale");
        p.codeVersion = rs.getString("code_version");
        p.libelle = rs.getString("libelle");
        p.dateDebut = rs.getDate("date_debut");
        p.dateFin = rs.getDate("date_fin");
        p.smeNational = rs.getBigDecimal("sme_national");
        p.smeAgricole = rs.getBigDecimal("sme_agricole");
        p.plafondCotisationNbSme = rs.getBigDecimal("plafond_cotisation_nb_sme");
        p.seuilExonerationIrsa = rs.getBigDecimal("seuil_exoneration_irsa");
        p.minimumPerceptionIrsa = rs.getBigDecimal("minimum_perception_irsa");
        p.deductionChargeFamille = rs.getBigDecimal("deduction_charge_famille");
        p.arrondiBaseIrsa = rs.getString("arrondi_base_irsa");
        p.dureeHebdoLegale = rs.getBigDecimal("duree_hebdo_legale");
        p.diviseurMensuelHeures = rs.getBigDecimal("diviseur_mensuel_heures");
        p.seuilHsTranche1 = rs.getBigDecimal("seuil_hs_tranche1");
        p.plafondHsHebdo = rs.getBigDecimal("plafond_hs_hebdo");
        p.acquisitionCongeMensuelJours = rs.getBigDecimal("acquisition_conge_mensuel_jours");
        p.diviseurValorisationCongeJours = rs.getInt("diviseur_valorisation_conge_jours");
        try {
            p.plafondCotisationMensuel = rs.getBigDecimal("plafond_cotisation_mensuel");
        } catch (Exception ignored) {}
        return p;
    }

    // Getters & Setters
    public Integer getIdConfigLegale() { return idConfigLegale; }
    public String getCodeVersion() { return codeVersion; }
    public void setCodeVersion(String v) { this.codeVersion = v; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String v) { this.libelle = v; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date v) { this.dateDebut = v; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date v) { this.dateFin = v; }
    public BigDecimal getSmeNational() { return smeNational; }
    public void setSmeNational(BigDecimal v) { this.smeNational = v; }
    public BigDecimal getSmeAgricole() { return smeAgricole; }
    public void setSmeAgricole(BigDecimal v) { this.smeAgricole = v; }
    public BigDecimal getPlafondCotisationNbSme() { return plafondCotisationNbSme; }
    public void setPlafondCotisationNbSme(BigDecimal v) { this.plafondCotisationNbSme = v; }
    public BigDecimal getSeuilExonerationIrsa() { return seuilExonerationIrsa; }
    public void setSeuilExonerationIrsa(BigDecimal v) { this.seuilExonerationIrsa = v; }
    public BigDecimal getMinimumPerceptionIrsa() { return minimumPerceptionIrsa; }
    public void setMinimumPerceptionIrsa(BigDecimal v) { this.minimumPerceptionIrsa = v; }
    public BigDecimal getDeductionChargeFamille() { return deductionChargeFamille; }
    public void setDeductionChargeFamille(BigDecimal v) { this.deductionChargeFamille = v; }
    public String getArrondiBaseIrsa() { return arrondiBaseIrsa; }
    public void setArrondiBaseIrsa(String v) { this.arrondiBaseIrsa = v; }
    public BigDecimal getDureeHebdoLegale() { return dureeHebdoLegale; }
    public void setDureeHebdoLegale(BigDecimal v) { this.dureeHebdoLegale = v; }
    public BigDecimal getDiviseurMensuelHeures() { return diviseurMensuelHeures; }
    public void setDiviseurMensuelHeures(BigDecimal v) { this.diviseurMensuelHeures = v; }
    public BigDecimal getSeuilHsTranche1() { return seuilHsTranche1; }
    public void setSeuilHsTranche1(BigDecimal v) { this.seuilHsTranche1 = v; }
    public BigDecimal getPlafondHsHebdo() { return plafondHsHebdo; }
    public void setPlafondHsHebdo(BigDecimal v) { this.plafondHsHebdo = v; }
    public BigDecimal getAcquisitionCongeMensuelJours() { return acquisitionCongeMensuelJours; }
    public void setAcquisitionCongeMensuelJours(BigDecimal v) { this.acquisitionCongeMensuelJours = v; }
    public Integer getDiviseurValorisationCongeJours() { return diviseurValorisationCongeJours; }
    public void setDiviseurValorisationCongeJours(Integer v) { this.diviseurValorisationCongeJours = v; }
    public BigDecimal getPlafondCotisationMensuel() { return plafondCotisationMensuel; }
}
