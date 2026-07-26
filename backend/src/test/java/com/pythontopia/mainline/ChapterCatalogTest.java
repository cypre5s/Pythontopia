package com.pythontopia.mainline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 章节目录：7 章覆盖全部 65 个 KC（与 course_kc.json 一致）。 */
class ChapterCatalogTest {

  @Test
  void sevenChaptersCoverAll65KcWithoutGapOrOverlap() {
    Set<String> all = new LinkedHashSet<>();
    int totalCount = 0;
    for (int ch = 1; ch <= ChapterCatalog.CHAPTER_COUNT; ch++) {
      var codes = ChapterCatalog.kcCodes(ch);
      totalCount += codes.size();
      all.addAll(codes);
      assertThat(ChapterCatalog.title(ch)).isNotBlank();
    }
    assertThat(totalCount).isEqualTo(65); // 无重叠
    assertThat(all).hasSize(65); // 无缺口
    assertThat(all).contains("PY43-KC-001", "PY43-KC-065");
  }

  @Test
  void kcCodesAreZeroPadded() {
    assertThat(ChapterCatalog.kcCodes(1)).first().isEqualTo("PY43-KC-001");
  }

  /** KC → 章反查（掌握度事件触发章星重算需要它）：与 kcCodes 正查口径一致，非本课程 KC 返回空。 */
  @Test
  void chapterOfKcMirrorsKcCodes() {
    for (int ch = 1; ch <= ChapterCatalog.CHAPTER_COUNT; ch++) {
      for (String kc : ChapterCatalog.kcCodes(ch)) {
        assertThat(ChapterCatalog.chapterOfKc(kc)).contains(ch);
      }
    }
    assertThat(ChapterCatalog.chapterOfKc("PY43-KC-066")).isEmpty();
    assertThat(ChapterCatalog.chapterOfKc("  PY43-KC-054 ")).contains(7);
    assertThat(ChapterCatalog.chapterOfKc(null)).isEmpty();
    assertThat(ChapterCatalog.chapterOfKc(" ")).isEmpty();
  }

  @Test
  void invalidChapterRejected() {
    assertThat(ChapterCatalog.isValidChapter(0)).isFalse();
    assertThat(ChapterCatalog.isValidChapter(8)).isFalse();
    assertThatThrownBy(() -> ChapterCatalog.title(8)).isInstanceOf(IllegalArgumentException.class);
  }
}
