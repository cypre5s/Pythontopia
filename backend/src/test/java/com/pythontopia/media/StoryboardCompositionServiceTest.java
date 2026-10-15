package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.media.domain.MediaAsset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

  @Test
  void composeSkipsNarrationWhenTtsIsDisabled(@TempDir Path tempDir) throws Exception {
    Path ffmpeg = tempDir.resolve("ffmpeg");
    Files.writeString(
        ffmpeg,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"-version\" ]; then exit 0; fi\n"
            + "for arg do output=\"$arg\"; done\n"
            + ": > \"$output\"\n");
    assertThat(ffmpeg.toFile().setExecutable(true)).isTrue();

    Path imagePath = Files.write(tempDir.resolve("image.jpg"), new byte[] {1});
    XfyunImageService imageService = Mockito.mock(XfyunImageService.class);
    XfyunTtsService ttsService = Mockito.mock(XfyunTtsService.class);
    MediaAssetStorage storage = Mockito.mock(MediaAssetStorage.class);
    MediaAssetRepository repository = Mockito.mock(MediaAssetRepository.class);
    MediaAsset image =
        new MediaAsset(
            1L, 1L, MediaAssetKind.IMAGE, "prompt", "image.jpg", "image/jpeg", false, "XFYUN",
            List.of("PY43-KC-001"), Instant.now());
    MediaAsset video =
        new MediaAsset(
            2L, 1L, MediaAssetKind.VIDEO, "storyboard composition", "video.mp4", "video/mp4",
            false, "FFMPEG", List.of("PY43-KC-001"), Instant.now());
    Mockito.when(ttsService.isEnabled()).thenReturn(false);
    Mockito.when(
            imageService.generate(
                1L, "像素插画", "PY43-KC-001", List.of("PY43-KC-001")))
        .thenReturn(image);
    Mockito.when(storage.resolveExisting("image.jpg")).thenReturn(Optional.of(imagePath));
    Mockito.when(storage.write(Mockito.eq("mp4"), Mockito.any(byte[].class))).thenReturn("video.mp4");
    Mockito.when(
            repository.insert(
                1L,
                MediaAssetKind.VIDEO,
                "storyboard composition",
                "video.mp4",
                "video/mp4",
                false,
                "FFMPEG",
                List.of("PY43-KC-001"),
                null))
        .thenReturn(video);
    StoryboardCompositionService service =
        new StoryboardCompositionService(ffmpeg.toString(), imageService, ttsService, storage, repository);
    List<String> phases = new ArrayList<>();

    MediaAsset result =
        service.compose(
            1L,
            List.of(new StoryboardCompositionService.SceneSpec(
                "像素插画", "旁白", "x4_yezi", 20, List.of("PY43-KC-001"))),
            (phase, sceneIndex, sceneTotal, roleKey, message, percent) -> phases.add(phase));

    assertThat(result.degraded()).isFalse();
    assertThat(phases).containsExactly("illustrate", "edit");
    Mockito.verify(ttsService, Mockito.never()).synthesize(Mockito.any(), Mockito.anyString(), Mockito.anyString());
  }

  @Test
  void composeDoesNotExposeFfmpegOutputToClient(@TempDir Path tempDir) throws Exception {
    Path ffmpeg = tempDir.resolve("ffmpeg");
    Files.writeString(
        ffmpeg,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"-version\" ]; then exit 0; fi\n"
            + "echo private-temp-path-sentinel >&2\n"
            + "exit 7\n");
    assertThat(ffmpeg.toFile().setExecutable(true)).isTrue();

    Path imagePath = Files.write(tempDir.resolve("image.jpg"), new byte[] {1});
    XfyunImageService imageService = Mockito.mock(XfyunImageService.class);
    XfyunTtsService ttsService = Mockito.mock(XfyunTtsService.class);
    MediaAssetStorage storage = Mockito.mock(MediaAssetStorage.class);
    MediaAsset image =
        new MediaAsset(
            1L,
            1L,
            MediaAssetKind.IMAGE,
            "prompt",
            "image.jpg",
            "image/jpeg",
            false,
            "XFYUN",
            List.of("PY43-KC-001"), Instant.now());
    Mockito.when(imageService.generate(1L, "像素插画", "PY43-KC-001", List.of("PY43-KC-001")))
        .thenReturn(image);
    Mockito.when(storage.resolveExisting("image.jpg")).thenReturn(Optional.of(imagePath));
    StoryboardCompositionService service =
        new StoryboardCompositionService(
            ffmpeg.toString(),
            imageService,
            ttsService,
            storage,
            Mockito.mock(MediaAssetRepository.class));

    assertThatThrownBy(
            () ->
                service.compose(
                    1L,
                    List.of(
                        new StoryboardCompositionService.SceneSpec(
                            "像素插画", "旁白", "x4_yezi", 20, List.of("PY43-KC-001")))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("短视频合成失败")
        .hasMessageNotContaining("private-temp-path-sentinel");
  }
}
