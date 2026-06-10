package com.pythontopia.mainline.star;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.mainline.ChapterCatalog;
import com.pythontopia.mainline.MainlineStageProgressRepository;
import com.pythontopia.mainline.MainlineStageRepository;
import com.pythontopia.mainline.domain.ChapterStar;
import com.pythontopia.mainline.domain.MainlineStage;
import com.pythontopia.mainline.domain.MainlineStageProgress;
import com.pythontopia.mainline.star.StarFormula.CoreStars;
import com.pythontopia.mainline.test.ChapterTestRepository;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 章末星级统一收口（r15 计划 §5）。{@link #recalc} 是<b>唯一</b>写 chapter_star 的入口，
 * 由测试提交 / stage 完成 / 掌握度刷新 / 补星事件统一触发。
 *
 * <p>性质：
 *
 * <ul>
 *   <li><b>幂等</b>：相同输入重复 recalc 不改变 stars、不新增 star_award_event（仅当星数上升才记一条 delta）。
 *   <li><b>只升不降</b>：{@code stars = GREATEST(existing, computed)}（DB 层 GREATEST，§5）。
 *   <li><b>构成可溯</b>：breakdown 记 {@code {test,stage,mastery,recovered}}。
 * </ul>
 *
 * <p>补星（ERRORBOOK_RECOVERY / ENDLESS_RECOVERY）+ 无尽 summary 校验 + recovery 端点由 Wave 2 子代理
 * B1 负责（见 {@link #applyRecoveryStar}）。本服务在 recalc 时<b>保留</b> existing.breakdown.recovered，
 * 确保 B1 已发放的补星不被核心重算覆盖。
 */
@Service
public class ChapterStarService {

  private static final Logger log = LoggerFactory.getLogger(ChapterStarService.class);

  private final ChapterStarRepository starRepository;
  private final MainlineStageRepository stageRepository;
  private final MainlineStageProgressRepository progressRepository;
  private final ChapterTestRepository chapterTestRepository;
  private final MasteryRepository masteryRepository;
  private final ObjectMapper objectMapper;

  public ChapterStarService(
      ChapterStarRepository starRepository,
      MainlineStageRepository stageRepository,
      MainlineStageProgressRepository progressRepository,
      ChapterTestRepository chapterTestRepository,
      MasteryRepository masteryRepository,
      ObjectMapper objectMapper) {
    this.starRepository = starRepository;
    this.stageRepository = stageRepository;
    this.progressRepository = progressRepository;
    this.chapterTestRepository = chapterTestRepository;
    this.masteryRepository = masteryRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * 重算并持久化某学生某章的核心星级（幂等、只升不降）。
   *
   * @param source 触发来源（仅核心路径 TEST / STAGE_COMPLETE / MASTERY 在此使用）。
   * @return 重算后的 {@link ChapterStar}。
   */
  @Transactional
  public ChapterStar recalc(long userId, int chapter, StarSource source) {
    if (!ChapterCatalog.isValidChapter(chapter)) {
      throw new IllegalArgumentException("chapter out of range 1..7: " + chapter);
    }
    boolean allStagesComplete = allStagesComplete(userId, chapter);
    Integer bestScore = chapterTestRepository.findBestScore(userId, chapter).orElse(null);
    double avgMastery = averageMastery(userId, chapter);

    CoreStars core = StarFormula.evaluate(bestScore, allStagesComplete, avgMastery);

    ChapterStar existing = starRepository.find(userId, chapter).orElse(null);
    int existingStars = existing == null ? 0 : existing.stars();
    List<String> recovered = existingRecovered(existing);

    int computed = Math.min(StarFormula.MAX_STARS, core.total() + recovered.size());
    int finalStars = Math.max(existingStars, computed);

    String breakdownJson = breakdownJson(core, recovered);
    starRepository.upsert(userId, chapter, finalStars, bestScore, breakdownJson);

    if (finalStars > existingStars) {
      int delta = finalStars - existingStars;
      starRepository.insertEvent(
          userId,
          chapter,
          delta,
          source.name(),
          "recalc core=" + core.total() + " recovered=" + recovered.size());
      log.info(
          "chapter star up user={} ch={} {}→{} source={}",
          userId, chapter, existingStars, finalStars, source);
    }
    return starRepository.find(userId, chapter).orElseThrow();
  }

  /** overview 读路径复用：本章是否所有 stage 三件套完成。 */
  public boolean allStagesComplete(long userId, int chapter) {
    List<MainlineStage> stages = stageRepository.findByChapter(chapter);
    if (stages.isEmpty()) {
      return false;
    }
    Set<Long> completedStageIds =
        progressRepository.findByUser(userId).stream()
            .filter(MainlineStageProgress::completed)
            .map(MainlineStageProgress::stageId)
            .collect(Collectors.toSet());
    return stages.stream().allMatch(stage -> completedStageIds.contains(stage.id()));
  }

  /** 本章 KC 平均掌握度（缺失 KC 计 0，避免「只练 1 个 KC 就满分」的虚高）。 */
  public double averageMastery(long userId, int chapter) {
    List<String> kcCodes = ChapterCatalog.kcCodes(chapter);
    if (kcCodes.isEmpty()) {
      return 0.0;
    }
    Map<String, BigDecimal> scores =
        masteryRepository.findAll(userId).stream()
            .filter(s -> s.masteryScore() != null)
            .collect(
                Collectors.toMap(
                    MasterySnapshot::kcCode, MasterySnapshot::masteryScore, (a, b) -> b));
    double sum = 0.0;
    for (String kc : kcCodes) {
      BigDecimal score = scores.get(kc);
      if (score != null) {
        sum += score.doubleValue();
      }
    }
    return sum / kcCodes.size();
  }

  private List<String> existingRecovered(ChapterStar existing) {
    if (existing == null || existing.breakdown() == null) {
      return List.of();
    }
    var node = existing.breakdown().get("recovered");
    if (node == null || !node.isArray()) {
      return List.of();
    }
    return java.util.stream.StreamSupport.stream(node.spliterator(), false)
        .map(com.fasterxml.jackson.databind.JsonNode::asText)
        .filter(s -> s != null && !s.isBlank())
        .distinct()
        .limit(2) // 至多 2 条补星途径（ERRORBOOK / ENDLESS），封顶 3★（§5）。
        .toList();
  }

  private String breakdownJson(CoreStars core, List<String> recovered) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("test", core.test());
    node.put("stage", core.stage());
    node.put("mastery", core.mastery());
    ArrayNode recoveredArr = node.putArray("recovered");
    recovered.forEach(recoveredArr::add);
    try {
      return objectMapper.writeValueAsString(node);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      return "{\"test\":0,\"stage\":0,\"mastery\":0,\"recovered\":[]}";
    }
  }

  /**
   * TODO(Wave 2 · 子代理 B1)：补星发放钩子（错题复盘 / 无尽挑战，每途径至多 +1，封顶 3★，幂等）。
   *
   * <p>实现要点（B1）：校验补星条件（bug-mine RESOLVED ≥ 3 / 无尽单轮答对 ≥ 8 且正确率 ≥ 80%），
   * 将 {@code source} 写入 chapter_star.breakdown.recovered（去重），插入 star_award_event(delta=+1)，
   * 然后调用 {@link #recalc} 收口。本核心服务已保证 recalc 保留既有 recovered，不会覆盖补星。
   *
   * <p>当前未接线，调用即 fail-fast（不静默降级，遵守 AGENTS.md）。
   */
  public ChapterStar applyRecoveryStar(long userId, int chapter, StarSource source) {
    throw new UnsupportedOperationException(
        "补星发放（" + source + "）由 Wave 2 子代理 B1 实现；A1 仅交付核心星级 recalc 钩子");
  }
}
