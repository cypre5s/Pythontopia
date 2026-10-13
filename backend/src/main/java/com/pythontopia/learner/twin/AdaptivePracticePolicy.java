package com.pythontopia.learner.twin;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** One deterministic selection rule shared by the learner Twin and Endless Expedition. */
@Service
public class AdaptivePracticePolicy {

  public record Candidate(
      String kcCode,
      String kcName,
      int chapter,
      boolean assessed,
      Double masteryScore,
      Double confidence,
      boolean reviewDue,
      OffsetDateTime dueAt,
      OffsetDateTime lastPracticedAt,
      List<Boolean> recentOutcomes,
      List<String> prerequisiteCodes) {
    public Candidate {
      recentOutcomes = recentOutcomes == null ? List.of() : List.copyOf(recentOutcomes);
      prerequisiteCodes = prerequisiteCodes == null ? List.of() : List.copyOf(prerequisiteCodes);
    }
  }

  public record Pick(
      String kcCode,
      String kcName,
      int chapter,
      int difficulty,
      String action,
      String reason,
      boolean remediationRequired) {}

  public List<Pick> select(
      Collection<Candidate> candidates,
      Set<String> targetKcCodes,
      Integer focusChapter,
      List<String> exclude,
      int count) {
    return select(candidates, targetKcCodes, focusChapter, exclude, count, null);
  }

  public List<Pick> select(
      Collection<Candidate> candidates,
      Set<String> targetKcCodes,
      Integer focusChapter,
      List<String> exclude,
      int count,
      String preferredKcCode) {
    if (candidates == null || candidates.isEmpty()) return List.of();
    Map<String, Candidate> byCode = new HashMap<>();
    for (Candidate candidate : candidates) byCode.put(candidate.kcCode(), candidate);
    List<Candidate> scope = candidates.stream()
        .filter(candidate -> focusChapter == null || candidate.chapter() == focusChapter)
        .toList();
    if (scope.isEmpty() && focusChapter != null) {
      scope = candidates.stream().filter(candidate -> candidate.chapter() <= focusChapter).toList();
    }

    Set<String> prerequisiteFallback = new LinkedHashSet<>();
    List<Candidate> eligible = scope.stream()
        .filter(candidate -> prerequisitesReady(candidate, byCode))
        .toList();
    if (eligible.isEmpty() && focusChapter != null) {
      List<Candidate> unresolved = scope;
      for (int depth = 0; depth < 7 && !unresolved.isEmpty(); depth++) {
        Set<String> missing = new LinkedHashSet<>();
        for (Candidate candidate : unresolved) {
          for (String prerequisite : candidate.prerequisiteCodes()) {
            Candidate prerequisiteCandidate = byCode.get(prerequisite);
            if (!prerequisiteReady(prerequisiteCandidate)) missing.add(prerequisite);
          }
        }
        List<Candidate> next = missing.stream().map(byCode::get).filter(candidate -> candidate != null).toList();
        if (next.isEmpty()) break;
        List<Candidate> ready = next.stream()
            .filter(candidate -> prerequisitesReady(candidate, byCode))
            .toList();
        if (!ready.isEmpty()) {
          eligible = ready;
          prerequisiteFallback.addAll(ready.stream().map(Candidate::kcCode).toList());
          break;
        }
        unresolved = next;
      }
    }
    if (eligible.isEmpty()) return List.of();

    Set<String> excluded = exclude == null ? Set.of() : Set.copyOf(exclude);
    List<Candidate> notRepeated = eligible.stream()
        .filter(candidate -> !excluded.contains(candidate.kcCode()))
        .toList();
    if (!notRepeated.isEmpty()) eligible = notRepeated;

    Set<String> targets = targetKcCodes == null ? Set.of() : targetKcCodes;
    return eligible.stream()
        .sorted(Comparator
            .comparing((Candidate candidate) -> candidate.kcCode().equals(preferredKcCode))
            .reversed()
            .thenComparing(Comparator
            .comparingInt((Candidate candidate) -> priority(candidate, targets))
            .reversed()
            .thenComparingInt(Candidate::chapter)
            .thenComparing(Candidate::kcCode)))
        .limit(Math.max(1, Math.min(40, count)))
        .map(candidate -> toPick(candidate, prerequisiteFallback.contains(candidate.kcCode())))
        .toList();
  }

