package com.pythontopia.world.boss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 一局无尽远征结束后请求结算。成绩由服务端保存的判题结果推导。
 *
 * <p>客户端只提供服务端发放的 run id，不接收客户端分数、深度或通关声明。
 */
public record ExpeditionScoreSubmitDto(
    @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String runId) {}
