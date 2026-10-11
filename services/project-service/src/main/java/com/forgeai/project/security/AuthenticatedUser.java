package com.forgeai.project.security;

public record AuthenticatedUser (
    Long userId,
    String email,
    String role
){}
