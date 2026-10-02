package mg.galaxam.sirh.securite;

import java.util.Collection;
import java.util.Collections;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {

    private final Integer idUtilisateur;
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean active;

    public CustomUserDetails(Integer idUtilisateur, String username, String password, Collection<? extends GrantedAuthority> authorities, boolean active) {
        this.idUtilisateur = idUtilisateur;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
        this.active = active;
    }

    public CustomUserDetails(Integer idUtilisateur, String username, String password, String role, boolean active) {
        this(idUtilisateur, username, password, Collections.singletonList(new SimpleGrantedAuthority(role)), active);
    }

    public Integer getIdUtilisateur() {
        return idUtilisateur;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public static Integer getCurrentUserId() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) {
            return user.getIdUtilisateur();
        }
        return 1;
    }
}

