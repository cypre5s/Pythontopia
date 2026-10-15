package com.pythontopia.npc;

import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.npc.dto.NpcDialogResponse;
import com.pythontopia.npc.dto.NpcGreetingResponse;
import com.pythontopia.npc.graph.NpcDialogGraphClient;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * NPC 首句个性化寒暄服务（§8）：组装近期记忆摘要 + 8 维画像 brief，复用 graph NpcDialogAgent 产出 1–2 句，
 * 按 npc×学生缓存 30 分钟。fail-fast：记忆/agent 失败 → 结构化降级（不伪造）；新玩家无记忆 → none。
 */
@Service
public class NpcGreetingService {

  private static final Logger log = LoggerFactory.getLogger(NpcGreetingService.class);

  /**
   * npc×学生 寒暄缓存有效期。R-15 反馈：改为「当日级」（26h，留余量覆盖到次日凌晨预生成刷新），
   * 配合 {@link NpcGreetingPrewarmJob} 每日预热，玩家白天进对话命中缓存、零等待、一天一更新。
   */
  static final Duration CACHE_TTL = Duration.ofHours(26);

  /** 注入寒暄上下文的近期记忆要点条数（与答疑 memory_brief 同量级）。 */
  private static final int MEMORY_LIMIT = 5;

  /** error_pattern 易错标签在 brief 中的最大条数（控 token，与答疑一致）。 */
  private static final int ERROR_TAG_LIMIT = 3;

  /** cognitive_style 贴标签的置信度门槛（<0.3 信号不足，不强断言，防幻觉）。 */
  private static final double STYLE_CONFIDENCE_FLOOR = 0.3;

  private final MemoryRetrievalService memoryRetrievalService;
  private final PassiveProfileService passiveProfileService;
  private final NpcPersonaService personaService;
  private final NpcDialogGraphClient graphClient;
  private final Clock clock;
  private final TutorDailyRequestLimitService requestLimit;

  /** npc×学生 → 缓存条目；仅缓存成功寒暄与「无记忆」确定态，不缓存下游故障（保证可恢复）。 */
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  @Autowired
  public NpcGreetingService(
      MemoryRetrievalService memoryRetrievalService,
      PassiveProfileService passiveProfileService,
      NpcPersonaService personaService,
      NpcDialogGraphClient graphClient,
      TutorDailyRequestLimitService requestLimit) {
    this(
        memoryRetrievalService,
        passiveProfileService,
        personaService,
        graphClient,
        Clock.systemUTC(),
        requestLimit);
  }

  NpcGreetingService(
      MemoryRetrievalService memoryRetrievalService,
      PassiveProfileService passiveProfileService,
      NpcPersonaService personaService,
      NpcDialogGraphClient graphClient,
      Clock clock,
      TutorDailyRequestLimitService requestLimit) {
    this.memoryRetrievalService = memoryRetrievalService;
    this.passiveProfileService = passiveProfileService;
    this.personaService = personaService;
    this.graphClient = graphClient;
    this.clock = clock;
    this.requestLimit = requestLimit;
  }

  /** 取（或复用缓存）该生在某 NPC 处的首句个性化寒暄。 */
  public NpcGreetingResponse greeting(long userId, String npcKey) {
    String key = cacheKey(userId, npcKey);
    CacheEntry cached = cache.get(key);
    if (cached != null && clock.instant().isBefore(cached.expiresAt())) {
      return cached.response().asCached();
    }
    return computeAndCache(userId, npcKey);
  }

  /**
   * 强制重算并刷新该生在某 NPC 的寒暄缓存（无视现有缓存），供 {@link NpcGreetingPrewarmJob}
   * 每日预生成调用：把当天缓存提前写好，玩家进对话直接命中。返回 degraded（下游故障）时不写缓存、可恢复。
   */
  public NpcGreetingResponse prewarm(long userId, String npcKey) {
    return computeAndCache(userId, npcKey);
  }

  /** 计算寒暄并按「非降级」写入缓存（成功寒暄 + 确定无记忆态才缓存；下游故障不缓存，下次重试）。 */
  private NpcGreetingResponse computeAndCache(long userId, String npcKey) {
    NpcGreetingResponse fresh = compute(userId, npcKey);
    if (!fresh.degraded()) {
      cache.put(cacheKey(userId, npcKey), new CacheEntry(fresh, clock.instant().plus(CACHE_TTL)));
    }
    return fresh;
  }

