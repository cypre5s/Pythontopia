package com.pythontopia.admin;

import com.pythontopia.auth.security.Role;

public record AdminUserDto(Long id, String username, String displayName, Role role, String status) {}
