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
 * 章末星级统一收口（§5）：recalc 是唯一写 chapter_star 的入口（测试/stage/掌握度/补星触发）。
 * 幂等、只升不降（GREATEST）、breakdown 记 {test,stage,mastery,recovered}；recalc 保留既有 recovered 不覆盖补星。
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

  /** 重算并持久化核心星级（幂等、只升不降）。source：TEST / STAGE_COMPLETE / MASTERY。 */
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

    // stars 只升不降（GREATEST），breakdown 却整份覆盖：掌握度衰减后会出现「stars=3 但 breakdown 只有 2 分
    // 构成」的自相矛盾。保留了更高的历史星数时，breakdown 也逐键取历史最大，二者口径保持一致。
    CoreStars persisted = finalStars > computed ? maxCore(core, existing) : core;
    String breakdownJson = breakdownJson(persisted, recovered);
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

  /** 新算构成与既有 breakdown 逐键取 max（既有缺失/非法键按 0 处理）。 */
  private static CoreStars maxCore(CoreStars core, ChapterStar existing) {
    com.fasterxml.jackson.databind.JsonNode breakdown =
        existing == null ? null : existing.breakdown();
    if (breakdown == null || !breakdown.isObject()) {
      return core;
    }
    return new CoreStars(
        Math.max(core.test(), breakdown.path("test").asInt(0)),
        Math.max(core.stage(), breakdown.path("stage").asInt(0)),
        Math.max(core.mastery(), breakdown.path("mastery").asInt(0)));
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

  /** 补星发放（§5）：source 记入 breakdown.recovered（去重，封顶 2 途径）+ 重算；幂等（已发放不重复加星）。 */
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
