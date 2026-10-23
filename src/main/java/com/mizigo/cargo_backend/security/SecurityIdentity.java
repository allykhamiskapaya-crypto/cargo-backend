package com.mizigo.cargo_backend.security;

import org.springframework.security.oauth2.jwt.Jwt;

public final class SecurityIdentity {

    private SecurityIdentity() {}

    public static Long userId(Jwt jwt) {
        Object value = jwt.getClaim("userId");
        return value instanceof Number number ? number.longValue() : null;
    }

    public static boolean hasRole(Jwt jwt, String role) {
        return role.equals(jwt.getClaimAsString("role"));
    }

    public static boolean isAdmin(Jwt jwt) {
        return hasRole(jwt, "ADMIN");
    }
}