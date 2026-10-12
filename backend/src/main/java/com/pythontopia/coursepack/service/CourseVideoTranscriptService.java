package com.pythontopia.coursepack.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseVideoManifestEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 读取由 {@code faster-whisper} 从真实课程 MP4 生成的分段字幕，并按播放时刻取前后 30 秒作为导师上下文。
 *
 * <p>字幕文件携带源视频 object id、SHA-256、模型和校对状态。文件缺失、格式错误或与视频清单不匹配时明确返回
 * 503；绝不使用标题/时间戳拼接内容冒充字幕。
 */
@Service
public class CourseVideoTranscriptService {

  private static final int SCHEMA_VERSION = 1;
  private static final int WINDOW_SECONDS = 30;
  private static final int MAX_CONTEXT_CHARS = 4_000;
  private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

  private final Path transcriptPath;
  private final CourseVideoService videoService;
  private final ObjectMapper objectMapper;

  private volatile Map<Long, VideoTranscript> cached = Map.of();
  private volatile long cachedKey = Long.MIN_VALUE;

  public CourseVideoTranscriptService(
      @Value(
              "${pythontopia.coursepack.videoTranscripts:./data/course-pack-43/video_transcripts.json}")
          String transcriptPath,
      CourseVideoService videoService,
      ObjectMapper objectMapper) {
    this.transcriptPath = Paths.get(transcriptPath).toAbsolutePath().normalize();
    this.videoService = videoService;
    this.objectMapper = objectMapper;
  }

