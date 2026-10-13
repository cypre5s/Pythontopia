package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.tutor.dto.TutorAskRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Task 2.1 答疑侧：近期记忆要点注入答疑 context（buildMemoryBrief + ask 装配）。 */
class TutorMemoryBriefTest {

  private final TutorGraphClient graphClient = mock(TutorGraphClient.class);
  private final MemoryRetrievalService memoryRetrievalService = mock(MemoryRetrievalService.class);
  private final TutorService service =
      new TutorService(
          graphClient,
          mock(TutorSessionRepository.class),
          new ObjectMapper(),
          mock(com.pythontopia.learner.profile.service.PassiveProfileService.class),
          mock(ApplicationEventPublisher.class),
          mock(ClassroomDashboardService.class),
          memoryRetrievalService,
          mock(com.pythontopia.coursepack.service.CourseVideoTranscriptService.class),
          mock(TutorRequestBudgetService.class));

  private static MemoryItemDto memory(String summary) {
    return new MemoryItemDto(
        1L, "practice_failed", summary, List.of("PY43-KC-001"), "2026-06-08T00:00:00Z", 4, 1.0,
        0.8, 1.0, 0.9);
  }

  @Test
  void buildMemoryBriefJoinsSummaries() {
    String brief =
        TutorService.buildMemoryBrief(
            List.of(memory("练习失败：PY43-KC-001（WA）"), memory("调试修复：PY43-KC-001")));
    assertThat(brief).startsWith("近期学情:");
    assertThat(brief).contains("练习失败：PY43-KC-001（WA）");
    assertThat(brief).contains("调试修复：PY43-KC-001");
  }

  @Test
  void buildMemoryBriefEmptyWhenNoMemories() {
    assertThat(TutorService.buildMemoryBrief(List.of())).isEmpty();
    assertThat(TutorService.buildMemoryBrief(null)).isEmpty();
  }

  @Test
  void askInjectsMemoryBriefIntoContextWithFocusKc() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    when(memoryRetrievalService.retrieve(eq(7L), eq("PY43-KC-001"), anyInt()))
        .thenReturn(List.of(memory("练习失败：PY43-KC-001（WA）")));

    service.ask(
        student,
        new TutorAskRequest(
            "为什么我的循环不对", List.of("PY43-KC-001"), "原始上下文", null, null, null, null, null,
            null));

    ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
    verify(graphClient)
        .askStream(
            eq(7L),
            any(),
            anyList(),
            contextCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            any());
    String context = contextCaptor.getValue();
    assertThat(context).contains("近期学情:");
    assertThat(context).contains("练习失败：PY43-KC-001（WA）");
    assertThat(context).contains("原始上下文");
    // 记忆检索须以首个 KC 为聚焦点。
    verify(memoryRetrievalService).retrieve(eq(7L), eq("PY43-KC-001"), anyInt());
  }

  @Test
  void askWithoutKcUsesNullFocus() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt())).thenReturn(List.of());

    service.ask(
        student,
        new TutorAskRequest("什么是闭包", List.of(), null, null, null, null, null, null, null));

    verify(memoryRetrievalService).retrieve(eq(7L), isNull(), anyInt());
  }
}
