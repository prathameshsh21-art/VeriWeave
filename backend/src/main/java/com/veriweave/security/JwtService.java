package com.veriweave.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${veriweave.jwt.secret}")
    private String jwtSecret;

    @Value("${veriweave.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${veriweave.jwt.refresh-expiration-ms}")
    private long jwtRefreshExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object userId = claims.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateAccessToken(Long userId, String email, String fullName) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", userId);
        extraClaims.put("fullName", fullName);
        extraClaims.put("tokenType", "ACCESS");
        return buildToken(extraClaims, email, jwtExpirationMs);
    }

    public String generateRefreshToken(Long userId, String email) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", userId);
        extraClaims.put("tokenType", "REFRESH");
        return buildToken(extraClaims, email, jwtRefreshExpirationMs);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractTokenType(String token) {
        Claims claims = extractAllClaims(token);
        Object type = claims.get("tokenType");
        return type != null ? type.toString() : null;
    }

    public boolean isAccessToken(String token, String username) {
        if (!isTokenValid(token, username)) {
            return false;
        }
        return "ACCESS".equalsIgnoreCase(extractTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        if (isTokenExpired(token)) {
            return false;
        }
        return "REFRESH".equalsIgnoreCase(extractTokenType(token));
    }

    public boolean isTokenValid(String token, String username) {
        try {
            final String extractedUser = extractUsername(token);
            return (extractedUser != null && extractedUser.equals(username) && !isTokenExpired(token));
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (Exception ex) {
            return true;
        }
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
}
