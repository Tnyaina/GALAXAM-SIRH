package mg.galaxam.sirh.model.paie.workflow;

import java.util.Set;
import java.util.stream.Collectors;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.security.core.Authentication;

public record PayrollPeriodUserContext(Integer userId, Set<String> authorities) {

    public static PayrollPeriodUserContext from(Authentication authentication) {
        Integer id = authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details
                ? details.getIdUtilisateur()
                : null;
        Set<String> granted = authentication == null
                ? Set.of()
                : authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .collect(Collectors.toUnmodifiableSet());
        return new PayrollPeriodUserContext(id, granted);
    }

    public boolean hasAny(String... expected) {
        if (authorities.contains("ROLE_ADMIN")) return true;
        for (String authority : expected) {
            if (authorities.contains(authority)) return true;
        }
        return false;
    }
}
