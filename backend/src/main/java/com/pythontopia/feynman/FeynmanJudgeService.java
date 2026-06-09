package com.pythontopia.feynman;

import com.pythontopia.feynman.dto.FeynmanDtos.Verdict;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySource;
import com.pythontopia.submission.judge.JudgeTestCase;
import com.pythontopia.submission.judge.PythonSandbox;
import com.pythontopia.submission.judge.SandboxRunResult;
import java.math.BigDecimal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeynmanJudgeService {

  private final JdbcTemplate jdbc;
  private final PythonSandbox sandbox;
  private final FeynmanSessionService sessionService;
  private final MasteryEvaluator masteryEvaluator;
  private final ApplicationEventPublisher eventPublisher;

  public FeynmanJudgeService(
      JdbcTemplate jdbc,
      PythonSandbox sandbox,
      FeynmanSessionService sessionService,
      MasteryEvaluator masteryEvaluator,
      ApplicationEventPublisher eventPublisher) {
    this.jdbc = jdbc;
    this.sandbox = sandbox;
    this.sessionService = sessionService;
    this.masteryEvaluator = masteryEvaluator;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public Verdict judge(long sessionId) {
    FeynmanSessionService.SessionData session = sessionService.loadSession(sessionId);
    String code = latestProducedCode(sessionId);
    if (code == null || code.isBlank() || looksLikeAnswerDump(code)) {
      publishFailed(session);
      return new Verdict(sessionId, false, "学弟还没有产出可判题代码，或内容疑似直接泄答");
    }
    SandboxRunResult result = sandbox.run(code, new JudgeTestCase("", "", 5000));
    if (result.isError()) {
      publishFailed(session);
      jdbc.update("UPDATE feynman_session SET status = 'JUDGED_FAIL' WHERE id = ?", sessionId);
      return new Verdict(sessionId, false, "学弟代码还没跑通：" + result.errorKind());
    }
    jdbc.update("UPDATE feynman_session SET status = 'JUDGED_PASS' WHERE id = ?", sessionId);
    masteryEvaluator.applyEvent(
        session.userId(), session.kcCode(), MasterySource.PRACTICE_PASS, BigDecimal.valueOf(0.10), "feynman");
    eventPublisher.publishEvent(
        new LearningEvent.TaughtAiSucceeded(session.userId(), session.kcCode(), sessionId));
    return new Verdict(sessionId, true, "教学成功：学弟代码通过沙箱运行，掌握度已回流。");
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

  private static boolean looksLikeAnswerDump(String code) {
    String text = code == null ? "" : code.toLowerCase();
    return text.contains("answer_key");
  }
}
