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

  /**
   * correct/total 全由客户端自证：{@code 8/1 = 8.0 ≥ 0.8} 这类构造过去能白拿 1★（并顺带解锁下一章）。
   * 服务端必须先做可信性兜底：答对数 &gt; 总题数 / 超单轮题量上限 / 连对数 &gt; 总题数一律不发星。
   */
  @Test
  void endlessImplausibleSelfReportAwardsNothing() {
    assertThat(service.recordEndlessSummary(USER, CH, 8, 1, 0)).isFalse(); // correct > total
    assertThat(service.recordEndlessSummary(USER, CH, 10, 10, 11)).isFalse(); // streak > total
    assertThat(service.recordEndlessSummary(USER, CH, 500, 500, 0)).isFalse(); // 超单轮题量上限

    verify(starService, never()).applyRecoveryStar(anyLong(), anyInt(), any());
    verify(starRepository, never()).countEventsToday(anyLong(), anyInt(), any());
  }

  @Test
  void endlessDailyCapBlocksSecondAward() {
    when(starRepository.countEventsToday(USER, CH, StarSource.ENDLESS_RECOVERY.name())).thenReturn(1);
    assertThat(service.recordEndlessSummary(USER, CH, 10, 10, 9)).isFalse();
    verify(starService, never()).applyRecoveryStar(anyLong(), anyInt(), any());
  }

  /** 补星同样加星（并解锁下一章），因此不能对未解锁的章发放。 */
  @Test
  void recoveryRejectedForLockedChapter() {
    when(starRepository.find(USER, 4)).thenReturn(Optional.empty()); // 第 4 章 0★ → 第 5 章未解锁

    assertThatThrownBy(() -> service.recordEndlessSummary(USER, 5, 10, 10, 10))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("尚未解锁");
    assertThatThrownBy(() -> service.claimErrorbookRecovery(USER, 5))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("尚未解锁");

    verify(starService, never()).applyRecoveryStar(anyLong(), anyInt(), any());
  }
}
