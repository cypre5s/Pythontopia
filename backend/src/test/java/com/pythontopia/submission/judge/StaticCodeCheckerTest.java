package com.pythontopia.submission.judge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StaticCodeCheckerTest {

  private final StaticCodeChecker checker = new StaticCodeChecker();

  @Test
  void rejectsImportOs() {
    StaticCodeChecker.CheckResult result = checker.check("import os\nprint('hi')");
    assertThat(result.passed()).isFalse();
    assertThat(result.failureReason()).startsWith("banned_token:");
  }

  @Test
  void rejectsBuiltinOpen() {
    StaticCodeChecker.CheckResult result = checker.check("with open('a.txt') as f:\n  pass");
    assertThat(result.passed()).isFalse();
  }

  @Test
  void rejectsEvalAndExec() {
    assertThat(checker.check("eval('1+1')").passed()).isFalse();
    assertThat(checker.check("exec('print(1)')").passed()).isFalse();
  }

  @Test
  void acceptsSimpleProgram() {
    StaticCodeChecker.CheckResult result = checker.check("x = int(input())\nprint(x * 2)");
    assertThat(result.passed()).isTrue();
  }

  @Test
  void rejectsOversizedCode() {
    String big = "x = 1\n".repeat(2000);
    StaticCodeChecker.CheckResult result = checker.check(big);
    assertThat(result.passed()).isFalse();
    assertThat(result.failureReason()).isEqualTo("CODE_TOO_LONG");
  }
}
