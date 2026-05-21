package com.pythontopia.learner.profile;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.service.ProfileService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 阶段 13 - 把学习事件（练习通过/失败/Debug 修复）被动转化为 profile_revision。
 *
 * <p>注意：mastery 评估由 {@link com.pythontopia.learner.event.LearningEventListener} 单独处理；
 * 本监听器只负责写入 profile_revision，便于评估周期统计画像置信度增量。
 */
@Component
public class PassiveProfileRevisionListener {

  private final ProfileService profileService;

  public PassiveProfileRevisionListener(ProfileService profileService) {
    this.profileService = profileService;
  }

  @EventListener
  public void onPracticeSucceeded(LearningEvent.PracticeSucceeded event) {
    profileService.applyPassiveRevision(event.userId(), "PRACTICE_PASS", diffFor(event));
  }

  @EventListener
  public void onPracticeFailed(LearningEvent.PracticeFailed event) {
    profileService.applyPassiveRevision(event.userId(), "PRACTICE_FAIL", diffFor(event));
  }

  @EventListener
  public void onDebugResolved(LearningEvent.DebugResolved event) {
    profileService.applyPassiveRevision(event.userId(), "DEBUG_FIX", diffFor(event));
  }

  private Map<String, Object> diffFor(LearningEvent.PracticeSucceeded event) {
    LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
    diff.put("kcCode", event.kcCode());
    diff.put("problemId", event.problemId());
    diff.put("kind", "practice_pass");
    return diff;
  }

  private Map<String, Object> diffFor(LearningEvent.PracticeFailed event) {
    LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
    diff.put("kcCode", event.kcCode());
    diff.put("problemId", event.problemId());
    diff.put("failureKind", event.failureKind());
    diff.put("kind", "practice_fail");
    return diff;
  }

  private Map<String, Object> diffFor(LearningEvent.DebugResolved event) {
    LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
    diff.put("kcCode", event.kcCode());
    diff.put("problemId", event.problemId());
    diff.put("kind", "debug_resolved");
    return diff;
  }
}
