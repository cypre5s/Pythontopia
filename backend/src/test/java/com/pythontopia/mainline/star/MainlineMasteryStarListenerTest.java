package com.pythontopia.mainline.star;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pythontopia.learner.event.LearningEvent;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * {@code StarSource.MASTERY} 的唯一触发点：没有它，掌握度达标后 3★ 永远不发（recalc 只被 TEST /
 * STAGE_COMPLETE 调用）。
 */
class MainlineMasteryStarListenerTest {

  private static final long USER = 7L;

  private final ChapterStarService starService = mock(ChapterStarService.class);
  private final MainlineMasteryStarListener listener = new MainlineMasteryStarListener(starService);

  @Test
  void practiceSuccessTriggersMasteryRecalcOfOwningChapter() {
    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(USER, "PY43-KC-054", 11L, "MEDIUM"));

    verify(starService).recalc(USER, 7, StarSource.MASTERY); // KC-054 属第 7 章
  }

  @Test
  void resourceCheckTriggersMasteryRecalcOfOwningChapter() {
    listener.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            USER, 5L, "PY43-KC-008", "QUIZ", true, new BigDecimal("0.05"), null));

    verify(starService).recalc(USER, 2, StarSource.MASTERY); // KC-008 属第 2 章
  }

  @Test
  void unknownOrMissingKcIsSkipped() {
    listener.onPracticeSucceeded(new LearningEvent.PracticeSucceeded(USER, "NOT-A-KC", 11L, null));
    listener.onPracticeSucceeded(new LearningEvent.PracticeSucceeded(USER, null, 11L, null));
    listener.onPracticeSucceeded(new LearningEvent.PracticeSucceeded(null, "PY43-KC-001", 11L, null));

    verify(starService, never()).recalc(anyLong(), anyInt(), any());
  }

  /** 星级重算失败不得打断发布方（提交判题 / 资源微测）链路。 */
  @Test
  void recalcFailureIsDegradedNotPropagated() {
    doThrow(new IllegalStateException("db down"))
        .when(starService)
        .recalc(USER, 1, StarSource.MASTERY);

    assertThatCode(
            () ->
                listener.onPracticeSucceeded(
                    new LearningEvent.PracticeSucceeded(USER, "PY43-KC-001", 11L, null)))
        .doesNotThrowAnyException();
  }
}
