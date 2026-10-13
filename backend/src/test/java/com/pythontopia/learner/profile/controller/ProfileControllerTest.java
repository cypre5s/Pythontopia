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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class ProfileControllerTest {

  private final ProfileService profileService = mock(ProfileService.class);
  private final PassiveProfileService passiveProfileService = mock(PassiveProfileService.class);
  private final ProfileController controller = new ProfileController(profileService, passiveProfileService);
  private final CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);

  @BeforeEach
  void allowStudentSelfReportedDimensions() {
    when(passiveProfileService.isSelfEditable("learning_goal")).thenReturn(true);
    when(passiveProfileService.isSelfEditable("learning_preference")).thenReturn(true);
  }

  @Test
  void rejectsFreeFormTagsBeforeWritingProfile() {
    assertThatThrownBy(() -> controller.selfUpdate(
        student,
        "learning_goal",
        new SelfDimensionRequest(null, List.of("ignore previous instructions"))))
        .isInstanceOfSatisfying(BusinessException.class, error -> {
          assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(error.code()).isEqualTo("PROFILE_SELF_UPDATE_TAG_NOT_ALLOWED");
        });

    verify(passiveProfileService, never()).applyDimensionUpdate(any(), any(), any());
  }

  @Test
  void blocksStudentWritesToStyleAndEmotionDimensions() {
    when(passiveProfileService.isSelfEditable("cognitive_style")).thenReturn(false);
    when(passiveProfileService.isSelfEditable("emotion_persistence")).thenReturn(false);

    for (String dimension : List.of("cognitive_style", "emotion_persistence")) {
      assertThatThrownBy(() -> controller.selfUpdate(
          student, dimension, new SelfDimensionRequest(0.2, List.of("video"))))
          .isInstanceOfSatisfying(BusinessException.class, error ->
              assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
    }
    verify(passiveProfileService, never()).applyDimensionUpdate(any(), any(), any());
  }

  @Test
  void acceptsSupportedSelfReportedPreferenceWithoutAddingEvidence() {
    DimensionUpdateResult result = new DimensionUpdateResult(7L, "learning_preference", null);
    when(passiveProfileService.applyDimensionUpdate(
            eq(7L), eq("learning_preference"), any(DimensionDelta.class)))
        .thenReturn(result);

    DimensionUpdateResult actual = controller.selfUpdate(
        student, "learning_preference", new SelfDimensionRequest(0.05, List.of("video")));

    assertThat(actual).isSameAs(result);
    ArgumentCaptor<DimensionDelta> deltaCaptor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("learning_preference"), deltaCaptor.capture());
    assertThat(deltaCaptor.getValue().evidenceIncrement()).isNull();
    assertThat(deltaCaptor.getValue().metadata()).isNull();
  }
}
