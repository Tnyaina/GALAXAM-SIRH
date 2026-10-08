package mg.galaxam.sirh.securite;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class Utilisateur extends ClassMere {

    private Integer idUtilisateur;
    private String identifiant;
    private String email;
    private String motDePasseHache;
    private Integer idRole; // Clé étrangère vers RefRole
    private boolean estActif;
    private Timestamp dateDesactivation;
    private String motifDesactivation;
    private String refreshToken;
    private Timestamp refreshTokenExpiration;

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

        identifiant = identifiant.trim();
        if (identifiant.length() > 100) throw new IllegalArgumentException("Le nom d'affichage ne peut pas dépasser 100 caractères.");

        if (email == null || email.isBlank()) throw new IllegalArgumentException("L'adresse e-mail est obligatoire.");
        email = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("L'adresse e-mail est invalide.");
        Utilisateur existant = rechercherParEmail(email, c);
        if (existant != null && !existant.getIdUtilisateur().equals(this.idUtilisateur)) {
            throw new IllegalArgumentException("Cette adresse e-mail est déjà utilisée par un autre compte.");
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
                "(identifiant, email, mot_de_passe_hache, id_role, est_actif, motif_desactivation, date_desactivation, refresh_token, refresh_token_expiration) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, identifiant);
            ps.setString(2, email);
            ps.setString(3, motDePasseHache);
            ps.setInt(4, idRole);
            ps.setBoolean(5, estActif);
            ps.setString(6, motifDesactivation);
            ps.setTimestamp(7, dateDesactivation);
            ps.setString(8, refreshToken);
            ps.setTimestamp(9, refreshTokenExpiration);

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
                "identifiant=?, email=?, " +
                "mot_de_passe_hache=?, " +
                "id_role=?, " +
                "est_actif=?, " +
                "motif_desactivation=?, " +
                "date_desactivation=?, " +
                "refresh_token=?, " +
                "refresh_token_expiration=?, " +
                "updated_at=CURRENT_TIMESTAMP " +
                "WHERE id_utilisateur=?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, identifiant);
            ps.setString(2, email);
            ps.setString(3, motDePasseHache);
            ps.setInt(4, idRole);
            ps.setBoolean(5, estActif);
            ps.setString(6, motifDesactivation);
            ps.setTimestamp(7, dateDesactivation);
            ps.setString(8, refreshToken);
            ps.setTimestamp(9, refreshTokenExpiration);
            ps.setInt(10, idUtilisateur);

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
        String sql = "SELECT * FROM utilisateur WHERE LOWER(identifiant) = LOWER(?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login != null ? login.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static Utilisateur rechercherParEmail(String email, Connection c) throws Exception {
        String sql = "SELECT * FROM utilisateur WHERE LOWER(email) = LOWER(?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email != null ? email.trim() : "");
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return mapResultSet(rs); }
        }
        return null;
    }

    public static Utilisateur rechercherParRefreshToken(String token, Connection c) throws Exception {
        if (token == null || token.isBlank()) return null;
        String sql = "SELECT * FROM utilisateur WHERE refresh_token = ? AND est_actif = TRUE AND (refresh_token_expiration IS NULL OR refresh_token_expiration > CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static void enregistrerRefreshToken(int idUtilisateur, String token, Timestamp expiration, Connection c) throws Exception {
        String sql = "UPDATE utilisateur SET refresh_token = ?, refresh_token_expiration = ?, updated_at = CURRENT_TIMESTAMP WHERE id_utilisateur = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setTimestamp(2, expiration);
            ps.setInt(3, idUtilisateur);
            ps.executeUpdate();
        }
    }

    public static void revoquerRefreshToken(int idUtilisateur, Connection c) throws Exception {
        String sql = "UPDATE utilisateur SET refresh_token = NULL, refresh_token_expiration = NULL, updated_at = CURRENT_TIMESTAMP WHERE id_utilisateur = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idUtilisateur);
            ps.executeUpdate();
        }
    }

    private static Utilisateur mapResultSet(ResultSet rs) throws Exception {
        Utilisateur u = new Utilisateur();
        u.idUtilisateur = rs.getInt("id_utilisateur");
        u.identifiant = rs.getString("identifiant");
        u.email = rs.getString("email");
        u.motDePasseHache = rs.getString("mot_de_passe_hache");
        u.idRole = rs.getInt("id_role");
        u.estActif = rs.getBoolean("est_actif");
        u.motifDesactivation = rs.getString("motif_desactivation");
        u.dateDesactivation = rs.getTimestamp("date_desactivation");
        u.refreshToken = rs.getString("refresh_token");
        u.refreshTokenExpiration = rs.getTimestamp("refresh_token_expiration");
        u.motDePasseHash = true;
        return u;
    }

    // Méthodes de sélection
    public static List<Utilisateur> lister(Connection c) throws Exception {
        String sql = "SELECT * FROM utilisateur ORDER BY identifiant";
        List<Utilisateur> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    // Getters & Setters
    public Integer getIdUtilisateur() {
        return idUtilisateur;
    }

    public void setIdUtilisateur(Integer idUtilisateur) {
        this.idUtilisateur = idUtilisateur;
    }

    public String getIdentifiant() {
        return identifiant;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

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

    public void setMotDePasseHache(String motDePasseHache) {
        this.motDePasseHache = motDePasseHache;
    }

    public Timestamp getDateDesactivation() {
        return dateDesactivation;
    }

    public void setDateDesactivation(Timestamp dateDesactivation) {
        this.dateDesactivation = dateDesactivation;
    }

    public String getMotifDesactivation() {
        return motifDesactivation;
    }

    public void setMotifDesactivation(String motifDesactivation) {
        this.motifDesactivation = motifDesactivation;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Timestamp getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public void setRefreshTokenExpiration(Timestamp refreshTokenExpiration) {
        this.refreshTokenExpiration = refreshTokenExpiration;
    }
}
