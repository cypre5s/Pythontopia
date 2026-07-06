package com.pythontopia.submission.judge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StaticCodeCheckerTest {

  private final StaticCodeChecker checker = new StaticCodeChecker("python3");

  @Test
  void rejectsImportOs() {
    StaticCodeChecker.CheckResult result = checker.check("import os\nprint('hi')");
    assertThat(result.passed()).isFalse();
    assertThat(result.failureReason()).startsWith("banned_import:os:");
  }

  @Test
  void rejectsBuiltinOpen() {
    StaticCodeChecker.CheckResult result = checker.check("with open('a.txt') as f:\n  pass");
    assertThat(result.passed()).isFalse();
    assertThat(result.failureReason()).startsWith("banned_call:open:");
  }

  @Test
  void rejectsEvalAndExec() {
    assertThat(checker.check("eval('1+1')").passed()).isFalse();
    assertThat(checker.check("exec('print(1)')").passed()).isFalse();
  }

  @Test
  void rejectsBreakpointBypass() {
    StaticCodeChecker.CheckResult result = checker.check("breakpoint()");
    assertThat(result.passed()).isFalse();
    assertThat(result.failureReason()).startsWith("banned_call:breakpoint");
  }

  @Test
  void rejectsGetattrBypass() {
    StaticCodeChecker.CheckResult result =
        checker.check("getattr(__builtins__, 'exec')('print(1)')");
    assertThat(result.passed()).isFalse();
  }

  @Test
  void rejectsDunderAccess() {
    assertThat(checker.check("''.__class__.__mro__").passed()).isFalse();
    assertThat(checker.check("x.__globals__").passed()).isFalse();
  }

  @Test
  void rejectsOsSystemViaAlias() {
    // 即便 os 已通过别名导入也不放过 os.system 调用
    StaticCodeChecker.CheckResult result =
        checker.check("import os as _x\n_x.system('ls')");
    assertThat(result.passed()).isFalse();
  }

  @Test
  void acceptsSimpleProgram() {
    StaticCodeChecker.CheckResult result = checker.check("x = int(input())\nprint(x * 2)");
    assertThat(result.passed()).isTrue();
  }

  @Test
  void acceptsCommonSafePatterns() {
    assertThat(checker.check("for i in range(10):\n  print(i)").passed()).isTrue();
    assertThat(checker.check("def add(a, b):\n  return a + b").passed()).isTrue();
    assertThat(checker.check("import math\nprint(math.sqrt(4))").passed()).isTrue();
  }

  @Test
  void rejectsOversizedCode() {
    String big = "x = 1\n".repeat(2000);
    StaticCodeChecker.CheckResult result = checker.check(big);
    assertThat(result.passed()).isFalse();
    assertThat(result.failureReason()).isEqualTo("CODE_TOO_LONG");
  }

  @Test
  void rejectsTabIndentedImportAlias() {
    StaticCodeChecker.CheckResult result = checker.check("\timport os as o\nprint(1)");
    assertThat(result.passed()).isFalse();
  }

  @Test
  void rejectsDisallowedImportViaAst() {
    StaticCodeChecker.CheckResult result = checker.check("import socket\nprint(1)");
    assertThat(result.passed()).isFalse();
    assertThat(result.failureReason()).contains("banned_import");
  }

  @Test
  void acceptsSafeImportViaAst() {
    assertThat(checker.check("import math\nprint(math.sqrt(4))").passed()).isTrue();
  }
}
