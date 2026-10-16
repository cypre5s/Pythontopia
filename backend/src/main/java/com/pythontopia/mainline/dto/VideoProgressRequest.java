package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 零增量只启动服务端计时基准；正增量会受服务端已流逝时间、单次上限和限频约束。 */
public record VideoProgressRequest(@NotNull @Min(0) Integer deltaSec) {}
