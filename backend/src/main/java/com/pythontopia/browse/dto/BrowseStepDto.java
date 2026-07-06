package com.pythontopia.browse.dto;

import com.pythontopia.browse.entity.BrowseStepEntity;

/**
 * 浏览步骤的对外视图（时间线一格）。{@code index} 为 0-based 有界序号，{@code action} ∈
 * {navigate, read, search, stop}，{@code url} 为命中地址（可空串），{@code summary} 为去敏摘要。
 */
public record BrowseStepDto(int index, String action, String url, String summary) {

  public static BrowseStepDto from(BrowseStepEntity entity) {
    return new BrowseStepDto(entity.idx(), entity.action(), entity.url(), entity.summary());
  }
}
