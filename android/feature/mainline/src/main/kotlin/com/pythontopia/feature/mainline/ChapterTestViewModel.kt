package com.pythontopia.feature.mainline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythontopia.core.data.dto.mainline.ChapterTestPaperDto
import com.pythontopia.core.data.dto.mainline.ChapterTestResultDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 章末测试 UI 状态：开考题面 / 作答 / 交卷结果。 */
data class ChapterTestUiState(
    val isLoading: Boolean = true,
    val chapter: Int = 0,
    val paper: ChapterTestPaperDto? = null,
    val answers: Map<String, String> = emptyMap(),
    val submitting: Boolean = false,
    val result: ChapterTestResultDto? = null,
    val error: String? = null,
)

/**
 * 章末测试 ViewModel：prepare（触发预生成）→ start（去答案发卷）→ 本地作答 → submit（服务端判分）。
 * 答案与判分全在后端；客户端不持有正确答案，仅在结果里展示。
 */
@HiltViewModel
class ChapterTestViewModel @Inject constructor(
    private val repository: MainlineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChapterTestUiState())
    val state: StateFlow<ChapterTestUiState> = _state.asStateFlow()

    fun load(chapter: Int) {
        _state.update {
            it.copy(isLoading = true, chapter = chapter, paper = null, result = null, answers = emptyMap(), error = null)
        }
        viewModelScope.launch {
            runCatching {
                runCatching { repository.prepareTest(chapter) } // 触发异步预生成，忽略其返回/失败
                repository.startTest(chapter)
            }.onSuccess { paper ->
                _state.update { it.copy(isLoading = false, paper = paper, error = null) }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(isLoading = false, error = throwable.message ?: "开考失败（题目可能仍在预生成，稍后重试）")
                }
            }
        }
    }

    fun setAnswer(qid: String, value: String) {
        _state.update { it.copy(answers = it.answers + (qid to value)) }
    }

    fun submit() {
        val paper = _state.value.paper ?: return
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.submitTest(_state.value.chapter, paper.testId, _state.value.answers) }
                .onSuccess { result -> _state.update { it.copy(submitting = false, result = result) } }
                .onFailure { throwable ->
                    _state.update { it.copy(submitting = false, error = throwable.message ?: "交卷失败") }
                }
        }
    }
}
