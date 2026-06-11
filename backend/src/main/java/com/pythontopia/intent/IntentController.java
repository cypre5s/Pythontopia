package com.pythontopia.intent;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.IntentRouteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 百晓工坊「意图路由」入口，镜像 {@code OrchestratorController} 的鉴权范式：仅学生可用。把一句话分类成
 * 该调用的工具（问答 / 6 类资源）并锚定 KC，前端据此分派到既有的导师 / 工坊 SSE 管线。非流式——单次
 * 分类、立即返回 JSON。
 */
@RestController
@RequestMapping("/api/intent")
public class IntentController {

  private final IntentService service;

  public IntentController(IntentService service) {
    this.service = service;
  }

  @PostMapping("/route")
  public IntentRouteResponse route(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody IntentRouteRequest request) {
    requireStudent(user);
    return service.route(user, request);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可使用百晓工坊");
    }
  }
}
