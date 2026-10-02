package com.uyir.hospital.security;

import com.uyir.hospital.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

// AuthService validates credentials and issues the token; this service doesn't verify it
// itself - it trusts the X-User-Id / X-User-Role headers the gateway forwards downstream
// after validating that token.
@Component
public class CurrentUserContext {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private final HttpServletRequest request;

    public CurrentUserContext(HttpServletRequest request) {
        this.request = request;
    }

    public String getUserId() {
        return request.getHeader(USER_ID_HEADER);
    }

    public Role getRole() {
        String header = request.getHeader(USER_ROLE_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(header.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String requireRole(Role required) {
        return requireAnyRole(required);
    }

    public String requireAnyRole(Role... allowed) {
        Role role = getRole();
        List<Role> allowedRoles = Arrays.asList(allowed);
        if (role == null || !allowedRoles.contains(role)) {
            String expected = allowed.length == 1 ? "the '" + allowed[0] + "' role" : "one of the roles " + allowedRoles;
            throw new ForbiddenException("This action requires " + expected + " (" + USER_ROLE_HEADER + " header)");
        }
        String userId = getUserId();
        if (userId == null || userId.isBlank()) {
            throw new ForbiddenException(USER_ID_HEADER + " header is required");
        }
        return userId;
    }
}
