package com.pythontopia.feature.path

/** 学习路径 UI 状态；[filteredNodes] 由筛选集派生，节点顺序由后端 `PathPlannerAgent` 保证。 */
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
