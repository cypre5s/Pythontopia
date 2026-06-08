package com.pythontopia.bugmine.dto;

import com.pythontopia.submission.dto.PublicProblemDto;
import java.time.OffsetDateTime;
import java.util.List;

public record BugMineSessionDto(
    Long id,
    Long userId,
    Long submissionId,
    Long problemId,
    Long questId,
    BugMineSessionStatus status,
    int retryCount,
    int depthLayer,
    int profileDepthTarget,
    boolean masteryConfirmed,
    Long variantSubmissionId,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt,
    BugMineVariantProblemDto variantProblem,
    String profileGuidance,
    List<BugMineDepthStepDto> depthSteps,
    List<BugMineCardDto> cards,
    /** 会话正在调试的完整题干（含样例），随会话一次性下发，避免客户端再发一次单题请求。 */
    PublicProblemDto problem) {}
