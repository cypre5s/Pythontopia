package com.pythontopia.feature.arena

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 今日赛事 tab：题号选择、题面、代码编辑、提交与上次判题结果。 */
@Composable
fun ProblemRunner(
    state: ArenaUiState,
    onSelectProblem: (Int) -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.session
    if (session == null) {
        EmptyState(text = if (state.isLoadingSession) "正在加载赛事…" else "今日暂无赛事，请稍后再试")
        return
    }
    if (session.problems.isEmpty()) {
        EmptyState(text = "本场赛事没有题目，请联系老师配置")
        return
    }
    val problem = state.currentProblem ?: session.problems.first()
    val scroll = rememberScrollState()

    Column(modifier = modifier.fillMaxSize().verticalScroll(scroll)) {
        ProblemPicker(
            problems = session.problems,
            currentIdx = state.currentProblemIdx,
            onSelectProblem = onSelectProblem,
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProblemStatementCard(problem = problem)
        Spacer(modifier = Modifier.height(12.dp))
        CodeEditor(
            code = state.currentCode,
            onCodeChange = onCodeChange,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SubmitBar(
            isSubmitting = state.isSubmitting,
            onSubmit = onSubmit,
        )
        state.lastSubmission?.let { last ->
            Spacer(modifier = Modifier.height(12.dp))
            SubmissionResultCard(submission = last)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProblemPicker(
    problems: List<ArenaProblem>,
    currentIdx: Int,
    onSelectProblem: (Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(problems.withIndex().toList()) { (idx, problem) ->
            FilterChip(
                selected = idx == currentIdx,
                onClick = { onSelectProblem(idx) },
                label = { Text(problem.title) },
            )
        }
    }
}

@Composable
private fun ProblemStatementCard(problem: ArenaProblem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = problem.title, style = MaterialTheme.typography.titleMedium)
                AssistChip(
                    onClick = {},
                    label = { Text("满分 ${problem.scoreReward}") },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = problem.statementMd,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 无语法高亮；Surface+border 包 BasicTextField 避免默认背景拦截点击。 */
@Composable
private fun CodeEditor(
    code: String,
    onCodeChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
    ) {
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

@Composable
private fun SubmitBar(isSubmitting: Boolean, onSubmit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
        }
        Button(onClick = onSubmit, enabled = !isSubmitting) {
            Text(if (isSubmitting) "判题中…" else "提交")
        }
    }
}

@Composable
private fun SubmissionResultCard(submission: Submission) {
    val container = when (submission.status) {
        ArenaRepository.STATUS_PASSED -> MaterialTheme.colorScheme.primaryContainer
        ArenaRepository.STATUS_FAILED -> MaterialTheme.colorScheme.errorContainer
        ArenaRepository.STATUS_RUNTIME_ERROR -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val label = when (submission.status) {
        ArenaRepository.STATUS_PASSED -> "通过"
        ArenaRepository.STATUS_FAILED -> "未通过"
        ArenaRepository.STATUS_RUNTIME_ERROR -> "运行时错误"
        else -> submission.status
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "上次判题：$label", style = MaterialTheme.typography.titleSmall)
                Text(text = "+${submission.scoreAwarded}", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = submission.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}
