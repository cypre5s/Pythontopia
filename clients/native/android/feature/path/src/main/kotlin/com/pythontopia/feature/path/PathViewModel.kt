package com.pythontopia.feature.path

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 学习路径 ViewModel；[PathUiState.advancingNodeId] 保证同一时刻仅一个节点 inflight。 */
@HiltViewModel
class PathViewModel @Inject constructor(
    private val repository: PathRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PathUiState())
    val state: StateFlow<PathUiState> = _state.asStateFlow()

    /** 拉取活跃路径；[packId] 默认 pack 43（`Python语言基础`）。 */
    fun load(userId: Long, packId: Long = PathRepository.DEFAULT_PACK_ID) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val path = runCatching { repository.fetchPath(userId, packId) }
                .getOrElse { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "学习路径拉取失败",
                        )
                    }
                    return@launch
                }
            _state.update {
                it.copy(
                    isLoading = false,
                    pathId = path.pathId,
                    nodes = path.nodes,
                    currentNodeId = path.currentNodeId,
                    error = null,
                )
            }
        }
    }

    fun toggleStatusFilter(status: PathNodeStatus) {
        _state.update {
            val next = if (status in it.statusFilter) it.statusFilter - status else it.statusFilter + status
            it.copy(statusFilter = next)
        }
    }

    fun toggleTypeFilter(type: PathNodeType) {
        _state.update {
            val next = if (type in it.typeFilter) it.typeFilter - type else it.typeFilter + type
            it.copy(typeFilter = next)
        }
    }

    fun clearFilters() {
        _state.update { it.copy(statusFilter = emptySet(), typeFilter = emptySet()) }
    }

    /** UNLOCKED→开始、IN_PROGRESS→完成；LOCKED/DONE 与 inflight 中重复点击为 no-op。 */
    fun advanceNode(node: PathNodeDto) {
        val pathId = _state.value.pathId ?: return
        if (_state.value.advancingNodeId != null) return
        when (node.status) {
            PathNodeStatus.UNLOCKED -> startNode(pathId, node.id)
            PathNodeStatus.IN_PROGRESS -> completeNode(pathId, node.id)
            else -> Unit
        }
    }

    private fun startNode(pathId: String, nodeId: String) {
        _state.update { it.copy(advancingNodeId = nodeId, error = null) }
        viewModelScope.launch {
            val updated = runCatching { repository.markNodeStarted(pathId, nodeId) }
                .getOrElse { throwable ->
                    _state.update {
                        it.copy(advancingNodeId = null, error = throwable.message ?: "节点启动失败")
                    }
                    return@launch
                }
            _state.update {
                it.copy(
                    advancingNodeId = null,
                    pathId = updated.pathId,
                    nodes = updated.nodes,
                    currentNodeId = updated.currentNodeId,
                )
            }
        }
    }

    private fun completeNode(pathId: String, nodeId: String) {
        _state.update { it.copy(advancingNodeId = nodeId, error = null) }
        viewModelScope.launch {
            val updated = runCatching { repository.markNodeDone(pathId, nodeId) }
                .getOrElse { throwable ->
                    _state.update {
                        it.copy(advancingNodeId = null, error = throwable.message ?: "节点完成失败")
                    }
                    return@launch
                }
            _state.update {
                it.copy(
                    advancingNodeId = null,
                    pathId = updated.pathId,
                    nodes = updated.nodes,
                    currentNodeId = updated.currentNodeId,
                )
            }
        }
    }

    /** 清除 [PathUiState.error]。 */
    fun consumeError() {
        _state.update { it.copy(error = null) }
    }
}
