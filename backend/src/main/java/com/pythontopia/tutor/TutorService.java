package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.service.CourseVideoTranscriptService;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.tutor.dto.TutorAskRequest;
import com.pythontopia.tutor.dto.TutorSessionDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TutorService {

  private static final Logger log = LoggerFactory.getLogger(TutorService.class);

  /** 注入答疑上下文的近期记忆要点条数（Task 2.1）。 */
  private static final int MEMORY_HIGHLIGHT_LIMIT = 5;
  /** 多轮记忆：注入答疑上下文的同会话既往轮次上限（取最近 N 轮）。 */
  private static final int HISTORY_TURN_LIMIT = 6;
  /** 多轮记忆：单轮历史答案在 brief 中的截断长度（控 token）。 */
  private static final int HISTORY_ANSWER_MAX = 240;
  private final TutorGraphClient graphClient;
  private final TutorSessionRepository sessionRepository;
  private final ObjectMapper objectMapper;
  private final PassiveProfileService passiveProfileService;
  private final ApplicationEventPublisher eventPublisher;
  private final ClassroomDashboardService classroomDashboardService;
  private final MemoryRetrievalService memoryRetrievalService;
  private final CourseVideoTranscriptService videoTranscriptService;
  private final TutorRequestBudgetService requestBudgetService;
  /** 进行中的答疑流（userId:conversationId），防同一会话窗口并发重复提问。 */
  private final Set<String> inFlightConversations = ConcurrentHashMap.newKeySet();

  public TutorService(
      TutorGraphClient graphClient,
      TutorSessionRepository sessionRepository,
      ObjectMapper objectMapper,
      PassiveProfileService passiveProfileService,
      ApplicationEventPublisher eventPublisher,
      ClassroomDashboardService classroomDashboardService,
      MemoryRetrievalService memoryRetrievalService,
      CourseVideoTranscriptService videoTranscriptService,
      TutorRequestBudgetService requestBudgetService) {
    this.graphClient = graphClient;
    this.sessionRepository = sessionRepository;
    this.objectMapper = objectMapper;
    this.passiveProfileService = passiveProfileService;
    this.eventPublisher = eventPublisher;
    this.classroomDashboardService = classroomDashboardService;
    this.memoryRetrievalService = memoryRetrievalService;
    this.videoTranscriptService = videoTranscriptService;
    this.requestBudgetService = requestBudgetService;
  }

  public SseEmitter ask(CurrentUserPrincipal user, TutorAskRequest request) {
    Long userId = user.userId();
    // 同一会话窗口并发重复提问防护：上一问流式作答未结束时按统一错误约定快速拒绝；
    // 无 conversationId 的独立单问不互斥。释放由中继 onFinished 回调保证恰好一次。
    String conversationKey = inFlightKey(userId, request.conversationId());
    if (conversationKey != null && !inFlightConversations.add(conversationKey)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "SESSION_BUSY", "上一条消息还在处理中，请稍候再试");
    }
    Runnable release =
        conversationKey == null ? () -> {} : () -> inFlightConversations.remove(conversationKey);
    try {
      return askInternal(user, request, release);
    } catch (RuntimeException e) {
      release.run();
      throw e;
    }
  }

  private SseEmitter askInternal(
      CurrentUserPrincipal user, TutorAskRequest request, Runnable release) {
    Long userId = user.userId();
    SseEmitter emitter = new SseEmitter(120_000L);

    String profileBrief = resolveProfileBrief(userId, request.style());
    String classroomBrief = resolveClassroomBrief(user, request.classroomId());
    String memoryBrief = resolveMemoryBrief(userId, request.kcCodes());
    // 多轮记忆：同会话既往问答前置进上下文，让模型在本窗口内有真正的连续记忆
    // （conversationId 本身不下传 graph，仅用其在后端取历史拼进 context）。
    String historyBrief = resolveHistoryBrief(userId, request.conversationId());
    // 看课页回传 videoId + 播放时刻时，读取由真实 MP4 转写的时间窗字幕；缺失/错版明确失败，
    // 不再把视频 id 与时间戳拼成一段文字冒充字幕上下文。
    String videoBrief = resolveVideoBrief(request.videoId(), request.videoTimeSec());
    // 顺序：班级学情（教师）→ 个人近期记忆 → 本会话历史 → 视频上下文 → 原始上下文。
    String effectiveContext =
        mergeContext(
            classroomBrief,
            mergeContext(
                memoryBrief,
                mergeContext(historyBrief, mergeContext(videoBrief, request.context()))));

    requestBudgetService.reserve(userId);
    graphClient.askStream(
        userId,
        request.question(),
        request.kcCodes(),
        effectiveContext,
        request.images(),
        profileBrief,
        emitter,
        resultJson -> {
          try {
            persistSession(userId, request.question(), request.conversationId(), resultJson);
          } catch (Exception e) {
            log.warn("Failed to persist tutor session", e);
          }
        },
        release);

    return emitter;
  }

  private static String inFlightKey(Long userId, String conversationId) {
    if (conversationId == null || conversationId.isBlank()) {
      return null;
    }
    return userId + ":" + conversationId.trim();
  }

  /**
   * 教师 copilot 班级学情注入（数据级）。<b>红线</b>：仅当{@code classroomId}非空且角色为教师/管理员时
   * 才向 {@link ClassroomDashboardService#copilotBrief} 取数——学生角色一律早退，<b>绝不触达班级聚合</b>；
   * 取数自带 {@code requireOwner} 归属校验，非归属或取数异常一律降级为空串（不破坏答疑）。
   */
  String resolveClassroomBrief(CurrentUserPrincipal user, Long classroomId) {
    if (classroomId == null) {
      return "";
    }
    if (user == null || (user.role() != Role.TEACHER && user.role() != Role.ADMIN)) {
      // 学生（及任何非教师角色）：忽略 classroomId，绝不读取班级聚合数据。
      return "";
    }
    try {
      return classroomDashboardService.copilotBrief(user, classroomId);
    } catch (RuntimeException ex) {
      log.warn("Classroom copilot brief degraded for classroom {}: {}", classroomId, ex.getMessage());
      return "";
    }
  }

  /** 班级学情摘要前置到原始上下文（教师 copilot）；摘要为空时原样返回 context。 */
  static String mergeContext(String classroomBrief, String context) {
    String brief = classroomBrief == null ? "" : classroomBrief.trim();
    String base = context == null ? "" : context.trim();
    if (brief.isEmpty()) {
      return context;
    }
    return base.isEmpty() ? brief : brief + "\n\n" + base;
  }

  /** 学生采纳某讲法 → 发 TutorStyleEngaged，回流画像 cognitive_style（因材施教闭环）。 */
  public void recordStyleEngaged(Long userId, String style, String kcCode) {
    eventPublisher.publishEvent(new LearningEvent.TutorStyleEngaged(userId, style, kcCode));
  }

  /**
   * 取学生近期记忆要点组装 memory_brief 注入答疑上下文（Task 2.1）；以首个 KC 为聚焦点，
   * 任何失败降级为空串（不破坏答疑）。
   */
  private String resolveMemoryBrief(Long userId, List<String> kcCodes) {
    String focusKc = (kcCodes == null || kcCodes.isEmpty()) ? null : kcCodes.get(0);
    try {
      return buildMemoryBrief(
          memoryRetrievalService.retrieve(userId, focusKc, MEMORY_HIGHLIGHT_LIMIT));
    } catch (RuntimeException ex) {
      log.warn("resolveMemoryBrief degraded for user {}: {}", userId, ex.getMessage());
      return "";
    }
  }

  /** 由记忆条目组装 "近期学情: A；B；C"；空则空串。 */
  static String buildMemoryBrief(List<MemoryItemDto> memories) {
    if (memories == null || memories.isEmpty()) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    for (MemoryItemDto memory : memories) {
      String summary = memory.summaryZh();
      if (summary != null && !summary.isBlank()) {
        parts.add(summary.trim());
      }
    }
    return parts.isEmpty() ? "" : "近期学情: " + String.join("；", parts);
  }

  /**
   * 取同会话既往问答组装「对话历史」brief 注入上下文（真·多轮记忆）；conversationId 为空或失败降级为空串。
   */
  private String resolveHistoryBrief(Long userId, String conversationId) {
    if (conversationId == null || conversationId.isBlank()) {
      return "";
    }
    try {
      return buildHistoryBrief(
          sessionRepository.findByConversation(userId, conversationId, HISTORY_TURN_LIMIT));
    } catch (RuntimeException ex) {
      log.warn(
          "resolveHistoryBrief degraded for conversation {}: {}", conversationId, ex.getMessage());
      return "";
    }
  }

  /**
   * 由同会话既往问答组装多轮记忆 brief："对话历史:\n[1] 学生: Q\n    导师: A(截断)\n..."。
   * 答案按 {@link #HISTORY_ANSWER_MAX} 截断控 token；空列表返回空串。
   */
  static String buildHistoryBrief(List<TutorSessionDto> turns) {
    if (turns == null || turns.isEmpty()) {
      return "";
    }
    List<String> lines = new ArrayList<>();
    int idx = 1;
    for (TutorSessionDto turn : turns) {
      String q = turn.question() == null ? "" : turn.question().trim();
      if (q.isEmpty()) {
        continue;
      }
      lines.add("[" + idx + "] 学生: " + q);
      String a = turn.answerMd() == null ? "" : turn.answerMd().trim();
      if (!a.isEmpty()) {
        lines.add("    导师: " + truncate(a, HISTORY_ANSWER_MAX));
      }
      idx += 1;
    }
    return lines.isEmpty() ? "" : "对话历史:\n" + String.join("\n", lines);
  }

  private static String truncate(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max) + "…";
  }

  /** 视频 id 为空时不读取字幕；非空时由 {@link CourseVideoTranscriptService} 按真实时间窗提供上下文。 */
  String resolveVideoBrief(Long videoId, Integer videoTimeSec) {
    if (videoId == null) {
      return "";
    }
    return videoTranscriptService.contextFor(videoId, videoTimeSec);
  }

  /**
   * 取学生 8 维富化画像摘要（{@link PassiveProfileService#profileBrief(Long)}）作答疑 profile_brief；
   * 任何失败降级（仅显式 style 仍生效）。
   *
   * @param styleOverride 学生本次显式选择的讲法（{@code visual}/{@code logical}/{@code trial}），可空；
   *     非空时无视画像置信度门槛，用于「换讲法即时改写当前答案」。
   */
  String resolveProfileBrief(Long userId, String styleOverride) {
    String brief;
    try {
      brief = passiveProfileService.profileBrief(userId);
    } catch (RuntimeException ex) {
      log.warn("resolveProfileBrief degraded for user {}: {}", userId, ex.getMessage());
      // 画像取数失败时，仍尊重学生的显式换讲法选择（无画像 brief，仅靠 override）。
      brief = "";
    }
    return applyStyleOverride(brief, styleOverride);
  }

  /**
   * 学生显式换讲法时改写 brief 的认知风格段：brief 含「认知风格: 」段则替换其值为 override，
   * 否则在最前面插入「认知风格: {override}」；override 空白时原样返回 brief。
   */
  static String applyStyleOverride(String brief, String styleOverride) {
    String base = brief == null ? "" : brief;
    String style = styleOverride == null ? "" : styleOverride.trim();
    if (style.isEmpty()) {
      return base;
    }
    String styleSegment = "认知风格: " + style;
    if (base.isEmpty()) {
      return styleSegment;
    }
    List<String> segments = new ArrayList<>(List.of(base.split("; ")));
    for (int i = 0; i < segments.size(); i++) {
      if (segments.get(i).startsWith("认知风格: ")) {
        segments.set(i, styleSegment);
        return String.join("; ", segments);
      }
    }
    segments.add(0, styleSegment);
    return String.join("; ", segments);
  }

  /**
   * 兼容旧签名：无显式换讲法时，按画像（含置信度门槛）组装 profile_brief。
   *
   * @deprecated R-16 起答疑 brief 改用 {@link PassiveProfileService#profileBrief(Long)} 的 8 维富化
   *     组装（{@link #resolveProfileBrief}），本两维（认知风格+易错）旧版仅保留兼容既有调用方/测试。
   */
  @Deprecated
  static String buildProfileBrief(List<ProfileDimensionDto> dims) {
    return buildProfileBrief(dims, null);
  }

  /**
   * 由画像维度组装答疑 profile_brief。认知风格优先级：<b>显式选择 {@code styleOverride}</b>（学生点
   * 「换讲法」，无视置信度门槛）→ 否则取 cognitive_style 且置信度≥0.3（防幻觉，信号不足不贴标签）。
   * 易错点取 error_pattern 最近至多 3 个 tag，无论是否有 override 都照常附带。
   * 格式："认知风格: &lt;tag&gt;; 易错: &lt;a,b,c&gt;"，无信号则空串。
   *
   * @deprecated R-16 起答疑 brief 改用 {@link PassiveProfileService#profileBrief(Long)} 的 8 维富化
   *     组装（{@link #resolveProfileBrief}），本两维（认知风格+易错）旧版仅保留兼容既有调用方/测试。
   */
  @Deprecated
  static String buildProfileBrief(List<ProfileDimensionDto> dims, String styleOverride) {
    // 显式换讲法：学生的主动选择，绕过画像置信度门槛。
    String style =
        (styleOverride != null && !styleOverride.isBlank()) ? styleOverride.trim() : null;
    List<String> errTags = List.of();
    if (dims != null) {
      for (ProfileDimensionDto d : dims) {
        if (style == null
            && "cognitive_style".equals(d.key())
            && d.confidence() >= 0.3
            && !d.tags().isEmpty()) {
          style = d.tags().get(d.tags().size() - 1);
        } else if ("error_pattern".equals(d.key()) && !d.tags().isEmpty()) {
          List<String> t = d.tags();
          errTags = t.size() > 3 ? t.subList(t.size() - 3, t.size()) : t;
        }
      }
    }
    List<String> parts = new ArrayList<>();
    if (style != null) {
      parts.add("认知风格: " + style);
    }
    if (!errTags.isEmpty()) {
      parts.add("易错: " + String.join(",", errTags));
    }
    return String.join("; ", parts);
  }

  public List<TutorSessionDto> listSessions(Long userId, int limit) {
    return sessionRepository.findByUser(userId, limit);
  }

  /** 取某会话窗口内的全部问答（按时间升序），供前端多轮 thread 展示。按 user 过滤防越权。 */
  public List<TutorSessionDto> listConversation(Long userId, String conversationId) {
    if (conversationId == null || conversationId.isBlank()) {
      return List.of();
    }
    return sessionRepository.findByConversation(userId, conversationId, 50);
  }

  private void persistSession(Long userId, String question, String conversationId, String resultJson) {
    try {
      JsonNode root = objectMapper.readTree(resultJson);
      // AG-UI RUN_FINISHED 形状是 {"result": {...}}；历史 result 事件是裸结果。两种都接
      // （与 TutorWorkflowService 同款解包），否则 answer_md 取不到、落库被当空答案丢弃。
      JsonNode result =
          root.has("result") && root.path("result").isObject() ? root.path("result") : root;
      String answerMd = textField(result, "answer_md");
      String groundingStatus = textField(result, "grounding_status");

      List<Integer> evidencePageIds = new ArrayList<>();
      JsonNode pages = result.path("evidence_page_ids");
      if (pages.isArray()) {
        for (JsonNode node : pages) {
          evidencePageIds.add(node.asInt());
        }
      }

      sessionRepository.insert(
          userId, question, answerMd, evidencePageIds, groundingStatus, conversationId);
    } catch (Exception e) {
      log.warn("Failed to parse tutor result for persistence", e);
    }
  }

  private String textField(JsonNode root, String field) {
    JsonNode node = root.path(field);
    return node.isMissingNode() || node.isNull() ? null : node.asText();
  }
}
