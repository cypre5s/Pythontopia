package com.pythontopia.feature.tutor

/** 单条聊天消息。 */
data class TutorMessage(
    val role: Role,
    val text: String,
    val evidencePageIds: List<Int> = emptyList(),
    val groundingStatus: String? = null,
    /** 助手消息流式生成中（光标/进度）。 */
    val streaming: Boolean = false,
) {
    enum class Role { USER, ASSISTANT }
}

/**
 * 导师问答 UI 状态（MVI 单一状态）。
 *
 * - [messages] 当前会话气泡（user / assistant 交替）。
 * - [input] 输入框文本。
 * - [sending] 正在等待/接收流式回答（禁用发送、显示进度）。
 * - [error] 流式失败提示。
 * - [history] 历史会话（GET /api/tutor/sessions）。
 */
data class TutorUiState(
    val messages: List<TutorMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    val history: List<TutorSessionDto> = emptyList(),
) {
    /** 可发送：输入非空白且当前没有进行中的流式回答。 */
    val canSend: Boolean get() = input.isNotBlank() && !sending
}
