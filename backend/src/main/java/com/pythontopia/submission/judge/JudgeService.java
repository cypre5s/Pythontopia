package com.pythontopia.submission.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.submission.judge.JudgeCaseEvaluator.CaseEvaluation;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.util.ArrayList;
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
  private final JudgeCaseEvaluator caseEvaluator;

  public JudgeService(
      PracticeSubmissionRepository submissionRepository,
      PublicProblemRepository problemRepository,
      PythonSandbox sandbox,
      StaticCodeChecker staticChecker,
      ObjectMapper objectMapper,
      JudgeWorkerPool judgeWorkerPool,
      JudgeCaseEvaluator caseEvaluator) {
    this.submissionRepository = submissionRepository;
    this.problemRepository = problemRepository;
    this.sandbox = sandbox;
    this.staticChecker = staticChecker;
    this.objectMapper = objectMapper;
    this.judgeWorkerPool = judgeWorkerPool;
    this.caseEvaluator = caseEvaluator;
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
        judgeWorkerPool.submitAndWait(() -> caseEvaluator.evaluate(code, cases));
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

  private List<JudgeTestCase> extractCases(PublicProblemRepository.JudgeTestCases tc) {
    List<JudgeTestCase> result = new ArrayList<>();
    if (tc.hiddenTestCases() != null && tc.hiddenTestCases().isArray() && tc.hiddenTestCases().size() > 0) {
      result.addAll(caseEvaluator.extractCases(tc.hiddenTestCases(), MAX_CASES));
    } else if (tc.samples() != null && tc.samples().isArray()) {
      result.addAll(caseEvaluator.extractCases(tc.samples(), MAX_CASES));
    }
    return result;
  }

  public record JudgeOutcome(Verdict verdict, int passedCases, int totalCases, String failureReason) {}
}
