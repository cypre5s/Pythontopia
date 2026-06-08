package com.pythontopia.feature.path

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.design.PytSpace

/**
 * 学习路径主屏。
 *
 * @param userId 测试/注入用；真实请求身份来自 JWT，不随请求体上传。
 * @param onOpenNodeTarget 节点点击后由 host nav graph 按 [PathNodeDto.type] 跳转对应 feature。
 */
@Composable
fun PathScreen(
    userId: Long = CURRENT_USER_SENTINEL_ID,
    onOpenNodeTarget: (PathNodeDto) -> Unit = {},
    viewModel: PathViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userId) {
        viewModel.load(userId)
    }

    LaunchedEffect(state.error) {
        val msg = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeError()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Header(
                pathId = state.pathId,
                onRefresh = { viewModel.load(userId) },
            )
            when {
                state.isLoading && state.nodes.isEmpty() -> LoadingBlock()
                state.isEmpty -> EmptyBlock(onRefresh = { viewModel.load(userId) })
                else -> Content(
                    state = state,
                    onClickNode = { node ->
                        viewModel.advanceNode(node)
                        onOpenNodeTarget(node)
                    },
                    onToggleStatus = viewModel::toggleStatusFilter,
                    onToggleType = viewModel::toggleTypeFilter,
                    onClearFilters = viewModel::clearFilters,
                )
            }
        }
    }
}

@Composable
private fun Header(pathId: String?, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PytSpace.s4, vertical = PytSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "学习路径",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = pathId?.let { "Path #$it" } ?: "尚未规划",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        TextButton(onClick = onRefresh) { Text("刷新") }
    }
}

@Composable
private fun Content(
    state: PathUiState,
    onClickNode: (PathNodeDto) -> Unit,
    onToggleStatus: (PathNodeStatus) -> Unit,
    onToggleType: (PathNodeType) -> Unit,
    onClearFilters: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        PathGraphView(
            nodes = state.nodes,
            currentNodeId = state.currentNodeId,
            modifier = Modifier.padding(horizontal = PytSpace.s4),
        )
        PathFilters(
            statusFilter = state.statusFilter,
            typeFilter = state.typeFilter,
            onToggleStatus = onToggleStatus,
            onToggleType = onToggleType,
            onClearAll = onClearFilters,
        )
        Text(
            text = "节点 ${state.filteredNodes.size}/${state.nodes.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = PytSpace.s4),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = PytSpace.s4, vertical = PytSpace.s2),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            items(
                items = state.filteredNodes,
                key = { it.id },
            ) { node ->
                PathNodeCard(
                    node = node,
                    isCurrent = node.id == state.currentNodeId,
                    isAdvancing = state.advancingNodeId == node.id,
                    onClick = onClickNode,
                )
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(PytSpace.s2))
            Text("正在拉取学习路径…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyBlock(onRefresh: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
        ) {
            Text(
                text = "你还没有学习路径",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "进入小镇与镇长 Py 对话即可触发首次规划",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Button(onClick = onRefresh) { Text("重新拉取") }
        }
    }
}

/** Compose key 用哨兵值；后端身份来自 JWT 当前用户。 */
const val CURRENT_USER_SENTINEL_ID: Long = -1L
