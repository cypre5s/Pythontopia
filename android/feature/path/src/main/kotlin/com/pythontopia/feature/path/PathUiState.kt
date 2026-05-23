package com.pythontopia.feature.path

/**
 * 学习路径 UI 状态。
 *
 * MVVM 单一 [PathUiState] 持有：
 *  - 网络拉取过程（[isLoading]、[error]）
 *  - 路径数据（[pathId]、[nodes]、[currentNodeId]）
 *  - 用户筛选偏好（[statusFilter]、[typeFilter]）
 *  - 节点动作 inflight 标记（[advancingNodeId]）：避免重复点
 *
 * [filteredNodes] 由 [nodes] / [statusFilter] / [typeFilter] 派生，避免在 Composable 内重复算。
 * 节点顺序遵循后端返回顺序；前端不参与拓扑排序，由 backend `PathPlannerAgent` 保证。
 */
data class PathUiState(
    val isLoading: Boolean = false,
    val pathId: String? = null,
    val nodes: List<PathNodeDto> = emptyList(),
    val currentNodeId: String? = null,
    val statusFilter: Set<PathNodeStatus> = emptySet(),
    val typeFilter: Set<PathNodeType> = emptySet(),
    val advancingNodeId: String? = null,
    val error: String? = null,
) {

    val filteredNodes: List<PathNodeDto>
        get() = nodes.filter { node ->
            (statusFilter.isEmpty() || node.status in statusFilter) &&
                (typeFilter.isEmpty() || node.type in typeFilter)
        }

    val isEmpty: Boolean
        get() = !isLoading && nodes.isEmpty() && error == null
}
