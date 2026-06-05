package com.pythontopia.submission.judge;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 基于子进程的 Python 沙箱：无需 Docker，通过 {@code python -I}（隔离模式）加显式墙钟超时
 * 施加确定性资源限制；输出经脱敏，绝不泄露宿主机路径。
 */
@Component
public class SubprocessPythonSandbox implements PythonSandbox {

  private static final Logger log = LoggerFactory.getLogger(SubprocessPythonSandbox.class);

  private final String pythonExecutable;

  public SubprocessPythonSandbox(
      @Value("${pythontopia.judge.python:python3}") String pythonExecutable) {
    this.pythonExecutable = pythonExecutable;
  }

  @Override
  public String name() {
    return "subprocess";
  }

  @Override
  public SandboxRunResult run(String code, JudgeTestCase testCase) {
    Path workingDir = null;
    Path scriptFile = null;
    try {
      workingDir = Files.createTempDirectory("pythontopia-judge-");
      scriptFile = workingDir.resolve("submission.py");
      Files.writeString(scriptFile, code, StandardCharsets.UTF_8);

      ProcessBuilder builder =
          new ProcessBuilder(
                  List.of(pythonExecutable, "-I", "-B", scriptFile.getFileName().toString()))
              .directory(workingDir.toFile())
              .redirectErrorStream(false);
      builder.environment().clear();
      builder.environment().put("PATH", System.getenv("PATH"));
      builder.environment().put("PYTHONDONTWRITEBYTECODE", "1");

      long started = System.currentTimeMillis();
      Process process = builder.start();
      try (OutputStream stdin = process.getOutputStream()) {
        stdin.write(testCase.input().getBytes(StandardCharsets.UTF_8));
      } catch (IOException ignored) {
        // 部分短代码未读取 stdin 即结束，忽略此异常。
      }
      boolean finished =
          process.waitFor(Math.max(100, testCase.timeoutMs()), TimeUnit.MILLISECONDS);
      long durationMs = System.currentTimeMillis() - started;
      if (!finished) {
        process.destroyForcibly();
        return new SandboxRunResult(true, -1, "", "Killed: TLE", durationMs, "TLE");
      }
      String stdout = readBoundedStream(process.getInputStream(), 64 * 1024);
      String stderr = readBoundedStream(process.getErrorStream(), 16 * 1024);
      return new SandboxRunResult(
          false, process.exitValue(), redact(stdout), redact(stderr), durationMs, null);
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.warn("Subprocess sandbox failed: {}", exception.getMessage());
      return new SandboxRunResult(
          false, -1, "", "Sandbox error", 0L, "SYSTEM_ERROR");
    } finally {
      if (scriptFile != null) {
        try {
          Files.deleteIfExists(scriptFile);
        } catch (IOException ignored) {
          // 尽力清理临时文件，忽略失败。
        }
      }
      if (workingDir != null) {
        try {
          Files.deleteIfExists(workingDir);
        } catch (IOException ignored) {
          // 尽力清理临时文件，忽略失败。
        }
      }
    }
  }

  private String readBoundedStream(java.io.InputStream stream, int maxBytes) throws IOException {
    byte[] buffer = stream.readNBytes(maxBytes);
    if (buffer.length == 0) {
      return "";
    }
    return new String(buffer, StandardCharsets.UTF_8);
  }

  private String redact(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    return text.replaceAll("(/home/[^\\s\"']+)", "[REDACTED_HOST_PATH]")
        .replaceAll("(/root/[^\\s\"']+)", "[REDACTED_HOST_PATH]")
        .replaceAll("(/tmp/[^\\s\"']+)", "[REDACTED_TMP_PATH]");
  }
}
