package com.pythontopia.feynman;

import com.pythontopia.feynman.dto.FeynmanDtos.JudgeStatus;
import com.pythontopia.feynman.dto.FeynmanDtos.Verdict;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySource;
import com.pythontopia.submission.judge.JudgeCaseEvaluator.CaseEvaluation;
import com.pythontopia.submission.judge.JudgeCaseEvaluator;
import com.pythontopia.submission.judge.JudgeTestCase;
import com.pythontopia.submission.judge.JudgeWorkerPool;
import com.pythontopia.submission.judge.PythonSandbox;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeynmanJudgeService {

  private static final Logger log = LoggerFactory.getLogger(FeynmanJudgeService.class);
  private static final String PASS_CLAIM_SQL =
      "UPDATE feynman_session SET status = 'JUDGED_PASS' "
          + "WHERE id = ? AND status <> 'JUDGED_PASS'";
  private static final String FAIL_CLAIM_SQL =
      "UPDATE feynman_session SET status = 'JUDGED_FAIL' "
          + "WHERE id = ? AND status <> 'JUDGED_PASS'";
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
    if ("JUDGED_PASS".equals(session.status())) {
      return alreadyPassed(sessionId);
    }
    if (!sessionService.hasMisconceptions(session.tuteeState())) {
      return new Verdict(
          sessionId,
          JudgeStatus.NA,
          "自主讲题模式没有真实错题记录，不进行错题专项判题，也不计入掌握度。");
    }
    // 概念模式（通识知识 / 课件外概念，无代表题）：以「代码找茬」为评估，不做代表题真判题，
    // 不计掌握度、不记失败事件——直接返回说明，避免误判为"失败"。
    if (session.problemId() == null) {
      return new Verdict(
          sessionId,
          JudgeStatus.NA,
          "概念模式（通识知识）以代码找茬为准，本模式不做代表题真判题，也不计入掌握度。");
    }
    String code = FeynmanAntiCheat.stripCodeFence(latestProducedCode(sessionId));
    if (code == null || code.isBlank()) {
      return failUnlessAlreadyPassed(session, "学弟还没有产出可判题代码。");
    }
    if (FeynmanAntiCheat.looksLikeLeakedAnswer(code)) {
      return failUnlessAlreadyPassed(session, "学弟产出的内容含 answer_key，疑似直接泄答。");
    }
    List<JudgeTestCase> cases = testCasesFor(session.problemId());
    if (cases.isEmpty()) {
      return new Verdict(
          sessionId,
          JudgeStatus.ERROR,
          "该知识点代表题暂无可用测试用例，判题未能进行，本次不计成败。");
    }
    CaseEvaluation evaluation;
    try {
      evaluation =
          judgeWorkerPool.submitAndWait(() -> caseEvaluator.evaluate(code, cases));
    } catch (RuntimeException exception) {
      log.error(
          "Feynman judge worker failed for session={} errorType={}",
          sessionId,
          exception.getClass().getSimpleName());
      return new Verdict(
          sessionId, JudgeStatus.ERROR, "判题系统暂时不可用，本次未判定教学成败，请稍后重试。");
    }
    com.pythontopia.submission.dto.Verdict finalVerdict = evaluation.finalVerdict();
    if (finalVerdict == com.pythontopia.submission.dto.Verdict.SYSTEM_ERROR) {
      return new Verdict(
          sessionId, JudgeStatus.ERROR, "判题系统异常，本次未判定教学成败，请稍后重试。");
    }
    if (finalVerdict != com.pythontopia.submission.dto.Verdict.AC) {
      String caseKind = cases.stream().anyMatch(JudgeTestCase::hidden) ? "隐藏用例" : "公开样例";
      return failUnlessAlreadyPassed(
          session, "学弟代码未通过代表题" + caseKind + "：" + finalVerdict.name());
    }
    if (jdbc.update(PASS_CLAIM_SQL, sessionId) != 1) {
      return alreadyPassed(sessionId);
    }
    masteryEvaluator.applyEvent(
        session.userId(), session.kcCode(), MasterySource.PRACTICE_PASS, BigDecimal.valueOf(0.10), "feynman");
    eventPublisher.publishEvent(
        new LearningEvent.TaughtAiSucceeded(session.userId(), session.kcCode(), sessionId));
    return new Verdict(
        sessionId, JudgeStatus.PASSED, "教学成功：学弟代码通过沙箱运行，掌握度已回流。");
  }

  private Verdict failUnlessAlreadyPassed(
      FeynmanSessionService.SessionData session, String message) {
    if (jdbc.update(FAIL_CLAIM_SQL, session.id()) != 1) {
      return alreadyPassed(session.id());
    }
    publishFailed(session);
    return new Verdict(session.id(), JudgeStatus.FAILED, message);
  }

  private static Verdict alreadyPassed(long sessionId) {
    return new Verdict(sessionId, JudgeStatus.PASSED, ALREADY_PASSED_MESSAGE);
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
