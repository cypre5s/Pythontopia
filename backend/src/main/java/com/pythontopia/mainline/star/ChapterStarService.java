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
   * 补星发放（错题复盘 / 无尽挑战，每途径至多 +1，封顶 3★，幂等；r15 §5，Wave 2 B1）。
   *
   * <p>条件校验由 {@link com.pythontopia.mainline.MainlineRecoveryService} 负责；本方法只把 {@code source}
   * 记入 {@code breakdown.recovered}（去重，封顶 2 途径）并按 §5 重算（核心星 + 补星，封顶 3★，只升不降）。
   * <b>幂等</b>：该 source 已在 recovered 内则不重复加星、不记事件。
   */
  @Transactional
  public ChapterStar applyRecoveryStar(long userId, int chapter, StarSource source) {
    if (!ChapterCatalog.isValidChapter(chapter)) {
      throw new IllegalArgumentException("chapter out of range 1..7: " + chapter);
    }
    if (source != StarSource.ERRORBOOK_RECOVERY && source != StarSource.ENDLESS_RECOVERY) {
      throw new IllegalArgumentException("applyRecoveryStar 仅用于补星途径: " + source);
    }
    ChapterStar existing = starRepository.find(userId, chapter).orElse(null);
    List<String> recovered = existingRecovered(existing);
    if (recovered.contains(source.name())) {
      return existing; // 幂等：本途径已发放，不重复加星 / 不记事件（existing 必非 null）。
    }
    List<String> newRecovered =
        java.util.stream.Stream.concat(recovered.stream(), java.util.stream.Stream.of(source.name()))
            .distinct()
            .limit(2)
            .toList();
    int existingStars = existing == null ? 0 : existing.stars();
    Integer bestScore = chapterTestRepository.findBestScore(userId, chapter).orElse(null);
    CoreStars core =
        StarFormula.evaluate(
            bestScore, allStagesComplete(userId, chapter), averageMastery(userId, chapter));
    int computed = Math.min(StarFormula.MAX_STARS, core.total() + newRecovered.size());
    int finalStars = Math.max(existingStars, computed);
    starRepository.upsert(userId, chapter, finalStars, bestScore, breakdownJson(core, newRecovered));
    if (finalStars > existingStars) {
      starRepository.insertEvent(
          userId,
          chapter,
          finalStars - existingStars,
          source.name(),
          "recovery " + source + " recovered=" + newRecovered.size());
      log.info(
          "chapter recovery star user={} ch={} {}→{} source={}",
          userId, chapter, existingStars, finalStars, source);
    }
    return starRepository.find(userId, chapter).orElseThrow();
  }
}
