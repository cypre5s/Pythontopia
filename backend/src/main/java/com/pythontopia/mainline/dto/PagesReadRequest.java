package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 批量课件页阅读打点（「学」打点）。阅读器按批上报本小节 KC 关联页，服务端按页 ID 白名单过滤，
 * 再并集去重累加。空集合视为无新增（仍幂等返回当前进度）。
 *
 * <p>单批上限 {@link #MAX_BATCH_PAGES} 页（限制请求体体积）；服务端另对本小节
 * KC 课件页做白名单过滤，不属于本 stage 的 pageId 直接丢弃（见 {@code MainlineService.reportPagesRead}）。
 */
public record PagesReadRequest(@NotNull @Size(max = MAX_BATCH_PAGES) List<Long> pageIds) {

  /** 单次批量上报的页数上限（一个小节的 KC 课件页远小于此值）。 */
  public static final int MAX_BATCH_PAGES = 64;
}
