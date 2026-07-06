package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.common.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StoryboardCompositionServiceTest {

  private StoryboardCompositionService serviceWith(String ffmpegPath) {
    return new StoryboardCompositionService(
        ffmpegPath,
        Mockito.mock(XfyunImageService.class),
        Mockito.mock(XfyunTtsService.class),
        Mockito.mock(MediaAssetStorage.class),
        Mockito.mock(MediaAssetRepository.class));
  }

  @Test
  void reportsUnavailableWhenFfmpegMissing() {
    StoryboardCompositionService service =
        serviceWith("/nonexistent/ffmpeg-binary-pyt-test");
    assertThat(service.isAvailable()).isFalse();
  }

  @Test
  void composeThrowsGracefulErrorWhenFfmpegMissing() {
    StoryboardCompositionService service =
        serviceWith("/nonexistent/ffmpeg-binary-pyt-test");
    assertThatThrownBy(
            () ->
                service.compose(
                    1L,
                    List.of(
                        new StoryboardCompositionService.SceneSpec(
                            "像素插画", "旁白", "x4_yezi", 20, List.of("PY43-KC-001")))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("ffmpeg");
  }

  @Test
  void composeRejectsEmptyScenes() {
    StoryboardCompositionService service = serviceWith("/nonexistent/ffmpeg-binary-pyt-test");
    assertThatThrownBy(() -> service.compose(1L, List.of()))
        .isInstanceOf(BusinessException.class);
  }
}
