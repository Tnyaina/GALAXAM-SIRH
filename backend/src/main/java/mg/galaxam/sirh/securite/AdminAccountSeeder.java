package mg.galaxam.sirh.securite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import mg.galaxam.sirh.admin.persistence.RefRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Initialisation automatique du premier compte Administrateur.
 *
 * <p>Le bootstrap est idempotent : il ne s'exécute réellement que lorsqu'aucun
 * administrateur actif n'est présent en base. Les démarrages suivants ne modifient
 * donc ni les comptes ni les mots de passe déjà configurés.</p>
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.security.bootstrap-admin.enabled", havingValue = "true")
public class AdminAccountSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminAccountSeeder.class);

    @Autowired
    private DataSource dataSource;

    @Value("${app.security.default-admin.username:}")
    private String defaultAdminUsername;

    @Value("${app.security.default-admin.password:}")
    private String defaultAdminPassword;

    @Override
    public void run(String... args) {
        if (defaultAdminUsername == null || defaultAdminUsername.isBlank()
                || defaultAdminPassword == null || defaultAdminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap administrateur activé sans GALAXAM_DEFAULT_ADMIN_USERNAME/GALAXAM_DEFAULT_ADMIN_PASSWORD.");
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Une base déjà administrable ne doit jamais être modifiée au démarrage.
                String checkAdminSql =
                    "SELECT COUNT(u.id_utilisateur) FROM utilisateur u " +
                    "JOIN ref_role r ON u.id_role = r.id_role " +
                    "WHERE r.code_role = 'ROLE_ADMIN' AND u.est_actif = true";

                boolean adminExists = false;
                try (PreparedStatement ps = c.prepareStatement(checkAdminSql)) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            adminExists = true;
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("[AdminAccountSeeder] Table utilisateur non accessible ou non initialisée : {}", ex.getMessage());
                    c.rollback();
                    return;
                }

                if (adminExists) {
                    logger.info("[AdminAccountSeeder] Un compte administrateur actif est déjà présent en base ; bootstrap ignoré.");
                    c.rollback();
                    return;
                }

                logger.warn("[AdminAccountSeeder] Aucun administrateur actif trouvé. Initialisation automatique du compte racine...");

                // 2. Trouver ou créer le rôle ROLE_ADMIN
                RefRole adminRole = RefRole.rechercherParCode("ROLE_ADMIN", c);
                if (adminRole == null) {
                    adminRole = new RefRole();
                    adminRole.setCodeRole("ROLE_ADMIN");
                    adminRole.setLibelle("Administrateur Système");
                    adminRole.setEstActif(true);
                    adminRole.save(c);
                    logger.info("[AdminAccountSeeder] Rôle ROLE_ADMIN créé automatiquement (ID={}).", adminRole.getIdRole());
                }

                // 3. Vérifier si un compte avec l'identifiant par défaut existe déjà (désactivé ou sans rôle admin)
                Utilisateur existingUser = Utilisateur.rechercherParIdentifiant(defaultAdminUsername, c);
                if (existingUser != null) {
                    existingUser.setIdRole(adminRole.getIdRole());
                    existingUser.setEstActif(true);
                    existingUser.setMotDePasseEnClair(defaultAdminPassword);
                    existingUser.controler(c);
                    existingUser.save(c);
                    logger.info("[AdminAccountSeeder] Compte existant '{}' réactivé et promu administrateur avec succès.", defaultAdminUsername);
                } else {
                    Utilisateur newUser = new Utilisateur();
                    newUser.setIdentifiant(defaultAdminUsername);
                    newUser.setMotDePasseEnClair(defaultAdminPassword);
                    newUser.setIdRole(adminRole.getIdRole());
                    newUser.setEstActif(true);
                    newUser.controler(c);
                    newUser.save(c);
                    logger.info("[AdminAccountSeeder] Compte administrateur racine créé avec succès (identifiant: '{}').", defaultAdminUsername);
                }

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                logger.error("[AdminAccountSeeder] Échec de l'initialisation du compte administrateur : {}", ex.getMessage(), ex);
            }
        } catch (Exception e) {
            logger.error("[AdminAccountSeeder] Erreur de connexion lors du seed administrateur : {}", e.getMessage(), e);
        }
    }
}
