package com.pythontopia.world.boss;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.boss.dto.ExpeditionLeaderboardDto;
import com.pythontopia.world.boss.dto.ExpeditionScoreSubmitDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 无尽远征成绩上报 + 班级排行榜端点。
 *
 * <ul>
 *   <li>{@code POST /api/world/expedition/score}：一局结束上报成绩，返回最新班级榜；
 *   <li>{@code GET /api/world/expedition/leaderboard}：取当前学生所在班级的最高单局得分榜。
 * </ul>
 *
 * <p>鉴权：仅学生可用（教师无个人远征成绩），非学生 403。
 */
@RestController
@RequestMapping("/api/world/expedition")
public class ExpeditionScoreController {

  private final ExpeditionScoreService service;

  public ExpeditionScoreController(ExpeditionScoreService service) {
    this.service = service;
  }

  @PostMapping("/score")
  public ExpeditionLeaderboardDto submitScore(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody ExpeditionScoreSubmitDto dto) {
    requireStudent(user);
    service.record(user.userId(), dto);
    return service.leaderboard(user.userId());
  }

  @GetMapping("/leaderboard")
  public ExpeditionLeaderboardDto leaderboard(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service.leaderboard(user.userId());
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可使用无尽远征排行榜");
    }
  }
}
