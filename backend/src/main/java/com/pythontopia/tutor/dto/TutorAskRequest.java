package com.pythontopia.tutor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 导师问答请求（{@code POST /api/tutor/ask}，SSE 流式）。字段契约：
 *
 * <ul>
 *   <li>{@code question}：必填，≤2000 字。
 *   <li>{@code kcCodes}：关联知识点，转发为 Python graph 的 {@code kc_codes} 做 RAG 过滤检索；可空。
 *   <li>{@code context}：当前学习上下文（视频标题 / PPT 页 / 题面摘要），透传 graph 增强证据检索；可空。
 *   <li>{@code images}：可选截图 / 手写代码图，最多 3 张、单图 ≤8MB 字符；仅允许 JPEG、PNG、GIF、WebP，
 *       每张原始图片 ≤5MiB。Graph 校验并规范化 base64 后，由 DeepSeek 视觉模型直接理解并以文字回答。
 *   <li>{@code style}：学生显式选择的认知风格（visual/logical/trial）；非空时<b>无视画像置信度门槛</b>，
 *       由 {@code TutorService} 注入 profile_brief 即时按该风格改写答案，为空则由画像决定。
 *   <li>{@code conversationId}：会话窗口 id——同一聊天窗口（一次打开 / 一次「新对话」）内多次提问共享，
 *       供「聊天记录」按会话分组；仅前端生成 + 后端落库，不下传 graph。
 *   <li>{@code classroomId}：<b>教师 copilot</b> 专用——教师在自己班级页提问时带上班级 id，后端校验归属
 *       （{@code ClassroomGuard.requireOwner}）后注入本班<b>只读</b>学情（最弱 KC / 高频误区）增强作答。
 *       <b>红线</b>：学生角色一律忽略此字段，绝不触达任何班级聚合数据。
 * </ul>
 */
public record TutorAskRequest(
    @NotBlank @Size(max = 2000) String question,
    List<String> kcCodes,
    // 放宽至 20000：除视频/PPT 摘要外，还承载工坊「上传文件解析文本」带标签注入（每文件 ~6000 字，
    // 多文件拼接），仍有界防滥用。上传内容是用户提供上下文，绝不当课件 evidence。
    @Size(max = 20000) String context,
    @Size(max = 3) List<@Size(max = 8_000_000) String> images,
    @Pattern(regexp = "visual|logical|trial") String style,
    @Size(max = 64) String conversationId,
    @Positive Long classroomId,
    // 看课页学生提问时回传当前视频 id + 播放时刻（秒）；后端读取真实 MP4 的机器转写时间窗，
    // 与课件 RAG 证据一同交给导师。字幕缺失或错版时明确报错，不伪造视频上下文。
    @Positive Long videoId,
    @PositiveOrZero Integer videoTimeSec) {}
