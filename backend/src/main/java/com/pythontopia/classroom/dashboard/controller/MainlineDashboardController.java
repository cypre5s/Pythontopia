package com.pythontopia.classroom.dashboard.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.dashboard.dto.ChapterTestStatsDto;
import com.pythontopia.classroom.dashboard.dto.EndlessActivityDto;
import com.pythontopia.classroom.dashboard.dto.MainlineProgressMatrixDto;
import com.pythontopia.classroom.dashboard.dto.MainlineStarMatrixDto;
import com.pythontopia.classroom.dashboard.service.MainlineDashboardService;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * R-15 主线教师看板的 4 个聚合端点（§9）。沿用既有 {@link TeacherDashboardController} 的基址与鉴权约定：
 * 类级 {@code @PreAuthorize('TEACHER','ADMIN')} 做角色门，{@link MainlineDashboardService} 内再过
 * {@link com.pythontopia.classroom.security.ClassroomGuard} 做班级归属门（双重防线）。
 *
 * <p>聚合 + 5 分钟 TTL 缓存全部下沉到 service；本控制器仅做参数校验与转发。
 */
@Validated
@RestController
@RequestMapping("/api/teacher/classrooms/{classroomId}/dashboard")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class MainlineDashboardController {

  private final MainlineDashboardService service;

  public MainlineDashboardController(MainlineDashboardService service) {
    this.service = service;
  }

  /** 每章 0/1/2/3★ 人数矩阵 + 总星 TopN。 */
  @GetMapping("/mainline-stars")
  public MainlineStarMatrixDto mainlineStars(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long classroomId) {
    return service.mainlineStars(user, classroomId);
  }

  /** 每章参试人数 / 提交次数 / 通过率 / 分数箱线分布。 */
  @GetMapping("/chapter-tests")
  public ChapterTestStatsDto chapterTests(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long classroomId) {
    return service.chapterTests(user, classroomId);
  }

  /** 近 7 日无尽补星轮次 / 补星数 / 活跃人数（按天 + 按章）。 */
  @GetMapping("/endless-activity")
  public EndlessActivityDto endlessActivity(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long classroomId) {
    return service.endlessActivity(user, classroomId);
  }

  /** 成员 × 章节 的 stage 完成度矩阵。 */
  @GetMapping("/mainline-progress")
  public MainlineProgressMatrixDto mainlineProgress(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long classroomId) {
    return service.mainlineProgress(user, classroomId);
  }
}
