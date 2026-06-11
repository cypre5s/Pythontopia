package com.pythontopia.mainline;

/**
 * stage 完成重算端口（Phase 0 解耦契约，与 {@code MainlinePracticePort} 互补）。
 *
 * <p>由 {@code MainlineService}（Phase 1 P3）实现；由题库域（{@code mainline.practice}，Phase 1 P2）
 * 在「客观题答对」后调用，触发一次三件套是否齐全的判定 + 星级重算（幂等）。题库域应以
 * {@code Optional<MainlineStageCompletionPort>} 注入，bean 未就绪时跳过（不影响答题打点本身）。
 */
public interface MainlineStageCompletionPort {

  /** 重新判定该用户该 stage 是否三件套齐全；首次齐全则置完成并重算星级（幂等）。 */
  void recheckCompletion(long userId, long stageId);
}
