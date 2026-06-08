package com.pythontopia.orchestrator;

import com.pythontopia.bugmine.service.BugMineService;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 学习总管服务层，镜像 {@code TutorService}：创建带 120s 超时的 {@link SseEmitter}，交给
 * {@link OrchestratorGraphClient} 流式转发后立即返回。无 Repository——会话记忆在 Python 服务侧。
 *
 * <p>每轮对话从 {@link BugMineService} 取「记忆矿脉」当下到期的 KC（{@code decaying_kcs}）一并透传，
 * 让总管把看/学/练/测计划优先锚定到待复习知识点；取数失败不阻断对话，降级为空列表。
 */
@Service
public class OrchestratorService {

  private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);
  /** 透传给总管的到期 KC 上限，与百晓工坊遗忘回声候选条数对齐，避免冲淡计划焦点。 */
  private static final int DECAYING_KC_LIMIT = 5;

  private final OrchestratorGraphClient graphClient;
  private final BugMineService bugMineService;

  public OrchestratorService(OrchestratorGraphClient graphClient, BugMineService bugMineService) {
    this.graphClient = graphClient;
    this.bugMineService = bugMineService;
  }

  public SseEmitter turn(Long userId, OrchestratorTurnRequest request) {
    SseEmitter emitter = new SseEmitter(120_000L);
    graphClient.turnStream(userId, request, decayingKcs(userId), emitter);
    return emitter;
  }

  private List<String> decayingKcs(Long userId) {
    try {
      return bugMineService.decayingKcCodes(userId, DECAYING_KC_LIMIT);
    } catch (RuntimeException e) {
      log.warn("Failed to load decaying KCs for orchestrator, continuing without them", e);
      return List.of();
    }
  }
}
