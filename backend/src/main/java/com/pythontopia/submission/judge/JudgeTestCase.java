package com.pythontopia.submission.judge;

public record JudgeTestCase(String input, String expectedOutput, int timeoutMs) {

  public JudgeTestCase {
    if (timeoutMs <= 0) {
      timeoutMs = 5000;
    }
    if (input == null) {
      input = "";
    }
    if (expectedOutput == null) {
      expectedOutput = "";
    }
  }
}
