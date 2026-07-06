package com.pythontopia.feature.problems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 题库浏览：拉题目 + KC + 提交进度，本地过滤/排序。题面随行展开，不跳 arena。 */
@HiltViewModel
class ProblemsViewModel @Inject constructor(
    private val repository: ProblemsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProblemsUiState())
    val state: StateFlow<ProblemsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.load()
                .onSuccess { rows ->
                    _state.update { it.copy(all = rows, loading = false) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = "题库加载失败：${e.message ?: "服务不可用"}")
                    }
                }
        }
    }

    fun setSearch(value: String) = _state.update { it.copy(search = value) }

    fun setDifficulty(value: ProblemDifficulty?) = _state.update { it.copy(difficulty = value) }

    fun setStatus(value: ProblemStatus?) = _state.update { it.copy(status = value) }

    fun setSort(value: ProblemSort) = _state.update { it.copy(sort = value) }

    fun setKc(value: String?) = _state.update { it.copy(kcCode = value) }

    fun clearFilters() = _state.update {
        it.copy(search = "", difficulty = null, status = null, kcCode = null)
    }
}
