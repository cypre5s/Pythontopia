package com.pythontopia.mainline;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.mainline.domain.ChapterStar;
import com.pythontopia.mainline.star.ChapterStarRepository;
import org.springframework.http.HttpStatus;

/**
 * 章解锁硬门槛（§5）：第 N 章需前一章 ≥1★，第 1 章始终开放。
 *
 * <p>{@code MainlineService.overview} 只把 {@code unlocked} 当展示位下发；服务端写路径若不各自 gate，
 * 学生直接对第 7 章的 stage 打点/开考即可跳过全部前置。此处以纯静态工具收口该判定（无 Spring bean，
 * 与 {@link StageCompletion} / {@link ChapterCatalog} 同风格），供主线各服务各调一行。
 */
public final class ChapterUnlockGuard {

  /** 解锁前一章所需的最小星数。 */
  public static final int REQUIRED_PREV_STARS = 1;

  private ChapterUnlockGuard() {}

  /**
   * 章未解锁则抛 {@code 409 CHAPTER_LOCKED}。
   *
   * @param starRepository 章星权威只读来源
   * @param userId 当前学生
   * @param chapter 目标章（1..7）
   */
  public static void requireUnlocked(
      ChapterStarRepository starRepository, long userId, int chapter) {
    if (chapter <= 1) {
      return;
    }
    int prevStars =
        starRepository.find(userId, chapter - 1).map(ChapterStar::stars).orElse(0);
    if (prevStars < REQUIRED_PREV_STARS) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CHAPTER_LOCKED",
          "第 " + (chapter - 1) + " 章还没拿到 1 星，第 " + chapter + " 章尚未解锁");
    }
  }
}
