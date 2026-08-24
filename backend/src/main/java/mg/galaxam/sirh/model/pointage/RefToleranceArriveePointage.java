package mg.galaxam.sirh.model.pointage;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

public class RefToleranceArriveePointage extends ClassMere {

    private Integer idTolerance;
    private Integer idSite;
    private Time heurePlafond;

    // Propriétés jointures pour affichage
    private String codeSite;
    private String libelleSite;

    public RefToleranceArriveePointage() {
        this.setNomTable("REF_TOLERANCE_ARRIVEE_POINTAGE");
    }

    @Override
    public Integer getTuppleID() {
        return this.idTolerance;
    }

    @Override
    public String getAttributIDName() {
        return "id_tolerance";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idSite == null || idSite <= 0) {
            throw new IllegalArgumentException("Le site est obligatoire.");
        }
        if (heurePlafond == null) {
            throw new IllegalArgumentException("L'heure plafond d'arrivée est obligatoire.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_tolerance_arrivee_pointage (id_site, heure_plafond) VALUES (?, ?) ON CONFLICT (id_site) DO UPDATE SET heure_plafond = EXCLUDED.heure_plafond RETURNING id_tolerance";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSite);
            ps.setTime(2, heurePlafond);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idTolerance = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_tolerance_arrivee_pointage SET id_site = ?, heure_plafond = ? WHERE id_tolerance = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSite);
            ps.setTime(2, heurePlafond);
            ps.setInt(3, idTolerance);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_tolerance_arrivee_pointage WHERE id_tolerance = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTolerance);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public RefToleranceArriveePointage rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT t.id_tolerance, t.id_site, t.heure_plafond, s.code_site, s.libelle AS libelle_site " +
                     "FROM ref_tolerance_arrivee_pointage t " +
                     "JOIN ref_site s ON t.id_site = s.id_site " +
                     "WHERE t.id_tolerance = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RefToleranceArriveePointage t = new RefToleranceArriveePointage();
                    t.setIdTolerance(rs.getInt("id_tolerance"));
                    t.setIdSite(rs.getInt("id_site"));
                    t.setHeurePlafond(rs.getTime("heure_plafond"));
                    t.setCodeSite(rs.getString("code_site"));
                    t.setLibelleSite(rs.getString("libelle_site"));
                    return t;
                }
            }
        }
        return null;
    }

    public static List<RefToleranceArriveePointage> listAll(Connection c) throws SQLException {
        List<RefToleranceArriveePointage> list = new ArrayList<>();
        String sql = "SELECT t.id_tolerance, t.id_site, t.heure_plafond, s.code_site, s.libelle AS libelle_site " +
                     "FROM ref_tolerance_arrivee_pointage t " +
                     "JOIN ref_site s ON t.id_site = s.id_site " +
                     "ORDER BY s.code_site";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RefToleranceArriveePointage t = new RefToleranceArriveePointage();
                t.setIdTolerance(rs.getInt("id_tolerance"));
                t.setIdSite(rs.getInt("id_site"));
                t.setHeurePlafond(rs.getTime("heure_plafond"));
                t.setCodeSite(rs.getString("code_site"));
                t.setLibelleSite(rs.getString("libelle_site"));
                list.add(t);
            }
        }
        return list;
    }

    // Getters and Setters
    public Integer getIdTolerance() { return idTolerance; }
    public void setIdTolerance(Integer idTolerance) { this.idTolerance = idTolerance; }

    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }

    public Time getHeurePlafond() { return heurePlafond; }
    public void setHeurePlafond(Time heurePlafond) { this.heurePlafond = heurePlafond; }

    public String getCodeSite() { return codeSite; }
    public void setCodeSite(String codeSite) { this.codeSite = codeSite; }

    public String getLibelleSite() { return libelleSite; }
    public void setLibelleSite(String libelleSite) { this.libelleSite = libelleSite; }
}
