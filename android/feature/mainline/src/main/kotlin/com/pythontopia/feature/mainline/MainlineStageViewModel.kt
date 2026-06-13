package com.pythontopia.feature.mainline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythontopia.core.data.dto.mainline.StageDetailDto
import com.pythontopia.core.data.dto.mainline.StageProgressDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 主线小节（三件套）UI 状态。 */
data class MainlineStageUiState(
    val isLoading: Boolean = true,
    val detail: StageDetailDto? = null,
    val progress: StageProgressDto? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/** 小节学习页 ViewModel：拉取 stage 详情并对三件套打点（看/学/练）。 */
@HiltViewModel
class MainlineStageViewModel @Inject constructor(
    private val repository: MainlineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MainlineStageUiState())
    val state: StateFlow<MainlineStageUiState> = _state.asStateFlow()

    private var stageId: Long = 0L

    fun load(stageId: Long) {
        this.stageId = stageId
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.stageDetail(stageId) }
                .onSuccess { detail ->
                    _state.update { it.copy(isLoading = false, detail = detail, error = null) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.message ?: "小节详情加载失败")
                    }
                }
        }
    }

    /** 「看」打点：上报一段可见观看时长（秒）。 */
    fun reportVideo(deltaSec: Int) = withBusy {
        applyProgress(repository.reportVideoProgress(stageId, deltaSec), "已上报观看 ${deltaSec}s")
    }

    /** 「学」打点：把本小节关联的课件页全部标记已读。 */
    fun markAllPagesRead() = withBusy {
        val pageIds = _state.value.detail?.coursewarePageIds.orEmpty()
        if (pageIds.isEmpty()) {
            _state.update { it.copy(message = "本小节暂无课件页") }
        } else {
            applyProgress(repository.reportPagesRead(stageId, pageIds), "已标记 ${pageIds.size} 页课件已读")
        }
    }

    /** 「练」打点：例题完成（提交判题通过后由调用方带 submissionId 更可信）。 */
    fun markExampleDone(problemId: Long, submissionId: Long? = null) = withBusy {
        applyProgress(repository.reportExampleDone(stageId, problemId, submissionId), "已记录例题 #$problemId 完成")
    }

    private fun applyProgress(progress: StageProgressDto, message: String) {
        _state.update { it.copy(progress = progress, message = message) }
    }

    private fun withBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, error = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { throwable ->
                    _state.update { it.copy(error = throwable.message ?: "打点失败") }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}
