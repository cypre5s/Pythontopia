package com.pythontopia.mainline.practice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 客观题作答提交体。
 *
 * <p>{@code answers} 长度按题型：{@code CHOICE}/{@code JUDGE} 为 1（选项文本 / 「正确」「错误」）；
 * {@code FILL} 为 {@code blankCount}（按空位顺序）。请求最多 32 项、每项 8192 字符。判分由服务端归一化（去空白、
 * 忽略大小写等策略由实现定）。
 */
public record PracticeAnswerRequest(
    @NotNull @Size(max = 32) List<@Size(max = 8192) String> answers) {}
