package com.pythontopia.mainline.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.mainline.test.ChapterTestJudge.JudgeReport;
import com.pythontopia.mainline.test.question.QuestionItem;
import com.pythontopia.mainline.test.question.QuestionPaper;
import com.pythontopia.mainline.test.question.QuestionType;
import com.pythontopia.submission.judge.JudgeCaseEvaluator;
import com.pythontopia.submission.judge.JudgeWorkerPool;
import com.pythontopia.submission.judge.PythonSandbox;
import com.pythontopia.submission.judge.SandboxRunResult;
import com.pythontopia.submission.dto.Verdict;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 章测服务端判分：客观题精确/归一化 + 编程题按通过用例比例给分（沙箱 mock）。 */
class ChapterTestJudgeTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final PythonSandbox sandbox = mock(PythonSandbox.class);
  private final JudgeCaseEvaluator caseEvaluator = new JudgeCaseEvaluator(sandbox, mapper);
  private final ChapterTestJudge judge =
      new ChapterTestJudge(caseEvaluator, new JudgeWorkerPool());

  private QuestionItem choice(String qid, List<String> options, String answer, int score) {
    return new QuestionItem(
        qid, QuestionType.CHOICE, "stem", null, null, options, mapper.valueToTree(answer),
        null, null, null, null, null, "kc", List.of(), "explain", score);
  }

  private QuestionItem judgeItem(String qid, boolean answer, int score) {
    return new QuestionItem(
        qid, QuestionType.JUDGE, "stem", null, null, null, mapper.valueToTree(answer),
        null, null, null, null, null, "kc", List.of(), "explain", score);
  }

  private QuestionItem fill(String qid, List<String> answers, int score) {
    return new QuestionItem(
        qid, QuestionType.FILL, "stem ____", null, null, null, null, answers, "normalized",
        null, null, null, "kc", List.of(), "explain", score);
  }

  private QuestionItem coding(String qid, int cases, int score) {
    ArrayNode hidden = mapper.createArrayNode();
    for (int i = 0; i < cases; i++) {
      hidden.add(mapper.createObjectNode().put("input", "x").put("output", "5"));
    }
    return new QuestionItem(
        qid, QuestionType.CODING, null, "title", "statement", null, null, null, null,
        List.of(), hidden, "print(5)", "kc", List.of(), "explain", score);
  }

  @Test
  void choiceExactByLetterAndByOptionText() {
    QuestionPaper paper =
        new QuestionPaper(
            List.of(choice("c1", List.of("A. x", "B. y", "C. z", "D. w"), "B", 8)));
    // 提交字母（大小写不敏感）
    assertThat(judge.judge(paper, Map.of("c1", "b")).score()).isEqualTo(100);
    // 提交选项原文
    assertThat(judge.judge(paper, Map.of("c1", "B. y")).score()).isEqualTo(100);
    // 错选项
    assertThat(judge.judge(paper, Map.of("c1", "A")).score()).isZero();
  }

  @Test
  void judgeBooleanAcceptsVariants() {
    QuestionPaper paper = new QuestionPaper(List.of(judgeItem("j1", true, 5)));
    assertThat(judge.judge(paper, Map.of("j1", "true")).score()).isEqualTo(100);
    assertThat(judge.judge(paper, Map.of("j1", "对")).score()).isEqualTo(100);
    assertThat(judge.judge(paper, Map.of("j1", "1")).score()).isEqualTo(100);
    assertThat(judge.judge(paper, Map.of("j1", "false")).score()).isZero();
  }

  @Test
  void fillNormalizedMatchesCandidateSet() {
    QuestionPaper paper = new QuestionPaper(List.of(fill("f1", List.of("range", "range()"), 5)));
    assertThat(judge.judge(paper, Map.of("f1", "range")).score()).isEqualTo(100);
    // 全角括号 + 大小写 + 空白 归一化命中
    assertThat(judge.judge(paper, Map.of("f1", " RANGE（）".replace("（）", "()"))).score())
        .isEqualTo(100);
    assertThat(judge.judge(paper, Map.of("f1", "len")).score()).isZero();
  }

  @Test
  void fillNormalizesFullWidthToHalfWidth() {
    QuestionPaper paper = new QuestionPaper(List.of(fill("f1", List.of("int()"), 5)));
    // 全角 int（） → 归一化为 int()
    assertThat(judge.judge(paper, Map.of("f1", "ｉｎｔ（）")).score()).isEqualTo(100);
  }

  @Test
  void fillAcceptsFunctionNameWithOrWithoutEmptyCall() {
    QuestionPaper paper = new QuestionPaper(List.of(fill("f1", List.of("input"), 5)));

    assertThat(judge.judge(paper, Map.of("f1", "input()")).score()).isEqualTo(100);
  }

  @Test
  void codingAllCasesPassGivesFullScore() {
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any()))
        .thenReturn(new SandboxRunResult(false, 0, "5\n", "", 5, null));
    QuestionPaper paper = new QuestionPaper(List.of(coding("p1", 4, 30)));
    JudgeReport report = judge.judge(paper, Map.of("p1", "print(5)"));
    assertThat(report.score()).isEqualTo(100);
    assertThat(report.maxScore()).isEqualTo(100);
    assertThat(report.items().get(0).correct()).isTrue();
    assertThat(report.items().get(0).passedCases()).isEqualTo(4);
    assertThat(report.items().get(0).totalCases()).isEqualTo(4);
  }

  @Test
  void codingPartialPassScoresByRatio() {
    lenient().when(sandbox.name()).thenReturn("subprocess");
    // 前两用例通过，第三个 WA（评测器首错即停）→ passed=2 / total=4 → round(30*2/4)=15。
    when(sandbox.run(any(), any()))
        .thenReturn(
            new SandboxRunResult(false, 0, "5\n", "", 5, null),
            new SandboxRunResult(false, 0, "5\n", "", 5, null),
            new SandboxRunResult(false, 0, "9\n", "", 5, null),
            new SandboxRunResult(false, 0, "5\n", "", 5, null));
    QuestionPaper paper = new QuestionPaper(List.of(coding("p1", 4, 30)));
    JudgeReport report = judge.judge(paper, Map.of("p1", "print(5)"));
    assertThat(report.score()).isEqualTo(50);
    assertThat(report.items().get(0).correct()).isFalse();
    assertThat(report.items().get(0).passedCases()).isEqualTo(2);
  }

  @Test
  void codingSandboxFailureReturnsRetryableServiceError() {
    when(sandbox.name()).thenReturn("piston:3.12.0");
    when(sandbox.run(any(), any()))
        .thenReturn(new SandboxRunResult(false, -1, "", "Sandbox not configured", 0, "SYSTEM_ERROR"));
    QuestionPaper paper = new QuestionPaper(List.of(coding("p1", 1, 30)));

    assertThatThrownBy(() -> judge.judge(paper, Map.of("p1", "print(5)")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(exception.code()).isEqualTo("CHAPTER_TEST_JUDGE_UNAVAILABLE");
            });
  }

  @Test
  void unansweredOrBlankCodingScoresZero() {
    QuestionPaper paper =
        new QuestionPaper(List.of(choice("c1", List.of("A", "B"), "A", 8), coding("p1", 3, 30)));
    JudgeReport report = judge.judge(paper, Map.of()); // 全不作答
    assertThat(report.score()).isZero();
    assertThat(report.maxScore()).isEqualTo(100);
  }

  @Test
  void totalScoreSumsAcrossTypes() {
    QuestionPaper paper =
        new QuestionPaper(
            List.of(
                choice("c1", List.of("A", "B"), "A", 8),
                judgeItem("j1", true, 5),
                fill("f1", List.of("x"), 5)));
    JudgeReport report =
        judge.judge(paper, Map.of("c1", "A", "j1", "true", "f1", "x"));
    assertThat(report.score()).isEqualTo(100);
    assertThat(report.maxScore()).isEqualTo(100);
    assertThat(report.items()).extracting(ChapterTestJudge.QuestionJudgement::maxScore)
        .containsExactly(8, 5, 5);
  }
}
