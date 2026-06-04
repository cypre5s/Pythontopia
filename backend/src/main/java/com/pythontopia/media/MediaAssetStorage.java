package com.pythontopia.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 多模态生成产物的本地文件存储。所有生成（真实或占位）的图/音/视频字节都落在
 * {@code pythontopia.media.generatedDir}（默认 {@code ./data/course-pack-43/generated-media}，
 * 不入 git，可重生）。读写均带 path-traversal 守卫，对齐 {@code CourseVideoService} 的做法。
 */
@Component
public class MediaAssetStorage {

  private static final Logger LOG = LoggerFactory.getLogger(MediaAssetStorage.class);

  private final Path baseDir;

  public MediaAssetStorage(
      @Value("${pythontopia.media.generatedDir:./data/course-pack-43/generated-media}")
          String generatedDir) {
    this.baseDir = Paths.get(generatedDir).toAbsolutePath().normalize();
  }

  /** 写入字节并返回相对 {@link #baseDir} 的文件名（含随机前缀，稳定可复用作 file_path）。 */
  public String write(String extension, byte[] bytes) {
    String safeExt = extension == null || extension.isBlank() ? "bin" : extension.replace(".", "");
    String fileName = UUID.randomUUID() + "." + safeExt;
    Path target = baseDir.resolve(fileName).normalize();
    if (!target.startsWith(baseDir)) {
      throw new IllegalArgumentException("rejected media path traversal: " + target);
    }
    try {
      Files.createDirectories(baseDir);
      Files.write(target, bytes);
    } catch (IOException ex) {
      throw new UncheckedIOException("failed to write media asset " + fileName, ex);
    }
    return fileName;
  }

  /** 解析相对文件名为绝对路径；越权/缺失返回空，让上层回 404。 */
  public Optional<Path> resolveExisting(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return Optional.empty();
    }
    Path candidate = baseDir.resolve(fileName).normalize();
    if (!candidate.startsWith(baseDir)) {
      LOG.warn("rejected suspicious media path traversal target {}", candidate);
      return Optional.empty();
    }
    return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
  }

  public Path baseDir() {
    return baseDir;
  }
}
