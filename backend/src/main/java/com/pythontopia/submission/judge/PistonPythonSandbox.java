package com.pythontopia.submission.judge;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Piston 远程判题适配器。Piston/Isolate 负责 namespace、chroot、cgroup、无网络和逐提交低权用户隔离；
 * 本类只转换 Pythontopia 判题契约，任何远程异常均 fail closed，绝不回退到应用容器内执行。
 */
@Component
@ConditionalOnProperty(name = "pythontopia.judge.provider", havingValue = "piston")
public class PistonPythonSandbox implements PythonSandbox {

  private static final Logger log = LoggerFactory.getLogger(PistonPythonSandbox.class);
  private static final long MEMORY_LIMIT_BYTES = 256L * 1024 * 1024;
  private static final int MAX_STDOUT_CHARS = 64 * 1024;
  private static final int MAX_STDERR_CHARS = 16 * 1024;

  private final RestTemplate restTemplate;
  private final String baseUrl;
  private final String pythonVersion;
  private final String authToken;

  public PistonPythonSandbox(
      @Qualifier("pistonRestTemplate") RestTemplate restTemplate,
      @Value("${pythontopia.judge.piston.base-url:}") String baseUrl,
      @Value("${pythontopia.judge.piston.python-version:}") String pythonVersion,
      @Value("${pythontopia.judge.piston.auth-token:}") String authToken) {
    this.restTemplate = restTemplate;
    this.baseUrl = baseUrl == null ? "" : stripTrailingSlash(baseUrl.trim());
    this.pythonVersion = pythonVersion == null ? "" : pythonVersion.trim();
    this.authToken = authToken == null ? "" : authToken.trim();
  }

  @Override
  public String name() {
    return pythonVersion.isBlank() ? "piston:unconfigured" : "piston:" + pythonVersion;
  }

  @Override
  public SandboxRunResult run(String code, JudgeTestCase testCase) {
    if (!isConfigured()) {
      return systemError("Sandbox not configured");
    }
    Map<String, Object> request =
        Map.<String, Object>ofEntries(
            Map.entry("language", "python"),
            Map.entry("version", pythonVersion),
            Map.entry("files", List.of(Map.of("name", "submission.py", "content", code))),
            Map.entry("stdin", testCase.input()),
            Map.entry("args", List.of()),
            Map.entry("run_timeout", testCase.timeoutMs()),
            Map.entry("run_cpu_time", testCase.timeoutMs()),
            Map.entry("run_memory_limit", MEMORY_LIMIT_BYTES),
            Map.entry("compile_timeout", testCase.timeoutMs()),
            Map.entry("compile_cpu_time", testCase.timeoutMs()),
            Map.entry("compile_memory_limit", MEMORY_LIMIT_BYTES));
    try {
      JsonNode body =
          restTemplate
              .exchange(
                  baseUrl + "/api/v2/execute",
                  HttpMethod.POST,
                  entity(request),
                  JsonNode.class)
              .getBody();
      return mapResponse(body);
    } catch (RestClientException ex) {
      log.warn("Piston sandbox unavailable: {}", ex.getMessage());
      return systemError("Sandbox unavailable");
    }
  }

  @Override
  public Readiness readiness() {
    if (!isConfigured()) {
      return new Readiness(false, "piston URL or Python runtime is not configured");
    }
    try {
      JsonNode runtimes =
          restTemplate
              .exchange(
                  baseUrl + "/api/v2/runtimes", HttpMethod.GET, entity(null), JsonNode.class)
              .getBody();
      if (runtimes != null && runtimes.isArray()) {
        for (JsonNode runtime : runtimes) {
          if ("python".equals(runtime.path("language").asText())
              && pythonVersion.equals(runtime.path("version").asText())) {
            return new Readiness(true, "piston python " + pythonVersion);
          }
        }
      }
      return new Readiness(false, "piston python runtime not installed: " + pythonVersion);
    } catch (RestClientException ex) {
      log.warn("Piston readiness probe failed: {}", ex.getMessage());
      return new Readiness(false, "piston unavailable");
    }
  }

  private SandboxRunResult mapResponse(JsonNode body) {
    JsonNode run = body == null ? null : body.get("run");
    if (run == null || !run.isObject()) {
      return systemError("Sandbox returned an invalid response");
    }
    String status = text(run, "status");
    boolean timedOut = "TO".equals(status);
    int exitCode = run.path("code").canConvertToInt() ? run.path("code").asInt() : -1;
    String stdout = truncate(text(run, "stdout"), MAX_STDOUT_CHARS);
    String stderr = text(run, "stderr");
    if (stderr.isBlank()) {
      stderr = text(run, "message");
    }
    stderr = truncate(stderr, MAX_STDERR_CHARS);
    String errorKind =
        timedOut ? "TLE" : ("XX".equals(status) ? "SYSTEM_ERROR" : null);
    return new SandboxRunResult(
        timedOut, exitCode, stdout, stderr, run.path("wall_time").asLong(0L), errorKind);
  }

  private HttpEntity<?> entity(Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (!authToken.isBlank()) {
      headers.setBearerAuth(authToken);
    }
    return new HttpEntity<>(body, headers);
  }

  private boolean isConfigured() {
    return !baseUrl.isBlank() && !pythonVersion.isBlank();
  }

  private static SandboxRunResult systemError(String message) {
    return new SandboxRunResult(false, -1, "", message, 0L, "SYSTEM_ERROR");
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? "" : value.asText("");
  }

  private static String truncate(String value, int maxChars) {
    return value.length() <= maxChars ? value : value.substring(0, maxChars);
  }

  private static String stripTrailingSlash(String value) {
    int end = value.length();
    while (end > 0 && value.charAt(end - 1) == '/') {
      end--;
    }
    return value.substring(0, end);
  }
}
