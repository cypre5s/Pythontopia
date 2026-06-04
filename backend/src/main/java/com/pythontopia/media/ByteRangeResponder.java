package com.pythontopia.media;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 文件字节流式响应工具，支持 HTTP Range（{@code <video>}/{@code <audio>} 拖动进度需要 206 +
 * Content-Range）。从 {@code CourseVideoController} 抽出的通用逻辑，供 {@code MediaAssetController}
 * 服务生成的图/音/视频媒资复用。
 */
public final class ByteRangeResponder {

  private static final String BYTES_UNIT = "bytes";
  private static final String BYTES_PREFIX = "bytes=";
  private static final long[] NOT_SATISFIABLE = new long[0];

  private ByteRangeResponder() {}

  public static ResponseEntity<Resource> serve(Path file, MediaType contentType, String rangeHeader) {
    long total;
    try {
      total = Files.size(file);
    } catch (IOException ex) {
      return ResponseEntity.internalServerError().build();
    }

    long[] range = parseRange(rangeHeader, total);
    if (range == NOT_SATISFIABLE) {
      return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
          .headers(baseHeaders(contentType))
          .header(HttpHeaders.CONTENT_RANGE, BYTES_UNIT + " */" + total)
          .build();
    }
    if (range == null) {
      HttpHeaders headers = baseHeaders(contentType);
      headers.setContentLength(total);
      return ResponseEntity.ok().headers(headers).body(new FileSystemResource(file));
    }

    long start = range[0];
    long end = range[1];
    long length = end - start + 1;
    HttpHeaders headers = baseHeaders(contentType);
    headers.setContentLength(length);
    headers.set(HttpHeaders.CONTENT_RANGE, BYTES_UNIT + " " + start + "-" + end + "/" + total);
    try {
      return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
          .headers(headers)
          .body(slice(file, start, length));
    } catch (IOException ex) {
      return ResponseEntity.internalServerError().build();
    }
  }

  private static HttpHeaders baseHeaders(MediaType contentType) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(contentType);
    headers.set(HttpHeaders.ACCEPT_RANGES, BYTES_UNIT);
    headers.setCacheControl("private, max-age=86400");
    return headers;
  }

  /** 见 {@code CourseVideoController#parseRange}：null=全量(200)，NOT_SATISFIABLE=越界(416)。 */
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

  private static Resource slice(Path file, long start, long length) throws IOException {
    SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ);
    channel.position(start);
    return new InputStreamResource(new BoundedInputStream(Channels.newInputStream(channel), length));
  }

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
