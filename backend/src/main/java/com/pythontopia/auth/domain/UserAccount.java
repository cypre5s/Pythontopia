package com.pythontopia.auth.domain;

import com.pythontopia.auth.security.Role;

public record UserAccount(
    Long id,
    String username,
    String displayName,
    String passwordHash,
    String email,
    Role role,
    String status) {}
