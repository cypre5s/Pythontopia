package com.pythontopia.world.boss;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.AiBudgeted;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import com.pythontopia.world.boss.dto.BossPrefetchStatusDto;
import com.pythontopia.world.boss.dto.BossProblemDto;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import com.pythontopia.world.boss.dto.FrontierSelectRequest;
import com.pythontopia.world.boss.dto.FrontierSelectResultDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Boss 副本端点。
 *
 * <ul>
 *   <li>{@code POST /api/world/boss/{chapter}/problem}：根据 chapter / stage 拉取审查后的 AI 题；
 *   <li>{@code POST /api/world/boss/{chapter}/attempt}：提交解答并由服务端 graph 正式评测。
 * </ul>
 *
 * <p>鉴权：JwtAuthenticationFilter 强制登录；{@link CurrentUserPrincipal} 用于上报 graph 做自适应难度。
 */
@RestController
@RequestMapping("/api/world/boss")
public class BossController {

  private final BossService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public BossController(BossService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @PostMapping("/{chapter}/problem")
  @AiBudgeted(roles = Role.STUDENT)
  public BossProblemDto requestProblem(
      @PathVariable @Min(1) @Max(7) int chapter,
      @Valid @RequestBody BossProblemRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    return service.fetchProblem(chapter, request, requireStudent(user));
  }

  /**
   * 触发后台预生成「下一题」（同维度 + 当前难度），立即返回 202；真正生成异步进行。
   * 前端在进入答题后 fire-and-forget 调用，玩家做题时后台备好下一题落 READY。
   */
  @PostMapping("/{chapter}/prefetch")
  @AiBudgeted(roles = Role.STUDENT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public BossPrefetchStatusDto prefetchProblem(
      @PathVariable @Min(1) @Max(7) int chapter,
      @Valid @RequestBody BossProblemRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    return service.prefetch(chapter, request, requireStudent(user));
  }

  /** 查询同一完整维度的预加载状态；PENDING 绝不冒充 READY，FAILED 可再次 POST 重试。 */
  @GetMapping("/{chapter}/prefetch")
  public BossPrefetchStatusDto prefetchStatus(
      @PathVariable @Min(1) @Max(7) int chapter,
      @RequestParam(required = false) @Min(1) @Max(4) Integer stage,
      @RequestParam(required = false) String preferredKc,
      @RequestParam @Min(1) @Max(5) Integer difficulty,
      @RequestParam(required = false)
          @Pattern(regexp = "^(pdf_question|fill_blank|debug|short_answer|closed_loop|dual_track|multi_round|teach_back|bug_mine)$")
          String preferredKind,
      @RequestParam(required = false) String arenaLevel,
      @RequestParam(required = false) String expeditionRunId,
      @RequestParam(required = false) @Pattern(regexp = "^[A-Za-z0-9_-]{1,32}$")
          String expeditionNodeId,
      @CurrentUser CurrentUserPrincipal user) {
    return service.prefetchStatus(
        chapter,
        stage,
        preferredKc,
        difficulty,
        preferredKind,
        arenaLevel,
        expeditionRunId,
        expeditionNodeId,
        requireStudent(user));
  }

  /**
   * 无尽远征「下一题选择器」：后端读权威掌握度 + 画像 → graph 排序候选 KC，前端据此生成分支地图。
   * graph 不可达或没有证据支持的候选时显式返回 503，前端不得用本地默认 KC 冒充画像结果。
   */
  @PostMapping("/select-frontier")
  public FrontierSelectResultDto selectFrontier(
      @Valid @RequestBody FrontierSelectRequest request, @CurrentUser CurrentUserPrincipal user) {
    return service.selectFrontier(requireStudent(user), request);
  }

  @PostMapping("/{chapter}/attempt")
  public BossAttemptResultDto submitAttempt(
      @PathVariable @Min(1) @Max(7) int chapter,
      @Valid @RequestBody BossAttemptRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    if (request.chapter() != chapter) {
      throw new IllegalArgumentException("chapter param mismatch with body");
    }
    Long userId = requireStudent(user);
    if (!studentWriteThrottle.tryAcquireSandboxJudge(userId)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "SANDBOX_JUDGE_RATE_LIMITED",
          "代码判题每个账号每分钟最多 6 次，请稍后再试");
    }
    return service.evaluateAttempt(request, userId);
  }

  private Long requireStudent(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可使用 Boss 练习");
    }
    return user.userId();
  }
}
