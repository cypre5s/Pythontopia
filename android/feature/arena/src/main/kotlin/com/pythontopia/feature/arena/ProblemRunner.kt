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

/**
 * 训练场"今日赛事"tab 的核心组件：依次显示题目 + 简化 CodeEditor + 提交按钮。
 *
 * 布局（自上而下垂直可滚）：
 *  1. 题目切换 chip row（LazyRow，按题目数横向滚）
 *  2. 题面卡片（题目标题 + 积分 + statementMd）
 *  3. 代码编辑器（BasicTextField 包一层等宽字体 + 边框，**不做语法高亮**——
 *     Beta 首发简化版避免引入 KotlinSyntaxHighlighter / VisualTransformation 复杂度）
 *  4. 提交按钮 + 上次判题结果卡片
 *
 * 故意把 problem 列表用 LazyRow 而非 TabRow 渲染：
 *  - Material3 的 TabRow 在 5+ tab 时会强制拉伸或溢出，体验差
 *  - LazyRow + FilterChip 更接近"题号面板"的语义，能容纳任意题目数量
 */
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

/**
 * 简化版代码编辑器。
 *
 * Beta 首发故意 **不做** 语法高亮与自动缩进：
 *  - 不引入 KotlinSyntaxHighlighter / VisualTransformation 复杂度
 *  - 训练场的题目代码量普遍 5-15 行，BasicTextField + 等宽字体已足够
 *  - 自动缩进会与 IME 联想冲突，引入更多 corner case
 *
 * 用外层 [Surface] + [border] 控制视觉，是因为 BasicTextField 的默认背景属性会拦截
 * 点击；外层包一层 Surface + border 视觉上等价于 OutlinedTextField，但不会强制施加
 * Material3 多余 padding，对小屏更友好。
 */
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
