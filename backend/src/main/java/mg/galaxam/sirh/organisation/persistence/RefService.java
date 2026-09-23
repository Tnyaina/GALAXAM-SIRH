package mg.galaxam.sirh.organisation.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RefService extends ClassMere {

    private Integer idService;
    private String codeService;
    private String libelle;
    private Integer idDepartement;
    private boolean estActif;
    private String departementLibelle; // Libellé pour affichage

    public RefService() {
        this.setNomTable("REF_SERVICE");
        this.estActif = true;
    }

    @Override
    public Integer getTuppleID() {
        return this.idService;
    }

    @Override
    public String getAttributIDName() {
        return "id_service";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (codeService == null || codeService.isBlank()) {
            throw new IllegalArgumentException("Le code du service est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du service est obligatoire.");
        }
        if (idDepartement == null) {
            throw new IllegalArgumentException("Le département de rattachement est obligatoire.");
        }
        this.codeService = this.codeService.trim().toUpperCase();

        RefService existant = rechercherParCodeEtDepartement(this.codeService, this.idDepartement, c);
        if (existant != null && !existant.getIdService().equals(this.idService)) {
            throw new IllegalArgumentException("Ce code de service existe déjà dans ce département.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_service (code_service, libelle, id_departement, est_actif) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codeService);
            ps.setString(2, libelle);
            ps.setInt(3, idDepartement);
            ps.setBoolean(4, estActif);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idService = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_service SET code_service = ?, libelle = ?, id_departement = ?, est_actif = ? WHERE id_service = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeService);
            ps.setString(2, libelle);
            ps.setInt(3, idDepartement);
            ps.setBoolean(4, estActif);
            ps.setInt(5, idService);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_service WHERE id_service = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idService);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT s.id_service, s.code_service, s.libelle, s.id_departement, s.est_actif, d.libelle as dept_libelle " +
                     "FROM ref_service s JOIN ref_departement d ON s.id_departement = d.id_departement " +
                     "WHERE s.id_service = ?";
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

    public static RefService rechercherParCodeEtDepartement(String code, Integer idDept, Connection c) throws Exception {
        String sql = "SELECT s.id_service, s.code_service, s.libelle, s.id_departement, s.est_actif, d.libelle as dept_libelle " +
                     "FROM ref_service s JOIN ref_departement d ON s.id_departement = d.id_departement " +
                     "WHERE s.code_service = ? AND s.id_departement = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setInt(2, idDept);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<RefService> listAll(Connection c) throws Exception {
        List<RefService> list = new ArrayList<>();
        String sql = "SELECT s.id_service, s.code_service, s.libelle, s.id_departement, s.est_actif, d.libelle as dept_libelle " +
                     "FROM ref_service s JOIN ref_departement d ON s.id_departement = d.id_departement " +
                     "ORDER BY s.id_departement ASC, s.id_service ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public static List<RefService> listByDepartement(Integer idDept, Connection c) throws Exception {
        List<RefService> list = new ArrayList<>();
        String sql = "SELECT s.id_service, s.code_service, s.libelle, s.id_departement, s.est_actif, d.libelle as dept_libelle " +
                     "FROM ref_service s JOIN ref_departement d ON s.id_departement = d.id_departement " +
                     "WHERE s.id_departement = ? ORDER BY s.id_service ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idDept);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    private static RefService mapResultSet(ResultSet rs) throws SQLException {
        RefService s = new RefService();
        s.setIdService(rs.getInt("id_service"));
        s.setCodeService(rs.getString("code_service"));
        s.setLibelle(rs.getString("libelle"));
        s.setIdDepartement(rs.getInt("id_departement"));
        s.setEstActif(rs.getBoolean("est_actif"));
        s.setDepartementLibelle(rs.getString("dept_libelle"));
        return s;
    }

    // Getters and Setters
    public Integer getIdService() { return idService; }
    public void setIdService(Integer idService) { this.idService = idService; }

    public String getCodeService() { return codeService; }
    public void setCodeService(String codeService) { this.codeService = codeService; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public Integer getIdDepartement() { return idDepartement; }
    public void setIdDepartement(Integer idDepartement) { this.idDepartement = idDepartement; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }

    public String getDepartementLibelle() { return departementLibelle; }
    public void setDepartementLibelle(String departementLibelle) { this.departementLibelle = departementLibelle; }
}
