package mg.galaxam.sirh.securite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private DataSource dataSource;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try (Connection c = dataSource.getConnection()) {
            Utilisateur u = Utilisateur.rechercherParIdentifiant(username, c);
            if (u == null) {
                throw new UsernameNotFoundException("Utilisateur non trouvé avec l'identifiant : " + username);
            }
            if (!u.isEstActif()) {
                throw new UsernameNotFoundException("Le compte utilisateur est désactivé : " + username);
            }

            String codeRole = "ROLE_USER";
            if (u.getIdRole() != null) {
                RefRole r = new RefRole();
                RefRole role = (RefRole) r.findById(String.valueOf(u.getIdRole()), c);
                if (role != null && role.getCodeRole() != null) {
                    codeRole = role.getCodeRole();
                }
            }

            return org.springframework.security.core.userdetails.User.builder()
                    .username(u.getIdentifiant())
                    .password(u.getMotDePasseHache())
                    .authorities(codeRole)
                    .build();
        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UsernameNotFoundException("Erreur lors de la récupération de l'utilisateur " + username, e);
        }
    }
}
