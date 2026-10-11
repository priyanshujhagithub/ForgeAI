package com.forgeai.project.security;

import org.springframework.security.core.context.SecurityContextHolder;

// The authenticated caller, as established by JwtAuthenticationFilter.
// Controllers pass userId from here into services, which use it to scope
// every lookup to the caller's own data.

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser get() {
        return (AuthenticatedUser) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
