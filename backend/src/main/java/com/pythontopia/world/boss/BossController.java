package com.pythontopia.world.boss;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import com.pythontopia.world.boss.dto.BossProblemDto;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * R-13 §5 Phase 6：Boss 副本端点。
 *
 * <ul>
 *   <li>{@code POST /api/world/boss/{chapter}/problem}：根据 chapter / stage 拉取 AI / golden 题；
 *   <li>{@code POST /api/world/boss/{chapter}/attempt}：提交一次解答（当前由前端 Pyodide 兜底）。
 * </ul>
 *
 * 鉴权：通过 SecurityConfig 已有的 JwtAuthenticationFilter 强制登录用户，{@link CurrentUserPrincipal}
 * 用于上报 graph 做自适应难度。
 */
@Validated
@RestController
@RequestMapping("/api/world/boss")
public class BossController {

  private final BossService service;

  public BossController(BossService service) {
    this.service = service;
  }

  @PostMapping("/{chapter}/problem")
  public BossProblemDto requestProblem(
      @PathVariable @Min(1) @Max(7) int chapter,
      @Valid @RequestBody BossProblemRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    Long userId = user == null ? null : user.userId();
    return service.fetchProblem(chapter, request, userId);
  }

  @PostMapping("/{chapter}/attempt")
  public BossAttemptResultDto submitAttempt(
      @PathVariable @Min(1) @Max(7) int chapter,
      @Valid @RequestBody BossAttemptRequest request) {
    if (request.chapter() != chapter) {
      throw new IllegalArgumentException("chapter param mismatch with body");
    }
    return service.evaluateAttempt(request);
  }
}
