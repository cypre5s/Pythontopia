package com.pythontopia.loop;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.loop.LoopRunRepository.LoopRunInsert;
import com.pythontopia.loop.dto.LoopRunDto;
import com.pythontopia.loop.dto.LoopRunRecordRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 闭环 run 审计落库服务（S-spine 第 4 条）。供 graph S1 在每轮有界闭环结束后回写一条审计。
 *
 * <p>权威校验在服务层完成（不依赖 MVC {@code @Valid}），对齐 §0「有界非自由 ReAct」「业务 fail-fast」
 * 红线与 §8.6 安全：{@code user_id} 以认证主体为准，body 不一致即越权；step/replan/token 越界一律
 * fail-fast 抛 {@link BusinessException}。带默认值的镜像字段缺省时归一为 {@code AgenticLoopRunRecord}
 * 的默认值（向后兼容）。
 */
@Service
public class LoopRunService {

  private static final int MAX_SESSION_ID_LEN = 120;
  private static final int MAX_TRACE_ID_LEN = 128;
  private static final int MAX_TOOL_NAME_LEN = 64;
  private static final int MAX_TOOLS = 64;
  private static final int DEFAULT_MAX_STEPS = 4;
  private static final int MAX_STEPS_CEILING = 8;

  private final LoopRunRepository repository;

  public LoopRunService(LoopRunRepository repository) {
    this.repository = repository;
  }

  /**
   * 落库一条闭环 run 审计。{@code authUserId} 为权威所有者（来自 JWT 主体）。
   *
   * @throws BusinessException 400 字段越界 / 403 body user_id 与认证用户不一致（越权）
   */
  public LoopRunDto record(Long authUserId, LoopRunRecordRequest request) {
    if (authUserId == null) {
      throw badRequest("LOOP_RUN_USER_REQUIRED", "认证用户缺失");
    }
    if (request == null) {
      throw badRequest("LOOP_RUN_BODY_REQUIRED", "闭环 run 请求体不能为空");
    }
    requireOwnership(authUserId, request.userId());

    String sessionId = requireSessionId(request.sessionId());
    int turn = requireNonNegative(request.turn(), "turn", "LOOP_RUN_TURN");
    int maxSteps = normalizeMaxSteps(request.maxSteps());
    int stepsUsed = normalizeStepsUsed(request.stepsUsed(), maxSteps);
    int replanCount = normalizeNonNegative(request.replanCount(), 0, "replanCount", "LOOP_RUN_REPLAN");
    List<String> toolsUsed = normalizeTools(request.toolsUsed());
    boolean groundingPass = request.groundingPass() == null || request.groundingPass();
    boolean safetyPass = request.safetyPass() == null || request.safetyPass();
    int inputTokens = normalizeNonNegative(request.inputTokens(), 0, "inputTokens", "LOOP_RUN_INPUT_TOKENS");
    int outputTokens =
        normalizeNonNegative(request.outputTokens(), 0, "outputTokens", "LOOP_RUN_OUTPUT_TOKENS");
    String traceId = normalizeTraceId(request.traceId());

    LoopRunInsert insert =
        new LoopRunInsert(
            authUserId,
            sessionId,
            turn,
            maxSteps,
            stepsUsed,
            replanCount,
            toolsUsed,
            groundingPass,
            safetyPass,
            inputTokens,
            outputTokens,
            traceId);
    return LoopRunDto.from(repository.insert(insert));
  }

