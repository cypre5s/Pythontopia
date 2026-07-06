package com.pythontopia.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythontopia.core.data.dto.learner.QuestDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 公告板：今日任务列表 + 开始/完成/跳过。完成强校验由后端执行，端侧不伪造证据。 */
@HiltViewModel
class BoardViewModel @Inject constructor(
    private val repository: BoardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BoardUiState())
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.today()
                .onSuccess { quests -> _state.update { it.copy(quests = quests, loading = false) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = "公告板加载失败：${e.message ?: "服务不可用"}")
                    }
                }
        }
    }

    fun start(id: Long) = runAction(id) { repository.start(id) }

    fun complete(id: Long, note: String? = null) = runAction(id) { repository.complete(id, note) }

    fun skip(id: Long, reason: String? = null) = runAction(id) { repository.skip(id, reason) }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    private fun runAction(id: Long, block: suspend () -> Result<QuestDto>) {
        if (_state.value.actionInFlightId != null) return
        _state.update { it.copy(actionInFlightId = id, error = null, notice = null) }
        viewModelScope.launch {
            block()
                .onSuccess { updated ->
                    _state.update { s ->
                        s.copy(
                            quests = s.quests.map { if (it.id == updated.id) updated else it },
                            actionInFlightId = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(actionInFlightId = null, notice = "操作未成功：${e.message ?: "请稍后再试"}")
                    }
                }
        }
    }
}
