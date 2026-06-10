package com.pythontopia.mainline.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.mainline.test.question.QuestionItem;
import com.pythontopia.mainline.test.question.QuestionPaper;
import com.pythontopia.mainline.test.question.QuestionType;
import com.pythontopia.submission.judge.JudgeCaseEvaluator;
import com.pythontopia.submission.judge.JudgeCaseEvaluator.CaseEvaluation;
import com.pythontopia.submission.judge.JudgeTestCase;
import com.pythontopia.submission.judge.JudgeWorkerPool;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 章测服务端判分（§4.2，全本地零 LLM）：CHOICE/JUDGE 精确比对；FILL 归一化(NFKC+去空白+大小写+候选集)；
 * CODING 走 JudgeCaseEvaluator 沙箱跑 hidden_test_cases 按通过比例给分（经 JudgeWorkerPool 并发池）。
 */
@Service
public class ChapterTestJudge {

  /** 章测编程题最多取的隐藏用例数（避免单题判分过久占用判题池）。 */
  static final int MAX_CODING_CASES = 20;

  private final JudgeCaseEvaluator caseEvaluator;
  private final JudgeWorkerPool judgeWorkerPool;

  public ChapterTestJudge(JudgeCaseEvaluator caseEvaluator, JudgeWorkerPool judgeWorkerPool) {
    this.caseEvaluator = caseEvaluator;
    this.judgeWorkerPool = judgeWorkerPool;
  }

  public JudgeReport judge(QuestionPaper paper, Map<String, String> answers) {
    Map<String, String> safeAnswers = answers == null ? Map.of() : answers;
    List<QuestionJudgement> results = new ArrayList<>();
    int total = 0;
    int max = 0;
    for (QuestionItem item : paper.items()) {
      String submitted = safeAnswers.get(item.qid());
      QuestionJudgement judgement = judgeOne(item, submitted);
      results.add(judgement);
      total += judgement.awardedScore();
      max += judgement.maxScore();
    }
    return new JudgeReport(total, max, results);
  }

  private QuestionJudgement judgeOne(QuestionItem item, String submitted) {
    QuestionType type = item.type();
    if (type == null) {
      return objective(item, submitted, false, 0);
    }
    return switch (type) {
      case CHOICE -> judgeChoice(item, submitted);
      case JUDGE -> judgeBoolean(item, submitted);
      case FILL -> judgeFill(item, submitted);
      case CODING -> judgeCoding(item, submitted);
    };
  }

  private QuestionJudgement judgeChoice(QuestionItem item, String submitted) {
    String answerKey = item.answer() == null ? "" : item.answer().asText("");
    boolean correct = false;
    if (submitted != null) {
      // 1) 直接比对选项标识（A/B/C/D，大小写/空白不敏感）。
      if (normChoice(submitted).equals(normChoice(answerKey))) {
        correct = true;
      } else {
        // 2) 兼容提交「选项原文」：把 answerKey 解析为字母索引取对应选项原文再比。
        int idx = letterIndex(answerKey);
        List<String> options = item.safeOptions();
        if (idx >= 0 && idx < options.size()) {
          correct = normFill(submitted).equals(normFill(options.get(idx)));
        }
      }
    }
    return objective(item, submitted, correct, correct ? item.score() : 0);
  }

  private QuestionJudgement judgeBoolean(QuestionItem item, String submitted) {
    Boolean expected = parseBool(item.answer());
    Boolean actual = parseBool(submitted);
    boolean correct = expected != null && expected.equals(actual);
    return objective(item, submitted, correct, correct ? item.score() : 0);
  }

  private QuestionJudgement judgeFill(QuestionItem item, String submitted) {
    boolean correct = false;
    if (submitted != null) {
      String normalized = normFill(submitted);
      for (String candidate : item.safeAnswers()) {
        if (normFill(candidate).equals(normalized)) {
          correct = true;
          break;
        }
      }
    }
    return objective(item, submitted, correct, correct ? item.score() : 0);
  }

