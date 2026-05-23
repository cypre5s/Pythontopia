package com.pythontopia.feature.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * Workshop（代码工坊）主屏。
 *
 * 布局（任务 5：3 段竖向布局，对应工坊「题目 / 编辑 / 状态」三步流程）：
 *  - 顶部 [ProblemHeader]：题面摘要 + 「选题」按钮 + 难度 chip
 *  - 中部 [CodeEditor]：等宽多行 TextField，weight(1f) 撑满
 *  - 底部 [SubmissionResultPanel]：状态 chip + traceback + 通过用例 + 历史 chip
 *
 * 所有状态由 [WorkshopViewModel] 单向写入；本 Composable 只 collect + 转发事件，
 * 不持有任何本地 [androidx.compose.runtime.mutableStateOf]。
 */
@Composable
fun WorkshopScreen(
    viewModel: WorkshopViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state.history.isEmpty()) {
            viewModel.loadHistory()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PytSpace.s4),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
        ) {
            ProblemHeader(
                state = state,
                onPickProblem = viewModel::openPicker,
            )

            CodeEditor(
                code = state.userCode,
                onCodeChange = viewModel::updateCode,
                enabled = !state.isSubmitting && !state.isPolling,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            SubmitActionRow(
                canSubmit = state.canSubmit,
                isSubmitting = state.isSubmitting,
                isPolling = state.isPolling,
                onSubmit = viewModel::submit,
            )

            SubmissionResultPanel(
                submission = state.latestStatus,
                isSubmitting = state.isSubmitting,
                isPolling = state.isPolling,
                errorMessage = state.errorMessage,
                modifier = Modifier.fillMaxWidth(),
            )

            HistoryStrip(history = state.history)
        }
    }

    ProblemPicker(
        isOpen = state.isPickerOpen,
        kcCode = state.kcCodeFilter,
        onKcChange = viewModel::changeKcFilter,
        problems = state.problems,
        isLoading = state.isLoadingProblems,
        onSelect = viewModel::selectProblem,
        onDismiss = viewModel::dismissPicker,
    )
}

@Composable
private fun ProblemHeader(
    state: WorkshopUiState,
    onPickProblem: () -> Unit,
) {
    val problem = state.currentProblem
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PytColors.bgElevated, RoundedCornerShape(PytSpace.s2))
            .border(1.dp, PytColors.borderSoft, RoundedCornerShape(PytSpace.s2))
            .padding(PytSpace.s3),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = problem?.title ?: "代码工坊（CodeLab）",
                    color = PytColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = problem?.let { "${it.kcCode} · 难度 ${it.difficulty ?: "未知"}" }
                        ?: "尚未选题，点右侧按钮挑一道 ${state.kcCodeFilter} 的题",
                    color = PytColors.textHint,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedButton(onClick = onPickProblem) {
                Text(if (problem == null) "选题" else "换题")
            }
        }
        if (problem != null) {
            val scroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .verticalScroll(scroll),
            ) {
                Text(
                    text = problem.statementMarkdown,
                    color = PytColors.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (problem.samples.isNotEmpty()) {
                SamplesStrip(samples = problem.samples)
            }
        }
    }
}

@Composable
private fun SamplesStrip(samples: List<ProblemSampleDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
        samples.take(MAX_SAMPLES).forEachIndexed { index, sample ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PytColors.bgSoft, RoundedCornerShape(PytSpace.s1))
                    .padding(horizontal = PytSpace.s2, vertical = PytSpace.s1),
                horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
            ) {
                Text(
                    text = "样例${index + 1}",
                    color = PytColors.textHint,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "in: ${sample.input.take(SAMPLE_PREVIEW)}",
                    color = PytColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "out: ${sample.output.take(SAMPLE_PREVIEW)}",
                    color = PytColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SubmitActionRow(
    canSubmit: Boolean,
    isSubmitting: Boolean,
    isPolling: Boolean,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onSubmit, enabled = canSubmit) {
            Text(
                when {
                    isSubmitting -> "提交中..."
                    isPolling -> "判题中..."
                    else -> "提交判题"
                },
            )
        }
    }
}

@Composable
private fun HistoryStrip(history: List<SubmissionDto>) {
    if (history.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
        Text(
            text = "最近 ${history.size.coerceAtMost(MAX_HISTORY)} 次提交",
            color = PytColors.textHint,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
            history.take(MAX_HISTORY).forEach { item ->
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .background(PytColors.bgSoft, RoundedCornerShape(PytSpace.s1))
                        .padding(horizontal = PytSpace.s2, vertical = PytSpace.s1),
                ) {
                    Column {
                        StatusChip(status = item.status)
                        Text(
                            text = "#${item.id}",
                            color = PytColors.textHint,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

private const val SAMPLE_PREVIEW: Int = 24
private const val MAX_SAMPLES: Int = 2
private const val MAX_HISTORY: Int = 4