  private static boolean prerequisitesReady(Candidate candidate, Map<String, Candidate> byCode) {
    if (candidate == null) return false;
    for (String prerequisite : candidate.prerequisiteCodes()) {
      if (!prerequisiteReady(byCode.get(prerequisite))) return false;
    }
    return true;
  }

  private static boolean prerequisiteReady(Candidate candidate) {
    return candidate != null
        && candidate.assessed()
        && candidate.masteryScore() != null
        && candidate.masteryScore() >= 0.6;
  }

  private static int priority(Candidate candidate, Set<String> targets) {
    int score = 0;
    if (candidate.reviewDue()) score += 1000;
    if (consecutiveFailures(candidate) >= 2) score += 800;
    if (targets.contains(candidate.kcCode()) && !mastered(candidate)) score += 500;
    if (candidate.assessed() && candidate.masteryScore() != null && candidate.masteryScore() < 0.65) {
      score += 300;
    }
    if (!candidate.assessed()) score += 150;
    if (candidate.confidence() != null && candidate.confidence() < 0.5) score += 40;
    if (candidate.masteryScore() != null) score += (int) Math.round((1.0 - candidate.masteryScore()) * 20);
    if (candidate.lastPracticedAt() != null
        && Duration.between(candidate.lastPracticedAt(), OffsetDateTime.now(ZoneOffset.UTC)).toDays() <= 3) {
      score -= 100;
    }
    return score;
  }

  private static boolean mastered(Candidate candidate) {
    return candidate.assessed() && candidate.masteryScore() != null && candidate.masteryScore() >= 0.8;
  }

  private static int consecutiveFailures(Candidate candidate) {
    int count = 0;
    for (Boolean outcome : candidate.recentOutcomes()) {
      if (Boolean.TRUE.equals(outcome)) break;
      count++;
    }
    return count;
  }

  private static Pick toPick(Candidate candidate, boolean prerequisiteFallback) {
    boolean remediation = consecutiveFailures(candidate) >= 2;
    String action = remediation
        ? "REMEDIATION"
        : (candidate.reviewDue() || prerequisiteFallback ? "REVIEW" : "PRACTICE");
    String reason;
    if (prerequisiteFallback) {
      reason = "先修知识尚未有足够证据，建议先复习此知识点";
    } else if (remediation) {
      reason = "最近连续两次练习未通过，先复习再挑战";
    } else if (candidate.reviewDue()) {
      reason = "FSRS-lite 复习已到期";
    } else if (!candidate.assessed()) {
      reason = "尚未评估，先从基础题建立证据";
    } else if (candidate.masteryScore() != null && candidate.masteryScore() < 0.65) {
      reason = "近期练习证据显示仍需巩固";
    } else {
      reason = "按学习目标与间隔复习状态安排";
    }
    return new Pick(
        candidate.kcCode(), candidate.kcName(), candidate.chapter(),
        difficulty(candidate), action, reason, remediation);
  }

  public static int difficulty(Candidate candidate) {
    if (!candidate.assessed() || candidate.masteryScore() == null) return 1;
    int base = clamp((int) Math.round(1.0 + candidate.masteryScore() * 4.0), 1, 5);
    List<Boolean> outcomes = candidate.recentOutcomes();
    if (outcomes.size() < 2) return base;
    if (Boolean.TRUE.equals(outcomes.get(0)) && Boolean.TRUE.equals(outcomes.get(1))) {
      return Math.min(5, base + 1);
    }
    if (Boolean.FALSE.equals(outcomes.get(0)) && Boolean.FALSE.equals(outcomes.get(1))) {
      return Math.max(1, base - 1);
    }
    return base;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
