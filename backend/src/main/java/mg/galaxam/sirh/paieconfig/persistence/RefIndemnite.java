package mg.galaxam.sirh.paieconfig.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import mg.galaxam.sirh.pointage.persistence.EmployeIndemniteDerogation;

public class RefIndemnite extends ClassMere {

    private Integer idIndemnite;
    private String code;
    private String libelle;
    private String codeRubriquePaie;
    private boolean imposableIrsa = false;
    private Boolean soumisCotisation;
    private boolean fractionnable = false;
    private boolean estActif = true;

    public RefIndemnite() {
        this.setNomTable("REF_INDEMNITE");
    }

    @Override
    public Integer getTuppleID() {
        return idIndemnite;
    }

    @Override
    public String getAttributIDName() {
        return "id_indemnite";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_indemnite (code, libelle, code_rubrique_paie, imposable_irsa, soumis_cotisation, fractionnable, est_actif) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_indemnite";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, codeRubriquePaie);
            ps.setBoolean(4, imposableIrsa);
            ps.setObject(5, soumisCotisation);
            ps.setBoolean(6, fractionnable);
            ps.setBoolean(7, estActif);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idIndemnite = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_indemnite SET code = ?, libelle = ?, code_rubrique_paie = ?, imposable_irsa = ?, soumis_cotisation = ?, fractionnable = ?, est_actif = ? " +
                     "WHERE id_indemnite = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, codeRubriquePaie);
            ps.setBoolean(4, imposableIrsa);
            ps.setObject(5, soumisCotisation);
            ps.setBoolean(6, fractionnable);
            ps.setBoolean(7, estActif);
            ps.setInt(8, idIndemnite);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_indemnite WHERE id_indemnite = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idIndemnite);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_indemnite, code, libelle, code_rubrique_paie, imposable_irsa, soumis_cotisation, fractionnable, est_actif FROM ref_indemnite WHERE id_indemnite = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefIndemnite item = new RefIndemnite();
                    item.idIndemnite = rs.getInt("id_indemnite");
                    item.code = rs.getString("code");
                    item.libelle = rs.getString("libelle");
                    item.codeRubriquePaie = rs.getString("code_rubrique_paie");
                    item.imposableIrsa = rs.getBoolean("imposable_irsa");
                    item.soumisCotisation = (Boolean) rs.getObject("soumis_cotisation");
                    item.fractionnable = rs.getBoolean("fractionnable");
                    item.estActif = rs.getBoolean("est_actif");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<RefIndemnite> listAll(Connection c) throws Exception {
        String sql = "SELECT id_indemnite, code, libelle, code_rubrique_paie, imposable_irsa, soumis_cotisation, fractionnable, est_actif FROM ref_indemnite ORDER BY code";
        List<RefIndemnite> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RefIndemnite item = new RefIndemnite();
                item.idIndemnite = rs.getInt("id_indemnite");
                item.code = rs.getString("code");
                item.libelle = rs.getString("libelle");
                item.codeRubriquePaie = rs.getString("code_rubrique_paie");
                item.imposableIrsa = rs.getBoolean("imposable_irsa");
                item.soumisCotisation = (Boolean) rs.getObject("soumis_cotisation");
                item.fractionnable = rs.getBoolean("fractionnable");
                item.estActif = rs.getBoolean("est_actif");
                list.add(item);
            }
        }
        return list;
    }

    /**
     * Désactive transactionnellement l'indemnité et cascade la désactivation :
     * 1. Désactivation de l'indemnité de référence (est_actif = false)
     * 2. Désactivation des grilles tarifaires par site (site_indemnite_config)
     * 3. Clôture des dérogations individuelles d'employés actives (employe_indemnite_derogation)
     *
     * @param c Connexion JDBC avec transaction active
     * @return tableau de 2 entiers [nbSitesDesactives, nbDerogationsCloturees]
     * @throws Exception en cas d'erreur SQL
     */
    public int[] desactiver(Connection c) throws Exception {
        return desactiver(java.time.LocalDate.now(), c);
    }

    public int[] desactiver(java.time.LocalDate dateEffet, Connection c) throws Exception {
        if (this.idIndemnite == null) {
            throw new IllegalArgumentException("L'identifiant de l'indemnité est obligatoire pour la désactivation.");
        }

        this.estActif = false;

        // 1. Désactiver l'indemnité dans ref_indemnite
        String sql = "UPDATE ref_indemnite SET est_actif = false WHERE id_indemnite = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, this.idIndemnite);
            ps.executeUpdate();
        }

        // 2. Cascade sur site_indemnite_config
        int nbSites = SiteIndemniteConfig.desactiverParIndemnite(this.idIndemnite, dateEffet, c);

        // 3. Cascade sur employe_indemnite_derogation
        int nbDerogations = EmployeIndemniteDerogation.cloturerParIndemnite(this.idIndemnite, dateEffet, c);

        return new int[]{nbSites, nbDerogations};
    }

    /**
     * Réactive l'indemnité dans ref_indemnite.
     *
     * @param c Connexion JDBC active
     * @throws Exception en cas d'erreur SQL
     */
    public void activer(Connection c) throws Exception {
        if (this.idIndemnite == null) {
            throw new IllegalArgumentException("L'identifiant de l'indemnité est obligatoire pour l'activation.");
        }

        this.estActif = true;

        String sql = "UPDATE ref_indemnite SET est_actif = true WHERE id_indemnite = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, this.idIndemnite);
            ps.executeUpdate();
        }
    }

    /**
     * Fonction transactionnelle autonome pour désactiver une indemnité avec cascade globale.
     * Gère explicitement le commit / rollback.
     *
     * @param idIndemnite ID de l'indemnité
     * @param c Connexion JDBC
     * @return l'entité RefIndemnite mise à jour
     * @throws Exception en cas d'erreur
     */
    public static RefIndemnite desactiverTransactionnel(int idIndemnite, Connection c) throws Exception {
        boolean originalAutoCommit = c.getAutoCommit();
        if (originalAutoCommit) {
            c.setAutoCommit(false);
        }
        try {
            RefIndemnite ind = new RefIndemnite();
            RefIndemnite existing = (RefIndemnite) ind.rechercherParId(idIndemnite, c);
            if (existing == null) {
                throw new IllegalArgumentException("Indemnité introuvable avec l'ID : " + idIndemnite);
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
    public Integer getIdIndemnite() { return idIndemnite; }
    public void setIdIndemnite(Integer idIndemnite) { this.idIndemnite = idIndemnite; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getCodeRubriquePaie() { return codeRubriquePaie; }
    public void setCodeRubriquePaie(String codeRubriquePaie) {
        this.codeRubriquePaie = codeRubriquePaie == null || codeRubriquePaie.isBlank()
                ? null : codeRubriquePaie.trim();
    }
    public boolean isImposableIrsa() { return imposableIrsa; }
    public void setImposableIrsa(boolean imposableIrsa) { this.imposableIrsa = imposableIrsa; }
    public Boolean getSoumisCotisation() { return soumisCotisation; }
    public void setSoumisCotisation(Boolean soumisCotisation) { this.soumisCotisation = soumisCotisation; }
    public boolean isFractionnable() { return fractionnable; }
    public void setFractionnable(boolean fractionnable) { this.fractionnable = fractionnable; }
    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
}
