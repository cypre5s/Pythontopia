package com.pythontopia.submission.judge;

public interface PythonSandbox {

  /** 返回部署类型用于日志区分（subprocess / docker）。 */
  String name();

  /** 以给定 stdin 运行 Python 3.11 代码并返回运行结果。 */
  SandboxRunResult run(String code, JudgeTestCase testCase);
}
