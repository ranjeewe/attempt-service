package com.mcqbuddy.attempt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class BearerRoleChecker {

    @Value("${mcqbuddy.auth.jwt.secret}")
    private String jwtSecret;

    public boolean isStaff(String authorizationHeader) {
        Claims claims = parseClaims(authorizationHeader);
        if (claims == null) {
            return false;
        }
        Object rolesClaim = claims.get("roles");
        if (!(rolesClaim instanceof List<?> roles)) {
            return false;
        }
        for (Object role : roles) {
            if (role instanceof String roleName) {
                String normalized = roleName.trim().toUpperCase();
                if ("TEACHER".equals(normalized) || "ADMIN".equals(normalized)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String callerPublicId(String authorizationHeader) {
        Claims claims = parseClaims(authorizationHeader);
        if (claims == null) {
            return null;
        }
        String publicId = claims.get("publicId", String.class);
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        return publicId.trim().toLowerCase();
    }

    private Claims parseClaims(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            return null;
        }
        try {
            return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
        } catch (Exception ex) {
            return null;
        }
    }

    private SecretKey key() {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            bytes = Arrays.copyOf(bytes, 32);
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
