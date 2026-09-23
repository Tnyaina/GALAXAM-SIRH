package mg.galaxam.sirh.conge.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RefPoolCongeAnnuel extends ClassMere {

    private Integer idPool;
    private String code;
    private String libelle;
    private BigDecimal plafondJoursAn;

    public RefPoolCongeAnnuel() {
        this.setNomTable("REF_POOL_CONGE_ANNUEL");
    }

    @Override
    public Integer getTuppleID() {
        return this.idPool;
    }

    @Override
    public String getAttributIDName() {
        return "id_pool";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code du pool annuel est obligatoire.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du pool annuel est obligatoire.");
        }
        if (plafondJoursAn == null || plafondJoursAn.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le plafond de jours par an doit être supérieur ou égal à 0.");
        }
        this.code = this.code.trim().toUpperCase();

        RefPoolCongeAnnuel existant = rechercherParCode(this.code, c);
        if (existant != null && !existant.getIdPool().equals(this.idPool)) {
            throw new IllegalArgumentException("Ce code de pool est déjà utilisé.");
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO ref_pool_conge_annuel (code, libelle, plafond_jours_an) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBigDecimal(3, plafondJoursAn);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idPool = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE ref_pool_conge_annuel SET code = ?, libelle = ?, plafond_jours_an = ? WHERE id_pool = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, libelle);
            ps.setBigDecimal(3, plafondJoursAn);
            ps.setInt(4, idPool);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM ref_pool_conge_annuel WHERE id_pool = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPool);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public static RefPoolCongeAnnuel rechercherParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT id_pool, code, libelle, plafond_jours_an FROM ref_pool_conge_annuel WHERE id_pool = ?";
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

    public static RefPoolCongeAnnuel rechercherParCode(String code, Connection c) throws Exception {
        if (code == null || code.isBlank()) return null;
        String sql = "SELECT id_pool, code, libelle, plafond_jours_an FROM ref_pool_conge_annuel WHERE code = ?";
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

    public static List<RefPoolCongeAnnuel> listAll(Connection c) throws Exception {
        List<RefPoolCongeAnnuel> list = new ArrayList<>();
        String sql = "SELECT id_pool, code, libelle, plafond_jours_an FROM ref_pool_conge_annuel ORDER BY id_pool";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    /** Projection locale de la consommation annuelle d'un pool pour un employé. */
    public static final class Consommation {
        private final Integer idEmploye;
        private final Integer idPool;
        private final Integer annee;
        private final BigDecimal joursConsommes;
        private final BigDecimal joursEnAttente;
        private final BigDecimal plafond;

        public Consommation(Integer idEmploye, Integer idPool, Integer annee, BigDecimal joursConsommes,
                            BigDecimal joursEnAttente, BigDecimal plafond) {
            this.idEmploye = idEmploye;
            this.idPool = idPool;
            this.annee = annee;
            this.joursConsommes = joursConsommes != null ? joursConsommes : BigDecimal.ZERO;
            this.joursEnAttente = joursEnAttente != null ? joursEnAttente : BigDecimal.ZERO;
            this.plafond = plafond;
        }

        public Integer getIdEmploye() { return idEmploye; }
        public Integer getIdPool() { return idPool; }
        public Integer getAnnee() { return annee; }
        public BigDecimal getJoursConsommes() { return joursConsommes; }
        public BigDecimal getJoursEnAttente() { return joursEnAttente; }
        public BigDecimal getJoursEngages() { return joursConsommes.add(joursEnAttente); }
        public BigDecimal getPlafond() { return plafond; }
        public BigDecimal getJoursDisponibles() {
            return plafond != null ? plafond.subtract(joursConsommes) : null;
        }
        public BigDecimal getJoursDisponiblesApresReservations() {
            return plafond != null ? plafond.subtract(getJoursEngages()) : null;
        }
    }

    public static List<Consommation> listerConsommations(Integer idEmploye, Integer annee, Connection c) throws Exception {
        List<Consommation> list = new ArrayList<>();
        if (idEmploye == null) return list;
        int anneeEffective = annee != null ? annee : java.time.Year.now().getValue();
        String sql = "SELECT ? AS id_employe, p.id_pool, ? AS annee, "
                + "COALESCE(v.jours_consommes, 0) AS jours_consommes, "
                + "COALESCE(v.jours_en_attente, 0) AS jours_en_attente, p.plafond_jours_an "
                + "FROM ref_pool_conge_annuel p "
                + "LEFT JOIN v_pool_conge_annuel_consommation v "
                + "ON v.id_pool = p.id_pool AND v.id_employe = ? AND v.annee = ? "
                + "ORDER BY p.id_pool";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, anneeEffective);
            ps.setInt(3, idEmploye);
            ps.setInt(4, anneeEffective);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Consommation(rs.getInt("id_employe"), rs.getInt("id_pool"),
                            rs.getInt("annee"), rs.getBigDecimal("jours_consommes"),
                            rs.getBigDecimal("jours_en_attente"), rs.getBigDecimal("plafond_jours_an")));
                }
            }
        }
        return list;
    }

    public static Consommation rechercherConsommation(Integer idEmploye, Integer idPool, Integer annee,
                                                       Connection c) throws Exception {
        for (Consommation consommation : listerConsommations(idEmploye, annee, c)) {
            if (idPool.equals(consommation.getIdPool())) return consommation;
        }
        return null;
    }

    /**
     * Sérialise les mutations de quota d'un employé. Le verrou est conservé jusqu'au commit
     * de la transaction JDBC appelante et empêche deux réservations concurrentes de dépasser le pool.
     */
    public static void verrouillerQuota(Integer idEmploye, Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id_employe FROM employe WHERE id_employe = ? FOR UPDATE")) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Employé introuvable.");
            }
        }
    }

    private static RefPoolCongeAnnuel mapResultSet(ResultSet rs) throws Exception {
        RefPoolCongeAnnuel obj = new RefPoolCongeAnnuel();
        obj.setIdPool(rs.getInt("id_pool"));
        obj.setCode(rs.getString("code"));
        obj.setLibelle(rs.getString("libelle"));
        obj.setPlafondJoursAn(rs.getBigDecimal("plafond_jours_an"));
        return obj;
    }

    // Getters / Setters
    public Integer getIdPool() { return idPool; }
    public void setIdPool(Integer idPool) { this.idPool = idPool; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public BigDecimal getPlafondJoursAn() { return plafondJoursAn; }
    public void setPlafondJoursAn(BigDecimal plafondJoursAn) { this.plafondJoursAn = plafondJoursAn; }
}
