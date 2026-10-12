package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.coursepack.service.CourseVideoTranscriptService;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.tutor.dto.TutorAskRequest;
import com.pythontopia.tutor.dto.TutorSessionDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** 域1② 真·多轮记忆：同会话历史拼进答疑 context（buildHistoryBrief + ask 装配）。 */
class TutorHistoryBriefTest {

  private final TutorGraphClient graphClient = mock(TutorGraphClient.class);
  private final TutorSessionRepository sessionRepository = mock(TutorSessionRepository.class);
  private final MemoryRetrievalService memoryRetrievalService = mock(MemoryRetrievalService.class);
  private final CourseVideoTranscriptService videoTranscriptService =
      mock(CourseVideoTranscriptService.class);
  private final TutorService service =
      new TutorService(
          graphClient,
          sessionRepository,
          new ObjectMapper(),
          mock(com.pythontopia.learner.profile.service.PassiveProfileService.class),
          mock(ApplicationEventPublisher.class),
          mock(ClassroomDashboardService.class),
          memoryRetrievalService,
          videoTranscriptService);

  private static TutorSessionDto turn(String question, String answer) {
    return new TutorSessionDto(
        1L, question, answer, List.of(), "PASSED", "conv-1", Instant.parse("2026-06-09T00:00:00Z"));
  }

  @Test
  void buildHistoryBriefFormatsTurns() {
    String brief =
        TutorService.buildHistoryBrief(
            List.of(turn("什么是变量", "变量是存储数据的名字"), turn("那列表呢", "列表是可变的有序集合")));
    assertThat(brief).startsWith("对话历史:");
    assertThat(brief).contains("[1] 学生: 什么是变量");
    assertThat(brief).contains("导师: 变量是存储数据的名字");
    assertThat(brief).contains("[2] 学生: 那列表呢");
  }

  @Test
  void buildHistoryBriefEmptyWhenNoTurns() {
    assertThat(TutorService.buildHistoryBrief(List.of())).isEmpty();
    assertThat(TutorService.buildHistoryBrief(null)).isEmpty();
  }

  @Test
  void buildHistoryBriefTruncatesLongAnswer() {
    String longAnswer = "答".repeat(400);
    String brief = TutorService.buildHistoryBrief(List.of(turn("长答案问题", longAnswer)));
    assertThat(brief).contains("…");
    // 截断后长度远小于原答案（240 截断 + 前缀）。
    assertThat(brief.length()).isLessThan(longAnswer.length());
  }

  @Test
  void askInjectsRealTranscriptWindowWhenVideoPresent() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    when(videoTranscriptService.contextFor(42L, 125))
        .thenReturn("课程视频转写: #42\n[01:40-02:10] 列表索引从零开始");

    service.ask(
        student,
        new TutorAskRequest(
            "这里为什么减一", List.of(), "原始上下文", null, null, null, null, 42L, 125));

    ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
    verify(graphClient)
        .askStream(
            eq(7L), any(), anyList(), contextCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(contextCaptor.getValue())
        .contains("课程视频转写", "列表索引从零开始", "原始上下文");
    verify(videoTranscriptService).contextFor(42L, 125);
  }

  @Test
  void askWithoutVideoNeverReadsTranscriptCorpus() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);

    service.ask(
        student,
        new TutorAskRequest(
            "什么是列表", List.of(), "原始上下文", null, null, null, null, null, null));

    verifyNoInteractions(videoTranscriptService);
  }

  @Test
  void askInjectsHistoryBriefWhenConversationIdPresent() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    when(sessionRepository.findByConversation(eq(7L), eq("conv-1"), anyInt()))
        .thenReturn(List.of(turn("什么是变量", "变量是存储数据的名字")));

    service.ask(
        student,
        new TutorAskRequest(
            "那元组呢", List.of(), "原始上下文", null, null, "conv-1", null, null, null));

    ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
    verify(graphClient)
        .askStream(
            eq(7L), any(), anyList(), contextCaptor.capture(), any(), any(), any(), any(), any());
    String context = contextCaptor.getValue();
    assertThat(context).contains("对话历史:");
    assertThat(context).contains("什么是变量");
    assertThat(context).contains("原始上下文");
    verify(sessionRepository).findByConversation(eq(7L), eq("conv-1"), anyInt());
  }
}
