package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.feynman.dto.FeynmanDtos.Verdict;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.submission.judge.JudgeCaseEvaluator;
import com.pythontopia.submission.judge.JudgeTestCase;
import com.pythontopia.submission.judge.PythonSandbox;
import com.pythontopia.submission.judge.SandboxRunResult;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

class FeynmanJudgeServiceTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PythonSandbox sandbox = mock(PythonSandbox.class);
  private final FeynmanSessionService sessionService = mock(FeynmanSessionService.class);
  private final MasteryEvaluator masteryEvaluator = mock(MasteryEvaluator.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final PublicProblemRepository problemRepository = mock(PublicProblemRepository.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JudgeCaseEvaluator caseEvaluator = new JudgeCaseEvaluator(sandbox, objectMapper);

  private final FeynmanJudgeService service =
      new FeynmanJudgeService(
          jdbc, sandbox, sessionService, masteryEvaluator, publisher, problemRepository, caseEvaluator);

  @Test
  void judgeFailsWhenProducedCodeDoesNotMatchRepresentativeHiddenCase() {
    when(sessionService.loadSession(10L))
        .thenReturn(new FeynmanSessionService.SessionData(10L, 7L, "PY43-KC-010", 101L, "TAUGHT", "{}"));
    when(jdbc.query(eq("SELECT taught_fix FROM feynman_turn "
            + "WHERE session_id = ? AND taught_fix IS NOT NULL "
            + "ORDER BY created_at DESC LIMIT 1"),
        any(org.springframework.jdbc.core.RowMapper.class),
        eq(10L)))
        .thenReturn(List.of("print(1)"));
    when(problemRepository.findTestCases(101L))
        .thenReturn(
            Optional.of(
                new PublicProblemRepository.JudgeTestCases(
                    objectMapper.createArrayNode(),
                    objectMapper
                        .createArrayNode()
                        .add(
                            objectMapper
                                .createObjectNode()
                                .put("input", "2\n")
                                .put("output", "4")))));
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(eq("print(1)"), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 0, "1\n", "", 8, null));

    Verdict verdict = service.judge(10L);

    assertThat(verdict.passed()).isFalse();
    assertThat(verdict.message()).contains("隐藏用例");
    verify(jdbc).update("UPDATE feynman_session SET status = 'JUDGED_FAIL' WHERE id = ?", 10L);
  }
}
