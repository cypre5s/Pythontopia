package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  public TutorService(
      TutorGraphClient graphClient,
      TutorSessionRepository sessionRepository,
      ObjectMapper objectMapper,
      PassiveProfileService passiveProfileService,
      ApplicationEventPublisher eventPublisher) {
    this.graphClient = graphClient;
    this.sessionRepository = sessionRepository;
    this.objectMapper = objectMapper;
    this.passiveProfileService = passiveProfileService;
    this.eventPublisher = eventPublisher;
  }

  public SseEmitter ask(Long userId, TutorAskRequest request) {
    SseEmitter emitter = new SseEmitter(120_000L);

    String profileBrief = resolveProfileBrief(userId);

    graphClient.askStream(
        userId,
        request.question(),
        request.kcCodes(),
        request.context(),
        request.images(),
        profileBrief,
        emitter,
        resultJson -> {
          try {
            persistSession(userId, request.question(), resultJson);
          } catch (Exception e) {
            log.warn("Failed to persist tutor session", e);
          }
        });

    return emitter;
  }

  /** 学生采纳某讲法 → 发 TutorStyleEngaged，回流画像 cognitive_style（因材施教闭环）。 */
  public void recordStyleEngaged(Long userId, String style, String kcCode) {
    eventPublisher.publishEvent(new LearningEvent.TutorStyleEngaged(userId, style, kcCode));
  }

  /** 取学生画像（认知风格 + 易错点）组装 profile_brief；任何失败降级为空串（=纯文本答疑）。 */
  private String resolveProfileBrief(Long userId) {
    try {
      return buildProfileBrief(passiveProfileService.getDimensions(userId));
    } catch (RuntimeException ex) {
      log.warn("resolveProfileBrief degraded for user {}: {}", userId, ex.getMessage());
      return "";
    }
  }

  /**
   * 由画像维度组装答疑 profile_brief：仅在 cognitive_style 置信度≥0.3 时给出认知风格（防幻觉，
   * 信号不足不贴标签）；易错点取 error_pattern 最近至多 3 个 tag。
   * 格式："认知风格: &lt;tag&gt;; 易错: &lt;a,b,c&gt;"，无信号则空串。
   */
  static String buildProfileBrief(List<ProfileDimensionDto> dims) {
    if (dims == null) {
      return "";
    }
    String style = null;
    List<String> errTags = List.of();
    for (ProfileDimensionDto d : dims) {
      if ("cognitive_style".equals(d.key()) && d.confidence() >= 0.3 && !d.tags().isEmpty()) {
        style = d.tags().get(d.tags().size() - 1);
      } else if ("error_pattern".equals(d.key()) && !d.tags().isEmpty()) {
        List<String> t = d.tags();
        errTags = t.size() > 3 ? t.subList(t.size() - 3, t.size()) : t;
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

  private void persistSession(Long userId, String question, String resultJson) {
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

      sessionRepository.insert(userId, question, answerMd, evidencePageIds, groundingStatus);
    } catch (Exception e) {
      log.warn("Failed to parse tutor result for persistence", e);
    }
  }

  private String textField(JsonNode root, String field) {
    JsonNode node = root.path(field);
    return node.isMissingNode() || node.isNull() ? null : node.asText();
  }
}
