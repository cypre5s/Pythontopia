package com.pythontopia.intent;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.coursepack.service.CoursePackService;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.IntentRouteResponse;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 意图路由服务层，镜像 {@code OrchestratorService}：薄薄一层，委托 {@link IntentGraphClient}
 * 调 Python graph。无 Repository——不落库。
 *
 * <p>R-16：路由前注入学生 8 维画像摘要 {@code profile_brief}（{@link
 * PassiveProfileService#profileBrief(Long)}），让 IntentRouterAgent 按情绪/基础倾斜 REVIEW vs ASK
 * 等判定。仅 STUDENT 角色注入；任何取数异常降级空串，不破坏路由。
 */
@Service
public class IntentService {

  private static final Logger log = LoggerFactory.getLogger(IntentService.class);

  private final IntentGraphClient graphClient;
  private final PassiveProfileService passiveProfileService;
  private final InteractionContextVerifier interactionVerifier;
  private final RouteExecutionTokenSigner routeTokenSigner;
  private final WorkshopLearningContextService learningContextService;
  private final CoursePackService coursePackService;

  @Autowired
  public IntentService(
      IntentGraphClient graphClient,
      PassiveProfileService passiveProfileService,
      InteractionContextVerifier interactionVerifier,
      RouteExecutionTokenSigner routeTokenSigner,
      WorkshopLearningContextService learningContextService,
      CoursePackService coursePackService) {
    this.graphClient = graphClient;
    this.passiveProfileService = passiveProfileService;
    this.interactionVerifier = interactionVerifier;
    this.routeTokenSigner = routeTokenSigner;
    this.learningContextService = learningContextService;
    this.coursePackService = coursePackService;
  }

  IntentService(
      IntentGraphClient graphClient,
      PassiveProfileService passiveProfileService,
      InteractionContextVerifier interactionVerifier,
      RouteExecutionTokenSigner routeTokenSigner,
      WorkshopLearningContextService learningContextService) {
    this(
        graphClient,
        passiveProfileService,
        interactionVerifier,
        routeTokenSigner,
        learningContextService,
        null);
  }

  IntentService(
      IntentGraphClient graphClient,
      PassiveProfileService passiveProfileService,
      InteractionContextVerifier interactionVerifier,
      RouteExecutionTokenSigner routeTokenSigner) {
    this(graphClient, passiveProfileService, interactionVerifier, routeTokenSigner, null, null);
  }

  /** 保留给既有纯单元测试的轻量装配；生产由上面的 Spring 构造注入共享 verifier 与 signer。 */
  IntentService(
      IntentGraphClient graphClient,
      PassiveProfileService passiveProfileService,
      PracticeSubmissionRepository submissionRepository) {
    this(
        graphClient,
        passiveProfileService,
        new InteractionContextVerifier(submissionRepository),
        null,
        null,
        null);
  }

  public IntentRouteResponse route(CurrentUserPrincipal user, IntentRouteRequest request) {
    Long userId = user != null && user.role() == Role.STUDENT ? user.userId() : null;
    IntentRouteRequest safeRequest =
        copyWithServerContext(
            request,
            interactionVerifier.verify(userId, request.message(), request.interaction()),
            authoritativeCourseKcs(request));
    LearnerRoutingContext learnerContext = resolveLearnerContext(user, request.message());
    String profileBrief =
        learnerContext == null ? resolveProfileBrief(user) : learnerContext.profileBrief();
    IntentRouteResponse response =
        learnerContext == null
            ? graphClient.route(safeRequest, profileBrief)
            : graphClient.route(safeRequest, profileBrief, learnerContext);
    if (learningContextService != null && userId != null) {
      learningContextService.recordRoute(userId, response, learnerContext);
    }
    if (response.routeDecision() == null || routeTokenSigner == null || userId == null) {
      return response;
    }
    // Graph 路由使用 safeRequest 的服务端补全上下文；执行凭证则必须绑定前端
    // 实际能原样回传的 request。否则自由文本诊断在路由时隐式补全最近失败提交后，
    // Orchestrator 会用原始 null interaction 验签失败。
    return response.withRouteExecutionToken(
        routeTokenSigner.issue(
            userId, response.routeDecision(), request.message(), request.interaction()));
  }

  private LearnerRoutingContext resolveLearnerContext(
      CurrentUserPrincipal user, String message) {
    if (learningContextService == null
        || user == null
        || user.role() != Role.STUDENT
        || user.userId() == null) {
      return null;
    }
    return learningContextService.build(user.userId(), message);
  }

  private List<IntentRouteRequest.CourseKc> authoritativeCourseKcs(IntentRouteRequest request) {
    if (coursePackService == null) {
      return request.courseKcs() == null ? List.of() : request.courseKcs();
    }
    return coursePackService.listKcsByChapter(null).stream()
        .map(kc -> new IntentRouteRequest.CourseKc(kc.kcCode(), kc.kcName()))
        .toList();
  }

  private static IntentRouteRequest copyWithServerContext(
      IntentRouteRequest request,
      IntentRouteRequest.InteractionContext interaction,
      List<IntentRouteRequest.CourseKc> courseKcs) {
    return new IntentRouteRequest(
        request.message(),
        request.manualKcCodes(),
        courseKcs,
        request.currentChapter(),
        request.recentKcCodes(),
        request.recentTurns(),
        request.lastIntent(),
        request.lastKcCodes(),
        request.sessionCorrections(),
        request.scope(),
        request.source(),
        request.capabilityNamespace(),
        request.requestedIntent(),
        interaction);
  }

  /** 仅学生注入画像摘要；异常降级空串（画像可用性绝不阻塞意图路由）。 */
  private String resolveProfileBrief(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.STUDENT || user.userId() == null) {
      return "";
    }
    try {
      String brief = passiveProfileService.profileBrief(user.userId());
      return brief == null ? "" : brief;
    } catch (RuntimeException ex) {
      log.warn("intent profile brief degraded for user {}: {}", user.userId(), ex.getMessage());
      return "";
    }
  }
}
