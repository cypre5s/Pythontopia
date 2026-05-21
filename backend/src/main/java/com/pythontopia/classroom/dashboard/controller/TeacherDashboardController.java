package com.pythontopia.classroom.dashboard.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.dashboard.dto.ErrorHotspotDto;
import com.pythontopia.classroom.dashboard.dto.KcHeatmapEntryDto;
import com.pythontopia.classroom.dashboard.dto.QuestCompletionDto;
import com.pythontopia.classroom.dashboard.dto.RecentActivityDto;
import com.pythontopia.classroom.dashboard.dto.ResourceUsageDto;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/teacher/classrooms/{classroomId}/dashboard")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TeacherDashboardController {

  private final ClassroomDashboardService service;

  public TeacherDashboardController(ClassroomDashboardService service) {
    this.service = service;
  }

  @GetMapping("/kc-heatmap")
  public List<KcHeatmapEntryDto> kcHeatmap(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long classroomId) {
    return service.kcHeatmap(user, classroomId);
  }

  @GetMapping("/quest-summary")
  public List<QuestCompletionDto> questSummary(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long classroomId) {
    return service.questSummary(user, classroomId);
  }

  @GetMapping("/error-hotspots")
  public List<ErrorHotspotDto> errorHotspots(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long classroomId) {
    return service.errorHotspots(user, classroomId);
  }

  @GetMapping("/resource-usage")
  public ResourceUsageDto resourceUsage(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long classroomId) {
    return service.resourceUsage(user, classroomId);
  }

  @GetMapping("/recent-activity")
  public List<RecentActivityDto> recentActivity(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable @Positive Long classroomId,
      @RequestParam(required = false) Integer limit) {
    return service.recentActivity(user, classroomId, limit);
  }
}
