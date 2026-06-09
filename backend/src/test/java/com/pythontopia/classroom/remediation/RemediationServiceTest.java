package com.pythontopia.classroom.remediation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import com.pythontopia.workshop.dto.ContentFormat;
import com.pythontopia.workshop.dto.LearningResourceDto;
import com.pythontopia.workshop.dto.ResourceCreateRequest;
import com.pythontopia.workshop.dto.ResourceCreateResponse;
import com.pythontopia.workshop.dto.ResourceFeedbackStatus;
import com.pythontopia.workshop.dto.ResourceStatusFlag;
import com.pythontopia.workshop.dto.ResourceType;
import com.pythontopia.workshop.service.ResourceWorkshopService;
import java.time.OffsetDateTime;
import java.util.List;
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

  @Mock ClassroomDashboardService dashboard;
  @Mock ResourceWorkshopService workshop;
  @Mock CoursePackRepository coursePackRepository;
  @Mock ClassroomGuard classroomGuard;
  private RemediationService service;

  private final CurrentUserPrincipal teacher =
      new CurrentUserPrincipal(TEACHER_ID, "teacher", Role.TEACHER);

  @BeforeEach
  void setUp() {
    service = new RemediationService(dashboard, workshop, coursePackRepository, classroomGuard);
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
