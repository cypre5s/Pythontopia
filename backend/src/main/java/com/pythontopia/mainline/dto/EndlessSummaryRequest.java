package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 无尽远征补星申请；成绩由服务端按用户、活动 run 和发题章节查询判题记录。 */
public record EndlessSummaryRequest(
    @NotNull @Min(1) @Max(7) Integer chapter,
    @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String runId) {}
