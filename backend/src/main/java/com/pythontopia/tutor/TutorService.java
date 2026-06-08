package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.tutor.dto.TutorAskRequest;
import com.pythontopia.tutor.dto.TutorSessionDto;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TutorService {

  private static final Logger log = LoggerFactory.getLogger(TutorService.class);

  private final TutorGraphClient graphClient;
  private final TutorSessionRepository sessionRepository;
  private final ObjectMapper objectMapper;
  private final PassiveProfileService passiveProfileService;
  private final ApplicationEventPublisher eventPublisher;
  private final ClassroomDashboardService classroomDashboardService;

  public TutorService(
      TutorGraphClient graphClient,
      TutorSessionRepository sessionRepository,
      ObjectMapper objectMapper,
      PassiveProfileService passiveProfileService,
      ApplicationEventPublisher eventPublisher,
      ClassroomDashboardService classroomDashboardService) {
    this.graphClient = graphClient;
    this.sessionRepository = sessionRepository;
    this.objectMapper = objectMapper;
    this.passiveProfileService = passiveProfileService;
    this.eventPublisher = eventPublisher;
    this.classroomDashboardService = classroomDashboardService;
  }

  public SseEmitter ask(CurrentUserPrincipal user, TutorAskRequest request) {
    Long userId = user.userId();
    SseEmitter emitter = new SseEmitter(120_000L);

    String profileBrief = resolveProfileBrief(userId, request.style());
    String classroomBrief = resolveClassroomBrief(user, request.classroomId());
    String effectiveContext = mergeContext(classroomBrief, request.context());

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
        });

    return emitter;
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
   * 取学生画像（认知风格 + 易错点）组装 profile_brief；任何失败降级（仅显式 style 仍生效）。
   *
   * @param styleOverride 学生本次显式选择的讲法（{@code visual}/{@code logical}/{@code trial}），可空；
   *     非空时无视画像置信度门槛，用于「换讲法即时改写当前答案」。
   */
  private String resolveProfileBrief(Long userId, String styleOverride) {
    try {
      return buildProfileBrief(passiveProfileService.getDimensions(userId), styleOverride);
    } catch (RuntimeException ex) {
      log.warn("resolveProfileBrief degraded for user {}: {}", userId, ex.getMessage());
      // 画像取数失败时，仍尊重学生的显式换讲法选择（无画像维度，仅靠 override）。
      return buildProfileBrief(null, styleOverride);
    }
  }

  /** 兼容旧签名：无显式换讲法时，按画像（含置信度门槛）组装 profile_brief。 */
  static String buildProfileBrief(List<ProfileDimensionDto> dims) {
    return buildProfileBrief(dims, null);
  }

  /**
   * 由画像维度组装答疑 profile_brief。认知风格优先级：<b>显式选择 {@code styleOverride}</b>（学生点
   * 「换讲法」，无视置信度门槛）→ 否则取 cognitive_style 且置信度≥0.3（防幻觉，信号不足不贴标签）。
   * 易错点取 error_pattern 最近至多 3 个 tag，无论是否有 override 都照常附带。
   * 格式："认知风格: &lt;tag&gt;; 易错: &lt;a,b,c&gt;"，无信号则空串。
   */
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

  private void persistSession(Long userId, String question, String conversationId, String resultJson) {
    try {
      JsonNode root = objectMapper.readTree(resultJson);
      String answerMd = textField(root, "answer_md");
      String groundingStatus = textField(root, "grounding_status");

      List<Integer> evidencePageIds = new ArrayList<>();
      JsonNode pages = root.path("evidence_page_ids");
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
