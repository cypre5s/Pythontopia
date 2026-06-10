package com.pythontopia.mainline;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.pythontopia.mainline.domain.ChapterStar;
import com.pythontopia.mainline.star.ChapterStarRepository;
import com.pythontopia.mainline.star.ChapterStarService;
import com.pythontopia.mainline.star.StarSource;
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

  private final ObjectMapper mapper = new ObjectMapper();

  @Mock private BugMineRepository bugMineRepository;
  @Mock private ChapterStarService starService;
  @Mock private ChapterStarRepository starRepository;

  private MainlineRecoveryService service;

  @BeforeEach
  void setUp() {
    service = new MainlineRecoveryService(bugMineRepository, starService, starRepository);
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
    assertThat(service.recordEndlessSummary(USER, CH, 7, 10, 3)).isFalse(); // 答对 < 8
    assertThat(service.recordEndlessSummary(USER, CH, 8, 11, 3)).isFalse(); // 8/11 ≈ 0.73 < 0.8
    assertThat(service.recordEndlessSummary(USER, CH, 10, 0, 0)).isFalse(); // total 非法
    verify(starService, never()).applyRecoveryStar(anyLong(), anyInt(), any());
  }

  @Test
  void endlessQualifyingAwardsStar() {
    when(starRepository.countEventsToday(USER, CH, StarSource.ENDLESS_RECOVERY.name())).thenReturn(0);
    when(starRepository.find(USER, CH)).thenReturn(Optional.of(star(1)));
    when(starService.applyRecoveryStar(USER, CH, StarSource.ENDLESS_RECOVERY)).thenReturn(star(2));
    assertThat(service.recordEndlessSummary(USER, CH, 8, 10, 5)).isTrue(); // 正确率 0.8 边界达标
  }

  @Test
  void endlessDailyCapBlocksSecondAward() {
    when(starRepository.countEventsToday(USER, CH, StarSource.ENDLESS_RECOVERY.name())).thenReturn(1);
    assertThat(service.recordEndlessSummary(USER, CH, 10, 10, 9)).isFalse();
    verify(starService, never()).applyRecoveryStar(anyLong(), anyInt(), any());
  }
}
