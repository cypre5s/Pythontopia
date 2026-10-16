package com.pythontopia.mainline.practice;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.ResourceNotFoundException;
import com.pythontopia.mainline.ChapterUnlockGuard;
import com.pythontopia.mainline.MainlineStageCompletionPort;
import com.pythontopia.mainline.MainlineStageRepository;
import com.pythontopia.mainline.domain.MainlineStage;
import com.pythontopia.mainline.practice.dto.PracticeAnswerResultDto;
import com.pythontopia.mainline.practice.dto.StagePracticeQuestionDto;
import com.pythontopia.mainline.star.ChapterStarRepository;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 「练-客观题」题库域服务：实现 {@link MainlinePracticePort}（供 P3 标准聚合消费）+ 服务端判分。
 *
 * <p>判分策略：{@code CHOICE}/{@code JUDGE} 比对答案文本（NFKC + 去空白，大小写敏感）；{@code FILL} 逐空规范化
 * （trim + NFKC 去全角空格 + 去成对引号 + 去空白 + 忽略大小写）。答对则幂等并集计入小节进度并触发一次
 * stage 完成重算。<b>答错只回讲解、不回答案</b>——回传正确答案等于把「练-客观题」这道门直接送给学生
 * （11/24 个小节 {@code required_example_count = 0}，客观题是唯一的「练」门槛），也违反「不给可复制提交的
 * 完整答案」硬约束；订正答案在答对后 / 章测结果页给出。
 *
 * <p>{@link MainlineStageCompletionPort} 经 {@link ObjectProvider} 惰性获取：既满足「bean 不在时跳过」的
 * 优雅降级（与 {@code Optional.ifPresent} 语义一致），又避免与 P3 的 {@code Optional<MainlinePracticePort>}
 * 形成双向构造注入循环（Spring Boot 默认禁用循环引用，否则应用无法启动）。
 */
@Service
public class MainlinePracticeService implements MainlinePracticePort {

  private final MainlinePracticeRepository repository;
  private final MainlineStageRepository stageRepository;
  private final ChapterStarRepository starRepository;
  private final ObjectProvider<MainlineStageCompletionPort> completionPortProvider;

  public MainlinePracticeService(
      MainlinePracticeRepository repository,
      MainlineStageRepository stageRepository,
      ChapterStarRepository starRepository,
      ObjectProvider<MainlineStageCompletionPort> completionPortProvider) {
    this.repository = repository;
    this.stageRepository = stageRepository;
    this.starRepository = starRepository;
    this.completionPortProvider = completionPortProvider;
  }

  // --- MainlinePracticePort ------------------------------------------------

  @Override
  public List<StagePracticeQuestionDto> questionsForStage(int chapter, int stageIndex) {
    return repository.findByStage(chapter, stageIndex).stream()
        .map(MainlinePracticeQuestion::toDto)
        .toList();
  }

  @Override
  public int requiredQuestionCount(int chapter, int stageIndex) {
    return repository.findByStage(chapter, stageIndex).size();
  }

  @Override
  public List<Long> correctQuestionIds(long userId, long stageId) {
    return repository.findCorrectQuestionIds(userId, stageId);
  }

  @Override
  public boolean questionsComplete(long userId, long stageId, int chapter, int stageIndex) {
    List<MainlinePracticeQuestion> questions = repository.findByStage(chapter, stageIndex);
    if (questions.isEmpty()) {
      return true; // 无客观题要求 → 视为已完成。
    }
    Set<Long> correct = new HashSet<>(repository.findCorrectQuestionIds(userId, stageId));
    return questions.stream().allMatch(q -> correct.contains(q.id()));
  }

  // --- 判分 ----------------------------------------------------------------

  /**
   * 客观题判分：校验章已解锁 + 题目归属该 stage → 判对错 → 答对幂等计入进度并触发 stage 完成重算。
   * 答对回传正确答案 + 讲解；答错只回讲解（{@link PracticeAnswerResultDto#correctAnswers()} 为空），
   * 避免一次试错即拿到可直接复制回填的答案。
   */
  public PracticeAnswerResultDto grade(
      long userId, long stageId, long questionId, List<String> answers) {
    MainlineStage stage =
        stageRepository
            .findById(stageId)
            .orElseThrow(() -> new ResourceNotFoundException("stage 不存在: " + stageId));
    ChapterUnlockGuard.requireUnlocked(starRepository, userId, stage.chapter());
    MainlinePracticeQuestion question =
        repository
            .findById(questionId)
            .orElseThrow(() -> new ResourceNotFoundException("客观题不存在: " + questionId));
    if (question.chapter() != stage.chapter() || question.stageIndex() != stage.stageIndex()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "PRACTICE_QUESTION_STAGE_MISMATCH", "题目不属于该小节");
    }

