package mg.galaxam.sirh.model.paieconfig;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class RefTypeCotisation extends ClassMere {

    private Integer idTypeCotisation;
    private String code;
    private String libelle;
    private BigDecimal tauxSalarial;
    private BigDecimal tauxPatronal;
    private Date dateDebut;
    private Date dateFin;
    /** 'ENTREPRISE' (FMFP, ISI) ou 'SITE' (CNAPS, OSTIE, OMSI) */
    private String portee;
    private boolean estActif = true;

    public RefTypeCotisation() {
        this.setNomTable("REF_TYPE_COTISATION");
        this.estActif = true;
    }

    @Override
    public Integer getTuppleID() {
        return idTypeCotisation;
    }

    @Override
    public String getAttributIDName() {
        return "id_type_cotisation";
    }

    public void valider() {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code de cotisation est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé de cotisation est obligatoire.");
        }
        if (tauxSalarial == null || tauxSalarial.compareTo(BigDecimal.ZERO) < 0 || tauxSalarial.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Le taux salarial doit être compris entre 0% et 100%.");
        }
        if (tauxPatronal == null || tauxPatronal.compareTo(BigDecimal.ZERO) < 0 || tauxPatronal.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Le taux patronal doit être compris entre 0% et 100%.");
        }
        if (dateDebut == null) {
            throw new IllegalArgumentException("La date de début d'application est obligatoire.");
        }
        if (dateFin != null && dateFin.before(dateDebut)) {
            throw new IllegalArgumentException("La date de fin ne peut pas être antérieure à la date de début.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        valider();
        String sql = "INSERT INTO ref_type_cotisation (code, libelle, taux_salarial, taux_patronal, date_debut, date_fin, portee, est_actif) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_type_cotisation";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBigDecimal(3, tauxSalarial);
            ps.setBigDecimal(4, tauxPatronal);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);
            ps.setString(7, portee != null ? portee : "SITE");
            ps.setBoolean(8, estActif);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idTypeCotisation = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        valider();
        String sql = "UPDATE ref_type_cotisation SET code = ?, libelle = ?, taux_salarial = ?, taux_patronal = ?, " +
                     "date_debut = ?, date_fin = ?, portee = ?, est_actif = ? WHERE id_type_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBigDecimal(3, tauxSalarial);
            ps.setBigDecimal(4, tauxPatronal);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);
            ps.setString(7, portee != null ? portee : "SITE");
            ps.setBoolean(8, estActif);
            ps.setInt(9, idTypeCotisation);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_type_cotisation WHERE id_type_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTypeCotisation);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_type_cotisation, code, libelle, taux_salarial, taux_patronal, date_debut, date_fin, portee, est_actif FROM ref_type_cotisation WHERE id_type_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefTypeCotisation item = new RefTypeCotisation();
                    item.idTypeCotisation = rs.getInt("id_type_cotisation");
                    item.code = rs.getString("code");
                    item.libelle = rs.getString("libelle");
                    item.tauxSalarial = rs.getBigDecimal("taux_salarial");
                    item.tauxPatronal = rs.getBigDecimal("taux_patronal");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    item.portee = rs.getString("portee");
                    item.estActif = rs.getBoolean("est_actif");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<RefTypeCotisation> listAll(Connection c) throws Exception {
        String sql = "SELECT id_type_cotisation, code, libelle, taux_salarial, taux_patronal, date_debut, date_fin, portee, est_actif FROM ref_type_cotisation ORDER BY code";
        List<RefTypeCotisation> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RefTypeCotisation item = new RefTypeCotisation();
                item.idTypeCotisation = rs.getInt("id_type_cotisation");
                item.code = rs.getString("code");
                item.libelle = rs.getString("libelle");
                item.tauxSalarial = rs.getBigDecimal("taux_salarial");
                item.tauxPatronal = rs.getBigDecimal("taux_patronal");
                item.dateDebut = rs.getDate("date_debut");
                item.dateFin = rs.getDate("date_fin");
                item.portee = rs.getString("portee");
                item.estActif = rs.getBoolean("est_actif");
                list.add(item);
            }
        }
        return list;
    }

    /**
     * Retourne les cotisations actives à une date précise (résolution temporelle).
     * <p>
     * Utilisé par les déclarations historiques pour s'assurer que les taux affichés
     * correspondent aux taux en vigueur à la date de la période déclarée, et non
     * aux taux configurés à la date de consultation.
     * <p>
     * Un enregistrement est retenu si :
     *   date_debut <= :at AND (date_fin IS NULL OR date_fin >= :at) AND est_actif = true
     *
     * @param at Date d'effectivité cible (généralement date_debut de la période de paie)
     * @param c  Connexion JDBC
     */
    public static List<RefTypeCotisation> listActiveAt(java.sql.Date at, Connection c) throws Exception {
        String sql = "SELECT id_type_cotisation, code, libelle, taux_salarial, taux_patronal, date_debut, date_fin, portee, est_actif "
                + "FROM ref_type_cotisation "
                + "WHERE est_actif = true "
                + "  AND date_debut <= ? "
                + "  AND (date_fin IS NULL OR date_fin >= ?) "
                + "ORDER BY code";
        List<RefTypeCotisation> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, at);
            ps.setDate(2, at);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RefTypeCotisation item = new RefTypeCotisation();
                    item.idTypeCotisation = rs.getInt("id_type_cotisation");
                    item.code = rs.getString("code");
                    item.libelle = rs.getString("libelle");
                    item.tauxSalarial = rs.getBigDecimal("taux_salarial");
                    item.tauxPatronal = rs.getBigDecimal("taux_patronal");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    item.portee = rs.getString("portee");
                    item.estActif = rs.getBoolean("est_actif");
                    list.add(item);
                }
            }
        }
        return list;
    }

    /**
     * Désactive le type de cotisation et cascade la désactivation à toutes ses instances
     * associées dans site_cotisation au sein de la transaction portée par la connexion c.
     *
     * @param c Connexion JDBC avec transaction active
     * @return nombre d'instances de site_cotisation désactivées
     * @throws Exception en cas d'erreur de requête
     */
    public int desactiver(Connection c) throws Exception {
        if (this.idTypeCotisation == null) {
            throw new IllegalArgumentException("L'identifiant du type de cotisation est obligatoire pour la désactivation.");
        }

        this.estActif = false;

        // 1. Désactiver la cotisation elle-même
        String sqlCotisation = "UPDATE ref_type_cotisation SET est_actif = false WHERE id_type_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlCotisation)) {
            ps.setInt(1, this.idTypeCotisation);
            ps.executeUpdate();
        }

        // 2. Désactiver en cascade toutes ses instances dans site_cotisation
        return SiteCotisation.desactiverParTypeCotisation(this.idTypeCotisation, c);
    }

    /**
     * Réactive le type de cotisation dans ref_type_cotisation.
     *
     * @param c Connexion JDBC avec transaction active
     * @throws Exception en cas d'erreur de requête
     */
    public void activer(Connection c) throws Exception {
        if (this.idTypeCotisation == null) {
            throw new IllegalArgumentException("L'identifiant du type de cotisation est obligatoire pour l'activation.");
        }

        this.estActif = true;

        String sqlCotisation = "UPDATE ref_type_cotisation SET est_actif = true WHERE id_type_cotisation = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlCotisation)) {
            ps.setInt(1, this.idTypeCotisation);
            ps.executeUpdate();
        }
    }

    /**
     * Fonction transactionnelle autonome pour désactiver une cotisation et toutes ses instances rattachées.
     * Gère explicitement l'ouverture de connexion, le début de transaction, le commit et le rollback.
     *
     * @param idTypeCotisation ID de la cotisation
     * @param c Connexion JDBC
     * @return l'entité RefTypeCotisation mise à jour
     * @throws Exception en cas d'erreur
     */
    public static RefTypeCotisation desactiverTransactionnel(int idTypeCotisation, Connection c) throws Exception {
        boolean originalAutoCommit = c.getAutoCommit();
        if (originalAutoCommit) {
            c.setAutoCommit(false);
        }
        try {
            RefTypeCotisation cot = new RefTypeCotisation();
            RefTypeCotisation existing = (RefTypeCotisation) cot.rechercherParId(idTypeCotisation, c);
            if (existing == null) {
                throw new IllegalArgumentException("Type de cotisation non trouvé avec l'identifiant : " + idTypeCotisation);
            }

            existing.desactiver(c);

            if (originalAutoCommit) {
                c.commit();
            }
            return existing;
        } catch (Exception ex) {
            if (originalAutoCommit) {
                c.rollback();
            }
            throw ex;
        } finally {
            if (originalAutoCommit) {
                c.setAutoCommit(true);
            }
        }
    }

    // Getters / Setters
    public Integer getIdTypeCotisation() { return idTypeCotisation; }
    public void setIdTypeCotisation(Integer idTypeCotisation) { this.idTypeCotisation = idTypeCotisation; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public BigDecimal getTauxSalarial() { return tauxSalarial; }
    public void setTauxSalarial(BigDecimal tauxSalarial) { this.tauxSalarial = tauxSalarial; }
    public BigDecimal getTauxPatronal() { return tauxPatronal; }
    public void setTauxPatronal(BigDecimal tauxPatronal) { this.tauxPatronal = tauxPatronal; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
    public String getPortee() { return portee; }
    public void setPortee(String portee) { this.portee = portee; }
    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
}

