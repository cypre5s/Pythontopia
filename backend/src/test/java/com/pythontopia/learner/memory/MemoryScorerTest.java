package com.pythontopia.learner.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 记忆检索打分纯函数单测：recency×importance×relevance，权重 0.4/0.3/0.3 固化。 */
class MemoryScorerTest {

  private static final Instant NOW = Instant.parse("2026-06-08T00:00:00Z");

  private static MemoryRow row(
      long id, String type, List<String> kcCodes, int importance, Instant createdAt) {
    return new MemoryRow(id, type, kcCodes, importance, createdAt, null);
  }

  @Test
  void importantRelevantOldBeatsTrivialRecentWhenFocused() {
    MemoryRow recentLow = row(1, "resource_consumed", List.of("PY43-KC-001"), 1, NOW);
    MemoryRow oldHigh =
        row(2, "practice_failed", List.of("PY43-KC-016"), 4, NOW.minus(10, ChronoUnit.DAYS));

    double oldHighTotal = MemoryScorer.score(oldHigh, "PY43-KC-016", NOW).total();
    double recentLowTotal = MemoryScorer.score(recentLow, "PY43-KC-016", NOW).total();

    assertThat(oldHighTotal).isGreaterThan(recentLowTotal);
  }

  @Test
  void relevanceIsOneForDirectHit() {
    MemoryRow hit = row(1, "practice_failed", List.of("PY43-KC-016"), 4, NOW);

    assertThat(MemoryScorer.score(hit, "PY43-KC-016", NOW).relevance()).isEqualTo(1.0);
  }

  @Test
  void relevanceIsHalfForSameChapter() {
    MemoryRow sameChapter = row(1, "practice_failed", List.of("PY43-KC-015"), 4, NOW);
    Set<String> chapterKcs = Set.of("PY43-KC-015", "PY43-KC-016", "PY43-KC-017");

    double relevance =
        MemoryScorer.score(sameChapter, "PY43-KC-016", chapterKcs, NOW).relevance();

    assertThat(relevance).isEqualTo(0.5);
  }

  @Test
  void relevanceIsZeroForUnrelated() {
    MemoryRow unrelated = row(1, "practice_failed", List.of("PY43-KC-001"), 4, NOW);
    Set<String> chapterKcs = Set.of("PY43-KC-015", "PY43-KC-016");

    assertThat(MemoryScorer.score(unrelated, "PY43-KC-016", chapterKcs, NOW).relevance())
        .isEqualTo(0.0);
  }

  @Test
  void directHitOutranksSameChapter() {
    MemoryRow hit = row(1, "practice_failed", List.of("PY43-KC-016"), 4, NOW);
    MemoryRow sameChapter = row(2, "practice_failed", List.of("PY43-KC-015"), 4, NOW);
    Set<String> chapterKcs = Set.of("PY43-KC-015", "PY43-KC-016");

    double hitRel = MemoryScorer.score(hit, "PY43-KC-016", chapterKcs, NOW).relevance();
    double sameRel = MemoryScorer.score(sameChapter, "PY43-KC-016", chapterKcs, NOW).relevance();

    assertThat(hitRel).isGreaterThan(sameRel);
  }

  @Test
  void noFocusRanksByRecencyAndImportanceOnly() {
    MemoryRow newer = row(1, "resource_consumed", List.of("PY43-KC-001"), 1, NOW);
    MemoryRow older =
        row(2, "resource_consumed", List.of("PY43-KC-002"), 1, NOW.minus(5, ChronoUnit.DAYS));

    MemoryScorer.ScoreBreakdown newerScore = MemoryScorer.score(newer, null, NOW);
    MemoryScorer.ScoreBreakdown olderScore = MemoryScorer.score(older, null, NOW);

    // 无聚焦时 relevance 恒为 0，仅 recency+importance 决定，越新越高。
    assertThat(newerScore.relevance()).isEqualTo(0.0);
    assertThat(olderScore.relevance()).isEqualTo(0.0);
    assertThat(newerScore.total()).isGreaterThan(olderScore.total());
  }

  @Test
  void recencyDecaysWithHalfLifeFourteenDays() {
    MemoryRow fresh = row(1, "practice_failed", List.of("K"), 3, NOW);
    MemoryRow halfLife =
        row(2, "practice_failed", List.of("K"), 3, NOW.minus(14, ChronoUnit.DAYS));

    assertThat(MemoryScorer.score(fresh, null, NOW).recency()).isEqualTo(1.0);
    assertThat(MemoryScorer.score(halfLife, null, NOW).recency()).isCloseTo(0.5, within(1e-6));
  }

  @Test
  void weightsAreFixedAtPointFourThreeThree() {
    // importance=5 → 1.0；direct hit relevance=1.0；fresh recency=1.0 → total=1.0
    MemoryRow maxRow = row(1, "comeback_solved", List.of("PY43-KC-016"), 5, NOW);
    MemoryScorer.ScoreBreakdown s = MemoryScorer.score(maxRow, "PY43-KC-016", NOW);

    assertThat(s.recency()).isEqualTo(1.0);
    assertThat(s.importance()).isEqualTo(1.0);
    assertThat(s.relevance()).isEqualTo(1.0);
    assertThat(s.total()).isCloseTo(1.0, within(1e-9));

    // 全 0 的边界：importance 最低 1/5=0.2，无聚焦 relevance 0，极旧 recency≈0
    MemoryRow lowRow =
        row(2, "resource_consumed", List.of(), 1, NOW.minus(3650, ChronoUnit.DAYS));
    MemoryScorer.ScoreBreakdown low = MemoryScorer.score(lowRow, null, NOW);
    assertThat(low.total()).isCloseTo(0.3 * 0.2, within(1e-3));
  }

  private static org.assertj.core.data.Offset<Double> within(double v) {
    return org.assertj.core.data.Offset.offset(v);
  }
}
