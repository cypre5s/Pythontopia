package com.pythontopia.feature.mainline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythontopia.core.data.dto.mainline.PracticeAnswerResultDto
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
    /** 「练」客观题已答对 id 集合（初始来自 detail.questionsCorrectIds，本端答对后并入）。 */
    val correctQuestionIds: Set<Long> = emptySet(),
    /** 各客观题最近一次判分结果（含正确答案 + 解析，供自检）。 */
    val questionResults: Map<Long, PracticeAnswerResultDto> = emptyMap(),
    /** 正在判分中的客观题 id（禁重复提交）。 */
    val answeringIds: Set<Long> = emptySet(),
    /** 「学」课件页（逐页阅读）。 */
    val coursePages: List<StageCoursePageDto> = emptyList(),
    /** 已读课件页 id 集合（初始来自 detail.pagesReadIds，逐页阅读后并入）。 */
    val readPageIds: Set<Long> = emptySet(),
    /** 当前展开阅读的课件页 id。 */
    val expandedPageId: Long? = null,
    /** 「看」首个课件视频的可播放地址（无视频则 null，UI 回退手动打点）。 */
    val videoUrl: String? = null,
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
                    _state.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            error = null,
                            correctQuestionIds = detail.questionsCorrectIds.toSet(),
                            readPageIds = detail.pagesReadIds.toSet(),
                            videoUrl = detail.videos.firstOrNull()?.id?.let { id -> repository.videoUrl(id) },
                        )
                    }
                    // 「学」逐页阅读：按本小节 KC 拉课件页（best-effort，失败不阻塞三件套其余）。
                    val pages = repository.coursePagesForKcs(detail.kcCodes)
                    _state.update { it.copy(coursePages = pages) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.message ?: "小节详情加载失败")
                    }
                }
        }
    }

    /** 「学」逐页阅读：展开某页即视为阅读并打点（幂等）；再点收起。 */
    fun togglePage(pageId: Long) {
        val collapsing = _state.value.expandedPageId == pageId
        _state.update { it.copy(expandedPageId = if (collapsing) null else pageId) }
        if (collapsing || pageId in _state.value.readPageIds) return
        viewModelScope.launch {
            runCatching { repository.reportPageRead(stageId, pageId) }
                .onSuccess { progress ->
                    _state.update { it.copy(progress = progress, readPageIds = it.readPageIds + pageId) }
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

    /**
     * 「练」客观题作答：服务端判分（不泄题）。答对则并入 correctQuestionIds 并锁题；
     * 无论对错都回传 correctAnswers + explanation 供自检。判分失败静默（可改答重试，幂等）。
     */
    fun answerQuestion(questionId: Long, answers: List<String>) {
        val s = _state.value
        if (questionId in s.correctQuestionIds || questionId in s.answeringIds) return
        _state.update { it.copy(answeringIds = it.answeringIds + questionId) }
        viewModelScope.launch {
            runCatching { repository.answerPracticeQuestion(stageId, questionId, answers) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            answeringIds = it.answeringIds - questionId,
                            questionResults = it.questionResults + (questionId to result),
                            correctQuestionIds = if (result.correct) it.correctQuestionIds + questionId else it.correctQuestionIds,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(answeringIds = it.answeringIds - questionId) }
                }
        }
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
