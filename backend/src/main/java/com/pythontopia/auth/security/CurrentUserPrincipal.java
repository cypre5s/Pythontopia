package com.pythontopia.auth.security;

public record CurrentUserPrincipal(Long userId, String username, Role role) {}
