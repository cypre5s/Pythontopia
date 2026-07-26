package com.pythontopia.classroom.dashboard.service;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.dashboard.dto.ErrorHotspotDto;
import com.pythontopia.classroom.dashboard.dto.KcHeatmapEntryDto;
import com.pythontopia.classroom.dashboard.dto.QuestCompletionDto;
import com.pythontopia.classroom.dashboard.dto.RecentActivityDto;
import com.pythontopia.classroom.dashboard.dto.ResourceUsageDto;
import com.pythontopia.classroom.dashboard.repository.ClassroomDashboardRepository;
import com.pythontopia.classroom.security.ClassroomGuard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomDashboardService {

  private static final int DEFAULT_ERROR_DAYS = 7;
  private static final int DEFAULT_RESOURCE_DAYS = 7;
  /** 最近活跃事件流的时间窗口，与误区 / 资源使用统一为 7 天。 */
  private static final int DEFAULT_ACTIVITY_DAYS = 7;
  private static final int DEFAULT_ACTIVITY_LIMIT = 20;
  /** 教师 copilot 摘要里各列表的最大条目数（控制注入答疑的上下文长度）。 */
  private static final int COPILOT_TOP_N = 3;

  private final ClassroomDashboardRepository repository;
  private final ClassroomGuard classroomGuard;

  public ClassroomDashboardService(
      ClassroomDashboardRepository repository, ClassroomGuard classroomGuard) {
    this.repository = repository;
    this.classroomGuard = classroomGuard;
  }

  /**
   * KC 热力图（读 + 刷快照）。快照刷新走<b>一条</b>多值 upsert：原实现对 65 个 KC 逐个
   * {@code INSERT ON CONFLICT}，而班级详情页在 onMounted / 路由切换 / 发布任务后各调一次，
   * 等于每次打开班级就打 65 条往返。
   */
  @Transactional
  public List<KcHeatmapEntryDto> kcHeatmap(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    List<KcHeatmapEntryDto> entries = repository.findKcHeatmap(classroomId);
    repository.upsertSnapshots(classroomId, entries);
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
    return repository.findRecentActivity(classroomId, safe, DEFAULT_ACTIVITY_DAYS);
  }

  /**
   * 教师 copilot 班级学情摘要（只读）：归属教师答疑时注入本班最弱知识点 + 高频误区的<b>人话摘要</b>，
   * 让 {@code /api/tutor/ask} 的回答能贴合本班真实学情。<b>红线</b>：{@link ClassroomGuard#requireOwner}
   * 在读取任何聚合前先做角色 + 归属校验，学生 / 非归属教师一律 {@code FORBIDDEN}；本方法只读不写快照。
   */
  @Transactional(readOnly = true)
  public String copilotBrief(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    List<KcHeatmapEntryDto> heatmap = repository.findKcHeatmap(classroomId);
    List<ErrorHotspotDto> hotspots = repository.findErrorHotspots(classroomId, DEFAULT_ERROR_DAYS);
    return buildCopilotBrief(heatmap, hotspots);
  }

  /**
   * 由热力图 + 误区聚合拼接教师 copilot 摘要文案（纯函数，便于直接单测）。最弱知识点按「挣扎人数降序、
   * 掌握度升序」取前 {@value #COPILOT_TOP_N}（仅计有学生作答的 KC）；高频误区按失败次数取前
   * {@value #COPILOT_TOP_N}。文案显式声明「只读 + 严禁泄题」，绝不输出可直接提交的答案。
   */
  static String buildCopilotBrief(List<KcHeatmapEntryDto> heatmap, List<ErrorHotspotDto> hotspots) {
    List<String> weakest = topWeakestKcs(heatmap);
    List<String> errors = topErrorHotspots(hotspots);
    if (weakest.isEmpty() && errors.isEmpty()) {
      return "【本班只读学情·仅教师可见】暂无足够学情数据可供参考（严禁泄题，不得给出可直接提交的完整答案）。";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("【本班只读学情·仅教师可见（严禁泄题，不得给出可直接提交的完整答案）】");
    if (!weakest.isEmpty()) {
      sb.append("\n最弱知识点：").append(String.join("；", weakest));
    }
    if (!errors.isEmpty()) {
      sb.append("\n高频错误：").append(String.join("；", errors));
    }
    return sb.toString();
  }

  private static List<String> topWeakestKcs(List<KcHeatmapEntryDto> heatmap) {
    List<String> out = new ArrayList<>();
    if (heatmap == null) {
      return out;
    }
    heatmap.stream()
        // studentCount 是班级规模而非「练过这个 KC 的人数」，用它过滤等于永不过滤：
        // 只要班里有 1 个人，65 个 KC 全部通过，于是刚建好的空班也会被断言成
        //「最弱知识点：<某个从没教过的 KC>(掌握度0.00)」。真正的「有数据」判据是三个分桶之和。
        .filter(e -> e.masteredCount() + e.learningCount() + e.strugglingCount() > 0)
        .sorted(
            Comparator.comparingInt(KcHeatmapEntryDto::strugglingCount)
                .reversed()
                .thenComparing(e -> mastery(e.avgMastery())))
        .limit(COPILOT_TOP_N)
        .forEach(
            e ->
                out.add(
                    String.format(
                        "%s(掌握度%.2f，%d人挣扎)",
                        kcLabel(e.kcCode(), e.kcName()),
                        mastery(e.avgMastery()),
                        e.strugglingCount())));
    return out;
  }

  private static List<String> topErrorHotspots(List<ErrorHotspotDto> hotspots) {
    List<String> out = new ArrayList<>();
    if (hotspots == null) {
      return out;
    }
    hotspots.stream()
        .sorted(Comparator.comparingInt(ErrorHotspotDto::failedSubmissionCount).reversed())
        .limit(COPILOT_TOP_N)
        .forEach(
            h ->
                out.add(
                    String.format(
                        "%s(%d次失败，%d人)",
                        kcLabel(h.kcCode(), h.kcName()),
                        h.failedSubmissionCount(),
                        h.affectedStudentCount())));
    return out;
  }

  private static double mastery(BigDecimal value) {
    return value == null ? 0.0 : value.setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private static String kcLabel(String code, String name) {
    if (name != null && !name.isBlank()) {
      return name.trim();
    }
    return code == null || code.isBlank() ? "未知知识点" : code;
  }
}
