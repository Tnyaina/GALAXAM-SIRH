package mg.galaxam.sirh.config;

import java.util.Arrays;
import jakarta.annotation.PostConstruct;
import mg.galaxam.sirh.securite.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    public static final String ROLE_ADMIN = "ADMIN";

    private static final String[] PUBLIC_ENDPOINTS = {
        "/auth/login",
        "/auth/refresh",
        "/auth/logout",
        "/error"
    };

    /**
     * Valide la configuration CORS au démarrage.
     * Rejette : origines null/blank, "*" exact, tout pattern contenant '*' ou '?'
     * (qui permettraient des wildcards sous-domaine avec allowCredentials=true).
     */
    @PostConstruct
    public void validateCorsConfiguration() {
        if (allowedOrigins == null || allowedOrigins.length == 0) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins doit contenir au moins une origine explicite.");
        }
        for (String origin : allowedOrigins) {
            if (origin == null || origin.isBlank()) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins contient une origine null ou vide.");
            }
            if ("*".equals(origin.trim())) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins ne doit pas contenir '*' lorsque les credentials sont autorisés.");
            }
            // Rejeter tout pattern avec wildcard (*, ?, [) qui permettrait une origine non exacte
            if (origin.contains("*") || origin.contains("?") || origin.contains("[")) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins contient un wildcard non autorisé ('" + origin + "'). "
                        + "Seules les origines exactes sont permises avec allowCredentials=true.");
            }
        }
    }

    private static final String[] ADMIN_ENDPOINTS = {
        "/auth/register",
        "/admin/**",
        "/securite/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
            )
            .authorizeHttpRequests(auth -> auth
                // 1. Accès publics sans authentification
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                // 2. Administration système et sécurité IAM (Super-Admin uniquement)
                .requestMatchers(ADMIN_ENDPOINTS).hasRole(ROLE_ADMIN)

                // 3. Tout le reste de l'API requiert une authentification valide
                // (La finesse READ/WRITE/VALIDATE est assurée par @PreAuthorize et la hiérarchie de rôles)
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Hiérarchie de sécurité officielle Spring Security :
     * Le rôle ADMIN hérite automatiquement de toutes les permissions filles.
     */
    @Bean
    public org.springframework.security.access.hierarchicalroles.RoleHierarchy roleHierarchy() {
        return org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl.withDefaultRolePrefix()
            .role(ROLE_ADMIN).implies(
                "EMPLOYE_READ", "EMPLOYE_WRITE", "EMPLOYE_VALIDATE",
                "CONGE_READ", "CONGE_WRITE", "CONGE_VALIDATE",
                "POINTAGE_READ", "POINTAGE_WRITE", "POINTAGE_VALIDATE",
                "PAIE_READ", "PAIE_WRITE", "PAIE_VALIDATE",
                "ORGANISATION_READ", "ORGANISATION_WRITE"
            )
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // P1-04 : setAllowedOrigins (liste exacte) remplace setAllowedOriginPatterns
        // qui acceptait les wildcards (https://*.example.com) avec allowCredentials=true.
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(java.util.List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
