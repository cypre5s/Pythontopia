package com.pythontopia.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces bounded API request body sizes before Spring binds the request body. */
public class ApiRequestBodyLimitFilter extends OncePerRequestFilter {

  public static final int MAX_SNAPSHOT_BYTES = 256 * 1024;
  public static final int MAX_LEARNING_EVENT_BYTES = 16 * 1024;
  public static final int MAX_LEARNING_EVENT_BATCH_BYTES = 512 * 1024;
  public static final int MAX_AUTH_REQUEST_BYTES = 4 * 1024;
  // Tool-call requests carry two JSON fields, each independently limited to 1 MiB.
  public static final int MAX_AGENT_LOOP_REQUEST_BYTES = 3 * 1024 * 1024;

  private static final Pattern SNAPSHOT_PATH =
      Pattern.compile("^/api/world/saves/[0-9]+/snapshot$");
  private static final Pattern AGENT_LOOP_PATH =
      Pattern.compile(
          "^/api/(?:agent-runs/[0-9]+/(?:loop-states|policy-decisions)"
              + "|loop/runs/[0-9]+/(?:checkpoints|tool-calls|agent-runs|eval-results))$");
  private static final Pattern LEARNING_EVENT_PATH = Pattern.compile("^/api/learning/events$");
  private static final Pattern LEARNING_EVENT_BATCH_PATH =
      Pattern.compile("^/api/learning/events/batch$");
  private static final Pattern AUTH_REQUEST_PATH =
      Pattern.compile("^/api/auth/(?:login|register|refresh)$");

  private final ObjectMapper objectMapper;

  public ApiRequestBodyLimitFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equalsIgnoreCase(request.getMethod())
        || maxRequestBytes(requestPath(request)) == 0;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestPath = requestPath(request);
    int maxBytes = maxRequestBytes(requestPath);
    if (request.getContentLengthLong() > maxBytes) {
      writeTooLarge(response, requestPath);
      return;
    }

    byte[] body = readWithinLimit(request, maxBytes);
    if (body == null) {
      writeTooLarge(response, requestPath);
      return;
    }

    filterChain.doFilter(new CachedBodyRequest(request, body), response);
  }

  private static String requestPath(HttpServletRequest request) {
    String requestPath = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
      return requestPath.substring(contextPath.length());
    }
    return requestPath;
  }

  private static int maxRequestBytes(String requestPath) {
    if (SNAPSHOT_PATH.matcher(requestPath).matches()) {
      return MAX_SNAPSHOT_BYTES;
    }
    if (AGENT_LOOP_PATH.matcher(requestPath).matches()) {
      return MAX_AGENT_LOOP_REQUEST_BYTES;
    }
    if (LEARNING_EVENT_PATH.matcher(requestPath).matches()) {
      return MAX_LEARNING_EVENT_BYTES;
    }
    if (LEARNING_EVENT_BATCH_PATH.matcher(requestPath).matches()) {
      return MAX_LEARNING_EVENT_BATCH_BYTES;
    }
    if (AUTH_REQUEST_PATH.matcher(requestPath).matches()) {
      return MAX_AUTH_REQUEST_BYTES;
    }
    return 0;
  }

  private byte[] readWithinLimit(HttpServletRequest request, int maxBytes) throws IOException {
    int initialCapacity = (int) Math.min(Math.max(request.getContentLengthLong(), 0), maxBytes);
    ByteArrayOutputStream body = new ByteArrayOutputStream(initialCapacity);
    byte[] buffer = new byte[8 * 1024];
    int totalBytes = 0;
    ServletInputStream input = request.getInputStream();

    while (true) {
      int remainingIncludingOverflowByte = maxBytes + 1 - totalBytes;
      int bytesRead =
          input.read(buffer, 0, Math.min(buffer.length, remainingIncludingOverflowByte));
      if (bytesRead < 0) {
        return body.toByteArray();
      }
      if (bytesRead == 0) {
        continue;
      }
      totalBytes += bytesRead;
      if (totalBytes > maxBytes) {
        return null;
      }
      body.write(buffer, 0, bytesRead);
    }
  }

  private void writeTooLarge(HttpServletResponse response, String requestPath) throws IOException {
    boolean snapshotRequest = SNAPSHOT_PATH.matcher(requestPath).matches();
    boolean learningEventRequest = LEARNING_EVENT_PATH.matcher(requestPath).matches();
    boolean learningEventBatchRequest = LEARNING_EVENT_BATCH_PATH.matcher(requestPath).matches();
    boolean authRequest = AUTH_REQUEST_PATH.matcher(requestPath).matches();
    String errorCode = "LOOP_RUNTIME_REQUEST_TOO_LARGE";
    String errorMessage = "Loop Runtime request body cannot exceed 3 MiB";
    if (authRequest) {
      errorCode = "AUTH_REQUEST_TOO_LARGE";
      errorMessage = "Authentication request body cannot exceed 4 KiB";
    } else if (snapshotRequest) {
      errorCode = "SAVE_SLOT_SNAPSHOT_TOO_LARGE";
      errorMessage = "Save slot snapshot is too large";
    } else if (learningEventRequest) {
      errorCode = "LEARNING_EVENT_REQUEST_TOO_LARGE";
      errorMessage = "Learning event request body cannot exceed 16 KiB";
    } else if (learningEventBatchRequest) {
      errorCode = "LEARNING_EVENT_REQUEST_TOO_LARGE";
      errorMessage = "Learning event batch body cannot exceed 512 KiB";
    }
    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
        response.getOutputStream(),
        ApiError.of(
            errorCode, errorMessage, List.of(), ""));
  }

  private static final class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    private CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return input.read();
        }

        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
          throw new UnsupportedOperationException("Asynchronous body reads are not supported");
        }
      };
    }

    @Override
    public BufferedReader getReader() throws IOException {
      String encoding = getCharacterEncoding();
      Charset charset = encoding == null ? StandardCharsets.ISO_8859_1 : Charset.forName(encoding);
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
  }
}
