package com.pythontopia.learner.worldstate;

import com.pythontopia.learner.worldstate.dto.WorldStateDto;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PracticeSubmissionRepository.PlannerSubmission;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 提供客观练习连击展示；不从答题行为推断学生情绪或人格。
 *
 * <p>读时计算、纯派生、不入库；任何下游失败均降级为中性（sunny / streak 0 / 0.5），冷启动空画像为晴天。
 */
@Service
public class WorldStateService {

  private static final Logger log = LoggerFactory.getLogger(WorldStateService.class);
  private static final double NEUTRAL_COMPATIBILITY_SCORE = 0.5;
  private static final int RECENT_WINDOW = 10;

  private final PracticeSubmissionRepository submissionRepository;

  public WorldStateService(PracticeSubmissionRepository submissionRepository) {
    this.submissionRepository = submissionRepository;
  }

  public WorldStateDto current(Long userId) {
    int streak = recentStreak(userId);
    return new WorldStateDto("sunny", streak, NEUTRAL_COMPATIBILITY_SCORE);
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
}
