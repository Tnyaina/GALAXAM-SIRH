package mg.galaxam.sirh.securite;

import mg.galaxam.sirh.referentiel.RefRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;

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

            // 3. Construction de l'objet CustomUserDetails pour conserver l'ID en mémoire
            return new CustomUserDetails(
                    u.getIdUtilisateur(),
                    u.getIdentifiant(),
                    u.getMotDePasseHache(),
                    codeRole,
                    u.isEstActif()
            );

        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UsernameNotFoundException("Erreur système lors de la récupération de l'utilisateur " + username, e);
        }
    }
}