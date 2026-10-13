package com.pythontopia.learner.event;

/**
 * 进程内学习事件的标记接口；当 KC 掌握度可能变化时发布（画像初始化、资源消费、练习成功/失败、调试修复）。
 *
 * <p>掌握度评估器经 {@code LearningEventListener} 订阅这些事件并被动更新学生画像。
 */
public sealed interface LearningEvent
    permits LearningEvent.ProfileBootstrapped,
        LearningEvent.ResourceConsumed,
        LearningEvent.ResourceCheckCompleted,
        LearningEvent.PracticeSucceeded,
        LearningEvent.PracticeFailed,
        LearningEvent.DebugResolved,
        LearningEvent.MediaConsumed,
        LearningEvent.TutorStyleEngaged,
        LearningEvent.ProbeChoiceMade,
        LearningEvent.PracticeRetried,
        LearningEvent.PracticeAbandoned,
        LearningEvent.ComebackSolved,
        LearningEvent.TaughtAiSucceeded,
        LearningEvent.TaughtAiFailed,
        LearningEvent.GuideActionClicked,
        LearningEvent.GuideCheckpointCompleted {

  Long userId();

  record ProfileBootstrapped(Long userId, java.util.List<String> unlockedKcs)
      implements LearningEvent {}

  record ResourceConsumed(
      Long userId, Long resourceId, String kcCode, String resourceKind, java.math.BigDecimal delta)
      implements LearningEvent {
    public ResourceConsumed(Long userId, String kcCode, String resourceKind) {
      this(userId, null, kcCode, resourceKind, null);
    }

    public ResourceConsumed(
        Long userId, String kcCode, String resourceKind, java.math.BigDecimal delta) {
      this(userId, null, kcCode, resourceKind, delta);
    }
  }

  record ResourceCheckCompleted(
      Long userId,
      Long checkId,
      Long resourceId,
      String kcCode,
      String evidenceType,
      boolean passed,
      java.math.BigDecimal delta,
      String remediationType,
      Long submissionId)
      implements LearningEvent {
    public ResourceCheckCompleted(
        Long userId,
        Long checkId,
        Long resourceId,
        String kcCode,
        String evidenceType,
        boolean passed,
        java.math.BigDecimal delta,
        String remediationType) {
      this(userId, checkId, resourceId, kcCode, evidenceType, passed, delta, remediationType, null);
    }

    public ResourceCheckCompleted(
        Long userId,
        Long resourceId,
        String kcCode,
        String evidenceType,
        boolean passed,
        java.math.BigDecimal delta,
        String remediationType) {
      this(userId, null, resourceId, kcCode, evidenceType, passed, delta, remediationType, null);
    }
  }

  record PracticeSucceeded(
      Long userId,
      String kcCode,
      Long problemId,
      String difficulty,
      Long submissionId,
      String idempotencyKey)
      implements LearningEvent {
    public PracticeSucceeded(Long userId, String kcCode, Long problemId, String difficulty) {
      this(userId, kcCode, problemId, difficulty, null, null);
    }

    public PracticeSucceeded(
        Long userId, String kcCode, Long problemId, String difficulty, Long submissionId) {
      this(userId, kcCode, problemId, difficulty, submissionId, null);
    }
  }

  record PracticeFailed(
      Long userId,
      String kcCode,
      Long problemId,
      String failureKind,
      String difficulty,
      Long submissionId,
      String idempotencyKey)
      implements LearningEvent {
    public PracticeFailed(
        Long userId, String kcCode, Long problemId, String failureKind, String difficulty) {
      this(userId, kcCode, problemId, failureKind, difficulty, null, null);
    }

    public PracticeFailed(
        Long userId,
        String kcCode,
        Long problemId,
        String failureKind,
        String difficulty,
        Long submissionId) {
      this(userId, kcCode, problemId, failureKind, difficulty, submissionId, null);
    }
  }

  record DebugResolved(Long userId, String kcCode, Long problemId) implements LearningEvent {}

  /**
   * 学生消费了学习内容（章节视频 / 课件 / AI 动画 / 知识点图解）。{@code mediaType} 形如 {@code video} / {@code courseware} /
   * {@code storyboard} / {@code illustration}；用于把消费回流到画像 {@code learning_preference}
   * 维度（媒介偏好），驱动后续个性化推送。
   */
  record MediaConsumed(
      Long userId, String mediaType, Integer chapter, java.util.List<String> kcCodes)
      implements LearningEvent {}

  /** 学生在答疑/讲解中显式选择某种媒介；该选择只更新学习偏好，不代表固定类型或能力。 */
  record TutorStyleEngaged(Long userId, String style, String kcCode) implements LearningEvent {}

  /** 学生在世界内做出中性选择；服务端目录只将明确选择回流为自述学习媒介偏好。 */
  record ProbeChoiceMade(Long userId, String probeKey, String choiceId) implements LearningEvent {}

  /** 学生在同一题失败后再次提交；仅保留为学习过程事件，不推断情绪或人格。 */
  record PracticeRetried(Long userId, Long problemId, String kcCode) implements LearningEvent {}

  /** 学生在失败后放弃；仅保留为学习过程事件，不推断情绪或人格。 */
  record PracticeAbandoned(Long userId, Long problemId, String kcCode) implements LearningEvent {}

  /** 同一题多次失败后最终通过；仅保留可核对的练习过程。 */
  record ComebackSolved(Long userId, Long problemId, String kcCode, int priorFails)
      implements LearningEvent {}

  record TaughtAiSucceeded(Long userId, String kcCode, Long sessionId) implements LearningEvent {}

  record TaughtAiFailed(Long userId, String kcCode, Long sessionId) implements LearningEvent {}

  record GuideActionClicked(Long userId, Long problemId, String kcCode, String actionKey)
      implements LearningEvent {}

  record GuideCheckpointCompleted(Long userId, Long problemId, String kcCode, String cardKey)
      implements LearningEvent {}
}
