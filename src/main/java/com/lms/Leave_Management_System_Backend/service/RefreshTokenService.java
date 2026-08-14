package com.lms.Leave_Management_System_Backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RefreshTokenService {

    private static class TokenEntry {
        private final String email;
        private final Instant expiresAt;

        private TokenEntry(String email, Instant expiresAt) {
            this.email = email;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, TokenEntry> store = new ConcurrentHashMap<>();

    @Value("${jwt.refresh_expiration_seconds}")
    private long refreshExpirySeconds;

    public void store(String token, String email) {
        if (token == null || email == null) {
            return;
        }
        store.put(token, new TokenEntry(email.toLowerCase(), Instant.now().plusSeconds(refreshExpirySeconds)));
    }

    public boolean validate(String token, String email) {
        if (token == null || email == null) {
            return false;
        }
        TokenEntry entry = store.get(token);
        if (entry == null) {
            return false;
        }
        if (!entry.email.equalsIgnoreCase(email.toLowerCase())) {
            return false;
        }
        if (Instant.now().isAfter(entry.expiresAt)) {
            store.remove(token);
            return false;
        }
        return true;
    }

    public void revoke(String token) {
        if (token != null) {
            store.remove(token);
        }
    }

    public void revokeByEmail(String email) {
        if (email == null) {
            return;
        }
        String normalizedEmail = email.toLowerCase();
        store.entrySet().removeIf(entry -> entry.getValue().email.equals(normalizedEmail));
    }
}
