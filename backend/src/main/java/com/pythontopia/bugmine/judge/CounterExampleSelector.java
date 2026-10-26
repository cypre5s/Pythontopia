package com.pythontopia.bugmine.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.submission.judge.JudgeTestCase;
import com.pythontopia.submission.judge.JudgeWorkerPool;
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
  private final JudgeWorkerPool judgeWorkerPool;

  public CounterExampleSelector(
      PublicProblemRepository problemRepository,
      PythonSandbox sandbox,
      JudgeWorkerPool judgeWorkerPool) {
    this.problemRepository = problemRepository;
    this.sandbox = sandbox;
    this.judgeWorkerPool = judgeWorkerPool;
  }

  /**
   * 返回首个未通过的测试用例作为反例：优先取隐藏用例（其 stdin 会脱敏以防泄题），否则取样例；
   * 无用例或全部通过时返回 {@link Optional#empty()}。
   *
   * <p>用例扫描（fork Python 子进程）走 {@link JudgeWorkerPool}，与正式判题共用并发上限，
   * 不在 Tomcat 请求线程上直接 fork。调用方（{@code BugMineService.open}）不在池线程内，
   * 因此不存在「池线程再向同一池提交」的嵌套死锁；沙箱等待期间也不持有数据库事务。
   */
  public Optional<CounterExample> findFirstFailing(Long problemId, Long userId, String code) {
    Optional<PublicProblemRepository.JudgeTestCases> testCasesOpt =
        problemRepository.findTestCases(problemId, userId);
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
    JsonNode cases = source;
    boolean redactStdin = redacted;
    return judgeWorkerPool.submitAndWait(() -> scan(cases, code, redactStdin));
  }

  private Optional<CounterExample> scan(JsonNode source, String code, boolean redacted) {
    Iterator<JsonNode> iter = source.elements();
    while (iter.hasNext()) {
      JsonNode element = iter.next();
      String input = element.path("input").asText("");
      String expected = element.path("output").asText("");
      if (expected.isEmpty()) {
        expected = element.path("expected_stdout").asText("");
      }
      SandboxRunResult result =
          sandbox.run(code, new JudgeTestCase(input, expected, 5000, redacted));
      String actual = result.stdout() == null ? "" : result.stdout();
      if (!normalise(expected).equals(normalise(actual))) {
        return Optional.of(
            redacted
                ? new CounterExample(
                    "隐藏测试用例（输入已隐藏）", "期望输出已隐藏", "实际输出已隐藏", true)
                : new CounterExample(input, expected, actual, false));
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

  public record CounterExample(String stdin, String expected, String actual, boolean redacted) {}
}
