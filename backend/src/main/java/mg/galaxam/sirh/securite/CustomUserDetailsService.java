package mg.galaxam.sirh.securite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import mg.galaxam.sirh.model.admin.RefRole;
import mg.galaxam.sirh.model.securite.Utilisateur;
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
            
            // 1. Recherche de l'utilisateur par son identifiant
            Utilisateur u = Utilisateur.rechercherParIdentifiant(username, c);
            if (u == null) {
                throw new UsernameNotFoundException("Utilisateur non trouvé avec l'identifiant : " + username);
            }

            Set<GrantedAuthority> authorities = new HashSet<>();

            // 2. Récupération et normalisation du rôle
            String codeRole = "ROLE_USER";
            if (u.getIdRole() != null) {
                RefRole r = new RefRole();
                RefRole role = (RefRole) r.findById(String.valueOf(u.getIdRole()), c);
                if (role != null && role.getCodeRole() != null && !role.getCodeRole().isBlank()) {
                    String dbRole = role.getCodeRole().trim();
                    codeRole = dbRole.startsWith("ROLE_") ? dbRole : "ROLE_" + dbRole;
                }
            }
            authorities.add(new SimpleGrantedAuthority(codeRole));

            // 3. Récupération dynamique de la matrice des autorisations granulaires depuis role_permission
            if (u.getIdRole() != null) {
                String sqlPerms = "SELECT UPPER(m.code_module) AS code_module, rp.droit_lecture, rp.droit_ecriture, rp.droit_validation " +
                        "FROM role_permission rp " +
                        "JOIN ref_module m ON rp.id_module = m.id_module " +
                        "WHERE rp.id_role = ? AND m.est_actif = TRUE";
                try (PreparedStatement ps = c.prepareStatement(sqlPerms)) {
                    ps.setInt(1, u.getIdRole());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String modCode = rs.getString("code_module");
                            boolean canRead = rs.getBoolean("droit_lecture");
                            boolean canWrite = rs.getBoolean("droit_ecriture");
                            boolean canValidate = rs.getBoolean("droit_validation");

                            if (canRead) authorities.add(new SimpleGrantedAuthority(modCode + "_READ"));
                            if (canWrite) authorities.add(new SimpleGrantedAuthority(modCode + "_WRITE"));
                            if (canValidate) authorities.add(new SimpleGrantedAuthority(modCode + "_VALIDATE"));
                        }
                    }
                }
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
                    u.getIdUtilisateur(),
                    u.getIdentifiant(),
                    u.getMotDePasseHache(),
                    authorities,
                    u.isEstActif()
            );

        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UsernameNotFoundException("Erreur système lors de la récupération de l'utilisateur " + username, e);
        }
    }
}
