package com.pythontopia.learner.profile.dto;

import java.util.List;

public record ProfileDimensionProjectionOutboxPageDto(
    List<ProfileDimensionProjectionOutboxDto> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {}
