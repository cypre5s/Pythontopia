package com.pythontopia.feature.path

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 状态/类型多选筛选；空集合视为全选，与 [PathUiState.filteredNodes] 一致。横向滚动因 FlowRow 尚未稳定。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathFilters(
    statusFilter: Set<PathNodeStatus>,
    typeFilter: Set<PathNodeType>,
    onToggleStatus: (PathNodeStatus) -> Unit,
    onToggleType: (PathNodeType) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChipRow(label = "状态") {
            PathNodeStatus.entries.forEach { status ->
                FilterChip(
                    selected = status in statusFilter,
                    onClick = { onToggleStatus(status) },
                    label = { Text(status.labelZh()) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
            if (statusFilter.isNotEmpty() || typeFilter.isNotEmpty()) {
                FilterChip(
                    selected = false,
                    onClick = onClearAll,
                    label = { Text("清除") },
                )
            }
        }
        FilterChipRow(label = "类型") {
            PathNodeType.entries.forEach { type ->
                FilterChip(
                    selected = type in typeFilter,
                    onClick = { onToggleType(type) },
                    label = { Text(type.labelZh()) },
                )
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

/** 状态中文标签。 */
internal fun PathNodeStatus.labelZh(): String = when (this) {
    PathNodeStatus.LOCKED -> "未解锁"
    PathNodeStatus.UNLOCKED -> "待开始"
    PathNodeStatus.IN_PROGRESS -> "进行中"
    PathNodeStatus.DONE -> "已完成"
}

/** 类型中文标签。 */
internal fun PathNodeType.labelZh(): String = when (this) {
    PathNodeType.READ -> "阅读"
    PathNodeType.PRACTICE -> "练习"
    PathNodeType.QUEST -> "任务"
    PathNodeType.DEBUG -> "调试"
}
