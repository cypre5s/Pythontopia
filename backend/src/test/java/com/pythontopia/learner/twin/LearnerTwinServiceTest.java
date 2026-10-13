package com.pythontopia.learner.twin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pythontopia.coursepack.domain.CourseChapter;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.mastery.review.ReviewScheduler;
import com.pythontopia.learner.mastery.review.ReviewStateRepository;
import com.pythontopia.learner.mastery.review.ReviewStateService;
import com.pythontopia.learner.profile.dto.LearnerProfileSignals;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LearnerTwinServiceTest {

  private final CoursePackRepository coursePack = mock(CoursePackRepository.class);
  private final MasteryRepository mastery = mock(MasteryRepository.class);
  private final ReviewStateRepository reviewRepository = mock(ReviewStateRepository.class);
  private final PassiveProfileService profiles = mock(PassiveProfileService.class);
  private final LearnerTwinService service = new LearnerTwinService(
      coursePack,
      mastery,
      reviewRepository,
      new ReviewStateService(reviewRepository, new ReviewScheduler()),
      profiles,
      new AdaptivePracticePolicy());

  @Test
  void bootstrapScoresAreShownAsUnassessedAndPrerequisiteIsRecommendedFirst() {
    when(coursePack.findKcs(43L, null)).thenReturn(List.of(
        kc(1L, "PY43-KC-001", "变量", 1L),
        kc(2L, "PY43-KC-002", "表达式", 1L)));
    when(coursePack.findChapters(43L)).thenReturn(List.of(new CourseChapter(1L, 43L, 1, "第一章", "")));
    when(coursePack.findKcPrerequisites(43L)).thenReturn(Map.of("PY43-KC-002", List.of("PY43-KC-001")));
    when(mastery.findAll(7L)).thenReturn(List.of(
        snapshot("PY43-KC-001", "0.500", "0.000"),
        snapshot("PY43-KC-002", "0.550", "0.700")));
    when(mastery.findAssessedKcCodes(7L)).thenReturn(Set.of("PY43-KC-002"));
    when(mastery.findRecentPracticeOutcomesByUser(7L)).thenReturn(List.of());
    when(mastery.findLastPracticedAtByUser(7L)).thenReturn(Map.of());
    when(reviewRepository.findAll(7L)).thenReturn(List.of());
    when(profiles.getPersonalizationSignals(7L)).thenReturn(LearnerProfileSignals.empty());

    var state = service.state(7L);

    assertThat(state.totalKcCount()).isEqualTo(2);
    assertThat(state.assessedKcCount()).isEqualTo(1);
    assertThat(state.kcs().getFirst().assessed()).isFalse();
    assertThat(state.kcs().getFirst().masteryScore()).isNull();
    assertThat(state.nextSteps().getFirst().kcCode()).isEqualTo("PY43-KC-001");
  }

  @Test
  void difficultyForTopicUsesItsVerifiedRecentPracticeOutcomes() {
    String kcCode = "PY43-KC-001";
    when(coursePack.findKcs(43L, null)).thenReturn(List.of(kc(1L, kcCode, "变量", 1L)));
    when(coursePack.findChapters(43L)).thenReturn(List.of(new CourseChapter(1L, 43L, 1, "第一章", "")));
    when(coursePack.findKcPrerequisites(43L)).thenReturn(Map.of());
    when(mastery.findAll(7L)).thenReturn(List.of(snapshot(kcCode, "0.500", "0.700")));
    when(mastery.findAssessedKcCodes(7L)).thenReturn(Set.of(kcCode));
    when(mastery.findRecentPracticeOutcomesByUser(7L)).thenReturn(List.of(
        new MasteryRepository.PracticeOutcome(kcCode, true, null),
        new MasteryRepository.PracticeOutcome(kcCode, true, null)));
    when(mastery.findLastPracticedAtByUser(7L)).thenReturn(Map.of());
    when(reviewRepository.findAll(7L)).thenReturn(List.of());
    when(profiles.getPersonalizationSignals(7L)).thenReturn(LearnerProfileSignals.empty());

    assertThat(service.recommendedDifficulty(7L, kcCode)).isEqualTo(4);
  }

  private static CourseKc kc(Long id, String code, String name, Long chapterId) {
    return new CourseKc(id, 43L, code, name, chapterId, 1, "Easy", "");
  }

  private static MasterySnapshot snapshot(String kcCode, String score, String confidence) {
    return new MasterySnapshot(
        1L, 7L, kcCode, new BigDecimal(score), new BigDecimal(confidence), null, null);
  }
}
