package mg.galaxam.sirh.fixtures;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import mg.galaxam.sirh.securite.CustomUserDetailsService;
import mg.galaxam.sirh.securite.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TestUserFixtures {

    public static final String ADMIN_EMAIL = "daf@galaxam.com";
    public static final String ADMIN_RAW_PASSWORD = "TnrGalLog2605";

    public static final String POINTAGE_EMAIL = "pointage@galaxam.com";
    public static final String POINTAGE_RAW_PASSWORD = "PointageGal2605";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    public void initTestUsers() {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Assurer la présence des rôles
                int idRoleAdmin = resolveOrInsertRole(c, "ROLE_ADMIN", "Administrateur Système");
                int idRolePointage = resolveOrInsertRole(c, "ROLE_AGENT_POINTAGE", "Agent de Pointage");

                // 2. Assurer les permissions pour ROLE_AGENT_POINTAGE sur le module POINTAGE
                int idModulePointage = resolveModuleId(c, "POINTAGE");
                if (idModulePointage > 0) {
                    upsertRolePermission(c, idRolePointage, idModulePointage, true, true, false);
                }

                // 3. Upsert du compte Administrateur / DAF
                upsertUser(c, ADMIN_EMAIL, ADMIN_RAW_PASSWORD, idRoleAdmin);

                // 4. Upsert du compte Agent de Pointage
                upsertUser(c, POINTAGE_EMAIL, POINTAGE_RAW_PASSWORD, idRolePointage);

                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Erreur initialisation des utilisateurs de test : " + e.getMessage(), e);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getAdminToken() {
        initTestUsers();
        UserDetails user = userDetailsService.loadUserByUsername(ADMIN_EMAIL);
        return jwtService.generateAccessToken(user);
    }

    public String getPointageToken() {
        initTestUsers();
        UserDetails user = userDetailsService.loadUserByUsername(POINTAGE_EMAIL);
        return jwtService.generateAccessToken(user);
    }

    public int getAdminUserId() {
        initTestUsers();
        return getUserIdByIdentifiant(ADMIN_EMAIL);
    }

    public int getPointageUserId() {
        initTestUsers();
        return getUserIdByIdentifiant(POINTAGE_EMAIL);
    }

    private int getUserIdByIdentifiant(String identifiant) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_utilisateur FROM utilisateur WHERE identifiant = ?")) {
            ps.setString(1, identifiant);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("Utilisateur non trouvé : " + identifiant);
    }

    public UserDetails getAdminUserDetails() {
        initTestUsers();
        return userDetailsService.loadUserByUsername(ADMIN_EMAIL);
    }

    public UserDetails getPointageUserDetails() {
        initTestUsers();
        return userDetailsService.loadUserByUsername(POINTAGE_EMAIL);
    }

    private int resolveOrInsertRole(Connection c, String codeRole, String libelle) throws Exception {
        String sqlSelect = "SELECT id_role FROM ref_role WHERE code_role = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlSelect)) {
            ps.setString(1, codeRole);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id_role");
                }
            }
        }

        String sqlInsert = "INSERT INTO ref_role (code_role, libelle, est_actif) VALUES (?, ?, TRUE) RETURNING id_role";
        try (PreparedStatement ps = c.prepareStatement(sqlInsert)) {
            ps.setString(1, codeRole);
            ps.setString(2, libelle);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Impossible de créer le rôle " + codeRole);
    }

    private int resolveModuleId(Connection c, String codeModule) throws Exception {
        String sql = "SELECT id_module FROM ref_module WHERE code_module = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeModule);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id_module");
            }
        }
        return -1;
    }

    private void upsertRolePermission(Connection c, int idRole, int idModule, boolean read, boolean write, boolean validate) throws Exception {
        String sql = "INSERT INTO role_permission (id_role, id_module, droit_lecture, droit_ecriture, droit_validation) " +
                     "VALUES (?, ?, ?, ?, ?) " +
                     "ON CONFLICT (id_role, id_module) DO UPDATE SET " +
                     "droit_lecture = EXCLUDED.droit_lecture, " +
                     "droit_ecriture = EXCLUDED.droit_ecriture, " +
                     "droit_validation = EXCLUDED.droit_validation";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRole);
            ps.setInt(2, idModule);
            ps.setBoolean(3, read);
            ps.setBoolean(4, write);
            ps.setBoolean(5, validate);
            ps.executeUpdate();
        }
    }

    private void upsertUser(Connection c, String email, String rawPassword, int idRole) throws Exception {
        String hash = passwordEncoder.encode(rawPassword);
        String sql = "INSERT INTO utilisateur (identifiant, mot_de_passe_hache, id_role, est_actif, updated_at) " +
                     "VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT (identifiant) DO UPDATE SET " +
                     "mot_de_passe_hache = EXCLUDED.mot_de_passe_hache, " +
                     "id_role = EXCLUDED.id_role, " +
                     "est_actif = TRUE, " +
                     "date_desactivation = NULL, " +
                     "updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, hash);
            ps.setInt(3, idRole);
            ps.executeUpdate();
        }
    }
}
