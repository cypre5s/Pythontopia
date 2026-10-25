package com.pythontopia.submission.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.judge.JudgeCaseEvaluator.CaseEvaluation;
import com.pythontopia.submission.repository.PublicProblemRepository;
import com.pythontopia.submission.service.SubmissionTransactionService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JudgeService {

  private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

  static final int MAX_CASES = 10;

  private final SubmissionTransactionService submissionTransactions;
  private final PublicProblemRepository problemRepository;
  private final PythonSandbox sandbox;
  private final StaticCodeChecker staticChecker;
  private final ObjectMapper objectMapper;
  private final JudgeWorkerPool judgeWorkerPool;
  private final JudgeCaseEvaluator caseEvaluator;

  public JudgeService(
      SubmissionTransactionService submissionTransactions,
      PublicProblemRepository problemRepository,
      PythonSandbox sandbox,
      StaticCodeChecker staticChecker,
      ObjectMapper objectMapper,
      JudgeWorkerPool judgeWorkerPool,
      JudgeCaseEvaluator caseEvaluator) {
    this.submissionTransactions = submissionTransactions;
    this.problemRepository = problemRepository;
    this.sandbox = sandbox;
    this.staticChecker = staticChecker;
    this.objectMapper = objectMapper;
    this.judgeWorkerPool = judgeWorkerPool;
    this.caseEvaluator = caseEvaluator;
  }

  /**
   * AST 预检和判题沙箱调用均在判题工作线程池上执行；数据库写入由短事务单独提交。
   * 内部用 {@link JudgeWorkerPool#submitAndWait} 限制并发子进程数。
   */
  public JudgeOutcome judge(Long submissionId, Long problemId, String code, Long userId) {
    submissionTransactions.markRunning(submissionId);
    StaticCodeChecker.CheckResult check =
        judgeWorkerPool.submitAndWait(() -> staticChecker.check(code));
    if (!check.passed()) {
      // StaticCodeChecker 跨包契约：systemError=true 表示判题环境故障（解释器缺 ast / fork 失败），
      // 必须判 SYSTEM_ERROR + ERROR，绝不能把基础设施问题记成学生的 CE + FAILED。
      Verdict verdict = check.systemError() ? Verdict.SYSTEM_ERROR : Verdict.CE;
      if (check.systemError()) {
        log.error(
            "Static check reported infrastructure failure for submission {} ({}); "
                + "judged as SYSTEM_ERROR instead of student CE",
            submissionId,
            check.failureReason());
      }
      ObjectNode trace = objectMapper.createObjectNode();
      trace.put("sandbox", sandbox.name());
      trace.put("checker", "static");
      submissionTransactions.finishJudge(
          submissionId, verdict, 0, 0, check.failureReason(), trace, 0, null);
      return new JudgeOutcome(verdict, 0, 0, check.failureReason());
    }

    List<JudgeTestCase> cases =
        problemRepository
            .findTestCases(problemId, userId)
            .map(this::extractCases)
            .orElse(List.of());
    if (cases.isEmpty()) {
      ObjectNode trace = objectMapper.createObjectNode();
      trace.put("sandbox", sandbox.name());
      trace.put("checker", "no_test_cases");
      submissionTransactions.finishJudge(
          submissionId,
          Verdict.SYSTEM_ERROR,
          0,
          0,
          "题目没有可用测试用例",
          trace,
          0,
          null);
      return new JudgeOutcome(Verdict.SYSTEM_ERROR, 0, 0, "题目没有可用测试用例");
    }

    // 沙箱子进程在工作线程跑；DB 写入留在调用线程的事务里，避免 FK 竞态。
    CaseEvaluation evaluation =
        judgeWorkerPool.submitAndWait(() -> caseEvaluator.evaluate(code, cases));
    persistEvaluation(submissionId, cases.size(), evaluation);
    return new JudgeOutcome(
        evaluation.finalVerdict(),
        evaluation.passedCases(),
        cases.size(),
        evaluation.failureReason());
  }

  /** Writes the verdict and final submission metrics together in one short transaction. */
  private void persistEvaluation(
      Long submissionId, int totalCases, CaseEvaluation evaluation) {
    submissionTransactions.finishJudge(
        submissionId,
        evaluation.finalVerdict(),
        evaluation.passedCases(),
        totalCases,
        evaluation.failureReason(),
        evaluation.trace(),
        evaluation.totalRuntimeMs(),
        evaluation.maxMemoryKb());
  }

  private List<JudgeTestCase> extractCases(PublicProblemRepository.JudgeTestCases tc) {
    List<JudgeTestCase> result = new ArrayList<>();
    if (tc.hiddenTestCases() != null && tc.hiddenTestCases().isArray() && tc.hiddenTestCases().size() > 0) {
      // 标记为隐藏：期望输出不得随失败原因回传给学生。
      result.addAll(caseEvaluator.extractCases(tc.hiddenTestCases(), MAX_CASES, true));
    } else if (tc.samples() != null && tc.samples().isArray()) {
      result.addAll(caseEvaluator.extractCases(tc.samples(), MAX_CASES, false));
    }
    return result;
  }

  public record JudgeOutcome(Verdict verdict, int passedCases, int totalCases, String failureReason) {}
}
