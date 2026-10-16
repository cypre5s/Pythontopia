package com.pythontopia.intent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 百晓工坊「意图路由」请求（前端 camelCase）。
 *
 * <p>前端把这一句话、已加载的课程 KC 目录（{@code courseKcs}，为 {@code @} 提及菜单本就全量加载）
 * 以及学生手动 {@code @} 指定的 KC（{@code manualKcCodes}，最高优先级）发来；Java 在转交 Graph 前
 * 用唯一课程包 43 的服务端目录覆盖客户端目录，避免页面刚加载时的空目录竞态或客户端伪造目录。
 * 真正的意图分类与 KC 锚定在 Python graph 的 IntentRouterAgent 完成。除 {@code message} 外均可空。
 *
 * <p>R-16 多轮意图理解新增（契约见百晓深度优化蓝本 §0）：{@code recentTurns}（≤6 轮，时间正序）、
 * {@code lastIntent}/{@code lastKcCodes}（上轮已执行意图与 KC，指代消解锚点）、
 * {@code sessionCorrections}（≤3 条会话内纠错记忆）、{@code scope}（default/quiz，考核模式收缩工具集）。
 * 学生画像摘要 {@code profile_brief} 由后端 IntentService 注入，前端不传。{@code source} 标记本轮来自
 * 自由输入、斜杠命令、能力启动器或上下文建议，用于 graph 决定是否需要澄清。{@code capabilityNamespace}
 * 是能力启动器的 6 类分组 hint，只收缩候选工具空间，不等同于强制 primary intent。
 */
public record IntentRouteRequest(
    @NotBlank @Size(max = 4000) String message,
    @Size(max = 65) List<@Size(max = 32) String> manualKcCodes,
    @Valid @Size(max = 65) List<CourseKc> courseKcs,
    Integer currentChapter,
    @Size(max = 65) List<@Size(max = 32) String> recentKcCodes,
    @Valid @Size(max = 6) List<Turn> recentTurns,
    @Size(max = 32) String lastIntent,
    @Size(max = 3) List<@Size(max = 32) String> lastKcCodes,
    @Valid @Size(max = 3) List<Correction> sessionCorrections,
    @Pattern(regexp = "default|quiz") String scope,
    @Pattern(regexp = "composer_text|slash_command|capability_launcher|contextual_suggestion")
        String source,
    @Pattern(regexp = "ask|quiz|make|practice|plan|find") String capabilityNamespace,
    @Pattern(
            regexp =
                "ASK|VIDEO|REVIEW|HANDOUT|MINDMAP|PRACTICE|CODE_LAB|READING|STORYBOARD|ASKME|EXPLAIN_CODE|FIX_BUG|PLAN|PROGRESS|NAVIGATE|MY_STUDY|BROWSE")
        String requestedIntent,
    @Valid InteractionContext interaction) {

  /** 课程 KC 目录条目：code 用于锚定，name 用于让 LLM 把语句主题映射到正确知识点。 */
  public record CourseKc(@NotBlank @Size(max = 32) String code, @Size(max = 200) String name) {}

  /** 近期对话轮（指代消解上下文）：role 限 user|assistant，content 为 ≤600 字摘要。 */
  public record Turn(
      @NotBlank @Pattern(regexp = "user|assistant") String role,
      @Size(max = 600) String content) {}

  /** 会话内意图纠错记忆：学生点「不是想要这个」改派后记录（原话 → 改派意图）。 */
  public record Correction(
      @Size(max = 600) String message, @Size(max = 32) String correctedIntent) {}

  /**
   * 当前交互引用。只允许客户端声明页面/编辑器事实和资源 id；画像、掌握度、判题结果必须由服务端校验。
   */
  public record InteractionContext(
      @Size(max = 128) String sessionId,
      @Size(max = 64) String currentScene,
      @Size(max = 64) String currentStage,
      Long currentProblemId,
      @Size(max = 12000) String currentCode,
      Long latestSubmissionId,
      @Size(max = 10) List<Long> selectedPageIds,
      @Size(max = 4000) String errorText,
      @Size(max = 64) String frontendAction,
      Long currentResourceId) {}
}
