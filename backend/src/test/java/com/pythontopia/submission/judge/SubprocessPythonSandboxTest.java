package com.pythontopia.submission.judge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubprocessPythonSandboxTest {

  private final SubprocessPythonSandbox sandbox = new SubprocessPythonSandbox();

  @Test
  void processLocalExecutionIsNeverReady() {
    assertThat(sandbox.readiness().ready()).isFalse();
    assertThat(sandbox.readiness().detail()).contains("Piston");
  }

  @Test
  void processLocalExecutionFailsClosed() {
    SandboxRunResult result = sandbox.run("print('must not run')", new JudgeTestCase("", "", 1000));

    assertThat(result.errorKind()).isEqualTo("SYSTEM_ERROR");
    assertThat(result.stdout()).isEmpty();
    assertThat(result.stderr()).contains("Piston");
  }
}
