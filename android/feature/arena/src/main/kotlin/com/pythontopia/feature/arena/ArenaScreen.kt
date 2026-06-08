package com.pythontopia.feature.arena

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 训练场入口：头部 + TabRow，底部按 [ArenaUiState.selectedTab] 切换 ProblemRunner / LeaderboardView。 */
@Composable
fun ArenaScreen(viewModel: ArenaViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ArenaHeader(state = state, onRefresh = viewModel::refreshSession)
        Spacer(modifier = Modifier.height(12.dp))
        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            ArenaTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab.label) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        state.errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (state.selectedTab) {
                ArenaTab.TODAY -> ProblemRunner(
                    state = state,
                    onSelectProblem = viewModel::selectProblem,
                    onCodeChange = viewModel::updateCode,
                    onSubmit = viewModel::submitCurrent,
                )
                ArenaTab.LEADERBOARD -> LeaderboardView(
                    state = state,
                    onRefresh = viewModel::refreshLeaderboard,
                )
            }
        }
    }
}

@Composable
private fun ArenaHeader(state: ArenaUiState, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.session?.title ?: "训练场",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = when {
                    state.isLoadingSession -> "正在加载今日赛事…"
                    state.session == null -> "暂无可参加的赛事"
                    else -> "题目共 ${state.session.problems.size} 道"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        state.session?.let { session ->
            CountdownTimer(
                startEpochMs = session.startEpochMs,
                endEpochMs = session.endEpochMs,
            )
        }
        TextButton(onClick = onRefresh) { Text("刷新") }
    }
}