    List<String> submitted = answers == null ? List.of() : answers;
    boolean correct = judge(question, submitted);
    if (correct) {
      repository.appendCorrect(userId, stageId, questionId); // 幂等：重复答对不重复计。
      triggerCompletion(userId, stageId);
    }
    return new PracticeAnswerResultDto(
        questionId, correct, correct ? question.answers() : List.of(), question.explanation());
  }

  /** bean 不在时跳过（{@code Optional.ifPresent} 语义），不影响答题打点本身。 */
  private void triggerCompletion(long userId, long stageId) {
    Optional.ofNullable(completionPortProvider.getIfAvailable())
        .ifPresent(port -> port.recheckCompletion(userId, stageId));
  }

  private static boolean judge(MainlinePracticeQuestion question, List<String> submitted) {
    return isCorrect(question.type(), question.answers(), submitted);
  }

  /** Shared server-side matcher for the mainline and objective Boss submissions. */
  public static boolean isCorrect(
      PracticeQuestionType type, List<String> expected, List<String> submitted) {
    if (type == null) return false;
    List<String> answers = expected == null ? List.of() : expected;
    List<String> values = submitted == null ? List.of() : submitted;
    return switch (type) {
      case CHOICE, JUDGE -> judgeObjective(answers, values);
      case FILL -> judgeFill(answers, values);
    };
  }

  /** 选择 / 判断：取首个提交项，与任一正确答案文本（NFKC + 去空白）相等即对。 */
  private static boolean judgeObjective(List<String> answers, List<String> submitted) {
    if (submitted.isEmpty()) {
      return false;
    }
    String picked = normalizeObjective(submitted.get(0));
    if (picked.isEmpty()) {
      return false;
    }
    return answers.stream().anyMatch(ans -> normalizeObjective(ans).equals(picked));
  }

  /** 填空：提交空数须与答案空数一致，逐空规范化全等才算对。 */
  private static boolean judgeFill(List<String> expected, List<String> submitted) {
    if (expected.isEmpty() || submitted.size() != expected.size()) {
      return false;
    }
    for (int i = 0; i < expected.size(); i++) {
      if (!normalizeFill(submitted.get(i)).equals(normalizeFill(expected.get(i)))) {
        return false;
      }
    }
    return true;
  }

  // --- 归一化 --------------------------------------------------------------

  /** 选择 / 判断归一：NFKC（全/半角统一，含全角空格）→ 去全部空白 → trim（大小写敏感）。 */
  static String normalizeObjective(String value) {
    if (value == null) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("\\s+", "").trim();
  }

  /** 填空归一：NFKC → trim → 去成对引号 → 去全部空白 → 小写。 */
  static String normalizeFill(String value) {
    if (value == null) {
      return "";
    }
    String nfkc = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
    String unquoted = stripPairedQuotes(nfkc);
    return unquoted.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
  }

  /** 成对开闭引号映射（直引号自配对 + 中英文弯引号 + 中文书名/方引号）。 */
  private static final Map<Character, Character> QUOTE_PAIRS =
      Map.of(
          '"', '"',
          '\'', '\'',
          '`', '`',
          '\u201C', '\u201D', // “ ”
          '\u2018', '\u2019', // ‘ ’
          '\u300C', '\u300D', // 「 」
          '\u300E', '\u300F'); // 『 』

  /** 反复剥离首尾成对引号（{@code "abc"} / {@code 「abc」} → {@code abc}），剥一层 trim 一次。 */
  static String stripPairedQuotes(String value) {
    String result = value;
    while (result.length() >= 2) {
      char first = result.charAt(0);
      char last = result.charAt(result.length() - 1);
      Character expectedClose = QUOTE_PAIRS.get(first);
      if (expectedClose != null && expectedClose == last) {
        result = result.substring(1, result.length() - 1).trim();
      } else {
        break;
      }
    }
    return result;
  }
}
