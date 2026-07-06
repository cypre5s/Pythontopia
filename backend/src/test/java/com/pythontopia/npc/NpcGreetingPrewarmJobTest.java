package com.pythontopia.npc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.event.LearningEventLogRepository;
import com.pythontopia.npc.dto.NpcGreetingResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class NpcGreetingPrewarmJobTest {

  private final LearningEventLogRepository eventRepository =
      mock(LearningEventLogRepository.class);
  private final NpcGreetingService greetingService = mock(NpcGreetingService.class);

  private NpcGreetingPrewarmJob job(boolean enabled) {
    return new NpcGreetingPrewarmJob(eventRepository, greetingService, enabled, 7, 200);
  }

  @Test
  void prewarmsEachActiveUserAcrossAllCoreNpcs() {
    when(eventRepository.findActiveUserIds(any(), anyInt())).thenReturn(List.of(1L, 2L));
    when(greetingService.prewarm(anyLong(), anyString())).thenReturn(NpcGreetingResponse.none());

    job(true).prewarmDaily();

    // 2 个活跃学生 × 核心 NPC 全量预热。
    verify(greetingService, times(2 * NpcGreetingPrewarmJob.CORE_NPCS.size()))
        .prewarm(anyLong(), anyString());
  }

  @Test
  void disabledSkipsAllWork() {
    job(false).prewarmDaily();
    verifyNoInteractions(eventRepository, greetingService);
  }

  @Test
  void singleFailureDoesNotAbortRemaining() {
    when(eventRepository.findActiveUserIds(any(), anyInt())).thenReturn(List.of(1L));
    // 首个 NPC 预热抛异常，其余仍应继续。
    when(greetingService.prewarm(eq(1L), anyString()))
        .thenThrow(new RuntimeException("boom"))
        .thenReturn(NpcGreetingResponse.none());

    job(true).prewarmDaily(); // 不抛

    verify(greetingService, times(NpcGreetingPrewarmJob.CORE_NPCS.size()))
        .prewarm(eq(1L), anyString());
  }

  @Test
  void listActiveUsersFailureIsSwallowed() {
    when(eventRepository.findActiveUserIds(any(), anyInt()))
        .thenThrow(new RuntimeException("db down"));

    job(true).prewarmDaily(); // 不抛

    verify(greetingService, times(0)).prewarm(anyLong(), anyString());
  }
}
