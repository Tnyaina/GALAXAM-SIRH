package mg.galaxam.sirh.securite;

import bean.ClassMere;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.*;

public class Utilisateur extends ClassMere {

    private Integer idUtilisateur;
    private String identifiant;
    private String motDePasseHache;
    private Integer idRole; // Clé étrangère vers RefRole
    private boolean estActif;
    private Timestamp dateDesactivation;
    private String motifDesactivation;

    private boolean motDePasseHash = false;
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public Utilisateur() {
        this.setNomTable("UTILISATEUR");
        this.estActif = true;
    }

    @Override
    public Integer getTuppleID() {
        return this.idUtilisateur;
    }

    @Override
    public String getAttributIDName() {
        return "id_utilisateur";
    }

    // =====================================================
    // CONTROLE METIER
    // =====================================================

    @Override
    public void controler(Connection c) throws Exception {

        if (identifiant == null || identifiant.isBlank()) {
            throw new IllegalArgumentException("L'identifiant est obligatoire.");
        }

        Utilisateur existant = rechercherParIdentifiant(this.identifiant, c);
        if (existant != null && !existant.getIdUtilisateur().equals(this.idUtilisateur)) {
            throw new IllegalArgumentException("Cet identifiant est déjà utilisé par un autre compte.");
        }

        identifiant = identifiant.trim();

        if (identifiant.length() < 3 || identifiant.length() > 50) {
            throw new IllegalArgumentException("L'identifiant doit contenir entre 3 et 50 caractères.");
        }

        if (!identifiant.matches("^[A-Za-z0-9._-]+$")) {
            throw new IllegalArgumentException("L'identifiant contient des caractères invalides.");
        }

        if (idRole == null) {
            throw new IllegalArgumentException("Le rôle de l'utilisateur est obligatoire.");
        }

        if (motDePasseHache == null || motDePasseHache.isBlank()) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire.");
        }

        if (!motDePasseHash) {
            verifierComplexiteMotDePasse(motDePasseHache);
            this.motDePasseHache = encoder.encode(this.motDePasseHache);
            this.motDePasseHash = true;
        }
    }

    private void verifierComplexiteMotDePasse(String mdp) {
        if (mdp.length() < 8) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins 8 caractères.");
        }
        if (!mdp.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Le mot de passe doit contenir une majuscule.");
        }
        if (!mdp.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Le mot de passe doit contenir une minuscule.");
        }
        if (!mdp.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Le mot de passe doit contenir un chiffre.");
        }
    }

    // =====================================================
    // GESTION MOT DE PASSE
    // =====================================================

    public void setMotDePasseEnClair(String mdpEnClair) {
        this.motDePasseHache = mdpEnClair;
        this.motDePasseHash = false;
    }

    public boolean verifierMotDePasse(String mdpSaisi) {
        if (mdpSaisi == null || motDePasseHache == null) {
            return false;
        }
        return encoder.matches(mdpSaisi, motDePasseHache);
    }

    // =====================================================
    // PERSISTANCE (Alignée avec le script SQL)
    // =====================================================

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO utilisateur " +
                "(identifiant, mot_de_passe_hache, id_role, est_actif, motif_desactivation, date_desactivation) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, identifiant);
            ps.setString(2, motDePasseHache);
            ps.setInt(3, idRole);
            ps.setBoolean(4, estActif);
            ps.setString(5, motifDesactivation);
            ps.setTimestamp(6, dateDesactivation);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idUtilisateur = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE utilisateur SET " +
                "identifiant=?, " +
                "mot_de_passe_hache=?, " +
                "id_role=?, " +
                "est_actif=?, " +
                "motif_desactivation=?, " +
                "date_desactivation=?, " +
                "updated_at=CURRENT_TIMESTAMP " +
                "WHERE id_utilisateur=?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, identifiant);
            ps.setString(2, motDePasseHache);
            ps.setInt(3, idRole);
            ps.setBoolean(4, estActif);
            ps.setString(5, motifDesactivation);
            ps.setTimestamp(6, dateDesactivation);
            ps.setInt(7, idUtilisateur);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM utilisateur WHERE id_utilisateur=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idUtilisateur);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String idRecherche, Connection c) throws Exception {
        return rechercherParId(Integer.parseInt(idRecherche), c);
    }

    public Utilisateur rechercherParId(int idRecherche, Connection c) throws Exception {
        String sql = "SELECT * FROM utilisateur WHERE id_utilisateur=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRecherche);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static Utilisateur rechercherParIdentifiant(String login, Connection c) throws Exception {
        String sql = "SELECT * FROM utilisateur WHERE identifiant=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    private static Utilisateur mapResultSet(ResultSet rs) throws Exception {
        Utilisateur u = new Utilisateur();
        u.idUtilisateur = rs.getInt("id_utilisateur");
        u.identifiant = rs.getString("identifiant");
        u.motDePasseHache = rs.getString("mot_de_passe_hache");
        u.idRole = rs.getInt("id_role");
        u.estActif = rs.getBoolean("est_actif");
        u.motifDesactivation = rs.getString("motif_desactivation");
        u.dateDesactivation = rs.getTimestamp("date_desactivation");
        u.motDePasseHash = true;
        return u;
    }

    // Getters & Setters
    public Integer getIdUtilisateur() {
        return idUtilisateur;
    }

    public String getIdentifiant() {
        return identifiant;
    }

    public void setIdentifiant(String identifiant) {
        this.identifiant = identifiant;
    }

    public Integer getIdRole() {
        return idRole;
    }

    public void setIdRole(Integer idRole) {
        this.idRole = idRole;
    }

    public boolean isEstActif() {
        return estActif;
    }

    public void setEstActif(boolean estActif) {
        this.estActif = estActif;
    }

    public String getMotDePasseHache() {
        return motDePasseHache;
    }
}