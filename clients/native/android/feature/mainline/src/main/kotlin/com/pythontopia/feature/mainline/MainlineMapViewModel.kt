package com.pythontopia.feature.mainline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythontopia.core.data.dto.mainline.ChapterOverviewDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 主线冒险地图 UI 状态。 */
data class MainlineMapUiState(
    val isLoading: Boolean = true,
    val totalStars: Int = 0,
    val chapters: List<ChapterOverviewDto> = emptyList(),
    val message: String? = null,
    val error: String? = null,
)

/** 冒险地图 ViewModel：拉取 `/api/mainline/overview`，只读渲染章节/小节进度与星级。 */
@HiltViewModel
class MainlineMapViewModel @Inject constructor(
    private val repository: MainlineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MainlineMapUiState())
    val state: StateFlow<MainlineMapUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.overview() }
                .onSuccess { overview ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            totalStars = overview.totalStars,
                            chapters = overview.chapters,
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "主线冒险地图加载失败，请确认已登录且后端可用",
                        )
                    }
                }
        }
    }

    /** 错题复盘补星；成功发放后刷新地图以更新星数。 */
    fun claimErrorbookRecovery(chapter: Int) {
        viewModelScope.launch {
            runCatching { repository.claimErrorbookRecovery(chapter) }
                .onSuccess { result ->
                    if (result.starAwarded) {
                        _state.update { it.copy(message = "第 $chapter 章错题复盘补星成功 +1★") }
                        load()
                    } else {
                        _state.update {
                            it.copy(message = "第 $chapter 章暂不可补星（需本章错题矿洞通关 ≥ 3 或已封顶）")
                        }
                    }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(message = throwable.message ?: "补星请求失败") }
                }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
