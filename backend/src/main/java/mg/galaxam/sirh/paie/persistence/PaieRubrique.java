package mg.galaxam.sirh.paie.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant une rubrique du catalogue de paie.
 */
public class PaieRubrique extends ClassMere {

    private Integer idRubrique;
    private String code;
    private String libelle;
    private String categorie;
    private String typeValeur = "MONTANT";
    private String sens;
    private boolean soumisCnaps = true;
    private boolean soumisSante = true;
    private boolean soumisIrsa = true;
    private boolean estFixe = false;
    private Integer ordreAffichage = 10;
    private boolean estActif = true;
    private String description;
    private String referenceReglementaire;
    private LocalDate dateDebutEffet;
    private String etapeCalcul;
    private String methodeCalcul = "MONTANT";
    private String politiqueArrondi = "CENTIME";
    private Boolean soumisFmfp;
    private LocalDate dateFinEffet;
    private Integer idRubriqueOrigine;
    private String conditionApplication = "TOUS";
    private Timestamp createdAt;

    public PaieRubrique() {
        this.setNomTable("PAIE_RUBRIQUE");
    }

    @Override
    public Integer getTuppleID() {
        return idRubrique;
    }

    @Override
    public String getAttributIDName() {
        return "id_rubrique";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO paie_rubrique (code, libelle, categorie, type_valeur, sens, " +
                     "soumis_cnaps, soumis_sante, soumis_irsa, est_fixe, ordre_affichage, est_actif, description, reference_reglementaire, date_debut_effet, etape_calcul, date_fin_effet, id_rubrique_origine, condition_application, methode_calcul, politique_arrondi, soumis_fmfp) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_rubrique";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, categorie);
            ps.setString(4, typeValeur);
            ps.setString(5, sens);
            ps.setBoolean(6, soumisCnaps);
            ps.setBoolean(7, soumisSante);
            ps.setBoolean(8, soumisIrsa);
            ps.setBoolean(9, estFixe);
            ps.setInt(10, ordreAffichage != null ? ordreAffichage : 10);
            ps.setBoolean(11, estActif);
            ps.setString(12, description);
            ps.setString(13, referenceReglementaire);
            ps.setDate(14, dateDebutEffet == null ? null : Date.valueOf(dateDebutEffet));
            ps.setString(15, etapeCalcul);
            ps.setDate(16, dateFinEffet == null ? null : Date.valueOf(dateFinEffet));
            ps.setObject(17, idRubriqueOrigine);
            ps.setString(18, conditionApplication);
            ps.setString(19,methodeCalcul); ps.setString(20,politiqueArrondi); ps.setObject(21,soumisFmfp);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idRubrique = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE paie_rubrique SET code = ?, libelle = ?, categorie = ?, type_valeur = ?, sens = ?, " +
                     "soumis_cnaps = ?, soumis_sante = ?, soumis_irsa = ?, est_fixe = ?, ordre_affichage = ?, " +
                     "est_actif = ?, description = ?, reference_reglementaire = ?, date_debut_effet = ?, etape_calcul = ?, date_fin_effet = ?, id_rubrique_origine = ?, condition_application = ?, methode_calcul = ?, politique_arrondi = ?, soumis_fmfp = ? WHERE id_rubrique = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, categorie);
            ps.setString(4, typeValeur);
            ps.setString(5, sens);
            ps.setBoolean(6, soumisCnaps);
            ps.setBoolean(7, soumisSante);
            ps.setBoolean(8, soumisIrsa);
            ps.setBoolean(9, estFixe);
            ps.setInt(10, ordreAffichage != null ? ordreAffichage : 10);
            ps.setBoolean(11, estActif);
            ps.setString(12, description);
            ps.setString(13, referenceReglementaire);
            ps.setDate(14, dateDebutEffet == null ? null : Date.valueOf(dateDebutEffet));
            ps.setString(15, etapeCalcul);
            ps.setDate(16, dateFinEffet == null ? null : Date.valueOf(dateFinEffet));
            ps.setObject(17, idRubriqueOrigine);
            ps.setString(18, conditionApplication);
            ps.setString(19,methodeCalcul); ps.setString(20,politiqueArrondi); ps.setObject(21,soumisFmfp);
            ps.setInt(22, idRubrique);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM paie_rubrique WHERE id_rubrique = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRubrique);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        return trouverParId(Integer.parseInt(id), c);
    }

    public static PaieRubrique trouverParId(int id, Connection c) throws Exception {
        String sql = "SELECT * FROM paie_rubrique WHERE id_rubrique = ?";
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

    public static PaieRubrique trouverParCode(String code, Connection c) throws Exception {
        String sql = "SELECT * FROM paie_rubrique WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<PaieRubrique> listerActifs(Connection c) throws Exception {
        List<PaieRubrique> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_rubrique WHERE est_actif = TRUE ORDER BY ordre_affichage ASC, code ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public static List<PaieRubrique> listerTous(Connection c) throws Exception {
        List<PaieRubrique> list = new ArrayList<>();
        String sql = "SELECT * FROM paie_rubrique ORDER BY ordre_affichage ASC, code ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static PaieRubrique mapResultSet(ResultSet rs) throws Exception {
        PaieRubrique r = new PaieRubrique();
        r.setIdRubrique(rs.getInt("id_rubrique"));
        r.setCode(rs.getString("code"));
        r.setLibelle(rs.getString("libelle"));
        r.setCategorie(rs.getString("categorie"));
        r.setTypeValeur(rs.getString("type_valeur"));
        r.setSens(rs.getString("sens"));
        r.setSoumisCnaps(rs.getBoolean("soumis_cnaps"));
        r.setSoumisSante(rs.getBoolean("soumis_sante"));
        r.setSoumisIrsa(rs.getBoolean("soumis_irsa"));
        r.setEstFixe(rs.getBoolean("est_fixe"));
        r.setOrdreAffichage(rs.getInt("ordre_affichage"));
        r.setEstActif(rs.getBoolean("est_actif"));
        r.setDescription(rs.getString("description"));
        r.setReferenceReglementaire(rs.getString("reference_reglementaire"));
        Date dateEffet = rs.getDate("date_debut_effet");
        r.setDateDebutEffet(dateEffet == null ? null : dateEffet.toLocalDate());
        r.setEtapeCalcul(rs.getString("etape_calcul"));
        r.setMethodeCalcul(rs.getString("methode_calcul")); r.setPolitiqueArrondi(rs.getString("politique_arrondi"));
        r.setSoumisFmfp((Boolean)rs.getObject("soumis_fmfp"));
        Date fin = rs.getDate("date_fin_effet");
        r.setDateFinEffet(fin == null ? null : fin.toLocalDate());
        r.setIdRubriqueOrigine((Integer) rs.getObject("id_rubrique_origine"));
        r.setConditionApplication(rs.getString("condition_application"));
        r.setCreatedAt(rs.getTimestamp("created_at"));
        return r;
    }

    // Getters & Setters
    public Integer getIdRubrique() { return idRubrique; }
    public void setIdRubrique(Integer idRubrique) { this.idRubrique = idRubrique; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public String getTypeValeur() { return typeValeur; }
    public void setTypeValeur(String typeValeur) { this.typeValeur = typeValeur; }

    public String getSens() { return sens; }
    public void setSens(String sens) { this.sens = sens; }

    public boolean isSoumisCnaps() { return soumisCnaps; }
    public void setSoumisCnaps(boolean soumisCnaps) { this.soumisCnaps = soumisCnaps; }

    public boolean isSoumisSante() { return soumisSante; }
    public void setSoumisSante(boolean soumisSante) { this.soumisSante = soumisSante; }

    public boolean isSoumisIrsa() { return soumisIrsa; }
    public void setSoumisIrsa(boolean soumisIrsa) { this.soumisIrsa = soumisIrsa; }

    public boolean isEstFixe() { return estFixe; }
    public void setEstFixe(boolean estFixe) { this.estFixe = estFixe; }

    public Integer getOrdreAffichage() { return ordreAffichage; }
    public void setOrdreAffichage(Integer ordreAffichage) { this.ordreAffichage = ordreAffichage; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReferenceReglementaire() { return referenceReglementaire; }
    public void setReferenceReglementaire(String referenceReglementaire) { this.referenceReglementaire = referenceReglementaire; }
    public LocalDate getDateDebutEffet() { return dateDebutEffet; }
    public void setDateDebutEffet(LocalDate dateDebutEffet) { this.dateDebutEffet = dateDebutEffet; }
    public LocalDate getDateFinEffet() { return dateFinEffet; }
    public void setDateFinEffet(LocalDate v) { dateFinEffet = v; }
    public Integer getIdRubriqueOrigine() { return idRubriqueOrigine; }
    public void setIdRubriqueOrigine(Integer v) { idRubriqueOrigine = v; }
    public String getConditionApplication() { return conditionApplication; }
    public void setConditionApplication(String v) { conditionApplication = v; }
    public String getMethodeCalcul() { return methodeCalcul; }
    public void setMethodeCalcul(String v) { methodeCalcul=v; }
    public String getPolitiqueArrondi() { return politiqueArrondi; }
    public void setPolitiqueArrondi(String v) { politiqueArrondi=v; }
    public Boolean getSoumisFmfp() { return soumisFmfp; }
    public void setSoumisFmfp(Boolean v) { soumisFmfp=v; }
    public String getEtapeCalcul() { return etapeCalcul; }
    public void setEtapeCalcul(String etapeCalcul) { this.etapeCalcul = etapeCalcul; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
