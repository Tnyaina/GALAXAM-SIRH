package mg.galaxam.sirh.securite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private DataSource dataSource;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try (Connection c = dataSource.getConnection()) {
            
            Set<GrantedAuthority> authorities = new HashSet<>();
            String sql = "SELECT u.id_utilisateur, u.identifiant, u.email, u.mot_de_passe_hache, u.est_actif, "
                    + "r.code_role, UPPER(m.code_module) AS code_module, "
                    + "rp.droit_lecture, rp.droit_ecriture, rp.droit_validation "
                    + "FROM utilisateur u "
                    + "LEFT JOIN ref_role r ON r.id_role = u.id_role "
                    + "LEFT JOIN role_permission rp ON rp.id_role = u.id_role "
                    + "LEFT JOIN ref_module m ON m.id_module = rp.id_module AND m.est_actif = TRUE "
                    + "WHERE u.id_utilisateur = (SELECT matched.id_utilisateur FROM utilisateur matched "
                    + "WHERE LOWER(matched.email) = LOWER(?) ORDER BY matched.id_utilisateur LIMIT 1)";

            Integer userId = null;
            String identifiant = null;
            String email = null;
            String passwordHash = null;
            boolean active = false;
            String codeRole = "ROLE_USER";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username != null ? username.trim() : "");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (userId == null) {
                            userId = rs.getInt("id_utilisateur");
                            identifiant = rs.getString("identifiant");
                            email = rs.getString("email");
                            passwordHash = rs.getString("mot_de_passe_hache");
                            active = rs.getBoolean("est_actif");

                            String dbRole = rs.getString("code_role");
                            if (dbRole != null && !dbRole.isBlank()) {
                                String normalizedRole = dbRole.trim();
                                codeRole = normalizedRole.startsWith("ROLE_")
                                        ? normalizedRole : "ROLE_" + normalizedRole;
                            }
                            authorities.add(new SimpleGrantedAuthority(codeRole));
                        }

                        String moduleCode = rs.getString("code_module");
                        if (moduleCode != null) {
                            if (rs.getBoolean("droit_lecture")) {
                                authorities.add(new SimpleGrantedAuthority(moduleCode + "_READ"));
                            }
                            if (rs.getBoolean("droit_ecriture")) {
                                authorities.add(new SimpleGrantedAuthority(moduleCode + "_WRITE"));
                            }
                            if (rs.getBoolean("droit_validation")) {
                                authorities.add(new SimpleGrantedAuthority(moduleCode + "_VALIDATE"));
                            }
                        }
                    }
                }
            }
            if (userId == null) {
                throw new UsernameNotFoundException("Utilisateur non trouvé avec l'identifiant : " + username);
            }

            // 4. Filet de sécurité pour le rôle ADMIN : toujours accorder tous les droits de base
            if (codeRole.contains("ADMIN")) {
                for (String mod : new String[]{"EMPLOYE", "POINTAGE", "PAIE", "CONGE", "ORGANISATION"}) {
                    authorities.add(new SimpleGrantedAuthority(mod + "_READ"));
                    authorities.add(new SimpleGrantedAuthority(mod + "_WRITE"));
                    authorities.add(new SimpleGrantedAuthority(mod + "_VALIDATE"));
                }
            }

            // 5. Construction de l'objet CustomUserDetails avec ses autorisations granulaires
            return new CustomUserDetails(
                    userId,
                    email,
                    passwordHash,
                    authorities,
                    active,
                    identifiant
            );

        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UsernameNotFoundException("Erreur système lors de la récupération de l'utilisateur " + username, e);
        }
    }
}
