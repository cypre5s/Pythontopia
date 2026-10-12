package com.pythontopia.submission.judge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Legacy provider retained only to fail clearly when an old deployment selects it.
 * Process-local execution cannot isolate the application filesystem and is never safe for submissions.
 */
@Component
@ConditionalOnProperty(name = "pythontopia.judge.provider", havingValue = "subprocess")
public class SubprocessPythonSandbox implements PythonSandbox {

  private static final String DISABLED_MESSAGE =
      "Process-local Python execution is disabled; configure the isolated Piston service";

  @Override
  public String name() {
    return "subprocess-disabled";
  }

  @Override
  public Readiness readiness() {
    return new Readiness(false, DISABLED_MESSAGE);
  }

  @Override
  public SandboxRunResult run(String code, JudgeTestCase testCase) {
    return new SandboxRunResult(false, -1, "", DISABLED_MESSAGE, 0L, "SYSTEM_ERROR");
  }
}
