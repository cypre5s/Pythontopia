package com.pythontopia.mainline.dto;

/**
 * 补星类端点统一返回（无尽小结 / 错题复盘，r15 §9）。
 *
 * @param starAwarded 本次是否真正发放了一颗补星（幂等：已发放过 / 未达条件 / 已封顶均为 false）。
 */
public record StarAwardResultDto(boolean starAwarded) {}
