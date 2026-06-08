package com.pythontopia.feature.retro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

private const val DEFAULT_USER_ID: String = "demo-student"

/** 复盘大厅入口；`userId` 默认 demo 占位，应由 NavGraph 注入真实会话用户。 */
@Composable
fun RetroScreen(
    userId: String = DEFAULT_USER_ID,
    viewModel: RetroViewModel = hiltViewModel(),
    onResourceClick: ((ResourceDto) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(userId) {
        if (state.currentReport == null && !state.loading) {
            viewModel.load(userId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PytSpace.s5),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        HeaderBlock()
        WeekChipRow(
            currentReport = state.currentReport,
            pastReports = state.pastReports,
            thisWeekIndex = state.thisWeekIndex,
            onPick = viewModel::selectWeek,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.loading && state.currentReport == null -> LoadingBlock()
                state.error != null -> ErrorBlock(message = state.error!!)
                state.currentReport == null -> EmptyBlock()
                else -> ReportContent(
                    report = state.currentReport!!,
                    onResourceClick = { res ->
                        viewModel.markRead(state.currentReport!!.reportId)
                        onResourceClick?.invoke(res)
                    },
                )
            }
        }
    }
}

@Composable
private fun HeaderBlock() {
    Column {
        Text(
            text = "复盘大厅",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "回看本周的浇水与除草，挑出下一周该补的 KC。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        )
    }
}

@Composable
private fun WeekChipRow(
    currentReport: RetroReport?,
    pastReports: List<RetroReport>,
    thisWeekIndex: Long?,
    onPick: (Long) -> Unit,
) {
    val all = buildList {
        currentReport?.let(::add)
        addAll(pastReports)
    }.distinctBy { it.weekIndex }.sortedByDescending { it.weekIndex }

    if (all.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
        contentPadding = PaddingValues(vertical = PytSpace.s1),
    ) {
        items(items = all, key = { it.weekIndex }) { report ->
            val isSelected = currentReport?.weekIndex == report.weekIndex
            val isThisWeek = thisWeekIndex == report.weekIndex
            FilterChip(
                selected = isSelected,
                onClick = { onPick(report.weekIndex) },
                label = {
                    Text(
                        text = if (isThisWeek) "本周 · W${report.weekIndex}" else "W${report.weekIndex}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PytColors.primary,
                    selectedLabelColor = PytColors.onPrimary,
                    containerColor = PytColors.bgElevated,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) PytColors.borderStrong else PytColors.borderSoft,
                ),
            )
        }
    }
}

@Composable
private fun ReportContent(
    report: RetroReport,
    onResourceClick: (ResourceDto) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
        contentPadding = PaddingValues(bottom = PytSpace.s5),
    ) {
        item(key = report.reportId) {
            WeeklyReportCard(
                report = report,
                onResourceClick = onResourceClick,
            )
        }
    }
}

@Composable
private fun LoadingBlock() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s5),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = PytColors.primary)
        Text(
            text = "  正在加载本周复盘…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ErrorBlock(message: String) {
    Text(
        text = "加载失败：$message",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = PytSpace.s5),
    )
}

@Composable
private fun EmptyBlock() {
    Text(
        text = "本周还没生成复盘报告。先去农场种下第一颗 KC 再回来吧。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        modifier = Modifier.padding(top = PytSpace.s5),
    )
}
