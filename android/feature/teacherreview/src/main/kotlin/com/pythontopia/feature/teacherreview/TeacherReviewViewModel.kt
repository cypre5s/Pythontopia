package com.pythontopia.feature.teacherreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 教师审题：选章/阶段 → 调 graph 出 1 道 AI/golden 题 → 看 5 重审查状态 → 本地「上线/驳回」。
 * 审核决定为会话本地（与 Web localStorage 兜底一致，后端无审核决定持久化端点）。
 */
@HiltViewModel
class TeacherReviewViewModel @Inject constructor(
    private val repository: TeacherReviewRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TeacherReviewUiState())
    val state: StateFlow<TeacherReviewUiState> = _state.asStateFlow()

    fun setChapter(chapter: Int) = _state.update { it.copy(chapter = chapter) }

    fun setStage(stage: Int) = _state.update { it.copy(stage = stage) }

    fun fetch() {
        val s = _state.value
        if (s.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.fetchProblem(s.chapter, s.stage)
                .onSuccess { problem ->
                    val entry = ReviewEntry(problem = problem, fetchedAtMs = System.currentTimeMillis())
                    _state.update {
                        it.copy(entries = listOf(entry) + it.entries, selectedKey = entry.key, loading = false)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = "拉题失败：${e.message ?: "graph 不可用"}")
                    }
                }
        }
    }

    fun select(key: String) = _state.update { it.copy(selectedKey = key) }

    fun approve(key: String) = decide(key, ReviewDecision.APPROVED, null)

    fun reject(key: String, reason: String) =
        decide(key, ReviewDecision.REJECTED, reason.trim().ifBlank { "未提供原因" })

    fun clearAll() = _state.update { it.copy(entries = emptyList(), selectedKey = null) }

    private fun decide(key: String, decision: ReviewDecision, reason: String?) {
        _state.update { s ->
            s.copy(
                entries = s.entries.map {
                    if (it.key == key) it.copy(decision = decision, reason = reason) else it
                },
            )
        }
    }
}
