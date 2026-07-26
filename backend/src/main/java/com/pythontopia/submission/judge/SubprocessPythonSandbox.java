package com.pythontopia.submission.judge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 *   <li>{@code -t ceil(timeoutMs/1000)+2} — CPU 时间兜底，<strong>刻意高于墙钟超时</strong>，
 *       让墙钟成为唯一的超时判据（否则 CPU 上限先到会把超时进程杀成 128+signal 退出，
 *       而 {@code exec} 已替换 bash、没有 shell 打印 "Killed"，最终被误判成 RE）</li>
 *   <li>{@code -v 262144} — 虚拟内存 256MB（内存炸弹防御）</li>
 *   <li>{@code -n 64} — 最大 64 文件描述符</li>
 *   <li>{@code -f 16384} — 最大 16MB 文件大小</li>
 *   <li>{@code -u 16} — 最大 16 进程（fork 炸弹防御）</li>
 * </ul>
 *
 * <p><b>管道排空</b>：{@code start()} 之后立即用两个守护线程持续排空 stdout/stderr（超出上限的字节
 * 继续读并丢弃），再 {@code waitFor}。若先 {@code waitFor} 后读，输出超过 Linux 管道缓冲区（64KiB）
 * 的正确程序会阻塞在 write 上直至墙钟超时，被误判成 TLE 且 stdout 丢空。
 *
 * <p><b>网络隔离</b>：当 {@code pythontopia.judge.isolation != none} 且宿主支持非特权用户命名空间时，
 * 在 {@code bash} 外再包一层 {@code unshare -r -n}（新建 user + network namespace，无需 root）。新 netns
 * 无任何网络接口（连 {@code lo} 都不通），从而彻底切断学生代码的出网能力——防止 SSRF 内网 graph/rag、
 * 窃取云厂商元数据（如 {@code 100.100.100.100} / {@code 169.254.169.254}）、以及把数据外传。启动时探测一次：
 * 若 {@code unshare -r -n} 不可用（如默认 seccomp 的容器内禁用了 unshare 系统调用），自动回退到无隔离子进程，
 * 判题链路不受影响（仅记 WARN 提示运维启用）。
 *
 * <p><b>已知残留</b>：本沙箱不做文件系统隔离，进程仍以应用同一用户身份运行，可读取该用户有权读的宿主文件。
 * 生产应叠加部署层加固（独立低权判题用户 / 只读根容器 / chroot），见仓库 NFR 审查报告。
 */
@Component
public class SubprocessPythonSandbox implements PythonSandbox {

  private static final Logger log = LoggerFactory.getLogger(SubprocessPythonSandbox.class);

  private static final int MAX_STDOUT_BYTES = 64 * 1024;
  private static final int MAX_STDERR_BYTES = 16 * 1024;
  /** CPU 时间上限相对墙钟超时的余量（秒）：保证墙钟先到，CPU ulimit 只做兜底。 */
  private static final int CPU_LIMIT_HEADROOM_SECONDS = 2;
  /** 进程退出后等待排空线程收尾的上限（毫秒）：管道已 EOF，正常瞬时完成。 */
  private static final long PUMP_JOIN_MILLIS = 2000L;

  private final String pythonExecutable;

  /** 是否在子进程外包一层 {@code unshare -r -n} 切断网络（启动探测结果，运行期不再变化）。 */
  private final boolean networkIsolated;

  public SubprocessPythonSandbox(
      @Value("${pythontopia.judge.python:python3}") String pythonExecutable,
      @Value("${pythontopia.judge.isolation:auto}") String isolationMode) {
    this.pythonExecutable = pythonExecutable;
    boolean wantIsolation = !"none".equalsIgnoreCase(isolationMode);
    this.networkIsolated = wantIsolation && probeNetworkIsolation();
    if (wantIsolation && !networkIsolated) {
      log.warn(
          "Judge network isolation requested (isolation={}) but `unshare -r -n` is unavailable "
              + "(no unprivileged user namespaces — e.g. default-seccomp container). Falling back "
              + "to non-isolated subprocess: submitted code CAN reach the network. Run the backend "
              + "on a host with userns enabled, or grant the container a seccomp profile permitting "
              + "unshare, to enable network isolation.",
          isolationMode);
    } else if (networkIsolated) {
      log.info("Judge sandbox network isolation ENABLED via `unshare -r -n` (mode={})", isolationMode);
    } else {
      log.info("Judge sandbox network isolation DISABLED (isolation=none)");
    }
  }

  @Override
  public String name() {
    return networkIsolated ? "subprocess+netns" : "subprocess";
  }

