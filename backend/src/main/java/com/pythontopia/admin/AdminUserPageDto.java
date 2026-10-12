package com.pythontopia.admin;

import java.util.List;

public record AdminUserPageDto(
    List<AdminUserDto> content, int page, int size, long totalElements, int totalPages) {}
