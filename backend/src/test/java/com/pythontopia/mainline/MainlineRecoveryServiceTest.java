package com.pythontopia.mainline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.bugmine.repository.BugMineRepository;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.mainline.domain.ChapterStar;
import com.pythontopia.mainline.star.ChapterStarRepository;
import com.pythontopia.mainline.star.ChapterStarService;
import com.pythontopia.mainline.star.StarSource;
import com.pythontopia.world.boss.ExpeditionScoreService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** B1 补星条件校验单测：阈值 / 幂等 / 正确率边界 / 无尽每日封顶。 */
@ExtendWith(MockitoExtension.class)
class MainlineRecoveryServiceTest {

  private static final long USER = 1L;
  private static final int CH = 1;
  private static final String RUN_ID = "123e4567-e89b-12d3-a456-426614174000";

  private final ObjectMapper mapper = new ObjectMapper();

  @Mock private BugMineRepository bugMineRepository;
  @Mock private ChapterStarService starService;
  @Mock private ChapterStarRepository starRepository;
  @Mock private ExpeditionScoreService expeditionScoreService;

  private MainlineRecoveryService service;

  @BeforeEach
  void setUp() {
    service =
        new MainlineRecoveryService(
            bugMineRepository, starService, starRepository, expeditionScoreService);
  }

  private ChapterStar star(int stars) {
    return new ChapterStar(1L, USER, CH, stars, null, mapper.createObjectNode(), null);
  }

  @Test
  void errorbookBelowThresholdAwardsNothing() {
    when(bugMineRepository.countResolvedByKcCodes(eq(USER), anyList())).thenReturn(2);
    assertThat(service.claimErrorbookRecovery(USER, CH)).isFalse();
    verify(starService, never()).applyRecoveryStar(anyLong(), anyInt(), any());
  }

  @Test
  void errorbookAtThresholdAwardsStar() {
    when(bugMineRepository.countResolvedByKcCodes(eq(USER), anyList())).thenReturn(3);
    when(starRepository.find(USER, CH)).thenReturn(Optional.of(star(1)));
    when(starService.applyRecoveryStar(USER, CH, StarSource.ERRORBOOK_RECOVERY))
        .thenReturn(star(2));
    assertThat(service.claimErrorbookRecovery(USER, CH)).isTrue();
  }

  @Test
  void errorbookIdempotentNoNewStar() {
    when(bugMineRepository.countResolvedByKcCodes(eq(USER), anyList())).thenReturn(5);
    when(starRepository.find(USER, CH)).thenReturn(Optional.of(star(2)));
    when(starService.applyRecoveryStar(USER, CH, StarSource.ERRORBOOK_RECOVERY))
        .thenReturn(star(2)); // 已发放过：星数不变。
    assertThat(service.claimErrorbookRecovery(USER, CH)).isFalse();
  }

  @Test
  void endlessNotQualifyingAwardsNothing() {
    when(expeditionScoreService.summarizeVerifiedAttempts(USER, RUN_ID, CH))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptSummary(10, 7));
    assertThat(service.recordEndlessSummary(USER, CH, RUN_ID)).isFalse();
    verify(starService, never()).applyRecoveryStar(anyLong(), anyInt(), any());
  }

  @Test
  void endlessQualifyingAwardsStar() {
    when(expeditionScoreService.summarizeVerifiedAttempts(USER, RUN_ID, CH))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptSummary(10, 8));
    when(starRepository.countEventsToday(USER, CH, StarSource.ENDLESS_RECOVERY.name())).thenReturn(0);
    when(starRepository.find(USER, CH)).thenReturn(Optional.of(star(1)));
    when(starService.applyRecoveryStar(USER, CH, StarSource.ENDLESS_RECOVERY)).thenReturn(star(2));
    assertThat(service.recordEndlessSummary(USER, CH, RUN_ID)).isTrue(); // 正确率 0.8 边界达标
  }

  @Test
  void clientClaimedResultsCannotAwardAnEndlessStarWithoutVerifiedAttempts() {
    when(expeditionScoreService.summarizeVerifiedAttempts(USER, RUN_ID, CH))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptSummary(0, 0));
    assertThat(service.recordEndlessSummary(USER, CH, RUN_ID)).isFalse();

    verify(starService, never()).applyRecoveryStar(anyLong(), anyInt(), any());
  }

  @Test
  void endlessDailyCapBlocksSecondAward() {
    when(expeditionScoreService.summarizeVerifiedAttempts(USER, RUN_ID, CH))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptSummary(10, 9));
    when(starRepository.countEventsToday(USER, CH, StarSource.ENDLESS_RECOVERY.name())).thenReturn(1);
    assertThat(service.recordEndlessSummary(USER, CH, RUN_ID)).isFalse();
    verify(starService, never()).applyRecoveryStar(anyLong(), anyInt(), any());
  }

  /** 补星同样加星（并解锁下一章），因此不能对未解锁的章发放。 */
  @Test
  void recoveryRejectedForLockedChapter() {
    when(starRepository.find(USER, 4)).thenReturn(Optional.empty()); // 第 4 章 0★ → 第 5 章未解锁

    assertThatThrownBy(() -> service.recordEndlessSummary(USER, 5, RUN_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("尚未解锁");
    assertThatThrownBy(() -> service.claimErrorbookRecovery(USER, 5))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("尚未解锁");

    verify(starService, never()).applyRecoveryStar(anyLong(), anyInt(), any());
  }
}
