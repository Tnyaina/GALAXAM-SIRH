package mg.galaxam.sirh.conge.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class RefTypeConge extends ClassMere {

    private Integer idTypeConge;
    private String code;
    private String libelle;
    private boolean estPaye = true;
    private boolean decompteSoldeAcquis = true;
    private Integer exigeAncienneteMinMois = 0;
    private BigDecimal dureeFixeJours;
    private BigDecimal plafondJoursEpisode;
    private Integer idTypeCongeBascule;
    private Integer idPoolCongeAnnuel;
    private boolean soumisDelaiPreavis = false;
    private boolean suspendAcquisitionSolde = false;
    private boolean exigeJustificatif = false;
    private boolean estActif = true;
    private Timestamp createdAt;

    public RefTypeConge() {
        this.setNomTable("REF_TYPE_CONGE");
    }

    @Override
    public Integer getTuppleID() {
        return this.idTypeConge;
    }

    @Override
    public String getAttributIDName() {
        return "id_type_conge";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code du type de congé est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du type de congé est obligatoire.");
        }
        this.code = this.code.trim().toUpperCase();

        RefTypeConge existant = rechercherParCode(this.code, c);
        if (existant != null && !existant.getIdTypeConge().equals(this.idTypeConge)) {
            throw new IllegalArgumentException("Ce code de type de congé est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_type_conge (" +
                "code, libelle, est_paye, decompte_solde_acquis, exige_anciennete_min_mois, " +
                "duree_fixe_jours, plafond_jours_episode, id_type_conge_bascule, id_pool_conge_annuel, " +
                "soumis_delai_preavis, suspend_acquisition_solde, exige_justificatif, est_actif" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, code);
            ps.setString(i++, libelle);
            ps.setBoolean(i++, estPaye);
            ps.setBoolean(i++, decompteSoldeAcquis);
            ps.setInt(i++, exigeAncienneteMinMois != null ? exigeAncienneteMinMois : 0);
            setNullableBigDecimal(ps, i++, dureeFixeJours);
            setNullableBigDecimal(ps, i++, plafondJoursEpisode);
            setNullableInt(ps, i++, idTypeCongeBascule);
            setNullableInt(ps, i++, idPoolCongeAnnuel);
            ps.setBoolean(i++, soumisDelaiPreavis);
            ps.setBoolean(i++, suspendAcquisitionSolde);
            ps.setBoolean(i++, exigeJustificatif);
            ps.setBoolean(i++, estActif);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idTypeConge = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_type_conge SET " +
                "code = ?, libelle = ?, est_paye = ?, decompte_solde_acquis = ?, exige_anciennete_min_mois = ?, " +
                "duree_fixe_jours = ?, plafond_jours_episode = ?, id_type_conge_bascule = ?, id_pool_conge_annuel = ?, " +
                "soumis_delai_preavis = ?, suspend_acquisition_solde = ?, exige_justificatif = ?, est_actif = ? " +
                "WHERE id_type_conge = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, code);
            ps.setString(i++, libelle);
            ps.setBoolean(i++, estPaye);
            ps.setBoolean(i++, decompteSoldeAcquis);
            ps.setInt(i++, exigeAncienneteMinMois != null ? exigeAncienneteMinMois : 0);
            setNullableBigDecimal(ps, i++, dureeFixeJours);
            setNullableBigDecimal(ps, i++, plafondJoursEpisode);
            setNullableInt(ps, i++, idTypeCongeBascule);
            setNullableInt(ps, i++, idPoolCongeAnnuel);
            ps.setBoolean(i++, soumisDelaiPreavis);
            ps.setBoolean(i++, suspendAcquisitionSolde);
            ps.setBoolean(i++, exigeJustificatif);
            ps.setBoolean(i++, estActif);
            ps.setInt(i++, idTypeConge);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_type_conge WHERE id_type_conge = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTypeConge);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public static RefTypeConge rechercherParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM ref_type_conge WHERE id_type_conge = ?";
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

    public static RefTypeConge rechercherParCode(String code, Connection c) throws Exception {
        if (code == null || code.isBlank()) return null;
        String sql = "SELECT * FROM ref_type_conge WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<RefTypeConge> listAll(Connection c, boolean actifUniquement) throws Exception {
        List<RefTypeConge> list = new ArrayList<>();
        String sql = "SELECT * FROM ref_type_conge " +
                (actifUniquement ? "WHERE est_actif = TRUE " : "") +
                "ORDER BY id_type_conge";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private static RefTypeConge mapResultSet(ResultSet rs) throws Exception {
        RefTypeConge obj = new RefTypeConge();
        obj.setIdTypeConge(rs.getInt("id_type_conge"));
        obj.setCode(rs.getString("code"));
        obj.setLibelle(rs.getString("libelle"));
        obj.setEstPaye(rs.getBoolean("est_paye"));
        obj.setDecompteSoldeAcquis(rs.getBoolean("decompte_solde_acquis"));
        obj.setExigeAncienneteMinMois(rs.getInt("exige_anciennete_min_mois"));
        obj.setDureeFixeJours(rs.getBigDecimal("duree_fixe_jours"));
        obj.setPlafondJoursEpisode(rs.getBigDecimal("plafond_jours_episode"));
        obj.setIdTypeCongeBascule(getNullableIntFromRs(rs, "id_type_conge_bascule"));
        obj.setIdPoolCongeAnnuel(getNullableIntFromRs(rs, "id_pool_conge_annuel"));
        obj.setSoumisDelaiPreavis(rs.getBoolean("soumis_delai_preavis"));
        obj.setSuspendAcquisitionSolde(rs.getBoolean("suspend_acquisition_solde"));
        obj.setExigeJustificatif(rs.getBoolean("exige_justificatif"));
        obj.setEstActif(rs.getBoolean("est_actif"));
        obj.setCreatedAt(rs.getTimestamp("created_at"));
        return obj;
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws Exception {
        if (value != null) ps.setInt(index, value);
        else ps.setNull(index, Types.INTEGER);
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int index, BigDecimal value) throws Exception {
        if (value != null) ps.setBigDecimal(index, value);
        else ps.setNull(index, Types.NUMERIC);
    }

    private static Integer getNullableIntFromRs(ResultSet rs, String colName) throws Exception {
        int val = rs.getInt(colName);
        return rs.wasNull() ? null : val;
    }

    // Getters / Setters
    public Integer getIdTypeConge() { return idTypeConge; }
    public void setIdTypeConge(Integer idTypeConge) { this.idTypeConge = idTypeConge; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public boolean isEstPaye() { return estPaye; }
    public void setEstPaye(boolean estPaye) { this.estPaye = estPaye; }

    public boolean isDecompteSoldeAcquis() { return decompteSoldeAcquis; }
    public void setDecompteSoldeAcquis(boolean decompteSoldeAcquis) { this.decompteSoldeAcquis = decompteSoldeAcquis; }

    public Integer getExigeAncienneteMinMois() { return exigeAncienneteMinMois; }
    public void setExigeAncienneteMinMois(Integer exigeAncienneteMinMois) { this.exigeAncienneteMinMois = exigeAncienneteMinMois; }

    public BigDecimal getDureeFixeJours() { return dureeFixeJours; }
    public void setDureeFixeJours(BigDecimal dureeFixeJours) { this.dureeFixeJours = dureeFixeJours; }

    public BigDecimal getPlafondJoursEpisode() { return plafondJoursEpisode; }
    public void setPlafondJoursEpisode(BigDecimal plafondJoursEpisode) { this.plafondJoursEpisode = plafondJoursEpisode; }

    public Integer getIdTypeCongeBascule() { return idTypeCongeBascule; }
    public void setIdTypeCongeBascule(Integer idTypeCongeBascule) { this.idTypeCongeBascule = idTypeCongeBascule; }

    public Integer getIdPoolCongeAnnuel() { return idPoolCongeAnnuel; }
    public void setIdPoolCongeAnnuel(Integer idPoolCongeAnnuel) { this.idPoolCongeAnnuel = idPoolCongeAnnuel; }

    public boolean isSoumisDelaiPreavis() { return soumisDelaiPreavis; }
    public void setSoumisDelaiPreavis(boolean soumisDelaiPreavis) { this.soumisDelaiPreavis = soumisDelaiPreavis; }

    public boolean isSuspendAcquisitionSolde() { return suspendAcquisitionSolde; }
    public void setSuspendAcquisitionSolde(boolean suspendAcquisitionSolde) { this.suspendAcquisitionSolde = suspendAcquisitionSolde; }

    public boolean isExigeJustificatif() { return exigeJustificatif; }
    public void setExigeJustificatif(boolean exigeJustificatif) { this.exigeJustificatif = exigeJustificatif; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
