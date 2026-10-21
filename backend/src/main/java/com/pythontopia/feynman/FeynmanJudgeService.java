package com.pythontopia.feynman;

import com.pythontopia.feynman.dto.FeynmanDtos.JudgeStatus;
import com.pythontopia.feynman.dto.FeynmanDtos.Verdict;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySource;
import com.pythontopia.submission.judge.JudgeCaseEvaluator;
import com.pythontopia.submission.judge.JudgeCaseEvaluator.CaseEvaluation;
import com.pythontopia.submission.judge.JudgeTestCase;
import com.pythontopia.submission.judge.JudgeWorkerPool;
import com.pythontopia.submission.judge.PythonSandbox;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeynmanJudgeService {

  /** 已判定通过后重复点「真判题」时回放的结论（不重复计分、不重复发事件）。 */
  private static final String ALREADY_PASSED_MESSAGE =
      "本会话此前已判定教学成功，掌握度已回流；重复判题不再计分。";

  private final JdbcTemplate jdbc;
  private final PythonSandbox sandbox;
  private final FeynmanSessionService sessionService;
  private final MasteryEvaluator masteryEvaluator;
  private final ApplicationEventPublisher eventPublisher;
  private final PublicProblemRepository problemRepository;
  private final JudgeCaseEvaluator caseEvaluator;
  private final JudgeWorkerPool judgeWorkerPool;

  public FeynmanJudgeService(
      JdbcTemplate jdbc,
      PythonSandbox sandbox,
      FeynmanSessionService sessionService,
      MasteryEvaluator masteryEvaluator,
      ApplicationEventPublisher eventPublisher,
      PublicProblemRepository problemRepository,
      JudgeCaseEvaluator caseEvaluator,
      JudgeWorkerPool judgeWorkerPool) {
    this.jdbc = jdbc;
    this.sandbox = sandbox;
    this.sessionService = sessionService;
    this.masteryEvaluator = masteryEvaluator;
    this.eventPublisher = eventPublisher;
    this.problemRepository = problemRepository;
    this.caseEvaluator = caseEvaluator;
    this.judgeWorkerPool = judgeWorkerPool;
  }

  @Transactional
  public Verdict judge(long userId, long sessionId) {
    FeynmanSessionService.SessionData session = sessionService.loadSession(userId, sessionId);
    // 幂等闸门：一个会话只允许计分一次。判题最长 10 用例 × 5000ms，前端超时后学生会自然重试；
    // 若每次通过都 applyEvent(+0.10) / 发 TaughtAiSucceeded(+0.03)，10 次即可把掌握度刷到 1.0。
    // JUDGED_FAIL 不锁：学生可以继续教、重新产出代码后再判。
    if ("JUDGED_PASS".equals(session.status())) {
      return new Verdict(sessionId, JudgeStatus.PASSED, ALREADY_PASSED_MESSAGE);
    }
    // 概念模式（通识知识 / 课件外概念，无代表题）：以「代码找茬」为评估，不做代表题真判题，
    // 不计掌握度、不记失败事件——独立的 NA 中性状态，不是「失败」。
    if (session.problemId() == null) {
      return new Verdict(
          sessionId,
          JudgeStatus.NA,
          "概念模式（通识知识）以代码找茬为准，本模式不做代表题真判题，也不计入掌握度。");
    }
    String code = FeynmanAntiCheat.stripCodeFence(latestProducedCode(sessionId));
    if (code == null || code.isBlank()) {
      publishFailed(session);
      return new Verdict(sessionId, JudgeStatus.FAILED, "学弟还没有产出可判题代码。");
    }
    // 产出码只查 answer_key：它本来就该是完整程序，用教学文本那套 def/class/import/围栏规则
    // 会把「函数与变量作用域」「文件操作」等 KC 的正确解一律误判成泄答。
    if (FeynmanAntiCheat.looksLikeLeakedAnswer(code)) {
      publishFailed(session);
      return new Verdict(sessionId, JudgeStatus.FAILED, "学弟产出的内容含 answer_key，疑似直接泄答。");
    }
    List<JudgeTestCase> cases = testCasesFor(session.problemId());
    if (cases.isEmpty()) {
      // 题库缺用例属平台数据缺陷，不是学生教学失败：不发 TaughtAiFailed、不写 JUDGED_FAIL。
      return new Verdict(
          sessionId, JudgeStatus.ERROR, "该知识点代表题暂无可用测试用例，判题未能进行（不计成败）。");
    }
    boolean hiddenCases = cases.stream().anyMatch(JudgeTestCase::hidden);
    // 沙箱子进程在判题工作线程池上跑（与 JudgeService 同一池，限制并发 fork）；
    // DB 写留在调用线程的事务里。
    CaseEvaluation evaluation = judgeWorkerPool.submitAndWait(() -> caseEvaluator.evaluate(code, cases));
    com.pythontopia.submission.dto.Verdict finalVerdict = evaluation.finalVerdict();
    if (finalVerdict == com.pythontopia.submission.dto.Verdict.SYSTEM_ERROR) {
      // 与 JudgeService 的明文契约一致：SYSTEM_ERROR 是判题系统故障，必须落 ERROR，
      // 绝不能和学生答错一样计 FAILED，否则沙箱起不来也会把画像打上 teach_back_failed。
      return new Verdict(
          sessionId,
          JudgeStatus.ERROR,
          "判题系统异常，本次未能判定教学成败（不计入画像），稍后重试。");
    }
    if (finalVerdict != com.pythontopia.submission.dto.Verdict.AC) {
      publishFailed(session);
      jdbc.update("UPDATE feynman_session SET status = 'JUDGED_FAIL' WHERE id = ?", sessionId);
      return new Verdict(
          sessionId,
          JudgeStatus.FAILED,
          "学弟代码未通过代表题" + (hiddenCases ? "隐藏用例" : "公开样例") + "："
              + (evaluation.failureReason() == null
                  ? finalVerdict.name()
                  : evaluation.failureReason()));
    }
    jdbc.update("UPDATE feynman_session SET status = 'JUDGED_PASS' WHERE id = ?", sessionId);
    masteryEvaluator.applyEvent(
        session.userId(), session.kcCode(), MasterySource.PRACTICE_PASS, BigDecimal.valueOf(0.10), "feynman");
    eventPublisher.publishEvent(
        new LearningEvent.TaughtAiSucceeded(session.userId(), session.kcCode(), sessionId));
    return new Verdict(
        sessionId, JudgeStatus.PASSED, "教学成功：学弟代码通过沙箱运行，掌握度已回流。");
  }

  private List<JudgeTestCase> testCasesFor(Long problemId) {
    if (problemId == null) {
      return List.of();
    }
    return problemRepository
        .findTestCases(problemId)
        .map(
            tc -> {
              List<JudgeTestCase> cases = new ArrayList<>();
              if (tc.hiddenTestCases() != null
                  && tc.hiddenTestCases().isArray()
                  && tc.hiddenTestCases().size() > 0) {
                cases.addAll(caseEvaluator.extractCases(tc.hiddenTestCases(), 10, true));
              } else if (tc.samples() != null && tc.samples().isArray()) {
                cases.addAll(caseEvaluator.extractCases(tc.samples(), 10));
              }
              return cases;
            })
        .orElse(List.of());
  }

  private void publishFailed(FeynmanSessionService.SessionData session) {
    eventPublisher.publishEvent(
        new LearningEvent.TaughtAiFailed(session.userId(), session.kcCode(), session.id()));
  }

  private String latestProducedCode(long sessionId) {
    return jdbc.query(
            "SELECT taught_fix FROM feynman_turn "
                + "WHERE session_id = ? AND taught_fix IS NOT NULL "
                + "ORDER BY created_at DESC LIMIT 1",
            (rs, rowNum) -> rs.getString("taught_fix"),
            sessionId)
        .stream()
        .findFirst()
        .orElse(null);
  }
}
