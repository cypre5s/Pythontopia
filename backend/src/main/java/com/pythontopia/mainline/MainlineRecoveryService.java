package com.pythontopia.mainline;

import com.pythontopia.bugmine.repository.BugMineRepository;
import com.pythontopia.mainline.domain.ChapterStar;
import com.pythontopia.mainline.star.ChapterStarRepository;
import com.pythontopia.mainline.star.ChapterStarService;
import com.pythontopia.mainline.star.StarSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 补星条件校验（§5/§7）：错题复盘（本章 RESOLVED≥3 → ERRORBOOK_RECOVERY）、
 * 无尽（单轮答对≥8 且正确率≥80%，每日每章 1 次 → ENDLESS_RECOVERY）。
 * 加星/幂等/封顶由 ChapterStarService.applyRecoveryStar 收口。
 *
 * <p>两条补星途径都会真实加星（进而解锁下一章），因此同样受章解锁 gate 约束
 * （{@link ChapterUnlockGuard}）：不能对一个还没解锁的章补星。
 */
@Service
public class MainlineRecoveryService {

  static final int ERRORBOOK_RESOLVED_THRESHOLD = 3;
  static final int ENDLESS_MIN_CORRECT = 8;
  static final double ENDLESS_MIN_ACCURACY = 0.80;

  /** 单轮无尽题量上限（与 {@code EndlessSummaryRequest} 同口径，服务端兜底不依赖 Bean Validation）。 */
  static final int ENDLESS_MAX_ROUND_QUESTIONS = 200;

  private static final Logger log = LoggerFactory.getLogger(MainlineRecoveryService.class);

  private final BugMineRepository bugMineRepository;
  private final ChapterStarService starService;
  private final ChapterStarRepository starRepository;

  public MainlineRecoveryService(
      BugMineRepository bugMineRepository,
      ChapterStarService starService,
      ChapterStarRepository starRepository) {
    this.bugMineRepository = bugMineRepository;
    this.starService = starService;
    this.starRepository = starRepository;
  }

  /** 错题复盘补星：本章 RESOLVED ≥ 3 则发放（幂等）。返回本次是否真正发放了补星。 */
  @Transactional
  public boolean claimErrorbookRecovery(long userId, int chapter) {
    requireChapter(chapter);
    ChapterUnlockGuard.requireUnlocked(starRepository, userId, chapter);
    int resolved = bugMineRepository.countResolvedByKcCodes(userId, ChapterCatalog.kcCodes(chapter));
    if (resolved < ERRORBOOK_RESOLVED_THRESHOLD) {
      return false;
    }
    int before = currentStars(userId, chapter);
    ChapterStar after =
        starService.applyRecoveryStar(userId, chapter, StarSource.ERRORBOOK_RECOVERY);
    return after.stars() > before;
  }

  /**
   * 无尽小结 → {@code ENDLESS_RECOVERY} 补星判定（达标 + 每日每章 1 次有效）。返回本次是否发放了补星。
   *
   * <p>correct/total/streak 均为客户端自证（服务端无无尽轮次落库可交叉核对），因此这里先做可信性兜底：
   * 非法区间（{@code total<=0}、{@code correct>total}、超单轮题量上限）一律不发星，不进入判定。
   */
  @Transactional
  public boolean recordEndlessSummary(
      long userId, int chapter, int correct, int total, int streak) {
    requireChapter(chapter);
    ChapterUnlockGuard.requireUnlocked(starRepository, userId, chapter);
    if (!isPlausibleRound(correct, total, streak)) {
      return false;
    }
    boolean qualifies =
        correct >= ENDLESS_MIN_CORRECT && (double) correct / total >= ENDLESS_MIN_ACCURACY;
    if (!qualifies) {
      return false;
    }
    // 防刷：每日每章至多 1 次有效 ENDLESS_RECOVERY 判定。
    if (starRepository.countEventsToday(userId, chapter, StarSource.ENDLESS_RECOVERY.name()) >= 1) {
      return false;
    }
    int before = currentStars(userId, chapter);
    ChapterStar after = starService.applyRecoveryStar(userId, chapter, StarSource.ENDLESS_RECOVERY);
    boolean awarded = after.stars() > before;
    if (awarded) {
      log.info(
          "endless recovery star user={} ch={} correct={}/{} streak={}",
          userId, chapter, correct, total, streak);
    }
    return awarded;
  }

  /** 一轮无尽小结的自证数据是否可信：0 &lt; total ≤ 上限，且 correct / streak 都不超过 total。 */
  private static boolean isPlausibleRound(int correct, int total, int streak) {
    return total > 0
        && total <= ENDLESS_MAX_ROUND_QUESTIONS
        && correct >= 0
        && correct <= total
        && streak >= 0
        && streak <= total;
  }

  private int currentStars(long userId, int chapter) {
    return starRepository.find(userId, chapter).map(ChapterStar::stars).orElse(0);
  }

  private void requireChapter(int chapter) {
    if (!ChapterCatalog.isValidChapter(chapter)) {
      throw new IllegalArgumentException("chapter out of range 1..7: " + chapter);
    }
  }
}
