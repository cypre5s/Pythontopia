package com.pythontopia.orchestrator;

import com.pythontopia.bugmine.service.BugMineService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.service.CoursePackService;
import com.pythontopia.intent.InteractionContextVerifier;
import com.pythontopia.intent.RouteExecutionTokenSigner;
import com.pythontopia.intent.WorkshopLearningContextService;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final RouteExecutionTokenSigner routeTokenSigner;
  private final InteractionContextVerifier interactionVerifier;
  private final CoursePackService coursePackService;
  private final WorkshopLearningContextService learningContextService;
  /** 进行中的会话流（userId:sessionId），防同一会话并发重复提交。 */
  private final Set<String> inFlightSessions = ConcurrentHashMap.newKeySet();

  @Autowired
  public OrchestratorService(
      OrchestratorGraphClient graphClient,
      BugMineService bugMineService,
      RouteExecutionTokenSigner routeTokenSigner,
      InteractionContextVerifier interactionVerifier,
      CoursePackService coursePackService,
      WorkshopLearningContextService learningContextService) {
    this.graphClient = graphClient;
    this.bugMineService = bugMineService;
    this.routeTokenSigner = routeTokenSigner;
    this.interactionVerifier = interactionVerifier;
    this.coursePackService = coursePackService;
    this.learningContextService = learningContextService;
  }

  public OrchestratorService(
      OrchestratorGraphClient graphClient,
      BugMineService bugMineService,
      RouteExecutionTokenSigner routeTokenSigner,
      InteractionContextVerifier interactionVerifier,
      CoursePackService coursePackService) {
    this(
        graphClient,
        bugMineService,
        routeTokenSigner,
        interactionVerifier,
        coursePackService,
        null);
  }

  /** 保留给既有签名/上下文单测的轻量装配；生产使用上面的五参数构造。 */
  public OrchestratorService(
      OrchestratorGraphClient graphClient,
      BugMineService bugMineService,
      RouteExecutionTokenSigner routeTokenSigner,
      InteractionContextVerifier interactionVerifier) {
    this(graphClient, bugMineService, routeTokenSigner, interactionVerifier, null, null);
  }

  /** 既有 legacy PLAN 单元测试的轻量装配；生产使用带签名器与共享上下文校验器的构造。 */
  public OrchestratorService(OrchestratorGraphClient graphClient, BugMineService bugMineService) {
    this(graphClient, bugMineService, null, null, null, null);
  }

  public SseEmitter turn(Long userId, OrchestratorTurnRequest request) {
    // 先校验客户端原样回传的 message/context，确保任何篡改都在触发数据库补全前被拒绝；
    // 凭证通过后仍重新按当前服务端数据补全 interaction，Graph 只接收权威上下文。
    requireVerifiedMultiRoute(userId, request);
    OrchestratorTurnRequest safeRequest =
        verifiedLearningContext(
            userId, verifiedCourseContext(verifiedInteraction(userId, request)));
    String sessionKey = inFlightKey(userId, safeRequest.sessionId());
    if (sessionKey != null && !inFlightSessions.add(sessionKey)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "SESSION_BUSY", "上一条消息还在处理中，请稍候再试");
    }
    Runnable release =
        sessionKey == null ? () -> {} : () -> inFlightSessions.remove(sessionKey);
    try {
      SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
      graphClient.turnStream(userId, safeRequest, decayingKcs(userId), emitter, release);
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

  private void requireVerifiedMultiRoute(Long userId, OrchestratorTurnRequest request) {
    if (request.routeDecision() == null
        || request.routeDecision().task() == null
        || !"MULTI_TASK".equals(request.routeDecision().task().family())) {
      return;
    }
    if (routeTokenSigner == null
        || !routeTokenSigner.verify(
            userId,
            request.routeDecision(),
            request.message(),
            request.interaction(),
            request.routeExecutionToken())) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "ROUTE_EXECUTION_TOKEN_INVALID",
          "路由执行凭证无效或已过期，请重新提交");
    }
  }

  private OrchestratorTurnRequest verifiedInteraction(
      Long userId, OrchestratorTurnRequest request) {
    if (interactionVerifier == null) {
      return request;
    }
    return request.withInteraction(
        interactionVerifier.verify(userId, request.message(), request.interaction()));
  }

  private OrchestratorTurnRequest verifiedLearningContext(
      Long userId, OrchestratorTurnRequest request) {
    if (learningContextService == null) {
      return request;
    }
    return request.withLearningContext(learningContextService.build(userId, request.message()));
  }

  /**
   * 忽略客户端自报的课程目录，始终从唯一知识底座 pack 43 重建 Graph 证据边界。
   * 客户端选中的 recent KC 只能是该目录的子集。
   */
  private OrchestratorTurnRequest verifiedCourseContext(OrchestratorTurnRequest request) {
    if (coursePackService == null) {
      return request;
    }
    LinkedHashSet<String> authoritative = new LinkedHashSet<>();
    coursePackService.listKcsByChapter(null).stream()
        .map(kc -> kc.kcCode())
        .filter(code -> code != null && !code.isBlank())
        .map(String::trim)
        .forEach(authoritative::add);
    if (authoritative.isEmpty()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "COURSE_KC_INDEX_UNAVAILABLE",
          "课程知识点目录暂时不可用");
    }
    List<String> verifiedRecent =
        request.recentKcCodes() == null
            ? List.of()
            : request.recentKcCodes().stream()
                .filter(authoritative::contains)
                .distinct()
                .toList();
    return request.withCourseContext(verifiedRecent, List.copyOf(authoritative));
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
