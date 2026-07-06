package com.pythontopia.coursepack.dto;

import com.pythontopia.coursepack.domain.CourseVideoManifestEntry;
import java.util.List;

/**
 * 下发给前端 {@code src/api/courseVideo.ts} 的视频条目。{@code available} 表示 manifest 记录对应的
 * mp4 文件当前是否已就绪可播放（清单有记录但本地未下载时为 false，前端据此回退到 PPT/问答）。
 */
public record CourseVideoDto(
    long id,
    int chapter,
    String title,
    Integer durationSec,
    List<String> kcCodes,
    boolean available) {

  public static CourseVideoDto from(CourseVideoManifestEntry entry, boolean available) {
    return new CourseVideoDto(
        entry.id() == null ? 0L : entry.id(),
        entry.chapter() == null ? 0 : entry.chapter(),
        entry.title(),
        entry.durationSec(),
        entry.kcCodes() == null ? List.of() : List.copyOf(entry.kcCodes()),
        available);
  }
}
