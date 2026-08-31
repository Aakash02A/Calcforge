package com.calcforge.security;

/** The JWT-derived principal attached to the security context for authenticated cloud requests. */
public record AuthenticatedUser(Long id, String email) {
}
