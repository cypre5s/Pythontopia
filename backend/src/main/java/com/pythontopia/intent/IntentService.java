package com.pythontopia.intent;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.IntentRouteResponse;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  public IntentService(
      IntentGraphClient graphClient, PassiveProfileService passiveProfileService) {
    this.graphClient = graphClient;
    this.passiveProfileService = passiveProfileService;
  }

  public IntentRouteResponse route(CurrentUserPrincipal user, IntentRouteRequest request) {
    return graphClient.route(request, resolveProfileBrief(user));
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
