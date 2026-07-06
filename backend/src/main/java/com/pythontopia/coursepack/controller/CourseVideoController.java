package com.pythontopia.coursepack.controller;

import com.pythontopia.coursepack.domain.CourseVideoManifestEntry;
import com.pythontopia.coursepack.dto.CourseVideoDto;
import com.pythontopia.coursepack.service.CourseVideoService;
import jakarta.validation.constraints.Positive;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 把任务点视频按章节列出（读 {@code video_manifest.json}）并按 id 流式分发，支持
 * HTTP Range（{@code <video>} 拖动进度需要 206 + Content-Range）。
 *
 * <p>清单 schema 见 {@link CourseVideoManifestEntry}。当前内置课程无任务点视频，清单为空数组，
 * 列表返回 {@code []}。端点在 {@code SecurityConfig} 里随 {@code /api/course/**} 放行匿名，
 * 因为 {@code <video src>} 不会带 Authorization 头。
 */
@Validated
@RestController
@RequestMapping("/api/course/videos")
public class CourseVideoController {

  private static final Logger LOG = LoggerFactory.getLogger(CourseVideoController.class);

  private static final MediaType VIDEO_MP4 = MediaType.parseMediaType("video/mp4");

  private static final String BYTES_UNIT = "bytes";

  private static final String BYTES_PREFIX = "bytes=";

  /** {@link #parseRange} 的哨兵返回值：Range 无法满足（上层回 416）。 */
  private static final long[] NOT_SATISFIABLE = new long[0];

  private final CourseVideoService service;

  public CourseVideoController(CourseVideoService service) {
    this.service = service;
  }

  @GetMapping
  public List<CourseVideoDto> list(@RequestParam(required = false) Integer chapterId) {
    return service.listVideos(chapterId);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Resource> stream(
      @PathVariable @Positive long id,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
    Optional<CourseVideoManifestEntry> entry = service.findEntry(id);
    if (entry.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    Optional<Path> resolved = service.resolveExisting(entry.get());
    if (resolved.isEmpty()) {
      LOG.warn("video {} present in manifest but file missing/unsafe", id);
      return ResponseEntity.notFound().build();
    }
    Path file = resolved.get();
    long total;
    try {
      total = Files.size(file);
    } catch (IOException ex) {
      LOG.error("failed to stat video {}", file, ex);
      return ResponseEntity.internalServerError().build();
    }

    long[] range = parseRange(rangeHeader, total);
    if (range == NOT_SATISFIABLE) {
      return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
          .headers(baseHeaders())
          .header(HttpHeaders.CONTENT_RANGE, BYTES_UNIT + " */" + total)
          .build();
    }
    if (range == null) {
      HttpHeaders headers = baseHeaders();
      headers.setContentLength(total);
      return ResponseEntity.ok().headers(headers).body(new FileSystemResource(file));
    }

    long start = range[0];
    long end = range[1];
    long length = end - start + 1;
    HttpHeaders headers = baseHeaders();
    headers.setContentLength(length);
    headers.set(HttpHeaders.CONTENT_RANGE, BYTES_UNIT + " " + start + "-" + end + "/" + total);
    try {
      return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
          .headers(headers)
          .body(slice(file, start, length));
    } catch (IOException ex) {
      LOG.error("failed to open video slice {} [{}-{}]", file, start, end, ex);
      return ResponseEntity.internalServerError().build();
    }
  }

  private HttpHeaders baseHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(VIDEO_MP4);
    headers.set(HttpHeaders.ACCEPT_RANGES, BYTES_UNIT);
    headers.setCacheControl("private, max-age=86400");
    return headers;
  }

  /**
   * 手动解析单段 {@code Range: bytes=start-end}（含 {@code start-}、{@code -suffix}）。返回
   * {@code [start,end]}（闭区间，已 clamp）；无/非法 Range 返回 {@code null}（上层回 200 全量）；
   * 越界返回 {@link #NOT_SATISFIABLE}（上层回 416）。
   */
  private static long[] parseRange(String rangeHeader, long total) {
    if (rangeHeader == null || rangeHeader.isBlank()) {
      return null;
    }
    String spec = rangeHeader.trim();
    if (!spec.startsWith(BYTES_PREFIX)) {
      return null;
    }
    spec = spec.substring(BYTES_PREFIX.length()).trim();
    int comma = spec.indexOf(',');
    if (comma >= 0) {
      spec = spec.substring(0, comma).trim();
    }
    int dash = spec.indexOf('-');
    if (dash < 0) {
      return null;
    }
    String startStr = spec.substring(0, dash).trim();
    String endStr = spec.substring(dash + 1).trim();
    long start;
    long end;
    try {
      if (startStr.isEmpty()) {
        if (endStr.isEmpty()) {
          return null;
        }
        long suffix = Long.parseLong(endStr);
        if (suffix <= 0) {
          return NOT_SATISFIABLE;
        }
        start = Math.max(0, total - suffix);
        end = total - 1;
      } else {
        start = Long.parseLong(startStr);
        end = endStr.isEmpty() ? total - 1 : Long.parseLong(endStr);
      }
    } catch (NumberFormatException ex) {
      return null;
    }
    if (start < 0 || start >= total || end < start) {
      return NOT_SATISFIABLE;
    }
    end = Math.min(end, total - 1);
    return new long[] {start, end};
  }

  /** 仅从 {@code start} 起读 {@code length} 字节，避免把整段视频读进内存。 */
  private static Resource slice(Path file, long start, long length) throws IOException {
    SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ);
    channel.position(start);
    return new InputStreamResource(new BoundedInputStream(Channels.newInputStream(channel), length));
  }

  /** 读到上限即 EOF；关闭时关掉底层 channel 流。 */
  private static final class BoundedInputStream extends FilterInputStream {

    private long remaining;

    BoundedInputStream(InputStream in, long limit) {
      super(in);
      this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      int value = super.read();
      if (value >= 0) {
        remaining--;
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int off, int len) throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      int toRead = (int) Math.min(len, remaining);
      int read = super.read(buffer, off, toRead);
      if (read > 0) {
        remaining -= read;
      }
      return read;
    }
  }
}
