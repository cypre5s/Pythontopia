package com.pythontopia.world.boss;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.world.boss.dto.ExpeditionRunActionRequest;
import com.pythontopia.world.boss.dto.ExpeditionRunDto;
import com.pythontopia.world.boss.dto.ExpeditionRunStartRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 无尽远征 run 跨会话持久化端点（后端权威，支持跨设备 / 会话续局）。
 *
 * <ul>
 *   <li>{@code GET /api/world/expedition/run}：取当前用户活跃 run（无 → 204）；
 *   <li>{@code POST /api/world/expedition/run}：按服务端学习前沿创建 run 与地图；
 *   <li>{@code PUT /api/world/expedition/run}：保存活跃 run 快照；
 *   <li>{@code DELETE /api/world/expedition/run}：结束 / 弃局，删除活跃 run。
 * </ul>
 *
 * <p>鉴权：JwtAuthenticationFilter 强制登录；run 存档仅学生可用（教师无个人远征进度），非学生 403。
 */
@RestController
@RequestMapping("/api/world/expedition")
public class ExpeditionRunController {

  private final ExpeditionRunService service;
  private final BossService bossService;
  private final StudentWriteThrottle studentWriteThrottle;

  public ExpeditionRunController(
      ExpeditionRunService service,
      BossService bossService,
      StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.bossService = bossService;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @GetMapping("/run")
  public ResponseEntity<ExpeditionRunDto> getActiveRun(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service
        .findActive(user.userId())
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/run")
  @ResponseStatus(HttpStatus.CREATED)
  public ExpeditionRunDto startRun(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody ExpeditionRunStartRequest request) {
    requireStudent(user);
    requireWriteCapacity(user.userId());
    var frontier =
        bossService.selectFrontier(
            user.userId(), request.focusChapter(), request.preferredKcCode());
    return service.start(
        user.userId(), request.focusChapter(), request.isDaily(), frontier.picks());
  }

  @PutMapping("/run")
  public ExpeditionRunDto saveRun(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody ExpeditionRunDto run) {
    requireStudent(user);
    requireWriteCapacity(user.userId());
    return service.save(user.userId(), run);
  }

  @PostMapping("/run/actions")
  public ExpeditionRunDto performRunAction(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody ExpeditionRunActionRequest request) {
    requireStudent(user);
    requireWriteCapacity(user.userId());
    return service.performAction(user.userId(), request);
  }

  @DeleteMapping("/run")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void abandonRun(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    requireWriteCapacity(user.userId());
    service.abandon(user.userId());
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可使用无尽远征存档");
    }
  }

  private void requireWriteCapacity(long userId) {
    if (!studentWriteThrottle.tryAcquire(userId, 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS, "STUDENT_WRITE_RATE_LIMITED", "每个账号每分钟最多写入 100 条记录");
    }
  }
}
