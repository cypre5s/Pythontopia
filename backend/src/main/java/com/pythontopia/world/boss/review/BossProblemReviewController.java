package com.pythontopia.world.boss.review;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.AiBudgeted;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Server-authoritative teacher review queue and approved-bank publication API. */
@Validated
@RestController
@RequestMapping("/api/teacher/problem-reviews")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class BossProblemReviewController {

  private final BossProblemReviewService service;

  public BossProblemReviewController(BossProblemReviewService service) {
    this.service = service;
  }

  @PostMapping
  @AiBudgeted
  @ResponseStatus(HttpStatus.CREATED)
  public BossProblemReviewDto create(
      @CurrentUser CurrentUserPrincipal reviewer,
      @Valid @RequestBody BossProblemReviewCreateRequest request) {
    return service.create(reviewer, request);
  }

  @GetMapping
  public List<BossProblemReviewDto> list(
      @CurrentUser CurrentUserPrincipal reviewer,
      @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
    return service.list(reviewer, limit);
  }

  @GetMapping("/page")
  public BossProblemReviewPageDto page(
      @CurrentUser CurrentUserPrincipal reviewer,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) BossProblemReviewDecision decision,
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(defaultValue = "REQUESTED_AT") BossProblemReviewSortField sortBy,
      @RequestParam(defaultValue = "DESC") BossProblemReviewSortDirection sortDirection) {
    return service.page(reviewer, page, size, decision, q, sortBy, sortDirection);
  }

  @PatchMapping("/{reviewId}")
  public BossProblemReviewDto decide(
      @CurrentUser CurrentUserPrincipal reviewer,
      @PathVariable @Positive long reviewId,
      @Valid @RequestBody BossProblemReviewDecisionRequest request) {
    return service.decide(reviewer, reviewId, request);
  }
}
