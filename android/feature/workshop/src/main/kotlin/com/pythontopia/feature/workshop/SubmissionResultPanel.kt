package com.pythontopia.feature.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * Workshop（代码工坊）判题结果面板：屏幕底部的状态显示区。
 *
 * 三种状态：
 *   1. 还没提交：[EmptyState] 提示用户先选题；
 *   2. 提交中或轮询中：[StatusChip] + [CircularProgressIndicator]，traceback 区为空；
 *   3. 已拿到 [SubmissionDto]：[StatusChip] + 通过用例 + runtime + traceback（仅失败时显示）。
 *
 * traceback 区使用等宽字体 + 水平/垂直双滚动，方便阅读 Python traceback 多缩进结构。
 */
@Composable
fun SubmissionResultPanel(
    submission: SubmissionDto?,
    isSubmitting: Boolean,
    isPolling: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PytColors.bgElevated, RoundedCornerShape(PytSpace.s2))
            .border(1.dp, PytColors.borderSoft, RoundedCornerShape(PytSpace.s2))
            .padding(PytSpace.s3),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        when {
            submission != null -> ResolvedRow(
                submission = submission,
                isPolling = isPolling,
            )
            isSubmitting -> PendingRow(label = "正在提交到判题服务...")
            else -> EmptyState()
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = PytColors.danger,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        val traceback = submission?.traceback
        if (!traceback.isNullOrBlank() && !submission.status.isSuccess) {
            TracebackBox(traceback = traceback)
        }
    }
}

@Composable
private fun ResolvedRow(submission: SubmissionDto, isPolling: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(status = submission.status)
        if (isPolling) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = PytSpace.s1),
                strokeWidth = 2.dp,
            )
        }
        Box(modifier = Modifier.weight(1f))
        StatBubble(label = "用例", value = formatCases(submission.passedCases, submission.totalCases))
        StatBubble(label = "耗时", value = submission.runtimeMs?.let { "${it} ms" } ?: "—")
    }
}

@Composable
private fun PendingRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp)
        Text(label, color = PytColors.textMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyState() {
    Text(
        text = "选一道题，写完代码后点「提交」交给服务端判题。Beta 首发不跑 Pyodide，所有结果以服务端为准。",
        color = PytColors.textMuted,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun StatusChip(status: SubmissionStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = statusColors(status)
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(PytSpace.s1))
            .border(1.dp, fg.copy(alpha = 0.6f), RoundedCornerShape(PytSpace.s1))
            .padding(horizontal = PytSpace.s2, vertical = PytSpace.s1),
    ) {
        Text(
            text = status.displayLabel,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StatBubble(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(PytColors.bgSoft, RoundedCornerShape(PytSpace.s1))
            .padding(horizontal = PytSpace.s2, vertical = PytSpace.s1),
    ) {
        Text(label, color = PytColors.textHint, style = MaterialTheme.typography.labelSmall)
        Text(value, color = PytColors.textPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TracebackBox(traceback: String) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = TRACEBACK_MAX_HEIGHT.dp)
            .background(PytColors.bgSunken, RoundedCornerShape(PytSpace.s2))
            .border(1.dp, PytColors.danger.copy(alpha = 0.4f), RoundedCornerShape(PytSpace.s2))
            .padding(PytSpace.s2)
            .verticalScroll(verticalScroll)
            .horizontalScroll(horizontalScroll),
    ) {
        Text(
            text = traceback,
            color = PytColors.danger,
            fontFamily = FontFamily.Monospace,
            fontSize = TRACEBACK_FONT_SIZE.sp,
        )
    }
}

private fun statusColors(status: SubmissionStatus): Pair<Color, Color> = when (status) {
    SubmissionStatus.PENDING -> PytColors.bgSoft to PytColors.textHint
    SubmissionStatus.RUNNING -> PytColors.bgSoft to PytColors.info
    SubmissionStatus.PASSED,
    SubmissionStatus.ACCEPTED -> PytColors.success.copy(alpha = 0.18f) to PytColors.success
    SubmissionStatus.FAILED,
    SubmissionStatus.WRONG_ANSWER -> PytColors.danger.copy(alpha = 0.18f) to PytColors.danger
    SubmissionStatus.TIME_LIMIT -> PytColors.warning.copy(alpha = 0.18f) to PytColors.warning
    SubmissionStatus.ERROR,
    SubmissionStatus.RUNTIME_ERROR -> PytColors.danger.copy(alpha = 0.18f) to PytColors.danger
}

private fun formatCases(passed: Int?, total: Int?): String {
    if (passed == null && total == null) return "—"
    val p = passed?.toString() ?: "?"
    val t = total?.toString() ?: "?"
    return "$p / $t"
}

private const val TRACEBACK_FONT_SIZE: Int = 12
private const val TRACEBACK_MAX_HEIGHT: Int = 220
