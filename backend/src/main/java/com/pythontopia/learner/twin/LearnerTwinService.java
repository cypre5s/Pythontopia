package com.pythontopia.learner.twin;

import com.pythontopia.coursepack.domain.CourseChapter;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.mastery.review.ReviewState;
import com.pythontopia.learner.mastery.review.ReviewStateRepository;
import com.pythontopia.learner.mastery.review.ReviewStateService;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.twin.dto.LearnerTwinStateDto;
import com.pythontopia.learner.twin.dto.LearnerTwinStateDto.KcProgress;
import com.pythontopia.learner.twin.dto.LearnerTwinStateDto.NextStep;
import com.pythontopia.world.boss.dto.FrontierPickDto;
import com.pythontopia.world.boss.dto.FrontierSelectResultDto;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only authoritative learner state and the shared deterministic practice frontier. */
@Service
public class LearnerTwinService {

  private static final long COURSE_PACK_ID = 43L;
  private final CoursePackRepository coursePackRepository;
  private final MasteryRepository masteryRepository;
  private final ReviewStateRepository reviewStateRepository;
  private final ReviewStateService reviewStateService;
  private final PassiveProfileService passiveProfileService;
  private final AdaptivePracticePolicy policy;

  public LearnerTwinService(
      CoursePackRepository coursePackRepository,
      MasteryRepository masteryRepository,
      ReviewStateRepository reviewStateRepository,
      ReviewStateService reviewStateService,
      PassiveProfileService passiveProfileService,
      AdaptivePracticePolicy policy) {
    this.coursePackRepository = coursePackRepository;
    this.masteryRepository = masteryRepository;
    this.reviewStateRepository = reviewStateRepository;
    this.reviewStateService = reviewStateService;
    this.passiveProfileService = passiveProfileService;
    this.policy = policy;
  }

  @Transactional(readOnly = true)
  public LearnerTwinStateDto state(Long userId) {
    LearnerSnapshot snapshot = load(userId);
    int assessedCount = (int) snapshot.progress().stream().filter(KcProgress::assessed).count();
    int masteredCount = (int) snapshot.progress().stream()
        .filter(progress -> progress.assessed() && progress.masteryScore() != null
            && progress.masteryScore() >= 0.8)
        .count();
    List<NextStep> nextSteps = policy.select(
            snapshot.candidates(), snapshot.targetKcCodes(), null, List.of(), 2).stream()
        .map(pick -> toNextStep(pick, snapshot.chapterNames()))
        .toList();
    return new LearnerTwinStateDto(
        assessedCount,
        snapshot.progress().size(),
        masteredCount,
        growthStage(assessedCount, masteredCount, snapshot.progress().size()),
        snapshot.progress(),
        nextSteps);
  }

  @Transactional(readOnly = true)
  public FrontierSelectResultDto frontier(
      Long userId, Integer focusChapter, List<String> exclude, int count) {
    return frontier(userId, focusChapter, exclude, count, null);
  }

  @Transactional(readOnly = true)
  public FrontierSelectResultDto frontier(
      Long userId, Integer focusChapter, List<String> exclude, int count, String preferredKcCode) {
    LearnerSnapshot snapshot = load(userId);
    List<FrontierPickDto> picks = policy.select(
            snapshot.candidates(), snapshot.targetKcCodes(), focusChapter, exclude, count, preferredKcCode).stream()
        .map(pick -> new FrontierPickDto(
            pick.kcCode(), pick.chapter(), pick.difficulty(), pick.reason()))
        .toList();
    return new FrontierSelectResultDto(picks);
  }

  @Transactional(readOnly = true)
  public Integer recommendedDifficulty(Long userId, String kcCode) {
    if (userId == null || kcCode == null) return null;
    return load(userId).candidates().stream()
        .filter(candidate -> kcCode.equals(candidate.kcCode()))
        .findFirst()
        .map(AdaptivePracticePolicy::difficulty)
        .orElse(null);
  }

