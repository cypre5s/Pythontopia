package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 批量课件页阅读打点（「学」打点）。整章 PPT 页数多（最多 116 页），逐页打点不现实；阅读器按批上报
 * 已读 {@code pageIds}，服务端并集去重幂等累加。空集合视为无新增（仍幂等返回当前进度）。
 *
 * <p>单批上限 {@link #MAX_BATCH_PAGES} 页（防「整章 id 一次灌爆」的体积滥用）；服务端另对本小节
 * 课件页切片做白名单过滤，不属于本 stage 的 pageId 直接丢弃（见 {@code MainlineService.reportPagesRead}）。
 */
public record PagesReadRequest(@NotNull @Size(max = MAX_BATCH_PAGES) List<Long> pageIds) {

  /** 单次批量上报的页数上限（一个小节的课件切片远小于此值）。 */
  public static final int MAX_BATCH_PAGES = 64;
}
