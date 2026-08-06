package mg.galaxam.sirh.employe;

import bean.ClassMere;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class EmployeAffectationCarriere extends ClassMere {

    private Integer idAffectation;
    private Integer idEmploye;
    private Integer idContrat;
    private Integer idMotifMouvement;

    private Integer idSite;
    private Integer idDepartement;
    private Integer idService;
    private Integer idFonction;
    private Integer idResponsableHierarchique;

    private Integer idCategorieProfessionnelle;
    private Integer idModeCalculPaie;
    private BigDecimal salaireBase;

    private Date dateDebut;
    private Date dateFin;
    private String commentaire;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public EmployeAffectationCarriere() {
        this.setNomTable("EMPLOYE_AFFECTATION_CARRIERE");
    }

    @Override
    public Integer getTuppleID() {
        return this.idAffectation;
    }

    @Override
    public String getAttributIDName() {
        return "id_affectation";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'employé est obligatoire pour l'affectation.");
        }
        if (idContrat == null) {
            throw new IllegalArgumentException("Le contrat de travail est obligatoire.");
        }
        if (idMotifMouvement == null) {
            throw new IllegalArgumentException("Le motif de mouvement est obligatoire.");
        }
        if (idSite == null) throw new IllegalArgumentException("Le site de rattachement est obligatoire.");
        if (idDepartement == null) throw new IllegalArgumentException("Le département est obligatoire.");
        if (idService == null) throw new IllegalArgumentException("Le service est obligatoire.");
        if (idFonction == null) throw new IllegalArgumentException("La fonction / poste est obligatoire.");
        if (idCategorieProfessionnelle == null) throw new IllegalArgumentException("La catégorie professionnelle est obligatoire.");
        if (idModeCalculPaie == null) throw new IllegalArgumentException("Le mode de calcul de la paie est obligatoire.");
        if (salaireBase == null || salaireBase.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le salaire de base doit être supérieur ou égal à 0.");
        }
        if (dateDebut == null) throw new IllegalArgumentException("La date de début d'affectation est obligatoire.");
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO employe_affectation_carriere (" +
                "id_employe, id_contrat, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_responsable_hierarchique, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin, commentaire" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_affectation";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idContrat);
            ps.setInt(3, idMotifMouvement);
            ps.setInt(4, idSite);
            ps.setInt(5, idDepartement);
            ps.setInt(6, idService);
            ps.setInt(7, idFonction);
            ps.setObject(8, idResponsableHierarchique);
            ps.setInt(9, idCategorieProfessionnelle);
            ps.setInt(10, idModeCalculPaie);
            ps.setBigDecimal(11, salaireBase);
            ps.setDate(12, dateDebut);
            ps.setDate(13, dateFin);
            ps.setString(14, commentaire);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idAffectation = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE employe_affectation_carriere SET " +
                "id_motif_mouvement = ?, id_site = ?, id_departement = ?, id_service = ?, id_fonction = ?, id_responsable_hierarchique = ?, id_categorie_professionnelle = ?, id_mode_calcul_paie = ?, salaire_base = ?, date_debut = ?, date_fin = ?, commentaire = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_affectation = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idMotifMouvement);
            ps.setInt(2, idSite);
            ps.setInt(3, idDepartement);
            ps.setInt(4, idService);
            ps.setInt(5, idFonction);
            ps.setObject(6, idResponsableHierarchique);
            ps.setInt(7, idCategorieProfessionnelle);
            ps.setInt(8, idModeCalculPaie);
            ps.setBigDecimal(9, salaireBase);
            ps.setDate(10, dateDebut);
            ps.setDate(11, dateFin);
            ps.setString(12, commentaire);
            ps.setInt(13, idAffectation);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM employe_affectation_carriere WHERE id_affectation = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idAffectation);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public EmployeAffectationCarriere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM employe_affectation_carriere WHERE id_affectation = ?";
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

    public static List<EmployeAffectationCarriere> listByEmployeId(Integer idEmploye, Connection c) throws Exception {
        List<EmployeAffectationCarriere> list = new ArrayList<>();
        String sql = "SELECT * FROM employe_affectation_carriere WHERE id_employe = ? ORDER BY date_debut DESC";
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

    public static EmployeAffectationCarriere getAffectationCourante(Integer idEmploye, Connection c) throws Exception {
        String sql = "SELECT * FROM employe_affectation_carriere WHERE id_employe = ? AND (date_fin IS NULL OR date_fin >= CURRENT_DATE) ORDER BY date_debut DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * Clôture l'affectation précédente lorsqu'une nouvelle affectation débute.
     */
    public static void cloturerAffectationCourante(Integer idEmploye, Date dateFinCloture, Connection c) throws Exception {
        String sql = "UPDATE employe_affectation_carriere SET date_fin = ?, updated_at = CURRENT_TIMESTAMP WHERE id_employe = ? AND date_fin IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, dateFinCloture);
            ps.setInt(2, idEmploye);
            ps.executeUpdate();
        }
    }

    public static EmployeAffectationCarriere mapResultSet(ResultSet rs) throws SQLException {
        EmployeAffectationCarriere a = new EmployeAffectationCarriere();
        a.setIdAffectation(rs.getInt("id_affectation"));
        a.setIdEmploye(rs.getInt("id_employe"));
        a.setIdContrat(rs.getInt("id_contrat"));
        a.setIdMotifMouvement(rs.getInt("id_motif_mouvement"));

        a.setIdSite(rs.getInt("id_site"));
        a.setIdDepartement(rs.getInt("id_departement"));
        a.setIdService(rs.getInt("id_service"));
        a.setIdFonction(rs.getInt("id_fonction"));
        a.setIdResponsableHierarchique((Integer) rs.getObject("id_responsable_hierarchique"));

        a.setIdCategorieProfessionnelle(rs.getInt("id_categorie_professionnelle"));
        a.setIdModeCalculPaie(rs.getInt("id_mode_calcul_paie"));
        a.setSalaireBase(rs.getBigDecimal("salaire_base"));

        a.setDateDebut(rs.getDate("date_debut"));
        a.setDateFin(rs.getDate("date_fin"));
        a.setCommentaire(rs.getString("commentaire"));

        a.setCreatedAt(rs.getTimestamp("created_at"));
        a.setUpdatedAt(rs.getTimestamp("updated_at"));
        return a;
    }

    // Getters and Setters
    public Integer getIdAffectation() { return idAffectation; }
    public void setIdAffectation(Integer idAffectation) { this.idAffectation = idAffectation; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdContrat() { return idContrat; }
    public void setIdContrat(Integer idContrat) { this.idContrat = idContrat; }

    public Integer getIdMotifMouvement() { return idMotifMouvement; }
    public void setIdMotifMouvement(Integer idMotifMouvement) { this.idMotifMouvement = idMotifMouvement; }

    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }

    public Integer getIdDepartement() { return idDepartement; }
    public void setIdDepartement(Integer idDepartement) { this.idDepartement = idDepartement; }

    public Integer getIdService() { return idService; }
    public void setIdService(Integer idService) { this.idService = idService; }

    public Integer getIdFonction() { return idFonction; }
    public void setIdFonction(Integer idFonction) { this.idFonction = idFonction; }

    public Integer getIdResponsableHierarchique() { return idResponsableHierarchique; }
    public void setIdResponsableHierarchique(Integer idResponsableHierarchique) { this.idResponsableHierarchique = idResponsableHierarchique; }

    public Integer getIdCategorieProfessionnelle() { return idCategorieProfessionnelle; }
    public void setIdCategorieProfessionnelle(Integer idCategorieProfessionnelle) { this.idCategorieProfessionnelle = idCategorieProfessionnelle; }

    public Integer getIdModeCalculPaie() { return idModeCalculPaie; }
    public void setIdModeCalculPaie(Integer idModeCalculPaie) { this.idModeCalculPaie = idModeCalculPaie; }

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
