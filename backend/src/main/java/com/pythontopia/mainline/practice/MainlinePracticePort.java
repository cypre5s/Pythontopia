package com.pythontopia.mainline.practice;

import com.pythontopia.mainline.practice.dto.StagePracticeQuestionDto;
import java.util.List;

/**
 * 「练-客观题」只读端口（Phase 0 解耦契约）。
 *
 * <p>由题库域（{@code mainline.practice} 包，Phase 1 P2）实现；由主线标准服务（{@code MainlineService}，
 * Phase 1 P3）消费——用于在 {@code stageDetail} 注入题面、在过线判定里计入「客观题是否全对」。
 * 反向（答对后触发 stage 完成重算）见 {@code MainlineStageCompletionPort}。两个端口互为接口，
 * 使 P2 / P3 可并行开发、各自编译。
 *
 * <p>消费方应以 {@code Optional<MainlinePracticePort>} 注入：题库 bean 尚未就绪时优雅降级
 * （questions=[]、requiredQuestions=0、questionsComplete=true 即「无客观题要求」），保证应用可启动。
 */
public interface MainlinePracticePort {

  /** 某小节的客观题题面（不含答案，按 ordinal 升序）。 */
  List<StagePracticeQuestionDto> questionsForStage(int chapter, int stageIndex);

  /** 某小节需答对的客观题数（mix4 = 4；无题为 0）。 */
  int requiredQuestionCount(int chapter, int stageIndex);

  /** 某用户在某 stage 已答对的客观题 id 集合（去重）。 */
  List<Long> correctQuestionIds(long userId, long stageId);

  /** 某用户在某 stage 的客观题是否已全部答对（无题要求时视为已完成）。 */
  boolean questionsComplete(long userId, long stageId, int chapter, int stageIndex);
}
