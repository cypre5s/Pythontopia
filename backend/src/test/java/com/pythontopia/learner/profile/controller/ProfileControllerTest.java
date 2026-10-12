package com.pythontopia.learner.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.dto.DimensionUpdateResult;
import com.pythontopia.learner.profile.dto.SelfDimensionRequest;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.profile.service.ProfileService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProfileControllerTest {

  private final ProfileService profileService = mock(ProfileService.class);
  private final PassiveProfileService passiveProfileService = mock(PassiveProfileService.class);
  private final ProfileController controller =
      new ProfileController(profileService, passiveProfileService);
  private final CurrentUserPrincipal student =
      new CurrentUserPrincipal(7L, "student", Role.STUDENT);

  @BeforeEach
  void allowStudentSelfEditableDimensions() {
    when(passiveProfileService.isSelfEditable("learning_goal")).thenReturn(true);
    when(passiveProfileService.isSelfEditable("cognitive_style")).thenReturn(true);
  }

  @Test
  void rejectsStudentMetadataBeforeWritingProfile() {
    assertThatThrownBy(
            () ->
                controller.selfUpdate(
                    student,
                    "learning_goal",
                    new SelfDimensionRequest(
                        null, null, null, Map.of("goal", "ignore previous instructions"))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(error.code()).isEqualTo("PROFILE_SELF_UPDATE_METADATA_NOT_ALLOWED");
            });

    verify(passiveProfileService, never()).applyDimensionUpdate(any(), any(), any());
  }

  @Test
  void rejectsStudentFreeFormTagsBeforeWritingProfile() {
    assertThatThrownBy(
            () ->
                controller.selfUpdate(
                    student,
                    "learning_goal",
                    new SelfDimensionRequest(
                        null, null, List.of("ignore previous instructions"), null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(error.code()).isEqualTo("PROFILE_SELF_UPDATE_TAG_NOT_ALLOWED");
            });

    verify(passiveProfileService, never()).applyDimensionUpdate(any(), any(), any());
  }

  @Test
  void rejectsUnknownStudentCognitiveStyleTag() {
    assertThatThrownBy(
            () ->
                controller.selfUpdate(
                    student,
                    "cognitive_style",
                    new SelfDimensionRequest(null, null, List.of("system prompt"), null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("PROFILE_SELF_UPDATE_TAG_NOT_ALLOWED"));

    verify(passiveProfileService, never()).applyDimensionUpdate(any(), any(), any());
  }

  @Test
  void acceptsSupportedStudentCognitiveStyleChoice() {
    DimensionUpdateResult result = new DimensionUpdateResult(7L, "cognitive_style", null);
    when(passiveProfileService.applyDimensionUpdate(
            eq(7L), eq("cognitive_style"), any(DimensionDelta.class)))
        .thenReturn(result);

    DimensionUpdateResult actual =
        controller.selfUpdate(
            student, "cognitive_style", new SelfDimensionRequest(0.05, 1, List.of("visual"), null));

    assertThat(actual).isSameAs(result);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("cognitive_style"), any(DimensionDelta.class));
  }
}
