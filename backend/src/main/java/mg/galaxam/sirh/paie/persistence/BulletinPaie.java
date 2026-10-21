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
 * En-tête de bulletin de paie avec snapshotting immuable.
 */
public class BulletinPaie extends ClassMere {

    private Integer idBulletin;
    private Integer idPeriodePaie;
    private Integer idEmploye;
    private Integer idConfigLegale;
    private Integer idSnapshotPointage;
    private Integer versionSnapshotPointage;
    private String typeBulletin = "MENSUEL";
    private Integer idTypeBulletin;
    private String referenceBulletin;

    // Snapshot Employé & Organisation
    private String matricule;
    private String nomPrenom;
    private String cinNumero;
    private Date cinDateDelivrance;
    private String cinLieuDelivrance;
    private String numeroCnaps;
    private String intitulePoste;
    private String nomDepartement;
    private String nomSite;
    private String regimeIntervenant; // PERMANENT, CONSULTANT, TEMPORAIRE, STAGIAIRE
    private String categorieProfessionnelle;
    private String modeCalculPaie = "MENSUEL";
    private Date dateEmbauche;
    private Integer ancienneteMois = 0;
    private Integer nbEnfantsCharge = 0;
    private String modePaiement = "VIREMENT";
    private Integer idModePaiement;
    private String banqueNom;
    private String banqueRib;

    // Snapshot Compteurs & Temps
    private BigDecimal heuresNormales = BigDecimal.ZERO;
    private BigDecimal heuresHs30 = BigDecimal.ZERO;
    private BigDecimal heuresHs50 = BigDecimal.ZERO;
    private BigDecimal heuresNuit = BigDecimal.ZERO;
    private BigDecimal joursOuvrables = BigDecimal.ZERO;
    private BigDecimal joursTravailles = BigDecimal.ZERO;
    private BigDecimal joursAbsence = BigDecimal.ZERO;
    private BigDecimal joursConges = BigDecimal.ZERO;
    private Integer minutesRetard = 0;

    // Totaux Financiers
    private BigDecimal salaireBase;
    private BigDecimal tauxHoraire;
    private BigDecimal totalGainsBruts = BigDecimal.ZERO;
    private BigDecimal totalCotisationsSalariales = BigDecimal.ZERO;
    private BigDecimal totalCotisationsPatronales = BigDecimal.ZERO;
    private BigDecimal salaireNetImposable = BigDecimal.ZERO;
    private BigDecimal revenuNetImposable;
    private BigDecimal baseIrsaArrondie = BigDecimal.ZERO;
    private BigDecimal irsaBrut = BigDecimal.ZERO;
    private BigDecimal reductionEnfants = BigDecimal.ZERO;
    private BigDecimal irsaNet = BigDecimal.ZERO;
    private BigDecimal retenueIsi5 = BigDecimal.ZERO;
    private BigDecimal totalIndemnitesNonImposables = BigDecimal.ZERO;
    private BigDecimal totalRetenuesAvancesAcomptes = BigDecimal.ZERO;
    private BigDecimal netAvantArrondi = BigDecimal.ZERO;
    private BigDecimal netAPayer = BigDecimal.ZERO;
    private BigDecimal coutGlobalEmployeur = BigDecimal.ZERO;

    // Cycle de Vie & Audit
    private String statut = "CALCULE"; // CALCULE, VALIDE, CLOTURE, ANNULE
    private String commentaire;
    private Timestamp dateCalcul;
    private Integer calculePar;
    private Timestamp dateValidation;
    private Integer validePar;
    private Timestamp dateCloture;
    private Integer cloturePar;
    private String motifReouverture;
    private Timestamp dateReouverture;
    private Integer rouvertPar;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Lignes de ventilation
    private List<BulletinPaieLigne> lignes = new ArrayList<>();

    public BulletinPaie() {
        this.setNomTable("BULLETIN_PAIE");
    }

    @Override
    public Integer getTuppleID() {
        return idBulletin;
    }

    public Integer getIdSnapshotPointage() { return idSnapshotPointage; }
    public void setIdSnapshotPointage(Integer value) { this.idSnapshotPointage = value; }
    public Integer getVersionSnapshotPointage() { return versionSnapshotPointage; }
    public void setVersionSnapshotPointage(Integer value) { this.versionSnapshotPointage = value; }

