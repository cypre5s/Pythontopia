package com.pythontopia.orchestrator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 学习总管（OrchestratorAgent）单轮对话请求。
 *
 * <p>前端把当前学习上下文随每轮消息发来，Java 端只做透传代理：会话记忆与多智能体编排都在 Python
 * graph 服务里完成，后端不落库。除 {@code message} 外字段均可空；{@code sessionId} 为空时由后端
 * 生成一个临时会话 id。{@code courseKcCodes} 作为课程 KC 索引（{@code course_kc_index}）传给
 * Python 用于证据接地与防幻觉。
 */
public record OrchestratorTurnRequest(
    @NotBlank @Size(max = 4000) String message,
    @Size(max = 64) String sessionId,
    @Valid List<OrchestratorTurnMessage> history,
    Integer currentChapter,
    List<String> recentKcCodes,
    List<String> recentFailures,
    @Size(max = 2000) String profileBrief,
    List<String> courseKcCodes) {}
