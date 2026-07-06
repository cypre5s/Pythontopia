package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 视频可见时长增量上报。{@code deltaSec} 必须为正；服务端再钳制到单次上限（默认 120s）并限频（默认 2 次/分），
 * 防挂机刷时长（§9）。
 */
public record VideoProgressRequest(@NotNull @Min(1) Integer deltaSec) {}
