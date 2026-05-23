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
 * 布局（从上到下）：
 *  - Header：标题 + 当前路径 id
 *  - [PathGraphView]：纵向链式可视化
 *  - [PathFilters]：状态 / 类型多选 chip
 *  - LazyColumn of [PathNodeCard]：详细节点列表，可点
 *  - Snackbar：错误提示，可手动 dismiss
 *
 * 跳转策略：节点点击优先 advance（UNLOCKED -> IN_PROGRESS 或 IN_PROGRESS -> DONE），
 * 顺带通过 [onOpenNodeTarget] 通知 host nav graph 跳到对应 feature（READ/PRACTICE/QUEST/DEBUG）。
 *
 * @param userId        当前学习者 ID。host 通常从 SessionRepository 注入；
 *                      为了 Beta+1 演示，未登录时传 [DEMO_USER_ID]，仓库会 fallback 到 demo 路径。
 * @param onOpenNodeTarget 节点点击副作用回调；feature/path 本身不持有 NavController，
 *                      由 :app navGraph 决定跳到 :feature:workshop / :feature:arena 等。
 */
@Composable
fun PathScreen(
    userId: Long = DEMO_USER_ID,
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

/** 未登录场景下传给 [PathScreen] 的占位 userId；仓库会用 fallback 返回 demo 路径。 */
const val DEMO_USER_ID: Long = -1L
