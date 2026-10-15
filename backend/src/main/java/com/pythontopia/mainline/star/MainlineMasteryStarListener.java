package com.pythontopia.mainline.star;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.mainline.ChapterCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 掌握度变化 → 章级星级重算（{@link StarSource#MASTERY} 的唯一触发点）。
 *
 * <p>没有这个监听器时 {@code MASTERY} 全仓无调用点：{@code recalc} 只在章测提交（TEST）与 stage 完成
 * （STAGE_COMPLETE）时被调，学生考完 90 分（当时 2★）后把本章 KC 掌握度刷到 0.85，3★ 条件已满足却没有
 * 任何事件触发重算，除非重考——3★ 实际永远不发。
 *
 * <p>这里订阅已有的学习事件（{@link LearningEvent.PracticeSucceeded} /
 * {@link LearningEvent.ResourceCheckCompleted}——即掌握度评估器 {@code MasteryEvaluator} 的两个正向来源），
 * 把 KC 反查回章号后调一次 {@code recalc}。{@code recalc} 幂等且只升不降，重复触发无副作用。
 *
 * <p>说明：与 {@code learner} 域的掌握度写入监听器同为默认最低优先级，若本监听器先于掌握度写入执行，
 * 本次 recalc 读到的是上一次的掌握度，3★ 会顺延到下一个学习事件发放——不影响最终一致。星级重算失败
 * 不得打断发布方（提交判题 / 资源微测）链路，故整体 try/catch 降级为 warn 日志。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MainlineMasteryStarListener {

  private static final Logger log = LoggerFactory.getLogger(MainlineMasteryStarListener.class);

  private final ChapterStarService starService;

  public MainlineMasteryStarListener(ChapterStarService starService) {
    this.starService = starService;
  }

  @EventListener
  public void onPracticeSucceeded(LearningEvent.PracticeSucceeded event) {
    recalcChapterOf(event.userId(), event.kcCode());
  }

  @EventListener
  public void onResourceCheckCompleted(LearningEvent.ResourceCheckCompleted event) {
    recalcChapterOf(event.userId(), event.kcCode());
  }

  /** KC → 章（非本课程 KC 直接跳过）→ 重算；异常降级为 warn，绝不冒泡打断学习事件链路。 */
  private void recalcChapterOf(Long userId, String kcCode) {
    if (userId == null) {
      return;
    }
    ChapterCatalog.chapterOfKc(kcCode)
        .ifPresent(
            chapter -> {
              try {
                starService.recalc(userId, chapter, StarSource.MASTERY);
              } catch (RuntimeException ex) {
                log.warn(
                    "掌握度触发的章星重算失败 kc={} ch={} errorType={}",
                    kcCode,
                    chapter,
                    ex.getClass().getSimpleName());
              }
            });
  }
}
