package com.pythontopia.feature.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 资源生成 UI 状态：表单（类型/KC/重点）+ 流式过程（plan/trace）+ 终态（result/error）。 */
data class WorkshopGenUiState(
    val type: WorkshopResourceType = WorkshopResourceType.HANDOUT,
    val kcInput: String = "",
    val focusHint: String = "",
    val streaming: Boolean = false,
    val plan: List<String> = emptyList(),
    val traces: List<String> = emptyList(),
    val result: WorkshopGenEvent.Result? = null,
    val error: String? = null,
    val detailLoading: Boolean = false,
    val detail: WorkshopResourceDetailDto? = null,
    val detailContent: WorkshopContentView? = null,
) {
    val kcCodes: List<String>
        get() = kcInput.split(',', '，', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    val canGenerate: Boolean get() = kcCodes.isNotEmpty() && !streaming
}

/** 资源生成 ViewModel：消费工坊 SSE 流，把 plan/trace 累积到状态、result 收尾。 */
@HiltViewModel
class WorkshopGenViewModel @Inject constructor(
    private val repository: WorkshopGenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkshopGenUiState())
    val state: StateFlow<WorkshopGenUiState> = _state.asStateFlow()

    /** 由意图路由预填类型与 KC（不自动开跑，让学生确认重点再生成）。 */
    fun prefill(type: WorkshopResourceType?, kcCodes: List<String>) {
        _state.update {
            it.copy(
                type = type ?: it.type,
                kcInput = if (kcCodes.isNotEmpty()) kcCodes.joinToString(", ") else it.kcInput,
            )
        }
    }

    fun setType(type: WorkshopResourceType) = _state.update { it.copy(type = type) }
    fun setKcInput(value: String) = _state.update { it.copy(kcInput = value) }
    fun setFocusHint(value: String) = _state.update { it.copy(focusHint = value) }

    fun generate() {
        val current = _state.value
        if (!current.canGenerate) return
        _state.update {
            it.copy(
                streaming = true,
                plan = emptyList(),
                traces = emptyList(),
                result = null,
                error = null,
                detail = null,
                detailContent = null,
            )
        }
        viewModelScope.launch {
            repository.generateStream(current.type, current.kcCodes, current.focusHint)
                .catch { throwable ->
                    _state.update { it.copy(error = throwable.message ?: "资源生成连接失败") }
                }
                .onCompletion { _state.update { it.copy(streaming = false) } }
                .collect { event -> reduce(event) }
        }
    }

    /** 打开刚生成的资源详情并按类型解析 content。 */
    fun openResource() {
        val id = _state.value.result?.resourceId ?: return
        if (_state.value.detailLoading) return
        _state.update { it.copy(detailLoading = true, error = null) }
        viewModelScope.launch {
            repository.findResource(id)
                .onSuccess { d ->
                    _state.update {
                        it.copy(
                            detailLoading = false,
                            detail = d,
                            detailContent = repository.parseContent(d.type, d.content),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(detailLoading = false, error = "打开生成内容失败：${e.message ?: "网络异常"}") }
                }
        }
    }

    fun closeDetail() {
        _state.update { it.copy(detail = null, detailContent = null) }
    }

    private fun reduce(event: WorkshopGenEvent) {
        _state.update { state ->
            when (event) {
                is WorkshopGenEvent.Plan -> state.copy(
                    plan = event.steps.ifEmpty { listOfNotNull(event.message) },
                )
                is WorkshopGenEvent.Trace -> state.copy(traces = state.traces + event.label)
                is WorkshopGenEvent.Result -> state.copy(result = event)
                is WorkshopGenEvent.Failed -> state.copy(error = event.message)
            }
        }
    }
}
