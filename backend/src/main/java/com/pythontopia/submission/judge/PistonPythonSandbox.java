package com.pythontopia.submission.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Piston 远程判题适配器。Piston 负责逐提交低权用户、资源限制和无网络执行；本类只转换 Pythontopia 判题契约，任何远程异常均 fail
 * closed，绝不回退到应用容器内执行。
 */
@Component
@ConditionalOnProperty(name = "pythontopia.judge.provider", havingValue = "piston")
public class PistonPythonSandbox implements PythonSandbox {

  private static final Logger log = LoggerFactory.getLogger(PistonPythonSandbox.class);
  private static final long MEMORY_LIMIT_BYTES = 256L * 1024 * 1024;
  private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
  private static final int MAX_STDOUT_CHARS = 64 * 1024;
  private static final int MAX_STDERR_CHARS = 16 * 1024;
  private static final Set<String> PISTON_FAILURE_STATUSES =
      Set.of("RE", "SG", "TO", "OL", "EL", "XX");

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final String baseUrl;
  private final String pythonVersion;
  private final String authToken;

  public PistonPythonSandbox(
      @Qualifier("pistonRestTemplate") RestTemplate restTemplate,
      ObjectMapper objectMapper,
      @Value("${pythontopia.judge.piston.base-url:}") String baseUrl,
      @Value("${pythontopia.judge.piston.python-version:}") String pythonVersion,
      @Value("${pythontopia.judge.piston.auth-token:}") String authToken) {
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
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
    byte[] requestBody;
    try {
      requestBody = objectMapper.writeValueAsBytes(request);
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      return systemError("Failed to serialize sandbox request");
    }
    try {
      JsonNode body =
          restTemplate.execute(
              baseUrl + "/api/v2/execute",
              HttpMethod.POST,
              clientHttpRequest -> {
                clientHttpRequest.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                clientHttpRequest.getHeaders().setContentLength(requestBody.length);
                if (!authToken.isBlank()) {
                  clientHttpRequest.getHeaders().setBearerAuth(authToken);
                }
                clientHttpRequest.getBody().write(requestBody);
              },
              response -> {
                byte[] responseBody = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                if (responseBody.length > MAX_RESPONSE_BYTES) {
                  throw new IOException("Piston response exceeded the size limit");
                }
                return objectMapper.readTree(responseBody);
              });
      return mapResponse(body);
    } catch (RestClientException ex) {
      log.warn("Piston sandbox unavailable: errorType={}", ex.getClass().getSimpleName());
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
              .exchange(baseUrl + "/api/v2/runtimes", HttpMethod.GET, entity(null), JsonNode.class)
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
      log.warn("Piston readiness probe failed: errorType={}", ex.getClass().getSimpleName());
      return new Readiness(false, "piston unavailable");
    }
  }

  private SandboxRunResult mapResponse(JsonNode body) {
    JsonNode run = body == null ? null : body.get("run");
    if (run == null || !run.isObject()) {
      return systemError("Sandbox returned an invalid response");
    }
    JsonNode statusNode = run.get("status");
    String status = statusNode == null || statusNode.isNull() ? null : statusNode.asText(null);
    if ((statusNode != null && !statusNode.isNull() && !statusNode.isTextual())
        || (status != null && !PISTON_FAILURE_STATUSES.contains(status))) {
      return systemError("Sandbox returned an invalid execution status");
    }
    JsonNode stdoutNode = run.get("stdout");
    JsonNode stderrNode = run.get("stderr");
    JsonNode codeNode = run.get("code");
    JsonNode signalNode = run.get("signal");
    if (stdoutNode == null
        || !stdoutNode.isTextual()
        || stderrNode == null
        || !stderrNode.isTextual()
        || (signalNode != null && !signalNode.isNull() && !signalNode.isTextual())
        || (codeNode == null && status == null && (signalNode == null || signalNode.isNull()))
        || (codeNode != null && !codeNode.isNull() && !codeNode.isIntegralNumber())) {
      return systemError("Sandbox returned an invalid execution result");
    }
    boolean timedOut = "TO".equals(status);
    // Legacy Piston omits status and exit code on SIGKILL; preserve JudgeCaseEvaluator's signal
    // handling.
    int exitCode =
        codeNode != null && codeNode.canConvertToInt()
            ? codeNode.asInt()
            : switch (text(run, "signal")) {
              case "SIGKILL" -> 137;
              case "SIGXCPU" -> 152;
              default -> -1;
            };
    if ((status != null && !timedOut && exitCode == 0)
        || (status == null && signalNode != null && !signalNode.isNull() && exitCode == 0)) {
      exitCode = -1;
    }
    String stdout = truncate(text(run, "stdout"), MAX_STDOUT_CHARS);
    String stderr = text(run, "stderr");
    if (stderr.isBlank()) {
      stderr = text(run, "message");
    }
    if (stderr.isBlank() && status != null) {
      stderr = "Piston execution status: " + status;
    }
    stderr = truncate(stderr, MAX_STDERR_CHARS);
    String errorKind = timedOut ? "TLE" : ("XX".equals(status) ? "SYSTEM_ERROR" : null);
    return new SandboxRunResult(
        timedOut,
        exitCode,
        stdout,
        stderr,
        run.path("wall_time").asLong(0L),
        memoryKb(run.get("memory")),
        errorKind);
  }

  private Integer memoryKb(JsonNode memoryNode) {
    if (memoryNode == null || !memoryNode.isIntegralNumber() || !memoryNode.canConvertToLong()) {
      return null;
    }
    long bytes = memoryNode.asLong();
    if (bytes < 0 || bytes > (long) Integer.MAX_VALUE * 1024) {
      return null;
    }
    return (int) ((bytes + 1023) / 1024);
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
