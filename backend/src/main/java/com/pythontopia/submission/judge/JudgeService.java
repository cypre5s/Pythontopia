package com.pythontopia.submission.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JudgeService {

  static final int MAX_CASES = 10;

  private final PracticeSubmissionRepository submissionRepository;
  private final PublicProblemRepository problemRepository;
  private final PythonSandbox sandbox;
  private final StaticCodeChecker staticChecker;
  private final ObjectMapper objectMapper;
  private final JudgeWorkerPool judgeWorkerPool;

  public JudgeService(
      PracticeSubmissionRepository submissionRepository,
      PublicProblemRepository problemRepository,
      PythonSandbox sandbox,
      StaticCodeChecker staticChecker,
      ObjectMapper objectMapper,
      JudgeWorkerPool judgeWorkerPool) {
    this.submissionRepository = submissionRepository;
    this.problemRepository = problemRepository;
    this.sandbox = sandbox;
    this.staticChecker = staticChecker;
    this.objectMapper = objectMapper;
    this.judgeWorkerPool = judgeWorkerPool;
  }

  /**
   * 在判题工作线程池上执行 Python 子进程调用（避免占用 Tomcat 请求线程），
   * 但 DB 事务仍在调用线程上管理（由上层 {@code @Transactional} 控制）。
   * 内部用 {@link JudgeWorkerPool#submitAndWait} 限制并发子进程数。
   */
  @Transactional
  public JudgeOutcome judge(Long submissionId, Long problemId, String code) {
    submissionRepository.markStatus(submissionId, SubmissionStatus.RUNNING, null, null);
    StaticCodeChecker.CheckResult check = staticChecker.check(code);
    if (!check.passed()) {
      ObjectNode trace = objectMapper.createObjectNode();
      trace.put("sandbox", sandbox.name());
      trace.put("checker", "static");
      submissionRepository.insertJudgeResult(
          submissionId, Verdict.CE, 0, 0, check.failureReason(), trace);
      submissionRepository.markStatus(submissionId, SubmissionStatus.FAILED, 0, null);
      return new JudgeOutcome(Verdict.CE, 0, 0, check.failureReason());
    }

    List<JudgeTestCase> cases =
        problemRepository
            .findTestCases(problemId)
            .map(this::extractCases)
            .orElse(List.of());
    if (cases.isEmpty()) {
      ObjectNode trace = objectMapper.createObjectNode();
      trace.put("sandbox", sandbox.name());
      trace.put("checker", "no_test_cases");
      submissionRepository.insertJudgeResult(
          submissionId, Verdict.SYSTEM_ERROR, 0, 0, "题目没有可用测试用例", trace);
      submissionRepository.markStatus(submissionId, SubmissionStatus.ERROR, 0, null);
      return new JudgeOutcome(Verdict.SYSTEM_ERROR, 0, 0, "题目没有可用测试用例");
    }

    // 沙箱子进程在工作线程跑；DB 写入留在调用线程的事务里，避免 FK 竞态。
    CaseEvaluation evaluation =
        judgeWorkerPool.submitAndWait(() -> evaluateCases(code, cases));
    persistEvaluation(submissionId, cases.size(), evaluation);
    SubmissionStatus status =
        evaluation.finalVerdict() == Verdict.AC
            ? SubmissionStatus.PASSED
            : SubmissionStatus.FAILED;
    submissionRepository.markStatus(
        submissionId, status, evaluation.totalRuntimeMs(), null);
    return new JudgeOutcome(
        evaluation.finalVerdict(),
        evaluation.passedCases(),
        cases.size(),
        evaluation.failureReason());
  }

  /** 纯沙箱评测（无 DB），供工作线程调用。 */
  private CaseEvaluation evaluateCases(String code, List<JudgeTestCase> cases) {
    int passed = 0;
    Verdict finalVerdict = Verdict.AC;
    String failureReason = null;
    int totalRuntime = 0;
    ArrayNode caseTrace = objectMapper.createArrayNode();
    for (int i = 0; i < cases.size(); i++) {
      JudgeTestCase testCase = cases.get(i);
      SandboxRunResult result = sandbox.run(code, testCase);
      ObjectNode entry = objectMapper.createObjectNode();
      entry.put("index", i);
      entry.put("durationMs", result.durationMs());
      entry.put("timedOut", result.timedOut());
      entry.put("exitCode", result.exitCode());
      entry.put("stdoutBytes", result.stdout() == null ? 0 : result.stdout().length());
      entry.put("stderrBytes", result.stderr() == null ? 0 : result.stderr().length());
      totalRuntime += (int) result.durationMs();
      Verdict verdict = verdictFor(result, testCase);
      entry.put("verdict", verdict.name());
      caseTrace.add(entry);
      if (verdict == Verdict.AC) {
        passed += 1;
        continue;
      }
      finalVerdict = verdict;
      failureReason = summariseFailure(verdict, result, testCase);
      break;
    }

    ObjectNode trace = objectMapper.createObjectNode();
    trace.put("sandbox", sandbox.name());
    trace.put("totalRuntimeMs", totalRuntime);
    trace.set("cases", caseTrace);
    return new CaseEvaluation(finalVerdict, passed, totalRuntime, failureReason, trace);
  }

  private void persistEvaluation(
      Long submissionId, int totalCases, CaseEvaluation evaluation) {
    submissionRepository.insertJudgeResult(
        submissionId,
        evaluation.finalVerdict(),
        evaluation.passedCases(),
        totalCases,
        evaluation.failureReason(),
        evaluation.trace());
  }

  private record CaseEvaluation(
      Verdict finalVerdict,
      int passedCases,
      int totalRuntimeMs,
      String failureReason,
      ObjectNode trace) {}

  private List<JudgeTestCase> extractCases(PublicProblemRepository.JudgeTestCases tc) {
    List<JudgeTestCase> result = new ArrayList<>();
    if (tc.hiddenTestCases() != null && tc.hiddenTestCases().isArray() && tc.hiddenTestCases().size() > 0) {
      collect(result, tc.hiddenTestCases());
    } else if (tc.samples() != null && tc.samples().isArray()) {
      collect(result, tc.samples());
    }
    if (result.size() > MAX_CASES) {
      return result.subList(0, MAX_CASES);
    }
    return result;
  }

  private void collect(List<JudgeTestCase> out, JsonNode array) {
    Iterator<JsonNode> iter = array.elements();
    while (iter.hasNext()) {
      JsonNode element = iter.next();
      String input = element.path("input").asText("");
      String expected = element.path("output").asText("");
      if (expected.isEmpty()) {
        expected = element.path("expected_stdout").asText("");
      }
      int timeout = element.path("timeout_ms").asInt(5000);
      out.add(new JudgeTestCase(input, expected, timeout));
    }
  }

  private Verdict verdictFor(SandboxRunResult result, JudgeTestCase testCase) {
    if (result.timedOut()) {
      return Verdict.TLE;
    }
    if ("SYSTEM_ERROR".equals(result.errorKind())) {
      return Verdict.SYSTEM_ERROR;
    }
    if (result.exitCode() != 0) {
      return Verdict.RE;
    }
    String expected = normalise(testCase.expectedOutput());
    String actual = normalise(result.stdout());
    return expected.equals(actual) ? Verdict.AC : Verdict.WA;
  }

  private String normalise(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\r\n", "\n").replace("\r", "\n").stripTrailing();
  }

  private String summariseFailure(Verdict verdict, SandboxRunResult result, JudgeTestCase testCase) {
    return switch (verdict) {
      case WA -> "测试用例失败：期望 '" + truncate(testCase.expectedOutput()) + "'，实际 '" + truncate(result.stdout()) + "'";
      case RE -> "运行时错误：" + truncate(result.stderr());
      case TLE -> "超过时间限制 " + testCase.timeoutMs() + "ms";
      case SYSTEM_ERROR -> "判题系统异常";
      default -> "失败：" + verdict.name();
    };
  }

  private String truncate(String value) {
    if (value == null) {
      return "";
    }
    String stripped = value.length() > 120 ? value.substring(0, 120) + "…" : value;
    return stripped.replace("\n", "\\n");
  }

  public record JudgeOutcome(Verdict verdict, int passedCases, int totalCases, String failureReason) {}
}
