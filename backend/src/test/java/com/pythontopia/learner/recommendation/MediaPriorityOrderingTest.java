package com.pythontopia.learner.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * learning_preference.tags → 媒介优先级的取向回归。
 *
 * <p>全仓约定 tags 数组「末尾 = 最近 / 主导信号」：PassiveProfileService 按时间追加，INTERVIEW 自评还会
 * 把学生所选 tag 移到末尾；DialogContextService / NpcGreetingService / MemoryBriefService 一律取末尾。
 * 推荐服务若按正序遍历，学生刚刚显式自评的偏好会被排到优先级最低。
 */
class MediaPriorityOrderingTest {

  @Test
  void latestTagWins() {
    // 早期消费的是视频，最近显式选了图解 → 图解应排第一。
    List<String> priority =
        LearnerRecommendationService.mediaPriorityFromTags(List.of("video", "ppt", "illustration"));

    assertThat(priority).containsExactly("visual", "text", "narrative");
  }

  @Test
  void deduplicatesKeepingTheLatestOccurrenceRank() {
    List<String> priority =
        LearnerRecommendationService.mediaPriorityFromTags(
            List.of("illustration", "video", "diagram"));

    // diagram 与 illustration 同属 visual：以最近一次出现（末尾）的位次为准。
    assertThat(priority).containsExactly("visual", "narrative");
  }

  @Test
  void unmappedTagsAreIgnoredAndEmptyStaysEmpty() {
    assertThat(LearnerRecommendationService.mediaPriorityFromTags(List.of("retry", "off_by_one")))
        .isEmpty();
    assertThat(LearnerRecommendationService.mediaPriorityFromTags(List.of())).isEmpty();
    assertThat(LearnerRecommendationService.mediaPriorityFromTags(null)).isEmpty();
  }
}