  private QuestionJudgement judgeCoding(QuestionItem item, String submitted) {
    List<JudgeTestCase> cases = extractCodingCases(item);
    if (submitted == null || submitted.isBlank() || cases.isEmpty()) {
      return new QuestionJudgement(
          item.qid(),
          QuestionType.CODING,
          false,
          0,
          item.score(),
          submitted,
          item.referenceSolution(),
          item.explain(),
          0,
          cases.size());
    }
    // 沙箱子进程跑在既有判题并发池上（JudgeWorkerPool 控并发），与 JudgeService 同源。
    CaseEvaluation evaluation =
        judgeWorkerPool.submitAndWait(() -> caseEvaluator.evaluate(submitted, cases));
    int passed = evaluation.passedCases();
    int totalCases = cases.size();
    int awarded = totalCases == 0 ? 0 : Math.round((float) item.score() * passed / totalCases);
    boolean correct = passed == totalCases && totalCases > 0;
    return new QuestionJudgement(
        item.qid(),
        QuestionType.CODING,
        correct,
        awarded,
        item.score(),
        submitted,
        item.referenceSolution(),
        item.explain(),
        passed,
        totalCases);
  }

  private List<JudgeTestCase> extractCodingCases(QuestionItem item) {
    JsonNode hidden = item.hiddenTestCases();
    if (hidden != null && hidden.isArray() && hidden.size() > 0) {
      return caseEvaluator.extractCases(hidden, MAX_CODING_CASES);
    }
    // 兜底：无隐藏用例时退化到公开样例（不应常态，graph 端会保证 hidden 用例齐备）。
    List<JudgeTestCase> fromSamples = new ArrayList<>();
    for (QuestionItem.Sample sample : item.safeSamples()) {
      fromSamples.add(new JudgeTestCase(sample.input(), sample.output(), 5000));
    }
    return fromSamples;
  }

  private QuestionJudgement objective(
      QuestionItem item, String submitted, boolean correct, int awarded) {
    return new QuestionJudgement(
        item.qid(),
        item.type(),
        correct,
        awarded,
        item.score(),
        submitted,
        correctAnswerText(item),
        item.explain(),
        null,
        null);
  }

  private String correctAnswerText(QuestionItem item) {
    if (item.type() == QuestionType.FILL) {
      return String.join(" / ", item.safeAnswers());
    }
    return item.answer() == null ? "" : item.answer().asText("");
  }

  // --- 归一化工具 -----------------------------------------------------------

  /** 选项标识归一：trim + 去空白 + 大写（A/B/C/D 不区分大小写）。 */
  static String normChoice(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("\\s+", "").trim().toUpperCase(java.util.Locale.ROOT);
  }

  /** 填空归一：NFKC（全/半角统一）+ 去全部空白 + 小写 + trim。 */
  static String normFill(String value) {
    if (value == null) {
      return "";
    }
    String nfkc = Normalizer.normalize(value, Normalizer.Form.NFKC);
    return nfkc.replaceAll("\\s+", "").trim().toLowerCase(java.util.Locale.ROOT);
  }

  static int letterIndex(String letter) {
    if (letter == null || letter.isEmpty()) {
      return -1;
    }
    char c = Character.toUpperCase(letter.trim().charAt(0));
    return (c >= 'A' && c <= 'Z') ? c - 'A' : -1;
  }

  /** 解析 JUDGE 提交：接受 true/false、1/0、对/错、yes/no、T/F。 */
  static Boolean parseBool(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    return parseBool(node.asText(null));
  }

  static Boolean parseBool(String value) {
    if (value == null) {
      return null;
    }
    String v = value.trim().toLowerCase(java.util.Locale.ROOT);
    return switch (v) {
      case "true", "t", "1", "yes", "y", "对", "正确", "是" -> Boolean.TRUE;
      case "false", "f", "0", "no", "n", "错", "错误", "否" -> Boolean.FALSE;
      default -> null;
    };
  }

  /** 单题判分明细（供 detail JSONB 与结果 DTO 复用）。 */
  public record QuestionJudgement(
      String qid,
      QuestionType type,
      boolean correct,
      int awardedScore,
      int maxScore,
      String yourAnswer,
      String correctAnswer,
      String explain,
      Integer passedCases,
      Integer totalCases) {}

  /** 整卷判分报告。 */
  public record JudgeReport(int score, int maxScore, List<QuestionJudgement> items) {}
}
