package com.pythontopia.classroom.remediation;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.dashboard.dto.ErrorHotspotDto;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.workshop.dto.LearningResourceDto;
import com.pythontopia.workshop.dto.ResourceCreateRequest;
import com.pythontopia.workshop.dto.ResourceCreateResponse;
import com.pythontopia.workshop.dto.ResourceType;
import com.pythontopia.workshop.service.ResourceWorkshopService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 班级薄弱点一键补救（P6.1）：教师对本班高频误区 KC 一键触发，逐个薄弱 KC 走既有真协同资源生成链
 * （{@link ResourceWorkshopService}）产出补救包（讲义 + 靶向练习），每件携带 evidence_page_ids 可溯源。
 *
 * <p>红线：先经 {@link ClassroomGuard#requireOwner} 用<b>真实当前教师</b>做角色 + 班级归属校验（修复旧
 * 实现传 null user 在生产环境 401 的缺陷），非归属教师/学生直接拒绝。
 */
@Service
public class RemediationService {

  private static final Logger log = LoggerFactory.getLogger(RemediationService.class);
  private static final Long DEFAULT_PACK_ID = 43L;

  /** 每个薄弱 KC 生成的补救资源类型：讲义 + 靶向练习。 */
  private static final List<ResourceType> REMEDIATION_TYPES =
      List.of(ResourceType.HANDOUT, ResourceType.PRACTICE);

  private final ClassroomDashboardService dashboard;
  private final ResourceWorkshopService workshop;
  private final CoursePackRepository coursePackRepository;
  private final ClassroomGuard classroomGuard;

  public RemediationService(
      ClassroomDashboardService dashboard,
      ResourceWorkshopService workshop,
      CoursePackRepository coursePackRepository,
      ClassroomGuard classroomGuard) {
    this.dashboard = dashboard;
    this.workshop = workshop;
    this.coursePackRepository = coursePackRepository;
    this.classroomGuard = classroomGuard;
  }

  /**
   * 对班级薄弱 KC 一键生成补救包。教师归属校验先行；逐个薄弱 KC 生成讲义 + 练习，聚合各资源的证据页。
   * 资源归属于触发的教师账号（teacherId）。
   */
  public RemediationResultDto trigger(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    Long teacherId = user.userId();

    List<ErrorHotspotDto> hotspots = dashboard.errorHotspots(user, classroomId);
    Map<String, CourseKc> kcIndex =
        coursePackRepository.findKcs(DEFAULT_PACK_ID, null).stream()
            .collect(Collectors.toMap(CourseKc::kcCode, kc -> kc, (a, b) -> a));

    List<RemediationResultDto.Pack> packs = new ArrayList<>();
    for (ErrorHotspotDto spot : hotspots) {
      String kcCode = spot.kcCode();
      if (kcCode == null || kcCode.isBlank()) {
        continue;
      }
      String kcName = resolveKcName(spot, kcIndex);

      List<Long> resourceIds = new ArrayList<>();
      Set<Long> evidencePageIds = new LinkedHashSet<>();
      for (ResourceType type : REMEDIATION_TYPES) {
        ResourceCreateResponse created =
            workshop.create(
                teacherId,
                new ResourceCreateRequest(List.of(kcCode), type, "班级补救：" + kcName));
        resourceIds.add(created.resourceId());
        LearningResourceDto resource = workshop.findOne(teacherId, created.resourceId());
        if (resource.evidencePageIds() != null) {
          evidencePageIds.addAll(resource.evidencePageIds());
        }
      }
      packs.add(
          new RemediationResultDto.Pack(
              kcCode, kcName, resourceIds, new ArrayList<>(evidencePageIds)));
    }

    log.info(
        "remediation: classroom {} teacher {} produced {} packs",
        classroomId,
        teacherId,
        packs.size());
    return new RemediationResultDto(classroomId, packs.size(), packs);
  }

  private String resolveKcName(ErrorHotspotDto spot, Map<String, CourseKc> kcIndex) {
    if (spot.kcName() != null && !spot.kcName().isBlank()) {
      return spot.kcName();
    }
    CourseKc kc = kcIndex.get(spot.kcCode());
    return kc != null && kc.kcName() != null ? kc.kcName() : spot.kcCode();
  }
}
