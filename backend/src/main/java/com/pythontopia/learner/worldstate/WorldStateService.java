package com.pythontopia.learner.worldstate;

import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.worldstate.dto.WorldStateDto;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PracticeSubmissionRepository.PlannerSubmission;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 把 {@code emotion_persistence} 画像与最近练习势头派生为「小镇心情天气」。
 *
 * <p>读时计算、纯派生、不入库；任何下游失败均降级为中性（sunny / streak 0 / 0.5），冷启动空画像为晴天。
 */
@Service
public class WorldStateService {

  private static final Logger log = LoggerFactory.getLogger(WorldStateService.class);
  private static final double DEFAULT_EMOTION = 0.5;
  private static final int RECENT_WINDOW = 10;

  private final PassiveProfileService passiveProfileService;
  private final PracticeSubmissionRepository submissionRepository;

  public WorldStateService(
      PassiveProfileService passiveProfileService,
      PracticeSubmissionRepository submissionRepository) {
    this.passiveProfileService = passiveProfileService;
    this.submissionRepository = submissionRepository;
  }

  public WorldStateDto current(Long userId) {
    double emotionScore = emotionScoreOf(userId);
    int streak = recentStreak(userId);
    return new WorldStateDto(moodOf(emotionScore, streak), streak, emotionScore);
  }

  private double emotionScoreOf(Long userId) {
    try {
      for (ProfileDimensionDto d : passiveProfileService.getDimensions(userId)) {
        if ("emotion_persistence".equals(d.key())) {
          return d.score();
        }
      }
    } catch (RuntimeException ex) {
      log.warn("world-state emotion read degraded for user {}: {}", userId, ex.getMessage());
    }
    return DEFAULT_EMOTION;
  }

  /** 最近 {@value #RECENT_WINDOW} 次提交的「连续同结果」势头：尾部连胜为正、连挫为负。 */
  private int recentStreak(Long userId) {
    List<PlannerSubmission> recent;
    try {
      recent = submissionRepository.findRecentForPlanner(userId, RECENT_WINDOW);
    } catch (RuntimeException ex) {
      log.warn("world-state streak read degraded for user {}: {}", userId, ex.getMessage());
      return 0;
    }
    if (recent.isEmpty()) {
      return 0;
    }
    boolean lastPassed = recent.get(recent.size() - 1).passed();
    int count = 0;
    for (int i = recent.size() - 1; i >= 0; i--) {
      if (recent.get(i).passed() == lastPassed) {
        count++;
      } else {
        break;
      }
    }
    return lastPassed ? count : -count;
  }

  /**
   * 心情派生：连胜/连挫优先，其次看 emotion_persistence 得分。
   * 连胜≥2 或得分≥0.6 → 晴；连挫≥2 或得分&lt;0.4 → 雨；其余多云。
   */
  static String moodOf(double emotionScore, int streak) {
    if (streak >= 2) {
      return "sunny";
    }
    if (streak <= -2) {
      return "rainy";
    }
    if (emotionScore >= 0.6) {
      return "sunny";
    }
    if (emotionScore < 0.4) {
      return "rainy";
    }
    return "cloudy";
  }
}
