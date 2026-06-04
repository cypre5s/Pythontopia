package com.pythontopia.tutor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 导师问答请求。
 *
 * <p>R-12 主线 Phase 3：把单数 {@code kcCode} 升级为复数 {@code kcCodes}（与 Python
 * graph 服务的 {@code kc_codes: list[str]} 契约对齐，此前单复数不一致导致 KC 永远没
 * 传到 RAG）。新增 {@code context}（当前视频标题 / PPT 页 / 题面摘要等），透传给
 * Python 用于增强证据检索与答疑上下文。两者均可空，兼容纯文本提问。
 *
 * <p>R-14 看图答疑（B）：可选 {@code images}（截图 / 手写代码图，可带 data URL 前缀，最多 3 张，
 * 仿 Cursor 多图附件）。非空时后端逐张经讯飞「图片理解」转客观描述并入 context，再交
 * deepseek-v4-pro 作答；单图上限约 8MB 字符（≈ 讯飞 4MB base64），缺省则为纯文本提问。
 */
public record TutorAskRequest(
    @NotBlank @Size(max = 2000) String question,
    List<String> kcCodes,
    @Size(max = 2000) String context,
    @Size(max = 3) List<@Size(max = 8_000_000) String> images) {}
