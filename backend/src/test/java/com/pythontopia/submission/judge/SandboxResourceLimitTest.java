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

  /**
   * 回归（管道死锁）：输出远超 Linux 管道缓冲区（64KiB）的<strong>正确</strong>程序必须正常结束，
   * stdout 被截断到上限而不是丢空。修复前 waitFor 先于读取，子进程写满管道即阻塞 → 墙钟超时，
   * 断言 {@code length() <= 64KB} 在长度为 0 时同样成立（假绿），故此处显式区分「截断」与「死锁」。
   */
  @Test
  void oversizedOutputIsTruncatedNotDeadlocked() {
    // print a very long string — stdout bounded to 64KB
    String code = "print('x' * 200_000)";
    JudgeTestCase tc = new JudgeTestCase("", "", 3000);
    SandboxRunResult result = sandbox.run(code, tc);

    assertThat(result.timedOut()).as("大输出不得被误判成 TLE（管道死锁）").isFalse();
    assertThat(result.exitCode()).as("程序本身是正确的，应正常退出").isZero();
    assertThat(result.stdout().length())
        .as("stdout 必须恰好截断到上限，而不是被丢成空串")
        .isEqualTo(64 * 1024);
    assertThat(result.durationMs()).as("不应耗尽整个墙钟超时").isLessThan(3000L);
  }

  /**
   * 回归（stderr 管道同样要排空）：往 stderr 写满管道缓冲区的程序也不能阻塞，
   * 且它写到 stdout 的正确答案必须完整拿回来（修复前 stderr 写满 → 进程阻塞 → TLE，stdout 丢空）。
   */
  @Test
  void oversizedStderrDoesNotBlockCorrectStdout() {
    String code = "import sys\nsys.stderr.write('e' * 200_000)\nprint('hello')";
    JudgeTestCase tc = new JudgeTestCase("", "hello", 3000);
    SandboxRunResult result = sandbox.run(code, tc);

    assertThat(result.timedOut()).as("大 stderr 不得阻塞子进程").isFalse();
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).contains("hello");
    assertThat(result.stderr().length())
        .as("stderr 截断到 16KB 上限")
        .isEqualTo(16 * 1024);
  }

  /**
   * 回归（CPU ulimit 抢跑）：墙钟超时必须是唯一的超时判据。修复前 {@code ulimit -t 5} 固定 5s，
   * 用例超时 6s 时 CPU 上限先到 → 进程以 128+signal 退出、stderr 为空（exec 替换了 bash，
   * 没有 shell 打印 "Killed"）→ {@code timedOut=false}，最终被判成 RE 而不是 TLE。
   */
  @Test
  void cpuLimitDoesNotPreemptWallClockTimeout() {
    JudgeTestCase tc = new JudgeTestCase("", "", 6000);
    SandboxRunResult result = sandbox.run("while True:\n  pass", tc);

    assertThat(result.timedOut()).as("必须由墙钟判定超时，而不是被 CPU ulimit 杀成 RE").isTrue();
    assertThat(result.errorKind()).isEqualTo("TLE");
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
