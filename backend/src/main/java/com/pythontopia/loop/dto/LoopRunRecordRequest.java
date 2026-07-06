package com.pythontopia.loop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 闭环 run 审计回写请求体（{@code POST /api/loop/runs}）。字段镜像 graph 侧
 * {@code schemas/agentic_loop.py} 的 {@code AgenticLoopRunRecord}（HTTP/镜像契约，非直接 import）；
 * JSON 采用项目统一的 camelCase，与前端 {@code loopTypes.ts} 同形。
 *
 * <p>带默认值的字段（maxSteps/stepsUsed/replanCount/toolsUsed/grounding/safety/tokens/traceId）声明为
 * 包装类型，省略即为 {@code null}，由 {@code LoopRunService} 归一为镜像默认值（向后兼容）。
 * 注解负责 MVC 层 {@code @Valid} 的 400；{@code LoopRunService} 另做权威校验，使服务层（含本测试）
 * 不依赖 MVC 即可 fail-fast。
 *
 * <p>{@code userId} 为镜像字段；服务端以 JWT 主体为权威落库，body 与认证用户不一致即判越权（403）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoopRunRecordRequest(
    @NotNull @Min(1) Long userId,
    @NotBlank @Size(max = 120) String sessionId,
    @NotNull @PositiveOrZero Integer turn,
    @Min(1) @Max(8) Integer maxSteps,
    @PositiveOrZero Integer stepsUsed,
    @PositiveOrZero Integer replanCount,
    List<@NotBlank @Size(max = 64) String> toolsUsed,
    Boolean groundingPass,
    Boolean safetyPass,
    @PositiveOrZero Integer inputTokens,
    @PositiveOrZero Integer outputTokens,
    @Size(max = 128) String traceId) {}