  private LearnerSnapshot load(Long userId) {
    List<CourseKc> kcs = coursePackRepository.findKcs(COURSE_PACK_ID, null);
    List<CourseChapter> chapters = coursePackRepository.findChapters(COURSE_PACK_ID);
    Map<Long, CourseChapter> chapterById = chapters.stream()
        .collect(Collectors.toMap(CourseChapter::id, chapter -> chapter));
    Map<String, String> chapterNames = new HashMap<>();
    Map<Long, Integer> chapterOrders = new HashMap<>();
    for (CourseChapter chapter : chapters) {
      chapterOrders.put(chapter.id(), chapter.orderIndex() == null ? 0 : chapter.orderIndex());
    }
    Map<String, MasterySnapshot> masteryByKc = masteryRepository.findAll(userId).stream()
        .filter(snapshot -> snapshot.kcCode() != null)
        .collect(Collectors.toMap(MasterySnapshot::kcCode, snapshot -> snapshot, (left, right) -> left));
    Map<String, List<String>> evidenceSourcesByKc = masteryRepository.findEvidenceSourcesByKc(userId);
    Set<String> assessedKcs = evidenceSourcesByKc.keySet();
    Map<String, List<Boolean>> outcomesByKc = new HashMap<>();
    for (MasteryRepository.PracticeOutcome outcome : masteryRepository.findRecentPracticeOutcomesByUser(userId)) {
      outcomesByKc.computeIfAbsent(outcome.kcCode(), ignored -> new ArrayList<>()).add(outcome.passed());
    }
    Map<String, OffsetDateTime> lastPracticedByKc = masteryRepository.findLastPracticedAtByUser(userId);
    Map<String, ReviewState> reviewByKc = reviewStateRepository.findAll(userId).stream()
        .collect(Collectors.toMap(ReviewState::kcCode, state -> state, (left, right) -> left));
    Map<String, List<String>> prerequisites = coursePackRepository.findKcPrerequisites(COURSE_PACK_ID);
    Set<String> targetKcs = Set.copyOf(
        passiveProfileService.getPersonalizationSignals(userId).targetKcCodes());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<KcProgress> progress = new ArrayList<>();
    List<AdaptivePracticePolicy.Candidate> candidates = new ArrayList<>();

    for (CourseKc kc : kcs) {
      int chapterOrder = chapterOrders.getOrDefault(kc.chapterId(), 0);
      CourseChapter chapter = chapterById.get(kc.chapterId());
      String chapterName = chapter == null ? "第" + chapterOrder + "章" : chapter.title();
      chapterNames.put(kc.kcCode(), chapterName);
      boolean assessed = assessedKcs.contains(kc.kcCode());
      MasterySnapshot stored = masteryByKc.get(kc.kcCode());
      Double score = assessed && stored != null && stored.masteryScore() != null
          ? stored.masteryScore().doubleValue() : null;
      Double confidence = assessed && stored != null && stored.confidence() != null
          ? stored.confidence().doubleValue() : null;
      List<Boolean> outcomes = outcomesByKc.getOrDefault(kc.kcCode(), List.of());
      OffsetDateTime lastPracticed = lastPracticedByKc.get(kc.kcCode());
      ReviewState review = reviewByKc.get(kc.kcCode());
      boolean reviewDue = false;
      OffsetDateTime dueAt = null;
      if (assessed) {
        var signal = reviewStateService.evaluate(
            kc.kcCode(),
            review == null ? null : review.stability(),
            review == null ? null : review.lastReviewAt(),
            review == null ? null : review.dueAt(),
            score == null ? 0.5 : score,
            lastPracticed,
            review == null ? 0 : review.reps(),
            review == null ? 0 : review.lapses(),
            now);
        reviewDue = signal.due();
        dueAt = signal.dueAt();
      }
      int consecutiveFailures = consecutiveFailures(outcomes);
      progress.add(new KcProgress(
          kc.kcCode(), kc.kcName(), chapterOrder, chapterName, assessed, score, confidence,
          reviewDue, dueAt, lastPracticed, consecutiveFailures,
          assessed ? evidenceSourcesByKc.getOrDefault(kc.kcCode(), List.of()) : List.of()));
      candidates.add(new AdaptivePracticePolicy.Candidate(
          kc.kcCode(), kc.kcName(), chapterOrder, assessed, score, confidence,
          reviewDue, dueAt, lastPracticed, outcomes,
          prerequisites.getOrDefault(kc.kcCode(), List.of())));
    }
    return new LearnerSnapshot(progress, candidates, targetKcs, chapterNames);
  }

  private static int consecutiveFailures(List<Boolean> outcomes) {
    int count = 0;
    for (Boolean passed : outcomes) {
      if (Boolean.TRUE.equals(passed)) break;
      count++;
    }
    return count;
  }

  private static NextStep toNextStep(
      AdaptivePracticePolicy.Pick pick, Map<String, String> chapterNames) {
    return new NextStep(
        pick.kcCode(), pick.kcName(), pick.chapter(), chapterNames.get(pick.kcCode()),
        pick.difficulty(), pick.action(), pick.reason(), pick.remediationRequired());
  }

  private static String growthStage(int assessed, int mastered, int total) {
    if (assessed == 0) return "初识小镇";
    if (mastered >= Math.ceil(total * 0.5)) return "稳步精进";
    return "持续练习";
  }

  private record LearnerSnapshot(
      List<KcProgress> progress,
      List<AdaptivePracticePolicy.Candidate> candidates,
      Set<String> targetKcCodes,
      Map<String, String> chapterNames) {}
}
