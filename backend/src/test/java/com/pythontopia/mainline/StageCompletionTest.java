package com.pythontopia.mainline;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.mainline.domain.MainlineStage;
import com.pythontopia.mainline.domain.MainlineStageProgress;
import java.util.List;
import org.junit.jupiter.api.Test;

/** stage 三件套过线判定纯逻辑测试。 */
class StageCompletionTest {

  private MainlineStage stage(
      int requiredVideoSec, int requiredPages, int requiredExamples, List<Long> coursewarePages) {
    return new MainlineStage(
        10L, 2, 1, "变量与运算", List.of("PY43-KC-010"), "Python 变量",
        coursewarePages, List.of(), requiredVideoSec, requiredPages, requiredExamples);
  }

  private MainlineStageProgress progress(int videoSec, List<Long> pages, List<Long> examples) {
    return new MainlineStageProgress(1L, 7L, 10L, videoSec, pages, examples, null, null);
  }

  @Test
  void nullProgressIsNotComplete() {
    MainlineStage s = stage(240, 0, 2, List.of());
    assertThat(StageCompletion.allComplete(s, null)).isFalse();
    assertThat(StageCompletion.videoComplete(s, null)).isFalse();
  }

  @Test
  void emptyCoursewareMakesPagesTriviallyComplete() {
    // 占位期：courseware_page_ids 空 + required_pages=0 ⇒「学」默认过线。
    MainlineStage s = stage(240, 0, 2, List.of());
    assertThat(StageCompletion.pagesComplete(s, progress(0, List.of(), List.of()))).isTrue();
  }

  @Test
  void requiredPagesCountedDistinct() {
    MainlineStage s = stage(240, 2, 2, List.of());
    assertThat(StageCompletion.pagesComplete(s, progress(0, List.of(1L, 1L), List.of()))).isFalse();
    assertThat(StageCompletion.pagesComplete(s, progress(0, List.of(1L, 2L), List.of()))).isTrue();
  }

  @Test
  void coursewareSubsetRequiredWhenListed() {
    MainlineStage s = stage(240, 0, 2, List.of(100L, 101L));
    assertThat(StageCompletion.pagesComplete(s, progress(0, List.of(100L), List.of()))).isFalse();
    assertThat(StageCompletion.pagesComplete(s, progress(0, List.of(100L, 101L), List.of())))
        .isTrue();
  }

  @Test
  void examplesCountedDistinct() {
    MainlineStage s = stage(240, 0, 2, List.of());
    assertThat(StageCompletion.examplesComplete(s, progress(0, List.of(), List.of(5L)))).isFalse();
    assertThat(StageCompletion.examplesComplete(s, progress(0, List.of(), List.of(5L, 5L))))
        .isFalse();
    assertThat(StageCompletion.examplesComplete(s, progress(0, List.of(), List.of(5L, 6L)))).isTrue();
  }

  @Test
  void allThreePiecesRequiredForCompletion() {
    MainlineStage s = stage(240, 0, 2, List.of());
    assertThat(StageCompletion.allComplete(s, progress(240, List.of(), List.of(1L, 2L)))).isTrue();
    // 视频差一点 → 未完成。
    assertThat(StageCompletion.allComplete(s, progress(239, List.of(), List.of(1L, 2L)))).isFalse();
    // 例题不足 → 未完成。
    assertThat(StageCompletion.allComplete(s, progress(240, List.of(), List.of(1L)))).isFalse();
  }

  @Test
  void requiredPagesDisplayPrefersExplicitElseCoursewareSize() {
    assertThat(StageCompletion.requiredPagesDisplay(stage(240, 3, 2, List.of(1L, 2L)))).isEqualTo(3);
    assertThat(StageCompletion.requiredPagesDisplay(stage(240, 0, 2, List.of(1L, 2L)))).isEqualTo(2);
    assertThat(StageCompletion.requiredPagesDisplay(stage(240, 0, 2, List.of()))).isZero();
  }
}
