package com.pythontopia.classroom.remediation;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.dashboard.dto.ErrorHotspotDto;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.workshop.config.WorkshopProperties;
import com.pythontopia.workshop.dto.LearningResourceDto;
import com.pythontopia.workshop.dto.ResourceCreateRequest;
import com.pythontopia.workshop.dto.ResourceCreateResponse;
import com.pythontopia.workshop.dto.ResourceType;
import com.pythontopia.workshop.repository.LearningResourceRepository;
import com.pythontopia.workshop.service.ResourceWorkshopService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

  /** 单个薄弱 KC 占用的工坊配额份数（与 {@link #REMEDIATION_TYPES} 同步）。 */
  private static final int RESOURCES_PER_KC = 2;

  private final ClassroomDashboardService dashboard;
  private final ResourceWorkshopService workshop;
  private final CoursePackRepository coursePackRepository;
  private final ClassroomGuard classroomGuard;
  private final WorkshopProperties workshopProperties;
  private final LearningResourceRepository learningResourceRepository;

  public RemediationService(
      ClassroomDashboardService dashboard,
      ResourceWorkshopService workshop,
      CoursePackRepository coursePackRepository,
      ClassroomGuard classroomGuard,
      WorkshopProperties workshopProperties,
      LearningResourceRepository learningResourceRepository) {
    this.dashboard = dashboard;
    this.workshop = workshop;
    this.coursePackRepository = coursePackRepository;
    this.classroomGuard = classroomGuard;
    this.workshopProperties = workshopProperties;
    this.learningResourceRepository = learningResourceRepository;
  }

  /**
   * 对班级薄弱 KC 一键生成补救包。教师归属校验先行；逐个薄弱 KC 生成讲义 + 练习，聚合各资源的证据页。
   * 资源归属于触发的教师账号（teacherId）。
   *
   * <p><b>配额与半成品语义</b>：每个 KC 要占用 {@value #RESOURCES_PER_KC} 份工坊配额，而
   * {@code ResourceWorkshopService.create} 的第一行就是 {@code enforceThrottle}（默认每小时 10 份）。
   * 原实现无裁剪、无 try/catch、无事务包裹：≥6 个薄弱 KC 必然在中途抛 429，已生成并提交的
   * 10 份资源 id 与证据页全部随异常丢失，教师只看到「补救包生成失败」。这里改为
   * <b>先按剩余配额裁剪 KC 数</b>，再在循环内捕获单 KC 失败并 {@code break}，把已完成的 packs
   * 正常返回并置 {@code partial=true}。
   */
  public RemediationResultDto trigger(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    Long teacherId = user.userId();

    List<ErrorHotspotDto> hotspots =
        dashboard.errorHotspots(user, classroomId).stream()
            .filter(spot -> spot.kcCode() != null && !spot.kcCode().isBlank())
            .toList();
    int weakKcCount = hotspots.size();
    int affordableKcs = affordableKcCount(teacherId);
    List<ErrorHotspotDto> targets = hotspots.stream().limit(affordableKcs).toList();
    boolean partial = targets.size() < weakKcCount;

    Map<String, CourseKc> kcIndex =
        coursePackRepository.findKcs(DEFAULT_PACK_ID, null).stream()
            .collect(Collectors.toMap(CourseKc::kcCode, kc -> kc, (a, b) -> a));

    List<RemediationResultDto.Pack> packs = new ArrayList<>();
    for (ErrorHotspotDto spot : targets) {
      try {
        packs.add(buildPack(teacherId, spot, kcIndex));
      } catch (RuntimeException failure) {
        // 单个 KC 失败（限流 429 / 生成网关超时）不应吞掉此前已提交的资源：停在这里，返回半成品。
        log.warn(
            "remediation: classroom {} teacher {} stopped at kc {} after {} packs: {}",
            classroomId,
            teacherId,
            spot.kcCode(),
            packs.size(),
            failure.getClass().getSimpleName());
        partial = true;
        break;
      }
    }

    log.info(
        "remediation: classroom {} teacher {} produced {}/{} packs (partial={})",
        classroomId,
        teacherId,
        packs.size(),
        weakKcCount,
        partial);
    return new RemediationResultDto(classroomId, weakKcCount, partial, packs);
  }

  private RemediationResultDto.Pack buildPack(
      Long teacherId, ErrorHotspotDto spot, Map<String, CourseKc> kcIndex) {
    String kcCode = spot.kcCode();
    String kcName = resolveKcName(spot, kcIndex);
    List<Long> resourceIds = new ArrayList<>();
    Set<Long> evidencePageIds = new LinkedHashSet<>();
    for (ResourceType type : REMEDIATION_TYPES) {
      ResourceCreateResponse created =
          workshop.create(
              teacherId, new ResourceCreateRequest(List.of(kcCode), type, "班级补救：" + kcName));
      resourceIds.add(created.resourceId());
      LearningResourceDto resource = workshop.findOne(teacherId, created.resourceId());
      if (resource.evidencePageIds() != null) {
        evidencePageIds.addAll(resource.evidencePageIds());
      }
    }
    return new RemediationResultDto.Pack(
        kcCode, kcName, resourceIds, new ArrayList<>(evidencePageIds));
  }

  /**
   * 本小时还能生成几个 KC 的补救包 = （每小时配额 − 已用量）/ 每 KC 资源数。
   * 白名单账号不受限，直接放行全部薄弱 KC。
   */
  private int affordableKcCount(Long teacherId) {
    if (workshopProperties.isAllowlisted(teacherId)) {
      return Integer.MAX_VALUE;
    }
    OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
    int used = learningResourceRepository.countCreatedAfter(teacherId, since);
    int remaining = workshopProperties.maxResourcesPerHour() - used;
    return Math.max(0, remaining / RESOURCES_PER_KC);
  }

  private String resolveKcName(ErrorHotspotDto spot, Map<String, CourseKc> kcIndex) {
    if (spot.kcName() != null && !spot.kcName().isBlank()) {
      return spot.kcName();
    }
    CourseKc kc = kcIndex.get(spot.kcCode());
    return kc != null && kc.kcName() != null ? kc.kcName() : spot.kcCode();
  }
}