  /** 返回带时间码和来源限定的真实字幕窗口；视频、字幕或窗口不可用时 fail closed。 */
  public String contextFor(long videoId, Integer videoTimeSec) {
    CourseVideoManifestEntry entry =
        videoService
            .findEntry(videoId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "COURSE_VIDEO_NOT_FOUND", "课程视频不存在"));
    VideoTranscript transcript = loadTranscripts().get(videoId);
    if (transcript == null) {
      throw unavailable("课程视频字幕尚未生成");
    }
    if (!Objects.equals(clean(entry.objectid()), clean(transcript.objectId()))) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "VIDEO_TRANSCRIPT_STALE",
          "课程视频字幕与当前视频版本不匹配");
    }

    int anchor = Math.max(0, videoTimeSec == null ? 0 : videoTimeSec);
    int windowStart = Math.max(0, anchor - WINDOW_SECONDS);
    int windowEnd = anchor + WINDOW_SECONDS;
    List<TranscriptSegment> segments =
        transcript.segments().stream()
            .filter(segment -> segment.endSec() >= windowStart && segment.startSec() <= windowEnd)
            .toList();
    if (segments.isEmpty()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "VIDEO_TRANSCRIPT_WINDOW_EMPTY",
          "当前视频时段没有可用字幕，请调整播放位置后重试");
    }

    String title = clean(entry.title());
    StringBuilder context =
        new StringBuilder("课程视频转写: #")
            .append(videoId)
            .append(title.isEmpty() ? "" : " " + title)
            .append("（机器转写未经人工校对，仅作当前讲解上下文；事实与引用以课件 RAG 证据为准）\n");
    for (TranscriptSegment segment : segments) {
      String line =
          "["
              + timestamp(segment.startSec())
              + "-"
              + timestamp(segment.endSec())
              + "] "
              + clean(segment.text())
              + "\n";
      if (context.length() + line.length() > MAX_CONTEXT_CHARS) {
        break;
      }
      context.append(line);
    }
    return context.toString().trim();
  }

  /** 生产 readiness：视频清单、MP4 与同源字幕必须逐条齐全。 */
  public Readiness readiness() {
    try {
      Map<Long, VideoTranscript> transcripts = loadTranscripts();
      var videos = videoService.listVideos(null);
      if (videos.isEmpty()) {
        return new Readiness(false, "videos=0 transcripts=" + transcripts.size());
      }
      long availableVideos = videos.stream().filter(video -> video.available()).count();
      long matchingTranscripts =
          videos.stream()
              .filter(
                  video -> {
                    VideoTranscript transcript = transcripts.get(video.id());
                    CourseVideoManifestEntry entry =
                        videoService.findEntry(video.id()).orElse(null);
                    return transcript != null
                        && entry != null
                        && Objects.equals(clean(entry.objectid()), clean(transcript.objectId()));
                  })
              .count();
      boolean ready =
          availableVideos == videos.size()
              && matchingTranscripts == videos.size()
              && transcripts.size() == videos.size();
      return new Readiness(
          ready,
          "videos="
              + availableVideos
              + "/"
              + videos.size()
              + " transcripts="
              + matchingTranscripts
              + "/"
              + videos.size());
    } catch (BusinessException ex) {
      return new Readiness(false, ex.code());
    }
  }

  private Map<Long, VideoTranscript> loadTranscripts() {
    long key;
    try {
      if (!Files.isRegularFile(transcriptPath)) {
        throw unavailable("课程视频字幕文件不存在");
      }
      key = Files.getLastModifiedTime(transcriptPath).toMillis() * 31 + Files.size(transcriptPath);
    } catch (IOException ex) {
      throw unavailable("无法读取课程视频字幕文件");
    }
    if (key == cachedKey) {
      return cached;
    }
    synchronized (this) {
      if (key == cachedKey) {
        return cached;
      }
      try {
        TranscriptCorpus corpus = objectMapper.readValue(transcriptPath.toFile(), TranscriptCorpus.class);
        Map<Long, VideoTranscript> loaded = validate(corpus);
        cached = Collections.unmodifiableMap(loaded);
        cachedKey = key;
        return cached;
      } catch (IOException | IllegalArgumentException ex) {
        throw unavailable("课程视频字幕文件格式无效");
      }
    }
  }

  private static Map<Long, VideoTranscript> validate(TranscriptCorpus corpus) {
    if (corpus == null
        || corpus.schemaVersion() != SCHEMA_VERSION
        || corpus.generator() == null
        || !"faster-whisper".equals(corpus.generator().engine())
        || corpus.videos() == null
        || corpus.videos().isEmpty()) {
      throw new IllegalArgumentException("invalid transcript corpus metadata");
    }
    Map<Long, VideoTranscript> result = new LinkedHashMap<>();
    for (VideoTranscript video : corpus.videos()) {
      if (video == null
          || video.videoId() == null
          || video.videoId() <= 0
          || clean(video.objectId()).isEmpty()
          || !SHA_256.matcher(clean(video.sourceSha256())).matches()
          || video.segments() == null
          || video.segments().isEmpty()) {
        throw new IllegalArgumentException("invalid video transcript metadata");
      }
      double previousStart = -1;
      for (TranscriptSegment segment : video.segments()) {
        if (segment == null
            || segment.startSec() < 0
            || segment.endSec() <= segment.startSec()
            || segment.startSec() < previousStart
            || clean(segment.text()).isEmpty()) {
          throw new IllegalArgumentException("invalid transcript segment");
        }
        previousStart = segment.startSec();
      }
      if (result.put(video.videoId(), video) != null) {
        throw new IllegalArgumentException("duplicate video transcript id");
      }
    }
    return result;
  }

  private static BusinessException unavailable(String message) {
    return new BusinessException(
        HttpStatus.SERVICE_UNAVAILABLE, "VIDEO_TRANSCRIPT_UNAVAILABLE", message);
  }

  private static String timestamp(double seconds) {
    long whole = Math.max(0, Math.round(seconds));
    long hours = whole / 3600;
    long minutes = (whole % 3600) / 60;
    long remainder = whole % 60;
    return hours > 0
        ? String.format("%d:%02d:%02d", hours, minutes, remainder)
        : String.format("%02d:%02d", minutes, remainder);
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TranscriptCorpus(
      int schemaVersion,
      TranscriptGenerator generator,
      String reviewStatus,
      List<VideoTranscript> videos) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TranscriptGenerator(String engine, String engineVersion, String model) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record VideoTranscript(
      Long videoId,
      String objectId,
      String sourceFile,
      String sourceSha256,
      String model,
      String language,
      String reviewStatus,
      List<TranscriptSegment> segments) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TranscriptSegment(
      double startSec, double endSec, String text, double avgLogProb, double noSpeechProb) {}

  public record Readiness(boolean ready, String detail) {}
}
