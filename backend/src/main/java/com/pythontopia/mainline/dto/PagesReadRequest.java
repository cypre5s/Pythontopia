package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量课件页阅读打点（「学」打点）。整章 PPT 页数多（最多 116 页），逐页打点不现实；阅读器按批上报
 * 已读 {@code pageIds}，服务端并集去重幂等累加。空集合视为无新增（仍幂等返回当前进度）。
 */
public record PagesReadRequest(@NotNull List<Long> pageIds) {}
