package com.pythontopia.submission.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证 SubprocessPythonSandbox 的 ulimit 资源限制：
 * 内存炸弹 / TLE / 正常执行三类路径均在沙箱内被拦截，不拖垮宿主机。
 *
 * <p>假定测试环境已安装 {@code python3} 与 {@code bash}（CI 与本地开发均满足）。
 */
class SandboxResourceLimitTest {

  // ulimit 资源限制测试与网络隔离正交，固定 isolation=none 保证不依赖 CI 是否开启 user namespace。
  private final SubprocessPythonSandbox sandbox = new SubprocessPythonSandbox("python3", "none");
  private static final JudgeTestCase ECHO_HELLO = new JudgeTestCase("", "hello", 3000);

  @Test
  void normalExecutionPasses() {
    SandboxRunResult result = sandbox.run("print('hello')", ECHO_HELLO);
    assertThat(result.timedOut()).isFalse();
    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).contains("hello");
  }

  @Test
  void tleOnInfiniteLoop() {
    JudgeTestCase tc = new JudgeTestCase("", "", 500);
    SandboxRunResult result = sandbox.run("while True:\n  pass", tc);
    assertThat(result.timedOut() || "TLE".equals(result.errorKind()))
        .as("Infinite loop must time out or be killed")
        .isTrue();
  }

  @Test
  void memoryBombIsRejected() {
    // try to allocate ~500MB list — ulimit -v 256MB should block it
    String code = "x = 'a' * (500 * 1024 * 1024)\nprint(len(x))";
    JudgeTestCase tc = new JudgeTestCase("", "", 3000);
    SandboxRunResult result = sandbox.run(code, tc);
    // MemoryError or killed by ulimit → SYSTEM_ERROR or non-zero exit
    assertThat(result.exitCode()).isNotEqualTo(0);
  }

  @Test
  void forkBombIsContained() {
    // os.fork() in a tight loop — ulimit -u 16 should prevent host meltdown.
    // 不用 try/except 吞掉 OSError，否则 fork 达上限后会「正常退出 0」。
    String code = "import os\nwhile True:\n  os.fork()";
    JudgeTestCase tc = new JudgeTestCase("", "", 3000);
    SandboxRunResult result = sandbox.run(code, tc);
    assertThat(
            result.timedOut()
                || "SYSTEM_ERROR".equals(result.errorKind())
                || "TLE".equals(result.errorKind())
                || result.exitCode() != 0)
        .as("Fork bomb must be contained")
        .isTrue();
  }

  @Test
  void oversizedOutputIsBounded() {
    // print a very long string — stdout bounded to 64KB
    String code = "print('x' * 200_000)";
    JudgeTestCase tc = new JudgeTestCase("", "", 3000);
    SandboxRunResult result = sandbox.run(code, tc);
    assertThat(result.stdout().length()).isLessThanOrEqualTo(64 * 1024);
  }

  @Test
  void networkIsBlockedWhenIsolationAvailable() {
    SubprocessPythonSandbox isolated = new SubprocessPythonSandbox("python3", "auto");
    assumeTrue(
        isolated.isNetworkIsolated(),
        "unprivileged user namespaces unavailable here; network isolation skipped");
    // 学生代码尝试出网；隔离生效时新 netns 无网络接口 → OSError，打印 BLOCKED。
    String code =
        "import socket\n"
            + "try:\n"
            + "  socket.create_connection(('1.1.1.1', 80), 2)\n"
            + "  print('REACHABLE')\n"
            + "except OSError:\n"
            + "  print('BLOCKED')\n";
    SandboxRunResult result = isolated.run(code, new JudgeTestCase("", "BLOCKED", 3000));
    assertThat(result.stdout()).contains("BLOCKED");
    assertThat(result.stdout()).doesNotContain("REACHABLE");
  }
}
