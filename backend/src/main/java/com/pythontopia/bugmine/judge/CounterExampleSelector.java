package com.pythontopia.bugmine.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.submission.judge.JudgeTestCase;
import com.pythontopia.submission.judge.PythonSandbox;
import com.pythontopia.submission.judge.SandboxRunResult;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.util.Iterator;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CounterExampleSelector {

  private final PublicProblemRepository problemRepository;
  private final PythonSandbox sandbox;

  public CounterExampleSelector(
      PublicProblemRepository problemRepository, PythonSandbox sandbox) {
    this.problemRepository = problemRepository;
    this.sandbox = sandbox;
  }

  /**
   * 返回首个未通过的测试用例作为反例：优先取隐藏用例（其 stdin 会脱敏以防泄题），否则取样例；
   * 无用例或全部通过时返回 {@link Optional#empty()}。
   */
  public Optional<CounterExample> findFirstFailing(Long problemId, String code) {
    Optional<PublicProblemRepository.JudgeTestCases> testCasesOpt =
        problemRepository.findTestCases(problemId);
    if (testCasesOpt.isEmpty()) {
      return Optional.empty();
    }
    PublicProblemRepository.JudgeTestCases tc = testCasesOpt.get();
    boolean redacted = false;
    JsonNode source;
    if (tc.hiddenTestCases() != null && tc.hiddenTestCases().isArray() && tc.hiddenTestCases().size() > 0) {
      source = tc.hiddenTestCases();
      redacted = true;
    } else if (tc.samples() != null && tc.samples().isArray()) {
      source = tc.samples();
    } else {
      return Optional.empty();
    }
    Iterator<JsonNode> iter = source.elements();
    while (iter.hasNext()) {
      JsonNode element = iter.next();
      String input = element.path("input").asText("");
      String expected = element.path("output").asText("");
      if (expected.isEmpty()) {
        expected = element.path("expected_stdout").asText("");
      }
      SandboxRunResult result = sandbox.run(code, new JudgeTestCase(input, expected, 5000));
      String actual = result.stdout() == null ? "" : result.stdout();
      if (!normalise(expected).equals(normalise(actual))) {
        String shownInput = redacted ? redact(input) : input;
        return Optional.of(new CounterExample(shownInput, expected, actual, redacted));
      }
    }
    return Optional.empty();
  }

  private String normalise(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\r\n", "\n").replace("\r", "\n").stripTrailing();
  }

  private String redact(String input) {
    if (input == null) {
      return "";
    }
    if (input.length() <= 32) {
      return input;
    }
    return input.substring(0, 16) + "…[redacted " + (input.length() - 16) + "B]";
  }

  public record CounterExample(String stdin, String expected, String actual, boolean redacted) {}
}
