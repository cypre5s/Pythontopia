package com.pythontopia.admin;

import jakarta.validation.constraints.NotNull;

public record AdminUserStatusRequest(@NotNull AdminUserStatus status) {}
