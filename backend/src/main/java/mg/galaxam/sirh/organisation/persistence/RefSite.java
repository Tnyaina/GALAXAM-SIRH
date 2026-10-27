package mg.galaxam.sirh.organisation.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class RefSite extends ClassMere {

    private Integer idSite;
    private String codeSite;
    private String libelle;
    private Integer idFilialeRattachement;
    private boolean horaireContinu;
    private Time heureDebutMatin;
    private Time heureFinMatin;
    private Time heureDebutApresMidi;
    private Time heureFinApresMidi;
    private boolean plancherArriveeActif;
    private Time heureEntreeMinimaleValorisee;
    private boolean estActif;
    private String filialeRaisonSociale; // Pour affichage

    public RefSite() {
        this.setNomTable("REF_SITE");
        this.estActif = true;
        this.horaireContinu = true;
        this.plancherArriveeActif = false;
    }

    @Override
    public Integer getTuppleID() {
        return this.idSite;
    }

    @Override
    public String getAttributIDName() {
        return "id_site";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (codeSite == null || codeSite.isBlank()) {
            throw new IllegalArgumentException("Le code du site est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du site est obligatoire.");
        }
        if (plancherArriveeActif && heureEntreeMinimaleValorisee == null) {
            throw new IllegalArgumentException("L'heure d'entrée minimale valorisée est obligatoire lorsque le plancher d'arrivée est actif.");
        }
        this.codeSite = this.codeSite.trim().toUpperCase();

        RefSite existant = rechercherParCode(this.codeSite, c);
        if (existant != null && !existant.getIdSite().equals(this.idSite)) {
            throw new IllegalArgumentException("Ce code de site est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_site (code_site, libelle, id_filiale_rattachement, horaire_continu, " +
                     "heure_debut_matin, heure_fin_matin, heure_debut_apres_midi, heure_fin_apres_midi, " +
                     "plancher_arrivee_actif, heure_entree_minimale_valorisee, est_actif) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codeSite);
            ps.setString(2, libelle);
            if (idFilialeRattachement != null) ps.setInt(3, idFilialeRattachement); else ps.setNull(3, Types.INTEGER);
            ps.setBoolean(4, horaireContinu);
            ps.setTime(5, heureDebutMatin);
            ps.setTime(6, heureFinMatin);
            ps.setTime(7, heureDebutApresMidi);
            ps.setTime(8, heureFinApresMidi);
            ps.setBoolean(9, plancherArriveeActif);
            ps.setTime(10, heureEntreeMinimaleValorisee);
            ps.setBoolean(11, estActif);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idSite = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_site SET code_site = ?, libelle = ?, id_filiale_rattachement = ?, horaire_continu = ?, " +
                     "heure_debut_matin = ?, heure_fin_matin = ?, heure_debut_apres_midi = ?, heure_fin_apres_midi = ?, " +
                     "plancher_arrivee_actif = ?, heure_entree_minimale_valorisee = ?, est_actif = ? " +
                     "WHERE id_site = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeSite);
            ps.setString(2, libelle);
            if (idFilialeRattachement != null) ps.setInt(3, idFilialeRattachement); else ps.setNull(3, Types.INTEGER);
            ps.setBoolean(4, horaireContinu);
            ps.setTime(5, heureDebutMatin);
            ps.setTime(6, heureFinMatin);
            ps.setTime(7, heureDebutApresMidi);
            ps.setTime(8, heureFinApresMidi);
            ps.setBoolean(9, plancherArriveeActif);
            ps.setTime(10, heureEntreeMinimaleValorisee);
            ps.setBoolean(11, estActif);
            ps.setInt(12, idSite);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_site WHERE id_site = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSite);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT s.id_site, s.code_site, s.libelle, s.id_filiale_rattachement, s.horaire_continu, " +
                     "s.heure_debut_matin, s.heure_fin_matin, s.heure_debut_apres_midi, s.heure_fin_apres_midi, " +
                     "s.plancher_arrivee_actif, s.heure_entree_minimale_valorisee, s.est_actif, " +
                     "f.raison_sociale as filiale_nom " +
                     "FROM ref_site s LEFT JOIN ref_filiale f ON s.id_filiale_rattachement = f.id_filiale " +
                     "WHERE s.id_site = ?";
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

    public static RefSite rechercherParCode(String code, Connection c) throws Exception {
        String sql = "SELECT s.id_site, s.code_site, s.libelle, s.id_filiale_rattachement, s.horaire_continu, " +
                     "s.heure_debut_matin, s.heure_fin_matin, s.heure_debut_apres_midi, s.heure_fin_apres_midi, " +
                     "s.plancher_arrivee_actif, s.heure_entree_minimale_valorisee, s.est_actif, " +
                     "f.raison_sociale as filiale_nom " +
                     "FROM ref_site s LEFT JOIN ref_filiale f ON s.id_filiale_rattachement = f.id_filiale " +
                     "WHERE s.code_site = ?";
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

    public static List<RefSite> listAll(Connection c) throws Exception {
        return list(c, false);
    }

    public static List<RefSite> listActifs(Connection c) throws Exception {
        return list(c, true);
    }

    public static List<RefSite> listByFiliale(Integer idFiliale, boolean actifsOnly, Connection c) throws Exception {
        List<RefSite> list = new ArrayList<>();
        String sql = "SELECT s.id_site, s.code_site, s.libelle, s.id_filiale_rattachement, s.horaire_continu, " +
                     "s.heure_debut_matin, s.heure_fin_matin, s.heure_debut_apres_midi, s.heure_fin_apres_midi, " +
                     "s.plancher_arrivee_actif, s.heure_entree_minimale_valorisee, s.est_actif, " +
                     "f.raison_sociale as filiale_nom " +
                     "FROM ref_site s LEFT JOIN ref_filiale f ON s.id_filiale_rattachement = f.id_filiale " +
                     "WHERE s.id_filiale_rattachement = ?" +
                     (actifsOnly ? " AND s.est_actif = TRUE" : "") + " ORDER BY s.id_site ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idFiliale);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    private static List<RefSite> list(Connection c, boolean actifsOnly) throws Exception {
        List<RefSite> list = new ArrayList<>();
        String sql = "SELECT s.id_site, s.code_site, s.libelle, s.id_filiale_rattachement, s.horaire_continu, " +
                     "s.heure_debut_matin, s.heure_fin_matin, s.heure_debut_apres_midi, s.heure_fin_apres_midi, " +
                     "s.plancher_arrivee_actif, s.heure_entree_minimale_valorisee, s.est_actif, " +
                     "f.raison_sociale as filiale_nom " +
                     "FROM ref_site s LEFT JOIN ref_filiale f ON s.id_filiale_rattachement = f.id_filiale" +
                     (actifsOnly ? " WHERE s.est_actif = TRUE" : "") + " ORDER BY s.id_site ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefSite mapResultSet(ResultSet rs) throws SQLException {
        RefSite s = new RefSite();
        s.setIdSite(rs.getInt("id_site"));
        s.setCodeSite(rs.getString("code_site"));
        s.setLibelle(rs.getString("libelle"));
        int fId = rs.getInt("id_filiale_rattachement");
        s.setIdFilialeRattachement(rs.wasNull() ? null : fId);
        s.setHoraireContinu(rs.getBoolean("horaire_continu"));
        s.setHeureDebutMatin(rs.getTime("heure_debut_matin"));
        s.setHeureFinMatin(rs.getTime("heure_fin_matin"));
        s.setHeureDebutApresMidi(rs.getTime("heure_debut_apres_midi"));
        s.setHeureFinApresMidi(rs.getTime("heure_fin_apres_midi"));
        s.setPlancherArriveeActif(rs.getBoolean("plancher_arrivee_actif"));
        s.setHeureEntreeMinimaleValorisee(rs.getTime("heure_entree_minimale_valorisee"));
        s.setEstActif(rs.getBoolean("est_actif"));
        s.setFilialeRaisonSociale(rs.getString("filiale_nom"));
        return s;
    }

    // Getters and Setters
    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }

    public String getCodeSite() { return codeSite; }
    public void setCodeSite(String codeSite) { this.codeSite = codeSite; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public Integer getIdFilialeRattachement() { return idFilialeRattachement; }
    public void setIdFilialeRattachement(Integer idFilialeRattachement) { this.idFilialeRattachement = idFilialeRattachement; }

    public boolean isHoraireContinu() { return horaireContinu; }
    public void setHoraireContinu(boolean horaireContinu) { this.horaireContinu = horaireContinu; }

    public Time getHeureDebutMatin() { return heureDebutMatin; }
    public void setHeureDebutMatin(Time heureDebutMatin) { this.heureDebutMatin = heureDebutMatin; }

    public Time getHeureFinMatin() { return heureFinMatin; }
    public void setHeureFinMatin(Time heureFinMatin) { this.heureFinMatin = heureFinMatin; }

    public Time getHeureDebutApresMidi() { return heureDebutApresMidi; }
    public void setHeureDebutApresMidi(Time heureDebutApresMidi) { this.heureDebutApresMidi = heureDebutApresMidi; }

    public Time getHeureFinApresMidi() { return heureFinApresMidi; }
    public void setHeureFinApresMidi(Time heureFinApresMidi) { this.heureFinApresMidi = heureFinApresMidi; }

    public boolean isPlancherArriveeActif() { return plancherArriveeActif; }
    public void setPlancherArriveeActif(boolean plancherArriveeActif) { this.plancherArriveeActif = plancherArriveeActif; }

    public Time getHeureEntreeMinimaleValorisee() { return heureEntreeMinimaleValorisee; }
    public void setHeureEntreeMinimaleValorisee(Time heureEntreeMinimaleValorisee) { this.heureEntreeMinimaleValorisee = heureEntreeMinimaleValorisee; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }

    public String getFilialeRaisonSociale() { return filialeRaisonSociale; }
    public void setFilialeRaisonSociale(String filialeRaisonSociale) { this.filialeRaisonSociale = filialeRaisonSociale; }
}