    @Override
    public String getAttributIDName() {
        return "id_bulletin";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO bulletin_paie (" +
                "id_periode_paie, id_employe, id_config_legale, type_bulletin, id_type_bulletin, reference_bulletin, matricule, nom_prenom, " +
                "cin_numero, cin_date_delivrance, cin_lieu_delivrance, numero_cnaps, intitule_poste, " +
                "nom_departement, nom_site, regime_intervenant, categorie_professionnelle, mode_calcul_paie, " +
                "date_embauche, anciennete_mois, nb_enfants_charge, mode_paiement, id_mode_paiement, banque_nom, banque_rib, " +
                "heures_normales, heures_hs_30, heures_hs_50, heures_nuit, jours_ouvrables, jours_travailles, " +
                "jours_absence, jours_conges, minutes_retard, salaire_base, taux_horaire, total_gains_bruts, " +
                "total_cotisations_salariales, total_cotisations_patronales, salaire_net_imposable, " +
                "base_irsa_arrondie, irsa_brut, reduction_enfants, irsa_net, retenue_isi_5, " +
                "total_indemnites_non_imposables, total_retenues_avances_acomptes, net_avant_arrondi, " +
                "net_a_payer, cout_global_employeur, statut, commentaire, date_calcul, calcule_par" +
                ") VALUES (" +
                "?, ?, ?, ?, ?, ?, ?, ?, " +
                "?, ?, ?, ?, ?, " +
                "?, ?, ?, ?, ?, " +
                "?, ?, ?, ?, ?, ?, ?, " +
                "?, ?, ?, ?, ?, ?, " +
                "?, ?, ?, ?, ?, ?, " +
                "?, ?, ?, " +
                "?, ?, ?, ?, ?, " +
                "?, ?, ?, " +
                "?, ?, ?, ?, CURRENT_TIMESTAMP, ?" +
                ") RETURNING id_bulletin";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setInt(p++, idPeriodePaie);
            ps.setInt(p++, idEmploye);
            if (idConfigLegale != null) {
                ps.setInt(p++, idConfigLegale);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }
            ps.setString(p++, typeBulletin != null ? typeBulletin : "MENSUEL");
            if (idTypeBulletin != null) {
                ps.setInt(p++, idTypeBulletin);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }
            ps.setString(p++, referenceBulletin);
            ps.setString(p++, matricule);
            ps.setString(p++, nomPrenom);

            ps.setString(p++, cinNumero);
            ps.setDate(p++, cinDateDelivrance);
            ps.setString(p++, cinLieuDelivrance);
            ps.setString(p++, numeroCnaps);
            ps.setString(p++, intitulePoste);

            ps.setString(p++, nomDepartement);
            ps.setString(p++, nomSite);
            ps.setString(p++, regimeIntervenant);
            ps.setString(p++, categorieProfessionnelle);
            ps.setString(p++, modeCalculPaie != null ? modeCalculPaie : "MENSUEL");

            ps.setDate(p++, dateEmbauche);
            ps.setInt(p++, ancienneteMois != null ? ancienneteMois : 0);
            ps.setInt(p++, nbEnfantsCharge != null ? nbEnfantsCharge : 0);
            ps.setString(p++, modePaiement != null ? modePaiement : "VIREMENT");
            if (idModePaiement != null) {
                ps.setInt(p++, idModePaiement);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }
            ps.setString(p++, banqueNom);
            ps.setString(p++, banqueRib);

            ps.setBigDecimal(p++, heuresNormales != null ? heuresNormales : BigDecimal.ZERO);
            ps.setBigDecimal(p++, heuresHs30 != null ? heuresHs30 : BigDecimal.ZERO);
            ps.setBigDecimal(p++, heuresHs50 != null ? heuresHs50 : BigDecimal.ZERO);
            ps.setBigDecimal(p++, heuresNuit != null ? heuresNuit : BigDecimal.ZERO);
            ps.setBigDecimal(p++, joursOuvrables != null ? joursOuvrables : BigDecimal.ZERO);
            ps.setBigDecimal(p++, joursTravailles != null ? joursTravailles : BigDecimal.ZERO);

            ps.setBigDecimal(p++, joursAbsence != null ? joursAbsence : BigDecimal.ZERO);
            ps.setBigDecimal(p++, joursConges != null ? joursConges : BigDecimal.ZERO);
            ps.setInt(p++, minutesRetard != null ? minutesRetard : 0);
            ps.setBigDecimal(p++, salaireBase);
            ps.setBigDecimal(p++, tauxHoraire);
            ps.setBigDecimal(p++, totalGainsBruts != null ? totalGainsBruts : BigDecimal.ZERO);

            ps.setBigDecimal(p++, totalCotisationsSalariales != null ? totalCotisationsSalariales : BigDecimal.ZERO);
            ps.setBigDecimal(p++, totalCotisationsPatronales != null ? totalCotisationsPatronales : BigDecimal.ZERO);
            ps.setBigDecimal(p++, salaireNetImposable != null ? salaireNetImposable : BigDecimal.ZERO);

            ps.setBigDecimal(p++, baseIrsaArrondie != null ? baseIrsaArrondie : BigDecimal.ZERO);
            ps.setBigDecimal(p++, irsaBrut != null ? irsaBrut : BigDecimal.ZERO);
            ps.setBigDecimal(p++, reductionEnfants != null ? reductionEnfants : BigDecimal.ZERO);
            ps.setBigDecimal(p++, irsaNet != null ? irsaNet : BigDecimal.ZERO);
            ps.setBigDecimal(p++, retenueIsi5 != null ? retenueIsi5 : BigDecimal.ZERO);

            ps.setBigDecimal(p++, totalIndemnitesNonImposables != null ? totalIndemnitesNonImposables : BigDecimal.ZERO);
            ps.setBigDecimal(p++, totalRetenuesAvancesAcomptes != null ? totalRetenuesAvancesAcomptes : BigDecimal.ZERO);
            ps.setBigDecimal(p++, netAvantArrondi != null ? netAvantArrondi : BigDecimal.ZERO);

            ps.setBigDecimal(p++, netAPayer != null ? netAPayer : BigDecimal.ZERO);
            ps.setBigDecimal(p++, coutGlobalEmployeur != null ? coutGlobalEmployeur : BigDecimal.ZERO);
            ps.setString(p++, statut != null ? statut : "CALCULE");
            ps.setString(p++, commentaire);
            if (calculePar != null) {
                ps.setInt(p++, calculePar);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idBulletin = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE bulletin_paie SET " +
                "id_periode_paie = ?, id_employe = ?, id_config_legale = ?, type_bulletin = ?, id_type_bulletin = ?, reference_bulletin = ?, matricule = ?, nom_prenom = ?, " +
                "cin_numero = ?, cin_date_delivrance = ?, cin_lieu_delivrance = ?, numero_cnaps = ?, intitule_poste = ?, " +
                "nom_departement = ?, nom_site = ?, regime_intervenant = ?, categorie_professionnelle = ?, mode_calcul_paie = ?, " +
                "date_embauche = ?, anciennete_mois = ?, nb_enfants_charge = ?, mode_paiement = ?, id_mode_paiement = ?, banque_nom = ?, banque_rib = ?, " +
                "heures_normales = ?, heures_hs_30 = ?, heures_hs_50 = ?, heures_nuit = ?, jours_ouvrables = ?, jours_travailles = ?, " +
                "jours_absence = ?, jours_conges = ?, minutes_retard = ?, salaire_base = ?, taux_horaire = ?, total_gains_bruts = ?, " +
                "total_cotisations_salariales = ?, total_cotisations_patronales = ?, salaire_net_imposable = ?, " +
                "base_irsa_arrondie = ?, irsa_brut = ?, reduction_enfants = ?, irsa_net = ?, retenue_isi_5 = ?, " +
                "total_indemnites_non_imposables = ?, total_retenues_avances_acomptes = ?, net_avant_arrondi = ?, " +
                "net_a_payer = ?, cout_global_employeur = ?, statut = ?, commentaire = ?, " +
                "date_validation = ?, valide_par = ?, date_cloture = ?, cloture_par = ?, " +
                "motif_reouverture = ?, date_reouverture = ?, rouvert_par = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_bulletin = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setInt(p++, idPeriodePaie);
            ps.setInt(p++, idEmploye);
            if (idConfigLegale != null) {
                ps.setInt(p++, idConfigLegale);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }
            ps.setString(p++, typeBulletin != null ? typeBulletin : "MENSUEL");
            if (idTypeBulletin != null) {
                ps.setInt(p++, idTypeBulletin);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }
            ps.setString(p++, referenceBulletin);
            ps.setString(p++, matricule);
            ps.setString(p++, nomPrenom);

            ps.setString(p++, cinNumero);
            ps.setDate(p++, cinDateDelivrance);
            ps.setString(p++, cinLieuDelivrance);
            ps.setString(p++, numeroCnaps);
            ps.setString(p++, intitulePoste);

            ps.setString(p++, nomDepartement);
            ps.setString(p++, nomSite);
            ps.setString(p++, regimeIntervenant);
            ps.setString(p++, categorieProfessionnelle);
            ps.setString(p++, modeCalculPaie != null ? modeCalculPaie : "MENSUEL");

            ps.setDate(p++, dateEmbauche);
            ps.setInt(p++, ancienneteMois != null ? ancienneteMois : 0);
            ps.setInt(p++, nbEnfantsCharge != null ? nbEnfantsCharge : 0);
            ps.setString(p++, modePaiement != null ? modePaiement : "VIREMENT");
            if (idModePaiement != null) {
                ps.setInt(p++, idModePaiement);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }
            ps.setString(p++, banqueNom);
            ps.setString(p++, banqueRib);

            ps.setBigDecimal(p++, heuresNormales != null ? heuresNormales : BigDecimal.ZERO);
            ps.setBigDecimal(p++, heuresHs30 != null ? heuresHs30 : BigDecimal.ZERO);
            ps.setBigDecimal(p++, heuresHs50 != null ? heuresHs50 : BigDecimal.ZERO);
            ps.setBigDecimal(p++, heuresNuit != null ? heuresNuit : BigDecimal.ZERO);
            ps.setBigDecimal(p++, joursOvrables(joursOuvrables));
            ps.setBigDecimal(p++, joursTravailles != null ? joursTravailles : BigDecimal.ZERO);

            ps.setBigDecimal(p++, joursAbsence != null ? joursAbsence : BigDecimal.ZERO);
            ps.setBigDecimal(p++, joursConges != null ? joursConges : BigDecimal.ZERO);
            ps.setInt(p++, minutesRetard != null ? minutesRetard : 0);
            ps.setBigDecimal(p++, salaireBase);
            ps.setBigDecimal(p++, tauxHoraire);
            ps.setBigDecimal(p++, totalGainsBruts != null ? totalGainsBruts : BigDecimal.ZERO);

            ps.setBigDecimal(p++, totalCotisationsSalariales != null ? totalCotisationsSalariales : BigDecimal.ZERO);
            ps.setBigDecimal(p++, totalCotisationsPatronales != null ? totalCotisationsPatronales : BigDecimal.ZERO);
            ps.setBigDecimal(p++, salaireNetImposable != null ? salaireNetImposable : BigDecimal.ZERO);

            ps.setBigDecimal(p++, baseIrsaArrondie != null ? baseIrsaArrondie : BigDecimal.ZERO);
            ps.setBigDecimal(p++, irsaBrut != null ? irsaBrut : BigDecimal.ZERO);
            ps.setBigDecimal(p++, reductionEnfants != null ? reductionEnfants : BigDecimal.ZERO);
            ps.setBigDecimal(p++, irsaNet != null ? irsaNet : BigDecimal.ZERO);
            ps.setBigDecimal(p++, retenueIsi5 != null ? retenueIsi5 : BigDecimal.ZERO);

            ps.setBigDecimal(p++, totalIndemnitesNonImposables != null ? totalIndemnitesNonImposables : BigDecimal.ZERO);
            ps.setBigDecimal(p++, totalRetenuesAvancesAcomptes != null ? totalRetenuesAvancesAcomptes : BigDecimal.ZERO);
            ps.setBigDecimal(p++, netAvantArrondi != null ? netAvantArrondi : BigDecimal.ZERO);

            ps.setBigDecimal(p++, netAPayer != null ? netAPayer : BigDecimal.ZERO);
            ps.setBigDecimal(p++, coutGlobalEmployeur != null ? coutGlobalEmployeur : BigDecimal.ZERO);
            ps.setString(p++, statut != null ? statut : "CALCULE");
            ps.setString(p++, commentaire);

            ps.setTimestamp(p++, dateValidation);
            if (validePar != null) {
                ps.setInt(p++, validePar);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }

            ps.setTimestamp(p++, dateCloture);
            if (cloturePar != null) {
                ps.setInt(p++, cloturePar);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }

            ps.setString(p++, motifReouverture);
            ps.setTimestamp(p++, dateReouverture);
            if (rouvertPar != null) {
                ps.setInt(p++, rouvertPar);
            } else {
                ps.setNull(p++, java.sql.Types.INTEGER);
            }

            ps.setInt(p++, idBulletin);
            ps.executeUpdate();
        }
        return this;
    }

    private BigDecimal joursOvrables(BigDecimal j) {
        return j != null ? j : BigDecimal.ZERO;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM bulletin_paie WHERE id_bulletin = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idBulletin);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        String sql = "SELECT * FROM bulletin_paie WHERE id_bulletin = ?";
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

    public static BulletinPaie trouverParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM bulletin_paie WHERE id_bulletin = ?";
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

    public static BulletinPaie trouverParPeriodeEtEmploye(Integer idPeriode, Integer idEmploye, Connection c) throws Exception {
        return trouverParPeriodeEtEmployeEtType(idPeriode, idEmploye, "MENSUEL", c);
    }

    public static BulletinPaie trouverParPeriodeEtEmployeEtType(Integer idPeriode, Integer idEmploye, String typeBulletin, Connection c) throws Exception {
        String sql = "SELECT * FROM bulletin_paie WHERE id_periode_paie = ? AND id_employe = ? AND type_bulletin = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPeriode);
            ps.setInt(2, idEmploye);
            ps.setString(3, typeBulletin != null ? typeBulletin : "MENSUEL");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static BulletinPaie trouverParReference(String reference, Connection c) throws Exception {
        String sql = "SELECT * FROM bulletin_paie WHERE reference_bulletin = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<BulletinPaie> listerParPeriode(Integer idPeriode, Connection c) throws Exception {
        List<BulletinPaie> list = new ArrayList<>();
        String sql = "SELECT * FROM bulletin_paie WHERE id_periode_paie = ? ORDER BY matricule ASC";
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

    /** Charge les bulletins de plusieurs périodes en une lecture, triés par période puis matricule. */
    public static List<BulletinPaie> listerParPeriodes(List<Integer> idsPeriodes, Connection c) throws Exception {
        if (idsPeriodes == null || idsPeriodes.isEmpty()) return List.of();
        List<Integer> uniqueIds = idsPeriodes.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(uniqueIds.size(), "?"));
        String sql = "SELECT * FROM bulletin_paie WHERE id_periode_paie IN (" + placeholders + ") "
                + "ORDER BY id_periode_paie ASC, matricule ASC";
        List<BulletinPaie> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int index = 0; index < uniqueIds.size(); index++) ps.setInt(index + 1, uniqueIds.get(index));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    /**
     * Règle d'arrondi monétaire malgache : arrondi à la dizaine d'Ariary la plus proche.
     * Ex: 450 123.45 Ar -> 450 120 Ar ; 450 127.00 Ar -> 450 130 Ar.
     */
    public static BigDecimal arrondirDizaineAriary(BigDecimal montant) {
        if (montant == null) return BigDecimal.ZERO;
        return montant.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).multiply(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP);
    }

    private static BulletinPaie mapResultSet(ResultSet rs) throws Exception {
        BulletinPaie b = new BulletinPaie();
        b.setIdBulletin(rs.getInt("id_bulletin"));
        b.setIdPeriodePaie(rs.getInt("id_periode_paie"));
        b.setIdEmploye(rs.getInt("id_employe"));
        try {
            int idCfg = rs.getInt("id_config_legale");
            b.setIdConfigLegale(rs.wasNull() ? null : idCfg);
            int idSnapshot = rs.getInt("id_snapshot_pointage");
            b.setIdSnapshotPointage(rs.wasNull() ? null : idSnapshot);
            int snapshotVersion = rs.getInt("version_snapshot_pointage");
            b.setVersionSnapshotPointage(rs.wasNull() ? null : snapshotVersion);
            b.setTypeBulletin(rs.getString("type_bulletin") != null ? rs.getString("type_bulletin") : "MENSUEL");
            int idTb = rs.getInt("id_type_bulletin");
            b.setIdTypeBulletin(rs.wasNull() ? null : idTb);
            int idMp = rs.getInt("id_mode_paiement");
            b.setIdModePaiement(rs.wasNull() ? null : idMp);
        } catch (Exception ignored) {}
        b.setReferenceBulletin(rs.getString("reference_bulletin"));

        b.setMatricule(rs.getString("matricule"));
        b.setNomPrenom(rs.getString("nom_prenom"));
        b.setCinNumero(rs.getString("cin_numero"));
        b.setCinDateDelivrance(rs.getDate("cin_date_delivrance"));
        b.setCinLieuDelivrance(rs.getString("cin_lieu_delivrance"));
        b.setNumeroCnaps(rs.getString("numero_cnaps"));
        b.setIntitulePoste(rs.getString("intitule_poste"));
        b.setNomDepartement(rs.getString("nom_departement"));
        b.setNomSite(rs.getString("nom_site"));
        b.setRegimeIntervenant(rs.getString("regime_intervenant"));
        b.setCategorieProfessionnelle(rs.getString("categorie_professionnelle"));
        b.setModeCalculPaie(rs.getString("mode_calcul_paie"));
        b.setDateEmbauche(rs.getDate("date_embauche"));
        b.setAncienneteMois(rs.getInt("anciennete_mois"));
        b.setNbEnfantsCharge(rs.getInt("nb_enfants_charge"));
        b.setModePaiement(rs.getString("mode_paiement"));
        b.setBanqueNom(rs.getString("banque_nom"));
        b.setBanqueRib(rs.getString("banque_rib"));

        b.setHeuresNormales(rs.getBigDecimal("heures_normales"));
        b.setHeuresHs30(rs.getBigDecimal("heures_hs_30"));
        b.setHeuresHs50(rs.getBigDecimal("heures_hs_50"));
        b.setHeuresNuit(rs.getBigDecimal("heures_nuit"));
        b.setJoursOuvrables(rs.getBigDecimal("jours_ouvrables"));
        b.setJoursTravailles(rs.getBigDecimal("jours_travailles"));
        b.setJoursAbsence(rs.getBigDecimal("jours_absence"));
        b.setJoursConges(rs.getBigDecimal("jours_conges"));
        b.setMinutesRetard(rs.getInt("minutes_retard"));

        b.setSalaireBase(rs.getBigDecimal("salaire_base"));
        b.setTauxHoraire(rs.getBigDecimal("taux_horaire"));
        b.setTotalGainsBruts(rs.getBigDecimal("total_gains_bruts"));
        b.setTotalCotisationsSalariales(rs.getBigDecimal("total_cotisations_salariales"));
        b.setTotalCotisationsPatronales(rs.getBigDecimal("total_cotisations_patronales"));
        b.setSalaireNetImposable(rs.getBigDecimal("salaire_net_imposable"));
        b.setBaseIrsaArrondie(rs.getBigDecimal("base_irsa_arrondie"));
        b.setIrsaBrut(rs.getBigDecimal("irsa_brut"));
        b.setReductionEnfants(rs.getBigDecimal("reduction_enfants"));
        b.setIrsaNet(rs.getBigDecimal("irsa_net"));
        b.setRetenueIsi5(rs.getBigDecimal("retenue_isi_5"));
        b.setTotalIndemnitesNonImposables(rs.getBigDecimal("total_indemnites_non_imposables"));
        b.setTotalRetenuesAvancesAcomptes(rs.getBigDecimal("total_retenues_avances_acomptes"));
        b.setNetAvantArrondi(rs.getBigDecimal("net_avant_arrondi"));
        b.setNetAPayer(rs.getBigDecimal("net_a_payer"));
        b.setCoutGlobalEmployeur(rs.getBigDecimal("cout_global_employeur"));

        b.setStatut(rs.getString("statut"));
        b.setCommentaire(rs.getString("commentaire"));
        b.setDateCalcul(rs.getTimestamp("date_calcul"));
        int calcPar = rs.getInt("calcule_par");
        b.setCalculePar(rs.wasNull() ? null : calcPar);
        b.setDateValidation(rs.getTimestamp("date_validation"));
        int valPar = rs.getInt("valide_par");
        b.setValidePar(rs.wasNull() ? null : valPar);
        b.setDateCloture(rs.getTimestamp("date_cloture"));
        int clotPar = rs.getInt("cloture_par");
        b.setCloturePar(rs.wasNull() ? null : clotPar);
        b.setMotifReouverture(rs.getString("motif_reouverture"));
        b.setDateReouverture(rs.getTimestamp("date_reouverture"));
        int rouvPar = rs.getInt("rouvert_par");
        b.setRouvertPar(rs.wasNull() ? null : rouvPar);
        b.setCreatedAt(rs.getTimestamp("created_at"));
        b.setUpdatedAt(rs.getTimestamp("updated_at"));

        return b;
    }

    /**
     * Procédure de réouverture d'urgence tracée d'un bulletin clôturé (réservée DAF / Direction).
     */
    public void rouvrirBulletin(String motif, Integer idUtilisateur, Connection c) throws Exception {
        if (motif == null || motif.trim().length() < 5) {
            throw new IllegalArgumentException("Un motif explicite d'au moins 5 caractères est requis pour réouvrir un bulletin clôturé");
        }
        this.statut = "ROUVERT";
        this.motifReouverture = motif.trim();
        this.dateReouverture = new Timestamp(System.currentTimeMillis());
        this.rouvertPar = idUtilisateur;
        this.update(c);
    }

    // Getters & Setters
    public Integer getIdBulletin() { return idBulletin; }
    public void setIdBulletin(Integer idBulletin) { this.idBulletin = idBulletin; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getReferenceBulletin() { return referenceBulletin; }
    public void setReferenceBulletin(String referenceBulletin) { this.referenceBulletin = referenceBulletin; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNomPrenom() { return nomPrenom; }
    public void setNomPrenom(String nomPrenom) { this.nomPrenom = nomPrenom; }

    public String getCinNumero() { return cinNumero; }
    public void setCinNumero(String cinNumero) { this.cinNumero = cinNumero; }

    public Date getCinDateDelivrance() { return cinDateDelivrance; }
    public void setCinDateDelivrance(Date cinDateDelivrance) { this.cinDateDelivrance = cinDateDelivrance; }

    public String getCinLieuDelivrance() { return cinLieuDelivrance; }
    public void setCinLieuDelivrance(String cinLieuDelivrance) { this.cinLieuDelivrance = cinLieuDelivrance; }

    public String getNumeroCnaps() { return numeroCnaps; }
    public void setNumeroCnaps(String numeroCnaps) { this.numeroCnaps = numeroCnaps; }

    public String getIntitulePoste() { return intitulePoste; }
    public void setIntitulePoste(String intitulePoste) { this.intitulePoste = intitulePoste; }

    public String getNomDepartement() { return nomDepartement; }
    public void setNomDepartement(String nomDepartement) { this.nomDepartement = nomDepartement; }

    public String getNomSite() { return nomSite; }
    public void setNomSite(String nomSite) { this.nomSite = nomSite; }

    public String getRegimeIntervenant() { return regimeIntervenant; }
    public void setRegimeIntervenant(String regimeIntervenant) { this.regimeIntervenant = regimeIntervenant; }

    public String getCategorieProfessionnelle() { return categorieProfessionnelle; }
    public void setCategorieProfessionnelle(String categorieProfessionnelle) { this.categorieProfessionnelle = categorieProfessionnelle; }

    public String getModeCalculPaie() { return modeCalculPaie; }
    public void setModeCalculPaie(String modeCalculPaie) { this.modeCalculPaie = modeCalculPaie; }

    public Date getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(Date dateEmbauche) { this.dateEmbauche = dateEmbauche; }

    public Integer getAncienneteMois() { return ancienneteMois; }
    public void setAncienneteMois(Integer ancienneteMois) { this.ancienneteMois = ancienneteMois; }

    public Integer getNbEnfantsCharge() { return nbEnfantsCharge; }
    public void setNbEnfantsCharge(Integer nbEnfantsCharge) { this.nbEnfantsCharge = nbEnfantsCharge; }

    public String getModePaiement() { return modePaiement; }
    public void setModePaiement(String modePaiement) { this.modePaiement = modePaiement; }

    public String getBanqueNom() { return banqueNom; }
    public void setBanqueNom(String banqueNom) { this.banqueNom = banqueNom; }

    public String getBanqueRib() { return banqueRib; }
    public void setBanqueRib(String banqueRib) { this.banqueRib = banqueRib; }

    public BigDecimal getHeuresNormales() { return heuresNormales; }
    public void setHeuresNormales(BigDecimal heuresNormales) { this.heuresNormales = heuresNormales; }

    public BigDecimal getHeuresHs30() { return heuresHs30; }
    public void setHeuresHs30(BigDecimal heuresHs30) { this.heuresHs30 = heuresHs30; }

    public BigDecimal getHeuresHs50() { return heuresHs50; }
    public void setHeuresHs50(BigDecimal heuresHs50) { this.heuresHs50 = heuresHs50; }

    public BigDecimal getHeuresNuit() { return heuresNuit; }
    public void setHeuresNuit(BigDecimal heuresNuit) { this.heuresNuit = heuresNuit; }

    public BigDecimal getJoursOuvrables() { return joursOuvrables; }
    public void setJoursOuvrables(BigDecimal joursOuvrables) { this.joursOuvrables = joursOuvrables; }

    public BigDecimal getJoursTravailles() { return joursTravailles; }
    public void setJoursTravailles(BigDecimal joursTravailles) { this.joursTravailles = joursTravailles; }

    public BigDecimal getJoursAbsence() { return joursAbsence; }
    public void setJoursAbsence(BigDecimal joursAbsence) { this.joursAbsence = joursAbsence; }

    public BigDecimal getJoursConges() { return joursConges; }
    public void setJoursConges(BigDecimal joursConges) { this.joursConges = joursConges; }

    public Integer getMinutesRetard() { return minutesRetard; }
    public void setMinutesRetard(Integer minutesRetard) { this.minutesRetard = minutesRetard; }

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public BigDecimal getTauxHoraire() { return tauxHoraire; }
    public void setTauxHoraire(BigDecimal tauxHoraire) { this.tauxHoraire = tauxHoraire; }

    public BigDecimal getTotalGainsBruts() { return totalGainsBruts; }
    public void setTotalGainsBruts(BigDecimal totalGainsBruts) { this.totalGainsBruts = totalGainsBruts; }

    public BigDecimal getTotalCotisationsSalariales() { return totalCotisationsSalariales; }
    public void setTotalCotisationsSalariales(BigDecimal totalCotisationsSalariales) { this.totalCotisationsSalariales = totalCotisationsSalariales; }

    public BigDecimal getTotalCotisationsPatronales() { return totalCotisationsPatronales; }
    public void setTotalCotisationsPatronales(BigDecimal totalCotisationsPatronales) { this.totalCotisationsPatronales = totalCotisationsPatronales; }

    /** @deprecated Valeur historique NETI. Utiliser {@link #getRevenuNetAvantImpot()}. */
    @Deprecated
    public BigDecimal getSalaireNetImposable() { return salaireNetImposable; }
    public void setSalaireNetImposable(BigDecimal salaireNetImposable) { this.salaireNetImposable = salaireNetImposable; }

    /** Palier social historique NETI : brut moins cotisations salariales, avant IRSA. */
    public BigDecimal getRevenuNetAvantImpot() {
        return totalGainsBruts.subtract(totalCotisationsSalariales).max(BigDecimal.ZERO);
    }

    /** Revenu fiscal avant l'arrondi de l'assiette IRSA; distingue notamment les HS exonérées. */
    public BigDecimal getRevenuNetImposable() {
        return revenuNetImposable != null ? revenuNetImposable : salaireNetImposable;
    }
    public void setRevenuNetImposable(BigDecimal revenuNetImposable) { this.revenuNetImposable = revenuNetImposable; }

    public BigDecimal getBaseIrsaArrondie() { return baseIrsaArrondie; }
    public void setBaseIrsaArrondie(BigDecimal baseIrsaArrondie) { this.baseIrsaArrondie = baseIrsaArrondie; }
    public BigDecimal getAssietteIrsa() { return baseIrsaArrondie; }

    public BigDecimal getIrsaBrut() { return irsaBrut; }
    public void setIrsaBrut(BigDecimal irsaBrut) { this.irsaBrut = irsaBrut; }
    public BigDecimal getIrsaCalcule() { return irsaBrut; }

    public BigDecimal getReductionEnfants() { return reductionEnfants; }
    public void setReductionEnfants(BigDecimal reductionEnfants) { this.reductionEnfants = reductionEnfants; }

    public BigDecimal getIrsaNet() { return irsaNet; }
    public void setIrsaNet(BigDecimal irsaNet) { this.irsaNet = irsaNet; }
    public BigDecimal getIrsaDu() { return irsaNet; }

    public BigDecimal getRetenueIsi5() { return retenueIsi5; }
    public void setRetenueIsi5(BigDecimal retenueIsi5) { this.retenueIsi5 = retenueIsi5; }

    public BigDecimal getTotalIndemnitesNonImposables() { return totalIndemnitesNonImposables; }
    public void setTotalIndemnitesNonImposables(BigDecimal totalIndemnitesNonImposables) { this.totalIndemnitesNonImposables = totalIndemnitesNonImposables; }

    public BigDecimal getTotalRetenuesAvancesAcomptes() { return totalRetenuesAvancesAcomptes; }
    public void setTotalRetenuesAvancesAcomptes(BigDecimal totalRetenuesAvancesAcomptes) { this.totalRetenuesAvancesAcomptes = totalRetenuesAvancesAcomptes; }

    public BigDecimal getNetAvantArrondi() { return netAvantArrondi; }
    public void setNetAvantArrondi(BigDecimal netAvantArrondi) { this.netAvantArrondi = netAvantArrondi; }

    public BigDecimal getNetAPayer() { return netAPayer; }
    public void setNetAPayer(BigDecimal netAPayer) { this.netAPayer = netAPayer; }

    public BigDecimal getCoutGlobalEmployeur() { return coutGlobalEmployeur; }
    public void setCoutGlobalEmployeur(BigDecimal coutGlobalEmployeur) { this.coutGlobalEmployeur = coutGlobalEmployeur; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Timestamp getDateCalcul() { return dateCalcul; }
    public void setDateCalcul(Timestamp dateCalcul) { this.dateCalcul = dateCalcul; }

    public Integer getCalculePar() { return calculePar; }
    public void setCalculePar(Integer calculePar) { this.calculePar = calculePar; }

    public Timestamp getDateValidation() { return dateValidation; }
    public void setDateValidation(Timestamp dateValidation) { this.dateValidation = dateValidation; }

    public Integer getValidePar() { return validePar; }
    public void setValidePar(Integer validePar) { this.validePar = validePar; }

    public Timestamp getDateCloture() { return dateCloture; }
    public void setDateCloture(Timestamp dateCloture) { this.dateCloture = dateCloture; }

    public Integer getCloturePar() { return cloturePar; }
    public void setCloturePar(Integer cloturePar) { this.cloturePar = cloturePar; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getMotifReouverture() { return motifReouverture; }
    public void setMotifReouverture(String motifReouverture) { this.motifReouverture = motifReouverture; }

    public Timestamp getDateReouverture() { return dateReouverture; }
    public void setDateReouverture(Timestamp dateReouverture) { this.dateReouverture = dateReouverture; }

    public Integer getRouvertPar() { return rouvertPar; }
    public void setRouvertPar(Integer rouvertPar) { this.rouvertPar = rouvertPar; }

    public Integer getIdConfigLegale() { return idConfigLegale; }
    public void setIdConfigLegale(Integer idConfigLegale) { this.idConfigLegale = idConfigLegale; }

    public String getTypeBulletin() { return typeBulletin; }
    public void setTypeBulletin(String typeBulletin) { this.typeBulletin = typeBulletin; }

    public Integer getIdTypeBulletin() { return idTypeBulletin; }
    public void setIdTypeBulletin(Integer idTypeBulletin) { this.idTypeBulletin = idTypeBulletin; }

    public Integer getIdModePaiement() { return idModePaiement; }
    public void setIdModePaiement(Integer idModePaiement) { this.idModePaiement = idModePaiement; }

    public List<BulletinPaieLigne> getLignes() { return lignes; }
    public void setLignes(List<BulletinPaieLigne> lignes) { this.lignes = lignes; }
}
