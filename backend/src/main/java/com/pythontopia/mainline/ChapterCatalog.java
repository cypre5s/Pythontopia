package com.pythontopia.mainline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * packId=43 的 7 章静态目录（章号 → 标题 + KC 区间）。
 *
 * <p>标题取 {@code data/course-pack-43/json/course_chapter.json} 权威短意；KC 区间与
 * {@code frontend/src/data/chapters.ts} / {@code course_kc.json} 校对一致（ch1=001–007 … ch7=054–065）。
 * 这是<b>课程权威映射</b>而非臆造：用于 overview 章标题与星级重算的「本章 KC 集合」。
 */
public final class ChapterCatalog {

  private ChapterCatalog() {}

  public static final int CHAPTER_COUNT = 7;
  private static final String KC_PREFIX = "PY43-KC-";

  private record ChapterDef(int chapter, String title, int startKc, int endKc) {}

  private static final List<ChapterDef> CHAPTERS =
      List.of(
          new ChapterDef(1, "第一章：计算工具与计算思维", 1, 7),
          new ChapterDef(2, "第二章：Python 语言基础", 8, 16),
          new ChapterDef(3, "第三章：计算机信息表示及计算", 17, 30),
          new ChapterDef(4, "第四章：流程自动化", 31, 38),
          new ChapterDef(5, "第五章：数据结构", 39, 47),
          new ChapterDef(6, "第六章：数据存取与分析", 48, 53),
          new ChapterDef(7, "第七章：归纳与抽象", 54, 65));

  public static boolean isValidChapter(int chapter) {
    return chapter >= 1 && chapter <= CHAPTER_COUNT;
  }

  public static String title(int chapter) {
    return def(chapter).title();
  }

  /** 该章全部 KC code（PY43-KC-NNN，零填充 3 位）。 */
  public static List<String> kcCodes(int chapter) {
    ChapterDef def = def(chapter);
    List<String> codes = new ArrayList<>();
    for (int n = def.startKc(); n <= def.endKc(); n++) {
      codes.add(KC_PREFIX + String.format("%03d", n));
    }
    return codes;
  }

  /** KC code → 所属章的反查索引（由 {@link #kcCodes(int)} 构建，保证与正查口径一致）。 */
  private static final Map<String, Integer> CHAPTER_BY_KC = buildChapterByKc();

  private static Map<String, Integer> buildChapterByKc() {
    Map<String, Integer> index = new LinkedHashMap<>();
    for (int chapter = 1; chapter <= CHAPTER_COUNT; chapter++) {
      for (String kc : kcCodes(chapter)) {
        index.put(kc, chapter);
      }
    }
    return Map.copyOf(index);
  }

  /** KC code → 章号；不属于本课程 KC 区间（或为空）时返回 {@link Optional#empty()}。 */
  public static Optional<Integer> chapterOfKc(String kcCode) {
    if (kcCode == null || kcCode.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(CHAPTER_BY_KC.get(kcCode.trim()));
  }

  private static ChapterDef def(int chapter) {
    if (!isValidChapter(chapter)) {
      throw new IllegalArgumentException("chapter out of range 1..7: " + chapter);
    }
    return CHAPTERS.get(chapter - 1);
  }
}
