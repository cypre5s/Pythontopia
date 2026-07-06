package com.pythontopia.feature.retro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 复盘大厅 ViewModel；`load` 串联本周与历史，`selectWeek` 本地切换，`markRead` 失败静默。 */
@HiltViewModel
class RetroViewModel @Inject constructor(
    private val repository: RetroRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RetroUiState())
    val state: StateFlow<RetroUiState> = _state.asStateFlow()

    fun load(userId: String, pastWeeks: Int = DEFAULT_PAST_WEEKS) {
        require(userId.isNotBlank()) { "userId must not be blank" }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val current = repository.thisWeek(userId)
                val past = if (pastWeeks > 0) {
                    repository.pastWeeks(userId, pastWeeks, baseWeekIndex = current.weekIndex)
                } else {
                    emptyList()
                }
                current to past
            }.onSuccess { (current, past) ->
                _state.update {
                    it.copy(
                        loading = false,
                        currentReport = current,
                        pastReports = past,
                        thisWeekIndex = current.weekIndex,
                        error = null,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "加载复盘报告失败",
                    )
                }
            }
        }
    }

    /** 在已加载报告池内切换周视图，不发起网络；未加载的 weekIndex 直接返回。 */
    fun selectWeek(weekIndex: Long) {
        val current = _state.value.currentReport
        if (current?.weekIndex == weekIndex) return
        val pool = buildList {
            current?.let(::add)
            addAll(_state.value.pastReports)
        }
        val picked = pool.firstOrNull { it.weekIndex == weekIndex } ?: return
        val rebuiltPast = pool.filter { it.weekIndex != weekIndex }
            .sortedByDescending { it.weekIndex }
        _state.update {
            it.copy(currentReport = picked, pastReports = rebuiltPast)
        }
    }

    fun markRead(reportId: String) {
        viewModelScope.launch {
            runCatching { repository.markRead(reportId) }
        }
    }

    companion object {
        const val DEFAULT_PAST_WEEKS: Int = 3
    }
}
