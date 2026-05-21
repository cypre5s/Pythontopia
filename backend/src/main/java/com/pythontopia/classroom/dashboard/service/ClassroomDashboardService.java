package com.pythontopia.classroom.dashboard.service;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.dashboard.dto.ErrorHotspotDto;
import com.pythontopia.classroom.dashboard.dto.KcHeatmapEntryDto;
import com.pythontopia.classroom.dashboard.dto.QuestCompletionDto;
import com.pythontopia.classroom.dashboard.dto.RecentActivityDto;
import com.pythontopia.classroom.dashboard.dto.ResourceUsageDto;
import com.pythontopia.classroom.dashboard.repository.ClassroomDashboardRepository;
import com.pythontopia.classroom.security.ClassroomGuard;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomDashboardService {

  private static final int DEFAULT_ERROR_DAYS = 7;
  private static final int DEFAULT_RESOURCE_DAYS = 7;
  private static final int DEFAULT_ACTIVITY_LIMIT = 20;

  private final ClassroomDashboardRepository repository;
  private final ClassroomGuard classroomGuard;

  public ClassroomDashboardService(
      ClassroomDashboardRepository repository, ClassroomGuard classroomGuard) {
    this.repository = repository;
    this.classroomGuard = classroomGuard;
  }

  @Transactional
  public List<KcHeatmapEntryDto> kcHeatmap(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    List<KcHeatmapEntryDto> entries = repository.findKcHeatmap(classroomId);
    for (KcHeatmapEntryDto entry : entries) {
      repository.upsertSnapshot(classroomId, entry);
    }
    return entries;
  }

  @Transactional(readOnly = true)
  public List<QuestCompletionDto> questSummary(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    return repository.findQuestSummary(classroomId);
  }

  @Transactional(readOnly = true)
  public List<ErrorHotspotDto> errorHotspots(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    return repository.findErrorHotspots(classroomId, DEFAULT_ERROR_DAYS);
  }

  @Transactional(readOnly = true)
  public ResourceUsageDto resourceUsage(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    return repository.findResourceUsage(classroomId, DEFAULT_RESOURCE_DAYS);
  }

  @Transactional(readOnly = true)
  public List<RecentActivityDto> recentActivity(
      CurrentUserPrincipal user, Long classroomId, Integer limit) {
    classroomGuard.requireOwner(user, classroomId);
    int safe = limit == null ? DEFAULT_ACTIVITY_LIMIT : Math.max(1, Math.min(50, limit));
    return repository.findRecentActivity(classroomId, safe);
  }
}
