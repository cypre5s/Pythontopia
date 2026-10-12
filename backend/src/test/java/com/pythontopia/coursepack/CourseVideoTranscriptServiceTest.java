package com.pythontopia.coursepack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseVideoManifestEntry;
import com.pythontopia.coursepack.service.CourseVideoService;
import com.pythontopia.coursepack.service.CourseVideoTranscriptService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

class CourseVideoTranscriptServiceTest {

  @TempDir Path tempDir;

  private final CourseVideoService videoService = mock(CourseVideoService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void returnsOnlyTimedWindowWithExplicitMachineTranscriptProvenance() throws Exception {
    Path transcript = tempDir.resolve("video_transcripts.json");
    Files.writeString(transcript, transcriptJson("object-1"));
    when(videoService.findEntry(1L)).thenReturn(Optional.of(entry(1L, "object-1")));
    CourseVideoTranscriptService service = service(transcript);

    String context = service.contextFor(1L, 50);

    assertThat(context)
        .contains("课程视频转写", "#1", "00:35-00:55", "列表索引", "机器转写未经人工校对")
        .doesNotContain("00:00-00:10", "计算机发展史");
  }

  @Test
  void failsClosedWhenTranscriptFileIsMissing() {
    when(videoService.findEntry(1L)).thenReturn(Optional.of(entry(1L, "object-1")));
    CourseVideoTranscriptService service = service(tempDir.resolve("missing.json"));

    assertThatThrownBy(() -> service.contextFor(1L, 10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(ex.code()).isEqualTo("VIDEO_TRANSCRIPT_UNAVAILABLE");
            });
  }

  @Test
  void rejectsTranscriptWhoseSourceObjectDoesNotMatchCourseManifest() throws Exception {
    Path transcript = tempDir.resolve("video_transcripts.json");
    Files.writeString(transcript, transcriptJson("stale-object"));
    when(videoService.findEntry(1L)).thenReturn(Optional.of(entry(1L, "object-1")));
    CourseVideoTranscriptService service = service(transcript);

    assertThatThrownBy(() -> service.contextFor(1L, 10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(ex.code()).isEqualTo("VIDEO_TRANSCRIPT_STALE");
            });
  }

  @Test
  void rejectsUnknownVideoBeforeReadingTranscript() throws Exception {
    Path transcript = tempDir.resolve("video_transcripts.json");
    Files.writeString(transcript, transcriptJson("object-1"));
    when(videoService.findEntry(99L)).thenReturn(Optional.empty());
    CourseVideoTranscriptService service = service(transcript);

    assertThatThrownBy(() -> service.contextFor(99L, 10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.code()).isEqualTo("COURSE_VIDEO_NOT_FOUND");
            });
  }

  private CourseVideoTranscriptService service(Path transcript) {
    return new CourseVideoTranscriptService(transcript.toString(), videoService, objectMapper);
  }

  private static CourseVideoManifestEntry entry(long id, String objectId) {
    return new CourseVideoManifestEntry(
        id, 1, "列表", objectId, "video.mp4", 120, List.of("PY43-KC-024"));
  }

  private static String transcriptJson(String objectId) {
    return """
        {
          "schemaVersion": 1,
          "generator": {"engine": "faster-whisper", "engineVersion": "1.2.1", "model": "turbo"},
          "reviewStatus": "machine_generated_unreviewed",
          "videos": [{
            "videoId": 1,
            "objectId": "%s",
            "sourceFile": "video.mp4",
            "sourceSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "model": "turbo",
            "language": "zh",
            "reviewStatus": "machine_generated_unreviewed",
            "segments": [
              {"startSec": 0, "endSec": 10, "text": "计算机发展史", "avgLogProb": -0.1, "noSpeechProb": 0.0},
              {"startSec": 35, "endSec": 55, "text": "列表索引从零开始", "avgLogProb": -0.2, "noSpeechProb": 0.0},
              {"startSec": 70, "endSec": 79, "text": "索引越界会报错", "avgLogProb": -0.3, "noSpeechProb": 0.0}
            ]
          }]
        }
        """
        .formatted(objectId);
  }
}