  private NpcGreetingResponse compute(long userId, String npcKey) {
    List<MemoryItemDto> memories;
    try {
      memories = memoryRetrievalService.retrieve(userId, null, MEMORY_LIMIT);
    } catch (RuntimeException ex) {
      // 记忆检索失败属下游故障 → 结构化降级，不伪造寒暄。
      log.warn(
          "npc greeting memory retrieval failed for user={} npc={} errorType={}",
          userId,
          npcKey,
          ex.getClass().getSimpleName());
      return NpcGreetingResponse.serviceUnavailable();
    }
    if (memories == null || memories.isEmpty()) {
      // 新玩家/无记忆：非故障，直接走脚本（不展示「记忆服务暂不可用」）。
      return NpcGreetingResponse.none();
    }

    String personaCore = loadPersonaCore(npcKey);
    String profileBrief = resolveProfileBrief(userId);
    String prompt = buildGreetingPrompt(profileBrief);

    requestLimit.reserve(userId);
    Optional<NpcDialogResponse> graphReply = graphClient.talk(npcKey, personaCore, memories, prompt);
    if (graphReply.isEmpty()) {
      // graph NpcDialogAgent 不可达/超时/缺 token → 结构化降级（fail-fast，不静默降级成普通寒暄）。
      // 具体 RestClientException 由 NpcDialogGraphClient 记录；此处补一条 service 级 reason 便于定位「记忆服务暂不可用」。
      log.warn(
          "npc greeting degraded for user={} npc={}: graph NpcDialogAgent unreachable (empty Optional; "
              + "检查 graph 是否可达/超时，以及是否需配 PYT_RAG_ADMIN_TOKEN)",
          userId,
          npcKey);
      return NpcGreetingResponse.serviceUnavailable();
    }
    NpcDialogResponse reply = graphReply.get();
    if (reply.replyMd() == null || reply.replyMd().isBlank()) {
      // 下游可达但返回空文本（LLM 兜底缺失/被裁剪）：视为不可用，避免展示空寒暄。
      log.warn(
          "npc greeting degraded for user={} npc={}: graph returned blank reply_md", userId, npcKey);
      return NpcGreetingResponse.serviceUnavailable();
    }
    return NpcGreetingResponse.personalized(reply.replyMd().trim(), reply.grounded(), reply.citedEventIds());
  }

  /** 加载 NPC 人格核心；人格缺失/读失败时退化为通用人格（不阻断寒暄产出）。 */
  private String loadPersonaCore(String npcKey) {
    try {
      return personaService.load(npcKey).coreMd();
    } catch (RuntimeException ex) {
      return "- NPC：" + npcKey + "\n- 风格：温和、具体、教育优先。\n";
    }
  }

  /** 取该生 8 维画像组装寒暄用 profile brief（认知风格 + 易错）；失败降级为空串（不阻断寒暄）。 */
  private String resolveProfileBrief(long userId) {
    try {
      return buildProfileBrief(passiveProfileService.getDimensions(userId));
    } catch (RuntimeException ex) {
      log.warn(
          "npc greeting profile brief degraded for user={} errorType={}",
          userId,
          ex.getClass().getSimpleName());
      return "";
    }
  }

  /**
   * 由画像维度组装寒暄 brief："认知风格: &lt;tag&gt;; 易错: &lt;a,b,c&gt;"。
   *
   * <p>认知风格取 cognitive_style 末尾 tag 且置信度≥{@value #STYLE_CONFIDENCE_FLOOR}（防幻觉，信号不足不贴标签）；
   * 易错取 error_pattern 最近至多 {@value #ERROR_TAG_LIMIT} 个 tag。无信号则空串。
   */
  static String buildProfileBrief(List<ProfileDimensionDto> dims) {
    String style = null;
    List<String> errTags = List.of();
    if (dims != null) {
      for (ProfileDimensionDto d : dims) {
        if (d == null) {
          continue;
        }
        if ("cognitive_style".equals(d.key())
            && d.confidence() >= STYLE_CONFIDENCE_FLOOR
            && d.tags() != null
            && !d.tags().isEmpty()) {
          style = d.tags().get(d.tags().size() - 1);
        } else if ("error_pattern".equals(d.key()) && d.tags() != null && !d.tags().isEmpty()) {
          List<String> t = d.tags();
          errTags = t.size() > ERROR_TAG_LIMIT ? t.subList(t.size() - ERROR_TAG_LIMIT, t.size()) : t;
        }
      }
    }
    List<String> parts = new ArrayList<>();
    if (style != null && !style.isBlank()) {
      parts.add("认知风格: " + style);
    }
    if (!errTags.isEmpty()) {
      parts.add("易错: " + String.join(",", errTags));
    }
    return String.join("; ", parts);
  }

  /**
   * 构造寒暄意图的 user 轮次（复用既有 {@code NpcDialogAgent} 路由）：让 NPC 结合可引用记忆主动打招呼，
   * 并自然带出一个具体的下一步学习建议；只能依据 memories 说「我记得」并引用对应 event_id。
   */
  static String buildGreetingPrompt(String profileBrief) {
    String profileSuffix =
        (profileBrief == null || profileBrief.isBlank()) ? "" : "（画像：" + profileBrief + "）";
    return "（旁白：学生"
        + profileSuffix
        + "刚走到你面前还没开口。请你作为本 NPC，结合上方可引用的长期记忆，用 1-2 句简短中文主动、"
        + "亲切地跟 TA 打招呼，并自然带出一个具体的下一步学习建议；只能依据 memories 说"
        + "「我记得」，并在 cited_event_ids 里引用对应 event_id。不要向 TA 提问，由你先开口。）";
  }

  private static String cacheKey(long userId, String npcKey) {
    return userId + ":" + npcKey;
  }

  /** 仅供测试：清空寒暄缓存。 */
  void clearCacheForTest() {
    cache.clear();
  }

  private record CacheEntry(NpcGreetingResponse response, Instant expiresAt) {}
}
