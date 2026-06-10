package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 例题完成打点。{@code submissionId} 可选；若带，则服务端校验该提交为本人且状态 PASSED（§9），
 * 防伪造完成。
 */
public record ExampleDoneRequest(
    @NotNull @Positive Long problemId, @Positive Long submissionId) {}
