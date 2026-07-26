package com.pythontopia.classroom.remediation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.dto.ErrorHotspotDto;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.workshop.config.WorkshopProperties;
import com.pythontopia.workshop.dto.ContentFormat;
import com.pythontopia.workshop.dto.LearningResourceDto;
import com.pythontopia.workshop.dto.ResourceCreateRequest;
import com.pythontopia.workshop.dto.ResourceCreateResponse;
import com.pythontopia.workshop.dto.ResourceFeedbackStatus;
import com.pythontopia.workshop.dto.ResourceStatusFlag;
import com.pythontopia.workshop.dto.ResourceType;
import com.pythontopia.workshop.repository.LearningResourceRepository;
import com.pythontopia.workshop.service.ResourceWorkshopService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * P6.1：班级补救包真实实现。校验教师归属强制（非归属 → 拒绝、不生成），以及对薄弱 KC 真产出补救包，
 * 每个 pack 携带非空 evidencePageIds（不是旧的 log+count 空壳）。生成协作者用 mock，但断言真实接线与
 * 输出契约形状（非 no-op）。
 */
@ExtendWith(MockitoExtension.class)
class RemediationServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final long TEACHER_ID = 10L;
  private static final long CLASSROOM_ID = 3L;

  /** 工坊配额：每小时 10 份资源；每个薄弱 KC 需要 2 份（讲义 + 练习）→ 一次最多 5 个 KC。 */
  private static final int MAX_RESOURCES_PER_HOUR = 10;

  @Mock ClassroomDashboardService dashboard;
  @Mock ResourceWorkshopService workshop;
  @Mock CoursePackRepository coursePackRepository;
  @Mock ClassroomGuard classroomGuard;
  @Mock LearningResourceRepository learningResourceRepository;
  // WorkshopProperties 是 record（final），不能 mock；直接用真实配置对象。
  private final WorkshopProperties workshopProperties =
      new WorkshopProperties(MAX_RESOURCES_PER_HOUR, null, List.of());

  private RemediationService service;

  private final CurrentUserPrincipal teacher =
      new CurrentUserPrincipal(TEACHER_ID, "teacher", Role.TEACHER);

  @BeforeEach
  void setUp() {
    service =
        new RemediationService(
            dashboard,
            workshop,
            coursePackRepository,
            classroomGuard,
            workshopProperties,
            learningResourceRepository);
  }

  @Test
  @DisplayName("非归属教师 → 拒绝且不触发任何资源生成（fail-fast）")
  void rejectsNonOwnerBeforeGenerating() {
    when(classroomGuard.requireOwner(any(), anyLong()))
        .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "班级不属于当前教师"));

    assertThatThrownBy(() -> service.trigger(teacher, CLASSROOM_ID))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> assertThat(((BusinessException) ex).status()).isEqualTo(HttpStatus.FORBIDDEN));

    verifyNoInteractions(dashboard);
    verifyNoInteractions(workshop);
    verifyNoInteractions(learningResourceRepository);
  }

  @Test
  @DisplayName("薄弱 KC → 每个生成讲义+练习补救包，pack 携带非空 evidencePageIds")
  void producesPacksWithEvidenceForWeakKcs() {
    when(dashboard.errorHotspots(eq(teacher), eq(CLASSROOM_ID)))
        .thenReturn(
            List.of(
                new ErrorHotspotDto("PY43-KC-001", "条件判断", 5, 3, List.of()),
                new ErrorHotspotDto("PY43-KC-002", "循环", 4, 2, List.of())));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());
    when(learningResourceRepository.countCreatedAfter(eq(TEACHER_ID), any(OffsetDateTime.class)))
        .thenReturn(0);
    when(workshop.create(eq(TEACHER_ID), any(ResourceCreateRequest.class)))
        .thenReturn(
            new ResourceCreateResponse(1L, 101L, "SUCCESS"),
            new ResourceCreateResponse(2L, 102L, "SUCCESS"),
            new ResourceCreateResponse(3L, 103L, "SUCCESS"),
            new ResourceCreateResponse(4L, 104L, "SUCCESS"));
    when(workshop.findOne(eq(TEACHER_ID), anyLong())).thenReturn(resourceWithEvidence());

    RemediationResultDto result = service.trigger(teacher, CLASSROOM_ID);

    assertThat(result.classroomId()).isEqualTo(CLASSROOM_ID);
    assertThat(result.weakKcCount()).isEqualTo(2);
    assertThat(result.partial()).isFalse();
    assertThat(result.packs()).hasSize(2);
    assertThat(result.packs())
        .allSatisfy(
            pack -> {
              assertThat(pack.kcCode()).startsWith("PY43-KC-");
              assertThat(pack.resourceIds()).hasSize(2); // 讲义 + 练习
              assertThat(pack.evidencePageIds()).isNotEmpty().contains(11L, 12L);
            });
    // 接线断言：归属校验用真实当前教师；errorHotspots 用真实 user（修复旧 null-user 缺陷）。
    verify(classroomGuard).requireOwner(eq(teacher), eq(CLASSROOM_ID));
    verify(dashboard).errorHotspots(eq(teacher), eq(CLASSROOM_ID));
  }

  /**
   * 回归（缺陷 7）：≥6 个薄弱 KC 时，旧实现串行调 12 次 {@code workshop.create}，第 11 次必被
   * {@code enforceThrottle} 抛 429 —— 前 5 个 KC 已提交的 10 份资源 id 与证据页全部随异常丢失。
   * 现在先按剩余配额裁剪：本小时还剩 10 份 → 只做 5 个 KC，返回 partial=true 且 packs 非空。
   */
  @Test
  @DisplayName("薄弱 KC 超出每小时配额 → 按剩余配额裁剪，返回半成品 partial=true 而非整单失败")
  void trimsHotspotsToRemainingHourlyQuota() {
    when(dashboard.errorHotspots(eq(teacher), eq(CLASSROOM_ID))).thenReturn(hotspots(6));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());
    when(learningResourceRepository.countCreatedAfter(eq(TEACHER_ID), any(OffsetDateTime.class)))
        .thenReturn(0);
    AtomicLong seq = new AtomicLong(500L);
    when(workshop.create(eq(TEACHER_ID), any(ResourceCreateRequest.class)))
        .thenAnswer(invocation -> new ResourceCreateResponse(seq.incrementAndGet(), 900L, "SUCCESS"));
    when(workshop.findOne(eq(TEACHER_ID), anyLong())).thenReturn(resourceWithEvidence());

    RemediationResultDto result = service.trigger(teacher, CLASSROOM_ID);

    assertThat(result.weakKcCount()).isEqualTo(6);
    assertThat(result.packs()).hasSize(MAX_RESOURCES_PER_HOUR / 2);
    assertThat(result.partial()).isTrue();
    // 绝不越过配额：恰好 10 次生成调用，第 11 次的 429 根本不会发生。
    verify(workshop, times(MAX_RESOURCES_PER_HOUR))
        .create(eq(TEACHER_ID), any(ResourceCreateRequest.class));
  }

  /** 已用掉 8 份配额 → 只剩 2 份 → 本次只能做 1 个 KC。 */
  @Test
  @DisplayName("配额已被本小时其它生成用掉 → 只做剩余配额允许的 KC 数")
  void respectsAlreadyConsumedQuota() {
    when(dashboard.errorHotspots(eq(teacher), eq(CLASSROOM_ID))).thenReturn(hotspots(4));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());
    when(learningResourceRepository.countCreatedAfter(eq(TEACHER_ID), any(OffsetDateTime.class)))
        .thenReturn(8);
    AtomicLong seq = new AtomicLong(700L);
    when(workshop.create(eq(TEACHER_ID), any(ResourceCreateRequest.class)))
        .thenAnswer(invocation -> new ResourceCreateResponse(seq.incrementAndGet(), 900L, "SUCCESS"));
    when(workshop.findOne(eq(TEACHER_ID), anyLong())).thenReturn(resourceWithEvidence());

    RemediationResultDto result = service.trigger(teacher, CLASSROOM_ID);

    assertThat(result.packs()).hasSize(1);
    assertThat(result.partial()).isTrue();
    verify(workshop, times(2)).create(eq(TEACHER_ID), any(ResourceCreateRequest.class));
  }

  /** 生成中途抛错（限流 / 网关超时）→ 保留已完成的 pack 并 break，不整单丢弃。 */
  @Test
  @DisplayName("单个 KC 生成失败 → 保留已生成的 packs 并 partial=true，不抛异常")
  void keepsFinishedPacksWhenOneKcFails() {
    when(dashboard.errorHotspots(eq(teacher), eq(CLASSROOM_ID))).thenReturn(hotspots(3));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());
    when(learningResourceRepository.countCreatedAfter(eq(TEACHER_ID), any(OffsetDateTime.class)))
        .thenReturn(0);
    when(workshop.create(eq(TEACHER_ID), any(ResourceCreateRequest.class)))
        .thenReturn(new ResourceCreateResponse(1L, 101L, "SUCCESS"))
        .thenReturn(new ResourceCreateResponse(2L, 102L, "SUCCESS"))
        .thenThrow(
            new BusinessException(
                HttpStatus.TOO_MANY_REQUESTS, "RESOURCE_THROTTLED", "每小时最多生成 10 份资源"));
    when(workshop.findOne(eq(TEACHER_ID), anyLong())).thenReturn(resourceWithEvidence());

    RemediationResultDto result = service.trigger(teacher, CLASSROOM_ID);

    assertThat(result.packs()).hasSize(1);
    // ResourceCreateResponse(runId, resourceId, status) → 补救包收集的是 resourceId。
    assertThat(result.packs().get(0).resourceIds()).containsExactly(101L, 102L);
    assertThat(result.partial()).isTrue();
    assertThat(result.weakKcCount()).isEqualTo(3);
    // 失败后立即 break：第 3 次 create 之后不再继续尝试第 3 个 KC。
    verify(workshop, times(3)).create(eq(TEACHER_ID), any(ResourceCreateRequest.class));
  }

  private static List<ErrorHotspotDto> hotspots(int count) {
    List<ErrorHotspotDto> out = new ArrayList<>(count);
    for (int i = 1; i <= count; i++) {
      out.add(
          new ErrorHotspotDto(
              String.format("PY43-KC-%03d", i), "知识点" + i, 20 - i, 3, List.of()));
    }
    return out;
  }

  private static LearningResourceDto resourceWithEvidence() {
    return new LearningResourceDto(
        99L,
        TEACHER_ID,
        43L,
        List.of("PY43-KC-001"),
        11L,
        ResourceType.HANDOUT,
        "补救讲义",
        ContentFormat.MARKDOWN,
        MAPPER.createObjectNode(),
        List.of(11L, 12L),
        "summary",
        ResourceStatusFlag.PASSED,
        ResourceStatusFlag.PASSED,
        ResourceFeedbackStatus.NONE,
        null,
        OffsetDateTime.now());
  }
}
