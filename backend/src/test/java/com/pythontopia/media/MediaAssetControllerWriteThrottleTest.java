package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtProperties;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.media.dto.StoryboardVideoRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MediaAssetControllerWriteThrottleTest {

  private static final long USER_ID = 7L;

  private StudentWriteThrottle throttle;
  private StoryboardCompositionService compositionService;
  private MediaAssetController controller;

  @BeforeEach
  void setUp() {
    throttle = mock(StudentWriteThrottle.class);
    compositionService = mock(StoryboardCompositionService.class);
    controller =
        new MediaAssetController(
            mock(XfyunImageService.class),
            compositionService,
            mock(MediaAssetRepository.class),
            mock(MediaAssetStorage.class),
            new MediaAssetUrls(new JwtProperties("media-rate-test-secret-at-least-32-chars", 60)),
            mock(UserRepository.class),
            throttle);
    when(compositionService.isImageGenerationEnabled()).thenReturn(true);
    when(throttle.tryAcquireMediaComposition(USER_ID)).thenReturn(false);
  }

  @Test
  void rejectsSynchronousCompositionBeforeStartingWorkWhenAccountBudgetIsExhausted() {
    CurrentUserPrincipal user = new CurrentUserPrincipal(USER_ID, "student", Role.STUDENT);
    StoryboardVideoRequest request =
        new StoryboardVideoRequest(
            List.of(new StoryboardVideoRequest.Scene("image", "narration", null, 20, List.of())));

    assertThatThrownBy(() -> controller.composeStoryboardVideo(user, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(exception.code()).isEqualTo("MEDIA_COMPOSE_RATE_LIMITED");
            });
    verify(throttle).tryAcquireMediaComposition(USER_ID);
    verify(compositionService, never()).compose(eq(USER_ID), anyList());
  }
}
