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

/** 导师问答 MVI 状态；[history] 来自 `GET /api/tutor/sessions`。 */
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
