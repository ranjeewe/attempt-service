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
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            return false;
        }
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
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
        } catch (Exception ex) {
            return false;
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
