package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.tutor.dto.TutorAskRequest;
import com.pythontopia.tutor.dto.TutorSessionDto;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TutorService {

  private static final Logger log = LoggerFactory.getLogger(TutorService.class);

  private final TutorGraphClient graphClient;
  private final TutorSessionRepository sessionRepository;
  private final ObjectMapper objectMapper;

  public TutorService(
      TutorGraphClient graphClient,
      TutorSessionRepository sessionRepository,
      ObjectMapper objectMapper) {
    this.graphClient = graphClient;
    this.sessionRepository = sessionRepository;
    this.objectMapper = objectMapper;
  }

  public SseEmitter ask(Long userId, TutorAskRequest request) {
    SseEmitter emitter = new SseEmitter(120_000L);

    graphClient.askStream(
        userId, request.question(), request.kcCodes(), request.context(), emitter, resultJson -> {
      try {
        persistSession(userId, request.question(), resultJson);
      } catch (Exception e) {
        log.warn("Failed to persist tutor session", e);
      }
    });

    return emitter;
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
