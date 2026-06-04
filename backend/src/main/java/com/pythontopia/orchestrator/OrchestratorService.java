package com.pythontopia.orchestrator;

import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 学习总管服务层，镜像 {@code TutorService}：创建带 120s 超时的 {@link SseEmitter}，交给
 * {@link OrchestratorGraphClient} 流式转发后立即返回。无 Repository——会话记忆在 Python 服务侧。
 */
@Service
public class OrchestratorService {

  private final OrchestratorGraphClient graphClient;

  public OrchestratorService(OrchestratorGraphClient graphClient) {
    this.graphClient = graphClient;
  }

  public SseEmitter turn(Long userId, OrchestratorTurnRequest request) {
    SseEmitter emitter = new SseEmitter(120_000L);
    graphClient.turnStream(userId, request, emitter);
    return emitter;
  }
}
