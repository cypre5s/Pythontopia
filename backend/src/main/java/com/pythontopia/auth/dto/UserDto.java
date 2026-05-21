package com.pythontopia.auth.dto;

import com.pythontopia.auth.security.Role;

public record UserDto(Long id, String username, String displayName, Role role) {}
