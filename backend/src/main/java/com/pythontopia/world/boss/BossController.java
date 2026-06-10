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
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Boss 副本端点。
 *
 * <ul>
 *   <li>{@code POST /api/world/boss/{chapter}/problem}：根据 chapter / stage 拉取 AI / golden 题；
 *   <li>{@code POST /api/world/boss/{chapter}/attempt}：提交解答，服务端 graph 评测，失败时退化到前端 Pyodide。
 * </ul>
 *
 * <p>鉴权：JwtAuthenticationFilter 强制登录；{@link CurrentUserPrincipal} 用于上报 graph 做自适应难度。
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

  /**
   * 触发后台预生成「下一题」（同维度 + 当前难度），立即返回 202；真正生成异步进行。
   * 前端在进入答题后 fire-and-forget 调用，玩家做题时后台备好下一题落 READY。
   */
  @PostMapping("/{chapter}/prefetch")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void prefetchProblem(
      @PathVariable @Min(1) @Max(7) int chapter,
      @Valid @RequestBody BossProblemRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    Long userId = user == null ? null : user.userId();
    service.prefetch(chapter, request, userId);
  }

  @PostMapping("/{chapter}/attempt")
  public BossAttemptResultDto submitAttempt(
      @PathVariable @Min(1) @Max(7) int chapter,
      @Valid @RequestBody BossAttemptRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    if (request.chapter() != chapter) {
      throw new IllegalArgumentException("chapter param mismatch with body");
    }
    Long userId = user == null ? null : user.userId();
    return service.evaluateAttempt(request, userId);
  }
}
