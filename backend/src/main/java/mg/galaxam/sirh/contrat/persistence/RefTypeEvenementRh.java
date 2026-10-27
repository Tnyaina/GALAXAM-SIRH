package mg.galaxam.sirh.contrat.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RefTypeEvenementRh extends ClassMere {

    private Integer idTypeEvenementRh;
    private String code;
    private String libelle;
    private String description;

    public RefTypeEvenementRh() {
        this.setNomTable("REF_TYPE_EVENEMENT_RH");
    }

    @Override
    public Integer getTuppleID() { return idTypeEvenementRh; }

    @Override
    public String getAttributIDName() { return "id_type_evenement_rh"; }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_type_evenement_rh (code, libelle, description) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, description);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) this.idTypeEvenementRh = rs.getInt(1);
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_type_evenement_rh SET code = ?, libelle = ?, description = ? WHERE id_type_evenement_rh = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setString(3, description);
            ps.setInt(4, idTypeEvenementRh);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_type_evenement_rh WHERE id_type_evenement_rh = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTypeEvenementRh);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public RefTypeEvenementRh rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM ref_type_evenement_rh WHERE id_type_evenement_rh = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    public static RefTypeEvenementRh rechercherParCode(String code, Connection c) throws Exception {
        String sql = "SELECT * FROM ref_type_evenement_rh WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    public static List<RefTypeEvenementRh> listAll(Connection c) throws Exception {
        List<RefTypeEvenementRh> list = new ArrayList<>();
        String sql = "SELECT * FROM ref_type_evenement_rh ORDER BY libelle ASC";
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapResultSet(rs));
        }
        return list;
    }

    public static RefTypeEvenementRh mapResultSet(ResultSet rs) throws SQLException {
        RefTypeEvenementRh r = new RefTypeEvenementRh();
        r.setIdTypeEvenementRh(rs.getInt("id_type_evenement_rh"));
        r.setCode(rs.getString("code"));
        r.setLibelle(rs.getString("libelle"));
        r.setDescription(rs.getString("description"));
        return r;
    }

    // Getters & Setters
    public Integer getIdTypeEvenementRh() { return idTypeEvenementRh; }
    public void setIdTypeEvenementRh(Integer idTypeEvenementRh) { this.idTypeEvenementRh = idTypeEvenementRh; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // ─── Helpers Métier & Sérialisation JSON ──────────────────────────────────

    public boolean isExigeNouveauContrat() {
        if (code == null) return false;
        return switch (code) {
            case "TRANSFORMATION_CDI", "RENOUVELLEMENT_CDD", "EMBAUCHE_APRES_STAGE", "REPRISE_CONSULTANT", "EMBAUCHE_INITIALE", "REPRISE_ANCIENNETE" -> true;
            default -> false;
        };
    }

    public boolean isAutorisePeriodeEssai() {
        if (code == null) return false;
        return isExigeNouveauContrat() && !"RENOUVELLEMENT_CDD".equals(code);
    }

    public boolean isEmbauche() {
        return "EMBAUCHE_INITIALE".equals(code) || "REPRISE_ANCIENNETE".equals(code);
    }

    public boolean isEssai() {
        return "CONFIRMATION_ESSAI".equals(code) || "RENOUVELLEMENT_ESSAI".equals(code);
    }

    public boolean isConfirmationEssai() {
        return "CONFIRMATION_ESSAI".equals(code);
    }

    public boolean isRenouvellementEssai() {
        return "RENOUVELLEMENT_ESSAI".equals(code);
    }

    public boolean isRenouvellementCdd() {
        return "RENOUVELLEMENT_CDD".equals(code);
    }

    public boolean isRupture() {
        if (code == null) return false;
        return switch (code) {
            case "FIN_CONTRAT", "DEMISSION", "LICENCIEMENT", "RUPTURE_CONVENTIONNELLE",
                 "RUPTURE_ESSAI_EMPLOYEUR", "RUPTURE_ESSAI_SALARIE" -> true;
            default -> false;
        };
    }

    public boolean isAvenant() {
        if (code == null) return false;
        return switch (code) {
            case "MUTATION", "PROMOTION", "AUGMENTATION", "REVALORISATION", "AJUSTEMENT_POSTE" -> true;
            default -> false;
        };
    }
}
