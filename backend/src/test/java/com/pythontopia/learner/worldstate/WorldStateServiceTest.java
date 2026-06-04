package com.pythontopia.learner.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.worldstate.dto.WorldStateDto;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PracticeSubmissionRepository.PlannerSubmission;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 情绪化小镇②心情派生：连胜/连挫优先，再看 emotion_persistence 慢分；任何失败降级中性。 */
class WorldStateServiceTest {

  private final PassiveProfileService passiveProfileService = mock(PassiveProfileService.class);
  private final PracticeSubmissionRepository submissionRepository =
      mock(PracticeSubmissionRepository.class);
  private final WorldStateService service =
      new WorldStateService(passiveProfileService, submissionRepository);

  @Test
  void moodOf_streakWinsOverSlowScore() {
    assertThat(WorldStateService.moodOf(0.1, 2)).isEqualTo("sunny");
    assertThat(WorldStateService.moodOf(0.9, -2)).isEqualTo("rainy");
    assertThat(WorldStateService.moodOf(0.9, 0)).isEqualTo("sunny");
    assertThat(WorldStateService.moodOf(0.1, 0)).isEqualTo("rainy");
    assertThat(WorldStateService.moodOf(0.5, 0)).isEqualTo("cloudy");
  }

  @Test
  void current_lowEmotionNoStreak_isRainy() {
    when(passiveProfileService.getDimensions(eq(7L)))
        .thenReturn(List.of(dim("emotion_persistence", 0.2)));
    when(submissionRepository.findRecentForPlanner(eq(7L), anyInt())).thenReturn(List.of());

    WorldStateDto dto = service.current(7L);
    assertThat(dto.mood()).isEqualTo("rainy");
    assertThat(dto.emotionScore()).isEqualTo(0.2);
    assertThat(dto.streak()).isZero();
  }

  @Test
  void current_recentWinStreak_isSunnyEvenWhenSlowScoreLow() {
    when(passiveProfileService.getDimensions(eq(7L)))
        .thenReturn(List.of(dim("emotion_persistence", 0.3)));
    // 升序（最新在末尾）：尾部两次通过 → streak +2。
    when(submissionRepository.findRecentForPlanner(eq(7L), anyInt()))
        .thenReturn(
            List.of(
                new PlannerSubmission(1L, "kc", false),
                new PlannerSubmission(2L, "kc", true),
                new PlannerSubmission(3L, "kc", true)));

    WorldStateDto dto = service.current(7L);
    assertThat(dto.streak()).isEqualTo(2);
    assertThat(dto.mood()).isEqualTo("sunny");
  }

  @Test
  void current_degradesToNeutralWhenReadsThrow() {
    when(passiveProfileService.getDimensions(anyLong())).thenThrow(new RuntimeException("db down"));
    when(submissionRepository.findRecentForPlanner(anyLong(), anyInt()))
        .thenThrow(new RuntimeException("db down"));

    WorldStateDto dto = service.current(7L);
    assertThat(dto.emotionScore()).isEqualTo(0.5);
    assertThat(dto.streak()).isZero();
    assertThat(dto.mood()).isEqualTo("cloudy");
  }

  private static ProfileDimensionDto dim(String key, double score) {
    return new ProfileDimensionDto(key, key, score, 0.5, 4, true, List.of());
  }
}
