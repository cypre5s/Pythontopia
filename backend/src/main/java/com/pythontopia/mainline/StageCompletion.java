package com.pythontopia.mainline;

import com.pythontopia.mainline.domain.MainlineStage;
import com.pythontopia.mainline.domain.MainlineStageProgress;
import java.util.HashSet;

/**
 * stage 三件套（看/学/练）过线判定——纯函数，overview 展示与「完成即重算」共用，便于单测。
 *
 * <ul>
 *   <li>看：{@code video_watched_sec ≥ required_video_sec}。
 *   <li>学：{@code required_pages>0} 时按去重页数达标；否则需读全 {@code courseware_page_ids}（空集视为已读，占位期）。
 *   <li>练：去重例题数 ≥ {@code required_example_count}。
 * </ul>
 */
public final class StageCompletion {

  private StageCompletion() {}

  public static boolean videoComplete(MainlineStage stage, MainlineStageProgress progress) {
    return progress != null && progress.videoWatchedSec() >= stage.requiredVideoSec();
  }

  public static int distinctPages(MainlineStageProgress progress) {
    return progress == null ? 0 : (int) progress.pagesRead().stream().distinct().count();
  }

  public static boolean pagesComplete(MainlineStage stage, MainlineStageProgress progress) {
    if (stage.requiredPages() > 0) {
      return distinctPages(progress) >= stage.requiredPages();
    }
    if (stage.coursewarePageIds().isEmpty()) {
      return true; // 占位期：courseware_page_ids 未回填 ⇒ 「学」环节默认过线（见 V156 TODO）。
    }
    return progress != null
        && new HashSet<>(progress.pagesRead()).containsAll(stage.coursewarePageIds());
  }

  public static int distinctExamples(MainlineStageProgress progress) {
    return progress == null ? 0 : (int) progress.examplesDone().stream().distinct().count();
  }

  public static boolean examplesComplete(MainlineStage stage, MainlineStageProgress progress) {
    return distinctExamples(progress) >= stage.requiredExampleCount();
  }

  public static boolean allComplete(MainlineStage stage, MainlineStageProgress progress) {
    return videoComplete(stage, progress)
        && pagesComplete(stage, progress)
        && examplesComplete(stage, progress);
  }

  /** overview 展示用的「目标页数」：requiredPages>0 取之，否则取 courseware_page_ids 总数。 */
  public static int requiredPagesDisplay(MainlineStage stage) {
    return stage.requiredPages() > 0 ? stage.requiredPages() : stage.coursewarePageIds().size();
  }
}
