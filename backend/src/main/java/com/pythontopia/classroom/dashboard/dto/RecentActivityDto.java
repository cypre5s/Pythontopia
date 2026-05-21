package com.pythontopia.classroom.dashboard.dto;

import java.time.OffsetDateTime;

public record RecentActivityDto(
    Long studentId,
    String displayName,
    String eventKind,
    String kcCode,
    String detail,
    OffsetDateTime occurredAt) {}