  /** 仅供测试断言隔离是否生效。 */
  boolean isNetworkIsolated() {
    return networkIsolated;
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
      // -t 取「墙钟超时 + 2s」：墙钟必须先到，CPU 上限只在墙钟失效时兜底，避免超时被判成 RE。
      long wallTimeoutMs = Math.max(100, testCase.timeoutMs());
      long cpuLimitSeconds =
          (long) Math.ceil(wallTimeoutMs / 1000.0) + CPU_LIMIT_HEADROOM_SECONDS;
      String ulimitWrapper =
          "ulimit -t "
              + cpuLimitSeconds
              + " -v 262144 -n 64 -f 16384 -u 16; exec "
              + pythonExecutable
              + " -I -B "
              + scriptFile.getFileName().toString();

      // 可用时在最外层再包 `unshare -r -n` 切断网络命名空间；不可用则退回纯 bash 子进程。
      List<String> command = new ArrayList<>(6);
      if (networkIsolated) {
        command.add("unshare");
        command.add("-r");
        command.add("-n");
      }
      command.add("bash");
      command.add("-c");
      command.add(ulimitWrapper);

      ProcessBuilder builder =
          new ProcessBuilder(command)
              .directory(workingDir.toFile())
              .redirectErrorStream(false);
      builder.environment().clear();
      builder.environment().put("PATH", System.getenv("PATH"));
      builder.environment().put("PYTHONDONTWRITEBYTECODE", "1");

      long started = System.currentTimeMillis();
      Process process = builder.start();
      // 必须在 waitFor 之前排空：管道缓冲区（Linux 64KiB）写满会阻塞子进程，
      // 导致输出较大的正确程序被墙钟误判成 TLE 且 stdout 丢空。
      StreamPump stdoutPump =
          StreamPump.start(process.getInputStream(), MAX_STDOUT_BYTES, "judge-stdout-pump");
      StreamPump stderrPump =
          StreamPump.start(process.getErrorStream(), MAX_STDERR_BYTES, "judge-stderr-pump");
      try (OutputStream stdin = process.getOutputStream()) {
        stdin.write(testCase.input().getBytes(StandardCharsets.UTF_8));
      } catch (IOException ignored) {
        // 部分短代码未读取 stdin 即结束，忽略此异常。
      }
      boolean finished = process.waitFor(wallTimeoutMs, TimeUnit.MILLISECONDS);
      long durationMs = System.currentTimeMillis() - started;
      if (!finished) {
        process.destroyForcibly();
        stdoutPump.awaitCompletion(PUMP_JOIN_MILLIS);
        stderrPump.awaitCompletion(PUMP_JOIN_MILLIS);
        return new SandboxRunResult(
            true, -1, redact(stdoutPump.text()), "Killed: TLE", durationMs, "TLE");
      }
      stdoutPump.awaitCompletion(PUMP_JOIN_MILLIS);
      stderrPump.awaitCompletion(PUMP_JOIN_MILLIS);
      String stdout = stdoutPump.text();
      String stderr = stderrPump.text();
      int exitCode = process.exitValue();
      // ulimit 超限时 bash/内核会向 stderr 写入 "Killed"；检测并标记为资源溢出。
      String errorKind = exitCode != 0 && isResourceKill(stderr) ? "SYSTEM_ERROR" : null;
      return new SandboxRunResult(
          false, exitCode, redact(stdout), redact(stderr), durationMs, errorKind);
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

  /**
   * 探测宿主是否支持非特权 {@code unshare -r -n}（新建 user+network 命名空间）。容器默认 seccomp
   * 会以 EPERM 拒绝 unshare，或镜像内无 util-linux 时抛 IOException——两种情况都返回 false 走回退。
   */
  private static boolean probeNetworkIsolation() {
    Process probe = null;
    try {
      probe =
          new ProcessBuilder("unshare", "-r", "-n", "true")
              .redirectErrorStream(true)
              .start();
      if (!probe.waitFor(2, TimeUnit.SECONDS)) {
        probe.destroyForcibly();
        return false;
      }
      return probe.exitValue() == 0;
    } catch (IOException ex) {
      return false;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      if (probe != null) {
        probe.destroyForcibly();
      }
      return false;
    }
  }

  /** ulimit 超限（SIGKILL/SIGXCPU/SIGXFSZ）时 stderr 包含 "Killed" 或 "Trace/breakpoint trap"。 */
  private static boolean isResourceKill(String stderr) {
    if (stderr == null || stderr.isEmpty()) {
      return false;
    }
    return stderr.contains("Killed") || stderr.contains("Trace/breakpoint trap");
  }

  /**
   * 后台排空一条子进程输出管道：保留前 {@code maxBytes} 字节，超出部分继续读取并丢弃，
   * 保证子进程永远不会因管道写满而阻塞。守护线程，随 JVM 退出。
   */
  private static final class StreamPump {

    private final Thread thread;
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

    private StreamPump(InputStream stream, int maxBytes, String threadName) {
      this.thread =
          new Thread(
              () -> {
                byte[] chunk = new byte[8192];
                try (InputStream in = stream) {
                  int read;
                  while ((read = in.read(chunk)) != -1) {
                    int room = maxBytes - captured.size();
                    if (room > 0) {
                      captured.write(chunk, 0, Math.min(room, read));
                    }
                    // room <= 0：已达上限，继续读并丢弃，防止子进程阻塞在 write 上。
                  }
                } catch (IOException ignored) {
                  // 进程被强杀时管道会提前关闭，已捕获的部分照常返回。
                }
              },
              threadName);
      this.thread.setDaemon(true);
    }

    static StreamPump start(InputStream stream, int maxBytes, String threadName) {
      StreamPump pump = new StreamPump(stream, maxBytes, threadName);
      pump.thread.start();
      return pump;
    }

    void awaitCompletion(long millis) {
      try {
        thread.join(millis);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }

    /** 已捕获（可能被截断到上限）的文本。 */
    String text() {
      byte[] bytes = captured.toByteArray();
      return bytes.length == 0 ? "" : new String(bytes, StandardCharsets.UTF_8);
    }
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
