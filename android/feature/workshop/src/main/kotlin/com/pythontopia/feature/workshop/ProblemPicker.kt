package com.pythontopia.feature.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * Workshop（代码工坊）选题对话框。
 *
 * 关键交互：
 *   - 顶部 OutlinedTextField 让用户改 KC 过滤码（例如 PY43-KC-016 → PY43-KC-022），
 *     onValueChange 直通 [onKcChange]，由 ViewModel 决定是否触发 [WorkshopViewModel.loadProblems]；
 *   - 中部 LazyColumn 列出当前 KC 下的题目；
 *   - 加载中显示 spinner，空列表显示占位文案；
 *   - 点击单条 → [onSelect]，对话框由 ViewModel 关闭，不在这里 dismiss 自己。
 */
@Composable
fun ProblemPicker(
    isOpen: Boolean,
    kcCode: String,
    onKcChange: (String) -> Unit,
    problems: List<ProblemDto>,
    isLoading: Boolean,
    onSelect: (ProblemDto) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择题目", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                OutlinedTextField(
                    value = kcCode,
                    onValueChange = onKcChange,
                    singleLine = true,
                    label = { Text("KC 编号") },
                    placeholder = { Text("例如 PY43-KC-016") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 360.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    when {
                        isLoading -> CircularProgressIndicator(modifier = Modifier.padding(PytSpace.s4))
                        problems.isEmpty() -> EmptyHint(kcCode = kcCode)
                        else -> ProblemList(problems = problems, onSelect = onSelect)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun ProblemList(
    problems: List<ProblemDto>,
    onSelect: (ProblemDto) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
        items(items = problems, key = { it.id }) { problem ->
            ProblemRow(problem = problem, onClick = { onSelect(problem) })
        }
    }
}

@Composable
private fun ProblemRow(problem: ProblemDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PytColors.bgSoft, RoundedCornerShape(PytSpace.s2))
            .border(1.dp, PytColors.borderSoft, RoundedCornerShape(PytSpace.s2))
            .clickable(onClick = onClick)
            .padding(PytSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = problem.title,
                color = PytColors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${problem.kcCode} · 难度 ${problem.difficulty ?: "未知"}",
                color = PytColors.textHint,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(modifier = Modifier.width(PytSpace.s2))
        DifficultyBadge(difficulty = problem.difficulty)
    }
}

@Composable
private fun DifficultyBadge(difficulty: String?) {
    val label = difficulty ?: "—"
    val (bg, fg) = when (label.lowercase()) {
        "low", "easy" -> PytColors.success.copy(alpha = 0.18f) to PytColors.success
        "medium", "mid" -> PytColors.warning.copy(alpha = 0.18f) to PytColors.warning
        "high", "hard" -> PytColors.danger.copy(alpha = 0.18f) to PytColors.danger
        else -> PytColors.bgElevated to PytColors.textMuted
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(PytSpace.s1))
            .padding(horizontal = PytSpace.s2, vertical = 2.dp),
    ) {
        Text(text = label, color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyHint(kcCode: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PytSpace.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        Text(
            text = "未拉到 $kcCode 的题目",
            color = PytColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "确认账号已登录且后端 /api/problems?kcCode= 已上线",
            color = PytColors.textHint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
