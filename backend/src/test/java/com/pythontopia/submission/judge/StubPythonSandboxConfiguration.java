package com.pythontopia.submission.judge;

import java.util.Locale;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Deterministic integration-test judge; submitted code is never executed in the test JVM. */
@TestConfiguration(proxyBeanMethods = false)
public class StubPythonSandboxConfiguration {

  @Bean
  @Primary
  PythonSandbox stubPythonSandbox() {
    return new PythonSandbox() {
      @Override
      public String name() {
        return "test-stub";
      }

      @Override
      public SandboxRunResult run(String code, JudgeTestCase testCase) {
        String stdout = "42\n";
        if (code.contains("area = 3.1415 * radius * radius")) {
          try {
            double radius = Double.parseDouble(testCase.input().trim());
            stdout = String.format(Locale.ROOT, "%.4f%n", 3.1415 * radius * radius);
          } catch (NumberFormatException ignored) {
            return new SandboxRunResult(false, 1, "", "invalid test input", 1L, null);
          }
        }
        return new SandboxRunResult(false, 0, stdout, "", 1L, null);
      }
    };
  }
}
