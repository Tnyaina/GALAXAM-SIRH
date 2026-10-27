package mg.galaxam.sirh.pointage.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class EmployeIndemniteDerogation extends ClassMere {

    private Integer idDerogation;
    private Integer idEmploye;
    private Integer idIndemnite;
    private boolean beneficie;
    private String motif;
    private Date dateDebut;
    private Date dateFin;
    private Integer createdBy;

    // Propriétés jointures pour affichage
    private String matriculeEmploye;
    private String nomPrenomEmploye;
    private String codeIndemnite;
    private String libelleIndemnite;

    public EmployeIndemniteDerogation() {
        this.setNomTable("EMPLOYE_INDEMNITE_DEROGATION");
        this.beneficie = false;
        this.dateDebut = new Date(System.currentTimeMillis());
    }

    @Override
    public Integer getTuppleID() {
        return this.idDerogation;
    }

    @Override
    public String getAttributIDName() {
        return "id_derogation";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null || idEmploye <= 0) {
            throw new IllegalArgumentException("L'employé est obligatoire.");
        }
        if (idIndemnite == null || idIndemnite <= 0) {
            throw new IllegalArgumentException("L'indemnité est obligatoire.");
        }
        if (dateDebut == null) {
            throw new IllegalArgumentException("La date de début est obligatoire.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO employe_indemnite_derogation (id_employe, id_indemnite, beneficie, motif, date_debut, date_fin, created_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_derogation";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idIndemnite);
            ps.setBoolean(3, beneficie);
            ps.setString(4, motif);
            ps.setDate(5, dateDebut);
            if (dateFin != null) ps.setDate(6, dateFin); else ps.setNull(6, Types.DATE);
            if (createdBy != null) ps.setInt(7, createdBy); else ps.setNull(7, Types.INTEGER);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idDerogation = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE employe_indemnite_derogation SET id_employe = ?, id_indemnite = ?, beneficie = ?, motif = ?, date_debut = ?, date_fin = ? WHERE id_derogation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idIndemnite);
            ps.setBoolean(3, beneficie);
            ps.setString(4, motif);
            ps.setDate(5, dateDebut);
            if (dateFin != null) ps.setDate(6, dateFin); else ps.setNull(6, Types.DATE);
            ps.setInt(7, idDerogation);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM employe_indemnite_derogation WHERE id_derogation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idDerogation);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public EmployeIndemniteDerogation rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT d.id_derogation, d.id_employe, d.id_indemnite, d.beneficie, d.motif, d.date_debut, d.date_fin, d.created_by, " +
                     "e.matricule, e.nom || ' ' || e.prenom AS nom_prenom, i.code AS code_indemnite, i.libelle AS libelle_indemnite " +
                     "FROM employe_indemnite_derogation d " +
                     "JOIN employe e ON d.id_employe = e.id_employe " +
                     "JOIN ref_indemnite i ON d.id_indemnite = i.id_indemnite " +
                     "WHERE d.id_derogation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EmployeIndemniteDerogation d = new EmployeIndemniteDerogation();
                    d.setIdDerogation(rs.getInt("id_derogation"));
                    d.setIdEmploye(rs.getInt("id_employe"));
                    d.setIdIndemnite(rs.getInt("id_indemnite"));
                    d.setBeneficie(rs.getBoolean("beneficie"));
                    d.setMotif(rs.getString("motif"));
                    d.setDateDebut(rs.getDate("date_debut"));
                    d.setDateFin(rs.getDate("date_fin"));
                    d.setCreatedBy((Integer) rs.getObject("created_by"));
                    d.setMatriculeEmploye(rs.getString("matricule"));
                    d.setNomPrenomEmploye(rs.getString("nom_prenom"));
                    d.setCodeIndemnite(rs.getString("code_indemnite"));
                    d.setLibelleIndemnite(rs.getString("libelle_indemnite"));
                    return d;
                }
            }
        }
        return null;
    }

    public static List<EmployeIndemniteDerogation> listAllActive(Connection c) throws SQLException {
        List<EmployeIndemniteDerogation> list = new ArrayList<>();
        String sql = "SELECT d.id_derogation, d.id_employe, d.id_indemnite, d.beneficie, d.motif, d.date_debut, d.date_fin, d.created_by, " +
                     "e.matricule, e.nom || ' ' || e.prenom AS nom_prenom, i.code AS code_indemnite, i.libelle AS libelle_indemnite " +
                     "FROM employe_indemnite_derogation d " +
                     "JOIN employe e ON d.id_employe = e.id_employe " +
                     "JOIN ref_indemnite i ON d.id_indemnite = i.id_indemnite " +
                     "ORDER BY e.nom, e.prenom";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                EmployeIndemniteDerogation d = new EmployeIndemniteDerogation();
                d.setIdDerogation(rs.getInt("id_derogation"));
                d.setIdEmploye(rs.getInt("id_employe"));
                d.setIdIndemnite(rs.getInt("id_indemnite"));
                d.setBeneficie(rs.getBoolean("beneficie"));
                d.setMotif(rs.getString("motif"));
                d.setDateDebut(rs.getDate("date_debut"));
                d.setDateFin(rs.getDate("date_fin"));
                d.setCreatedBy((Integer) rs.getObject("created_by"));
                d.setMatriculeEmploye(rs.getString("matricule"));
                d.setNomPrenomEmploye(rs.getString("nom_prenom"));
                d.setCodeIndemnite(rs.getString("code_indemnite"));
                d.setLibelleIndemnite(rs.getString("libelle_indemnite"));
                list.add(d);
            }
        }
        return list;
    }

    /**
     * Clôture toutes les dérogations actives associées à une indemnité donnée.
     * Met fin à leur validité en fixant date_fin = CURRENT_DATE (si indéterminée ou future).
     *
     * @param idIndemnite identifiant de l'indemnité
     * @param c connexion JDBC transactionnelle
     * @return nombre de dérogations clôturées
     * @throws Exception en cas d'erreur SQL
     */
    public static int cloturerParIndemnite(int idIndemnite, Connection c) throws Exception {
        return cloturerParIndemnite(idIndemnite, java.time.LocalDate.now(), c);
    }

    public static int cloturerParIndemnite(int idIndemnite, java.time.LocalDate dateEffet, Connection c) throws Exception {
        java.time.LocalDate target = dateEffet != null ? dateEffet : java.time.LocalDate.now();
        String sql = "UPDATE employe_indemnite_derogation SET date_fin = ? " +
                     "WHERE id_indemnite = ? AND (date_fin IS NULL OR date_fin > ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            java.sql.Date d = java.sql.Date.valueOf(target);
            ps.setDate(1, d);
            ps.setInt(2, idIndemnite);
            ps.setDate(3, d);
            return ps.executeUpdate();
        }
    }

    // Getters and Setters
    public Integer getIdDerogation() { return idDerogation; }
    public void setIdDerogation(Integer idDerogation) { this.idDerogation = idDerogation; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdIndemnite() { return idIndemnite; }
    public void setIdIndemnite(Integer idIndemnite) { this.idIndemnite = idIndemnite; }

    public boolean isBeneficie() { return beneficie; }
    public void setBeneficie(boolean beneficie) { this.beneficie = beneficie; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public String getMatriculeEmploye() { return matriculeEmploye; }
    public void setMatriculeEmploye(String matriculeEmploye) { this.matriculeEmploye = matriculeEmploye; }

    public String getNomPrenomEmploye() { return nomPrenomEmploye; }
    public void setNomPrenomEmploye(String nomPrenomEmploye) { this.nomPrenomEmploye = nomPrenomEmploye; }

    public String getCodeIndemnite() { return codeIndemnite; }
    public void setCodeIndemnite(String codeIndemnite) { this.codeIndemnite = codeIndemnite; }

    public String getLibelleIndemnite() { return libelleIndemnite; }
    public void setLibelleIndemnite(String libelleIndemnite) { this.libelleIndemnite = libelleIndemnite; }
}
