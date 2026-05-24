package com.pythontopia.feature.arena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChapterTowerViewModel @Inject constructor(
    private val repository: ArenaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChapterTowerUiState())
    val state: StateFlow<ChapterTowerUiState> = _state.asStateFlow()

    init {
        loadChapter(1)
    }

    fun selectChapter(chapter: Int) {
        if (chapter !in 1..7) return
        _state.update { it.copy(selectedChapter = chapter, selectedLevelId = null) }
        loadChapter(chapter)
    }

    fun selectLevel(levelId: String) {
        val level = _state.value.levels.find { it.id == levelId } ?: return
        if (!level.unlocked) return
        _state.update { it.copy(selectedLevelId = levelId) }
    }

    fun markCleared(levelId: String) {
        _state.update { state ->
            val updated = state.levels.map {
                if (it.id == levelId) it.copy(cleared = true) else it
            }
            val clearedCount = updated.count { it.cleared }
            state.copy(levels = updated, chapterCleared = clearedCount)
        }
    }

    private fun loadChapter(chapter: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val levels = buildLevelsForChapter(chapter)
                _state.update {
                    it.copy(
                        levels = levels,
                        chapterCleared = levels.count { l -> l.cleared },
                        chapterTotal = levels.size,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "加载失败",
                    )
                }
            }
        }
    }

    private fun buildLevelsForChapter(chapter: Int): List<ChapterTowerLevel> {
        val kinds = listOf(
            "pdf_question", "fill_blank", "debug", "short_answer",
            "closed_loop", "dual_track", "multi_round", "teach_back", "bug_mine",
        )
        return (1..10).map { index ->
            val kind = if (index == 10) "boss" else kinds[(index - 1) % kinds.size]
            val unlocked = index == 1
            ChapterTowerLevel(
                id = "$chapter-$index",
                chapter = chapter,
                index = index,
                kind = kind,
                title = if (index == 10) "第${chapter}章 BOSS" else "第${index}关 · ${kindLabel(kind)}",
                hint = "完成本关解锁下一关",
                kcCodes = listOf("PY43-KC-${String.format("%03d", (chapter - 1) * 9 + index)}"),
                difficulty = minOf(index / 2 + 1, 5),
                cleared = false,
                unlocked = unlocked,
            )
        }
    }

    private fun kindLabel(kind: String): String = when (kind) {
        "pdf_question" -> "课件问答"
        "fill_blank" -> "代码填空"
        "debug" -> "改错题"
        "short_answer" -> "简答"
        "closed_loop" -> "调试闭环"
        "dual_track" -> "双轨判题"
        "multi_round" -> "3题组合"
        "teach_back" -> "教学反转"
        "bug_mine" -> "Bug矿洞"
        "boss" -> "章末BOSS"
        else -> kind
    }
}
