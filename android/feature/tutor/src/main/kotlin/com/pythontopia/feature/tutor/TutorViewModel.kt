package com.pythontopia.feature.tutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 发问后追加流式助手气泡，增量回填 token，终态写入 grounding/证据页并刷新历史；失败写入 [TutorUiState.error]。 */
@HiltViewModel
class TutorViewModel @Inject constructor(
    private val repository: TutorRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TutorUiState())
    val state: StateFlow<TutorUiState> = _state.asStateFlow()

    init {
        loadHistory()
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
    }

    fun ask() {
        val question = _state.value.input.trim()
        if (question.isEmpty() || _state.value.sending) return
        _state.update {
            it.copy(
                input = "",
                sending = true,
                error = null,
                messages = it.messages +
                    TutorMessage(TutorMessage.Role.USER, question) +
                    TutorMessage(TutorMessage.Role.ASSISTANT, "", streaming = true),
            )
        }
        viewModelScope.launch {
            try {
                repository.askStream(question).collect(::applyEvent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "导师服务连接失败") }
            } finally {
                _state.update { it.copy(sending = false, messages = finishStreaming(it.messages)) }
            }
            loadHistory()
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            val sessions = repository.sessions()
            _state.update { it.copy(history = sessions) }
        }
    }

    private fun applyEvent(event: TutorStreamEvent) {
        _state.update { st ->
            when (event) {
                is TutorStreamEvent.Token ->
                    st.copy(messages = mapLastAssistant(st.messages) { it.copy(text = it.text + event.delta) })
                is TutorStreamEvent.Result ->
                    st.copy(
                        messages = mapLastAssistant(st.messages) {
                            it.copy(
                                text = event.answerMd.ifBlank { it.text },
                                evidencePageIds = event.evidencePageIds,
                                groundingStatus = event.groundingStatus,
                                streaming = false,
                            )
                        },
                    )
                is TutorStreamEvent.Failed -> st.copy(error = event.message)
            }
        }
    }

    private fun mapLastAssistant(
        messages: List<TutorMessage>,
        transform: (TutorMessage) -> TutorMessage,
    ): List<TutorMessage> {
        val idx = messages.indexOfLast { it.role == TutorMessage.Role.ASSISTANT }
        if (idx < 0) return messages
        return messages.toMutableList().also { it[idx] = transform(it[idx]) }
    }

    private fun finishStreaming(messages: List<TutorMessage>): List<TutorMessage> =
        mapLastAssistant(messages) { msg ->
            if (!msg.streaming) {
                msg
            } else {
                msg.copy(
                    text = msg.text.ifBlank { "（导师未返回内容，请重试）" },
                    streaming = false,
                )
            }
        }
}
