package com.pythontopia.submission.judge;

public interface PythonSandbox {

  record Readiness(boolean ready, String detail) {}

  /** 返回部署类型用于日志区分（subprocess / docker）。 */
  String name();

  /** 以给定 stdin 运行 Python 3.11 代码并返回运行结果。 */
  SandboxRunResult run(String code, JudgeTestCase testCase);

  /**
   * 发布就绪检查。实现必须只做只读探测，不得执行用户代码；远程执行器应校验目标运行时已安装。
   */
  default Readiness readiness() {
    return new Readiness(true, name());
  }
}
