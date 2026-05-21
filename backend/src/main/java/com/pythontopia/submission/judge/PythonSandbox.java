package com.pythontopia.submission.judge;

public interface PythonSandbox {

  /** Returns the deployment kind for logs (subprocess / docker). */
  String name();

  /** Runs the given Python 3.11 code with provided stdin and returns the run result. */
  SandboxRunResult run(String code, JudgeTestCase testCase);
}