  /**
   * 取某 run 详情，并校验归属。跨用户/不存在均返回 404（隐藏存在性，防枚举）。
   *
   * @throws BusinessException 404 不存在或非本人所有
   */
  public LoopRunDto getForOwner(Long authUserId, Long id) {
    if (authUserId == null) {
      throw badRequest("LOOP_RUN_USER_REQUIRED", "认证用户缺失");
    }
    LoopRunEntity entity =
        repository
            .findById(id)
            .filter(run -> run.userId().equals(authUserId))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "LOOP_RUN_NOT_FOUND", "闭环 run 不存在"));
    return LoopRunDto.from(entity);
  }

  private void requireOwnership(Long authUserId, Long bodyUserId) {
    if (bodyUserId == null) {
      throw badRequest("LOOP_RUN_USER_REQUIRED", "user_id 不能为空");
    }
    if (!bodyUserId.equals(authUserId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "LOOP_RUN_FORBIDDEN", "不能为其它用户记录闭环 run 审计");
    }
  }

  private String requireSessionId(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      throw badRequest("LOOP_RUN_SESSION_REQUIRED", "session_id 不能为空");
    }
    String trimmed = sessionId.strip();
    if (trimmed.length() > MAX_SESSION_ID_LEN) {
      throw badRequest(
          "LOOP_RUN_SESSION_TOO_LONG", "session_id 超过 " + MAX_SESSION_ID_LEN + " 字符");
    }
    return trimmed;
  }

  private int requireNonNegative(Integer value, String field, String codePrefix) {
    if (value == null) {
      throw badRequest(codePrefix + "_REQUIRED", field + " 不能为空");
    }
    if (value < 0) {
      throw badRequest(codePrefix + "_INVALID", field + " 不能为负");
    }
    return value;
  }

  private int normalizeNonNegative(Integer value, int fallback, String field, String codePrefix) {
    if (value == null) {
      return fallback;
    }
    if (value < 0) {
      throw badRequest(codePrefix + "_INVALID", field + " 不能为负");
    }
    return value;
  }

  private int normalizeMaxSteps(Integer maxSteps) {
    int resolved = maxSteps == null ? DEFAULT_MAX_STEPS : maxSteps;
    if (resolved < 1 || resolved > MAX_STEPS_CEILING) {
      throw badRequest(
          "LOOP_RUN_MAX_STEPS_INVALID", "max_steps 必须在 1.." + MAX_STEPS_CEILING + " 之间（有界）");
    }
    return resolved;
  }

  private int normalizeStepsUsed(Integer stepsUsed, int maxSteps) {
    int resolved = normalizeNonNegative(stepsUsed, 0, "stepsUsed", "LOOP_RUN_STEPS_USED");
    if (resolved > maxSteps) {
      throw badRequest(
          "LOOP_RUN_STEPS_EXCEED_MAX", "steps_used 不能超过 max_steps（违反有界语义）");
    }
    return resolved;
  }

  private List<String> normalizeTools(List<String> toolsUsed) {
    if (toolsUsed == null || toolsUsed.isEmpty()) {
      return List.of();
    }
    if (toolsUsed.size() > MAX_TOOLS) {
      throw badRequest("LOOP_RUN_TOOLS_TOO_MANY", "tools_used 超过 " + MAX_TOOLS + " 项");
    }
    List<String> normalized = new ArrayList<>(toolsUsed.size());
    for (String tool : toolsUsed) {
      if (tool == null || tool.isBlank()) {
        throw badRequest("LOOP_RUN_TOOL_BLANK", "tools_used 含空工具名");
      }
      String trimmed = tool.strip();
      if (trimmed.length() > MAX_TOOL_NAME_LEN) {
        throw badRequest(
            "LOOP_RUN_TOOL_TOO_LONG", "工具名超过 " + MAX_TOOL_NAME_LEN + " 字符");
      }
      normalized.add(trimmed);
    }
    return List.copyOf(normalized);
  }

  private String normalizeTraceId(String traceId) {
    if (traceId == null) {
      throw badRequest("LOOP_RUN_TRACE_REQUIRED", "trace_id 不能为空");
    }
    String trimmed = traceId.strip();
    if (trimmed.isEmpty()) {
      throw badRequest("LOOP_RUN_TRACE_REQUIRED", "trace_id 不能为空");
    }
    if (trimmed.length() > MAX_TRACE_ID_LEN) {
      throw badRequest("LOOP_RUN_TRACE_TOO_LONG", "trace_id 超过 " + MAX_TRACE_ID_LEN + " 字符");
    }
    return trimmed;
  }

  private BusinessException badRequest(String code, String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
  }
}
