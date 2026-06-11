package com.pythontopia.mainline;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.mainline.dto.EndlessSummaryRequest;
import com.pythontopia.mainline.dto.ExampleDoneRequest;
import com.pythontopia.mainline.dto.MainlineOverviewDto;
import com.pythontopia.mainline.dto.PageReadRequest;
import com.pythontopia.mainline.dto.PagesReadRequest;
import com.pythontopia.mainline.dto.StageDetailDto;
import com.pythontopia.mainline.dto.StageProgressDto;
import com.pythontopia.mainline.dto.StarAwardResultDto;
import com.pythontopia.mainline.dto.VideoProgressRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主线学生端：关卡地图总览 + stage 三件套打点（r15 计划 §9，全部 JWT + STUDENT）。
 * 鉴权由 JwtAuthenticationFilter 强制；{@link CurrentUserPrincipal} 提供 userId。
 */
@Validated
@RestController
@RequestMapping("/api/mainline")
public class MainlineController {

  private final MainlineService service;
  private final MainlineRecoveryService recoveryService;

  public MainlineController(MainlineService service, MainlineRecoveryService recoveryService) {
    this.service = service;
    this.recoveryService = recoveryService;
  }

  @GetMapping("/overview")
  public MainlineOverviewDto overview(@CurrentUser CurrentUserPrincipal user) {
    return service.overview(requireUserId(user));
  }

  @GetMapping("/stages/{stageId}")
  public StageDetailDto stageDetail(
      @PathVariable @Positive long stageId, @CurrentUser CurrentUserPrincipal user) {
    return service.stageDetail(requireUserId(user), stageId);
  }

  @PostMapping("/stages/{stageId}/video-progress")
  public StageProgressDto videoProgress(
      @PathVariable @Positive long stageId,
      @Valid @RequestBody VideoProgressRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    return service.reportVideoProgress(requireUserId(user), stageId, request.deltaSec());
  }

  @PostMapping("/stages/{stageId}/page-read")
  public StageProgressDto pageRead(
      @PathVariable @Positive long stageId,
      @Valid @RequestBody PageReadRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    return service.reportPageRead(requireUserId(user), stageId, request.pageId());
  }

  @PostMapping("/stages/{stageId}/pages-read")
  public StageProgressDto pagesRead(
      @PathVariable @Positive long stageId,
      @Valid @RequestBody PagesReadRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    return service.reportPagesRead(requireUserId(user), stageId, request.pageIds());
  }

  @PostMapping("/stages/{stageId}/example-done")
  public StageProgressDto exampleDone(
      @PathVariable @Positive long stageId,
      @Valid @RequestBody ExampleDoneRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    return service.reportExampleDone(
        requireUserId(user), stageId, request.problemId(), request.submissionId());
  }

  @PostMapping("/endless/summary")
  public StarAwardResultDto endlessSummary(
      @Valid @RequestBody EndlessSummaryRequest request, @CurrentUser CurrentUserPrincipal user) {
    boolean awarded =
        recoveryService.recordEndlessSummary(
            requireUserId(user),
            request.chapter(),
            request.correct(),
            request.total(),
            request.streak());
    return new StarAwardResultDto(awarded);
  }

  @PostMapping("/chapters/{chapter}/recovery/errorbook")
  public StarAwardResultDto recoveryErrorbook(
      @PathVariable @Positive int chapter, @CurrentUser CurrentUserPrincipal user) {
    return new StarAwardResultDto(
        recoveryService.claimErrorbookRecovery(requireUserId(user), chapter));
  }

  private long requireUserId(CurrentUserPrincipal user) {
    if (user == null || user.userId() == null) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "需要登录");
    }
    return user.userId();
  }
}
