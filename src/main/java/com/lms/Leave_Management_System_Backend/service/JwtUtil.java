package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.dto.UserDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access_expiration_seconds}")
    private long accessExpirySeconds;

    @Value("${jwt.refresh_expiration_seconds}")
    private long refreshExpirySeconds;

    private Key key;

    @Autowired
    private JwtBlacklistService jwtBlacklistService;

    @PostConstruct
    public void init() {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(secretBytes, 0, padded, 0, secretBytes.length);
            secretBytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(java.util.Base64.getEncoder().encodeToString(secretBytes)));
    }

    public String generateAccessToken(UserDto user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("name", user.getName());
        claims.put("role", user.getRole());

        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessExpirySeconds * 1000L);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String email) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshExpirySeconds * 1000L);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            log.debug("Token validation failed: null or blank token");
            return false;
        }
        
        // Check if token is blacklisted
        if (jwtBlacklistService != null) {
            boolean isBlacklisted = jwtBlacklistService.isTokenBlacklisted(token);
            if (isBlacklisted) {
                log.warn("Token validation failed: token is blacklisted");
                return false;
            }
        } else {
            log.debug("JwtBlacklistService is not available - blacklist check skipped");
        }
        
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            log.debug("Token validation successful");
            return true;
        } catch (Exception ex) {
            log.warn("Token validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String extractUserId(String token) {
        try {
            return getEmailFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the remaining time in seconds until token expiration
     * @param token The JWT token
     * @return Remaining seconds, or 0 if token is invalid/expired
     */
    public long getRemainingExpirationTime(String token) {
        try {
            Claims claims = getClaims(token);
            Date expiration = claims.getExpiration();
            Date now = new Date();
            long remainingMillis = expiration.getTime() - now.getTime();
            return Math.max(0, remainingMillis / 1000);
        } catch (Exception e) {
            return 0;
        }
    }
}
