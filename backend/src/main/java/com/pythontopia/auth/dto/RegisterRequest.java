package com.pythontopia.auth.dto;

import com.pythontopia.auth.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 64) String username,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 128) String displayName,
    @NotNull Role role,
    @Email @Size(max = 128) String email,
    String teacherInviteCode) {}
