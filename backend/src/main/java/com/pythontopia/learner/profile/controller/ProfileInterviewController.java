package com.pythontopia.learner.profile.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.AiBudgeted;
import com.pythontopia.learner.profile.dto.ProfileInterviewStartResponse;
import com.pythontopia.learner.profile.dto.ProfileTurnRequest;
import com.pythontopia.learner.profile.dto.ProfileTurnResponse;
import com.pythontopia.learner.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/learner/profile/interview")
public class ProfileInterviewController {

  private final ProfileService profileService;

  public ProfileInterviewController(ProfileService profileService) {
    this.profileService = profileService;
  }

  @PostMapping
  ProfileInterviewStartResponse open(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return profileService.openOrResumeInterview(user.userId());
  }

  @PostMapping("/{sessionId}/turn")
  @AiBudgeted(roles = Role.STUDENT)
  ProfileTurnResponse turn(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable String sessionId,
      @Valid @RequestBody ProfileTurnRequest request) {
    requireStudent(user);
    return profileService.sendTurn(user.userId(), sessionId, request);
  }

  @PostMapping(
      value = "/{sessionId}/turn/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @AiBudgeted(roles = Role.STUDENT)
  SseEmitter turnStream(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable String sessionId,
      @Valid @RequestBody ProfileTurnRequest request) {
    requireStudent(user);
    return profileService.sendTurnStream(user.userId(), sessionId, request);
  }

  @PostMapping("/{sessionId}/finish")
  void finish(@CurrentUser CurrentUserPrincipal user, @PathVariable String sessionId) {
    requireStudent(user);
    profileService.finishInterview(user.userId(), sessionId);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only students can use profile interview");
    }
  }
}
