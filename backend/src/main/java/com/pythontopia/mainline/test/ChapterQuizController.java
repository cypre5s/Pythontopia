package com.pythontopia.mainline.test;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.AiBudgeted;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.mainline.dto.ChapterTestPaperDto;
import com.pythontopia.mainline.dto.ChapterTestResultDto;
import com.pythontopia.mainline.dto.ChapterTestSubmitRequest;
import com.pythontopia.mainline.dto.TestPrepareResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mainline/chapters/{chapter}/quiz")
@PreAuthorize("hasRole('STUDENT')")
public class ChapterQuizController {

  private final ChapterTestService service;

  public ChapterQuizController(ChapterTestService service) {
    this.service = service;
  }

  @PostMapping("/prepare")
  @AiBudgeted(roles = Role.STUDENT)
  public TestPrepareResponse prepare(
      @PathVariable @Min(1) @Max(7) int chapter, @CurrentUser CurrentUserPrincipal user) {
    return service.prepareQuiz(requireUserId(user), chapter);
  }

  @PostMapping("/start")
  public ChapterTestPaperDto start(
      @PathVariable @Min(1) @Max(7) int chapter, @CurrentUser CurrentUserPrincipal user) {
    return service.startQuiz(requireUserId(user), chapter);
  }

  @PostMapping("/{testId}/submit")
  public ChapterTestResultDto submit(
      @PathVariable @Min(1) @Max(7) int chapter,
      @PathVariable @Positive long testId,
      @Valid @RequestBody ChapterTestSubmitRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    return service.submitQuiz(requireUserId(user), chapter, testId, request);
  }

  @GetMapping("/latest")
  public ChapterTestResultDto latest(
      @PathVariable @Min(1) @Max(7) int chapter, @CurrentUser CurrentUserPrincipal user) {
    return service.latestQuiz(requireUserId(user), chapter);
  }

  private long requireUserId(CurrentUserPrincipal user) {
    if (user == null || user.userId() == null) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "需要登录");
    }
    return user.userId();
  }
}
