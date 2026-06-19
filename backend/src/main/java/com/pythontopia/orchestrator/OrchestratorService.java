package com.pythontopia.orchestrator;

import com.pythontopia.bugmine.service.BugMineService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 学习总管服务层，镜像 {@code TutorService}：创建带长任务超时的 {@link SseEmitter}，交给
 * {@link OrchestratorGraphClient} 流式转发后立即返回。无 Repository——会话记忆在 Python 服务侧。
 *
 * <p>每轮对话从 {@link BugMineService} 取「记忆矿脉」当下到期的 KC（{@code decaying_kcs}）一并透传，
 * 让总管把看/学/练/测计划优先锚定到待复习知识点；取数失败不阻断对话，降级为空列表。
 *
 * <p>同一 (userId, sessionId) 的上一轮流还在进行时，新 turn 按统一错误约定快速拒绝
 * （409 {@code SESSION_BUSY}），流终结（含失败 / 断开）由中继回调恰好释放一次；
 * 空 sessionId 由服务端生成唯一 id，无会话语义，不互斥。
 */
@Service
public class OrchestratorService {

  private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);
  /** 有界 agentic loop 会串行跑看/学/练/测工具链，生产小机型上 120s 偶发不够。 */
  static final long STREAM_TIMEOUT_MS = 300_000L;
  /** 透传给总管的到期 KC 上限，与百晓工坊遗忘回声候选条数对齐，避免冲淡计划焦点。 */
  private static final int DECAYING_KC_LIMIT = 5;

  private final OrchestratorGraphClient graphClient;
  private final BugMineService bugMineService;
  /** 进行中的会话流（userId:sessionId），防同一会话并发重复提交。 */
  private final Set<String> inFlightSessions = ConcurrentHashMap.newKeySet();

  public OrchestratorService(OrchestratorGraphClient graphClient, BugMineService bugMineService) {
    this.graphClient = graphClient;
    this.bugMineService = bugMineService;
  }

  public SseEmitter turn(Long userId, OrchestratorTurnRequest request) {
    String sessionKey = inFlightKey(userId, request.sessionId());
    if (sessionKey != null && !inFlightSessions.add(sessionKey)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "SESSION_BUSY", "上一条消息还在处理中，请稍候再试");
    }
    Runnable release =
        sessionKey == null ? () -> {} : () -> inFlightSessions.remove(sessionKey);
    try {
      SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
      graphClient.turnStream(userId, request, decayingKcs(userId), emitter, release);
      return emitter;
    } catch (RuntimeException e) {
      release.run();
      throw e;
    }
  }

  /**
   * 混合工具编排（Phase F）SSE：镜像 {@link #turn}，转发到 graph 的 tool-loop 流。
   * 同一 (userId, sessionId) 与 turn 共享在途互斥（一会话一活动流）。
   */
  public SseEmitter toolLoop(Long userId, OrchestratorTurnRequest request) {
    String sessionKey = inFlightKey(userId, request.sessionId());
    if (sessionKey != null && !inFlightSessions.add(sessionKey)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "SESSION_BUSY", "上一条消息还在处理中，请稍候再试");
    }
    Runnable release =
        sessionKey == null ? () -> {} : () -> inFlightSessions.remove(sessionKey);
    try {
      SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
      graphClient.toolLoopStream(userId, request, emitter, release);
      return emitter;
    } catch (RuntimeException e) {
      release.run();
      throw e;
    }
  }

  private static String inFlightKey(Long userId, String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return null;
    }
    return userId + ":" + sessionId.trim();
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
