package com.pythontopia.world.boss.review;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.mainline.ChapterCatalog;
import com.pythontopia.world.boss.BossGraphClient;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/** Generates review candidates, exposes reviewer-only audit rows, and records terminal decisions. */
@Service
public class BossProblemReviewService {

  private final BossProblemReviewRepository repository;
  private final BossGraphClient graphClient;

  public BossProblemReviewService(
      BossProblemReviewRepository repository, BossGraphClient graphClient) {
    this.repository = repository;
    this.graphClient = graphClient;
  }

  public BossProblemReviewDto create(
      CurrentUserPrincipal reviewer, BossProblemReviewCreateRequest request) {
    requireReviewer(reviewer);
    String kcCode = resolveKc(request.chapter(), request.kcCode());
    int difficulty = request.difficulty() == null ? request.stage() : request.difficulty();
    BossInternalProblem generated;
    try {
      generated =
          graphClient.requestProblem(
              reviewer.userId(),
              request.chapter(),
              request.stage(),
              kcCode,
              difficulty,
              kcCode,
              request.kind(),
              null);
    } catch (RestClientException ex) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PROBLEM_REVIEW_GENERATION_UNAVAILABLE",
          "AI 出题服务暂不可用，请稍后重试");
    }
    validateGenerated(generated, request.chapter(), request.stage(), kcCode, difficulty);
    return toDto(repository.create(reviewer.userId(), request.kind(), generated));
  }

  public List<BossProblemReviewDto> list(CurrentUserPrincipal reviewer, int limit) {
    requireReviewer(reviewer);
    return repository.list(limit).stream().map(BossProblemReviewService::toDto).toList();
  }

  public BossProblemReviewPageDto page(
      CurrentUserPrincipal reviewer,
      int page,
      int size,
      BossProblemReviewDecision decision,
      String query,
      BossProblemReviewSortField sortBy,
      BossProblemReviewSortDirection sortDirection) {
    requireReviewer(reviewer);
    String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
    long total = repository.countMatching(decision, normalizedQuery);
    List<BossProblemReviewDto> content =
        repository
            .listMatching(
                decision, normalizedQuery, size, (long) page * size, sortBy, sortDirection)
            .stream()
            .map(BossProblemReviewService::toDto)
            .toList();
    int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
    return new BossProblemReviewPageDto(content, page, size, total, totalPages);
  }

  public BossProblemReviewDto decide(
      CurrentUserPrincipal reviewer,
      long reviewId,
      BossProblemReviewDecisionRequest request) {
    requireReviewer(reviewer);
    if (request.decision() == BossProblemReviewDecision.PENDING) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_PROBLEM_REVIEW_DECISION",
          "审核决定只能是 APPROVED 或 REJECTED");
    }
    return repository
        .decide(
            reviewId,
            request.version(),
            request.decision(),
            reviewer.userId(),
            request.reason())
        .map(BossProblemReviewService::toDto)
        .orElseThrow(() -> decisionFailure(reviewId));
  }

  private BusinessException decisionFailure(long reviewId) {
    if (repository.findById(reviewId).isEmpty()) {
      return new BusinessException(
          HttpStatus.NOT_FOUND, "PROBLEM_REVIEW_NOT_FOUND", "审核题目不存在");
    }
    return new BusinessException(
        HttpStatus.CONFLICT,
        "PROBLEM_REVIEW_VERSION_CONFLICT",
        "审核记录已被其他教师处理，请刷新队列");
  }

  private static String resolveKc(int chapter, String requestedKc) {
    String kcCode =
        requestedKc == null ? ChapterCatalog.kcCodes(chapter).get(0) : requestedKc;
    if (ChapterCatalog.chapterOfKc(kcCode).filter(value -> value == chapter).isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_PROBLEM_REVIEW_SCOPE",
          "KC 不属于所选章节的课程包 43 范围");
    }
    return kcCode;
  }

  private static void validateGenerated(
      BossInternalProblem problem, int chapter, int stage, String kcCode, int difficulty) {
    boolean dimensionsMatch =
        problem != null
            && problem.chapter() == chapter
            && problem.stage() == stage
            && difficulty == problem.difficulty()
            && kcCode.equals(problem.kcCode());
    boolean completeReviewedPayload =
        problem != null
            && problem.problemId() != null
            && !problem.problemId().isBlank()
            && problem.statement() != null
            && !problem.statement().isBlank()
            && problem.reviewed()
            && problem.aiGenerated()
            && !problem.testCases().isEmpty()
            && problem.referenceSolution() != null
            && !problem.referenceSolution().isBlank();
    if (!dimensionsMatch || !completeReviewedPayload) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "PROBLEM_REVIEW_GENERATION_INVALID",
          "AI 出题结果未满足完整题面、隐藏用例或审查契约");
    }
  }

  private static void requireReviewer(CurrentUserPrincipal reviewer) {
    if (reviewer == null
        || (reviewer.role() != Role.TEACHER && reviewer.role() != Role.ADMIN)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "TEACHER_REQUIRED", "仅教师或管理员可访问题目审核队列");
    }
  }

  private static BossProblemReviewDto toDto(BossProblemReviewRecord record) {
    return new BossProblemReviewDto(
        record.id(),
        record.packId(),
        record.sourceProblemId(),
        record.kind(),
        record.problem().toPublic(),
        record.decision(),
        record.reviewSource(),
        record.requesterId(),
        record.requesterUsername(),
        record.requestedAt(),
        record.deciderId(),
        record.deciderUsername(),
        record.decidedAt(),
        record.decisionReason(),
        record.version());
  }
}
