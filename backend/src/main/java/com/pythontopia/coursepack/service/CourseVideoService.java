package com.pythontopia.coursepack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.coursepack.domain.CourseVideoManifestEntry;
import com.pythontopia.coursepack.dto.CourseVideoDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 读取 {@code video_manifest.json} 并把任务点视频文件解析到 {@code videoDir} 之下。
 *
 * <p>清单按文件 mtime+size 做轻量缓存：爬虫重写清单后下一次请求自动重载，无需重启；空/缺失清单
 * 视作空列表（课程无任务点视频时前端拿到 {@code []} 并回退）。文件解析带 path-traversal 守卫，
 * 对齐 {@code CourseDocumentPdfController} 的做法。
 */
@Service
public class CourseVideoService {

  private static final Logger LOG = LoggerFactory.getLogger(CourseVideoService.class);

  private final Path videoDir;
  private final Path manifestPath;
  private final ObjectMapper objectMapper;

  private volatile List<CourseVideoManifestEntry> cached = List.of();
  private volatile long cachedKey = Long.MIN_VALUE;

  public CourseVideoService(
      @Value("${pythontopia.coursepack.videoDir:./data/course-pack-43/videos}") String videoDir,
      @Value("${pythontopia.coursepack.videoManifest:./data/course-pack-43/video_manifest.json}")
          String videoManifest,
      ObjectMapper objectMapper) {
    this.videoDir = Paths.get(videoDir).toAbsolutePath().normalize();
    this.manifestPath = Paths.get(videoManifest).toAbsolutePath().normalize();
    this.objectMapper = objectMapper;
  }

  /** 列出（可按章节过滤）视频条目，{@code available} 标记取决于 mp4 是否已在磁盘就绪。 */
  public List<CourseVideoDto> listVideos(Integer chapterId) {
    return loadManifest().stream()
        .filter(e -> chapterId == null || chapterId.equals(e.chapter()))
        .map(e -> CourseVideoDto.from(e, resolveExisting(e).isPresent()))
        .toList();
  }

  public Optional<CourseVideoManifestEntry> findEntry(long id) {
    return loadManifest().stream().filter(e -> e.id() != null && e.id() == id).findFirst();
  }

  /**
   * 解析清单条目对应的本地 mp4 路径；越权/缺失/无文件名都返回空，让上层回 404 / available=false。
   */
  public Optional<Path> resolveExisting(CourseVideoManifestEntry entry) {
    if (entry == null || entry.file() == null || entry.file().isBlank()) {
      return Optional.empty();
    }
    Path candidate = videoDir.resolve(entry.file()).normalize();
    if (!candidate.startsWith(videoDir)) {
      LOG.warn("rejected suspicious video path traversal target {}", candidate);
      return Optional.empty();
    }
    return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
  }

  private List<CourseVideoManifestEntry> loadManifest() {
    long key;
    try {
      if (!Files.isRegularFile(manifestPath)) {
        cached = List.of();
        cachedKey = Long.MIN_VALUE;
        return cached;
      }
      key = Files.getLastModifiedTime(manifestPath).toMillis() * 31 + Files.size(manifestPath);
    } catch (IOException ex) {
      LOG.error("failed to stat video manifest {}", manifestPath, ex);
      return cached;
    }
    if (key == cachedKey) {
      return cached;
    }
    try {
      CourseVideoManifestEntry[] entries =
          objectMapper.readValue(manifestPath.toFile(), CourseVideoManifestEntry[].class);
      cached = List.of(entries);
      cachedKey = key;
      LOG.info("loaded {} course video manifest entries from {}", cached.size(), manifestPath);
    } catch (IOException ex) {
      LOG.error("failed to parse video manifest {}", manifestPath, ex);
      cached = List.of();
      cachedKey = key;
    }
    return cached;
  }
}
