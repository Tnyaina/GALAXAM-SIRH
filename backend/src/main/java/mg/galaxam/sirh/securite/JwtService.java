package mg.galaxam.sirh.securite;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class JwtService {

    @Value("${app.security.jwt.secret}")
    private String secretKeyString;

    @Value("${app.security.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${app.security.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretKeyString);
        } catch (Exception e) {
            keyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 64) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-512");
                keyBytes = md.digest(keyBytes);
            } catch (NoSuchAlgorithmException e) {
                // Ignore fallback
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        String role = userDetails.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .orElse("ROLE_USER");

        java.util.List<String> permissions = userDetails.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .collect(java.util.stream.Collectors.toList());

        extraClaims.put("role", role);
        extraClaims.put("permissions", permissions);
        return generateAccessToken(extraClaims, userDetails);
    }

    public String generateAccessToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }
}
