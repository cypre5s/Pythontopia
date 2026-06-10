package com.pythontopia.learner.event;

/**
 * 进程内学习事件的标记接口；当 KC 掌握度可能变化时发布（画像初始化、资源消费、练习成功/失败、调试修复）。
 *
 * <p>掌握度评估器经 {@code LearningEventListener} 订阅这些事件并被动更新学生画像。
 */
public sealed interface LearningEvent
    permits LearningEvent.ProfileBootstrapped,
        LearningEvent.ResourceConsumed,
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
        LearningEvent.TaughtAiFailed {

  Long userId();

  record ProfileBootstrapped(Long userId, java.util.List<String> unlockedKcs) implements LearningEvent {}

  record ResourceConsumed(Long userId, String kcCode, String resourceKind) implements LearningEvent {}

  record PracticeSucceeded(Long userId, String kcCode, Long problemId, String difficulty)
      implements LearningEvent {}

  record PracticeFailed(
      Long userId, String kcCode, Long problemId, String failureKind, String difficulty)
      implements LearningEvent {}

  record DebugResolved(Long userId, String kcCode, Long problemId) implements LearningEvent {}

  /**
   * 学生消费了多模态学习内容（章节讲解视频 / AI 动画 / 知识点图解）。{@code mediaType} 形如
   * {@code video} / {@code storyboard} / {@code illustration}；用于把多模态消费回流到画像
   * {@code learning_preference} 维度（媒介偏好），驱动后续个性化推送。
   */
  record MediaConsumed(
      Long userId, String mediaType, Integer chapter, java.util.List<String> kcCodes)
      implements LearningEvent {}

  /**
   * 学生在答疑/讲解中采纳某讲法（{@code visual}/{@code logical}/{@code trial}）。回流到画像
   * {@code cognitive_style} 维度，让「认知风格」随学动态更新（闭环），而非仅访谈设一次。
   */
  record TutorStyleEngaged(Long userId, String style, String kcCode) implements LearningEvent {}

  /**
   * 学生在世界内「探针」中做出中性选择。{@code probeKey}/{@code choiceId} 由服务端
   * {@code ProbeCatalog} 权威解析为维度/score 增量/tag（前端不可篡改信号）；用于把「随玩随选」回流到画像
   * {@code cognitive_style} / {@code emotion_persistence} / {@code learning_preference} 等维度。
   */
  record ProbeChoiceMade(Long userId, String probeKey, String choiceId) implements LearningEvent {}

  /**
   * 学生在同一题失败后再次提交（坚持度行为信号）。回流到画像
   * {@code emotion_persistence}（轻微正向：愿意重试 = 韧性）。
   */
  record PracticeRetried(Long userId, Long problemId, String kcCode) implements LearningEvent {}

  /**
   * 学生在失败后放弃（如 Bug 矿洞会话 ABANDONED）。回流到 {@code emotion_persistence}（负向）。
   */
  record PracticeAbandoned(Long userId, Long problemId, String kcCode) implements LearningEvent {}

  /**
   * 逆袭：同一题在累计 {@code priorFails}≥2 次失败后最终通过。回流到 {@code emotion_persistence}
   * （较强正向：从挫败中坚持并成功）。
   */
  record ComebackSolved(Long userId, Long problemId, String kcCode, int priorFails)
      implements LearningEvent {}

  record TaughtAiSucceeded(Long userId, String kcCode, Long sessionId) implements LearningEvent {}

  record TaughtAiFailed(Long userId, String kcCode, Long sessionId) implements LearningEvent {}
}
