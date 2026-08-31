package com.calcforge.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** Returns the authenticated cloud user's id, or empty if this request is unauthenticated (local-only). */
    public static Optional<Long> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user.id());
    }

    /** Returns the authenticated cloud user's id, throwing if this endpoint requires authentication and there is none. */
    public static Long requireCurrentUserId() {
        return getCurrentUserId().orElseThrow(() ->
                new org.springframework.security.access.AccessDeniedException("Authentication required"));
    }
}
