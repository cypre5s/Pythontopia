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
 * 基于子进程的 Python 沙箱：通过 {@code bash -c 'ulimit ... exec python3 -I -B'}
 * 施加确定性资源限制（CPU/内存/文件/进程数）；外层墙钟超时为兜底；输出经脱敏，绝不泄露宿主机路径。
 *
 * <p>ulimit 参数（全部防御性，缺省场景远低于上限）：
 * <ul>
 *   <li>{@code -t 5} — CPU 时间 5s（TLE）</li>
 *   <li>{@code -v 262144} — 虚拟内存 256MB（内存炸弹防御）</li>
 *   <li>{@code -n 64} — 最大 64 文件描述符</li>
 *   <li>{@code -f 16384} — 最大 16MB 文件大小</li>
 *   <li>{@code -u 16} — 最大 16 进程（fork 炸弹防御）</li>
 * </ul>
 */
@Component
public class SubprocessPythonSandbox implements PythonSandbox {

  private static final Logger log = LoggerFactory.getLogger(SubprocessPythonSandbox.class);

  private static final int MAX_STDOUT_BYTES = 64 * 1024;
  private static final int MAX_STDERR_BYTES = 16 * 1024;

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

      // bash + ulimit 资源限制，然后 exec python3 -I -B（隔离模式 + 不写 .pyc）。
      // ulimit 值：-t CPU秒, -v 虚拟内存KB, -n fd数, -f 文件大小KB, -u 进程数。
      String ulimitWrapper =
          "ulimit -t 5 -v 262144 -n 64 -f 16384 -u 16; exec "
              + pythonExecutable
              + " -I -B "
              + scriptFile.getFileName().toString();

      ProcessBuilder builder =
          new ProcessBuilder(List.of("bash", "-c", ulimitWrapper))
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
      String stdout = readBoundedStream(process.getInputStream(), MAX_STDOUT_BYTES);
      String stderr = readBoundedStream(process.getErrorStream(), MAX_STDERR_BYTES);
      // ulimit 超限时 bash/内核会向 stderr 写入 "Killed"；检测并标记为资源溢出。
      if (process.exitValue() != 0 && isResourceKill(stderr)) {
        return new SandboxRunResult(
            false, -1, redact(stdout), redact(stderr), durationMs, "SYSTEM_ERROR");
      }
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

  /** ulimit 超限（SIGKILL/SIGXCPU/SIGXFSZ）时 stderr 包含 "Killed" 或 "Trace/breakpoint trap"。 */
  private static boolean isResourceKill(String stderr) {
    if (stderr == null || stderr.isEmpty()) {
      return false;
    }
    return stderr.contains("Killed") || stderr.contains("Trace/breakpoint trap");
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
